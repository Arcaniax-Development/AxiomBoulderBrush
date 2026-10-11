package net.arcaniax.arceon.axiom.client.tools;

import com.moulberry.axiom.RayCaster;
import com.moulberry.axiom.UserAction;
import com.moulberry.axiom.VersionUtilsNbt;
import com.moulberry.axiom.clipboard.Selection;
import com.moulberry.axiom.editor.EditorUI;
import com.moulberry.axiom.editor.GenericRadiusAdjustment;
import com.moulberry.axiom.editor.ImGuiHelper;
import com.moulberry.axiom.editor.widgets.PresetWidget;
import com.moulberry.axiom.gizmo.Gizmo;
import com.moulberry.axiom.i18n.AxiomI18n;
import com.moulberry.axiom.render.AxiomWorldRenderContext;
import com.moulberry.axiom.render.regions.ChunkedBooleanRegion;
import com.moulberry.axiom.restrictions.AxiomPermission;
import com.moulberry.axiom.tools.Tool;
import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.regions.BlockRegion;
import com.moulberry.axiomclientapi.service.ToolService;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.type.ImBoolean;
import net.arcaniax.arceon.axiom.*;
import net.arcaniax.arceon.axiom.client.network.Networking;
import net.arcaniax.arceon.axiom.client.util.ServiceHelper;
import net.arcaniax.arceon.axiom.client.gui.DefaultsGUI;
import net.arcaniax.arceon.axiom.client.util3D.IcoSphere;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.lang.reflect.Method;

public class BoulderBrush implements Tool {

    private BlockRegion blockRegion = ServiceHelper.createBlockRegion();
    private final ChunkedBooleanRegion previewRegion = new ChunkedBooleanRegion();
    private Object chunkedBlockRegion = newChunkedBlockRegion();

    private static String VERSION = "v1.2.0";

    private static Random randomGen = new Random();
    private Gizmo gizmo = null;
    private boolean recalculate = false;
    private boolean recalculateVariation = true;
    private boolean reorientPoint = false;
    private boolean usingRealCoordinates = false;
    private final float[] rotationX = new float[]{0.0F};
    private final float[] rotationY = new float[]{0.0F};
    private final float[] rotationZ = new float[]{0.0F};
    private final Quaternionf rotationQuaternion = new Quaternionf();
    private final int[] placeRotation = new int[]{0};


    private final int[] randomSize = new int[3];
    private final int[] randomSizeSingle = new int[1];

    private final GenericRadiusAdjustment radiusAdjustment = new GenericRadiusAdjustment(64);

    private final int[] radius = new int[]{10};
    private final int[] height = new int[]{10};
    private final int[] depth = new int[]{10};
    private final int[] subs = new int[]{2};
    private final ImBoolean randomSizeEnabled = new ImBoolean(false);
    private final ImBoolean keepProportions = new ImBoolean(false);
    private final ImBoolean heightEnabled = new ImBoolean(false);
    private final ImBoolean depthEnabled = new ImBoolean(false);
    private final ImBoolean angled = new ImBoolean(false);
    private final ImBoolean angledView = new ImBoolean(false);
    private final ImBoolean hollow = new ImBoolean(false);
    private final ImBoolean keepExisting = new ImBoolean(false);
    private final ImBoolean outline = new ImBoolean(false);
    private final ImBoolean extendToGround = new ImBoolean(false);
    private final ImBoolean gizmoEnabled = new ImBoolean(false);
    private final ImBoolean maskEnabled = new ImBoolean(false);
    private boolean dirty = true;
    private Instant lastMoved = Instant.now();
    private int lastRx = -1;
    private int lastRy = -1;
    private int lastRz = -1;
    private IcoSphere icoSphere = null;
    private Set<BlockPos> lastShell = new HashSet<>();
    private Vec3 lastHit = Vec3.ZERO;
    private boolean lastAngled = false;
    private boolean lastExtendToGround = false;
    private boolean lastAngledView = false;
    private boolean reRandomize = false;
    private final PresetWidget presetWidget = new PresetWidget(this, "arceon_boulder");

    public BoulderBrush(){}

    @Override
    public void reset() {
        this.blockRegion.clear();
        this.chunkedBlockRegion = newChunkedBlockRegion();
        lastShell = new HashSet<>();
        this.icoSphere = null;
        recalculate = true;
    }



    public void reset(boolean resetGizmo) {
        if (resetGizmo){this.gizmo = null;}
        this.blockRegion.clear();
        this.chunkedBlockRegion = newChunkedBlockRegion();
        lastShell = new HashSet<>();
        this.icoSphere = null;
        recalculate = true;

    }

    @Override
    public String name() {
        return "Boulder Brush";
    }

