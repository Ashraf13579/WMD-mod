package com.ashraf.wmd;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.nbt.Tag;

public final class CcRenderersS52 {

    private CcRenderersS52() {}

    private static int lampColour(int kind) {
        return switch (kind) { case WmdUi.K_GOOD -> 0xFF35C24A; case WmdUi.K_WARN -> 0xFFE0A030; case WmdUi.K_BAD -> 0xFFD04030; default -> 0xFF505A64; };
    }

    public static class ArtilleryConsole extends CcRenderersB.ScreenRenderer<CcArtilleryConsoleBlockEntity> {
        public ArtilleryConsole() { super("cc_artillery_console", 2, CcSpecS52.ART_SCREEN); }

        @Override
        void draw(CcArtilleryConsoleBlockEntity be, double tt, PoseStack ps, MultiBufferSource buf, float z) {
            List<CompoundTag> rows = be.gunRows();

            float ox = 0.5F, oy = 0.22F;
            for (int ring = 1; ring <= 3; ring++) {
                float rr = 0.15F * ring;
                float px = ox - rr * 1.6F, py = oy;
                for (int i = 1; i <= 16; i++) {
                    double a = Math.PI * i / 16.0;
                    float x = ox - (float) Math.cos(a) * rr * 1.6F, y = oy + (float) Math.sin(a) * rr * 1.4F;
                    line(ps, buf, px, py, x, y, z, ring == 3 ? 0x80406080 : 0x50406080);
                    px = x;
                    py = y;
                }
            }
            line(ps, buf, 0.02F, oy, 0.98F, oy, z, 0x80406080);
            boolean blink = ((long) (tt / 5.0)) % 2 == 0;

            for (CompoundTag c : rows) {
                if (!c.getBoolean("ht")) continue;
                net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.of(c.getLong("p"));
                double dx = c.getInt("tx") + 0.5 - p.getX() - 0.5, dz = c.getInt("tz") + 0.5 - p.getZ() - 0.5;
                int gi = Math.max(0, Math.min(Artillery.GUNS.length - 1, c.getInt("g")));
                double share = Math.min(1.0, Math.hypot(dx, dz) / Math.max(1, Artillery.range(gi)));
                if (c.getInt("g") == Artillery.SUPER_GUN) share = Math.min(1.0, Math.hypot(dx, dz) / Balance.SUPER_GUN_RANGE);
                double a = Math.atan2(dx, -dz);
                double fa = Math.PI / 2.0 - Math.max(-Math.PI / 2.0, Math.min(Math.PI / 2.0, a));
                float rr = (float) (0.45 * share);
                float x = ox - (float) Math.cos(fa) * rr * 1.6F, y = oy + (float) Math.sin(fa) * rr * 1.4F;
                boolean firing = c.getInt("m") != 0;
                fill(ps, buf, x - 0.02F, y - 0.04F, x + 0.02F, y + 0.04F, z - 0.01F, firing && blink ? 0xFFFFFFFF : lampColour(CcScreenArtillery.gunKind(c)));
                line(ps, buf, ox, oy, x, y, z - 0.005F, 0x30FFFFFF);
            }

            for (int i = 0; i < 4; i++) {
                int kind = i < rows.size() ? CcScreenArtillery.gunKind(rows.get(i)) : -1;
                float u = 0.12F + i * 0.25F;
                fill(ps, buf, u - 0.05F, 0.03F, u + 0.05F, 0.13F, z - 0.01F, kind < 0 ? 0xFF2A3038 : lampColour(kind));
            }
            text(ps, buf, rows.isEmpty() ? "NO GUNS" : "GUNS " + rows.size(), 0.5F, 0.9F, z - 0.02F, height() * 0.11F, 0xFF8FC8FF);
            if (be.missionArmed((long) tt) && blink) {
                fill(ps, buf, 0.0F, 0.0F, 1.0F, 0.03F, z - 0.02F, 0xFFFF3030);
                fill(ps, buf, 0.0F, 0.97F, 1.0F, 1.0F, z - 0.02F, 0xFFFF3030);
                fill(ps, buf, 0.0F, 0.0F, 0.015F, 1.0F, z - 0.02F, 0xFFFF3030);
                fill(ps, buf, 0.985F, 0.0F, 1.0F, 1.0F, z - 0.02F, 0xFFFF3030);
            }
        }
    }

    public static class PlannerDesk extends CcRenderersB.ScreenRenderer<CcPlannerBlockEntity> {
        public PlannerDesk() { super("cc_planner", 2, new CcSpecB.Rect(-21.4F, 21.4F, 20.4F, 29.2F, 15.4F, 0.0F)); }

        @Override
        public void render(CcPlannerBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            ps.pushPose();
            ps.translate(0.0, -CcModuleBlocksS52.PlannerDesk.DECK_DROP, 0.0);
            super.render(be, pt, ps, buf, light, overlay);
            ps.popPose();
        }

