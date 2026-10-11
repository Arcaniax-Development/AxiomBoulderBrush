package net.arcaniax.arceon.axiom.client.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

public class StringPacketPayload implements CustomPacketPayload {

    // Packet ID
    public static final Type<StringPacketPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("arceon", "perms"));

    // Codec for encoding/decoding
    public static final StreamCodec<ByteBuf, StringPacketPayload> CODEC =
            StreamCodec.of(StringPacketPayload::write, StringPacketPayload::read);
    private final String message;

    public StringPacketPayload(String message) {
        this.message = message;
    }

    public String getMessage(){
        return message;
    }

    // Encode to ByteBuf
    private static void write(ByteBuf buf, StringPacketPayload packet) {
        byte[] bytes = packet.message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        //buf.writeInt(bytes.length); // prefix length
        buf.writeBytes(bytes);
    }

    // Decode from ByteBuf
    private static StringPacketPayload read(ByteBuf buf) {
        buf.readByte();
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        String msg = new String(bytes, StandardCharsets.UTF_8);
        return new StringPacketPayload(msg);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