    public UserAction.ActionResult callAction(UserAction action, Object object) {
        return switch (action) {
            case DELETE -> {
                this.reset(true);
                yield UserAction.ActionResult.USED_STOP;
            }
            case ROTATE_PLACEMENT -> {
                lastShell = new HashSet<>();
                recalculate = true;
                reRandomize = true;
                recalculateVariation = true;
                this.icoSphere = null;
                yield UserAction.ActionResult.USED_STOP;
            }
            case RIGHT_MOUSE -> {
                if (this.gizmoEnabled.get()){
                    RayCaster.RaycastResult result = Tool.raycastBlock(false, true, true);
                    if (result != null) {
                        BlockPos blockPos = result.blockPos();
                        if (this.gizmo != null) {
                            this.gizmo.moveTo(blockPos);
                            this.gizmo.enableAxes = true;
                        } else {
                            this.gizmo = new Gizmo(blockPos);
                            //this.icoSphere = null;
                        }
                    }
                    this.reorientPoint = true;
                    this.recalculate = true;
                }
                else{
                    place();
                }
                yield UserAction.ActionResult.USED_STOP;
            }
            case LEFT_MOUSE -> {
                if (this.gizmo != null && this.gizmo.leftClick()) {
                    yield UserAction.ActionResult.USED_STOP;
                }
                yield UserAction.ActionResult.NOT_HANDLED;
            }
            case ESCAPE -> {
                if (this.gizmo != null && this.gizmo.enableAxes) {
                    this.gizmo.enableAxes = false;
                    yield UserAction.ActionResult.USED_STOP;
                }
                yield UserAction.ActionResult.NOT_HANDLED;
            }
            case SCROLL -> {
                UserAction.ScrollAmount scrollAmount = (UserAction.ScrollAmount)object;
                if (this.handleScroll(scrollAmount.scrollX(), scrollAmount.scrollY())) {
                    recalculate = true;
                    yield UserAction.ActionResult.USED_STOP;
                }
                yield UserAction.ActionResult.NOT_HANDLED;
            }
            case ENTER, PASTE -> {
                place();
                yield UserAction.ActionResult.USED_STOP;
            }
            default -> UserAction.ActionResult.NOT_HANDLED;
        };
    }

    public void place(){
        ToolService toolService = ServiceHelper.getToolService();
        RayCaster.RaycastResult hitResult = Tool.raycastBlock(false, true, Tool.defaultIncludeFluids());

        Level level = ArceonClient.client.getInstance().level;

        boolean arceonDetected = true;
        boolean hasPerms = true;

        if (Networking.onServer && !Networking.attemptToUse(Minecraft.getInstance().player, true)){
            arceonDetected = false;
        }

        if (Networking.onServer && !Networking.hasPermission("boulder")){
            hasPerms = false;
        }

        if ((hitResult != null || gizmoEnabled.get()) && arceonDetected && hasPerms)
        {
            this.blockRegion = ServiceHelper.createBlockRegion();

            if (!lastShell.isEmpty() || recalculate || dirty){
                Set<BlockPos> placed = hollow.get() || extendToGround.get()  ? lastShell : fillFromShell(lastShell, lastHit);

                for (BlockPos bp : placed) {
                    if (keepExisting.get() && level != null){
                        BlockState blockState = level.getBlockState(bp);
                        if (blockState.getBlock().equals(Blocks.AIR)){
                            blockRegion.addBlockIfNotPresent(bp.getX(), bp.getY(), bp.getZ(), toolService.getActiveBlock());
                        }
                    }
                    else{
                        blockRegion.addBlockIfNotPresent(bp.getX(), bp.getY(), bp.getZ(), toolService.getActiveBlock());
                    }
                }

                toolService.pushBlockRegionChange(blockRegion);
            }
            else if (hitResult != null){
                int rx = this.radius[0];
                int ry = this.heightEnabled.get() ? this.height[0] : this.radius[0];
                int rz = this.depthEnabled.get() ? this.depth[0] : this.radius[0];

                Vec3 center = hitResult.getBlockPos().getCenter();


                Vec3 facing = Tool.getLookDirection();
                if (angledView.get()){
                    facing = Vec3.directionFromRotation(Minecraft.getInstance().player.getRotationVector());
                }

                double div = 2.3;

                boolean angledPlacement = false;
                if (angled.get() || angledView.get()){
                    angledPlacement = true;
                }

                lastShell = generateShell(center, facing, rx, ry, rz, angledPlacement, subs[0], div, true, extendToGround.get());
                Set<BlockPos> placed = hollow.get() || extendToGround.get() ? lastShell : fillFromShell(lastShell, lastHit);

                for (BlockPos bp : placed) {
                    if (keepExisting.get() && level != null){
                        BlockState blockState = level.getBlockState(bp);
                        if (blockState.getBlock().equals(Blocks.AIR)){
                            blockRegion.addBlockIfNotPresent(bp.getX(), bp.getY(), bp.getZ(), toolService.getActiveBlock());
                        }
                    }
                    else{
                        blockRegion.addBlockIfNotPresent(bp.getX(), bp.getY(), bp.getZ(), toolService.getActiveBlock());
                    };
                }

                toolService.pushBlockRegionChange(blockRegion);
            }

        }

    }

