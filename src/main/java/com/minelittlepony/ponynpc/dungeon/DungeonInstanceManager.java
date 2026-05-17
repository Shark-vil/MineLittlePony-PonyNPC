package com.minelittlepony.ponynpc.dungeon;

import com.minelittlepony.ponynpc.party.Party;
import com.minelittlepony.ponynpc.party.PartyManager;
import com.minelittlepony.ponynpc.state.QuestPocketState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Управляет активными инстансами данжей. */
public final class DungeonInstanceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/DungeonInstance");
    private static final DungeonInstanceManager INSTANCE = new DungeonInstanceManager();

    public static final RegistryKey<World> DUNGEON_WORLD_KEY =
        RegistryKey.of(RegistryKeys.WORLD, Identifier.of("ponynpc", "dungeon"));

    /** UUID игрока → его инстанс. */
    private final Map<UUID, DungeonInstance> playerInstance = new HashMap<>();
    private int nextSlot = 0;
    private final Deque<Integer> freeSlots = new ArrayDeque<>();

    private DungeonInstanceManager() {}

    public static DungeonInstanceManager getInstance() { return INSTANCE; }

    // ── API ───────────────────────────────────────────────────────

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

    /**
     * Войти в данж.
     * Если игрок — лидер пати, вся пати входит вместе (solo иначе).
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
        ServerWorld dungeonWorld = server.getWorld(DUNGEON_WORLD_KEY);
        if (dungeonWorld == null) {
            LOGGER.error("Dungeon world '{}' is not loaded!", DUNGEON_WORLD_KEY.getValue());
            leader.sendMessage(Text.literal("[Dungeon] Dungeon world is not available."), false);
            return false;
        }

        int slotId = allocateSlot();
        DungeonInstance inst = new DungeonInstance(slotId, dungeonId);

        // Collect participants: leader + party members (if leader leads a party)
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

        // Generate platform before teleporting
        if (def.generatePlatform) {
            int ox = inst.getCenterX() - def.platformWidth  / 2;
            int oz = inst.getCenterZ() - def.platformLength / 2;
            DungeonGenerator.generatePlatform(dungeonWorld, ox, 64, oz,
                def.platformWidth, def.platformLength);
        }

        // Spawn position: just inside the entrance (south end)
        double spawnX = inst.getCenterX() + 0.5;
        double spawnY = 65;
        double spawnZ = inst.getCenterZ() - def.platformLength / 2.0 + 2.5;

        for (ServerPlayerEntity player : participants) {
            DungeonInstance.ReturnData ret = new DungeonInstance.ReturnData(
                player.getServerWorld().getRegistryKey(),
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch()
            );
            inst.addPlayer(player.getUuid(), ret);
            playerInstance.put(player.getUuid(), inst);
            teleportPlayer(player, dungeonWorld, spawnX, spawnY, spawnZ, 0f, 0f);
            player.sendMessage(Text.literal("[Dungeon] You entered: " + def.name), false);
        }

        LOGGER.info("Dungeon '{}' instance created in slot {} with {} player(s)",
            dungeonId, slotId, participants.size());
        return true;
    }

    /** Выйти из данжа без наград. */
    public boolean exitDungeon(ServerPlayerEntity player, MinecraftServer server) {
        DungeonInstance inst = playerInstance.get(player.getUuid());
        if (inst == null) {
            player.sendMessage(Text.literal("[Dungeon] You are not in a dungeon."), false);
            return false;
        }
        teleportBack(player, inst, server);
        removeFromInstance(player.getUuid(), inst);
        player.sendMessage(Text.literal("[Dungeon] You left the dungeon."), false);
        return true;
    }

    /**
     * Завершить данж: выдать награды всем участникам и телепортировать назад.
     * Вызывается action "dungeon:complete:<id>" через QuestActionExecutor.
     */
    public boolean completeDungeon(ServerPlayerEntity triggerPlayer, MinecraftServer server) {
        DungeonInstance inst = playerInstance.get(triggerPlayer.getUuid());
        if (inst == null) {
            triggerPlayer.sendMessage(Text.literal("[Dungeon] You are not in a dungeon."), false);
            return false;
        }
        if (inst.isCompleted()) return false; // prevent double-complete
        inst.markCompleted();

        DungeonDefinition def = DungeonRegistry.getInstance().getDungeon(inst.dungeonId).orElse(null);
        for (UUID memberId : new HashSet<>(inst.getPlayers())) {
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberId);
            if (member != null) {
                if (def != null) giveRewards(member, def);
                teleportBack(member, inst, server);
                member.sendMessage(Text.literal("[Dungeon] Dungeon completed! Rewards given."), false);
            }
            playerInstance.remove(memberId);
        }
        freeSlots.add(inst.slotId);
        LOGGER.info("Dungeon '{}' completed (slot {})", inst.dungeonId, inst.slotId);
        return true;
    }

    /** Вызывается при дисконнекте — убирает игрока из инстанса без телепорта. */
    public void onPlayerDisconnect(UUID uuid) {
        DungeonInstance inst = playerInstance.remove(uuid);
        if (inst != null) {
            inst.removePlayer(uuid);
            if (inst.isEmpty()) freeSlots.add(inst.slotId);
        }
    }

    // ── Внутренние методы ─────────────────────────────────────────

    private int allocateSlot() {
        return freeSlots.isEmpty() ? nextSlot++ : freeSlots.poll();
    }

    private void removeFromInstance(UUID uuid, DungeonInstance inst) {
        playerInstance.remove(uuid);
        inst.removePlayer(uuid);
        if (inst.isEmpty()) freeSlots.add(inst.slotId);
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
                    if (reward.id != null)
                        QuestPocketState.getOrCreate(player.getServer())
                            .giveItem(player.getUuid(), reward.id, Math.max(1, reward.count));
                }
            }
        }
    }
}
