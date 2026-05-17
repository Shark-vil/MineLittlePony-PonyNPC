package com.minelittlepony.ponynpc.registry;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class PonyNpcSounds {

    public static final SoundEvent TALK           = register("talk");
    public static final SoundEvent TALK_MAGIC     = register("talk_magic");
    public static final SoundEvent QUEST_ACCEPT   = register("quest_accept");
    public static final SoundEvent QUEST_STAGE    = register("quest_stage");
    public static final SoundEvent QUEST_COMPLETE = register("quest_complete");
    public static final SoundEvent DUNGEON_ENTER  = register("dungeon_enter");
    public static final SoundEvent DUNGEON_EXIT   = register("dungeon_exit");
    public static final SoundEvent DUNGEON_COMPLETE = register("dungeon_complete");
    public static final SoundEvent ITEM_RECEIVE   = register("item_receive");
    public static final SoundEvent DIALOG_CLICK   = register("dialog_click");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of("ponynpc", name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void bootstrap() {
        // Вызывается при инициализации мода для загрузки класса
    }
}
