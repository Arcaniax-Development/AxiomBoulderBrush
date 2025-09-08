package net.arcaniax.arceon.axiom;

import com.moulberry.axiom.RayCaster;
import com.moulberry.axiom.UserAction;
import com.moulberry.axiom.VersionUtilsNbt;
import com.moulberry.axiom.block_maps.BlockColourMap;
import com.moulberry.axiom.custom_blocks.CustomBlockState;
import com.moulberry.axiom.editor.ImGuiHelper;
import com.moulberry.axiom.editor.widgets.PresetWidget;
import com.moulberry.axiom.editor.widgets.SelectBlockWidget;
import com.moulberry.axiom.editor.windows.operations.AutoshadeWindow;
import com.moulberry.axiom.i18n.AxiomI18n;
import com.moulberry.axiom.restrictions.AxiomPermission;
import com.moulberry.axiom.tools.Tool;
import com.moulberry.axiom.utils.BlockWithFloat;
import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.pathers.ToolPatherUnique;
import com.moulberry.axiomclientapi.regions.BlockRegion;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import com.moulberry.axiomclientapi.service.ToolService;
import imgui.ImGui;
import imgui.flag.ImGuiButtonFlags;
import imgui.type.ImBoolean;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.VaultBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;

public class BoulderBrush implements Tool {

    private final BlockRegion blockRegion = ServiceHelper.createBlockRegion();
    private final BooleanRegion previewRegion = ServiceHelper.createBooleanRegion();
    private ToolPatherUnique pather = null;
    private boolean usingTool = false;

    private final int[] radius = new int[]{10};
    private final int[] height = new int[]{10};
    private final int[] depth = new int[]{10};
    private final int[] subs = new int[]{2};
    private final ImBoolean heightEnabled = new ImBoolean(false);
    private final ImBoolean depthEnabled = new ImBoolean(false);
    private final ImBoolean angled = new ImBoolean(false);
    private final ImBoolean angledView = new ImBoolean(false);
    private final ImBoolean hollow = new ImBoolean(false);
    private final ImBoolean outline = new ImBoolean(true);
    private final ImBoolean extendToGround = new ImBoolean(false);
    private boolean dirty = true;
    private Instant lastMoved = Instant.now();
    private int lastRx = -1;
    private int lastRy = -1;
    private int lastRz = -1;
    private IcoSphere icoSphere = null;
    private Set<BlockPos> lastShell = new HashSet<>();
    private Vec3d lastHit = Vec3d.ZERO;
    private boolean lastAngled = false;
    private boolean lastAngledView = false;
    private final PresetWidget presetWidget = new PresetWidget(this, "arceon_boulder");

    public BoulderBrush(){}

    @Override
    public void reset() {
        this.blockRegion.clear();
        lastShell = new HashSet<>();
        this.pather = null;
        this.usingTool = false;
    }

    @Override
    public String name() {
        return "Boulder Brush";
    }

