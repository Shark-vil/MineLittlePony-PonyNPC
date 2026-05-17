package com.minelittlepony.ponynpc.dungeon;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** JSON-описание данжа: параметры генерации и награды. */
public class DungeonDefinition {
    public String id;
    public String name = "Dungeon";
    @SerializedName("generate_platform") public boolean generatePlatform = true;
    @SerializedName("platform_width")   public int platformWidth  = 40;
    @SerializedName("platform_length")  public int platformLength = 60;
    public List<RewardDef> rewards = new ArrayList<>();

    public static class RewardDef {
        /** "item", "quest_item", or "currency". */
        public String type;
        /** Item/quest-item identifier (null for currency). */
        public String id;
        public int count = 1;
    }
}
