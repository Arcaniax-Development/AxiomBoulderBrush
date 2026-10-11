package net.arcaniax.arceon.axiom.client.loft;

import net.arcaniax.arceon.axiom.ArceonClient;
import net.arcaniax.arceon.axiom.client.network.packet.LoftCancelPacketPayload;
import net.arcaniax.arceon.axiom.client.network.packet.LoftHelloPacketPayload;
import net.arcaniax.arceon.axiom.client.network.packet.LoftResultPacketPayload;
import net.arcaniax.arceon.axiom.client.network.packet.LoftSessionOpenPacketPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.List;

public final class LoftEditorClient {
    public static final int PROTOCOL_VERSION = 1;
    private static final boolean LOFT_DORMANT = false;
    private static LoftEditSession activeSession;
    private static Object activeLevel;
    private static RenderSnapshot activeRenderSnapshot = RenderSnapshot.EMPTY;
    private static boolean registered;
    private static int helloRetryTicksRemaining;
    private static int helloRetryCooldownTicks;

    private LoftEditorClient() {
    }

    public static void register() {
        if (LOFT_DORMANT) {
            registered = true;
            clearSession();
            debug("Loft editor is dormant in this build; client editor and preview renderer are disabled");
            return;
        }
        if (registered) {
            debug("register() ignored because Loft editor is already registered");
            return;
        }
        registered = true;

        ClientTickEvents.END_CLIENT_TICK.register(LoftEditorClient::tick);
        LoftWireframeRenderer.register();
        debug("Client editor registered; protocol={}", PROTOCOL_VERSION);
    }

    public static void sendHello() {
        if (LOFT_DORMANT) {
            debug("Skipping arceon:loft_hello because Loft is dormant in this build");
            return;
        }
        try {
            String version = FabricLoader.getInstance()
                    .getModContainer("arceon")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
            debug("Sending arceon:loft_hello protocol={} clientModVersion={}", PROTOCOL_VERSION, version);
            ClientPlayNetworking.send(new LoftHelloPacketPayload(PROTOCOL_VERSION, version));
            debug("Queued arceon:loft_hello successfully");
        } catch (Exception exception) {
            ArceonClient.LOGGER.error("[LOFT DEBUG] Unable to send arceon:loft_hello", exception);
        }
    }

    public static void queueHelloRetries() {
        if (LOFT_DORMANT) {
            helloRetryTicksRemaining = 0;
            helloRetryCooldownTicks = 0;
            debug("Ignoring Loft hello retry request because Loft is dormant in this build");
            return;
        }
        helloRetryTicksRemaining = 20 * 12;
        helloRetryCooldownTicks = 0;
        debug("Queued Loft hello retries for rejoin/session recovery");
    }

    public static void open(LoftSessionOpenPacketPayload packet) {
        if (LOFT_DORMANT) {
            clearSession();
            debug("Ignoring arceon:loft_session_open because Loft is dormant in this build");
            return;
        }
        List<LoftFrame> frames = packet.frames();
        int pointCount = countPoints(frames);
        boolean isUpdate = activeSession != null && activeSession.sessionId().equals(packet.sessionId());
        int selectedPointId = Integer.MIN_VALUE;
        if (isUpdate) {
            if (packet.revision() < activeSession.revision()) {
                ArceonClient.LOGGER.warn(
                        "[LOFT DEBUG] Ignoring stale live update for session {}; revision {} < {}",
                        packet.sessionId(), packet.revision(), activeSession.revision()
                );
                return;
            }
            LoftPoint selected = activeSession.selectedPoint();
            if (selected != null) {
                selectedPointId = selected.pointId();
            }
        }
        debug(
                "{} arceon:loft_session_open protocol={} sessionId={} revision={} world={} lowPoly={} frames={} points={}",
                isUpdate ? "Updating" : "Opening",
                packet.protocolVersion(), packet.sessionId(), packet.revision(), packet.world(), packet.lowPoly(),
                frames.size(), pointCount
        );
        if (packet.protocolVersion() != PROTOCOL_VERSION) {
            ArceonClient.LOGGER.warn(
                    "[LOFT DEBUG] Rejecting session {} because protocol {} != {}",
                    packet.sessionId(), packet.protocolVersion(), PROTOCOL_VERSION
            );
            showMessage("Unsupported Loft editor protocol: " + packet.protocolVersion());
            return;
        }

        if (pointCount == 0) {
            debug("Received empty Loft session snapshot {}; clearing client preview", packet.sessionId());
            clearSession();
            return;
        }

        Minecraft client = Minecraft.getInstance();
        activeSession = new LoftEditSession(packet.sessionId(), packet.revision(), packet.world(), packet.lowPoly(), frames);
        helloRetryTicksRemaining = 0;
        helloRetryCooldownTicks = 0;
        if (selectedPointId != Integer.MIN_VALUE) {
            activeSession.selectPointId(selectedPointId);
        }
        activeLevel = client.level;
        if (!rebuildRenderSnapshot()) {
            return;
        }
        debug(
                "Session {} {} and is active; preview rendering starts on the next client ticks",
                packet.sessionId(), isUpdate ? "live snapshot updated" : "opened"
        );
        if (!isUpdate) {
            showMessage("Loft editor opened: " + activeSession.viewFrames().size() + " frame(s)");
        }
    }