    public static List<Vec3> line(Vec3 start, Vec3 end, int amount) {
        amount--;
        List<Vec3> locs = new ArrayList<>();
        double xD = end.x - start.x;
        double yD = end.y - start.y;
        double zD = end.z - start.z;
        for (int x = 0; x < amount; x++) {
            locs.add(start.add(x * (xD / amount), x * (yD / amount), x * (zD / amount)));
        }
        locs.add(end);
        return locs;
    }

    public static Vec3 transformLocal(Vec3 origin, Vec3 dir, Vec3 local, boolean angled) {
        Vec3 f = dir.normalize();

        if (!angled) {
            f = new Vec3(f.x, 0, f.z);
            if (f.lengthSqr() < 1e-6) {
                f = new Vec3(0, 0, 1);
            }
            f = f.normalize();
        }

        Vec3 up = new Vec3(0, 1, 0);
        if (Math.abs(f.dot(up)) > 0.999) {
            up = new Vec3(1, 0, 0);
        }

        Vec3 r = up.cross(f).normalize();
        Vec3 u = f.cross(r).normalize();

        return origin
                .add(r.scale(local.x))
                .add(u.scale(local.y))
                .add(f.scale(local.z));
    }

    private Vec3 rotateAround(Vec3 point, Vec3 offset, Quaternionf q) {
        Vector3f v = point.toVector3f();

        // Apply rotation
        v = q.transform(v);

        // Move back to offset
        v = v.add((float)offset.x, (float)offset.y, (float)offset.z);

        return new Vec3(v.x, v.y, v.z);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Object newChunkedBlockRegion() {
        try {
            return Class.forName("com.moulberry.axiom.render.regions.ChunkedBlockRegion")
                    .getConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to create ChunkedBlockRegion", e);
        }
    }

    private void addChunkedBlock(int x, int y, int z, Object blockState) {
        try {
            Method addBlock = chunkedBlockRegion.getClass().getMethod(
                    "addBlock", int.class, int.class, int.class, blockState.getClass()
            );
            addBlock.invoke(chunkedBlockRegion, x, y, z, blockState);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to add preview block", e);
        }
    }

    private void renderChunkedBlockRegion(AxiomWorldRenderContext context, Vec3 target, Quaternionf rotation, float alpha, float lineAlpha) {
        try {
            Method render = chunkedBlockRegion.getClass().getMethod(
                    "render", AxiomWorldRenderContext.class, Vec3.class, Quaternionf.class, float.class, float.class
            );
            render.invoke(chunkedBlockRegion, context, target, rotation, alpha, lineAlpha);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to render ChunkedBlockRegion preview", e);
        }
    }

    private Set<BlockPos> generateShell(Vec3 center, Vec3 facing, int rx, int ry, int rz, boolean angled, int subs, double div, boolean random, boolean extendToGround) {
        Set<BlockPos> blocksEdge = new HashSet<>();
        chunkedBlockRegion = newChunkedBlockRegion();

        ToolService toolService = ServiceHelper.getToolService();

        subs = clamp(subs, 1, 5);

        if (random && randomSizeEnabled.get() && (recalculateVariation || icoSphere == null)) { // RANDOM SIZE VARIATIONS

            randomSize[0] = clamp(randomSize[0], 0, 32);
            randomSize[1] = clamp(randomSize[1], 0, 32);
            randomSize[2] = clamp(randomSize[2], 0, 32);
            int ranX ,ranY, ranZ;
            randomSizeSingle[0] = clamp(randomSizeSingle[0], 0, 32);
            if (!keepProportions.get()) { // FULL RANDOM
                ranX = randomGen.nextInt(-randomSize[0], randomSize[0]+1);
                ranY = randomGen.nextInt(-randomSize[1], randomSize[1]+1);
                ranZ = randomGen.nextInt(-randomSize[2], randomSize[2]+1);
            }
            else{ // KEEP PROPORTIONS
                int r = randomGen.nextInt(-randomSizeSingle[0], randomSizeSingle[0]+1);
                ranX = ranY = ranZ = r;
            }
            rx += ranX; ry += ranY; rz += ranZ;
            //SAVE last random values
            lastRx = ranX;
            lastRy = ranY;
            lastRz = ranZ;
            recalculateVariation = false;
        }
        else if (random && randomSizeEnabled.get() && !recalculateVariation && gizmo != null){
            rx += lastRx;
            ry += lastRy;
            rz += lastRz;
        }

        IcoSphere is = (icoSphere != null) ? icoSphere : new IcoSphere(subs, 1.2, random);

        // CLAMP
        rx = clamp(rx, 1, 64);
        ry = clamp(ry, 1, 64);
        rz = clamp(rz, 1, 64);





        for (int[] triangle : is.triangles) {
            Double[] _1 = is.points.get(triangle[0]);
            Double[] _2 = is.points.get(triangle[1]);
            Double[] _3 = is.points.get(triangle[2]);

            Vec3 _l1 = new Vec3(_1[0] * (rx / div), _1[1] * (ry / div), _1[2] * (rz / div));
            Vec3 _l2 = new Vec3(_2[0] * (rx / div), _2[1] * (ry / div), _2[2] * (rz / div));
            Vec3 _l3 = new Vec3(_3[0] * (rx / div), _3[1] * (ry / div), _3[2] * (rz / div));

            if (gizmoEnabled.get() && gizmo!=null && rotationQuaternion!=null){
                _l1 = rotateAround(_l1, center, rotationQuaternion);
                _l2 = rotateAround(_l2, center, rotationQuaternion);
                _l3 = rotateAround(_l3, center, rotationQuaternion);
            }
            else{
                _l1 = transformLocal(center, facing, _l1, angled);
                _l2 = transformLocal(center, facing, _l2, angled);
                _l3 = transformLocal(center, facing, _l3, angled);
            }



            double xD = _l1.x - _l2.x;
            double yD = _l1.y - _l2.y;
            double zD = _l1.z - _l2.z;
            int count = (int) (_l2.distanceTo(_l1) * 3) + 1;




            for (int x = 0; x < count; x++) {
                Vec3 start = _l2.add(x * (xD / count), x * (yD / count), x * (zD / count));
                for (Vec3 ve : line(start, _l3, count * 2)) {
                    BlockPos pos = new BlockPos(
                            (int) Math.floor(ve.x),
                            (int) Math.floor(ve.y),
                            (int) Math.floor(ve.z)
                    );
                    blocksEdge.add(pos);
                    if (gizmoEnabled.get() && gizmo != null) {
                        BlockPos local = pos.subtract(gizmo.getTargetPosition());
                        addChunkedBlock(local.getX(), local.getY(), local.getZ(), toolService.getActiveBlock());
                    }
                }
            }
        }
        Level level = ArceonClient.client.getInstance().level;

        if (extendToGround && level != null){
            Set<BlockPos> blocksEdgeCopy = new HashSet<>();

            for (BlockPos pos : blocksEdge){
                BlockPos below = pos;
                do {
                    blocksEdgeCopy.add(below);
                    below = below.below();
                } while (level.getBlockState(below).getBlock().equals(Blocks.AIR) && !blocksEdge.contains(below));
            }
            return blocksEdgeCopy;
        }

        return blocksEdge;
    }

    private Set<BlockPos> fillFromShell(Set<BlockPos> shell, Vec3 center) {
        Set<BlockPos> filled = new HashSet<>(shell);

        for (BlockPos pos : shell) {
            Vec3 from = pos.getCenter();
            int count = (int) (from.distanceTo(center) * 2) + 1;
            for (Vec3 ve : line(from, center, count)) {
                filled.add(new BlockPos(
                        (int) Math.floor(ve.x),
                        (int) Math.floor(ve.y),
                        (int) Math.floor(ve.z)
                ));
            }
        }

        return filled;
    }

    private Set<BlockPos> blocksToPlaceFromShell(Set<BlockPos> shell, Vec3 center) {
        if (shell == null || shell.isEmpty()) {
            return Collections.emptySet();
        }
        return (hollow.get() || extendToGround.get()) ? shell : fillFromShell(shell, center);
    }

    private void rebuildPreviewRegion(Set<BlockPos> shell, BlockPos anchor, Vec3 center) {
        previewRegion.clear();
        for (BlockPos bp : blocksToPlaceFromShell(shell, center)) {
            previewRegion.add(bp.getX() - anchor.getX(), bp.getY() - anchor.getY(), bp.getZ() - anchor.getZ());
        }
    }

    private int getPlaceRotation() {
        return this.placeRotation[0];
    }


    private void setRotationAuto(BlockPos at) {
        this.rotationX[0] = this.rotationY[0] = this.rotationZ[0] = 0.0F;
        this.rotationQuaternion.identity();
        float offsetX = 0.0F;
        float offsetY = 0.0F;
        float offsetZ = 0.0F;
        int checkRadiusSq = 12;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        Level world = ArceonClient.client.getInstance().level;
        if (world != null) {
            int x = at.getX();
            int y = at.getY();
            int z = at.getZ();

            for(int xo = -3; xo <= 3; ++xo) {
                for(int yo = -3; yo <= 3; ++yo) {
                    for(int zo = -3; zo <= 3; ++zo) {
                        int distSq = xo * xo + yo * yo + zo * zo;
                        if (distSq <= checkRadiusSq) {
                            BlockState block = world.getBlockState(mutableBlockPos.set(x + xo, y + yo, z + zo));
                            if (block.blocksMotion()) {
                                float factor = 1.0F / (float)Math.max(1.0, Math.sqrt((double)distSq));
                                offsetX -= (float)xo * factor;
                                offsetY -= (float)yo * factor;
                                offsetZ -= (float)zo * factor;
                            }
                        }
                    }
                }
            }

            float lengthSq = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
            if (lengthSq > 0.01F) {
                float invNormalLength = 1.0F / (float)Math.sqrt((double)lengthSq);
                float normalX = offsetX * invNormalLength;
                float normalY = offsetY * invNormalLength;
                float normalZ = offsetZ * invNormalLength;
                if ((double)Math.abs(normalX) < 1.0E-4) {
                    normalX = 0.0F;
                }

                if ((double)Math.abs(normalY) < 1.0E-4) {
                    normalY = 0.0F;
                }

                if ((double)Math.abs(normalZ) < 1.0E-4) {
                    normalZ = 0.0F;
                }

                double horizontal = Math.sqrt((double)(normalX * normalX + normalZ * normalZ));
                float pitch = Mth.wrapDegrees((float)(-(Mth.atan2((double)normalY, horizontal) * 57.2957763671875)) + 90.0F);
                float yaw = Mth.wrapDegrees((float)(Mth.atan2((double)normalZ, (double)normalX) * 57.2957763671875) + 90.0F);
                this.rotationQuaternion.rotateXYZ((float)Math.toRadians((double)pitch), (float)Math.toRadians((double)yaw), 0.0F);
                Vector3f euler = this.rotationQuaternion.getEulerAnglesYXZ(new Vector3f());
                this.rotationX[0] = (float)Math.round(Math.toDegrees((double)euler.x));
                this.rotationY[0] = (float)Math.round(Math.toDegrees((double)euler.y));
                this.rotationZ[0] = (float)Math.round(Math.toDegrees((double)euler.z));
                this.rotationQuaternion.identity();
                this.rotationQuaternion.rotateYXZ((float)Math.toRadians((double)this.rotationY[0]), (float)Math.toRadians((double)this.rotationX[0]), (float)Math.toRadians((double)this.rotationZ[0]));
            }

        }
    }


    //@Override
    //public void render(Camera camera, float tickDelta, long time, PoseStack matrices, Matrix4f projection) {

    @Override
    public void render(AxiomWorldRenderContext axiomWorldRenderContext) {

        if (Networking.onServer && !Networking.attemptToUse(Minecraft.getInstance().player, false)) {
            return;
        }

        if (Networking.onServer && !Networking.hasPermission("boulder")) {
            return;
        }

        int rx = this.radius[0];
        int ry = this.heightEnabled.get() ? this.height[0] : this.radius[0];
        int rz = this.depthEnabled.get() ? this.depth[0] : this.radius[0];

        boolean isCtrlDown = EditorUI.isCtrlOrCmdDown();
        boolean showGizmo = EditorUI.isActive();

        if (gizmoEnabled.get()){

            Selection.render(axiomWorldRenderContext, 4);
            if (showGizmo && this.gizmo != null) { // RENDER GIZMO
                this.gizmo.render(axiomWorldRenderContext, isCtrlDown);

                boolean isLeftDown = Tool.isMouseDown(0);
                Vec3 lookDirection = Tool.getLookDirection();

                // new icosphere
                if (icoSphere == null){
                    lastShell = new HashSet<>();
                    icoSphere = new IcoSphere(subs[0], 1.2, true);
                }

                if (reRandomize || dirty || recalculate || !lastHit.equals(gizmo.getTargetVec())){
                    if (dirty || recalculate || reRandomize){

                        recalculate = false;
                        dirty = false;
                        double div = 2.3;
                        lastShell = new HashSet<>();

                        if (reRandomize){
                            icoSphere = new IcoSphere(subs[0], 1.2, true);
                            reRandomize = false;
                        }

                        lastShell = generateShell(gizmo.getTargetVec(), Vec3.ZERO, rx, ry, rz, false, subs[0], div, true, extendToGround.get());
                    }

                    rebuildPreviewRegion(lastShell, gizmo.getTargetPosition(), gizmo.getTargetVec());

                    // store last values
                    lastHit = gizmo.getTargetVec();
                    lastMoved = Instant.now();
                }



                BlockPos before;
                if (Tool.isMouseDown(1) && this.gizmo != null && this.gizmo.enableAxes && this.reorientPoint) {
                    RayCaster.RaycastResult result = Tool.raycastBlock(false, true, Tool.defaultIncludeFluids());
                    if (result != null) {
                        before = result.blockPos();

                        if (!this.gizmo.getTargetPosition().equals(before)) {
                            int placeRotationMode = this.getPlaceRotation();
                            if (placeRotationMode == 2) {
                                this.rotationX[0] = this.rotationY[0] = this.rotationZ[0] = 0.0F;
                                this.rotationQuaternion.identity();
                            } else if (placeRotationMode == 1) {
                                this.setRotationAuto(result.blockPos());
                            }

                            this.gizmo.moveTo(before);
                            this.gizmo.enableAxes = true;
                            this.recalculate = true;
                        }
                    }
                } else {
                    this.reorientPoint = false;
                }

                boolean hasGrabbedGizmo = false;
                if (this.gizmo != null) {
                    this.gizmo.enableRotation = true;
                    this.gizmo.rotationSnapRadians = 0.017453292F;
                    this.gizmo.localRotation = null;

                    before = this.gizmo.getTargetPosition();
                    this.gizmo.update(axiomWorldRenderContext.nanos(), lookDirection, isLeftDown, isCtrlDown, true);
                    this.gizmo.setAxisDirections(true, true, true);

                    if (this.gizmo.isGrabbed()) {
                        hasGrabbedGizmo = true;
                        this.recalculate = true;
                    }

                    Gizmo.GizmoRotation rotation = this.gizmo.popRotation();
                    if (rotation != null) {
                        this.performRotation(rotation.axis(), -rotation.radians());
                        this.recalculate = true;
                    }

                    if (this.usingRealCoordinates && !this.gizmo.getTargetPosition().equals(before)) {
                        this.recalculate = true;
                    }
                }

                if (!hasGrabbedGizmo && this.gizmo == null) {
                    RayCaster.RaycastResult result = Tool.raycastBlock(false, true, true);
                    if (result != null) {
                        Tool.renderRaycastOverlay(axiomWorldRenderContext, result);
                    }
                }
            }

            if (this.gizmo != null){
                float opacity = (float)Math.sin((double)((float)axiomWorldRenderContext.nanos() / 1000000.0F / 50.0F / 8.0F));
                Gizmo.GizmoRotation gizmoRotation = this.gizmo.peekGizmoRotation();
                Quaternionf rotation;
                if (gizmoRotation != null) {
                    rotation = (new Quaternionf());
                    switch (gizmoRotation.axis()) {
                        case X -> rotation.rotateX(gizmoRotation.radians());
                        case Y -> rotation.rotateY(gizmoRotation.radians());
                        case Z -> rotation.rotateZ(gizmoRotation.radians());
                    }

                    //rotation.mul(this.rotationQuaternion);
                } else {
                    rotation = null;
                }
                Vec3 pos = gizmo.getTargetPosition().getBottomCenter();
                double x = Math.floor(pos.x);
                if (x<0) x++;
                double y = pos.y;
                if (y<0) y++;
                double z = Math.floor(pos.z);
                if (z<0) z++;
                if (rotation!=null){
                    this.renderChunkedBlockRegion(axiomWorldRenderContext, gizmo.getTargetVec(), rotation, 0.75F + opacity * 0.25F, 0.3F - opacity * 0.2F);
                }
                else {
                    this.previewRegion.render(axiomWorldRenderContext, new Vec3(x, y, z), Effects.BLUE);
                }
            }
        }

        else{
            RayCaster.RaycastResult hitResult = Tool.raycastBlock(false, true, Tool.defaultIncludeFluids());
            if (hitResult==null){
                return;
            }

            Vec3 center = hitResult.getBlockPos().getCenter();


            boolean angledPlacement = false;
            if (angled.get() || angledView.get()){
                angledPlacement = true;
            }

            if (dirty || recalculate || !lastHit.equals(center)){
                lastShell = new HashSet<>(); // clear lastShell

                recalculate = false;
                dirty = false;

                Vec3 facing = Tool.getLookDirection();
                if (angledView.get()){
                    facing = Vec3.directionFromRotation(Minecraft.getInstance().player.getRotationVector());
                }

                double div = 2.3;



                lastShell = generateShell(center, facing, rx, ry, rz, angledPlacement, subs[0], div, true, extendToGround.get());
                rebuildPreviewRegion(lastShell, hitResult.getBlockPos(), center);

                // store last values
                lastHit = center;
                lastMoved = Instant.now();
            }
            else if ((lastShell.isEmpty() || recalculate || dirty) && lastHit.equals(center) && Instant.now().isAfter(lastMoved.plus(50, ChronoUnit.MILLIS))) {

                Vec3 facing = Tool.getLookDirection();
                if (angledView.get()){
                    facing = Vec3.directionFromRotation(Minecraft.getInstance().player.getRotationVector());
                }

                double div = 2.3;

                lastShell = generateShell(center, facing, rx, ry, rz, angledPlacement, subs[0], div, true, extendToGround.get());

                rebuildPreviewRegion(lastShell, hitResult.getBlockPos(), center);

                // store last values
                lastHit = center;
                lastMoved = Instant.now();
            }
            BlockPos pos = hitResult.getBlockPos();
            Vec3 bottomCenter = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            this.previewRegion.render(
                    axiomWorldRenderContext,
                    bottomCenter.add(-0.5, 0, -0.5),
                    lastShell.isEmpty() && outline.get() ? Effects.OUTLINE : Effects.BLUE
            );
        }




    }

    private void performRotation(Direction.Axis axis, float radians) {
        Quaternionf rotation = new Quaternionf();
        rotation.rotateYXZ((float)Math.toRadians((double)this.rotationY[0]), (float)Math.toRadians((double)this.rotationX[0]), (float)Math.toRadians((double)this.rotationZ[0]));
        switch (axis) {
            case X -> rotation.rotateLocalX(-radians);
            case Y -> rotation.rotateLocalY(-radians);
            case Z -> rotation.rotateLocalZ(-radians);
        }

        Vector3f euler = rotation.getEulerAnglesYXZ(new Vector3f());
        this.rotationX[0] = (float)Math.round(Math.toDegrees((double)euler.x));
        this.rotationY[0] = (float)Math.round(Math.toDegrees((double)euler.y));
        this.rotationZ[0] = (float)Math.round(Math.toDegrees((double)euler.z));
        this.rotationQuaternion.identity();
        this.rotationQuaternion.rotateYXZ((float)Math.toRadians((double)this.rotationY[0]), (float)Math.toRadians((double)this.rotationX[0]), (float)Math.toRadians((double)this.rotationZ[0]));
    }

    public boolean handleScroll(int xScroll, int yScroll) {
        if (this.gizmo != null) {
            Vec3 look = Tool.getLookDirection();
            this.gizmo.handleScroll(xScroll, yScroll, EditorUI.isCtrlOrCmdDown(), look);
            return true;
        } else {
            return false;
        }
    }


    @Override
    public void displayImguiOptions() {

        boolean changed = false;

        ImGuiHelper.separatorWithText("Arceon: Boulder Brush ");

        if (Networking.onServer && !Networking.attemptToUse(Minecraft.getInstance().player, false)){
            DefaultsGUI.getArceon();
            return;
        }

        if (Networking.onServer && !Networking.hasPermission("boulder")){
            DefaultsGUI.noPermission();
            return;
        }

        ImGui.text("Gizmo");
        changed |= ImGui.checkbox("Manual Placement", this.gizmoEnabled);

        if (changed && !gizmoEnabled.get()){
            lastShell = new HashSet<>();
            icoSphere = null;
        }

        if (gizmoEnabled.get()){
            ImGuiHelper.setupBorder();
            if (ImGui.button("Reset Rotations")) {
                this.rotationX[0] = this.rotationY[0] = this.rotationZ[0] = 0.0F;
                this.rotationQuaternion.identity();
                recalculate = true;
            }
            if (this.gizmo != null && ImGui.button("Place Boulder")) {
                this.place();
            }
            ImGuiHelper.finishBorder();
        }

        ImGui.separator();

        ImGui.text("Smoothness");
        changed |= ImGui.sliderInt("##subs", this.subs, 0, 5);

        ImGuiHelper.setupBorder();
        // RADIUS

        ImGuiHelper.separatorWithText("Size Settings");
        if (this.depthEnabled.get()){
            ImGui.text("Width");
        }
        else{
            ImGui.text("Size");
        }
        changed |= ImGui.sliderInt("##rad", this.radius, 1, 64);

        // HEIGHT
        boolean changedHeight = false;
        changedHeight |= ImGui.checkbox("Height", this.heightEnabled);
        if (changedHeight){
            changed = true;
            this.height[0] = this.radius[0];
        }
        if (this.heightEnabled.get()){
            changed |= ImGui.sliderInt("##height", this.height, 1, 64);
        }
        // Depth
        boolean changedDepth = false;
        changedDepth |= ImGui.checkbox("Depth", this.depthEnabled);
        if (changedDepth){
            changed = true;
            this.depth[0] = this.radius[0];
        }
        if (this.depthEnabled.get()){
            changed |= ImGui.sliderInt("##depth", this.depth, 1, 64);
        }

        ImGui.separator();

        boolean changedRandom = false;
        changedRandom |= ImGui.checkbox("Random Variation", this.randomSizeEnabled);

        if (changedRandom){
            changed = true;
            recalculateVariation = true;
            //this.randomSize[0] = 5;
            //this.randomSize[1] = 5;
            //this.randomSize[2] = 5;
        }
        if (this.randomSizeEnabled.get()) {
            boolean changedValues = false;
            if (keepProportions.get()){
                changedValues |= ImGuiHelper.inputInt("##randomsizesingle", this.randomSizeSingle);
            }
            else{
                changedValues |= ImGuiHelper.inputInt("##randomsize", this.randomSize);
            }
            changedValues |= ImGui.checkbox("Keep Proportions", this.keepProportions);
            if (changedValues){
                changed = true;
                recalculateVariation = true;
            }
        }


        ImGuiHelper.finishBorder();

        ImGui.separator();
        ImGuiHelper.separatorWithText("Extra Settings");
        ImGui.separator();
        changed |= ImGui.checkbox("Hollow", this.hollow);
        changed |= ImGui.checkbox(AxiomI18n.get("axiom.tool.path.extend_to_ground"), this.extendToGround);
        changed |= ImGui.checkbox("Keep Existing", this.keepExisting);

        changed |= ImGui.checkbox("Angled (Tool)", this.angled);
        if (changed && this.angled.get()){
            this.angledView.set(false);
        }
        changed |= ImGui.checkbox("Angled (View)", this.angledView);
        if (changed && this.angledView.get()){
            this.angled.set(false);
        }

        ImGui.separator();
        ImGuiHelper.separatorWithText("Visual Settings");
        ImGui.separator();
        changed |= ImGui.checkbox("Outline", this.outline);

        ImGui.separator();
        ImGuiHelper.separatorWithText(AxiomI18n.get("axiom.widget.presets"));
        ImGui.separator();
        presetWidget.displayImgui(changed);

        if (changed) {
            dirty = true;
        }
        ImGui.separator();
        ImGuiHelper.separatorWithText("Shortcuts");
        ImGui.separator();

        ImGui.text("Randomize [CTRL + R]");

        ImGui.separator();


        ImGuiHelper.separatorWithText("Version: " + VERSION);

    }

    public boolean initiateAdjustment() {
        int radius;
        int height;
        if (this.heightEnabled.get()) {
            radius = Math.round(this.radius[0] / 2.0F + this.depth[0] / 2.0F);
            height = Math.round(this.height[0]);
        } else {
            radius = Math.round(this.radius[0]);
            height = -1;
        }

        float distance = 0f;
        if (lastHit!=null){
            distance = (float)lastHit.distanceTo(Minecraft.getInstance().player.getEyePosition());
        }
        else if (gizmo!=null){
            distance = (float)this.gizmo.getInterpPosition().distanceTo(Minecraft.getInstance().player.getEyePosition());
        }
        this.radiusAdjustment.initiateAdjustment(radius, height, distance);
        return true;
    }

    public Vec2 renderAdjustment(float mouseX, float mouseY, Vec2 mouseDelta) {
        GenericRadiusAdjustment.Result result = this.radiusAdjustment.renderAdjustment(mouseX, mouseY, mouseDelta);
        if (result.changed()) {
            if (this.heightEnabled.get()) {
                this.radius[0] = result.radius();
                if (result.height() >= 0 && result.height()<=64) {
                    this.height[0] = result.height();
                }

                if (depthEnabled.get()){
                    this.depth[0] = result.radius();
                }
            } else {
                this.radius[0] = result.radius();
            }

            this.recalculate = true;
        }

        return result.mouseDelta();
    }

    public void writeSettings(CompoundTag nbt) {
        nbt.putInt("radius", radius[0]);
        nbt.putInt("height", height[0]);
        nbt.putInt("depth", depth[0]);
        nbt.putInt("subs", subs[0]);
        nbt.putInt("propVar", randomSizeSingle[0]);
        nbt.putInt("varX", randomSize[0]);
        nbt.putInt("varY", randomSize[1]);
        nbt.putInt("varZ", randomSize[2]);
        nbt.putBoolean("hollow", hollow.get());
        nbt.putBoolean("angled", angled.get());
        nbt.putBoolean("angledView", angledView.get());
        nbt.putBoolean("outline", outline.get());
        nbt.putBoolean("heightEnabled", heightEnabled.get());
        nbt.putBoolean("depthEnabled", depthEnabled.get());
        nbt.putBoolean("keepExisting", keepExisting.get());
        nbt.putBoolean("extendToGround", extendToGround.get());
        nbt.putBoolean("gizmo", gizmoEnabled.get());
        nbt.putBoolean("randSize", randomSizeEnabled.get());
        nbt.putBoolean("keepProp", keepProportions.get());
    }

    public void loadSettings(CompoundTag nbt) {
        radius[0] = VersionUtilsNbt.helperCompoundTagGetIntOr(nbt, "radius", radius[0]);
        height[0] = VersionUtilsNbt.helperCompoundTagGetIntOr(nbt, "height", height[0]);
        depth[0] = VersionUtilsNbt.helperCompoundTagGetIntOr(nbt, "depth", depth[0]);
        subs[0]   = VersionUtilsNbt.helperCompoundTagGetIntOr(nbt, "subs", subs[0]);
        randomSizeSingle[0]   = VersionUtilsNbt.helperCompoundTagGetIntOr(nbt, "propVar", randomSizeSingle[0]);
        randomSize[0]   = VersionUtilsNbt.helperCompoundTagGetIntOr(nbt, "varX", randomSize[0]);
        randomSize[1]   = VersionUtilsNbt.helperCompoundTagGetIntOr(nbt, "varY", randomSize[1]);
        randomSize[2]   = VersionUtilsNbt.helperCompoundTagGetIntOr(nbt, "varZ", randomSize[2]);

        hollow.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "hollow", hollow.get()));
        keepExisting.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "keepExisting", keepExisting.get()));
        angled.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "angled", angled.get()));
        angledView.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "angledView", angledView.get()));
        outline.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "outline", outline.get()));
        heightEnabled.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "heightEnabled", heightEnabled.get()));
        depthEnabled.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "depthEnabled", depthEnabled.get()));
        extendToGround.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "extendToGround", extendToGround.get()));
        gizmoEnabled.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "gizmo", gizmoEnabled.get()));
        randomSizeEnabled.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "randSize", randomSizeEnabled.get()));
        keepProportions.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "keepProp", keepProportions.get()));
    }

    @Override
    public char iconChar() {
        return '\uEA00';
    }

    @Override
    public String keybindId() {
        return "";
    }

    @Override
    public EnumSet<AxiomPermission> requiredPermissions() {
        return EnumSet.of(AxiomPermission.USE, AxiomPermission.USE);
    }

}
