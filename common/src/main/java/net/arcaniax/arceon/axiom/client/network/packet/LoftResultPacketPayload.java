package net.arcaniax.arceon.axiom.client.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public class LoftResultPacketPayload implements CustomPacketPayload {
    public static final Type<LoftResultPacketPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("arceon", "loft_result"));

    public static final StreamCodec<ByteBuf, LoftResultPacketPayload> CODEC =
            StreamCodec.of(LoftResultPacketPayload::write, LoftResultPacketPayload::read);

    private final int protocolVersion;
    private final UUID sessionId;
    private final boolean accepted;
    private final String code;
    private final String message;

    public LoftResultPacketPayload(int protocolVersion, UUID sessionId, boolean accepted, String code, String message) {
        this.protocolVersion = protocolVersion;
        this.sessionId = sessionId;
        this.accepted = accepted;
        this.code = code;
        this.message = message;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public boolean accepted() {
        return accepted;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    private static void write(ByteBuf buf, LoftResultPacketPayload packet) {
        buf.writeInt(packet.protocolVersion);
        LoftPacketCodec.writeUuid(buf, packet.sessionId);
        buf.writeBoolean(packet.accepted);
        LoftPacketCodec.writeString(buf, packet.code);
        LoftPacketCodec.writeString(buf, packet.message);
    }

    private static LoftResultPacketPayload read(ByteBuf buf) {
        int protocolVersion = buf.readInt();
        UUID sessionId = LoftPacketCodec.readUuid(buf);
        boolean accepted = buf.readBoolean();
        String code = LoftPacketCodec.readString(buf);
        String message = LoftPacketCodec.readString(buf);
        return new LoftResultPacketPayload(protocolVersion, sessionId, accepted, code, message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
