package com.minelittlepony.ponynpc.quest;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** Один этап квеста (загружается из JSON). */
public class QuestStage {
    /** Тип этапа — определяет логику завершения. */
    public QuestStageType type = QuestStageType.DIALOG;
    /** Человекочитаемое описание (для интерфейсов). */
    public String description = "";
    /** ID следующего этапа после завершения. null = квест завершён. */
    @SerializedName("next_stage") public String nextStage = null;

    // ── COLLECT_QUEST_ITEM ──────────────────────────────────────
    /** ID квестового предмета в кармане (для типа COLLECT_QUEST_ITEM). */
    @SerializedName("item_id") public String itemId = null;

    // ── WAIT_DAYS ───────────────────────────────────────────────
    /** Количество игровых дней для ожидания (24000 тиков = 1 день). */
    public int days = 1;

    // ── MULTI_COLLECT ───────────────────────────────────────────
    /** Список ID целей (предметов или NPC-взаимодействий). */
    public List<String> targets = new ArrayList<>();
    /** Сколько уникальных целей надо собрать. 0 = все из targets. */
    @SerializedName("required_count") public int requiredCount = 0;

    // ── DUNGEON ─────────────────────────────────────────────────
    /** ID данжа (Phase 2). */
    @SerializedName("dungeon_id") public String dungeonId = null;

    // ── Proximity trigger ───────────────────────────────────────
    /**
     * Опциональный proximity-триггер для этого этапа.
     * Срабатывает когда игрок входит в зону (нужная стадия квеста).
     */
    @SerializedName("proximity_trigger") public ProximityTriggerConfig proximityTrigger = null;

    /** Возвращает реальное требуемое количество (0 означает "все targets"). */
    public int getEffectiveRequiredCount() {
        return requiredCount > 0 ? requiredCount : targets.size();
    }
}
