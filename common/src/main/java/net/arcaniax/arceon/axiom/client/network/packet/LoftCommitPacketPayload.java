package net.arcaniax.arceon.axiom.client.network.packet;

import io.netty.buffer.ByteBuf;
import net.arcaniax.arceon.axiom.client.loft.LoftFrame;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LoftCommitPacketPayload implements CustomPacketPayload {
    public static final Type<LoftCommitPacketPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("arceon", "loft_commit"));

    public static final StreamCodec<ByteBuf, LoftCommitPacketPayload> CODEC =
            StreamCodec.of(LoftCommitPacketPayload::write, LoftCommitPacketPayload::read);

    private final int protocolVersion;
    private final UUID sessionId;
    private final int revision;
    private final List<LoftFrame> frames;

    public LoftCommitPacketPayload(int protocolVersion, UUID sessionId, int revision, List<LoftFrame> frames) {
        this.protocolVersion = protocolVersion;
        this.sessionId = sessionId;
        this.revision = revision;
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

    public List<LoftFrame> frames() {
        return copyFrames(frames);
    }

    private static void write(ByteBuf buf, LoftCommitPacketPayload packet) {
        buf.writeInt(packet.protocolVersion);
        LoftPacketCodec.writeUuid(buf, packet.sessionId);
        buf.writeInt(packet.revision);
        LoftPacketCodec.writeIndexedFrames(buf, packet.frames);
    }

    private static LoftCommitPacketPayload read(ByteBuf buf) {
        int protocolVersion = buf.readInt();
        UUID sessionId = LoftPacketCodec.readUuid(buf);
        int revision = buf.readInt();
        List<LoftFrame> frames = LoftPacketCodec.readIndexedFrames(buf);
        return new LoftCommitPacketPayload(protocolVersion, sessionId, revision, frames);
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
