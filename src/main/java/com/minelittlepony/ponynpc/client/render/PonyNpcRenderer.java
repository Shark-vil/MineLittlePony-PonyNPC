package com.minelittlepony.ponynpc.client.render;

import com.minelittlepony.api.pony.Pony;
import com.minelittlepony.api.pony.meta.Race;
import com.minelittlepony.api.model.Models;
import com.minelittlepony.client.model.ClientPonyModel;
import com.minelittlepony.client.model.ModelType;
import com.minelittlepony.client.render.entity.PonyRenderer;
import com.minelittlepony.client.render.entity.npc.textures.TextureSupplier;
import com.minelittlepony.ponynpc.entity.PonyNpcEntity;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * Рендерер NPC пони. Использует модели MLP на основе race из trigger-пикселей скина.
 * Скин берётся из PonyNpcEntity.getSkinId(); если не задан — случайный фоновый пони.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class PonyNpcRenderer extends PonyRenderer<PonyNpcEntity, ClientPonyModel<PonyNpcEntity>> {

    // Кеш моделей по расе, чтобы не создавать каждый кадр
    private final Function<Race, Models<PonyNpcEntity, ClientPonyModel<PonyNpcEntity>>> modelCache =
        Util.memoize(race -> {
            Race effectiveRace = resolveRace(race);
            return (Models<PonyNpcEntity, ClientPonyModel<PonyNpcEntity>>)
                (Object) ModelType.getPlayerModel(effectiveRace).create(false);
        });

    public PonyNpcRenderer(EntityRendererFactory.Context context) {
        super(
            context,
            ModelType.getPlayerModel(Race.EARTH).getKey(false),
            buildTextureSupplier()
        );
        // Динамический выбор модели по расе скина
        manager.setModelsLookup(entity -> modelCache.apply(getEntityPony(entity).race()));
    }

    /**
     * Создаёт TextureSupplier:
     * - Если у NPC задан skinId — берёт текстуру из него.
     * - Иначе — случайный фоновый пони от MLP по UUID.
     */
    private static TextureSupplier<PonyNpcEntity> buildTextureSupplier() {
        return entity -> {
            Identifier custom = entity.getSkinTextureId();
            if (custom != null) return custom;
            return Pony.getManager().getBackgroundPony(entity.getUuid()).texture();
        };
    }

    private static Race resolveRace(Race race) {
        if (race == null || race.isHuman() || ModelType.getPlayerModel(race) == null) {
            return Race.EARTH;
        }
        return race;
    }
}
