package com.minelittlepony.ponynpc.state;

import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Виртуальный «квестовый карман» игрока — хранит квестовые предметы.
 * Предметы нельзя выбросить или потерять при смерти (они не в инвентаре).
 * Привязан к overworld PersistentState (server-wide).
 */
public class QuestPocketState extends PersistentState {

    private static final String SAVE_ID = "ponynpc_quest_pocket";

    public static final Type<QuestPocketState> TYPE = new Type<>(
        QuestPocketState::new,
        QuestPocketState::fromNbt,
        null
    );

    // playerUUID → itemId → count
    private final Map<UUID, Map<String, Integer>> pockets = new HashMap<>();

    private QuestPocketState() {}

    public static QuestPocketState getOrCreate(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, SAVE_ID);
    }

    // ── API ───────────────────────────────────────────────────────

    public boolean hasItem(UUID playerUuid, String itemId) {
        Map<String, Integer> pocket = pockets.get(playerUuid);
        return pocket != null && pocket.getOrDefault(itemId, 0) > 0;
    }

    public int getCount(UUID playerUuid, String itemId) {
        Map<String, Integer> pocket = pockets.get(playerUuid);
        return pocket == null ? 0 : pocket.getOrDefault(itemId, 0);
    }

    public void giveItem(UUID playerUuid, String itemId, int count) {
        pockets.computeIfAbsent(playerUuid, u -> new HashMap<>())
               .merge(itemId, count, Integer::sum);
        markDirty();
    }

    /** Забирает предмет. Возвращает false если предмета нет. */
    public boolean takeItem(UUID playerUuid, String itemId) {
        return takeItem(playerUuid, itemId, 1);
    }

    public boolean takeItem(UUID playerUuid, String itemId, int count) {
        Map<String, Integer> pocket = pockets.get(playerUuid);
        if (pocket == null) return false;
        int have = pocket.getOrDefault(itemId, 0);
        if (have < count) return false;
        int remaining = have - count;
        if (remaining == 0) pocket.remove(itemId);
        else pocket.put(itemId, remaining);
        markDirty();
        return true;
    }

    /** Возвращает все предметы игрока (копия). */
    public Map<String, Integer> getPocket(UUID playerUuid) {
        Map<String, Integer> pocket = pockets.get(playerUuid);
        return pocket == null ? Collections.emptyMap() : Collections.unmodifiableMap(pocket);
    }

    // ── NBT ───────────────────────────────────────────────────────

    public static QuestPocketState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        QuestPocketState state = new QuestPocketState();
        NbtCompound playersNbt = nbt.getCompound("players");
        for (String uuidStr : playersNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                NbtCompound pocketNbt = playersNbt.getCompound(uuidStr);
                Map<String, Integer> pocket = new HashMap<>();
                for (String itemId : pocketNbt.getKeys()) {
                    pocket.put(itemId, pocketNbt.getInt(itemId));
                }
                state.pockets.put(uuid, pocket);
            } catch (IllegalArgumentException ignored) {}
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound playersNbt = new NbtCompound();
        pockets.forEach((uuid, pocket) -> {
            NbtCompound pocketNbt = new NbtCompound();
            pocket.forEach(pocketNbt::putInt);
            playersNbt.put(uuid.toString(), pocketNbt);
        });
        nbt.put("players", playersNbt);
        return nbt;
    }
}
