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
}
