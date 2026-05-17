package com.minelittlepony.ponynpc.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.minelittlepony.ponynpc.PonyNpcMod;
import com.minelittlepony.ponynpc.dialog.DialogManager;
import com.minelittlepony.ponynpc.dungeon.DungeonInstanceManager;
import com.minelittlepony.ponynpc.dungeon.DungeonRegistry;
import com.minelittlepony.ponynpc.entity.PonyNpcEntity;
import com.minelittlepony.ponynpc.entity.QuestMarkerEntity;
import com.minelittlepony.ponynpc.ghost.GhostNpcManager;
import com.minelittlepony.ponynpc.quest.QuestManager;
import com.minelittlepony.ponynpc.registry.PonyNpcEntities;
import com.minelittlepony.ponynpc.state.QuestPocketState;
import com.minelittlepony.ponynpc.state.QuestStateStore;
import com.minelittlepony.ponynpc.trigger.ProximityTriggerManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static net.minecraft.server.command.CommandManager.*;

public final class PonyNpcCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/Command");

    private static final SuggestionProvider<ServerCommandSource> DIALOG_SUGGESTIONS = (ctx, builder) -> {
        DialogManager.getInstance().getDialogIds().stream()
            .filter(id -> id.startsWith(builder.getRemaining()))
            .forEach(builder::suggest);
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> SKIN_SUGGESTIONS = (ctx, builder) -> {
        PonyNpcMod.BUILTIN_SKIN_NAMES.stream()
            .filter(n -> n.startsWith(builder.getRemaining()))
            .forEach(builder::suggest);
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> QUEST_SUGGESTIONS = (ctx, builder) -> {
        QuestManager.getInstance().getQuestIds().stream()
            .filter(id -> id.startsWith(builder.getRemaining()))
            .forEach(builder::suggest);
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> STAGE_SUGGESTIONS = (ctx, builder) -> {
        try {
            String questId = StringArgumentType.getString(ctx, "quest_id");
            QuestManager.getInstance().getQuest(questId).ifPresent(def ->
                def.stages.keySet().stream()
                    .filter(id -> id.startsWith(builder.getRemaining()))
                    .forEach(builder::suggest)
            );
        } catch (Exception ignored) {}
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> DUNGEON_SUGGESTIONS = (ctx, builder) -> {
        DungeonRegistry.getInstance().getDungeonIds().stream()
            .filter(id -> id.startsWith(builder.getRemaining()))
            .forEach(builder::suggest);
        return builder.buildFuture();
    };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            register(dispatcher, registryAccess)
        );
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                  CommandRegistryAccess registryAccess) {
        dispatcher.register(literal("ponynpc")
            .requires(src -> src.hasPermissionLevel(2))

            // ── NPC spawn / remove ──────────────────────────────
            .then(literal("spawn")
                .then(argument("dialog_id", StringArgumentType.string())
                    .suggests(DIALOG_SUGGESTIONS)
                    .executes(ctx -> spawnNpc(ctx,
                        StringArgumentType.getString(ctx, "dialog_id"), "", "", false))
                    .then(argument("skin", StringArgumentType.string())
                        .suggests(SKIN_SUGGESTIONS)
                        .executes(ctx -> spawnNpc(ctx,
                            StringArgumentType.getString(ctx, "dialog_id"),
                            StringArgumentType.getString(ctx, "skin"), "", false))
                        .then(literal("stationary")
                            .executes(ctx -> spawnNpc(ctx,
                                StringArgumentType.getString(ctx, "dialog_id"),
                                StringArgumentType.getString(ctx, "skin"), "", true))
                            .then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> spawnNpc(ctx,
                                    StringArgumentType.getString(ctx, "dialog_id"),
                                    StringArgumentType.getString(ctx, "skin"),
                                    StringArgumentType.getString(ctx, "name"), true))))
                        .then(argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> spawnNpc(ctx,
                                StringArgumentType.getString(ctx, "dialog_id"),
                                StringArgumentType.getString(ctx, "skin"),
                                StringArgumentType.getString(ctx, "name"), false))))))

            // ── Marker spawn (item-rendered, persistent) ────────────
            .then(literal("spawn_marker")
                .then(argument("dialog_id", StringArgumentType.string())
                    .suggests(DIALOG_SUGGESTIONS)
                    .then(argument("item_id", StringArgumentType.word())
                        .executes(ctx -> spawnMarker(ctx,
                            StringArgumentType.getString(ctx, "dialog_id"),
                            StringArgumentType.getString(ctx, "item_id"),
                            ""))
                        .then(argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> spawnMarker(ctx,
                                StringArgumentType.getString(ctx, "dialog_id"),
                                StringArgumentType.getString(ctx, "item_id"),
                                StringArgumentType.getString(ctx, "name")))))))

            .then(literal("remove").executes(PonyNpcCommand::removeNpc))

            // ── Reload (dialogs + quests + dungeons) ─────────────
            .then(literal("reload").executes(ctx -> {
                int d = DialogManager.getInstance().reload();
                int q = QuestManager.getInstance().reload();
                int u = DungeonRegistry.getInstance().reload();
                ctx.getSource().sendFeedback(
                    () -> Text.literal("[PonyNPC] Reloaded " + d + " dialog(s), " + q + " quest(s), " + u + " dungeon(s)"),
                    true
                );
                return d + q + u;
            }))

            .then(literal("list").executes(PonyNpcCommand::listDialogs))

            // ── Quest management ─────────────────────────────────
            .then(literal("quest")
                .then(literal("list").executes(PonyNpcCommand::listQuests))

                .then(literal("start")
                    .then(argument("quest_id", StringArgumentType.string())
                        .suggests(QUEST_SUGGESTIONS)
                        .executes(ctx -> questStart(ctx,
                            ctx.getSource().getPlayerOrThrow(),
                            StringArgumentType.getString(ctx, "quest_id")))
                        .then(argument("target", EntityArgumentType.player())
                            .executes(ctx -> questStart(ctx,
                                EntityArgumentType.getPlayer(ctx, "target"),
                                StringArgumentType.getString(ctx, "quest_id"))))))

                .then(literal("status")
                    .then(argument("quest_id", StringArgumentType.string())
                        .suggests(QUEST_SUGGESTIONS)
                        .executes(ctx -> questStatus(ctx,
                            ctx.getSource().getPlayerOrThrow(),
                            StringArgumentType.getString(ctx, "quest_id")))
                        .then(argument("target", EntityArgumentType.player())
                            .executes(ctx -> questStatus(ctx,
                                EntityArgumentType.getPlayer(ctx, "target"),
                                StringArgumentType.getString(ctx, "quest_id"))))))

                .then(literal("advance")
                    .then(argument("quest_id", StringArgumentType.string())
                        .suggests(QUEST_SUGGESTIONS)
                        .executes(ctx -> questAdvance(ctx,
                            ctx.getSource().getPlayerOrThrow(),
                            StringArgumentType.getString(ctx, "quest_id")))
                        .then(argument("target", EntityArgumentType.player())
                            .executes(ctx -> questAdvance(ctx,
                                EntityArgumentType.getPlayer(ctx, "target"),
                                StringArgumentType.getString(ctx, "quest_id"))))))

                .then(literal("reset")
                    .then(argument("quest_id", StringArgumentType.string())
                        .suggests(QUEST_SUGGESTIONS)
                        .executes(ctx -> questReset(ctx,
                            ctx.getSource().getPlayerOrThrow(),
                            StringArgumentType.getString(ctx, "quest_id")))
                        .then(argument("target", EntityArgumentType.player())
                            .executes(ctx -> questReset(ctx,
                                EntityArgumentType.getPlayer(ctx, "target"),
                                StringArgumentType.getString(ctx, "quest_id")))))))

            // ── Trigger position ──────────────────────────────────
            .then(literal("trigger")
                .then(literal("set")
                    .then(argument("quest_id", StringArgumentType.word())
                        .suggests(QUEST_SUGGESTIONS)
                        .then(argument("stage_id", StringArgumentType.word())
                            .suggests(STAGE_SUGGESTIONS)
                            .executes(ctx -> triggerSet(ctx,
                                StringArgumentType.getString(ctx, "quest_id"),
                                StringArgumentType.getString(ctx, "stage_id"),
                                -1))
                            .then(argument("radius", DoubleArgumentType.doubleArg(0.5))
                                .executes(ctx -> triggerSet(ctx,
                                    StringArgumentType.getString(ctx, "quest_id"),
                                    StringArgumentType.getString(ctx, "stage_id"),
                                    DoubleArgumentType.getDouble(ctx, "radius"))))))))

            // ── Quest pocket ─────────────────────────────────────
            .then(literal("pocket")
                .executes(ctx -> showPocket(ctx, ctx.getSource().getPlayerOrThrow()))
                .then(argument("target", EntityArgumentType.player())
                    .executes(ctx -> showPocket(ctx, EntityArgumentType.getPlayer(ctx, "target"))))
                .then(literal("give")
                    .then(argument("item_id", StringArgumentType.word())
                        .executes(ctx -> pocketGive(ctx,
                            ctx.getSource().getPlayerOrThrow(),
                            StringArgumentType.getString(ctx, "item_id"), 1))
                        .then(argument("target", EntityArgumentType.player())
                            .executes(ctx -> pocketGive(ctx,
                                EntityArgumentType.getPlayer(ctx, "target"),
                                StringArgumentType.getString(ctx, "item_id"), 1)))))
                .then(literal("take")
                    .then(argument("item_id", StringArgumentType.word())
                        .executes(ctx -> pocketTake(ctx,
                            ctx.getSource().getPlayerOrThrow(),
                            StringArgumentType.getString(ctx, "item_id")))
                        .then(argument("target", EntityArgumentType.player())
                            .executes(ctx -> pocketTake(ctx,
                                EntityArgumentType.getPlayer(ctx, "target"),
                                StringArgumentType.getString(ctx, "item_id")))))))

            // ── Dungeon management (admin) ────────────────────────
            .then(literal("dungeon")
                .then(literal("list").executes(ctx -> {
                    var ids = DungeonRegistry.getInstance().getDungeonIds();
                    if (ids.isEmpty()) {
                        ctx.getSource().sendFeedback(
                            () -> Text.literal("[PonyNPC] No dungeons loaded."), false);
                        return 0;
                    }
                    ctx.getSource().sendFeedback(
                        () -> Text.literal("[PonyNPC] " + ids.size() + " dungeon(s): " + String.join(", ", ids)), false);
                    return ids.size();
                }))
                .then(literal("enter")
                    .then(argument("dungeon_id", StringArgumentType.word())
                        .suggests(DUNGEON_SUGGESTIONS)
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            String id = StringArgumentType.getString(ctx, "dungeon_id");
                            DungeonInstanceManager.getInstance().enterDungeon(player, id, ctx.getSource().getServer());
                            return 1;
                        })
                        .then(argument("target", EntityArgumentType.player())
                            .executes(ctx -> {
                                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                String id = StringArgumentType.getString(ctx, "dungeon_id");
                                DungeonInstanceManager.getInstance().enterDungeon(target, id, ctx.getSource().getServer());
                                return 1;
                            }))))
                .then(literal("exit")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        DungeonInstanceManager.getInstance().exitDungeon(player, ctx.getSource().getServer());
                        return 1;
                    })
                    .then(argument("target", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                            DungeonInstanceManager.getInstance().exitDungeon(target, ctx.getSource().getServer());
                            return 1;
                        }))))
        );
    }

    // ── NPC spawn / remove ────────────────────────────────────────

    private static int spawnNpc(CommandContext<ServerCommandSource> ctx,
                                 String dialogId, String skin, String name, boolean stationary) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            var world = player.getServerWorld();

            PonyNpcEntity npc = PonyNpcEntities.PONY_NPC.create(world);
            if (npc == null) { ctx.getSource().sendError(Text.literal("[PonyNPC] Failed to create entity")); return 0; }

            double yawRad = Math.toRadians(player.getYaw());
            npc.refreshPositionAndAngles(
                player.getX() - Math.sin(yawRad),
                player.getY(),
                player.getZ() + Math.cos(yawRad),
                player.getYaw() + 180, 0
            );

            npc.initialize(world, world.getLocalDifficulty(npc.getBlockPos()), SpawnReason.COMMAND, null);
            npc.setPersistent();
            npc.setDialogId(dialogId);
            if (!skin.isEmpty()) npc.setSkinId(skin);
            npc.setNpcDisplayName(name.isEmpty() ? dialogId : name);
            npc.setCustomNameVisible(true);
            npc.setStationary(stationary);

            world.getServer().execute(() -> world.spawnEntityAndPassengers(npc));

            ctx.getSource().sendFeedback(
                () -> Text.literal("[PonyNPC] Spawned NPC: dialog=" + dialogId + (stationary ? " [stationary]" : "")),
                true
            );
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("[PonyNPC] " + e.getMessage()));
            return 0;
        }
    }

    private static int spawnMarker(CommandContext<ServerCommandSource> ctx,
                                    String dialogId, String itemId, String name) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            var world = player.getServerWorld();

            QuestMarkerEntity marker = PonyNpcEntities.QUEST_MARKER.create(world);
            if (marker == null) { ctx.getSource().sendError(Text.literal("[PonyNPC] Failed to create marker")); return 0; }

            ItemStack displayItem = ItemStack.EMPTY;
            Identifier id = Identifier.tryParse(itemId);
            if (id != null) {
                var item = Registries.ITEM.get(id);
                displayItem = new ItemStack(item);
            }

            String displayName = name.isEmpty() ? dialogId : name;
            marker.setupPermanent(displayName, dialogId, displayItem);
            marker.setCustomNameVisible(true);
            marker.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), 0, 0);

            world.getServer().execute(() -> world.spawnEntityAndPassengers(marker));

            ctx.getSource().sendFeedback(
                () -> Text.literal("[PonyNPC] Spawned marker: dialog=" + dialogId + " item=" + itemId),
                true
            );
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("[PonyNPC] " + e.getMessage()));
            return 0;
        }
    }

    private static int removeNpc(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            double dist = 5.0;
            Vec3d start = player.getCameraPosVec(1.0F);
            Vec3d dir   = player.getRotationVec(1.0F);
            Vec3d end   = start.add(dir.multiply(dist));
            Box box = player.getBoundingBox().stretch(dir.multiply(dist)).expand(1.0);

            EntityHitResult hit = ProjectileUtil.raycast(
                player, start, end, box, e -> e instanceof PonyNpcEntity, dist * dist);

            if (hit != null && hit.getEntity() instanceof PonyNpcEntity npc) {
                npc.discard();
                ctx.getSource().sendFeedback(() -> Text.literal("[PonyNPC] NPC removed"), true);
                return 1;
            }
            ctx.getSource().sendError(Text.literal("[PonyNPC] Look at a Pony NPC to remove it"));
            return 0;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("[PonyNPC] " + e.getMessage()));
            return 0;
        }
    }

    // ── Dialog listing ────────────────────────────────────────────

    private static int listDialogs(CommandContext<ServerCommandSource> ctx) {
        var byMode = DialogManager.getInstance().getDialogIdsByMode();
        if (byMode.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("[PonyNPC] No dialogs loaded."), false);
            return 0;
        }
        int total = byMode.values().stream().mapToInt(List::size).sum();
        ctx.getSource().sendFeedback(() -> Text.literal("[PonyNPC] " + total + " dialog(s):"), false);
        byMode.forEach((mode, ids) ->
            ctx.getSource().sendFeedback(
                () -> Text.literal("  [" + mode.name().toLowerCase() + "] " + String.join(", ", ids)), false)
        );
        return total;
    }

    // ── Quest commands ────────────────────────────────────────────

    private static int listQuests(CommandContext<ServerCommandSource> ctx) {
        Set<String> ids = QuestManager.getInstance().getQuestIds();
        if (ids.isEmpty()) {
            ctx.getSource().sendFeedback(
                () -> Text.literal("[PonyNPC] No quests loaded. Place JSON in config/ponynpc/quests/"), false);
            return 0;
        }
        ctx.getSource().sendFeedback(
            () -> Text.literal("[PonyNPC] " + ids.size() + " quest(s): " + String.join(", ", ids)), false);
        return ids.size();
    }

    private static int questStart(CommandContext<ServerCommandSource> ctx,
                                   ServerPlayerEntity target, String questId) {
        QuestManager qm = QuestManager.getInstance();
        if (qm.getQuest(questId).isEmpty()) {
            ctx.getSource().sendError(Text.literal("[PonyNPC] Quest not found: " + questId));
            return 0;
        }
        qm.startQuest(target, questId);
        ctx.getSource().sendFeedback(
            () -> Text.literal("[PonyNPC] Quest '" + questId + "' started for " + target.getName().getString()),
            true
        );
        return 1;
    }

    private static int questStatus(CommandContext<ServerCommandSource> ctx,
                                    ServerPlayerEntity target, String questId) {
        QuestManager qm = QuestManager.getInstance();
        String playerName = target.getName().getString();
        if (!qm.isQuestStarted(target, questId)) {
            ctx.getSource().sendFeedback(() -> Text.literal("[PonyNPC] " + playerName + " has not started '" + questId + "'"), false);
            return 0;
        }
        if (qm.isQuestComplete(target, questId)) {
            ctx.getSource().sendFeedback(() -> Text.literal("[PonyNPC] " + playerName + ": quest '" + questId + "' — COMPLETE"), false);
            return 1;
        }
        String stage = qm.getCurrentStage(target, questId).orElse("?");
        ctx.getSource().sendFeedback(
            () -> Text.literal("[PonyNPC] " + playerName + ": quest '" + questId + "' — stage: " + stage), false);
        return 1;
    }

    private static int questAdvance(CommandContext<ServerCommandSource> ctx,
                                     ServerPlayerEntity target, String questId) {
        QuestManager qm = QuestManager.getInstance();
        if (!qm.isQuestStarted(target, questId)) {
            ctx.getSource().sendError(Text.literal("[PonyNPC] Player has not started this quest"));
            return 0;
        }
        qm.completeCurrentStage(target, questId);
        String stage = qm.getCurrentStage(target, questId).orElse("COMPLETE");
        ctx.getSource().sendFeedback(
            () -> Text.literal("[PonyNPC] Advanced quest '" + questId + "' — now on: " + stage), true);
        return 1;
    }

    private static int questReset(CommandContext<ServerCommandSource> ctx,
                                   ServerPlayerEntity target, String questId) {
        QuestStateStore.getOrCreate(target.getServer()).resetQuest(target.getUuid(), questId);
        ProximityTriggerManager.getInstance().clearForQuest(target.getUuid(), questId);
        GhostNpcManager.getInstance().despawnAll(target.getUuid());
        ctx.getSource().sendFeedback(
            () -> Text.literal("[PonyNPC] Quest '" + questId + "' reset for " + target.getName().getString()),
            true
        );
        return 1;
    }

    // ── Quest pocket ──────────────────────────────────────────────

    private static int showPocket(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target) {
        var pocket = QuestPocketState.getOrCreate(target.getServer()).getPocket(target.getUuid());
        String name = target.getName().getString();
        if (pocket.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("[PonyNPC] " + name + "'s quest pocket is empty"), false);
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("[PonyNPC] " + name + "'s quest pocket:"), false);
        pocket.forEach((itemId, count) ->
            ctx.getSource().sendFeedback(() -> Text.literal("  - " + itemId + " x" + count), false)
        );
        return pocket.size();
    }

    private static int pocketGive(CommandContext<ServerCommandSource> ctx,
                                   ServerPlayerEntity target, String itemId, int count) {
        QuestPocketState.getOrCreate(target.getServer()).giveItem(target.getUuid(), itemId, count);
        ctx.getSource().sendFeedback(
            () -> Text.literal("[PonyNPC] Gave quest item '" + itemId + "' to " + target.getName().getString()),
            true
        );
        return 1;
    }

    private static int pocketTake(CommandContext<ServerCommandSource> ctx,
                                   ServerPlayerEntity target, String itemId) {
        boolean ok = QuestPocketState.getOrCreate(target.getServer()).takeItem(target.getUuid(), itemId);
        if (ok) {
            ctx.getSource().sendFeedback(
                () -> Text.literal("[PonyNPC] Took quest item '" + itemId + "' from " + target.getName().getString()),
                true
            );
            return 1;
        }
        ctx.getSource().sendError(Text.literal("[PonyNPC] Player doesn't have '" + itemId + "'"));
        return 0;
    }

    // ── Trigger position ──────────────────────────────────────────

    /**
     * Записывает текущую позицию игрока как центр proximity_trigger для указанного этапа.
     * Читает и перезаписывает JSON в config/ponynpc/quests/<questId>.json.
     * radius=-1 означает "не менять радиус".
     */
    private static int triggerSet(CommandContext<ServerCommandSource> ctx,
                                   String questId, String stageId, double radius) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

            if (QuestManager.getInstance().getQuest(questId).isEmpty()) {
                ctx.getSource().sendError(Text.literal("[PonyNPC] Quest not found: " + questId));
                return 0;
            }
            if (QuestManager.getInstance().getQuest(questId).get().getStage(stageId).isEmpty()) {
                ctx.getSource().sendError(Text.literal("[PonyNPC] Stage not found: " + stageId));
                return 0;
            }

            Path questFile = FabricLoader.getInstance().getConfigDir()
                .resolve("ponynpc").resolve("quests").resolve(questId + ".json");
            if (!Files.exists(questFile)) {
                ctx.getSource().sendError(Text.literal("[PonyNPC] Quest file not found: " + questFile));
                return 0;
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject root;
            try (Reader reader = Files.newBufferedReader(questFile)) {
                root = gson.fromJson(reader, JsonObject.class);
            }

            JsonObject stages = root.getAsJsonObject("stages");
            if (stages == null || !stages.has(stageId)) {
                ctx.getSource().sendError(Text.literal("[PonyNPC] Stage '" + stageId + "' not in JSON"));
                return 0;
            }

            JsonObject stage = stages.getAsJsonObject(stageId);
            JsonObject trigger = stage.has("proximity_trigger")
                ? stage.getAsJsonObject("proximity_trigger")
                : new JsonObject();

            String dim = player.getServerWorld().getRegistryKey().getValue().toString();
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();

            trigger.addProperty("dimension", dim);
            trigger.addProperty("x", Math.round(x * 10.0) / 10.0);
            trigger.addProperty("y", Math.round(y * 10.0) / 10.0);
            trigger.addProperty("z", Math.round(z * 10.0) / 10.0);
            if (radius > 0) trigger.addProperty("radius", radius);

            stage.add("proximity_trigger", trigger);
            Files.writeString(questFile, gson.toJson(root));

            QuestManager.getInstance().reload();

            double fx = x, fy = y, fz = z;
            ctx.getSource().sendFeedback(() -> Text.literal(
                "[PonyNPC] Trigger set for " + questId + "/" + stageId
                + " → " + dim + " (%.1f, %.1f, %.1f)".formatted(fx, fy, fz)
                + (radius > 0 ? " r=" + radius : "")),
                true);
            return 1;

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("[PonyNPC] Error: " + e.getMessage()));
            LOGGER.error("triggerSet failed", e);
            return 0;
        }
    }
}
