package com.minelittlepony.ponynpc;

import com.minelittlepony.ponynpc.command.DungeonBuilderCommand;
import com.minelittlepony.ponynpc.command.PartyCommand;
import com.minelittlepony.ponynpc.command.PonyNpcCommand;
import com.minelittlepony.ponynpc.dialog.DialogManager;
import com.minelittlepony.ponynpc.dungeon.DungeonInstanceManager;
import com.minelittlepony.ponynpc.dungeon.DungeonRegistry;
import com.minelittlepony.ponynpc.ghost.GhostNpcManager;
import com.minelittlepony.ponynpc.network.NetworkHandler;
import com.minelittlepony.ponynpc.party.PartyManager;
import com.minelittlepony.ponynpc.quest.QuestManager;
import com.minelittlepony.ponynpc.registry.PonyNpcEntities;
import com.minelittlepony.ponynpc.registry.PonyNpcSounds;
import com.minelittlepony.ponynpc.trigger.ProximityTriggerManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.TreeSet;

public class PonyNpcMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC");

    /** Короткие имена встроенных скинов (например "applejack"). Заполняется при старте. */
    public static final Set<String> BUILTIN_SKIN_NAMES = new TreeSet<>();

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing PonyNPC");

        PonyNpcSounds.bootstrap();
        PonyNpcEntities.bootstrap();
        NetworkHandler.registerServer();
        PonyNpcCommand.register();
        PartyCommand.register();
        DungeonBuilderCommand.register();

        // Block break protection: deny breaking in dungeon instances unless destructible = true
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return true;
            if (!world.getRegistryKey().equals(DungeonInstanceManager.DUNGEON_WORLD_KEY)) return true;
            // Admins not inside an active instance can always break (for building)
            return DungeonInstanceManager.getInstance().getInstance(sp.getUuid())
                .map(inst -> DungeonRegistry.getInstance().getDungeon(inst.dungeonId)
                    .map(def -> def.destructible)
                    .orElse(false))
                .orElse(true); // not in instance → allow (admin building)
        });

        discoverBuiltinSkins();

        // Загружаем диалоги, квесты и данжи при старте сервера
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            int dialogs  = DialogManager.getInstance().reload();
            int quests   = QuestManager.getInstance().reload();
            int dungeons = DungeonRegistry.getInstance().reload();
            LOGGER.info("PonyNPC: loaded {} dialog(s), {} quest(s), {} dungeon(s)",
                dialogs, quests, dungeons);
        });

        // Тик: proximity-триггеры + WAIT_DAYS завершение
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ProximityTriggerManager.getInstance().tick(server);
            // Проверяем WAIT_DAYS раз в 200 тиков (10 сек)
            if (server.getTicks() % 200 == 0) {
                QuestManager.getInstance().tickWaitDays(server);
            }
        });

        // При отключении игрока — чистим ghost NPC, proximity-триггеры, пати, данж
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var uuid = handler.player.getUuid();
            GhostNpcManager.getInstance().despawnAll(uuid);
            ProximityTriggerManager.getInstance().clearForPlayer(uuid);
            PartyManager.getInstance().onPlayerDisconnect(uuid, server);
            DungeonInstanceManager.getInstance().onPlayerDisconnect(uuid);
        });
    }

    private static void discoverBuiltinSkins() {
        FabricLoader.getInstance().getModContainer("ponynpc").ifPresent(container -> {
            for (var root : container.getRootPaths()) {
                var skinsDir = root.resolve("assets/ponynpc/textures/skins");
                if (!Files.isDirectory(skinsDir)) continue;
                try (var stream = Files.list(skinsDir)) {
                    stream.filter(p -> p.getFileName().toString().endsWith(".png"))
                          .map(p -> p.getFileName().toString())
                          .map(n -> n.substring(0, n.length() - 4))
                          .forEach(BUILTIN_SKIN_NAMES::add);
                } catch (IOException ignored) {}
            }
        });
        LOGGER.info("PonyNPC: discovered {} built-in skin(s)", BUILTIN_SKIN_NAMES.size());
    }
}
