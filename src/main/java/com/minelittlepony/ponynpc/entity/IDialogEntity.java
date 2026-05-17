package com.minelittlepony.ponynpc.entity;

import java.util.UUID;

/**
 * Implemented by any entity that can open a dialog screen for a player.
 */
public interface IDialogEntity {
    String getDialogId();
    UUID getUuid();
    String getUuidAsString();
    String getNpcDisplayName();
}
