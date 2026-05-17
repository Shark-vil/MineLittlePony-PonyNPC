package com.minelittlepony.ponynpc;

import com.minelittlepony.ponynpc.client.render.PonyNpcRenderer;
import com.minelittlepony.ponynpc.client.render.QuestMarkerRenderer;
import com.minelittlepony.ponynpc.client.screen.DialogScreen;
import com.minelittlepony.ponynpc.client.screen.QuestJournalScreen;
import com.minelittlepony.ponynpc.network.QuestJournalPayload;
import com.minelittlepony.ponynpc.network.RequestQuestJournalPayload;
import com.minelittlepony.ponynpc.registry.PonyNpcEntities;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class PonyNpcClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/Client");

    private static final KeyBinding JOURNAL_KEY = KeyBindingHelper.registerKeyBinding(
        new KeyBinding("key.ponynpc.journal", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.ponynpc")
    );

    @Override
    public void onInitializeClient() {
        LOGGER.info("[PonyNPC] Client initialized");
        EntityRendererRegistry.register(PonyNpcEntities.PONY_NPC, PonyNpcRenderer::new);
        EntityRendererRegistry.register(PonyNpcEntities.QUEST_MARKER, QuestMarkerRenderer::new);

        // Сетевые пакеты диалога
        ClientPlayNetworking.registerGlobalReceiver(
            com.minelittlepony.ponynpc.network.OpenDialogPayload.ID,
            (payload, context) -> context.client().execute(() -> handleOpenDialog(payload, context.client()))
        );
        ClientPlayNetworking.registerGlobalReceiver(
            com.minelittlepony.ponynpc.network.CloseDialogPayload.ID,
            (payload, context) -> context.client().execute(() -> handleCloseDialog(context.client()))
        );

        // Сетевой пакет дневника квестов
        ClientPlayNetworking.registerGlobalReceiver(
            QuestJournalPayload.ID,
            (payload, context) -> context.client().execute(() ->
                context.client().setScreen(new QuestJournalScreen(payload)))
        );

        // Клавиша J — открыть дневник квестов
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (JOURNAL_KEY.wasPressed()) {
                if (mc.player != null && mc.currentScreen == null) {
                    ClientPlayNetworking.send(new RequestQuestJournalPayload());
                }
            }
        });
    }

    /** Открывает или обновляет диалоговый экран. */
    private void handleOpenDialog(
            com.minelittlepony.ponynpc.network.OpenDialogPayload payload,
            MinecraftClient mc) {

        LOGGER.info("[PonyNPC] Client received OpenDialogPayload, dialogId={}, nodeId={}", payload.dialogId(), payload.nodeId());
        Screen current = mc.currentScreen;

        if (current instanceof DialogScreen dialogScreen
                && dialogScreen.getNpcUuid().equals(payload.npcUuid())) {
            // Уже открыт диалог с этим NPC — просто обновляем узел
            dialogScreen.updateNode(payload);
        } else {
            // Открываем новый экран
            mc.setScreen(new DialogScreen(payload));
        }
    }

    /** Закрывает диалоговый экран, если он открыт. */
    private void handleCloseDialog(MinecraftClient mc) {
        if (mc.currentScreen instanceof DialogScreen dialogScreen) {
            // Используем специальный метод, который не отправляет __close__ обратно серверу
            dialogScreen.closeByServer();
        }
    }
}
