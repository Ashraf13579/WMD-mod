package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientWarden {

    private ClientWarden() {}

    private record Fx(WardenFxPayload p, long start) {}

    private static final class Drone { float px, py, pz, cx, cy, cz; long seen; int warden; }

    private static final List<Fx> LIVE = Statics.client(new ArrayList<>());
    private static final Map<Integer, Drone> DRONES = Statics.client(new HashMap<>());

    private static final Map<Integer, Integer> STEPS = Statics.client(new HashMap<>());
    private static float shake;
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.1F), 1.6F);
    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.1F), 0.6F);
    private static final float STEP = 3.0F;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) { LIVE.clear(); DRONES.clear(); STEPS.clear(); shake = 0.0F; WardenFxPayload.PENDING.clear(); WardenDronesPayload.PENDING.clear(); return; }
        long now = level.getGameTime();
        shake *= 0.85F;
        for (WardenFxPayload p; (p = WardenFxPayload.PENDING.poll()) != null; ) {
            LIVE.add(new Fx(p, now));
            onArrive(level, mc.player, p);
            if (p.kind() == WardenFxPayload.RAIL || p.kind() == WardenFxPayload.EYE || p.kind() == WardenFxPayload.RING || p.kind() == WardenFxPayload.SHELL)
                ClientWardenBeams.add(p, now);
        }
        for (WardenDronesPayload p; (p = WardenDronesPayload.PENDING.poll()) != null; ) drones(p, now);
        for (Iterator<Fx> it = LIVE.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            long age = now - f.start;
            if (age > f.p.dur()) { it.remove(); continue; }
            if (f.p.kind() == WardenFxPayload.SHELL) shell(level, f.p, age);
        }
        DRONES.values().removeIf(d -> now - d.seen > 10);
        for (WardenEntity w : level.getEntitiesOfClass(WardenEntity.class, mc.player.getBoundingBox().inflate(160.0))) { atmosphere(level, mc.player, w, now); motion(level, w, now); }
    }

    private static void onArrive(ClientLevel level, Player me, WardenFxPayload p) {
        RandomSource r = level.random;
        double d = me.position().distanceTo(new Vec3(p.x(), p.y(), p.z()));
        if (p.kind() == WardenFxPayload.SLAM) {
            for (int i = 0; i < 90; i++) {
                double a = r.nextDouble() * Math.PI * 2.0, sp = 0.35 + r.nextDouble() * 0.45;
                level.addParticle(i % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.POOF, p.x(), p.y() + 0.3, p.z(), Math.cos(a) * sp, 0.02 + r.nextDouble() * 0.05, Math.sin(a) * sp);
            }
            ceilingDust(level, me, 40, 24.0);
            shake = Math.max(shake, (float) Math.max(0.0, 1.0 - d / 64.0) * 1.6F + 0.1F);
        } else if (p.kind() == WardenFxPayload.FLASH) {
            VeilBridge.flash(p.x(), p.y(), p.z(), 1.0F, 0.55F, 0.25F, 40.0F, 3.0F, 0.4F, 24);
            for (int i = 0; i < 40; i++) {
                level.addParticle(ParticleTypes.FLAME, p.x(), p.y(), p.z(), (r.nextDouble() - 0.5) * 0.8, r.nextDouble() * 0.5, (r.nextDouble() - 0.5) * 0.8);
                level.addParticle(ParticleTypes.LARGE_SMOKE, p.x(), p.y(), p.z(), (r.nextDouble() - 0.5) * 0.3, r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.3);
            }
            shake = Math.max(shake, (float) Math.max(0.0, 1.0 - d / 96.0) * 1.3F + 0.1F);
        } else if (p.kind() == WardenFxPayload.RAIL) {
            Vec3 a = new Vec3(p.x(), p.y(), p.z()), b = new Vec3(p.tx(), p.ty(), p.tz());
            for (int i = 0; i < 16; i++) level.addParticle(ParticleTypes.LARGE_SMOKE, b.x, b.y, b.z, (r.nextDouble() - 0.5) * 0.3, r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.3);
            VeilBridge.flash(a.x, a.y, a.z, 0.6F, 0.85F, 1.0F, 30.0F, 4.0F, 0.4F, 10);
            VeilBridge.flash(b.x, b.y, b.z, 0.6F, 0.85F, 1.0F, 26.0F, 4.0F, 0.4F, 14);
            shake = Math.max(shake, (float) Math.max(0.0, 1.0 - d / 96.0) * 1.2F + 0.15F);
        } else if (p.kind() == WardenFxPayload.EYE) {
            Vec3 a = new Vec3(p.x(), p.y(), p.z()), b = new Vec3(p.tx(), p.ty(), p.tz());
            for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.SMOKE, b.x, b.y, b.z, (r.nextDouble() - 0.5) * 0.1, 0.05, (r.nextDouble() - 0.5) * 0.1);
            VeilBridge.flash(a.x, a.y, a.z, 1.0F, 0.2F, 0.1F, 16.0F, 4.0F, 0.3F, 8);
            VeilBridge.flash(b.x, b.y, b.z, 1.0F, 0.35F, 0.1F, 12.0F, 4.0F, 0.3F, 12);
            shake = Math.max(shake, (float) Math.max(0.0, 1.0 - d / 64.0) * 0.6F + 0.05F);
        } else if (p.kind() == WardenFxPayload.EMP) {
            ClientFxEmp.start(p.x(), p.y(), p.z(), 0, p.tx());
        } else if (p.kind() == WardenFxPayload.PURGE) {
            VeilBridge.flash(p.x(), p.y(), p.z(), 1.0F, 0.95F, 0.85F, 90.0F, 8.0F, 0.5F, 50);
            for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x() + (r.nextDouble() - 0.5) * 20.0, p.y() + (r.nextDouble() - 0.5) * 8.0, p.z() + (r.nextDouble() - 0.5) * 20.0, 0.0, 0.0, 0.0);
            ceilingDust(level, me, 80, 30.0);
            shake = Math.max(shake, (float) Math.max(0.0, 1.0 - d / 128.0) * 2.5F + 0.2F);
        }
    }

    private static void motion(ClientLevel level, WardenEntity w, long now) {
        RandomSource r = level.random;
        Entity tg = w.aimTarget();
        Vec3 tgEye = tg == null ? null : tg.getEyePosition();
        int pod = w.pod(), prev = w.clientPodPrev;
        w.clientPodPrev = pod;
        for (int k = 0; k < Balance.WARDEN_ROCKETS; k++) {
            int f = WardenAttacks.podFire(k);
            if (prev < f && pod >= f) {
                Vec3 m = w.local(WardenAttacks.podRight(k) ? 2.6 : -2.6, 13.0, 0.6);
                for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.FLAME, m.x, m.y, m.z, (r.nextDouble() - 0.5) * 0.2, r.nextDouble() * 0.1, (r.nextDouble() - 0.5) * 0.2);
                for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, (r.nextDouble() - 0.5) * 0.1, 0.05 + r.nextDouble() * 0.05, (r.nextDouble() - 0.5) * 0.1);
                VeilBridge.flash(m.x, m.y, m.z, 1.0F, 0.6F, 0.25F, 10.0F, 3.0F, 0.2F, 5);
            }
        }
        if (w.clientMortarKick == 0) {
            Vec3 m = w.local(0.0, 13.2, -2.8);
            for (int i = 0; i < 14; i++) level.addParticle(i % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, (r.nextDouble() - 0.5) * 0.15, 0.15 + r.nextDouble() * 0.15, (r.nextDouble() - 0.5) * 0.15);
        }
        int eye = w.eye();
        if (eye > 0) {
            Vec3 e = WardenOverdrive.eyeAt(w);
            float k = Math.min(1.0F, eye / (float) Balance.WARDEN_EYE_CHARGE);
            VeilBridge.glow(EYE_KEY + w.getId(), e.x, e.y, e.z, 1.0F, 0.15F, 0.1F, 3.0F + 9.0F * k, 1.0F + 4.0F * k, 0.4F, now);
            for (int i = 0; i < 1 + (int) (4 * k); i++) {
                Vec3 o = e.add((r.nextDouble() - 0.5) * 4.0, (r.nextDouble() - 0.5) * 4.0, (r.nextDouble() - 0.5) * 4.0);
                Vec3 v = e.subtract(o).scale(0.15);
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, o.x, o.y, o.z, v.x, v.y, v.z);
            }
        }
        int rail = w.rail();
        if (rail > 0) {
            float k = Math.min(1.0F, rail / (float) Balance.WARDEN_RAIL_CHARGE);
            for (int i = 0; i < 1 + (int) (3 * k); i++) {
                double zr = 16.5 + 16.0 * r.nextInt(6), z = -zr / 16.0, lift = 1.0 + (118.0 - zr) / 16.0 * 0.17;
                Vec3 q = w.local((r.nextDouble() - 0.5) * 1.2, 12.75 + lift + (r.nextDouble() - 0.5) * 1.2, z);
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.3);
            }
            Vec3 muzzle = w.railMuzzle();
            VeilBridge.glow(RAIL_KEY + w.getId(), muzzle.x, muzzle.y, muzzle.z, 0.5F, 0.8F, 1.0F, 4.0F + 10.0F * k, 1.0F + 4.0F * k, 0.4F, now);
        }
    }

    private static final long EYE_KEY = 0x5741_5244_0001_0000L, RAIL_KEY = 0x5741_5244_0002_0000L;

    private static void shell(ClientLevel level, WardenFxPayload p, long age) {
        float u = Mth.clamp(age / (float) Math.max(1, p.dur()), 0.0F, 1.0F);
        double h = 4.0 * Balance.WARDEN_MORTAR_APEX * u * (1.0F - u);
        double x = Mth.lerp(u, p.x(), p.tx()), y = Mth.lerp(u, p.y(), p.ty()) + h, z = Mth.lerp(u, p.z(), p.tz());
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.02, 0.0);
        if (age == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, p.x(), p.y(), p.z(), 0.0, 0.08, 0.0);
    }

    private static void atmosphere(ClientLevel level, Player me, WardenEntity w, long now) {
        RandomSource r = level.random;
        int mode = w.mode(), stage = w.stage(), t = w.clientModeTicks;
        if (mode == WardenEntity.DORMANT) return;
        long key = 0x57A0_0000_0000L ^ ((long) w.getId() << 4);
        double d = me.distanceTo(w);
        boolean active = mode == WardenEntity.ACTIVE;

        Vec3 eye = w.local(0.0, 10.6, 6.3);
        boolean angry = stage >= 2 || mode == WardenEntity.DYING;
        VeilBridge.glow(key | 1, eye.x, eye.y, eye.z, angry ? 1.0F : 0.35F, angry ? 0.15F : 0.7F, angry ? 0.1F : 1.0F, 10.0F, 1.1F, now);
        if (mode != WardenEntity.DYING) {
            float pulse = 0.5F + 0.5F * Mth.sin((now % 1000) * 0.35F);
            Vec3 bea = w.local(Mth.cos(now * 0.3F) * 1.5, 13.6, -2.0 + Mth.sin(now * 0.3F) * 1.5);
            VeilBridge.glow(key | 2, bea.x, bea.y, bea.z, 1.0F, 0.08F, 0.04F, 26.0F, 0.4F + 0.8F * pulse, 0.25F, now);
        }

        for (int s = -1; s <= 1; s += 2) {
            Vec3 st = w.local(s * 1.125, 14.3, -4.4);
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, st.x, st.y, st.z, (r.nextDouble() - 0.5) * 0.02, 0.07, (r.nextDouble() - 0.5) * 0.02);
            if (stage >= 2 && now % 2 == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, st.x, st.y, st.z, 0.0, 0.12, 0.0);
            if (stage >= 2 && now % 6 == 0) level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, st.x, st.y, st.z, 0.0, 0.1, 0.0);
        }

        if (r.nextInt(2) == 0) {
            WardenPart leg = w.legs[r.nextInt(4)];
            level.addParticle(EMBER, leg.getX() + (r.nextDouble() - 0.5) * 2.0, w.getY() + 10.0 + r.nextDouble() * 3.0, leg.getZ() + (r.nextDouble() - 0.5) * 2.0, 0.0, 0.02, 0.0);
        }
        if (stage >= 2) level.addParticle(ParticleTypes.SMALL_FLAME, w.getX() + (r.nextDouble() - 0.5) * 8.0, w.getY() + 8.0 + r.nextDouble() * 5.0, w.getZ() + (r.nextDouble() - 0.5) * 8.0, 0.0, 0.04, 0.0);

        if (mode >= WardenEntity.KLAXON && mode <= WardenEntity.DOOR) {
            ceilingDust(level, me, mode == WardenEntity.DOOR ? 2 : 4, 20.0);
            if (mode == WardenEntity.HATCH || mode == WardenEntity.RISING)
                for (int i = 0; i < 6; i++)
                    level.addParticle(ParticleTypes.CLOUD, w.getX() + (r.nextDouble() - 0.5) * 16.0, w.getY() + r.nextDouble() * 4.0, w.getZ() + (r.nextDouble() - 0.5) * 16.0, 0.0, 0.18 + r.nextDouble() * 0.12, 0.0);
            if (mode == WardenEntity.RISING && d < 80.0) shake = Math.max(shake, 0.12F + 0.4F * Math.min(1.0F, t / 200.0F));
            if (mode == WardenEntity.DOOR && d < 80.0) shake = Math.max(shake, 0.18F);
        }

        if (active) {
            int steps = (int) (w.walkDist / STEP);
            Integer was = STEPS.put(w.getId(), steps);
            if (was != null && steps != was) {
                boolean pair = (steps & 1) == 0;
                for (int k = -1; k <= 1; k += 2) {
                    Vec3 f = w.local(k * 7.6, 0.1, (pair ? k : -k) * 7.1);
                    BlockState under = level.getBlockState(BlockPos.containing(f.x, f.y - 0.5, f.z));
                    for (int i = 0; i < 18; i++) {
                        double a = r.nextDouble() * Math.PI * 2.0, sp = 0.15 + r.nextDouble() * 0.15;
                        level.addParticle(ParticleTypes.POOF, f.x, f.y + 0.2, f.z, Math.cos(a) * sp, 0.02, Math.sin(a) * sp);
                        if (!under.isAir() && i % 3 == 0) level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, under), f.x, f.y + 0.2, f.z, Math.cos(a) * sp, 0.15, Math.sin(a) * sp);
                    }
                }
                if (d < 48.0) shake = Math.max(shake, (float) (1.0 - d / 48.0) * 0.55F + 0.08F);
                if (r.nextInt(3) == 0) ceilingDust(level, me, 6, 16.0);
            }
        }

        if (stage >= 2 && mode != WardenEntity.DYING) {
            if (now % 3 == 0) {
                Vec3 sp = w.local((r.nextDouble() - 0.5) * 9.0, 9.0 + r.nextDouble() * 3.0, (r.nextDouble() - 0.5) * 8.0);
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, sp.x, sp.y, sp.z, (r.nextDouble() - 0.5) * 0.3, r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.3);
                level.addParticle(ParticleTypes.LAVA, sp.x, sp.y, sp.z, 0.0, 0.0, 0.0);
            }
            if (now % 2 == 0) for (int s = -1; s <= 1; s += 2) {
                Vec3 lv = w.local(s * 1.4, 12.8, -3.5), out = w.local(s * 2.4, 12.9, -3.5).subtract(lv);
                level.addParticle(ParticleTypes.CLOUD, lv.x, lv.y, lv.z, out.x * 0.5, 0.06, out.z * 0.5);
            }
        }

        if (stage >= 3 && mode != WardenEntity.DYING) {
            Vec3 core = w.local(0.0, 8.6, 3.8);
            float g = 0.5F + 0.5F * Mth.sin(now * 0.25F);
            VeilBridge.glow(key | 3, core.x, core.y, core.z, 1.0F, 0.5F + 0.15F * g, 0.18F, 40.0F, 1.6F + 1.8F * g, 0.35F, now);
            VeilBridge.haze(core.x, core.y, core.z, 5.0, 96.0, 0.7F);
            if (d < 64.0) shake = Math.max(shake, 0.07F + 0.06F * g);
            if (now % 4 == 0) ceilingDust(level, me, 3, 20.0);
        }

        int sh = w.shield();
        if (sh == ForcefieldBlockEntity.UP || sh == ForcefieldBlockEntity.CRACK)
            ClientForcefield.trackVirtual(WardenMeltdown.key(w), w.position(), Balance.WARDEN_SHIELD_R, sh, w.shieldPct(), now);
        if (w.rail() > 0) {
            float u = Math.min(1.0F, w.rail() / (float) Balance.WARDEN_RAIL_CHARGE);
            boolean locked = w.rail() >= Balance.WARDEN_RAIL_CHARGE - Balance.WARDEN_RAIL_LOCK;
            Vec3 m = w.railMuzzle();
            VeilBridge.glow(key | 4, m.x, m.y, m.z, locked ? 1.0F : 0.5F, locked ? 0.3F : 0.8F, locked ? 0.3F : 1.0F, 6.0F + 20.0F * u, 0.5F + 3.0F * u, 0.4F, now);
            for (int i = 0; i < 1 + (int) (u * 4); i++) {
                Vec3 q = m.add((r.nextDouble() - 0.5) * 1.6, (r.nextDouble() - 0.5) * 1.6, (r.nextDouble() - 0.5) * 1.6);
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, (m.x - q.x) * 0.1, (m.y - q.y) * 0.1, (m.z - q.z) * 0.1);
            }
            if (d < 64.0) shake = Math.max(shake, 0.05F + 0.25F * u);
        }

        if (mode == WardenEntity.DYING) {
            for (int i = 0; i < 4; i++) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, w.getX() + (r.nextDouble() - 0.5) * 8.0, w.getY() + 4.0 + r.nextDouble() * 8.0, w.getZ() + (r.nextDouble() - 0.5) * 8.0, 0.0, 0.08, 0.0);
                level.addParticle(ParticleTypes.FLAME, w.getX() + (r.nextDouble() - 0.5) * 8.0, w.getY() + 4.0 + r.nextDouble() * 8.0, w.getZ() + (r.nextDouble() - 0.5) * 8.0, 0.0, 0.05, 0.0);
            }
            if (d < 64.0) shake = Math.max(shake, 0.3F);
        }
    }

    private static void ceilingDust(ClientLevel level, Player me, int n, double r) {
        RandomSource rnd = level.random;
        for (int i = 0; i < n; i++) {
            Vec3 from = me.position().add((rnd.nextDouble() - 0.5) * 2.0 * r, 2.0, (rnd.nextDouble() - 0.5) * 2.0 * r);
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(0.0, 40.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, me));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            BlockState s = level.getBlockState(hit.getBlockPos());
            if (s.isAir()) continue;
            level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, s), hit.getLocation().x, hit.getLocation().y - 0.05, hit.getLocation().z, 0.0, 0.0, 0.0);
        }
    }

    private static void drones(WardenDronesPayload p, long now) {
        java.util.Set<Integer> here = new java.util.HashSet<>();
        for (int i = 0; i < p.ids().length; i++) {
            int id = p.ids()[i];
            here.add(id);
            Drone d = DRONES.computeIfAbsent(id, k -> new Drone());
            boolean fresh = d.seen == 0L;
            d.px = fresh ? p.xyz()[i * 3] : d.cx; d.py = fresh ? p.xyz()[i * 3 + 1] : d.cy; d.pz = fresh ? p.xyz()[i * 3 + 2] : d.cz;
            d.cx = p.xyz()[i * 3]; d.cy = p.xyz()[i * 3 + 1]; d.cz = p.xyz()[i * 3 + 2];
            d.seen = now;
            d.warden = p.warden();
        }
        DRONES.entrySet().removeIf(e -> e.getValue().warden == p.warden() && !here.contains(e.getKey()));
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || DRONES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MeshModel m = MeshModel.named("swarm_drone");
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int ov = OverlayTexture.NO_OVERLAY;
        for (Drone d : DRONES.values()) {
            float u = Mth.clamp((now - d.seen + pt) / 2.0F, 0.0F, 1.0F);
            double x = Mth.lerp(u, d.px, d.cx), y = Mth.lerp(u, d.py, d.cy), z = Mth.lerp(u, d.pz, d.cz);
            Vec3 dir = new Vec3(d.cx - d.px, d.cy - d.py, d.cz - d.pz);
            dir = dir.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 1.0, 0.0) : dir.normalize();
            if (mc.level.random.nextInt(2) == 0) mc.level.addParticle(ParticleTypes.SMOKE, x - dir.x * 0.5, y - dir.y * 0.5, z - dir.z * 0.5, 0.0, 0.0, 0.0);
            if (!m.has("sd_body")) { mc.level.addParticle(RED, x, y, z, 0.0, 0.0, 0.0); continue; }
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(x, y, z));
            ps.pushPose();
            ps.translate(x - cam.x, y - cam.y, z - cam.z);
            ps.mulPose(new Quaternionf().rotationTo(new Vector3f(1, 0, 0), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z)));
            m.draw("sd_body", ps.last(), buffers, light, ov);
            if (m.has("sd_glow")) m.draw("sd_glow", ps.last(), buffers, LightTexture.FULL_BRIGHT, ov);
            if (m.has("sd_wing_r")) m.draw("sd_wing_r", ps.last(), buffers, light, ov);
            if (m.has("sd_wing_f")) m.draw("sd_wing_f", ps.last(), buffers, light, ov);
            if (m.has("sd_prop")) {
                ps.pushPose();
                ps.mulPose(new Quaternionf().rotationX((float) (((now + pt) * 2.6) % (Math.PI * 2.0))));
                m.draw("sd_prop", ps.last(), buffers, light, ov);
                ps.popPose();
            }
            ps.popPose();
        }
        buffers.endBatch();
    }

    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (shake < 0.02F || ClientCutscene.active()) return;
        var lvl = Minecraft.getInstance().level;
        if (lvl == null) return;
        RandomSource rnd = lvl.random;
        event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5F) * shake * 1.6F);
        event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5F) * shake * 1.6F);
        event.setRoll(event.getRoll() + (rnd.nextFloat() - 0.5F) * shake * 0.8F);
    }
}
