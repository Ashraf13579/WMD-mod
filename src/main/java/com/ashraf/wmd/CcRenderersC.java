package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public final class CcRenderersC {

    private CcRenderersC() {}

    public static class SatGlobe extends StagedUnitRenderer<CcSatGlobeBlockEntity> {

        private static final int LAT = 5, LON = 8, SEG = 32;

        public SatGlobe() { super("cc_sat_globe", 3); }

        private record SatPos(OrbitMath.Sat sat, float[] at, float[][] ring, int colour) {}

        @Override
        public void render(CcSatGlobeBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            super.render(be, pt, ps, buf, light, overlay);
            if (!be.isOnline() || be.getLevel() == null || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
            Direction f = be.getBlockState().getValue(MachineBlock.FACING);
            double tt = be.getLevel().getGameTime() + pt;
            float R = CcSpecC.GLOBE_R, OR = CcSpecC.ORBIT_R;
            double[] off = CcRenderers.worldOf(f, CcSpecC.GLOBE_X, CcSpecC.GLOBE_Z);
            double rx = be.getBlockPos().getX() + off[0], rz = be.getBlockPos().getZ() + off[1];
            float flicker = 0.85F + 0.15F * (float) Math.sin(tt * 0.9);

            CcRenderers.modelFrame(ps, f);
            ps.translate(CcSpecC.GLOBE_X, CcSpecC.GLOBE_Y, CcSpecC.GLOBE_Z);
            ps.mulPose(Axis.YP.rotationDegrees(-CcRenderers.angle(f)));

            List<SatPos> sats = new ArrayList<>();
            UUID owner = be.getOwner();
            long now = OrbitView.now();
            if (owner != null) {
                for (OrbitMath.Sat s : OrbitView.sats()) {
                    if (!owner.equals(s.owner()) || now >= s.endMs()) continue;
                    double th = 2.0 * Math.PI * OrbitMath.offset(s, rx, rz, now) / OrbitMath.wrap();
                    float[][] ring = new float[SEG + 1][];
                    for (int k = 0; k <= SEG; k++) ring[k] = orbitPoint(s, 2.0 * Math.PI * k / SEG, OR);
                    sats.add(new SatPos(s, orbitPoint(s, th, OR), ring, CcScreenC.colour(s.type())));
                }
            }

            PoseStack.Pose p;
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees((float) (tt * CcSpecC.GLOBE_DEG_PER_TICK) % 360.0F));
            p = ps.last();
            VertexConsumer lines = buf.getBuffer(RenderType.lines());
            int grid = ((int) (110 * flicker) << 24) | 0x3FA9F5;
            for (int i = 1; i < LAT; i++) {
                double la = Math.PI * i / LAT - Math.PI / 2.0;
                float y = R * (float) Math.sin(la), r = R * (float) Math.cos(la);
                for (int k = 0; k < SEG; k++) {
                    double a0 = 2.0 * Math.PI * k / SEG, a1 = 2.0 * Math.PI * (k + 1) / SEG;
                    CcRenderers.line(lines, p, r * (float) Math.cos(a0), y, r * (float) Math.sin(a0), r * (float) Math.cos(a1), y, r * (float) Math.sin(a1), grid);
                }
            }
            for (int j = 0; j < LON; j++) {
                double lo = Math.PI * j / LON;
                float cx = (float) Math.cos(lo), cz = (float) Math.sin(lo);
                for (int k = 0; k < SEG; k++) {
                    double a0 = 2.0 * Math.PI * k / SEG, a1 = 2.0 * Math.PI * (k + 1) / SEG;
                    float r0 = R * (float) Math.cos(a0), r1 = R * (float) Math.cos(a1);
                    CcRenderers.line(lines, p, r0 * cx, R * (float) Math.sin(a0), r0 * cz, r1 * cx, R * (float) Math.sin(a1), r1 * cz, grid);
                }
            }
            ps.popPose();
            p = ps.last();
            for (SatPos sp : sats) {
                int col = (0x70 << 24) | sp.colour();
                for (int k = 0; k < SEG; k++) {
                    float[] a = sp.ring()[k], b = sp.ring()[k + 1];
                    CcRenderers.line(lines, p, a[0], a[1], a[2], b[0], b[1], b[2], col);
                }
                if (sp.sat().type().equals("recon")) {
                    float[][] foot = coneFoot(sp.at(), R);
                    for (int k = 0; k < foot.length; k += 2)
                        CcRenderers.line(lines, p, sp.at()[0], sp.at()[1], sp.at()[2], foot[k][0], foot[k][1], foot[k][2], 0x90FFFFFF);
                    for (int k = 0; k < foot.length; k++) {
                        float[] a = foot[k], b = foot[(k + 1) % foot.length];
                        CcRenderers.line(lines, p, a[0], a[1], a[2], b[0], b[1], b[2], 0xC0FFFFFF);
                    }
                }
            }

            CcRenderers.line(lines, p, 0.0F, R, 0.0F, 0.0F, R + 1.6F, 0.0F, 0xFF50A0FF);

            VertexConsumer q = buf.getBuffer(RenderType.lightning());
            Matrix4f m = ps.last().pose();
            boolean blinkOn = ((long) tt) % 20 < 14;
            for (SatPos sp : sats) {
                float[] a = sp.at();
                float s = blinkOn ? 0.7F : 0.45F;
                int col = 0xF0000000 | sp.colour();
                cube(q, m, a[0], a[1], a[2], s, col);
                if (sp.sat().type().equals("recon")) {
                    float[][] foot = coneFoot(a, R);
                    for (int k = 0; k < foot.length; k++) {
                        float[] b = foot[k], c = foot[(k + 1) % foot.length];
                        CcRenderers.quad(q, m, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], a[0], a[1], a[2], 0x22FFFFFF);
                    }
                }
            }
            cube(q, m, 0.0F, R + 1.6F, 0.0F, 0.5F, 0xF050A0FF);
            ps.popPose();
        }

        private static float[] orbitPoint(OrbitMath.Sat s, double th, float r) {
            double sh = Math.sin(s.heading()), ch = Math.cos(s.heading());
            double ux = sh, uz = -ch;
            double vx = ch, vz = sh;
            double tilt = Math.atan(s.cross()) * 0.5;
            double up = Math.cos(th), fwd = Math.sin(th);
            double x = fwd * ux + up * Math.sin(tilt) * vx, y = up * Math.cos(tilt), z = fwd * uz + up * Math.sin(tilt) * vz;
            return new float[] { (float) (x * r), (float) (y * r), (float) (z * r) };
        }

        private static float[][] coneFoot(float[] at, float R) {
            float l = (float) Math.sqrt(at[0] * at[0] + at[1] * at[1] + at[2] * at[2]);
            float nx = at[0] / l, ny = at[1] / l, nz = at[2] / l;

            float ax = Math.abs(ny) < 0.9F ? 0.0F : 1.0F, ay = Math.abs(ny) < 0.9F ? 1.0F : 0.0F, az = 0.0F;
            float ex = ay * nz - az * ny, ey = az * nx - ax * nz, ez = ax * ny - ay * nx;
            float el = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
            ex /= el; ey /= el; ez /= el;
            float gx = ny * ez - nz * ey, gy = nz * ex - nx * ez, gz = nx * ey - ny * ex;
            float rr = R * 0.28F, h = (float) Math.sqrt(Math.max(0.0F, R * R - rr * rr));
            float[][] pts = new float[8][];
            for (int k = 0; k < 8; k++) {
                double a = 2.0 * Math.PI * k / 8;
                float c = (float) Math.cos(a) * rr, sn = (float) Math.sin(a) * rr;
                pts[k] = new float[] { nx * h + ex * c + gx * sn, ny * h + ey * c + gy * sn, nz * h + ez * c + gz * sn };
            }
            return pts;
        }

        private static void cube(VertexConsumer q, Matrix4f m, float x, float y, float z, float s, int col) {
            CcRenderers.quad(q, m, x - s, y - s, z, x + s, y - s, z, x + s, y + s, z, x - s, y + s, z, col);
            CcRenderers.quad(q, m, x, y - s, z - s, x, y - s, z + s, x, y + s, z + s, x, y + s, z - s, col);
            CcRenderers.quad(q, m, x - s, y, z - s, x + s, y, z - s, x + s, y, z + s, x - s, y, z + s, col);
        }

        @Override
        public AABB getRenderBoundingBox(CcSatGlobeBlockEntity be) {
            return new AABB(be.getBlockPos()).inflate(3, 0, 3).expandTowards(0, 4, 0);
        }
    }

    public static class GpsRack extends StagedUnitRenderer<CcGpsRackBlockEntity> {

        public GpsRack() { super("cc_gps_rack", 2); }

        @Override
        public void render(CcGpsRackBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            super.render(be, pt, ps, buf, light, overlay);
            if (!be.isOnline() || be.getLevel() == null || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
            Direction f = be.getBlockState().getValue(MachineBlock.FACING);
            CcRenderers.modelFrame(ps, f);
            float x0 = CcSpecC.GPS_X0, x1 = CcSpecC.GPS_X1, y0 = CcSpecC.GPS_Y0, y1 = CcSpecC.GPS_Y1, z = CcSpecC.GPS_Z - 0.05F;
            CcRenderers.rect(buf.getBuffer(RenderType.debugQuads()), ps.last().pose(), x0, y0, x1, y1, z, CcRenderers.screenBg(be, 0xF0081420));
            long day = be.getLevel().getDayTime() % 24000L;
            int hh = (int) ((day / 1000L + 6L) % 24L), mm = (int) ((day % 1000L) * 60L / 1000L);
            float cx = (x0 + x1) / 2.0F, h = y1 - y0;
            CcRenderers.text(ps, buf, String.format("%02d:%02d", hh, mm), cx, y0 + h * 0.66F, z - 0.05F, h * 0.42F, 0xFF9FE3FF);
            boolean live = be.live();
            CcRenderers.text(ps, buf, live ? "GPS LOCK" : "NO NAV SAT", cx, y0 + h * 0.24F, z - 0.05F, h * 0.22F,
                    live ? 0xFF60E070 : ((long) (be.getLevel().getGameTime() / 10L)) % 2 == 0 ? 0xFFFF6040 : 0xFF803020);
            ps.popPose();
        }
    }

    public static class AlarmBoard extends StagedUnitRenderer<CcAlarmBoardBlockEntity> {

        public AlarmBoard() { super("cc_alarm_board", 2); }

        @Override
        public void render(CcAlarmBoardBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            ps.pushPose();
            ps.translate(0.0, CcModuleBlocksC.AlarmBoard.WALL_LIFT, 0.0);
            super.render(be, pt, ps, buf, light, overlay);
            if (!be.isOnline() || be.getLevel() == null || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) { ps.popPose(); return; }
            Direction f = be.getBlockState().getValue(MachineBlock.FACING);
            CcRenderers.modelFrame(ps, f);
            float x0 = CcSpecC.ALARM_X0, x1 = CcSpecC.ALARM_X1, y0 = CcSpecC.ALARM_Y0, y1 = CcSpecC.ALARM_Y1, z = CcSpecC.ALARM_Z - 0.05F;
            boolean al = be.alert(), blink = ((long) (be.getLevel().getGameTime() / 5L)) % 2 == 0;
            CcRenderers.rect(buf.getBuffer(RenderType.debugQuads()), ps.last().pose(), x0, y0, x1, y1, z, al && blink ? 0xF0401010 : CcRenderers.screenBg(be, 0xF0081420));
            float cx = (x0 + x1) / 2.0F, h = y1 - y0;
            CcRenderers.text(ps, buf, al ? "ALARM" : "ALL CLEAR", cx, y0 + h * 0.62F, z - 0.05F, h * 0.3F, al ? (blink ? 0xFFFF4040 : 0xFFFFA0A0) : 0xFF60E070);
            CcRenderers.text(ps, buf, "EVENTS " + be.count(), cx, y0 + h * 0.24F, z - 0.05F, h * 0.2F, 0xFF8090A0);
            ps.popPose();
            ps.popPose();
        }
    }
}
