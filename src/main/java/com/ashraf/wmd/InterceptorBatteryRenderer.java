package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class InterceptorBatteryRenderer extends GeoBlockRenderer<InterceptorBatteryBlockEntity> {

    private static final float AZ_SIGN = 1.0F, EL_SIGN = 1.0F, CAP_SIGN = 1.0F, OUT_SIGN = 1.0F;

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    static class LauncherModel extends GeoModel<InterceptorBatteryBlockEntity> {
        @Override public ResourceLocation getModelResource(InterceptorBatteryBlockEntity be) { return rl("geo/block/battery_t2.geo.json"); }
        @Override public ResourceLocation getTextureResource(InterceptorBatteryBlockEntity be) { return rl("textures/mesh/battery_t2_mesh.png"); }
        @Override public ResourceLocation getAnimationResource(InterceptorBatteryBlockEntity be) { return rl("animations/block/interceptor_launcher.animation.json"); }

        @Override
        public void setCustomAnimations(InterceptorBatteryBlockEntity be, long instanceId, AnimationState<InterceptorBatteryBlockEntity> state) {
            if (be.getLevel() == null) return;
            final double[] aim = be.drawnAim(state.getPartialTick());
            final double elevation = aim[1], azimuth = aim[0];
            final float ramp = be.shownRamp;
            final double tt = be.getLevel().getGameTime() + state.getPartialTick();
            getBone("turn").ifPresent(b -> b.setRotY(AZ_SIGN * (float) Math.toRadians(azimuth)));
            getBone("pack").ifPresent(b -> b.setRotX(EL_SIGN * (float) Math.toRadians(BatteryGeometry.E0 - elevation)));
            double[] ram = BatteryGeometry.ram(elevation);
            for (String n : new String[] { "L", "R" }) {
                getBone("ram" + n).ifPresent(b -> b.setRotX((float) Math.toRadians(ram[0])));
                getBone("rod" + n).ifPresent(b -> b.setPosY((float) (ram[1] - BatteryGeometry.LR)));
            }
            for (int i = 0; i < InterceptorBatteryBlockEntity.SLOTS; i++) {
                final int ii = i;
                final float open = (float) LabAnim.ease(be.shownCap[i]) * 110.0F * (i / 2 == 0 ? -1.0F : 1.0F);
                getBone("cap" + i).ifPresent(b -> b.setRotX(CAP_SIGN * (float) Math.toRadians(open)));
                getBone("ic" + i).ifPresent(b -> b.setHidden(be.magazine.getStackInSlot(ii).isEmpty()));
            }
            boolean empty = be.loaded() == 0, low = be.energy() < Balance.INT_SHOT_FE, reloading = be.cooldownTicks() > 0, moving = ramp > 0.02F && ramp < 0.98F;
            boolean blink = (((long) tt / 6) & 1) == 0;
            getBone("lamp_g").ifPresent(b -> b.setHidden(empty || low || reloading || be.getOwner() == null));
            getBone("lamp_a").ifPresent(b -> b.setHidden(!(reloading || (moving && blink))));
            getBone("lamp_r").ifPresent(b -> b.setHidden(!(empty || low) || (!blink && ramp > 0.5F)));
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

    private record Socket(Matrix4f pose, Matrix3f normal, float px, float py, float pz) {}
    private final Map<String, Socket> sockets = new HashMap<>();

    private final Map<String, Socket> meshPoses = new HashMap<>();
    private final MeshModel mesh = MeshModel.named("battery_t2");

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (bone.getName().startsWith("ic")) {
            if (!bone.isHidden())
                sockets.put(bone.getName(), new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()));
            return;
        }
        if (mesh.has(bone.getName())) {
            if (!bone.isHidden())
                meshPoses.put(bone.getName(), new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), 0, 0, 0));
            return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    public InterceptorBatteryRenderer() {
        super(new LauncherModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(InterceptorBatteryBlockEntity be) { return new AABB(be.getBlockPos()).inflate(9, 12, 9); }

    @Override
    public int getViewDistance() { return 160; }

    @Override
    public void render(InterceptorBatteryBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        int light = packedLight;
        if (level != null) {
            int a = LevelRenderer.getLightColor(level, be.getBlockPos().above(3));
            light = LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(packedLight)), Math.max(LightTexture.sky(a), LightTexture.sky(packedLight)));
        }
        sockets.clear();
        meshPoses.clear();
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.scale((float) BatteryGeometry.F, (float) BatteryGeometry.F, (float) BatteryGeometry.F);
        poseStack.translate(-0.5, 0.0, -0.5);
        super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        poseStack.popPose();
        for (Map.Entry<String, Socket> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue().pose());
            ps.last().normal().set(e.getValue().normal());
            mesh.draw(e.getKey(), ps.last(), bufferSource, light, packedOverlay);
        }
        meshPoses.clear();
        MissileBuild build = MissileBuild.interceptor(2, false);
        float s = (float) BatteryGeometry.SCALE;
        for (Socket k : sockets.values()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(k.pose());
            ps.last().normal().set(k.normal());
            ps.translate(k.px() / 16.0F, k.py() / 16.0F, k.pz() / 16.0F);
            ps.scale(s, s, s);
            MissileParts.render(build, true, ps, bufferSource, light, partialTick);
        }
        sockets.clear();
    }
}
