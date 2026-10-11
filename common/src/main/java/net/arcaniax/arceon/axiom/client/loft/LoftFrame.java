package net.arcaniax.arceon.axiom.client.loft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LoftFrame {
    private final List<LoftPoint> points = new ArrayList<>();

    public LoftFrame(List<LoftPoint> points) {
        for (LoftPoint point : points) {
            this.points.add(point.copy());
        }
    }

    public List<LoftPoint> points() {
        return points;
    }

    public List<LoftPoint> viewPoints() {
        return Collections.unmodifiableList(points);
    }

    public LoftFrame copy() {
        return new LoftFrame(points);
    }
}
