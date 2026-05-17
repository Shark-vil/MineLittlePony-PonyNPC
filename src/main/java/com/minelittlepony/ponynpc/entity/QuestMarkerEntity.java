package com.minelittlepony.ponynpc.entity;

import com.minelittlepony.ponynpc.network.NetworkHandler;
import com.minelittlepony.ponynpc.quest.QuestActionExecutor;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.*;

/**
 * Маркер-объект квеста — интерактивная сущность без модели пони.
 * Видима только конкретным игрокам (реализует PlayerVisibleEntity).
 * По ПКМ выполняет список quest-actions и исчезает.
 */
public class QuestMarkerEntity extends Entity implements PlayerVisibleEntity, IDialogEntity {

    private List<String> actions = new ArrayList<>();
    private final Set<UUID> visibleToPlayers = new HashSet<>();
    private String dialogId = "";

    public QuestMarkerEntity(EntityType<? extends QuestMarkerEntity> type, World world) {
        super(type, world);
        this.setInvulnerable(true);
        this.setCustomNameVisible(true);
        this.setNoGravity(true);
    }

    public void setup(String name, List<String> actions, UUID visibleTo) {
        this.setCustomName(Text.literal(name));
        this.actions = new ArrayList<>(actions);
        this.visibleToPlayers.add(visibleTo);
    }

    public void setDialogId(String id) {
        this.dialogId = id != null ? id : "";
    }

    @Override
    public String getDialogId() {
        return dialogId;
    }

    @Override
    public String getNpcDisplayName() {
        return getCustomName() != null ? getCustomName().getString() : "";
    }

    @Override
    public String getUuidAsString() {
        return getUuid().toString();
    }

    public void addVisibleTo(UUID uuid) {
        visibleToPlayers.add(uuid);
    }

    @Override
    public boolean isVisibleTo(ServerPlayerEntity player) {
        return visibleToPlayers.isEmpty() || visibleToPlayers.contains(player.getUuid());
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!getWorld().isClient() && player instanceof ServerPlayerEntity sp
                && isVisibleTo(sp)) {
            if (!dialogId.isEmpty()) {
                NetworkHandler.openDialogForPlayer(sp, this);
            } else {
                QuestActionExecutor.executeAll(sp, null, actions);
                this.discard();
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.success(getWorld().isClient());
    }

    @Override public boolean isCollidable()             { return false; }
    @Override public boolean canBeHitByProjectile()     { return false; }
    @Override public boolean damage(DamageSource s, float a) { return false; }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {}

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {}
}
