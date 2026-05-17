package com.minelittlepony.ponynpc.entity;

import com.minelittlepony.ponynpc.network.NetworkHandler;
import com.minelittlepony.ponynpc.quest.QuestActionExecutor;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.*;

/**
 * Маркер-объект квеста — интерактивная сущность без модели пони.
 * Видима только конкретным игрокам (реализует PlayerVisibleEntity).
 * По ПКМ открывает диалог или выполняет actions.
 */
public class QuestMarkerEntity extends Entity implements PlayerVisibleEntity, IDialogEntity {

    private static final TrackedData<ItemStack> DISPLAY_ITEM =
        DataTracker.registerData(QuestMarkerEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

    private List<String> actions = new ArrayList<>();
    private final Set<UUID> visibleToPlayers = new HashSet<>();
    private String dialogId = "";

    public QuestMarkerEntity(EntityType<? extends QuestMarkerEntity> type, World world) {
        super(type, world);
        this.setInvulnerable(true);
        this.setCustomNameVisible(true);
        this.setNoGravity(true);
    }

    /** Временный маркер (dungeon finish, proximity trigger) — видим конкретному игроку. */
    public void setup(String name, List<String> actions, UUID visibleTo) {
        this.setCustomName(Text.literal(name));
        this.actions = new ArrayList<>(actions);
        this.visibleToPlayers.add(visibleTo);
    }

    /** Постоянный маркер (placed via command) — видим всем игрокам. */
    public void setupPermanent(String name, String dialogId, ItemStack displayItem) {
        this.setCustomName(Text.literal(name));
        this.dialogId = dialogId != null ? dialogId : "";
        this.setDisplayItem(displayItem);
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

    public void setDisplayItem(ItemStack stack) {
        this.dataTracker.set(DISPLAY_ITEM, stack == null ? ItemStack.EMPTY : stack);
    }

    public ItemStack getDisplayItem() {
        return this.dataTracker.get(DISPLAY_ITEM);
    }

    public void addVisibleTo(UUID uuid) {
        visibleToPlayers.add(uuid);
    }

    @Override
    public boolean isVisibleTo(ServerPlayerEntity player) {
        return visibleToPlayers.isEmpty() || visibleToPlayers.contains(player.getUuid());
    }

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!getWorld().isClient() && player instanceof ServerPlayerEntity sp && isVisibleTo(sp)) {
            if (!dialogId.isEmpty()) {
                NetworkHandler.openDialogForPlayer(sp, this);
            } else {
                QuestActionExecutor.executeAll(sp, null, actions);
                if (visibleToPlayers.contains(sp.getUuid())) this.discard();
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.success(getWorld().isClient());
    }

    @Override public boolean isCollidable()             { return false; }
    @Override public boolean canBeHitByProjectile()     { return false; }
    @Override public boolean damage(DamageSource s, float a) { return false; }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(DISPLAY_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        dialogId = nbt.getString("DialogId");
        if (nbt.contains("DisplayItem")) {
            Identifier id = Identifier.tryParse(nbt.getString("DisplayItem"));
            if (id != null) setDisplayItem(new ItemStack(Registries.ITEM.get(id)));
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("DialogId", dialogId);
        ItemStack display = getDisplayItem();
        if (!display.isEmpty()) {
            nbt.putString("DisplayItem", Registries.ITEM.getId(display.getItem()).toString());
        }
    }
}
