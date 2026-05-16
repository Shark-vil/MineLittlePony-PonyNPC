package com.minelittlepony.ponynpc.dialog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.*;

public final class DialogManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/DialogManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DialogManager INSTANCE = new DialogManager();

    private final Map<String, DialogData> dialogs = new HashMap<>();

    private DialogManager() {}

    public static DialogManager getInstance() {
        return INSTANCE;
    }

    /**
     * Загружает все JSON-диалоги из config/ponynpc/dialogs/.
     * Вызывается при старте сервера и по команде /ponynpc reload.
     */
    public int reload() {
        dialogs.clear();

        Path dialogDir = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("ponynpc")
            .resolve("dialogs");

        if (!Files.exists(dialogDir)) {
            try {
                Files.createDirectories(dialogDir);
                copyBuiltinExamples(dialogDir);
            } catch (IOException e) {
                LOGGER.error("Failed to create dialog directory", e);
                return 0;
            }
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dialogDir, "*.json")) {
            for (Path file : stream) {
                loadDialog(file);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read dialog directory", e);
        }

        LOGGER.info("Loaded {} dialog(s) from {}", dialogs.size(), dialogDir);
        return dialogs.size();
    }

    private void loadDialog(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            DialogData data = GSON.fromJson(reader, DialogData.class);
            if (data == null) {
                LOGGER.warn("Empty dialog file: {}", file.getFileName());
                return;
            }

            // Автоматический ID из имени файла
            if (data.id == null || data.id.isEmpty()) {
                String fileName = file.getFileName().toString();
                data.id = fileName.substring(0, fileName.lastIndexOf('.'));
            }

            // Если mode не задан — по умолчанию QUEST (обратная совместимость)
            if (data.mode == null) {
                data.mode = DialogMode.QUEST;
            }

            if (!validate(data, file.getFileName().toString())) return;

            dialogs.put(data.id, data);
            LOGGER.debug("Loaded dialog '{}' (mode: {})", data.id, data.mode);
        } catch (Exception e) {
            LOGGER.error("Failed to load dialog file: {}", file.getFileName(), e);
        }
    }

    /** Валидирует загруженный диалог в соответствии с его режимом. */
    private boolean validate(DialogData data, String fileName) {
        return switch (data.mode) {
            case QUEST -> {
                if (!data.nodes.containsKey("start")) {
                    LOGGER.warn("Dialog '{}' (mode=quest) has no 'start' node — skipping", data.id);
                    yield false;
                }
                yield true;
            }
            case RANDOM -> {
                if (data.phrases.isEmpty()) {
                    LOGGER.warn("Dialog '{}' (mode=random) has no phrases — skipping", data.id);
                    yield false;
                }
                yield true;
            }
        };
    }

    /**
     * Копирует встроенные примеры диалогов в папку конфига при первом запуске.
     */
    private void copyBuiltinExamples(Path dir) {
        copyBuiltin(dir, "example.json");
        copyBuiltin(dir, "example_random.json");
    }

    private void copyBuiltin(Path dir, String name) {
        Path target = dir.resolve(name);
        if (Files.exists(target)) return;
        try {
            var stream = getClass().getResourceAsStream("/data/ponynpc/dialogs/" + name);
            if (stream != null) {
                Files.copy(stream, target);
                LOGGER.info("Copied built-in dialog example: {}", name);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not copy built-in dialog '{}'", name, e);
        }
    }

    public Optional<DialogData> getDialog(String id) {
        return Optional.ofNullable(dialogs.get(id));
    }

    public Set<String> getDialogIds() {
        return Collections.unmodifiableSet(dialogs.keySet());
    }

    /** Возвращает загруженные диалоги по режиму (для отладки/UI). */
    public Map<DialogMode, List<String>> getDialogIdsByMode() {
        Map<DialogMode, List<String>> result = new EnumMap<>(DialogMode.class);
        dialogs.forEach((id, data) ->
            result.computeIfAbsent(data.mode, m -> new ArrayList<>()).add(id)
        );
        return result;
    }
}
