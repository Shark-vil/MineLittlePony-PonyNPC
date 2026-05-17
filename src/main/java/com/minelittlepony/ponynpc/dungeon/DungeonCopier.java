package com.minelittlepony.ponynpc.dungeon;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Копирует блоки из зоны строительства в слот игрока и очищает слот после выхода.
 * Поддерживает block entities (сундуки, таблички, и т.д.).
 */
public final class DungeonCopier {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/DungeonCopier");
    private static final int SET_FLAGS = 3; // notify clients + update comparators

    private DungeonCopier() {}

    /**
     * Копирует блоки из srcOrigin в dstOrigin (включая block entity NBT).
     * srcOrigin и dstOrigin — минимальные углы (pos1).
     */
    public static void copy(ServerWorld world, BlockPos srcOrigin, BlockPos dstOrigin,
                             int width, int height, int length) {
        LOGGER.debug("Copying dungeon {}x{}x{} from {} to {}", width, height, length, srcOrigin, dstOrigin);
        preloadChunks(world, dstOrigin, width, height, length);

        BlockPos.Mutable src = new BlockPos.Mutable();
        BlockPos.Mutable dst = new BlockPos.Mutable();

        // First pass: set block states (no block entities yet)
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < length; dz++) {
                    src.set(srcOrigin.getX() + dx, srcOrigin.getY() + dy, srcOrigin.getZ() + dz);
                    dst.set(dstOrigin.getX() + dx, dstOrigin.getY() + dy, dstOrigin.getZ() + dz);
                    world.setBlockState(dst, world.getBlockState(src), SET_FLAGS);
                }
            }
        }

        // Second pass: copy block entity NBT (block entities exist after block states are set)
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < length; dz++) {
                    src.set(srcOrigin.getX() + dx, srcOrigin.getY() + dy, srcOrigin.getZ() + dz);
                    dst.set(dstOrigin.getX() + dx, dstOrigin.getY() + dy, dstOrigin.getZ() + dz);
                    copyBlockEntity(world, src, dst.toImmutable());
                }
            }
        }
        LOGGER.info("Dungeon copy done: {} → {} ({}x{}x{})", srcOrigin, dstOrigin, width, height, length);
    }

    /**
     * Очищает слот: заменяет все блоки воздухом и удаляет не-игровые сущности.
     */
    public static void clear(ServerWorld world, BlockPos origin, int width, int height, int length) {
        LOGGER.debug("Clearing dungeon slot at {} ({}x{}x{})", origin, width, height, length);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        var air = net.minecraft.block.Blocks.AIR.getDefaultState();

        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < length; dz++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!world.getBlockState(pos).isAir()) {
                        world.setBlockState(pos, air, SET_FLAGS);
                    }
                }
            }
        }

        // Remove non-player entities in the area
        Box box = new Box(origin.getX(), origin.getY(), origin.getZ(),
                          origin.getX() + width, origin.getY() + height, origin.getZ() + length);
        world.getEntitiesByClass(Entity.class, box, e -> !(e instanceof PlayerEntity))
             .forEach(Entity::discard);

        LOGGER.info("Dungeon slot cleared at {}", origin);
    }

    private static void copyBlockEntity(ServerWorld world, BlockPos.Mutable src, BlockPos dst) {
        BlockEntity srcBe = world.getBlockEntity(src);
        if (srcBe == null) return;

        NbtCompound nbt = srcBe.createNbt(world.getRegistryManager());
        // Adjust stored position to destination
        nbt.putInt("x", dst.getX());
        nbt.putInt("y", dst.getY());
        nbt.putInt("z", dst.getZ());

        BlockEntity dstBe = world.getBlockEntity(dst);
        if (dstBe != null) {
            dstBe.read(nbt, world.getRegistryManager());
            dstBe.markDirty();
        }
    }

    private static void preloadChunks(ServerWorld world, BlockPos origin,
                                       int width, int height, int length) {
        int minCX = origin.getX() >> 4;
        int maxCX = (origin.getX() + width - 1) >> 4;
        int minCZ = origin.getZ() >> 4;
        int maxCZ = (origin.getZ() + length - 1) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                world.getChunk(cx, cz);
            }
        }
    }
}
