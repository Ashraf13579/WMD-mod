package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class PadRenderer extends GeoBlockRenderer<LaunchTerminalBlockEntity> {

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path);
    }

    static class PadModel extends GeoModel<LaunchTerminalBlockEntity> {
        @Override
        public ResourceLocation getModelResource(LaunchTerminalBlockEntity be) { return rl("geo/block/pad_t" + be.getTier() + ".geo.json"); }

        @Override
        public ResourceLocation getTextureResource(LaunchTerminalBlockEntity be) { return rl("textures/block/launch_pad.png"); }

        @Override
        public ResourceLocation getAnimationResource(LaunchTerminalBlockEntity be) { return rl("animations/block/launch_pad.animation.json"); }

        @Override
        public void setCustomAnimations(LaunchTerminalBlockEntity be, long instanceId, AnimationState<LaunchTerminalBlockEntity> state) {
            float pt = state.getPartialTick();
            float hatch = be.hatchOpen(pt);
            float arms = be.armsOpen(pt);
            float half = PadGeometry.hatchHalf(be.getTier());

            getBone("hatch_l").ifPresent(b -> b.setPosX(hatch * (half + 1.0F)));
            getBone("hatch_r").ifPresent(b -> b.setPosX(-hatch * (half + 1.0F)));
            final float spread = be.getTier() >= 4 ? 2.2F : 1.6F;
            for (int i = 0; i < 6; i++) {
                final int k = i;

                float a = be.crewRocket() && k >= 4 ? 1.0F : Mth.clamp(arms * spread - k * 0.2F, 0.0F, 1.0F);
                getBone("arm_" + i).ifPresent(b -> b.setRotY(a * 80.0F * Mth.DEG_TO_RAD * (k % 2 == 0 ? 1 : -1)));
            }
            getBone("hose").ifPresent(b -> b.setHidden(!be.hoseConnected()));
            if (be.getTier() >= 4) {
                getBone("hose_fuel").ifPresent(b -> b.setHidden(!be.hoseConnected()));
                getBone("hose_ox").ifPresent(b -> b.setHidden(!be.hoseConnected()));

                var st = be.getStage();
                float rel = st == LaunchTerminalBlockEntity.Stage.LAUNCHING || st == LaunchTerminalBlockEntity.Stage.COOLDOWN ? 1.0F
                        : st == LaunchTerminalBlockEntity.Stage.COUNTDOWN ? Mth.clamp((be.getStageTicks() + pt - 150.0F) / 20.0F, 0.0F, 1.0F) : 0.0F;
                for (int i = 0; i < 4; i++) {
                    final float sgn = (i == 0 || i == 2) ? -1.0F : 1.0F;
                    getBone("clamp_" + i).ifPresent(b -> b.setRotZ(sgn * rel * 70.0F * Mth.DEG_TO_RAD));
                }
            }
            if (be.getTier() >= 4) {
                final PadRig.State rig = be.getStage() == LaunchTerminalBlockEntity.Stage.LOADING && be.getLoadedBuild().staged() ? PadRig.at(be.progress(pt)) : PadRig.at(0.0);
                getBone("bridge").ifPresent(b -> b.setPosZ((float) (rig.bridgeZ - PadRig.CZ)));
                getBone("trolley").ifPresent(b -> b.setPosX((float) -rig.trolleyX));
                getBone("rope").ifPresent(b -> b.setScaleY((float) Math.max(0.05, rig.rope() / 100.0)));
                getBone("spreader").ifPresent(b -> b.setPosY((float) -(rig.rope() - 100.0)));
                getBone("wring").ifPresent(b -> b.setPosY((float) (rig.ringY - PadRig.PARK_RING)));
                getBone("wcar").ifPresent(b -> b.setRotY((float) Math.toRadians(rig.ringAngle)));
                getBone("weld0").ifPresent(b -> { b.setPosX((float) rig.weldE0); b.setPosY((float) rig.weldY0); });
                getBone("weld1").ifPresent(b -> { b.setPosX((float) rig.weldE1); b.setPosY((float) rig.weldY1); });
            }

            float lift = be.liftOffset(pt);
            getBone("lift").ifPresent(b -> b.setPosY(lift * 16.0F));
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

    public PadRenderer() {
        super(new PadModel());

        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected Direction getFacing(LaunchTerminalBlockEntity block) { return Direction.NORTH; }

    @Override
    public AABB getRenderBoundingBox(LaunchTerminalBlockEntity be) {

        double r = be.getTier() >= 4 ? 21 : 14;
        return new AABB(be.getBlockPos()).inflate(r, 2, r).expandTowards(0, 330, 0).expandTowards(0, -26, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(LaunchTerminalBlockEntity be) { return true; }

    @Override
    public int getViewDistance() { return 256; }

    @Override
    public void render(LaunchTerminalBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!be.isFormed() || be.getLevel() == null) return;
        int light = sampleLight(be);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - be.getFacing().toYRot()));
        poseStack.translate(-0.5, 0.0, -0.5);

        mesh = MeshModel.pad(be.getTier());
        meshPoses.clear();
        super.render(be, partialTick, poseStack, bufferSource, light, packedOverlay);
        for (Map.Entry<String, Pose> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue().pose());
            ps.last().normal().set(e.getValue().normal());
            if (e.getKey().equals("pad") && mesh.drawCached("pad", ps.last(), light, packedOverlay)) continue;
            mesh.draw(e.getKey(), ps.last(), bufferSource, light, packedOverlay, null);
        }
        meshPoses.clear();
        PadLights.update(be, partialTick);
        if (be.getTier() >= 4) renderCrewAccess(be, partialTick, poseStack, bufferSource, light, packedOverlay);

        if (be.showMissile()) {
            float up = be.missileOffset(partialTick);
            int n = PadStructure.size(be.getTier());
            poseStack.pushPose();
            poseStack.translate(0.0, (be.getTier() >= 4 ? LaunchTerminalBlockEntity.PAD4_MOUNT_Y : 1.0) + up, n / 2);

            int ml = up > 3 ? LightTexture.pack(15, 15) : light;
            poseStack.translate(0.5, 0.0, 0.5);
            if (be.getTier() >= 4 && be.getLoadedBuild().staged() && be.getStage() == LaunchTerminalBlockEntity.Stage.LOADING)
                MissileParts.renderStackBuilt(be.getLoadedBuild(), PadRig.at(be.progress(partialTick)), true, poseStack, bufferSource, ml, partialTick);
            else {
                if (be.crewRocket()) MissileParts.hatchOpen = (float) PadCrew.hatch(be.boardTime(partialTick));
                MissileParts.render(be.getLoadedBuild(), poseStack, bufferSource, ml, partialTick);
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void renderCrewAccess(LaunchTerminalBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        MeshModel m = MeshModel.named("pad_t4_crew");
        if (!m.has("crew")) return;
        double t = be.boardTime(pt);
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        m.draw("crew", ps.last(), buffers, light, overlay);
        ps.pushPose();
        ps.translate(0.0, PadCrew.liftY(t) * PadCrew.PX, 0.0);
        m.draw("lift", ps.last(), buffers, light, overlay);
        float d = (float) PadCrew.doors(t);
        for (int k = 0; k < 2; k++) {
            ps.pushPose();
            aboutShaft(ps, (k == 0 ? -80.0F : 80.0F) * d);
            m.draw(k == 0 ? "lift_door_a" : "lift_door_b", ps.last(), buffers, light, overlay);
            ps.popPose();
        }
        ps.popPose();
        ps.pushPose();
        aboutShaft(ps, (float) (-PadCrew.ARM_SWING * be.crewArmAway(pt)));
        m.draw("crew_arm", ps.last(), buffers, light, overlay);
        ps.popPose();
        ClientBoarding.puppet(be, t, ps, buffers, light, pt);
        ClientBoarding.sounds(be, t);
        ps.popPose();
    }

    private static void aboutShaft(PoseStack ps, float deg) {
        if (Math.abs(deg) < 0.01F) return;
        double x = PadCrew.SX * PadCrew.PX, z = PadCrew.SZ * PadCrew.PX;
        ps.translate(x, 0.0, z);
        ps.mulPose(Axis.YP.rotationDegrees(deg));
        ps.translate(-x, 0.0, -z);
    }

    private static java.util.function.Predicate<float[]> viewTest(LaunchTerminalBlockEntity be) {
        net.minecraft.client.renderer.culling.Frustum fr = net.minecraft.client.Minecraft.getInstance().levelRenderer.getFrustum();
        if (fr == null) return null;
        double th = Math.toRadians(180.0 - be.getFacing().toYRot()), c = Math.cos(th), s = Math.sin(th);
        double ox = be.getBlockPos().getX() + 0.5, oy = be.getBlockPos().getY(), oz = be.getBlockPos().getZ() + 0.5;
        return bb -> {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                double x = (i & 1) == 0 ? bb[0] : bb[3], z = (i & 2) == 0 ? bb[2] : bb[5];
                double wx = x * c + z * s, wz = -x * s + z * c;
                minX = Math.min(minX, wx); maxX = Math.max(maxX, wx); minZ = Math.min(minZ, wz); maxZ = Math.max(maxZ, wz);
            }
            return fr.isVisible(new AABB(ox + minX - 0.1, oy + bb[1] - 0.1, oz + minZ - 0.1, ox + maxX + 0.1, oy + bb[4] + 0.1, oz + maxZ + 0.1));
        };
    }

    private static int sampleLight(LaunchTerminalBlockEntity be) {
        Level level = be.getLevel();
        Vec3 c = be.padCentre();
        Direction m = Machines.mount(be.getBlockState());
        BlockPos p = BlockPos.containing(c.x + m.getStepX(), c.y + m.getStepY(), c.z + m.getStepZ());
        int a = LevelRenderer.getLightColor(level, p);
        int b = LevelRenderer.getLightColor(level, be.getBlockPos().relative(be.getFacing()).relative(Machines.mount(be.getBlockState())));
        return LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(b)), Math.max(LightTexture.sky(a), LightTexture.sky(b)));
    }
}
