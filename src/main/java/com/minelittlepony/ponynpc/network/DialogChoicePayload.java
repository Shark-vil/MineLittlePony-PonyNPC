package com.minelittlepony.ponynpc.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * C2S: Игрок выбрал вариант ответа в диалоге.
 * Специальный ID "__close__" означает что игрок закрыл окно через ESC.
 */
public record DialogChoicePayload(UUID npcUuid, String nodeId, String choiceId) implements CustomPayload {

    public static final Id<DialogChoicePayload> ID = new Id<>(Identifier.of("ponynpc", "dialog_choice"));

    public static final PacketCodec<PacketByteBuf, DialogChoicePayload> CODEC = new PacketCodec<>() {
        @Override
        public DialogChoicePayload decode(PacketByteBuf buf) {
            return new DialogChoicePayload(buf.readUuid(), buf.readString(), buf.readString());
        }

        @Override
        public void encode(PacketByteBuf buf, DialogChoicePayload value) {
            buf.writeUuid(value.npcUuid());
            buf.writeString(value.nodeId());
            buf.writeString(value.choiceId());
        }
    };

    @Override
    public Id<DialogChoicePayload> getId() {
        return ID;
    }
}
