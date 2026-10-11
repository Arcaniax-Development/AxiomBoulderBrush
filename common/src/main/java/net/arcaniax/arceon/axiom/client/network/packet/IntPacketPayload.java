package net.arcaniax.arceon.axiom.client.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class IntPacketPayload implements CustomPacketPayload {

    // Packet ID
    public static final Type<IntPacketPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("arceon", "version"));

    // Codec for encoding/decoding
    public static final StreamCodec<ByteBuf, IntPacketPayload> CODEC =
            StreamCodec.of(IntPacketPayload::write, IntPacketPayload::read);
    private final int anInt;

    public IntPacketPayload(int anInt) {
        this.anInt = anInt;
    }

    public int getInt(){
        return anInt;
    }

    // Encode to ByteBuf
    private static void write(ByteBuf buf, IntPacketPayload packet) {
        buf.writeInt(packet.anInt);
    }

    // Decode from ByteBuf
    private static IntPacketPayload read(ByteBuf buf) {
        int anInt = buf.readInt();
        return new IntPacketPayload(anInt);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
