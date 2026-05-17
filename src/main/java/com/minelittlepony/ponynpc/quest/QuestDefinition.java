package com.minelittlepony.ponynpc.quest;

import com.google.gson.annotations.SerializedName;

import java.util.*;

/** Определение квеста (загружается из JSON-файла). */
public class QuestDefinition {
    /** Уникальный ID квеста. Автоматически берётся из имени файла если пустой. */
    public String id = "";
    /** Отображаемое название квеста. */
    public String name = "";
    /** ID начального этапа. */
    @SerializedName("start_stage") public String startStage = "start";
    /** Все этапы квеста: stageId → QuestStage. */
    public Map<String, QuestStage> stages = new LinkedHashMap<>();

    /** Получить этап по ID. */
    public Optional<QuestStage> getStage(String stageId) {
        return Optional.ofNullable(stages.get(stageId));
    }

    /** Возвращает true если квест корректен (есть начальный этап). */
    public boolean isValid() {
        return !id.isEmpty() && stages.containsKey(startStage);
    }
}
