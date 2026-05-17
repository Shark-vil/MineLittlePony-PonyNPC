package com.minelittlepony.ponynpc.state;

import net.minecraft.nbt.*;

import java.util.*;

/** Прогресс одного игрока по одному квесту. */
public class PlayerQuestState {

    public static final String NOT_STARTED = null;
    public static final String COMPLETED = "__complete__";

    /** Текущий активный этап. null = не начат, COMPLETED = завершён. */
    private String currentStage;
    /** Завершённые этапы. */
    private final Set<String> completedStages = new HashSet<>();
    /** Время старта каждого этапа в тиках мира (для WAIT_DAYS). */
    private final Map<String, Long> stageTimes = new HashMap<>();
    /** Прогресс MULTI_COLLECT: stageId → set собранных target ID. */
    private final Map<String, Set<String>> stageCollected = new HashMap<>();

    public PlayerQuestState() {}

    // ── Состояние ────────────────────────────────────────────────

    public boolean isStarted() { return currentStage != null; }
    public boolean isComplete() { return COMPLETED.equals(currentStage); }
    public boolean isOnStage(String stageId) { return stageId != null && stageId.equals(currentStage); }

    public String getCurrentStage() { return currentStage; }

    public void setCurrentStage(String stageId, long worldTime) {
        currentStage = stageId;
        if (stageId != null && !COMPLETED.equals(stageId)) {
            stageTimes.put(stageId, worldTime);
        }
    }

    public void markStageComplete(String stageId) {
        completedStages.add(stageId);
    }

    public boolean isStageComplete(String stageId) {
        return completedStages.contains(stageId);
    }

    // ── WAIT_DAYS ────────────────────────────────────────────────

    /** Возвращает мировое время когда начался этот этап, или -1 если неизвестно. */
    public long getStageStartTime(String stageId) {
        return stageTimes.getOrDefault(stageId, -1L);
    }

    // ── MULTI_COLLECT ────────────────────────────────────────────

    public boolean collectTarget(String stageId, String targetId) {
        return stageCollected.computeIfAbsent(stageId, k -> new HashSet<>()).add(targetId);
    }

    public int getCollectedCount(String stageId) {
        Set<String> collected = stageCollected.get(stageId);
        return collected == null ? 0 : collected.size();
    }

    public boolean isTargetCollected(String stageId, String targetId) {
        Set<String> collected = stageCollected.get(stageId);
        return collected != null && collected.contains(targetId);
    }

    // ── NBT ──────────────────────────────────────────────────────

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        if (currentStage != null) nbt.putString("stage", currentStage);

        NbtList completedList = new NbtList();
        completedStages.forEach(s -> completedList.add(NbtString.of(s)));
        nbt.put("completed", completedList);

        NbtCompound timesNbt = new NbtCompound();
        stageTimes.forEach((k, v) -> timesNbt.putLong(k, v));
        nbt.put("times", timesNbt);

        NbtCompound collectedNbt = new NbtCompound();
        stageCollected.forEach((stageId, targets) -> {
            NbtList list = new NbtList();
            targets.forEach(t -> list.add(NbtString.of(t)));
            collectedNbt.put(stageId, list);
        });
        nbt.put("collected", collectedNbt);

        return nbt;
    }

    public static PlayerQuestState fromNbt(NbtCompound nbt) {
        PlayerQuestState state = new PlayerQuestState();
        if (nbt.contains("stage")) state.currentStage = nbt.getString("stage");

        NbtList completedList = nbt.getList("completed", NbtElement.STRING_TYPE);
        for (int i = 0; i < completedList.size(); i++) {
            state.completedStages.add(completedList.getString(i));
        }

        NbtCompound timesNbt = nbt.getCompound("times");
        for (String k : timesNbt.getKeys()) {
            state.stageTimes.put(k, timesNbt.getLong(k));
        }

        NbtCompound collectedNbt = nbt.getCompound("collected");
        for (String stageId : collectedNbt.getKeys()) {
            NbtList list = collectedNbt.getList(stageId, NbtElement.STRING_TYPE);
            Set<String> targets = new HashSet<>();
            for (int i = 0; i < list.size(); i++) targets.add(list.getString(i));
            state.stageCollected.put(stageId, targets);
        }

        return state;
    }
}
