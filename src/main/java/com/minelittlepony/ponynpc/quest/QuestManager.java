package com.minelittlepony.ponynpc.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minelittlepony.ponynpc.registry.PonyNpcSounds;
import com.minelittlepony.ponynpc.state.QuestPocketState;
import com.minelittlepony.ponynpc.state.QuestStateStore;
import com.minelittlepony.ponynpc.trigger.ProximityTriggerManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Загружает квесты из JSON, управляет прогрессом.
 *
 * Поддерживает вложенные папки — кладите квесты куда удобно:
 *   quests/main_story/chapter1/shadow_book.json
 *   quests/side/fishing.json
 *
 * ID берётся из поля "id" в JSON. Если поле пустое — из пути относительно
 * папки quests/ без расширения (например "main_story/chapter1/shadow_book").
 *
 * Индексы ускоряют поиск по dungeonId, itemId и WAIT_DAYS квестам:
 * без них поиск — O(all_quests), с ними — O(matching_quests).
 */
public final class QuestManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/QuestManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final QuestManager INSTANCE = new QuestManager();

    private final Map<String, QuestDefinition> quests     = new LinkedHashMap<>();
    // Путь к файлу каждого квеста (для triggerSet и прочих команд записи)
    private final Map<String, Path>            questFiles = new HashMap<>();

    // ── Индексы для быстрого поиска ───────────────────────────────
    // dungeonId → список questId у которых есть DUNGEON-этап с этим данжем
    private final Map<String, List<String>> dungeonToQuests = new HashMap<>();
    // itemId → список questId у которых есть COLLECT_QUEST_ITEM-этап с этим предметом
    private final Map<String, List<String>> itemToQuests    = new HashMap<>();
    // questId у которых есть хотя бы один WAIT_DAYS-этап
    private final Set<String> waitDaysQuestIds = new HashSet<>();

    private QuestManager() {}

    public static QuestManager getInstance() { return INSTANCE; }

    // ── Загрузка ──────────────────────────────────────────────────

    /** Перезагружает все квесты. При наличии сервера перерегистрирует триггеры онлайн-игроков. */
    public int reload(MinecraftServer server) {
        quests.clear();
        questFiles.clear();
        dungeonToQuests.clear();
        itemToQuests.clear();
        waitDaysQuestIds.clear();

        Path questDir = FabricLoader.getInstance().getConfigDir().resolve("ponynpc").resolve("quests");
        if (!Files.exists(questDir)) {
            try { Files.createDirectories(questDir); }
            catch (IOException e) { LOGGER.error("Failed to create quest directory", e); return 0; }
        }

        copyBuiltinExamples(questDir);

        // Рекурсивный обход — поддерживает произвольную вложенность папок
        try (Stream<Path> stream = Files.walk(questDir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json"))
                  .forEach(file -> loadQuest(file, questDir));
        } catch (IOException e) {
            LOGGER.error("Failed to walk quest directory", e);
        }

        buildIndexes();
        LOGGER.info("Loaded {} quest(s)", quests.size());

        // Перерегистрируем proximity triggers для всех онлайн-игроков
        if (server != null) {
            ProximityTriggerManager ptm = ProximityTriggerManager.getInstance();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ptm.clearForPlayer(player.getUuid());
                reRegisterTriggers(player);
            }
        }

        return quests.size();
    }

    /** Перегрузка без сервера — для вызова на SERVER_STARTED когда игроков ещё нет. */
    public int reload() { return reload(null); }

    private void loadQuest(Path file, Path questDir) {
        try (Reader reader = Files.newBufferedReader(file)) {
            QuestDefinition def = GSON.fromJson(reader, QuestDefinition.class);
            if (def == null) { LOGGER.warn("Empty quest file: {}", file); return; }
            if (def.id == null || def.id.isEmpty()) {
                // ID из относительного пути: "main_story/ch1/shadow_book.json" → "main_story/ch1/shadow_book"
                String rel = questDir.relativize(file).toString()
                                     .replace(java.io.File.separatorChar, '/')
                                     .replaceAll("\\.json$", "");
                def.id = rel;
            }
            if (!def.isValid()) {
                LOGGER.warn("Quest '{}' invalid (start_stage '{}' not found) — skipped", def.id, def.startStage);
                return;
            }
            quests.put(def.id, def);
            questFiles.put(def.id, file);
            LOGGER.debug("Loaded quest '{}'", def.id);
        } catch (Exception e) {
            LOGGER.error("Failed to load quest: {}", file, e);
        }
    }

    private void buildIndexes() {
        Map<String, ProximityTriggerConfig> autoStart = new LinkedHashMap<>();

        for (QuestDefinition def : quests.values()) {
            for (Map.Entry<String, QuestStage> entry : def.stages.entrySet()) {
                QuestStage stage = entry.getValue();

                if (stage.type == QuestStageType.DUNGEON && stage.dungeonId != null) {
                    dungeonToQuests.computeIfAbsent(stage.dungeonId, k -> new ArrayList<>()).add(def.id);
                }
                if (stage.type == QuestStageType.COLLECT_QUEST_ITEM && stage.itemId != null) {
                    itemToQuests.computeIfAbsent(stage.itemId, k -> new ArrayList<>()).add(def.id);
                }
                if (stage.type == QuestStageType.WAIT_DAYS) {
                    waitDaysQuestIds.add(def.id);
                }
            }

            if (def.startStage != null) {
                def.getStage(def.startStage)
                   .map(s -> s.proximityTrigger)
                   .filter(Objects::nonNull)
                   .ifPresent(cfg -> autoStart.put(def.id, cfg));
            }
        }

        ProximityTriggerManager.getInstance().setAutoStartQuests(autoStart);
        LOGGER.debug("Indexes: {} dungeon, {} item, {} wait_days, {} auto-start",
            dungeonToQuests.size(), itemToQuests.size(), waitDaysQuestIds.size(), autoStart.size());
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

    /** Возвращает путь к файлу квеста (для команд записи типа triggerSet). */
    public Optional<Path> getQuestFilePath(String questId) {
        return Optional.ofNullable(questFiles.get(questId));
    }

    // ── Quest API ─────────────────────────────────────────────────

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

    /** Начать квест. Регистрирует proximity trigger стартового этапа если есть. */
    public void startQuest(ServerPlayerEntity player, String questId) {
        QuestDefinition def = quests.get(questId);
        if (def == null) { LOGGER.warn("startQuest: unknown quest '{}'", questId); return; }
        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());
        if (store.isStarted(player.getUuid(), questId)) return;

        store.startQuest(player.getUuid(), questId, def.startStage, player.getServerWorld().getTimeOfDay());
        player.playSound(PonyNpcSounds.QUEST_ACCEPT, 0.6f, 1.0f);
        LOGGER.info("Quest '{}' started for '{}'", questId, player.getName().getString());

        def.getStage(def.startStage).ifPresent(stage -> {
            if (stage.proximityTrigger != null) {
                ProximityTriggerManager.getInstance().registerTrigger(
                    player.getUuid(), questId, def.startStage, stage.proximityTrigger);
            }
        });
    }

    /**
     * Завершить текущий этап и перейти к следующему.
     * Обновляет индекс proximity triggers.
     */
    public void completeCurrentStage(ServerPlayerEntity player, String questId) {
        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());
        Optional<String> currentOpt = store.getCurrentStage(player.getUuid(), questId);
        if (currentOpt.isEmpty()) {
            LOGGER.warn("completeCurrentStage: '{}' not on quest '{}'",
                player.getName().getString(), questId);
            return;
        }
        String current = currentOpt.get();
        QuestDefinition def = quests.get(questId);
        if (def == null) return;

        String next = def.getStage(current).map(s -> s.nextStage).orElse(null);
        store.completeCurrentStage(player.getUuid(), questId, current, next,
            player.getServerWorld().getTimeOfDay());

        ProximityTriggerManager ptm = ProximityTriggerManager.getInstance();
        ptm.unregisterForStage(player.getUuid(), questId, current);
        if (next != null) {
            def.getStage(next).ifPresent(nextStage -> {
                if (nextStage.proximityTrigger != null) {
                    ptm.registerTrigger(player.getUuid(), questId, next, nextStage.proximityTrigger);
                }
            });
        }

        if (next == null) {
            player.playSound(PonyNpcSounds.QUEST_COMPLETE, 0.8f, 1.0f);
            LOGGER.info("Quest '{}' COMPLETED by '{}'", questId, player.getName().getString());
        } else {
            player.playSound(PonyNpcSounds.QUEST_STAGE, 0.6f, 1.1f);
            LOGGER.info("Quest '{}': '{}' → '{}' for '{}'",
                questId, current, next, player.getName().getString());
        }
    }

    /** Принудительно завершить конкретный этап (для admin-команд). */
    public void completeStage(ServerPlayerEntity player, String questId, String stageId) {
        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());
        QuestDefinition def = quests.get(questId);
        if (def == null) return;

        String next = def.getStage(stageId).map(s -> s.nextStage).orElse(null);
        store.completeCurrentStage(player.getUuid(), questId, stageId, next,
            player.getServerWorld().getTime());

        ProximityTriggerManager ptm = ProximityTriggerManager.getInstance();
        ptm.unregisterForStage(player.getUuid(), questId, stageId);
        if (next != null) {
            def.getStage(next).ifPresent(nextStage -> {
                if (nextStage.proximityTrigger != null) {
                    ptm.registerTrigger(player.getUuid(), questId, next, nextStage.proximityTrigger);
                }
            });
        }
    }

    // ── Quest Pocket ───────────────────────────────────────────────

    public boolean hasQuestItem(ServerPlayerEntity player, String itemId) {
        return QuestPocketState.getOrCreate(player.getServer()).hasItem(player.getUuid(), itemId);
    }

    /**
     * Выдаёт квестовый предмет и автоматически завершает COLLECT_QUEST_ITEM этапы.
     * Использует индекс itemToQuests — O(matching_quests) вместо O(all_quests).
     */
    public void giveQuestItem(ServerPlayerEntity player, String itemId, int count) {
        QuestPocketState.getOrCreate(player.getServer()).giveItem(player.getUuid(), itemId, count);
        LOGGER.info("Quest item '{}' x{} given to '{}'", itemId, count, player.getName().getString());

        List<String> candidates = itemToQuests.getOrDefault(itemId, List.of());
        if (candidates.isEmpty()) return;

        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());
        for (String questId : candidates) {
            store.getCurrentStage(player.getUuid(), questId).ifPresent(stageId ->
                getQuest(questId).flatMap(def -> def.getStage(stageId)).ifPresent(stage -> {
                    if (stage.type == QuestStageType.COLLECT_QUEST_ITEM && itemId.equals(stage.itemId)) {
                        completeCurrentStage(player, questId);
                    }
                })
            );
        }
    }

    public boolean takeQuestItem(ServerPlayerEntity player, String itemId) {
        boolean ok = QuestPocketState.getOrCreate(player.getServer()).takeItem(player.getUuid(), itemId);
        if (ok) LOGGER.info("Quest item '{}' taken from '{}'", itemId, player.getName().getString());
        return ok;
    }

    // ── MULTI_COLLECT ──────────────────────────────────────────────

    public boolean collectTarget(ServerPlayerEntity player, String questId, String stageId, String targetId) {
        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());
        boolean added = store.collectTarget(player.getUuid(), questId, stageId, targetId);
        if (!added) return false;

        getQuest(questId).flatMap(def -> def.getStage(stageId)).ifPresent(stage -> {
            if (stage.type == QuestStageType.MULTI_COLLECT) {
                int count = store.getCollectedCount(player.getUuid(), questId, stageId);
                if (count >= stage.getEffectiveRequiredCount()) completeCurrentStage(player, questId);
            }
        });
        return true;
    }

    /**
     * Завершает DUNGEON-этапы для данного данжа.
     * Использует индекс dungeonToQuests — O(matching_quests) вместо O(all_quests).
     */
    public void completeDungeonStages(ServerPlayerEntity player, String dungeonId) {
        List<String> candidates = dungeonToQuests.getOrDefault(dungeonId, List.of());
        if (candidates.isEmpty()) return;

        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());
        for (String questId : candidates) {
            store.getCurrentStage(player.getUuid(), questId).ifPresent(stageId ->
                getQuest(questId).flatMap(def -> def.getStage(stageId)).ifPresent(stage -> {
                    if (stage.type == QuestStageType.DUNGEON && dungeonId.equals(stage.dungeonId)) {
                        completeCurrentStage(player, questId);
                    }
                })
            );
        }
    }

    // ── WAIT_DAYS ─────────────────────────────────────────────────

    /**
     * Проверяет и завершает WAIT_DAYS этапы.
     * Использует индекс waitDaysQuestIds — перебираются только релевантные квесты.
     */
    public void tickWaitDays(MinecraftServer server) {
        if (waitDaysQuestIds.isEmpty()) return;
        long worldTime = server.getOverworld().getTimeOfDay();
        QuestStateStore store = QuestStateStore.getOrCreate(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            for (String questId : waitDaysQuestIds) {
                store.getCurrentStage(player.getUuid(), questId).ifPresent(stageId ->
                    getQuest(questId).flatMap(def -> def.getStage(stageId)).ifPresent(stage -> {
                        if (stage.type == QuestStageType.WAIT_DAYS) {
                            long startTime = store.getStageStartTime(player.getUuid(), questId, stageId);
                            if (startTime >= 0 && (worldTime - startTime) >= (long) stage.days * 24000L) {
                                completeCurrentStage(player, questId);
                            }
                        }
                    })
                );
            }
        }
    }

    // ── Login hooks ───────────────────────────────────────────────

    /**
     * Вызывается при входе игрока:
     * 1. Восстанавливает proximity triggers для активных этапов
     * 2. Проверяет WAIT_DAYS (мог истечь пока игрок был оффлайн)
     */
    public void onPlayerJoin(ServerPlayerEntity player) {
        reRegisterTriggers(player);
        checkWaitDaysOnJoin(player);
    }

    private void reRegisterTriggers(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());

        Map<String, String> activeStages = new HashMap<>();
        for (String questId : store.getActiveQuestIds(uuid)) {
            store.getCurrentStage(uuid, questId).ifPresent(stageId -> activeStages.put(questId, stageId));
        }
        ProximityTriggerManager.getInstance().onPlayerJoin(uuid, activeStages);
    }

    private void checkWaitDaysOnJoin(ServerPlayerEntity player) {
        if (waitDaysQuestIds.isEmpty()) return;
        long worldTime = player.getServerWorld().getTimeOfDay();
        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());

        for (String questId : waitDaysQuestIds) {
            store.getCurrentStage(player.getUuid(), questId).ifPresent(stageId ->
                getQuest(questId).flatMap(def -> def.getStage(stageId)).ifPresent(stage -> {
                    if (stage.type == QuestStageType.WAIT_DAYS) {
                        long startTime = store.getStageStartTime(player.getUuid(), questId, stageId);
                        if (startTime >= 0 && (worldTime - startTime) >= (long) stage.days * 24000L) {
                            completeCurrentStage(player, questId);
                        }
                    }
                })
            );
        }
    }
}
