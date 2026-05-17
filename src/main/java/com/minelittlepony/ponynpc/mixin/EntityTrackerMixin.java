package com.minelittlepony.ponynpc.mixin;

import com.minelittlepony.ponynpc.entity.PlayerVisibleEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Фильтрует entity-tracking: любая PlayerVisibleEntity (ghost NPC, маркер)
 * не отправляется игрокам не из списка visibleToPlayers.
 *
 * Таргет — inner class ThreadedAnvilChunkStorage$EntityTracker.
 */
@Mixin(targets = "net.minecraft.server.world.ThreadedAnvilChunkStorage$EntityTracker")
public abstract class EntityTrackerMixin {

    @Shadow @Final Entity entity;

    @Inject(
        method = "startTracking(Lnet/minecraft/server/network/ServerPlayerEntity;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ponynpc$filterPlayerVisible(ServerPlayerEntity player, CallbackInfo ci) {
        if (entity instanceof PlayerVisibleEntity visible && !visible.isVisibleTo(player)) {
            ci.cancel();
        }
    }
}
