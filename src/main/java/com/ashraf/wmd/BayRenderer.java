package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class BayRenderer extends GeoBlockRenderer<BayModuleBlockEntity> {

    private static final float XS = -1F;

    private static final float ZS = 1F;

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    static String modelName(BayModuleBlockEntity be) {
        String v = "";

        if (be.kind == BayKind.WELD) { BayModuleBlockEntity c = be.core(); int cal = c == null ? 1 : c.calibre(); if (cal > 1) v = "_c" + cal; }

        if (be.kind == BayKind.EXTEND) {
            BayModuleBlockEntity c = be.core();
            int pair = c == null ? 0 : (Math.max(1, BayLine.extensionNumber(be)) - 1) / 3;
            boolean l = c != null && BayLine.rackOf(c, 2 * pair) != null, r = c != null && BayLine.rackOf(c, 2 * pair + 1) != null;
            v = l && r ? "_rack_lr" : l ? "_rack" : r ? "_rack_r" : v;
        }
        return "bay_" + be.kind.name().toLowerCase() + v;
    }

    static class BayModel extends GeoModel<BayModuleBlockEntity> {

        @Override public ResourceLocation getModelResource(BayModuleBlockEntity be) { return rl("geo/block/" + modelName(be) + ".geo.json"); }
        @Override public ResourceLocation getTextureResource(BayModuleBlockEntity be) { return rl("textures/block/bay_" + be.kind.name().toLowerCase() + ".png"); }
        @Override public ResourceLocation getAnimationResource(BayModuleBlockEntity be) { return rl("animations/block/bay_" + be.kind.name().toLowerCase() + ".animation.json"); }

        @Override
        public void setCustomAnimations(BayModuleBlockEntity be, long instanceId, AnimationState<BayModuleBlockEntity> state) {
            if (be.getLevel() == null) return;
            final float pt = state.getPartialTick();
            final BayAnim.Pose p = be.kind.isRack() ? be.rackPose(pt) : be.pose(pt);
            final double tt = be.getLevel().getGameTime() + pt;
            switch (be.kind) {
                case CORE -> animateCore(be, p, tt);
                case WELD -> animateWeld(p, tt);
                case CRANE -> animateCrane(be, p, tt);
                case WARHEAD -> animateWarhead(be, p, tt);
                case CLEAN -> animateClean(be, p, tt);
                case PAINT -> animatePaint(be, p, tt);
                case CRYO -> animateCryo(be, p, tt);
                case RACK, RACK_R -> animateRack(be, p, tt);
                default -> {}
            }
            BoneOverrides.apply(this, getModelResource(be));
        }

        private static void size(software.bernie.geckolib.cache.object.GeoBone b, BayAnim.Pose p) {
            b.setScaleX((float) BayCalibre.rad(p.cal)); b.setScaleY((float) BayCalibre.rad(p.cal)); b.setScaleZ((float) BayCalibre.len(p.cal));
        }

        private void animateCore(BayModuleBlockEntity be, BayAnim.Pose p, double tt) {
            final float lc = (float) BayCalibre.len(p.cal), rc = (float) BayCalibre.rad(p.cal), lift = (float) BayCalibre.lift(p.cal);
            final boolean running = be.stage() != BayModuleBlockEntity.Stage.IDLE;
            getBone("cradle").ifPresent(b -> b.setPosZ((float) p.cradleZ));

            getBone("cs0").ifPresent(b -> b.setPosZ(40F * lc));
            getBone("cs1").ifPresent(b -> b.setPosZ(110F * lc));
            getBone("cs2").ifPresent(b -> { b.setHidden(p.cal < 2); b.setPosZ(75F * lc); });
            getBone("mbase").ifPresent(b -> b.setPosY(40F + 0.82F * 9F - (float) BayArt.AXY + lift));
            getBone("mt1").ifPresent(b -> { b.setScaleX(rc); b.setScaleY(lc); b.setScaleZ(rc); });
            getBone("mt2").ifPresent(b -> b.setHidden(true));
            getBone("mt3").ifPresent(b -> b.setHidden(true));
            getBone("mt1").ifPresent(b -> b.setHidden(!p.cradleLoaded));
            for (int k = 0; k < 4; k++) {
                final int kk = k;
                getBone("m1_" + BayAnim.SEG[k]).ifPresent(b -> { b.setHidden((p.attached >> kk & 1) == 0); b.setPosY((float) p.slide[kk]); });
                getBone("part" + k).ifPresent(b -> { b.setHidden((be.stockMask() >> kk & 1) == 0); size(b, p); b.setPosY(lift); b.setPosZ((float) (BayAnim.stock0(p.cal) - BayAnim.STOCK0_Z)); });
                getBone("held" + k).ifPresent(b -> { b.setHidden(p.held != kk); size(b, p); b.setPosZ((float) (BayAnim.GRIP_DZ - BayAnim.gripDz(p.cal))); });
            }
            getBone("loader").ifPresent(b -> b.setPosZ((float) p.loaderZ));
            getBone("larm_m").ifPresent(b -> b.setPosX(XS * (float) p.armX));
            getBone("larm").ifPresent(b -> b.setPosY(-(float) p.armDown));
            getBone("lcol").ifPresent(b -> b.setScaleY(1.0F + (float) p.armDown / 4.0F));
            getBone("gjl").ifPresent(b -> { size(b, p); b.setPosX(XS * -11.5F * rc * (float) p.jaw); });
            getBone("gjr").ifPresent(b -> { size(b, p); b.setPosX(XS * 11.5F * rc * (float) p.jaw); });
            final boolean ready = (be.flags() & BayModuleBlockEntity.FLAG_READY) != 0;
            final boolean linked = (be.flags() & BayModuleBlockEntity.FLAG_LINKED) != 0;
            getBone("scr_on").ifPresent(b -> b.setHidden(!running && !ready));
            getBone("scr_warn").ifPresent(b -> b.setHidden(running || ready));
            getBone("tw_g").ifPresent(b -> b.setHidden(!(ready && !running)));
            getBone("tw_a").ifPresent(b -> b.setHidden(!running || ((long) tt) % 24 > 14));
            getBone("tw_r").ifPresent(b -> b.setHidden(linked));
            final float pulse = 0.8F + 0.2F * (float) Math.sin(tt * 0.3);
            getBone("lamp").ifPresent(b -> { b.setHidden(!running); b.setScaleX(pulse); b.setScaleY(pulse); b.setScaleZ(pulse); });
        }

        private void animateWeld(BayAnim.Pose p, double tt) {
            final float w = (float) p.weld;
            getBone("orb").ifPresent(b -> b.setRotZ((float) p.ring));
            getBone("hw_a1").ifPresent(b -> b.setRotX(w * 0.18F * (float) Math.sin(tt * 0.11)));
            getBone("hw_a2").ifPresent(b -> b.setRotX(w * 0.25F * (float) Math.sin(tt * 0.17 + 1.0)));
            for (int i = 0; i < 2; i++) {
                final int ii = i;
                getBone("fr" + i + "_s").ifPresent(b -> b.setRotY(w * 0.25F * (float) Math.sin(tt * 0.07 + ii * 2.0)));
                getBone("fr" + i + "_a").ifPresent(b -> b.setRotX(w * 0.12F * (float) Math.sin(tt * 0.13 + ii)));
                getBone("fr" + i + "_b").ifPresent(b -> b.setRotX(w * 0.2F * (float) Math.sin(tt * 0.19 + ii * 3.0)));
                getBone("arc_f" + i).ifPresent(b -> b.setHidden(w < 0.05F || ((long) tt + ii) % 4 == 0));
            }
            getBone("arc_o").ifPresent(b -> b.setHidden(w < 0.05F || ((long) tt) % 5 == 0));
            getBone("arc_h").ifPresent(b -> b.setHidden(w < 0.05F || ((long) tt) % 6 == 0));
            final float pulse = 0.8F + 0.2F * (float) Math.sin(tt * 0.4);
            getBone("lamp").ifPresent(b -> { b.setHidden(w < 0.05F); b.setScaleX(pulse); b.setScaleY(pulse); b.setScaleZ(pulse); });
        }

        private boolean running(BayModuleBlockEntity be) {
            BayModuleBlockEntity c = be.core();
            return c != null && c.stage() != BayModuleBlockEntity.Stage.IDLE;
        }

        private void animateCrane(BayModuleBlockEntity be, BayAnim.Pose p, double tt) {
            final boolean run = running(be);
            final BayModuleBlockEntity c = be.core();
            final float lc = (float) BayCalibre.len(p.cal), rc = (float) BayCalibre.rad(p.cal), lift = (float) BayCalibre.lift(p.cal);
            getBone("part1").ifPresent(b -> { b.setHidden(c == null || (c.stockMask() >> 1 & 1) == 0); size(b, p); b.setPosY(lift); });
            getBone("held1").ifPresent(b -> { b.setHidden(p.held != 1); size(b, p); b.setPosZ((float) BayAnim.CR_GRIP * (lc - 1F)); });
            getBone("crb").ifPresent(b -> b.setPosZ((float) p.craneZ));
            getBone("trol").ifPresent(b -> b.setPosX(XS * (float) p.trolX));
            getBone("hook").ifPresent(b -> b.setPosY((float) p.hookDy));
            getBone("rope").ifPresent(b -> b.setScaleY(1.0F + (float) (-p.hookDy / 52.0)));
            getBone("tq").ifPresent(b -> b.setPosX(XS * (18F * (1.0F - (float) p.tq) + (float) BayAnim.toolOut(p.cal))));
            getBone("hjl").ifPresent(b -> { size(b, p); b.setPosX(XS * -11.5F * rc * (float) p.jaw); });
            getBone("hjr").ifPresent(b -> { size(b, p); b.setPosX(XS * 11.5F * rc * (float) p.jaw); });
            getBone("tqs").ifPresent(b -> b.setRotX((float) p.tqSpin));
            getBone("cbc").ifPresent(b -> { b.setHidden(!run); b.setRotY((float) (tt * 0.25)); });
            final float pulse = 0.8F + 0.2F * (float) Math.sin(tt * 0.3);
            getBone("lamp").ifPresent(b -> { b.setHidden(!run); b.setScaleX(pulse); b.setScaleY(pulse); b.setScaleZ(pulse); });
        }

        private void animateWarhead(BayModuleBlockEntity be, BayAnim.Pose p, double tt) {
            final boolean run = running(be);
            final BayModuleBlockEntity c = be.core();
            final float lc = (float) BayCalibre.len(p.cal), rc = (float) BayCalibre.rad(p.cal), lift = (float) BayCalibre.lift(p.cal);
            for (int k = 2; k <= 3; k++) {
                final int kk = k;
                getBone("part" + k).ifPresent(b -> { b.setHidden(c == null || (c.stockMask() >> kk & 1) == 0); size(b, p); b.setPosY(lift); b.setPosZ((float) (BayAnim.whStock(p.cal, kk) - BayAnim.WL_STOCK[kk])); });
                getBone("held" + k).ifPresent(b -> { b.setHidden(p.held != kk); size(b, p); b.setPosZ((float) BayAnim.WL_GRIP[kk] * (lc - 1F)); });
            }
            getBone("wlt").ifPresent(b -> b.setPosZ((float) p.wlz));
            getBone("wla").ifPresent(b -> b.setPosX(XS * (float) p.wlx));
            getBone("wlg").ifPresent(b -> b.setPosY(-(float) p.ext));
            getBone("wlm").ifPresent(b -> b.setScaleY(1.0F + (float) p.ext / 14.2F));
            getBone("wjl").ifPresent(b -> { size(b, p); b.setPosX(XS * -11.5F * rc * (float) p.jaw); });
            getBone("wjr").ifPresent(b -> { size(b, p); b.setPosX(XS * 11.5F * rc * (float) p.jaw); });

            getBone("cl_l").ifPresent(b -> { b.setPosX(XS * -(8F * (1.0F - (float) p.clamp) + 9F * (rc - 1F))); b.setPosY(lift); });
            getBone("cl_r").ifPresent(b -> { b.setPosX(XS * (8F * (1.0F - (float) p.clamp) + 9F * (rc - 1F))); b.setPosY(lift); });
            getBone("bcn").ifPresent(b -> { b.setHidden(!run); b.setRotY((float) (tt * 0.25)); });
            getBone("lamp").ifPresent(b -> b.setHidden(!run));
        }

        private void animateClean(BayModuleBlockEntity be, BayAnim.Pose p, double tt) {
            final boolean run = running(be);

            getBone("hood_l").ifPresent(b -> b.setRotZ(ZS * (float) Math.toRadians(-80 * (1 - p.hood))));
            getBone("hood_r").ifPresent(b -> b.setRotZ(ZS * (float) Math.toRadians(80 * (1 - p.hood))));
            getBone("ra_s").ifPresent(b -> b.setRotY((float) p.rsw));
            getBone("ra_a").ifPresent(b -> b.setRotX((float) p.ra));
            getBone("ra_b").ifPresent(b -> b.setRotX((float) p.rb));
            getBone("ra_w").ifPresent(b -> b.setRotX((float) p.rw));
            getBone("scn").ifPresent(b -> { b.setHidden(p.scan < 0); b.setPosZ(-40F + 80F * (float) Math.max(0, p.scan)); });
            getBone("ph").ifPresent(b -> b.setPosY(26F * (float) p.hatch));
            getBone("lid").ifPresent(b -> b.setRotY(run ? (float) (tt * 0.12) : 0F));
            getBone("part2").ifPresent(b -> b.setHidden(true));
            getBone("held2").ifPresent(b -> b.setHidden(true));
            getBone("lamp").ifPresent(b -> b.setHidden(!run));
        }

        private void animatePaint(BayModuleBlockEntity be, BayAnim.Pose p, double tt) {
            final boolean run = running(be);
            getBone("pr0").ifPresent(b -> b.setRotZ((float) p.ringP));
            getBone("pr1").ifPresent(b -> b.setRotZ((float) (-p.ringP * 1.2)));
            getBone("pr2").ifPresent(b -> b.setRotZ((float) (p.ringP * 0.8)));
            getBone("gn_l").ifPresent(b -> b.setPosZ((float) p.gz));
            getBone("gn_r").ifPresent(b -> b.setPosZ((float) p.gz));
            getBone("ir").ifPresent(b -> b.setHidden(!p.ir));
            getBone("arch").ifPresent(b -> b.setPosZ((float) (-54.0 * p.arch)));
            getBone("scl").ifPresent(b -> b.setRotZ((float) (Math.PI * 0.5 * p.arch + tt * 0.0)));
            getBone("fan").ifPresent(b -> b.setRotY(run ? (float) (tt * 0.35) : 0F));
            getBone("lamp").ifPresent(b -> b.setHidden(!run));
        }

        private static final float WS = 1F;

        private void animateRack(BayModuleBlockEntity be, BayAnim.Pose p, double tt) {
            final BayModuleBlockEntity c = be.core();
            final boolean run = c != null && c.stage() != BayModuleBlockEntity.Stage.IDLE;
            final float lc = (float) BayCalibre.len(p.cal), rc = (float) BayCalibre.rad(p.cal);

            final float s = be.rackSide() == 1 ? -1F : 1F;
            final float off = be.rackSide() == 1 ? 0.4F : 0F;

            getBone("crb").ifPresent(b -> { b.setPosZ((float) p.rbz + off); b.setPosY(off); });
            getBone("trol").ifPresent(b -> b.setPosX(XS * s * (float) p.rtx));
            getBone("hook").ifPresent(b -> b.setPosY((float) p.rdy));
            getBone("rope").ifPresent(b -> b.setScaleY(1.0F + (float) (-p.rdy / 52.0)));
            getBone("hjl").ifPresent(b -> { size(b, p); b.setPosX(XS * s * -0.3F * rc * (float) p.rjaw); });
            getBone("hjr").ifPresent(b -> { size(b, p); b.setPosX(XS * s * 0.3F * rc * (float) p.rjaw); });

            getBone("hold").ifPresent(b -> { b.setHidden(!p.rcarry); b.setPosY((float) (BayAnim.HOLD_Y - BayArt.AXY)); b.setPosZ(-80F * lc); });
            getBone("hm").ifPresent(b -> { b.setScaleX(rc); b.setScaleY(lc); b.setScaleZ(rc); });

            getBone("wheel").ifPresent(b -> b.setRotZ(WS * s * (float) (p.wpos * Math.PI / 3.0)));
            for (int i = 0; i < BayModuleBlockEntity.RACK_SLOTS; i++) {
                final int ii = i;
                final double ang = BayAnim.LOAD_ANG + (i - p.wpos) * Math.PI / 3.0;
                final double gx = BayAnim.HUB_X + BayAnim.RING_R * Math.sin(ang) - BayAnim.LOAD_X, gy = BayAnim.HUB_Y + BayAnim.RING_R * Math.cos(ang) - (BayAnim.HUB_Y + BayAnim.RING_R * Math.cos(BayAnim.LOAD_ANG));
                getBone("gd" + i).ifPresent(b -> { b.setPosX(XS * s * (float) gx); b.setPosY((float) gy); });
                final var stack = be.rackStack(i);
                final boolean filling = c != null && c.storing() && c.storeRack() == be.rackIndex() && c.storeSlot() == i && p.rplaced && c.stage() == BayModuleBlockEntity.Stage.STORE;
                final int cal = Math.min(3, !stack.isEmpty() ? MissileBuild.of(stack).calibre() : p.cal);
                final float sr = (float) BayCalibre.rad(cal), sl = (float) BayCalibre.len(cal);
                getBone("sm" + i).ifPresent(b -> { b.setHidden(stack.isEmpty() && !filling); b.setPosX(XS * s * (float) BayAnim.LOAD_X); b.setPosY((float) (BayAnim.loadAxis(cal) - BayArt.AXY)); b.setPosZ((float) BayAnim.RACK_TAIL); });
                getBone("smm" + i).ifPresent(b -> { b.setScaleX(sr); b.setScaleY(sl); b.setScaleZ(sr); });
            }
            getBone("cbc").ifPresent(b -> { b.setHidden(!run); b.setRotY((float) (tt * 0.25)); });
            getBone("lamp").ifPresent(b -> b.setHidden(!run));
        }

        private void animateCryo(BayModuleBlockEntity be, BayAnim.Pose p, double tt) {
            final boolean run = running(be);
            getBone("lr").ifPresent(b -> b.setRotZ((float) p.lring));
            getBone("lz").ifPresent(b -> b.setHidden(!p.beams || ((long) tt) % 5 == 0));
            getBone("oa_l").ifPresent(b -> b.setPosZ((float) (12.0 * p.oarm)));
            getBone("oa_r").ifPresent(b -> b.setPosZ((float) (-12.0 * p.oarm)));
            getBone("ob_l").ifPresent(b -> b.setRotX((float) p.otilt));
            getBone("ob_r").ifPresent(b -> b.setRotX((float) -p.otilt));
            getBone("valve").ifPresent(b -> b.setRotY(p.cryo > 0.02 ? (float) (tt * 0.1) : 0F));
            getBone("gauge").ifPresent(b -> b.setHidden(!run));
            getBone("lamp").ifPresent(b -> b.setHidden(!run));
        }
    }

    private record Socket(Matrix4f pose, Matrix3f normal, float px, float py, float pz) {}

    private final Map<String, Socket> sockets = new HashMap<>();

    private final Map<String, Socket> meshPoses = new HashMap<>();
    private MeshModel mesh;

    private static boolean isSocket(String n) {
        return n.startsWith("m1_") || n.equals("hm") || n.startsWith("smm")
                || (n.endsWith("_g") && (n.startsWith("part") || n.startsWith("held")));
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

    private static int slotOf(String n) {
        if (n.startsWith("m1_")) {
            String s = n.substring(3);
            for (int k = 0; k < 4; k++) if (BayAnim.SEG[k].equals(s)) return k;
            return 0;
        }
        int k = Character.getNumericValue(n.charAt(4));
        return Math.max(0, Math.min(3, k));
    }

    private void drawSockets(BayModuleBlockEntity be, float pt, MultiBufferSource buffers, int light) {
        BayModuleBlockEntity core = be.core();
        if (core == null) return;
        BayAnim.Pose p = be.kind.isRack() ? be.rackPose(pt) : be.pose(pt);
        for (Map.Entry<String, Socket> e : sockets.entrySet()) {
            String n = e.getKey();
            Socket s = e.getValue();
            PoseStack ps = new PoseStack();
            ps.last().pose().set(s.pose());
            ps.last().normal().set(s.normal());
            ps.translate(s.px() / 16.0F, s.py() / 16.0F, s.pz() / 16.0F);
            if (n.equals("hm") || n.startsWith("smm")) {

                MissileBuild b = core.build();
                int cal = p.cal;
                if (n.startsWith("smm")) {
                    var stack = be.rackStack(Character.getNumericValue(n.charAt(3)));
                    if (!stack.isEmpty()) { b = MissileBuild.of(stack); cal = b.calibre(); }
                }
                boolean big = n.startsWith("smm") && cal > 3;
                unscale(ps, Math.min(3, cal));
                if (big) ps.scale(0.8F, 0.8F, 0.8F);
                MissileParts.render(b, true, ps, buffers, light, pt);
            } else {

                int k = slotOf(n);
                MissileBuild b = n.startsWith("part") ? core.buildFromParts() : core.build();
                float coat = n.startsWith("m1_") ? MissileParts.coverage(p.cal, k, core.paintProgress(pt)) : 0.0F;
                ps.translate(0.0F, MissileParts.BASE[k] / 16.0F, 0.0F);
                unscale(ps, p.cal);
                MissileParts.renderPart(b, k, coat, ps, buffers, light, pt);
            }
        }
    }

    private static void unscale(PoseStack ps, int cal) {
        float r = (float) BayCalibre.rad(cal), l = (float) BayCalibre.len(cal);
        ps.scale(1.0F / r, 1.0F / l, 1.0F / r);
    }

    public BayRenderer() {
        super(new BayModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(BayModuleBlockEntity be) {
        int r = be.kind == BayKind.CORE ? 128 : be.kind.isRack() ? 32 : 14;
        return new AABB(be.getBlockPos()).inflate(r, 8, r);
    }

    @Override
    public int getViewDistance() { return 160; }

    @Override
    public boolean shouldRenderOffScreen(BayModuleBlockEntity be) { return be.kind == BayKind.CORE; }

    @Override
    public void render(BayModuleBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        int light = packedLight;
        if (level != null) {
            int a = LevelRenderer.getLightColor(level, be.getBlockPos().above(2));
            light = LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(packedLight)), Math.max(LightTexture.sky(a), LightTexture.sky(packedLight)));
        }
        sockets.clear();
        meshPoses.clear();
        mesh = MeshModel.named(modelName(be));
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
