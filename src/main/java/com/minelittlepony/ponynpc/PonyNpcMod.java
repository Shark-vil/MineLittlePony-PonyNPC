package com.minelittlepony.ponynpc;

import com.minelittlepony.ponynpc.command.PonyNpcCommand;
import com.minelittlepony.ponynpc.dialog.DialogManager;
import com.minelittlepony.ponynpc.network.NetworkHandler;
import com.minelittlepony.ponynpc.registry.PonyNpcEntities;
import com.minelittlepony.ponynpc.registry.PonyNpcSounds;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.TreeSet;

public class PonyNpcMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC");

    /** Short names of built-in skins (e.g. "applejack"), populated at startup. */
    public static final Set<String> BUILTIN_SKIN_NAMES = new TreeSet<>();

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing PonyNPC");

        PonyNpcSounds.bootstrap();
        PonyNpcEntities.bootstrap();
        NetworkHandler.registerServer();
        PonyNpcCommand.register();

        discoverBuiltinSkins();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            int count = DialogManager.getInstance().reload();
            LOGGER.info("PonyNPC: loaded {} dialog(s)", count);
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
        LOGGER.info("PonyNPC: discovered {} built-in skins", BUILTIN_SKIN_NAMES.size());
    }
}
