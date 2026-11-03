package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class WarheadReactorRenderer extends GeoBlockRenderer<WarheadReactorBlockEntity> {

    private static final float NEEDLE_SIGN = 1.0F, VALVE_SIGN = -1.0F, KNOB_SIGN = 1.0F, SPIN_SIGN = 1.0F;

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    static class ReactorModel extends GeoModel<WarheadReactorBlockEntity> {
        @Override public ResourceLocation getModelResource(WarheadReactorBlockEntity be) { return rl("geo/block/warhead_reactor.geo.json"); }
        @Override public ResourceLocation getTextureResource(WarheadReactorBlockEntity be) { return rl("textures/block/warhead_reactor.png"); }
        @Override public ResourceLocation getAnimationResource(WarheadReactorBlockEntity be) { return rl("animations/block/warhead_reactor.animation.json"); }

        @Override
        public void setCustomAnimations(WarheadReactorBlockEntity be, long instanceId, AnimationState<WarheadReactorBlockEntity> state) {
            float pt = state.getPartialTick();
            int n = be.gaugeCount(), tier = be.type().tier;
            boolean running = be.isRunning();
            int pulse = (be.clientTicks / 5) % 2;
            for (int i = 0; i < 4; i++) {
                final int k = i;
                boolean active = i < n;
                float value = be.shownGauge[i] + (be.g[i] - be.shownGauge[i]) * 0.35F * pt;
                int st = be.statusOf(be.g[i]);
                getBone("needle" + i).ifPresent(b -> { b.setHidden(!active); b.setRotZ(NEEDLE_SIGN * (float) Math.toRadians((value - 0.5F) * 140.0F)); });
                getBone("zone" + i + "c").ifPresent(b -> b.setHidden(!active));
                getBone("zone" + i + "m").ifPresent(b -> b.setHidden(!active || tier > 2));
                getBone("zone" + i + "o").ifPresent(b -> b.setHidden(!active || tier > 1));
                boolean lamps = active && running;
                getBone("lamp" + i + "g").ifPresent(b -> b.setHidden(!(lamps && st == 0)));
                getBone("lamp" + i + "a").ifPresent(b -> b.setHidden(!(lamps && st == 1 && pulse == 0)));
                getBone("lamp" + i + "r").ifPresent(b -> b.setHidden(!(lamps && st == 2 && (be.clientTicks / 2) % 2 == 0)));
                getBone("valve" + i).ifPresent(b -> b.setRotZ(VALVE_SIGN * (float) Math.toRadians(be.valveAngle[k])));
            }
            LabModuleBlockEntity core = LabLine.coreOf(be);
            boolean shell = core != null && core.pose(pt).active == LabKind.REACTOR && core.pose(pt).cell == LabAnim.Item.CHARGED;
            getBone("w_charged").ifPresent(b -> b.setHidden(!shell));
            getBone("selector").ifPresent(b -> b.setRotY(KNOB_SIGN * (float) Math.toRadians(be.typeIndex() * 30.0F)));
            getBone("button").ifPresent(b -> b.setPosY(running ? -0.9F : 0.0F));
            getBone("runlamp").ifPresent(b -> b.setHidden(!running));
            int lit = Math.min(6, (be.greenTicks() * 6 + Balance.LAB_RUN_TICKS - 1) / Balance.LAB_RUN_TICKS);
            for (int k = 0; k < 6; k++) { final int kk = k; getBone("tower" + k).ifPresent(b -> b.setHidden(kk >= lit)); }
            float glow = running ? 0.75F + 0.25F * (float) Math.sin((be.clientTicks + pt) * 0.4F) : 0.3F;
            getBone("core").ifPresent(b -> { b.setScaleX(glow); b.setScaleY(running ? 1.0F : 0.5F); b.setScaleZ(glow); });
            getBone("stirrer").ifPresent(b -> b.setRotY(SPIN_SIGN * (be.spin + (running ? 0.35F : 0.04F) * pt)));
            for (int k = 0; k < 3; k++) { final int kk = k; getBone("crack" + k).ifPresent(b -> b.setHidden(be.damage() <= kk)); }
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

    private final Map<String, Matrix4f> meshPoses = new HashMap<>();
    private final Map<String, Matrix3f> meshNormals = new HashMap<>();
    private MeshModel mesh;

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (mesh != null && mesh.has(bone.getName())) {
            if (!bone.isHidden()) {
                meshPoses.put(bone.getName(), new Matrix4f(poseStack.last().pose()));
                meshNormals.put(bone.getName(), new Matrix3f(poseStack.last().normal()));
            }
            return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    @Override
    public void render(WarheadReactorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        meshPoses.clear();
        meshNormals.clear();
        mesh = MeshModel.named("warhead_reactor");
        super.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        for (Map.Entry<String, Matrix4f> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue());
            ps.last().normal().set(meshNormals.get(e.getKey()));
            mesh.draw(e.getKey(), ps.last(), bufferSource, packedLight, packedOverlay);
        }
        meshPoses.clear();
        meshNormals.clear();
    }

    public WarheadReactorRenderer() {
        super(new ReactorModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(WarheadReactorBlockEntity be) { return new AABB(be.getBlockPos()).inflate(5, 3, 5); }

    @Override
    public int getViewDistance() { return 128; }
}
