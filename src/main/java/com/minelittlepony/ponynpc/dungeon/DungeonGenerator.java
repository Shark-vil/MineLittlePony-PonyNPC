package com.minelittlepony.ponynpc.dungeon;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Размещает физическую платформу данжа в данж-измерении. */
public final class DungeonGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/DungeonGenerator");
    private static final int WALL_HEIGHT = 4;
    // Flags: notify clients (1) + update comparators (2)
    private static final int SET_FLAGS = 3;

    private DungeonGenerator() {}

    /**
     * Генерирует платформу данжа.
     * Вход — в центре южной стены (low Z), финиш — у северной стены (high Z).
     */
    public static void generatePlatform(ServerWorld world,
                                         int originX, int originY, int originZ,
                                         int width, int length) {
        LOGGER.debug("Generating dungeon platform at ({},{},{}) size {}x{}",
            originX, originY, originZ, width, length);
        preloadChunks(world, originX, originZ, originX + width - 1, originZ + length - 1);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        // ── Floor ─────────────────────────────────────────────────
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < length; dz++) {
                world.setBlockState(pos.set(originX + dx, originY, originZ + dz),
                    Blocks.STONE_BRICKS.getDefaultState(), SET_FLAGS);
            }
        }

        // ── Walls (WALL_HEIGHT tall) ───────────────────────────────
        for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
            // South (entrance side, low Z)
            for (int dx = 0; dx < width; dx++)
                world.setBlockState(pos.set(originX + dx, originY + dy, originZ),
                    Blocks.STONE_BRICKS.getDefaultState(), SET_FLAGS);
            // North (finish side, high Z)
            for (int dx = 0; dx < width; dx++)
                world.setBlockState(pos.set(originX + dx, originY + dy, originZ + length - 1),
                    Blocks.STONE_BRICKS.getDefaultState(), SET_FLAGS);
            // West
            for (int dz = 1; dz < length - 1; dz++)
                world.setBlockState(pos.set(originX, originY + dy, originZ + dz),
                    Blocks.STONE_BRICKS.getDefaultState(), SET_FLAGS);
            // East
            for (int dz = 1; dz < length - 1; dz++)
                world.setBlockState(pos.set(originX + width - 1, originY + dy, originZ + dz),
                    Blocks.STONE_BRICKS.getDefaultState(), SET_FLAGS);
        }

        // ── Entrance: 3-wide, 3-tall gap in south wall ─────────────
        int entranceCenterX = originX + width / 2;
        for (int dy = 1; dy <= 3; dy++) {
            for (int ddx = -1; ddx <= 1; ddx++) {
                world.setBlockState(pos.set(entranceCenterX + ddx, originY + dy, originZ),
                    Blocks.AIR.getDefaultState(), SET_FLAGS);
            }
        }

        // ── Lighting: glowstone at wall-tops every 6 blocks ────────
        for (int dx = 3; dx < width; dx += 6) {
            world.setBlockState(pos.set(originX + dx, originY + WALL_HEIGHT, originZ),
                Blocks.GLOWSTONE.getDefaultState(), SET_FLAGS);
            world.setBlockState(pos.set(originX + dx, originY + WALL_HEIGHT, originZ + length - 1),
                Blocks.GLOWSTONE.getDefaultState(), SET_FLAGS);
        }
        for (int dz = 3; dz < length; dz += 6) {
            world.setBlockState(pos.set(originX, originY + WALL_HEIGHT, originZ + dz),
                Blocks.GLOWSTONE.getDefaultState(), SET_FLAGS);
            world.setBlockState(pos.set(originX + width - 1, originY + WALL_HEIGHT, originZ + dz),
                Blocks.GLOWSTONE.getDefaultState(), SET_FLAGS);
        }

        // ── Finish zone: 3×3 gold blocks near north wall ───────────
        int finishCX = originX + width / 2;
        int finishZ  = originZ + length - 3;
        for (int ddx = -1; ddx <= 1; ddx++) {
            for (int ddz = -1; ddz <= 1; ddz++) {
                world.setBlockState(pos.set(finishCX + ddx, originY, finishZ + ddz),
                    Blocks.GOLD_BLOCK.getDefaultState(), SET_FLAGS);
            }
        }

        LOGGER.info("Dungeon platform generated: ({},{},{}) {}x{}", originX, originY, originZ, width, length);
    }

    private static void preloadChunks(ServerWorld world,
                                       int minX, int minZ, int maxX, int maxZ) {
        int minCX = minX >> 4, maxCX = maxX >> 4;
        int minCZ = minZ >> 4, maxCZ = maxZ >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                world.getChunk(cx, cz);
            }
        }
    }
}
