package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GantryCabinetRenderer implements BlockEntityRenderer<GantryCabinetBlockEntity> {

    private static final float MX = 12.0F, FOOT = -32.0F;
    private final Look look = new Look();
    private long lastSpark = -1;

    @Override
    public void render(GantryCabinetBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        look.render(be, pt, ps, buffers, light, overlay);
        Level level = be.getLevel();
        if (level == null || !be.linked()) return;
        MeshModel kit = MeshModel.named("gantry_crane");
        if (!kit.has("girder")) return;
        be.ease();
        float[] c = be.crane(pt);
        float u = c[0], vm = c[1], mast = c[2], arm = c[3];
        Direction vd = be.vDir(), ud = be.uDir();
        Vec3 o = be.origin();
        BlockPos bp = be.getBlockPos();
        int span = be.span();
        Vec3 trolleyW = o.add(step(ud).scale(u)).add(step(vd).scale(vm)).add(0.0, -1.0, 0.0);
        int lt = LevelRenderer.getLightColor(level, BlockPos.containing(trolleyW));

        ps.pushPose();
        ps.translate(o.x - bp.getX(), o.y - bp.getY(), o.z - bp.getZ());
        Matrix4f basis = new Matrix4f(vd.getStepX(), 0.0F, vd.getStepZ(), 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, ud.getStepX(), 0.0F, ud.getStepZ(), 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
        ps.last().pose().mul(basis);
        ps.last().normal().mul(new Matrix3f(basis));
        ps.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);

        ps.pushPose();
        ps.translate(0.0F, 0.0F, u * 16.0F);
        float roll = u * 16.0F / 3.0F;
        truck(kit, ps, buffers, lt, overlay, roll);
        ps.pushPose();
        ps.translate(span * 16.0F, 0.0F, 0.0F);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        truck(kit, ps, buffers, lt, overlay, -roll);
        ps.popPose();
        for (int k = 0; k < span - 1; k++) {
            ps.pushPose();
            ps.translate(k * 16.0F, 0.0F, 0.0F);
            part(kit, "girder", ps, buffers, lt, overlay);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(span * 16.0F - 12.0F, 0.0F, 0.0F);
        ps.scale(0.5F, 1.0F, 1.0F);
        ps.translate(-4.0F, 0.0F, 0.0F);
        part(kit, "girder", ps, buffers, lt, overlay);
        ps.popPose();
        float tx = vm * 16.0F - MX - 6.0F;
        for (int i = 0; i < Balance.GANTRY_FESTOONS; i++) {
            float fx = 8.0F + i * Math.max(0.0F, tx - 8.0F) / Balance.GANTRY_FESTOONS;
            ps.pushPose();
            ps.translate(fx - 16.0F, 0.0F, 0.0F);
            part(kit, "festoon", ps, buffers, lt, overlay);
            ps.popPose();
        }

        ps.pushPose();
        ps.translate(vm * 16.0F - MX, 0.0F, 0.0F);
        part(kit, "trolley", ps, buffers, lt, overlay);
        part(kit, "tote", ps, buffers, lt, overlay);
        ps.pushPose();
        rotZAbout(ps, MX + 6.0F, 29.0F, (float) Math.toRadians(100.0) * be.clientHatch(pt));
        part(kit, "tote_lid", ps, buffers, lt, overlay);
        ps.popPose();
        float e = mast / 5.0F;
        String[] stages = { "mast1", "mast2", "mast3", "mast4", "mast5", "mast6" };
        for (int k = 0; k < stages.length; k++) {
            ps.pushPose();
            ps.translate(0.0F, -e * k, 0.0F);
            part(kit, stages[k], ps, buffers, lt, overlay);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(0.0F, -mast, 0.0F);
        part(kit, "wrist", ps, buffers, lt, overlay);
        float a = (float) Math.toRadians(arm);
        rotZAbout(ps, MX, FOOT - 3.6F, a);
        part(kit, "upper", ps, buffers, lt, overlay);
        rotZAbout(ps, MX, FOOT - 27.0F, -a);
        part(kit, "fore", ps, buffers, lt, overlay);
        BlockState held = be.clientHeld();
        boolean holding = held != null && be.clientPhase() <= GantryCabinetBlockEntity.WELD;
        for (String jaw : new String[] { "jaw_l", "jaw_r" }) {
            ps.pushPose();
            float jx = jaw.equals("jaw_l") ? MX - 10.1F : MX + 10.1F;
            rotZAbout(ps, jx, FOOT - 46.6F, (float) Math.toRadians(holding ? 0.0 : jaw.equals("jaw_l") ? -12.0 : 12.0));
            part(kit, jaw, ps, buffers, lt, overlay);
            ps.popPose();
        }
        if (holding) {
            ps.pushPose();
            ps.translate(MX, FOOT - 52.0F, 0.0F);
            ps.scale(14.4F, 14.4F, 14.4F);
            ps.translate(-0.5F, -0.5F, -0.5F);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(held, ps, buffers, lt, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        ps.popPose();
        ps.popPose();
        ps.popPose();
        ps.popPose();

        if (be.clientPhase() == GantryCabinetBlockEntity.WELD) weld(be, level, o, ud, vd, u, vm, mast, a);
    }

    private static void part(MeshModel kit, String bone, PoseStack ps, MultiBufferSource buffers, int lt, int overlay) {
        ps.pushPose();
        ps.scale(16.0F, 16.0F, 16.0F);
        kit.draw(bone, ps.last(), buffers, lt, overlay);
        ps.popPose();
    }

    private static Vec3 step(Direction d) { return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ()); }

    private static void rotZAbout(PoseStack ps, float x, float y, float rad) {
        ps.translate(x, y, 0.0F);
        ps.mulPose(Axis.ZP.rotation(rad));
        ps.translate(-x, -y, 0.0F);
    }

    private static void truck(MeshModel kit, PoseStack ps, MultiBufferSource buffers, int lt, int overlay, float roll) {
        part(kit, "truck", ps, buffers, lt, overlay);
        for (String w : new String[] { "wheel_f", "wheel_b" }) {
            ps.pushPose();
            float z = w.equals("wheel_f") ? -8.0F : 8.0F;
            ps.translate(0.0F, 3.0F, z);
            ps.mulPose(Axis.XP.rotation(roll));
            ps.translate(0.0F, -3.0F, -z);
            part(kit, w, ps, buffers, lt, overlay);
            ps.popPose();
        }
    }

    private void weld(GantryCabinetBlockEntity be, Level level, Vec3 o, Direction ud, Direction vd, float u, float vm, float mast, float a) {
        float down = (32.0F + mast + 3.6F + 23.4F * (float) Math.cos(a) + 25.0F) / 16.0F;
        float v = vm + 23.4F * (float) Math.sin(a) / 16.0F;
        Vec3 w = o.add(step(ud).scale(u)).add(step(vd).scale(v)).add(0.0, -down, 0.0);
        VeilBridge.machineGlow(0x6A00_0000_0000_0000L ^ be.getBlockPos().asLong(), w.x, w.y, w.z, 0.35F, 0.6F, 1.0F, 5.0F, 1.6F, level.getGameTime());

        ClientWeldFx.arc(be.getBlockPos().asLong(), w, level.getGameTime());
    }

    @Override
    public AABB getRenderBoundingBox(GantryCabinetBlockEntity be) {
        AABB own = look.getRenderBoundingBox(be);
        if (!be.linked()) return own;
        Vec3 o = be.origin(), far = o.add(step(be.uDir()).scale(be.len() - 1)).add(step(be.vDir()).scale(be.span()));
        return own.minmax(new AABB(o, far).inflate(1.5, 2.5, 1.5).expandTowards(0.0, -Balance.GANTRY_MAST_MAX / 16.0 - 6.0, 0.0));
    }

    @Override public boolean shouldRenderOffScreen(GantryCabinetBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 128; }

    static final class Look extends StagedUnitRenderer<GantryCabinetBlockEntity> {
        private static final Set<String> ATTACH = Set.of("housing", "housing_door_l", "housing_door_r", "housing_hatch_f", "housing_hatch_b");
        private BlockState skin;

        Look() { super("gantry_cabinet", 2, 2); }

        @Override protected Set<String> attachBones() { return ATTACH; }
        @Override protected boolean drawsItself(String bone) { return skin != null && ATTACH.contains(bone); }

        @Override
        public void render(GantryCabinetBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            skin = be.copycat().shown();
            try {
                super.render(be, partialTick, poseStack, buffers, light, overlay);
            } finally {
                skin = null;
            }
        }

        @Override
        protected void drawAttached(GantryCabinetBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            if (skin == null) return;
            for (String b : ATTACH)
                if (poses.containsKey(b)) CopycatSkin.draw(MeshModel.named("gantry_cabinet"), b, poses.get(b), normals.get(b), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        }
    }
}
