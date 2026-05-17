package com.minelittlepony.ponynpc.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minelittlepony.ponynpc.quest.BlockZoneDefinition;
import com.minelittlepony.ponynpc.quest.BlockZoneRegistry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static net.minecraft.server.command.CommandManager.*;

/**
 * /zone — создание block_zones в игре (OP level 2).
 *
 * Workflow:
 *  1. /zone wand
 *       Выдаёт жезл. ЛКМ по блоку = pos1, ПКМ по блоку = pos2.
 *
 *  2. Настрой диалоги:
 *       /zone add_default <dialog>             — добавить диалог в пул по умолчанию
 *       /zone mark <dialog> [name]             — фиксированный особый блок (прицел)
 *       /zone random_pick <dialog> [count] [name] — N рандомных блоков как "особые" per-player
 *
 *  3. /zone save <id> <block_type> [condition]
 *       Сохраняет зону и перезагружает.
 *
 * Остальные команды:
 *  /zone pos1 / pos2   — альтернатива жезлу (смотримый блок)
 *  /zone unmark        — снять fixed-отметку
 *  /zone info          — текущая сессия
 *  /zone list          — загруженные зоны
 *  /zone clear         — сбросить сессию
 */
public final class ZoneBuilderCommand {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String WAND_TAG = "ponynpc_zone_wand";

