package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class TorpedoLineRenderer extends GeoBlockRenderer<TorpedoLineBlockEntity> {

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    private static TorpedoLineBlockEntity coreOf(TorpedoLineBlockEntity be) { return be.kind() == TorpedoLineKind.SHOP ? be : TorpedoLine.coreOf(be); }

    static void hide(GeoModel<?> m, String bone, boolean hidden) { m.getBone(bone).ifPresent(b -> b.setHidden(hidden)); }

    static class LineModel extends GeoModel<TorpedoLineBlockEntity> {
        @Override public ResourceLocation getModelResource(TorpedoLineBlockEntity be) { return rl("geo/block/" + be.kind().block() + ".geo.json"); }
        @Override public ResourceLocation getTextureResource(TorpedoLineBlockEntity be) { return rl("textures/block/" + be.kind().block() + ".png"); }
        @Override public ResourceLocation getAnimationResource(TorpedoLineBlockEntity be) { return rl("animations/block/" + be.kind().block() + ".animation.json"); }

        @Override
        public void setCustomAnimations(TorpedoLineBlockEntity be, long instanceId, AnimationState<TorpedoLineBlockEntity> state) {
            if (be.getLevel() == null) return;
            final float pt = state.getPartialTick();
            final TorpedoLineBlockEntity core = coreOf(be);
            final TorpedoLineAnim.Pose p = core == null ? new TorpedoLineAnim.Pose() : core.pose(pt);
            final double tt = be.getLevel().getGameTime() + pt;
            final boolean active = p.active == be.kind();
            if (core != null && be.kind() != TorpedoLineKind.TEST) {
                final TorpedoLineBlockEntity.Stage want = be.kind() == TorpedoLineKind.SHOP ? TorpedoLineBlockEntity.Stage.BEND : TorpedoLineBlockEntity.Stage.FIT;
                final net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos());
                be.loop = LoopSound.keep(be.loop, be.getLevel().getGameTime(), be.kind() == TorpedoLineKind.SHOP ? WmdSounds.TL_ROLL_LOOP.get() : WmdSounds.TL_FIT_LOOP.get(), Balance.SND_MACHINE, 1.0F, () -> at, () -> !be.isRemoved() && core.stage() == want);
            }
            getBone("runlamp").ifPresent(b -> b.setHidden(!active || ((long) tt) % 20 > 12));
            switch (be.kind()) {
                case SHOP -> {
                    getBone("trolley").ifPresent(b -> b.setPosZ((float) p.trolley));
                    getBone("crane").ifPresent(b -> b.setPosZ((float) p.crane));
                    getBone("hoist").ifPresent(b -> b.setPosX((float) -p.hoistX));
                    getBone("hook").ifPresent(b -> b.setPosY((float) p.hookY));
                    getBone("rope").ifPresent(b -> b.setScaleY((float) Math.max(0.03, (TorpedoLineAnim.HOOK_MAX + 0.4 - p.hookY) / (TorpedoLineAnim.HOOK_MAX + 0.4))));
                    getBone("roll0").ifPresent(b -> b.setRotZ((float) p.rollSpin));
                    getBone("roll1").ifPresent(b -> b.setRotZ((float) (-p.rollSpin * 1.08)));
                    getBone("roll2").ifPresent(b -> b.setRotZ((float) (-p.rollSpin * 1.08)));
                    getBone("plate_feed").ifPresent(b -> { b.setHidden(!p.plateFeedOn); b.setPosX((float) -p.plateFeedX); });
                    hide(this, "plate_hook", p.load != TorpedoLineAnim.Load.PLATE);
                    getBone("torch").ifPresent(b -> b.setPosZ((float) (p.torchZ - TorpedoLineAnim.ROLL_Z)));
                    for (int k = 0; k < 3; k++) hide(this, "stock_plate" + k, k < 3 - p.stockPlates);
                    getBone("cutter").ifPresent(b -> b.setRotZ((float) p.cutterSpin));
                }
                case FITOUT -> {
                    for (int a = 0; a < 2; a++) {
                        double[] r = TorpedoLineAnim.ik(a, p.wrist[a]);
                        String n = a == 0 ? "a1" : "a2";
                        getBone(n + "_yaw").ifPresent(b -> b.setRotY((float) -r[0]));
                        getBone(n + "_sh").ifPresent(b -> { b.setRotX(0.0F); b.setRotZ((float) r[1]); });
                        getBone(n + "_el").ifPresent(b -> { b.setRotX(0.0F); b.setRotZ((float) r[2]); });
                        getBone(n + "_wr").ifPresent(b -> b.setRotZ((float) r[3]));
                        final float g = (float) Math.toRadians(16.0 * p.grip[a]);
                        getBone(n + "_f1").ifPresent(b -> b.setRotX(g));
                        getBone(n + "_f2").ifPresent(b -> b.setRotX(-g));
                    }
                    hide(this, "a1_cell", !p.cellCarried);
                    hide(this, "stock_cell", !p.stockCell);
                    hide(this, "stock_warhead", !p.stockWarhead);
                    hide(this, "stock_nose", !p.stockNose);
                    hide(this, "stock_tail", !p.stockTail);
                }
                case TEST -> {
                    getBone("jaw_l").ifPresent(b -> b.setPosX((float) (-2.4 * p.jaws)));
                    getBone("jaw_r").ifPresent(b -> b.setPosX((float) (2.4 * p.jaws)));
                    getBone("bubbles").ifPresent(b -> b.setHidden(!p.bubbling || ((long) tt) % 6 > 3));
                }
            }
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

    private record Socket(Matrix4f pose, Matrix3f normal, float px, float py, float pz) {}
    private final Map<String, Socket> sockets = new HashMap<>();
    private final Map<String, Socket> meshPoses = new HashMap<>();
    private MeshModel mesh;

    private static boolean isSocket(String n) {
        return n.equals("base") || n.equals("trolley") || n.equals("hook") || n.equals("formed") || n.equals("a1_tip") || n.equals("a2_tip") || n.startsWith("slot");
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (isSocket(bone.getName()))
            sockets.put(bone.getName(), new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()));
        if (mesh != null && mesh.has(bone.getName())) {
            if (!bone.isHidden()) meshPoses.put(bone.getName(), new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), 0, 0, 0));
            return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    private static PoseStack at(Socket s, double dx, double dy, double dz) {
        PoseStack ps = new PoseStack();
        ps.last().pose().set(s.pose());
        ps.last().normal().set(s.normal());
        ps.translate((s.px() + dx) / 16.0F, (s.py() + dy) / 16.0F, (s.pz() + dz) / 16.0F);
        return ps;
    }

    private static PoseStack atTip(Socket base, Socket tip, double dx, double dy, double dz) {
        Vector3f w = tip.pose().transformPosition(new Vector3f(tip.px() / 16.0F, tip.py() / 16.0F, tip.pz() / 16.0F));
        Vector3f local = new Matrix4f(base.pose()).invert().transformPosition(w);
        PoseStack ps = new PoseStack();
        ps.last().pose().set(base.pose());
        ps.last().normal().set(base.normal());
        ps.translate(local.x() + dx / 16.0F, local.y() + dy / 16.0F, local.z() + dz / 16.0F);
        return ps;
    }

    private static int[] tint(TorpedoKind k, double paint) {
        if (!k.tinted()) return new int[] { 255, 255, 255 };
        int[] a = MissileParts.tint(0), b = MissileParts.tint(k.paint);
        return new int[] { (int) (a[0] + (b[0] - a[0]) * paint), (int) (a[1] + (b[1] - a[1]) * paint), (int) (a[2] + (b[2] - a[2]) * paint) };
    }

    private static void drawPiece(PoseStack ps, TorpedoKind k, TorpedoLineAnim.Piece piece, int[] t, double spin, MultiBufferSource buffers, int light) {
        MeshModel m = MeshModel.named(k.mesh());
        if (!m.has("part")) return;
        double[] r = TorpedoLineAnim.range(k, piece);
        double c = (r[0] + r[1]) / 2.0;
        if (spin != 0.0) ps.mulPose(Axis.ZP.rotation((float) spin));
        ps.translate(0.0F, -k.axisPx() / 16.0F, (float) (-c / 16.0));
        m.drawTintedZ("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], (float) (r[0] / 16.0), (float) (r[1] / 16.0));
    }

    private static void drawAssembly(PoseStack base, TorpedoKind k, TorpedoLineAnim.Pose p, boolean all, int[] t, MultiBufferSource buffers, int light) {
        for (TorpedoLineAnim.Piece piece : TorpedoLineAnim.Piece.values()) {
            boolean on = all || switch (piece) {
                case NOSE -> p.nose;
                case WARHEAD -> p.warhead;
                case TAIL -> p.tail;
                default -> piece.ordinal() - TorpedoLineAnim.Piece.BODY0.ordinal() < p.sections;
            };
            if (!on) continue;
            PoseStack ps = new PoseStack();
            ps.last().pose().set(base.last().pose());
            ps.last().normal().set(base.last().normal());
            ps.translate(0.0F, 0.0F, (float) (TorpedoLineAnim.centre(k, piece) / 16.0));
            drawPiece(ps, k, piece, t, 0.0, buffers, light);
        }
    }

    private void drawSockets(TorpedoLineBlockEntity be, float pt, MultiBufferSource buffers, int light) {
        TorpedoLineBlockEntity core = coreOf(be);
        TorpedoLineAnim.Pose p = core == null ? null : core.pose(pt);
        TorpedoKind run = core == null ? TorpedoKind.T1 : core.type();
        int[] bare = tint(run, 0.0);

        if (be.kind() == TorpedoLineKind.TEST) {
            for (int i = 0; i < Balance.TLINE_RACK; i++) {
                TorpedoKind k = TorpedoKind.of(be.rackSlot(i));
                if (k == null) continue;
                int at = i >= 2 && TorpedoKind.of(be.rackSlot(i - 2)) == null ? i - 2 : i;
                Socket s = sockets.get("slot" + at);
                if (s != null) drawAssembly(at(s, 0, 0, 0), k, p == null ? new TorpedoLineAnim.Pose() : p, true, tint(k, 1.0), buffers, light);
            }
        }
        if (p == null) return;
        switch (be.kind()) {
            case SHOP -> {
                Socket formed = sockets.get("formed"), trolley = sockets.get("trolley"), hook = sockets.get("hook");
                if (formed != null && p.formedOn)
                    drawPiece(at(formed, 0, 0, p.formedZ - TorpedoLineAnim.ROLL_Z), run, TorpedoLineAnim.Piece.values()[TorpedoLineAnim.Piece.BODY0.ordinal() + p.formedIndex],
                            bare, p.formedZ >= TorpedoLineAnim.ROLL_Z - 0.01 ? p.rollSpin : 0.0, buffers, light);
                if (trolley != null && p.onTrolley) drawAssembly(at(trolley, 0, TorpedoLineAnim.AY - 3.4, 0), run, p, false, bare, buffers, light);
                if (hook != null) {
                    PoseStack h = at(hook, 0, -TorpedoLineAnim.LOAD_DROP, 0);
                    switch (p.load) {
                        case SECTION -> drawPiece(h, run, TorpedoLineAnim.Piece.values()[TorpedoLineAnim.Piece.BODY0.ordinal() + p.loadIndex], bare, 0.0, buffers, light);
                        case WARHEAD -> drawPiece(h, run, TorpedoLineAnim.Piece.WARHEAD, bare, 0.0, buffers, light);
                        case TORPEDO -> drawAssembly(h, run, p, true, tint(run, p.paint), buffers, light);
                        default -> { }
                    }
                }
            }
            case FITOUT -> {
                Socket base = sockets.get("base");
                Socket t1 = sockets.get("a1_tip"), t2 = sockets.get("a2_tip");
                if (base != null && t1 != null && p.noseCarried) drawPiece(atTip(base, t1, 0, -4.0, 0), run, TorpedoLineAnim.Piece.NOSE, bare, 0.0, buffers, light);
                if (base != null && t2 != null && p.tailCarried) drawPiece(atTip(base, t2, 0, -4.0, 0), run, TorpedoLineAnim.Piece.TAIL, bare, 0.0, buffers, light);
            }
            default -> { }
        }
    }

    public TorpedoLineRenderer() {
        super(new LineModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(TorpedoLineBlockEntity be) {
        return be.kind() == TorpedoLineKind.SHOP ? new AABB(be.getBlockPos()).inflate(22, 6, 22) : new AABB(be.getBlockPos()).inflate(8, 4, 8);
    }

    @Override
    public int getViewDistance() { return 128; }

    @Override
    public boolean shouldRenderOffScreen(TorpedoLineBlockEntity be) { return be.kind() == TorpedoLineKind.SHOP; }

    @Override
    public void render(TorpedoLineBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
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
