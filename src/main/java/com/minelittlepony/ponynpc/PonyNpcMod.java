package com.minelittlepony.ponynpc;

import com.minelittlepony.ponynpc.command.PonyNpcCommand;
import com.minelittlepony.ponynpc.dialog.DialogManager;
import com.minelittlepony.ponynpc.network.NetworkHandler;
import com.minelittlepony.ponynpc.registry.PonyNpcEntities;
import com.minelittlepony.ponynpc.registry.PonyNpcSounds;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PonyNpcMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing PonyNPC");

        // Регистрация звуков и типов сущностей
        PonyNpcSounds.bootstrap();
        PonyNpcEntities.bootstrap();

        // Регистрация сетевых пакетов (серверная сторона)
        NetworkHandler.registerServer();

        // Регистрация команд
        PonyNpcCommand.register();

        // Загрузка диалогов при старте сервера
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            int count = DialogManager.getInstance().reload();
            LOGGER.info("PonyNPC: loaded {} dialog(s)", count);
        });
    }
}
