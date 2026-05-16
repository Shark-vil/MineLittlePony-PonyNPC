package com.minelittlepony.ponynpc.dialog;

import com.google.gson.annotations.SerializedName;

public class DialogChoice {
    /** Уникальный ID выбора внутри узла. */
    public String id = "";

    /** Текст варианта ответа игрока. */
    public String text = "";

    /**
     * ID следующего узла после выбора. null = закрыть диалог.
     */
    public String next = null;

    /**
     * Если true — выбор помечается как выполненный после первого нажатия.
     * Повторно он всё ещё доступен, но визуально отмечен.
     */
    public boolean once = false;
}
