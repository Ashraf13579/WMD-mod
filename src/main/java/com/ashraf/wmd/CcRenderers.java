package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CcRenderers {

    private CcRenderers() {}

    private static final int FULL = LightTexture.FULL_BRIGHT;

    static float angle(Direction f) {
        return switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; };
    }

    static void modelFrame(PoseStack ps, Direction f) {
        ps.pushPose();
        ps.translate(0.5F, 0.0F, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(angle(f)));
        ps.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
    }

    static double[] worldOf(Direction f, double lxPx, double lzPx) {
        double lx = lxPx / 16.0, lz = lzPx / 16.0, dx, dz;
        switch (f) {
            case EAST -> { dx = -lz; dz = lx; }
            case SOUTH -> { dx = -lx; dz = -lz; }
            case WEST -> { dx = lz; dz = -lx; }
            default -> { dx = lx; dz = lz; }
        }
        return new double[] { 0.5 + dx, 0.5 + dz };
    }

    static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                     float x3, float y3, float z3, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
    }

    static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        quad(vc, m, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, argb);
    }

    static void line(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1.0E-4F) return;
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        vc.addVertex(p, x0, y0, z0).setColor(r, g, b, a).setNormal(p, dx / l, dy / l, dz / l);
        vc.addVertex(p, x1, y1, z1).setColor(r, g, b, a).setNormal(p, dx / l, dy / l, dz / l);
    }

    static UUID me() { return Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null; }

    static int screenBg(CcModuleBlockEntity be, int normal) {
        CcBuildingBlockEntity b = be.building();
        if (b == null || b.defcon() > 1 || be.getLevel() == null) return normal;
        double k = 0.5 + 0.5 * Math.sin(be.getLevel().getGameTime() * 0.15);
        int r = 0x30 + (int) (0x30 * k);
        return (normal & 0xFF000000) | (r << 16) | 0x0606;
    }

    static void textFit(PoseStack ps, MultiBufferSource buf, String s, float cx, float cy, float z, float h, float maxW, int colour) {
        float w = Minecraft.getInstance().font.width(s) * h / 9.0F;
        text(ps, buf, s, cx, cy, z, w > maxW ? h * maxW / w : h, colour);
    }

    public static class MapTable extends StagedUnitRenderer<CcMapTableBlockEntity> {

        private static final int MAX_LABELS = 6;
        private static final double VIEW_RANGE = 32.0;
        private static final int[] ASSET_COL = { 0xFFFF9030, 0xFF40E060, 0xFFFFE040, 0xFF40D0FF, 0xFF5090FF, 0xFF70F0FF, 0xFFFF3030, 0xFFFF40FF,
                0xFFC8E8FF, 0xFFB060FF };

        private record Area(double cx, double cz, double halfB, float half, float floor, float[] tops, int gn, float relief) {
            float scale() { return half / (float) halfB; }
            boolean inside(double x, double z) { return Math.abs(x - cx) <= halfB && Math.abs(z - cz) <= halfB; }
            float hx(double x) { return (float) (x - cx) * scale(); }
            float hz(double z) { return (float) (z - cz) * scale(); }

            float ground(double x, double z) {
                if (tops == null || gn <= 0) return floor;
                int i = (int) Math.floor((x - cx + halfB) / (2.0 * halfB) * gn), j = (int) Math.floor((z - cz + halfB) / (2.0 * halfB) * gn);
                i = Math.max(0, Math.min(gn - 1, i)); j = Math.max(0, Math.min(gn - 1, j));
                return floor + tops[j * gn + i];
            }
            float top() { return floor + relief; }
        }

        private static long lastMapAsk = -1000L, lastDotsAsk = -1000L;

        public MapTable() { super("cc_map_table", 2, 6); }

        private static void askRecon(CcMapTableBlockEntity be) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen instanceof ReconMapScreen || mc.player.blockPosition().distSqr(be.getBlockPos()) > ReconPayload.WALL_VIEW_RANGE * ReconPayload.WALL_VIEW_RANGE) return;
            long t = be.getLevel().getGameTime();
            if (t < lastMapAsk || t < lastDotsAsk) { lastMapAsk = lastDotsAsk = -1000L; }
            if ((ReconView.map == null || ReconView.stale()) && t - lastMapAsk >= 40) {
                lastMapAsk = t;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ReconPayload(be.getBlockPos(), ReconPayload.MAP, 0, 0));
            } else if (t - lastDotsAsk >= Math.max(20, Balance.RECON_DOT_REFRESH_TICKS)) {
                lastDotsAsk = t;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ReconPayload(be.getBlockPos(), ReconPayload.DOTS, 0, 0));
            }
        }

        @Override
        public void render(CcMapTableBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            super.render(be, pt, ps, buf, light, overlay);
            if (!be.isRunning() || be.getLevel() == null || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (cam.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > VIEW_RANGE * VIEW_RANGE) return;
            Direction f = be.getBlockState().getValue(MachineBlock.FACING);
            double[] off = worldOf(f, CcModelSpec.HOLO_X, CcModelSpec.HOLO_Z);
            double cx = be.getBlockPos().getX() + off[0], cz = be.getBlockPos().getZ() + off[1];
            UUID me = me();
            boolean wantRecon = be.view() == CcMapTableBlockEntity.VIEW_RECON && me != null && me.equals(be.getOwner());
            if (wantRecon) askRecon(be);
            CcHolo.Model hm = wantRecon ? CcHolo.recon(be.getLevel(), be.getBlockPos()) : null;
            boolean recon = hm != null;
            if (hm == null) hm = CcHolo.home(be.getLevel(), be.getBlockPos(), cx, cz);
            Area area = new Area(hm.cx, hm.cz, hm.halfB, CcModelSpec.HOLO_MODEL_HALF, CcModelSpec.HOLO_MODEL_BASE, hm.top, hm.gn, Math.max(6.0F, hm.maxTop));

            modelFrame(ps, f);

            ps.translate(CcModelSpec.HOLO_X, CcModelSpec.HOLO_SCREEN_CY, CcModelSpec.HOLO_Z);
            ps.mulPose(Axis.XP.rotationDegrees(CcModelSpec.HOLO_LEAN_DEG));
            Matrix4f back = new Matrix4f(ps.last().pose()).scale(1.0F, 1.0F, -1.0F).mul(SCREEN).translate(0.0F, -CcModelSpec.HOLO_MODEL_BASE, 0.0F);
            ps.mulPose(SCREEN);
            ps.translate(0.0F, -CcModelSpec.HOLO_MODEL_BASE, 0.0F);
            Matrix4f m = ps.last().pose();
            PoseStack.Pose p = ps.last();
            double tt = be.getLevel().getGameTime() + pt;
            float flicker = (0.96F + 0.04F * (float) Math.sin(tt * 0.7)) * be.holoShown(tt);
            CcHolo.queue(hm, m, 0.84F * flicker);
            CcHolo.queue(hm, back, 0.84F * flicker);
            float top = area.top() + 0.5F, half = area.half(), fl = area.floor();
            double tableY = be.getBlockPos().getY();
            CcTarget des = be.designated();
            ReconMapPayload rm = recon ? ReconView.map : null;

            VertexConsumer vc = buf.getBuffer(RenderType.lightning());

            float scan = fl + (float) ((tt * 0.012) % 1.0) * (area.relief() + 2.0F);

            if (area.inside(cx, cz)) {
                float tx = area.hx(cx), tz = area.hz(cz);
                post(vc, m, tx, area.ground(cx, cz), tz, 3.0F, 0.25F, 0xC0A0E0FF, 0xFFFFFFFF);
            }
            int[] as = be.assets();
            for (int i = 0; i + 2 < as.length; i += 3) {
                int col = ASSET_COL[Math.max(0, Math.min(ASSET_COL.length - 1, as[i + 2]))];
                boolean orbit = as[i + 2] >= CcMapTableBlockEntity.ASSET_ORBIT_OWN;
                if (!area.inside(as[i] + 0.5, as[i + 1] + 0.5)) {
                    if (!orbit) continue;
                    double ang = Math.atan2(as[i + 1] + 0.5 - area.cz(), as[i] + 0.5 - area.cx());
                    float rx = (float) Math.cos(ang), rz = (float) Math.sin(ang), k = half / Math.max(Math.abs(rx), Math.abs(rz));
                    float ex = rx * k, ez = rz * k, y = fl + 0.6F, w = 0.6F;
                    quad(vc, m, ex - w, y, ez - w, ex - w, y, ez + w, ex + w, y, ez + w, ex + w, y, ez - w, (col & 0x00FFFFFF) | 0xE0000000);
                    continue;
                }
                float gy = area.ground(as[i] + 0.5, as[i + 1] + 0.5);
                if (orbit) post(vc, m, area.hx(as[i] + 0.5), gy, area.hz(as[i + 1] + 0.5), top + 10.0F - gy, 0.2F, (col & 0x00FFFFFF) | 0xA0000000, col);
                else post(vc, m, area.hx(as[i] + 0.5), gy, area.hz(as[i + 1] + 0.5), 4.0F, 0.25F, (col & 0x00FFFFFF) | 0xD0000000, col);
            }
            if (rm != null) {
                int[] ic = rm.icons();
                for (int i = 0; i + 2 < ic.length; i += 3) {
                    if (!area.inside(ic[i] + 0.5, ic[i + 1] + 0.5)) continue;
                    int k = Math.max(0, Math.min(ReconMapScreen.ICON_COL.length - 1, ic[i + 2]));
                    post(vc, m, area.hx(ic[i] + 0.5), area.ground(ic[i] + 0.5, ic[i + 1] + 0.5), area.hz(ic[i + 1] + 0.5), 3.5F, 0.22F, 0xD0FF2020, ReconMapScreen.ICON_COL[k]);
                }
                ReconMapPayload live = ReconView.last;
                if (live != null && (live.status() == Recon.STATUS_OVERHEAD || live.status() == Recon.STATUS_DRONE || live.status() == Recon.STATUS_STATION)) {
                    int[] d = live.dots();
                    for (int i = 0; i + 1 < d.length; i += 2) {
                        if (!area.inside(d[i] + 0.5, d[i + 1] + 0.5)) continue;
                        post(vc, m, area.hx(d[i] + 0.5), area.ground(d[i] + 0.5, d[i + 1] + 0.5), area.hz(d[i + 1] + 0.5), 1.6F, 0.25F, 0xE0FFE040, 0xFFFFF080);
                    }
                }
            }
            if (des != null) {
                double dx = des.x() + 0.5, dz = des.z() + 0.5;
                if (area.inside(dx, dz)) {
                    float pulse = 0.75F + 0.25F * (float) Math.sin(tt * 0.3);
                    int a = (int) (200 * pulse);
                    post(vc, m, area.hx(dx), area.ground(dx, dz), area.hz(dz), top + 12.0F - area.ground(dx, dz), 0.35F, (a << 24) | 0xFF2020, 0xFFFFFFFF);
                } else {
                    double ang = Math.atan2(dz - area.cz(), dx - area.cx());
                    float rx = (float) Math.cos(ang), rz = (float) Math.sin(ang), k = half / Math.max(Math.abs(rx), Math.abs(rz));
                    float ex = rx * k, ez = rz * k, y = fl + 0.6F;
                    quad(vc, m, ex - 0.8F, y, ez - 0.8F, ex - 0.8F, y, ez + 0.8F, ex + 0.8F, y, ez + 0.8F, ex + 0.8F, y, ez - 0.8F, 0xE0FF2020);
                }
            }
            List<Flight> shown = new ArrayList<>();
            for (Flight fl2 : ClientFlights.FLIGHTS.values()) if (flight(null, vc, p, m, fl2, area, tableY, tt, me)) shown.add(fl2);
            sats(null, vc, p, m, be.getOwner(), cx, cz, half, top);

            for (DroneView.Contact c : DroneView.contacts()) {
                float[] q = holoPoint(new Vec3(c.x(), c.y(), c.z()), area, tableY);
                if (q == null) continue;
                boolean stranger = c.owner().getMostSignificantBits() == 0L && c.owner().getLeastSignificantBits() == 0L;
                diamond(vc, m, q, 0.55F, c.phase() == Drones.FALLING ? 0xFF808080 : stranger ? 0xFFFF40C0 : 0xFF40E0FF);
            }
            for (BombView.Contact b : BombView.contacts()) {
                float[] q = holoPoint(new Vec3(b.x(), b.y(), b.z()), area, tableY);
                if (q != null) diamond(vc, m, q, 0.4F, 0xFFFFA040);
            }

            VertexConsumer lines = buf.getBuffer(RenderType.lines());
            int edge = recon ? 0xB060C8FF : 0xA060C0FF;
            float rt = fl + area.relief();
            float[] cxs = { -half, half, half, -half }, czs = { -half, -half, half, half };
            for (int c = 0; c < 4; c++) {
                float ax = cxs[c], az = czs[c], bx = cxs[(c + 1) % 4], bz = czs[(c + 1) % 4];
                line(lines, p, ax, fl, az, bx, fl, bz, edge);
                line(lines, p, ax, scan, az, bx, scan, bz, 0x9070D8FF);
                line(lines, p, ax, rt, az, bx, rt, bz, 0x2860C0FF);
                line(lines, p, ax, fl, az, ax, rt, az, 0x3060C0FF);
            }
            for (Flight fl2 : ClientFlights.FLIGHTS.values()) flight(lines, null, p, m, fl2, area, tableY, tt, me);
            sats(lines, null, p, m, be.getOwner(), cx, cz, half, top);
            for (CommandCenter.Warning w : be.warnings()) warning(lines, p, w, area, tt);
            for (BombView.Contact b : BombView.contacts()) {
                float[] q = holoPoint(new Vec3(b.x(), b.y(), b.z()), area, tableY);
                if (q == null || !area.inside(b.ix(), b.iz())) continue;
                line(lines, p, q[0], q[1], q[2], area.hx(b.ix()), area.ground(b.ix(), b.iz()) + 0.2F, area.hz(b.iz()), 0xC0FFA040);
            }

            int labels = 0;
            for (Flight fl2 : shown) {
                if (labels++ >= MAX_LABELS) break;
                float[] q = holoPoint(fl2.position(tt), area, tableY);
                if (q == null) continue;
                long left = Math.max(0L, (long) (fl2.endTime() - tt));
                String hyper = fl2.build() != null && fl2.build().hypersonic() ? "HYPER " : "";
                billboard(ps, buf, hyper + (fl2.targetKnown() ? "T-" + OrbitMath.mmss(left * 50L) : "T-?"), q[0], q[1] + 1.6F, q[2], 1.6F, me != null && fl2.isOwnedBy(me, Minecraft.getInstance().player.getGameProfile().getName()) ? 0xFF80C0FF : 0xFFFF6060);
            }
            if (wantRecon && !recon) billboard(ps, buf, "NO RECON IMAGE", 0.0F, top + 4.0F, 0.0F, 2.0F, 0xFFFFA0A0);
            if (des != null && area.inside(des.x() + 0.5, des.z() + 0.5))
                billboard(ps, buf, des.x() + " " + des.z(), area.hx(des.x() + 0.5), top + 13.5F, area.hz(des.z() + 0.5), 1.6F, 0xFFFF8080);
            ps.popPose();
        }

        private static void projector(VertexConsumer vc, Matrix4f m, float half, float fl, float flicker) {
            int a = (int) (0x1C * flicker), cone = (a << 24) | 0x3C8CFF;
            float r0 = 2.0F, y0 = 1.0F, r1 = half - 0.5F, y1 = fl - 0.05F;
            quad(vc, m, -r0, y0, -r0, r0, y0, -r0, r1, y1, -r1, -r1, y1, -r1, cone);
            quad(vc, m, r0, y0, -r0, r0, y0, r0, r1, y1, r1, r1, y1, -r1, cone);
            quad(vc, m, r0, y0, r0, -r0, y0, r0, -r1, y1, r1, r1, y1, r1, cone);
            quad(vc, m, -r0, y0, r0, -r0, y0, -r0, -r1, y1, -r1, -r1, y1, r1, cone);
            float ex = BOWL_EMIT_X, ez = BOWL_EMIT_Z, w = 0.3F;
            int beam = ((int) (0x60 * flicker) << 24) | 0x60C8FF;
            if (fl - EMIT_Y < 2.0F) return;
            for (int c = 0; c < 4; c++) {
                float sx = (c & 1) == 0 ? -ex : ex, sz = (c & 2) == 0 ? -ez : ez, tx = Math.signum(sx) * (half - 1.0F), tz = Math.signum(sz) * (half - 1.0F);
                quad(vc, m, sx - w, EMIT_Y, sz, sx + w, EMIT_Y, sz, tx + w, fl, tz, tx - w, fl, tz, beam);
                quad(vc, m, sx, EMIT_Y, sz - w, sx, EMIT_Y, sz + w, tx, fl, tz + w, tx, fl, tz - w, beam);
            }
        }

        private static final Matrix4f SCREEN = new Matrix4f(-1, 0, 0, 0,  0, 0, -1, 0,  0, -1, 0, 0,  0, 0, 0, 1);

        private static final float BOWL_EMIT_X = 28.4F, BOWL_EMIT_Z = 12.4F, EMIT_Y = 14.0F;

        private static void post(VertexConsumer vc, Matrix4f m, float x, float y0, float z, float h, float w, int col, int cap) {
            float y1 = y0 + h;
            quad(vc, m, x - w, y0, z, x + w, y0, z, x + w, y1, z, x - w, y1, z, col);
            quad(vc, m, x, y0, z - w, x, y0, z + w, x, y1, z + w, x, y1, z - w, col);
            float c = w * 2.2F;
            quad(vc, m, x - c, y1, z - c, x - c, y1, z + c, x + c, y1, z + c, x + c, y1, z - c, cap);
        }

        private static void billboard(PoseStack ps, MultiBufferSource buf, String s, float x, float y, float z, float height, int colour) {
            Font font = Minecraft.getInstance().font;
            ps.pushPose();
            ps.translate(x, y, z);
            ps.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            float k = height / 9.0F;
            ps.scale(k, -k, k);
            font.drawInBatch(s, -font.width(s) / 2.0F, -4.0F, colour, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0x40000000, FULL);
            ps.popPose();
        }

        private static boolean flight(VertexConsumer lines, VertexConsumer quads, PoseStack.Pose p, Matrix4f m, Flight f, Area area, double tableY, double tt, UUID me) {
            if (f.build() != null && f.build().isInterceptor()) return false;
            boolean mine = me != null && f.isOwnedBy(me, Minecraft.getInstance().player.getGameProfile().getName());
            int col = mine ? 0xFF50A0FF : 0xFFFF4040;
            int segs = 32;
            double t0 = f.start(), t1 = f.endTime();
            float[] prev = null;
            if (lines != null && !f.isDeadReckoned()) for (int k = 0; k <= segs; k++) {
                float[] q = holoPoint(f.position(t0 + (t1 - t0) * k / segs), area, tableY);
                if (q != null && prev != null) line(lines, p, prev[0], prev[1], prev[2], q[0], q[1], q[2], (col & 0x00FFFFFF) | 0xA0000000);
                prev = q;
            }
            if (tt >= t0 && tt <= t1) {
                float[] q = holoPoint(f.position(tt), area, tableY);
                if (q == null) return false;
                float r = 0.7F;
                if (quads != null) quad(quads, m, q[0] - r, q[1], q[2] - r, q[0] - r, q[1], q[2] + r, q[0] + r, q[1], q[2] + r, q[0] + r, q[1], q[2] - r, col);
                return true;
            }
            return false;
        }

        private static void diamond(VertexConsumer vc, Matrix4f m, float[] q, float r, int col) {
            quad(vc, m, q[0] - r, q[1], q[2], q[0], q[1], q[2] + r, q[0] + r, q[1], q[2], q[0], q[1], q[2] - r, col);
        }

        private static float[] holoPoint(Vec3 v, Area area, double tableY) {
            if (!area.inside(v.x, v.z)) return null;
            float y = area.top() + 1.0F + (float) Math.max(0.0, Math.min(1.0, (v.y - tableY) / 400.0)) * 8.0F;
            return new float[] { area.hx(v.x), y, area.hz(v.z) };
        }

        private static void warning(VertexConsumer lines, PoseStack.Pose p, CommandCenter.Warning w, Area area, double tt) {
            if (w.sat()) return;
            float base = area.top() + 0.6F;
            float[] prev = null;
            int segs = 24;
            for (int k = 0; k <= segs; k++) {
                double t = k / (double) segs, x = w.ox() + (w.ix() - w.ox()) * t, z = w.oz() + (w.iz() - w.oz()) * t;
                float[] q = area.inside(x, z) ? new float[] { area.hx(x), base + (float) Math.sin(Math.PI * t) * 6.0F, area.hz(z) } : null;
                if (q != null && prev != null) line(lines, p, prev[0], prev[1], prev[2], q[0], q[1], q[2], 0xFFFF3030);
                prev = q;
            }
            if (!area.inside(w.ix(), w.iz())) return;
            float ix = area.hx(w.ix()), iz = area.hz(w.iz()), y = area.ground(w.ix(), w.iz()) + 0.3F;
            float r = 1.2F + 1.2F * (float) ((tt * 0.08) % 1.0);
            for (int i = 0; i < 16; i++) {
                double a0 = Math.PI * 2.0 * i / 16, a1 = Math.PI * 2.0 * (i + 1) / 16;
                line(lines, p, ix + r * (float) Math.cos(a0), y, iz + r * (float) Math.sin(a0), ix + r * (float) Math.cos(a1), y, iz + r * (float) Math.sin(a1), 0xFFFF3030);
            }
        }

        private static void sats(VertexConsumer lines, VertexConsumer quads, PoseStack.Pose p, Matrix4f m, UUID owner, double cx, double cz, float half, float top) {
            if (owner == null) return;
            long now = OrbitView.now();
            for (OrbitMath.Sat s : OrbitView.sats()) {
                if (!owner.equals(s.owner()) || now >= s.endMs() || !OrbitMath.isOverhead(s, cx, cz, now)) continue;
                float[] last = null;
                for (int k = 6; k >= 0; k--) {
                    long t = now - k * 10_000L;
                    if (!OrbitMath.isOverhead(s, cx, cz, t)) { last = null; continue; }
                    float[] q = skyPoint(OrbitMath.sky(s, cx, cz, t), half, top);
                    float r = k == 0 ? 0.6F : 0.25F;
                    if (quads != null) quad(quads, m, q[0] - r, q[1], q[2] - r, q[0] - r, q[1], q[2] + r, q[0] + r, q[1], q[2] + r, q[0] + r, q[1], q[2] - r, k == 0 ? 0xFFFFFFFF : 0x90FFFFFF);
                    if (lines != null && last != null) line(lines, p, last[0], last[1], last[2], q[0], q[1], q[2], 0x60FFFFFF);
                    last = q;
                }
            }
        }

        private static float[] skyPoint(double[] sky, float half, float top) {
            double el = sky[1], flat = Math.sqrt(sky[2] * sky[2] + sky[4] * sky[4]);
            float r = (float) ((90.0 - el) / 90.0) * half;
            float ux = flat < 1.0E-6 ? 0.0F : (float) (sky[2] / flat), uz = flat < 1.0E-6 ? 0.0F : (float) (sky[4] / flat);
            return new float[] { ux * r, top + 9.0F + (float) (el / 90.0) * 4.0F, uz * r };
        }

        @Override
        public AABB getRenderBoundingBox(CcMapTableBlockEntity be) {
            return new AABB(be.getBlockPos()).inflate(4, 0, 4).expandTowards(0, 6, 0);
        }
    }

    public static class WarningBoard extends StagedUnitRenderer<CcWarningBoardBlockEntity> {

        private static final double MIN_SPAN = 2000.0;
        private static final int RED = 0xFFFF3030, RED_DIM = 0x80FF3030, BLUE = 0xFF50A0FF, BLUE_DIM = 0x8050A0FF;

        public WarningBoard() { super("cc_warning_board", 3); }

        private record Track(int ox, int oz, int ix, int iz, long launchGt, long impactGt, boolean sat) {}

        @Override
        public void render(CcWarningBoardBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            super.render(be, pt, ps, buf, light, overlay);
            if (!be.isOnline() || be.getLevel() == null || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
            Direction f = be.getBlockState().getValue(MachineBlock.FACING);
            modelFrame(ps, f);
            Matrix4f m = ps.last().pose();
            PoseStack.Pose p = ps.last();
            float x0 = CcModelSpec.BOARD_X0, x1 = CcModelSpec.BOARD_X1, y0 = CcModelSpec.BOARD_Y0, y1 = CcModelSpec.BOARD_Y1;
            float z = CcModelSpec.BOARD_Z - 0.05F, zl = z - 0.05F;
            float cxm = (x0 + x1) / 2.0F, cym = (y0 + y1) / 2.0F;
            double bx = be.getBlockPos().getX() + 0.5, bz = be.getBlockPos().getZ() + 0.5;

            boolean out = be.mode() == CcWarningBoardBlockEntity.MODE_OUTBOUND;
            List<Track> tracks = new ArrayList<>();
            if (out) for (CcWarningBoardBlockEntity.Out o : be.outbound()) tracks.add(new Track(o.ox(), o.oz(), o.tx(), o.tz(), o.launchGt(), o.impactGt(), o.sat()));
            else for (CommandCenter.Warning w : be.warnings()) tracks.add(new Track(w.ox(), w.oz(), w.ix(), w.iz(), w.launchGt(), w.impactGt(), w.sat()));

            float halfW = (x1 - x0) / 2.0F, halfH = (y1 - y0) / 2.0F;
            double span = MIN_SPAN;
            for (Track w : tracks)
                span = Math.max(span, 1.15 * Math.max(Math.max(Math.abs(w.oz() - bz), Math.abs(w.iz() - bz)),
                        Math.max(Math.abs(w.ox() - bx), Math.abs(w.ix() - bx)) * halfH / halfW));
            float s = (float) (halfH / span);
            double gridStep = span <= 4000 ? 500.0 : span <= 16000 ? 1000.0 : span <= 64000 ? 5000.0 : 20000.0;
            rect(buf.getBuffer(RenderType.debugQuads()), m, x0, y0, x1, y1, z, screenBg(be, out ? 0xF0081828 : 0xF0081420));

            int gridN = (int) Math.ceil(span * halfW / halfH / gridStep) + 1;
            for (int k = -gridN; k <= gridN; k++) {
                float gx = cxm - (float) (k * gridStep - (bx % gridStep)) * s;
                if (gx > x0 && gx < x1) line(buf.getBuffer(RenderType.lines()), p, gx, y0, zl, gx, y1, zl, k == 0 ? 0x5060A0E0 : 0x30406080);
                float gy = cym - (float) (k * gridStep - (bz % gridStep)) * s;
                if (gy > y0 && gy < y1) line(buf.getBuffer(RenderType.lines()), p, x0, gy, zl, x1, gy, zl, 0x30406080);
            }

            rect(buf.getBuffer(RenderType.debugQuads()), m, cxm - 0.8F, cym - 0.8F, cxm + 0.8F, cym + 0.8F, zl, 0xFF50A0FF);

            CcBuildingBlockEntity bb = be.building();
            CcTarget des = bb != null ? bb.designated() : null;
            if (des != null) {
                float dx = clampX(cxm - (float) (des.x() + 0.5 - bx) * s), dy = clampY(cym - (float) (des.z() + 0.5 - bz) * s);
                rect(buf.getBuffer(RenderType.debugQuads()), m, dx - 0.6F, dy - 0.6F, dx + 0.6F, dy + 0.6F, zl - 0.03F, 0xFFFF1010);
                line(buf.getBuffer(RenderType.lines()), p, clampX(dx - 1.6F), dy, zl - 0.03F, clampX(dx + 1.6F), dy, zl - 0.03F, 0xFFFFFFFF);
                line(buf.getBuffer(RenderType.lines()), p, dx, clampY(dy - 1.6F), zl - 0.03F, dx, clampY(dy + 1.6F), zl - 0.03F, 0xFFFFFFFF);
            }
            long now = be.getLevel().getGameTime();
            double tt = now + pt;
            boolean blink = ((long) (tt / 6.0)) % 2 == 0;
            int full = out ? BLUE : RED, dim = out ? BLUE_DIM : RED_DIM;
            for (Track w : tracks) {
                float sx = cxm - (float) (w.ox() - bx) * s, sy = cym - (float) (w.oz() - bz) * s;
                float ix = cxm - (float) (w.ix() - bx) * s, iy = cym - (float) (w.iz() - bz) * s;
                float k = w.impactGt() > w.launchGt() ? (float) Math.max(0.0, Math.min(1.0, (tt - w.launchGt()) / (double) (w.impactGt() - w.launchGt()))) : 1.0F;
                rect(buf.getBuffer(RenderType.debugQuads()), m, clampX(sx - 0.7F), clampY(sy - 0.7F), clampX(sx + 0.7F), clampY(sy + 0.7F), zl,
                        out ? 0xFF80C8FF : 0xFFFF8040);
                if (w.sat()) {
                    line(buf.getBuffer(RenderType.lines()), p, clampX(sx), clampY(sy), zl, clampX(sx), clampY(sy + 4.0F), zl, 0xFFFFFFFF);
                    continue;
                }
                float dx = ix - sx, dy = iy - sy, len = (float) Math.sqrt(dx * dx + dy * dy);
                float nx = len < 1.0E-3F ? 0.0F : -dy / len, ny = len < 1.0E-3F ? 1.0F : dx / len;
                if (ny < 0) { nx = -nx; ny = -ny; }
                float px0 = sx, py0 = sy;
                int segs = 20;
                for (int i = 1; i <= segs; i++) {
                    float t = i / (float) segs, bulge = (float) Math.sin(Math.PI * t) * 0.18F * len;
                    float px = sx + dx * t + nx * bulge, py = sy + dy * t + ny * bulge;
                    if (inside(px0, py0) && inside(px, py)) line(buf.getBuffer(RenderType.lines()), p, px0, py0, zl, px, py, zl, t <= k ? full : dim);
                    px0 = px; py0 = py;
                }

                float bk = (float) Math.sin(Math.PI * k) * 0.18F * len;
                float mx = sx + dx * k + nx * bk, my = sy + dy * k + ny * bk;
                if (inside(mx, my) && k < 1.0F)
                    rect(buf.getBuffer(RenderType.debugQuads()), m, mx - 0.5F, my - 0.5F, mx + 0.5F, my + 0.5F, zl - 0.02F, blink ? 0xFFFFFFFF : (out ? 0xFF80C8FF : 0xFFFF6060));

                float r = 1.2F + 0.8F * (float) ((tt * 0.08) % 1.0);
                int ring = out && k >= 1.0F ? 0xFF80FF80 : full;
                for (int i = 0; i < 16; i++) {
                    double a0 = Math.PI * 2.0 * i / 16, a1 = Math.PI * 2.0 * (i + 1) / 16;
                    float ax = ix + r * (float) Math.cos(a0), ay = iy + r * (float) Math.sin(a0), bxx = ix + r * (float) Math.cos(a1), byy = iy + r * (float) Math.sin(a1);
                    if (inside(ax, ay) && inside(bxx, byy)) line(buf.getBuffer(RenderType.lines()), p, ax, ay, zl, bxx, byy, zl, ring);
                }
            }

            rect(buf.getBuffer(RenderType.debugQuads()), m, CcModelSpec.STRIP_X0, CcModelSpec.STRIP_Y0, CcModelSpec.STRIP_X1, CcModelSpec.STRIP_Y1, z, 0xF0101010);
            String txt;
            int colour;
            if (out) {
                long left = be.nextOwnImpact(now);
                txt = left < 0 ? "NO MISSILES OUT" : "HIT T-" + OrbitMath.mmss(left * 50L);
                colour = left < 0 ? 0xFF6080A0 : (blink ? 0xFF50A0FF : 0xFFA0D0FF);

                text(ps, buf, "OUTBOUND", x1 - 9.0F, y1 - 2.2F, zl - 0.04F, 2.6F, 0xFF80C8FF);
            } else {
                long left = be.nearestImpact(now);
                txt = left < 0 ? "NO LAUNCHES" : "IMPACT T-" + OrbitMath.mmss(left * 50L);
                colour = left < 0 ? 0xFF60C060 : (blink ? 0xFFFF4040 : 0xFFFFA0A0);
            }

            int dc = be.defcon();
            if (dc < CcDefcon.PEACE || bb != null) text(ps, buf, "DEFCON " + dc, x0 + 8.0F, y1 - 2.2F, zl - 0.04F, 2.6F,
                    dc <= 1 && !blink ? 0xFF801010 : CcDefcon.colour(dc));

            if (be.domeDown()) text(ps, buf, "DOME DOWN", cxm, y0 + 2.4F, zl - 0.04F, 2.6F, blink ? 0xFFFF3030 : 0xFF801818);

            if (!out && SuperGun.anyOnTheWay(be.warnings(), now)) text(ps, buf, "SUPER GUN", cxm, y1 - 2.2F, zl - 0.04F, 2.6F, blink ? 0xFFFF3030 : 0xFFFFA0A0);
            else if (!out) {
                String tag = threatTag(be.warnings(), now);
                if (tag != null) text(ps, buf, tag, cxm, y1 - 2.2F, zl - 0.04F, 2.6F, blink ? 0xFFFF3030 : 0xFFFFA0A0);
            }
            float th = Math.min((CcModelSpec.STRIP_Y1 - CcModelSpec.STRIP_Y0) * 0.8F,
                    (CcModelSpec.STRIP_X1 - CcModelSpec.STRIP_X0) * 0.92F * 9.0F / Math.max(1, Minecraft.getInstance().font.width(txt)));
            text(ps, buf, txt, (CcModelSpec.STRIP_X0 + CcModelSpec.STRIP_X1) / 2.0F, (CcModelSpec.STRIP_Y0 + CcModelSpec.STRIP_Y1) / 2.0F, zl, th, colour);

            if (PortableJammerBlockEntity.jamsCached(be.getLevel(), be.getBlockPos(), be.getOwner())) {
                long seed = now / 3;
                net.minecraft.client.renderer.MultiBufferSource src = buf;
                VertexConsumer q = src.getBuffer(net.minecraft.client.renderer.RenderType.debugQuads());
                for (int k = 0; k < 26; k++) {
                    long h = (seed * 31 + k) * 0x9E3779B97F4A7C15L;
                    float u = ((h >>> 8) & 1023) / 1023.0F, v = ((h >>> 20) & 1023) / 1023.0F, w = 1.0F + ((h >>> 32) & 7);
                    float gx = x0 + u * (x1 - x0 - w), gy = y0 + v * (y1 - y0 - 0.6F);
                    rect(q, m, gx, gy, gx + w, gy + 0.6F, zl - 0.06F, (k % 3 == 0 ? 0x90FF3030 : 0x70FFFFFF));
                }
                text(ps, buf, "SIGNAL CORRUPT", cxm, cym, zl - 0.08F, 3.0F, ((now / 5) % 2 == 0) ? 0xFFFF4040 : 0xFFFFC0C0);
            }
            ps.popPose();
        }

        private static String threatTag(List<CommandCenter.Warning> ws, long now) {
            boolean drone = false, bomb = false;
            for (CommandCenter.Warning w : ws) {
                if (w.impactGt() < now) continue;
                if (w.hyper()) return "HYPERSONIC";
                drone |= w.what() == CommandCenter.Warning.WHAT_DRONE;
                bomb |= w.what() == CommandCenter.Warning.WHAT_BOMB;
            }
            return drone ? "DRONE" : bomb ? "BOMBS" : null;
        }

        private static float clampX(float x) { return Math.max(CcModelSpec.BOARD_X0, Math.min(CcModelSpec.BOARD_X1, x)); }
        private static float clampY(float y) { return Math.max(CcModelSpec.BOARD_Y0, Math.min(CcModelSpec.BOARD_Y1, y)); }
        private static boolean inside(float x, float y) {
            return x >= CcModelSpec.BOARD_X0 && x <= CcModelSpec.BOARD_X1 && y >= CcModelSpec.BOARD_Y0 && y <= CcModelSpec.BOARD_Y1;
        }
    }

    static void text(PoseStack ps, MultiBufferSource buf, String s, float cx, float cy, float z, float height, int colour) {
        Font font = Minecraft.getInstance().font;
        ps.pushPose();
        ps.translate(cx, cy, z);
        float k = height / 9.0F;
        ps.scale(-k, -k, -k);
        font.drawInBatch(s, -font.width(s) / 2.0F, -4.0F, colour, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, FULL);
        ps.popPose();
    }

    public static class ReconWall extends StagedUnitRenderer<CcReconWallBlockEntity> {

        private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dynamic/cc_recon_wall");
        private static DynamicTexture texture;
        private static int shownVersion = -1;

        public ReconWall() { super("cc_recon_wall", 3); }

        private static long lastDots, lastMap;

        private static void askLive(CcReconWallBlockEntity be) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen instanceof ReconMapScreen || mc.player.blockPosition().distSqr(be.getBlockPos()) > ReconPayload.WALL_VIEW_RANGE * ReconPayload.WALL_VIEW_RANGE) return;
            long t = be.getLevel().getGameTime();
            if (t - lastDots >= Math.max(10, Balance.RECON_DOT_REFRESH_TICKS)) {
                lastDots = t;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ReconPayload(be.getBlockPos(), ReconPayload.DOTS, 0, 0));
            }
            if ((ReconView.map == null || ReconView.stale()) && t - lastMap >= 40) {
                lastMap = t;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ReconPayload(be.getBlockPos(), ReconPayload.MAP, 0, 0));
            }
            if (t - lastBda >= 100) {
                lastBda = t;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ReconPayload(be.getBlockPos(), ReconPayload.BDA, BdaView.key(), 0));
            }
        }

        private static long lastBda = -1000L;

        static final ResourceLocation BDA_BEFORE = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dynamic/cc_bda_before");
        static final ResourceLocation BDA_AFTER = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dynamic/cc_bda_after");
        private static boolean bdaTex;
        private static int bdaVersion = -1;

        static boolean bdaTextures() {
            if (BdaView.version != bdaVersion) {
                bdaVersion = BdaView.version;
                if (bdaTex) {
                    Minecraft.getInstance().getTextureManager().release(BDA_BEFORE);
                    Minecraft.getInstance().getTextureManager().release(BDA_AFTER);
                    bdaTex = false;
                }
                BdaPayload p = BdaView.done();
                if (p != null && p.before().length == CcBda.CROP * CcBda.CROP) {
                    Minecraft.getInstance().getTextureManager().register(BDA_BEFORE, new DynamicTexture(picture(p.before())));
                    Minecraft.getInstance().getTextureManager().register(BDA_AFTER, new DynamicTexture(picture(p.after())));
                    bdaTex = true;
                }
            }
            return bdaTex;
        }

        static NativeImage picture(byte[] col) {
            int n = CcBda.CROP;
            NativeImage img = new NativeImage(n, n, false);
            for (int zz = 0; zz < n; zz++) for (int xx = 0; xx < n; xx++) {
                int v = col[zz * n + xx] & 0xFF;
                img.setPixelRGBA(xx, zz, v < 4 ? 0xFF2A2A2A : MapColor.getColorFromPackedId(v));
            }
            return img;
        }

        private static void texQuad(MultiBufferSource buf, Matrix4f m, ResourceLocation tex, float l, float r, float y0, float y1, float z) {
            VertexConsumer vc = buf.getBuffer(RenderType.text(tex));
            vc.addVertex(m, l, y1, z).setColor(255, 255, 255, 255).setUv(0, 0).setLight(FULL);
            vc.addVertex(m, l, y0, z).setColor(255, 255, 255, 255).setUv(0, 1).setLight(FULL);
            vc.addVertex(m, r, y0, z).setColor(255, 255, 255, 255).setUv(1, 1).setLight(FULL);
            vc.addVertex(m, r, y1, z).setColor(255, 255, 255, 255).setUv(1, 0).setLight(FULL);
            vc.addVertex(m, r, y1, z).setColor(255, 255, 255, 255).setUv(1, 0).setLight(FULL);
            vc.addVertex(m, r, y0, z).setColor(255, 255, 255, 255).setUv(1, 1).setLight(FULL);
            vc.addVertex(m, l, y0, z).setColor(255, 255, 255, 255).setUv(0, 1).setLight(FULL);
            vc.addVertex(m, l, y1, z).setColor(255, 255, 255, 255).setUv(0, 0).setLight(FULL);
        }

        private static final int RECON_S = 10, BDA_S = 6;
        private static final long BDA_SHOW_MS = 30L * 60_000L;

        private static boolean showBda(double tt) {
            BdaPayload p = BdaView.done();
            if (p == null || System.currentTimeMillis() - p.afterMs() > BDA_SHOW_MS) return false;
            return ((long) (tt / 20.0)) % (RECON_S + BDA_S) >= RECON_S;
        }

        private static void damageReport(CcReconWallBlockEntity be, PoseStack ps, MultiBufferSource buf, Matrix4f m, float x0, float x1, float y0, float y1, float z, double tt) {
            BdaPayload p = BdaView.done();
            rect(buf.getBuffer(RenderType.debugQuads()), m, x0, y0, x1, y1, z, screenBg(be, 0xF0101820));
            float sq = Math.min((x1 - x0 - 6.0F) / 2.0F, y1 - y0 - 12.0F), cx = (x0 + x1) / 2.0F, top = y1 - 5.0F, bot = top - sq;
            float lL = cx + 1.0F + sq, lR = cx + 1.0F, rL = cx - 1.0F, rR = cx - 1.0F - sq;
            texQuad(buf, m, BDA_BEFORE, lL, lR, bot, top, z - 0.05F);
            texQuad(buf, m, BDA_AFTER, rL, rR, bot, top, z - 0.05F);
            double span = CcBda.CROP * (double) p.step();
            int[] lost = p.lost();
            for (int i = 0; i + 2 < lost.length; i += 3) {
                double u = (lost[i] - p.cx0()) / span, v = (lost[i + 1] - p.cz0()) / span;
                if (u < 0 || u >= 1 || v < 0 || v >= 1) continue;
                float px = rL - (float) u * sq, py = top - (float) v * sq, r = 0.9F;
                line(buf.getBuffer(RenderType.lines()), ps.last(), px - r, py - r, z - 0.1F, px + r, py + r, z - 0.1F, 0xFFFF2020);
                line(buf.getBuffer(RenderType.lines()), ps.last(), px - r, py + r, z - 0.1F, px + r, py - r, z - 0.1F, 0xFFFF2020);
            }
            double iu = (p.x() - p.cx0()) / span, iv = (p.z() - p.cz0()) / span;
            for (float l : new float[] { lL, rL }) {
                float px = l - (float) iu * sq, py = top - (float) iv * sq;
                line(buf.getBuffer(RenderType.lines()), ps.last(), px - 1.2F, py, z - 0.1F, px + 1.2F, py, z - 0.1F, 0xFFFFFFFF);
                line(buf.getBuffer(RenderType.lines()), ps.last(), px, py - 1.2F, z - 0.1F, px, py + 1.2F, z - 0.1F, 0xFFFFFFFF);
            }
            boolean blink = ((long) (tt / 8.0)) % 2 == 0;
            textFit(ps, buf, "DAMAGE REPORT  " + p.x() + " " + p.z(), cx, y1 - 2.4F, z - 0.1F, 2.4F, x1 - x0 - 2.0F, 0xFFFFD060);
            textFit(ps, buf, "BEFORE", (lL + lR) / 2.0F, bot - 1.8F, z - 0.1F, 2.0F, sq, 0xFF8090A0);
            textFit(ps, buf, "AFTER", (rL + rR) / 2.0F, bot - 1.8F, z - 0.1F, 2.0F, sq, 0xFF8090A0);
            int n = p.lost().length / 3, all = p.beforeIcons().length / 3;
            textFit(ps, buf, "MACHINES DESTROYED: " + n + " / " + all, cx, y0 + 2.0F, z - 0.1F, 2.6F, x1 - x0 - 2.0F, n > 0 ? (blink ? 0xFFFF4040 : 0xFFFFA0A0) : 0xFF60C060);
        }

        private static void liveCam(CcReconWallBlockEntity be, Flight f, PoseStack ps, MultiBufferSource buf, Matrix4f m, float x0, float x1, float y0, float y1, float z, double tt) {
            rect(buf.getBuffer(RenderType.debugQuads()), m, x0, y0, x1, y1, z, screenBg(be, 0xF0060C14));
            boolean blink = ((long) (tt / 6.0)) % 2 == 0;
            float h = y1 - y0 - 6.0F, mapL = x1 - 1.5F, mapR = mapL - h, mapB = y0 + 1.5F, mapT = mapB + h, cxm = (mapL + mapR) / 2.0F, cym = (mapB + mapT) / 2.0F;
            rect(buf.getBuffer(RenderType.debugQuads()), m, mapR, mapB, mapL, mapT, z - 0.02F, 0xF0081A26);
            double tclamp = Math.max(f.start(), Math.min(f.endTime(), tt));
            Vec3 now = f.position(tclamp), ahead = f.position(Math.min(f.endTime(), tclamp + 2.0));
            double ox = f.ox(), oz = f.oz(), tx = f.tx() + 0.5, tz = f.tz() + 0.5;
            double mx = (ox + tx) / 2.0, mz = (oz + tz) / 2.0;
            double span = Math.max(256.0, 0.6 * Math.max(Math.abs(tx - ox), Math.abs(tz - oz)));
            float s = (float) (h / 2.0 / span);

            for (int k = -2; k <= 2; k++) {
                float gx = cxm - k * h / 4.0F, gy = cym + k * h / 4.0F;
                line(buf.getBuffer(RenderType.lines()), ps.last(), gx, mapB, z - 0.04F, gx, mapT, z - 0.04F, 0x30406080);
                line(buf.getBuffer(RenderType.lines()), ps.last(), mapR, gy, z - 0.04F, mapL, gy, z - 0.04F, 0x30406080);
            }

            float px0 = 0, py0 = 0;
            for (int i = 0; i <= 24; i++) {
                double t = f.start() + (f.endTime() - f.start()) * i / 24.0;
                Vec3 v = f.position(t);
                float px = cxm - (float) (v.x - mx) * s, py = cym - (float) (v.z - mz) * s;
                if (i > 0) line(buf.getBuffer(RenderType.lines()), ps.last(), px0, py0, z - 0.06F, px, py, z - 0.06F, t <= tclamp ? 0xFF50A0FF : 0x6050A0FF);
                px0 = px; py0 = py;
            }
            float sx = cxm - (float) (ox - mx) * s, sy = cym - (float) (oz - mz) * s;
            rect(buf.getBuffer(RenderType.debugQuads()), m, sx - 0.6F, sy - 0.6F, sx + 0.6F, sy + 0.6F, z - 0.07F, 0xFFC0D0E0);
            float ix = cxm - (float) (tx - mx) * s, iy = cym - (float) (tz - mz) * s, rr = 1.4F + 0.5F * (float) ((tt * 0.1) % 1.0);
            for (int i = 0; i < 16; i++) {
                double a0 = Math.PI * 2.0 * i / 16, a1 = Math.PI * 2.0 * (i + 1) / 16;
                line(buf.getBuffer(RenderType.lines()), ps.last(), ix + rr * (float) Math.cos(a0), iy + rr * (float) Math.sin(a0), z - 0.07F,
                        ix + rr * (float) Math.cos(a1), iy + rr * (float) Math.sin(a1), z - 0.07F, 0xFFFF3030);
            }

            float qx = cxm - (float) (now.x - mx) * s, qy = cym - (float) (now.z - mz) * s;
            double hx = -(ahead.x - now.x), hy = -(ahead.z - now.z), hl = Math.hypot(hx, hy);
            if (tt < f.endTime() && hl > 1.0E-4) {
                double a = Math.atan2(hy, hx), half = Math.toRadians(25.0);
                float len = 4.0F;
                float ax = qx + len * (float) Math.cos(a - half), ay = qy + len * (float) Math.sin(a - half);
                float bx = qx + len * (float) Math.cos(a + half), by = qy + len * (float) Math.sin(a + half);
                line(buf.getBuffer(RenderType.lines()), ps.last(), qx, qy, z - 0.08F, ax, ay, z - 0.08F, 0xFFFFE060);
                line(buf.getBuffer(RenderType.lines()), ps.last(), qx, qy, z - 0.08F, bx, by, z - 0.08F, 0xFFFFE060);
                line(buf.getBuffer(RenderType.lines()), ps.last(), ax, ay, z - 0.08F, bx, by, z - 0.08F, 0x80FFE060);
            }
            rect(buf.getBuffer(RenderType.debugQuads()), m, qx - 0.6F, qy - 0.6F, qx + 0.6F, qy + 0.6F, z - 0.09F, blink ? 0xFFFFFFFF : 0xFF50A0FF);

            float pl = mapR - 1.0F, pr = x0 + 1.0F, pw = pl - pr, pc = (pl + pr) / 2.0F;
            boolean hit = tt >= f.endTime();
            textFit(ps, buf, (blink ? "REC  " : "     ") + "LIVE CAM", pc, y1 - 3.0F, z - 0.1F, 2.6F, pw, 0xFFFF4040);
            textFit(ps, buf, "#" + f.id() + "  T" + f.tier(), pc, y1 - 7.0F, z - 0.1F, 2.0F, pw, 0xFF8090A0);
            textFit(ps, buf, "X " + Math.round(now.x), pc, y1 - 12.0F, z - 0.1F, 2.2F, pw, 0xFF9FE3FF);
            textFit(ps, buf, "Z " + Math.round(now.z), pc, y1 - 15.5F, z - 0.1F, 2.2F, pw, 0xFF9FE3FF);
            textFit(ps, buf, "ALT " + Math.round(now.y), pc, y1 - 19.0F, z - 0.1F, 2.2F, pw, 0xFFE0E0E0);
            textFit(ps, buf, hit ? "IMPACT" : "T-" + OrbitMath.mmss((long) Math.max(0.0, f.endTime() - tt) * 50L), pc, y0 + 4.0F, z - 0.1F, 3.0F, pw,
                    hit ? (blink ? 0xFFFF4040 : 0xFFFFC040) : 0xFFFFD060);
        }

        private static boolean ensureTexture() {
            if (ReconView.version != shownVersion) {
                shownVersion = ReconView.version;
                if (texture != null) { Minecraft.getInstance().getTextureManager().release(TEX); texture = null; }
                ReconMapPayload mp = ReconView.map;
                if (mp != null && mp.n() > 0 && mp.col().length == mp.n() * mp.n()) {
                    int n = mp.n();
                    NativeImage img = new NativeImage(n, n, false);
                    byte[] col = mp.col();
                    for (int zz = 0; zz < n; zz++) for (int xx = 0; xx < n; xx++) {
                        int v = col[zz * n + xx] & 0xFF;
                        img.setPixelRGBA(xx, zz, v < 4 ? 0xFF2A2A2A : MapColor.getColorFromPackedId(v));
                    }
                    texture = new DynamicTexture(img);
                    Minecraft.getInstance().getTextureManager().register(TEX, texture);
                }
            }
            return texture != null;
        }

        @Override
        public void render(CcReconWallBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            super.render(be, pt, ps, buf, light, overlay);
            if (!be.isOnline() || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
            Direction f = be.getBlockState().getValue(MachineBlock.FACING);
            modelFrame(ps, f);
            Matrix4f m = ps.last().pose();
            float x0 = CcModelSpec.WALL_X0, x1 = CcModelSpec.WALL_X1, y0 = CcModelSpec.WALL_Y0, y1 = CcModelSpec.WALL_Y1, z = CcModelSpec.WALL_Z - 0.05F;
            UUID me = me();
            boolean own = me != null && me.equals(be.getOwner());
            if (own) askLive(be);

            double tt = be.getLevel() != null ? be.getLevel().getGameTime() + pt : 0.0;
            CcBuildingBlockEntity bb = be.building();
            long cam = bb != null && be.getLevel() != null ? bb.liveCam(be.getLevel().getGameTime()) : 0L;
            Flight cf = cam != 0L ? ClientFlights.FLIGHTS.get(cam) : null;
            if (cf != null && tt <= cf.endTime() + CcMissileCamBlockEntity.LIVE_LINGER_TICKS) {
                liveCam(be, cf, ps, buf, m, x0, x1, y0, y1, z, tt);
            } else if (own && showBda(tt) && bdaTextures()) {
                damageReport(be, ps, buf, m, x0, x1, y0, y1, z, tt);
            } else if (own && ensureTexture()) {

                float h = y1 - y0, w = Math.min(h, x1 - x0), cx = (x0 + x1) / 2.0F;
                float l = cx + w / 2.0F, r = cx - w / 2.0F;
                VertexConsumer vc = buf.getBuffer(RenderType.text(TEX));
                vc.addVertex(m, l, y1, z).setColor(255, 255, 255, 255).setUv(0, 0).setLight(FULL);
                vc.addVertex(m, l, y0, z).setColor(255, 255, 255, 255).setUv(0, 1).setLight(FULL);
                vc.addVertex(m, r, y0, z).setColor(255, 255, 255, 255).setUv(1, 1).setLight(FULL);
                vc.addVertex(m, r, y1, z).setColor(255, 255, 255, 255).setUv(1, 0).setLight(FULL);
                vc.addVertex(m, r, y1, z).setColor(255, 255, 255, 255).setUv(1, 0).setLight(FULL);
                vc.addVertex(m, r, y0, z).setColor(255, 255, 255, 255).setUv(1, 1).setLight(FULL);
                vc.addVertex(m, l, y0, z).setColor(255, 255, 255, 255).setUv(0, 1).setLight(FULL);
                vc.addVertex(m, l, y1, z).setColor(255, 255, 255, 255).setUv(0, 0).setLight(FULL);
                VertexConsumer q = buf.getBuffer(RenderType.debugQuads());
                int side = screenBg(be, 0xF0101820);
                if (x0 < r) rect(q, m, x0, y0, r, y1, z + 0.01F, side);
                if (l < x1) rect(q, m, l, y0, x1, y1, z + 0.01F, side);

                ReconMapPayload live = ReconView.last, mp = ReconView.map;
                if (live != null && mp != null && mp.n() > 0 && (live.status() == Recon.STATUS_OVERHEAD || live.status() == Recon.STATUS_DRONE || live.status() == Recon.STATUS_STATION)) {
                    double span = mp.n() * (double) mp.step();
                    int[][] sets = { live.dots(), live.flights() };
                    for (int s = 0; s < 2; s++) {
                        int[] a = sets[s];
                        int stride = s == 0 ? 2 : 3;
                        for (int i = 0; i + stride - 1 < a.length; i += stride) {
                            double u = (a[i] - mp.x0()) / span, v = (a[i + 1] - mp.z0()) / span;
                            if (u < 0 || u >= 1 || v < 0 || v >= 1) continue;
                            float px = l - (float) u * w, py = y1 - (float) v * h, rr = 0.7F;
                            int col = s == 0 ? 0xFFFFE040 : (a[i + 2] == 1 ? 0xFF50A0FF : 0xFFFF3030);
                            rect(buf.getBuffer(RenderType.debugQuads()), m, px - rr, py - rr, px + rr, py + rr, z - 0.05F, col);
                        }
                    }
                }
            } else {
                VertexConsumer q = buf.getBuffer(RenderType.debugQuads());
                rect(q, m, x0, y0, x1, y1, z, screenBg(be, 0xF0101820));
                VertexConsumer lines = buf.getBuffer(RenderType.lines());
                PoseStack.Pose p = ps.last();
                for (float gx = x0 + 4.0F; gx < x1; gx += 4.0F) line(lines, p, gx, y0, z - 0.05F, gx, y1, z - 0.05F, 0x30507090);
                for (float gy = y0 + 4.0F; gy < y1; gy += 4.0F) line(lines, p, x0, gy, z - 0.05F, x1, gy, z - 0.05F, 0x30507090);
                text(ps, buf, "NO IMAGE", (x0 + x1) / 2.0F, (y0 + y1) / 2.0F, z - 0.1F, 4.0F, 0xFF8090A0);
            }
            ps.popPose();
        }
    }
}
