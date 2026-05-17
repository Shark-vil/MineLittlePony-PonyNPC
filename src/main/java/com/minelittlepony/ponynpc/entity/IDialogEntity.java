package com.minelittlepony.ponynpc.entity;

import java.util.UUID;

/**
 * Implemented by anything that can open a dialog screen for a player.
 * May be an Entity (PonyNpcEntity, QuestMarkerEntity) or a virtual source (VirtualDialogContext).
 */
public interface IDialogEntity {
    String getDialogId();
    String getNpcDisplayName();
    UUID getDialogSourceUuid();
}
