package net.arcaniax.arceon.axiom.client.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class LoftHelloPacketPayload implements CustomPacketPayload {
    public static final Type<LoftHelloPacketPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("arceon", "loft_hello"));

    public static final StreamCodec<ByteBuf, LoftHelloPacketPayload> CODEC =
            StreamCodec.of(LoftHelloPacketPayload::write, LoftHelloPacketPayload::read);

    private final int protocolVersion;
    private final String clientModVersion;

    public LoftHelloPacketPayload(int protocolVersion, String clientModVersion) {
        this.protocolVersion = protocolVersion;
        this.clientModVersion = clientModVersion;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public String clientModVersion() {
        return clientModVersion;
    }

    private static void write(ByteBuf buf, LoftHelloPacketPayload packet) {
        buf.writeInt(packet.protocolVersion);
        LoftPacketCodec.writeString(buf, packet.clientModVersion);
    }

    private static LoftHelloPacketPayload read(ByteBuf buf) {
        int protocolVersion = buf.readInt();
        String clientModVersion = LoftPacketCodec.readString(buf);
        return new LoftHelloPacketPayload(protocolVersion, clientModVersion);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
