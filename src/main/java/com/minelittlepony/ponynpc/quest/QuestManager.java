package com.minelittlepony.ponynpc.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minelittlepony.ponynpc.state.QuestPocketState;
import com.minelittlepony.ponynpc.state.QuestStateStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.*;

/** Загружает квесты из JSON, предоставляет API квестового прогресса. */
public final class QuestManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/QuestManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final QuestManager INSTANCE = new QuestManager();

    private final Map<String, QuestDefinition> quests = new HashMap<>();

    private QuestManager() {}

    public static QuestManager getInstance() { return INSTANCE; }

    // ── Загрузка ──────────────────────────────────────────────────

    public int reload() {
        quests.clear();
        Path questDir = FabricLoader.getInstance().getConfigDir().resolve("ponynpc").resolve("quests");
        if (!Files.exists(questDir)) {
            try {
                Files.createDirectories(questDir);
                copyBuiltinExamples(questDir);
            } catch (IOException e) {
                LOGGER.error("Failed to create quest directory", e);
                return 0;
            }
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(questDir, "*.json")) {
            for (Path file : stream) loadQuest(file);
        } catch (IOException e) {
            LOGGER.error("Failed to read quest directory", e);
        }
        LOGGER.info("Loaded {} quest(s)", quests.size());
        return quests.size();
    }

    private void loadQuest(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            QuestDefinition def = GSON.fromJson(reader, QuestDefinition.class);
            if (def == null) { LOGGER.warn("Empty quest file: {}", file.getFileName()); return; }
            if (def.id == null || def.id.isEmpty()) {
                String fn = file.getFileName().toString();
                def.id = fn.substring(0, fn.lastIndexOf('.'));
            }
            if (!def.isValid()) {
                LOGGER.warn("Quest '{}' invalid (no start_stage '{}') — skipping", def.id, def.startStage);
                return;
            }
            quests.put(def.id, def);
            LOGGER.debug("Loaded quest '{}'", def.id);
        } catch (Exception e) {
            LOGGER.error("Failed to load quest: {}", file.getFileName(), e);
        }
    }

    private void copyBuiltinExamples(Path dir) {
        copyBuiltin(dir, "shadow_book_ch1.json");
    }

    private void copyBuiltin(Path dir, String name) {
        Path target = dir.resolve(name);
        if (Files.exists(target)) return;
        try (var stream = getClass().getResourceAsStream("/data/ponynpc/quests/" + name)) {
            if (stream != null) { Files.copy(stream, target); LOGGER.info("Copied quest example: {}", name); }
        } catch (IOException e) {
            LOGGER.warn("Could not copy quest example '{}'", name, e);
        }
    }

    // ── Поиск ─────────────────────────────────────────────────────

    public Optional<QuestDefinition> getQuest(String questId) {
        return Optional.ofNullable(quests.get(questId));
    }

    public Set<String> getQuestIds() { return Collections.unmodifiableSet(quests.keySet()); }

    // ── Quest API (делегирует к QuestStateStore) ───────────────────

    public boolean isQuestStarted(ServerPlayerEntity player, String questId) {
        return QuestStateStore.getOrCreate(player.getServer()).isStarted(player.getUuid(), questId);
    }

    public boolean isQuestComplete(ServerPlayerEntity player, String questId) {
        return QuestStateStore.getOrCreate(player.getServer()).isComplete(player.getUuid(), questId);
    }

    public boolean isOnStage(ServerPlayerEntity player, String questId, String stageId) {
        return QuestStateStore.getOrCreate(player.getServer()).isOnStage(player.getUuid(), questId, stageId);
    }

    public Optional<String> getCurrentStage(ServerPlayerEntity player, String questId) {
        return QuestStateStore.getOrCreate(player.getServer()).getCurrentStage(player.getUuid(), questId);
    }

    /** Начать квест. Ничего не делает если уже начат. */
    public void startQuest(ServerPlayerEntity player, String questId) {
        QuestDefinition def = quests.get(questId);
        if (def == null) { LOGGER.warn("startQuest: unknown quest '{}'", questId); return; }
        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());
        store.startQuest(player.getUuid(), questId, def.startStage, player.getServerWorld().getTime());
        LOGGER.info("Quest '{}' started for player '{}'", questId, player.getName().getString());
    }

    /**
     * Завершить текущий этап игрока в квесте и перейти к следующему.
     * Если у этапа нет next_stage — квест считается завершённым.
     */
    public void completeCurrentStage(ServerPlayerEntity player, String questId) {
        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());
        Optional<String> currentStageOpt = store.getCurrentStage(player.getUuid(), questId);
        if (currentStageOpt.isEmpty()) {
            LOGGER.warn("completeCurrentStage: player '{}' is not on quest '{}'",
                player.getName().getString(), questId);
            return;
        }
        String currentStage = currentStageOpt.get();
        QuestDefinition def = quests.get(questId);
        if (def == null) return;

        String nextStage = def.getStage(currentStage).map(s -> s.nextStage).orElse(null);
        store.completeCurrentStage(player.getUuid(), questId, currentStage, nextStage,
            player.getServerWorld().getTime());

        if (nextStage == null) {
            LOGGER.info("Quest '{}' COMPLETED by player '{}'", questId, player.getName().getString());
        } else {
            LOGGER.info("Quest '{}' advanced: '{}' → '{}' for player '{}'",
                questId, currentStage, nextStage, player.getName().getString());
        }
    }

    /**
     * Завершить конкретный этап (используется когда нужно пропустить/форсировать).
     */
    public void completeStage(ServerPlayerEntity player, String questId, String stageId) {
        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());
        QuestDefinition def = quests.get(questId);
        if (def == null) return;
        String nextStage = def.getStage(stageId).map(s -> s.nextStage).orElse(null);
        store.completeCurrentStage(player.getUuid(), questId, stageId, nextStage,
            player.getServerWorld().getTime());
    }

    // ── Quest Pocket ───────────────────────────────────────────────

    public boolean hasQuestItem(ServerPlayerEntity player, String itemId) {
        return QuestPocketState.getOrCreate(player.getServer()).hasItem(player.getUuid(), itemId);
    }

    public void giveQuestItem(ServerPlayerEntity player, String itemId, int count) {
        QuestPocketState.getOrCreate(player.getServer()).giveItem(player.getUuid(), itemId, count);
        LOGGER.info("Quest item '{}' x{} given to '{}'", itemId, count, player.getName().getString());

        // Авто-завершение COLLECT_QUEST_ITEM стадий если предмет совпадает
        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());
        for (Map.Entry<String, QuestDefinition> entry : quests.entrySet()) {
            String questId = entry.getKey();
            store.getCurrentStage(player.getUuid(), questId).ifPresent(stageId -> {
                entry.getValue().getStage(stageId).ifPresent(stage -> {
                    if (stage.type == QuestStageType.COLLECT_QUEST_ITEM
                            && itemId.equals(stage.itemId)) {
                        completeCurrentStage(player, questId);
                    }
                });
            });
        }
    }

    public boolean takeQuestItem(ServerPlayerEntity player, String itemId) {
        boolean ok = QuestPocketState.getOrCreate(player.getServer()).takeItem(player.getUuid(), itemId);
        if (ok) LOGGER.info("Quest item '{}' taken from '{}'", itemId, player.getName().getString());
        return ok;
    }

    // ── MULTI_COLLECT ──────────────────────────────────────────────

    /**
     * Отмечает цель собранной. Если набрано required_count — завершает этап.
     * Возвращает true если цель была добавлена (не дубль).
     */
    public boolean collectTarget(ServerPlayerEntity player, String questId, String stageId, String targetId) {
        MinecraftServer server = player.getServer();
        QuestStateStore store = QuestStateStore.getOrCreate(server);
        boolean added = store.collectTarget(player.getUuid(), questId, stageId, targetId);
        if (!added) return false;

        QuestDefinition def = quests.get(questId);
        if (def != null) {
            def.getStage(stageId).ifPresent(stage -> {
                if (stage.type == QuestStageType.MULTI_COLLECT) {
                    int count = store.getCollectedCount(player.getUuid(), questId, stageId);
                    if (count >= stage.getEffectiveRequiredCount()) {
                        completeCurrentStage(player, questId);
                    }
                }
            });
        }
        return true;
    }

    // ── Тик для WAIT_DAYS ─────────────────────────────────────────

    /** Проверяет и завершает WAIT_DAYS этапы для всех онлайн игроков. */
    public void tickWaitDays(MinecraftServer server) {
        long worldTime = server.getOverworld().getTime();
        QuestStateStore store = QuestStateStore.getOrCreate(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            for (Map.Entry<String, QuestDefinition> entry : quests.entrySet()) {
                String questId = entry.getKey();
                QuestDefinition def = entry.getValue();
                Optional<String> stageOpt = store.getCurrentStage(player.getUuid(), questId);
                stageOpt.ifPresent(stageId -> {
                    def.getStage(stageId).ifPresent(stage -> {
                        if (stage.type == QuestStageType.WAIT_DAYS) {
                            long startTime = store.getStageStartTime(player.getUuid(), questId, stageId);
                            if (startTime >= 0 && (worldTime - startTime) >= (long) stage.days * 24000L) {
                                completeCurrentStage(player, questId);
                            }
                        }
                    });
                });
            }
        }
    }
}
