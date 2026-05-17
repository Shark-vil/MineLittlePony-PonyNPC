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
    /**
     * Дополнительные quest-действия при срабатывании.
     * Формат — те же строки что и в DialogChoice.actions.
     * Пример: "quest:my_quest:complete_stage"
     */
    public java.util.List<String> actions = new java.util.ArrayList<>();
}
