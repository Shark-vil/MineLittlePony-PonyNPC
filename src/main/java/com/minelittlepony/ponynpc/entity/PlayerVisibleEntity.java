package com.minelittlepony.ponynpc.entity;

import net.minecraft.server.network.ServerPlayerEntity;

/** Сущность, видимая только указанным игрокам. Используется EntityTrackerMixin. */
public interface PlayerVisibleEntity {
    boolean isVisibleTo(ServerPlayerEntity player);
}
