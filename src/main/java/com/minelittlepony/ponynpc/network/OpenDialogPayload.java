package com.minelittlepony.ponynpc.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S2C: Сервер открывает диалоговое окно у игрока.
 */
public record OpenDialogPayload(
    UUID npcUuid,
    String npcName,
    String dialogId,
    String nodeId,
    String text,
    String sound,
    int soundChars,
    List<ChoiceData> choices
) implements CustomPayload {

    public static final Id<OpenDialogPayload> ID = new Id<>(Identifier.of("ponynpc", "open_dialog"));

    public static final PacketCodec<PacketByteBuf, OpenDialogPayload> CODEC = new PacketCodec<>() {
        @Override
        public OpenDialogPayload decode(PacketByteBuf buf) {
            UUID npcUuid = buf.readUuid();
            String npcName = buf.readString();
            String dialogId = buf.readString();
            String nodeId = buf.readString();
            String text = buf.readString(32767);
            String sound = buf.readString();
            int soundChars = buf.readVarInt();
            int choiceCount = buf.readVarInt();
            List<ChoiceData> choices = new ArrayList<>(choiceCount);
            for (int i = 0; i < choiceCount; i++) {
                choices.add(ChoiceData.decode(buf));
            }
            return new OpenDialogPayload(npcUuid, npcName, dialogId, nodeId, text, sound, soundChars, choices);
        }

        @Override
        public void encode(PacketByteBuf buf, OpenDialogPayload value) {
            buf.writeUuid(value.npcUuid());
            buf.writeString(value.npcName());
            buf.writeString(value.dialogId());
            buf.writeString(value.nodeId());
            buf.writeString(value.text());
            buf.writeString(value.sound());
            buf.writeVarInt(value.soundChars());
            buf.writeVarInt(value.choices().size());
            for (ChoiceData choice : value.choices()) {
                choice.encode(buf);
            }
        }
    };

    @Override
    public Id<OpenDialogPayload> getId() {
        return ID;
    }

    public record ChoiceData(String id, String text, boolean done) {
        public static ChoiceData decode(PacketByteBuf buf) {
            return new ChoiceData(buf.readString(), buf.readString(), buf.readBoolean());
        }

        public void encode(PacketByteBuf buf) {
            buf.writeString(id);
            buf.writeString(text);
            buf.writeBoolean(done);
        }
    }
}
