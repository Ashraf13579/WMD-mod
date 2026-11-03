package com.ashraf.wmd;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

final class ClientRodFx {

    private ClientRodFx() {}

    static final double LEN = 22.0, RAD = 1.1, NOSE = 3.6, FIN = 2.6, FIN_LEN = 5.0;
    private static final int SIDES = 8, SEGS = 7;
    private static final ByteBufferBuilder SOLID = new ByteBufferBuilder(1 << 16), GLOW = new ByteBufferBuilder(1 << 17);

    private static float[] heat(double t) {
        double[][] st = { { 0.0, 0.22, 0.23, 0.25 }, { 0.3, 0.55, 0.08, 0.04 }, { 0.55, 1.0, 0.38, 0.08 }, { 0.8, 1.0, 0.78, 0.35 }, { 1.0, 1.0, 0.97, 0.9 } };
        t = Math.max(0.0, Math.min(1.0, t));
        for (int i = 1; i < st.length; i++) {
            if (t <= st[i][0]) {
                double u = (t - st[i - 1][0]) / (st[i][0] - st[i - 1][0]);
                return new float[] { (float) (st[i - 1][1] + (st[i][1] - st[i - 1][1]) * u), (float) (st[i - 1][2] + (st[i][2] - st[i - 1][2]) * u), (float) (st[i - 1][3] + (st[i][3] - st[i - 1][3]) * u) };
            }
        }
        return new float[] { 1.0F, 0.97F, 0.9F };
    }

