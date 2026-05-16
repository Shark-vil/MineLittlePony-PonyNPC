package com.minelittlepony.ponynpc.registry;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class PonyNpcSounds {

    public static final SoundEvent TALK = register("talk");
    public static final SoundEvent TALK_MAGIC = register("talk_magic");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of("ponynpc", name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void bootstrap() {
        // Вызывается при инициализации мода для загрузки класса
    }
}
