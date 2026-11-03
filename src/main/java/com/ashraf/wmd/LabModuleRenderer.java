package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class LabModuleRenderer extends GeoBlockRenderer<LabModuleBlockEntity> {

    private static final float XS = -1F;

    private static final float WS = 1F;

    private static final float STEP_PX = 80F;

    private static final double LAB_RADIUS = 6.5;

    private static float labScale(int cal) { return (float) Math.min(0.7, LAB_RADIUS / (9.9 * BayCalibre.rad(cal))); }

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    private static boolean shown(LabAnim.Pose p, LabKind who, LabAnim.Item it) { return p != null && p.active == who && p.cell == it; }

    static class LabModel extends GeoModel<LabModuleBlockEntity> {
        @Override public ResourceLocation getModelResource(LabModuleBlockEntity be) { return rl("geo/block/lab_" + be.kind().id() + ".geo.json"); }
        @Override public ResourceLocation getTextureResource(LabModuleBlockEntity be) { return rl("textures/block/lab_" + be.kind().id() + ".png"); }
        @Override public ResourceLocation getAnimationResource(LabModuleBlockEntity be) { return rl("animations/block/lab_" + be.kind().id() + ".animation.json"); }

        @Override
        public void setCustomAnimations(LabModuleBlockEntity be, long instanceId, AnimationState<LabModuleBlockEntity> state) {
            if (be.getLevel() == null) return;
            final float pt = state.getPartialTick();
            final LabModuleBlockEntity core = be.kind() == LabKind.CORE ? be : LabLine.coreOf(be);
            final LabAnim.Pose p = core == null ? null : core.pose(pt);
            final double tt = be.getLevel().getGameTime() + pt;
            final boolean active = p != null && p.active == be.kind();
            final double f = p == null ? 0 : p.f;
            getBone("lamp_run").ifPresent(b -> b.setHidden(!active));
            switch (be.kind()) {
                case CORE -> {
                    boolean ready = core != null && core.stage() == LabModuleBlockEntity.Stage.IDLE && LabLine.complete(core);
                    getBone("lamp_ok").ifPresent(b -> b.setHidden(!ready));
                    final LabAnim.Pose q = p == null ? new LabAnim.Pose() : p;
                    getBone("w_ing").ifPresent(b -> b.setHidden(!shown(q, LabKind.CORE, LabAnim.Item.INGOTS)));
                    getBone("car").ifPresent(b -> b.setPosX(XS * -STEP_PX * (float) q.car));
                    getBone("arm").ifPresent(b -> b.setPosZ(-(float) q.reach * (float) q.arm));
                    getBone("bar").ifPresent(b -> b.setScaleZ(1.0F + (float) (q.reach / 4.0) * (float) q.arm));
                    getBone("c_ing").ifPresent(b -> b.setHidden(q.carry != LabAnim.Item.INGOTS));
                    getBone("c_plate").ifPresent(b -> b.setHidden(q.carry != LabAnim.Item.PLATES));
                    getBone("c_shell").ifPresent(b -> b.setHidden(q.carry != LabAnim.Item.SHELL));
                    getBone("c_chg").ifPresent(b -> b.setHidden(q.carry != LabAnim.Item.CHARGED));
                    getBone("whs").ifPresent(b -> b.setHidden(q.carry != LabAnim.Item.WARHEAD));
                }
                case PRESS -> {
                    final LabAnim.Pose q = p;
                    getBone("ram").ifPresent(b -> b.setPosY((float) -(7.0 * (active ? stroke(f) : 0.0))));
                    getBone("w_ing").ifPresent(b -> b.setHidden(!shown(q, LabKind.PRESS, LabAnim.Item.INGOTS)));
                    getBone("w_plate").ifPresent(b -> b.setHidden(!shown(q, LabKind.PRESS, LabAnim.Item.PLATES)));
                    getBone("w_shell").ifPresent(b -> b.setHidden(!shown(q, LabKind.PRESS, LabAnim.Item.SHELL)));
                }
                case FILLER -> {
                    final LabAnim.Pose q = p;
                    boolean shell = shown(q, LabKind.FILLER, LabAnim.Item.SHELL), chg = shown(q, LabKind.FILLER, LabAnim.Item.CHARGED);
                    double level = chg ? Mth.clamp((f - 0.30) / 0.40, 0.0, 1.0) : 0.0;
                    boolean pouring = chg && f >= 0.30 && f < 0.70;
                    getBone("w_shell").ifPresent(b -> b.setHidden(!(shell || chg)));
                    getBone("w_fill").ifPresent(b -> { b.setHidden(!chg || level < 0.02); b.setScaleY((float) Math.max(0.05, level)); });
                    getBone("stream").ifPresent(b -> b.setHidden(!pouring));
                    getBone("valve").ifPresent(b -> b.setRotY(pouring ? (float) (tt * 0.2) : 0F));
                    getBone("level").ifPresent(b -> b.setScaleY(active ? (float) (1.0 - 0.3 * Mth.clamp((f - 0.30) / 0.40, 0.0, 1.0)) : 1.0F));
                }
                case OVEN -> {
                    final LabAnim.Pose q = p;
                    boolean chg = shown(q, LabKind.OVEN, LabAnim.Item.CHARGED);
                    double lid = chg ? 9.0 * (1.0 - LabAnim.ease((f - 0.30) / 0.15)) : 0.0;
                    boolean soldering = chg && f >= 0.45;
                    double u = Mth.clamp((f - 0.45) / 0.30, 0.0, 1.0);
                    getBone("w_chg").ifPresent(b -> b.setHidden(!chg));
                    getBone("lid").ifPresent(b -> { b.setHidden(!chg); b.setPosY((float) lid); });
                    getBone("torch").ifPresent(b -> b.setPosX(XS * (float) (soldering ? -13.0 + 26.0 * u : -13.0)));
                    getBone("spark").ifPresent(b -> b.setHidden(!soldering || (((long) tt) & 1) == 0));
                    getBone("coil").ifPresent(b -> { float g = active ? 0.85F + 0.15F * (float) Math.sin(tt * 0.3) : 0.4F; b.setScaleX(g); b.setScaleZ(g); b.setScaleY(g); });
                    getBone("ow").ifPresent(b -> b.setHidden(!shown(q, LabKind.OVEN, LabAnim.Item.WARHEAD)));
                }
                case STORAGE -> {
                    final double deg = p == null ? be.angleNow(tt) : p.wheelDeg;
                    getBone("wheel").ifPresent(b -> b.setRotZ(WS * (float) Math.toRadians(deg)));
                    for (int i = 0; i < LabModuleBlockEntity.SLOTS; i++) {
                        final int ii = i;
                        boolean fresh = p != null && p.placed && core != null && ii == core.storeSlot() && core.stage() == LabModuleBlockEntity.Stage.STORE;
                        getBone("sw" + i).ifPresent(b -> b.setHidden(be.cradle(ii).isEmpty() && !fresh));
                    }
                }
                default -> { }
            }
            BoneOverrides.apply(this, getModelResource(be));
        }

        private static double stroke(double f) { return Math.max(bump(f, 0.26, 0.42), Math.max(bump(f, 0.42, 0.58), bump(f, 0.58, 0.74))); }

        private static double bump(double f, double a, double b) {
            if (f < a || f > b) return 0.0;
            double m = (a + b) / 2.0;
            return LabAnim.ease(f < m ? (f - a) / (m - a) : (b - f) / (b - m));
        }
    }

    private record Socket(Matrix4f pose, Matrix3f normal, float px, float py, float pz) {}
    private final Map<String, Socket> sockets = new HashMap<>();

    private static boolean isSocket(String n) { return n.equals("whs") || n.equals("ow") || n.startsWith("sw"); }

    private final Map<String, Socket> meshPoses = new HashMap<>();
    private MeshModel mesh;

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

    private static MissileBuild buildOf(WarheadType t, boolean refined) {
        return new MissileBuild(t.tier, t.tier, t.tier, t.tier, t.id, false, false, refined);
    }

    private static MissileBuild buildOf(ItemStack stack) {
        if (stack.getItem() instanceof MissilePartItem w && w.slot() == 3) {
            WarheadType t = WarheadType.of(w.variant());
            return buildOf(t, stack.getOrDefault(WmdComponents.WARHEAD_GRADE.get(), 0) > 0);
        }
        return buildOf(WarheadType.HE, false);
    }

    private void drawSockets(LabModuleBlockEntity be, float pt, MultiBufferSource buffers, int light) {
        LabModuleBlockEntity core = be.kind() == LabKind.CORE ? be : LabLine.coreOf(be);
        for (Map.Entry<String, Socket> e : sockets.entrySet()) {
            String n = e.getKey();
            Socket s = e.getValue();
            MissileBuild b;
            if (n.startsWith("sw")) {
                int i = Character.getNumericValue(n.charAt(2));
                ItemStack stack = be.cradle(i);
                if (!stack.isEmpty()) b = buildOf(stack);
                else if (core != null) b = buildOf(core.type(), core.grade() > 0);
                else continue;
            } else if (core != null) b = buildOf(core.type(), core.grade() > 0);
            else continue;
            PoseStack ps = new PoseStack();
            ps.last().pose().set(s.pose());
            ps.last().normal().set(s.normal());
            ps.translate(s.px() / 16.0F, s.py() / 16.0F, s.pz() / 16.0F);
            float sc = labScale(b.calibre());
            ps.scale(sc, sc, sc);
            if (n.startsWith("sw")) ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
            else {
                if (n.equals("whs") && core != null) ps.mulPose(Axis.YP.rotationDegrees(-90.0F * (float) core.pose(pt).turn));
                ps.mulPose(Axis.ZP.rotationDegrees(90.0F));
            }
            ps.translate(0.0F, -MissileParts.warheadLengthPx(b.calibre()) / 32.0F, 0.0F);
            MissileParts.renderPart(b, 3, 0.0F, ps, buffers, light, pt);
        }
    }

    public LabModuleRenderer() {
        super(new LabModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(LabModuleBlockEntity be) { return new AABB(be.getBlockPos()).inflate(32, 6, 32); }

    @Override
    public int getViewDistance() { return 128; }

    @Override
    public boolean shouldRenderOffScreen(LabModuleBlockEntity be) { return be.kind() == LabKind.CORE; }

    @Override
    public void render(LabModuleBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        int light = packedLight;
        if (level != null) {
            int a = LevelRenderer.getLightColor(level, be.getBlockPos().above(2));
            light = LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(packedLight)), Math.max(LightTexture.sky(a), LightTexture.sky(packedLight)));
        }
        sockets.clear();
        meshPoses.clear();
        mesh = MeshModel.named("lab_" + be.kind().id());
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
