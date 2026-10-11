package net.arcaniax.arceon.axiom.client.network.packet;

import io.netty.buffer.ByteBuf;
import net.arcaniax.arceon.axiom.client.loft.LoftFrame;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LoftSessionOpenPacketPayload implements CustomPacketPayload {
    public static final Type<LoftSessionOpenPacketPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("arceon", "loft_session_open"));

    public static final StreamCodec<ByteBuf, LoftSessionOpenPacketPayload> CODEC =
            StreamCodec.of(LoftSessionOpenPacketPayload::write, LoftSessionOpenPacketPayload::read);

    private final int protocolVersion;
    private final UUID sessionId;
    private final int revision;
    private final String world;
    private final boolean lowPoly;
    private final List<LoftFrame> frames;

    public LoftSessionOpenPacketPayload(
            int protocolVersion,
            UUID sessionId,
            int revision,
            String world,
            boolean lowPoly,
            List<LoftFrame> frames
    ) {
        this.protocolVersion = protocolVersion;
        this.sessionId = sessionId;
        this.revision = revision;
        this.world = world;
        this.lowPoly = lowPoly;
        this.frames = copyFrames(frames);
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public int revision() {
        return revision;
    }

    public String world() {
        return world;
    }

    public boolean lowPoly() {
        return lowPoly;
    }

    public List<LoftFrame> frames() {
        return copyFrames(frames);
    }

    private static void write(ByteBuf buf, LoftSessionOpenPacketPayload packet) {
        buf.writeInt(packet.protocolVersion);
        LoftPacketCodec.writeUuid(buf, packet.sessionId);
        buf.writeInt(packet.revision);
        LoftPacketCodec.writeString(buf, packet.world);
        buf.writeBoolean(packet.lowPoly);
        LoftPacketCodec.writeIndexedFrames(buf, packet.frames);
    }

    private static LoftSessionOpenPacketPayload read(ByteBuf buf) {
        int protocolVersion = buf.readInt();
        UUID sessionId = LoftPacketCodec.readUuid(buf);
        int revision = buf.readInt();
        String world = LoftPacketCodec.readString(buf);
        boolean lowPoly = buf.readBoolean();
        List<LoftFrame> frames = LoftPacketCodec.readIndexedFrames(buf);
        return new LoftSessionOpenPacketPayload(protocolVersion, sessionId, revision, world, lowPoly, frames);
    }

    private static List<LoftFrame> copyFrames(List<LoftFrame> frames) {
        List<LoftFrame> copy = new ArrayList<>();
        for (LoftFrame frame : frames) {
            copy.add(frame.copy());
        }
        return copy;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
