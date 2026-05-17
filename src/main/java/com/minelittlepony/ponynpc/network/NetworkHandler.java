package com.minelittlepony.ponynpc.network;

import com.minelittlepony.ponynpc.dialog.*;
import com.minelittlepony.ponynpc.entity.PonyNpcEntity;
import com.minelittlepony.ponynpc.ghost.GhostNpcManager;
import com.minelittlepony.ponynpc.quest.*;
import com.minelittlepony.ponynpc.registry.PonyNpcEntities;
import com.minelittlepony.ponynpc.state.PlayerDialogState;
import com.minelittlepony.ponynpc.state.PlayerQuestState;
import com.minelittlepony.ponynpc.state.QuestStateStore;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public final class NetworkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/Network");

    public static final String RANDOM_NODE_ID = "__random__";

    public static void registerServer() {
        PayloadTypeRegistry.playS2C().register(OpenDialogPayload.ID, OpenDialogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CloseDialogPayload.ID, CloseDialogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QuestJournalPayload.ID, QuestJournalPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DialogChoicePayload.ID, DialogChoicePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestQuestJournalPayload.ID, RequestQuestJournalPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(DialogChoicePayload.ID, (payload, context) -> {
            context.server().execute(() -> handleChoice(payload, context.player()));
        });
        ServerPlayNetworking.registerGlobalReceiver(RequestQuestJournalPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                QuestJournalPayload journal = buildJournal(context.player());
                ServerPlayNetworking.send(context.player(), journal);
            });
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  ДНЕВНИК КВЕСТОВ
    // ══════════════════════════════════════════════════════════════

    private static QuestJournalPayload buildJournal(ServerPlayerEntity player) {
        UUID uuid   = player.getUuid();
        QuestStateStore store = QuestStateStore.getOrCreate(player.getServer());
        QuestManager qm       = QuestManager.getInstance();

        List<QuestJournalPayload.QuestEntry> result = new ArrayList<>();

        for (String questId : qm.getQuestIds()) {
            if (!store.isStarted(uuid, questId)) continue;

            Optional<QuestDefinition> defOpt = qm.getQuest(questId);
            if (defOpt.isEmpty()) continue;
            QuestDefinition def = defOpt.get();

            Optional<PlayerQuestState> stateOpt = store.getState(uuid, questId);
            if (stateOpt.isEmpty()) continue;
            PlayerQuestState state = stateOpt.get();

            boolean complete = state.isComplete();

            // Обходим цепочку этапов от startStage по nextStage
            List<QuestJournalPayload.StageEntry> stages = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            String stageId = def.startStage;
            while (stageId != null && !visited.contains(stageId)) {
                visited.add(stageId);
                QuestStage stage = def.stages.get(stageId);
                if (stage == null) break;

                int status;
                if (complete || state.isStageComplete(stageId)) {
                    status = 2;
                } else if (state.isOnStage(stageId)) {
                    status = 1;
                } else {
                    status = 0;
                }

                int collected = 0, required = 0;
                if (stage.type == QuestStageType.MULTI_COLLECT) {
                    collected = store.getCollectedCount(uuid, questId, stageId);
                    required  = stage.getEffectiveRequiredCount();
                }

                stages.add(new QuestJournalPayload.StageEntry(
                    stage.description, status, collected, required));
                stageId = stage.nextStage;
            }

            String displayName = (def.name == null || def.name.isEmpty()) ? questId : def.name;
            result.add(new QuestJournalPayload.QuestEntry(displayName, complete, stages));
        }

        return new QuestJournalPayload(result);
    }

    // ══════════════════════════════════════════════════════════════
    //  ОТКРЫТИЕ ДИАЛОГА
    // ══════════════════════════════════════════════════════════════

    public static void openDialogForPlayer(ServerPlayerEntity player, PonyNpcEntity npc) {
        String dialogId = npc.getDialogId();
        if (dialogId.isEmpty()) {
            player.sendMessage(Text.literal("[PonyNPC] This NPC has no dialog configured."), false);
            return;
        }

        Optional<DialogData> dialogOpt = DialogManager.getInstance().getDialog(dialogId);
        if (dialogOpt.isEmpty()) {
            player.sendMessage(Text.literal("[PonyNPC] Dialog not found: " + dialogId), false);
            return;
        }

        DialogData dialog = dialogOpt.get();

        switch (dialog.mode) {
            case RANDOM -> openRandomDialog(player, npc, dialog);
            case QUEST  -> openQuestDialog(player, npc, dialog);
        }
    }

    // ── RANDOM ────────────────────────────────────────────────────

    private static void openRandomDialog(ServerPlayerEntity player, PonyNpcEntity npc, DialogData dialog) {
        String phrase = dialog.getRandomPhrase().orElse("...");
        String sound = (dialog.sound != null && !dialog.sound.isEmpty()) ? dialog.sound : "ponynpc:talk";
        int soundChars = Math.max(1, dialog.soundChars);

        ServerPlayNetworking.send(player, new OpenDialogPayload(
            npc.getUuid(), npc.getNpcDisplayName(), dialog.id,
            RANDOM_NODE_ID, phrase, sound, soundChars, List.of()
        ));
    }

    // ── QUEST ─────────────────────────────────────────────────────

    private static void openQuestDialog(ServerPlayerEntity player, PonyNpcEntity npc, DialogData dialog) {
        Optional<DialogNode> startNodeOpt = dialog.getNode("start");
        if (startNodeOpt.isEmpty()) {
            player.sendMessage(Text.literal("[PonyNPC] Dialog '" + dialog.id + "' has no 'start' node."), false);
            return;
        }
        PlayerDialogState state = PlayerDialogState.getOrCreate(player.getServerWorld());
        sendQuestNode(player, npc, dialog, "start", startNodeOpt.get(), state);
    }

    private static void sendQuestNode(ServerPlayerEntity player, PonyNpcEntity npc,
                                      DialogData dialog, String nodeId, DialogNode node,
                                      PlayerDialogState state) {
        UUID playerUuid = player.getUuid();
        String progressKey = dialog.id + ":" + npc.getUuidAsString();

        List<OpenDialogPayload.ChoiceData> choiceDataList = buildChoiceList(player, npc, node, progressKey, state);

        ServerPlayNetworking.send(player, new OpenDialogPayload(
            npc.getUuid(), npc.getNpcDisplayName(), dialog.id, nodeId,
            node.text,
            node.sound != null ? node.sound : "",
            Math.max(1, node.soundChars),
            choiceDataList
        ));
    }

    /**
     * Строит список выборов с учётом условий:
     * - hiddenIfConditionFails=true && условие не выполнено → не включаем
     * - hiddenIfConditionFails=false && условие не выполнено → включаем с available=false
     */
    private static List<OpenDialogPayload.ChoiceData> buildChoiceList(ServerPlayerEntity player,
                                                                       PonyNpcEntity npc,
                                                                       DialogNode node,
                                                                       String progressKey,
                                                                       PlayerDialogState state) {
        List<OpenDialogPayload.ChoiceData> result = new ArrayList<>();
        UUID playerUuid = player.getUuid();

        for (DialogChoice choice : node.choices) {
            boolean conditionMet = QuestConditionChecker.checkAll(player, choice.conditions);

            if (!conditionMet && choice.hiddenIfConditionFails) continue; // скрыть

            boolean done = choice.once && state.isChoiceDone(playerUuid, progressKey, choice.id);
            result.add(new OpenDialogPayload.ChoiceData(choice.id, choice.text, done, conditionMet));
        }

        return result;
    }

    // ══════════════════════════════════════════════════════════════
    //  ОБРАБОТКА ВЫБОРА
    // ══════════════════════════════════════════════════════════════

    private static void handleChoice(DialogChoicePayload payload, ServerPlayerEntity player) {
        // Ghost NPC despawn при ESC
        if ("__close__".equals(payload.choiceId())) {
            handleClose(player, payload);
            return;
        }
        if (RANDOM_NODE_ID.equals(payload.nodeId())) return;

        PonyNpcEntity npc = findNpc(player, payload.npcUuid());
        if (npc == null) return;

        String dialogId = npc.getDialogId();
        Optional<DialogData> dialogOpt = DialogManager.getInstance().getDialog(dialogId);
        if (dialogOpt.isEmpty()) return;

        DialogData dialog = dialogOpt.get();
        if (dialog.mode != DialogMode.QUEST) return;

        Optional<DialogNode> nodeOpt = dialog.getNode(payload.nodeId());
        if (nodeOpt.isEmpty()) {
            LOGGER.warn("Player {} referenced unknown node '{}' in dialog '{}'",
                player.getName().getString(), payload.nodeId(), dialogId);
            return;
        }

        DialogNode node = nodeOpt.get();
        DialogChoice selected = node.choices.stream()
            .filter(c -> c.id.equals(payload.choiceId()))
            .findFirst().orElse(null);

        if (selected == null) {
            LOGGER.warn("Player {} sent unknown choice '{}' for node '{}'",
                player.getName().getString(), payload.choiceId(), payload.nodeId());
            return;
        }

        // Серверная проверка условий (клиент не trusted)
        if (!QuestConditionChecker.checkAll(player, selected.conditions)) {
            LOGGER.warn("Player {} tried unavailable choice '{}' — conditions not met",
                player.getName().getString(), selected.id);
            return;
        }

        // Пометить once-выбор
        if (selected.once && player.getWorld() instanceof ServerWorld sw) {
            String progressKey = dialog.id + ":" + npc.getUuidAsString();
            PlayerDialogState.getOrCreate(sw).markChoiceDone(player.getUuid(), progressKey, selected.id);
        }

        // Выполнить действия
        QuestActionExecutor.executeAll(player, npc, selected.actions);

        // Навигация
        if (selected.next == null) {
            ServerPlayNetworking.send(player, new CloseDialogPayload(npc.getUuid()));
            GhostNpcManager.getInstance().onDialogClosed(player.getUuid(), npc);
        } else {
            Optional<DialogNode> nextNodeOpt = dialog.getNode(selected.next);
            if (nextNodeOpt.isEmpty()) {
                LOGGER.warn("Choice '{}' references unknown node '{}'", selected.id, selected.next);
                ServerPlayNetworking.send(player, new CloseDialogPayload(npc.getUuid()));
                GhostNpcManager.getInstance().onDialogClosed(player.getUuid(), npc);
                return;
            }
            PlayerDialogState dialogState = player.getWorld() instanceof ServerWorld sw
                ? PlayerDialogState.getOrCreate(sw) : null;
            if (dialogState != null) {
                sendQuestNode(player, npc, dialog, selected.next, nextNodeOpt.get(), dialogState);
            }
        }
    }

    private static void handleClose(ServerPlayerEntity player, DialogChoicePayload payload) {
        PonyNpcEntity npc = findNpc(player, payload.npcUuid());
        if (npc != null) {
            GhostNpcManager.getInstance().onDialogClosed(player.getUuid(), npc);
        }
    }

    // ── Утилиты ───────────────────────────────────────────────────

    private static PonyNpcEntity findNpc(ServerPlayerEntity player, UUID uuid) {
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) return null;
        for (PonyNpcEntity candidate : serverWorld.getEntitiesByType(
            PonyNpcEntities.PONY_NPC, e -> e.getUuid().equals(uuid)
        )) {
            return candidate;
        }
        return null;
    }
}
