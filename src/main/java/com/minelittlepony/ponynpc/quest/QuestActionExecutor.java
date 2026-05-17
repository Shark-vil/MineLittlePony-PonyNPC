package com.minelittlepony.ponynpc.quest;

import com.minelittlepony.ponynpc.dungeon.DungeonInstanceManager;
import com.minelittlepony.ponynpc.entity.PonyNpcEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Выполняет действия диалоговых выборов и proximity-триггеров.
 * Формат строк: см. quest_dev_log.md → Действия.
 */
public final class QuestActionExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/Actions");

    private QuestActionExecutor() {}

    /**
     * Выполняет все действия из списка.
     * @param player   игрок
     * @param npc      NPC с которым взаимодействует игрок (может быть null)
     */
    public static void executeAll(ServerPlayerEntity player, PonyNpcEntity npc, List<String> actions) {
        if (actions == null || actions.isEmpty()) return;
        for (String action : actions) execute(player, npc, action);
    }

    /** Выполняет одно действие. */
    public static void execute(ServerPlayerEntity player, PonyNpcEntity npc, String action) {
        if (action == null || action.isBlank()) return;
        String[] parts = action.split(":");

        if ("quest".equals(parts[0])) {
            executeQuestAction(player, npc, parts);
        } else if ("ghost_npc".equals(parts[0])) {
            executeGhostNpcAction(player, npc, parts);
        } else if ("dungeon".equals(parts[0])) {
            executeDungeonAction(player, parts);
        } else {
            LOGGER.warn("Unknown action namespace: '{}'", parts[0]);
        }
    }

    // ── Quest actions ─────────────────────────────────────────────

    private static void executeQuestAction(ServerPlayerEntity player, PonyNpcEntity npc, String[] parts) {
        if (parts.length < 3) { LOGGER.warn("Quest action too short: {}", String.join(":", parts)); return; }
        String questId = parts[1];
        String type = parts[2];
        QuestManager qm = QuestManager.getInstance();

        switch (type) {
            case "start" -> {
                // quest:<id>:start
                qm.startQuest(player, questId);
                sendQuestMessage(player, "Quest started: " + questId);
            }
            case "complete_stage" -> {
                // quest:<id>:complete_stage  OR  quest:<id>:complete_stage:<stageId>
                if (parts.length >= 4) {
                    qm.completeStage(player, questId, parts[3]);
                } else {
                    qm.completeCurrentStage(player, questId);
                }
            }
            case "give_quest_item" -> {
                // quest:<id>:give_quest_item:<itemId>
                if (parts.length < 4) { LOGGER.warn("give_quest_item missing itemId"); return; }
                qm.giveQuestItem(player, parts[3], 1);
                sendQuestMessage(player, "Received quest item: " + parts[3]);
            }
            case "give_quest_item_multi" -> {
                // quest:<id>:give_quest_item_multi:<itemId>:<count>
                if (parts.length < 5) { LOGGER.warn("give_quest_item_multi missing args"); return; }
                int count;
                try { count = Integer.parseInt(parts[4]); } catch (NumberFormatException e) { count = 1; }
                qm.giveQuestItem(player, parts[3], count);
                sendQuestMessage(player, "Received quest item: " + parts[3] + " x" + count);
            }
            case "take_quest_item" -> {
                // quest:<id>:take_quest_item:<itemId>
                if (parts.length < 4) { LOGGER.warn("take_quest_item missing itemId"); return; }
                qm.takeQuestItem(player, parts[3]);
            }
            case "give_currency" -> {
                // quest:<id>:give_currency:<amount>
                if (parts.length < 4) { LOGGER.warn("give_currency missing amount"); return; }
                int amount;
                try { amount = Integer.parseInt(parts[3]); } catch (NumberFormatException e) { amount = 1; }
                giveCurrency(player, amount);
            }
            case "give_item" -> {
                // quest:<id>:give_item:<namespace>:<name>:<count>
                // e.g.  quest:my_quest:give_item:minecraft:iron_chestplate:1
                if (parts.length < 6) { LOGGER.warn("give_item missing args"); return; }
                String itemId = parts[3] + ":" + parts[4];
                int count;
                try { count = Integer.parseInt(parts[5]); } catch (NumberFormatException e) { count = 1; }
                giveMinecraftItem(player, itemId, count);
            }
            case "collect" -> {
                // quest:<id>:collect:<stageId>:<targetId>
                if (parts.length < 5) { LOGGER.warn("collect missing args"); return; }
                boolean added = qm.collectTarget(player, questId, parts[3], parts[4]);
                if (added) {
                    sendQuestMessage(player, "Progress updated for: " + parts[3]);
                }
            }
            default -> LOGGER.warn("Unknown quest action type: '{}'", type);
        }
    }

    // ── Dungeon actions ───────────────────────────────────────────

    private static void executeDungeonAction(ServerPlayerEntity player, String[] parts) {
        if (parts.length < 2) return;
        MinecraftServer server = player.getServer();
        DungeonInstanceManager dim = DungeonInstanceManager.getInstance();
        switch (parts[1]) {
            case "enter" -> {
                // dungeon:enter:<dungeonId>
                if (parts.length < 3) { LOGGER.warn("dungeon:enter missing dungeonId"); return; }
                dim.enterDungeon(player, parts[2], server);
            }
            case "exit" ->
                // dungeon:exit
                dim.exitDungeon(player, server);
            case "complete" ->
                // dungeon:complete (rewards all players in the instance, teleports back)
                dim.completeDungeon(player, server);
            default -> LOGGER.warn("Unknown dungeon action: '{}'", parts[1]);
        }
    }

    // ── Ghost NPC actions ─────────────────────────────────────────

    private static void executeGhostNpcAction(ServerPlayerEntity player, PonyNpcEntity npc, String[] parts) {
        if (parts.length < 2) return;
        if ("despawn".equals(parts[1]) && npc != null && npc.isGhostNpc()) {
            npc.discard();
            LOGGER.debug("Ghost NPC despawned after dialog for '{}'", player.getName().getString());
        }
    }

    // ── Утилиты ───────────────────────────────────────────────────

    private static void giveCurrency(ServerPlayerEntity player, int amount) {
        if (amount <= 0) return;
        // Раздаём стаки по 64
        while (amount > 0) {
            int batch = Math.min(amount, 64);
            player.giveItemStack(new ItemStack(Items.GOLD_NUGGET, batch));
            amount -= batch;
        }
        sendQuestMessage(player, "Received " + amount + " gold nugget(s)");
    }

    private static void giveMinecraftItem(ServerPlayerEntity player, String itemId, int count) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) { LOGGER.warn("Invalid item identifier: '{}'", itemId); return; }
        Item item = Registries.ITEM.get(id);
        if (item == Items.AIR) { LOGGER.warn("Item not found: '{}'", itemId); return; }
        int remaining = count;
        while (remaining > 0) {
            int batch = Math.min(remaining, item.getMaxCount());
            player.giveItemStack(new ItemStack(item, batch));
            remaining -= batch;
        }
    }

    private static void sendQuestMessage(ServerPlayerEntity player, String msg) {
        player.sendMessage(Text.literal("[Quest] " + msg), true);
    }
}
