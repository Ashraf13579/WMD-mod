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

public class CiwsRenderer extends GeoBlockRenderer<CiwsBlockEntity> {

    private static final float AZ_SIGN = 1.0F, EL_SIGN = 1.0F, SPIN_SIGN = 1.0F;

    private static final int BELT_N = 9;
    private static final double[][] BELT = { { -7.0, 42.0, 16.0 }, { -7.4, 47.0, 13.0 }, { -7.0, 52.0, 10.5 }, { -6.2, 55.5, 8.2 } };

    private static double[] beltAt(double u) {
        double total = 0;
        double[] len = new double[BELT.length - 1];
        for (int i = 0; i < len.length; i++) { len[i] = Math.sqrt(Math.pow(BELT[i + 1][0] - BELT[i][0], 2) + Math.pow(BELT[i + 1][1] - BELT[i][1], 2) + Math.pow(BELT[i + 1][2] - BELT[i][2], 2)); total += len[i]; }
        double d = u * total;
        for (int i = 0; i < len.length; i++) {
            if (d <= len[i] || i == len.length - 1) {
                double f = Math.min(1.0, d / len[i]);
                return new double[] { BELT[i][0] + (BELT[i + 1][0] - BELT[i][0]) * f, BELT[i][1] + (BELT[i + 1][1] - BELT[i][1]) * f, BELT[i][2] + (BELT[i + 1][2] - BELT[i][2]) * f };
            }
            d -= len[i];
        }
        return BELT[BELT.length - 1];
    }

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    static class CiwsModel extends GeoModel<CiwsBlockEntity> {
        @Override public ResourceLocation getModelResource(CiwsBlockEntity be) { return rl("geo/block/ciws.geo.json"); }
        @Override public ResourceLocation getTextureResource(CiwsBlockEntity be) { return rl("textures/mesh/ciws_mesh.png"); }
        @Override public ResourceLocation getAnimationResource(CiwsBlockEntity be) { return rl("animations/block/interceptor_launcher.animation.json"); }

        @Override
        public void setCustomAnimations(CiwsBlockEntity be, long instanceId, AnimationState<CiwsBlockEntity> state) {
            if (be.getLevel() == null) return;
            final float[] aim = be.drawnAim(state.getPartialTick());
            getBone("turn").ifPresent(b -> b.setRotY(AZ_SIGN * (float) Math.toRadians(aim[0])));
            getBone("gun").ifPresent(b -> b.setRotX(EL_SIGN * (float) Math.toRadians(aim[1])));
            getBone("barrels").ifPresent(b -> b.setRotZ(SPIN_SIGN * (float) Math.toRadians(be.spin)));
            getBone("barrels").ifPresent(b -> { b.setRotZ(SPIN_SIGN * (float) Math.toRadians(be.spin)); b.setPosZ(be.recoil); });
            getBone("pod").ifPresent(b -> b.setRotY((float) Math.sin(be.podAngle) * 0.9F));

            final float fill = Mth.clamp(be.rounds() / (float) be.drumSize(), 0.0F, 1.0F);
            getBone("ammofill").ifPresent(b -> { b.setHidden(be.rounds() <= 0); b.setScaleY(Math.max(0.08F, fill)); });
            final int shown = be.rounds() <= 0 ? 0 : Math.min(BELT_N, 2 + (int) Math.ceil(fill * (BELT_N - 2)));
            for (int k = 0; k < BELT_N; k++) {
                final int kk = k;
                final float u = (be.beltPhase + (float) k / BELT_N) % 1.0F;
                getBone("belt" + k).ifPresent(b -> {
                    b.setHidden(kk >= shown);
                    double[] q = beltAt(u);
                    b.setPosX(-(float) (q[0] - BELT[0][0])); b.setPosY((float) (q[1] - BELT[0][1])); b.setPosZ((float) (q[2] - BELT[0][2]));
                });
            }
            boolean empty = be.rounds() <= 0, low = be.rounds() < Balance.CIWS_BOX / 2 || be.energy() < Balance.CIWS_FIRE_FE;
            boolean blink = (((be.getLevel().getGameTime()) / 6) & 1) == 0;
            getBone("lamp_g").ifPresent(b -> b.setHidden(low || be.getOwner() == null));
            getBone("lamp_a").ifPresent(b -> b.setHidden(!(low && !empty) || !blink));
            getBone("lamp_r").ifPresent(b -> b.setHidden(!empty && be.energy() >= Balance.CIWS_FIRE_FE));
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

    private record Pose(Matrix4f pose, Matrix3f normal) {}
    private final Map<String, Pose> meshPoses = new HashMap<>();
    private MeshModel mesh;

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (mesh != null && mesh.has(bone.getName())) {
            if (!bone.isHidden()) meshPoses.put(bone.getName(), new Pose(new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal())));
            return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    public CiwsRenderer() { super(new CiwsModel()); }

    @Override
    public AABB getRenderBoundingBox(CiwsBlockEntity be) {
        if (Machines.mount(be.getBlockState()) != net.minecraft.core.Direction.UP) return MountRender.box(be.getBlockPos(), 7);
        return new AABB(be.getBlockPos()).inflate(4, 4, 4).expandTowards(0, 3, 0);
    }

    @Override
    public int getViewDistance() { return 160; }

    @Override
    public void render(CiwsBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        int light = packedLight;
        if (level != null) {
            int a = LevelRenderer.getLightColor(level, be.getBlockPos().above(3));
            light = LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(packedLight)), Math.max(LightTexture.sky(a), LightTexture.sky(packedLight)));
        }
        meshPoses.clear();
        mesh = MeshModel.named("ciws");
        poseStack.pushPose();

        super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        poseStack.popPose();
        for (Map.Entry<String, Pose> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue().pose());
            ps.last().normal().set(e.getValue().normal());
            mesh.draw(e.getKey(), ps.last(), bufferSource, light, packedOverlay);
        }
        Pose pb = meshPoses.get("barrels"), pg = meshPoses.get("gun");
        if (pb != null && pg != null) {
            net.minecraft.world.phys.Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            org.joml.Vector4f m = new org.joml.Vector4f(0.0F, 56.0F / 16.0F, -42.0F / 16.0F, 1.0F), ej = new org.joml.Vector4f(7.0F / 16.0F, 55.0F / 16.0F, 6.5F / 16.0F, 1.0F);
            pb.pose().transform(m); pg.pose().transform(ej);
            be.drawnMuzzle = new net.minecraft.world.phys.Vec3(cam.x + m.x, cam.y + m.y, cam.z + m.z);
            be.drawnEject = new net.minecraft.world.phys.Vec3(cam.x + ej.x, cam.y + ej.y, cam.z + ej.z);
            be.drawnAt = level == null ? 0 : level.getGameTime();
        }
        meshPoses.clear();
    }
}
