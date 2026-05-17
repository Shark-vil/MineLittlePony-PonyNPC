package com.minelittlepony.ponynpc.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: игрок запрашивает данные дневника квестов. */
public record RequestQuestJournalPayload() implements CustomPayload {

    public static final Id<RequestQuestJournalPayload> ID =
        new Id<>(Identifier.of("ponynpc", "request_quest_journal"));

    public static final PacketCodec<PacketByteBuf, RequestQuestJournalPayload> CODEC =
        new PacketCodec<>() {
            @Override public RequestQuestJournalPayload decode(PacketByteBuf buf) { return new RequestQuestJournalPayload(); }
            @Override public void encode(PacketByteBuf buf, RequestQuestJournalPayload v) {}
        };

    @Override public Id<RequestQuestJournalPayload> getId() { return ID; }
}
