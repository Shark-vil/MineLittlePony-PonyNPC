package com.minelittlepony.ponynpc.trigger;

import com.minelittlepony.ponynpc.entity.PonyNpcEntity;
import com.minelittlepony.ponynpc.ghost.GhostNpcManager;
import com.minelittlepony.ponynpc.quest.*;
import com.minelittlepony.ponynpc.state.PlayerQuestState;
import com.minelittlepony.ponynpc.state.QuestStateStore;
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
 * Логика повторного срабатывания:
 * - Триггер не срабатывает повторно пока spawned ghost NPC жив.
 * - Когда игрок покидает радиус, а ghost NPC уже нет → триггер сбрасывается.
 * - Следующий вход в радиус снова spawns NPC.
 */
public final class ProximityTriggerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/ProximityTrigger");
    private static final int CHECK_INTERVAL = 20;
    private static final ProximityTriggerManager INSTANCE = new ProximityTriggerManager();

    // fireKey = playerUuid:questId:stageId
    private final Set<String> firedTriggers   = new HashSet<>();
    private final Set<String> playersInRange  = new HashSet<>();
    // NPC spawned by a trigger (null if trigger had no ghost_npc)
    private final Map<String, PonyNpcEntity>  triggerNpcs = new HashMap<>();

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
                            // Already fired — re-fire only if the spawned NPC is gone
                            PonyNpcEntity npc = triggerNpcs.get(fireKey);
                            if (npc != null && !npc.isRemoved()) return; // NPC still alive
                            // NPC gone but player still in range → respawn immediately
                            firedTriggers.remove(fireKey);
                            triggerNpcs.remove(fireKey);
                        }

                        firedTriggers.add(fireKey);
                        PonyNpcEntity spawned = fireTrigger(player, server, questId, stageId, trigger);
                        triggerNpcs.put(fireKey, spawned); // may be null if no ghost_npc configured

                    } else {
                        // Player left the radius
                        if (playersInRange.remove(fireKey)) {
                            // They were in range — reset if the NPC is gone
                            PonyNpcEntity npc = triggerNpcs.get(fireKey);
                            if (npc == null || npc.isRemoved()) {
                                firedTriggers.remove(fireKey);
                                triggerNpcs.remove(fireKey);
                            }
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

    /** Fires the trigger and returns the ghost NPC that was spawned (or null). */
    private PonyNpcEntity fireTrigger(ServerPlayerEntity player, MinecraftServer server,
                                      String questId, String stageId,
                                      ProximityTriggerConfig trigger) {
        LOGGER.info("Proximity trigger fired: quest='{}' stage='{}' player='{}'",
            questId, stageId, player.getName().getString());

        PonyNpcEntity spawned = null;
        if (trigger.spawnGhostNpc != null) {
            ServerWorld world = player.getServerWorld();
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
                    triggerNpcs.remove(key);
                    playersInRange.remove(key);
                }
                return expired;
            } catch (IllegalArgumentException e) {
                return true;
            }
        });
    }

    public void clearForPlayer(UUID playerUuid) {
        String prefix = playerUuid + ":";
        firedTriggers.removeIf(k  -> k.startsWith(prefix));
        triggerNpcs.keySet().removeIf(k -> k.startsWith(prefix));
        playersInRange.removeIf(k  -> k.startsWith(prefix));
    }
}