    public UserAction.ActionResult callAction(UserAction action, Object object) {
        return switch (action) {
            case MIDDLE_MOUSE -> {
                this.reset();
                ToolService toolService = ServiceHelper.getToolService();
                
                yield UserAction.ActionResult.USED_STOP;
            }
            case ROTATE_PLACEMENT -> {
                lastShell = new HashSet<>();
                yield UserAction.ActionResult.USED_STOP;
            }
            case ENTER, PASTE, RIGHT_MOUSE -> {
                this.pather = null;
                this.usingTool = false;
                ToolService toolService = ServiceHelper.getToolService();
                RayCaster.RaycastResult hitResult = Tool.raycastBlock(false, true, Tool.defaultIncludeFluids());

                boolean arceonDetected = true;
                boolean hasPerms = true;

                if (Networking.onServer && !Networking.attemptToUse(ArceonInitializer.client.player, true)){
                    arceonDetected = false;
                }

                if (Networking.onServer && !Networking.hasPermission("boulder")){
                    hasPerms = false;
                }

                if (hitResult != null && arceonDetected && hasPerms)
                {
                    blockRegion.clear();

                    if (!lastShell.isEmpty()){
                        Set<BlockPos> placed = hollow.get() ? lastShell : fillFromShell(lastShell, lastHit);

                        for (BlockPos bp : placed) {
                            blockRegion.addBlock(bp.getX(), bp.getY(), bp.getZ(), toolService.getActiveBlock());
                        }

                        toolService.pushBlockRegionChange(blockRegion);
                    }
                    else{
                        int rx = this.radius[0];
                        int ry = this.heightEnabled.get() ? this.height[0] : this.radius[0];
                        int rz = this.depthEnabled.get() ? this.depth[0] : this.radius[0];

                        Vec3d center = hitResult.getBlockPos().toCenterPos();


                        Vec3d facing = Tool.getLookDirection();
                        if (angledView.get()){
                            facing = MinecraftClient.getInstance().player.getRotationVec(1.0F);
                        }

                        double div = 2.3;

                        boolean angledPlacement = false;
                        if (angled.get() || angledView.get()){
                            angledPlacement = true;
                        }

                        lastShell = generateShell(center, facing, rx, ry, rz, angledPlacement, subs[0], div, true);
                        Set<BlockPos> placed = hollow.get() ? lastShell : fillFromShell(lastShell, lastHit);

                        for (BlockPos bp : placed) {
                            blockRegion.addBlock(bp.getX(), bp.getY(), bp.getZ(), toolService.getActiveBlock());
                        }

                        toolService.pushBlockRegionChange(blockRegion);
                    }

                }
                yield UserAction.ActionResult.USED_STOP;
            }
            default -> UserAction.ActionResult.NOT_HANDLED;
        };
    }

    public static List<Vec3d> line(Vec3d start, Vec3d end, int amount) {
        amount--;
        List<Vec3d> locs = new ArrayList<>();
        double xD = end.x - start.x;
        double yD = end.y - start.y;
        double zD = end.z - start.z;
        for (int x = 0; x < amount; x++) {
            locs.add(start.add(x * (xD / amount), x * (yD / amount), x * (zD / amount)));
        }
        locs.add(end);
        return locs;
    }

    public static Vec3d transformLocal(Vec3d origin, Vec3d dir, Vec3d local, boolean angled) {
        Vec3d f = dir.normalize();

        if (!angled) {
            f = new Vec3d(f.x, 0, f.z);
            if (f.lengthSquared() < 1e-6) {
                f = new Vec3d(0, 0, 1);
            }
            f = f.normalize();
        }

        Vec3d up = new Vec3d(0, 1, 0);
        if (Math.abs(f.dotProduct(up)) > 0.999) {
            up = new Vec3d(1, 0, 0);
        }

        Vec3d r = up.crossProduct(f).normalize();
        Vec3d u = f.crossProduct(r).normalize();

        return origin.add(
                r.multiply(local.x)
                        .add(u.multiply(local.y))
                        .add(f.multiply(local.z))
        );
    }

    private Set<BlockPos> generateShell(Vec3d center, Vec3d facing, int rx, int ry, int rz, boolean angled, int subs, double div, boolean random) {
        Set<BlockPos> blocksEdge = new HashSet<>();

        IcoSphere is = (icoSphere != null) ? icoSphere : new IcoSphere(subs, 1.2, random);

        for (int[] triangle : is.triangles) {
            Double[] _1 = is.points.get(triangle[0]);
            Double[] _2 = is.points.get(triangle[1]);
            Double[] _3 = is.points.get(triangle[2]);

            Vec3d _l1 = new Vec3d(_1[0] * (rx / div), _1[1] * (ry / div), _1[2] * (rz / div));
            Vec3d _l2 = new Vec3d(_2[0] * (rx / div), _2[1] * (ry / div), _2[2] * (rz / div));
            Vec3d _l3 = new Vec3d(_3[0] * (rx / div), _3[1] * (ry / div), _3[2] * (rz / div));

            _l1 = transformLocal(center, facing, _l1, angled);
            _l2 = transformLocal(center, facing, _l2, angled);
            _l3 = transformLocal(center, facing, _l3, angled);

            double xD = _l1.x - _l2.x;
            double yD = _l1.y - _l2.y;
            double zD = _l1.z - _l2.z;
            int count = (int) (_l2.distanceTo(_l1) * 3) + 1;

            for (int x = 0; x < count; x++) {
                Vec3d start = _l2.add(x * (xD / count), x * (yD / count), x * (zD / count));
                for (Vec3d ve : line(start, _l3, count * 2)) {
                    blocksEdge.add(BlockPos.ofFloored(ve.x, ve.y, ve.z));
                }
            }
        }

        return blocksEdge;
    }

