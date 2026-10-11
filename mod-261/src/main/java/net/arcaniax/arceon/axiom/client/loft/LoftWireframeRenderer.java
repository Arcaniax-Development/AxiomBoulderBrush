package net.arcaniax.arceon.axiom.client.loft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;

public final class LoftWireframeRenderer {
    private static final int CURVE_RED = 45;
    private static final int CURVE_GREEN = 210;
    private static final int CURVE_BLUE = 255;
    private static final int FRAME_RED = 255;
    private static final int FRAME_GREEN = 120;
    private static final int FRAME_BLUE = 35;
    private static final int POINT_RED = 255;
    private static final int POINT_GREEN = 170;
    private static final int POINT_BLUE = 65;
    private static final int POINT_FILL_RED = 255;
    private static final int POINT_FILL_GREEN = 205;
    private static final int POINT_FILL_BLUE = 135;
    private static final int START_POINT_RED = 255;
    private static final int START_POINT_GREEN = 225;
    private static final int START_POINT_BLUE = 70;
    private static final int START_POINT_FILL_RED = 255;
    private static final int START_POINT_FILL_GREEN = 240;
    private static final int START_POINT_FILL_BLUE = 150;
    private static final double POINT_HALF_SIZE = 0.13D;
    private static final double POINT_HIGHLIGHT_HALF_SIZE = POINT_HALF_SIZE * 0.36D;
    private static final int POINT_FILL_ALPHA = 245;
    private static final int POINT_SHADOW_ALPHA = 125;
    private static final float POINT_OUTLINE_WIDTH = 4.5F;
    private static final int SURFACE_RED = 45;
    private static final int SURFACE_GREEN = 210;
    private static final int SURFACE_BLUE = 255;
    private static final int SURFACE_ALPHA = 55;
    private static final Vec3 SURFACE_LIGHT_DIRECTION = new Vec3(-0.35D, 0.85D, -0.4D).normalize();
    private static final float BASE_LABEL_SCALE = 0.025F;
    private static final double LABEL_SCALE_DISTANCE = 8.0D;
    private static final double MAX_LABEL_SCALE_FACTOR = 2.5D;
    private static boolean registered;

