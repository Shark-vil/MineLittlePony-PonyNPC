package com.minelittlepony.ponynpc.quest;

import com.minelittlepony.ponynpc.state.QuestPocketState;
import com.minelittlepony.ponynpc.state.QuestStateStore;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Проверяет условия диалоговых выборов.
 * Формат строк: см. quest_dev_log.md → Условия.
 */
public final class QuestConditionChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/Conditions");

    private QuestConditionChecker() {}

    /**
     * Проверяет все условия из списка (AND-логика).
     * @return true если все условия выполнены (или список пустой/null)
     */
    public static boolean checkAll(ServerPlayerEntity player, List<String> conditions) {
        if (conditions == null || conditions.isEmpty()) return true;
        for (String condition : conditions) {
            if (!check(player, condition)) return false;
        }
        return true;
    }

    /**
     * Проверяет одно условие.
     * Возвращает true в случае ошибки парсинга (fail-open).
     */
    public static boolean check(ServerPlayerEntity player, String condition) {
        if (condition == null || condition.isBlank()) return true;
        String[] parts = condition.split(":");
        if (parts.length < 3) {
            LOGGER.warn("Invalid condition format: '{}'", condition);
            return true;
        }

        // quest:<questId>:...
        if ("quest".equals(parts[0])) {
            return checkQuestCondition(player, parts);
        }

        LOGGER.warn("Unknown condition namespace: '{}'", parts[0]);
        return true;
    }

    private static boolean checkQuestCondition(ServerPlayerEntity player, String[] parts) {
        if (parts.length < 3) return true;
        String questId = parts[1];
        String type = parts[2];
        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());

        return switch (type) {
            case "stage" -> {
                // quest:<id>:stage:<stageId>
                if (parts.length < 4) yield false;
                yield store.isOnStage(player.getUuid(), questId, parts[3]);
            }
            case "not_started" ->
                // quest:<id>:not_started
                !store.isStarted(player.getUuid(), questId);
            case "complete" ->
                // quest:<id>:complete
                store.isComplete(player.getUuid(), questId);
            case "started" ->
                // quest:<id>:started
                store.isStarted(player.getUuid(), questId);
            case "has_item" -> {
                // quest:<id>:has_item:<itemId>
                if (parts.length < 4) yield false;
                yield QuestPocketState.getOrCreate(player.getServer())
                    .hasItem(player.getUuid(), parts[3]);
            }
            case "counter_gte" -> {
                // quest:<id>:counter_gte:<stageId>:<n>
                if (parts.length < 5) yield false;
                int required;
                try { required = Integer.parseInt(parts[4]); } catch (NumberFormatException e) { yield false; }
                yield store.getCollectedCount(player.getUuid(), questId, parts[3]) >= required;
            }
            default -> {
                LOGGER.warn("Unknown quest condition type: '{}'", type);
                yield true;
            }
        };
    }
}
