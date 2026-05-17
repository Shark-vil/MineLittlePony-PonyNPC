package com.minelittlepony.ponynpc.entity;

import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Виртуальный источник диалога — не является сущностью.
 * Используется для интерактивных блоков (block_zones).
 */
public final class VirtualDialogContext implements IDialogEntity {

    private final UUID uuid;
    private final String dialogId;
    private final String name;

    public VirtualDialogContext(UUID uuid, String dialogId, String name) {
        this.uuid = uuid;
        this.dialogId = dialogId;
        this.name = name;
    }

    /** Детерминированный UUID по координатам блока (стабилен между сессиями). */
    public static UUID blockUuid(BlockPos pos) {
        return UUID.nameUUIDFromBytes(
            ("block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ())
                .getBytes(StandardCharsets.UTF_8));
    }

    @Override public String getDialogId()       { return dialogId; }
    @Override public String getNpcDisplayName() { return name; }
    @Override public UUID getDialogSourceUuid() { return uuid; }
}
