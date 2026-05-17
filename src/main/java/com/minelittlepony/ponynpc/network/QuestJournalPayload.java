package com.minelittlepony.ponynpc.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** S2C: сервер отправляет игроку данные дневника квестов. */
public record QuestJournalPayload(List<QuestEntry> entries) implements CustomPayload {

    public static final Id<QuestJournalPayload> ID =
        new Id<>(Identifier.of("ponynpc", "quest_journal"));

    public static final PacketCodec<PacketByteBuf, QuestJournalPayload> CODEC =
        new PacketCodec<>() {
            @Override
            public QuestJournalPayload decode(PacketByteBuf buf) {
                int count = buf.readVarInt();
                List<QuestEntry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) entries.add(QuestEntry.decode(buf));
                return new QuestJournalPayload(entries);
            }

            @Override
            public void encode(PacketByteBuf buf, QuestJournalPayload v) {
                buf.writeVarInt(v.entries().size());
                for (QuestEntry e : v.entries()) e.encode(buf);
            }
        };

    @Override public Id<QuestJournalPayload> getId() { return ID; }

    public record QuestEntry(String questName, boolean complete, List<StageEntry> stages) {
        public static QuestEntry decode(PacketByteBuf buf) {
            String name    = buf.readString();
            boolean done   = buf.readBoolean();
            int count      = buf.readVarInt();
            List<StageEntry> stages = new ArrayList<>(count);
            for (int i = 0; i < count; i++) stages.add(StageEntry.decode(buf));
            return new QuestEntry(name, done, stages);
        }
        public void encode(PacketByteBuf buf) {
            buf.writeString(questName);
            buf.writeBoolean(complete);
            buf.writeVarInt(stages.size());
            for (StageEntry s : stages) s.encode(buf);
        }
    }

    /**
     * @param status  0 = будущий, 1 = текущий, 2 = завершён
     * @param collected / required  для MULTI_COLLECT (иначе 0)
     */
    public record StageEntry(String description, int status, int collected, int required) {
        public static StageEntry decode(PacketByteBuf buf) {
            return new StageEntry(buf.readString(32767), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
        public void encode(PacketByteBuf buf) {
            buf.writeString(description);
            buf.writeVarInt(status);
            buf.writeVarInt(collected);
            buf.writeVarInt(required);
        }
    }
}
