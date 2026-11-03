package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class NuclearReactorRenderer extends GeoBlockRenderer<NuclearReactorBlockEntity> {

    private static final String NAME = "nuclear_reactor";
    private static final float CRANE_DROP_PX = 24.0F, VENT_OPEN_RAD = (float) Math.toRadians(70.0);
    private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "geo/block/" + NAME + ".geo.json");

    static class ReactorModel extends RefineryUnitRenderer.UnitModel<NuclearReactorBlockEntity> {
        ReactorModel() { super(NAME, 10); }

        @Override
        public void setCustomAnimations(NuclearReactorBlockEntity be, long instanceId, AnimationState<NuclearReactorBlockEntity> state) {
            super.setCustomAnimations(be, instanceId, state);
            float pt = state.getPartialTick();
            double tt = (be.getLevel() != null ? be.getLevel().getGameTime() : 0) + pt;
            int stage = be.stage();
            getBone("rings").ifPresent(b -> b.setRotY(be.ringAngle(pt)));
            getBone("shell").ifPresent(b -> b.setRotY(-0.6F * be.ringAngle(pt)));
            getBone("rods").ifPresent(b -> b.setPosY(be.rodPx(pt)));
            final float vent = -VENT_OPEN_RAD * be.ventOpen(pt);
            for (int i = 0; i < 4; i++) getBone("vent" + i).ifPresent(b -> b.setRotX(vent));
            final float spin = (float) (tt * (stage >= NuclearReactorBlockEntity.IMMINENT ? 0.6 : 0.3));
            for (int i = 0; i < 4; i++) getBone("beacon" + i).ifPresent(b -> { b.setHidden(stage < NuclearReactorBlockEntity.ALARM); b.setRotY(spin); });
            float cp = be.craneProgress(pt);
            getBone("crane").ifPresent(b -> b.setPosY(cp < 0 ? 0.0F : -CRANE_DROP_PX * (float) Math.sin(Math.PI * cp)));
            final float pump = be.spinAngle(pt) * 1.5F;
            for (int i = 0; i < 4; i++) getBone("pump" + i).ifPresent(b -> b.setRotY(pump));
            for (int k = 1; k <= 3; k++) { final int kk = k; getBone("heat" + k).ifPresent(b -> b.setHidden(stage < kk)); }
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

    private final Map<String, Matrix4f> meshPoses = new HashMap<>();
    private final Map<String, Matrix3f> meshNormals = new HashMap<>();
    private MeshModel mesh;

    public NuclearReactorRenderer() {
        super(new ReactorModel());
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
    public void render(NuclearReactorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!GeckoLibCache.getBakedModels().containsKey(GEO)) return;
        if (!be.isFormed()) return;

        int light = be.getLevel() != null ? Math.max(packedLight, LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(be.getBlockState().getValue(NuclearReactorBlock.FACING)))) : packedLight;
        mesh = MeshModel.named(NAME);
        meshPoses.clear();
        meshNormals.clear();
        poseStack.pushPose();
        poseStack.translate(0.0, -ReactorStructure.CTRL_Y, 0.0);
        super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        poseStack.popPose();
        int[] tint = tint(be);
        for (Map.Entry<String, Matrix4f> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue());
            ps.last().normal().set(meshNormals.get(e.getKey()));
            if (e.getKey().equals("heat")) mesh.drawColoured("heat", ps.last(), bufferSource, light, packedOverlay, tint[0], tint[1], tint[2]);
            else mesh.draw(e.getKey(), ps.last(), bufferSource, light, packedOverlay);
        }
        meshPoses.clear();
        meshNormals.clear();
    }

    private static int[] tint(NuclearReactorBlockEntity be) {
        return switch (be.stage()) {
            case NuclearReactorBlockEntity.ALARM -> new int[] { 255, 205, 130 };
            case NuclearReactorBlockEntity.STEAM -> new int[] { 255, 150, 90 };
            case NuclearReactorBlockEntity.IMMINENT -> {
                long t = be.getLevel() != null ? be.getLevel().getGameTime() : 0;
                int g = (t / 5) % 2 == 0 ? 70 : 30;
                yield new int[] { 255, g, g };
            }
            default -> new int[] { 255, 255, 255 };
        };
    }

    @Override
    public AABB getRenderBoundingBox(NuclearReactorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(9, 0, 9).expandTowards(0, 10, 0).expandTowards(0, -2, 0);
    }

    @Override
    public int getViewDistance() { return 192; }
}
