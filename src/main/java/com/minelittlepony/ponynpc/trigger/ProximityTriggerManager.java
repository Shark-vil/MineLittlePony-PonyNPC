package com.minelittlepony.ponynpc.trigger;

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
 * Триггеры встроены в QuestStage.proximityTrigger и срабатывают
 * когда игрок входит в радиус (при условии нужного этапа квеста).
 */
public final class ProximityTriggerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/ProximityTrigger");
    private static final int CHECK_INTERVAL = 20; // тики (1 сек)
    private static final ProximityTriggerManager INSTANCE = new ProximityTriggerManager();

    // Запоминаем для каждого игрока какие триггеры уже сработали (до выхода со стадии)
    // key: playerUuid + ":" + questId + ":" + stageId
    private final Set<String> firedTriggers = new HashSet<>();

    private int tickCounter = 0;

    private ProximityTriggerManager() {}

    public static ProximityTriggerManager getInstance() { return INSTANCE; }

    /** Вызывается каждый тик сервера. */
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
                    if (firedTriggers.contains(fireKey)) return;

                    if (isPlayerInRange(player, server, trigger)) {
                        firedTriggers.add(fireKey);
                        fireTrigger(player, server, questId, stageId, trigger);
                    }
                });
            }
        }

        // Очищаем сработавшие триггеры для игроков которые сменили стадию
        cleanupFiredTriggers(server, store);
    }

    private boolean isPlayerInRange(ServerPlayerEntity player, MinecraftServer server, ProximityTriggerConfig trigger) {
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

    private void fireTrigger(ServerPlayerEntity player, MinecraftServer server, String questId,
                              String stageId, ProximityTriggerConfig trigger) {
        LOGGER.info("Proximity trigger fired: quest='{}' stage='{}' player='{}'",
            questId, stageId, player.getName().getString());

        // Spawn ghost NPC если настроен
        if (trigger.spawnGhostNpc != null) {
            ServerWorld world = player.getServerWorld();
            GhostNpcManager.getInstance().spawnGhostNpc(player, world, trigger.spawnGhostNpc);
        }

        // Дополнительные действия
        QuestActionExecutor.executeAll(player, null, trigger.actions);
    }

    private void cleanupFiredTriggers(MinecraftServer server, QuestStateStore store) {
        firedTriggers.removeIf(key -> {
            String[] parts = key.split(":", 3);
            if (parts.length < 3) return true;
            try {
                UUID playerUuid = UUID.fromString(parts[0]);
                String questId = parts[1];
                String stageId = parts[2];
                // Триггер больше не нужен если игрок сменил стадию
                return !store.isOnStage(playerUuid, questId, stageId);
            } catch (IllegalArgumentException e) {
                return true;
            }
        });
    }

    /** Сбросить все сработавшие триггеры для игрока (при logout). */
    public void clearForPlayer(UUID playerUuid) {
        firedTriggers.removeIf(key -> key.startsWith(playerUuid.toString() + ":"));
    }
}
