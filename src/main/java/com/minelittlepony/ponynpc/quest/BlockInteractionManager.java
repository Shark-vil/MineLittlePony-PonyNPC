package com.minelittlepony.ponynpc.quest;

import com.minelittlepony.ponynpc.entity.VirtualDialogContext;
import com.minelittlepony.ponynpc.network.NetworkHandler;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обрабатывает ПКМ по блокам в зарегистрированных block_zones.
 *
 * Логика разрешения диалога для блока (приоритет по убыванию):
 *  1. blocks[]        — фиксированные позиции с явным dialog
 *  2. random_picks[]  — per-player рандомно назначенные "особые" блоки
 *  3. default_dialogs — случайный из пула (или одиночный default_dialog)
 *
 * Random picks вычисляются один раз при первом взаимодействии игрока с зоной
 * и хранятся до дисконнекта.
 */
public final class BlockInteractionManager {

    private static final BlockInteractionManager INSTANCE = new BlockInteractionManager();
    private static final Random RNG = new Random();

    /** playerUuid → (contextUuid → VirtualDialogContext) — активные открытые диалоги */
    private final Map<UUID, Map<UUID, VirtualDialogContext>> activeContexts = new ConcurrentHashMap<>();

    /** playerUuid → zoneId → (blockPos → {dialogId, name}) — рандомные назначения per-player */
    private final Map<UUID, Map<String, Map<BlockPos, String[]>>> playerPickAssignments = new ConcurrentHashMap<>();

    private BlockInteractionManager() {}

    public static BlockInteractionManager getInstance() { return INSTANCE; }

    public void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            Block clickedBlock = sw.getBlockState(pos).getBlock();

            for (BlockZoneDefinition zone : BlockZoneRegistry.getInstance().getZones()) {
                if (!zone.isInRegion(pos, sw.getRegistryKey())) continue;

                Identifier targetId = Identifier.tryParse(zone.target_block);
                if (targetId == null) continue;
                if (!Registries.BLOCK.get(targetId).equals(clickedBlock)) continue;

                if (!zone.condition.isEmpty()
                        && !QuestConditionChecker.check(sp, zone.condition)) continue;

                String[] resolution = resolveBlock(sp, zone, pos, sw);
                String dialogId = resolution[0];
                String name     = resolution[1];

                if (dialogId.isEmpty()) continue;

                UUID ctxUuid = VirtualDialogContext.blockUuid(pos);
                VirtualDialogContext ctx = new VirtualDialogContext(ctxUuid, dialogId, name);
                activeContexts.computeIfAbsent(sp.getUuid(), k -> new ConcurrentHashMap<>())
                    .put(ctxUuid, ctx);

                NetworkHandler.openDialogForPlayer(sp, ctx);
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });
    }

    // ── Разрешение диалога для конкретного блока ─────────────────

    /**
     * Возвращает {dialogId, displayName} для блока в зоне.
     * Приоритет: fixed blocks → random_picks → default pool.
     */
    private String[] resolveBlock(ServerPlayerEntity player, BlockZoneDefinition zone,
                                   BlockPos pos, ServerWorld world) {
        // 1. Фиксированные позиции
        if (zone.blocks != null) {
            for (BlockZoneDefinition.BlockEntry e : zone.blocks) {
                if (e.x == pos.getX() && e.y == pos.getY() && e.z == pos.getZ()) {
                    String d = e.dialog.isEmpty() ? zone.resolveDefaultDialog(RNG) : e.dialog;
                    String n = e.name.isEmpty()   ? zone.default_name              : e.name;
                    return new String[]{d, n};
                }
            }
        }

        // 2. Random picks (per-player)
        if (zone.hasRandomPicks()) {
            Map<BlockPos, String[]> picks = getOrComputePicks(player, zone, world);
            String[] pick = picks.get(pos);
            if (pick != null) return pick;
        }

        // 3. Default pool
        return new String[]{zone.resolveDefaultDialog(RNG), zone.default_name};
    }

    /**
     * Возвращает уже вычисленные или вычисляет новые random_picks для игрока.
     * Сканирует зону на наличие target_block, исключает fixed positions,
     * перемешивает и назначает count блоков каждой RandomPickEntry.
     */
    private Map<BlockPos, String[]> getOrComputePicks(ServerPlayerEntity player,
                                                       BlockZoneDefinition zone,
                                                       ServerWorld world) {
        return playerPickAssignments
            .computeIfAbsent(player.getUuid(), k -> new ConcurrentHashMap<>())
            .computeIfAbsent(zone.id, k -> computePicks(zone, world));
    }

    private static Map<BlockPos, String[]> computePicks(BlockZoneDefinition zone, ServerWorld world) {
        // Собираем fixed-позиции чтобы исключить их из кандидатов
        Set<BlockPos> fixedPositions = new HashSet<>();
        if (zone.blocks != null) {
            for (BlockZoneDefinition.BlockEntry e : zone.blocks) {
                fixedPositions.add(new BlockPos(e.x, e.y, e.z));
            }
        }

        Block targetBlock = Registries.BLOCK.get(Identifier.tryParse(zone.target_block));

        // Сканируем регион — находим все matching блоки, не занятые fixed
        List<BlockPos> candidates = new ArrayList<>();
        for (int x = zone.min_x; x <= zone.max_x; x++) {
            for (int y = zone.min_y; y <= zone.max_y; y++) {
                for (int z = zone.min_z; z <= zone.max_z; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!fixedPositions.contains(p)
                            && world.getBlockState(p).getBlock().equals(targetBlock)) {
                        candidates.add(p);
                    }
                }
            }
        }

        Collections.shuffle(candidates, RNG);

        Map<BlockPos, String[]> result = new HashMap<>();
        int idx = 0;
        for (BlockZoneDefinition.RandomPickEntry pick : zone.random_picks) {
            for (int i = 0; i < pick.count && idx < candidates.size(); i++, idx++) {
                String name = (pick.name == null || pick.name.isEmpty())
                    ? zone.default_name : pick.name;
                result.put(candidates.get(idx), new String[]{pick.dialog, name});
            }
        }
        return result;
    }

    // ── Управление активными контекстами ─────────────────────────

    public Optional<VirtualDialogContext> getContext(UUID playerUuid, UUID contextUuid) {
        Map<UUID, VirtualDialogContext> map = activeContexts.get(playerUuid);
        if (map == null) return Optional.empty();
        return Optional.ofNullable(map.get(contextUuid));
    }

    public void removeContext(UUID playerUuid, UUID contextUuid) {
        Map<UUID, VirtualDialogContext> map = activeContexts.get(playerUuid);
        if (map != null) map.remove(contextUuid);
    }

    /** Сбросить per-player назначения random_picks для конкретной зоны (например при смене этапа). */
    public void resetPicksForZone(UUID playerUuid, String zoneId) {
        Map<String, Map<BlockPos, String[]>> zones = playerPickAssignments.get(playerUuid);
        if (zones != null) zones.remove(zoneId);
    }

    public void clearForPlayer(UUID playerUuid) {
        activeContexts.remove(playerUuid);
        playerPickAssignments.remove(playerUuid);
    }
}
