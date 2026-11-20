package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HoloTableRenderer extends StagedUnitRenderer<HoloTableBlockEntity> {

    private static final double VIEW_RANGE = 40.0;
    private static final int MAX_LABELS = 6;
    private static final int[] TRACK_COL = { 0xFF40E8FF, 0xFF4F8FFF, 0xFFFF4040, 0xFFFFC040, 0xFF70FF90 };

    private static final int[] ASSET_COL = { 0xFFFF9030, 0xFF40E060, 0xFFFFE040, 0xFF40D0FF, 0xFF5090FF, 0xFF70F0FF, 0xFFFF3030, 0xFFFF40FF, 0xFFC8E8FF, 0xFFB060FF };
    private static final float[][] HEADS = { { -34.0F, -2.0F }, { 34.0F, -2.0F }, { -34.0F, 34.0F }, { 34.0F, 34.0F } };
    private static long lastMapAsk = -1000L, lastDotsAsk = -1000L;

    public HoloTableRenderer() { super("holo_table", 2, 3); }

    private static void askRecon(HoloTableBlockEntity be) {
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
    public void render(HoloTableBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        super.render(be, pt, ps, buf, light, overlay);
        if (be.getLevel() == null || !be.isRunning() || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > VIEW_RANGE * VIEW_RANGE) return;
        double tt = be.getLevel().getGameTime() + pt;
        float shown = HoloAnim.up(be, tt, 8.0, 14.0);
        if (shown <= 0.02F) return;
        UUID me = CcRenderers.me();
        boolean wantRecon = be.view() == CcMapTableBlockEntity.VIEW_RECON && me != null && me.equals(be.getOwner());
        if (wantRecon) askRecon(be);
        ClientHoloTable.View v = ClientHoloTable.view(be);
        Direction f = v.f();
        float scan = HoloAnim.scanFront(be, tt);
        ClientHoloTable.Model hm = ClientHoloTable.model(be, v, scan);

        CcRenderers.modelFrame(ps, f);
        Matrix4f m = ps.last().pose();
        PoseStack.Pose p = ps.last();
        float flicker = 0.95F + 0.05F * (float) Math.sin(tt * 0.7);
        ClientHoloTable.queue(hm, m, 0.82F * flicker * shown);
        float relief = Math.max(4.0F, hm.maxTop), top = ClientHoloTable.FLOOR + relief + 1.0F;
        double tableY = be.getBlockPos().getY(), tx = be.getBlockPos().getX() + 0.5, tz = be.getBlockPos().getZ() + 0.5;
        String myName = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getGameProfile().getName() : "";
        ReconMapPayload rm = v.recon() ? ReconView.map : null;
        CcTarget des = be.designated();
        VertexConsumer q = buf.getBuffer(RenderType.lightning());

        float[] tp = v.toModel(tx, tz);
        if (ClientHoloTable.View.inside(tp)) post(q, m, tp[0], ground(hm, tp), tp[1], 3.0F, 0.25F, 0xC0A0E0FF, 0xFFFFFFFF);
        int[] as = be.assets();
        for (int i = 0; i + 2 < as.length; i += 3) {
            int kind = Math.max(0, Math.min(ASSET_COL.length - 1, as[i + 2])), col = ASSET_COL[kind];
            boolean orbit = as[i + 2] >= CcMapTableBlockEntity.ASSET_ORBIT_OWN;
            float[] ap = v.toModel(as[i] + 0.5, as[i + 1] + 0.5);
            if (!ClientHoloTable.View.inside(ap)) { rim(q, m, ap, orbit ? 0.6F : 0.4F, (col & 0x00FFFFFF) | 0xE0000000); continue; }
            float gy = ground(hm, ap);
            if (orbit) post(q, m, ap[0], gy, ap[1], top + 10.0F - gy, 0.2F, (col & 0x00FFFFFF) | 0xA0000000, col);
            else post(q, m, ap[0], gy, ap[1], 4.0F, 0.25F, (col & 0x00FFFFFF) | 0xD0000000, col);
        }
        if (rm != null) {
            int[] ic = rm.icons();
            for (int i = 0; ic != null && i + 2 < ic.length; i += 3) {
                float[] ip = v.toModel(ic[i] + 0.5, ic[i + 1] + 0.5);
                if (!ClientHoloTable.View.inside(ip)) continue;
                int k = Math.max(0, Math.min(ReconMapScreen.ICON_COL.length - 1, ic[i + 2]));
                post(q, m, ip[0], ground(hm, ip), ip[1], 3.5F, 0.22F, 0xD0FF2020, ReconMapScreen.ICON_COL[k]);
            }
            ReconMapPayload live = ReconView.last;
            if (live != null && (live.status() == Recon.STATUS_OVERHEAD || live.status() == Recon.STATUS_DRONE || live.status() == Recon.STATUS_STATION) && live.dots() != null) {
                int[] d = live.dots();
                for (int i = 0; i + 1 < d.length; i += 2) {
                    float[] dp = v.toModel(d[i] + 0.5, d[i + 1] + 0.5);
                    if (ClientHoloTable.View.inside(dp)) post(q, m, dp[0], ground(hm, dp), dp[1], 1.6F, 0.25F, 0xE0FFE040, 0xFFFFF080);
                }
            }
        }
        float[] dm = des == null ? null : v.toModel(des.x() + 0.5, des.z() + 0.5);
        if (dm != null) {
            if (ClientHoloTable.View.inside(dm)) {
                float pulse = 0.75F + 0.25F * (float) Math.sin(tt * 0.3), g = ground(hm, dm);
                int col = ((int) (210 * pulse) << 24) | 0xFF2020;
                CcRenderers.quad(q, m, dm[0] - 0.3F, g, dm[1], dm[0] + 0.3F, g, dm[1], dm[0] + 0.3F, top + 12.0F, dm[1], dm[0] - 0.3F, top + 12.0F, dm[1], col);
                CcRenderers.quad(q, m, dm[0], g, dm[1] - 0.3F, dm[0], g, dm[1] + 0.3F, dm[0], top + 12.0F, dm[1] + 0.3F, dm[0], top + 12.0F, dm[1] - 0.3F, col);
            } else rim(q, m, dm, 0.8F, 0xE0FF2020);
        }
        int[] tr = be.tracks();
        for (int i = 0; i + 4 < tr.length; i += 5) {
            float[] mp = v.toModel(tr[i] + 0.5, tr[i + 2] + 0.5);
            if (!ClientHoloTable.View.inside(mp)) continue;
            int col = TRACK_COL[Math.max(0, Math.min(TRACK_COL.length - 1, tr[i + 3]))];
            float y = alt(top, tr[i + 1], tableY), g = ground(hm, mp);
            arrow(q, m, f, mp[0], y, mp[1], tr[i + 4], col);
            CcRenderers.quad(q, m, mp[0] - 0.06F, g, mp[1], mp[0] + 0.06F, g, mp[1], mp[0] + 0.06F, y, mp[1], mp[0] - 0.06F, y, mp[1], (col & 0x00FFFFFF) | 0x90000000);
        }
        List<Flight> shown2 = new ArrayList<>();
        for (Flight fl : ClientFlights.FLIGHTS.values()) {
            if (fl.build() != null && fl.build().isInterceptor() || tt < fl.start() || tt > fl.endTime()) continue;
            Vec3 pos = fl.position(tt);
            float[] mp = v.toModel(pos.x, pos.z);
            if (!ClientHoloTable.View.inside(mp)) continue;
            boolean mine = me != null && fl.isOwnedBy(me, myName);
            float y = alt(top, pos.y, tableY), r = 0.6F;
            CcRenderers.quad(q, m, mp[0] - r, y, mp[1] - r, mp[0] - r, y, mp[1] + r, mp[0] + r, y, mp[1] + r, mp[0] + r, y, mp[1] - r, mine ? 0xFF50A0FF : 0xFFFF4040);
            shown2.add(fl);
        }
        for (DroneView.Contact c : DroneView.contacts()) {
            float[] mp = v.toModel(c.x(), c.z());
            if (!ClientHoloTable.View.inside(mp)) continue;
            boolean stranger = c.owner().getMostSignificantBits() == 0L && c.owner().getLeastSignificantBits() == 0L;
            diamond(q, m, mp[0], alt(top, c.y(), tableY), mp[1], 0.55F, c.phase() == Drones.FALLING ? 0xFF808080 : stranger ? 0xFFFF40C0 : 0xFF40E0FF);
        }
        for (BombView.Contact b : BombView.contacts()) {
            float[] mp = v.toModel(b.x(), b.z());
            if (ClientHoloTable.View.inside(mp)) diamond(q, m, mp[0], alt(top, b.y(), tableY), mp[1], 0.4F, 0xFFFFA040);
        }
        sats(null, q, p, m, be.getOwner(), tx, tz, f, top);
        if (scan < 1.0F) {
            float x = HoloAnim.BRIDGE_MIN + (HoloAnim.BRIDGE_MAX - HoloAnim.BRIDGE_MIN) * scan;
            CcRenderers.quad(q, m, x, ClientHoloTable.FLOOR, HoloTableBlockEntity.BED_Z0, x, ClientHoloTable.FLOOR, HoloTableBlockEntity.BED_Z1,
                    x, 23.4F, HoloTableBlockEntity.BED_Z1, x, 23.4F, HoloTableBlockEntity.BED_Z0, 0x7040D8FF);
        }

        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        for (int i = 0; i + 4 < tr.length; i += 5) {
            float[] mp = v.toModel(tr[i] + 0.5, tr[i + 2] + 0.5);
            if (!ClientHoloTable.View.inside(mp)) continue;
            int col = TRACK_COL[Math.max(0, Math.min(TRACK_COL.length - 1, tr[i + 3]))];
            ring(lines, p, mp[0], ground(hm, mp) + 0.1F, mp[1], 0.9F, (col & 0x00FFFFFF) | 0xC0000000);
        }
        for (Flight fl : ClientFlights.FLIGHTS.values()) {
            if (fl.build() != null && fl.build().isInterceptor() || fl.isDeadReckoned()) continue;
            int col = me != null && fl.isOwnedBy(me, myName) ? 0xA050A0FF : 0xA0FF4040;
            float[] prev = null;
            for (int k = 0; k <= 32; k++) {
                Vec3 pos = fl.position(fl.start() + (fl.endTime() - fl.start()) * k / 32.0);
                float[] mp = v.toModel(pos.x, pos.z);
                float[] cur = ClientHoloTable.View.inside(mp) ? new float[] { mp[0], alt(top, pos.y, tableY), mp[1] } : null;
                if (cur != null && prev != null) CcRenderers.line(lines, p, prev[0], prev[1], prev[2], cur[0], cur[1], cur[2], col);
                prev = cur;
            }
        }
        for (CommandCenter.Warning w : be.warnings()) {
            if (w.sat()) continue;
            float[] o = v.toModel(w.ox() + 0.5, w.oz() + 0.5), im = v.toModel(w.ix() + 0.5, w.iz() + 0.5);
            float[] prev = null;
            for (int k = 0; k <= 24; k++) {
                float t = k / 24.0F, x = o[0] + (im[0] - o[0]) * t, z = o[1] + (im[1] - o[1]) * t;
                float[] cur = ClientHoloTable.View.inside(new float[] { x, z }) ? new float[] { x, top + 6.0F * 4.0F * t * (1.0F - t), z } : null;
                if (cur != null && prev != null) CcRenderers.line(lines, p, prev[0], prev[1], prev[2], cur[0], cur[1], cur[2], 0xD0FF3030);
                prev = cur;
            }
            if (ClientHoloTable.View.inside(im)) ring(lines, p, im[0], ground(hm, im) + 0.2F, im[1], 1.2F + 1.2F * (float) ((tt * 0.08) % 1.0), 0xE0FF3030);
            else rimLine(lines, p, im, 0xE0FF3030);
        }
        for (BombView.Contact b : BombView.contacts()) {
            float[] mp = v.toModel(b.x(), b.z()), ip = v.toModel(b.ix(), b.iz());
            if (!ClientHoloTable.View.inside(mp) || !ClientHoloTable.View.inside(ip)) continue;
            CcRenderers.line(lines, p, mp[0], alt(top, b.y(), tableY), mp[1], ip[0], ground(hm, ip) + 0.2F, ip[1], 0xC0FFA040);
        }
        if (dm != null && ClientHoloTable.View.inside(dm)) ring(lines, p, dm[0], ground(hm, dm) + 0.2F, dm[1], 2.0F, 0xE0FF2020);
        sats(lines, null, p, m, be.getOwner(), tx, tz, f, top);
        float pyl = HoloAnim.up(be, tt, 4.0, 16.0);
        float hy = 12.6F + 2.5F + 4.5F + Balance.HOLOT_PYL_UP * pyl;
        float[][] corners = { { -HoloTableBlockEntity.BED_X, HoloTableBlockEntity.BED_Z0 }, { HoloTableBlockEntity.BED_X, HoloTableBlockEntity.BED_Z0 },
                { -HoloTableBlockEntity.BED_X, HoloTableBlockEntity.BED_Z1 }, { HoloTableBlockEntity.BED_X, HoloTableBlockEntity.BED_Z1 } };
        int beam = ((int) (0x70 * shown) << 24) | 0x60D0FF;
        for (int k = 0; k < 4; k++) CcRenderers.line(lines, p, HEADS[k][0], hy, HEADS[k][1], corners[k][0], ClientHoloTable.FLOOR, corners[k][1], beam);
        boolean alert = HoloAnim.alerted(be, tt) && ((long) tt / 5) % 2 == 0;
        int frame = alert ? 0xE0FF3030 : v.recon() ? 0xB060C8FF : 0x8060C0FF;
        float fy = ClientHoloTable.FLOOR, x0 = -HoloTableBlockEntity.BED_X, x1 = HoloTableBlockEntity.BED_X, z0 = HoloTableBlockEntity.BED_Z0, z1 = HoloTableBlockEntity.BED_Z1;
        CcRenderers.line(lines, p, x0, fy, z0, x1, fy, z0, frame);
        CcRenderers.line(lines, p, x1, fy, z0, x1, fy, z1, frame);
        CcRenderers.line(lines, p, x1, fy, z1, x0, fy, z1, frame);
        CcRenderers.line(lines, p, x0, fy, z1, x0, fy, z0, frame);

        int labels = 0;
        for (Flight fl : shown2) {
            if (labels++ >= MAX_LABELS) break;
            Vec3 pos = fl.position(tt);
            float[] mp = v.toModel(pos.x, pos.z);
            long left = Math.max(0L, (long) (fl.endTime() - tt));
            String hyper = fl.build() != null && fl.build().hypersonic() ? "HYPER " : "";
            boolean mine = me != null && fl.isOwnedBy(me, myName);
            billboard(ps, buf, hyper + (fl.targetKnown() ? "T-" + OrbitMath.mmss(left * 50L) : "T-?"), mp[0], alt(top, pos.y, tableY) + 1.6F, mp[1], 1.6F, mine ? 0xFF80C0FF : 0xFFFF6060);
        }
        if (rm != null && rm.icons() != null) {
            int[] ic = rm.icons();
            for (int i = 0; i + 2 < ic.length && labels < MAX_LABELS * 2; i += 3) {
                String[] sl = rm.shipLabel(ic[i], ic[i + 1]);
                float[] ip = v.toModel(ic[i] + 0.5, ic[i + 1] + 0.5);
                if (sl == null || !ClientHoloTable.View.inside(ip)) continue;
                billboard(ps, buf, sl[0], ip[0], ground(hm, ip) + 5.0F, ip[1], 1.3F, 0xFFC890FF);
                labels++;
            }
        }
        if (wantRecon && rm == null) billboard(ps, buf, "NO RECON IMAGE", 0.0F, top + 4.0F, HoloTableBlockEntity.BED_CZ, 2.0F, 0xFFFFA0A0);
        if (dm != null && ClientHoloTable.View.inside(dm)) billboard(ps, buf, des.x() + " " + des.z(), dm[0], top + 13.5F, dm[1], 1.6F, 0xFFFF8080);
        String span = v.recon() ? "RECON " + (int) Math.round((HoloTableBlockEntity.BED_Z1 - HoloTableBlockEntity.BED_Z0) / v.s())
                : be.zoom() == 0 ? "LOCAL " + be.span() : be.span() + " x " + be.span() / 2;
        billboard(ps, buf, span, 0.0F, top + 9.0F, HoloTableBlockEntity.BED_Z1, 1.3F, 0xC080D0FF);
        ps.popPose();
    }

    private static float ground(ClientHoloTable.Model hm, float[] mp) { return ClientHoloTable.FLOOR + ClientHoloTable.topAt(hm, mp[0], mp[1]); }

    private static float alt(float top, double y, double tableY) {
        return top + (float) Math.max(0.0, Math.min(1.0, (y - tableY) / Balance.HOLOT_ALT_TOP)) * Balance.HOLOT_ALT_PX;
    }

    private static void post(VertexConsumer vc, Matrix4f m, float x, float y0, float z, float h, float w, int col, int cap) {
        float y1 = y0 + h;
        CcRenderers.quad(vc, m, x - w, y0, z, x + w, y0, z, x + w, y1, z, x - w, y1, z, col);
        CcRenderers.quad(vc, m, x, y0, z - w, x, y0, z + w, x, y1, z + w, x, y1, z - w, col);
        float c = w * 2.2F;
        CcRenderers.quad(vc, m, x - c, y1, z - c, x - c, y1, z + c, x + c, y1, z + c, x + c, y1, z - c, cap);
    }

    private static void diamond(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, int col) {
        CcRenderers.quad(vc, m, x - r, y, z, x, y, z + r, x + r, y, z, x, y, z - r, col);
    }

    private static float[] edge(float[] mp) {
        float dx = mp[0], dz = mp[1] - HoloTableBlockEntity.BED_CZ;
        float hz = (HoloTableBlockEntity.BED_Z1 - HoloTableBlockEntity.BED_Z0) / 2.0F;
        float k = Math.min(Math.abs(dx) < 1.0E-4F ? Float.MAX_VALUE : HoloTableBlockEntity.BED_X / Math.abs(dx), Math.abs(dz) < 1.0E-4F ? Float.MAX_VALUE : hz / Math.abs(dz));
        return new float[] { dx * k, HoloTableBlockEntity.BED_CZ + dz * k };
    }

    private static void rim(VertexConsumer q, Matrix4f m, float[] mp, float w, int col) {
        float[] e = edge(mp);
        float y = ClientHoloTable.FLOOR + 0.6F;
        CcRenderers.quad(q, m, e[0] - w, y, e[1] - w, e[0] - w, y, e[1] + w, e[0] + w, y, e[1] + w, e[0] + w, y, e[1] - w, col);
    }

    private static void rimLine(VertexConsumer lines, PoseStack.Pose p, float[] mp, int col) {
        float[] e = edge(mp);
        float y = ClientHoloTable.FLOOR + 0.6F;
        CcRenderers.line(lines, p, e[0], y, e[1], e[0], y + 3.0F, e[1], col);
    }

    private static void sats(VertexConsumer lines, VertexConsumer quads, PoseStack.Pose p, Matrix4f m, UUID owner, double cx, double cz, Direction f, float top) {
        if (owner == null) return;
        long now = OrbitView.now();
        for (OrbitMath.Sat s : OrbitView.sats()) {
            if (!owner.equals(s.owner()) || now >= s.endMs() || !OrbitMath.isOverhead(s, cx, cz, now)) continue;
            float[] last = null;
            for (int k = 6; k >= 0; k--) {
                long t = now - k * 10_000L;
                if (!OrbitMath.isOverhead(s, cx, cz, t)) { last = null; continue; }
                float[] qp = skyPoint(OrbitMath.sky(s, cx, cz, t), f, top);
                float r = k == 0 ? 0.6F : 0.25F;
                if (quads != null) CcRenderers.quad(quads, m, qp[0] - r, qp[1], qp[2] - r, qp[0] - r, qp[1], qp[2] + r, qp[0] + r, qp[1], qp[2] + r, qp[0] + r, qp[1], qp[2] - r, k == 0 ? 0xFFFFFFFF : 0x90FFFFFF);
                if (lines != null && last != null) CcRenderers.line(lines, p, last[0], last[1], last[2], qp[0], qp[1], qp[2], 0x60FFFFFF);
                last = qp;
            }
        }
    }

    private static float[] skyPoint(double[] sky, Direction f, float top) {
        double el = sky[1], flat = Math.sqrt(sky[2] * sky[2] + sky[4] * sky[4]);
        double ux = flat < 1.0E-6 ? 0.0 : sky[2] / flat, uz = flat < 1.0E-6 ? 0.0 : sky[4] / flat;
        double[] l = HoloTableBlockEntity.toLocal(f, ux, uz);
        float r = (float) ((90.0 - el) / 90.0);
        float hz = (HoloTableBlockEntity.BED_Z1 - HoloTableBlockEntity.BED_Z0) / 2.0F;
        return new float[] { (float) l[0] * r * HoloTableBlockEntity.BED_X, top + 9.0F + (float) (el / 90.0) * 4.0F, HoloTableBlockEntity.BED_CZ + (float) l[1] * r * hz };
    }

    private static void arrow(VertexConsumer q, Matrix4f m, Direction f, float x, float y, float z, int heading, int col) {
        float r = 0.9F;
        if (heading < 0) { diamond(q, m, x, y, z, r, col); return; }
        double a = Math.toRadians(heading);
        double[] d = HoloTableBlockEntity.toLocal(f, Math.sin(a), -Math.cos(a));
        float dx = (float) d[0], dz = (float) d[1], sx = -dz, sz = dx;
        float tx = x + dx * 1.4F, tz = z + dz * 1.4F, bx = x - dx * 0.8F, bz = z - dz * 0.8F;
        CcRenderers.quad(q, m, tx, y, tz, bx + sx * r, y, bz + sz * r, x, y, z, bx - sx * r, y, bz - sz * r, col);
    }

    private static void ring(VertexConsumer lines, PoseStack.Pose p, float x, float y, float z, float r, int col) {
        float px = x + r, pz = z;
        for (int k = 1; k <= 16; k++) {
            double a = Math.PI * 2 * k / 16;
            float nx = x + (float) Math.cos(a) * r, nz = z + (float) Math.sin(a) * r;
            CcRenderers.line(lines, p, px, y, pz, nx, y, nz, col);
            px = nx; pz = nz;
        }
    }

    private static void billboard(PoseStack ps, MultiBufferSource buf, String s, float x, float y, float z, float height, int colour) {
        Font font = Minecraft.getInstance().font;
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float k = height / 9.0F;
        ps.scale(k, -k, k);
        font.drawInBatch(s, -font.width(s) / 2.0F, -4.0F, colour, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0x40000000, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(HoloTableBlockEntity be) { return super.getRenderBoundingBox(be).expandTowards(0.0, 2.0, 0.0); }
}
