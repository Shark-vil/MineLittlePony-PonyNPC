package com.minelittlepony.ponynpc.entity;

import com.minelittlepony.ponynpc.network.NetworkHandler;

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

public class PonyNpcEntity extends PathAwareEntity implements PlayerVisibleEntity, IDialogEntity {

    private static final String SKINS_PREFIX = "ponynpc:textures/skins/";
    private static final double MAX_INTERACT_DIST_SQ = 16.0; // 4 blocks max

    private static final TrackedData<String> SKIN_ID =
        DataTracker.registerData(PonyNpcEntity.class, TrackedDataHandlerRegistry.STRING);

    private String dialogId = "";
    private String npcDisplayName = "";

    // Stationary — server-side only, saved to NBT
    private boolean stationary = false;
    private boolean hasAnchor = false;
    private double anchorX, anchorY, anchorZ;

    // Ghost NPC — видим только указанным игрокам, не сохраняется в мире
    private final java.util.Set<java.util.UUID> visibleToPlayers = new java.util.HashSet<>();
    private boolean ghostNpc = false;
    private boolean despawnAfterDialog = false;

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
        this.goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 4.0F));
        this.goalSelector.add(2, new LookAroundGoal(this));
    }

    // ---- Tick — position anchor enforcement ----

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient() && stationary) {
            if (!hasAnchor) {
                anchorX = getX();
                anchorY = getY();
                anchorZ = getZ();
                hasAnchor = true;
            } else {
                double dx = getX() - anchorX;
                double dy = getY() - anchorY;
                double dz = getZ() - anchorZ;
                if (dx * dx + dy * dy + dz * dz > 0.01) {
                    refreshPositionAndAngles(anchorX, anchorY, anchorZ, getYaw(), getPitch());
                    setVelocity(0, 0, 0);
                    velocityDirty = true;
                }
            }
        }
    }

    // ---- Block velocity / knockback when stationary ----

    @Override
    public void addVelocity(double x, double y, double z) {
        if (!stationary) super.addVelocity(x, y, z);
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
        if (!stationary) super.takeKnockback(strength, x, z);
    }

    // ---- No physical collision (players walk through), but click still works ----

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ---- Invulnerability ----

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    // ---- Interaction (dialog) ----

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        // Limit dialog trigger to 4 blocks center-to-center distance
        if (player.squaredDistanceTo(this) > MAX_INTERACT_DIST_SQ) return ActionResult.PASS;

        if (!this.getWorld().isClient()) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                NetworkHandler.openDialogForPlayer(serverPlayer, this);
            }
        }
        return ActionResult.success(this.getWorld().isClient());
    }

    // ---- Skin data ----

    public String getSkinId() {
        return this.dataTracker.get(SKIN_ID);
    }

    /** Accepts either a short name ("applejack") or full identifier ("ponynpc:textures/skins/applejack.png"). */
    public void setSkinId(String skinId) {
        if (skinId != null && !skinId.isEmpty() && !skinId.contains(":")) {
            skinId = SKINS_PREFIX + skinId + ".png";
        }
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

    // ---- Dialog / name ----

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

    @Override
    public java.util.UUID getDialogSourceUuid() { return getUuid(); }

    public void setNpcDisplayName(String name) {
        this.npcDisplayName = name == null ? "" : name;
        if (!this.npcDisplayName.isEmpty()) {
            this.setCustomName(net.minecraft.text.Text.literal(this.npcDisplayName));
        }
    }

    // ---- Ghost NPC ----

    /** true если этот NPC видим только для игроков из visibleToPlayers. */
    public boolean isGhostNpc() { return ghostNpc; }
    public void setGhostNpc(boolean ghost) { this.ghostNpc = ghost; }

    /** true = NPC удаляется когда игрок закрывает диалог. */
    public boolean isDespawnAfterDialog() { return despawnAfterDialog; }
    public void setDespawnAfterDialog(boolean b) { this.despawnAfterDialog = b; }

    public void addVisibleTo(java.util.UUID playerUuid) { visibleToPlayers.add(playerUuid); }
    public void removeVisibleTo(java.util.UUID playerUuid) { visibleToPlayers.remove(playerUuid); }

    /**
     * Проверяет, может ли данный игрок видеть этот NPC.
     * Если visibleToPlayers пуст — видят все (обычный NPC).
     */
    public boolean isVisibleTo(net.minecraft.server.network.ServerPlayerEntity player) {
        if (visibleToPlayers.isEmpty()) return true;
        return visibleToPlayers.contains(player.getUuid());
    }

    // ---- Stationary ----

    public boolean isStationary() {
        return stationary;
    }

    public void setStationary(boolean stationary) {
        this.stationary = stationary;
        if (stationary && !hasAnchor && isAlive()) {
            anchorX = getX();
            anchorY = getY();
            anchorZ = getZ();
            hasAnchor = true;
        }
    }

    // ---- NBT ----

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("SkinId", getSkinId());
        nbt.putString("DialogId", dialogId);
        nbt.putString("NpcDisplayName", npcDisplayName);
        nbt.putBoolean("Stationary", stationary);
        if (hasAnchor) {
            nbt.putDouble("AnchorX", anchorX);
            nbt.putDouble("AnchorY", anchorY);
            nbt.putDouble("AnchorZ", anchorZ);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setSkinId(nbt.getString("SkinId"));
        dialogId = nbt.getString("DialogId");
        npcDisplayName = nbt.getString("NpcDisplayName");
        if (!npcDisplayName.isEmpty()) {
            this.setCustomName(net.minecraft.text.Text.literal(npcDisplayName));
            this.setCustomNameVisible(true);
        }
        stationary = nbt.getBoolean("Stationary");
        if (stationary && nbt.contains("AnchorX")) {
            anchorX = nbt.getDouble("AnchorX");
            anchorY = nbt.getDouble("AnchorY");
            anchorZ = nbt.getDouble("AnchorZ");
            hasAnchor = true;
        }
    }
}
