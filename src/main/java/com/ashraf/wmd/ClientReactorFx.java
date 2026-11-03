package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientReactorFx {

    private ClientReactorFx() {}

    private static final double RANGE = 96.0, PART_RANGE = 64.0;
    private static final int MAX_BOLTS = 10, MAX_PTS = 12, PART_CAP = 12;
    private static final int ALARM = 1, STEAM = 2, IMMINENT = 3;

    private static final double CORE_Y = 5.0, CORE_Z = 4.0, HEART_R = 1.04;

    private static final double[][] RINGS = { { 27.0 / 16, 0.0, 0.0 }, { 35.0 / 16, 28.0, 20.0 }, { 42.0 / 16, -24.0, -50.0 } };
    private static final double[][] RIBS = { { -4, 0 }, { 4, 0 }, { 0, 0 }, { -4, 8 }, { 4, 8 }, { 0, 8 }, { -4, 4 }, { 4, 4 } };
    private static final double RIB_Y0 = 3.2, RIB_Y1 = 8.5, RIB_INSET = 0.45;

    private static final double[][] BEACONS = { { -4, 10.6, 0 }, { 4, 10.6, 0 }, { 4, 10.6, 8 }, { -4, 10.6, 8 } };
    private static final double[][] VENTS = { { 0, 10.35, 0 }, { 0, 10.35, 8 }, { -4, 10.35, 4 }, { 4, 10.35, 4 } };
    private static final double ROD_TIP_Y = 11.1, ROD_TRAVEL = 2.0, CROWN_TOP = 10.25;

    private static final float[][] OUTER = { { 0.45F, 0.70F, 1.00F }, { 0.72F, 0.50F, 1.00F }, { 1.00F, 0.45F, 0.62F }, { 1.00F, 0.25F, 0.18F } };
    private static final float[][] INNER = { { 0.88F, 0.95F, 1.00F }, { 0.95F, 0.90F, 1.00F }, { 1.00F, 0.88F, 0.92F }, { 1.00F, 0.85F, 0.80F } };
    private static final float[][] CORE_COL = { { 0.25F, 0.55F, 1.00F }, { 0.60F, 0.30F, 1.00F }, { 0.90F, 0.25F, 0.55F }, { 1.00F, 0.15F, 0.10F } };
    private static final double[] STAGE_ARCS = { 0.0, 0.10, 0.22, 0.45 };

    private static final class Bolt {
        boolean live, fork;
        int age, life, n;
        double ax, ay, az, bx, by, bz, amp;
        float w, r, g, b;
        long seed;
        final double[] p = new double[MAX_PTS * 3];
    }

    private static final class R {
        final BlockPos ctrl;
        Level level;
        Direction facing = Direction.NORTH;
        long lastTick = Long.MIN_VALUE;
        boolean near, nearParts, running;
        double level01, heat01, cx, cy, cz;
        int stage, soundCd;
        float ring, ownRing, arcFlash;
        final Bolt[] bolts = new Bolt[MAX_BOLTS];
        R(BlockPos ctrl) { this.ctrl = ctrl; for (int i = 0; i < MAX_BOLTS; i++) bolts[i] = new Bolt(); }
    }

    private static final Map<Long, R> STATE = new HashMap<>();
    private static long lastSound = Long.MIN_VALUE, frame;
    private static int budget;

    private static double wx, wy, wz, tx, ty, tz;

    public static void tick(Level level, BlockPos ctrl, Direction facing, double level01, int stage, boolean running, double heat01, long gameTime) {
        tick(level, ctrl, facing, level01, stage, running, heat01, gameTime, Float.NaN);
    }

    public static void tick(Level level, BlockPos ctrl, Direction facing, double level01, int stage, boolean running, double heat01, long gameTime, float ringAngle) {
        if (level == null || !level.isClientSide) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) return;
        R r = STATE.get(ctrl.asLong());
        if (r == null || r.level != level) {
            r = new R(ctrl.immutable());
            r.level = level;
            STATE.put(ctrl.asLong(), r);
        }
        if (r.lastTick == gameTime) return;
        r.lastTick = gameTime;
        r.facing = facing;
        r.level01 = Math.max(0.0, Math.min(1.0, level01));
        r.stage = Math.max(0, Math.min(IMMINENT, stage));
        r.running = running;
        r.heat01 = Math.max(0.0, Math.min(1.0, heat01));
        r.ownRing = (r.ownRing + (running ? 0.02F + 0.24F * (float) r.level01 : 0.0F)) % (float) (Math.PI * 64);
        r.ring = Float.isNaN(ringAngle) ? r.ownRing : ringAngle;
        world(r, 0.0, CORE_Y, CORE_Z);
        r.cx = wx; r.cy = wy; r.cz = wz;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dx = cam.x - r.cx, dy = cam.y - r.cy, dz = cam.z - r.cz, d2 = dx * dx + dy * dy + dz * dz;
        r.near = d2 < RANGE * RANGE;
        r.nearParts = d2 < PART_RANGE * PART_RANGE;
        if (r.soundCd > 0) r.soundCd--;
        if (!r.near) { for (Bolt b : r.bolts) b.live = false; return; }
        RandomSource rnd = level.random;
        budget = PART_CAP;

        for (Bolt b : r.bolts) {
            if (!b.live) continue;
            if (++b.age >= b.life) { b.live = false; continue; }
            jag(b, rnd);
        }
        lights(r, gameTime, rnd);
        boolean active = running && r.level01 > 0.0 || r.stage > 0;
        if (!active) { r.arcFlash *= 0.6F; return; }
        arcs(level, r, rnd);
        if (r.nearParts) particles(level, r, gameTime, rnd);
    }

    public static void remove(BlockPos ctrl) {
        STATE.remove(ctrl.asLong());
    }

    private static void world(R r, double sx, double sy, double sz) {
        double dx, dz;
        switch (r.facing) {
            case EAST -> { dx = -sz; dz = sx; }
            case SOUTH -> { dx = -sx; dz = -sz; }
            case WEST -> { dx = sz; dz = -sx; }
            default -> { dx = sx; dz = sz; }
        }
        wx = r.ctrl.getX() + 0.5 + dx;
        wy = r.ctrl.getY() - 1 + sy;
        wz = r.ctrl.getZ() + 0.5 + dz;
    }

    private static void ringPoint(int k, double a, double spin) {
        double rad = RINGS[k][0], ax = Math.toRadians(RINGS[k][1]), ay = Math.toRadians(RINGS[k][2]);
        double x = rad * Math.cos(a), y = 0.0, z = rad * Math.sin(a);
        double y1 = y * Math.cos(ax) - z * Math.sin(ax), z1 = y * Math.sin(ax) + z * Math.cos(ax);
        double x2 = x * Math.cos(ay) + z1 * Math.sin(ay), z2 = -x * Math.sin(ay) + z1 * Math.cos(ay);
        double cs = Math.cos(spin), sn = Math.sin(spin);
        tx = x2 * cs + z2 * sn;
        ty = CORE_Y + y1;
        tz = CORE_Z - x2 * sn + z2 * cs;
    }

    private static void lights(R r, long t, RandomSource rnd) {
        long k = r.ctrl.asLong() ^ 0x4E57_0000_0000_0000L;
        float act = r.running ? (float) r.level01 : 0.0F;
        int st = r.stage;
        float[] c = CORE_COL[st];
        if (act <= 0.0F && st == 0) {
            VeilBridge.machineGlow(k, r.cx, r.cy, r.cz, 0.25F, 0.5F, 1.0F, 2.5F, 0.22F, 0.2F, t);
            r.arcFlash = 0.0F;
            return;
        }
        float pulse = st >= IMMINENT ? 0.55F * (float) Math.sin(t * 0.5) : 0.1F * (float) Math.sin(t * 0.11);
        float f = Math.min(1.0F, r.arcFlash), h = (float) r.heat01 * 0.25F;
        float cr = c[0] + (1.0F - c[0]) * f * 0.5F + h * 0.4F, cg = c[1] + (1.0F - c[1]) * f * 0.5F, cb = c[2] + (1.0F - c[2]) * f * 0.5F - h * 0.3F;
        VeilBridge.machineGlow(k, r.cx, r.cy, r.cz, Math.min(1.0F, cr), cg, Math.max(0.0F, cb), 4.0F + 3.0F * act + 0.8F * st,
                Math.max(0.2F, 0.7F + 0.9F * act + 0.15F * st + pulse + 1.2F * f), 0.6F, t);
        r.arcFlash *= 0.6F;

        int nRing = st >= STEAM ? 2 : 3;
        for (int i = 0; i < nRing; i++) {
            ringPoint(0, i * Math.PI * 2 / nRing, r.ring);
            world(r, tx, ty, tz);
            float mix = st > 0 ? 0.5F : 0.0F;
            VeilBridge.machineGlow(k + 1 + i, wx, wy, wz, 0.3F + (c[0] - 0.3F) * mix, 0.85F + (c[1] - 0.85F) * mix, 1.0F + (c[2] - 1.0F) * mix,
                    1.8F + 1.2F * act, 0.45F + 0.6F * act, t);
        }

        if (act > 0.0F) {
            world(r, 0.0, ROD_TIP_Y + ROD_TRAVEL * act - 0.4, CORE_Z);
            VeilBridge.machineGlow(k + 4, wx, wy, wz, st >= IMMINENT ? 1.0F : 0.35F, st >= IMMINENT ? 0.3F : 0.75F, st >= IMMINENT ? 0.25F : 1.0F,
                    3.0F + 2.5F * act, 0.35F + 0.7F * act, 0.3F, t);
        }
        if (st >= ALARM) {

            int period = st >= IMMINENT ? 3 : st >= STEAM ? 4 : 6;
            double[] b = BEACONS[(int) ((t / period) % 4)];
            world(r, b[0], b[1], b[2]);
            float blink = 1.0F - (t % period) / (float) period * 0.5F;
            VeilBridge.machineGlow(k + 5, wx, wy, wz, 1.0F, st == ALARM ? 0.3F : 0.12F, 0.08F, 5.0F, 1.5F * blink, t);
        }
        if (st >= STEAM) {

            if (t % (st >= IMMINENT ? 7 : 11) == 0) {
                double[] v = VENTS[rnd.nextInt(VENTS.length)];
                world(r, v[0], v[1] + 0.5, v[2]);
                if (!ClientHemp.darkAt(wx, wz)) VeilBridge.flash(wx, wy, wz, 0.9F, 0.95F, 1.0F, 4.5F, 1.3F, 0.2F, 6);
            }
            world(r, 0.0, CROWN_TOP + 1.0, CORE_Z);
            VeilBridge.haze(wx, wy, wz, 4.5, 48.0, st >= IMMINENT ? 0.45F : 0.28F);
        }
    }

    private static void arcs(Level level, R r, RandomSource rnd) {
        double act = r.running ? r.level01 : 0.0;
        double p = act > 0.0 ? 0.05 + 0.25 * Math.max(0.0, (act - 0.25) / 0.75) : 0.0;
        p += STAGE_ARCS[r.stage];
        int n = (int) p + (rnd.nextDouble() < p - (int) p ? 1 : 0);
        for (int i = 0; i < Math.min(n, 2); i++) spawn(level, r, rnd);
    }

    private static Bolt free(R r) {
        for (Bolt b : r.bolts) if (!b.live) return b;
        return null;
    }

    private static void spawn(Level level, R r, RandomSource rnd) {
        Bolt b = free(r);
        if (b == null) return;
        int st = r.stage;
        double roll = rnd.nextDouble(), sx, sy, sz, ex, ey, ez;
        boolean crown = st >= ALARM && roll < 0.2;
        if (crown) {
            double rise = r.running ? ROD_TRAVEL * r.level01 : 0.0;
            sx = rnd.nextInt(3) - 1; sy = ROD_TIP_Y + rise; sz = CORE_Z + rnd.nextInt(3) - 1;
            double[] bc = BEACONS[rnd.nextInt(4)];
            ex = bc[0]; ey = bc[1]; ez = bc[2];
        } else {
            if (roll < 0.6) {
                double[] rb = RIBS[rnd.nextInt(RIBS.length)];
                double dx = -rb[0], dz = CORE_Z - rb[1], l = Math.sqrt(dx * dx + dz * dz);
                sx = rb[0] + dx / l * RIB_INSET; sz = rb[1] + dz / l * RIB_INSET;
                sy = RIB_Y0 + rnd.nextDouble() * (RIB_Y1 - RIB_Y0);
            } else {
                int k = rnd.nextDouble() < 0.5 ? 0 : 1 + rnd.nextInt(2);
                ringPoint(k, rnd.nextDouble() * Math.PI * 2, r.ring);
                sx = tx; sy = ty; sz = tz;
            }

            double vx = sx + (rnd.nextDouble() - 0.5) * 0.9, vy = sy - CORE_Y + (rnd.nextDouble() - 0.5) * 0.9, vz = sz - CORE_Z + (rnd.nextDouble() - 0.5) * 0.9;
            double vl = Math.max(1e-4, Math.sqrt(vx * vx + vy * vy + vz * vz));
            ex = vx / vl * HEART_R; ey = CORE_Y + vy / vl * HEART_R; ez = CORE_Z + vz / vl * HEART_R;
        }
        world(r, sx, sy, sz);
        b.ax = wx; b.ay = wy; b.az = wz;
        world(r, ex, ey, ez);
        b.bx = wx; b.by = wy; b.bz = wz;
        double len = Math.sqrt((b.bx - b.ax) * (b.bx - b.ax) + (b.by - b.ay) * (b.by - b.ay) + (b.bz - b.az) * (b.bz - b.az));
        b.live = true; b.fork = false; b.age = 0;
        b.life = st >= STEAM ? 2 + rnd.nextInt(2) : 2 + rnd.nextInt(3);
        b.w = (float) ((crown ? 1.25 : 1.0) * (1.0 + 0.25 * st));
        b.amp = Math.min(0.75, len * 0.13) * (1.0 + 0.3 * st);
        b.r = OUTER[st][0]; b.g = OUTER[st][1]; b.b = OUTER[st][2];
        b.seed = rnd.nextLong();
        jag(b, rnd);

        if (rnd.nextDouble() < 0.35 + 0.1 * st) {
            Bolt fk = free(r);
            if (fk != null) {
                int m = b.n / 2;
                fk.ax = b.p[m * 3]; fk.ay = b.p[m * 3 + 1]; fk.az = b.p[m * 3 + 2];
                double fl = len * (0.2 + rnd.nextDouble() * 0.2);
                fk.bx = fk.ax + (rnd.nextDouble() - 0.5) * 2 * fl; fk.by = fk.ay + (rnd.nextDouble() - 0.5) * 2 * fl; fk.bz = fk.az + (rnd.nextDouble() - 0.5) * 2 * fl;
                fk.live = true; fk.fork = true; fk.age = 0; fk.life = b.life;
                fk.w = b.w * 0.55F; fk.amp = b.amp * 0.5;
                fk.r = b.r; fk.g = b.g; fk.b = b.b; fk.seed = rnd.nextLong();
                jag(fk, rnd);
            }
        }
        r.arcFlash = Math.min(1.5F, r.arcFlash + 1.0F);
        if (r.nearParts) { sparks(level, b.ax, b.ay, b.az, rnd); sparks(level, b.bx, b.by, b.bz, rnd); }

        long now = level.getGameTime();
        if (r.soundCd <= 0 && now - lastSound >= 6 && rnd.nextDouble() < 0.6) {
            r.soundCd = st >= STEAM ? 16 : 30;
            lastSound = now;
            level.playLocalSound((b.ax + b.bx) * 0.5, (b.ay + b.by) * 0.5, (b.az + b.bz) * 0.5, WmdSounds.DECOY_CRACKLE.get(), SoundSource.BLOCKS,
                    0.22F + 0.08F * st, 1.5F + rnd.nextFloat() * 0.4F, false);
        }
    }

    private static void jag(Bolt b, RandomSource rnd) {
        double dx = b.bx - b.ax, dy = b.by - b.ay, dz = b.bz - b.az, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = Math.max(4, Math.min(MAX_PTS - 1, (int) (len * 2.2) + 3));
        b.n = n + 1;
        if (len < 1e-4) { b.n = 0; return; }
        dx /= len; dy /= len; dz /= len;
        double hx = 0, hy = 1, hz = 0;
        if (Math.abs(dy) > 0.9) { hx = 1; hy = 0; }
        double ux = dy * hz - dz * hy, uy = dz * hx - dx * hz, uz = dx * hy - dy * hx, ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        double vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n, env = Math.sin(Math.PI * t), o1 = 0, o2 = 0;
            if (i > 0 && i < n) {
                t += (rnd.nextDouble() - 0.5) * 0.4 / n;
                o1 = (rnd.nextDouble() * 2 - 1) * b.amp * env;
                o2 = (rnd.nextDouble() * 2 - 1) * b.amp * env;
            }
            b.p[i * 3] = b.ax + dx * len * t + ux * o1 + vx * o2;
            b.p[i * 3 + 1] = b.ay + dy * len * t + uy * o1 + vy * o2;
            b.p[i * 3 + 2] = b.az + dz * len * t + uz * o1 + vz * o2;
        }
    }

    private static void add(Level level, SimpleParticleType type, double x, double y, double z, double vx, double vy, double vz) {
        if (budget <= 0) return;
        budget--;
        level.addParticle(type, x, y, z, vx, vy, vz);
    }

    private static void sparks(Level level, double x, double y, double z, RandomSource rnd) {
        int n = Math.min(3, ClientConfig.count(2.0F, rnd));
        for (int i = 0; i < n; i++)
            add(level, ParticleTypes.ELECTRIC_SPARK, x, y, z, (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.3) * 0.3, (rnd.nextDouble() - 0.5) * 0.3);
    }

    private static void particles(Level level, R r, long t, RandomSource rnd) {
        int st = r.stage;
        double act = r.running ? r.level01 : 0.0;

        int n = Math.min(2, ClientConfig.count((float) ((act > 0 ? 0.15 + 0.6 * act : 0.0) + 0.15 * st), rnd));
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 1.15 + rnd.nextDouble() * 1.25;
            world(r, Math.cos(a) * d, 3.0 + rnd.nextDouble() * 4.0, CORE_Z + Math.sin(a) * d);
            add(level, ParticleTypes.END_ROD, wx, wy, wz, Math.cos(a) * 0.004, 0.015 + rnd.nextDouble() * 0.025, Math.sin(a) * 0.004);
        }
        if (st >= ALARM && rnd.nextDouble() < 0.15 * st) {
            double ux = rnd.nextDouble() - 0.5, uy = rnd.nextDouble() - 0.5, uz = rnd.nextDouble() - 0.5, ul = Math.max(1e-4, Math.sqrt(ux * ux + uy * uy + uz * uz));
            world(r, ux / ul * HEART_R, CORE_Y + uy / ul * HEART_R, CORE_Z + uz / ul * HEART_R);
            add(level, ParticleTypes.ELECTRIC_SPARK, wx, wy, wz, ux / ul * 0.25, uy / ul * 0.25, uz / ul * 0.25);
        }
        if (st < STEAM) return;

        int jets = Math.min(4, ClientConfig.count(st >= IMMINENT ? 3.0F : 1.5F, rnd));
        for (int i = 0; i < jets; i++) {
            double[] v = VENTS[rnd.nextInt(VENTS.length)];
            world(r, v[0], v[1] + 0.2, v[2]);
            add(level, ParticleTypes.CLOUD, wx + (rnd.nextDouble() - 0.5) * 0.4, wy, wz + (rnd.nextDouble() - 0.5) * 0.4,
                    (rnd.nextDouble() - 0.5) * 0.08, 0.4 + rnd.nextDouble() * 0.3, (rnd.nextDouble() - 0.5) * 0.08);
        }
        if (t % (st >= IMMINENT ? 3 : 6) == 0) {
            double[] v = VENTS[rnd.nextInt(VENTS.length)];
            world(r, v[0], v[1] + 0.3, v[2]);
            add(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, wx, wy, wz, (rnd.nextDouble() - 0.5) * 0.02, 0.12 + rnd.nextDouble() * 0.05, (rnd.nextDouble() - 0.5) * 0.02);
        }
        if (st >= IMMINENT && t % 4 == 0) {
            world(r, (rnd.nextDouble() - 0.5) * 8.0, CROWN_TOP + 0.2, CORE_Z + (rnd.nextDouble() - 0.5) * 8.0);
            add(level, ParticleTypes.LARGE_SMOKE, wx, wy, wz, 0, 0.12, 0);
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || STATE.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { STATE.clear(); return; }
        long now = level.getGameTime();
        boolean anyNear = false;
        for (Iterator<R> it = STATE.values().iterator(); it.hasNext(); ) {
            R r = it.next();
            if (r.level != level || now - r.lastTick > 40 || now < r.lastTick - 100) { it.remove(); continue; }
            anyNear |= r.near;
        }
        if (!anyNear) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ClientBlastGeo.beginFrame();
        frame++;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (R r : STATE.values()) if (r.near) any |= drawReactor(bb, cam, left, up, r, now + pt, pt);
        ClientBlastGeo.draw(event, bb, any, true);
    }

    private static float hash01(long seed) {
        long h = (seed ^ frame * 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 31;
        return (h >>> 40 & 0xFFFF) / 65535.0F;
    }

    private static boolean drawReactor(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, R r, double t, float pt) {
        int st = r.stage;
        float act = r.running ? (float) r.level01 : 0.0F;
        float[] c = CORE_COL[st];

        float pulse = st >= IMMINENT ? 0.75F + 0.25F * (float) Math.sin(t * 0.5) : 0.9F + 0.1F * (float) Math.sin(t * 0.11);
        float ha = act <= 0.0F && st == 0 ? 0.06F : ((0.16F + 0.22F * act + 0.06F * st) * pulse + 0.25F * Math.min(1.0F, r.arcFlash));
        ClientBlastGeo.disc(bb, cam, left, up, r.cx, r.cy, r.cz, 1.9 + 0.6 * act, c[0] * 0.6F + 0.4F, c[1] * 0.6F + 0.4F, c[2] * 0.6F + 0.4F, ha, 16);

        if (act > 0.0F) {
            world(r, 0.0, CROWN_TOP, CORE_Z);
            double bx = wx, by = wy, bz = wz, top = by + 1.2 + 2.8 * act;
            float cr = st >= IMMINENT ? 1.0F : 0.35F, cg = st >= IMMINENT ? 0.35F : 0.75F, cb = st >= IMMINENT ? 0.3F : 1.0F;
            ClientBlastGeo.streak(bb, cam, bx, by, bz, bx, top, bz, 0.45, cr, cg, cb, 0.16F * act, 0.0F);
            ClientBlastGeo.streak(bb, cam, bx, by, bz, bx, top, bz, 0.15, cr, cg, cb, 0.30F * act, 0.0F);
        }

        int si = Math.min(st, INNER.length - 1);
        for (Bolt b : r.bolts) {
            if (!b.live || b.n < 2) continue;
            float fade = 1.0F - (b.age + pt) / (b.life + 1.0F);
            float a = fade * (0.6F + 0.4F * hash01(b.seed));
            if (a <= 0.02F) continue;
            for (int i = 0; i < b.n - 1; i++) {
                int j = i * 3, k = j + 3;
                ClientBlastGeo.streak(bb, cam, b.p[j], b.p[j + 1], b.p[j + 2], b.p[k], b.p[k + 1], b.p[k + 2], 0.09 * b.w, b.r, b.g, b.b, 0.35F * a, 0.35F * a);
                ClientBlastGeo.streak(bb, cam, b.p[j], b.p[j + 1], b.p[j + 2], b.p[k], b.p[k + 1], b.p[k + 2], 0.025 * b.w,
                        INNER[si][0], INNER[si][1], INNER[si][2], 0.95F * a, 0.95F * a);
            }
            if (!b.fork) {
                ClientBlastGeo.disc(bb, cam, left, up, b.ax, b.ay, b.az, 0.3 * b.w, b.r, b.g, b.b, 0.5F * a, 8);
                ClientBlastGeo.disc(bb, cam, left, up, b.bx, b.by, b.bz, 0.4 * b.w, INNER[si][0], INNER[si][1], INNER[si][2], 0.6F * a, 8);
            }
        }
        return true;
    }
}
