package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class CargoRampRenderer implements BlockEntityRenderer<CargoRampBlockEntity> {

    static final float PX = 1.0F / 16.0F;

    static final double PV_Y = 2.0, PV_Z = -6.0, JA_Y = 22.0, JA_Z = -2.6, JL_Y = 6.0, JL_Z = 3.4, JREST = 14.0,
            DPV_Y = 59.0, DPV_Z = -4.0, DA_Y = 62.0, DA_Z = -3.0, DL_Y = -8.0, DL_Z = 1.0, DREST = 11.0, TOE_Y = 47.6, TOE_Z = -3.1, ROLL_Y = 8.0, ROLL_Z = -4.6,
            LOCK_X = 4.3, LOCK_Y = 46.8;
    static final float TOE_OUT = -220.0F;

    static double[] rot(double y, double z, double aDeg) {
        double a = Math.toRadians(aDeg);
        return new double[] { y * Math.cos(a) - z * Math.sin(a), y * Math.sin(a) + z * Math.cos(a) };
    }

    static double[] jack(double ay, double az, double py, double pz, double rest) {
        double dy = py - ay, dz = pz - az, ln = Math.max(1e-6, Math.hypot(dy, dz));
        return new double[] { Math.toDegrees(Math.atan2(-dz / ln, -dy / ln)), -(ln - rest) };
    }

    static double[] rampJack(double theta) {
        double[] r = rot(JL_Y, JL_Z, -theta);
        return jack(JA_Y, JA_Z, PV_Y + r[0], PV_Z + r[1], JREST);
    }

    static double[] doorJack(double phi) {
        double[] r = rot(DL_Y, DL_Z, -phi);
        return jack(DA_Y, DA_Z, DPV_Y + r[0], DPV_Z + r[1], DREST);
    }

    @Override
    public void render(CargoRampBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        MeshModel mm = MeshModel.named("cargo_ramp");
        if (!mm.has("leaf_lo")) return;
        BlockState st = be.getBlockState();
        Direction f = st.getValue(MachineBlock.FACING);
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - f.toYRot()));
        if (!st.getValue(CargoRampBlock.HEAD) || be.width() == 0) {
            CargoRampBlockEntity head = st.getValue(CargoRampBlock.HEAD) ? null : CargoRampBlock.headOf(level, be.getBlockPos());
            if (head == null || head.width() == 0) lone(be, mm, ps, buffers, light, overlay, st.getValue(CargoRampBlock.LOW));
            ps.popPose();
            return;
        }
        int w = be.width(), h = be.height(), r = be.leafRows(), d = be.doorRows();
        float[] p = be.poseAfter(level.getGameTime() - be.syncAtClient() + pt);
        float theta = p[0], phi = p[1], toe = p[2], lock = p[3];
        if (theta >= 30.0F && be.opened()) CargoRampWalk.seen(be.getBlockPos());
        BlockState skin = be.copycat().shown();
        BlockPos o = be.getBlockPos();
        for (int c = 0; c < w; c++) {
            BlockPos tp = Machines.toWorld(o, f, c, 0, 0);
            int lIn = LevelRenderer.getLightColor(level, tp), lOut = Math.max(lIn, LevelRenderer.getLightColor(level, tp.relative(f)));
            ps.pushPose();
            ps.translate(c, 0.0, 0.0);
            draw(mm, "beam", ps, buffers, lIn, overlay);

            ps.pushPose();
            pivotX(ps, PV_Y, PV_Z, -theta);
            section(mm, "leaf_lo", "housing_lo", 0.0, ps, buffers, lOut, overlay, skin, level, tp);
            for (int k = 1; k <= r - 2; k++) section(mm, "leaf_m", "housing_m", k - 1, ps, buffers, lOut, overlay, skin, level, tp);
            ps.pushPose();
            ps.translate(0.0, r - 3, 0.0);
            section(mm, "leaf_t", "housing_t", 0.0, ps, buffers, lOut, overlay, skin, level, tp);
            if (mm.has("toe")) {
                ps.pushPose();
                pivotX(ps, TOE_Y, TOE_Z, TOE_OUT * toe);
                draw(mm, "toe", ps, buffers, lOut, overlay);
                ps.popPose();
            }
            ps.popPose();
            float spin = level.getGameTime() < be.rollUntilClient() ? (level.getGameTime() + pt) * 24.0F : 0.0F;
            if (mm.has("roller")) for (double y = ROLL_Y; y <= 16.0 * r - 10.0; y += 4.0) {
                ps.pushPose();
                ps.translate(0.0, (y - ROLL_Y) * PX, 0.0);
                if (spin != 0.0F) pivotX(ps, ROLL_Y, ROLL_Z, spin);
                draw(mm, "roller", ps, buffers, lOut, overlay);
                ps.popPose();
            }
            if (c == 0) draw(mm, "lug_w", ps, buffers, lOut, overlay);
            if (c == w - 1) draw(mm, "lug_e", ps, buffers, lOut, overlay);
            ps.popPose();

            for (String side : c == 0 && c == w - 1 ? new String[] { "w", "e" } : c == 0 ? new String[] { "w" } : c == w - 1 ? new String[] { "e" } : new String[0])
                endColumn(mm, side, ps, buffers, lIn, overlay, r, theta, lock);
            if (c == 0) {
                draw(mm, "base", ps, buffers, lIn, overlay);
                int col = lampColour(be, theta, lock);
                if (mm.has("lamp")) mm.drawColoured("lamp", ps.last(), buffers, 0xF000F0, overlay, (col >> 16) & 255, (col >> 8) & 255, col & 255);
            }

            if (d > 0) {
                ps.pushPose();
                ps.translate(0.0, h - 4, 0.0);
                draw(mm, "header", ps, buffers, lIn, overlay);
                for (String side : sides(c, w)) {
                    draw(mm, "dmount_" + side, ps, buffers, lIn, overlay);
                    double[] j = doorJack(phi);
                    ps.pushPose();
                    pivotX(ps, DA_Y, DA_Z, j[0]);
                    draw(mm, "djack_" + side, ps, buffers, lIn, overlay);
                    ps.translate(0.0, j[1] * PX, 0.0);
                    draw(mm, "drod_" + side, ps, buffers, lIn, overlay);
                    ps.popPose();
                }
                ps.pushPose();
                pivotX(ps, DPV_Y, DPV_Z, -phi);
                section(mm, "door", "housing_d", 0.0, ps, buffers, lIn, overlay, skin, level, tp);
                for (String side : sides(c, w)) draw(mm, "dlug_" + side, ps, buffers, lIn, overlay);
                for (int k = 1; k < d; k++) {
                    ps.pushPose();
                    ps.translate(0.0, (-20.0 - 16.0 * k) * PX, 0.0);
                    section(mm, "door_m", "housing_dm", 0.0, ps, buffers, lIn, overlay, skin, level, tp);
                    ps.popPose();
                }
                ps.popPose();
                ps.popPose();
            }
            ps.popPose();
        }
        ps.popPose();
        StaticLineFx.draw(be, theta, pt, ps, buffers, LevelRenderer.getLightColor(level, o.relative(f.getOpposite())));
    }

    private static String[] sides(int c, int w) {
        if (c == 0 && c == w - 1) return new String[] { "w", "e" };
        return c == 0 ? new String[] { "w" } : c == w - 1 ? new String[] { "e" } : new String[0];
    }

    private static void endColumn(MeshModel mm, String s, PoseStack ps, MultiBufferSource buffers, int light, int overlay, int r, float theta, float lock) {
        draw(mm, "jamb_" + s + "0", ps, buffers, light, overlay);
        for (int k = 1; k <= r - 2; k++) {
            ps.pushPose();
            ps.translate(0.0, k - 1, 0.0);
            draw(mm, "jamb_" + s, ps, buffers, light, overlay);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(0.0, r - 3, 0.0);
        draw(mm, "jamb_" + s + "l", ps, buffers, light, overlay);
        ps.pushPose();
        float sg = s.equals("w") ? -1.0F : 1.0F;
        ps.translate(sg * LOCK_X * PX, LOCK_Y * PX, -1.9 * PX);
        ps.mulPose(Axis.ZP.rotationDegrees(-sg * 90.0F * lock));
        ps.translate(-sg * LOCK_X * PX, -LOCK_Y * PX, 1.9 * PX);
        draw(mm, "lock_" + s, ps, buffers, light, overlay);
        ps.popPose();
        ps.popPose();
        draw(mm, "mount_" + s, ps, buffers, light, overlay);
        double[] j = rampJack(theta);
        ps.pushPose();
        pivotX(ps, JA_Y, JA_Z, j[0]);
        draw(mm, "jack_" + s, ps, buffers, light, overlay);
        ps.translate(0.0, j[1] * PX, 0.0);
        draw(mm, "jrod_" + s, ps, buffers, light, overlay);
        ps.popPose();
    }

    private static void lone(CargoRampBlockEntity be, MeshModel mm, PoseStack ps, MultiBufferSource buffers, int light, int overlay, boolean low) {
        BlockState skin = be.copycat().shown();
        if (low) {
            draw(mm, "beam", ps, buffers, light, overlay);
            section(mm, "leaf_lo", "housing_lo", 0.0, ps, buffers, light, overlay, skin, be.getLevel(), be.getBlockPos());
        } else section(mm, "leaf_m", "housing_m", -1.0, ps, buffers, light, overlay, skin, be.getLevel(), be.getBlockPos());
    }

    private static void section(MeshModel mm, String bone, String housing, double rowShift, PoseStack ps, MultiBufferSource buffers, int light, int overlay,
                                BlockState skin, Level level, BlockPos at) {
        ps.pushPose();
        if (rowShift != 0.0) ps.translate(0.0, rowShift, 0.0);
        draw(mm, bone, ps, buffers, light, overlay);
        if (mm.has(housing)) {
            if (skin != null) CopycatSkin.draw(mm, housing, ps.last().pose(), ps.last().normal(), skin, level, at, buffers, light, overlay);
            else mm.draw(housing, ps.last(), buffers, light, overlay);
        }
        ps.popPose();
    }

    static void pivotX(PoseStack ps, double y, double z, double deg) {
        ps.translate(0.0, y * PX, z * PX);
        ps.mulPose(Axis.XP.rotationDegrees((float) deg));
        ps.translate(0.0, -y * PX, -z * PX);
    }

    private static void draw(MeshModel mm, String bone, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (mm.has(bone)) mm.draw(bone, ps.last(), buffers, light, overlay);
    }

    private static int lampColour(CargoRampBlockEntity be, float theta, float lock) {
        int st = be.rampState();
        if (st == CargoRampBlockEntity.S_NO_RECT) return 0xFF4040;
        if (st == CargoRampBlockEntity.S_LEVEL || st == CargoRampBlockEntity.S_GROUND) return 0x50E080;
        if (st == CargoRampBlockEntity.S_MOVING) return be.getLevel() != null && (be.getLevel().getGameTime() / 5) % 2 == 0 ? 0xF0F0F0 : 0x8090A0;
        return 0x4AA3FF;
    }

    @Override
    public AABB getRenderBoundingBox(CargoRampBlockEntity be) {
        BlockPos o = be.getBlockPos();
        int n = Math.max(Math.max(be.width(), be.height()) + 6, (int) Balance.STATIC_SEARCH_R);
        return new AABB(o).inflate(n);
    }

    @Override public boolean shouldRenderOffScreen(CargoRampBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 192; }
}
