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

public class InterceptorLauncherRenderer extends GeoBlockRenderer<InterceptorLauncherBlockEntity> {

    private static final float AZ_SIGN = 1.0F, EL_SIGN = 1.0F, CAP_SIGN = 1.0F, OUT_SIGN = 1.0F;

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    static class LauncherModel extends GeoModel<InterceptorLauncherBlockEntity> {
        @Override public ResourceLocation getModelResource(InterceptorLauncherBlockEntity be) { return rl("geo/block/interceptor_launcher.geo.json"); }
        @Override public ResourceLocation getTextureResource(InterceptorLauncherBlockEntity be) { return rl("textures/block/interceptor_launcher.png"); }
        @Override public ResourceLocation getAnimationResource(InterceptorLauncherBlockEntity be) { return rl("animations/block/interceptor_launcher.animation.json"); }

        @Override
        public void setCustomAnimations(InterceptorLauncherBlockEntity be, long instanceId, AnimationState<InterceptorLauncherBlockEntity> state) {
            if (be.getLevel() == null) return;
            final double[] aim = be.drawnAim(state.getPartialTick());
            final double elevation = aim[1], azimuth = aim[0];
            final float ramp = be.shownRamp;
            final double tt = be.getLevel().getGameTime() + state.getPartialTick();
            getBone("turn").ifPresent(b -> b.setRotY(AZ_SIGN * (float) Math.toRadians(azimuth)));
            getBone("pack").ifPresent(b -> b.setRotX(EL_SIGN * (float) Math.toRadians(LauncherGeometry.E0 - elevation)));
            double[] ram = LauncherGeometry.ram(elevation);
            for (String n : new String[] { "L", "R" }) {
                getBone("ram" + n).ifPresent(b -> b.setRotX((float) Math.toRadians(ram[0])));
                getBone("rod" + n).ifPresent(b -> b.setPosY((float) (ram[1] - LauncherGeometry.LR)));
            }

            final int[][] out = { { -1, 0 }, { 1, 0 }, { -1, 1 }, { 1, 1 } };
            for (int i = 0; i < 4; i++) {
                final float stowed = -out[i][0] * (out[i][1] == 0 ? 1 : -1) * 90.0F * (1.0F - Mth.clamp(ramp * 1.6F - 0.1F, 0.0F, 1.0F));
                getBone("out" + i).ifPresent(b -> b.setRotY(OUT_SIGN * (float) Math.toRadians(stowed)));
            }
            for (int i = 0; i < InterceptorLauncherBlockEntity.SLOTS; i++) {
                final int ii = i;
                final float open = (float) LabAnim.ease(be.shownCap[i]) * 110.0F * (i / 4 == 0 ? -1.0F : 1.0F);
                getBone("cap" + i).ifPresent(b -> b.setRotX(CAP_SIGN * (float) Math.toRadians(open)));
                getBone("ic" + i).ifPresent(b -> b.setHidden(be.magazine.getStackInSlot(ii).isEmpty()));
            }
            getBone("dish").ifPresent(b -> b.setRotY(be.energy() > 1_000 ? (float) (tt * 0.07) : 0F));
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

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (bone.getName().startsWith("ic")) {
            if (!bone.isHidden())
                sockets.put(bone.getName(), new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()));
            return;
        }
        if (LauncherMesh.has(bone.getName())) {
            if (!bone.isHidden())
                meshPoses.put(bone.getName(), new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), 0, 0, 0));
            return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    public InterceptorLauncherRenderer() {
        super(new LauncherModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(InterceptorLauncherBlockEntity be) { return new AABB(be.getBlockPos()).inflate(7, 8, 7); }

    @Override
    public int getViewDistance() { return 160; }

    @Override
    public void render(InterceptorLauncherBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        int light = packedLight;
        if (level != null) {
            int a = LevelRenderer.getLightColor(level, be.getBlockPos().above(3));
            light = LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(packedLight)), Math.max(LightTexture.sky(a), LightTexture.sky(packedLight)));
        }
        sockets.clear();
        meshPoses.clear();
        super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        for (Map.Entry<String, Socket> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue().pose());
            ps.last().normal().set(e.getValue().normal());
            LauncherMesh.draw(e.getKey(), ps.last(), bufferSource, light, packedOverlay);
        }
        meshPoses.clear();
        MissileBuild build = MissileBuild.interceptor(1, false);
        float s = (float) LauncherGeometry.SCALE;
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
