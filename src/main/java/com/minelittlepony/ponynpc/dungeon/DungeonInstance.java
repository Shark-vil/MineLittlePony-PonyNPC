package com.minelittlepony.ponynpc.dungeon;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.*;

/** Активный экземпляр данжа для конкретной группы игроков. */
public class DungeonInstance {
    public final int slotId;
    public final String dungeonId;

    private final Set<UUID> players = new HashSet<>();
    private final Map<UUID, ReturnData> returnData = new HashMap<>();
    private boolean completed = false;

    public DungeonInstance(int slotId, String dungeonId) {
        this.slotId = slotId;
        this.dungeonId = dungeonId;
    }

    public void addPlayer(UUID uuid, ReturnData data) {
        players.add(uuid);
        returnData.put(uuid, data);
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
        returnData.remove(uuid);
    }

    public boolean hasPlayer(UUID uuid)      { return players.contains(uuid); }
    public Set<UUID> getPlayers()            { return Collections.unmodifiableSet(players); }
    public ReturnData getReturnData(UUID uuid){ return returnData.get(uuid); }
    public boolean isEmpty()                 { return players.isEmpty(); }
    public boolean isCompleted()             { return completed; }
    public void markCompleted()              { completed = true; }

    /** X-координата центра слота в данж-измерении. */
    public int getCenterX() { return slotId * 10000 + 5000; }
    /** Z-координата центра — постоянная для всех слотов. */
    public int getCenterZ() { return 5000; }

    /** Позиция возврата игрока при выходе из данжа. */
    public record ReturnData(RegistryKey<World> worldKey,
                             double x, double y, double z,
                             float yaw, float pitch) {}
}
