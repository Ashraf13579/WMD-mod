package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientPlasmaMortar {

    private ClientPlasmaMortar() {}

    static final double FX_RANGE = 256.0;
    static final int TRAIL = 16, SPARKS = 6;
    static final double SPARK_LIFE = 14.0;

    private static final Map<Long, PlasmaMortarBlockEntity> LIVE = Statics.client(new HashMap<>());
    private record Burn(Vec3 at, long start) {}
    private static final List<Burn> BURNS = Statics.client(new ArrayList<>());

    static void tick(PlasmaMortarBlockEntity be) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || be.getLevel() != level) return;
        LIVE.put(be.getBlockPos().asLong(), be);
        long now = level.getGameTime();
        double since = now - be.lastFire();
        if (since >= 12 && since < Balance.PMORT_VENT - 40 && now % 2 == 0) {
            Vec3 c = be.breechPoint();
            if (c.distanceTo(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()) > FX_RANGE) return;
            RandomSource rnd = level.random;
            for (int s = -1; s <= 1; s += 2) {
                double[] w = be.worldPoint(s * 12.75 / 16.0, 35.6 / 16.0, 5.0 / 16.0);
                level.addAlwaysVisibleParticle(WmdParticles.VAPOUR.get(), true, w[0] + rnd.nextGaussian() * 0.12, w[1], w[2] + rnd.nextGaussian() * 0.25,
                        rnd.nextGaussian() * 0.01, 0.05 + rnd.nextDouble() * 0.04, rnd.nextGaussian() * 0.01);
            }
        }
    }

    static void muzzle(ClientLevel level, ArtShell s, Vec3 cam) {
        Vec3 m = new Vec3(s.ox(), s.oy(), s.oz());
        WmdSound.play(WmdSounds.PM_FIRE.get(), m.x, m.y, m.z, Balance.SND_RELEASE, 1.0F);
        VeilBridge.flash(m.x, m.y, m.z, 0.45F, 0.85F, 1.0F, 18.0F, 3.0F, 0.6F, 6);
        if (m.distanceTo(cam) > FX_RANGE) return;
        RandomSource rnd = level.random;
        for (int i = 0; i < 10; i++)
            level.addAlwaysVisibleParticle(WmdParticles.VAPOUR.get(), true, m.x + rnd.nextGaussian() * 0.2, m.y + rnd.nextGaussian() * 0.2, m.z + rnd.nextGaussian() * 0.2,
                    rnd.nextGaussian() * 0.03, 0.04 + rnd.nextDouble() * 0.05, rnd.nextGaussian() * 0.03);
    }

    static void landed(Vec3 at, long now) {
        BURNS.add(new Burn(at, now));
        if (BURNS.size() > 32) BURNS.remove(0);
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { LIVE.clear(); BURNS.clear(); return; }
        boolean shells = false;
        for (ArtShell s : ArtilleryView.shells()) if (s.gun() == Artillery.PLASMA) { shells = true; break; }
        if (LIVE.isEmpty() && BURNS.isEmpty() && !shells) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        double tt = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = level.getGameTime();
        ClientBlastGeo.beginFrame();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;

        for (Iterator<PlasmaMortarBlockEntity> it = LIVE.values().iterator(); it.hasNext(); ) {
            PlasmaMortarBlockEntity be = it.next();
            if (be.isRemoved() || be.getLevel() != level) { it.remove(); continue; }
            float k = be.ball(tt);
            if (k <= 0.01F) continue;
            Vec3 c = be.breechPoint();
            if (c.distanceTo(cam) > FX_RANGE) continue;
            float fl = 0.85F + 0.15F * (float) Math.sin(tt * 1.7 + be.getBlockPos().getX());
            ball(bb, cam, left, up, c, 0.05 + 0.18 * k, tt, be.getBlockPos().asLong(), k);
            if (k > 0.2F && level.random.nextInt(3) == 0) {
                for (int s = -1; s <= 1; s += 2) {
                    double[] tip = be.worldPoint(s * 4.55 / 16.0, PlasmaMortarBlockEntity.PIV_Y / 16.0, PlasmaMortarBlockEntity.PIV_Z / 16.0);
                    ClientBlastGeo.streak(bb, cam, tip[0], tip[1], tip[2], c.x + level.random.nextGaussian() * 0.05, c.y + level.random.nextGaussian() * 0.05,
                            c.z + level.random.nextGaussian() * 0.05, 0.025, 0.55F, 0.85F, 1.0F, 0.9F, 0.4F);
                }
            }
            VeilBridge.glow(be.getBlockPos().asLong() * 31L + 7L, c.x, c.y, c.z, 0.4F, 0.75F, 1.0F, 3.0F + 5.0F * k, 1.4F * k * fl, now);
            any = true;
        }

        double near = Math.min(ClientFlights.nearLimit(), 768.0);
        for (ArtShell s : ArtilleryView.shells()) {
            if (s.gun() != Artillery.PLASMA || tt < s.start() + 1 || tt > s.endTime()) continue;
            Vec3 p = s.position(tt);
            if (p.distanceTo(cam) > near) continue;
            double R = Balance.PMORT_BALL_R;

            Vec3 b4 = s.position(tt - 1.0);
            double dx = p.x - b4.x, dy = p.y - b4.y, dz = p.z - b4.z, dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dl < 1e-6) { dx = 1.0; dy = 0.0; dz = 0.0; dl = 1.0; }
            dx /= dl; dy /= dl; dz /= dl;
            double ax = -dz, az = dx, al = Math.sqrt(ax * ax + az * az);
            if (al < 1e-6) { ax = 1.0; az = 0.0; al = 1.0; }
            ax /= al; az /= al;
            double bx = dy * az, by = dz * ax - dx * az, bz = -dy * ax;
            for (int i = TRAIL; i >= 1; i--) {
                double t = tt - i * 0.9;
                if (t < s.start()) continue;
                Vec3 q = s.position(t);
                float f = 1.0F - i / (float) (TRAIL + 1);
                ClientBlastGeo.disc(bb, cam, left, up, q.x, q.y, q.z, R * (0.3 + 0.85 * f), 0.5F - 0.05F * f, 0.4F + 0.5F * f, 1.0F, 0.55F * f * f);
            }
            for (int st = 0; st < 2; st++) {
                double lx = p.x, ly = p.y, lz = p.z;
                float la = 0.65F;
                for (int i = 1; i <= TRAIL; i++) {
                    double t = tt - i * 0.9;
                    if (t < s.start()) break;
                    Vec3 q = s.position(t);
                    float f = 1.0F - i / (float) (TRAIL + 1);
                    double ph = tt * 0.35 + i * 0.55 + st * Math.PI, off = R * 0.85 * f, co = Math.cos(ph) * off, so = Math.sin(ph) * off;
                    double qx = q.x + ax * co + bx * so, qy = q.y + by * so, qz = q.z + az * co + bz * so;
                    float qa = 0.65F * f;
                    ClientBlastGeo.streak(bb, cam, lx, ly, lz, qx, qy, qz, R * 0.07, st == 0 ? 0.45F : 0.72F, st == 0 ? 0.88F : 0.5F, 1.0F, la, qa);
                    lx = qx; ly = qy; lz = qz; la = qa;
                }
            }
            for (int k = 0; k < SPARKS; k++) {
                double born = Math.floor((tt - k * 2.3) / SPARK_LIFE) * SPARK_LIFE + k * 2.3, age = tt - born;
                if (born < s.start() + 1) continue;
                Vec3 q = s.position(born);
                long hk = (long) s.id() * 131L + (long) born * 7L + k;
                double sx = (h(hk) - 0.5) * R * 1.6, sz = (h(hk + 1) - 0.5) * R * 1.6;
                double fx = q.x + sx + (h(hk + 2) - 0.5) * 0.04 * age, fy = q.y - 0.012 * age * age, fz = q.z + sz + (h(hk + 3) - 0.5) * 0.04 * age;
                float life = (float) (1.0 - age / SPARK_LIFE);
                ClientBlastGeo.disc(bb, cam, left, up, fx, fy, fz, R * 0.24, 0.75F, 0.55F, 1.0F, 0.85F * life);
                ClientBlastGeo.disc(bb, cam, left, up, fx, fy, fz, R * 0.09, 1.0F, 0.95F, 1.0F, 0.9F * life);
            }
            float fl = 0.9F + 0.1F * (float) Math.sin(tt * 2.3 + s.id());
            ball(bb, cam, left, up, p, R, tt, s.id(), 1.0F);
            VeilBridge.glow(0x504D4F52L ^ s.id(), p.x, p.y, p.z, 0.4F, 0.75F, 1.0F, 12.0F, 2.2F * fl, now);
            any = true;
        }

        for (Iterator<Burn> it = BURNS.iterator(); it.hasNext(); ) {
            Burn b = it.next();
            double age = tt - b.start();
            if (age > Balance.PMORT_BURN || age < -40) { it.remove(); continue; }
            if (b.at().distanceTo(cam) > FX_RANGE * 2) continue;
            if (age < PlasmaMortarServer.MELT_AT) continue;
            float life = (float) (1.0 - age / Balance.PMORT_BURN), fl = 0.8F + 0.2F * (float) Math.sin(age * 0.6 + b.at().x);
            ClientBlastGeo.flatDisc(bb, cam, b.at().x, b.at().y + 0.15, b.at().z, Balance.PMORT_MELT_R * 1.15, 1.0F, 0.38F, 0.08F, 0.55F * life * fl, 20);
            ClientBlastGeo.flatDisc(bb, cam, b.at().x, b.at().y + 0.2, b.at().z, Balance.PMORT_MELT_R * 0.6, 1.0F, 0.75F, 0.3F, 0.5F * life * fl, 16);
            if (age < 200) {
                float h = (float) (1.0 - age / 200.0);
                ClientBlastGeo.disc(bb, cam, left, up, b.at().x, b.at().y + 1.5, b.at().z, 4.0 + age * 0.01, 0.35F, 0.7F, 1.0F, 0.3F * h);
            }
            VeilBridge.glow(Double.doubleToLongBits(b.at().x * 31.0 + b.at().z), b.at().x, b.at().y + 1.0, b.at().z, 1.0F, 0.45F, 0.12F, 14.0F, 2.0F * life * fl, now);
            any = true;
        }
        ClientBlastGeo.draw(event, bb, any, true);
    }

    static double h(long x) {
        x ^= x >>> 33; x *= 0xff51afd7ed558ccdL; x ^= x >>> 33; x *= 0xc4ceb9fe1a85ec53L; x ^= x >>> 33;
        return (x >>> 11) * 0x1.0p-53;
    }

    static void ball(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Vec3 c, double R, double tt, long seed, float k) {
        double sp = seed & 1023L;
        float fl = 0.88F + 0.12F * (float) Math.sin(tt * 2.3 + sp * 0.37);
        ClientBlastGeo.disc(bb, cam, left, up, c.x, c.y, c.z, R * 2.7, 0.25F, 0.5F, 1.0F, 0.32F * fl * k);
        ClientBlastGeo.disc(bb, cam, left, up, c.x, c.y, c.z, R * 1.5, 0.4F, 0.82F, 1.0F, 0.7F * fl * k);
        for (int i = 0; i < 5; i++) {
            double a = tt * (0.55 + 0.11 * i) + i * 1.26 + sp, b = tt * (0.71 - 0.07 * i) + i * 2.3;
            double rr = R * (0.28 + 0.14 * Math.sin(tt * 0.9 + i * 1.9)), ox = Math.cos(a) * rr, oy = Math.sin(b) * rr;
            boolean white = (i & 1) == 0;
            ClientBlastGeo.disc(bb, cam, left, up, c.x + left.x() * ox + up.x() * oy, c.y + left.y() * ox + up.y() * oy, c.z + left.z() * ox + up.z() * oy,
                    R * 0.5, white ? 1.0F : 0.7F, white ? 1.0F : 0.95F, 1.0F, 0.55F * k);
        }
        ClientBlastGeo.disc(bb, cam, left, up, c.x, c.y, c.z, R * 0.5, 1.0F, 1.0F, 1.0F, 0.95F * k);
        if (k > 0.3F) {
            float rk = Math.min(1.0F, (k - 0.3F) / 0.4F);
            double rr = R * 1.3, w = Math.max(0.012, R * 0.045);
            for (int j = 0; j < 3; j++) {
                double phi = 0.45 + j * 0.6, th = tt * (0.05 + 0.025 * j) + j * 2.1 + sp;
                double nx = Math.sin(phi) * Math.cos(th), ny = Math.cos(phi), nz = Math.sin(phi) * Math.sin(th);
                double ux = -nz, uz = nx, ul = Math.sqrt(ux * ux + uz * uz);
                ux /= ul; uz /= ul;
                double vx = ny * uz, vy = nz * ux - nx * uz, vz = -ny * ux;
                double spin = tt * (0.30 + 0.08 * j) * (j == 1 ? -1.0 : 1.0);
                float cr = j == 1 ? 0.72F : 0.45F, cg = j == 1 ? 0.5F : 0.88F;
                double px = 0.0, py = 0.0, pz = 0.0;
                float pa = 0.0F;
                for (int s = 0; s <= 24; s++) {
                    double a = Math.PI * 2 * s / 24.0, ca = Math.cos(a) * rr, sa = Math.sin(a) * rr;
                    double qx = c.x + ux * ca + vx * sa, qy = c.y + vy * sa, qz = c.z + uz * ca + vz * sa;
                    double node = Math.max(0.0, Math.cos(a - spin));
                    float qa = (0.35F + 0.6F * (float) (node * node * node * node)) * rk * fl;
                    if (s > 0) ClientBlastGeo.streak(bb, cam, px, py, pz, qx, qy, qz, w, cr, cg, 1.0F, pa, qa);
                    px = qx; py = qy; pz = qz; pa = qa;
                }
            }
        }
        if (k > 0.2F) {
            long bucket = (long) Math.floor(tt / 2.0);
            int arcs = 1 + (int) (h(seed * 31L + bucket) * 3.0);
            double w = Math.max(0.015, R * 0.035), jit = R * 0.28;
            for (int i = 0; i < arcs; i++) {
                long q = seed * 977L + bucket * 131L + i * 17L;
                double yaw = h(q) * Math.PI * 2, pit = Math.asin(h(q + 1) * 2.0 - 1.0);
                double dx = Math.cos(pit) * Math.cos(yaw), dy = Math.sin(pit), dz = Math.cos(pit) * Math.sin(yaw), len = R * (1.1 + h(q + 2) * 1.3) / 5.0;
                double ax = c.x + dx * R * 0.5, ay = c.y + dy * R * 0.5, az = c.z + dz * R * 0.5;
                for (int s = 1; s <= 5; s++) {
                    double bx = ax + dx * len + (h(q + 10 + 3 * s) - 0.5) * jit, by = ay + dy * len + (h(q + 11 + 3 * s) - 0.5) * jit,
                            bz = az + dz * len + (h(q + 12 + 3 * s) - 0.5) * jit;
                    ClientBlastGeo.streak(bb, cam, ax, ay, az, bx, by, bz, w, 0.85F, 0.93F, 1.0F, 0.95F * k * (1.0F - (s - 1) / 5.0F), 0.95F * k * (1.0F - s / 5.0F));
                    ax = bx; ay = by; az = bz;
                }
            }
        }
    }
}
