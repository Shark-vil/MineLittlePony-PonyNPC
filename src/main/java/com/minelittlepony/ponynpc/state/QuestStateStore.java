package com.minelittlepony.ponynpc.state;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Per-player хранилище прогресса квестов.
 *
 * Данные хранятся в {world}/data/ponynpc_quests/{uuid}.dat — по одному файлу на игрока.
 * В памяти живут только онлайн-игроки. Загрузка при входе, сохранение при выходе/остановке.
 *
 * NOTE: несовместимо со старым форматом ponynpc_quest_progress.dat.
 * Для миграции используйте /ponynpc quest reset.
 */
public final class QuestStateStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/QuestState");
    private static final QuestStateStore INSTANCE = new QuestStateStore();

    // Только онлайн-игроки: UUID → (questId → состояние)
    private final Map<UUID, Map<String, PlayerQuestState>> online = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();

    private MinecraftServer server;

    private QuestStateStore() {}

    public static QuestStateStore getInstance() { return INSTANCE; }

    /** Обратная совместимость — все вызовы getOrCreate(server) продолжают работать. */
    public static QuestStateStore getOrCreate(MinecraftServer server) {
        INSTANCE.server = server;
        return INSTANCE;
    }

    // ── Lifecycle ─────────────────────────────────────────────────

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void onPlayerJoin(UUID uuid) {
        online.put(uuid, loadFromDisk(uuid));
        LOGGER.debug("Loaded quest state for player {}", uuid);
    }

    public void onPlayerLeave(UUID uuid) {
        flushIfDirty(uuid);
        online.remove(uuid);
        dirty.remove(uuid);
    }

    /** Сохраняет всех грязных онлайн-игроков. Вызывается перед остановкой сервера. */
    public void onServerStopping() {
        for (UUID uuid : List.copyOf(dirty)) flushIfDirty(uuid);
        dirty.clear();
    }

    private void flushIfDirty(UUID uuid) {
        if (!dirty.contains(uuid)) return;
        Map<String, PlayerQuestState> state = online.get(uuid);
        if (state != null) saveToFile(uuid, state);
        dirty.remove(uuid);
    }

    // ── Чтение ───────────────────────────────────────────────────

    public boolean isStarted(UUID uuid, String questId) {
        return getState(uuid, questId).map(PlayerQuestState::isStarted).orElse(false);
    }

    public boolean isComplete(UUID uuid, String questId) {
        return getState(uuid, questId).map(PlayerQuestState::isComplete).orElse(false);
    }

    public boolean isOnStage(UUID uuid, String questId, String stageId) {
        return getState(uuid, questId).map(s -> s.isOnStage(stageId)).orElse(false);
    }

    public Optional<String> getCurrentStage(UUID uuid, String questId) {
        return getState(uuid, questId).map(PlayerQuestState::getCurrentStage);
    }

    public long getStageStartTime(UUID uuid, String questId, String stageId) {
        return getState(uuid, questId).map(s -> s.getStageStartTime(stageId)).orElse(-1L);
    }

    public int getCollectedCount(UUID uuid, String questId, String stageId) {
        return getState(uuid, questId).map(s -> s.getCollectedCount(stageId)).orElse(0);
    }

    public boolean isTargetCollected(UUID uuid, String questId, String stageId, String targetId) {
        return getState(uuid, questId).map(s -> s.isTargetCollected(stageId, targetId)).orElse(false);
    }

    /** Внутренний доступ для NetworkHandler.buildJournal. */
    public Optional<PlayerQuestState> getState(UUID uuid, String questId) {
        Map<String, PlayerQuestState> map = online.get(uuid);
        if (map != null) return Optional.ofNullable(map.get(questId));
        // Оффлайн-игрок (например, команды оператора): загружаем с диска без кэша
        return Optional.ofNullable(loadFromDisk(uuid).get(questId));
    }

    /**
     * Возвращает ID квестов, по которым игрок начал хотя бы один прогресс
     * (включая завершённые). Только для онлайн-игроков.
     */
    public Set<String> getStartedQuestIds(UUID uuid) {
        Map<String, PlayerQuestState> map = online.get(uuid);
        if (map == null) return Collections.emptySet();
        Set<String> result = new HashSet<>();
        for (var entry : map.entrySet()) {
            if (entry.getValue().isStarted()) result.add(entry.getKey());
        }
        return result;
    }

    /**
     * Возвращает ID активных (начатых и не завершённых) квестов игрока.
     * Только для онлайн-игроков.
     */
    public Set<String> getActiveQuestIds(UUID uuid) {
        Map<String, PlayerQuestState> map = online.get(uuid);
        if (map == null) return Collections.emptySet();
        Set<String> result = new HashSet<>();
        for (var entry : map.entrySet()) {
            PlayerQuestState s = entry.getValue();
            if (s.isStarted() && !s.isComplete()) result.add(entry.getKey());
        }
        return result;
    }

    // ── Запись ───────────────────────────────────────────────────

    public void startQuest(UUID uuid, String questId, String startStage, long worldTime) {
        PlayerQuestState state = onlineOrCreate(uuid, questId);
        if (!state.isStarted()) {
            state.setCurrentStage(startStage, worldTime);
            dirty.add(uuid);
        }
    }

    public void completeCurrentStage(UUID uuid, String questId,
                                     String currentStage, String nextStage, long worldTime) {
        PlayerQuestState state = onlineOrCreate(uuid, questId);
        state.markStageComplete(currentStage);
        state.setCurrentStage(nextStage == null ? PlayerQuestState.COMPLETED : nextStage, worldTime);
        dirty.add(uuid);
    }

    public boolean collectTarget(UUID uuid, String questId, String stageId, String targetId) {
        PlayerQuestState state = onlineOrCreate(uuid, questId);
        boolean added = state.collectTarget(stageId, targetId);
        if (added) dirty.add(uuid);
        return added;
    }

    public void resetQuest(UUID uuid, String questId) {
        Map<String, PlayerQuestState> map = online.get(uuid);
        if (map != null) {
            map.remove(questId);
            dirty.add(uuid);
        } else {
            // Оффлайн: загружаем, удаляем, сразу сохраняем
            Map<String, PlayerQuestState> loaded = loadFromDisk(uuid);
            if (loaded.remove(questId) != null) saveToFile(uuid, loaded);
        }
    }

    // ── Внутренние ────────────────────────────────────────────────

    private PlayerQuestState onlineOrCreate(UUID uuid, String questId) {
        return online.computeIfAbsent(uuid, u -> new HashMap<>())
                     .computeIfAbsent(questId, q -> new PlayerQuestState());
    }

    // ── Файловый I/O ──────────────────────────────────────────────

    private Path getPlayerFile(UUID uuid) {
        return server.getSavePath(WorldSavePath.ROOT)
                     .resolve("data")
                     .resolve("ponynpc_quests")
                     .resolve(uuid + ".dat");
    }

    private Map<String, PlayerQuestState> loadFromDisk(UUID uuid) {
        if (server == null) return new HashMap<>();
        Path file = getPlayerFile(uuid);
        if (!Files.exists(file)) return new HashMap<>();
        try {
            NbtCompound nbt = NbtIo.read(file);
            if (nbt == null) return new HashMap<>();
            Map<String, PlayerQuestState> result = new HashMap<>();
            for (String questId : nbt.getKeys()) {
                result.put(questId, PlayerQuestState.fromNbt(nbt.getCompound(questId)));
            }
            return result;
        } catch (IOException e) {
            LOGGER.error("Failed to load quest progress for player {}", uuid, e);
            return new HashMap<>();
        }
    }

    private void saveToFile(UUID uuid, Map<String, PlayerQuestState> state) {
        if (server == null) return;
        Path file = getPlayerFile(uuid);
        try {
            Files.createDirectories(file.getParent());
            NbtCompound nbt = new NbtCompound();
            state.forEach((questId, qs) -> nbt.put(questId, qs.toNbt()));
            NbtIo.write(nbt, file);
        } catch (IOException e) {
            LOGGER.error("Failed to save quest progress for player {}", uuid, e);
        }
    }
}