    private ZoneBuilderCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            registerCommands(dispatcher));

        // Жезл: ЛКМ по блоку = pos1
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!isZoneWand(player.getMainHandStack())) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

            ZoneBuilderSession session = ZoneBuilderSession.get(sp.getUuid());
            session.pos1 = pos;
            session.world = world.getRegistryKey();
            sp.sendMessage(Text.literal("[Zone] pos1 → " + formatPos(pos)).formatted(Formatting.YELLOW), true);
            return ActionResult.SUCCESS;
        });

        // Жезл: ПКМ по блоку = pos2
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!isZoneWand(player.getMainHandStack())) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

            BlockPos pos = hit.getBlockPos();
            ZoneBuilderSession session = ZoneBuilderSession.get(sp.getUuid());
            session.pos2 = pos;
            session.world = world.getRegistryKey();
            sp.sendMessage(Text.literal("[Zone] pos2 → " + formatPos(pos)).formatted(Formatting.YELLOW), true);
            return ActionResult.SUCCESS;
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("zone")
                .requires(src -> src.hasPermissionLevel(2))

                .then(literal("wand")
                    .executes(ZoneBuilderCommand::giveWand))

                .then(literal("pos1")
                    .executes(ZoneBuilderCommand::setPos1))

                .then(literal("pos2")
                    .executes(ZoneBuilderCommand::setPos2))

                .then(literal("mark")
                    .then(argument("dialog_id", StringArgumentType.word())
                        .executes(ctx -> markBlock(ctx, StringArgumentType.getString(ctx, "dialog_id"), ""))
                        .then(argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> markBlock(ctx,
                                StringArgumentType.getString(ctx, "dialog_id"),
                                StringArgumentType.getString(ctx, "name"))))))

                .then(literal("unmark")
                    .executes(ZoneBuilderCommand::unmarkBlock))

                // /zone add_default <dialog>  — добавить диалог в пул по умолчанию
                .then(literal("add_default")
                    .then(argument("dialog_id", StringArgumentType.word())
                        .executes(ctx -> addDefault(ctx, StringArgumentType.getString(ctx, "dialog_id")))))

                // /zone random_pick <dialog> [count] [name]
                .then(literal("random_pick")
                    .then(argument("dialog_id", StringArgumentType.word())
                        .executes(ctx -> addRandomPick(ctx,
                            StringArgumentType.getString(ctx, "dialog_id"), 1, ""))
                        .then(argument("count", IntegerArgumentType.integer(1, 64))
                            .executes(ctx -> addRandomPick(ctx,
                                StringArgumentType.getString(ctx, "dialog_id"),
                                IntegerArgumentType.getInteger(ctx, "count"), ""))
                            .then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> addRandomPick(ctx,
                                    StringArgumentType.getString(ctx, "dialog_id"),
                                    IntegerArgumentType.getInteger(ctx, "count"),
                                    StringArgumentType.getString(ctx, "name")))))))

                // /zone save <id> <target_block> [condition]
                .then(literal("save")
                    .then(argument("id", StringArgumentType.word())
                        .then(argument("target_block", StringArgumentType.word())
                            .executes(ctx -> saveZone(ctx,
                                StringArgumentType.getString(ctx, "id"),
                                StringArgumentType.getString(ctx, "target_block"),
                                ""))
                            .then(argument("condition", StringArgumentType.greedyString())
                                .executes(ctx -> saveZone(ctx,
                                    StringArgumentType.getString(ctx, "id"),
                                    StringArgumentType.getString(ctx, "target_block"),
                                    StringArgumentType.getString(ctx, "condition")))))))

                .then(literal("info")
                    .executes(ZoneBuilderCommand::showInfo))

                .then(literal("list")
                    .executes(ZoneBuilderCommand::listZones))

                .then(literal("clear")
                    .executes(ZoneBuilderCommand::clearSession))
        );
    }

    // ── /zone wand ────────────────────────────────────────────────

    private static int giveWand(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;

        ItemStack wand = new ItemStack(Items.STICK);
        wand.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal("[Zone Wand]").formatted(Formatting.GREEN));
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(WAND_TAG, true);
        wand.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        player.giveItemStack(wand);
        player.sendMessage(Text.literal("[Zone] Жезл выдан. ЛКМ по блоку = pos1, ПКМ = pos2."), false);
        return 1;
    }

    // ── /zone pos1 / pos2 (альтернатива жезлу) ───────────────────

    private static int setPos1(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        BlockPos pos = getLookedAtBlock(player);
        if (pos == null) {
            player.sendMessage(Text.literal("[Zone] Наведи прицел на блок."), false);
            return 0;
        }
        ZoneBuilderSession session = ZoneBuilderSession.get(player.getUuid());
        session.pos1 = pos;
        session.world = player.getServerWorld().getRegistryKey();
        player.sendMessage(Text.literal("[Zone] pos1 → " + formatPos(pos)).formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int setPos2(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        BlockPos pos = getLookedAtBlock(player);
        if (pos == null) {
            player.sendMessage(Text.literal("[Zone] Наведи прицел на блок."), false);
            return 0;
        }
        ZoneBuilderSession session = ZoneBuilderSession.get(player.getUuid());
        session.pos2 = pos;
        session.world = player.getServerWorld().getRegistryKey();
        player.sendMessage(Text.literal("[Zone] pos2 → " + formatPos(pos)).formatted(Formatting.YELLOW), false);
        return 1;
    }

    // ── /zone mark ────────────────────────────────────────────────

    private static int markBlock(CommandContext<ServerCommandSource> ctx, String dialogId, String name) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        BlockPos pos = getLookedAtBlock(player);
        if (pos == null) {
            player.sendMessage(Text.literal("[Zone] Наведи прицел на блок."), false);
            return 0;
        }
        ZoneBuilderSession.get(player.getUuid()).markBlock(pos, dialogId, name);
        player.sendMessage(Text.literal("[Zone] Отмечен " + formatPos(pos)
            + " → " + dialogId + (name.isEmpty() ? "" : " [" + name + "]")), false);
        return 1;
    }

    // ── /zone unmark ──────────────────────────────────────────────

    private static int unmarkBlock(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        BlockPos pos = getLookedAtBlock(player);
        if (pos == null) {
            player.sendMessage(Text.literal("[Zone] Наведи прицел на блок."), false);
            return 0;
        }
        boolean removed = ZoneBuilderSession.get(player.getUuid()).unmarkBlock(pos);
        player.sendMessage(Text.literal(removed
            ? "[Zone] Снята отметка с " + formatPos(pos)
            : "[Zone] Блок " + formatPos(pos) + " не был отмечен."), false);
        return removed ? 1 : 0;
    }

    // ── /zone add_default ─────────────────────────────────────────

    private static int addDefault(CommandContext<ServerCommandSource> ctx, String dialogId) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        ZoneBuilderSession.get(player.getUuid()).defaultDialogs.add(dialogId);
        player.sendMessage(Text.literal("[Zone] Добавлен диалог по умолчанию: " + dialogId), false);
        return 1;
    }

    // ── /zone random_pick ─────────────────────────────────────────

    private static int addRandomPick(CommandContext<ServerCommandSource> ctx,
                                      String dialogId, int count, String name) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        ZoneBuilderSession.RandomPick pick = new ZoneBuilderSession.RandomPick();
        pick.dialog = dialogId;
        pick.count  = count;
        pick.name   = name;
        ZoneBuilderSession.get(player.getUuid()).randomPicks.add(pick);
        player.sendMessage(Text.literal(
            "[Zone] random_pick: " + pick.format()), false);
        return 1;
    }

    // ── /zone save <id> <block_type> [condition] ──────────────────

    private static int saveZone(CommandContext<ServerCommandSource> ctx,
                                 String id, String targetBlock, String condition) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;

        ZoneBuilderSession session = ZoneBuilderSession.get(player.getUuid());
        if (!session.hasSelection()) {
            player.sendMessage(Text.literal(
                "[Zone] Сначала задай регион жезлом (ЛКМ = pos1, ПКМ = pos2)."), false);
            return 0;
        }
        if (session.defaultDialogs.isEmpty()) {
            player.sendMessage(Text.literal(
                "[Zone] Нужен хотя бы один диалог по умолчанию: /zone add_default <dialog_id>"), false);
            return 0;
        }

        BlockPos min = session.getMinCorner();
        BlockPos max = session.getMaxCorner();

        BlockZoneDefinition def = new BlockZoneDefinition();
        def.id           = id;
        def.condition    = condition;
        def.world        = session.world != null ? session.world.getValue().toString() : "minecraft:overworld";
        def.min_x = min.getX(); def.min_y = min.getY(); def.min_z = min.getZ();
        def.max_x = max.getX(); def.max_y = max.getY(); def.max_z = max.getZ();
        def.target_block = targetBlock;
        // Если один диалог — кладём и в default_dialog (совместимость), и в список
        def.default_dialogs = new ArrayList<>(session.defaultDialogs);
        def.default_dialog  = session.defaultDialogs.get(0);
        def.default_name    = "???";

        List<BlockZoneDefinition.BlockEntry> fixedEntries = new ArrayList<>();
        for (ZoneBuilderSession.MarkedBlock mb : session.blocks) {
            BlockZoneDefinition.BlockEntry e = new BlockZoneDefinition.BlockEntry();
            e.x = mb.x; e.y = mb.y; e.z = mb.z;
            e.dialog = mb.dialog;
            e.name   = mb.name;
            fixedEntries.add(e);
        }
        def.blocks = fixedEntries;

        List<BlockZoneDefinition.RandomPickEntry> pickEntries = new ArrayList<>();
        for (ZoneBuilderSession.RandomPick rp : session.randomPicks) {
            BlockZoneDefinition.RandomPickEntry e = new BlockZoneDefinition.RandomPickEntry();
            e.count  = rp.count;
            e.dialog = rp.dialog;
            e.name   = rp.name;
            pickEntries.add(e);
        }
        def.random_picks = pickEntries;

        Path dir = FabricLoader.getInstance().getConfigDir().resolve("ponynpc").resolve("block_zones");
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(id + ".json"), GSON.toJson(def));
        } catch (IOException e) {
            player.sendMessage(Text.literal("[Zone] Ошибка сохранения: " + e.getMessage()), false);
            return 0;
        }

        BlockZoneRegistry.getInstance().reload();
        ZoneBuilderSession.clear(player.getUuid());

        player.sendMessage(Text.literal(
            "[Zone] Сохранено '" + id + "': "
            + formatPos(min) + " → " + formatPos(max)
            + " | блок: " + targetBlock
            + " | fixed: " + fixedEntries.size()
            + " | random_picks: " + pickEntries.size()
            + " | default пул: " + session.defaultDialogs.size()), false);
        return 1;
    }

    // ── /zone info ────────────────────────────────────────────────

    private static int showInfo(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;

        ZoneBuilderSession s = ZoneBuilderSession.get(player.getUuid());
        StringBuilder sb = new StringBuilder("[Zone] Текущая сессия:\n");
        sb.append("  pos1: ").append(s.pos1 != null ? formatPos(s.pos1) : "не задано").append("\n");
        sb.append("  pos2: ").append(s.pos2 != null ? formatPos(s.pos2) : "не задано").append("\n");
        if (s.defaultDialogs.isEmpty()) {
            sb.append("  default dialogs: нет  ← нужен хотя бы один\n");
        } else {
            sb.append("  default dialogs (").append(s.defaultDialogs.size()).append("): ")
              .append(String.join(", ", s.defaultDialogs)).append("\n");
        }
        if (!s.randomPicks.isEmpty()) {
            sb.append("  random_picks (").append(s.randomPicks.size()).append("):\n");
            for (ZoneBuilderSession.RandomPick rp : s.randomPicks) {
                sb.append("    ").append(rp.format()).append("\n");
            }
        }
        if (!s.blocks.isEmpty()) {
            sb.append("  fixed блоки (").append(s.blocks.size()).append("):\n");
            for (ZoneBuilderSession.MarkedBlock b : s.blocks) {
                sb.append("    ").append(b.format()).append("\n");
            }
        }
        player.sendMessage(Text.literal(sb.toString()), false);
        return 1;
    }

    // ── /zone list ────────────────────────────────────────────────

    private static int listZones(CommandContext<ServerCommandSource> ctx) {
        var zones = BlockZoneRegistry.getInstance().getZones();
        if (zones.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("[Zone] Нет загруженных зон."), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("[Zone] Зоны (" + zones.size() + "):\n");
        for (var z : zones) {
            sb.append("  ").append(z.id)
              .append(" | блок: ").append(z.target_block)
              .append(" | condition: ").append(z.condition.isEmpty() ? "—" : z.condition)
              .append(" | особых: ").append(z.blocks != null ? z.blocks.size() : 0).append("\n");
        }
        String msg = sb.toString();
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
        return zones.size();
    }

    // ── /zone clear ───────────────────────────────────────────────

    private static int clearSession(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        ZoneBuilderSession.clear(player.getUuid());
        player.sendMessage(Text.literal("[Zone] Сессия сброшена."), false);
        return 1;
    }

    // ── Утилиты ───────────────────────────────────────────────────

    private static boolean isZoneWand(ItemStack stack) {
        if (stack.isEmpty()) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return false;
        return data.copyNbt().getBoolean(WAND_TAG);
    }

    private static BlockPos getLookedAtBlock(ServerPlayerEntity player) {
        HitResult hit = player.raycast(10.0, 0.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) hit).getBlockPos();
    }

    private static ServerPlayerEntity getPlayer(CommandContext<ServerCommandSource> ctx) {
        try {
            return ctx.getSource().getPlayerOrThrow();
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Must be run by a player."));
            return null;
        }
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
