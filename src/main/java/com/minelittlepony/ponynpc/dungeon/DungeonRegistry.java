package com.minelittlepony.ponynpc.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Загружает DungeonDefinition из config/ponynpc/dungeons/*.json. */
public final class DungeonRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/DungeonRegistry");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DungeonRegistry INSTANCE = new DungeonRegistry();

    private final Map<String, DungeonDefinition> dungeons = new HashMap<>();

    private DungeonRegistry() {}

    public static DungeonRegistry getInstance() { return INSTANCE; }

    // ── Загрузка ──────────────────────────────────────────────────

    public int reload() {
        dungeons.clear();
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("ponynpc").resolve("dungeons");
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                LOGGER.error("Failed to create dungeon directory", e);
                return 0;
            }
        }
        copyBuiltin(dir, "shadow_ruins.json");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) loadDungeon(file);
        } catch (IOException e) {
            LOGGER.error("Failed to read dungeon directory", e);
        }
        LOGGER.info("Loaded {} dungeon(s)", dungeons.size());
        return dungeons.size();
    }

    private void loadDungeon(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            DungeonDefinition def = GSON.fromJson(reader, DungeonDefinition.class);
            if (def == null) { LOGGER.warn("Empty dungeon file: {}", file.getFileName()); return; }
            if (def.id == null || def.id.isEmpty()) {
                String fn = file.getFileName().toString();
                def.id = fn.substring(0, fn.lastIndexOf('.'));
            }
            dungeons.put(def.id, def);
            LOGGER.debug("Loaded dungeon '{}'", def.id);
        } catch (Exception e) {
            LOGGER.error("Failed to load dungeon: {}", file.getFileName(), e);
        }
    }

    private void copyBuiltin(Path dir, String name) {
        Path target = dir.resolve(name);
        if (Files.exists(target)) return;
        try (var stream = getClass().getResourceAsStream("/data/ponynpc/dungeons/" + name)) {
            if (stream != null) { Files.copy(stream, target); LOGGER.info("Copied dungeon example: {}", name); }
        } catch (IOException e) {
            LOGGER.warn("Could not copy dungeon example '{}'", name, e);
        }
    }

    // ── API ───────────────────────────────────────────────────────

    public Optional<DungeonDefinition> getDungeon(String id) {
        return Optional.ofNullable(dungeons.get(id));
    }

    public Set<String> getDungeonIds() { return Collections.unmodifiableSet(dungeons.keySet()); }

    /** Returns a unique build-zone slot index for a new dungeon (slot → negative X side of dimension). */
    public int getNextBuildSlot() {
        return dungeons.size() + 1;
    }
}
