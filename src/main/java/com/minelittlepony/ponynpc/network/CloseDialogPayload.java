package com.minelittlepony.ponynpc.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * S2C: Сервер закрывает диалоговое окно у игрока.
 */
public record CloseDialogPayload(UUID npcUuid) implements CustomPayload {

    public static final Id<CloseDialogPayload> ID = new Id<>(Identifier.of("ponynpc", "close_dialog"));

    public static final PacketCodec<PacketByteBuf, CloseDialogPayload> CODEC = new PacketCodec<>() {
        @Override
        public CloseDialogPayload decode(PacketByteBuf buf) {
            return new CloseDialogPayload(buf.readUuid());
        }

        @Override
        public void encode(PacketByteBuf buf, CloseDialogPayload value) {
            buf.writeUuid(value.npcUuid());
        }
    };

    @Override
    public Id<CloseDialogPayload> getId() {
        return ID;
    }
}
