package com.minelittlepony.ponynpc.entity;

/**
 * Implemented by any entity that can open a dialog screen for a player.
 * Both implementors extend Entity, so callers needing UUID should cast to Entity directly.
 */
public interface IDialogEntity {
    String getDialogId();
    String getNpcDisplayName();
}
