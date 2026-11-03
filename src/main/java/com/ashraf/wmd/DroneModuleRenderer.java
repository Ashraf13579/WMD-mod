package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class DroneModuleRenderer extends GeoBlockRenderer<DroneModuleBlockEntity> {

    private static final float XS = -1F;
    private static final float STEP_PX = 80F;

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    private static DroneModuleBlockEntity coreOf(DroneModuleBlockEntity be) { return be.kind() == DroneKind.CORE ? be : DroneLine.coreOf(be); }

    private static boolean working(DroneAnim.Pose p, DroneKind k) { return p != null && p.active == k && p.f >= 0.24 && p.f < 0.76; }

    static class DroneModel extends GeoModel<DroneModuleBlockEntity> {
        @Override public ResourceLocation getModelResource(DroneModuleBlockEntity be) { return rl("geo/block/" + be.kind().block() + ".geo.json"); }
        @Override public ResourceLocation getTextureResource(DroneModuleBlockEntity be) { return rl("textures/block/" + be.kind().block() + ".png"); }
        @Override public ResourceLocation getAnimationResource(DroneModuleBlockEntity be) { return rl("animations/block/" + be.kind().block() + ".animation.json"); }

        @Override
        public void setCustomAnimations(DroneModuleBlockEntity be, long instanceId, AnimationState<DroneModuleBlockEntity> state) {
            if (be.getLevel() == null) return;
            final float pt = state.getPartialTick();
            final DroneModuleBlockEntity core = coreOf(be);
            final DroneAnim.Pose p = core == null ? new DroneAnim.Pose() : core.pose(pt);
            final double tt = be.getLevel().getGameTime() + pt;
            final boolean active = p.active == be.kind(), work = working(p, be.kind());
            getBone("lamp_run").ifPresent(b -> b.setHidden(!active));
            if (be.kind() == DroneKind.HANGAR) getBone("door").ifPresent(b -> b.setScaleY(1.0F - 0.94F * be.doorOpen(pt)));
            switch (be.kind()) {
                case CORE -> {
                    boolean ready = core != null && core.stage() == DroneModuleBlockEntity.Stage.IDLE && DroneLine.complete(core, core.type());
                    getBone("lamp_ok").ifPresent(b -> b.setHidden(!ready));
                    getBone("w_crate").ifPresent(b -> b.setHidden(!(p.active == DroneKind.CORE && p.cell == DroneAnim.Item.CRATE)));
                    getBone("car").ifPresent(b -> b.setPosX(XS * -STEP_PX * (float) p.car));
                    getBone("arm").ifPresent(b -> b.setPosZ(-(float) (p.reach * p.arm)));
                    getBone("bar").ifPresent(b -> b.setScaleZ(1.0F + (float) (p.reach * p.arm / 0.5)));
                    getBone("c_crate").ifPresent(b -> b.setHidden(p.carry != DroneAnim.Item.CRATE));
                }
                case FRAME -> {
                    getBone("head").ifPresent(b -> b.setPosX(work ? XS * (float) (14.0 * Math.sin(tt * 0.08)) : 0F));
                    getBone("torch").ifPresent(b -> b.setPosY(work ? -2.0F : 0F));
                    getBone("spark").ifPresent(b -> b.setHidden(!work || (((long) tt) & 1) == 0));
                }
                case AVIONICS -> {
                    getBone("r_yaw").ifPresent(b -> b.setRotY(work ? (float) (0.35 * Math.sin(tt * 0.05)) : 0F));
                    getBone("r_sh").ifPresent(b -> b.setRotX(work ? (float) (0.18 * Math.sin(tt * 0.07)) : 0F));
                    getBone("r_el").ifPresent(b -> b.setRotX(work ? (float) (0.12 * Math.sin(tt * 0.11)) : 0F));
                    getBone("tip").ifPresent(b -> b.setHidden(!work || (((long) tt / 3) & 1) == 0));
                    getBone("mag").ifPresent(b -> b.setRotY(active ? 0.45F : 0F));
                }
                case PROPULSION -> {
                    getBone("prop").ifPresent(b -> b.setRotZ(work ? (float) (tt * 0.6) : 0F));
                    getBone("rotor").ifPresent(b -> b.setRotY(work ? (float) (tt * 0.8) : 0F));
                }
                case TEST -> getBone("fan").ifPresent(b -> b.setRotX(p.spin && active ? (float) (tt * 0.7) : 0F));
                case STORAGE -> {
                    boolean mine = active;
                    getBone("stk").ifPresent(b -> b.setPosX(mine ? XS * (float) p.stkX : 0F));
                    getBone("lift").ifPresent(b -> b.setPosY(mine ? (float) p.liftY : 0F));
                    getBone("fork").ifPresent(b -> b.setPosZ(mine ? -(float) (p.fork * DroneAnim.STACK_OUT) : 0F));
                }
                default -> { }
            }
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

    private record Socket(Matrix4f pose, Matrix3f normal, float px, float py, float pz) {}
    private final Map<String, Socket> sockets = new HashMap<>();
    private final Map<String, Socket> meshPoses = new HashMap<>();
    private MeshModel mesh;

    private static boolean isSocket(String n) {
        return n.equals("cd") || n.equals("fd") || n.equals("ad") || n.equals("pd") || n.equals("td") || n.equals("sk") || n.startsWith("sd") || n.startsWith("hd");
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (!isSocket(bone.getName())) {
            if (mesh != null && mesh.has(bone.getName())) {
                if (!bone.isHidden()) meshPoses.put(bone.getName(), new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), 0, 0, 0));
                return;
            }
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
            return;
        }
        if (!bone.isHidden())
            sockets.put(bone.getName(), new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()));
    }

    private static PoseStack at(Socket s, float dy) {
        PoseStack ps = new PoseStack();
        ps.last().pose().set(s.pose());
        ps.last().normal().set(s.normal());
        ps.translate(s.px() / 16.0F, (s.py() + dy) / 16.0F, s.pz() / 16.0F);
        return ps;
    }

    static void drawDrone(PoseStack ps, DroneType t, DroneAnim.Item step, boolean full, boolean line, boolean spin, double tt, MultiBufferSource buffers, int light) {
        MeshModel m = MeshModel.named(t.mesh);
        if (!m.has("frame")) return;
        if (line) {
            ps.mulPose(Axis.YP.rotationDegrees(90.0F));
            ps.scale(t.lineScale, t.lineScale, t.lineScale);
        }
        int ov = OverlayTexture.NO_OVERLAY;
        m.draw("frame", ps.last(), buffers, light, ov);
        if (step.ordinal() >= DroneAnim.Item.AVIONICS.ordinal()) m.draw("avionics", ps.last(), buffers, light, ov);
        if (step.ordinal() >= DroneAnim.Item.POWERED.ordinal()) {
            m.draw("power", ps.last(), buffers, light, ov);
            if (t.rotors != null) {
                for (int i = 0; i < t.rotors.length; i++) {
                    float[] c = t.rotors[i];
                    ps.pushPose();
                    ps.translate(c[0] / 16.0F, c[1] / 16.0F, c[2] / 16.0F);
                    ps.mulPose(Axis.YP.rotationDegrees(spin ? (float) (tt * 47.0 * (i == 0 || i == 3 ? 1 : -1)) : 0F));
                    ps.translate(-c[0] / 16.0F, -c[1] / 16.0F, -c[2] / 16.0F);
                    m.draw("rotor" + i, ps.last(), buffers, light, ov);
                    ps.popPose();
                }
            } else if (t.prop != null) {
                float[] c = t.prop;
                ps.pushPose();
                ps.translate(c[0] / 16.0F, c[1] / 16.0F, c[2] / 16.0F);
                ps.mulPose(Axis.ZP.rotationDegrees(spin ? (float) (tt * 36.0) : 0F));
                ps.translate(-c[0] / 16.0F, -c[1] / 16.0F, -c[2] / 16.0F);
                m.draw("prop", ps.last(), buffers, light, ov);
                ps.popPose();
            }
        }
        if (t == DroneType.T4 && step.ordinal() >= DroneAnim.Item.POWERED.ordinal()) m.draw("gear", ps.last(), buffers, light, ov);
        if (full) m.draw("wing", ps.last(), buffers, light, ov);
    }

    private static void drawCrate(Socket s, MultiBufferSource buffers, int light) {
        MeshModel core = MeshModel.named("drone_core");
        if (!core.has("c_crate")) return;
        PoseStack ps = new PoseStack();
        ps.last().pose().set(s.pose());
        ps.last().normal().set(s.normal());
        ps.translate(0.0F, 0.0F, (s.pz() - 44.0F) / 16.0F);
        core.draw("c_crate", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
    }

    private static String stationSocket(DroneKind k) {
        return switch (k) { case FRAME -> "fd"; case AVIONICS -> "ad"; case PROPULSION -> "pd"; case TEST -> "td"; default -> null; };
    }

    private void drawSockets(DroneModuleBlockEntity be, float pt, MultiBufferSource buffers, int light) {
        DroneModuleBlockEntity core = coreOf(be);
        double tt = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + pt;
        DroneAnim.Pose p = core == null ? null : core.pose(pt);
        DroneType run = core == null ? DroneType.T1 : core.type();
        for (Map.Entry<String, Socket> e : sockets.entrySet()) {
            String n = e.getKey();
            Socket s = e.getValue();
            if (n.startsWith("sd")) {
                int i = n.charAt(2) - '0';
                ItemStack st = be.shelf(i);
                DroneType t = DroneType.of(st);
                if (t == null && p != null && p.placed && p.active == DroneKind.STORAGE && i == core.storeSlot()) t = run;
                if (t != null) drawDrone(at(s, 0), t, DroneAnim.Item.DRONE, true, false, false, tt, buffers, light);
                continue;
            }
            if (n.startsWith("hd")) {
                int i = n.charAt(2) - '0';
                DroneType t0 = DroneType.of(be.bay(0)), t1 = DroneType.of(be.bay(1));
                DroneType t = i == 2 ? (t0 == DroneType.T4 ? t0 : null) : i == 0 ? (t0 == DroneType.T4 ? null : t0) : t1;
                boolean wings = true;
                if (t == null && p != null && p.inHangar && p.active == DroneKind.HANGAR) {
                    int bay = core.storeSlot();
                    if (run == DroneType.T4 ? i == 2 : i == bay) { t = run; wings = p.wingsOn; }
                }
                if (t != null) drawDrone(at(s, 0), t, DroneAnim.Item.DRONE, wings, false, false, tt, buffers, light);
                continue;
            }
            if (p == null) continue;
            if (n.equals("cd")) {
                if (p.carry.ordinal() >= DroneAnim.Item.FRAME.ordinal()) drawDrone(at(s, 0), run, p.carry, false, true, false, tt, buffers, light);
                continue;
            }
            if (n.equals("sk")) {
                if (p.onStacker && be.kind() == DroneKind.STORAGE && p.active == DroneKind.STORAGE) drawDrone(at(s, 0), run, DroneAnim.Item.DRONE, true, false, false, tt, buffers, light);
                continue;
            }
            if (!n.equals(stationSocket(be.kind())) || p.active != be.kind() || p.cell == DroneAnim.Item.NONE) continue;
            if (p.cell == DroneAnim.Item.CRATE) { drawCrate(s, buffers, light); continue; }
            boolean spin = be.kind() == DroneKind.TEST ? p.spin : be.kind() == DroneKind.PROPULSION && working(p, be.kind());
            drawDrone(at(s, be.kind() == DroneKind.TEST ? (float) p.hover : 0F), run, p.cell, false, true, spin, tt, buffers, light);
        }
    }

    public DroneModuleRenderer() {
        super(new DroneModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(DroneModuleBlockEntity be) {
        return be.kind() == DroneKind.CORE ? new AABB(be.getBlockPos()).inflate(40, 8, 40) : new AABB(be.getBlockPos()).inflate(be.kind() == DroneKind.HANGAR ? 12 : 6);
    }

    @Override
    public int getViewDistance() { return 128; }

    @Override
    public boolean shouldRenderOffScreen(DroneModuleBlockEntity be) { return be.kind() == DroneKind.CORE; }

    @Override
    public void render(DroneModuleBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        int light = packedLight;
        if (level != null) {
            int a = LevelRenderer.getLightColor(level, be.getBlockPos().above(2));
            light = LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(packedLight)), Math.max(LightTexture.sky(a), LightTexture.sky(packedLight)));
        }
        sockets.clear();
        meshPoses.clear();
        mesh = MeshModel.named(be.kind().block());
        super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        for (Map.Entry<String, Socket> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue().pose());
            ps.last().normal().set(e.getValue().normal());
            mesh.draw(e.getKey(), ps.last(), bufferSource, light, packedOverlay);
        }
        meshPoses.clear();
        if (!sockets.isEmpty()) drawSockets(be, partialTick, bufferSource, light);
        sockets.clear();
    }
}
