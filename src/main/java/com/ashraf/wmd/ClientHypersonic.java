package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientHypersonic {

    private ClientHypersonic() {}

    static final double TRAIL_STEP = 1.2, OVERFLY_RANGE = 600.0, VIEW = 1600.0;
    static final int TRAIL_MAX = 90, BOOM_AFTER = 2, DOUBLE_GAP = 5, SCREAM_TICKS = 40;
    private static final DustColorTransitionOptions PLASMA = new DustColorTransitionOptions(new Vector3f(1.0F, 0.75F, 0.95F), new Vector3f(0.55F, 0.2F, 0.9F), 2.2F);
    private static final DustColorTransitionOptions PLASMA_CORE = new DustColorTransitionOptions(new Vector3f(1.0F, 0.97F, 0.95F), new Vector3f(1.0F, 0.55F, 0.85F), 1.4F);

    private static final Set<Long> BOOMED = new HashSet<>(), PASSED = new HashSet<>(), DIVED = new HashSet<>(), SCREAMED = new HashSet<>();
    private static final Map<Long, Double> GROUND_DIST = new HashMap<>();

    private record Later(long at, double x, double y, double z, SoundEvent sound, float volume, float pitch) {}
    private static final List<Later> LATER = new ArrayList<>();

    static final int APPROACH_EVERY = 14, STREAK_LINGER = 30;

    private static final class Incoming {
        final HyperIncomingPayload p; long nextBoom; boolean dived, screamed;
        Incoming(HyperIncomingPayload p) { this.p = p; this.nextBoom = p.warn(); }
        Vec3 pt(int i) { return new Vec3(p.pts()[3 * i], p.pts()[3 * i + 1], p.pts()[3 * i + 2]); }
        int n() { return p.pts().length / 3 - 1; }

        Vec3 dive(double u) {
            int n = n();
            double f = Math.max(0.0, Math.min(1.0, u)) * (n - 1);
            int i = Math.min(n - 2, (int) f);
            return pt(1 + i).lerp(pt(2 + i), f - i);
        }
    }
    private static final Map<Long, Incoming> INCOMING = new HashMap<>();

    public static void incoming(HyperIncomingPayload p) { if (p.pts().length >= 9) INCOMING.put(p.flight(), new Incoming(p)); }

    private static void incomingTick(ClientLevel level, long now, Vec3 cam) {
        for (Iterator<Incoming> it = INCOMING.values().iterator(); it.hasNext(); ) {
            Incoming in = it.next();
            HyperIncomingPayload p = in.p;
            if (now > p.impact() + STREAK_LINGER) { it.remove(); continue; }
            Vec3 target = in.pt(in.n());

            if (now < p.dive() && now >= in.nextBoom) {
                in.nextBoom = now + APPROACH_EVERY;
                double u = (now - p.warn()) / (double) Math.max(1L, p.dive() - p.warn());
                Vec3 at = in.pt(0).lerp(in.pt(1), u);
                float k = (float) Math.min(1.0, u);
                doubleCrack(level, now, at, at.distanceTo(cam) < 400.0, 14.0F + 18.0F * k);
            }

            if (now >= p.dive() && !in.dived) {
                in.dived = true;
                Vec3 top = in.pt(1);
                level.playLocalSound(top.x, top.y, top.z, WmdSounds.SONIC_BOOM_FAR.get(), SoundSource.HOSTILE, 40.0F, 0.75F, false);
                LATER.add(new Later(now + DOUBLE_GAP, top.x, top.y, top.z, WmdSounds.SONIC_BOOM_FAR.get(), 36.0F, 0.9F));
                LATER.add(new Later(now + 2L * DOUBLE_GAP, target.x, target.y + 60.0, target.z, WmdSounds.SONIC_BOOM.get(), 20.0F, 0.8F));
                LaunchSounds.reentry(top.x, top.y, top.z, 50.0F, 1.15F);
                VeilBridge.flash(top.x, top.y, top.z, 1.0F, 0.85F, 1.0F, 70.0F, 4.0F, 8);
            }
            if (now >= p.dive() && now < p.impact() && now % 6 == 0)
                T4Fx.shakeNear(target, 600.0, 0.3F + 0.6F * (float) Math.min(1.0, (now - p.dive()) / (double) Math.max(1L, p.impact() - p.dive())));

            if (now >= p.impact() - SCREAM_TICKS && now < p.impact() && !in.screamed) {
                in.screamed = true;
                Vec3 at = in.dive((now - p.dive()) / (double) Math.max(1L, p.impact() - p.dive()));
                level.playLocalSound(at.x, at.y, at.z, WmdSounds.TERMINAL_DIVE.get(), SoundSource.HOSTILE, 24.0F, 1.45F, false);
            }
        }
    }

    @SubscribeEvent
    static void onRender(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES || INCOMING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        double t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        com.mojang.blaze3d.vertex.BufferBuilder bb = null;
        for (Incoming in : INCOMING.values()) {
            HyperIncomingPayload p = in.p;
            if (t < p.dive()) continue;
            double span = Math.max(1.0, p.impact() - p.dive()), head = Math.min(1.0, (t - p.dive()) / span);
            float fade = t <= p.impact() ? 1.0F : (float) Math.max(0.0, 1.0 - (t - p.impact()) / STREAK_LINGER);
            if (fade <= 0.0F) continue;
            if (bb == null) bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
            int seg = 24;
            for (int k = 0; k < seg; k++) {
                double u0 = head * k / seg, u1 = head * (k + 1) / seg;
                Vec3 a = in.dive(u0).subtract(cam), b = in.dive(u1).subtract(cam);
                float tail = (float) (0.35 + 0.65 * (k + 1) / seg);
                streak(bb, a, b, 2.4, 1.0F, 0.45F, 0.85F, 0.22F * fade * tail);
                streak(bb, a, b, 0.55, 1.0F, 0.92F, 0.97F, 0.95F * fade * tail);
            }
        }
        if (bb != null) ClientBlastGeo.draw(event, bb, true, true);
    }

    private static void streak(com.mojang.blaze3d.vertex.BufferBuilder bb, Vec3 a, Vec3 b, double w, float r, float g, float bl, float al) {
        Vec3 d = b.subtract(a), side = d.cross(a.add(b).scale(0.5));
        if (side.lengthSqr() < 1.0E-9) return;
        side = side.normalize().scale(w);
        bb.addVertex((float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setColor(r, g, bl, al);
        bb.addVertex((float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setColor(r, g, bl, al);
        bb.addVertex((float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).setColor(r, g, bl, al);
        bb.addVertex((float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).setColor(r, g, bl, al);
    }

    private static void doubleCrack(ClientLevel level, long now, Vec3 p, boolean near, float volume) {
        SoundEvent s = near ? WmdSounds.SONIC_BOOM.get() : WmdSounds.SONIC_BOOM_FAR.get();
        level.playLocalSound(p.x, p.y, p.z, s, SoundSource.HOSTILE, volume, 0.9F, false);
        LATER.add(new Later(now + DOUBLE_GAP, p.x, p.y, p.z, s, volume * 0.9F, 1.05F));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || mc.player == null) {
            if (level == null) { BOOMED.clear(); PASSED.clear(); DIVED.clear(); SCREAMED.clear(); GROUND_DIST.clear(); LATER.clear(); INCOMING.clear(); }
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Later> it = LATER.iterator(); it.hasNext(); ) {
            Later l = it.next();
            if (now < l.at()) continue;
            level.playLocalSound(l.x(), l.y(), l.z(), l.sound(), SoundSource.HOSTILE, l.volume(), l.pitch(), false);
            it.remove();
        }
        RandomSource rnd = level.random;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        incomingTick(level, now, cam);
        for (Flight f : ClientFlights.FLIGHTS.values()) {
            if (f.build() == null || !f.build().hypersonic() || now < f.start() || now >= f.endTime()) continue;
            Vec3 p = f.position(now), q = f.position(now - 1);
            double far = p.distanceTo(cam);
            Vec3 d = p.subtract(q);
            double len = d.length();
            Vec3 dir = len < 1.0E-6 ? new Vec3(0, 1, 0) : d.scale(1.0 / len);
            boolean diving = f.inDive(now);
            if (far <= VIEW) {

                VeilBridge.glow(0x4B00_0000_0000_0000L ^ f.id(), p.x, p.y, p.z, 1.0F, 0.7F, 0.95F, diving ? 44.0F : 28.0F,
                        (diving ? 5.0F : 3.4F) * (0.85F + rnd.nextFloat() * 0.3F), 0.6F, now);
                if (diving) VeilBridge.bloomNear(p.x, p.y, p.z, 2500.0, 1.1F);
                for (int i = 0; i < 3; i++)
                    level.addAlwaysVisibleParticle(PLASMA_CORE, true, p.x + rnd.nextGaussian() * 0.2, p.y + rnd.nextGaussian() * 0.2, p.z + rnd.nextGaussian() * 0.2, 0, 0, 0);

                int n = (int) Math.min(TRAIL_MAX, Math.ceil(len / TRAIL_STEP));
                for (int i = 0; i < n; i++) {
                    Vec3 t = q.add(d.scale(i / (double) Math.max(1, n)));
                    level.addAlwaysVisibleParticle(PLASMA, true, t.x + rnd.nextGaussian() * 0.35, t.y + rnd.nextGaussian() * 0.35, t.z + rnd.nextGaussian() * 0.35, 0, 0, 0);
                }
            }

            if (now - f.start() >= BOOM_AFTER && BOOMED.add(f.id())) {
                Vec3 a = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0), u = dir.cross(a).normalize(), v = dir.cross(u).normalize();
                for (int i = 0; i < 48; i++) {
                    double ang = Math.PI * 2.0 * i / 48;
                    Vec3 o = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
                    level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, p.x + o.x * 2.0, p.y + o.y * 2.0, p.z + o.z * 2.0, o.x * 0.6 - dir.x * 0.2, o.y * 0.6 - dir.y * 0.2, o.z * 0.6 - dir.z * 0.2);
                }
                VeilBridge.flash(p.x, p.y, p.z, 1.0F, 0.85F, 1.0F, 40.0F, 3.0F, 6);
                doubleCrack(level, now, p, far < 400.0, far < 400.0 ? 8.0F : 16.0F);
            }

            double gd = Math.hypot(p.x - cam.x, p.z - cam.z);
            Double prev = GROUND_DIST.put(f.id(), gd);
            if (prev != null && gd > prev && prev < OVERFLY_RANGE && !diving && PASSED.add(f.id())) {
                float k = (float) (1.0 - prev / OVERFLY_RANGE);
                doubleCrack(level, now, p, prev < 200.0, 6.0F + 14.0F * k);
                if (prev < 150.0) T4Fx.shakeNear(cam, 50.0, 0.25F + 0.35F * k);
            }

            Vec3 target = f.seenTarget(now);
            boolean told = INCOMING.containsKey(f.id());
            if (diving && !told && DIVED.add(f.id())) {
                level.playLocalSound(target.x, target.y + 100.0, target.z, WmdSounds.SONIC_BOOM_FAR.get(), SoundSource.HOSTILE, 30.0F, 0.8F, false);
                LATER.add(new Later(now + DOUBLE_GAP, target.x, target.y + 100.0, target.z, WmdSounds.SONIC_BOOM_FAR.get(), 26.0F, 0.95F));
                LaunchSounds.reentry(p.x, p.y, p.z, 40.0F, 1.15F);
                VeilBridge.flash(p.x, p.y, p.z, 1.0F, 0.85F, 1.0F, 60.0F, 4.0F, 8);
            }
            long left = f.endTime() - now;
            if (diving && left % 8 == 0) T4Fx.shakeNear(target, 700.0, 0.2F + 0.5F * (1.0F - Math.min(1.0F, left / 120.0F)));
            if (left <= SCREAM_TICKS && !told && SCREAMED.add(f.id()))
                level.playLocalSound(p.x, p.y, p.z, WmdSounds.TERMINAL_DIVE.get(), SoundSource.HOSTILE, 16.0F, 1.4F, false);
        }
        if (BOOMED.size() > 256) {
            BOOMED.removeIf(id -> !ClientFlights.FLIGHTS.containsKey(id));
            PASSED.removeIf(id -> !ClientFlights.FLIGHTS.containsKey(id));
            DIVED.removeIf(id -> !ClientFlights.FLIGHTS.containsKey(id));
            SCREAMED.removeIf(id -> !ClientFlights.FLIGHTS.containsKey(id));
            GROUND_DIST.keySet().removeIf(id -> !ClientFlights.FLIGHTS.containsKey(id));
        }
    }
}
