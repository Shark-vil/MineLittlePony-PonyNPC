package com.minelittlepony.ponynpc.quest;

import com.google.gson.annotations.SerializedName;

public enum QuestStageType {
    /** Завершается через dialog action `quest:<id>:complete_stage`. */
    @SerializedName("dialog")             DIALOG,
    /** Завершается когда игрок получает квестовый предмет в карман. */
    @SerializedName("collect_quest_item") COLLECT_QUEST_ITEM,
    /** Завершается когда пройдёт N игровых дней (24000 тиков = 1 день). */
    @SerializedName("wait_days")          WAIT_DAYS,
    /** Завершается при прохождении данжа (Phase 2). */
    @SerializedName("dungeon")            DUNGEON,
    /** Завершается когда набраны required_count уникальных целей. */
    @SerializedName("multi_collect")      MULTI_COLLECT,
    /** Placeholder для мини-игр (Phase 3+). */
    @SerializedName("mini_game")          MINI_GAME
}
