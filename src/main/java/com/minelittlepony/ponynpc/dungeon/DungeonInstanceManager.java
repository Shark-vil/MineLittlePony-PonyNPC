package com.minelittlepony.ponynpc.dungeon;

import com.minelittlepony.ponynpc.entity.QuestMarkerEntity;
import com.minelittlepony.ponynpc.ghost.GhostNpcManager;
import com.minelittlepony.ponynpc.party.Party;
import com.minelittlepony.ponynpc.party.PartyManager;
import com.minelittlepony.ponynpc.quest.GhostNpcSpawnConfig;
import com.minelittlepony.ponynpc.quest.QuestManager;
import com.minelittlepony.ponynpc.registry.PonyNpcEntities;
import com.minelittlepony.ponynpc.registry.PonyNpcSounds;
import com.minelittlepony.ponynpc.state.QuestPocketState;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Управляет активными инстансами данжей (вход, выход, завершение, очистка). */
public final class DungeonInstanceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/DungeonInstance");
    private static final DungeonInstanceManager INSTANCE = new DungeonInstanceManager();

    public static final RegistryKey<World> DUNGEON_WORLD_KEY =
        RegistryKey.of(RegistryKeys.WORLD, Identifier.of("ponynpc", "dungeon"));

    private final Map<UUID, DungeonInstance> playerInstance = new HashMap<>();
    private int nextSlot = 0;
    private final Deque<Integer> freeSlots = new ArrayDeque<>();

    private DungeonInstanceManager() {}

    public static DungeonInstanceManager getInstance() { return INSTANCE; }

    // ── Запросы ───────────────────────────────────────────────────

    public boolean isInDungeon(UUID playerUuid) {
        return playerInstance.containsKey(playerUuid);
    }

    public boolean isInDungeon(UUID playerUuid, String dungeonId) {
        DungeonInstance inst = playerInstance.get(playerUuid);
        return inst != null && dungeonId.equals(inst.dungeonId);
    }

    public Optional<DungeonInstance> getInstance(UUID playerUuid) {
        return Optional.ofNullable(playerInstance.get(playerUuid));
    }

    // ── Вход ─────────────────────────────────────────────────────

    /**
     * Войти в данж.
     * Если игрок — лидер пати, вся пати входит вместе.
     */
    public boolean enterDungeon(ServerPlayerEntity leader, String dungeonId, MinecraftServer server) {
        if (playerInstance.containsKey(leader.getUuid())) {
            leader.sendMessage(Text.literal("[Dungeon] You are already in a dungeon."), false);
            return false;
        }
        DungeonDefinition def = DungeonRegistry.getInstance().getDungeon(dungeonId).orElse(null);
        if (def == null) {
            leader.sendMessage(Text.literal("[Dungeon] Unknown dungeon: " + dungeonId), false);
            return false;
        }
        if (!def.hasBuildOrigin()) {
            leader.sendMessage(Text.literal("[Dungeon] Dungeon '" + dungeonId
                + "' has no build zone. Use /dungeon save " + dungeonId + "."), false);
            return false;
        }
        ServerWorld dungeonWorld = server.getWorld(DUNGEON_WORLD_KEY);
        if (dungeonWorld == null) {
            LOGGER.error("Dungeon world '{}' is not loaded!", DUNGEON_WORLD_KEY.getValue());
            leader.sendMessage(Text.literal("[Dungeon] Dungeon world is not available."), false);
            return false;
        }

        // Collect participants
        List<ServerPlayerEntity> participants = new ArrayList<>();
        participants.add(leader);
        Party party = PartyManager.getInstance().getParty(leader.getUuid()).orElse(null);
        if (party != null && party.isLeader(leader.getUuid())) {
            for (UUID memberId : party.getMembers()) {
                if (memberId.equals(leader.getUuid())) continue;
                ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberId);
                if (member == null) continue;
                if (playerInstance.containsKey(memberId)) {
                    leader.sendMessage(Text.literal("[Dungeon] " + member.getName().getString()
                        + " is already in a dungeon — skipping."), false);
                    continue;
                }
                participants.add(member);
            }
        }

        int slotId = allocateSlot();
        DungeonInstance inst = new DungeonInstance(slotId, dungeonId);
        BlockPos srcOrigin = new BlockPos(def.buildOrigin.x, def.buildOrigin.y, def.buildOrigin.z);
        BlockPos dstOrigin = inst.getSlotOrigin(def);

        // Copy dungeon blocks from build zone to player slot
        DungeonCopier.copy(dungeonWorld, srcOrigin, dstOrigin, def.getWidth(), def.getHeight(), def.getLength());

        // Apply time of day
        if (def.fixedTime != null) {
            dungeonWorld.setTimeOfDay(def.fixedTime);
        }

        // Player spawn position in the slot
        double spawnX = dstOrigin.getX() + def.spawnOffset.x + 0.5;
        double spawnY = dstOrigin.getY() + def.spawnOffset.y;
        double spawnZ = dstOrigin.getZ() + def.spawnOffset.z + 0.5;

        // Build list of UUIDs for ghost NPC visibility
        List<UUID> uuids = new ArrayList<>();
        for (ServerPlayerEntity p : participants) uuids.add(p.getUuid());

        for (ServerPlayerEntity player : participants) {
            DungeonInstance.ReturnData ret = new DungeonInstance.ReturnData(
                player.getServerWorld().getRegistryKey(),
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch()
            );
            inst.addPlayer(player.getUuid(), ret);
            playerInstance.put(player.getUuid(), inst);
            teleportPlayer(player, dungeonWorld, spawnX, spawnY, spawnZ, 0f, 0f);
            player.playSound(PonyNpcSounds.DUNGEON_ENTER, 0.5f, 1.0f);
            player.sendMessage(Text.literal("[Dungeon] You entered: " + def.name), false);
        }

        // Spawn finish marker (preferred) or finish NPC at dungeon end point
        if (def.finishMarker != null) {
            double mX = dstOrigin.getX() + def.finishMarker.offset.x + 0.5;
            double mY = dstOrigin.getY() + def.finishMarker.offset.y;
            double mZ = dstOrigin.getZ() + def.finishMarker.offset.z + 0.5;
            QuestMarkerEntity marker = PonyNpcEntities.QUEST_MARKER.create(dungeonWorld);
            if (marker != null) {
                boolean useDialog = def.finishMarker.dialogId != null && !def.finishMarker.dialogId.isEmpty();
                boolean first = true;
                for (ServerPlayerEntity p : participants) {
                    if (first) {
                        if (useDialog) {
                            marker.setupDialog(def.finishMarker.name, def.finishMarker.dialogId, p.getUuid());
                        } else {
                            marker.setup(def.finishMarker.name, def.finishMarker.actions, p.getUuid());
                        }
                        first = false;
                    } else {
                        marker.addVisibleTo(p.getUuid());
                    }
                }
                marker.refreshPositionAndAngles(mX, mY, mZ, 0, 0);
                dungeonWorld.spawnEntity(marker);
            }
        } else if (def.finishNpc != null && !def.finishNpc.dialogId.isEmpty()) {
            double npcX = dstOrigin.getX() + def.finishNpc.offset.x + 0.5;
            double npcY = dstOrigin.getY() + def.finishNpc.offset.y;
            double npcZ = dstOrigin.getZ() + def.finishNpc.offset.z + 0.5;

            GhostNpcSpawnConfig cfg = new GhostNpcSpawnConfig();
            cfg.dialogId = def.finishNpc.dialogId;
            cfg.name = def.finishNpc.name;
            cfg.skin = def.finishNpc.skin;
            cfg.autoDialog = false;
            cfg.despawnAfterDialog = false;

            GhostNpcManager.getInstance().spawnGhostNpcAtPos(
                participants, dungeonWorld, npcX, npcY, npcZ, cfg);
        }

        LOGGER.info("Dungeon '{}' instance created in slot {} for {} player(s)",
            dungeonId, slotId, participants.size());
        return true;
    }

    // ── Выход (без наград) ────────────────────────────────────────

    public boolean exitDungeon(ServerPlayerEntity player, MinecraftServer server) {
        DungeonInstance inst = playerInstance.get(player.getUuid());
        if (inst == null) {
            player.sendMessage(Text.literal("[Dungeon] You are not in a dungeon."), false);
            return false;
        }
        GhostNpcManager.getInstance().despawnAll(player.getUuid());
        teleportBack(player, inst, server);
        removeFromInstance(player.getUuid(), inst, server);
        player.playSound(PonyNpcSounds.DUNGEON_EXIT, 0.6f, 1.0f);
        player.sendMessage(Text.literal("[Dungeon] You left the dungeon."), false);
        return true;
    }

    // ── Завершение (с наградами) ──────────────────────────────────

    /**
     * Завершить данж: дать награды всем участникам, телепортировать назад, очистить слот.
     */
    public boolean completeDungeon(ServerPlayerEntity triggerPlayer, MinecraftServer server) {
        DungeonInstance inst = playerInstance.get(triggerPlayer.getUuid());
        if (inst == null) {
            triggerPlayer.sendMessage(Text.literal("[Dungeon] You are not in a dungeon."), false);
            return false;
        }
        if (inst.isCompleted()) return false;
        inst.markCompleted();

        DungeonDefinition def = DungeonRegistry.getInstance().getDungeon(inst.dungeonId).orElse(null);
        Set<UUID> members = new HashSet<>(inst.getPlayers());

        for (UUID memberId : members) {
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberId);
            if (member != null) {
                if (def != null) giveRewards(member, def);
                QuestManager.getInstance().completeDungeonStages(member, inst.dungeonId);
                GhostNpcManager.getInstance().despawnAll(memberId);
                teleportBack(member, inst, server);
                member.playSound(PonyNpcSounds.DUNGEON_COMPLETE, 0.8f, 1.0f);
                member.sendMessage(Text.literal("[Dungeon] Dungeon completed! Rewards given."), false);
            }
            playerInstance.remove(memberId);
        }

        clearSlot(inst, def, server);
        freeSlots.add(inst.slotId);
        LOGGER.info("Dungeon '{}' completed (slot {})", inst.dungeonId, inst.slotId);
        return true;
    }

    // ── Дисконнект / возрождение ──────────────────────────────────

    public void onPlayerDisconnect(UUID uuid) {
        DungeonInstance inst = playerInstance.remove(uuid);
        if (inst == null) return;
        inst.removePlayer(uuid);
        if (inst.isEmpty()) freeSlots.add(inst.slotId);
    }

    /** Вызывается когда игрок возродился вне dungeon-измерения (например, после смерти). */
    public void onPlayerRespawnedOutside(UUID uuid) {
        DungeonInstance inst = playerInstance.remove(uuid);
        if (inst == null) return;
        inst.removePlayer(uuid);
        if (inst.isEmpty()) freeSlots.add(inst.slotId);
        LOGGER.info("Cleared dungeon instance for respawned player {}", uuid);
    }

    /** Принудительно удалить игрока из инстанса без наград и телепортации (для /dungeon exit). */
    public void forceRemoveFromInstance(UUID uuid) {
        DungeonInstance inst = playerInstance.remove(uuid);
        if (inst == null) return;
        inst.removePlayer(uuid);
        if (inst.isEmpty()) freeSlots.add(inst.slotId);
    }

    /**
     * Проверяет, может ли игрок сломать блок в данже.
     * Сравнивает текущий блок слота с блоком в build-зоне:
     * если они отличаются — игрок сам поставил этот блок → разрешаем сломать.
     */
    public boolean canBreakBlock(UUID playerUuid, ServerWorld dungeonWorld, BlockPos pos, BlockState currentState) {
        DungeonInstance inst = playerInstance.get(playerUuid);
        if (inst == null) return true; // не в инстансе — это админ в build-режиме
        DungeonDefinition def = DungeonRegistry.getInstance().getDungeon(inst.dungeonId).orElse(null);
        if (def == null || def.destructible) return true;
        if (!def.hasBuildOrigin()) return true;

        BlockPos slotOrigin = inst.getSlotOrigin(def);
        int rx = pos.getX() - slotOrigin.getX();
        int ry = pos.getY() - slotOrigin.getY();
        int rz = pos.getZ() - slotOrigin.getZ();

        // Вне границ данжа → разрешаем (игрок построил за пределами)
        if (rx < 0 || rx >= def.getWidth() || ry < 0 || ry >= def.getHeight() || rz < 0 || rz >= def.getLength()) {
            return true;
        }

        // Сравниваем с блоком в build-зоне
        BlockPos buildPos = new BlockPos(def.buildOrigin.x + rx, def.buildOrigin.y + ry, def.buildOrigin.z + rz);
        BlockState buildState = dungeonWorld.getBlockState(buildPos);
        return !buildState.equals(currentState);
    }

    // ── Внутренние методы ─────────────────────────────────────────

    private int allocateSlot() {
        return freeSlots.isEmpty() ? nextSlot++ : freeSlots.poll();
    }

    private void removeFromInstance(UUID uuid, DungeonInstance inst, MinecraftServer server) {
        playerInstance.remove(uuid);
        inst.removePlayer(uuid);
        if (inst.isEmpty()) {
            DungeonDefinition def = DungeonRegistry.getInstance().getDungeon(inst.dungeonId).orElse(null);
            clearSlot(inst, def, server);
            freeSlots.add(inst.slotId);
        }
    }

    private void clearSlot(DungeonInstance inst, DungeonDefinition def, MinecraftServer server) {
        if (def == null || !def.hasBuildOrigin()) return;
        ServerWorld dungeonWorld = server.getWorld(DUNGEON_WORLD_KEY);
        if (dungeonWorld == null) return;
        BlockPos origin = inst.getSlotOrigin(def);
        DungeonCopier.clear(dungeonWorld, origin, def.getWidth(), def.getHeight(), def.getLength());
    }

    private void teleportBack(ServerPlayerEntity player, DungeonInstance inst, MinecraftServer server) {
        DungeonInstance.ReturnData ret = inst.getReturnData(player.getUuid());
        if (ret == null) return;
        ServerWorld returnWorld = server.getWorld(ret.worldKey());
        if (returnWorld == null) returnWorld = server.getOverworld();
        teleportPlayer(player, returnWorld, ret.x(), ret.y(), ret.z(), ret.yaw(), ret.pitch());
    }

    private static void teleportPlayer(ServerPlayerEntity player, ServerWorld world,
                                        double x, double y, double z, float yaw, float pitch) {
        player.teleport(world, x, y, z, Set.<PositionFlag>of(), yaw, pitch);
    }

    private static void giveRewards(ServerPlayerEntity player, DungeonDefinition def) {
        for (DungeonDefinition.RewardDef reward : def.rewards) {
            if (reward == null || reward.type == null) continue;
            switch (reward.type) {
                case "currency" -> {
                    int amount = reward.count;
                    while (amount > 0) {
                        int batch = Math.min(amount, 64);
                        player.giveItemStack(new ItemStack(Items.GOLD_NUGGET, batch));
                        amount -= batch;
                    }
                }
                case "item" -> {
                    if (reward.id == null) break;
                    Identifier id = Identifier.tryParse(reward.id);
                    if (id == null) break;
                    var item = Registries.ITEM.get(id);
                    if (item != Items.AIR)
                        player.giveItemStack(new ItemStack(item, Math.max(1, reward.count)));
                }
                case "quest_item" -> {
                    if (reward.id != null) {
                        QuestPocketState.getOrCreate(player.getServer())
                            .giveItem(player.getUuid(), reward.id, Math.max(1, reward.count));
                        player.playSound(PonyNpcSounds.ITEM_RECEIVE, 0.8f, 1.2f);
                    }
                }
            }
        }
    }
}
