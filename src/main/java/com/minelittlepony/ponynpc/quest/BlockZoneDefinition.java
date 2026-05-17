package com.minelittlepony.ponynpc.quest;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;

/**
 * Определение зоны интерактивных блоков.
 * Загружается из config/ponynpc/block_zones/<id>.json
 *
 * Три механизма назначения диалогов (можно комбинировать):
 *
 * 1. blocks[] — фиксированные позиции с явным dialog/name.
 *
 * 2. random_picks[] — сервер рандомно выбирает N блоков из зоны и назначает им
 *    особый диалог. Выбор делается per-player при первом взаимодействии
 *    и хранится до дисконнекта. Каждый игрок может получить разные "особые" полки.
 *
 * 3. default_dialog / default_dialogs[] — для всех остальных блоков зоны.
 *    Если указан массив default_dialogs — при каждом клике случайно выбирается один.
 *
 * Полный пример:
 * <pre>
 * {
 *   "id": "library_archive",
 *   "condition": "quest:shadow_book_ch1:stage:find_book",
 *   "world": "minecraft:overworld",
 *   "min_x": 100, "min_y": 60, "min_z": 200,
 *   "max_x": 120, "max_y": 75, "max_z": 220,
 *   "target_block": "minecraft:bookshelf",
 *
 *   "random_picks": [
 *     { "count": 1, "dialog": "library_key_shelf", "name": "Полка с загадочной книгой" }
 *   ],
 *
 *   "default_dialogs": [
 *     "library_shelf_boring",
 *     "library_shelf_funny",
 *     "library_shelf_lore"
 *   ],
 *   "default_name": "Книжная полка",
 *
 *   "blocks": [
 *     { "x": 110, "y": 64, "z": 208, "dialog": "library_curator_note", "name": "Записка куратора" }
 *   ]
 * }
 * </pre>
 */
public final class BlockZoneDefinition {

    public String id = "";
    /** Условие активации (quest-условие, пустая строка = всегда). */
    public String condition = "";
    /** Измерение, например "minecraft:overworld". Пустой = любое. */
    public String world = "";
    public int min_x, min_y, min_z;
    public int max_x, max_y, max_z;
    /** Тип блока-цели, например "minecraft:bookshelf". */
    public String target_block = "";

    /** Диалог по умолчанию (shorthand для одной записи в default_dialogs). */
    public String default_dialog = "";
    /**
     * Пул диалогов по умолчанию. При каждом клике случайно выбирается один.
     * Если пустой — используется default_dialog.
     */
    public List<String> default_dialogs = List.of();
    /** Имя по умолчанию для шапки диалогового экрана. */
    public String default_name = "???";

    /** Фиксированные позиции с явными dialog/name. */
    public List<BlockEntry> blocks = List.of();

    /**
     * Группы рандомных назначений. Сервер при первом взаимодействии игрока
     * с зоной сканирует все matching-блоки, исключает blocks[], перемешивает
     * и назначает count штук каждой группе per-player.
     */
    public List<RandomPickEntry> random_picks = List.of();

    // ── Вложенные типы ───────────────────────────────────────────

    public static final class BlockEntry {
        public int x, y, z;
        public String dialog = "";
        public String name = "";
    }

    public static final class RandomPickEntry {
        /** Количество блоков, назначаемых этой группе. */
        public int count = 1;
        public String dialog = "";
        public String name = "";
    }

    // ── Вспомогательные методы ───────────────────────────────────

    public boolean isInRegion(BlockPos pos, RegistryKey<World> worldKey) {
        if (!world.isEmpty() && !world.equals(worldKey.getValue().toString())) return false;
        return pos.getX() >= min_x && pos.getX() <= max_x
            && pos.getY() >= min_y && pos.getY() <= max_y
            && pos.getZ() >= min_z && pos.getZ() <= max_z;
    }

    /** Возвращает случайный диалог из пула (или single default_dialog). */
    public String resolveDefaultDialog(Random rng) {
        if (default_dialogs != null && !default_dialogs.isEmpty()) {
            return default_dialogs.get(rng.nextInt(default_dialogs.size()));
        }
        return default_dialog;
    }

    /** true если есть хотя бы один источник диалога по умолчанию. */
    public boolean hasDefault() {
        return (default_dialogs != null && !default_dialogs.isEmpty()) || !default_dialog.isEmpty();
    }

    /** true если в зоне настроены random_picks. */
    public boolean hasRandomPicks() {
        return random_picks != null && !random_picks.isEmpty();
    }
}
