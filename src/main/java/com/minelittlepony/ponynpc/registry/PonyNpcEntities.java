package com.minelittlepony.ponynpc.registry;

import com.minelittlepony.ponynpc.entity.PonyNpcEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class PonyNpcEntities {

    public static final EntityType<PonyNpcEntity> PONY_NPC = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of("ponynpc", "pony_npc"),
        EntityType.Builder.<PonyNpcEntity>create(PonyNpcEntity::new, SpawnGroup.MISC)
            .dimensions(0.6F, 1.95F)
            .maxTrackingRange(10)
            .trackingTickInterval(3)
            .build()
    );

    public static void bootstrap() {
        FabricDefaultAttributeRegistry.register(PONY_NPC, PonyNpcEntity.createAttributes());
    }
}
