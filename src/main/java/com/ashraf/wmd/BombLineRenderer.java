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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class BombLineRenderer extends GeoBlockRenderer<BombLineBlockEntity> {

    private static final float XS = -1F;
    private static final float STEP_PX = 80F;

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    private static BombLineBlockEntity coreOf(BombLineBlockEntity be) { return be.kind() == BombLineKind.CORE ? be : BombLine.coreOf(be); }

    private static boolean working(BombLineAnim.Pose p, BombLineKind k) { return p != null && p.active == k && p.f >= 0.24 && p.f < 0.76; }

    static float lineScale(BombType t) { return Math.min(1.0F, Math.min(48.0F / t.length, 6.5F / t.radius)); }

    static class LineModel extends GeoModel<BombLineBlockEntity> {
        @Override public ResourceLocation getModelResource(BombLineBlockEntity be) { return rl("geo/block/" + be.kind().block() + ".geo.json"); }
        @Override public ResourceLocation getTextureResource(BombLineBlockEntity be) { return rl("textures/block/" + be.kind().block() + ".png"); }
        @Override public ResourceLocation getAnimationResource(BombLineBlockEntity be) { return rl("animations/block/" + be.kind().block() + ".animation.json"); }

        @Override
        public void setCustomAnimations(BombLineBlockEntity be, long instanceId, AnimationState<BombLineBlockEntity> state) {
            if (be.getLevel() == null) return;
            final float pt = state.getPartialTick();
            final BombLineBlockEntity core = coreOf(be);
            final BombLineAnim.Pose p = core == null ? new BombLineAnim.Pose() : core.pose(pt);
            final double tt = be.getLevel().getGameTime() + pt;
            final boolean active = p.active == be.kind(), work = working(p, be.kind());
            getBone("lamp_run").ifPresent(b -> b.setHidden(!active));
            switch (be.kind()) {
                case CORE -> {
                    boolean ready = core != null && core.stage() == BombLineBlockEntity.Stage.IDLE && BombLine.complete(core, core.type());
                    getBone("lamp_ok").ifPresent(b -> b.setHidden(!ready));
                    getBone("w_crate").ifPresent(b -> b.setHidden(!(p.active == BombLineKind.CORE && p.cell == BombLineAnim.Item.CRATE)));
                    getBone("car").ifPresent(b -> b.setPosX(XS * -STEP_PX * (float) p.car));
                    getBone("arm").ifPresent(b -> b.setPosZ(-(float) (p.reach * p.arm)));
                    getBone("bar").ifPresent(b -> b.setScaleZ(1.0F + (float) (p.reach * p.arm / 0.5)));
                    getBone("c_crate").ifPresent(b -> b.setHidden(p.carry != BombLineAnim.Item.CRATE));
                }
                case PRESS -> getBone("ram").ifPresent(b -> b.setPosY(active ? (float) (-3.0 * stroke(p.f)) : 0F));
                case FILLING -> {
                    boolean pouring = active && p.f >= 0.3 && p.f < 0.55;
                    getBone("spout").ifPresent(b -> b.setRotY(active && p.f >= 0.24 && p.f < 0.62 ? 0F : 0.9F));
                    getBone("stream").ifPresent(b -> b.setHidden(!pouring));
                }
                case FUZE -> {
                    getBone("r_yaw").ifPresent(b -> b.setRotY(work ? (float) (0.35 * Math.sin(tt * 0.05)) : 0F));
                    getBone("r_sh").ifPresent(b -> b.setRotX(work ? (float) (0.18 * Math.sin(tt * 0.07)) : 0F));
                    getBone("r_el").ifPresent(b -> b.setRotX(work ? (float) (0.12 * Math.sin(tt * 0.11)) : 0F));
                    getBone("tip").ifPresent(b -> b.setHidden(!work || (((long) tt / 3) & 1) == 0));
                }
                case STORAGE -> {
                    getBone("lift").ifPresent(b -> b.setPosY(active ? (float) p.liftY : 0F));
                    getBone("fork").ifPresent(b -> b.setPosZ(active ? -(float) (p.fork * p.forkOut) : 0F));
                }
                case CRADLE -> {
                    getBone("hoist").ifPresent(b -> b.setPosX(active ? XS * (float) p.hoistX : 0F));
                    getBone("hook").ifPresent(b -> b.setPosY(active ? (float) p.hookY : 0F));
                }
            }
            BoneOverrides.apply(this, getModelResource(be));
        }

        private static double stroke(double f) { return Math.max(bump(f, 0.26, 0.42), Math.max(bump(f, 0.42, 0.58), bump(f, 0.58, 0.74))); }

        private static double bump(double f, double a, double b) {
            if (f < a || f > b) return 0.0;
            double m = (a + b) / 2.0;
            return BombLineAnim.ease(f < m ? (f - a) / (m - a) : (b - f) / (b - m));
        }
    }

    private record Socket(Matrix4f pose, Matrix3f normal, float px, float py, float pz) {}
    private final Map<String, Socket> sockets = new HashMap<>();
    private final Map<String, Socket> meshPoses = new HashMap<>();
    private MeshModel mesh;

    private static boolean isSocket(String n) {
        return n.equals("cd") || n.equals("pd") || n.equals("fd") || n.equals("zd") || n.equals("sk") || n.equals("cr") || n.startsWith("sd");
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

    private static PoseStack at(Socket s) {
        PoseStack ps = new PoseStack();
        ps.last().pose().set(s.pose());
        ps.last().normal().set(s.normal());
        ps.translate(s.px() / 16.0F, s.py() / 16.0F, s.pz() / 16.0F);
        return ps;
    }

    private static void drawBomb(PoseStack ps, BombType t, BombLineAnim.Item step, boolean line, MultiBufferSource buffers, int light) {
        MeshModel m = MeshModel.named(t.mesh);
        if (!m.has("part")) return;
        float s = line ? lineScale(t) : 1.0F;
        if (t != BombType.BOUNCING) {
            ps.translate(0.0F, t.radius * s / 16.0F, 0.0F);
            ps.scale(s, s, s);
            ps.translate(t.length / 32.0F, 0.0F, 0.0F);
            ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
            ps.mulPose(Axis.ZP.rotationDegrees(90.0F));
        } else ps.scale(s, s, s);
        int ov = OverlayTexture.NO_OVERLAY;

        if (step == BombLineAnim.Item.CASING) { m.drawColoured("part", ps.last(), buffers, light, ov, 150, 155, 165); return; }
        m.draw("part", ps.last(), buffers, light, ov);
        m.draw("band", ps.last(), buffers, light, ov);
        if (step != BombLineAnim.Item.BOMB) return;
        for (String bone : t == BombType.GLIDE ? new String[] { "fuze", "tail", "wing_l", "wing_r" } : new String[] { "fuze", "tail" }) m.draw(bone, ps.last(), buffers, light, ov);
    }

    private static void drawCrate(Socket s, MultiBufferSource buffers, int light) {
        MeshModel core = MeshModel.named("bomb_line_core");
        if (!core.has("c_crate")) return;
        PoseStack ps = new PoseStack();
        ps.last().pose().set(s.pose());
        ps.last().normal().set(s.normal());
        ps.translate(0.0F, 0.0F, (s.pz() - 44.0F) / 16.0F);
        core.draw("c_crate", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
    }

    private static String stationSocket(BombLineKind k) {
        return switch (k) { case PRESS -> "pd"; case FILLING -> "fd"; case FUZE -> "zd"; default -> null; };
    }

    private void drawSockets(BombLineBlockEntity be, float pt, MultiBufferSource buffers, int light) {
        BombLineBlockEntity core = coreOf(be);
        BombLineAnim.Pose p = core == null ? null : core.pose(pt);
        BombType run = core == null ? BombType.IRON : core.type();
        for (Map.Entry<String, Socket> e : sockets.entrySet()) {
            String n = e.getKey();
            Socket s = e.getValue();
            if (n.startsWith("sd")) {
                int i = n.charAt(2) - '0';
                BombType t = BombType.of(be.pallet(i));
                if (t == null && p != null && p.placed && p.active == BombLineKind.STORAGE && i == core.storeSlot()) t = run;
                if (t != null) drawBomb(at(s), t, BombLineAnim.Item.BOMB, false, buffers, light);
                continue;
            }
            if (n.equals("cr")) {
                BombType t = BombType.of(be.cradleBomb());
                BombLineAnim.Item step = BombLineAnim.Item.BOMB;
                if (t == null && p != null && p.onCradle && p.active == BombLineKind.CRADLE) { t = run; step = p.done ? BombLineAnim.Item.BOMB : p.filled ? BombLineAnim.Item.FILLED : BombLineAnim.Item.CASING; }
                if (t != null) drawBomb(at(s), t, step, false, buffers, light);
                continue;
            }
            if (p == null) continue;
            if (n.equals("cd")) {
                if (p.carry.ordinal() >= BombLineAnim.Item.CASING.ordinal()) drawBomb(at(s), run, p.carry, true, buffers, light);
                continue;
            }
            if (n.equals("sk")) {
                if (p.onStacker && be.kind() == BombLineKind.STORAGE && p.active == BombLineKind.STORAGE) drawBomb(at(s), run, BombLineAnim.Item.BOMB, false, buffers, light);
                continue;
            }
            if (!n.equals(stationSocket(be.kind())) || p.active != be.kind() || p.cell == BombLineAnim.Item.NONE) continue;
            if (p.cell == BombLineAnim.Item.CRATE) { drawCrate(s, buffers, light); continue; }
            drawBomb(at(s), run, p.cell, true, buffers, light);
        }
    }

    public BombLineRenderer() {
        super(new LineModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(BombLineBlockEntity be) {
        return be.kind() == BombLineKind.CORE ? new AABB(be.getBlockPos()).inflate(34, 8, 34) : new AABB(be.getBlockPos()).inflate(be.kind() == BombLineKind.CRADLE ? 8 : 6);
    }

    @Override
    public int getViewDistance() { return 128; }

    @Override
    public boolean shouldRenderOffScreen(BombLineBlockEntity be) { return be.kind() == BombLineKind.CORE; }

    @Override
    public void render(BombLineBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
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
