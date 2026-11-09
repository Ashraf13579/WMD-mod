package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class DroneHiveRenderer extends GeoBlockRenderer<DroneHiveBlockEntity> {

    private static ResourceLocation rl(String p) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, p); }

    static float lidOpen(DroneHiveBlockEntity h, int bay, float pt) {
        if (h.getLevel() == null) return 0.0F;
        double t = h.getLevel().getGameTime() + pt - h.lidAt(bay);
        if (t < 0 || t > DroneHiveBlockEntity.LID_TICKS) return 0.0F;
        double f = t < 10 ? t / 10.0 : t > DroneHiveBlockEntity.LID_TICKS - 10 ? (DroneHiveBlockEntity.LID_TICKS - t) / 10.0 : 1.0;
        f = Mth.clamp(f, 0.0, 1.0);
        return (float) (f * f * (3.0 - 2.0 * f));
    }

    static double rotorRun(DroneHiveBlockEntity h, int bay, double tt) {
        double t = tt - h.lidAt(bay), T = DroneHiveBlockEntity.LID_TICKS;
        if (t < 0.0 || t >= T) return -1.0;
        return t - t * t / (2.0 * T);
    }

    static class HiveModel extends GeoModel<DroneHiveBlockEntity> {
        @Override public ResourceLocation getModelResource(DroneHiveBlockEntity be) { return rl("geo/block/drone_hive.geo.json"); }
        @Override public ResourceLocation getTextureResource(DroneHiveBlockEntity be) { return rl("textures/block/drone_hive.png"); }
        @Override public ResourceLocation getAnimationResource(DroneHiveBlockEntity be) { return rl("animations/block/drone_hive.animation.json"); }

        @Override
        public void setCustomAnimations(DroneHiveBlockEntity h, long instanceId, AnimationState<DroneHiveBlockEntity> state) {
            float pt = state.getPartialTick();
            for (int i = 0; i < DroneHiveBlockEntity.BAYS; i++) {
                final int bay = i;
                float side = DroneHiveBlockEntity.PAD[bay][0] > 0 ? -1.0F : 1.0F;
                float open = lidOpen(h, bay, pt);
                getBone("lid" + bay).ifPresent(b -> b.setRotZ(side * (float) Math.toRadians(105.0) * open));
                getBone("ring" + bay).ifPresent(b -> b.setHidden(!h.charging(bay)));
            }
            BoneOverrides.apply(this, getModelResource(h));
        }
    }

    private record Socket(Matrix4f pose, Matrix3f normal, float px, float py, float pz) {}
    private final Map<String, Socket> sockets = new HashMap<>(), meshPoses = new HashMap<>();
    private MeshModel mesh;

    public DroneHiveRenderer() {
        super(new HiveModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        String n = bone.getName();
        if (n.startsWith("bay")) {
            if (!bone.isHidden()) sockets.put(n, new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), bone.getPivotX(), bone.getPivotY(), bone.getPivotZ()));
            return;
        }
        if (mesh != null && mesh.has(n)) {
            if (!bone.isHidden()) meshPoses.put(n, new Socket(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()), 0, 0, 0));
            return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    @Override
    public void render(DroneHiveBlockEntity h, float pt, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        sockets.clear();
        meshPoses.clear();
        mesh = MeshModel.named("drone_hive");
        super.render(h, pt, poseStack, buffers, light, overlay);
        for (Map.Entry<String, Socket> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue().pose());
            ps.last().normal().set(e.getValue().normal());
            mesh.draw(e.getKey(), ps.last(), buffers, light, overlay);
        }
        double tt = h.getLevel() == null ? 0 : h.getLevel().getGameTime() + pt;
        for (Map.Entry<String, Socket> e : sockets.entrySet()) {
            int bay = e.getKey().charAt(3) - '0';
            DroneType t = h.docked(bay);
            if (t == null) continue;
            Socket s = e.getValue();
            PoseStack ps = new PoseStack();
            ps.last().pose().set(s.pose());
            ps.last().normal().set(s.normal());
            ps.translate(s.px() / 16.0F, s.py() / 16.0F, s.pz() / 16.0F);
            double rot = rotorRun(h, bay, tt);
            DroneModuleRenderer.drawDrone(ps, t, DroneAnim.Item.DRONE, true, false, rot >= 0.0, Math.max(0.0, rot), buffers, light);
        }
        sockets.clear();
        meshPoses.clear();
    }
}
