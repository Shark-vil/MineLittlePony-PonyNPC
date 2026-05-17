package com.minelittlepony.ponynpc.dialog;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class DialogChoice {
    /** Уникальный ID выбора внутри узла. */
    public String id = "";

    /** Текст варианта ответа игрока. */
    public String text = "";

    /** ID следующего узла после выбора. null = закрыть диалог. */
    public String next = null;

    /**
     * Если true — выбор помечается как выполненный после первого нажатия.
     * Повторно он всё ещё доступен, но визуально отмечен.
     */
    public boolean once = false;

    /**
     * Условия показа выбора. Все должны быть выполнены чтобы выбор был активен.
     * Формат: ["quest:<id>:stage:<stageId>", "quest:<id>:has_item:<itemId>", ...]
     */
    public List<String> conditions = null;

    /**
     * Если true и conditions не выполнены — выбор полностью скрыт.
     * Если false — выбор виден но серый/недоступен (показывает что опция существует).
     */
    @SerializedName("hidden_if_condition_fails")
    public boolean hiddenIfConditionFails = true;

    /**
     * Действия, выполняемые сервером после выбора этого варианта.
     * Формат: ["quest:<id>:complete_stage", "quest:<id>:give_quest_item:<itemId>", ...]
     */
    public List<String> actions = null;
}
