package com.minelittlepony.ponynpc.quest;

import com.google.gson.annotations.SerializedName;

/** Конфигурация proximity-триггера внутри стадии квеста. */
public class ProximityTriggerConfig {
    /** Идентификатор измерения, например "minecraft:overworld". */
    public String dimension = "minecraft:overworld";
    public double x = 0, y = 64, z = 0;
    /** Радиус в блоках. */
    public double radius = 10.0;
    /** Если задан — при срабатывании спаунит ghost NPC для игрока. */
    @SerializedName("spawn_ghost_npc") public GhostNpcSpawnConfig spawnGhostNpc = null;
    /** Если true — триггер сразу выполняет actions без спауна сущности. */
    @SerializedName("auto_complete") public boolean autoComplete = false;
    /** Если задан — спаунит кликабельный маркер-объект вместо пони-NPC. */
    @SerializedName("spawn_marker") public MarkerSpawnConfig spawnMarker = null;
    /**
     * Действия при срабатывании (для ghost_npc и auto_complete режимов).
     * Формат — те же строки что и в DialogChoice.actions.
     */
    public java.util.List<String> actions = new java.util.ArrayList<>();
}
