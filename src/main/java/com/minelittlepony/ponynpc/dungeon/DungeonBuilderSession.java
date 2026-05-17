package com.minelittlepony.ponynpc.dungeon;

import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Хранит временный выбор pos1/pos2/spawn/finish для каждого администратора. */
public final class DungeonBuilderSession {

    private static final Map<UUID, DungeonBuilderSession> SESSIONS = new ConcurrentHashMap<>();

    public BlockPos pos1;
    public BlockPos pos2;
    public BlockPos spawn;
    public BlockPos finish;

    private DungeonBuilderSession() {}

    public static DungeonBuilderSession get(UUID playerUuid) {
        return SESSIONS.computeIfAbsent(playerUuid, k -> new DungeonBuilderSession());
    }

    public static void clear(UUID playerUuid) {
        SESSIONS.remove(playerUuid);
    }

    /** true если pos1 и pos2 оба установлены. */
    public boolean hasSelection() { return pos1 != null && pos2 != null; }

    /** true если pos1, pos2 и spawn установлены. */
    public boolean isReadyToSave() { return hasSelection() && spawn != null; }

    /** Минимальный угол выбора (build origin). */
    public BlockPos getMinCorner() {
        if (!hasSelection()) return BlockPos.ORIGIN;
        return new BlockPos(
            Math.min(pos1.getX(), pos2.getX()),
            Math.min(pos1.getY(), pos2.getY()),
            Math.min(pos1.getZ(), pos2.getZ())
        );
    }

    public int getWidth()  {
        return Math.abs(pos2.getX() - pos1.getX()) + 1;
    }
    public int getHeight() {
        return Math.abs(pos2.getY() - pos1.getY()) + 1;
    }
    public int getLength() {
        return Math.abs(pos2.getZ() - pos1.getZ()) + 1;
    }
}
