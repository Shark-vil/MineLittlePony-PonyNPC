package com.minelittlepony.ponynpc.dungeon;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** JSON-описание данжа. Загружается из config/ponynpc/dungeons/. */
public class DungeonDefinition {
    public String id;
    public String name = "Dungeon";

    // ── Зона строительства (устанавливается через /dungeon save) ───
    /** Абсолютная позиция минимального угла (pos1) в измерении ponynpc:dungeon. */
    @SerializedName("build_origin") public IntVec3 buildOrigin;
    /** Размер копируемой области. */
    public SizeVec size = new SizeVec(50, 20, 70);
    /** Куда ставить игрока при входе (смещение от buildOrigin/slotOrigin). */
    @SerializedName("spawn_offset") public IntVec3 spawnOffset = new IntVec3(25, 1, 5);

    // ── NPC или маркер финиша (спаунится при входе, виден только участникам) ──
    @SerializedName("finish_npc") public FinishNpcConfig finishNpc;
    @SerializedName("finish_marker") public FinishMarkerConfig finishMarker;

    // ── Управление временем ────────────────────────────────────────
    /** Установить время суток в данж-измерении при входе. null = не менять.
     *  0=полночь, 6000=рассвет, 12000=полдень, 18000=закат. */
    @SerializedName("fixed_time") public Long fixedTime;

    // ── Защита блоков ──────────────────────────────────────────────
    /** Если true — игроки в инстансе могут ломать блоки. */
    public boolean destructible = false;

    // ── Награды ────────────────────────────────────────────────────
    public List<RewardDef> rewards = new ArrayList<>();

    // ── Вложенные классы ──────────────────────────────────────────

    public static class IntVec3 {
        public int x, y, z;
        public IntVec3() {}
        public IntVec3(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }

    public static class SizeVec {
        public int width, height, length;
        public SizeVec() {}
        public SizeVec(int w, int h, int l) { this.width = w; this.height = h; this.length = l; }
    }

    public static class FinishMarkerConfig {
        public String name = "Exit";
        public IntVec3 offset = new IntVec3(25, 1, 65);
        /** ID диалога, открываемого при клике по маркеру. Если задан — actions игнорируются. */
        @SerializedName("dialog_id") public String dialogId = "";
        public java.util.List<String> actions = new java.util.ArrayList<>();
    }

    public static class FinishNpcConfig {
        @SerializedName("dialog_id") public String dialogId = "";
        public String name = "Exit";
        public String skin = "";
        /** Смещение от slotOrigin (или buildOrigin) где появится NPC. */
        public IntVec3 offset = new IntVec3(25, 1, 65);
    }

    public static class RewardDef {
        public String type;  // "item", "quest_item", "currency"
        public String id;
        public int count = 1;
    }

    // ── Утилиты ──────────────────────────────────────────────────

    public boolean hasBuildOrigin() { return buildOrigin != null; }

    public int getWidth()  { return size != null ? size.width  : 50; }
    public int getHeight() { return size != null ? size.height : 20; }
    public int getLength() { return size != null ? size.length : 70; }
}
