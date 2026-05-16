package com.minelittlepony.ponynpc.entity;

import com.minelittlepony.ponynpc.network.NetworkHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class PonyNpcEntity extends PathAwareEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/Entity");

    // skinId синхронизируется через DataTracker — клиент знает какой скин рендерить
    private static final TrackedData<String> SKIN_ID =
        DataTracker.registerData(PonyNpcEntity.class, TrackedDataHandlerRegistry.STRING);

    // ID диалога из config/ponynpc/dialogs/<id>.json (только сервер)
    private String dialogId = "";
    // Кастомное имя NPC (сохраняется в NBT, также можно задать через setCustomName)
    private String npcDisplayName = "";

    public PonyNpcEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setInvulnerable(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN_ID, "");
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void initGoals() {
        // Смотрит на ближайших игроков
        this.goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        // Изредка смотрит по сторонам
        this.goalSelector.add(2, new LookAroundGoal(this));
    }

    // ---- Инвулнерабельность ----

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    // ---- Взаимодействие ----

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.getWorld().isClient()) {
            LOGGER.info("[PonyNPC] interactMob server-side, player={}, dialogId={}", player.getName().getString(), dialogId);
            if (player instanceof ServerPlayerEntity serverPlayer) {
                NetworkHandler.openDialogForPlayer(serverPlayer, this);
            }
        }
        return ActionResult.success(this.getWorld().isClient());
    }

    // ---- Данные скина и диалога ----

    public String getSkinId() {
        return this.dataTracker.get(SKIN_ID);
    }

    public void setSkinId(String skinId) {
        this.dataTracker.set(SKIN_ID, skinId == null ? "" : skinId);
    }

    public Identifier getSkinTextureId() {
        String id = getSkinId();
        if (!id.isEmpty()) {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed != null) return parsed;
        }
        return null;
    }

    public String getDialogId() {
        return dialogId;
    }

    public void setDialogId(String dialogId) {
        this.dialogId = dialogId == null ? "" : dialogId;
    }

    public String getNpcDisplayName() {
        if (!npcDisplayName.isEmpty()) return npcDisplayName;
        if (this.hasCustomName() && this.getCustomName() != null) {
            return this.getCustomName().getString();
        }
        return "???";
    }

    public void setNpcDisplayName(String name) {
        this.npcDisplayName = name == null ? "" : name;
        if (!this.npcDisplayName.isEmpty()) {
            this.setCustomName(net.minecraft.text.Text.literal(this.npcDisplayName));
        }
    }

    // ---- NBT ----

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("SkinId", getSkinId());
        nbt.putString("DialogId", dialogId);
        nbt.putString("NpcDisplayName", npcDisplayName);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setSkinId(nbt.getString("SkinId"));
        dialogId = nbt.getString("DialogId");
        npcDisplayName = nbt.getString("NpcDisplayName");
        // Восстанавливаем кастомное имя для отображения над головой
        if (!npcDisplayName.isEmpty()) {
            this.setCustomName(net.minecraft.text.Text.literal(npcDisplayName));
            this.setCustomNameVisible(true);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

}
