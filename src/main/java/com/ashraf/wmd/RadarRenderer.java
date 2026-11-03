package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class RadarRenderer extends GeoBlockRenderer<RadarBlockEntity> {

    private static final class MeshPart extends GeoBlockRenderer<RadarBlockEntity> {
        private final String name;
        private final Map<String, Matrix4f> poses = new HashMap<>();
        private final Map<String, Matrix3f> normals = new HashMap<>();

        MeshPart(String name) {
            super(new DefaultedBlockGeoModel<RadarBlockEntity>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, name)) {
                @Override
                public void setCustomAnimations(RadarBlockEntity be, long instanceId, software.bernie.geckolib.animation.AnimationState<RadarBlockEntity> state) {
                    super.setCustomAnimations(be, instanceId, state);
                    BoneOverrides.apply(this, getModelResource(be));
                }
            });
            this.name = name;
        }

        @Override
        public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
            MeshModel mesh = MeshModel.named(name);
            if (mesh != null && mesh.has(bone.getName())) {
                if (!bone.isHidden()) {
                    poses.put(bone.getName(), new Matrix4f(poseStack.last().pose()));
                    normals.put(bone.getName(), new Matrix3f(poseStack.last().normal()));
                }
                return;
            }
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
        }

        void draw(RadarBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            poses.clear();
            normals.clear();
            super.render(be, partialTick, poseStack, buffers, light, overlay);
            MeshModel mesh = MeshModel.named(name);
            if (mesh == null) return;
            for (Map.Entry<String, Matrix4f> e : poses.entrySet()) {
                PoseStack ps = new PoseStack();
                ps.last().pose().set(e.getValue());
                ps.last().normal().set(normals.get(e.getKey()));
                mesh.draw(e.getKey(), ps.last(), buffers, light, overlay);
            }
            poses.clear();
            normals.clear();
        }
    }

    private final MeshPart baseModel = new MeshPart("radar_base");
    private final MeshPart turretModel = new MeshPart("radar_turret");

    public RadarRenderer() {
        super(new DefaultedBlockGeoModel<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "radar_base")));
    }

    @Override
    public AABB getRenderBoundingBox(RadarBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 6, p.getY(), p.getZ() - 6, p.getX() + 7, p.getY() + 16, p.getZ() + 7);
    }

    @Override
    public int getViewDistance() { return 200; }

    @Override
    public void render(RadarBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!be.isFormed() || be.getLevel() == null) return;
        int light = sampleLight(be);

        baseModel.draw(be, partialTick, poseStack, bufferSource, light, packedOverlay);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);

        float bearing = be.getShownBearing(partialTick);
        double[] ship = ShipFrame.compassAim(be.getLevel(), be.getBlockPos(), bearing, 0.0, partialTick);
        if (ship != null) bearing = (float) ship[0];
        poseStack.mulPose(Axis.YP.rotationDegrees(-bearing));
        poseStack.translate(-0.5, 0.0, -0.5);
        turretModel.draw(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        poseStack.popPose();
    }

    private static int sampleLight(RadarBlockEntity be) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        int block = 0, sky = 0;
        BlockPos[] spots = {pos.above(RadarStructure.HEIGHT + 1), pos.offset(7, 2, 0), pos.offset(-7, 2, 0), pos.offset(0, 2, 7), pos.offset(0, 2, -7)};
        for (BlockPos s : spots) {
            int c = LevelRenderer.getLightColor(level, s);
            block = Math.max(block, LightTexture.block(c));
            sky = Math.max(sky, LightTexture.sky(c));
        }
        return LightTexture.pack(block, sky);
    }
}
