package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import software.bernie.geckolib.cache.object.GeoBone;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class PumpjackRenderer extends GeoBlockRenderer<PumpjackBlockEntity> {

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    private static final double PY = 84, PZ = 44, LR = 34, CY = 40, CZ = PZ + LR, RC = 9, RARC = 44;
    private static final double R0Y = PY, R0Z = PZ + LR, Q0Y = CY, Q0Z = CZ + RC;
    private static final double LP = Math.hypot(R0Y - Q0Y, R0Z - Q0Z);

    static class PumpModel extends GeoModel<PumpjackBlockEntity> {
        @Override public ResourceLocation getModelResource(PumpjackBlockEntity be) { return rl("geo/block/pumpjack.geo.json"); }
        @Override public ResourceLocation getTextureResource(PumpjackBlockEntity be) { return rl("textures/block/pumpjack.png"); }
        @Override public ResourceLocation getAnimationResource(PumpjackBlockEntity be) { return rl("animations/block/pumpjack.animation.json"); }

        @Override
        public void setCustomAnimations(PumpjackBlockEntity be, long instanceId, AnimationState<PumpjackBlockEntity> state) {
            double phi = be.crankAngle(state.getPartialTick());
            double qy = CY + RC * Math.cos(Math.PI / 2 + phi), qz = CZ + RC * Math.sin(Math.PI / 2 + phi);

            double dy = qy - PY, dz = qz - PZ, d = Math.hypot(dy, dz);
            double a = (LR * LR - LP * LP + d * d) / (2 * d);
            double h = Math.sqrt(Math.max(0, LR * LR - a * a));
            double ey = dy / d, ez = dz / d;
            double r1y = PY + a * ey - h * ez, r1z = PZ + a * ez + h * ey;
            double r2y = PY + a * ey + h * ez, r2z = PZ + a * ez - h * ey;
            boolean first = Math.hypot(r1y - R0Y, r1z - R0Z) <= Math.hypot(r2y - R0Y, r2z - R0Z);
            double ry = first ? r1y : r2y, rz = first ? r1z : r2z;
            double alpha = Math.atan2(rz - PZ, ry - PY) - Math.PI / 2;
            double delta = Math.atan2(rz - qz, ry - qy) - Math.atan2(R0Z - Q0Z, R0Y - Q0Y);
            getBone("crank").ifPresent(b -> b.setRotX((float) phi));
            getBone("pitman").ifPresent(b -> { b.setPosY((float) (qy - Q0Y)); b.setPosZ((float) (qz - Q0Z)); b.setRotX((float) delta); });
            getBone("beam").ifPresent(b -> b.setRotX((float) alpha));
            getBone("rod").ifPresent(b -> b.setPosY((float) (RARC * alpha)));
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
    public void render(PumpjackBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        mesh = MeshModel.named("pumpjack");
        meshPoses.clear();
        meshNormals.clear();
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

    public PumpjackRenderer() {
        super(new PumpModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(PumpjackBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(8, 0, 8).expandTowards(0, 8, 0);
    }

    @Override
    public int getViewDistance() { return 128; }
}
