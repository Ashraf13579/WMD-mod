package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class CcRenderersS5 {

    private CcRenderersS5() {}

    public static class InterceptorConsole extends CcRenderersB.ScreenRenderer<CcInterceptorConsoleBlockEntity> {
        public InterceptorConsole() { super("cc_interceptor_console", 2, CcSpecS5.INTERCEPTOR_SCREEN); }

        @Override
        void draw(CcInterceptorConsoleBlockEntity be, double tt, PoseStack ps, MultiBufferSource buf, float z) {
            boolean any = be.effectiveMode() == SatDefence.MODE_ANY;
            fill(ps, buf, 0.0F, 0.86F, 1.0F, 1.0F, z, any ? 0xFF6A1010 : 0xFF10305A);
            text(ps, buf, any ? "WEAPONS FREE" : "OWN AREA", 0.5F, 0.88F, z - 0.02F, height() * 0.11F, any ? 0xFFFF8080 : 0xFF8FC8FF);

            line(ps, buf, 0.04F, 0.12F, 0.96F, 0.12F, z, 0xFF406080);
            float px = 0.04F, py = 0.12F;
            for (int i = 1; i <= 16; i++) {
                double a = Math.PI * i / 16.0;
                float x = (float) (0.5 - 0.46 * Math.cos(a)), y = (float) (0.12 + 0.62 * Math.sin(a));
                line(ps, buf, px, py, x, y, z, 0x60406080);
                px = x; py = y;
            }
            long now = OrbitView.now();
            double bx = be.getBlockPos().getX() + 0.5, bz = be.getBlockPos().getZ() + 0.5;
            int n = 0;
            for (Tag t : be.rows()) {
                if (!(t instanceof CompoundTag c)) continue;
                OrbitMath.Sat s = null;
                for (OrbitMath.Sat o : OrbitView.sats()) if (o.id() == c.getInt("id")) s = o;
                if (s == null) continue;
                long shot = c.getLong("shot");
                boolean charged = shot <= 0L || now >= shot + OrbitMath.periodMs() - OrbitMath.windowMs();
                long over = OrbitMath.overheadLeft(s, bx, bz, now);
                float u;
                if (over > 0) u = (float) (1.0 - over / (double) OrbitMath.windowMs());
                else u = -0.04F * n;
                double a = Math.PI * Math.max(0.0, Math.min(1.0, u));
                float x = (float) (0.5 - 0.46 * Math.cos(a)), y = (float) (0.12 + 0.62 * Math.sin(a));
                if (over <= 0) { x = 0.06F + 0.05F * n; y = 0.18F; }
                int col = !c.getBoolean("ctl") ? 0xFF505A64 : charged ? 0xFF35C24A : 0xFFD04030;
                boolean blink = over > 0 && ((long) (tt / 5.0)) % 2 == 0;
                fill(ps, buf, x - 0.025F, y - 0.05F, x + 0.025F, y + 0.05F, z - 0.01F, blink ? 0xFFFFFFFF : col);
                n++;
            }
            if (n == 0) text(ps, buf, "NO INTERCEPTOR SAT", 0.5F, 0.45F, z - 0.02F, height() * 0.12F, 0xFF6A8AAA);

            if (!be.log().isEmpty() && be.log().get(be.log().size() - 1) instanceof CompoundTag last && now - last.getLong("ms") < 10_000L
                    && ((long) (tt / 4.0)) % 2 == 0) {
                boolean hit = last.getBoolean("hit");
                text(ps, buf, hit ? "KILL" : "MISS", 0.5F, 0.4F, z - 0.03F, height() * 0.2F, hit ? 0xFF60FF60 : 0xFFFFB040);
            }
        }
    }
}
