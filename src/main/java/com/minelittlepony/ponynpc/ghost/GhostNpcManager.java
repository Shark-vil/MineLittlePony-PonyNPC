package com.minelittlepony.ponynpc.ghost;

import com.minelittlepony.ponynpc.entity.PonyNpcEntity;
import com.minelittlepony.ponynpc.network.NetworkHandler;
import com.minelittlepony.ponynpc.quest.GhostNpcSpawnConfig;
import com.minelittlepony.ponynpc.registry.PonyNpcEntities;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Управляет ghost NPC — NPC, видимыми только для конкретных игроков.
 * Ghost NPC создаются/уничтожаются программно в рамках квестов.
 */
public final class GhostNpcManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/GhostNpc");
    private static final GhostNpcManager INSTANCE = new GhostNpcManager();

    // playerUUID → список ghost NPC спавненных для этого игрока
    private final Map<UUID, List<PonyNpcEntity>> playerGhosts = new HashMap<>();

    private GhostNpcManager() {}

    public static GhostNpcManager getInstance() { return INSTANCE; }

    /**
     * Спавнит ghost NPC в мире, видимый только для указанного игрока.
     * NPC спаунится там где стоит игрок (можно переопределить через config).
     * @param config  конфигурация из квестового JSON
     * @return спавненный ghost NPC, или null если не удалось
     */
    public PonyNpcEntity spawnGhostNpc(ServerPlayerEntity player, ServerWorld world,
                                       GhostNpcSpawnConfig config) {
        PonyNpcEntity npc = PonyNpcEntities.PONY_NPC.create(world);
        if (npc == null) { LOGGER.error("Failed to create ghost NPC entity"); return null; }

        // Позиция: перед игроком
        double yawRad = Math.toRadians(player.getYaw());
        double spawnX = player.getX() - Math.sin(yawRad) * 2;
        double spawnZ = player.getZ() + Math.cos(yawRad) * 2;
        npc.refreshPositionAndAngles(spawnX, player.getY(), spawnZ, player.getYaw() + 180, 0);

        npc.initialize(world, world.getLocalDifficulty(npc.getBlockPos()), SpawnReason.COMMAND, null);
        npc.setPersistent();

        npc.setDialogId(config.dialogId);
        if (config.skin != null && !config.skin.isEmpty()) npc.setSkinId(config.skin);
        npc.setNpcDisplayName(config.name);
        npc.setStationary(true);

        // Пометить как ghost NPC
        npc.setGhostNpc(true);
        npc.addVisibleTo(player.getUuid());
        npc.setDespawnAfterDialog(config.despawnAfterDialog);

        world.spawnEntityAndPassengers(npc);

        playerGhosts.computeIfAbsent(player.getUuid(), u -> new ArrayList<>()).add(npc);
        LOGGER.debug("Spawned ghost NPC '{}' for player '{}'", config.name, player.getName().getString());

        // Авто-диалог
        if (config.autoDialog && !config.dialogId.isEmpty()) {
            player.getServer().execute(() -> NetworkHandler.openDialogForPlayer(player, npc));
        }

        return npc;
    }

    /** Удалить конкретный ghost NPC для игрока. */
    public void despawnGhostNpc(UUID playerUuid, PonyNpcEntity npc) {
        List<PonyNpcEntity> ghosts = playerGhosts.get(playerUuid);
        if (ghosts != null) ghosts.remove(npc);
        if (!npc.isRemoved()) npc.discard();
    }

    /** Удалить все ghost NPC данного игрока (при логауте и т.д.). */
    public void despawnAll(UUID playerUuid) {
        List<PonyNpcEntity> ghosts = playerGhosts.remove(playerUuid);
        if (ghosts == null) return;
        for (PonyNpcEntity npc : ghosts) {
            if (!npc.isRemoved()) npc.discard();
        }
        LOGGER.debug("Despawned all ghost NPCs for player {}", playerUuid);
    }

    /** Вызывается из NetworkHandler когда диалог закрывается. */
    public void onDialogClosed(UUID playerUuid, PonyNpcEntity npc) {
        if (npc.isGhostNpc() && npc.isDespawnAfterDialog()) {
            despawnGhostNpc(playerUuid, npc);
        }
    }
}
