package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class ImpactFx {

    private ImpactFx() {}

    private static final class Blast {
        final Vec3 pos;
        final int tier, stage;
        final WarheadType type;
        final double radius, scale, zone;

        final double fb, shock, stem, dust;
        final boolean cap;

        final double g;
        final float gs;

        final double share;
        final boolean lead;

        double ground;
        int age;
        boolean passed;
        final long id = ++blastIds;

        Blast(Vec3 pos, int tier, WarheadType type, int stage) {
            this.pos = pos;
            this.ground = pos.y;
            this.tier = tier;
            this.type = type;
            this.stage = stage;
            double base = Missiles.craterRadius(tier);
            this.radius = Math.max(base * 0.3, base * type.crater);
            this.scale = switch (tier) { case 1 -> 1.0; case 2 -> 1.7; case 3 -> 2.8; default -> 4.6; };
            this.zone = type == WarheadType.CHAFF ? WarheadFx.CHAFF_RADIUS : base * type.area;

            double[] p = switch (type) {
                case HE -> new double[] { 1.0, 1.0, 0.0, 1.0, 0 };
                case INCENDIARY -> new double[] { 0.0, 0.3, 0.0, 0.4, 0 };
                case FRAG -> new double[] { 0.35, 0.6, 0.15, 0.8, 0 };
                case DECOY -> new double[] { 0.0, 0.0, 0.0, 0.0, 0 };
                case THERMOBARIC -> new double[] { 2.0, 1.6, 0.0, 1.2, 0 };
                case CLUSTER -> new double[] { 0.0, 0.0, 0.0, 0.0, 0 };
                case BREACHER -> new double[] { 0.0, 0.0, 0.0, 0.3, 0 };
                case CHAFF -> new double[] { 0.0, 0.0, 0.0, 0.0, 0 };
                case BUNKER -> new double[] { 0.0, 0.0, 0.0, 0.4, 0 };
                case EMP -> new double[] { 0.0, 0.0, 0.0, 0.0, 0 };
                case NUKE -> new double[] { 1.3, 1.3, 1.0, 1.2, 1 };
                case PLASMA -> new double[] { 1.5, 1.5, 0.0, 1.3, 0 };
                case ANTIMATTER -> new double[] { 1.7, 1.7, 0.0, 1.4, 0 };
                case ORBITAL_ROD -> new double[] { 0.0, 2.4, 0.0, 1.9, 0 };
                case ASTEROID -> new double[] { 2.0, 2.8, 0.0, 2.4, 0 };
                case RAILGUN -> new double[] { 1.2, 2.0, 0.0, 1.6, 0 };
                case THERMO -> new double[] { 1.5, 1.6, 1.1, 1.3, 1 };
                case TSAR -> new double[] { 1.9, 2.2, 1.5, 1.7, 1 };
                case ROD -> new double[] { 0.0, 1.3, 0.0, 1.0, 0 };
                case HEMP, SEISMIC -> new double[] { 0.0, 0.0, 0.0, 0.0, 0 };
                case INTERCEPTOR, ASAT, CUBESAT -> new double[] { 0.0, 0.0, 0.0, 0.0, 0 };
                case HEAVY_BLAST -> new double[] { 1.2, 1.4, 0.0, 1.4, 0 };
                case CARPET -> new double[] { 0.0, 0.0, 0.0, 0.0, 0 };
                case UPHEAVAL, DAM_BREAKER -> new double[] { 1.0, 1.0, 0.0, 1.0, 0 };
                case NAPALM, EARTHQUAKE -> new double[] { 0.0, 0.0, 0.0, 0.0, 0 };
                case PENETRATOR -> new double[] { 0.0, 0.0, 0.0, 0.4, 0 };
                case DAISY_CUTTER -> new double[] { 2.0, 1.6, 0.0, 1.2, 0 };
                case NEUTRON -> new double[] { 0.6, 1.2, 0.0, 0.3, 0 };
                case FLASH -> new double[] { 0.0, 0.0, 0.0, 0.0, 0 };
                case CONCUSSION -> new double[] { 0.0, 2.2, 0.0, 1.4, 0 };
                case CHEMICAL -> new double[] { 0.0, 0.0, 0.0, 0.2, 0 };
                case NANITE -> new double[] { 0.0, 0.0, 0.0, 0.3, 0 };
                case GRAVITY -> new double[] { 0.0, 1.2, 0.0, 0.8, 0 };
                case CRYO -> new double[] { 0.0, 1.8, 0.0, 0.4, 0 };
            };
            boolean deep = stage == 1;
            this.fb = deep ? 0 : p[0]; this.shock = deep ? 0 : p[1]; this.stem = deep ? 0 : p[2]; this.dust = deep ? 0 : p[3]; this.cap = p[4] > 0;
            double g0 = Mth.clamp(scale * Math.max(fb, shock) / 3.0, 1.0, 5.0);
            this.share = pieceShare(type, tier);
            this.g = g0 / share;
            this.gs = (float) Math.sqrt(g0);
            long now = ClientClock.ticks();
            this.lead = share >= 1.0 || now - lastPieceLead > 40;
            if (share < 1.0 && lead) lastPieceLead = now;
        }
    }

    private static long lastPieceLead = Long.MIN_VALUE / 2;

    static double pieceShare(WarheadType t, int tier) { return t == WarheadType.ASTEROID && tier < 4 ? Math.pow(2.0, tier - 4) : 1.0; }

    static final int COOL_TICKS = 6000;

    private static final List<double[]> QUAKES = new ArrayList<>();

    private static float[] cooling(double u) {
        u = Math.max(0.0, Math.min(1.0, u));
        if (u < 0.3) { double k = u / 0.3; return new float[] { 1.0F, (float) (0.6 - 0.18 * k), (float) (0.25 - 0.15 * k) }; }
        if (u < 0.7) { double k = (u - 0.3) / 0.4; return new float[] { (float) (1.0 - 0.3 * k), (float) (0.42 - 0.3 * k), (float) (0.1 - 0.07 * k) }; }
        double k = (u - 0.7) / 0.3;
        return new float[] { (float) (0.7 - 0.4 * k), (float) (0.12 - 0.07 * k), (float) (0.03 - 0.02 * k) };
    }

    private static final List<Blast> ACTIVE = new ArrayList<>();
    private static long blastIds;

    private static final class Crater {
        final Vec3 pos;
        final double radius;
        final long id;
        double[][] spots;
        int age;

        Crater(Vec3 pos, double radius, long id) { this.pos = pos; this.radius = radius; this.id = id; }
    }

    private static final List<Crater> CRATERS = new ArrayList<>();
    private static long craterIds;
    private static final int FIRE_TICKS = 1200, FIRE_FADE = 300;

    public static void start(double x, double y, double z, int tier, WarheadType type, int stage) { start(x, y, z, tier, type, stage, 0.0F); }

    public static void start(double x, double y, double z, int tier, WarheadType type, int stage, float air) { start(x, y, z, tier, type, stage, air, false); }

    public static void start(double x, double y, double z, int tier, WarheadType type, int stage, float air, boolean wet) { start(x, y, z, tier, type, stage, air, wet, false); }

    public static void start(double x, double y, double z, int tier, WarheadType type, int stage, float air, boolean wet, boolean shell) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (type == WarheadType.CLUSTER && stage > 0) { ClientCluster.handle(x, y, z, tier, stage); return; }
        if (stage >= 10) { T4Fx.handle(x, y, z, stage); return; }
        if (type == WarheadType.THERMOBARIC && stage == WarheadFx.ST_TBX_FUEL) {
            ClientBlastGeo.fuel(x, y, z, tier);
            Vec3 at = new Vec3(x, y, z);
            double dd = mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);
            play(WmdSounds.TBX_DISPERSE.get(), at, (float) Mth.clamp(1.6 - dd / 500.0, 0.15, 1.6), 1.0F, (int) (dd / 17.0));
            return;
        }
        Blast b = new Blast(new Vec3(x, y + air, z), tier, type, stage);
        b.ground = y;
        ACTIVE.add(b);
        if (stage == 0 && type == WarheadType.ASTEROID && b.lead) {
            double dq = mc.gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(x, y, z));
            if (dq < Balance.ASTEROID_DUST_R && mc.level != null) QUAKES.add(new double[] { mc.level.getGameTime() + dq / Balance.ASTEROID_QUAKE_SPEED, x, y, z });
        }
        if (stage == 0 && air <= 0.0F && !shell && (type == WarheadType.ASTEROID || type == WarheadType.RAILGUN || type == WarheadType.ORBITAL_ROD))
            ClientSnapshot.carve(x, y, z, Missiles.craterRadius(tier) * type.crater, type.depth, type == WarheadType.ORBITAL_ROD, type == WarheadType.ASTEROID);
        if (stage == 0 && WarheadFx.hasWave(type, tier) && !shell) ClientBlastGeo.start(x, y, z, tier, type, b.radius);
        if (stage == 0 && type == WarheadType.BREACHER && !shell) ClientFxBreacher.start(x, y, z, tier, b.radius);
        if (stage == 0 && type == WarheadType.CHAFF) ClientFxChaff.start(x, y, z, tier, b.radius);
        if (type == WarheadType.BUNKER && !shell) {
            if (stage == 0) ClientFxBunker.entry(x, y, z, tier, b.radius);
            else if (stage == 1) ClientFxBunker.deep(x, y, z, tier, b.radius);
        }
        if (stage == 0 && type.mushroom()) ClientFxNuke.start(x, y + air, z, tier, b.radius, type == WarheadType.THERMO, shell);
        if (stage == 0 && type.mushroom() && !shell) ClientMushroom.start(x, y + air, z, y, b.radius);
        if (stage == 0 && type == WarheadType.EMP) ClientFxEmp.start(x, y, z, tier, b.zone);

        if (stage == 0 && b.fb >= 0.5 && air <= 0.0F && !shell) CRATERS.add(new Crater(b.pos, b.radius, ++craterIds));
        if (stage == 0 && air <= 0.0F && !shell && (b.fb > 0.0 || b.shock > 0.5) && b.radius >= 3.0 && (wet || ClientFxSplash.onWater(mc.level, x, y, z)))
            ClientFxSplash.start(x, y, z, b.radius, b.gs);

        double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(b.pos);
        int delay = (int) (d / 17.0);
        if (stage == 0 && WarheadFx.farWave(type, tier)) {
            double reach = WarheadFx.waveReach(type, tier, b.radius);
            int ticks = WarheadFx.waveTicks(type, reach), arrive = WarheadFx.waveArrival(d, reach, ticks);
            delay = arrive >= 0 ? arrive : (int) Math.max(delay, ticks + (d - reach) / 17.0);
        }
        float loud = (float) Mth.clamp(2.0 - d / (700.0 * b.scale), 0.15, 2.0);
        if (stage == 1) {
            play(WmdSounds.BUNKER_DEEP.get(), b.pos, loud, 0.95F + (float) Math.random() * 0.1F, delay);
            if (d < 400 * b.scale) play(WmdSounds.BUNKER_HEAVE.get(), b.pos, Math.min(1.0F, loud * 0.85F), 1.0F, delay + 8);
            return;
        }
        switch (type) {
            case CLUSTER -> { }
            case DECOY -> {
                play(WmdSounds.DECOY_POP.get(), b.pos, loud, 1.0F, delay);
                play(WmdSounds.DECOY_CRACKLE.get(), b.pos, loud * 0.9F, 1.0F, delay + 2);
                play(WmdSounds.DECOY_FIZZ.get(), b.pos, loud * 0.7F, 1.0F, delay + 4);
            }
            case EMP -> {
                play(WmdSounds.EMP_ZAP.get(), b.pos, loud, 0.97F + (float) Math.random() * 0.06F, delay);
                if (d < b.zone * 1.5) play(WmdSounds.EMP_HUM.get(), b.pos, Math.min(1.0F, loud * 0.8F), 1.0F, delay + 6);
            }
            case CHAFF -> {
                play(WmdSounds.DECOY_POP.get(), b.pos, loud, 0.8F, delay);
                play(WmdSounds.CHAFF_CANISTERS.get(), b.pos, loud * 0.9F, 1.0F, delay + 1);
                play(WmdSounds.CHAFF_HISS.get(), b.pos, loud * 0.85F, 1.0F, delay + 4);
            }
            case BREACHER -> {
                play(WmdSounds.BREACHER_PUNCH.get(), b.pos, loud, 0.97F + (float) Math.random() * 0.06F, delay);
                if (d < 160 * b.scale) play(WmdSounds.BREACHER_RATTLE.get(), b.pos, Math.min(1.0F, loud * 0.9F), 0.95F + (float) Math.random() * 0.1F, delay + 2);
            }
            case ASTEROID -> {
                play(d < 300 * b.scale ? WmdSounds.AST_IMPACT.get() : WmdSounds.AST_IMPACT_FAR.get(), b.pos, loud, 1.0F, delay);
                play(WmdSounds.NUKE_ROLL.get(), b.pos, Math.min(1.0F, loud), 0.6F, delay + 6);
                play(WmdSounds.NUKE_ROLL.get(), b.pos, Math.min(1.0F, loud * 0.8F), 0.5F, delay + 40);
            }
            case ORBITAL_ROD -> {
                play(d < 200 * b.scale ? WmdSounds.RD_IMPACT.get() : WmdSounds.RD_IMPACT_FAR.get(), b.pos, loud, 1.0F, delay);
                play(WmdSounds.NUKE_ROLL.get(), b.pos, Math.min(1.0F, loud), 0.7F, delay + 6);
            }
            case RAILGUN -> {
                play(d < 200 * b.scale ? WmdSounds.RG_IMPACT.get() : WmdSounds.RG_IMPACT_FAR.get(), b.pos, loud, 1.0F, delay);
                if (d >= 250) play(WmdSounds.NUKE_ROLL.get(), b.pos, Math.min(1.0F, loud), 0.8F, delay + 6);
            }
            case ANTIMATTER -> {
                play(d < 200 * b.scale ? WmdSounds.ANNIHILATION.get() : WmdSounds.ANNIHILATION_FAR.get(), b.pos, loud, 1.0F, delay);
                if (d >= 300 * b.scale) play(WmdSounds.HE_FAR.get(), b.pos, loud, 0.6F, delay);
            }
            case PLASMA -> {
                play(d < 200 * b.scale ? WmdSounds.PLASMA_BLAST.get() : WmdSounds.PLASMA_BLAST_FAR.get(), b.pos, loud, 1.0F, delay);
                if (d >= 300 * b.scale) play(WmdSounds.HE_FAR.get(), b.pos, loud, 0.7F, delay);
            }
            case NUKE, THERMO, TSAR -> {
                boolean th = type == WarheadType.THERMO;
                play(WmdSounds.NUKE_BOOM.get(), b.pos, loud, th ? 0.8F : 0.95F, delay);
                if (d >= 300 * b.scale) play(WmdSounds.HE_FAR.get(), b.pos, loud, 0.6F, delay);
                if (d >= 250) play(WmdSounds.NUKE_ROLL.get(), b.pos, Math.min(1.0F, loud), th ? 0.85F : 1.0F, delay + 6);
            }
            case ROD -> { }
            case FRAG -> {
                play(WmdSounds.FRAG_CRACK.get(), b.pos, loud, 0.95F + (float) Math.random() * 0.1F, delay);
                if (d < 140 * b.scale) play(WmdSounds.FRAG_WHIZZ.get(), b.pos, Math.min(1.0F, loud), 1.0F, delay + 1);
                if (d < 160 * b.scale) play(WmdSounds.FRAG_PATTER.get(), b.pos, Math.min(1.0F, loud * 0.9F), 0.95F + (float) Math.random() * 0.1F, delay + 3);
            }
            case INCENDIARY -> {
                play(WmdSounds.INC_POP.get(), b.pos, loud, 0.95F + (float) Math.random() * 0.1F, delay);
                if (d < 220 * b.scale) play(WmdSounds.INC_STREAMERS.get(), b.pos, Math.min(1.0F, loud * 0.9F), 1.0F, delay + 3);
                play(WmdSounds.INCENDIARY_ROAR.get(), b.pos, loud * 0.85F, 1.0F, delay + 22);
            }
            case THERMOBARIC -> {
                play(WmdSounds.TBX_WHOOMP.get(), b.pos, loud, 0.9F + (float) Math.random() * 0.1F, delay);
                if (d >= 160 * b.scale) play(WmdSounds.HE_FAR.get(), b.pos, loud, 0.7F, delay);
            }
            case BUNKER -> play(WmdSounds.BUNKER_PUNCH.get(), b.pos, loud, 0.95F + (float) Math.random() * 0.1F, delay);
            case FLASH -> {
                VeilBridge.flash(b.pos.x, b.pos.y, b.pos.z, 1.0F, 1.0F, 1.0F, (float) Balance.FLASH_RANGE, 6.0F, 0.6F, 14);
                play(d < 200 * b.scale ? WmdSounds.FLASH_BANG.get() : WmdSounds.FLASH_BANG_FAR.get(), b.pos, loud, 1.0F, delay);
            }
            case CONCUSSION -> {
                play(WmdSounds.BLAST_WHUMP.get(), b.pos, loud, 0.8F, delay);
                play(d < 160 * b.scale ? WmdSounds.HE_NEAR.get() : WmdSounds.HE_FAR.get(), b.pos, loud, 0.85F, delay + 1);
                play(WmdSounds.SONIC_BOOM.get(), b.pos, loud, 0.9F, delay + 4);
            }
            case NEUTRON -> play(d < 200 * b.scale ? WmdSounds.NEUTRON_BLAST.get() : WmdSounds.NEUTRON_BLAST_FAR.get(), b.pos, loud, 1.0F, delay);
            case NANITE -> play(WmdSounds.NA_RELEASE.get(), b.pos, loud, 1.0F, delay);
            case GRAVITY -> play(WmdSounds.GR_OPEN.get(), b.pos, loud, 1.0F, delay);
            case CRYO -> play(WmdSounds.CR_BURST.get(), b.pos, loud, 1.0F, delay);
            case CHEMICAL -> {
                play(WmdSounds.GAS_RELEASE.get(), b.pos, loud, 0.95F + (float) Math.random() * 0.1F, delay);
            }
            case HE -> {
                if (d < 160 * b.scale) {
                    play(WmdSounds.HE_NEAR.get(), b.pos, loud, (float) (1.0 / Math.pow(b.scale, 0.25)) * (0.96F + (float) Math.random() * 0.08F), delay);
                    if (d < 120 * b.scale) play(WmdSounds.HE_DEBRIS.get(), b.pos, Math.min(1.0F, loud * 0.8F), 1.0F, delay + 12);
                } else play(WmdSounds.HE_FAR.get(), b.pos, loud, 0.9F + (float) Math.random() * 0.15F, delay);
            }
            default -> {
                float p = type == WarheadType.THERMO ? 0.5F : type == WarheadType.NUKE ? 0.7F : 1.0F;
                play(SoundEvents.GENERIC_EXPLODE.value(), b.pos, loud, 0.45F * p, delay);
                play(SoundEvents.LIGHTNING_BOLT_THUNDER, b.pos, loud, 0.55F * p, delay + 2);
                play(SoundEvents.GENERIC_EXPLODE.value(), b.pos, loud * 0.8F, 0.35F * p, delay + 6);
                if (d < 400) play(SoundEvents.WARDEN_SONIC_BOOM, b.pos, loud * 0.7F, 0.5F * p, delay);
            }
        }
    }

    private static void play(SoundEvent event, Vec3 p, float volume, float pitch, int delay) {
        volume *= ClientConfig.farBoom(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(p));
        if (volume <= 0.001F) return;
        SoundInstance s = new SimpleSoundInstance(event.getLocation(), SoundSource.BLOCKS, volume, pitch, RandomSource.create(), false, 0,
                SoundInstance.Attenuation.NONE, p.x, p.y, p.z, false);
        Minecraft.getInstance().getSoundManager().playDelayed(s, delay);
    }

    public static float shake(Vec3 cam) {
        float s = 0.0F;
        for (Blast b : ACTIVE) {
            double reach = 120.0 * b.scale + b.radius * 3;
            double d = cam.distanceTo(b.pos);
            if (d > reach) continue;
            int arrive = (int) (d / 17.0);
            if (b.age < arrive) continue;
            float time = Math.max(0.0F, 1.0F - (b.age - arrive) / 60.0F);
            float k = (float) (1.0 - d / reach);
            s = Math.max(s, k * k * 3.0F * time);
        }
        return s;
    }

    private static void puff(ClientLevel level, SimpleParticleType t, double x, double y, double z, double vx, double vy, double vz) {
        level.addAlwaysVisibleParticle(t, true, x, y, z, vx, vy, vz);
    }

    private static void puff(ClientLevel level, Blast b, SimpleParticleType t, double x, double y, double z, double vx, double vy, double vz) {
        WmdSmokeParticle.nextSize = b.gs;
        try { level.addAlwaysVisibleParticle(t, true, x, y, z, vx, vy, vz); } finally { WmdSmokeParticle.nextSize = 1.0F; }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { ACTIVE.clear(); CRATERS.clear(); QUAKES.clear(); return; }
        for (Iterator<double[]> qi = QUAKES.iterator(); qi.hasNext(); ) {
            double[] q = qi.next();
            if (level.getGameTime() < q[0]) continue;
            qi.remove();
            T4Fx.shakeNear(new Vec3(q[1], q[2], q[3]), Balance.ASTEROID_DUST_R, 2.4F);
        }
        RandomSource rnd = level.random;
        var BANK = WmdParticles.LAUNCH_SMOKE.get();
        var TRAIL = WmdParticles.TRAIL_SMOKE.get();
        var FLAME = WmdParticles.ROCKET_FLAME.get();
        for (Iterator<Blast> it = ACTIVE.iterator(); it.hasNext(); ) {
            Blast b = it.next();
            b.age++;
            double R = b.radius, s = b.scale, x = b.pos.x, y = b.pos.y, z = b.pos.z;
            if (b.age == 1) {
                for (int i = 0; i < (int) Math.ceil(6 * b.fb); i++) level.addAlwaysVisibleParticle(ParticleTypes.FLASH, true, x + rnd.nextGaussian() * R * 0.2, y + 2 + rnd.nextDouble() * R * 0.3, z + rnd.nextGaussian() * R * 0.2, 0, 0, 0);
                for (int i = 0; i < Math.min(6, (int) (4 * s * b.fb)); i++) level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, x + rnd.nextGaussian() * R * 0.25, y + rnd.nextDouble() * R * 0.3, z + rnd.nextGaussian() * R * 0.25, 0, 0, 0);

                for (int i = 0; i < ClientConfig.count((float) (160 * s * b.fb / b.g), rnd); i++) {
                    Vec3 d = new Vec3(rnd.nextGaussian(), Math.abs(rnd.nextGaussian()) * 0.8, rnd.nextGaussian()).normalize();
                    double r = rnd.nextDouble() * R * 0.35;
                    puff(level, b, FLAME, x + d.x * r, y + 1 + d.y * r, z + d.z * r, d.x * 0.35, d.y * 0.35 + 0.1, d.z * 0.35);
                }

                if (b.type != WarheadType.RAILGUN && !b.type.mushroom()) for (int i = 0; i < ClientConfig.count((float) (170 * s * b.shock / b.g), rnd); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, v = 1.1 + rnd.nextDouble() * 1.0;
                    puff(level, b, BANK, x + Math.cos(a) * R * 0.3, b.ground + 0.8 + rnd.nextDouble() * 1.5, z + Math.sin(a) * R * 0.3,
                            Math.cos(a) * v, 0.02 + rnd.nextDouble() * 0.05, Math.sin(a) * v);
                }
            }
            if ((b.type == WarheadType.RAILGUN || b.type == WarheadType.ORBITAL_ROD || b.type == WarheadType.ASTEROID) && b.age >= 2 && b.age <= Balance.RG_SURGE_TICKS) {
                int T = Balance.RG_SURGE_TICKS;
                double f0 = (b.age - 1) / (double) T, f1 = b.age / (double) T, span = Balance.RG_SURGE_EDGE - 0.3;
                double r0 = R * (0.3 + span * (1.0 - (1.0 - f0) * (1.0 - f0))), r1 = R * (0.3 + span * (1.0 - (1.0 - f1) * (1.0 - f1)));
                double n = Balance.RG_SURGE_PUFFS * r1 / (T * R * (0.3 + span * 0.67));
                for (int i = 0; i < ClientConfig.count((float) (n / b.g), rnd); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, rr = r0 + rnd.nextDouble() * (r1 - r0), v = (r1 - r0) * (0.12 + rnd.nextDouble() * 0.15);
                    double px = x + Math.cos(a) * rr, pz = z + Math.sin(a) * rr;
                    int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
                    int top = ClientSnapshot.topAt(bx, bz);
                    double gy = level.hasChunk(bx >> 4, bz >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) : top != Integer.MIN_VALUE ? top : y;
                    puff(level, b, BANK, px, gy + 0.8 + rnd.nextDouble() * 2.5, pz, Math.cos(a) * v, 0.02 + rnd.nextDouble() * 0.05, Math.sin(a) * v);
                }
            }
            if (b.age <= 24) {
                for (int i = 0; i < ClientConfig.count((float) (14 * s * b.fb / b.g), rnd); i++) {
                    puff(level, b, FLAME, x + rnd.nextGaussian() * R * 0.18, y + 1 + rnd.nextDouble() * Math.min(b.age * 1.5, R),
                            z + rnd.nextGaussian() * R * 0.18, rnd.nextGaussian() * 0.08, 0.35 + rnd.nextDouble() * 0.5, rnd.nextGaussian() * 0.08);
                }
            }
            double stem = Math.min(b.age * 1.1, R * 2.6);
            if (b.age <= 200 && !b.type.mushroom()) {
                var stemSmoke = b.type.mushroom() ? null : (net.minecraft.core.particles.DustParticleOptions) new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(palette(b.type)[4], palette(b.type)[5], palette(b.type)[6]), 3.2F + (float) s);
                double foot = b.ground, top = y - b.ground + stem;
                for (int i = 0; i < ClientConfig.count((float) (4.5 * s * b.stem / b.g), rnd); i++) {
                    double yy = rnd.nextDouble() * top, wide = R * (0.34 - 0.2 * (top > 0.1 ? yy / top : 0.0));
                    if (stemSmoke != null) level.addAlwaysVisibleParticle(stemSmoke, true, x + rnd.nextGaussian() * wide, foot + yy, z + rnd.nextGaussian() * wide, rnd.nextGaussian() * 0.03, 0.22 + rnd.nextDouble() * 0.3, rnd.nextGaussian() * 0.03);
                    else puff(level, b, TRAIL, x + rnd.nextGaussian() * wide, foot + yy, z + rnd.nextGaussian() * wide,
                            rnd.nextGaussian() * 0.03, 0.22 + rnd.nextDouble() * 0.3, rnd.nextGaussian() * 0.03);
                }
            }
            if (b.cap && !b.type.mushroom() && b.age > 12 && b.age <= 230) {
                double grow = Math.min(1.0, b.age / 90.0), Rc = R * (0.35 + 0.75 * grow) * 1.15, rc = Rc * 0.45;
                double cy = y + stem + rc * 0.5;
                var capCloud = b.type.mushroom() ? (Object) BANK : dust(palette(b.type), 1.3F);
                WmdSmokeParticle.nextSize = b.gs;
                try {
                    torus(level, rnd, x, cy, z, Rc, rc, ClientConfig.count((float) (34 * Math.min(2.6, s) / b.g), rnd), capCloud, 0.30, 0.05, 0.015);
                    if (b.age < 110) torus(level, rnd, x, cy, z, Rc * 0.8, rc * 0.7, ClientConfig.count((float) (14 * Math.min(2.6, s) / b.g), rnd), FLAME, 0.22, 0.04, 0.01);
                } finally { WmdSmokeParticle.nextSize = 1.0F; }
                for (int i = 0; i < ClientConfig.count((float) (7 * Math.min(2.6, s) / b.g), rnd); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, yy = y + stem * (0.25 + 0.4 * rnd.nextDouble());
                    if (b.type.mushroom()) puff(level, b, BANK, x + Math.cos(a) * R * 0.2, yy, z + Math.sin(a) * R * 0.2, Math.cos(a) * 0.22, 0.03, Math.sin(a) * 0.22);
                    else level.addAlwaysVisibleParticle(dust(palette(b.type), 1.0F), true, x + Math.cos(a) * R * 0.2, yy, z + Math.sin(a) * R * 0.2, Math.cos(a) * 0.22, 0.03, Math.sin(a) * 0.22);
                }
            }
            if (false && b.cap && b.age > 25 && b.age <= 220) {
                for (int i = 0; i < ClientConfig.count((float) (3.5 * s), rnd); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, v = 0.25 + rnd.nextDouble() * 0.35;
                    puff(level, BANK, x + Math.cos(a) * R * 0.25, y + stem + rnd.nextGaussian() * R * 0.15, z + Math.sin(a) * R * 0.25,
                            Math.cos(a) * v, 0.05 + rnd.nextDouble() * 0.08, Math.sin(a) * v);
                }
                if (b.age < 120) {
                    for (int i = 0; i < ClientConfig.count((float) (1.5 * s), rnd); i++) {
                        puff(level, FLAME, x + rnd.nextGaussian() * R * 0.15, y + stem * (0.8 + rnd.nextDouble() * 0.25), z + rnd.nextGaussian() * R * 0.15,
                                rnd.nextGaussian() * 0.05, 0.1, rnd.nextGaussian() * 0.05);
                    }
                }
            }
            if (b.age <= 110 && !b.type.mushroom()) {
                double ring = R * 0.4 + b.age * 0.7;
                var rollDust = b.type.mushroom() ? null : dust(palette(b.type), 1.0F);
                for (int i = 0; i < ClientConfig.count((float) (3 * s * b.dust / (rollDust != null ? b.gs : b.g)), rnd); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    if (rollDust != null) level.addAlwaysVisibleParticle(rollDust, true, x + Math.cos(a) * ring, b.ground + 0.5 + rnd.nextDouble(), z + Math.sin(a) * ring, Math.cos(a) * 0.45, 0.01, Math.sin(a) * 0.45);
                    else puff(level, b, BANK, x + Math.cos(a) * ring, b.ground + 0.5 + rnd.nextDouble(), z + Math.sin(a) * ring,
                            Math.cos(a) * 0.45, 0.01, Math.sin(a) * 0.45);
                }
            }
            ring(level, b, rnd);
            lights(level, b, rnd);
            special(level, b, rnd);

            int life = b.type == WarheadType.CHAFF ? 1800 : b.type == WarheadType.ORBITAL_ROD || b.type == WarheadType.ASTEROID ? COOL_TICKS : 260;
            if (b.age > life) it.remove();
        }
        craters(level, rnd);
    }

    private static float[] palette(WarheadType t) {
        return switch (t) {
            case HE -> new float[] { 0.42F, 0.33F, 0.22F, 3.4F, 0.10F, 0.09F, 0.08F };
            case INCENDIARY -> new float[] { 0.95F, 0.55F, 0.18F, 3.0F, 0.08F, 0.06F, 0.05F };
            case FRAG -> new float[] { 0.78F, 0.77F, 0.72F, 2.6F, 0.45F, 0.45F, 0.45F };
            case THERMOBARIC -> new float[] { 0.88F, 0.80F, 0.66F, 4.2F, 0.18F, 0.16F, 0.14F };
            case CLUSTER -> new float[] { 0.46F, 0.39F, 0.30F, 2.4F, 0.15F, 0.14F, 0.12F };
            case BREACHER -> new float[] { 0.55F, 0.50F, 0.45F, 2.4F, 0.2F, 0.2F, 0.2F };
            case CHAFF -> new float[] { 0.70F, 0.72F, 0.80F, 3.0F, 0.5F, 0.52F, 0.58F };
            case BUNKER -> new float[] { 0.36F, 0.28F, 0.20F, 3.6F, 0.12F, 0.1F, 0.08F };
            case NUKE, THERMO, TSAR -> new float[] { 0.62F, 0.50F, 0.38F, 5.2F, 0.14F, 0.12F, 0.1F };
            case PLASMA -> new float[] { 0.62F, 0.40F, 0.92F, 4.2F, 0.30F, 0.22F, 0.40F };
            case ANTIMATTER -> new float[] { 0.80F, 0.62F, 0.90F, 5.0F, 0.36F, 0.30F, 0.42F };
            case ORBITAL_ROD -> new float[] { 0.58F, 0.54F, 0.48F, 7.0F, 0.26F, 0.24F, 0.22F };
            case ASTEROID -> new float[] { 0.52F, 0.46F, 0.38F, 8.0F, 0.22F, 0.19F, 0.16F };
            case RAILGUN -> new float[] { 0.62F, 0.58F, 0.55F, 6.0F, 0.30F, 0.28F, 0.28F };
            case CONCUSSION -> new float[] { 0.80F, 0.76F, 0.68F, 4.4F, 0.55F, 0.52F, 0.48F };
            case CRYO -> new float[] { 0.86F, 0.93F, 1.0F, 3.6F, 0.75F, 0.82F, 0.92F };
            case GRAVITY -> new float[] { 0.20F, 0.12F, 0.28F, 3.4F, 0.08F, 0.05F, 0.12F };
            default -> new float[] { 0.5F, 0.5F, 0.5F, 2.4F, 0.3F, 0.3F, 0.3F };
        };
    }

    private static net.minecraft.core.particles.DustParticleOptions dust(float[] p, float k) {
        return new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(p[0], p[1], p[2]), p[3] * k);
    }

    private static void torus(ClientLevel level, RandomSource rnd, double cx, double cy, double cz, double Rc, double rc, int n, Object particle, double roll, double rise, double spread) {
        for (int i = 0; i < n; i++) {
            double th = rnd.nextDouble() * Math.PI * 2, ph = rnd.nextDouble() * Math.PI * 2, cp = Math.cos(ph), sp = Math.sin(ph), ct = Math.cos(th), st = Math.sin(th);
            double major = Rc + rc * cp;
            double ox = cx + major * ct, oy = cy + rc * sp, oz = cz + major * st;
            double out = roll * sp + spread, up = -roll * cp + rise;
            net.minecraft.core.particles.ParticleOptions opt = particle instanceof net.minecraft.core.particles.ParticleOptions po ? po : (net.minecraft.core.particles.ParticleOptions) particle;
            level.addAlwaysVisibleParticle(opt, true, ox, oy, oz, ct * out, up, st * out);
        }
    }

    private static void ring(ClientLevel level, Blast b, RandomSource rnd) {
        if (b.shock <= 0.05 || b.stage != 0 || b.type == WarheadType.CHAFF || b.type == WarheadType.DECOY) return;
        double reach = Math.max(b.radius * (b.type.reach > 0 ? b.type.reach : 3.0), b.radius * 2.2);
        int dur = (int) Mth.clamp(reach / 5.0, 24.0, 150.0);
        if (b.age > dur) return;
        double r = reach * (1.0 - Math.pow(1.0 - b.age / (double) dur, 2.0)), prev = reach * (1.0 - Math.pow(1.0 - (b.age - 1) / (double) dur, 2.0)), speed = Math.max(0.3, r - prev);
        float[] pal = palette(b.type);
        boolean big = b.scale >= 1.7 || b.type.mushroom();
        double x = b.pos.x, y = b.pos.y, z = b.pos.z;
        float thick = (float) Math.min(2.2, 0.8 + b.scale * 0.35);
        int n = (int) Mth.clamp(r * 2.0 * Math.min(1.5, b.shock) / b.gs, 14, 160);
        var dustOpt = dust(pal, 1.0F);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * (i + rnd.nextDouble()) / n, cs = Math.cos(a), sn = Math.sin(a);
            double rr = r - rnd.nextDouble() * 1.5 * thick;
            double px = x + cs * rr, pz = z + sn * rr;
            int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            level.addAlwaysVisibleParticle(dustOpt, true, px, gy + 0.3 + rnd.nextDouble() * 1.6 * thick, pz, cs * speed * 0.92, 0.03 + rnd.nextDouble() * 0.10, sn * speed * 0.92);
            if (i % 3 == 0) {
                net.minecraft.world.level.block.state.BlockState st = level.getBlockState(new net.minecraft.core.BlockPos(bx, gy - 1, bz));
                if (!st.isAir()) level.addAlwaysVisibleParticle(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, st), true, px, gy + 0.4, pz, cs * speed * 0.8 + rnd.nextGaussian() * 0.08, 0.25 + rnd.nextDouble() * 0.35, sn * speed * 0.8 + rnd.nextGaussian() * 0.08);
            }
            if (big && i % 4 == 0)
                level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, px, gy + 2.5 + rnd.nextDouble() * 3.0, pz, cs * speed * 0.85, 0.01, sn * speed * 0.85);
            if (b.scale >= 1.0 && i % 7 == 0) level.addAlwaysVisibleParticle(ParticleTypes.POOF, true, px, gy + 0.6, pz, cs * speed, 0.05, sn * speed);
        }

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dc = Math.hypot(cam.x - x, cam.z - z);
        if (!b.passed && r >= dc && dc < reach) {
            b.passed = true;
            T4Fx.shakeNear(new Vec3(x, cam.y, z), reach, (float) (1.4 * b.scale) * (float) (1.0 - dc / reach) + 0.15F);
            level.playLocalSound(cam.x, cam.y, cam.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, (float) Math.min(6.0, 2.0 + b.scale), 0.55F, false);
            for (int i = 0; i < 50; i++)
                level.addAlwaysVisibleParticle(dustOpt, true, cam.x + rnd.nextGaussian() * 3, cam.y + rnd.nextGaussian() * 1.5, cam.z + rnd.nextGaussian() * 3, (cam.x - x) / Math.max(1.0, dc) * speed + rnd.nextGaussian() * 0.1, 0.05, (cam.z - z) / Math.max(1.0, dc) * speed + rnd.nextGaussian() * 0.1);
        }
        if (b.age == 1 && b.scale >= 1.0) VeilBridge.shock(x, y + 1.0, z);
    }

    private static void special(ClientLevel level, Blast b, RandomSource rnd) {
        double R = b.radius, s = b.scale, x = b.pos.x, y = b.pos.y, z = b.pos.z;
        int age = b.age;
        var FLAME = WmdParticles.ROCKET_FLAME.get();
        var BANK = WmdParticles.LAUNCH_SMOKE.get();
        var TRAIL = WmdParticles.TRAIL_SMOKE.get();
        if (b.stage == 1) {
            if (age == 1) quasarSet(b, "bnk_heave:0.5");
            if (age <= 30) {
                for (int i = 0; i < ClientConfig.count((float) (6 * s), rnd); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * b.radius * 0.5;
                    puff(level, BANK, x + Math.cos(a) * rr, y + 0.5, z + Math.sin(a) * rr, Math.cos(a) * 0.3, 0.6 + rnd.nextDouble() * 0.9, Math.sin(a) * 0.3);
                }
            }
            return;
        }
        switch (b.type) {
            case HE -> {
                if (age == 1) quasarSet(b, "he_fire:1.5", "he_firelight:1.5", "he_ring:0.3", "he_debris:0.8", "he_sparks:1.5", "he_smoke:2.0");
                if (age <= 70) {
                    if (age <= 12) for (int i = 0; i < ClientConfig.count((float) (10 * s), rnd); i++) level.addAlwaysVisibleParticle(dust(palette(b.type), 1.2F), true, x + rnd.nextGaussian() * R * 0.15, y + 1, z + rnd.nextGaussian() * R * 0.15, rnd.nextGaussian() * 0.15, 0.7 + rnd.nextDouble() * 0.9, rnd.nextGaussian() * 0.15);
                }
            }
            case INCENDIARY -> {
                if (age == 1) quasarSet(b, "inc_drops:1.5", "inc_droplight:1.5");
                if (age <= 200) {
                    for (int i = 0; i < ClientConfig.count((float) (6 * s), rnd); i++) {
                        puff(level, FLAME, x + rnd.nextGaussian() * b.zone * 0.25, y + 2 + rnd.nextDouble() * 8, z + rnd.nextGaussian() * b.zone * 0.25, 0, -0.05, 0);
                    }
                    if (age % 4 == 0) level.addParticle(ParticleTypes.LAVA, x + rnd.nextGaussian() * b.zone * 0.2, y + 3 + rnd.nextDouble() * 5, z + rnd.nextGaussian() * b.zone * 0.2, 0, 0, 0);
                }
            }
            case FRAG -> {
                if (age == 1) quasarSet(b, "frag_flash:1.5", "frag_shards:1.2", "he_ring:0.3");
                if (age == 1) {
                    for (int i = 0; i < ClientConfig.count((float) (60 * s), rnd); i++) {
                        Vec3 d = new Vec3(rnd.nextGaussian(), Math.abs(rnd.nextGaussian()) * 0.5, rnd.nextGaussian()).normalize();
                        double v = 1.0 + rnd.nextDouble() * 1.8;
                        level.addAlwaysVisibleParticle(ParticleTypes.CRIT, true, x, y + 1, z, d.x * v, d.y * v, d.z * v);
                    }
                }
            }
            case DECOY -> {
                if (age == 1) {
                    VeilBridge.flash(x, y + 3, z, 1.0F, 0.95F, 0.85F, 24.0F, 4.0F, 0.5F, 6);
                    boolean q = VeilBridge.quasar("decoy_flares", x, y + 3, z);
                    if (q) { VeilBridge.quasar("decoy_flares_lit", x, y + 3, z); VeilBridge.quasar("decoy_glitter", x, y + 6, z); }
                    else for (int i = 0; i < 40; i++) level.addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, x + rnd.nextGaussian(), y + 2 + rnd.nextDouble() * 3, z + rnd.nextGaussian(), rnd.nextGaussian() * 0.25, 0.15 + rnd.nextDouble() * 0.2, rnd.nextGaussian() * 0.25);
                }
            }
            case THERMOBARIC -> {
                if (age == 1) quasarSet(b, "tbx_ball:2.0", "tbx_light:2.5", "tbx_ring:0.3", "he_debris:0.8", "tbx_smoke:3.0");
                if (age <= 40) {
                    double dome = R * 1.2 * Math.min(1.0, age / 10.0);
                    for (int i = 0; i < ClientConfig.count((float) (45 * s), rnd); i++) {
                        Vec3 d = new Vec3(rnd.nextGaussian(), Math.abs(rnd.nextGaussian()), rnd.nextGaussian()).normalize();
                        double r = dome * (0.5 + 0.5 * rnd.nextDouble());
                        puff(level, FLAME, x + d.x * r, y + 1 + d.y * r, z + d.z * r, d.x * 0.15, 0.08 + d.y * 0.15, d.z * 0.15);
                    }
                }
                if (age >= 5 && age <= 100 && age % 2 == 0) {
                    for (int i = 0; i < ClientConfig.count((float) (4 * s), rnd); i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * R * 1.4;
                        puff(level, TRAIL, x + Math.cos(a) * rr, y + 1 + rnd.nextDouble() * 4, z + Math.sin(a) * rr, 0, 0.08, 0);
                    }
                }
            }
            case BREACHER -> {
                if (age == 1) quasarSet(b, "he_debris:0.8", "frag_flash:1.0");
                if (age <= 20) {
                    for (int i = 0; i < ClientConfig.count((float) (8 * s), rnd); i++) {
                        puff(level, BANK, x + rnd.nextGaussian() * 0.8, y + 1, z + rnd.nextGaussian() * 0.8, rnd.nextGaussian() * 0.1, 0.8 + rnd.nextDouble() * 1.0, rnd.nextGaussian() * 0.1);
                    }
                }
            }
            case CHAFF -> {
                if (age == 1) quasarSet(b, "chf_glitter:6.0");
            }
            case BUNKER -> {
                if (age == 1) quasarSet(b, "bnk_column:0.5");
                if (age <= 30) {
                    double h = Math.min(age * 2.0, ClientFxBunker.columnHeight(b.radius));
                    for (int i = 0; i < ClientConfig.count((float) (5 * s), rnd); i++) {
                        puff(level, BANK, x + rnd.nextGaussian() * b.radius * 0.3, y + rnd.nextDouble() * h, z + rnd.nextGaussian() * b.radius * 0.3, rnd.nextGaussian() * 0.04, 0.3 + rnd.nextDouble() * 0.3, rnd.nextGaussian() * 0.04);
                    }
                }
            }
            case ASTEROID -> {
                if (age == 1 && b.lead) quasarSet(b, "nuk_ball:3.0", "nuk_light:4.0", "nuk_ring:0.5", "he_debris:3.0");
                if (age <= 60 && age % 2 == 0) for (int i = 0; i < Math.max(1, (int) Math.round(8 * b.share)); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, v = 2.0 + rnd.nextDouble() * 6.0;
                    level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, x + Math.cos(a) * b.radius * 0.3, y + 4.0, z + Math.sin(a) * b.radius * 0.3, Math.cos(a) * v, 1.0 + rnd.nextDouble() * 2.5, Math.sin(a) * v);
                    if (i % 2 == 0) puff(level, b, BANK, x + Math.cos(a) * b.radius * 0.4, y + 6.0 + rnd.nextDouble() * 20.0, z + Math.sin(a) * b.radius * 0.4, Math.cos(a) * v * 0.6, 0.6 + rnd.nextDouble(), Math.sin(a) * v * 0.6);
                }
                if (age >= 40 && age <= 400 && age % 4 == 0) for (int i = 0; i < Math.max(1, (int) Math.round(6 * b.share)); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, dist = b.radius * (1.0 + rnd.nextDouble() * 2.2);
                    level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, x + Math.cos(a) * dist, y + 60.0 + rnd.nextDouble() * 80.0, z + Math.sin(a) * dist, 0.0, -1.5, 0.0);
                }
                if (age <= 300 && age % 3 == 0) for (int i = 0; i < Math.max(1, (int) Math.round(4 * b.share)); i++) {
                    double h = rnd.nextDouble() * Math.min(500.0, age * 5.0);
                    puff(level, b, BANK, x + rnd.nextGaussian() * b.radius * 0.18, y + h, z + rnd.nextGaussian() * b.radius * 0.18, rnd.nextGaussian() * 0.08, 0.5 + rnd.nextDouble() * 0.6, rnd.nextGaussian() * 0.08);
                }
            }
            case ORBITAL_ROD -> {
                if (age == 1) quasarSet(b, "nuk_light:2.0", "nuk_ring:0.4", "he_debris:2.0");
                if (age <= 200 && age % 2 == 0) for (int i = 0; i < 5; i++) {
                    double h = rnd.nextDouble() * Math.min(300.0, age * 4.0);
                    puff(level, b, BANK, x + rnd.nextGaussian() * b.radius * 0.08, y + h, z + rnd.nextGaussian() * b.radius * 0.08, rnd.nextGaussian() * 0.05, 0.5 + rnd.nextDouble() * 0.5, rnd.nextGaussian() * 0.05);
                }
                if (age <= 90 && age % 3 == 0) for (int ring = 0; ring < 3; ring++) {
                    double front = (age - ring * 15) / 75.0 * b.radius * 3.0;
                    if (front <= 0.0) continue;
                    for (int i = 0; i < 12; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2;
                        int bx = (int) Math.floor(x + Math.cos(a) * front), bz = (int) Math.floor(z + Math.sin(a) * front);
                        int top = ClientSnapshot.topAt(bx, bz);
                        double gy = level.hasChunk(bx >> 4, bz >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) : top != Integer.MIN_VALUE ? top : y;
                        puff(level, b, BANK, x + Math.cos(a) * front, gy + 0.6, z + Math.sin(a) * front, Math.cos(a) * 0.6, 0.15, Math.sin(a) * 0.6);
                    }
                }
                if (age <= 400 && age % 4 == 0) for (int i = 0; i < 6; i++)
                    level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, x + rnd.nextGaussian() * b.radius * 0.4, y + 0.5, z + rnd.nextGaussian() * b.radius * 0.4, 0.0, 0.0, 0.0);
            }
            case RAILGUN -> {
                if (age == 1) quasarSet(b, "nuk_ball:2.0", "nuk_light:4.0", "nuk_ring:0.3", "he_debris:1.5");
                if (age <= 120 && age % 2 == 0) for (int i = 0; i < 14; i++) {
                    double h = rnd.nextDouble() * Math.min(260.0, age * 6.0);
                    level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, x + rnd.nextGaussian() * 2.5, y + h, z + rnd.nextGaussian() * 2.5, 0.0, 0.6 + rnd.nextDouble(), 0.0);
                }
                if (age <= 60) for (int k = 0; k < 12; k++) {
                    double a = k * Math.PI / 6.0 + 0.2 * Math.sin(k * 7.0), front = Math.min(1.0, age / 50.0) * b.radius * 2.5;
                    for (int i = 0; i < 3; i++) {
                        double d0 = front * (0.6 + 0.4 * rnd.nextDouble()), wob = rnd.nextGaussian() * 1.2;
                        level.addAlwaysVisibleParticle(ParticleTypes.ELECTRIC_SPARK, true, x + Math.cos(a) * d0 - Math.sin(a) * wob, y + 0.6, z + Math.sin(a) * d0 + Math.cos(a) * wob, 0.0, 0.05, 0.0);
                    }
                }
            }
            case ANTIMATTER -> {
                if (age == 1) quasarSet(b, "nuk_ball:3.0", "nuk_light:4.0", "nuk_ring:0.3", "he_debris:1.0");
                if (age <= 40 && age % 2 == 0) for (int i = 0; i < 10; i++)
                    level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, x + rnd.nextGaussian() * b.radius * 0.5, y + 1 + rnd.nextDouble() * b.radius * 0.7,
                            z + rnd.nextGaussian() * b.radius * 0.5, rnd.nextGaussian() * 0.6, rnd.nextDouble() * 0.6, rnd.nextGaussian() * 0.6);
            }
            case PLASMA -> {
                if (age == 1) quasarSet(b, "nuk_ball:3.0", "nuk_ring:0.3", "he_debris:1.0");
                if (age <= 60 && age % 2 == 0) for (int i = 0; i < 8; i++)
                    level.addAlwaysVisibleParticle(ParticleTypes.ELECTRIC_SPARK, true, x + rnd.nextGaussian() * b.radius * 0.4, y + 1 + rnd.nextDouble() * b.radius * 0.6,
                            z + rnd.nextGaussian() * b.radius * 0.4, rnd.nextGaussian() * 0.5, rnd.nextDouble() * 0.4, rnd.nextGaussian() * 0.5);
            }
            case NUKE, THERMO, TSAR -> {
                if (age == 1) quasarSet(b, "nuk_ball:3.0", "nuk_light:4.0", "nuk_ring:0.3", "he_debris:1.0", "nuk_ash:40.0");
            }
            case EMP -> {
                if (age == 1) quasarSet(b, "emp_arcs:2.0");
                if (age <= 80 && age % 3 == 0) for (int i = 0; i < 6; i++) level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, x + rnd.nextGaussian() * 2, y + 1 + rnd.nextDouble() * 4, z + rnd.nextGaussian() * 2, rnd.nextGaussian() * 0.1, 0.5 + rnd.nextDouble() * 0.5, rnd.nextGaussian() * 0.1);
            }
            default -> { }
        }
    }

    private static float fl(double v) { return (float) v; }

    private static double fade(int age, int dur) { return Math.max(0.0, 1.0 - age / (double) dur); }

    private static void lights(ClientLevel level, Blast b, RandomSource rnd) {
        if (!VeilBridge.active()) return;
        double R = b.radius, s = b.scale, x = b.pos.x, y = b.pos.y, z = b.pos.z;
        int age = b.age;
        long t = level.getGameTime();
        long k = 0x1F00_0000_0000_0000L ^ (b.id << 8);
        if (b.stage == 1) {
            if (age == 1) { VeilBridge.flash(x, y + 3, z, 1.0F, 0.7F, 0.35F, fl(R * 2 + 24), 4.0F, 0.5F, 36); VeilBridge.shock(x, y + 1, z); }
            if (age <= 40) {
                double f = fade(age, 40);
                VeilBridge.glow(k + 1, x, y + 2, z, 1.0F, 0.45F, 0.15F, fl(R * 1.6 + 10), fl(4.0 * f + 0.3), 0.6F, t);
                VeilBridge.bloomNear(x, y, z, 500.0, fl(1.2 * f));
            }
            return;
        }
        switch (b.type) {
            case HE -> {
                if (age == 1) { VeilBridge.flash(x, y + 2.0, z, 1.0F, 0.85F, 0.6F, 40.0F, 6.0F, 0.7F, 30); VeilBridge.shock(x, y + 1.0, z); }
                blastLights(level, b, rnd);
            }
            case INCENDIARY -> {
                if (age == 1) VeilBridge.flash(x, y + ClientBlastGeo.incBurstHeight(R), z, 1.0F, 0.85F, 0.6F, fl(R * 3 + 24), 5.0F, 0.6F, 20);
                if (age <= 600) {
                    double f = fade(age, 600), spread = Math.max(R * 1.3, 6.0) * Math.min(1.0, 0.3 + age / 120.0);
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3.0 + (b.id % 7), d = spread * Math.sqrt((i + 0.5) / 6.0);
                        float flick = fl(0.7 + rnd.nextDouble() * 0.6);
                        VeilBridge.glow(k + 10 + i, x + Math.cos(a) * d, y + 1.6, z + Math.sin(a) * d, 1.0F, fl(0.4 + 0.12 * rnd.nextDouble()), 0.1F, 15.0F, fl(2.0 * f * flick + 0.2), 0.3F, t);
                    }
                    if (age < 240) VeilBridge.bloomNear(x, y + 2, z, 500.0, fl(1.0 * fade(age, 240)));
                    VeilBridge.haze(x, y + 2, z, Math.max(R, 5.0), 300.0, fl(0.8 * f));
                }
            }
            case FRAG -> {
                if (age == 1) { VeilBridge.flash(x, y + 2.0, z, 1.0F, 0.95F, 0.85F, 30.0F, 7.0F, 0.2F, 8); VeilBridge.shock(x, y + 1.0, z); }
                if (age <= 8) {
                    for (int i = 0; i < 3; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * Math.max(R * 4, 8.0);
                        VeilBridge.flash(x + Math.cos(a) * d, y + 1 + rnd.nextDouble() * 4, z + Math.sin(a) * d, 1.0F, 0.9F, 0.7F, 6.0F, 3.0F, 0.1F, 5);
                    }
                }
            }
            case DECOY -> {
                if (age == 1) VeilBridge.flash(x, y + 3.0, z, 1.0F, 0.95F, 0.8F, 14.0F, 3.0F, 0.3F, 12);
                if (age == 2 || age == 5 || age == 8 || age == 11) {
                    float[][] c = { { 1.0F, 0.35F, 0.55F }, { 0.3F, 0.85F, 1.0F }, { 1.0F, 0.85F, 0.3F }, { 0.6F, 1.0F, 0.5F } };
                    float[] col = c[(age / 3) % 4];
                    VeilBridge.flash(x + rnd.nextGaussian() * 2, y + 4 + rnd.nextDouble() * 3, z + rnd.nextGaussian() * 2, col[0], col[1], col[2], 12.0F, 3.0F, 0.4F, 14);
                }
            }
            case THERMOBARIC -> {
                if (age == 1) { VeilBridge.flash(x, y + 3.0, z, 1.0F, 0.75F, 0.4F, fl(R * 4 + 30), 7.0F, 0.8F, 50); VeilBridge.shock(x, y + 1.0, z); }
                if (age <= 160) {
                    double f = fade(age, 70);
                    VeilBridge.glow(k + 1, x, y + R * 0.5 * Math.min(1.0, age / 10.0), z, 1.0F, 0.65F, 0.3F, fl(R * 3.2 + 16), fl(6.0 * f + 0.5), 0.8F, t);
                    VeilBridge.glow(k + 2, x, y + R * 0.8, z, 0.9F, 0.3F, 0.12F, fl(R * 4), fl(2.5 * fade(age, 160)), 0.5F, t);
                    VeilBridge.bloomNear(x, y + 2, z, 700.0, fl(1.8 * fade(age, 120)));
                    VeilBridge.haze(x, y + R * 0.4, z, R * 1.2, 600.0, fl(fade(age, 100)));
                }
            }
            case BREACHER -> {
                if (age == 1) VeilBridge.flash(x, y + 1.5, z, 1.0F, 0.95F, 0.85F, 22.0F, 5.0F, 0.3F, 5);
                double colH = Math.max(25.0, Math.min(80.0, 20.0 + R * 2.5));
                if (age <= 30) for (int i = 0; i < 3; i++) VeilBridge.glow(k + 3 + i, x, y + 2 + (i + 1) * colH * 0.25 * Math.min(1.0, age / 12.0), z, 1.0F, 0.6F, 0.25F, 10.0F, fl(2.4 * fade(age, 30)), 0.3F, t);
                if (age <= 200) VeilBridge.glow(k + 1, x, y + 1.0, z, 1.0F, 0.45F, 0.12F, 12.0F, fl(2.0 * Math.pow(fade(age, 200), 1.6)), 0.3F, t);
            }
            case CHAFF -> {
                if (age == 1) VeilBridge.flash(x, y + 3.0, z, 0.9F, 0.93F, 1.0F, 24.0F, 2.0F, 0.5F, 18);
                if (age <= 1800) {
                    double fin = Math.min(1.0, age / 60.0), fout = Math.min(1.0, (1800 - age) / 300.0);
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3.0 + (b.id % 5), d = b.zone * 0.55;
                        double breathe = 0.75 + 0.25 * Math.sin(age * 0.03 + i);
                        VeilBridge.glow(k + 20 + i, x + Math.cos(a) * d, y + 8, z + Math.sin(a) * d, 0.85F, 0.9F, 1.0F, 30.0F, fl(0.7 * breathe * fin * fout), 0.8F, t);
                    }
                }
            }
            case BUNKER -> {
                if (age == 1) VeilBridge.flash(x, y + 2.0, z, 1.0F, 0.6F, 0.3F, 18.0F, 2.0F, 0.4F, 8);
                if (age <= 30) {
                    VeilBridge.glow(k + 1, x, y + 2, z, 1.0F, 0.55F, 0.2F, 10.0F, fl(1.2 * fade(age, 30)), 0.4F, t);
                    VeilBridge.bloomNear(x, y + 2, z, 400.0, fl(0.4 * fade(age, 30)));
                }
            }
            case EMP -> {
                if (age == 1) VeilBridge.flash(x, y + 2.0, z, 0.6F, 0.8F, 1.0F, fl(b.zone * 0.6 + 30), 8.0F, 1.0F, 40);
                if (age <= 80) {
                    VeilBridge.glow(k + 1, x, y + 3, z, 0.55F, 0.85F, 1.0F, 40.0F, fl(4.0 * fade(age, 60)), 0.6F, t);
                    VeilBridge.bloomNear(x, y + 2, z, 900.0, fl(1.2 * fade(age, 80)));
                }
            }
            case ASTEROID -> {
                if (age == 1 && b.lead) {
                    VeilBridge.flash(x, y + 6.0, z, 1.0F, 0.95F, 0.85F, 420.0F, 18.0F, 1.6F, 60);
                    VeilBridge.flash(x, y + 80.0, z, 1.0F, 0.7F, 0.4F, 600.0F, 10.0F, 1.2F, 200);
                    VeilBridge.shock(x, y + 1.0, z);
                }
                if (age <= COOL_TICKS) {
                    double f = fade(age, 800), u = age / (double) COOL_TICKS;
                    float[] cc = cooling(u);
                    VeilBridge.glow(k + 1, x, y + 4.0, z, cc[0], cc[1], cc[2], fl(R * 0.7), fl((7.0 * f + 0.4) * (0.3 + 0.7 * Math.pow(1.0 - u, 1.5)) + 1.2 * Math.pow(1.0 - u, 2.0)), 0.6F, t);
                    if (age <= 300) VeilBridge.bloomNear(x, y + 2, z, 3000.0, fl(3.0 * fade(age, 300)));
                    if (age <= 600) VeilBridge.haze(x, y + R * 0.5, z, R * 1.4, 2600.0, fl(fade(age, 600)));
                }
            }
            case ORBITAL_ROD -> {
                if (age == 1) {
                    VeilBridge.flash(x, y + 4.0, z, 1.0F, 0.98F, 0.92F, 300.0F, 16.0F, 1.4F, 40);
                    VeilBridge.flash(x, y + 60.0, z, 0.9F, 0.85F, 0.75F, 420.0F, 8.0F, 1.0F, 140);
                    VeilBridge.shock(x, y + 1.0, z);
                }
                if (age <= COOL_TICKS) {
                    double f = fade(age, 600), u = age / (double) COOL_TICKS, tail = 1.5 * Math.pow(1.0 - u, 2.0);
                    float[] cc = cooling(u);
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3.0;
                        VeilBridge.glow(k + 1 + i, x + Math.cos(a) * R * 0.45, y + 2.0, z + Math.sin(a) * R * 0.45, cc[0], cc[1], cc[2], fl(R * 0.5), fl(5.0 * f + 0.3 * (1.0 - u) + tail), 0.6F, t);
                    }
                    VeilBridge.glow(k + 8, x, y + 2.0, z, cc[0], cc[1], cc[2], fl(R * 0.4), fl(6.0 * f + 0.5 * (1.0 - u) + tail), 0.6F, t);
                    if (age <= 200) VeilBridge.bloomNear(x, y + 2, z, 2200.0, fl(2.6 * fade(age, 200)));
                    if (age <= 300) VeilBridge.haze(x, y + R * 0.4, z, R, 1800.0, fl(fade(age, 300)));
                }
            }
            case RAILGUN -> {
                if (age == 1) {
                    VeilBridge.flash(x, y + 4.0, z, 0.9F, 0.95F, 1.0F, 260.0F, 16.0F, 1.4F, 40);
                    VeilBridge.flash(x, y + 40.0, z, 0.55F, 0.75F, 1.0F, 360.0F, 8.0F, 1.0F, 120);
                    VeilBridge.shock(x, y + 1.0, z);
                }
                if (age <= 240) {
                    double f = fade(age, 240);
                    for (int i = 0; i < 6; i++)
                        VeilBridge.glow(k + 1 + i, x, y + 10.0 + i * 40.0 * Math.min(1.0, age / 30.0), z, 0.6F, 0.8F, 1.0F, 60.0F, fl(6.0 * f + 0.5), 0.8F, t);
                    if (age <= 80) for (int r2 = 0; r2 < 6; r2++) {
                        double a = r2 * Math.PI / 3.0, dd = Math.min(1.0, age / 50.0) * R * 2.5;
                        VeilBridge.glow(k + 10 + r2, x + Math.cos(a) * dd, y + 1.0, z + Math.sin(a) * dd, 0.55F, 0.8F, 1.0F, 18.0F, fl(4.0 * fade(age, 80)), 0.4F, t);
                    }
                    VeilBridge.bloomNear(x, y + 2, z, 2000.0, fl(2.4 * fade(age, 160)));
                    VeilBridge.haze(x, y + R * 0.4, z, R, 1500.0, fl(fade(age, 140)));
                }
            }
            case ANTIMATTER -> {
                if (age == 1) {
                    VeilBridge.flash(x, y + 4.0, z, 1.0F, 1.0F, 1.0F, 220.0F, 16.0F, 1.4F, 40);
                    VeilBridge.flash(x, y + 10.0, z, 0.95F, 0.35F, 0.95F, 320.0F, 8.0F, 1.0F, 120);
                    VeilBridge.shock(x, y + 1.0, z);
                }
                if (age <= 200) {
                    double f = fade(age, 200), heat = Math.max(0.0, 1.0 - age / 100.0);
                    VeilBridge.glow(k + 1, x, y + R * 0.4 * Math.min(1.0, age / 5.0), z, fl(0.6 + 0.4 * heat), fl(0.25 + 0.75 * heat * heat), fl(0.85 + 0.15 * heat), fl(R * 4 + 60), fl(10.0 * f + 1.0), 1.0F, t);
                    VeilBridge.bloomNear(x, y + 2, z, 1800.0, fl(2.6 * fade(age, 140)));
                    VeilBridge.haze(x, y + R * 0.4, z, R, 1400.0, fl(fade(age, 120)));
                }
            }
            case PLASMA -> {
                if (age == 1) {
                    VeilBridge.flash(x, y + 3.0, z, 0.95F, 0.85F, 1.0F, 140.0F, 12.0F, 1.2F, 50);
                    VeilBridge.flash(x, y + 8.0, z, 0.6F, 0.25F, 1.0F, 220.0F, 6.0F, 0.9F, 100);
                    VeilBridge.shock(x, y + 1.0, z);
                }
                if (age <= 160) {
                    double f = fade(age, 160), heat = Math.max(0.0, 1.0 - age / 90.0);
                    VeilBridge.glow(k + 1, x, y + R * 0.4 * Math.min(1.0, age / 6.0), z, fl(0.55 + 0.45 * heat), fl(0.2 + 0.7 * heat), 1.0F, fl(R * 4 + 40), fl(8.0 * f + 1.0), 1.0F, t);
                    VeilBridge.bloomNear(x, y + 2, z, 1200.0, fl(2.0 * fade(age, 120)));
                    VeilBridge.haze(x, y + R * 0.4, z, R, 1000.0, fl(fade(age, 100)));
                    if (age <= 100 && age % 3 == 0) {
                        double a = rnd.nextDouble() * Math.PI * 2, d = R * 1.2 * Math.sqrt(rnd.nextDouble());
                        VeilBridge.flash(x + Math.cos(a) * d, y + 1.5, z + Math.sin(a) * d, 0.55F, 0.6F, 1.0F, 14.0F, 3.5F, 0.4F, 6 + rnd.nextInt(8));
                    }
                }
            }
            case NUKE, THERMO, TSAR -> {
                if (age == 1) {
                    VeilBridge.flash(x, y + 3.0, z, 1.0F, 0.95F, 0.8F, 160.0F, 12.0F, 1.2F, 60);
                    VeilBridge.flash(x, y + 10.0, z, 1.0F, 0.6F, 0.25F, 260.0F, 6.0F, 0.9F, 120);
                    VeilBridge.shock(x, y + 1.0, z);
                }
                if (age <= 200) {
                    double f = fade(age, 200), heat = Math.max(0.0, 1.0 - age / 120.0);

                    VeilBridge.glow(k + 1, x, y + R * 0.35 * Math.min(1.0, age / 6.0), z, 1.0F, fl(0.55 + 0.4 * heat), fl(0.25 + 0.55 * heat), fl(R * 4 + 40), fl(9.0 * f + 1.0), 1.0F, t);

                    double top = Math.min(age * 1.1, R * 2.6);
                    VeilBridge.glow(k + 2, x, y + top, z, 1.0F, 0.5F, 0.2F, fl(R * 3), fl(5.0 * fade(age, 160) * Math.min(1.0, age / 20.0)), 0.8F, t);
                    VeilBridge.bloomNear(x, y + 2, z, 1500.0, fl(2.2 * fade(age, 150)));
                    VeilBridge.haze(x, y + R * 0.4, z, R, 1200.0, fl(fade(age, 120)));
                    if (age <= 120 && age % 4 == 0) {
                        double a = rnd.nextDouble() * Math.PI * 2, d = R * 1.5 * Math.sqrt(rnd.nextDouble());
                        VeilBridge.flash(x + Math.cos(a) * d, y + 1.0, z + Math.sin(a) * d, 1.0F, 0.5F, 0.15F, 12.0F, 3.0F, 0.3F, 20 + rnd.nextInt(30));
                    }
                }
            }
            default -> { }
        }
    }

    private static boolean quasarSet(Blast b, String... parts) {
        String sz = b.tier <= 1 ? "s" : b.tier == 2 ? "m" : "l";
        int n = b.tier >= 4 ? 5 : 1;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI / 2, r = i == 0 ? 0.0 : b.radius * 0.45, px = b.pos.x + Math.cos(a) * r, pz = b.pos.z + Math.sin(a) * r;
            for (String part : parts) {
                int c = part.indexOf(':');
                if (!VeilBridge.quasar(part.substring(0, c) + "_" + sz, px, b.pos.y + Double.parseDouble(part.substring(c + 1)), pz)) return false;
            }
        }
        return true;
    }

    private static void blastLights(ClientLevel level, Blast b, RandomSource rnd) {
        double R = b.radius, x = b.pos.x, y = b.pos.y, z = b.pos.z;
        long t = level.getGameTime();
        if (b.age <= 24) {
            float k = 1.0F - b.age / 24.0F;
            double fy = y + 1 + Math.min(b.age * 1.5, R) * 0.5;
            VeilBridge.glow(0x1F00_0000_0000_0000L ^ b.pos.hashCode(), x, fy, z, 1.0F, 0.55F, 0.2F, (float) (R * 2 + 10), 4.0F * k + 0.5F, 0.8F, t);
        }
        if (b.age <= 80) {
            float k = 1.0F - b.age / 80.0F;
            VeilBridge.bloomNear(x, y + 2, z, 600.0, 1.6F * k);
            VeilBridge.haze(x, y + R * 0.4, z, R * 0.8, 500.0, k);
        }
        if (b.age == 2) {
            for (int i = 0; i < ClientConfig.count(60, rnd); i++) {
                double a = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * R * 0.5;
                level.addParticle(ParticleTypes.LAVA, x + Math.cos(a) * rr, y + 1 + rnd.nextDouble() * 3, z + Math.sin(a) * rr, 0, 0, 0);
            }
            for (int i = 0; i < 6; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, rr = R * (0.4 + rnd.nextDouble() * 0.8);
                VeilBridge.flash(x + Math.cos(a) * rr, y + 1.0, z + Math.sin(a) * rr, 1.0F, 0.5F, 0.15F, 5.0F, 2.0F, 20 + rnd.nextInt(30));
            }
        }
    }

    private static void craters(ClientLevel level, RandomSource rnd) {
        long t = level.getGameTime();
        for (Iterator<Crater> it = CRATERS.iterator(); it.hasNext(); ) {
            Crater c = it.next();
            c.age++;
            if (c.age > FIRE_TICKS) { it.remove(); continue; }
            if (c.age < 40) continue;
            if (c.spots == null) {
                int n = 6;
                c.spots = new double[n][];
                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, rr = c.radius * (0.15 + rnd.nextDouble() * 0.6);
                    int fx = (int) Math.floor(c.pos.x + Math.cos(a) * rr), fz = (int) Math.floor(c.pos.z + Math.sin(a) * rr);
                    int fy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, fx, fz);
                    c.spots[i] = new double[] { fx + 0.5, fy + 0.15, fz + 0.5 };
                }
            }
            if (c.spots.length > 0) VeilBridge.bloomNear(c.spots[0][0], c.spots[0][1], c.spots[0][2], 120.0, 0.5F);
            float fade = c.age > FIRE_TICKS - FIRE_FADE ? (FIRE_TICKS - c.age) / (float) FIRE_FADE : 1.0F;
            for (int i = 0; i < c.spots.length; i++) {
                double[] p = c.spots[i];
                float flick = 0.75F + rnd.nextFloat() * 0.5F;
                VeilBridge.glow(0x2E00_0000_0000_0000L ^ (c.id * 16 + i), p[0], p[1] + 0.6, p[2], 1.0F, 0.48F, 0.14F, 7.0F, 1.7F * fade * flick, t);
                if (rnd.nextFloat() < 0.6F * fade) level.addParticle(ParticleTypes.FLAME, p[0] + (rnd.nextDouble() - 0.5) * 0.8, p[1], p[2] + (rnd.nextDouble() - 0.5) * 0.8, 0, 0.03, 0);
                if (rnd.nextFloat() < 0.15F * fade) level.addParticle(ParticleTypes.LARGE_SMOKE, p[0] + (rnd.nextDouble() - 0.5) * 0.6, p[1] + 0.4, p[2] + (rnd.nextDouble() - 0.5) * 0.6, 0, 0.05, 0);
                if (rnd.nextFloat() < 0.02F * fade) level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, p[0], p[1] + 0.3, p[2], 0, 0.06, 0);
            }
        }
    }
}
