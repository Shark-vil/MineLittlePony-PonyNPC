package com.minelittlepony.ponynpc.trigger;

import com.minelittlepony.ponynpc.entity.PonyNpcEntity;
import com.minelittlepony.ponynpc.entity.QuestMarkerEntity;
import com.minelittlepony.ponynpc.ghost.GhostNpcManager;
import com.minelittlepony.ponynpc.quest.*;
import com.minelittlepony.ponynpc.registry.PonyNpcEntities;
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
 * Управляет proximity-триггерами квестов.
 *
 * Вместо O(players × all_quests) каждый тик поддерживает индекс
 * activeTriggers: только триггеры для текущих этапов каждого игрока.
 * tick() = O(players × active_triggers_per_player) — обычно 0-2 на игрока.
 *
 * Жизненный цикл:
 *  - registerTrigger() вызывается из QuestManager при старте/смене этапа
 *  - unregisterForStage() вызывается при завершении этапа
 *  - onPlayerJoin() перевосстанавливает триггеры при логине
 *  - clearForPlayer() вызывается при дисконнекте
 */
public final class ProximityTriggerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/ProximityTrigger");
    private static final int CHECK_INTERVAL = 20;
    private static final ProximityTriggerManager INSTANCE = new ProximityTriggerManager();

    // ── Индекс активных триггеров ─────────────────────────────────
    // Только триггеры этапов, на которых игрок сейчас находится
    private final Map<UUID, List<ActiveTrigger>> activeTriggers = new HashMap<>();

    // ── Авто-старт квестов ────────────────────────────────────────
    // Квесты, чей начальный этап имеет proximity_trigger (и квест ещё не начат)
    // questId → config стартового этапа; строится при reload
    private final Map<String, ProximityTriggerConfig> autoStartQuests = new LinkedHashMap<>();

    // ── Состояние срабатываний ────────────────────────────────────
    // fireKey = playerUuid:questId:stageId
    private final Set<String>          firedKeys        = new HashSet<>();
    private final Set<String>          playersInRange   = new HashSet<>();
    private final Map<String, Entity>  triggerEntities  = new HashMap<>();
    // fireKey → persistUntilStage (для ghost NPC с persist_until_stage)
    private final Map<String, String>  triggerPersistUntil = new HashMap<>();
    // persistKey = playerUuid:questId:persistUntilStageId → ghost NPC, переживший свой этап
    private final Map<String, Entity>  persistingGhosts = new HashMap<>();

    private int tickCounter = 0;

    private ProximityTriggerManager() {}

    public static ProximityTriggerManager getInstance() { return INSTANCE; }

    // ── Public API ────────────────────────────────────────────────

    /**
     * Регистрирует proximity trigger для указанного этапа игрока.
     * Вызывается из QuestManager при starQuest / completeCurrentStage.
     */
    public void registerTrigger(UUID uuid, String questId, String stageId, ProximityTriggerConfig config) {
        activeTriggers.computeIfAbsent(uuid, k -> new ArrayList<>())
                      .add(new ActiveTrigger(questId, stageId, config));
        LOGGER.debug("Registered trigger: player={} quest={} stage={}", uuid, questId, stageId);
    }

    /**
     * Снимает регистрацию триггера при завершении этапа.
     * Обрабатывает persist_until_stage: если задан — переносит entity в persistingGhosts.
     */
    public void unregisterForStage(UUID uuid, String questId, String stageId) {
        List<ActiveTrigger> list = activeTriggers.get(uuid);
        if (list != null) list.removeIf(t -> t.questId.equals(questId) && t.stageId.equals(stageId));

        String fireKey = uuid + ":" + questId + ":" + stageId;
        firedKeys.remove(fireKey);
        playersInRange.remove(fireKey);

        String persistUntilStage = triggerPersistUntil.remove(fireKey);
        Entity entity = triggerEntities.remove(fireKey);

        if (entity != null && !entity.isRemoved() && persistUntilStage != null) {
            persistingGhosts.put(uuid + ":" + questId + ":" + persistUntilStage, entity);
        } else if (entity != null && !entity.isRemoved()) {
            entity.discard();
        }
    }

    /**
     * Задаёт список квестов с авто-стартом через proximity trigger.
     * Вызывается из QuestManager.reload().
     */
    public void setAutoStartQuests(Map<String, ProximityTriggerConfig> quests) {
        autoStartQuests.clear();
        autoStartQuests.putAll(quests);
    }

    /**
     * Перевосстанавливает active triggers при логине игрока.
     * activeStages: questId → stageId (только активные, не завершённые квесты).
     */
    public void onPlayerJoin(UUID uuid, Map<String, String> activeStages) {
        List<ActiveTrigger> list = activeTriggers.computeIfAbsent(uuid, k -> new ArrayList<>());
        list.clear();

        QuestManager qm = QuestManager.getInstance();
        for (Map.Entry<String, String> entry : activeStages.entrySet()) {
            qm.getQuest(entry.getKey())
              .flatMap(def -> def.getStage(entry.getValue()))
              .map(stage -> stage.proximityTrigger)
              .filter(Objects::nonNull)
              .ifPresent(config -> list.add(new ActiveTrigger(entry.getKey(), entry.getValue(), config)));
        }
        LOGGER.debug("Restored {} proximity trigger(s) for player {}", list.size(), uuid);
    }

    // ── Tick ──────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        if (++tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        QuestManager qm = QuestManager.getInstance();
        QuestStateStore store = QuestStateStore.getOrCreate(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();

            // -- Активные триггеры текущего этапа: O(active_triggers_per_player) --
            List<ActiveTrigger> triggers = activeTriggers.get(uuid);
            if (triggers != null && !triggers.isEmpty()) {
                for (ActiveTrigger trigger : List.copyOf(triggers)) {
                    tickTrigger(player, server, trigger);
                }
            }

            // -- Авто-старт квестов: O(auto_start_quests), обычно единицы --
            for (Map.Entry<String, ProximityTriggerConfig> entry : autoStartQuests.entrySet()) {
                String questId = entry.getKey();
                if (store.isStarted(uuid, questId)) continue;
                if (isPlayerInRange(player, server, entry.getValue())) {
                    qm.startQuest(player, questId);
                    // registerTrigger вызовется внутри startQuest если стартовый этап имеет trigger
                }
            }
        }

        cleanupPersistingGhosts(store);
    }

    private void tickTrigger(ServerPlayerEntity player, MinecraftServer server, ActiveTrigger trigger) {
        UUID uuid = player.getUuid();
        String fireKey = uuid + ":" + trigger.questId + ":" + trigger.stageId;
        boolean inRange = isPlayerInRange(player, server, trigger.config);

        if (inRange) {
            playersInRange.add(fireKey);

            if (firedKeys.contains(fireKey)) {
                Entity entity = triggerEntities.get(fireKey);
                if (entity != null && !entity.isRemoved()) return; // ещё живёт
                // Сущность исчезла — разрешаем повторный спавн
                firedKeys.remove(fireKey);
                triggerEntities.remove(fireKey);
            }

            firedKeys.add(fireKey);
            Entity spawned = fireTrigger(player, server, trigger.questId, trigger.stageId, trigger.config);
            triggerEntities.put(fireKey, spawned);

            if (spawned != null && trigger.config.spawnGhostNpc != null
                    && trigger.config.spawnGhostNpc.persistUntilStage != null) {
                triggerPersistUntil.put(fireKey, trigger.config.spawnGhostNpc.persistUntilStage);
            }
        } else {
            if (playersInRange.remove(fireKey)) {
                Entity entity = triggerEntities.get(fireKey);
                if (entity == null || entity.isRemoved()) {
                    firedKeys.remove(fireKey);
                    triggerEntities.remove(fireKey);
                }
            }
        }
    }

    // ── Внутренние ────────────────────────────────────────────────

    private boolean isPlayerInRange(ServerPlayerEntity player, MinecraftServer server,
                                    ProximityTriggerConfig trigger) {
        Identifier dimId = Identifier.tryParse(trigger.dimension);
        if (dimId == null) return false;
        RegistryKey<World> dimKey = RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, dimId);
        ServerWorld triggerWorld = server.getWorld(dimKey);
        if (triggerWorld == null || !player.getWorld().equals(triggerWorld)) return false;
        Vec3d pos = player.getPos();
        double dx = pos.x - trigger.x, dy = pos.y - trigger.y, dz = pos.z - trigger.z;
        return dx * dx + dy * dy + dz * dz <= trigger.radius * trigger.radius;
    }

    private Entity fireTrigger(ServerPlayerEntity player, MinecraftServer server,
                               String questId, String stageId, ProximityTriggerConfig trigger) {
        LOGGER.info("Trigger fired: quest='{}' stage='{}' player='{}'",
            questId, stageId, player.getName().getString());

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

        // Ghost NPC
        PonyNpcEntity spawned = null;
        if (trigger.spawnGhostNpc != null) {
            spawned = GhostNpcManager.getInstance().spawnGhostNpc(player, player.getServerWorld(), trigger.spawnGhostNpc);
        }
        QuestActionExecutor.executeAll(player, null, trigger.actions);
        return spawned;
    }

    private void cleanupPersistingGhosts(QuestStateStore store) {
        persistingGhosts.entrySet().removeIf(entry -> {
            String[] parts = entry.getKey().split(":", 3);
            if (parts.length < 3) { entry.getValue().discard(); return true; }
            try {
                UUID uuid            = UUID.fromString(parts[0]);
                String questId       = parts[1];
                String untilStage    = parts[2];
                Entity entity        = entry.getValue();
                if (entity.isRemoved()) return true;
                boolean despawn = store.isOnStage(uuid, questId, untilStage)
                        || store.isComplete(uuid, questId)
                        || !store.isStarted(uuid, questId);
                if (despawn) { entity.discard(); return true; }
                return false;
            } catch (IllegalArgumentException e) {
                entry.getValue().discard();
                return true;
            }
        });
    }

    // ── Очистка ───────────────────────────────────────────────────

    public void clearForQuest(UUID uuid, String questId) {
        List<ActiveTrigger> list = activeTriggers.get(uuid);
        if (list != null) list.removeIf(t -> t.questId.equals(questId));

        String prefix = uuid + ":" + questId + ":";
        discardAndRemove(prefix);
        persistingGhosts.entrySet().removeIf(e -> {
            if (!e.getKey().startsWith(prefix)) return false;
            if (!e.getValue().isRemoved()) e.getValue().discard();
            return true;
        });
    }

    public void clearForPlayer(UUID uuid) {
        activeTriggers.remove(uuid);
        String prefix = uuid + ":";
        discardAndRemove(prefix);
        persistingGhosts.entrySet().removeIf(e -> {
            if (!e.getKey().startsWith(prefix)) return false;
            if (!e.getValue().isRemoved()) e.getValue().discard();
            return true;
        });
    }

    private void discardAndRemove(String prefix) {
        for (String key : List.copyOf(firedKeys)) {
            if (!key.startsWith(prefix)) continue;
            firedKeys.remove(key);
            triggerPersistUntil.remove(key);
            Entity e = triggerEntities.remove(key);
            if (e != null && !e.isRemoved()) e.discard();
            playersInRange.remove(key);
        }
    }

    // ── Inner class ───────────────────────────────────────────────

    private static final class ActiveTrigger {
        final String questId;
        final String stageId;
        final ProximityTriggerConfig config;

        ActiveTrigger(String questId, String stageId, ProximityTriggerConfig config) {
            this.questId = questId;
            this.stageId = stageId;
            this.config  = config;
        }
    }
}
