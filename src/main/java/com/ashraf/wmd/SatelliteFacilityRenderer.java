package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class SatelliteFacilityRenderer extends GeoBlockRenderer<SatelliteFacilityBlockEntity> {

    private static final String NAME = "satellite_facility";
    private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "geo/block/" + NAME + ".geo.json");
    static final float XS = -1.0F;

    static final float STAND_X = 0.0F, STAND_Y = 9.0F, STAND_Z = 32.0F;
    static final float HOOK_DROP = 5.2F;

    static float smooth(float k) { k = Mth.clamp(k, 0.0F, 1.0F); return k * k * (3.0F - 2.0F * k); }

    static float span(float p, float a, float b) { return smooth((p - a) / (b - a)); }

    static float deployAt(float p) { return 1.0F - span(p, 0.70F, 0.80F); }

    static class FacilityModel extends RefineryUnitRenderer.UnitModel<SatelliteFacilityBlockEntity> {
        FacilityModel() { super(NAME, 5); }

        @Override
        public void setCustomAnimations(SatelliteFacilityBlockEntity be, long instanceId, AnimationState<SatelliteFacilityBlockEntity> state) {
            super.setCustomAnimations(be, instanceId, state);
            float pt = state.getPartialTick();
            float p = be.build01(pt);
            boolean sat = p >= 0.0F;
            double tt = (be.getLevel() != null ? be.getLevel().getGameTime() : 0) + pt;

            float armIn = sat ? span(p, 0.0F, 0.08F) * (1.0F - span(p, 0.80F, 0.90F)) : 0.0F;
            float work = sat && be.isRunning() ? span(p, 0.08F, 0.12F) * (1.0F - span(p, 0.74F, 0.78F)) : 0.0F;
            final float a0 = (float) Math.toRadians(-40.0F * armIn + work * 14.0F * (float) Math.sin(tt * 0.06));
            final float a1 = (float) Math.toRadians(40.0F * armIn + work * 14.0F * (float) Math.sin(tt * 0.06 + 2.1));
            final float e0 = (float) Math.toRadians(-15.0F * armIn - work * 12.0F * (0.5F + 0.5F * (float) Math.sin(tt * 0.11)));
            final float e1 = (float) Math.toRadians(-15.0F * armIn - work * 12.0F * (0.5F + 0.5F * (float) Math.sin(tt * 0.11 + 1.7)));
            getBone("arm0").ifPresent(b -> b.setRotY(a0));
            getBone("arm1").ifPresent(b -> b.setRotY(a1));
            getBone("arm0b").ifPresent(b -> b.setRotX(e0));
            getBone("arm1b").ifPresent(b -> b.setRotX(e1));

            final float turn = sat ? (float) (Math.PI * 2.0 * span(p, 0.10F, 0.70F)) : 0.0F;
            getBone("stand").ifPresent(b -> b.setRotY(turn));

            final float gx = 2.0F * work * (float) Math.sin(tt * 0.02);
            getBone("gantry").ifPresent(b -> b.setPosX(XS * gx));

            final float close = sat ? span(p, 0.80F, 0.95F) : 0.0F;
            getBone("hook").ifPresent(b -> b.setPosY(-HOOK_DROP * close));
            for (String n : new String[] { "fair_l", "fair_r" }) {
                final float rest = (float) Math.toRadians(n.equals("fair_l") ? -45.0 : 45.0);
                getBone(n).ifPresent(b -> b.setRotZ((b.getInitialSnapshot() != null ? b.getInitialSnapshot().getRotZ() : rest) * (1.0F - close)));
            }
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

    private final Map<String, Matrix4f> meshPoses = new HashMap<>();
    private final Map<String, Matrix3f> meshNormals = new HashMap<>();
    private MeshModel mesh;

    public SatelliteFacilityRenderer() { super(new FacilityModel()); }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (bone.getName().equals("stand")) {
            meshPoses.put("@stand", new Matrix4f(poseStack.last().pose()));
            meshNormals.put("@stand", new Matrix3f(poseStack.last().normal()));
        }
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
    public void render(SatelliteFacilityBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!GeckoLibCache.getBakedModels().containsKey(GEO)) return;
        mesh = MeshModel.named(NAME);
        meshPoses.clear();
        meshNormals.clear();
        super.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        Matrix4f standPose = meshPoses.remove("@stand");
        Matrix3f standNormal = meshNormals.remove("@stand");
        for (Map.Entry<String, Matrix4f> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue());
            ps.last().normal().set(meshNormals.get(e.getKey()));
            mesh.draw(e.getKey(), ps.last(), bufferSource, packedLight, packedOverlay);
        }

        float p = be.build01(partialTick);
        String type = be.runType();
        if (p >= 0.0F && !type.isEmpty() && standPose != null) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(standPose);
            ps.last().normal().set(standNormal);
            ps.translate(STAND_X / 16.0F, STAND_Y / 16.0F, STAND_Z / 16.0F);
            double tt = (be.getLevel() != null ? be.getLevel().getGameTime() : 0) + partialTick;
            float deploy = deployAt(p);
            SatelliteMesh.draw(type, deploy, deploy > 0.5F && SatelliteMesh.blink(tt, be.getBlockPos().asLong() & 15L), ps, bufferSource, packedLight, packedOverlay);
        }
        meshPoses.clear();
        meshNormals.clear();
    }

    @Override
    public AABB getRenderBoundingBox(SatelliteFacilityBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(5, 0, 5).expandTowards(0, 6, 0);
    }

    @Override
    public int getViewDistance() { return 128; }
}
