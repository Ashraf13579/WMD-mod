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
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class GroundStationRenderer extends GeoBlockRenderer<GroundStationBlockEntity> {

    private static final String NAME = "ground_station";
    private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "geo/block/" + NAME + ".geo.json");
    static final float AZ_SIGN = -1.0F, TILT_SIGN = -1.0F;

    static final float MIN_EL = 30.0F;

    static class StationModel extends RefineryUnitRenderer.UnitModel<GroundStationBlockEntity> {
        StationModel() { super(NAME, 7); }

        @Override
        public void setCustomAnimations(GroundStationBlockEntity be, long instanceId, AnimationState<GroundStationBlockEntity> state) {
            super.setCustomAnimations(be, instanceId, state);
            float pt = state.getPartialTick();
            float facingYaw = (be.getBlockState().getValue(MachineBlock.FACING).toYRot() + 180.0F) % 360.0F;
            float dishAz = be.dishAz(pt), dishEl = be.dishEl(pt);
            double[] ship = ShipFrame.compassAim(be.getLevel(), be.getBlockPos(), dishAz, dishEl, pt);
            if (ship != null) { dishAz = (float) ship[0]; dishEl = (float) ship[1]; }
            final float yaw = AZ_SIGN * (float) Math.toRadians(dishAz - facingYaw);

            final float tilt = TILT_SIGN * (float) Math.toRadians(90.0F - Math.max(MIN_EL, dishEl));
            getBone("dish").ifPresent(b -> b.setRotY(yaw));
            getBone("tilt").ifPresent(b -> b.setRotX(tilt));
            final boolean jam = be.isJamming() && !ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5);
            getBone("jammer").ifPresent(b -> b.setHidden(!jam));
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

    private final Map<String, Matrix4f> meshPoses = new HashMap<>();
    private final Map<String, Matrix3f> meshNormals = new HashMap<>();
    private MeshModel mesh;

    public GroundStationRenderer() {
        super(new StationModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

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
    public void render(GroundStationBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!GeckoLibCache.getBakedModels().containsKey(GEO)) return;
        mesh = MeshModel.named(NAME);
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

    @Override
    public AABB getRenderBoundingBox(GroundStationBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(5, 0, 5).expandTowards(0, 9, 0).expandTowards(0, -1, 0);
    }

    @Override
    public int getViewDistance() { return 192; }
}