    private record Frame(Vec3 nose, Vec3 ax, Vec3 u, Vec3 v, Vec3 cam, double k) {
        Vec3 at(double s, double a, double r) {
            Vec3 p = nose.add(ax.scale(s)).add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r));
            return cam.add(p.subtract(cam).scale(k));
        }
    }

    static double heightLeft(double f) { return 1.0 - Math.pow(Math.max(0.0, Math.min(1.0, f)), 1.8); }

    static boolean draw(RenderLevelStageEvent event, Vec3 cam, Vector3f lv, Vector3f uv, double x, double y, double z, double ticksLeft, boolean broken, double now) {
        double f = 1.0 - ticksLeft / Balance.ROD_FALL_TICKS;
        if (f < 0.0 || f > 1.0) return false;
        double h = Balance.ROD_FALL_ALT * heightLeft(f), H = Math.min(1.0, f * 1.35);
        BufferBuilder solid = new BufferBuilder(SOLID, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        BufferBuilder glow = new BufferBuilder(GLOW, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int pieces = broken ? 3 : 1;
        double spin = 2.0 * Math.PI * (f * f * 70.0) + now * 0.2;
        ClientLevel level = Minecraft.getInstance().level;
        RandomSource rnd = level == null ? RandomSource.create() : level.random;
        for (int i = 0; i < pieces; i++) {
            double spread = broken ? (1.0 - h / Balance.ROD_FALL_ALT) * 45.0 : 0.0, a = i * Math.PI * 2.0 / 3.0;
            Vec3 nose = new Vec3(x + Math.cos(a) * spread, y + h, z + Math.sin(a) * spread);
            Vec3 ax = new Vec3(0.0, 1.0, 0.0);
            if (broken) {
                double tb = now * 0.15 + i * 2.0;
                ax = new Vec3(Math.sin(tb) * 0.8, Math.cos(tb), Math.cos(tb * 0.7) * 0.5).normalize();
            }
            Vec3 ref = Math.abs(ax.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 u0 = ax.cross(ref).normalize(), v0 = ax.cross(u0).normalize();
            double sp = spin + i * 1.3;
            Vec3 u = u0.scale(Math.cos(sp)).add(v0.scale(Math.sin(sp))), v = ax.cross(u).normalize();
            double dist = nose.distanceTo(cam), k = dist > 250.0 ? 250.0 / dist : 1.0;
            Frame fr = new Frame(nose, ax, u, v, cam, k);
            double len = broken ? LEN * 0.4 : LEN;
            body(solid, fr, len, H);
            sheath(glow, fr, len, H);
            Vec3 tail = fr.at(len, 0.0, 0.0), tip = fr.at(-1.0, 0.0, 0.0);
            double trail = (60.0 + 160.0 * (1.0 - f)) * k;
            ClientBlastGeo.streak(glow, cam, tail.x, tail.y, tail.z, tail.x, tail.y + trail, tail.z, 3.0 * k, 1.0F, 0.5F, 0.15F, 0.45F * (float) H, 0.0F);
            ClientBlastGeo.streak(glow, cam, tail.x, tail.y, tail.z, tail.x, tail.y + trail * 0.6, tail.z, 1.0 * k, 1.0F, 0.9F, 0.7F, 0.8F * (float) H, 0.0F);
            ClientBlastGeo.disc(glow, cam, lv, uv, tip.x, tip.y, tip.z, (3.0 + 7.0 * H) * k, 1.0F, 0.85F, 0.6F, 0.85F * (float) H, 14);

            if (level != null && H > 0.2) {
                float[] lc = heat(Math.min(1.0, H * 1.2));
                VeilBridge.glow(0x524F4446L + i, nose.x, nose.y, nose.z, lc[0], lc[1], lc[2], (float) Math.min(420.0, h + 60.0), (float) (2.0 + 6.0 * H), 0.4F, level.getGameTime());
            }
            if (level != null && k > 0.6 && H > 0.3 && rnd.nextFloat() < 0.6F) {
                for (int n = 0; n < 2; n++)
                    level.addParticle(rnd.nextBoolean() ? ParticleTypes.LAVA : ParticleTypes.FLAME, tail.x + rnd.nextGaussian(), tail.y, tail.z + rnd.nextGaussian(),
                            rnd.nextGaussian() * 0.2, 0.6 + rnd.nextDouble() * 0.8, rnd.nextGaussian() * 0.2);
            }
        }
        drawSolid(event, solid);
        ClientBlastGeo.draw(event, glow, true, true);
        return true;
    }

    private static void v(BufferBuilder bb, Vec3 cam, Vec3 p, float[] c, float a) {
        bb.addVertex((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).setColor(c[0], c[1], c[2], a);
    }

    private static void body(BufferBuilder bb, Frame fr, double len, double H) {
        Vec3 cam = fr.cam();
        double da = Math.PI * 2.0 / SIDES;
        for (int s = 0; s < SIDES; s++) {
            double a0 = s * da, a1 = a0 + da;
            float sh = s % 2 == 0 ? 1.0F : 0.82F;
            float[] c = shade(heat(H * 1.6), sh);
            Vec3 tip = fr.at(0.0, 0.0, 0.0);
            v(bb, cam, tip, c, 1.0F); v(bb, cam, tip, c, 1.0F); v(bb, cam, fr.at(NOSE, a1, RAD), c, 1.0F); v(bb, cam, fr.at(NOSE, a0, RAD), c, 1.0F);
        }
        for (int g = 0; g < SEGS; g++) {
            double s0 = NOSE + (len - NOSE) * g / SEGS, s1 = NOSE + (len - NOSE) * (g + 1) / SEGS;
            float[] c0 = heat(H * 1.6 - 0.9 * (s0 / len)), c1 = heat(H * 1.6 - 0.9 * (s1 / len));
            for (int s = 0; s < SIDES; s++) {
                double a0 = s * da, a1 = a0 + da;
                float sh = s % 2 == 0 ? 1.0F : 0.78F;
                v(bb, cam, fr.at(s0, a0, RAD), shade(c0, sh), 1.0F); v(bb, cam, fr.at(s0, a1, RAD), shade(c0, sh), 1.0F);
                v(bb, cam, fr.at(s1, a1, RAD), shade(c1, sh), 1.0F); v(bb, cam, fr.at(s1, a0, RAD), shade(c1, sh), 1.0F);
            }
        }
        for (int s = 0; s < SIDES; s++) {
            double a0 = s * da, a1 = a0 + da;
            float[] c = shade(heat(H * 1.6 - 0.9), 0.6F);
            Vec3 m = fr.at(len, 0.0, 0.0);
            v(bb, cam, m, c, 1.0F); v(bb, cam, m, c, 1.0F); v(bb, cam, fr.at(len, a1, RAD), c, 1.0F); v(bb, cam, fr.at(len, a0, RAD), c, 1.0F);
        }
        float[] fc = shade(heat(H * 1.6 - 0.95), 0.9F);
        for (int n = 0; n < 4; n++) {
            double a = n * Math.PI / 2.0;
            Vec3 p0 = fr.at(len - FIN_LEN, a, RAD), p1 = fr.at(len, a, RAD), p2 = fr.at(len, a, RAD + FIN), p3 = fr.at(len - FIN_LEN * 0.4, a, RAD + FIN);
            v(bb, cam, p0, fc, 1.0F); v(bb, cam, p1, fc, 1.0F); v(bb, cam, p2, fc, 1.0F); v(bb, cam, p3, fc, 1.0F);
            v(bb, cam, p3, fc, 1.0F); v(bb, cam, p2, fc, 1.0F); v(bb, cam, p1, fc, 1.0F); v(bb, cam, p0, fc, 1.0F);
        }
    }

    private static float[] shade(float[] c, float k) { return new float[] { c[0] * k, c[1] * k, c[2] * k }; }

    private static void sheath(BufferBuilder bb, Frame fr, double len, double H) {
        if (H < 0.05) return;
        Vec3 cam = fr.cam();
        int n = 14;
        double da = Math.PI * 2.0 / n;
        for (int layer = 0; layer < 3; layer++) {
            double back = len * (0.35 + 0.25 * layer), r1 = RAD * (2.0 + 1.6 * layer) * (0.6 + 0.6 * H), ahead = -1.2 - 0.6 * layer;
            float a0 = (float) ((0.55 - 0.15 * layer) * H), a1 = 0.0F;
            float[] c = layer == 0 ? new float[] { 1.0F, 0.95F, 0.85F } : layer == 1 ? new float[] { 1.0F, 0.65F, 0.25F } : new float[] { 1.0F, 0.35F, 0.1F };
            for (int s = 0; s < n; s++) {
                double b0 = s * da, b1 = b0 + da;
                v(bb, cam, fr.at(ahead, b0, RAD * 0.6), c, a0); v(bb, cam, fr.at(ahead, b1, RAD * 0.6), c, a0);
                v(bb, cam, fr.at(back, b1, r1), c, a1); v(bb, cam, fr.at(back, b0, r1), c, a1);
            }
        }
        float[] sc = { 0.75F, 0.85F, 1.0F };
        for (int s = 0; s < n; s++) {
            double b0 = s * da, b1 = b0 + da;
            v(bb, cam, fr.at(-0.5, b0, 0.3), sc, (float) (0.16 * H)); v(bb, cam, fr.at(-0.5, b1, 0.3), sc, (float) (0.16 * H));
            v(bb, cam, fr.at(len * 1.4, b1, RAD * 8.0), sc, 0.0F); v(bb, cam, fr.at(len * 1.4, b0, RAD * 8.0), sc, 0.0F);
        }
    }

    private static void drawSolid(RenderLevelStageEvent event, BufferBuilder bb) {
        MeshData md = bb.build();
        if (md == null) return;
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(event.getProjectionMatrix()), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(md);
        RenderSystem.enableCull();
        RenderSystem.restoreProjectionMatrix();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}