    public static void handleResult(LoftResultPacketPayload packet) {
        if (LOFT_DORMANT) {
            clearSession();
            debug("Ignoring arceon:loft_result because Loft is dormant in this build");
            return;
        }        debug(
                "Received arceon:loft_result protocol={} sessionId={} accepted={} code={} message={}",
                packet.protocolVersion(), packet.sessionId(), packet.accepted(), packet.code(), packet.message()
        );
        if (activeSession != null && !activeSession.sessionId().equals(packet.sessionId())) {
            ArceonClient.LOGGER.warn(
                    "[LOFT DEBUG] Ignoring result for session {}; active session is {}",
                    packet.sessionId(), activeSession.sessionId()
            );
            return;
        }

        String message = packet.message().isEmpty() ? packet.code() : packet.message();
        if (packet.accepted()) {
            clearSession();
            showMessage(message.isEmpty() ? "Loft accepted" : message);
        } else {
            showMessage(message.isEmpty() ? "Loft rejected: " + packet.code() : message);
        }
    }

    public static void clearSession() {
        if (activeSession != null) {
            debug("Clearing active session {}", activeSession.sessionId());
        } else {
            debug("clearSession() called with no active session");
        }
        activeSession = null;
        activeLevel = null;
        activeRenderSnapshot = RenderSnapshot.EMPTY;
    }

    public static void debug(String message, Object... arguments) {
        ArceonClient.LOGGER.info("[LOFT DEBUG] " + message, arguments);
    }

    private static void tick(Minecraft client) {
        tickHelloRetry();

        if (activeSession == null) {
            return;
        }

        if (client.player == null || client.level == null) {
            ArceonClient.LOGGER.warn("[LOFT DEBUG] Active session lost player or level; clearing session");
            clearSession();
            return;
        }

        if (activeLevel != null && activeLevel != client.level) {
            ArceonClient.LOGGER.warn(
                    "[LOFT DEBUG] Client level changed while session {} was active; sending cancel",
                    activeSession.sessionId()
            );
            sendCancel();
            clearSession();
            return;
        }

    }

