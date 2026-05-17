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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.*;

/**
 * /zone — редактирование block_zones в игре (OP level 2).
 *
 * Workflow:
 *  1. /zone wand          — взять жезл (ЛКМ по блоку = pos1, ПКМ = pos2)
 *  2. /zone target <block> — тип блока (например minecraft:bookshelf)
 *  3. /zone condition <c>  — условие квеста (пустая строка = нет)
 *  4. /zone default <dialog> — диалог по умолчанию для не-особых блоков
 *  5. /zone default_name <name> — имя для не-особых блоков
 *  6. /zone mark <dialog>  — навести прицел на блок, задать ему особый диалог
 *  7. /zone save <id>       — сохранить зону в файл и перезагрузить
 *
 * Дополнительно:
 *  /zone pos1 / pos2       — установить угол по смотримому блоку (альтернатива жезлу)
 *  /zone unmark            — снять отметку со смотримого блока
 *  /zone info              — показать текущую сессию
 *  /zone list              — все загруженные зоны
 *  /zone clear             — сбросить сессию
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

                .then(literal("target")
                    .then(argument("block_id", StringArgumentType.word())
                        .executes(ctx -> setTarget(ctx, StringArgumentType.getString(ctx, "block_id")))))

                .then(literal("condition")
                    .then(argument("condition", StringArgumentType.greedyString())
                        .executes(ctx -> setCondition(ctx, StringArgumentType.getString(ctx, "condition")))))

                .then(literal("default")
                    .then(argument("dialog_id", StringArgumentType.word())
                        .executes(ctx -> setDefault(ctx, StringArgumentType.getString(ctx, "dialog_id")))))

                .then(literal("default_name")
                    .then(argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> setDefaultName(ctx, StringArgumentType.getString(ctx, "name")))))

                .then(literal("mark")
                    .then(argument("dialog_id", StringArgumentType.word())
                        .executes(ctx -> markBlock(ctx, StringArgumentType.getString(ctx, "dialog_id"), ""))
                        .then(argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> markBlock(ctx,
                                StringArgumentType.getString(ctx, "dialog_id"),
                                StringArgumentType.getString(ctx, "name"))))))

                .then(literal("unmark")
                    .executes(ZoneBuilderCommand::unmarkBlock))

                .then(literal("save")
                    .then(argument("id", StringArgumentType.word())
                        .executes(ctx -> saveZone(ctx, StringArgumentType.getString(ctx, "id")))))

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

    // ── /zone pos1 / pos2 (raycast) ───────────────────────────────

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

    // ── /zone target ──────────────────────────────────────────────

    private static int setTarget(CommandContext<ServerCommandSource> ctx, String blockId) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        ZoneBuilderSession.get(player.getUuid()).targetBlock = blockId;
        player.sendMessage(Text.literal("[Zone] target_block = " + blockId), false);
        return 1;
    }

    // ── /zone condition ───────────────────────────────────────────

    private static int setCondition(CommandContext<ServerCommandSource> ctx, String condition) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        String value = condition.equalsIgnoreCase("none") ? "" : condition;
        ZoneBuilderSession.get(player.getUuid()).condition = value;
        player.sendMessage(Text.literal("[Zone] condition = " + (value.isEmpty() ? "(нет)" : value)), false);
        return 1;
    }

    // ── /zone default ─────────────────────────────────────────────

    private static int setDefault(CommandContext<ServerCommandSource> ctx, String dialogId) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        ZoneBuilderSession.get(player.getUuid()).defaultDialog = dialogId;
        player.sendMessage(Text.literal("[Zone] default_dialog = " + dialogId), false);
        return 1;
    }

    // ── /zone default_name ────────────────────────────────────────

    private static int setDefaultName(CommandContext<ServerCommandSource> ctx, String name) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        ZoneBuilderSession.get(player.getUuid()).defaultName = name;
        player.sendMessage(Text.literal("[Zone] default_name = " + name), false);
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
        player.sendMessage(Text.literal("[Zone] Отмечен блок " + formatPos(pos)
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
            ? "[Zone] Отметка снята с " + formatPos(pos)
            : "[Zone] Блок " + formatPos(pos) + " не был отмечен."), false);
        return removed ? 1 : 0;
    }

    // ── /zone save <id> ───────────────────────────────────────────

    private static int saveZone(CommandContext<ServerCommandSource> ctx, String id) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;

        ZoneBuilderSession session = ZoneBuilderSession.get(player.getUuid());
        if (!session.isReadyToSave()) {
            player.sendMessage(Text.literal(
                "[Zone] Не хватает данных. Нужно: pos1, pos2, target, default_dialog."), false);
            return 0;
        }

        BlockPos min = session.getMinCorner();
        BlockPos max = session.getMaxCorner();

        BlockZoneDefinition def = new BlockZoneDefinition();
        def.id            = id;
        def.condition     = session.condition;
        def.world         = session.world != null ? session.world.getValue().toString() : "minecraft:overworld";
        def.min_x = min.getX(); def.min_y = min.getY(); def.min_z = min.getZ();
        def.max_x = max.getX(); def.max_y = max.getY(); def.max_z = max.getZ();
        def.target_block  = session.targetBlock;
        def.default_dialog = session.defaultDialog;
        def.default_name  = session.defaultName;

        List<BlockZoneDefinition.BlockEntry> entries = new ArrayList<>();
        for (ZoneBuilderSession.MarkedBlock mb : session.blocks) {
            BlockZoneDefinition.BlockEntry e = new BlockZoneDefinition.BlockEntry();
            e.x = mb.x; e.y = mb.y; e.z = mb.z;
            e.dialog = mb.dialog;
            e.name   = mb.name;
            entries.add(e);
        }
        def.blocks = entries;

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
            "[Zone] Сохранено '" + id + "': регион " + formatPos(min) + " → " + formatPos(max)
            + ", блоков: " + entries.size() + " особых."), false);
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
        sb.append("  target_block: ").append(s.targetBlock.isEmpty() ? "не задано" : s.targetBlock).append("\n");
        sb.append("  condition: ").append(s.condition.isEmpty() ? "(нет)" : s.condition).append("\n");
        sb.append("  default_dialog: ").append(s.defaultDialog.isEmpty() ? "не задано" : s.defaultDialog).append("\n");
        sb.append("  default_name: ").append(s.defaultName).append("\n");
        if (s.blocks.isEmpty()) {
            sb.append("  особые блоки: нет");
        } else {
            sb.append("  особые блоки (").append(s.blocks.size()).append("):\n");
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
              .append(" [").append(z.target_block).append("]")
              .append(" condition=").append(z.condition.isEmpty() ? "—" : z.condition)
              .append(" blocks=").append(z.blocks != null ? z.blocks.size() : 0).append("\n");
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

    /** Возвращает координаты блока, на который смотрит игрок (до 10 блоков), или null. */
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
