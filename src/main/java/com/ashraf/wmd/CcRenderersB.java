package com.ashraf.wmd;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class CcRenderersB {

    private CcRenderersB() {}

    abstract static class ScreenRenderer<T extends CcConsoleBlockEntity> extends StagedUnitRenderer<T> {

        private final CcSpecB.Rect rect;

        ScreenRenderer(String name, int up, CcSpecB.Rect rect) {
            super(name, up);
            this.rect = rect;
        }

        @Override
        public void render(T be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            super.render(be, pt, ps, buf, light, overlay);
            if (!be.isOnline() || be.getLevel() == null || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
            Direction f = be.getBlockState().getValue(MachineBlock.FACING);
            CcRenderers.modelFrame(ps, f);
            if (rect.tilt() != 0.0F) {
                ps.translate(0.0F, rect.y0(), rect.z());
                ps.mulPose(Axis.XP.rotationDegrees(rect.tilt()));
                ps.translate(0.0F, -rect.y0(), -rect.z());
            }
            float z = rect.z() - 0.05F;
            fill(ps, buf, 0.0F, 0.0F, 1.0F, 1.0F, z, CcRenderers.screenBg(be, 0xF0081420));
            draw(be, be.getLevel().getGameTime() + pt, ps, buf, z - 0.05F);
            ps.popPose();
        }

        abstract void draw(T be, double tt, PoseStack ps, MultiBufferSource buf, float z);

        float mx(float u) { return rect.x1() - u * (rect.x1() - rect.x0()); }
        float my(float v) { return rect.y0() + v * (rect.y1() - rect.y0()); }
        float height() { return rect.y1() - rect.y0(); }

        void fill(PoseStack ps, MultiBufferSource buf, float u0, float v0, float u1, float v1, float z, int argb) {
            Matrix4f m = ps.last().pose();
            CcRenderers.rect(buf.getBuffer(RenderType.debugQuads()), m, Math.min(mx(u0), mx(u1)), my(v0), Math.max(mx(u0), mx(u1)), my(v1), z, argb);
        }

        void line(PoseStack ps, MultiBufferSource buf, float u0, float v0, float u1, float v1, float z, int argb) {
            CcRenderers.line(buf.getBuffer(RenderType.lines()), ps.last(), mx(u0), my(v0), z, mx(u1), my(v1), z, argb);
        }

        void text(PoseStack ps, MultiBufferSource buf, String s, float u, float v, float z, float hPx, int colour) {
            CcRenderers.text(ps, buf, s, mx(u), my(v), z, hPx, colour);
        }
    }

    private static int padColour(CompoundTag c) {
        if (!c.getBoolean("in") || c.getBoolean("dim")) return 0xFF505A64;
        int h = c.getInt("h"), st = c.getInt("s");
        if (h == LaunchTerminalBlockEntity.OK && st == LaunchTerminalBlockEntity.Stage.READY.ordinal()) return 0xFF35C24A;
        if (st != LaunchTerminalBlockEntity.Stage.IDLE.ordinal() && st != LaunchTerminalBlockEntity.Stage.READY.ordinal()) return 0xFFE0A030;
        return 0xFFD04030;
    }

    public static class LaunchConsole extends ScreenRenderer<CcLaunchConsoleBlockEntity> {
        public LaunchConsole() { super("cc_launch_console", 2, CcSpecB.LAUNCH_SCREEN); }

        @Override
        void draw(CcLaunchConsoleBlockEntity be, double tt, PoseStack ps, MultiBufferSource buf, float z) {
            List<Tag> rows = be.rows();
            int n = Math.max(1, Math.min(8, rows.size()));
            text(ps, buf, rows.isEmpty() ? "NO PADS" : "PADS " + rows.size(), 0.5F, 0.86F, z, height() * 0.16F, 0xFF8FC8FF);
            for (int i = 0; i < Math.min(8, rows.size()); i++) {
                CompoundTag c = (CompoundTag) rows.get(i);
                float u0 = 0.06F + i * 0.88F / n, u1 = u0 + 0.88F / n * 0.8F;
                fill(ps, buf, u0, 0.12F, u1, 0.68F, z, padColour(c));
            }
            boolean armed = be.armed((long) tt);
            if (armed && ((long) (tt / 5.0)) % 2 == 0) {
                fill(ps, buf, 0.0F, 0.0F, 1.0F, 0.04F, z - 0.02F, 0xFFFF3030);
                fill(ps, buf, 0.0F, 0.96F, 1.0F, 1.0F, z - 0.02F, 0xFFFF3030);
                fill(ps, buf, 0.0F, 0.0F, 0.02F, 1.0F, z - 0.02F, 0xFFFF3030);
                fill(ps, buf, 0.98F, 0.0F, 1.0F, 1.0F, z - 0.02F, 0xFFFF3030);
            }
        }
    }

    public static class RetargetConsole extends ScreenRenderer<CcRetargetConsoleBlockEntity> {
        public RetargetConsole() { super("cc_retarget_console", 2, CcSpecB.RETARGET_SCREEN); }

        @Override
        void draw(CcRetargetConsoleBlockEntity be, double tt, PoseStack ps, MultiBufferSource buf, float z) {

            line(ps, buf, 0.05F, 0.5F, 0.95F, 0.5F, z, 0x40406080);
            line(ps, buf, 0.5F, 0.08F, 0.5F, 0.92F, z, 0x40406080);
            float sweep = (float) ((tt * 0.02) % 1.0);
            line(ps, buf, sweep, 0.05F, sweep, 0.95F, z, 0x8040A0FF);
            List<Tag> rows = be.rows();
            int n = Math.max(1, rows.size());
            for (int i = 0; i < rows.size(); i++) {
                CompoundTag c = (CompoundTag) rows.get(i);
                float v = 0.15F + 0.7F * (i + 0.5F) / n;
                double left = Math.max(0.0, c.getLong("end") - tt);
                float u = (float) Math.max(0.06, Math.min(0.94, 0.94 - left / 6000.0));
                boolean used = (c.getInt("uf") & Flight.U_RETARGETED) != 0;
                int col = !c.getBoolean("in") ? 0xFF505A64 : used ? 0xFFE0A030 : 0xFF60C0FF;
                fill(ps, buf, u - 0.02F, v - 0.03F, u + 0.02F, v + 0.03F, z - 0.01F, col);
                line(ps, buf, u, v, 0.95F, v, z, 0x40FFFFFF);
            }
            if (rows.isEmpty()) text(ps, buf, "NO UPLINK", 0.5F, 0.5F, z - 0.02F, height() * 0.18F, 0xFF6A8AAA);
        }
    }

    public static class StrikePlanner extends ScreenRenderer<CcStrikePlannerBlockEntity> {
        public StrikePlanner() { super("cc_strike_planner", 2, CcSpecB.STRIKE_SCREEN); }

        @Override
        void draw(CcStrikePlannerBlockEntity be, double tt, PoseStack ps, MultiBufferSource buf, float z) {
            List<CompoundTag> shots = be.shots();
            long impact = be.getLevel() == null ? -1 : be.nextImpact(be.getLevel());
            if (shots.isEmpty() || impact < 0) { text(ps, buf, "NO STRIKE", 0.5F, 0.5F, z - 0.02F, height() * 0.18F, 0xFF6A8AAA); return; }
            long first = impact;
            for (CompoundTag c : shots) if (c.getLong("impact") == impact) first = Math.min(first, c.getLong("fire"));
            first = Math.min(first, (long) tt);
            double span = Math.max(20.0, impact - first);
            line(ps, buf, 0.05F, 0.35F, 0.95F, 0.35F, z, 0xFF406080);
            line(ps, buf, 0.95F, 0.15F, 0.95F, 0.75F, z, 0xFFFF3030);
            int i = 0;
            for (CompoundTag c : shots) {
                if (c.getLong("impact") != impact) continue;
                float u = 0.05F + 0.9F * (float) ((c.getLong("fire") - first) / span);
                int st = c.getInt("st");
                int col = st == CcStrikeData.FIRED ? 0xFF35C24A : st == CcStrikeData.SKIPPED ? 0xFFD04030 : 0xFFE0A030;
                float v = 0.42F + 0.08F * (i++ % 4);
                fill(ps, buf, u - 0.012F, 0.3F, u + 0.012F, v, z - 0.01F, col);
            }
            float now = 0.05F + 0.9F * (float) Math.max(0.0, Math.min(1.0, (tt - first) / span));
            line(ps, buf, now, 0.1F, now, 0.8F, z - 0.02F, 0xFFFFFFFF);
            boolean blink = ((long) (tt / 6.0)) % 2 == 0;
            text(ps, buf, "TOT T-" + OrbitMath.mmss(Math.max(0L, (long) ((impact - tt) * 50.0))), 0.5F, 0.88F, z - 0.02F, height() * 0.16F, blink ? 0xFFFF6060 : 0xFFFFB0B0);
        }
    }

    public static class MissileCam extends ScreenRenderer<CcMissileCamBlockEntity> {
        public MissileCam() { super("cc_missile_cam", 2, CcSpecB.CAM_SCREEN); }

        @Override
        void draw(CcMissileCamBlockEntity be, double tt, PoseStack ps, MultiBufferSource buf, float z) {
            List<Tag> rows = be.rows();
            if (rows.isEmpty()) {

                long seed = (long) (tt / 2.0);
                for (int k = 0; k < 24; k++) {
                    long h = (seed * 31 + k) * 0x9E3779B97F4A7C15L;
                    float u = ((h >>> 8) & 1023) / 1023.0F, v = ((h >>> 20) & 1023) / 1023.0F;
                    fill(ps, buf, u * 0.96F, v * 0.96F, u * 0.96F + 0.04F, v * 0.96F + 0.02F, z - 0.01F, 0x60FFFFFF);
                }
                text(ps, buf, "NO SIGNAL", 0.5F, 0.5F, z - 0.02F, height() * 0.16F, 0xFF8090A0);
                return;
            }

            float tilt = (float) Math.sin(tt * 0.03) * 0.08F, mid = 0.45F + (float) Math.sin(tt * 0.017) * 0.1F;
            fill(ps, buf, 0.02F, mid, 0.98F, 0.98F, z, 0xFF1A3050);
            fill(ps, buf, 0.02F, 0.02F, 0.98F, mid, z, 0xFF203018);
            line(ps, buf, 0.02F, mid - tilt, 0.98F, mid + tilt, z - 0.01F, 0xFFA0D0FF);
            line(ps, buf, 0.45F, 0.5F, 0.55F, 0.5F, z - 0.02F, 0xFFFFFFFF);
            line(ps, buf, 0.5F, 0.42F, 0.5F, 0.58F, z - 0.02F, 0xFFFFFFFF);
            CompoundTag c = (CompoundTag) rows.get(rows.size() - 1);

            String label = c.contains("dr") ? "DRONE #" + c.getInt("dr") + "  BAT " + c.getInt("bat") + "%"
                    : "CAM #" + c.getLong("id") + "  T-" + OrbitMath.mmss(Math.max(0L, (long) ((c.getLong("end") - tt) * 50.0)));
            text(ps, buf, label, 0.5F, 0.9F, z - 0.02F, height() * 0.12F, 0xFFFFFFFF);
            if (rows.size() > 1) text(ps, buf, rows.size() + " FEEDS", 0.5F, 0.1F, z - 0.02F, height() * 0.1F, 0xFFA0D0FF);
        }
    }
}
