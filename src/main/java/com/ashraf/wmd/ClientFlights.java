package com.ashraf.wmd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientFlights {

    private ClientFlights() {}

    static final Map<Long, Flight> FLIGHTS = new ConcurrentHashMap<>();

    private static final Map<Long, Double> HULL = new ConcurrentHashMap<>();
    private static final int HULL_HIDE = 10;
    private static final Map<Long, IncomingSound> SOUNDS = new HashMap<>();

    private static final java.util.Set<Long> SWERVED = new java.util.HashSet<>(), SWERVED2 = new java.util.HashSet<>();
    private static final double DRAW_RANGE = 1500.0, SCREAM_RANGE = 1000.0;

    public static void set(List<Flight> list) {
        FLIGHTS.clear();
        for (Flight f : list) FLIGHTS.put(f.id(), f);
    }

    public static void update(List<Flight> changed, List<Long> removed) {
        for (Long id : removed) FLIGHTS.remove(id);
        for (Flight f : changed) FLIGHTS.put(f.id(), f);
    }

    private static double smoother(double x) {
        x = Math.max(0.0, Math.min(1.0, x));
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    private static double attitude(double u) {
        if (u <= 0.0 || u >= 1.0) return 0.0;
        if (u < 0.3) return smoother(u / 0.3);
        if (u > 0.7) return 1.0 - smoother((u - 0.7) / 0.3);
        return 1.0;
    }

    private static Vec3 tilted(Flight f, double t, Vec3 ahead) {
        double u = (t - f.swerveAt()) / Flight.SWERVE_TICKS, a = attitude(u);
        if (a <= 0.0) return ahead;
        double sx = f.tx() - f.fx(), sz = f.tz() - f.fz(), sl = Math.hypot(sx, sz);
        if (sl < 0.5) return ahead;
        double lx = sx / sl, lz = sz / sl;
        double theta = Math.toRadians(Math.min(35.0, 8.0 + 0.55 * sl)) * a;
        double lat = ahead.x * lx + ahead.z * lz, want = Math.abs(ahead.y) * Math.tan(theta);
        return new Vec3(ahead.x + lx * (want - lat), ahead.y, ahead.z + lz * (want - lat));
    }

    private static Quaternionf orientation(Vector3f dir, Flight f) {
        double bx = f.tx() + 0.5 - f.ox(), bz = f.tz() + 0.5 - f.oz(), bl = Math.hypot(bx, bz);
        if (bl < 1.0E-6) { bx = 1.0; bz = 0.0; bl = 1.0; }
        bx /= bl; bz /= bl;
        Vector3f ref = new Vector3f((float) -bz, 0.0F, (float) bx);
        Vector3f ny = new Vector3f(dir);
        Vector3f nx = new Vector3f(ref).sub(new Vector3f(ny).mul(ref.dot(ny)));
        if (nx.lengthSquared() < 1.0E-6F) return new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, dir.x, dir.y, dir.z);
        nx.normalize();
        Vector3f nz = new Vector3f(nx).cross(ny);
        Quaternionf q = new Quaternionf().setFromNormalized(new Matrix3f(nx, ny, nz));
        return q.rotateY((float) -Math.atan2(-ref.z, ref.x));
    }

    static double nearLimit() {
        if (!DhBridge.active()) return DRAW_RANGE;
        return Math.min(DRAW_RANGE, Math.max(192.0, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0 * 1.5));
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || (FLIGHTS.isEmpty() && DEBRIS.isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double t = level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        FogRenderer.setupNoFog();
        boolean drew = !DEBRIS.isEmpty();
        if (drew) renderDebris(ps, buffers, level, cam, pt);
        for (Flight f : FLIGHTS.values()) {
            if (t < f.start() || t > f.endTime()) continue;
            if (f.id() == ClientMissileCam.watching()) continue;
            Double hull = HULL.get(f.id());
            if (hull != null && t >= hull && t < hull + HULL_HIDE) continue;
            Vec3 p = f.position(t);
            if (p.distanceTo(cam) > nearLimit() * 1.1) continue;
            if (f.isCruise()) { ClientCruise.render(f, t, p, cam, ps, buffers, level); drew = true; continue; }
            if (f.build().isSlbm()) { ClientSlbm.renderFlight(f, t, p, cam, ps, buffers, level); drew = true; continue; }
            Vec3 ahead = f.position(t + 1.0).subtract(p);
            if (ahead.lengthSqr() < 1.0E-6) continue;
            if (f.diverts()) ahead = tilted(f, t, ahead);
            Vector3f dir = new Vector3f((float) ahead.x, (float) ahead.y, (float) ahead.z).normalize();
            ps.pushPose();
            ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ps.mulPose(orientation(dir, f));
            ps.translate(0.0, -MissileParts.lengthOf(f.build()) * 0.5, 0.0);
            int light = p.y > level.getMaxBuildHeight() ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, BlockPos.containing(p));
            if (isRod(f) && f.endTime() - t < ROD_TICKS) { ps.translate(0.0, MissileParts.lengthOf(f.build()) * 0.5 - MissileParts.ROD_LEN * 0.5, 0.0); MissileParts.renderRod(ps, buffers, light, pt); }
            else if (f.build().staged() && Crew.isCrew(f.build())) MissileParts.renderCrewStack(f.build(), stagePhase(f, t), true, stagePhase(f, t) == 0, ps, buffers, light, pt);
            else if (f.build().staged() && (StationKit.isKit(f.build()) || Cargo.isCargo(f.build()))) MissileParts.renderSatStack(f.build(), stagePhase(f, t), true, 0.0F, 0.0F, 1.0F, ps, buffers, light, pt);
            else if (f.build().staged() && Satellites.isSatellite(f.build())) {
                float[] sd = satDeploy(f, t);
                MissileParts.renderSatStack(f.build(), stagePhase(f, t), true, sd[0], sd[1], sd[2], ps, buffers, light, pt);
            }
            else if (f.build().staged()) {
                if (Asat.isAsat(f.build())) MissileParts.asatFinOpen = (float) Math.max(0.0, Math.min(1.0, (t - f.start()) / AsatSpec.FIN_OPEN_TICKS));
                MissileParts.renderStack(f.build(), stagePhase(f, t), true, ps, buffers, light, pt);
                MissileParts.asatFinOpen = 0.0F;
            }
            else MissileParts.render(f.build(), ps, buffers, light, pt);
            ps.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch();
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
    }

    private static final class Debris {
        Vec3 pos, vel;
        final Quaternionf q;
        final float spin;
        final int kind;
        int age;
        Debris(Vec3 pos, Vec3 vel, Quaternionf q, float spin, int kind) { this.pos = pos; this.vel = vel; this.q = q; this.spin = spin; this.kind = kind; }
    }

    private static final java.util.List<Debris> DEBRIS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final Map<Long, Integer> SEP = new HashMap<>();

    static long sepTick(Flight f, int k) {
        return k == 1 ? f.start() + Math.min(40, f.duration() / 10) : f.start() + Math.min(160, f.duration() / 4);
    }

    static int stagePhase(Flight f, double t) { return t >= sepTick(f, 2) ? 2 : t >= sepTick(f, 1) ? 1 : 0; }

    static float[] satDeploy(Flight f, double t) {
        long ins = Satellites.insertTick(f);
        long t0 = Math.max(sepTick(f, 2) + 10, ins - 100);
        double w = Math.max(10.0, ins - t0);
        double u = (t - t0) / w;
        if (u <= 0.0) return new float[] { 0.0F, 0.0F, 1.0F };
        float open = (float) Math.min(1.0, u / 0.2);
        double k = Math.max(0.0, Math.min(1.0, (u - 0.2) / 0.4));
        float deploy = (float) (k * k * (3.0 - 2.0 * k));
        float scale = (float) (1.0 - Math.max(0.0, Math.min(1.0, (u - 0.8) / 0.2)));
        return new float[] { open, deploy, scale };
    }

    static double stageTail(Flight f, long now) {
        double half = MissileParts.stackLength(f.build()) * 0.5;
        return stagePhase(f, now) == 2 ? half - MissileParts.CORE_TOP_BLOCKS : half;
    }

    private static void stageEvents(ClientLevel level, long now) {
        for (Flight f : FLIGHTS.values()) {
            if (!f.build().staged() || now < f.start() || now > f.endTime()) continue;
            int done = SEP.getOrDefault(f.id(), 0);
            for (int k = done + 1; k <= 2; k++) {
                if (now < sepTick(f, k)) break;
                SEP.put(f.id(), k);
                if (now - sepTick(f, k) <= 20) separate(level, f, k, now);
            }
        }
        if (SEP.size() > 64) SEP.keySet().removeIf(id -> !FLIGHTS.containsKey(id));
    }

    private static void separate(ClientLevel level, Flight f, int k, long now) {
        RandomSource rnd = level.random;
        Vec3 p = f.position(now), ahead = f.position(now + 1.0).subtract(p);
        if (ahead.lengthSqr() < 1.0E-8) return;
        Vector3f dv = new Vector3f((float) ahead.x, (float) ahead.y, (float) ahead.z).normalize();
        Vec3 dir = new Vec3(dv.x, dv.y, dv.z);
        Quaternionf q = orientation(dv, f);
        double half = MissileParts.stackLength(f.build()) * 0.5;
        if (k == 1) {
            int n = f.build().boosters();
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians(MissileParts.boosterAngle(n, i));
                Vector3f radial = q.transform(new Vector3f((float) Math.cos(a), 0.0F, (float) Math.sin(a)));
                Vec3 rad = new Vec3(radial.x, radial.y, radial.z);
                Vec3 pos = p.add(dir.scale(MissileParts.boosterLength() * 0.5 - half)).add(rad.scale(2.4));
                DEBRIS.add(new Debris(pos, ahead.scale(0.7).add(rad.scale(0.55)).add(rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.05), new Quaternionf(q), 0.012F + rnd.nextFloat() * 0.02F, 1));
            }
            WmdSound.play(net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), p.x, p.y, p.z, 14.0F, 1.5F);
        } else {
            Vec3 pos = p.add(dir.scale(MissileParts.CORE_TOP_BLOCKS * 0.5 - half));
            DEBRIS.add(new Debris(pos, ahead.scale(0.55).subtract(dir.scale(0.12)), new Quaternionf(q), 0.006F + rnd.nextFloat() * 0.008F, 0));
            WmdSound.play(net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), p.x, p.y, p.z, 20.0F, 0.9F);
            WmdSound.play(net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, p.x, p.y, p.z, 16.0F, 0.6F);
        }

        Vec3 at = p.add(dir.scale((k == 1 ? 0.0 : MissileParts.CORE_TOP_BLOCKS) - half));
        VeilBridge.flash(at.x, at.y, at.z, 1.0F, 0.9F, 0.7F, 60.0F, 3.0F, 8);
        for (int i = 0; i < 70; i++)
            level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, at.x + rnd.nextGaussian() * 1.5, at.y + rnd.nextGaussian() * 1.5, at.z + rnd.nextGaussian() * 1.5, rnd.nextGaussian() * 0.35, rnd.nextGaussian() * 0.35, rnd.nextGaussian() * 0.35);
        for (int i = 0; i < 30; i++)
            level.addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, true, at.x, at.y, at.z, rnd.nextGaussian() * 0.5, rnd.nextGaussian() * 0.5, rnd.nextGaussian() * 0.5);
    }

    private static void tickDebris(ClientLevel level, long now) {
        RandomSource rnd = level.random;
        for (Debris d : DEBRIS) {
            d.age++;
            d.vel = d.vel.add(0.0, -0.05, 0.0).scale(0.998);
            d.pos = d.pos.add(d.vel);
            d.q.rotateX(d.spin).rotateZ(d.spin * 0.6F);

            for (int i = 0; i < 3; i++)
                level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, d.pos.x + rnd.nextGaussian() * 0.8, d.pos.y + rnd.nextGaussian() * 0.8, d.pos.z + rnd.nextGaussian() * 0.8, 0, 0.01, 0);
            if (d.age < 60) level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, d.pos.x, d.pos.y, d.pos.z, rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.05);
            BlockPos bp = BlockPos.containing(d.pos);
            boolean loaded = level.hasChunkAt(bp);
            int ground = loaded ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ()) : level.getMinBuildHeight();
            if (d.pos.y <= ground + 1.0 || d.age > 3000 || d.pos.y < level.getMinBuildHeight()) {
                if (loaded && d.age <= 3000) impact(level, d, ground);
                DEBRIS.remove(d);
            }
        }
    }

    private static void impact(ClientLevel level, Debris d, int ground) {
        RandomSource rnd = level.random;
        double x = d.pos.x, y = ground + 1.0, z = d.pos.z;
        boolean core = d.kind == 0;
        level.addParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, x, y, z, 0.0, 0.0, 0.0);
        int n = core ? 90 : 40;
        for (int i = 0; i < n; i++) {
            level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, x + rnd.nextGaussian(), y + rnd.nextDouble() * 2.0, z + rnd.nextGaussian(), rnd.nextGaussian() * 0.35, 0.1 + rnd.nextDouble() * 0.5, rnd.nextGaussian() * 0.35);
            level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, x + rnd.nextGaussian() * 2.0, y + rnd.nextDouble() * 3.0, z + rnd.nextGaussian() * 2.0, rnd.nextGaussian() * 0.25, 0.1 + rnd.nextDouble() * 0.3, rnd.nextGaussian() * 0.25);
        }
        WmdSound.play(net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), x, y, z, core ? 40.0F : 24.0F, core ? 0.55F : 0.8F);
        VeilBridge.flash(x, y + 2.0, z, 1.0F, 0.7F, 0.35F, core ? 90.0F : 50.0F, core ? 3.5F : 2.0F, 12);
    }

    private static void renderDebris(PoseStack ps, MultiBufferSource.BufferSource buffers, ClientLevel level, Vec3 cam, float pt) {
        for (Debris d : DEBRIS) {
            Vec3 p = d.pos.add(d.vel.scale(pt));
            if (p.distanceTo(cam) > nearLimit() * 1.1) continue;
            ps.pushPose();
            ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            ps.mulPose(d.q);
            float len = d.kind == 0 ? MissileParts.CORE_TOP_BLOCKS : d.kind == 2 ? 8.0F : MissileParts.boosterLength();
            ps.translate(0.0, -len * 0.5, 0.0);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(p));
            if (d.kind == 0) MissileParts.renderCore(MissileBuild.preset(4), 3, ps, buffers, light, pt);
            else if (d.kind == 2) MissileParts.renderShell(3, ps, buffers, light, pt);
            else MissileParts.renderBooster(3, ps, buffers, light, pt);
            ps.popPose();
        }
    }

    private static final java.util.Set<Long> FAIRED = new java.util.HashSet<>();

    private static void fairingFx(ClientLevel level, Flight f, long now, Vec3 p, Vec3 dir, RandomSource rnd) {
        if (satDeploy(f, now)[0] <= 0.0F || !FAIRED.add(f.id())) return;
        double half = MissileParts.stackLength(f.build()) * 0.5;
        Vec3 at = p.add(dir.scale(MissileParts.CORE_TOP_BLOCKS + MissileParts.PAYLOAD_H / 32.0 - half));
        LaunchSounds.fairing(at.x, at.y, at.z);
        for (int i = 0; i < 40; i++)
            level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, at.x + rnd.nextGaussian() * 1.2, at.y + rnd.nextGaussian() * 1.2, at.z + rnd.nextGaussian() * 1.2, rnd.nextGaussian() * 0.25, rnd.nextGaussian() * 0.25, rnd.nextGaussian() * 0.25);
        for (int i = 0; i < 20; i++)
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, at.x, at.y, at.z, rnd.nextGaussian() * 0.4, rnd.nextGaussian() * 0.4, rnd.nextGaussian() * 0.4);
        if (FAIRED.size() > 64) FAIRED.removeIf(id -> !FLIGHTS.containsKey(id));
    }

    private static final long ROD_TICKS = 200;
    private static boolean isRod(Flight f) { return f.build().warheadType().equals("rod"); }
    private static final java.util.Set<Long> CONVERTED = new java.util.HashSet<>();

    private static final java.util.Set<Long> BOOMED = new java.util.HashSet<>();

    private static final java.util.Map<Long, Integer> TERMINAL = new java.util.HashMap<>();

    private static void descentSounds(Flight f, long now, Vec3 p) {
        long left = f.endTime() - now;
        if (left > 160 || left <= 0) return;
        int done = TERMINAL.getOrDefault(f.id(), 0);
        if (f.isCruise()) {
            if (left <= 140 && (done & 2) == 0) { TERMINAL.put(f.id(), done | 2); LaunchSounds.cruiseApproach(p.x, p.y, p.z); }
        } else if (f.tier() >= 3) {
            if (left <= 150 && (done & 1) == 0) { done |= 1; TERMINAL.put(f.id(), done); LaunchSounds.boom(p.x, p.y, p.z, 40.0F, 1.0F); }
            if (left <= 130 && (done & 2) == 0) { TERMINAL.put(f.id(), done | 2); LaunchSounds.reentry(p.x, p.y, p.z, 36.0F, 1.0F); }
        } else if (left <= 110 && (done & 2) == 0) {
            TERMINAL.put(f.id(), done | 2);
            LaunchSounds.terminalDive(p.x, p.y, p.z);
        }
    }

    private static void stagedFx(ClientLevel level, Flight f, long now, Vec3 p, Vec3 cam, RandomSource rnd) {
        Vec3 ahead = f.position(now + 1.0).subtract(p);
        if (ahead.lengthSqr() < 1.0E-8) return;
        Vector3f dv = new Vector3f((float) ahead.x, (float) ahead.y, (float) ahead.z).normalize();
        Vec3 dir = new Vec3(dv.x, dv.y, dv.z), back = dir.scale(-1.0);
        if (stagePhase(f, now) == 0 && p.distanceTo(cam) < ClientConfig.trailRange()) {
            Quaternionf q = orientation(dv, f);
            double half = MissileParts.stackLength(f.build()) * 0.5;
            for (int i = 0; i < f.build().boosters(); i++) {
                double a = Math.toRadians(MissileParts.boosterAngle(f.build().boosters(), i));
                Vector3f lat = q.transform(new Vector3f((float) Math.cos(a), 0.0F, (float) Math.sin(a)));
                Vec3 nz = p.add(back.scale(half)).add(lat.x * 2.46, lat.y * 2.46, lat.z * 2.46);
                for (int k = 0; k < 4; k++)
                    level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, nz.x + rnd.nextGaussian() * 0.25, nz.y + rnd.nextGaussian() * 0.25, nz.z + rnd.nextGaussian() * 0.25, back.x * 0.5 + rnd.nextGaussian() * 0.04, back.y * 0.5, back.z * 0.5 + rnd.nextGaussian() * 0.04);
                for (int k = 0; k < 3; k++)
                    level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, nz.x + back.x * k * 1.5 + rnd.nextGaussian() * 0.5, nz.y + back.y * k * 1.5 + rnd.nextGaussian() * 0.5, nz.z + back.z * k * 1.5 + rnd.nextGaussian() * 0.5, 0, -0.01, 0);
                VeilBridge.glow(0x6E00_0000_0000_0000L ^ (f.id() * 4 + i), nz.x, nz.y, nz.z, 1.0F, 0.7F, 0.35F, 20.0F, 2.8F * (0.85F + rnd.nextFloat() * 0.3F), 0.4F, now);
            }
        }
        if (Satellites.isSatellite(f.build())) { fairingFx(level, f, now, p, dir, rnd); return; }
        if (Crew.isCrew(f.build()) || StationKit.isKit(f.build()) || Cargo.isCargo(f.build())) return;
        long left = f.endTime() - now;
        if (isRod(f) && left <= ROD_TICKS && left > 0) {
            if (CONVERTED.add(f.id())) {
                WmdSound.play(net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), p.x, p.y, p.z, 40.0F, 0.6F);
                if (LaunchSounds.on()) LaunchSounds.boom(p.x, p.y, p.z, 60.0F, 0.9F);
                else WmdSound.play(net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, p.x, p.y, p.z, 50.0F, 0.5F);
                VeilBridge.flash(p.x, p.y, p.z, 1.0F, 0.95F, 0.85F, 160.0F, 8.0F, 1.0F, 40);
                for (int i = 0; i < 140; i++) level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, p.x + rnd.nextGaussian() * 2.5, p.y + rnd.nextGaussian() * 6, p.z + rnd.nextGaussian() * 2.5, rnd.nextGaussian() * 0.8, rnd.nextGaussian() * 0.8, rnd.nextGaussian() * 0.8);
                for (int i = 0; i < 60; i++) level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, p.x, p.y, p.z, rnd.nextGaussian() * 1.2, rnd.nextGaussian() * 1.2, rnd.nextGaussian() * 1.2);
                DEBRIS.add(new Debris(p, ahead.scale(0.35).add(0.0, 0.3, 0.0), new Quaternionf(orientation(dv, f)), 0.02F, 2));
            }
            float kk = 1.0F - left / (float) ROD_TICKS;
            VeilBridge.glow(0x7E00_0000_0000_0000L ^ f.id(), p.x, p.y, p.z, 1.0F, 0.9F, 0.75F, 70.0F + 40.0F * kk, 4.5F, 0.9F, now);
            VeilBridge.bloomNear(p.x, p.y, p.z, 4000.0, 1.4F);
            if (p.distanceTo(cam) < 7000.0) {
                for (int i = 0; i < ClientConfig.count(60.0F, rnd); i++) {
                    double s = i * 3.0 + rnd.nextDouble() * 3.0;
                    Vec3 q2 = p.add(back.scale(s));
                    level.addAlwaysVisibleParticle(i < 14 ? ParticleTypes.END_ROD : WmdParticles.ROCKET_FLAME.get(), true, q2.x + rnd.nextGaussian() * 0.6, q2.y + rnd.nextGaussian() * 0.6, q2.z + rnd.nextGaussian() * 0.6, back.x * 0.1, back.y * 0.1, back.z * 0.1);
                    if (i % 4 == 0) level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true, q2.x + rnd.nextGaussian() * 2, q2.y + rnd.nextGaussian() * 2, q2.z + rnd.nextGaussian() * 2, 0, 0, 0);
                }
            }
            Vec3 target = f.seenTarget(now);
            if (LaunchSounds.on()) {
                if (left == 160) LaunchSounds.reentry(p.x, p.y, p.z, 50.0F, 0.9F);
                if (left == 100) LaunchSounds.boom(target.x, target.y + 120, target.z, 80.0F, 0.8F);
            } else {
                if (left == 100) WmdSound.play(net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, target.x, target.y + 120, target.z, 80.0F, 0.4F);
                if (left % 40 == 0) WmdSound.play(net.minecraft.sounds.SoundEvents.ELYTRA_FLYING, p.x, p.y, p.z, 40.0F, 0.35F);
            }
            if (left < 160 && left % 8 == 0) T4Fx.shakeNear(target, 1400.0, 0.5F * (1.0F - left / 160.0F) + 0.1F);
            return;
        }
        if (left < 320 && left > 0) {
            float k = 1.0F - left / 320.0F;
            VeilBridge.glow(0x6F00_0000_0000_0000L ^ f.id(), p.x, p.y, p.z, 1.0F, 0.78F, 0.5F, 36.0F + 30.0F * k, 3.0F + 3.0F * k, 0.8F, now);
            VeilBridge.bloomNear(p.x, p.y, p.z, 3000.0, 1.2F);
            if (p.distanceTo(cam) < 6000.0) {
                for (int i = 0; i < ClientConfig.count(26.0F, rnd); i++) {
                    double s = i * 2.2 + rnd.nextDouble() * 2.2;
                    Vec3 q2 = p.add(back.scale(s));
                    level.addAlwaysVisibleParticle(i < 6 ? ParticleTypes.END_ROD : WmdParticles.ROCKET_FLAME.get(), true, q2.x + rnd.nextGaussian() * 0.5, q2.y + rnd.nextGaussian() * 0.5, q2.z + rnd.nextGaussian() * 0.5, back.x * 0.1, back.y * 0.1, back.z * 0.1);
                }
            }
            Vec3 target = f.seenTarget(now);
            if (LaunchSounds.on()) {
                if (BOOMED.add(f.id())) LaunchSounds.boom(p.x, p.y, p.z, 60.0F, 1.0F);
                if (left == 280 || left == 160) LaunchSounds.reentry(p.x, p.y, p.z, 40.0F, left == 280 ? 0.95F : 1.05F);
            } else if (left == 140) WmdSound.play(net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, target.x, target.y + 80, target.z, 60.0F, 0.5F);
            if (left < 160 && left % 10 == 0) T4Fx.shakeNear(target, 900.0, 0.6F * (1.0F - left / 160.0F) + 0.1F);
        }
    }

    private static final double INCOMING_RANGE = 250.0;
    private static float incoming;

    @SubscribeEvent
    static void onFogColour(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor event) {
        if (incoming <= 0.01F) return;
        float k = Math.min(1.0F, incoming) * 0.6F;
        event.setRed(event.getRed() + (0.78F - event.getRed()) * k);
        event.setGreen(event.getGreen() * (1.0F - k * 0.75F));
        event.setBlue(event.getBlue() * (1.0F - k * 0.85F));
    }

    private static void hullTest(ClientLevel level, Flight f, long now, Vec3 cam) {
        if (!SableBridge.active() || HULL.containsKey(f.id()) || now - f.start() < Balance.SHIP_PATH_ARM_TICKS) return;
        Vec3 m0 = f.position(now), m1 = f.position(now + 1.0), step = m1.subtract(m0);
        if (m0.distanceTo(cam) > nearLimit() * 1.1 || step.lengthSqr() < 1.0E-6) return;
        Vec3 lead = step.normalize().scale(MissileParts.lengthOf(f.build()) * 0.5);
        Vec3 a = m0.add(lead), b = m1.add(lead);
        net.minecraft.world.phys.BlockHitResult r = level.clip(new net.minecraft.world.level.ClipContext(a, b, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (r.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !SableBridge.isOnShip(level, r.getBlockPos())) return;
        Vec3 w = SableBridge.toWorld(level, r.getLocation());
        if (w == null || w.distanceToSqr(r.getLocation()) < 1.0E-6) w = SableBridge.worldPos(level, r.getBlockPos());
        Vec3 ab = b.subtract(a);
        double k = Math.max(0.0, Math.min(1.0, w.subtract(a).dot(ab) / ab.lengthSqr()));
        HULL.put(f.id(), now + k);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            FLIGHTS.clear();
            DEBRIS.clear();
            SEP.clear();
            SOUNDS.clear();
            DhBridge.clear();
            VeilBridge.clear();
            return;
        }
        VeilBridge.tick(level.getGameTime());
        incoming *= 0.97F;
        ImpactFx.tick();
        ClientFxVacuum.tick();
        ClientClock.tick();
        ClientFxSplash.tick();
        ClientUnderwaterBlast.tick();
        DhBridge.tick(level, mc.gameRenderer.getMainCamera().getPosition());
        stageEvents(level, level.getGameTime());
        tickDebris(level, level.getGameTime());
        if (FLIGHTS.isEmpty()) return;
        long now = level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        RandomSource rnd = level.random;
        HULL.keySet().removeIf(id -> !FLIGHTS.containsKey(id));
        for (Flight f : FLIGHTS.values()) {
            if (now > f.endTime() + 40) { FLIGHTS.remove(f.id()); continue; }
            if (now < f.start() || now > f.endTime()) continue;
            hullTest(level, f, now, cam);
            Vec3 p = f.position(now), prev = f.position(now - 1);
            if (f.isCruise()) {
                ClientCruise.tickFx(level, f, now, p, prev, cam, rnd);
                descentSounds(f, now, p);
                LaunchSounds.flyby(f.id(), p, p.distanceTo(cam), now);
                if (!LaunchSounds.on() && f.targetKnown() && f.endTime() - now < 400 && cam.distanceTo(f.seenTarget(now)) < SCREAM_RANGE && !SOUNDS.containsKey(f.id())) {
                    IncomingSound s = new IncomingSound(f.id());
                    SOUNDS.put(f.id(), s);
                    mc.getSoundManager().play(s);
                }
                continue;
            }
            double r = Missiles.radiusUnits(f.tier()) / 16.0;
            Vec3 back = prev.subtract(p);
            double len = back.length();
            Vec3 tail = len > 1.0E-4 ? p.add(back.scale((f.build().staged() ? stageTail(f, now) : Missiles.height(f.tier()) * 0.5) / len)) : p;

            {
                int cal = f.build().calibre(), mot = f.build().divert() ? 4 : Math.max(1, Math.min(3, f.build().motor()));
                float sc = 0.8F + 0.3F * cal, flick = 0.85F + rnd.nextFloat() * 0.3F;
                float gr = mot == 1 ? 0.62F : mot == 2 ? 0.74F : mot == 3 ? 0.86F : 0.9F, bl = mot == 1 ? 0.28F : mot == 2 ? 0.4F : mot == 3 ? 0.6F : 1.0F, rd = mot == 4 ? 0.8F : 1.0F;
                VeilBridge.glow(0x5A00_0000_0000_0000L ^ f.id(), tail.x, tail.y, tail.z, rd, gr, bl, 16.0F * sc, 2.2F * sc * flick, 0.35F, now);
                VeilBridge.bloomNear(tail.x, tail.y, tail.z, 400.0 * sc, 0.9F);
                VeilBridge.haze(tail.x, tail.y, tail.z, 6.0 * sc, 250.0, 1.0F);
            }
            if (f.build().staged()) stagedFx(level, f, now, p, cam, rnd);
            else descentSounds(f, now, p);
            if (p.distanceTo(cam) < ClientConfig.trailRange()) {

                int n = Math.max(1, Math.min(ClientConfig.count(10, rnd), (int) (len / 1.2) + 1));
                for (int i = 0; i < n; i++) {
                    double k = (i + rnd.nextDouble()) / n;
                    level.addAlwaysVisibleParticle(WmdParticles.TRAIL_SMOKE.get(), true,
                            tail.x + back.x * k + rnd.nextGaussian() * r * 0.5, tail.y + back.y * k + rnd.nextGaussian() * r * 0.5,
                            tail.z + back.z * k + rnd.nextGaussian() * r * 0.5, 0, -0.01, 0);
                }
                for (int i = 0; i < 3; i++) {
                    level.addAlwaysVisibleParticle(WmdParticles.ROCKET_FLAME.get(), true, tail.x + rnd.nextGaussian() * r * 0.3,
                            tail.y + rnd.nextGaussian() * r * 0.3, tail.z + rnd.nextGaussian() * r * 0.3,
                            back.x * 0.05 + rnd.nextGaussian() * 0.03, back.y * 0.05, back.z * 0.05 + rnd.nextGaussian() * 0.03);
                }
            }

            if (f.diverts() && now >= f.swerveAt() && now < f.swerveAt() + Flight.SWERVE_TICKS + 6 && p.distanceTo(cam) < ClientConfig.trailRange() * 1.5) {
                double u = (now - f.swerveAt()) / Flight.SWERVE_TICKS;
                double sx = f.tx() - f.fx(), sz = f.tz() - f.fz(), sl = Math.hypot(sx, sz);
                if (u >= 0.0 && sl > 0.5) {
                    boolean brake = u > 0.5;
                    double w = u < 0.3 ? Math.sin(Math.PI * u / 0.3) : u > 0.7 && u < 1.0 ? Math.sin(Math.PI * (u - 0.7) / 0.3) : 0.0;
                    if (SWERVED.add(f.id())) {
                        WmdSound.play(net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST, p.x, p.y, p.z, 14.0F, 1.6F);
                        WmdSound.play(net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT, p.x, p.y, p.z, 4.0F, 2.0F);
                    }
                    if (u > 0.7 && SWERVED2.add(f.id())) WmdSound.play(net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST, p.x, p.y, p.z, 9.0F, 1.8F);
                    if (w > 0.02) {
                        double side = brake ? -1.0 : 1.0;
                        double jx = sx / sl * side, jz = sz / sl * side;
                        Vec3 vel = tilted(f, now, f.position(now + 1.0).subtract(p));
                        Vec3 axis = vel.lengthSqr() > 1.0E-6 ? vel.normalize() : new Vec3(0, 1, 0);
                        double mlen = Missiles.height(f.build().calibre());
                        Vec3 port = p.subtract(axis.scale(mlen * 0.5 - 1.8 * BayCalibre.len(f.build().calibre())));

                        VeilBridge.glow(0x5B00_0000_0000_0000L ^ f.id(), port.x, port.y, port.z, 0.85F, 0.92F, 1.0F, 12.0F, (float) (2.4 * w), 0.5F, now);
                        int n = ClientConfig.count((float) (22.0 * w), rnd);
                        for (int i = 0; i < n; i++) {
                            double spread = 0.18;
                            double vx = jx * (1.0 + rnd.nextDouble() * 0.7) + vel.x * 0.55 + rnd.nextGaussian() * spread;
                            double vy = vel.y * 0.55 + rnd.nextGaussian() * spread;
                            double vz = jz * (1.0 + rnd.nextDouble() * 0.7) + vel.z * 0.55 + rnd.nextGaussian() * spread;
                            double ox = jx * r * 1.1 + rnd.nextGaussian() * 0.15, oy = rnd.nextGaussian() * 0.6, oz = jz * r * 1.1 + rnd.nextGaussian() * 0.15;
                            level.addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, true, port.x + ox, port.y + oy, port.z + oz, vx, vy, vz);
                            if (i % 4 == 0) level.addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, true, port.x + ox, port.y + oy, port.z + oz, vx * 1.2, vy, vz * 1.2);
                        }
                    }
                }
            }

            if (f.tier() == 1 && f.endTime() - now < 400) {
                double dist = cam.distanceTo(f.seenTarget(now));
                if (dist < INCOMING_RANGE) {
                    float k = (1.0F - (f.endTime() - now) / 400.0F) * (float) (1.0 - dist / INCOMING_RANGE);
                    incoming = Math.max(incoming, k);
                }
            }

            if (!LaunchSounds.on() && f.endTime() - now < 400 && cam.distanceTo(f.seenTarget(now)) < SCREAM_RANGE && !SOUNDS.containsKey(f.id())) {
                IncomingSound s = new IncomingSound(f.id());
                SOUNDS.put(f.id(), s);
                mc.getSoundManager().play(s);
            }
        }
        SOUNDS.entrySet().removeIf(e -> !FLIGHTS.containsKey(e.getKey()) || e.getValue().isStopped());
        SWERVED.removeIf(id -> !FLIGHTS.containsKey(id));
        SWERVED2.removeIf(id -> !FLIGHTS.containsKey(id));
        BOOMED.removeIf(id -> !FLIGHTS.containsKey(id));
        TERMINAL.keySet().removeIf(id -> !FLIGHTS.containsKey(id));
    }
}