    private LoftWireframeRenderer() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        LevelRenderEvents.BEFORE_GIZMOS.register(LoftWireframeRenderer::render);
        LoftEditorClient.debug("Registered real line renderer using LevelRenderEvents.BEFORE_GIZMOS");
    }

    private static void render(LevelRenderContext context) {
        LoftEditorClient.RenderSnapshot snapshot = LoftEditorClient.renderSnapshot();
        if (snapshot.isEmpty()) {
            return;
        }

        Camera renderCamera = context.gameRenderer().getMainCamera();
        Vec3 camera = renderCamera.position();
        PoseStack.Pose pose = context.poseStack().last();
        VertexConsumer fills = context.bufferSource().getBuffer(RenderTypes.debugFilledBox());

        for (LoftEditSession.SurfaceQuad quad : snapshot.surfaceQuads()) {
            drawSurfaceQuad(fills, pose, camera, quad);
        }
        for (LoftEditorClient.RenderPoint point : snapshot.points()) {
            if (point.frameStart()) {
                drawPointFill(fills, pose, camera, point.position(),
                        START_POINT_FILL_RED, START_POINT_FILL_GREEN, START_POINT_FILL_BLUE);
            } else {
                drawPointFill(fills, pose, camera, point.position(),
                        POINT_FILL_RED, POINT_FILL_GREEN, POINT_FILL_BLUE);
            }
        }

        VertexConsumer lines = context.bufferSource().getBuffer(RenderTypes.linesTranslucent());

        for (LoftEditSession.Segment segment : snapshot.segments()) {
            if (segment.frame()) {
                drawLine(lines, pose, camera, segment.start(), segment.end(), FRAME_RED, FRAME_GREEN, FRAME_BLUE, 245, 6.0F);
            } else {
                drawLine(lines, pose, camera, segment.start(), segment.end(), CURVE_RED, CURVE_GREEN, CURVE_BLUE, 220, 3.5F);
            }
        }

        for (LoftEditorClient.RenderPoint point : snapshot.points()) {
            if (point.frameStart()) {
                drawPointOutline(lines, pose, camera, point.position(),
                        START_POINT_RED, START_POINT_GREEN, START_POINT_BLUE);
            } else {
                drawPointOutline(lines, pose, camera, point.position(),
                        POINT_RED, POINT_GREEN, POINT_BLUE);
            }
        }
        drawLabels(context, renderCamera, snapshot);
    }

    private static void drawLabels(
            LevelRenderContext context,
            Camera camera,
            LoftEditorClient.RenderSnapshot snapshot
    ) {
        Font font = Minecraft.getInstance().font;
        PoseStack poseStack = context.poseStack();
        Vec3 cameraPosition = camera.position();
        for (LoftEditorClient.RenderPoint point : snapshot.points()) {
            drawLabelVariant(context, camera, font, poseStack, point, cameraPosition, 0.74D, "", ChatFormatting.WHITE, true, 0xFF000000, 0xFFFFFFFF, Font.DisplayMode.SEE_THROUGH);
        }
    }

    private static void drawLabelVariant(
            LevelRenderContext context,
            Camera camera,
            Font font,
            PoseStack poseStack,
            LoftEditorClient.RenderPoint point,
            Vec3 cameraPosition,
            double verticalOffset,
            String prefix,
            ChatFormatting formatting,
            boolean shadow,
            int backgroundColor,
            int fallbackTextColor,
            Font.DisplayMode displayMode
    ) {
        Vec3 position = point.position();
        Vec3 labelPosition = new Vec3(position.x, position.y + verticalOffset, position.z);
        Vec3 cameraOffset = cameraPosition.subtract(labelPosition);
        if (cameraOffset.lengthSqr() > 1.0E-6D) {
            labelPosition = labelPosition.add(cameraOffset.normalize().scale(0.12D));
        }

        poseStack.pushPose();
        poseStack.translate(
                labelPosition.x - cameraPosition.x,
                labelPosition.y - cameraPosition.y,
                labelPosition.z - cameraPosition.z
        );
        poseStack.mulPose(camera.rotation());
        float labelScale = (float) (BASE_LABEL_SCALE * Math.clamp(
                position.distanceTo(cameraPosition) / LABEL_SCALE_DISTANCE,
                1.0D,
                MAX_LABEL_SCALE_FACTOR
        ));
        poseStack.scale(labelScale, -labelScale, labelScale);
        FormattedCharSequence label = Component.literal(prefix + point.label()).withStyle(formatting).getVisualOrderText();
        float textX = -font.width(label) / 2.0F;
        font.drawInBatch(
                label, textX, 0.0F, fallbackTextColor, shadow,
                poseStack.last().pose(), context.bufferSource(), displayMode,
                backgroundColor, 0x00F000F0
        );
        poseStack.popPose();
    }
    private static void drawPointFill(
            VertexConsumer fills,
            PoseStack.Pose pose,
            Vec3 camera,
            Vec3 center,
            int fillRed,
            int fillGreen,
            int fillBlue
    ) {
        Vec3 view = center.subtract(camera).normalize();
        Vec3 referenceUp = Math.abs(view.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = view.cross(referenceUp).normalize();
        Vec3 up = right.cross(view).normalize();

        Vec3 fillCenter = center.add(view.scale(0.004D));
        Vec3 shadowCenter = fillCenter.add(right.scale(POINT_HALF_SIZE * 0.16D)).subtract(up.scale(POINT_HALF_SIZE * 0.16D));
        drawSquare(fills, pose, camera, shadowCenter, right, up, POINT_HALF_SIZE * 1.08D,
                70, 42, 15, POINT_SHADOW_ALPHA);
        drawSquare(fills, pose, camera, fillCenter, right, up, POINT_HALF_SIZE,
                fillRed, fillGreen, fillBlue, POINT_FILL_ALPHA);
        drawSquare(fills, pose, camera,
                fillCenter.subtract(right.scale(POINT_HALF_SIZE * 0.34D)).add(up.scale(POINT_HALF_SIZE * 0.34D)),
                right, up, POINT_HIGHLIGHT_HALF_SIZE,
                brighten(fillRed, 28), brighten(fillGreen, 28), brighten(fillBlue, 28), 170);
    }

    private static void drawSquare(
            VertexConsumer fills,
            PoseStack.Pose pose,
            Vec3 camera,
            Vec3 center,
            Vec3 right,
            Vec3 up,
            double halfSize,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        Vec3 rightOffset = right.scale(halfSize);
        Vec3 upOffset = up.scale(halfSize);
        drawFillQuad(fills, pose, camera,
                center.add(rightOffset).add(upOffset),
                center.subtract(rightOffset).add(upOffset),
                center.subtract(rightOffset).subtract(upOffset),
                center.add(rightOffset).subtract(upOffset),
                red, green, blue, alpha);
    }

    private static int brighten(int color, int amount) {
        return Math.min(255, color + amount);
    }

    private static void drawPointOutline(
            VertexConsumer lines,
            PoseStack.Pose pose,
            Vec3 camera,
            Vec3 center,
            int red,
            int green,
            int blue
    ) {
        Vec3 view = center.subtract(camera).normalize();
        Vec3 referenceUp = Math.abs(view.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = view.cross(referenceUp).normalize();
        Vec3 up = right.cross(view).normalize();
        Vec3 rightOffset = right.scale(POINT_HALF_SIZE);
        Vec3 upOffset = up.scale(POINT_HALF_SIZE);
        Vec3 topRight = center.add(rightOffset).add(upOffset);
        Vec3 topLeft = center.subtract(rightOffset).add(upOffset);
        Vec3 bottomLeft = center.subtract(rightOffset).subtract(upOffset);
        Vec3 bottomRight = center.add(rightOffset).subtract(upOffset);

        drawLine(lines, pose, camera, topRight, topLeft, red, green, blue, 255, POINT_OUTLINE_WIDTH);
        drawLine(lines, pose, camera, topLeft, bottomLeft, red, green, blue, 255, POINT_OUTLINE_WIDTH);
        drawLine(lines, pose, camera, bottomLeft, bottomRight, red, green, blue, 255, POINT_OUTLINE_WIDTH);
        drawLine(lines, pose, camera, bottomRight, topRight, red, green, blue, 255, POINT_OUTLINE_WIDTH);
    }
    private static void drawFillQuad(
            VertexConsumer fills, PoseStack.Pose pose, Vec3 camera,
            Vec3 first, Vec3 second, Vec3 third, Vec3 fourth,
            int red, int green, int blue, int alpha
    ) {
        addFillVertex(fills, pose, camera, first, red, green, blue, alpha);
        addFillVertex(fills, pose, camera, second, red, green, blue, alpha);
        addFillVertex(fills, pose, camera, third, red, green, blue, alpha);
        addFillVertex(fills, pose, camera, fourth, red, green, blue, alpha);
    }

    private static void drawSurfaceQuad(
            VertexConsumer fills,
            PoseStack.Pose pose,
            Vec3 camera,
            LoftEditSession.SurfaceQuad quad
    ) {
        Vec3 normal = quad.second().subtract(quad.first())
                .cross(quad.fourth().subtract(quad.first()));
        if (normal.lengthSqr() <= 1.0E-10D) {
            return;
        }
        normal = normal.normalize();

        boolean frontFacing = normal.dot(camera.subtract(quad.first())) >= 0.0D;
        double brightness = 0.4D + 0.6D * Math.abs(normal.dot(SURFACE_LIGHT_DIRECTION));
        if (!frontFacing) {
            brightness *= 0.68D;
        }
        int red = (int) Math.round(SURFACE_RED * brightness);
        int green = (int) Math.round(SURFACE_GREEN * brightness);
        int blue = (int) Math.round(SURFACE_BLUE * brightness);

        if (frontFacing) {
            drawFillQuad(fills, pose, camera,
                    quad.first(), quad.second(), quad.third(), quad.fourth(),
                    red, green, blue, SURFACE_ALPHA);
        } else {
            drawFillQuad(fills, pose, camera,
                    quad.fourth(), quad.third(), quad.second(), quad.first(),
                    red, green, blue, SURFACE_ALPHA);
        }
    }

    private static void addFillVertex(
            VertexConsumer fills, PoseStack.Pose pose, Vec3 camera, Vec3 point,
            int red, int green, int blue, int alpha
    ) {
        fills.addVertex(pose.pose(),
                        (float) (point.x - camera.x),
                        (float) (point.y - camera.y),
                        (float) (point.z - camera.z))
                .setColor(red, green, blue, alpha);
    }

    private static void drawLine(
            VertexConsumer lines,
            PoseStack.Pose pose,
            Vec3 camera,
            Vec3 start,
            Vec3 end,
            int red,
            int green,
            int blue,
            int alpha,
            float width
    ) {
        float startX = (float) (start.x - camera.x);
        float startY = (float) (start.y - camera.y);
        float startZ = (float) (start.z - camera.z);
        float endX = (float) (end.x - camera.x);
        float endY = (float) (end.y - camera.y);
        float endZ = (float) (end.z - camera.z);

        float normalX = endX - startX;
        float normalY = endY - startY;
        float normalZ = endZ - startZ;
        float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (length <= 1.0E-5F) {
            return;
        }
        normalX /= length;
        normalY /= length;
        normalZ /= length;

        lines.addVertex(pose.pose(), startX, startY, startZ)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, normalX, normalY, normalZ)
                .setLineWidth(width);
        lines.addVertex(pose.pose(), endX, endY, endZ)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, normalX, normalY, normalZ)
                .setLineWidth(width);
    }
}

