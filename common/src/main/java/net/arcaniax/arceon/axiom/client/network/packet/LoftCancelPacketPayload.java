package net.arcaniax.arceon.axiom.client.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public class LoftCancelPacketPayload implements CustomPacketPayload {
    public static final Type<LoftCancelPacketPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("arceon", "loft_cancel"));

    public static final StreamCodec<ByteBuf, LoftCancelPacketPayload> CODEC =
            StreamCodec.of(LoftCancelPacketPayload::write, LoftCancelPacketPayload::read);

    private final int protocolVersion;
    private final UUID sessionId;

    public LoftCancelPacketPayload(int protocolVersion, UUID sessionId) {
        this.protocolVersion = protocolVersion;
        this.sessionId = sessionId;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public UUID sessionId() {
        return sessionId;
    }

    private static void write(ByteBuf buf, LoftCancelPacketPayload packet) {
        buf.writeInt(packet.protocolVersion);
        LoftPacketCodec.writeUuid(buf, packet.sessionId);
    }

    private static LoftCancelPacketPayload read(ByteBuf buf) {
        int protocolVersion = buf.readInt();
        UUID sessionId = LoftPacketCodec.readUuid(buf);
        return new LoftCancelPacketPayload(protocolVersion, sessionId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
