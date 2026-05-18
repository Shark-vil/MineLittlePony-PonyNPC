package com.minelittlepony.ponynpc.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class BlockZoneRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/BlockZones");
    private static final Gson GSON = new GsonBuilder().create();
    private static final BlockZoneRegistry INSTANCE = new BlockZoneRegistry();

    private final List<BlockZoneDefinition> zones = new ArrayList<>();

    private BlockZoneRegistry() {}

    public static BlockZoneRegistry getInstance() { return INSTANCE; }

    public List<BlockZoneDefinition> getZones() { return Collections.unmodifiableList(zones); }

    public int reload() {
        zones.clear();

        Path dir = FabricLoader.getInstance().getConfigDir()
            .resolve("ponynpc").resolve("block_zones");

        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                LOGGER.error("Failed to create block_zones directory", e);
                return 0;
            }
        }
        copyBuiltinExamples(dir);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    BlockZoneDefinition def = GSON.fromJson(reader, BlockZoneDefinition.class);
                    if (def != null && !def.id.isEmpty()) {
                        zones.add(def);
                    } else {
                        LOGGER.warn("Skipping block zone with missing id: {}", file.getFileName());
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load block zone from {}", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read block_zones directory", e);
        }

        LOGGER.info("Loaded {} block zone(s)", zones.size());
        return zones.size();
    }

    private void copyBuiltinExamples(Path dir) {
        copyBuiltin(dir, "library_archive.json");
    }

    private void copyBuiltin(Path dir, String name) {
        Path target = dir.resolve(name);
        if (Files.exists(target)) return;
        try (var stream = getClass().getResourceAsStream("/data/ponynpc/block_zones/" + name)) {
            if (stream != null) {
                Files.copy(stream, target);
                LOGGER.info("Copied block zone example: {}", name);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not copy block zone example '{}'", name, e);
        }
    }
}
