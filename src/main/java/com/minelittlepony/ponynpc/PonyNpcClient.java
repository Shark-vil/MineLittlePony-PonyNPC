package com.minelittlepony.ponynpc;

import com.minelittlepony.ponynpc.client.render.PonyNpcRenderer;
import com.minelittlepony.ponynpc.client.screen.DialogScreen;
import com.minelittlepony.ponynpc.registry.PonyNpcEntities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public class PonyNpcClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/Client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[PonyNPC] Client initialized");
        // Регистрируем рендерер сущности — использует MLP пони-модели
        EntityRendererRegistry.register(PonyNpcEntities.PONY_NPC, PonyNpcRenderer::new);

        // Регистрируем обработчики сетевых пакетов на клиенте
        ClientPlayNetworking.registerGlobalReceiver(
            com.minelittlepony.ponynpc.network.OpenDialogPayload.ID,
            (payload, context) -> context.client().execute(() -> handleOpenDialog(payload, context.client()))
        );
        ClientPlayNetworking.registerGlobalReceiver(
            com.minelittlepony.ponynpc.network.CloseDialogPayload.ID,
            (payload, context) -> context.client().execute(() -> handleCloseDialog(context.client()))
        );
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
