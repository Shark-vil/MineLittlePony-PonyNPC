package com.minelittlepony.ponynpc.dialog;

import com.google.gson.annotations.SerializedName;

/**
 * Режим работы диалога NPC.
 *
 * Принимается в нижнем или верхнем регистре в JSON ("random" / "RANDOM").
 */
public enum DialogMode {

    /**
     * Ветвистый диалог с выборами, переходами между узлами и прогрессом (once-флаги).
     * Использует поле {@code nodes} в JSON.
     */
    @SerializedName(value = "quest", alternate = "QUEST")
    QUEST,

    /**
     * Случайная одиночная реплика. NPC говорит одну из {@code phrases} и диалог закрывается.
     * Не оставляет прогресс, не требует {@code nodes}.
     * Использует поля {@code phrases}, {@code sound}, {@code sound_chars} в JSON.
     */
    @SerializedName(value = "random", alternate = "RANDOM")
    RANDOM

    // ── Зарезервировано для будущих режимов ───────────────────────────────
    // SCRIPTED  — детерминированная последовательность узлов без выборов
    // GPT       — реплика генерируется ИИ, узлы не нужны
    // SHOP      — диалог-магазин (покупка/продажа)
}
