package com.minelittlepony.ponynpc.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minelittlepony.ponynpc.dungeon.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * /dungeon — команды для создания и управления данжами (OP level 2).
 *
 * Workflow:
 *  1. /dungeon build <id>   — телепорт в измерение данжа к зоне строительства
 *  2. Строишь данж вручную в мире
 *  3. /dungeon pos1         — минимальный угол
 *  4. /dungeon pos2         — максимальный угол
 *  5. /dungeon spawn        — точка спауна игроков (абс. координата → будет вычислено как offset)
 *  6. /dungeon finish       — позиция NPC завершения (offset)
 *  7. /dungeon save <id>    — сохранить в конфиг
 *  8. /dungeon info <id>    — показать текущий конфиг
 *  9. /dungeon test <id>    — создать тестовый инстанс для себя
 */
public final class DungeonBuilderCommand {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private DungeonBuilderCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("dungeon")
                .requires(src -> src.hasPermissionLevel(2))

                .then(CommandManager.literal("pos1")
                    .executes(DungeonBuilderCommand::setPos1))

                .then(CommandManager.literal("pos2")
                    .executes(DungeonBuilderCommand::setPos2))

                .then(CommandManager.literal("spawn")
                    .executes(DungeonBuilderCommand::setSpawn))

                .then(CommandManager.literal("finish")
                    .executes(DungeonBuilderCommand::setFinish))

