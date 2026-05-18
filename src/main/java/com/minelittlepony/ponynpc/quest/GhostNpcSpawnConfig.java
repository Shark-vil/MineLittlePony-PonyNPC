package com.minelittlepony.ponynpc.quest;

import com.google.gson.annotations.SerializedName;

/** Конфигурация для спауна ghost NPC при срабатывании proximity trigger. */
public class GhostNpcSpawnConfig {
    /** ID диалога (из config/ponynpc/dialogs/). */
    @SerializedName("dialog_id") public String dialogId = "";
    /** Короткое имя скина или полный идентификатор. */
    public String skin = "";
    /** Отображаемое имя NPC. */
    public String name = "???";
    /** Автоматически открыть диалог сразу после спауна. */
    @SerializedName("auto_dialog") public boolean autoDialog = true;
    /** Удалить ghost NPC как только диалог закрывается. */
    @SerializedName("despawn_after_dialog") public boolean despawnAfterDialog = true;
    /**
     * Если задан — ghost NPC живёт до тех пор, пока игрок не достигнет этого этапа квеста.
     * При переходе на указанный этап (или завершении/сбросе квеста) NPC удаляется.
     * Используется вместе с despawn_after_dialog=false.
     */
    @SerializedName("persist_until_stage") public String persistUntilStage = null;
}
