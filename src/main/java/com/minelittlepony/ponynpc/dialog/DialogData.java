package com.minelittlepony.ponynpc.dialog;

import com.google.gson.annotations.SerializedName;

import java.util.*;

public class DialogData {

    /** Уникальный ID диалога (совпадает с именем файла без расширения). */
    public String id = "";

    /**
     * Режим диалога. По умолчанию: QUEST (обратная совместимость со старыми файлами).
     * Допустимые значения: "quest" / "QUEST", "random" / "RANDOM".
     */
    public DialogMode mode = DialogMode.QUEST;

    // ── QUEST-режим ────────────────────────────────────────────────────────

    /** Узлы диалога. Ключ — имя узла. Обязательный узел: "start". */
    public Map<String, DialogNode> nodes = new HashMap<>();

    // ── RANDOM-режим ───────────────────────────────────────────────────────

    /**
     * Список реплик для случайного выбора (режим RANDOM).
     * Одна из них выбирается случайно при каждом взаимодействии с NPC.
     */
    public List<String> phrases = new ArrayList<>();

    /**
     * Sound event ID для режима RANDOM (по умолчанию: "ponynpc:talk").
     * В режиме QUEST звук задаётся отдельно для каждого узла.
     */
    public String sound = "ponynpc:talk";

    /**
     * Интервал воспроизведения звука (символов) для режима RANDOM.
     * В режиме QUEST задаётся отдельно для каждого узла.
     */
    @SerializedName("sound_chars")
    public int soundChars = 2;

    // ── ROUTER-режим ───────────────────────────────────────────────────────

    /**
     * Список записей маршрутизатора (режим ROUTER).
     * Проверяются по порядку; выбирается первый, у которого выполнены все conditions.
     * Запись без conditions — безусловный fallback.
     */
    public List<RouterEntry> entries = new ArrayList<>();

    public static final class RouterEntry {
        /** ID диалога, которому передаётся управление. */
        public String dialog = "";
        /** Список условий (все должны быть выполнены). Пустой — всегда выполнен (fallback). */
        public List<String> conditions = List.of();
    }

    // ── Вспомогательные методы ─────────────────────────────────────────────

    public Optional<DialogNode> getNode(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    /**
     * Возвращает случайную реплику (для режима RANDOM).
     * Возвращает пустой Optional если список phrases пуст.
     */
    public Optional<String> getRandomPhrase() {
        if (phrases.isEmpty()) return Optional.empty();
        return Optional.of(phrases.get(new Random().nextInt(phrases.size())));
    }
}
