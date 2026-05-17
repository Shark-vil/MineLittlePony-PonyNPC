package com.minelittlepony.ponynpc.quest;

import com.minelittlepony.ponynpc.entity.VirtualDialogContext;
import com.minelittlepony.ponynpc.network.NetworkHandler;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обрабатывает ПКМ по блокам в зарегистрированных block_zones.
 * При совпадении условия/региона/типа открывает диалог без спауна сущностей.
 */
public final class BlockInteractionManager {

    private static final BlockInteractionManager INSTANCE = new BlockInteractionManager();

    /** playerUuid → (contextUuid → VirtualDialogContext) */
    private final Map<UUID, Map<UUID, VirtualDialogContext>> activeContexts = new ConcurrentHashMap<>();

    private BlockInteractionManager() {}

    public static BlockInteractionManager getInstance() { return INSTANCE; }

    public void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            Block clickedBlock = world.getBlockState(pos).getBlock();

            for (BlockZoneDefinition zone : BlockZoneRegistry.getInstance().getZones()) {
                if (!zone.isInRegion(pos, world.getRegistryKey())) continue;

                Identifier targetId = Identifier.tryParse(zone.target_block);
                if (targetId == null) continue;
                if (!Registries.BLOCK.get(targetId).equals(clickedBlock)) continue;

                if (!zone.condition.isEmpty()
                        && !QuestConditionChecker.check(sp, zone.condition)) continue;

                String dialogId = zone.getDialogFor(pos);
                if (dialogId.isEmpty()) continue;
                String name = zone.getNameFor(pos);

                UUID ctxUuid = VirtualDialogContext.blockUuid(pos);
                VirtualDialogContext ctx = new VirtualDialogContext(ctxUuid, dialogId, name);
                activeContexts.computeIfAbsent(sp.getUuid(), k -> new ConcurrentHashMap<>())
                    .put(ctxUuid, ctx);

                NetworkHandler.openDialogForPlayer(sp, ctx);
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });
    }

    public Optional<VirtualDialogContext> getContext(UUID playerUuid, UUID contextUuid) {
        Map<UUID, VirtualDialogContext> map = activeContexts.get(playerUuid);
        if (map == null) return Optional.empty();
        return Optional.ofNullable(map.get(contextUuid));
    }

    public void removeContext(UUID playerUuid, UUID contextUuid) {
        Map<UUID, VirtualDialogContext> map = activeContexts.get(playerUuid);
        if (map != null) map.remove(contextUuid);
    }

    public void clearForPlayer(UUID playerUuid) {
        activeContexts.remove(playerUuid);
    }
}