    private Set<BlockPos> fillFromShell(Set<BlockPos> shell, Vec3d center) {
        Set<BlockPos> filled = new HashSet<>(shell);

        for (BlockPos pos : shell) {
            Vec3d from = pos.toCenterPos();
            int count = (int) (from.distanceTo(center) * 2) + 1;
            for (Vec3d ve : line(from, center, count)) {
                filled.add(BlockPos.ofFloored(ve.x, ve.y, ve.z));
            }
        }

        return filled;
    }

    @Override
    public void render(Camera camera, float tickDelta, long time, MatrixStack poseStack, Matrix4f projection) {

        if (Networking.onServer && !Networking.attemptToUse(ArceonInitializer.client.player, false)) {
            return;
        }

        if (Networking.onServer && !Networking.hasPermission("boulder")) {
            return;
        }

        int rx = this.radius[0];
        int ry = this.heightEnabled.get() ? this.height[0] : this.radius[0];
        int rz = this.depthEnabled.get() ? this.depth[0] : this.radius[0];

        RayCaster.RaycastResult hitResult = Tool.raycastBlock(false, true, Tool.defaultIncludeFluids());
        if (hitResult==null){
            return;
        }

        Vec3d center = hitResult.getBlockPos().toCenterPos();


        ToolService toolService = ServiceHelper.getToolService();

        boolean angledPlacement = false;
        if (angled.get() || angledView.get()){
            angledPlacement = true;
        }

        if (!lastHit.equals(center) || lastRx != rx || lastRy != ry || lastRz != rz || lastAngled != angled.get()|| lastAngledView != angledView.get()){
            lastShell = new HashSet<>(); // clear lastShell

            Vec3d facing = Tool.getLookDirection();
            if (angledView.get()){
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                facing = player.getRotationVec(1.0F);
            }

            double div = 2.3;



            Set<BlockPos> tempShell = generateShell(center, facing, rx, ry, rz, angledPlacement, Math.min(subs[0], 2), div, false);
            previewRegion.clear();
            for (BlockPos bp : tempShell) {
                // store as relative coords for preview
                previewRegion.add(bp.getX() - hitResult.getBlockPos().getX(),
                        bp.getY() - hitResult.getBlockPos().getY(),
                        bp.getZ() - hitResult.getBlockPos().getZ());
            }

            // store last values
            lastRx = rx;
            lastRy = ry;
            lastRz = rz;
            lastAngled = angled.get();
            lastAngledView = angledView.get();
            lastHit = center;
            lastMoved = Instant.now();
        }
        else if (lastShell.isEmpty() && lastHit.equals(center) && Instant.now().isAfter(lastMoved.plus(50, ChronoUnit.MILLIS))) {

            Vec3d facing = Tool.getLookDirection();
            if (angledView.get()){
                facing = MinecraftClient.getInstance().player.getRotationVec(1.0F);
            }

            double div = 2.3;

            lastShell = generateShell(center, facing, rx, ry, rz, angledPlacement, subs[0], div, true);

            previewRegion.clear();
            for (BlockPos bp : lastShell) {
                // store as relative coords for preview
                previewRegion.add(bp.getX() - hitResult.getBlockPos().getX(),
                        bp.getY() - hitResult.getBlockPos().getY(),
                        bp.getZ() - hitResult.getBlockPos().getZ());
            }

            // store last values
            lastRx = rx;
            lastRy = ry;
            lastRz = rz;
            lastAngled = angled.get();
            lastAngledView = angledView.get();
            lastHit = center;
            lastMoved = Instant.now();
        }
        if (lastShell.isEmpty() && outline.get()){
            this.previewRegion.render(camera, hitResult.getBlockPos().toBottomCenterPos().add(-0.5, 0, -0.5), poseStack,
                    projection, time, Effects.OUTLINE);
        }
        else{
            this.previewRegion.render(camera, hitResult.getBlockPos().toBottomCenterPos().add(-0.5, 0, -0.5), poseStack,
                    projection, time, Effects.BLUE);
        }


    }


