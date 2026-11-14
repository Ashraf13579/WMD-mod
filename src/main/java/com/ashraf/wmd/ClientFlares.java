package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientFlares {

    private ClientFlares() {}

    private static final class Flare {
        final long born, key;
        final double windX, windZ;
        final float seed;
        Vec3 pos, prev, vel;
        int age = -1;
        final Vec3 at0, v0;
        final double side;
        final Vec3 kick;
        final ArrayDeque<double[]> trail = new ArrayDeque<>();
        Flare(long born, long key, Vec3 at0, Vec3 v0, double side, Vec3 kick, double windX, double windZ, float seed) {
            this.born = born; this.key = key; this.at0 = at0; this.v0 = v0; this.side = side; this.kick = kick; this.windX = windX; this.windZ = windZ; this.seed = seed;
        }
        boolean burning() { return age >= 0 && age < Balance.FLARE_FX_BURN; }
    }

    private static final class Spark { Vec3 pos, vel; int life; final int max; Spark(Vec3 p, Vec3 v, int l) { pos = p; vel = v; life = l; max = l; } }

    private static final List<Flare> FLARES = Statics.client(new ArrayList<>());
    private static final List<Spark> SPARKS = Statics.client(new ArrayList<>());
    private static final long KEY = 0x7A4D_00F1_0000_0000L;
    private static long keys;

    static void salvo(ClientLevel level, Vec3 at, Vec3 v) {
        RandomSource r = level.random;
        long now = level.getGameTime();
        Vec3 flat = new Vec3(v.x, 0.0, v.z);
        Vec3 side = flat.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize().cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Vec3 fwd = flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, -1.0) : flat.normalize();
        double wa = r.nextDouble() * Math.PI * 2;
        double wx = Math.cos(wa) * Balance.FLARE_FX_WIND, wz = Math.sin(wa) * Balance.FLARE_FX_WIND;
        for (int i = 0; i < Balance.FLARE_FX_PAIRS && FLARES.size() < Balance.FLARE_FX_MAX; i++) {
            for (int s = -1; s <= 1; s += 2) {
                double out = Balance.FLARE_FX_EJECT * (0.8 + r.nextDouble() * 0.4);
                Vec3 kick = side.scale(s * out).add(0.0, -0.08 - r.nextDouble() * 0.10, 0.0).add(fwd.scale((r.nextDouble() - 0.6) * 0.15));
                FLARES.add(new Flare(now + (long) i * Balance.FLARE_FX_GAP, KEY ^ (keys++), at.add(v.scale(i * Balance.FLARE_FX_GAP)), v, s, kick, wx, wz,
                        r.nextFloat() * 100.0F));
            }
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { if (!FLARES.isEmpty()) FLARES.clear(); if (!SPARKS.isEmpty()) SPARKS.clear(); return; }
        if (FLARES.isEmpty() && SPARKS.isEmpty()) return;
        long now = level.getGameTime();
        RandomSource r = level.random;
        for (Iterator<Flare> it = FLARES.iterator(); it.hasNext(); ) {
            Flare f = it.next();
            if (now < f.born) continue;
            if (now > f.born + Balance.FLARE_FX_BURN + Balance.FLARE_FX_TRAIL + 40 || now < f.born - 200) { it.remove(); continue; }
            if (f.age < 0) {
                f.pos = f.at0.add(f.v0.scale(now - f.born));
                f.prev = f.pos;
                f.vel = f.v0.scale(Balance.FLARE_FX_KEEP).add(f.kick);
                f.age = 0;
                VeilBridge.flash(f.pos.x, f.pos.y, f.pos.z, 1.0F, 0.9F, 0.7F, 5.0F, 1.2F, 3);
            }
            while (!f.trail.isEmpty() && now - f.trail.peekLast()[3] > Balance.FLARE_FX_TRAIL) f.trail.pollLast();
            if (!f.burning()) { f.age++; if (f.trail.isEmpty()) it.remove(); continue; }
            f.prev = f.pos;
            f.vel = f.vel.scale(Balance.FLARE_FX_DRAG).add(0.0, -Balance.FLARE_FX_GRAV, 0.0);
            f.pos = f.pos.add(f.vel);
            f.trail.addFirst(new double[] { f.pos.x, f.pos.y, f.pos.z, now });
            f.age++;
            float k = coreLeft(f.age);
            for (int i = 0; i < 2 && SPARKS.size() < 400; i++)
                SPARKS.add(new Spark(f.pos, f.vel.scale(0.4).add(r.nextGaussian() * 0.12, r.nextGaussian() * 0.12 + 0.03, r.nextGaussian() * 0.12), 5 + r.nextInt(6)));
            VeilBridge.glow(f.key, f.pos.x, f.pos.y, f.pos.z, 1.0F, 0.85F, 0.6F, 4.0F + 10.0F * k, 2.4F * k, now);
        }
        for (Iterator<Spark> it = SPARKS.iterator(); it.hasNext(); ) {
            Spark s = it.next();
            if (--s.life <= 0) { it.remove(); continue; }
            s.pos = s.pos.add(s.vel);
            s.vel = s.vel.scale(0.88).add(0.0, -0.03, 0.0);
        }
    }

    private static float coreLeft(double age) {
        double left = Balance.FLARE_FX_BURN - age;
        return (float) Math.max(0.0, Math.min(1.0, left / Balance.FLARE_FX_FADE));
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (FLARES.isEmpty() && SPARKS.isEmpty())) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + pt;
        float sky = Math.max(0.0F, Math.min(1.0F, (level.getSkyDarken(pt) - 0.2F) / 0.8F));
        ClientBlastGeo.beginFrame();
        BufferBuilder smoke = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Flare f : FLARES) if (f.age >= 0 && cam.distanceToSqr(f.pos) < Balance.FLARE_FX_RANGE * Balance.FLARE_FX_RANGE) any |= trail(smoke, cam, f, now, pt, sky);
        ClientBlastGeo.draw(event, smoke, any, false);
        BufferBuilder glow = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        any = false;
        for (Flare f : FLARES) {
            if (!f.burning() || cam.distanceToSqr(f.pos) > Balance.FLARE_FX_RANGE * Balance.FLARE_FX_RANGE) continue;
            any |= core(glow, cam, left, up, f, now, pt, sky);
        }
        for (Spark s : SPARKS) {
            Vec3 p = s.pos.add(s.vel.scale(pt)), tail = p.subtract(s.vel.scale(1.5));
            float a = (float) s.life / s.max;
            ClientBlastGeo.streak(glow, cam, tail.x, tail.y, tail.z, p.x, p.y, p.z, 0.025 * scale(cam, p), 1.0F, 0.78F, 0.42F, 0.0F, 0.9F * a);
            any = true;
        }
        ClientBlastGeo.draw(event, glow, any, true);
    }

    private static double scale(Vec3 cam, Vec3 p) { return Math.max(1.0, Math.min(6.0, cam.distanceTo(p) / Balance.FLARE_FX_NEAR)); }

    private static boolean core(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Flare f, double now, float pt, float sky) {
        Vec3 p = f.prev.lerp(f.pos, pt);
        float k = coreLeft(f.age - 1 + pt);
        if (k <= 0.0F) return false;
        double t = now + f.seed;
        float flick = (float) (0.78 + 0.12 * Math.sin(t * 2.3) + 0.10 * Math.sin(t * 5.7 + 1.3));
        double s = scale(cam, p) * (0.35 + 0.65 * k);
        float night = 1.0F + 0.8F * (1.0F - sky);
        float g = 0.55F + 0.45F * k, b = 0.25F + 0.65F * k;
        ClientBlastGeo.disc(bb, cam, left, up, p.x, p.y, p.z, Balance.FLARE_FX_HALO * s * night, 1.0F, 0.55F * g, 0.2F * b, 0.16F * flick * k * night, 12);
        ClientBlastGeo.disc(bb, cam, left, up, p.x, p.y, p.z, Balance.FLARE_FX_HALO * 0.35 * s, 1.0F, 0.82F * g, 0.5F * b, 0.55F * flick * k, 12);
        ClientBlastGeo.disc(bb, cam, left, up, p.x, p.y, p.z, Balance.FLARE_FX_CORE * s * flick, 1.0F, 0.98F * g, 0.92F * b, 1.0F, 10);
        ClientBlastGeo.disc(bb, cam, left, up, p.x, p.y, p.z, Balance.FLARE_FX_CORE * 0.6 * s, 1.0F, 1.0F, 0.97F, 1.0F, 8);
        return true;
    }

    private static boolean trail(BufferBuilder bb, Vec3 cam, Flare f, double now, float pt, float sky) {
        if (f.trail.isEmpty()) return false;
        boolean drew = false;
        Vec3 prevP = null;
        double prevAge = 0.0;
        if (f.burning()) { prevP = f.prev.lerp(f.pos, pt); prevAge = 0.0; }
        float base = 0.30F + 0.68F * sky;
        for (double[] q : f.trail) {
            double age = Math.max(0.0, now - q[3]);
            Vec3 p = new Vec3(q[0] + f.windX * age, q[1] - Balance.FLARE_FX_SINK * age, q[2] + f.windZ * age);
            if (prevP != null) {
                Vec3 along = p.subtract(prevP);
                if (along.lengthSqr() > 1.0E-8) {
                    Vec3 side = along.cross(prevP.subtract(cam));
                    if (side.lengthSqr() > 1.0E-10) {
                        side = side.normalize();
                        double wa = width(prevAge), wb = width(age);
                        float aa = alpha(prevAge), ab = alpha(age);
                        if (aa > 0.004F || ab > 0.004F) {
                            float[] ca = tint(prevAge, base, f.burning()), cb = tint(age, base, f.burning());
                            ClientBlastGeo.quad(bb, cam, prevP.add(side.scale(wa)), prevP.subtract(side.scale(wa)), p.subtract(side.scale(wb)), p.add(side.scale(wb)),
                                    ca[0], ca[1], ca[2], aa, cb[0], cb[1], cb[2], ab);
                            drew = true;
                        }
                    }
                }
            }
            prevP = p;
            prevAge = age;
        }
        return drew;
    }

    private static double width(double age) { return Balance.FLARE_FX_TRAIL_W0 + Balance.FLARE_FX_TRAIL_GROW * age; }

    private static float alpha(double age) {
        double u = age / Balance.FLARE_FX_TRAIL;
        if (u >= 1.0) return 0.0F;
        double in = Math.min(1.0, (age + 0.5) / 2.0);
        return (float) (Balance.FLARE_FX_TRAIL_ALPHA * in * Math.pow(1.0 - u, 1.6));
    }

    private static float[] tint(double age, float base, boolean burning) {
        if (!burning || age >= 10.0) return new float[] { base, base, base * 0.98F };
        float w = (float) (1.0 - age / 10.0);
        return new float[] { Math.min(1.0F, base + 0.3F * w), base + 0.12F * w, base * (1.0F - 0.35F * w) };
    }
}