        @Override
        void draw(CcPlannerBlockEntity be, double tt, PoseStack ps, MultiBufferSource buf, float z) {
            float span = Balance.ROBOT_ZONE * 2.0F, ax = 9.6F / 42.8F;
            float cx = 0.5F, cy = 0.5F;
            int ox = be.getBlockPos().getX(), oz = be.getBlockPos().getZ();
            line(ps, buf, cx - 0.01F, cy, cx + 0.01F, cy, z, 0xA0FFFFFF);
            line(ps, buf, cx, cy - 0.05F, cx, cy + 0.05F, z, 0xA0FFFFFF);
            boolean blink = ((long) (tt / 6.0)) % 2 == 0;

            java.util.Map<Long, float[]> at = new java.util.HashMap<>();
            for (Tag t : be.rows()) {
                if (!(t instanceof CompoundTag c) || c.getInt("t") != 0) continue;
                float u = cx + (c.getInt("x") - ox) / span * ax * 2.0F, v = cy - (c.getInt("z") - oz) / span * 2.0F;
                at.put(c.getLong("p"), new float[] { u, v });
                if (u < 0.0F || u > 1.0F || v < 0.0F || v > 1.0F) continue;
                int r = c.getInt("r");
                fill(ps, buf, u - 0.008F, v - 0.04F, u + 0.008F, v + 0.04F, z - 0.01F, r == 3 ? 0xFF3AC2D5 : r == 1 ? 0xFF3A7BD5 : 0xFFD58A3A);
            }
            for (Tag t : be.rows()) {
                if (!(t instanceof CompoundTag c) || c.getInt("t") != 2) continue;
                float[] d = at.get(c.getLong("dst")), s0 = c.getLong("src") == 0 ? null : at.get(c.getLong("src"));
                if (d == null || s0 == null) continue;
                int st = c.getInt("st");
                int col = st == Logistics.ST_DONE ? 0xC035C24A : st == Logistics.ST_ON_WAY ? 0xC04090E0 : st == Logistics.ST_OFF ? 0x80505A66 : 0xC0E0A030;
                line(ps, buf, Mth.clamp(s0[0], 0, 1), Mth.clamp(s0[1], 0, 1), Mth.clamp(d[0], 0, 1), Mth.clamp(d[1], 0, 1), z - 0.005F, col);
            }
            for (Tag t : be.rows()) {
                if (!(t instanceof CompoundTag c) || c.getInt("t") != 1) continue;
                float u = cx + (c.getInt("x") - ox) / span * ax * 2.0F, v = cy - (c.getInt("z") - oz) / span * 2.0F;
                if (u < 0.0F || u > 1.0F || v < 0.0F || v > 1.0F) continue;
                int col = c.getInt("k") != 0 ? 0xFFFF4040 : c.getInt("s") == MissileRobotEntity.IDLE ? 0xFF35C24A : (blink ? 0xFF60B0FF : 0xFF2050A0);
                fill(ps, buf, u - 0.006F, v - 0.03F, u + 0.006F, v + 0.03F, z - 0.015F, col);
            }
        }
    }

    public static class IronDomeDesk extends CcRenderersB.ScreenRenderer<CcIronDomeBlockEntity> {
        public IronDomeDesk() { super("cc_iron_dome", 2, CcSpecS52.DOME_SCREEN); }

        private static float uOf(float x) {
            CcSpecB.Rect r = CcSpecS52.DOME_SCREEN;
            return (r.x1() - x) / (r.x1() - r.x0());
        }

        @Override
        void draw(CcIronDomeBlockEntity be, double tt, PoseStack ps, MultiBufferSource buf, float z) {
            boolean set = be.hasCentre(), up = set && be.domeActive();
            boolean blink = ((long) (tt / 6.0)) % 2 == 0;

            float cx = 0.5F, cy = 0.5F, rx = 0.36F, ry = 0.42F;
            int ringCol = !set ? 0x50406080 : up ? 0xFF4090E0 : 0xFF803030;
            float px = cx + rx, py = cy;
            for (int i = 1; i <= 24; i++) {
                double a = Math.PI * 2.0 * i / 24.0;
                float x = cx + (float) Math.cos(a) * rx, y = cy + (float) Math.sin(a) * ry;
                line(ps, buf, px, py, x, y, z, ringCol);
                px = x;
                py = y;
            }
            line(ps, buf, cx - 0.06F, cy, cx + 0.06F, cy, z, 0xA0FFFFFF);
            line(ps, buf, cx, cy - 0.07F, cx, cy + 0.07F, z, 0xA0FFFFFF);

            int[] st = new int[net.minecraft.util.Mth.clamp(be.rows().size(), 0, 8)];
            int n = 0;
            for (Tag t : be.rows()) if (t instanceof CompoundTag c && n < st.length) st[n++] = c.getInt("st");
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2.0 * i / 8.0;
                float x = cx + (float) Math.sin(a) * rx, y = cy + (float) Math.cos(a) * ry;
                boolean ok = i < n && st[i] == IronDome.M_OK;
                int col = !set ? 0xFF2A3038 : ok ? 0xFF35C24A : (up || blink ? 0xFFD04030 : 0xFF501818);
                fill(ps, buf, x - 0.05F, y - 0.05F, x + 0.05F, y + 0.05F, z - 0.01F, col);
            }

            float u0 = uOf(CcSpecS52.DOME_STRIP_X1), u1 = uOf(CcSpecS52.DOME_STRIP_X0);
            fill(ps, buf, u0, 0.0F, u1, 1.0F, z + 0.04F, CcRenderers.screenBg(be, 0xF0081420));
            String s = !set ? "OFF" : up ? "DOME ON" : "DOWN";
            int col = !set ? 0xFF6A8AAA : up ? 0xFF60B0FF : (blink ? 0xFFFF4040 : 0xFF802020);
            text(ps, buf, s, (u0 + u1) / 2.0F, 0.62F, z - 0.02F, height() * 0.16F, col);
            if (set) text(ps, buf, "R " + be.radius(), (u0 + u1) / 2.0F, 0.28F, z - 0.02F, height() * 0.13F, 0xFFA0C8F0);
        }
    }
}
