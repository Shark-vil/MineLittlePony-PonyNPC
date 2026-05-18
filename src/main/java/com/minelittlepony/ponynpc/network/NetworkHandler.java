package com.minelittlepony.ponynpc.network;

import com.minelittlepony.ponynpc.dialog.*;
import com.minelittlepony.ponynpc.entity.IDialogEntity;
import com.minelittlepony.ponynpc.entity.PonyNpcEntity;
import com.minelittlepony.ponynpc.entity.QuestMarkerEntity;
import com.minelittlepony.ponynpc.entity.VirtualDialogContext;
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
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/Network");

    public static final String RANDOM_NODE_ID = "__random__";

    /** playerUuid → (entityUuid → resolvedDialogId) — переопределения диалога для router-режима. */
    private static final Map<UUID, Map<UUID, String>> playerDialogOverrides = new ConcurrentHashMap<>();

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

    public static void openDialogForPlayer(ServerPlayerEntity player, IDialogEntity entity) {
        String dialogId = entity.getDialogId();
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
            case RANDOM -> openRandomDialog(player, entity, dialog);
            case QUEST  -> openQuestDialog(player, entity, dialog);
            case ROUTER -> openRouterDialog(player, entity, dialog);
        }
    }

    // ── RANDOM ────────────────────────────────────────────────────

    private static void openRandomDialog(ServerPlayerEntity player, IDialogEntity entity, DialogData dialog) {
        String phrase = dialog.getRandomPhrase().orElse("...");
        String sound = (dialog.sound != null && !dialog.sound.isEmpty()) ? dialog.sound : "ponynpc:talk";
        int soundChars = Math.max(1, dialog.soundChars);

        ServerPlayNetworking.send(player, new OpenDialogPayload(
            entity.getDialogSourceUuid(), entity.getNpcDisplayName(), dialog.id,
            RANDOM_NODE_ID, phrase, sound, soundChars, List.of()
        ));
    }

    // ── QUEST ─────────────────────────────────────────────────────

    private static void openQuestDialog(ServerPlayerEntity player, IDialogEntity entity, DialogData dialog) {
        Optional<DialogNode> startNodeOpt = dialog.getNode("start");
        if (startNodeOpt.isEmpty()) {
            player.sendMessage(Text.literal("[PonyNPC] Dialog '" + dialog.id + "' has no 'start' node."), false);
            return;
        }
        PlayerDialogState state = PlayerDialogState.getOrCreate(player.getServerWorld());
        sendQuestNode(player, entity, dialog, "start", startNodeOpt.get(), state);
    }

    // ── ROUTER ────────────────────────────────────────────────────

    private static void openRouterDialog(ServerPlayerEntity player, IDialogEntity entity, DialogData routerDialog) {
        for (DialogData.RouterEntry entry : routerDialog.entries) {
            if (entry.dialog == null || entry.dialog.isEmpty()) continue;
            if (!entry.conditions.isEmpty() && !QuestConditionChecker.checkAll(player, entry.conditions)) continue;

            Optional<DialogData> targetOpt = DialogManager.getInstance().getDialog(entry.dialog);
            if (targetOpt.isEmpty()) {
                player.sendMessage(Text.literal("[PonyNPC] Router '" + routerDialog.id
                    + "': target dialog not found: " + entry.dialog), false);
                return;
            }

            DialogData target = targetOpt.get();
            if (target.mode == DialogMode.ROUTER) {
                LOGGER.warn("Router '{}' points to another router '{}' — nested routers are not supported",
                    routerDialog.id, entry.dialog);
                return;
            }

            playerDialogOverrides
                .computeIfAbsent(player.getUuid(), k -> new ConcurrentHashMap<>())
                .put(entity.getDialogSourceUuid(), entry.dialog);

            switch (target.mode) {
                case RANDOM -> openRandomDialog(player, entity, target);
                case QUEST  -> openQuestDialog(player, entity, target);
                default     -> {}
            }
            return;
        }
        // Ни одна запись не подошла — диалог не открывается
    }

    private static void sendQuestNode(ServerPlayerEntity player, IDialogEntity entity,
                                      DialogData dialog, String nodeId, DialogNode node,
                                      PlayerDialogState state) {
        String progressKey = dialog.id + ":" + entity.getDialogSourceUuid().toString();

        List<OpenDialogPayload.ChoiceData> choiceDataList = buildChoiceList(player, entity, node, progressKey, state);

        ServerPlayNetworking.send(player, new OpenDialogPayload(
            entity.getDialogSourceUuid(), entity.getNpcDisplayName(), dialog.id, nodeId,
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
                                                                       IDialogEntity entity,
                                                                       DialogNode node,
                                                                       String progressKey,
                                                                       PlayerDialogState state) {
        List<OpenDialogPayload.ChoiceData> result = new ArrayList<>();
        UUID playerUuid = player.getUuid();

        for (DialogChoice choice : node.choices) {
            boolean conditionMet = QuestConditionChecker.checkAll(player, choice.conditions);

            if (!conditionMet && choice.hiddenIfConditionFails) continue;

            boolean done = choice.once && state.isChoiceDone(playerUuid, progressKey, choice.id);
            result.add(new OpenDialogPayload.ChoiceData(choice.id, choice.text, done, conditionMet));
        }

        return result;
    }

    // ══════════════════════════════════════════════════════════════
    //  ОБРАБОТКА ВЫБОРА
    // ══════════════════════════════════════════════════════════════

    private static void handleChoice(DialogChoicePayload payload, ServerPlayerEntity player) {
        if ("__close__".equals(payload.choiceId())) {
            handleClose(player, payload);
            return;
        }
        if (RANDOM_NODE_ID.equals(payload.nodeId())) return;

        IDialogEntity entity = findDialogEntity(player, payload.npcUuid());
        if (entity == null) return;

        String dialogId = resolveDialogId(player, entity);
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

        if (!QuestConditionChecker.checkAll(player, selected.conditions)) {
            LOGGER.warn("Player {} tried unavailable choice '{}' — conditions not met",
                player.getName().getString(), selected.id);
            return;
        }

        if (selected.once && player.getWorld() instanceof ServerWorld sw) {
            String progressKey = dialog.id + ":" + entity.getDialogSourceUuid().toString();
            PlayerDialogState.getOrCreate(sw).markChoiceDone(player.getUuid(), progressKey, selected.id);
        }

        PonyNpcEntity ponyNpc = entity instanceof PonyNpcEntity p ? p : null;
        QuestActionExecutor.executeAll(player, ponyNpc, selected.actions);

        if (selected.next == null) {
            ServerPlayNetworking.send(player, new CloseDialogPayload(entity.getDialogSourceUuid()));
            closeDialogEntity(player, entity);
        } else {
            Optional<DialogNode> nextNodeOpt = dialog.getNode(selected.next);
            if (nextNodeOpt.isEmpty()) {
                LOGGER.warn("Choice '{}' references unknown node '{}'", selected.id, selected.next);
                ServerPlayNetworking.send(player, new CloseDialogPayload(entity.getDialogSourceUuid()));
                closeDialogEntity(player, entity);
                return;
            }
            PlayerDialogState dialogState = player.getWorld() instanceof ServerWorld sw
                ? PlayerDialogState.getOrCreate(sw) : null;
            if (dialogState != null) {
                sendQuestNode(player, entity, dialog, selected.next, nextNodeOpt.get(), dialogState);
            }
        }
    }

    private static void handleClose(ServerPlayerEntity player, DialogChoicePayload payload) {
        IDialogEntity entity = findDialogEntity(player, payload.npcUuid());
        if (entity == null) return;
        clearDialogOverride(player, entity);
        if (entity instanceof PonyNpcEntity ponyNpc) {
            GhostNpcManager.getInstance().onDialogClosed(player.getUuid(), ponyNpc);
        } else if (entity instanceof VirtualDialogContext ctx) {
            BlockInteractionManager.getInstance().removeContext(player.getUuid(), ctx.getDialogSourceUuid());
        }
    }

    private static void closeDialogEntity(ServerPlayerEntity player, IDialogEntity entity) {
        clearDialogOverride(player, entity);
        if (entity instanceof PonyNpcEntity ponyNpc) {
            GhostNpcManager.getInstance().onDialogClosed(player.getUuid(), ponyNpc);
        } else if (entity instanceof QuestMarkerEntity marker) {
            // Only discard actions-based markers; dialog-based markers (e.g. dungeon finish) stay for re-interaction
            if (!marker.isRemoved() && !marker.isPermanent() && marker.getDialogId().isEmpty()) {
                marker.discard();
            }
        } else if (entity instanceof VirtualDialogContext ctx) {
            BlockInteractionManager.getInstance().removeContext(player.getUuid(), ctx.getDialogSourceUuid());
        }
    }

    // ── Утилиты ───────────────────────────────────────────────────

    /** Возвращает overridden dialogId (router-режим) или entity.getDialogId(). */
    private static String resolveDialogId(ServerPlayerEntity player, IDialogEntity entity) {
        Map<UUID, String> overrides = playerDialogOverrides.get(player.getUuid());
        if (overrides != null) {
            String override = overrides.get(entity.getDialogSourceUuid());
            if (override != null && !override.isEmpty()) return override;
        }
        return entity.getDialogId();
    }

    private static void clearDialogOverride(ServerPlayerEntity player, IDialogEntity entity) {
        Map<UUID, String> overrides = playerDialogOverrides.get(player.getUuid());
        if (overrides != null) overrides.remove(entity.getDialogSourceUuid());
    }

    /** Вызывается при отключении игрока. */
    public static void clearPlayerState(UUID playerUuid) {
        playerDialogOverrides.remove(playerUuid);
    }

    private static IDialogEntity findDialogEntity(ServerPlayerEntity player, UUID uuid) {
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            for (PonyNpcEntity candidate : serverWorld.getEntitiesByType(
                PonyNpcEntities.PONY_NPC, e -> e.getUuid().equals(uuid)
            )) {
                return candidate;
            }
            for (QuestMarkerEntity candidate : serverWorld.getEntitiesByType(
                PonyNpcEntities.QUEST_MARKER, e -> e.getUuid().equals(uuid)
            )) {
                return candidate;
            }
        }
        return BlockInteractionManager.getInstance().getContext(player.getUuid(), uuid).orElse(null);
    }
}