    private static void tickHelloRetry() {
        if (helloRetryTicksRemaining <= 0 || activeSession != null) {
            return;
        }

        helloRetryTicksRemaining--;
        if (helloRetryCooldownTicks > 0) {
            helloRetryCooldownTicks--;
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        sendHello();
        helloRetryCooldownTicks = 40;
    }

    public static RenderSnapshot renderSnapshot() {
        return activeRenderSnapshot;
    }

    private static boolean rebuildRenderSnapshot() {
        if (activeSession == null) {
            activeRenderSnapshot = RenderSnapshot.EMPTY;
            return false;
        }

        try {
            List<LoftEditSession.Segment> segments = activeSession.buildPreviewSegments();
            List<LoftEditSession.SurfaceQuad> surfaceQuads = activeSession.buildPreviewSurfaceQuads();
            List<RenderPoint> points = new java.util.ArrayList<>();
            List<LoftFrame> frames = activeSession.viewFrames();
            for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
                List<LoftPoint> framePoints = frames.get(frameIndex).viewPoints();
                for (int pointIndex = 0; pointIndex < framePoints.size(); pointIndex++) {
                    points.add(new RenderPoint(
                            (frameIndex + 1) + "#" + (pointIndex + 1),
                            framePoints.get(pointIndex).asVec3(),
                            pointIndex == 0
                    ));
                }
            }
            activeRenderSnapshot = new RenderSnapshot(segments, surfaceQuads, points);
            debug(
                    "Built cached preview-only line snapshot session={} revision={} segments={} controlPoints={}",
                    activeSession.sessionId(), activeSession.revision(), segments.size(), points.size()
            );
            return true;
        } catch (RuntimeException exception) {
            ArceonClient.LOGGER.error(
                    "[LOFT DEBUG] Preview generation failed for session {}; clearing it to prevent a client crash",
                    activeSession.sessionId(), exception
            );
            showMessage("Loft preview failed; see client log");
            clearSession();
            return false;
        }
    }

    private static void sendCancel() {
        if (activeSession == null) {
            return;
        }
        try {
            debug("Sending arceon:loft_cancel sessionId={}", activeSession.sessionId());
            ClientPlayNetworking.send(new LoftCancelPacketPayload(PROTOCOL_VERSION, activeSession.sessionId()));
            debug("Queued arceon:loft_cancel successfully");
        } catch (Exception exception) {
            ArceonClient.LOGGER.error("[LOFT DEBUG] Unable to send arceon:loft_cancel", exception);
        }
    }

    private static int countPoints(List<LoftFrame> frames) {
        int pointCount = 0;
        for (LoftFrame frame : frames) {
            pointCount += frame.viewPoints().size();
        }
        return pointCount;
    }

    private static void showMessage(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        Component component = Component.literal(message);
        try {
            Method method = client.player.getClass().getMethod("sendOverlayMessage", Component.class);
            method.invoke(client.player, component);
        } catch (ReflectiveOperationException ignored) {
            try {
                Method method = client.player.getClass().getMethod("displayClientMessage", Component.class, boolean.class);
                method.invoke(client.player, component, true);
            } catch (ReflectiveOperationException ignoredAgain) {
            }
        }
    }

    public static final class RenderSnapshot {
        private static final RenderSnapshot EMPTY = new RenderSnapshot(List.of(), List.of(), List.of());

        private final List<LoftEditSession.Segment> segments;
        private final List<LoftEditSession.SurfaceQuad> surfaceQuads;
        private final List<RenderPoint> points;

        private RenderSnapshot(
                List<LoftEditSession.Segment> segments,
                List<LoftEditSession.SurfaceQuad> surfaceQuads,
                List<RenderPoint> points
        ) {
            this.segments = List.copyOf(segments);
            this.surfaceQuads = List.copyOf(surfaceQuads);
            this.points = List.copyOf(points);
        }

        public List<LoftEditSession.Segment> segments() {
            return segments;
        }

        public List<RenderPoint> points() {
            return points;
        }

        public List<LoftEditSession.SurfaceQuad> surfaceQuads() {
            return surfaceQuads;
        }

        public boolean isEmpty() {
            return segments.isEmpty() && surfaceQuads.isEmpty() && points.isEmpty();
        }
    }

    public static final class RenderPoint {
        private final String label;
        private final Vec3 position;
        private final boolean frameStart;

        private RenderPoint(String label, Vec3 position, boolean frameStart) {
            this.label = label;
            this.position = position;
            this.frameStart = frameStart;
        }

        public String label() {
            return label;
        }

        public Vec3 position() {
            return position;
        }

        public boolean frameStart() {
            return frameStart;
        }
    }
}
