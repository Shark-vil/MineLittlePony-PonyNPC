package com.minelittlepony.ponynpc.quest;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Определение зоны интерактивных блоков.
 * Загружается из config/ponynpc/block_zones/<id>.json
 *
 * Пример:
 * <pre>
 * {
 *   "id": "library_archive",
 *   "condition": "quest:shadow_book_ch1:stage:find_book_in_archive",
 *   "world": "minecraft:overworld",
 *   "min_x": 95, "min_y": 62, "min_z": 195,
 *   "max_x": 110, "max_y": 70, "max_z": 210,
 *   "target_block": "minecraft:bookshelf",
 *   "default_dialog": "library_random_shelf",
 *   "default_name": "Книжная полка",
 *   "blocks": [
 *     { "x": 100, "y": 64, "z": 200, "dialog": "library_shadow_book_shelf", "name": "Полка с загадочной книгой" }
 *   ]
 * }
 * </pre>
 *
 * - Правый клик по target_block внутри региона (при выполненном condition) открывает диалог.
 * - Конкретные позиции в "blocks" могут переопределить dialog/name.
 * - Все остальные совпадающие блоки в зоне получают default_dialog.
 */
public final class BlockZoneDefinition {

    public String id = "";
    /** Условие видимости зоны — одна строка в формате quest-условий (пустая = всегда активна). */
    public String condition = "";
    /** Идентификатор измерения, например "minecraft:overworld". Пустой = любое измерение. */
    public String world = "";
    public int min_x, min_y, min_z;
    public int max_x, max_y, max_z;
    /** Тип блока, например "minecraft:bookshelf". */
    public String target_block = "";
    /** Диалог по умолчанию для всех блоков в зоне, не указанных в blocks[]. */
    public String default_dialog = "";
    /** Имя источника диалога по умолчанию (отображается в шапке диалогового экрана). */
    public String default_name = "???";
    /** Конкретные блоки с переопределёнными dialog/name. */
    public List<BlockEntry> blocks = List.of();

    public static final class BlockEntry {
        public int x, y, z;
        public String dialog = "";
        public String name = "";
    }

    public boolean isInRegion(BlockPos pos, RegistryKey<World> worldKey) {
        if (!world.isEmpty() && !world.equals(worldKey.getValue().toString())) return false;
        return pos.getX() >= min_x && pos.getX() <= max_x
            && pos.getY() >= min_y && pos.getY() <= max_y
            && pos.getZ() >= min_z && pos.getZ() <= max_z;
    }

    public String getDialogFor(BlockPos pos) {
        if (blocks != null) {
            for (BlockEntry e : blocks) {
                if (e.x == pos.getX() && e.y == pos.getY() && e.z == pos.getZ()) {
                    return e.dialog.isEmpty() ? default_dialog : e.dialog;
                }
            }
        }
        return default_dialog;
    }

    public String getNameFor(BlockPos pos) {
        if (blocks != null) {
            for (BlockEntry e : blocks) {
                if (e.x == pos.getX() && e.y == pos.getY() && e.z == pos.getZ()) {
                    return e.name.isEmpty() ? default_name : e.name;
                }
            }
        }
        return default_name;
    }
}