    @Override
    public void displayImguiOptions() {

        boolean changed = false;

        ImGuiHelper.separatorWithText("Arceon: Boulder Brush ");

        if (Networking.onServer && !Networking.attemptToUse(ArceonInitializer.client.player, false)){
            ImGuiArceon.getArceon();
            return;
        }

        if (Networking.onServer && !Networking.hasPermission("boulder")){
            ImGuiArceon.noPermission();
            return;
        }

        ImGui.text("Smoothness");
        changed |= ImGui.sliderInt("", this.subs, 0, 5);

        ImGuiHelper.setupBorder();
        // RADIUS

        if (this.depthEnabled.get()){
            changed |= ImGui.sliderInt("Width", this.radius, 1, 64);
        }
        else{
            changed |= ImGui.sliderInt("Size", this.radius, 1, 64);
        }


        ImGui.separator();
        // HEIGHT
        changed |= ImGui.checkbox("different Height", this.heightEnabled);
        if (this.heightEnabled.get()){
            changed |= ImGui.sliderInt("Height", this.height, 1, 64);
        }

        ImGui.separator();
        // Depth
        changed |= ImGui.checkbox("different Depth", this.depthEnabled);
        if (this.depthEnabled.get()){
            changed |= ImGui.sliderInt("Depth", this.depth, 1, 64);
        }

        ImGuiHelper.finishBorder();


        ImGui.text("Extra Options:");
        ImGui.separator();
        changed |= ImGui.checkbox("Hollow", this.hollow);
        changed |= ImGui.checkbox("Angled (Tool)", this.angled);
        if (changed && this.angled.get()){
            this.angledView.set(false);
        }
        changed |= ImGui.checkbox("Angled (View)", this.angledView);
        if (changed && this.angledView.get()){
            this.angled.set(false);
        }

        ImGui.separator();
        ImGui.text("Preview Settings:");
        ImGui.separator();
        changed |= ImGui.checkbox("Outline", this.outline);


        ImGuiHelper.separatorWithText(AxiomI18n.get("axiom.widget.presets"));
        presetWidget.displayImgui(changed);
        if (changed) {
            dirty = true;
        }
        ImGui.separator();

        ImGui.text("CTRL+R => Randomize");

        ImGui.separator();

        //ImGui.checkbox(AxiomI18n.get("axiom.tool.path.extend_to_ground"), this.extendToGround);

        /*
        // DEPTH
        ImGui.checkbox("copy to Depth?", this.depthEnabled);
        if (!this.depthEnabled.get()){
            ImGui.sliderInt("Depth", this.depth, 1, 32);
        }

        ImGui.checkbox("Angled (not working)", this.angled);

         */
    }

    public void writeSettings(NbtCompound nbt) {
        nbt.putInt("radius", radius[0]);
        nbt.putInt("height", height[0]);
        nbt.putInt("depth", depth[0]);
        nbt.putInt("subs", subs[0]);
        nbt.putBoolean("hollow", hollow.get());
        nbt.putBoolean("angled", angled.get());
        nbt.putBoolean("heightEnabled", heightEnabled.get());
        nbt.putBoolean("depthEnabled", depthEnabled.get());
    }

    public void loadSettings(NbtCompound nbt) {
        radius[0] = VersionUtilsNbt.helperCompoundTagGetIntOr(nbt, "radius", radius[0]);
        height[0] = VersionUtilsNbt.helperCompoundTagGetIntOr(nbt, "height", height[0]);
        depth[0] = VersionUtilsNbt.helperCompoundTagGetIntOr(nbt, "depth", depth[0]);
        subs[0]   = VersionUtilsNbt.helperCompoundTagGetIntOr(nbt, "subs", subs[0]);

        hollow.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "hollow", hollow.get()));
        angled.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "angled", angled.get()));
        heightEnabled.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "heightEnabled", heightEnabled.get()));
        depthEnabled.set(VersionUtilsNbt.helperCompoundTagGetBooleanOr(nbt, "depthEnabled", depthEnabled.get()));
    }

    @Override
    public char iconChar() {
        return '\ue906';
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