                .then(CommandManager.literal("save")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(ctx -> save(ctx, StringArgumentType.getString(ctx, "id")))))

                .then(CommandManager.literal("build")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(ctx -> build(ctx, StringArgumentType.getString(ctx, "id")))))

                .then(CommandManager.literal("info")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "id")))))

                .then(CommandManager.literal("test")
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .executes(ctx -> test(ctx, StringArgumentType.getString(ctx, "id")))))
        );
    }

    // ── /dungeon pos1 ─────────────────────────────────────────────

    private static int setPos1(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        if (!requireDungeonWorld(player)) return 0;

        BlockPos pos = player.getBlockPos();
        DungeonBuilderSession.get(player.getUuid()).pos1 = pos;
        player.sendMessage(Text.literal("[Dungeon] pos1 set to " + formatPos(pos)), false);
        return 1;
    }

    // ── /dungeon pos2 ─────────────────────────────────────────────

    private static int setPos2(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        if (!requireDungeonWorld(player)) return 0;

        BlockPos pos = player.getBlockPos();
        DungeonBuilderSession.get(player.getUuid()).pos2 = pos;
        player.sendMessage(Text.literal("[Dungeon] pos2 set to " + formatPos(pos)), false);
        return 1;
    }

    // ── /dungeon spawn ────────────────────────────────────────────

    private static int setSpawn(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        if (!requireDungeonWorld(player)) return 0;

        DungeonBuilderSession session = DungeonBuilderSession.get(player.getUuid());
        if (!session.hasSelection()) {
            player.sendMessage(Text.literal("[Dungeon] Set pos1 and pos2 first."), false);
            return 0;
        }

        BlockPos pos = player.getBlockPos();
        session.spawn = pos;

        // Compute offset relative to min corner so the user sees what will be stored
        BlockPos min = session.getMinCorner();
        int ox = pos.getX() - min.getX();
        int oy = pos.getY() - min.getY();
        int oz = pos.getZ() - min.getZ();
        player.sendMessage(Text.literal(
            "[Dungeon] spawn set to " + formatPos(pos) + " (offset: " + ox + "," + oy + "," + oz + ")"), false);
        return 1;
    }

    // ── /dungeon finish ───────────────────────────────────────────

    private static int setFinish(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        if (!requireDungeonWorld(player)) return 0;

        DungeonBuilderSession session = DungeonBuilderSession.get(player.getUuid());
        if (!session.hasSelection()) {
            player.sendMessage(Text.literal("[Dungeon] Set pos1 and pos2 first."), false);
            return 0;
        }

        BlockPos pos = player.getBlockPos();
        session.finish = pos;

        BlockPos min = session.getMinCorner();
        int ox = pos.getX() - min.getX();
        int oy = pos.getY() - min.getY();
        int oz = pos.getZ() - min.getZ();
        player.sendMessage(Text.literal(
            "[Dungeon] finish NPC set to " + formatPos(pos) + " (offset: " + ox + "," + oy + "," + oz + ")"), false);
        return 1;
    }

    // ── /dungeon save <id> ────────────────────────────────────────

    private static int save(CommandContext<ServerCommandSource> ctx, String id) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;

        DungeonBuilderSession session = DungeonBuilderSession.get(player.getUuid());
        if (!session.isReadyToSave()) {
            player.sendMessage(Text.literal("[Dungeon] Need pos1, pos2, and spawn before saving."), false);
            return 0;
        }

        BlockPos min = session.getMinCorner();

        // Load existing definition or create new
        DungeonDefinition def = DungeonRegistry.getInstance().getDungeon(id).orElse(null);
        if (def == null) {
            def = new DungeonDefinition();
            def.id = id;
            def.name = id;
        }

        def.buildOrigin = new DungeonDefinition.IntVec3(min.getX(), min.getY(), min.getZ());
        def.size = new DungeonDefinition.SizeVec(session.getWidth(), session.getHeight(), session.getLength());

        // Spawn offset
        BlockPos sp = session.spawn;
        def.spawnOffset = new DungeonDefinition.IntVec3(
            sp.getX() - min.getX(), sp.getY() - min.getY(), sp.getZ() - min.getZ());

        // Finish NPC offset (optional)
        if (session.finish != null) {
            BlockPos fn = session.finish;
            if (def.finishNpc == null) def.finishNpc = new DungeonDefinition.FinishNpcConfig();
            def.finishNpc.offset = new DungeonDefinition.IntVec3(
                fn.getX() - min.getX(), fn.getY() - min.getY(), fn.getZ() - min.getZ());
        }

        // Persist to disk
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("ponynpc/dungeons");
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(id + ".json");
            Files.writeString(file, GSON.toJson(def));
        } catch (IOException e) {
            player.sendMessage(Text.literal("[Dungeon] Failed to save: " + e.getMessage()), false);
            return 0;
        }

        // Reload registry so the definition is live
        DungeonRegistry.getInstance().reload();
        DungeonBuilderSession.clear(player.getUuid());

        player.sendMessage(Text.literal("[Dungeon] Saved '" + id + "': "
            + session.getWidth() + "x" + session.getHeight() + "x" + session.getLength()
            + " @ " + formatPos(min)), false);
        return 1;
    }

    // ── /dungeon build <id> ───────────────────────────────────────

    private static int build(CommandContext<ServerCommandSource> ctx, String id) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;

        ServerWorld dungeonWorld = ctx.getSource().getServer()
            .getWorld(DungeonInstanceManager.DUNGEON_WORLD_KEY);
        if (dungeonWorld == null) {
            player.sendMessage(Text.literal("[Dungeon] Dungeon world not loaded."), false);
            return 0;
        }

        DungeonDefinition def = DungeonRegistry.getInstance().getDungeon(id).orElse(null);

        double tx, ty, tz;
        if (def != null && def.hasBuildOrigin()) {
            // Teleport to existing build zone
            tx = def.buildOrigin.x + 0.5;
            ty = def.buildOrigin.y;
            tz = def.buildOrigin.z + 0.5;
            player.sendMessage(Text.literal("[Dungeon] Teleporting to build zone of '" + id + "'."), false);
        } else {
            // Assign a new build zone at negative X (far from player slots which are at positive X)
            // Use slot -1 style: X = -(slot * 10000 + 5000). Start at -5000, -15000, etc.
            int buildSlot = DungeonRegistry.getInstance().getNextBuildSlot();
            tx = -(buildSlot * 10000 + 5000) + 0.5;
            ty = 64;
            tz = 0.5;
            player.sendMessage(Text.literal("[Dungeon] Teleporting to new build area for '" + id
                + "'. Build your dungeon here, then use /dungeon pos1/pos2/spawn/save " + id + "."), false);
        }

        player.teleport(dungeonWorld, tx, ty, tz,
            java.util.Set.of(), player.getYaw(), player.getPitch());
        return 1;
    }

    // ── /dungeon info <id> ────────────────────────────────────────

    private static int info(CommandContext<ServerCommandSource> ctx, String id) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;

        DungeonDefinition def = DungeonRegistry.getInstance().getDungeon(id).orElse(null);
        if (def == null) {
            player.sendMessage(Text.literal("[Dungeon] Unknown dungeon: " + id), false);
            return 0;
        }

        StringBuilder sb = new StringBuilder("[Dungeon] ").append(id).append(":\n");
        sb.append("  name: ").append(def.name).append("\n");
        sb.append("  size: ").append(def.getWidth()).append("x")
            .append(def.getHeight()).append("x").append(def.getLength()).append("\n");
        if (def.hasBuildOrigin()) {
            sb.append("  build_origin: ").append(def.buildOrigin.x).append(",")
                .append(def.buildOrigin.y).append(",").append(def.buildOrigin.z).append("\n");
        } else {
            sb.append("  build_origin: NOT SET (use /dungeon build ").append(id).append(")\n");
        }
        sb.append("  spawn_offset: ").append(def.spawnOffset.x).append(",")
            .append(def.spawnOffset.y).append(",").append(def.spawnOffset.z).append("\n");
        sb.append("  destructible: ").append(def.destructible).append("\n");
        sb.append("  fixed_time: ").append(def.fixedTime != null ? def.fixedTime : "none").append("\n");
        sb.append("  rewards: ").append(def.rewards.size());

        player.sendMessage(Text.literal(sb.toString()), false);
        return 1;
    }

    // ── /dungeon test <id> ────────────────────────────────────────

    private static int test(CommandContext<ServerCommandSource> ctx, String id) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;
        DungeonInstanceManager.getInstance().enterDungeon(player, id, ctx.getSource().getServer());
        return 1;
    }

    // ── Утилиты ───────────────────────────────────────────────────

    private static ServerPlayerEntity getPlayer(CommandContext<ServerCommandSource> ctx) {
        try {
            return ctx.getSource().getPlayerOrThrow();
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Must be run by a player."));
            return null;
        }
    }

    private static boolean requireDungeonWorld(ServerPlayerEntity player) {
        if (!player.getServerWorld().getRegistryKey().equals(DungeonInstanceManager.DUNGEON_WORLD_KEY)) {
            player.sendMessage(Text.literal(
                "[Dungeon] You must be in the dungeon dimension (ponynpc:dungeon). "
                + "Use /dungeon build <id> to teleport there."), false);
            return false;
        }
        return true;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
