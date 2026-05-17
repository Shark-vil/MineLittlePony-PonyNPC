package com.minelittlepony.ponynpc.trigger;

import com.minelittlepony.ponynpc.entity.PonyNpcEntity;
import com.minelittlepony.ponynpc.entity.QuestMarkerEntity;
import com.minelittlepony.ponynpc.ghost.GhostNpcManager;
import com.minelittlepony.ponynpc.quest.*;
import com.minelittlepony.ponynpc.registry.PonyNpcEntities;
import com.minelittlepony.ponynpc.state.PlayerQuestState;
import com.minelittlepony.ponynpc.state.QuestStateStore;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Проверяет proximity-триггеры квестов каждые 20 тиков.
 *
 * Три режима триггера:
 * - ghost_npc: спаунит пони-NPC для игрока (спавн + действия выполняются немедленно)
 * - spawn_marker: спаунит кликабельный QuestMarkerEntity (действия при клике)
 * - auto_complete: выполняет actions немедленно без сущности
 *
 * Логика повторного срабатывания:
 * - Триггер не срабатывает повторно пока spawned entity жив.
 * - Когда игрок покидает радиус, а entity уже нет → триггер сбрасывается.
 * - Следующий вход в радиус снова спаунит entity / выполняет actions.
 */
public final class ProximityTriggerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/ProximityTrigger");
    private static final int CHECK_INTERVAL = 20;
    private static final ProximityTriggerManager INSTANCE = new ProximityTriggerManager();

    // fireKey = playerUuid:questId:stageId
    private final Set<String>          firedTriggers   = new HashSet<>();
    private final Set<String>          playersInRange  = new HashSet<>();
    // Entity spawned by a trigger (null if auto_complete or no entity configured)
    private final Map<String, Entity>  triggerEntities = new HashMap<>();

    private int tickCounter = 0;

    private ProximityTriggerManager() {}

    public static ProximityTriggerManager getInstance() { return INSTANCE; }

    public void tick(MinecraftServer server) {
        if (++tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        QuestManager qm = QuestManager.getInstance();
        QuestStateStore store = QuestStateStore.getOrCreate(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerUuid = player.getUuid();

            for (String questId : qm.getQuestIds()) {
                Optional<String> stageIdOpt = store.getCurrentStage(playerUuid, questId);
                if (stageIdOpt.isEmpty()) continue;
                String stageId = stageIdOpt.get();
                if (stageId == null || stageId.equals(PlayerQuestState.COMPLETED)) continue;

                qm.getQuest(questId).flatMap(def -> def.getStage(stageId)).ifPresent(stage -> {
                    ProximityTriggerConfig trigger = stage.proximityTrigger;
                    if (trigger == null) return;

                    String fireKey = playerUuid + ":" + questId + ":" + stageId;
                    boolean inRange = isPlayerInRange(player, server, trigger);

                    if (inRange) {
                        playersInRange.add(fireKey);

                        if (firedTriggers.contains(fireKey)) {
                            // Already fired — re-fire only if the spawned entity is gone
                            Entity entity = triggerEntities.get(fireKey);
                            if (entity != null && !entity.isRemoved()) return; // Entity still alive
                            // Entity gone but player still in range → respawn
                            firedTriggers.remove(fireKey);
                            triggerEntities.remove(fireKey);
                        }

                        firedTriggers.add(fireKey);
                        Entity spawned = fireTrigger(player, server, questId, stageId, trigger);
                        triggerEntities.put(fireKey, spawned); // null for auto_complete

                    } else {
                        // Player left the radius
                        if (playersInRange.remove(fireKey)) {
                            Entity entity = triggerEntities.get(fireKey);
                            if (entity == null || entity.isRemoved()) {
                                firedTriggers.remove(fireKey);
                                triggerEntities.remove(fireKey);
                            }
                            // Ghost NPCs stay alive past radius edge; markers and auto reset immediately
                        }
                    }
                });
            }
        }

        cleanupFiredTriggers(server, store);
    }

    private boolean isPlayerInRange(ServerPlayerEntity player, MinecraftServer server,
                                    ProximityTriggerConfig trigger) {
        Identifier dimId = Identifier.tryParse(trigger.dimension);
        if (dimId == null) return false;
        RegistryKey<World> dimKey = RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, dimId);
        ServerWorld triggerWorld = server.getWorld(dimKey);
        if (triggerWorld == null) return false;
        if (!player.getWorld().equals(triggerWorld)) return false;

        Vec3d pos = player.getPos();
        double dx = pos.x - trigger.x;
        double dy = pos.y - trigger.y;
        double dz = pos.z - trigger.z;
        return (dx * dx + dy * dy + dz * dz) <= trigger.radius * trigger.radius;
    }

    /**
     * Fires the trigger according to its mode and returns the spawned entity (or null).
     * Modes:
     *   auto_complete  → execute actions immediately, return null
     *   spawn_marker   → spawn QuestMarkerEntity, return it
     *   ghost_npc      → spawn ghost NPC + execute trigger.actions, return NPC
     */
    private Entity fireTrigger(ServerPlayerEntity player, MinecraftServer server,
                               String questId, String stageId,
                               ProximityTriggerConfig trigger) {
        LOGGER.info("Proximity trigger fired: quest='{}' stage='{}' player='{}' mode='{}'",
            questId, stageId, player.getName().getString(),
            trigger.autoComplete ? "auto_complete" : trigger.spawnMarker != null ? "spawn_marker" : "ghost_npc");

        if (trigger.autoComplete) {
            QuestActionExecutor.executeAll(player, null, trigger.actions);
            return null;
        }

        if (trigger.spawnMarker != null) {
            ServerWorld world = player.getServerWorld();
            QuestMarkerEntity marker = PonyNpcEntities.QUEST_MARKER.create(world);
            if (marker != null) {
                marker.setup(trigger.spawnMarker.name, trigger.spawnMarker.actions, player.getUuid());
                marker.refreshPositionAndAngles(trigger.x, trigger.y, trigger.z, 0, 0);
                world.spawnEntity(marker);
                return marker;
            }
            return null;
        }

        // Ghost NPC mode
        ServerWorld world = player.getServerWorld();
        PonyNpcEntity spawned = null;
        if (trigger.spawnGhostNpc != null) {
            spawned = GhostNpcManager.getInstance().spawnGhostNpc(player, world, trigger.spawnGhostNpc);
        }
        QuestActionExecutor.executeAll(player, null, trigger.actions);
        return spawned;
    }

    private void cleanupFiredTriggers(MinecraftServer server, QuestStateStore store) {
        firedTriggers.removeIf(key -> {
            String[] parts = key.split(":", 3);
            if (parts.length < 3) return true;
            try {
                UUID playerUuid = UUID.fromString(parts[0]);
                String questId  = parts[1];
                String stageId  = parts[2];
                boolean expired = !store.isOnStage(playerUuid, questId, stageId);
                if (expired) {
                    Entity entity = triggerEntities.remove(key);
                    if (entity != null && !entity.isRemoved()) entity.discard();
                    playersInRange.remove(key);
                }
                return expired;
            } catch (IllegalArgumentException e) {
                return true;
            }
        });
    }

    /** Сбрасывает все proximity-триггеры конкретного квеста для игрока. */
    public void clearForQuest(UUID playerUuid, String questId) {
        String prefix = playerUuid + ":" + questId + ":";
        List<String> toRemove = new ArrayList<>();
        for (String k : firedTriggers) {
            if (k.startsWith(prefix)) toRemove.add(k);
        }
        for (String k : toRemove) {
            firedTriggers.remove(k);
            Entity entity = triggerEntities.remove(k);
            if (entity != null && !entity.isRemoved()) entity.discard();
            playersInRange.remove(k);
        }
    }

    public void clearForPlayer(UUID playerUuid) {
        String prefix = playerUuid + ":";
        List<String> toRemove = new ArrayList<>();
        for (String k : firedTriggers) {
            if (k.startsWith(prefix)) toRemove.add(k);
        }
        for (String k : toRemove) {
            firedTriggers.remove(k);
            Entity entity = triggerEntities.remove(k);
            if (entity != null && !entity.isRemoved()) entity.discard();
            playersInRange.remove(k);
        }
    }
}
