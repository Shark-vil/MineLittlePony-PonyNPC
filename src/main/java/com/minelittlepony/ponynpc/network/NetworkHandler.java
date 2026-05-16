package com.minelittlepony.ponynpc.network;

import com.minelittlepony.ponynpc.dialog.*;
import com.minelittlepony.ponynpc.entity.PonyNpcEntity;
import com.minelittlepony.ponynpc.registry.PonyNpcEntities;
import com.minelittlepony.ponynpc.state.PlayerDialogState;

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

    // Специальный nodeId для режима RANDOM (не является реальным узлом диалога)
    public static final String RANDOM_NODE_ID = "__random__";

    /** Регистрирует все пакеты на сервере. Вызывается из PonyNpcMod. */
    public static void registerServer() {
        PayloadTypeRegistry.playS2C().register(OpenDialogPayload.ID, OpenDialogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CloseDialogPayload.ID, CloseDialogPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DialogChoicePayload.ID, DialogChoicePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(DialogChoicePayload.ID, (payload, context) -> {
            context.server().execute(() -> handleChoice(payload, context.player()));
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  ОТКРЫТИЕ ДИАЛОГА (точка входа)
    // ══════════════════════════════════════════════════════════════

    /**
     * Открывает диалог у игрока для данного NPC.
     * Режим выбирается по полю {@code mode} в JSON-диалоге.
     */
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

        // Маршрутизация по режиму — точка расширения для новых режимов
        switch (dialog.mode) {
            case RANDOM  -> openRandomDialog(player, npc, dialog);
            case QUEST   -> openQuestDialog(player, npc, dialog);
            // Будущие режимы:
            // case SCRIPTED -> openScriptedDialog(player, npc, dialog);
            // case GPT      -> openGptDialog(player, npc, dialog);
        }
    }

    // ── Режим RANDOM ───────────────────────────────────────────────

    /**
     * Выбирает случайную реплику из {@code phrases} и отправляет без выборов.
     * Прогресс не отслеживается.
     */
    private static void openRandomDialog(ServerPlayerEntity player, PonyNpcEntity npc, DialogData dialog) {
        String phrase = dialog.getRandomPhrase().orElse("...");
        String sound = (dialog.sound != null && !dialog.sound.isEmpty()) ? dialog.sound : "ponynpc:talk";
        int soundChars = Math.max(1, dialog.soundChars);

        LOGGER.info("[PonyNPC] Sending RANDOM dialog '{}' to player {}", dialog.id, player.getName().getString());
        OpenDialogPayload payload = new OpenDialogPayload(
            npc.getUuid(),
            npc.getNpcDisplayName(),
            dialog.id,
            RANDOM_NODE_ID,
            phrase,
            sound,
            soundChars,
            List.of()  // Нет выборов — только прочитать и закрыть (ESC / клик)
        );

        ServerPlayNetworking.send(player, payload);
    }

    // ── Режим QUEST ────────────────────────────────────────────────

    /**
     * Открывает диалог с узлами (QUEST). Начинает с узла "start".
     */
    private static void openQuestDialog(ServerPlayerEntity player, PonyNpcEntity npc, DialogData dialog) {
        LOGGER.info("[PonyNPC] Sending QUEST dialog '{}' to player {}", dialog.id, player.getName().getString());
        Optional<DialogNode> startNodeOpt = dialog.getNode("start");
        if (startNodeOpt.isEmpty()) {
            player.sendMessage(
                Text.literal("[PonyNPC] Dialog '" + dialog.id + "' has no 'start' node."), false);
            return;
        }
        ServerWorld world = player.getServerWorld();
        PlayerDialogState state = PlayerDialogState.getOrCreate(world);
        sendQuestNode(player, npc, dialog, "start", startNodeOpt.get(), state);
    }

    /** Отправляет конкретный узел QUEST-диалога игроку. */
    private static void sendQuestNode(
        ServerPlayerEntity player,
        PonyNpcEntity npc,
        DialogData dialog,
        String nodeId,
        DialogNode node,
        PlayerDialogState state
    ) {
        UUID playerUuid = player.getUuid();
        // Ключ прогресса уникален для каждой пары (dialog, npc)
        String progressKey = dialog.id + ":" + npc.getUuidAsString();

        List<OpenDialogPayload.ChoiceData> choiceDataList = node.choices.stream()
            .map(choice -> new OpenDialogPayload.ChoiceData(
                choice.id,
                choice.text,
                choice.once && state.isChoiceDone(playerUuid, progressKey, choice.id)
            ))
            .collect(Collectors.toList());

        OpenDialogPayload payload = new OpenDialogPayload(
            npc.getUuid(),
            npc.getNpcDisplayName(),
            dialog.id,
            nodeId,
            node.text,
            node.sound != null ? node.sound : "",
            Math.max(1, node.soundChars),
            choiceDataList
        );

        ServerPlayNetworking.send(player, payload);
    }

    // ══════════════════════════════════════════════════════════════
    //  ОБРАБОТКА ВЫБОРА ИГРОКА
    // ══════════════════════════════════════════════════════════════

    private static void handleChoice(DialogChoicePayload payload, ServerPlayerEntity player) {
        // ESC — игрок закрыл диалог
        if ("__close__".equals(payload.choiceId())) return;
        // RANDOM-режим не имеет выборов — ничего не обрабатываем
        if (RANDOM_NODE_ID.equals(payload.nodeId())) return;

        // Поиск NPC по UUID в мире игрока
        PonyNpcEntity npc = findNpc(player, payload.npcUuid());
        if (npc == null) {
            LOGGER.debug("NPC {} not found for player {}", payload.npcUuid(), player.getName().getString());
            return;
        }

        String dialogId = npc.getDialogId();
        Optional<DialogData> dialogOpt = DialogManager.getInstance().getDialog(dialogId);
        if (dialogOpt.isEmpty()) return;

        DialogData dialog = dialogOpt.get();

        // Только QUEST-режим имеет осмысленные выборы
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
            .findFirst()
            .orElse(null);

        if (selected == null) {
            LOGGER.warn("Player {} sent unknown choice '{}' for node '{}' in dialog '{}'",
                player.getName().getString(), payload.choiceId(), payload.nodeId(), dialogId);
            return;
        }

        // Пометить выбор как выполненный (если once=true)
        if (selected.once && player.getWorld() instanceof ServerWorld sw) {
            String progressKey = dialog.id + ":" + npc.getUuidAsString();
            PlayerDialogState.getOrCreate(sw).markChoiceDone(player.getUuid(), progressKey, selected.id);
        }

        // Навигация
        if (selected.next == null) {
            ServerPlayNetworking.send(player, new CloseDialogPayload(npc.getUuid()));
        } else {
            Optional<DialogNode> nextNodeOpt = dialog.getNode(selected.next);
            if (nextNodeOpt.isEmpty()) {
                LOGGER.warn("Choice '{}' in dialog '{}' references unknown node '{}'",
                    selected.id, dialogId, selected.next);
                ServerPlayNetworking.send(player, new CloseDialogPayload(npc.getUuid()));
                return;
            }
            PlayerDialogState state = player.getWorld() instanceof ServerWorld sw
                ? PlayerDialogState.getOrCreate(sw)
                : null;
            if (state != null) {
                sendQuestNode(player, npc, dialog, selected.next, nextNodeOpt.get(), state);
            }
        }
    }

    // ── Вспомогательные методы ─────────────────────────────────────

    private static PonyNpcEntity findNpc(ServerPlayerEntity player, UUID uuid) {
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) return null;
        for (PonyNpcEntity candidate : serverWorld.getEntitiesByType(
            PonyNpcEntities.PONY_NPC,
            e -> e.getUuid().equals(uuid)
        )) {
            return candidate;
        }
        return null;
    }
}
