package com.minelittlepony.ponynpc.party;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Управляет партиями игроков. Данные хранятся в памяти (не persistent). */
public final class PartyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("PonyNPC/Party");
    private static final PartyManager INSTANCE = new PartyManager();

    /** UUID игрока → его пати. */
    private final Map<UUID, Party> playerParty = new HashMap<>();
    /** Pending invites: UUID целевого игрока → пати, куда его приглашают. */
    private final Map<UUID, Party> pendingInvites = new HashMap<>();

    private PartyManager() {}

    public static PartyManager getInstance() { return INSTANCE; }

    // ── API ───────────────────────────────────────────────────────

    public Optional<Party> getParty(UUID playerUuid) {
        return Optional.ofNullable(playerParty.get(playerUuid));
    }

    public boolean isInParty(UUID playerUuid) { return playerParty.containsKey(playerUuid); }

    /** Создать новую пати (игрок становится лидером). */
    public boolean createParty(ServerPlayerEntity leader) {
        if (isInParty(leader.getUuid())) {
            leader.sendMessage(Text.literal("[Party] You are already in a party. Leave first."), false);
            return false;
        }
        Party party = new Party(leader.getUuid());
        playerParty.put(leader.getUuid(), party);
        leader.sendMessage(Text.literal("[Party] Party created. Invite players with /party invite <player>"), false);
        LOGGER.info("Party created by {}", leader.getName().getString());
        return true;
    }

    /** Пригласить игрока в пати лидера. */
    public boolean invite(ServerPlayerEntity leader, ServerPlayerEntity target) {
        Party party = playerParty.get(leader.getUuid());
        if (party == null) {
            leader.sendMessage(Text.literal("[Party] You are not in a party. Use /party create."), false);
            return false;
        }
        if (!party.isLeader(leader.getUuid())) {
            leader.sendMessage(Text.literal("[Party] Only the leader can invite."), false);
            return false;
        }
        if (party.isMember(target.getUuid())) {
            leader.sendMessage(Text.literal("[Party] " + target.getName().getString() + " is already in the party."), false);
            return false;
        }
        if (isInParty(target.getUuid())) {
            leader.sendMessage(Text.literal("[Party] " + target.getName().getString() + " is in another party."), false);
            return false;
        }
        party.invite(target.getUuid());
        pendingInvites.put(target.getUuid(), party);

        target.sendMessage(Text.literal("[Party] You were invited by " + leader.getName().getString()
            + ". Type /party join " + leader.getName().getString() + " to accept."), false);
        leader.sendMessage(Text.literal("[Party] Invitation sent to " + target.getName().getString()), false);
        return true;
    }

    /** Принять приглашение в пати лидера. */
    public boolean join(ServerPlayerEntity player, ServerPlayerEntity leader, MinecraftServer server) {
        if (isInParty(player.getUuid())) {
            player.sendMessage(Text.literal("[Party] Leave your current party first."), false);
            return false;
        }
        Party party = playerParty.get(leader.getUuid());
        if (party == null || !party.isLeader(leader.getUuid())) {
            player.sendMessage(Text.literal("[Party] " + leader.getName().getString() + " has no party."), false);
            return false;
        }
        if (!party.hasPendingInvite(player.getUuid())) {
            player.sendMessage(Text.literal("[Party] No pending invite from " + leader.getName().getString()), false);
            return false;
        }
        party.addMember(player.getUuid());
        playerParty.put(player.getUuid(), party);
        pendingInvites.remove(player.getUuid());
        broadcastToParty(party, Text.literal("[Party] " + player.getName().getString() + " joined the party!"), server);
        return true;
    }

    /** Покинуть пати. */
    public boolean leave(ServerPlayerEntity player, MinecraftServer server) {
        Party party = playerParty.get(player.getUuid());
        if (party == null) {
            player.sendMessage(Text.literal("[Party] You are not in a party."), false);
            return false;
        }
        removeFromParty(player.getUuid(), party, server);
        player.sendMessage(Text.literal("[Party] You left the party."), false);
        return true;
    }

    /** Исключить игрока из пати (только лидер). */
    public boolean kick(ServerPlayerEntity leader, ServerPlayerEntity target, MinecraftServer server) {
        Party party = playerParty.get(leader.getUuid());
        if (party == null || !party.isLeader(leader.getUuid())) {
            leader.sendMessage(Text.literal("[Party] You are not the party leader."), false);
            return false;
        }
        if (!party.isMember(target.getUuid())) {
            leader.sendMessage(Text.literal("[Party] " + target.getName().getString() + " is not in the party."), false);
            return false;
        }
        if (target.getUuid().equals(leader.getUuid())) {
            leader.sendMessage(Text.literal("[Party] Use /party leave to disband."), false);
            return false;
        }
        removeFromParty(target.getUuid(), party, server);
        target.sendMessage(Text.literal("[Party] You were kicked from the party."), false);
        broadcastToParty(party, Text.literal("[Party] " + target.getName().getString() + " was kicked."), server);
        return true;
    }

    /** При выходе игрока с сервера — убрать из пати, очистить pending invite. */
    public void onPlayerDisconnect(UUID uuid, MinecraftServer server) {
        pendingInvites.remove(uuid);
        Party party = playerParty.get(uuid);
        if (party == null) return;
        removeFromParty(uuid, party, server);
    }

    private void removeFromParty(UUID uuid, Party party, MinecraftServer server) {
        party.removeMember(uuid);
        playerParty.remove(uuid);
        pendingInvites.remove(uuid);

        if (party.size() == 0) {
            LOGGER.debug("Party of {} disbanded (empty)", uuid);
            return;
        }

        if (party.isLeader(uuid)) {
            disbandParty(party, server);
        } else {
            broadcastToParty(party, Text.literal("[Party] A member left the party."), server);
        }
    }

    private void disbandParty(Party party, MinecraftServer server) {
        for (UUID memberId : new HashSet<>(party.getMembers())) {
            playerParty.remove(memberId);
            pendingInvites.remove(memberId);
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberId);
            if (member != null) {
                member.sendMessage(Text.literal("[Party] Party was disbanded (leader left)."), false);
            }
        }
        LOGGER.info("Party disbanded");
    }

    /** Отправить сообщение всем участникам пати. */
    public void broadcastToParty(Party party, Text message, MinecraftServer server) {
        for (UUID memberId : party.getMembers()) {
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberId);
            if (member != null) member.sendMessage(message, false);
        }
    }
}
