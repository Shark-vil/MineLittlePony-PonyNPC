package com.minelittlepony.ponynpc.command;

import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Временная сессия разметки block_zone для одного администратора. */
public final class ZoneBuilderSession {

    private static final Map<UUID, ZoneBuilderSession> SESSIONS = new ConcurrentHashMap<>();

    public BlockPos pos1;
    public BlockPos pos2;
    public RegistryKey<World> world;
    public String targetBlock   = "";
    public String condition     = "";
    public String defaultDialog = "";
    public String defaultName   = "???";
    public final List<MarkedBlock> blocks = new ArrayList<>();

    private ZoneBuilderSession() {}

    public static ZoneBuilderSession get(UUID uuid) {
        return SESSIONS.computeIfAbsent(uuid, k -> new ZoneBuilderSession());
    }

    public static void clear(UUID uuid) {
        SESSIONS.remove(uuid);
    }

    public boolean hasSelection() { return pos1 != null && pos2 != null; }

    public boolean isReadyToSave() {
        return hasSelection() && !targetBlock.isEmpty() && !defaultDialog.isEmpty();
    }

    public BlockPos getMinCorner() {
        return new BlockPos(
            Math.min(pos1.getX(), pos2.getX()),
            Math.min(pos1.getY(), pos2.getY()),
            Math.min(pos1.getZ(), pos2.getZ()));
    }

    public BlockPos getMaxCorner() {
        return new BlockPos(
            Math.max(pos1.getX(), pos2.getX()),
            Math.max(pos1.getY(), pos2.getY()),
            Math.max(pos1.getZ(), pos2.getZ()));
    }

    public void markBlock(BlockPos pos, String dialog, String name) {
        blocks.removeIf(b -> b.x == pos.getX() && b.y == pos.getY() && b.z == pos.getZ());
        MarkedBlock m = new MarkedBlock();
        m.x = pos.getX(); m.y = pos.getY(); m.z = pos.getZ();
        m.dialog = dialog;
        m.name = name;
        blocks.add(m);
    }

    public boolean unmarkBlock(BlockPos pos) {
        return blocks.removeIf(b -> b.x == pos.getX() && b.y == pos.getY() && b.z == pos.getZ());
    }

    public static final class MarkedBlock {
        public int x, y, z;
        public String dialog;
        public String name;

        public String format() {
            return "(" + x + "," + y + "," + z + ") → " + dialog + (name.isEmpty() ? "" : " [" + name + "]");
        }
    }
}
