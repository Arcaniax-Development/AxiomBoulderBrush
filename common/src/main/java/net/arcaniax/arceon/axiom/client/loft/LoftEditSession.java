package net.arcaniax.arceon.axiom.client.loft;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class LoftEditSession {
    private static final double PREVIEW_SAMPLES_PER_BLOCK = 2.0D;
    private static final int RAIL_SAMPLES_PER_FRAME_INTERVAL = 8;
    private static final int MAX_RAIL_SAMPLES = 160;
    private static final int MAX_SECTION_SAMPLES = 128;

    private final UUID sessionId;
    private final int revision;
    private final String world;
    private final boolean lowPoly;
    private final List<LoftFrame> frames = new ArrayList<>();
    private int selectedFrameIndex;
    private int selectedPointIndex;
    private int nextGeneratedPointId = -1;

    public LoftEditSession(UUID sessionId, int revision, String world, boolean lowPoly, List<LoftFrame> frames) {
        this.sessionId = sessionId;
        this.revision = revision;
        this.world = world;
        this.lowPoly = lowPoly;

        int lowestPointId = 0;
        for (LoftFrame frame : frames) {
            LoftFrame copy = frame.copy();
            if (!copy.viewPoints().isEmpty()) {
                this.frames.add(copy);
                for (LoftPoint point : copy.viewPoints()) {
                    lowestPointId = Math.min(lowestPointId, point.pointId());
                }
            }
        }
        this.nextGeneratedPointId = Math.min(-1, lowestPointId - 1);
        normalizeSelection();
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

    public List<LoftFrame> copyFrames() {
        List<LoftFrame> copy = new ArrayList<>();
        for (LoftFrame frame : frames) {
            copy.add(frame.copy());
        }
        return copy;
    }

    public List<LoftFrame> viewFrames() {
        return Collections.unmodifiableList(frames);
    }

    public LoftPoint selectedPoint() {
        normalizeSelection();
        if (frames.isEmpty()) {
            return null;
        }
        List<LoftPoint> points = frames.get(selectedFrameIndex).points();
        if (points.isEmpty()) {
            return null;
        }
        return points.get(selectedPointIndex);
    }

    public boolean canCommit() {
        if (frames.size() < 2) {
            return false;
        }
        for (LoftFrame frame : frames) {
            if (frame.viewPoints().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void selectNextPoint() {
        moveSelection(1);
    }

    public void selectPreviousPoint() {
        moveSelection(-1);
    }

    public boolean selectPointId(int pointId) {
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            List<LoftPoint> points = frames.get(frameIndex).viewPoints();
            for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
                if (points.get(pointIndex).pointId() == pointId) {
                    selectedFrameIndex = frameIndex;
                    selectedPointIndex = pointIndex;
                    return true;
                }
            }
        }
        return false;
    }

    public void moveSelectedTo(Vec3 position) {
        LoftPoint selected = selectedPoint();
        if (selected != null) {
            selected.moveTo(position);
        }
    }

    public void insertPointAfterSelected(Vec3 position) {
        if (frames.isEmpty()) {
            frames.add(new LoftFrame(List.of(new LoftPoint(allocatePointId(), position.x, position.y, position.z))));
            selectedFrameIndex = 0;
            selectedPointIndex = 0;
            return;
        }

        normalizeSelection();
        List<LoftPoint> points = frames.get(selectedFrameIndex).points();
        int insertIndex = points.isEmpty() ? 0 : selectedPointIndex + 1;
        points.add(insertIndex, new LoftPoint(allocatePointId(), position.x, position.y, position.z));
        selectedPointIndex = insertIndex;
    }

    public void removeSelectedPoint() {
        normalizeSelection();
        if (frames.isEmpty()) {
            return;
        }

        List<LoftPoint> points = frames.get(selectedFrameIndex).points();
        if (points.isEmpty()) {
            frames.remove(selectedFrameIndex);
            normalizeSelection();
            return;
        }

        points.remove(selectedPointIndex);
        if (points.isEmpty()) {
            frames.remove(selectedFrameIndex);
        }
        normalizeSelection();
    }

    public void addFrameAfterSelected(Vec3 position) {
        int insertIndex = frames.isEmpty() ? 0 : selectedFrameIndex + 1;
        frames.add(insertIndex, new LoftFrame(List.of(new LoftPoint(allocatePointId(), position.x, position.y, position.z))));
        selectedFrameIndex = insertIndex;
        selectedPointIndex = 0;
    }

    public void removeSelectedFrame() {
        normalizeSelection();
        if (frames.isEmpty()) {
            return;
        }
        frames.remove(selectedFrameIndex);
        normalizeSelection();
    }

    public String statusText() {
        normalizeSelection();
        if (frames.isEmpty()) {
            return "Loft editor: no points | N add frame | Backspace cancel";
        }

        int pointCount = frames.get(selectedFrameIndex).viewPoints().size();
        String commitState = canCommit() ? "Enter commit" : "needs 2 frames";
        return "Loft frame " + (selectedFrameIndex + 1) + "/" + frames.size()
                + " point " + (selectedPointIndex + 1) + "/" + pointCount
                + " | G move | I insert | Del remove | N frame | M remove frame | " + commitState;
    }

    public List<Segment> buildPreviewSegments() {
        List<Segment> segments = new ArrayList<>();
        if (frames.isEmpty()) {
            return segments;
        }

        for (LoftFrame frame : frames) {
            List<Vec3> frameControls = new ArrayList<>();
            for (LoftPoint point : frame.viewPoints()) {
                frameControls.add(point.asVec3());
            }

            if (!lowPoly && frameControls.size() >= 3) {
                int frameSamples = adaptiveSampleCount(
                        frameControls,
                        clamp(frameControls.size() * 6, 8, 24),
                        MAX_SECTION_SAMPLES
                );
                addSegments(segments, sampleCurve(frameControls, frameSamples, false), true);
            } else {
                addSegments(segments, frameControls, true);
            }
        }

        int maxPointCount = maxPointCount();
        if (frames.size() < 2 || maxPointCount <= 0) {
            return segments;
        }

        List<List<Vec3>> resampledFrames = new ArrayList<>();
        for (LoftFrame frame : frames) {
            resampledFrames.add(resampleFrame(frame, maxPointCount));
        }

        int minimumRailSamples = railSampleCountForFrames();
        int minimumSectionSamples = clamp(maxPointCount * 4, 4, 24);
        int lengthSamples = minimumRailSamples;
        double maximumRailDistance = 0.0D;
        List<List<Vec3>> railControlsByIndex = new ArrayList<>();

        for (int pointIndex = 0; pointIndex < maxPointCount; pointIndex++) {
            List<Vec3> controls = new ArrayList<>();
            for (List<Vec3> framePoints : resampledFrames) {
                if (!framePoints.isEmpty()) {
                    controls.add(framePoints.get(Math.min(pointIndex, framePoints.size() - 1)));
                }
            }
            railControlsByIndex.add(controls);
            maximumRailDistance = Math.max(maximumRailDistance, controlPolygonLength(controls));
            lengthSamples = Math.max(
                    lengthSamples,
                    adaptiveSampleCount(controls, minimumRailSamples, MAX_RAIL_SAMPLES)
            );
        }

        List<List<Vec3>> lengthwiseSamplesByIndex = new ArrayList<>();

        for (List<Vec3> controls : railControlsByIndex) {
            List<Vec3> samples = sampleCurve(controls, lengthSamples, lowPoly);
            lengthwiseSamplesByIndex.add(samples);
            addSegments(segments, samples);
        }

        int intermediateSectionCount = clamp(
                (int) Math.ceil(maximumRailDistance / 4.0D) - 1,
                0,
                16
        );
        if (maxPointCount > 1 && intermediateSectionCount > 0) {
            int previousSampleIndex = -1;
            for (int sectionIndex = 1; sectionIndex <= intermediateSectionCount; sectionIndex++) {
                int sampleIndex = (int) Math.round(
                        (double) sectionIndex * (lengthSamples - 1) / (double) (intermediateSectionCount + 1)
                );
                if (sampleIndex == previousSampleIndex) {
                    continue;
                }
                previousSampleIndex = sampleIndex;
                List<Vec3> controls = new ArrayList<>();
                for (List<Vec3> samples : lengthwiseSamplesByIndex) {
                    if (!samples.isEmpty()) {
                        controls.add(samples.get(Math.min(sampleIndex, samples.size() - 1)));
                    }
                }
                int sectionSamples = adaptiveSampleCount(
                        controls,
                        minimumSectionSamples,
                        MAX_SECTION_SAMPLES
                );
                addSegments(segments, sampleCurve(controls, sectionSamples, lowPoly));
            }
        }

        return segments;
    }

    public List<SurfaceQuad> buildPreviewSurfaceQuads() {
        List<SurfaceQuad> quads = new ArrayList<>();
        int maxPointCount = maxPointCount();
        if (frames.size() < 2 || maxPointCount < 2) {
            return quads;
        }

        List<List<Vec3>> resampledFrames = new ArrayList<>();
        int sectionSamples = clamp(maxPointCount * 4, 4, MAX_SECTION_SAMPLES);
        for (LoftFrame frame : frames) {
            List<Vec3> resampled = resampleFrame(frame, maxPointCount);
            resampledFrames.add(resampled);
            sectionSamples = Math.max(
                    sectionSamples,
                    adaptiveSampleCount(resampled, sectionSamples, MAX_SECTION_SAMPLES)
            );
        }

        int lengthSamples = railSampleCountForFrames();
        List<List<Vec3>> railControls = new ArrayList<>();
        for (int pointIndex = 0; pointIndex < maxPointCount; pointIndex++) {
            List<Vec3> controls = new ArrayList<>();
            for (List<Vec3> framePoints : resampledFrames) {
                controls.add(framePoints.get(pointIndex));
            }
            railControls.add(controls);
            lengthSamples = Math.max(
                    lengthSamples,
                    adaptiveSampleCount(controls, lengthSamples, MAX_RAIL_SAMPLES)
            );
        }

        List<List<Vec3>> rails = new ArrayList<>();
        for (List<Vec3> controls : railControls) {
            rails.add(sampleCurve(controls, lengthSamples, lowPoly));
        }

        List<Vec3> previousSection = null;
        for (int lengthIndex = 0; lengthIndex < lengthSamples; lengthIndex++) {
            List<Vec3> controls = new ArrayList<>();
            for (List<Vec3> rail : rails) {
                controls.add(rail.get(lengthIndex));
            }
            List<Vec3> section = sampleCurve(controls, sectionSamples, lowPoly);
            if (previousSection != null) {
                for (int sectionIndex = 1; sectionIndex < section.size(); sectionIndex++) {
                    quads.add(new SurfaceQuad(
                            previousSection.get(sectionIndex - 1),
                            previousSection.get(sectionIndex),
                            section.get(sectionIndex),
                            section.get(sectionIndex - 1)
                    ));
                }
            }
            previousSection = section;
        }
        return quads;
    }

    public List<LoftPoint> allPoints() {
        List<LoftPoint> points = new ArrayList<>();
        for (LoftFrame frame : frames) {
            for (LoftPoint point : frame.viewPoints()) {
                points.add(point.copy());
            }
        }
        return points;
    }

    private void moveSelection(int direction) {
        normalizeSelection();
        int total = 0;
        int current = -1;
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            int pointCount = frames.get(frameIndex).viewPoints().size();
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                if (frameIndex == selectedFrameIndex && pointIndex == selectedPointIndex) {
                    current = total;
                }
                total++;
            }
        }

        if (total == 0) {
            return;
        }

        int next = (current + direction) % total;
        if (next < 0) {
            next += total;
        }

        int index = 0;
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            int pointCount = frames.get(frameIndex).viewPoints().size();
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                if (index == next) {
                    selectedFrameIndex = frameIndex;
                    selectedPointIndex = pointIndex;
                    return;
                }
                index++;
            }
        }
    }

    private void normalizeSelection() {
        frames.removeIf(frame -> frame.viewPoints().isEmpty());
        if (frames.isEmpty()) {
            selectedFrameIndex = 0;
            selectedPointIndex = 0;
            return;
        }

        selectedFrameIndex = clamp(selectedFrameIndex, 0, frames.size() - 1);
        int pointCount = frames.get(selectedFrameIndex).viewPoints().size();
        selectedPointIndex = clamp(selectedPointIndex, 0, Math.max(0, pointCount - 1));
    }

    private int allocatePointId() {
        return nextGeneratedPointId--;
    }

    private int maxPointCount() {
        int maxPointCount = 0;
        for (LoftFrame frame : frames) {
            maxPointCount = Math.max(maxPointCount, frame.viewPoints().size());
        }
        return maxPointCount;
    }

    private int railSampleCountForFrames() {
        int frameIntervals = Math.max(1, frames.size() - 1);
        return clamp(
                frameIntervals * RAIL_SAMPLES_PER_FRAME_INTERVAL + 1,
                RAIL_SAMPLES_PER_FRAME_INTERVAL + 1,
                MAX_RAIL_SAMPLES
        );
    }

    private List<Vec3> resampleFrame(LoftFrame frame, int targetCount) {
        List<Vec3> controls = new ArrayList<>();
        for (LoftPoint point : frame.viewPoints()) {
            controls.add(point.asVec3());
        }
        if (controls.isEmpty()) {
            return Collections.emptyList();
        }
        return sampleCurve(controls, targetCount, lowPoly);
    }

    private static List<Vec3> sampleCurve(List<Vec3> controls, int sampleCount, boolean lowPoly) {
        List<Vec3> samples = new ArrayList<>();
        if (controls.isEmpty()) {
            return samples;
        }
        if (sampleCount <= 1) {
            samples.add(controls.get(0));
            return samples;
        }
        if (controls.size() == 1) {
            for (int i = 0; i < sampleCount; i++) {
                samples.add(controls.get(0));
            }
            return samples;
        }

        BezierSpline spline = lowPoly ? null : new BezierSpline(controls);
        double parameterMax = controls.size() - 1.0D;
        for (int i = 0; i < sampleCount; i++) {
            double parameter = parameterMax * (double) i / (double) (sampleCount - 1);
            samples.add(lowPoly ? evaluateLinear(controls, parameter) : spline.evaluate(parameter));
        }
        return samples;
    }

    private static Vec3 evaluateLinear(List<Vec3> knots, double parameter) {
        if (parameter < 0.0D) {
            return knots.get(0);
        }
        if (parameter >= knots.size() - 1.0D) {
            return knots.get(knots.size() - 1);
        }
        int segment = (int) Math.floor(parameter);
        double localParameter = parameter % 1.0D;
        return lerp(knots.get(segment), knots.get(segment + 1), localParameter);
    }

    private static Vec3 lerp(Vec3 start, Vec3 end, double t) {
        return new Vec3(
                start.x + (end.x - start.x) * t,
                start.y + (end.y - start.y) * t,
                start.z + (end.z - start.z) * t
        );
    }

    private static void addSegments(List<Segment> segments, List<Vec3> points) {
        addSegments(segments, points, false);
    }

    private static void addSegments(List<Segment> segments, List<Vec3> points, boolean frame) {
        for (int i = 1; i < points.size(); i++) {
            segments.add(new Segment(points.get(i - 1), points.get(i), frame));
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int adaptiveSampleCount(List<Vec3> controls, int minimum, int maximum) {
        if (controls.size() < 2) {
            return 1;
        }

        double controlPolygonLength = controlPolygonLength(controls);
        int distanceSamples = (int) Math.ceil(controlPolygonLength * PREVIEW_SAMPLES_PER_BLOCK) + 1;
        return clamp(Math.max(minimum, distanceSamples), 2, maximum);
    }

    private static double controlPolygonLength(List<Vec3> controls) {
        double length = 0.0D;
        for (int i = 1; i < controls.size(); i++) {
            length += controls.get(i - 1).distanceTo(controls.get(i));
        }
        return length;
    }

    private static final class BezierSpline {
        private final List<BezierSegment> segments;

        private BezierSpline(List<Vec3> knots) {
            int segmentCount = knots.size() - 1;
            double[] knotX = new double[knots.size()];
            double[] knotY = new double[knots.size()];
            double[] knotZ = new double[knots.size()];
            for (int i = 0; i < knots.size(); i++) {
                Vec3 knot = knots.get(i);
                knotX[i] = knot.x;
                knotY[i] = knot.y;
                knotZ[i] = knot.z;
            }

            AxisControls x = solveAxis(knotX);
            AxisControls y = solveAxis(knotY);
            AxisControls z = solveAxis(knotZ);
            this.segments = new ArrayList<>(segmentCount);
            for (int i = 0; i < segmentCount; i++) {
                this.segments.add(new BezierSegment(
                        knots.get(i),
                        new Vec3(x.first[i], y.first[i], z.first[i]),
                        new Vec3(x.second[i], y.second[i], z.second[i]),
                        knots.get(i + 1)
                ));
            }
        }

        private Vec3 evaluate(double parameter) {
            int segmentCount = segments.size();
            if (parameter <= 0.0D) {
                return segments.get(0).evaluate(0.0D);
            }

            int segment;
            double localParameter;
            if (parameter >= segmentCount) {
                segment = segmentCount - 1;
                localParameter = 1.0D;
            } else {
                segment = (int) Math.floor(parameter);
                localParameter = parameter - Math.floor(parameter);
            }
            return segments.get(segment).evaluate(localParameter);
        }

        private static AxisControls solveAxis(double[] knots) {
            int segmentCount = knots.length - 1;
            double[] first = new double[segmentCount];
            double[] second = new double[segmentCount];

            boolean flat = true;
            for (int i = 1; i < knots.length; i++) {
                if (knots[i] != knots[0]) {
                    flat = false;
                    break;
                }
            }
            if (flat) {
                for (int i = 0; i < segmentCount; i++) {
                    first[i] = knots[0];
                    second[i] = knots[0];
                }
                return new AxisControls(first, second);
            }

            if (segmentCount == 1) {
                double midpoint = (knots[0] + knots[1]) / 2.0D;
                first[0] = midpoint;
                second[0] = midpoint;
                return new AxisControls(first, second);
            }

            double[] a = new double[segmentCount];
            double[] b = new double[segmentCount];
            double[] c = new double[segmentCount];
            double[] r = new double[segmentCount];

            a[0] = 0.0D;
            b[0] = 2.0D;
            c[0] = 1.0D;
            r[0] = knots[0] + 2.0D * knots[1];

            for (int i = 1; i <= segmentCount - 2; i++) {
                a[i] = 1.0D;
                b[i] = 4.0D;
                c[i] = 1.0D;
                r[i] = 4.0D * knots[i] + 2.0D * knots[i + 1];
            }

            int last = segmentCount - 1;
            a[last] = 2.0D;
            b[last] = 7.0D;
            c[last] = 0.0D;
            r[last] = 8.0D * knots[last] + knots[segmentCount];

            for (int i = 1; i < segmentCount; i++) {
                float multiplier = (float) (a[i] / b[i - 1]);
                b[i] = b[i] - multiplier * c[i - 1];
                r[i] = r[i] - multiplier * r[i - 1];
            }

            first[last] = r[last] / b[last];
            for (int i = segmentCount - 2; i >= 0; i--) {
                first[i] = (r[i] - c[i] * first[i + 1]) / b[i];
            }

            for (int i = 0; i <= segmentCount - 2; i++) {
                second[i] = 2.0D * knots[i + 1] - first[i + 1];
            }
            second[last] = 0.5D * (knots[segmentCount] + first[last]);
            return new AxisControls(first, second);
        }
    }

    private static final class AxisControls {
        private final double[] first;
        private final double[] second;

        private AxisControls(double[] first, double[] second) {
            this.first = first;
            this.second = second;
        }
    }

    private static final class BezierSegment {
        private final Vec3 p0;
        private final Vec3 p1;
        private final Vec3 p2;
        private final Vec3 p3;

        private BezierSegment(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
            this.p0 = p0;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
        }

        private Vec3 evaluate(double parameter) {
            double inverse = 1.0D - parameter;
            double p0Weight = inverse * inverse * inverse;
            double p1Weight = 3.0D * inverse * inverse * parameter;
            double p2Weight = 3.0D * inverse * parameter * parameter;
            double p3Weight = parameter * parameter * parameter;
            return new Vec3(
                    p0Weight * p0.x + p1Weight * p1.x + p2Weight * p2.x + p3Weight * p3.x,
                    p0Weight * p0.y + p1Weight * p1.y + p2Weight * p2.y + p3Weight * p3.y,
                    p0Weight * p0.z + p1Weight * p1.z + p2Weight * p2.z + p3Weight * p3.z
            );
        }
    }

    public static class Segment {
        private final Vec3 start;
        private final Vec3 end;
        private final boolean frame;

        public Segment(Vec3 start, Vec3 end) {
            this(start, end, false);
        }

        public Segment(Vec3 start, Vec3 end, boolean frame) {
            this.start = start;
            this.end = end;
            this.frame = frame;
        }

        public Vec3 start() {
            return start;
        }

        public Vec3 end() {
            return end;
        }

        public boolean frame() {
            return frame;
        }
    }

    public record SurfaceQuad(Vec3 first, Vec3 second, Vec3 third, Vec3 fourth) {
    }
}
