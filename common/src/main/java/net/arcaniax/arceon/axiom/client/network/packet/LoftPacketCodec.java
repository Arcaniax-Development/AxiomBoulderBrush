package net.arcaniax.arceon.axiom.client.network.packet;

import io.netty.buffer.ByteBuf;
import net.arcaniax.arceon.axiom.client.loft.LoftFrame;
import net.arcaniax.arceon.axiom.client.loft.LoftPoint;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

final class LoftPacketCodec {
    private static final int MAX_STRING_BYTES = 32767;
    private static final int MAX_TOTAL_POINTS = 10000;

    private LoftPacketCodec() {
    }

    static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("String is too large for loft packet");
        }
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    static String readString(ByteBuf buf) {
        int length = buf.readInt();
        if (length < 0 || length > MAX_STRING_BYTES || length > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid string length in loft packet: " + length);
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    static void writeIndexedFrames(ByteBuf buf, List<LoftFrame> frames) {
        int pointCount = 0;
        for (LoftFrame frame : frames) {
            pointCount += frame.viewPoints().size();
        }
        if (pointCount > MAX_TOTAL_POINTS) {
            throw new IllegalArgumentException("Too many loft points: " + pointCount);
        }

        buf.writeInt(pointCount);
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            List<LoftPoint> points = frames.get(frameIndex).viewPoints();
            for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
                LoftPoint point = points.get(pointIndex);
                buf.writeInt(point.pointId());
                buf.writeInt(frameIndex);
                buf.writeInt(pointIndex);
                buf.writeDouble(point.x());
                buf.writeDouble(point.y());
                buf.writeDouble(point.z());
            }
        }
    }

    static List<LoftFrame> readIndexedFrames(ByteBuf buf) {
        int pointCount = buf.readInt();
        if (pointCount < 0 || pointCount > MAX_TOTAL_POINTS) {
            throw new IllegalArgumentException("Invalid loft point count: " + pointCount);
        }

        Map<Integer, List<IndexedPoint>> frames = new TreeMap<>();
        for (int i = 0; i < pointCount; i++) {
            int pointId = buf.readInt();
            int frameIndex = buf.readInt();
            int pointIndex = buf.readInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();

            if (frameIndex < 0 || pointIndex < 0) {
                throw new IllegalArgumentException("Negative loft frame or point index");
            }

            frames.computeIfAbsent(frameIndex, ignored -> new ArrayList<>())
                    .add(new IndexedPoint(pointIndex, new LoftPoint(pointId, x, y, z)));
        }

        List<LoftFrame> result = new ArrayList<>();
        for (List<IndexedPoint> indexedPoints : frames.values()) {
            indexedPoints.sort(Comparator.comparingInt(IndexedPoint::pointIndex));
            List<LoftPoint> points = new ArrayList<>();
            for (IndexedPoint indexedPoint : indexedPoints) {
                points.add(indexedPoint.point().copy());
            }
            result.add(new LoftFrame(points));
        }
        return result;
    }

    private record IndexedPoint(int pointIndex, LoftPoint point) {
    }
}
