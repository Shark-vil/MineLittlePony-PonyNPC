package com.minelittlepony.ponynpc.party;

import java.util.*;

/** Группа игроков для совместного прохождения данжей. */
public class Party {
    private final UUID leaderId;
    private final Set<UUID> members = new LinkedHashSet<>(); // включает лидера
    /** UUID игроков, которым отправлено приглашение (ещё не приняли). */
    private final Set<UUID> pendingInvites = new HashSet<>();

    public Party(UUID leaderId) {
        this.leaderId = leaderId;
        this.members.add(leaderId);
    }

    public UUID getLeaderId() { return leaderId; }
    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }
    public int size() { return members.size(); }

    public boolean isMember(UUID uuid) { return members.contains(uuid); }
    public boolean isLeader(UUID uuid) { return leaderId.equals(uuid); }

    public void addMember(UUID uuid) {
        members.add(uuid);
        pendingInvites.remove(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public boolean hasPendingInvite(UUID uuid) { return pendingInvites.contains(uuid); }

    public void invite(UUID uuid) { pendingInvites.add(uuid); }
    public void cancelInvite(UUID uuid) { pendingInvites.remove(uuid); }
}
