package net.arcaniax.arceon.axiom.client.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

public class PermsRequestPacketPayload implements CustomPacketPayload {

    // Packet ID
    public static final CustomPacketPayload.Type<PermsRequestPacketPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("arceon", "perms_request"));

    // Codec for encoding/decoding
    public static final StreamCodec<ByteBuf, PermsRequestPacketPayload> CODEC =
            StreamCodec.of(PermsRequestPacketPayload::write, PermsRequestPacketPayload::read);
    private final String message;

    public PermsRequestPacketPayload(String message) {
        this.message = message;
    }

    public String getMessage(){
        return message;
    }

    // Encode to ByteBuf
    private static void write(ByteBuf buf, PermsRequestPacketPayload packet) {
        byte[] bytes = packet.message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length); // prefix length
        buf.writeBytes(bytes);
    }

    // Decode from ByteBuf
    private static PermsRequestPacketPayload read(ByteBuf buf) {
        int length = buf.readInt();
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        String msg = new String(bytes, StandardCharsets.UTF_8);
        return new PermsRequestPacketPayload(msg);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
