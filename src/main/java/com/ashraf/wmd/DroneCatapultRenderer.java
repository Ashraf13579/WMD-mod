package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class DroneCatapultRenderer extends GeoBlockRenderer<DroneCatapultBlockEntity> {

    private static ResourceLocation rl(String p) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, p); }

    static final double STROKE = 136.0, INCL = Math.toRadians(10.0);

    static double shuttle(DroneCatapultBlockEntity c, float pt) {
        if (c.getLevel() == null) return 0.0;
        double t = c.getLevel().getGameTime() + pt - c.shotAt();
        if (t < 0 || t > BigDrones.THROW_TICKS + DroneCatapultBlockEntity.SHUTTLE_BACK) return 0.0;
        if (t <= BigDrones.THROW_TICKS) { double k = t / BigDrones.THROW_TICKS; return STROKE * k * k; }
        return STROKE * (1.0 - (t - BigDrones.THROW_TICKS) / DroneCatapultBlockEntity.SHUTTLE_BACK);
    }

    static class CatapultModel extends GeoModel<DroneCatapultBlockEntity> {
        @Override public ResourceLocation getModelResource(DroneCatapultBlockEntity be) { return rl("geo/block/drone_catapult.geo.json"); }
        @Override public ResourceLocation getTextureResource(DroneCatapultBlockEntity be) { return rl("textures/block/drone_catapult.png"); }
        @Override public ResourceLocation getAnimationResource(DroneCatapultBlockEntity be) { return rl("animations/block/drone_catapult.animation.json"); }

        @Override
        public void setCustomAnimations(DroneCatapultBlockEntity c, long instanceId, AnimationState<DroneCatapultBlockEntity> state) {
            double d = shuttle(c, state.getPartialTick());
            getBone("shuttle").ifPresent(b -> { b.setPosY((float) (d * Math.sin(INCL))); b.setPosZ((float) (-d * Math.cos(INCL))); });
            getBone("lamp_run").ifPresent(b -> b.setHidden(c.drone() == null));
            BoneOverrides.apply(this, getModelResource(c));
        }
    }

    private record Socket(Matrix4f pose, Matrix3f normal, float px, float py, float pz) {}
    private final Map<String, Socket> meshPoses = new HashMap<>();
    private Socket ct3;
    private MeshModel mesh;

    public DroneCatapultRenderer() {
        super(new CatapultModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        String n = bone.getName();
        if (n.equals("ct3")) {
            ct3 = new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), bone.getPivotX(), bone.getPivotY(), bone.getPivotZ());
            return;
        }
        if (mesh != null && mesh.has(n)) {
            if (!bone.isHidden()) meshPoses.put(n, new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), 0, 0, 0));
            return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    @Override
    public void render(DroneCatapultBlockEntity c, float pt, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        meshPoses.clear();
        ct3 = null;
        mesh = MeshModel.named("drone_catapult");
        super.render(c, pt, poseStack, buffers, light, overlay);
        for (Map.Entry<String, Socket> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue().pose());
            ps.last().normal().set(e.getValue().normal());
            mesh.draw(e.getKey(), ps.last(), buffers, light, overlay);
        }
        DroneType t = c.drone();
        if (t != null && ct3 != null) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(ct3.pose());
            ps.last().normal().set(ct3.normal());
            ps.translate(ct3.px() / 16.0F, ct3.py() / 16.0F, ct3.pz() / 16.0F);
            DroneModuleRenderer.drawDrone(ps, t, DroneAnim.Item.DRONE, true, false, false, 0.0, buffers, light);
        }
        meshPoses.clear();
        ct3 = null;
    }

    @Override
    public AABB getRenderBoundingBox(DroneCatapultBlockEntity c) { return new AABB(c.getBlockPos()).inflate(14, 6, 14); }

    @Override
    public int getViewDistance() { return 128; }
}
