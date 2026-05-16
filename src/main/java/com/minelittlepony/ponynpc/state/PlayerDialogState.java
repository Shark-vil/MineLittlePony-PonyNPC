package com.minelittlepony.ponynpc.state;

import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Хранит прогресс диалогов на стороне сервера (per-world).
 * Ключ прогресса: dialogId + ":" + npcUuid (чтобы разные NPC с одним диалогом имели независимый прогресс).
 */
public class PlayerDialogState extends PersistentState {

    private static final String SAVE_ID = "ponynpc_dialog_progress";

    public static final Type<PlayerDialogState> TYPE = new Type<>(
        PlayerDialogState::new,
        PlayerDialogState::fromNbt,
        null
    );

    // playerUUID -> (dialogKey -> Set<choiceId>)
    private final Map<UUID, Map<String, Set<String>>> progress = new HashMap<>();

    private PlayerDialogState() {}

    public static PlayerDialogState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
        PlayerDialogState state = new PlayerDialogState();
        NbtCompound playersNbt = nbt.getCompound("players");
        for (String playerUuidStr : playersNbt.getKeys()) {
            try {
                UUID playerUuid = UUID.fromString(playerUuidStr);
                NbtCompound dialogsNbt = playersNbt.getCompound(playerUuidStr);
                Map<String, Set<String>> dialogs = new HashMap<>();
                for (String dialogKey : dialogsNbt.getKeys()) {
                    NbtList choicesNbt = dialogsNbt.getList(dialogKey, NbtElement.STRING_TYPE);
                    Set<String> choices = new HashSet<>();
                    for (int i = 0; i < choicesNbt.size(); i++) {
                        choices.add(choicesNbt.getString(i));
                    }
                    dialogs.put(dialogKey, choices);
                }
                state.progress.put(playerUuid, dialogs);
            } catch (IllegalArgumentException ignored) {
                // Некорректный UUID — пропускаем
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
        NbtCompound playersNbt = new NbtCompound();
        progress.forEach((playerUuid, dialogs) -> {
            NbtCompound dialogsNbt = new NbtCompound();
            dialogs.forEach((dialogKey, choices) -> {
                NbtList choicesNbt = new NbtList();
                choices.forEach(c -> choicesNbt.add(NbtString.of(c)));
                dialogsNbt.put(dialogKey, choicesNbt);
            });
            playersNbt.put(playerUuid.toString(), dialogsNbt);
        });
        nbt.put("players", playersNbt);
        return nbt;
    }

    /** Проверяет, был ли уже выбран этот вариант игроком. */
    public boolean isChoiceDone(UUID playerUuid, String dialogKey, String choiceId) {
        return progress
            .getOrDefault(playerUuid, Map.of())
            .getOrDefault(dialogKey, Set.of())
            .contains(choiceId);
    }

    /** Помечает вариант как выполненный. */
    public void markChoiceDone(UUID playerUuid, String dialogKey, String choiceId) {
        progress
            .computeIfAbsent(playerUuid, u -> new HashMap<>())
            .computeIfAbsent(dialogKey, d -> new HashSet<>())
            .add(choiceId);
        markDirty();
    }

    public static PlayerDialogState getOrCreate(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, SAVE_ID);
    }
}
