package com.minelittlepony.ponynpc.state;

import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Server-wide хранилище прогресса квестов всех игроков.
 * Привязан к overworld PersistentState.
 */
public class QuestStateStore extends PersistentState {

    private static final String SAVE_ID = "ponynpc_quest_progress";

    public static final Type<QuestStateStore> TYPE = new Type<>(
        QuestStateStore::new,
        QuestStateStore::fromNbt,
        null
    );

    // playerUUID → questId → PlayerQuestState
    private final Map<UUID, Map<String, PlayerQuestState>> store = new HashMap<>();

    private QuestStateStore() {}

    public static QuestStateStore getOrCreate(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, SAVE_ID);
    }

    // ── Доступ к состоянию ────────────────────────────────────────

    private PlayerQuestState getOrCreateState(UUID playerUuid, String questId) {
        return store.computeIfAbsent(playerUuid, u -> new HashMap<>())
                    .computeIfAbsent(questId, q -> new PlayerQuestState());
    }

    public Optional<PlayerQuestState> getState(UUID playerUuid, String questId) {
        Map<String, PlayerQuestState> quests = store.get(playerUuid);
        if (quests == null) return Optional.empty();
        return Optional.ofNullable(quests.get(questId));
    }

    // ── Quest lifecycle ───────────────────────────────────────────

    public boolean isStarted(UUID playerUuid, String questId) {
        return getState(playerUuid, questId).map(PlayerQuestState::isStarted).orElse(false);
    }

    public boolean isComplete(UUID playerUuid, String questId) {
        return getState(playerUuid, questId).map(PlayerQuestState::isComplete).orElse(false);
    }

    public boolean isOnStage(UUID playerUuid, String questId, String stageId) {
        return getState(playerUuid, questId).map(s -> s.isOnStage(stageId)).orElse(false);
    }

    public Optional<String> getCurrentStage(UUID playerUuid, String questId) {
        return getState(playerUuid, questId).map(PlayerQuestState::getCurrentStage);
    }

    /** Начать квест (установить первый этап). */
    public void startQuest(UUID playerUuid, String questId, String startStage, long worldTime) {
        PlayerQuestState state = getOrCreateState(playerUuid, questId);
        if (!state.isStarted()) {
            state.setCurrentStage(startStage, worldTime);
            markDirty();
        }
    }

    /** Завершить текущий этап и перейти к следующему (null = квест завершён). */
    public void completeCurrentStage(UUID playerUuid, String questId, String currentStage, String nextStage, long worldTime) {
        PlayerQuestState state = getOrCreateState(playerUuid, questId);
        state.markStageComplete(currentStage);
        if (nextStage == null) {
            state.setCurrentStage(PlayerQuestState.COMPLETED, worldTime);
        } else {
            state.setCurrentStage(nextStage, worldTime);
        }
        markDirty();
    }

    // ── MULTI_COLLECT ─────────────────────────────────────────────

    public boolean collectTarget(UUID playerUuid, String questId, String stageId, String targetId) {
        PlayerQuestState state = getOrCreateState(playerUuid, questId);
        boolean added = state.collectTarget(stageId, targetId);
        if (added) markDirty();
        return added;
    }

    public int getCollectedCount(UUID playerUuid, String questId, String stageId) {
        return getState(playerUuid, questId).map(s -> s.getCollectedCount(stageId)).orElse(0);
    }

    public boolean isTargetCollected(UUID playerUuid, String questId, String stageId, String targetId) {
        return getState(playerUuid, questId).map(s -> s.isTargetCollected(stageId, targetId)).orElse(false);
    }

    /** Полностью сбрасывает прогресс квеста для игрока. */
    public void resetQuest(UUID playerUuid, String questId) {
        Map<String, PlayerQuestState> quests = store.get(playerUuid);
        if (quests != null) {
            quests.remove(questId);
            markDirty();
        }
    }

    // ── WAIT_DAYS ─────────────────────────────────────────────────

    public long getStageStartTime(UUID playerUuid, String questId, String stageId) {
        return getState(playerUuid, questId).map(s -> s.getStageStartTime(stageId)).orElse(-1L);
    }

    // ── NBT ───────────────────────────────────────────────────────

    public static QuestStateStore fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        QuestStateStore store = new QuestStateStore();
        NbtCompound playersNbt = nbt.getCompound("players");
        for (String uuidStr : playersNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                NbtCompound questsNbt = playersNbt.getCompound(uuidStr);
                Map<String, PlayerQuestState> quests = new HashMap<>();
                for (String questId : questsNbt.getKeys()) {
                    quests.put(questId, PlayerQuestState.fromNbt(questsNbt.getCompound(questId)));
                }
                store.store.put(uuid, quests);
            } catch (IllegalArgumentException ignored) {}
        }
        return store;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound playersNbt = new NbtCompound();
        store.forEach((uuid, quests) -> {
            NbtCompound questsNbt = new NbtCompound();
            quests.forEach((questId, state) -> questsNbt.put(questId, state.toNbt()));
            playersNbt.put(uuid.toString(), questsNbt);
        });
        nbt.put("players", playersNbt);
        return nbt;
    }
}
