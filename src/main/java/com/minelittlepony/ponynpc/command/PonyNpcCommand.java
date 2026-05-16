package com.minelittlepony.ponynpc.command;

import com.minelittlepony.ponynpc.PonyNpcMod;
import com.minelittlepony.ponynpc.dialog.DialogManager;
import com.minelittlepony.ponynpc.entity.PonyNpcEntity;
import com.minelittlepony.ponynpc.registry.PonyNpcEntities;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static net.minecraft.server.command.CommandManager.*;

public final class PonyNpcCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/Command");

    private static final SuggestionProvider<ServerCommandSource> DIALOG_SUGGESTIONS = (ctx, builder) -> {
        Set<String> ids = DialogManager.getInstance().getDialogIds();
        ids.stream()
            .filter(id -> id.startsWith(builder.getRemaining()))
            .forEach(builder::suggest);
        return builder.buildFuture();
    };

    /** Suggests built-in skin short names (e.g. "applejack") discovered at startup. */
    private static final SuggestionProvider<ServerCommandSource> SKIN_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemaining();
        PonyNpcMod.BUILTIN_SKIN_NAMES.stream()
            .filter(name -> name.startsWith(remaining))
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
            .requires(source -> source.hasPermissionLevel(2))

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

                        // stationary flag as literal keyword (before name, so name can remain greedy)
                        .then(literal("stationary")
                            .executes(ctx -> spawnNpc(ctx,
                                StringArgumentType.getString(ctx, "dialog_id"),
                                StringArgumentType.getString(ctx, "skin"), "", true))
                            .then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> spawnNpc(ctx,
                                    StringArgumentType.getString(ctx, "dialog_id"),
                                    StringArgumentType.getString(ctx, "skin"),
                                    StringArgumentType.getString(ctx, "name"), true)))
                        )

                        .then(argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> spawnNpc(ctx,
                                StringArgumentType.getString(ctx, "dialog_id"),
                                StringArgumentType.getString(ctx, "skin"),
                                StringArgumentType.getString(ctx, "name"), false)))
                    )
                )
            )

            .then(literal("remove")
                .executes(PonyNpcCommand::removeNpc))

            .then(literal("reload")
                .executes(PonyNpcCommand::reloadDialogs))

            .then(literal("list")
                .executes(PonyNpcCommand::listDialogs))
        );
    }

    private static int spawnNpc(CommandContext<ServerCommandSource> ctx,
                                 String dialogId, String skin, String name, boolean stationary) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            var world = player.getServerWorld();

            PonyNpcEntity npc = PonyNpcEntities.PONY_NPC.create(world);
            if (npc == null) {
                ctx.getSource().sendError(Text.literal("[PonyNPC] Failed to create entity"));
                return 0;
            }

            double yawRad = Math.toRadians(player.getYaw());
            double spawnX = player.getX() - Math.sin(yawRad);
            double spawnZ = player.getZ() + Math.cos(yawRad);
            npc.refreshPositionAndAngles(spawnX, player.getY(), spawnZ, player.getYaw() + 180, 0);

            npc.initialize(world, world.getLocalDifficulty(npc.getBlockPos()), SpawnReason.COMMAND, null);
            npc.setPersistent();

            npc.setDialogId(dialogId);
            if (!skin.isEmpty()) npc.setSkinId(skin);
            npc.setNpcDisplayName(name.isEmpty() ? dialogId : name);
            npc.setCustomNameVisible(true);
            npc.setStationary(stationary);

            world.getServer().execute(() -> {
                world.spawnEntityAndPassengers(npc);
                LOGGER.info("[PonyNPC] Spawned id={} stationary={}", npc.getId(), stationary);
            });

            ctx.getSource().sendFeedback(
                () -> Text.literal("[PonyNPC] Spawned NPC: dialog=" + dialogId
                    + (stationary ? " [stationary]" : "")),
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

            double distance = 5.0;
            Vec3d start = player.getCameraPosVec(1.0F);
            Vec3d dir   = player.getRotationVec(1.0F);
            Vec3d end   = start.add(dir.multiply(distance));
            Box searchBox = player.getBoundingBox().stretch(dir.multiply(distance)).expand(1.0);

            EntityHitResult hit = ProjectileUtil.raycast(
                player, start, end, searchBox,
                e -> e instanceof PonyNpcEntity,
                distance * distance
            );

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

    private static int reloadDialogs(CommandContext<ServerCommandSource> ctx) {
        int count = DialogManager.getInstance().reload();
        ctx.getSource().sendFeedback(
            () -> Text.literal("[PonyNPC] Reloaded " + count + " dialog(s)"),
            true
        );
        return count;
    }

    private static int listDialogs(CommandContext<ServerCommandSource> ctx) {
        var byMode = DialogManager.getInstance().getDialogIdsByMode();
        if (byMode.isEmpty()) {
            ctx.getSource().sendFeedback(
                () -> Text.literal("[PonyNPC] No dialogs loaded. Place JSON files in config/ponynpc/dialogs/"),
                false
            );
            return 0;
        }
        int total = byMode.values().stream().mapToInt(java.util.List::size).sum();
        ctx.getSource().sendFeedback(() -> Text.literal("[PonyNPC] " + total + " dialog(s) loaded:"), false);
        byMode.forEach((mode, ids) ->
            ctx.getSource().sendFeedback(
                () -> Text.literal("  [" + mode.name().toLowerCase() + "] " + String.join(", ", ids)),
                false
            )
        );
        return total;
    }
}
