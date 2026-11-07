package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WarheadFx {

    private WarheadFx() {}

    public static final int IGNITE = 1, FUEL = 2, BOMBLETS = 3, DEEP = 4, EMP = 5, CHAFF = 6, SHOCK = 7, ROD_FALL = 8, AFTER = 9, FISSURE = 10, HEMP_FIELD = 11, SEISMIC_QUAKE = 12, WRECK = 13, BLAST_WAVE = 14, GLASS_SHOCK = 15,
            NAPALM_WAVE = 16, QUAKE_BOMB = 17,
            GAS = 18,
            NANITE = 19, GRAVITY_WELL = 20, CRYO_FIELD = 21;

    public static final double SHOCK_RADIUS = 250.0;
    public static final int SHOCK_TICKS = 160, SHOCK_DEPTH = 40, SHOCK_MAX_COLUMNS = 3200;

    public static final double CHAFF_RADIUS = 50.0;

    private static final java.util.concurrent.atomic.AtomicInteger NEXT_FX_ID = new java.util.concurrent.atomic.AtomicInteger();

    public static final class Task {
        int kind, ticks, total, type, tier;
        boolean refined;
        double x, y, z, r, dx, dz;

        java.util.UUID owner;

        private int fxId;
        int fxId() { if (fxId == 0) fxId = NEXT_FX_ID.incrementAndGet(); return fxId; }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("k", kind); t.putInt("t", ticks); t.putInt("T", total); t.putInt("ty", type); t.putInt("ti", tier); t.putBoolean("rf", refined);
            t.putDouble("x", x); t.putDouble("y", y); t.putDouble("z", z); t.putDouble("r", r); t.putDouble("dx", dx); t.putDouble("dz", dz);
            if (owner != null) t.putUUID("ow", owner);
            return t;
        }

        static Task load(CompoundTag t) {
            Task k = new Task();
            k.kind = t.getInt("k"); k.ticks = t.getInt("t"); k.total = t.getInt("T"); k.type = t.getInt("ty"); k.tier = t.getInt("ti"); k.refined = t.getBoolean("rf");
            k.x = t.getDouble("x"); k.y = t.getDouble("y"); k.z = t.getDouble("z"); k.r = t.getDouble("r"); k.dx = t.getDouble("dx"); k.dz = t.getDouble("dz");
            if (t.hasUUID("ow")) k.owner = t.getUUID("ow");
            return k;
        }
    }

    private static Task task(int kind, double x, double y, double z, double r, int ticks, WarheadType t, int tier) {
        Task k = new Task();
        k.kind = kind; k.x = x; k.y = y; k.z = z; k.r = r; k.ticks = ticks; k.total = ticks; k.type = t.ordinal(); k.tier = tier;
        k.owner = ShipStrike.shooter();
        return k;
    }

    static final int IMPACT_WAIT_TICKS = 100;
    static boolean impactReady(ServerLevel level, FlightManager mgr, int... xz) {
        boolean ok = true;
        for (int i = 0; i + 1 < xz.length; i += 2)
            if (level.getChunkSource().getChunkNow(xz[i] >> 4, xz[i + 1] >> 4) == null) { mgr.holdForTest(level, xz[i], xz[i + 1], 16.0); ok = false; }
        return ok;
    }

    public static double holdRadius(int tier, WarheadType t) {
        double base = Missiles.craterRadius(tier);
        double carve = base * t.crater * Math.max(1.4, outer(t)) + 4.0;
        return Math.max(carve, Math.max(base * 1.5 + 16.0, Math.min(128.0, base * t.area * 1.2 + 16.0)));
    }

    private static double outer(WarheadType t) { return t == WarheadType.THERMOBARIC ? 2.2 : 0.0; }

    public static void detonate(ServerLevel level, FlightManager mgr, int gx, int gy, int gz, int tier, WarheadType t, double dirX, double dirZ) {
        detonate(level, mgr, gx, gy, gz, tier, t, dirX, dirZ, false);
    }

    public static void detonate(ServerLevel level, FlightManager mgr, int gx, int gy, int gz, int tier, WarheadType t, double dirX, double dirZ, boolean rf) {
        if (Forcefield.absorbs(level, gx + 0.5, gy, gz + 0.5, tier, t)) { mgr.release(level, gx, gz, holdRadius(tier, t), level.getGameTime() + 100); return; }
        Planet.flash(level, gx + 0.5, gz + 0.5, tier);
        NuclearWinter.blast(level, t);
        StationLife.blast(level, gx + 0.5, gy + 0.5, gz + 0.5, Missiles.craterRadius(tier) * Math.max(0.3, t.crater));
        double base = Missiles.craterRadius(tier);
        long now = level.getGameTime();
        double hold = holdRadius(tier, t);
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 1.0E-6) { dirX = 0.0; dirZ = 1.0; len = 1.0; }
        double dx = dirX / len, dz = dirZ / len;
        switch (t) {
            case INCENDIARY -> {
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                mgr.tasks.add(task(IGNITE, gx + 0.5, gy, gz + 0.5, base * 1.8 * (rf ? 1.3 : 1.0), rf ? 900 : 600, t, tier));
            }
            case FRAG -> {
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                fragGlass(level, new Vec3(gx + 0.5, gy + 1.0, gz + 0.5), base * t.crater * t.reach * (rf ? 1.3 : 1.0));
            }
            case DECOY -> {
                pop(level, gx, gy, gz, tier, t);
                mgr.release(level, gx, gz, hold, now + (rf ? 320 : 200));
            }
            case THERMOBARIC -> {
                Task k = task(FUEL, gx + 0.5, gy, gz + 0.5, base * 1.1 * (rf ? 1.25 : 1.0), FUEL_TICKS, t, tier);
                k.refined = rf;
                mgr.tasks.add(k);
                send(level, gx + 0.5, gy, gz + 0.5, tier, t, ST_TBX_FUEL);
            }
            case CLUSTER -> {

                Task k = task(BOMBLETS, gx + 0.5, gy, gz + 0.5, base * 0.28, CLUSTER_RAIN + (int) Math.round(8 * (tier + 1) * (rf ? 1.25 : 1.0)), t, tier);
                k.dx = dx; k.dz = dz;
                mgr.tasks.add(k);
                mgr.release(level, gx, gz, hold, now + 200 + k.total);
                int heading = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dz, dx))), 360);
                PacketDistributor.sendToPlayersNear(level, null, gx + 0.5, gy, gz + 0.5, 1500, new ImpactPayload(gx + 0.5, gy, gz + 0.5, tier, WarheadType.CLUSTER.ordinal(), ST_CLUSTER_OPEN + heading));
            }
            case HEAVY_BLAST -> {
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                fragGlass(level, new Vec3(gx + 0.5, gy + 1.0, gz + 0.5), Balance.HEAVY_GLASS);
            }
            case CARPET -> {
                Task k = task(BOMBLETS, gx + 0.5, gy, gz + 0.5, base * 0.28, CLUSTER_RAIN + Balance.CARPET_BOMBLETS / 2, t, tier);
                k.dx = dx; k.dz = dz;
                mgr.tasks.add(k);
                mgr.release(level, gx, gz, hold, now + 200 + k.total);
                int heading = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dz, dx))), 360);
                PacketDistributor.sendToPlayersNear(level, null, gx + 0.5, gy, gz + 0.5, 1500, new ImpactPayload(gx + 0.5, gy, gz + 0.5, tier, WarheadType.CLUSTER.ordinal(), ST_CLUSTER_OPEN + heading));
            }
            case UPHEAVAL -> {
                blast(level, mgr, gx, gy - 3, gz, tier, t, rf);
                double r = base * t.crater * 1.6;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(gx - r, gy - 4, gz - r, gx + r, gy + 8, gz + r))) {
                    double d = Math.hypot(e.getX() - gx - 0.5, e.getZ() - gz - 0.5);
                    if (d > r) continue;
                    e.setDeltaMovement(e.getDeltaMovement().add(0.0, 1.3 * (1.0 - d / r) + 0.2, 0.0));
                    e.hurtMarked = true;
                }
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, gx + 0.5, gy + 1.0, gz + 0.5, 30, r * 0.4, 1.0, r * 0.4, 0.02);
            }
            case NAPALM -> {
                Task k = task(NAPALM_WAVE, gx + 0.5, gy, gz + 0.5, NAPALM_LENGTH, NAPALM_TICKS, t, tier);
                k.dx = dx; k.dz = dz;
                mgr.tasks.add(k);
                send(level, gx + 0.5, gy, gz + 0.5, 1, WarheadType.INCENDIARY, 0);
                level.playSound(null, gx + 0.5, gy, gz + 0.5, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 12.0F, 0.5F);
            }
            case PENETRATOR -> {
                penShaft(level, gx, gy, gz, 1.6, PEN_DEPTH);
                mgr.tasks.add(task(DEEP, gx + 0.5, gy - PEN_DEPTH + 2.0, gz + 0.5, PEN_CHAMBER, 30, t, tier));
                mgr.release(level, gx, gz, hold, now + 200);
            }
            case DAISY_CUTTER -> {
                Vec3 pos = new Vec3(gx + 0.5, gy, gz + 0.5);
                hurt(level, pos, base * t.crater, t.reach, t.lethal, t.dmg, t.burn, t.burnSec);
                Crater crater = new Crater(gx, gy, gz, base * t.crater, t.depth, t.scorch, 0.0, holdRadius(tier, t));
                mgr.craters.add(crater);
                DamageRings.queue(level, crater, t);
                MapFeed.hit(level, t.id, tier, rf, pos.x, gy, pos.z, base * t.crater, ShipStrike.shooter());
                ShipStrike.blast(level, pos, DAISY_RADIUS * 0.3, ShipStrike.powerFor(DAISY_RADIUS * 0.3), ShipStrike.shooter());
                mgr.tasks.add(task(SHOCK, pos.x, gy, pos.z, DAISY_RADIUS, DAISY_TICKS, t, tier));
                fragGlass(level, pos.add(0.0, 1.0, 0.0), DAISY_RADIUS);
                send(level, pos.x, gy, pos.z, tier, t, 0);
                mgr.release(level, gx, gz, hold, now + DAISY_TICKS + 200);
            }
            case EARTHQUAKE -> {
                mgr.tasks.add(task(QUAKE_BOMB, gx + 0.5, gy, gz + 0.5, QUAKE_RADIUS, QUAKE_TICKS, t, tier));
                mgr.release(level, gx, gz, hold, now + QUAKE_TICKS + 400);
            }
            case DAM_BREAKER -> {
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                for (int i = 0; i < 24; i++) level.sendParticles(ParticleTypes.SPLASH, gx + 0.5, gy + 2.0 + i * 1.3, gz + 0.5, 30, 1.6, 0.6, 1.6, 0.4);
                level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, gx + 0.5, gy, gz + 0.5, 200, 3.0, 2.0, 3.0, 0.3);
                level.sendParticles(ParticleTypes.CLOUD, gx + 0.5, gy + 30.0, gz + 0.5, 60, 4.0, 3.0, 4.0, 0.05);
                level.playSound(null, gx + 0.5, gy, gz + 0.5, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 16.0F, 0.5F);
            }
            case BREACHER -> {
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                shaft(level, gx, gy, gz, 2.0 + tier * 0.8, (int) Math.round(24 * tier * (rf ? 1.3 : 1.0)));
            }
            case CHAFF -> {
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                mgr.tasks.add(task(CHAFF, gx + 0.5, gy, gz + 0.5, CHAFF_RADIUS, rf ? 2700 : 1800, t, tier));
            }
            case BUNKER -> {
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                double depth = base * t.crater * t.depth * (rf ? 1.25 : 1.0);
                mgr.tasks.add(task(DEEP, gx + 0.5, gy - depth * 0.85, gz + 0.5, base * 0.8, 50, t, tier));
            }
            case EMP -> {
                Task k = task(EMP, gx + 0.5, gy, gz + 0.5, base * 4.0 * (rf ? 1.25 : 1.0), 800, t, tier);
                mgr.tasks.add(k);
                drain(level, k);
                send(level, gx + 0.5, gy, gz + 0.5, tier, t, 0);
                mgr.release(level, gx, gz, hold, now + 1000);
            }
            case ROD -> startRod(level, mgr, gx, gy, gz, tier, t, rf, hold, now);
            case HEMP -> {
                Task k = task(HEMP_FIELD, gx + 0.5, gy, gz + 0.5, HEMP_RADIUS * (rf ? 1.25 : 1.0), HEMP_TICKS, t, tier);
                mgr.tasks.add(k);
                hempDrain(level, k, 0);
                send(level, gx + 0.5, gy, gz + 0.5, tier, t, ST_HEMP);

                mgr.release(level, gx, gz, hold, now + 200);
            }
            case SEISMIC -> {
                mgr.tasks.add(task(SEISMIC_QUAKE, gx + 0.5, gy, gz + 0.5, SEISMIC_RADIUS * (rf ? 1.25 : 1.0), SEISMIC_TICKS, t, tier));
                mgr.release(level, gx, gz, hold, now + SEISMIC_TICKS + 200);
            }
            case THERMO, TSAR -> {
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                mgr.tasks.add(task(SHOCK, gx + 0.5, gy, gz + 0.5, SHOCK_RADIUS * (rf ? 1.2 : 1.0), SHOCK_TICKS, t, tier));
            }

            case NEUTRON -> {
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                neutron(level, new Vec3(gx + 0.5, gy + 1.0, gz + 0.5), base * Balance.NEUTRON_KILL * (rf ? 1.2 : 1.0));
                mgr.release(level, gx, gz, hold, now + 200);
            }
            case RAILGUN -> {
                flash(level, new Vec3(gx + 0.5, gy + 4.0, gz + 0.5), Balance.FLASH_RANGE * 1.5, false);
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                railgunShaft(level, gx, gy, gz);
                mgr.tasks.add(task(SHOCK, gx + 0.5, gy, gz + 0.5, SHOCK_RADIUS * 1.6, SHOCK_TICKS, t, tier));
            }
            case ORBITAL_ROD -> {
                flash(level, new Vec3(gx + 0.5, gy + 4.0, gz + 0.5), Balance.FLASH_RANGE * 1.8, false);
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                rodShaft(level, gx, gy, gz);
                mgr.tasks.add(task(SHOCK, gx + 0.5, gy, gz + 0.5, SHOCK_RADIUS * 1.8, SHOCK_TICKS, t, tier));
            }
            case ASTEROID -> {
                flash(level, new Vec3(gx + 0.5, gy + 6.0, gz + 0.5), Balance.FLASH_RANGE * (tier >= 4 ? 2.4 : 1.2 + 0.3 * tier), false);
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                mgr.tasks.add(task(SHOCK, gx + 0.5, gy, gz + 0.5, SHOCK_RADIUS * (tier >= 4 ? 2.2 : 0.6 * tier), SHOCK_TICKS, t, tier));
            }
            case ANTIMATTER -> {
                flash(level, new Vec3(gx + 0.5, gy + 4.0, gz + 0.5), Balance.FLASH_RANGE * (tier >= 4 ? 2.0 : tier == 3 ? 1.5 : 1.0));
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                if (tier >= 3) mgr.tasks.add(task(SHOCK, gx + 0.5, gy, gz + 0.5, SHOCK_RADIUS * (tier >= 4 ? 1.2 : 0.6), SHOCK_TICKS, t, tier));
            }
            case FLASH -> {
                flash(level, new Vec3(gx + 0.5, gy + 3.0, gz + 0.5), Balance.FLASH_RANGE * (rf ? 1.25 : 1.0));
                send(level, gx + 0.5, gy + 3.0, gz + 0.5, tier, t, 0);
                mgr.release(level, gx, gz, hold, now + 200);
            }
            case CONCUSSION -> {
                blast(level, mgr, gx, gy, gz, tier, t, rf);
                concussion(level, new Vec3(gx + 0.5, gy + 1.0, gz + 0.5), Balance.CONC_PUSH * (rf ? 1.2 : 1.0));
                fragGlass(level, new Vec3(gx + 0.5, gy + 1.0, gz + 0.5), Balance.CONC_GLASS);
            }
            case NANITE -> {
                if (!Config.Server.nanites()) { pop(level, gx, gy, gz, tier, t); mgr.release(level, gx, gz, hold, now + 200); }
                else {
                    mgr.tasks.add(task(NANITE, gx + 0.5, gy + 0.5, gz + 0.5, Balance.NANITE_R * (rf ? 1.25 : 1.0), Balance.NANITE_TICKS, t, tier));
                    send(level, gx + 0.5, gy, gz + 0.5, tier, t, 0);
                    mgr.release(level, gx, gz, hold, now + Balance.NANITE_TICKS + 200);
                    Wmd.LOGGER.info("[nanite] swarm released at {} {} {}", gx, gy, gz);
                }
            }
            case GRAVITY -> {
                Task k = task(GRAVITY_WELL, gx + 0.5, gy + 0.5, gz + 0.5, Balance.GRAV_R * (rf ? 1.2 : 1.0), Balance.GRAV_TICKS, t, tier);
                k.refined = rf;
                mgr.tasks.add(k);
                send(level, gx + 0.5, gy, gz + 0.5, tier, t, 0);
                mgr.release(level, gx, gz, hold, now + Balance.GRAV_TICKS + 300);
            }
            case CRYO -> {
                mgr.tasks.add(task(CRYO_FIELD, gx + 0.5, gy + 0.5, gz + 0.5, Balance.CRYO_R * (rf ? 1.25 : 1.0), Balance.CRYO_TICKS, t, tier));
                Aftermath.frost(level, gx + 0.5, gy, gz + 0.5, (float) (Balance.CRYO_R * (rf ? 1.25 : 1.0) * 0.4));
                send(level, gx + 0.5, gy, gz + 0.5, tier, t, 0);
                mgr.release(level, gx, gz, hold, now + Balance.CRYO_SPREAD + 200);
            }
            case CHEMICAL -> {
                mgr.tasks.add(task(GAS, gx + 0.5, gy + 1.0, gz + 0.5, Balance.GAS_RADIUS * (rf ? 1.25 : 1.0), rf ? Balance.GAS_TICKS * 3 / 2 : Balance.GAS_TICKS, t, tier));
                send(level, gx + 0.5, gy, gz + 0.5, tier, t, 0);
                mgr.release(level, gx, gz, hold, now + 200);
            }
            default -> blast(level, mgr, gx, gy, gz, tier, t, rf);
        }
    }

    private static void railgunShaft(ServerLevel level, int gx, int gy, int gz) {
        int r = Balance.RG_SHAFT_R, n = 0;
        int y0 = Math.max(level.getMinBuildHeight() + 1, gy - Balance.RG_SHAFT_DEPTH);

        boolean guard = Config.Server.protectSpawnRadius() > 0 || Config.Server.respectClaims();
        java.util.Set<net.minecraft.world.level.chunk.LevelChunk> touched = new java.util.HashSet<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = gy + 4; y >= y0; y--)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) continue;
                    p.set(gx + dx, y, gz + dz);
                    net.minecraft.world.level.chunk.LevelChunk ch = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
                    if (ch == null) continue;
                    BlockState s = ch.getBlockState(p);
                    if (s.isAir() || s.is(Blocks.BEDROCK) || s.getDestroySpeed(level, p) < 0 || s.hasBlockEntity()) continue;
                    if (guard && !Config.Server.canBreak(level, p)) continue;
                    ch.setBlockState(p, Blocks.AIR.defaultBlockState(), false);
                    touched.add(ch);
                    n++;
                }
        var light = level.getChunkSource().getLightEngine();
        for (net.minecraft.world.level.chunk.LevelChunk ch : touched) {
            ch.setUnsaved(true);
            var pkt = new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(ch, light, null, null);
            for (net.minecraft.server.level.ServerPlayer pl : level.getChunkSource().chunkMap.getPlayers(ch.getPos(), false)) pl.connection.send(pkt);
            TargetSnapshot.changed(level, ch.getPos().x, ch.getPos().z);
        }
        Wmd.LOGGER.info("[railgun] shaft at {} {} {}: {} blocks down to y {}", gx, gy, gz, n, y0);
    }

    private static void rodShaft(ServerLevel level, int gx, int gy, int gz) {
        int r = Balance.ROD_SHAFT_R, n = 0;
        int bottom = level.getMinBuildHeight() + 5;
        boolean guard = Config.Server.protectSpawnRadius() > 0 || Config.Server.respectClaims();
        net.minecraft.util.RandomSource rnd = level.random;
        java.util.Set<net.minecraft.world.level.chunk.LevelChunk> touched = new java.util.HashSet<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = gy + 6; y >= bottom; y--)
            for (int dx = -r - 1; dx <= r + 1; dx++)
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 > (r + 1) * (r + 1)) continue;
                    p.set(gx + dx, y, gz + dz);
                    net.minecraft.world.level.chunk.LevelChunk ch = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
                    if (ch == null) continue;
                    BlockState s = ch.getBlockState(p);
                    if (s.isAir() || s.is(Blocks.BEDROCK) || s.getDestroySpeed(level, p) < 0 || s.hasBlockEntity()) continue;
                    if (guard && !Config.Server.canBreak(level, p)) continue;
                    BlockState to;
                    if (d2 <= r * r) to = y <= bottom + 1 ? Blocks.LAVA.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    else if (y > gy - 4) continue;
                    else { float roll = rnd.nextFloat(); to = roll < 0.45F ? Blocks.TINTED_GLASS.defaultBlockState() : roll < 0.85F ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState(); }
                    ch.setBlockState(p, to, false);
                    touched.add(ch);
                    n++;
                }
        var light = level.getChunkSource().getLightEngine();
        for (net.minecraft.world.level.chunk.LevelChunk ch : touched) {
            ch.setUnsaved(true);
            var pkt = new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(ch, light, null, null);
            for (net.minecraft.server.level.ServerPlayer pl : level.getChunkSource().chunkMap.getPlayers(ch.getPos(), false)) pl.connection.send(pkt);
            TargetSnapshot.changed(level, ch.getPos().x, ch.getPos().z);
        }
        Wmd.LOGGER.info("[rod] shaft at {} {} {}: {} blocks down to y {}", gx, gy, gz, n, bottom);
    }

    private static void neutron(ServerLevel level, Vec3 pos, double r) {
        DamageSource src = level.damageSources().indirectMagic(null, shooterEntity(level));
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(r))) {
            if (e.position().distanceTo(pos) > r) continue;
            e.invulnerableTime = 0;
            if (e.hurt(src, 10_000.0F)) n++;
        }
        Wmd.LOGGER.info("Neutron warhead at {} {} {}: {} living things killed within {}", Math.round(pos.x), Math.round(pos.y), Math.round(pos.z), n, Math.round(r));
    }

    private static void flash(ServerLevel level, Vec3 pos, double r) { flash(level, pos, r, true); }

    private static void flash(ServerLevel level, Vec3 pos, double r, boolean blind) {
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            Vec3 eye = p.getEyePosition();

            double dist = eye.distanceTo(pos);
            if (dist > r || p.isSpectator()) continue;
            double facing = p.getViewVector(1.0F).dot(pos.subtract(eye).normalize());
            if (facing < 0.0) { Wmd.LOGGER.info("[flash] {} at {} blocks: looking away", p.getGameProfile().getName(), Math.round(dist)); continue; }

            double seen = 0.0;
            for (double up : new double[] { 0.0, 9.0, 27.0 }) {
                Vec3 at = pos.add(0.0, up, 0.0);
                net.minecraft.world.phys.BlockHitResult h = level.clip(new net.minecraft.world.level.ClipContext(at, eye, net.minecraft.world.level.ClipContext.Block.VISUAL,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
                if (h.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) { seen = up == 0.0 ? 1.0 : up == 9.0 ? 0.85 : 0.65; break; }
            }
            Wmd.LOGGER.info("[flash] {} at {} blocks, facing {}, sight {}", p.getGameProfile().getName(), Math.round(dist), String.format("%.2f", facing), seen);
            if (seen <= 0.0) continue;
            if (blind) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, Balance.FLASH_TICKS, 0));
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, Balance.FLASH_TICKS / 4, 1));
            }

            float s = (float) Math.max(0.45, Math.min(1.0, (1.0 - dist / r * 0.6) * (0.55 + 0.45 * facing) * seen));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new FlashBlindPayload(s, (int) Math.round(Balance.FLASH_WHITE_TICKS * (0.5 + 0.5 * s))));
        }
    }

    private static void concussion(ServerLevel level, Vec3 pos, double r) {
        for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos, pos).inflate(r), x -> x.isAlive() && !x.isSpectator())) {
            double d = e.position().distanceTo(pos);
            if (d > r) continue;
            double k = 1.0 - d / r;
            Vec3 away = e.position().subtract(pos);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(0, 1, 0);
            away = away.normalize().scale(Balance.CONC_THROW * k);
            e.push(away.x, 0.5 + 1.2 * k, away.z);
            e.hurtMarked = true;
            if (e instanceof net.minecraft.server.level.ServerPlayer p && !p.isCreative())
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, Balance.CONC_NAUSEA_TICKS, 0));
        }
    }

    static Vec3 wind(ServerLevel level) {
        long day = level.getDayTime() / 24_000L;
        long h = (day * 0x9E3779B97F4A7C15L) ^ level.getSeed();
        h ^= h >>> 31;
        double a = (h & 0xFFFF) / 65536.0 * Math.PI * 2.0;
        return new Vec3(Math.cos(a), 0.0, Math.sin(a));
    }

    private static void far(ServerLevel level, net.minecraft.core.particles.ParticleOptions o, double x, double y, double z, int n, double sx, double sy, double sz, double speed) {
        for (net.minecraft.server.level.ServerPlayer p : level.players())
            if (p.distanceToSqr(x, y, z) < 256.0 * 256.0) level.sendParticles(p, o, true, x, y, z, n, sx, sy, sz, speed);
    }

    private static final java.util.Map<Integer, NanoBite> BITES = new java.util.HashMap<>();

    private static final class NanoBite {
        final java.util.ArrayDeque<long[]> queue = new java.util.ArrayDeque<>();
        final java.util.Set<Long> set = new java.util.HashSet<>();
    }

    static void clearBites() { BITES.clear(); }

    private static void fx(ServerLevel level, ExoticFxPayload p) {
        PacketDistributor.sendToPlayersNear(level, null, p.x(), p.y(), p.z(), Balance.EXOTIC_FX_RANGE, p);
    }

    private static void naniteStep(ServerLevel level, FlightManager mgr, Task k, int age) {
        int id = k.fxId();
        for (Task e : mgr.tasks) {
            if (e.kind != EMP && e.kind != HEMP_FIELD) continue;
            double dx = e.x - k.x, dz = e.z - k.z;
            if (dx * dx + dz * dz <= (e.r + k.r) * (e.r + k.r)) {
                Wmd.LOGGER.info("[nanite] swarm at {} {} {} killed by an EMP after {} s", Math.round(k.x), Math.round(k.y), Math.round(k.z), age / 20);
                level.playSound(null, k.x, k.y, k.z, WmdSounds.NA_DIE.get(), SoundSource.BLOCKS, 8.0F, 1.0F);
                fx(level, new ExoticFxPayload(id, ExoticFxPayload.NANITE_END, (float) k.x, (float) k.y, (float) k.z, (float) k.r, -1, k.total, new long[0]));
                BITES.remove(id);
                k.ticks = 1;
                return;
            }
        }
        double r = Math.max(3.0, k.r * Math.min(1.0, age / (k.total * 0.75)));
        var rnd = level.random;
        long now = level.getGameTime();
        NanoBite nb = BITES.computeIfAbsent(id, i -> new NanoBite());

        while (!nb.queue.isEmpty() && nb.queue.peek()[1] <= now) {
            long[] q = nb.queue.poll();
            nb.set.remove(q[0]);
            BlockPos bp = BlockPos.of(q[0]);
            if (!level.isLoaded(bp)) continue;
            BlockState s = level.getBlockState(bp);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.getDestroySpeed(level, bp) < 0.0F || !Config.Server.naniteMayEat(level, bp)) continue;
            if (DeadHandBlockEntity.isBunker(level, bp, s)) continue;
            level.setBlock(bp, Blocks.AIR.defaultBlockState(), 2 | 16);
        }

        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long[] picked = new long[Balance.NANITE_EAT];
        int eaten = 0;
        for (int i = 0; i < Balance.NANITE_TRIES && eaten < Balance.NANITE_EAT && k.ticks > Balance.NANITE_DISSOLVE; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0, b = Math.acos(2.0 * rnd.nextDouble() - 1.0), d = r * Math.cbrt(rnd.nextDouble());
            p.set(k.x + Math.sin(b) * Math.cos(a) * d, k.y + Math.cos(b) * d, k.z + Math.sin(b) * Math.sin(a) * d);
            if (!level.isLoaded(p) || nb.set.contains(p.asLong())) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.getDestroySpeed(level, p) < 0.0F || !Config.Server.naniteMayEat(level, p)) continue;
            nb.queue.add(new long[] { p.asLong(), now + Balance.NANITE_DISSOLVE });
            nb.set.add(p.asLong());
            picked[eaten++] = p.asLong();
        }
        if (eaten > 0 || age % 10 == 1)
            fx(level, new ExoticFxPayload(id, ExoticFxPayload.NANITE_STATE, (float) k.x, (float) k.y, (float) k.z, (float) r, k.ticks, k.total, java.util.Arrays.copyOf(picked, eaten)));
        if (k.ticks <= 1) {
            fx(level, new ExoticFxPayload(id, ExoticFxPayload.NANITE_END, (float) k.x, (float) k.y, (float) k.z, (float) r, 0, k.total, new long[0]));
            BITES.remove(id);
        }
        if (age % 100 == 0) level.playSound(null, k.x, k.y, k.z, WmdSounds.NA_SWARM_LOOP.get(), SoundSource.BLOCKS, (float) (2.0 + r * 0.25), 1.0F);
        if (age % 40 == 0) {
            DamageSource src = level.damageSources().indirectMagic(null, shooterEntity(level));
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(k.x - r, k.y - r, k.z - r, k.x + r, k.y + r, k.z + r)))
                if (e.isAlive() && e.distanceToSqr(k.x, k.y, k.z) <= r * r && !(e instanceof net.minecraft.world.entity.player.Player pl && (pl.isCreative() || pl.isSpectator())))
                    e.hurt(src, Balance.NANITE_DMG);
        }
    }

    private static boolean loose(ServerLevel level, BlockPos p, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.getDestroySpeed(level, p) < 0.0F) return false;
        return s.getDestroySpeed(level, p) <= 2.0F && !s.is(Blocks.BEDROCK);
    }

    private static void gravityStep(ServerLevel level, FlightManager mgr, Task k, int age) {
        Vec3 c = new Vec3(k.x, k.y + Balance.GRAV_LIFT_H, k.z);
        double r = k.r;
        if (k.ticks > 1) {
            for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(c, c).inflate(r))) {
                if (e instanceof net.minecraft.world.entity.player.Player pl && pl.isSpectator()) continue;
                Vec3 d = c.subtract(e.position());
                double dist = d.length();
                if (dist > r || dist < 0.5 || Forcefield.shelters(level, e.position(), c)) continue;
                Vec3 v = e.getDeltaMovement().add(d.scale(Balance.GRAV_PULL * (1.0 - 0.5 * dist / r) / dist));
                if (v.length() > Balance.GRAV_MAX_V) v = v.normalize().scale(Balance.GRAV_MAX_V);
                if (dist < 2.0) v = v.scale(0.4);
                e.setDeltaMovement(v);
                e.hurtMarked = true;
                e.fallDistance = 0.0F;
            }
            if (age % 4 == 0 && age < Balance.GRAV_TICKS - 20) {
                var rnd = level.random;
                int lift = Math.max(1, Balance.GRAV_BLOCKS * 4 / Balance.GRAV_TICKS);
                for (int i = 0; i < lift * 3 && lift > 0; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2.0, d = r * 0.6 * Math.sqrt(rnd.nextDouble());
                    int x = (int) Math.floor(k.x + Math.cos(a) * d), z = (int) Math.floor(k.z + Math.sin(a) * d);
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    BlockPos top = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
                    BlockState s = level.getBlockState(top);
                    if (!level.isLoaded(top) || !loose(level, top, s) || !Config.Server.canBreak(level, top)) continue;
                    net.minecraft.world.entity.item.FallingBlockEntity fb = net.minecraft.world.entity.item.FallingBlockEntity.fall(level, top, s);
                    fb.setDeltaMovement(0.0, 0.6, 0.0);
                    fb.dropItem = false;
                    lift--;
                }
            }
            if (age % 10 == 1)
                fx(level, new ExoticFxPayload(k.fxId(), ExoticFxPayload.GRAV_STATE, (float) c.x, (float) c.y, (float) c.z, (float) r, k.ticks, k.total, new long[0]));
            if (age % 120 == 0) level.playSound(null, c.x, c.y, c.z, WmdSounds.GR_PULL_LOOP.get(), SoundSource.BLOCKS, 10.0F, 1.0F);
            return;
        }

        fx(level, new ExoticFxPayload(k.fxId(), ExoticFxPayload.GRAV_THROW, (float) c.x, (float) c.y, (float) c.z, (float) r, 0, k.total, new long[0]));
        for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(c, c).inflate(r * 1.5))) {
            if (e instanceof net.minecraft.world.entity.player.Player pl && pl.isSpectator()) continue;
            if (Forcefield.shelters(level, e.position(), c)) continue;
            Vec3 d = e.position().subtract(c);
            Vec3 dir = d.lengthSqr() < 0.01 ? new Vec3(level.random.nextGaussian(), 0.5, level.random.nextGaussian()).normalize() : d.normalize();
            e.setDeltaMovement(dir.scale(Balance.GRAV_THROW * (0.8 + 0.4 * level.random.nextDouble())).add(0.0, Balance.GRAV_THROW_UP, 0.0));
            e.hurtMarked = true;
            if (e instanceof LivingEntity le) le.hurt(level.damageSources().explosion(null, shooterEntity(level)), Balance.GRAV_THROW_DMG);
        }
        level.playSound(null, c.x, c.y, c.z, WmdSounds.GR_THROW.get(), SoundSource.BLOCKS, 16.0F, 1.0F);
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            double dd = p.position().distanceTo(c);
            if (dd > 200.0 && dd < 1_000.0) p.playNotifySound(WmdSounds.GR_THROW_FAR.get(), SoundSource.BLOCKS, (float) Math.max(0.3, 1.0 - dd / 1_000.0), 1.0F);
        }
        far(level, ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        far(level, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 6, 3.0, 2.0, 3.0, 0);
        far(level, ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 400, r * 0.3, r * 0.2, r * 0.3, 2.5);
        blast(level, mgr, (int) Math.floor(k.x), (int) Math.floor(k.y), (int) Math.floor(k.z), k.tier, WarheadType.GRAVITY, k.refined);
        Wmd.LOGGER.info("[gravity] well at {} {} {} throws", Math.round(k.x), Math.round(k.y), Math.round(k.z));
    }

    private static void cryoStep(ServerLevel level, Task k, int age) {
        double r = k.r;
        if (age <= Balance.CRYO_SPREAD) {
            double r0 = r * (age - 1) / Balance.CRYO_SPREAD, r1 = r * age / Balance.CRYO_SPREAD;
            int ir = (int) Math.ceil(r1);
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = -ir; dx <= ir; dx++) for (int dz = -ir; dz <= ir; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < r0 || d >= r1) continue;
                int x = (int) Math.floor(k.x) + dx, z = (int) Math.floor(k.z) + dz;
                if (!level.isLoaded(p.set(x, (int) k.y, z))) continue;
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                p.set(x, top, z);
                if (!Config.Server.canBreak(level, p)) continue;
                BlockState s = level.getBlockState(p);
                if (s.is(Blocks.WATER)) level.setBlock(p, Blocks.ICE.defaultBlockState(), 2);
                else if (s.is(Blocks.LAVA)) level.setBlock(p, s.getFluidState().isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), 2);
                else if (s.is(net.minecraft.tags.BlockTags.FIRE)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                else if (level.random.nextFloat() < 0.75F) {
                    BlockPos up = p.above();
                    BlockState snow = Blocks.SNOW.defaultBlockState();
                    if (level.getBlockState(up).isAir() && snow.canSurvive(level, up)) level.setBlock(up, snow, 2);
                }
            }
            if (age % 2 == 0) {
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2.0 * i / 24 + age * 0.1;
                    far(level, ParticleTypes.SNOWFLAKE, k.x + Math.cos(a) * r1, k.y + 1.0, k.z + Math.sin(a) * r1, 3, 0.6, 0.6, 0.6, 0.05);
                }
            }
        }
        if (age % 10 == 0) far(level, ParticleTypes.SNOWFLAKE, k.x, k.y + 3, k.z, (int) (r * 0.8), r * 0.6, 3.0, r * 0.6, 0.01);
        if (age % 100 == 50) level.playSound(null, k.x, k.y + 2, k.z, WmdSounds.CR_FROST_LOOP.get(), SoundSource.BLOCKS, 5.0F, 1.0F);
        if (k.ticks == 60) level.playSound(null, k.x, k.y + 2, k.z, WmdSounds.CR_THAW.get(), SoundSource.BLOCKS, 6.0F, 1.0F);
        if (age % 10 != 0) return;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(k.x - r, k.y - 8, k.z - r, k.x + r, k.y + 24, k.z + r))) {
            if (!e.isAlive() || Math.hypot(e.getX() - k.x, e.getZ() - k.z) > r) continue;
            if (e instanceof net.minecraft.world.entity.player.Player pl && (pl.isCreative() || pl.isSpectator())) continue;
            if (e.isOnFire() || EmpField.heatNear(level, e.blockPosition())) continue;
            e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + 60));
            e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, Balance.CRYO_SLOW));
        }
    }

    private static void gasStep(ServerLevel level, Task k, int age) {
        Vec3 w = wind(level);
        k.x += w.x * Balance.GAS_WIND;
        k.z += w.z * Balance.GAS_WIND;
        if (age % 20 == 0) {
            BlockPos c = BlockPos.containing(k.x, 0.0, k.z);
            if (level.isLoaded(c)) k.y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()) + 1.0;
        }
        double fade = Math.min(1.0, k.ticks / 400.0);
        if (age % 79 == 0) level.playSound(null, k.x, k.y + 2.0, k.z, WmdSounds.GAS_CLOUD_LOOP.get(), SoundSource.BLOCKS, (float) (6.0 * fade), 1.0F);
        double r = k.r * (0.7 + 0.3 * Math.min(1.0, age / 200.0));
        if (age % 10 == 0) {

            GasCloudPayload gp = new GasCloudPayload(k.fxId(), (float) k.x, (float) k.y, (float) k.z, (float) r, (float) fade, (float) w.x, (float) w.z);
            for (net.minecraft.server.level.ServerPlayer p : level.players())
                if (Math.hypot(p.getX() - k.x, p.getZ() - k.z) < 240.0) PacketDistributor.sendToPlayer(p, gp);
        }
        if (age % 20 != 0) return;
        DamageSource src = level.damageSources().indirectMagic(null, shooterEntity(level));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(k.x - r, k.y - 4, k.z - r, k.x + r, k.y + 12, k.z + r))) {
            if (Math.hypot(e.getX() - k.x, e.getZ() - k.z) > r || !e.isAlive()) continue;
            if (GasMaskItem.protects(e)) { GasMaskItem.breathe(e); continue; }
            e.hurt(src, (float) (Balance.GAS_DAMAGE * Math.max(0.3, fade)));
            if (e instanceof net.minecraft.server.level.ServerPlayer p) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 120, 0));
        }
    }

    private static void send(ServerLevel level, double x, double y, double z, int tier, WarheadType t, int stage) {
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, 8000, new ImpactPayload(x, y, z, tier, t.ordinal(), stage));
    }

    private static void blast(ServerLevel level, FlightManager mgr, int gx, int gy, int gz, int tier, WarheadType t, boolean rf) {
        double base = Missiles.craterRadius(tier);

        double cm = !rf ? 1.0 : (t == WarheadType.HE || t == WarheadType.NUKE || t == WarheadType.THERMO) ? 1.2 : t == WarheadType.THERMOBARIC ? 1.25 : 1.0;
        double rm = !rf ? 1.0 : t == WarheadType.FRAG ? 1.3 : (t == WarheadType.NUKE || t == WarheadType.THERMO) ? 1.2 : 1.0;
        double dm = rf && t == WarheadType.BUNKER ? 1.25 : 1.0;
        double r = base * t.crater * cm;
        Vec3 pos = new Vec3(gx + 0.5, gy, gz + 0.5);
        int wl = Integer.MIN_VALUE;
        if (r > 0.0) {
            hurt(level, pos, r, t.reach * rm, t.lethal, t.dmg, t.burn, t.burnSec);
            burnItems(level, pos, r * Balance.BLAST_ITEM_CLEAR);
            if (t != WarheadType.PLASMA) FusionReactorBlockEntity.blastAt(level, pos, r * Math.max(1.0, t.reach * 0.5));
            AntimatterStoreBlockEntity.blastAt(level, pos, r * Math.max(1.0, t.reach * 0.5));
            RailgunBlockEntity.blastAt(level, pos, r * Math.max(1.0, t.reach * 0.5));
            Crater crater = new Crater(gx, gy, gz, r, t.depth * dm, t.scorch, outer(t), holdRadius(tier, t));
            wl = waterLevel(level, gx, gz, r);
            ImpactProfiler.watch(t.id, t == WarheadType.ASTEROID ? Math.max(r, 60.0) : r);
            if (r >= Balance.FAST_CRATER_R || t == WarheadType.RAILGUN || t == WarheadType.ORBITAL_ROD || t == WarheadType.ASTEROID) crater.fast();
            if (t == WarheadType.ORBITAL_ROD) crater.glass();
            if (t == WarheadType.ASTEROID) crater.peak();
            if (t == WarheadType.BUNKER) crater.buster();
            mgr.craters.add(crater);
            DamageRings.queue(level, crater, t);
            MapFeed.hit(level, t.id, tier, rf, gx + 0.5, gy, gz + 0.5, r, ShipStrike.shooter());

            Debris.blast(level, gx, gy, gz, r, crater, pieceShare(t, tier), true);
            ShipStrike.blast(level, pos, r, ShipStrike.powerFor(r), ShipStrike.shooter());
        }
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, 8000, new ImpactPayload(pos.x, pos.y, pos.z, tier, t.ordinal(), 0, 0.0F,
                wl != Integer.MIN_VALUE ? ImpactPayload.WET : 0));
        if (hasWave(t, tier) && r > 0.0) {
            double reach = waveReach(t, tier, r);
            mgr.tasks.add(task(BLAST_WAVE, pos.x, pos.y, pos.z, reach, waveTicks(t, reach), t, tier));
            if (farWave(t, tier) && Config.shockwaveBreaksGlass()) mgr.tasks.add(task(GLASS_SHOCK, pos.x, pos.y, pos.z, reach, waveTicks(t, reach), t, tier));
        }
    }

    static int waterLevel(ServerLevel level, int gx, int gz, double r) {
        java.util.Map<Integer, Integer> tops = new java.util.HashMap<>();
        int n = 0, wet = 0;
        boolean centre = false;
        for (int i = 0; i < 17; i++) {
            double a = i * 2.39996, d = i == 0 ? 0.0 : r * 0.8 * Math.sqrt(i / 16.0);
            int x = gx + (int) Math.round(Math.cos(a) * d), z = gz + (int) Math.round(Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            n++;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (!level.getFluidState(new BlockPos(x, top, z)).is(net.minecraft.tags.FluidTags.WATER)) continue;
            wet++;
            tops.merge(top, 1, Integer::sum);
            if (i == 0) centre = true;
        }
        if (n == 0 || !(centre || wet >= n * Balance.CRATER_WET_SHARE)) return Integer.MIN_VALUE;
        return tops.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(Integer.MIN_VALUE);
    }

    static void burnItems(ServerLevel level, Vec3 pos, double r) {
        if (r < 4.0) return;
        for (net.minecraft.world.entity.item.ItemEntity it : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos, pos).inflate(r, r * 0.6, r)))
            if (it.distanceToSqr(pos) <= r * r) it.discard();
    }

    public static boolean airburstable(WarheadType t) { return t == WarheadType.NUKE || t == WarheadType.THERMO; }

    public static void airburst(ServerLevel level, FlightManager mgr, int gx, int gy, int gz, int tier, WarheadType t, boolean rf) {
        airburst(level, mgr, gx, gy, gz, tier, t, rf, -1.0);
    }

    public static void airburst(ServerLevel level, FlightManager mgr, int gx, int gy, int gz, int tier, WarheadType t, boolean rf, double hAt) {
        if (Forcefield.absorbs(level, gx + 0.5, gy, gz + 0.5, tier, t)) { mgr.release(level, gx, gz, holdRadius(tier, t), level.getGameTime() + 100); return; }
        NuclearWinter.blast(level, t);
        double base = Missiles.craterRadius(tier);
        double r = base * t.crater * (rf ? 1.2 : 1.0), h = hAt > 0.0 ? hAt : r * Balance.AIRBURST_H;
        Vec3 ground = new Vec3(gx + 0.5, gy, gz + 0.5), air = ground.add(0.0, h, 0.0);
        Planet.flash(level, ground.x, ground.z, tier);
        StationLife.blast(level, air.x, air.y, air.z, r);
        flash(level, air, Balance.FLASH_RANGE * 1.6, t != WarheadType.TSAR);
        hurt(level, ground, r, t.reach * Balance.AIRBURST_REACH * (rf ? 1.2 : 1.0), t.lethal * Balance.AIRBURST_LETHAL, t.dmg, t.burn * Balance.AIRBURST_SCORCH, t.burnSec);
        burnItems(level, ground, r * Balance.BLAST_ITEM_CLEAR);
        FusionReactorBlockEntity.blastAt(level, ground, r * Math.max(1.0, t.reach * 0.5));
        AntimatterStoreBlockEntity.blastAt(level, ground, r * Math.max(1.0, t.reach * 0.5));
        RailgunBlockEntity.blastAt(level, ground, r * Math.max(1.0, t.reach * 0.5));
        Crater crater = new Crater(gx, gy, gz, r * 1.1, t.depth * Balance.AIRBURST_DEPTH, t.scorch * Balance.AIRBURST_SCORCH, Balance.AIRBURST_OUTER, holdRadius(tier, t));
        if (t == WarheadType.TSAR || r >= Balance.FAST_CRATER_R) { crater.fast(); ImpactProfiler.watch(t.id, r); }
        mgr.craters.add(crater);
        DamageRings.queue(level, crater, t);
        MapFeed.hit(level, t.id, tier, rf, ground.x, gy, ground.z, r, ShipStrike.shooter());
        ShipStrike.blast(level, air, r, ShipStrike.powerFor(r), ShipStrike.shooter());
        PacketDistributor.sendToPlayersNear(level, null, ground.x, gy, ground.z, 8000, new ImpactPayload(ground.x, gy, ground.z, tier, t.ordinal(), 0, (float) h));
        double reach = waveReach(t, tier, r) * Balance.AIRBURST_WAVE;
        mgr.tasks.add(task(BLAST_WAVE, ground.x, gy, ground.z, reach, waveTicks(t, reach), t, tier));
        if (Config.shockwaveBreaksGlass()) mgr.tasks.add(task(GLASS_SHOCK, ground.x, gy, ground.z, reach, waveTicks(t, reach), t, tier));
        if (t == WarheadType.THERMO || t == WarheadType.TSAR) mgr.tasks.add(task(SHOCK, ground.x, gy, ground.z, SHOCK_RADIUS * (rf ? 1.2 : 1.0) * Balance.AIRBURST_WAVE * (t == WarheadType.TSAR ? 3.0 : 1.0), SHOCK_TICKS, t, tier));
        MusicTriggersHook.fire(level, "wmd:airburst", BlockPos.containing(ground));
        Wmd.LOGGER.info("[blast] {} AIRBURST {} blocks over {} {} {}", t.id, (int) h, gx, gy, gz);
    }

    private static void pop(ServerLevel level, int gx, int gy, int gz, int tier, WarheadType t) {
        Vec3 pos = new Vec3(gx + 0.5, gy, gz + 0.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(4.0))) {
            if (e.position().distanceTo(pos) < 4.0) e.hurt(level.damageSources().explosion(null, null), 6.0F);
        }
        level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 1.0, pos.z, 40, 1.5, 1.0, 1.5, 0.1);
        send(level, pos.x, pos.y, pos.z, tier, t, 0);
    }

    static void hurt(ServerLevel level, Vec3 pos, double radius, double reachMul, double lethalMul, double dmgMul, double burnMul, int burnSec, java.util.UUID shooter) {
        java.util.UUID prev = ShipStrike.begin(shooter);
        try { hurt(level, pos, radius, reachMul, lethalMul, dmgMul, burnMul, burnSec); }
        finally { ShipStrike.end(prev); }
    }

    private static net.minecraft.world.entity.Entity shooterEntity(ServerLevel level) {
        java.util.UUID id = ShipStrike.shooter();
        return id == null || level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(id);
    }

    static void hurt(ServerLevel level, Vec3 pos, double radius, double reachMul, double lethalMul, double dmgMul, double burnMul, int burnSec) {
        Camo.blast(level, pos, radius * Math.max(1.0, reachMul));
        DamageSource blast = level.damageSources().explosion(null, shooterEntity(level));
        double reach = radius * reachMul, lethal = radius * lethalMul;
        if (reach <= 0.0) return;
        ShockWave.start(level, pos, reach);
        Debris.blastHit(level, pos, radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(reach))) {
            double d = e.position().distanceTo(pos);
            if (d > reach) continue;
            int layers = Hardened.layers(level, pos.add(0.0, 1.0, 0.0), e.position().add(0.0, e.getBbHeight() * 0.5, 0.0));
            double shield = Hardened.shield(layers);
            if (d < lethal && layers <= 0) { e.hurt(blast, 10_000.0F); continue; }
            double k = d < lethal ? 1.0 : 1.0 - (d - lethal) / Math.max(1.0E-6, reach - lethal);
            e.hurt(blast, (float) ((60.0 * k * k + 4.0) * dmgMul * shield));
            if (burnMul > 0.0 && d < radius * burnMul && layers <= 0) e.igniteForSeconds(burnSec);
            Vec3 away = e.position().subtract(pos);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(0, 1, 0);
            away = away.normalize().scale(2.5 * k * shield);
            e.push(away.x, 0.4 + 0.6 * k, away.z);
            e.hurtMarked = true;
        }
    }

    private static void shaft(ServerLevel level, int gx, int gy, int gz, double r, int depth) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int ri = (int) Math.ceil(r);
        int yEnd = Math.max(level.getMinBuildHeight() + 1, gy - depth);
        for (int y = gy; y >= yEnd; y--) {
            for (int x = -ri; x <= ri; x++) for (int z = -ri; z <= ri; z++) {
                if (x * x + z * z > r * r) continue;
                p.set(gx + x, y, gz + z);
                if (!level.hasChunkAt(p)) continue;
                BlockState s = level.getBlockState(p);
                if (s.isAir() || s.getDestroySpeed(level, p) < 0 || Hardened.rating(level, p, s) >= 2) continue;
                if (Config.Server.canBreak(level, p)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        level.sendParticles(ParticleTypes.POOF, gx + 0.5, gy + 1.0, gz + 0.5, 60, r, 2.0, r, 0.3);
        level.playSound(null, gx + 0.5, gy, gz + 0.5, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 6.0F, 1.4F);
    }

    static void tick(ServerLevel level, FlightManager mgr, long now) {
        if (mgr.tasks.isEmpty()) return;
        for (Iterator<Task> it = new ArrayList<>(mgr.tasks).iterator(); it.hasNext(); ) {
            Task k = it.next();
            int age = k.total - k.ticks;
            long tt0 = ImpactProfiler.start();
            java.util.UUID taskShooter = ShipStrike.begin(k.owner);
            try {
            switch (k.kind) {
                case IGNITE -> igniteStep(level, k, age);
                case FUEL -> {
                    if (age % 4 == 0) level.sendParticles(ParticleTypes.CLOUD, k.x, k.y + k.r * 0.35, k.z, 12, k.r * 0.45, k.r * 0.25, k.r * 0.45, 0.01);
                    if (k.ticks <= FUEL_TICKS - SUCK_START) suck(level, k);
                    if (k.ticks == 1) {
                        level.playSound(null, k.x, k.y, k.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 12.0F, 0.5F);
                        java.util.UUID prev = ShipStrike.begin(k.owner);
                        try { blast(level, mgr, (int) Math.floor(k.x), (int) Math.floor(k.y), (int) Math.floor(k.z), k.tier, WarheadType.ofOrdinal(k.type), k.refined); }
                        finally { ShipStrike.end(prev); }
                    }
                }
                case BOMBLETS -> {
                    if (age >= CLUSTER_RAIN) for (int i = 0; i < 2; i++) bomblet(level, mgr, k);
                }
                case DEEP -> {
                    if (k.ticks == 1) deepBlast(level, mgr, k);
                }
                case EMP -> {
                    if (age % 10 == 0) drain(level, k);
                    empFx(level, k, age);
                }
                case SHOCK -> shockStep(level, k, age);
                case ROD_FALL -> { if (k.ticks == 1) rodImpact(level, mgr, k); }
                case AFTER -> aftershock(level, k, age);
                case FISSURE -> fissureStep(level, k, age);
                case HEMP_FIELD -> {
                    if (age < HEMP_WAVES_END && age % 12 == 0) hempArcs(level, k, age);
                    if (age % 5 == 0) {
                        hempDrain(level, k, age);
                        mgr.shots.removeIf(sh -> { double dx = sh.from().x - k.x, dz = sh.from().z - k.z; return dx * dx + dz * dz <= k.r * k.r; });
                        hempKillFlights(level, mgr, k);
                    }
                }
                case SEISMIC_QUAKE -> seismicStep(level, mgr, k, age);
                case WRECK -> wreckStep(level, mgr, k, age);
                case BLAST_WAVE -> waveStep(level, k, age);
                case GLASS_SHOCK -> glassStep(level, k, age);
                case NAPALM_WAVE -> napalmStep(level, k, age);
                case QUAKE_BOMB -> quakeBombStep(level, mgr, k, age);
                case GAS -> gasStep(level, k, age);
                case NANITE -> naniteStep(level, mgr, k, age);
                case GRAVITY_WELL -> gravityStep(level, mgr, k, age);
                case CRYO_FIELD -> cryoStep(level, k, age);
                case CHAFF -> {
                    if (age % 40 == 0) {
                        for (int i = 0; i < 2; i++) {
                            double a = level.random.nextDouble() * Math.PI * 2, d = k.r * 0.8 * Math.sqrt(level.random.nextDouble());
                            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, k.x + Math.cos(a) * d, k.y + 1 + level.random.nextDouble() * 3, k.z + Math.sin(a) * d, 2, 3.0, 1.0, 3.0, 0.005);
                        }
                    }
                }
                default -> { }
            }
            } finally { ShipStrike.end(taskShooter); ImpactProfiler.task(k.kind, tt0); }
            if (--k.ticks <= 0) mgr.tasks.remove(k);
        }
        if (now % 100 == 0) mgr.setDirty();
    }

    private static void igniteStep(ServerLevel level, Task k, int age) {
        double grow = Math.min(1.0, 0.25 + 1.2 * age / (double) k.total);
        for (int i = 0; i < 8; i++) {
            double a = level.random.nextDouble() * Math.PI * 2, d = k.r * grow * Math.sqrt(level.random.nextDouble());
            int bx = (int) Math.floor(k.x + Math.cos(a) * d), bz = (int) Math.floor(k.z + Math.sin(a) * d);
            BlockPos probe = new BlockPos(bx, level.getMinBuildHeight() + 1, bz);
            if (!level.hasChunkAt(probe)) continue;
            int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            BlockPos pos = new BlockPos(bx, by, bz);
            if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, Direction.UP) && Config.Server.canBreak(level, pos)) level.setBlock(pos, BaseFireBlock.getState(level, pos), Block.UPDATE_ALL);
        }
        if (age % 20 == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(k.x - k.r, k.y - 8, k.z - k.r, k.x + k.r, k.y + 24, k.z + k.r))) {
                double dx = e.getX() - k.x, dz = e.getZ() - k.z;
                if (dx * dx + dz * dz < k.r * k.r * grow * grow) e.igniteForSeconds(6.0F);
            }
        }
    }

    private static void bomblet(ServerLevel level, FlightManager mgr, Task k) {
        double base = Missiles.craterRadius(k.tier);
        double length = base * 3.0, width = base * 0.9;
        double u = (level.random.nextDouble() - 0.5) * length, v = (level.random.nextDouble() - 0.5) * width;
        int bx = (int) Math.floor(k.x + k.dx * u - k.dz * v), bz = (int) Math.floor(k.z + k.dz * u + k.dx * v);
        BlockPos probe = new BlockPos(bx, level.getMinBuildHeight() + 1, bz);
        if (!level.hasChunkAt(probe)) return;
        int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        Vec3 pos = new Vec3(bx + 0.5, by, bz + 0.5);
        if (k.type == WarheadType.CARPET.ordinal() && level.random.nextDouble() < Balance.CARPET_DUD) {
            BombEntity.dud(level, pos, k.owner);
            return;
        }
        hurt(level, pos, k.r, 3.0, 0.7, 0.6, 1.2, 4);
        Crater crater = new Crater(bx, by, bz, k.r, 0.55, 0.6, 0.0, k.r * 1.5 + 16.0);
        mgr.craters.add(crater);
        DamageRings.queue(level, crater, WarheadType.values()[k.type]);
        Debris.blast(level, bx, by, bz, k.r, crater, Balance.DEBRIS_BOMBLET_SHARE, false);
        ShipStrike.blast(level, pos, k.r, ShipStrike.powerFor(k.r), k.owner);

        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, 1500, new ImpactPayload(pos.x, pos.y, pos.z, k.tier, WarheadType.CLUSTER.ordinal(), ST_BOMBLET));

        if (level.random.nextInt(7) == 0) {
            BlockPos fp = BlockPos.containing(pos.x + level.random.nextInt(5) - 2, pos.y, pos.z + level.random.nextInt(5) - 2);
            if (!level.hasChunk(fp.getX() >> 4, fp.getZ() >> 4)) return;
            fp = new BlockPos(fp.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fp.getX(), fp.getZ()), fp.getZ());
            if (level.getBlockState(fp).isAir() && BaseFireBlock.canBePlacedAt(level, fp, Direction.UP) && Config.Server.canBreak(level, fp)) level.setBlock(fp, BaseFireBlock.getState(level, fp), Block.UPDATE_ALL);
        }
    }

    public static final double NAPALM_LENGTH = 40.0, NAPALM_WIDTH = 8.0, PEN_CHAMBER = 6.0, DAISY_RADIUS = 100.0, QUAKE_RADIUS = 25.0;
    public static final int NAPALM_TICKS = 40, PEN_DEPTH = 30, DAISY_TICKS = 70, QUAKE_TICKS = 80, QUAKE_DEPTH = 20;

    private static void napalmStep(ServerLevel level, Task k, int age) {
        double f0 = NAPALM_LENGTH * age / (double) k.total - 4.0, f1 = NAPALM_LENGTH * (age + 1) / (double) k.total - 4.0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState fire = Wmd.NAPALM_FIRE.get().defaultBlockState();
        for (double u = f0; u < f1; u += 0.7) {
            for (double v = -NAPALM_WIDTH / 2; v <= NAPALM_WIDTH / 2; v += 0.7) {
                if (level.random.nextFloat() < 0.35F) continue;
                int bx = (int) Math.floor(k.x + k.dx * u - k.dz * v), bz = (int) Math.floor(k.z + k.dz * u + k.dx * v);
                p.set(bx, level.getMinBuildHeight() + 1, bz);
                if (!level.hasChunkAt(p)) continue;
                int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                p.set(bx, by, bz);
                BlockState s = level.getBlockState(p);
                if ((s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty() && NapalmFireBlock.canSit(level, p) && Config.Server.canBreak(level, p))
                    level.setBlock(p, fire, Block.UPDATE_ALL);
            }
        }
        double mid = (f0 + f1) * 0.5;
        double cx = k.x + k.dx * mid, cz = k.z + k.dz * mid;
        level.sendParticles(ParticleTypes.FLAME, cx, k.y + 1.0, cz, 40, NAPALM_WIDTH * 0.4, 1.2, NAPALM_WIDTH * 0.4, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, cx, k.y + 3.0, cz, 25, NAPALM_WIDTH * 0.4, 2.0, NAPALM_WIDTH * 0.4, 0.04);
        AABB box = new AABB(cx - NAPALM_WIDTH, k.y - 4, cz - NAPALM_WIDTH, cx + NAPALM_WIDTH, k.y + 6, cz + NAPALM_WIDTH);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) e.igniteForSeconds(10.0F);
        if (age % 8 == 0) level.playSound(null, cx, k.y, cz, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 8.0F, 0.6F);
    }

    private static void penShaft(ServerLevel level, int gx, int gy, int gz, double r, int depth) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int ri = (int) Math.ceil(r);
        int yEnd = Math.max(level.getMinBuildHeight() + 1, gy - depth);
        for (int y = gy; y >= yEnd; y--) {
            for (int x = -ri; x <= ri; x++) for (int z = -ri; z <= ri; z++) {
                if (x * x + z * z > r * r) continue;
                p.set(gx + x, y, gz + z);
                if (!level.hasChunkAt(p)) continue;
                BlockState s = level.getBlockState(p);
                if (s.isAir() || s.getDestroySpeed(level, p) < 0) continue;
                if (Config.Server.canBreak(level, p)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        level.sendParticles(ParticleTypes.POOF, gx + 0.5, gy + 1.0, gz + 0.5, 40, r, 2.0, r, 0.3);
        level.playSound(null, gx + 0.5, gy, gz + 0.5, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 8.0F, 0.5F);
    }

    private static void quakeBombStep(ServerLevel level, FlightManager mgr, Task k, int age) {
        int gx = (int) Math.floor(k.x), gy = (int) Math.floor(k.y), gz = (int) Math.floor(k.z);
        if (age == 0) {
            hurt(level, new Vec3(k.x, k.y, k.z), 6.0, 8.0, 0.0, 0.2, 0.0, 0);
            send(level, k.x, k.y, k.z, 3, WarheadType.SEISMIC, ST_QUAKE);
        }
        if (age == k.total / 2) mgr.tasks.add(task(DEEP, k.x, k.y - QUAKE_DEPTH, k.z, 10.0, 1, WarheadType.EARTHQUAKE, 3));
        if (age == k.total * 3 / 4) {
            Crater sink = new Crater(gx, gy, gz, k.r, 0.55, 0.0, 0.0, k.r * 1.5 + 16.0);
            mgr.craters.add(sink);
            DamageRings.queue(level, sink, WarheadType.values()[k.type]);
            Debris.blast(level, gx, gy, gz, k.r, sink);
            hurt(level, new Vec3(k.x, k.y, k.z), k.r, 1.6, 0.4, 1.2, 0.0, 0);
            MapFeed.hit(level, "earthquake", 3, false, k.x, gy, k.z, k.r, k.owner);
            Task s = task(SEISMIC_QUAKE, k.x, k.y, k.z, k.r * 1.2, 120, WarheadType.EARTHQUAKE, 3);
            s.owner = k.owner;
            mgr.tasks.add(s);
            Task a = task(AFTER, k.x, k.y, k.z, 60.0, 300, WarheadType.EARTHQUAKE, 3);
            a.owner = k.owner;
            mgr.tasks.add(a);
            send(level, k.x, k.y, k.z, 3, WarheadType.SEISMIC, ST_COLLAPSE);
        }
    }

    public static final int ST_BOMBLET = 21, ST_CLUSTER_OPEN = 1000, CLUSTER_RAIN = 30;
    public static final double CLUSTER_OPEN_UP = 60.0;

    private static void deepBlast(ServerLevel level, FlightManager mgr, Task k) {
        int bx = (int) Math.floor(k.x), bz = (int) Math.floor(k.z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return;
        double y = Math.max(level.getMinBuildHeight() + k.r + 2.0, k.y);
        Vec3 pos = new Vec3(k.x, y, k.z);
        hurt(level, pos, k.r, 2.0, 0.9, 1.5, 1.0, 6);
        Crater cavern = new Crater((int) Math.floor(k.x), (int) Math.floor(y), (int) Math.floor(k.z), k.r, 1.0, 0.0, 0.0, k.r * 1.5 + 16.0).buster();
        mgr.craters.add(cavern);
        Debris.blast(level, (int) Math.floor(k.x), (int) Math.floor(y), (int) Math.floor(k.z), k.r, cavern);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(k.x), (int) Math.floor(k.z));
        level.playSound(null, k.x, surface, k.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 20.0F, 0.4F);
        level.sendParticles(ParticleTypes.POOF, k.x, surface + 1.0, k.z, 80, k.r * 0.4, 2.0, k.r * 0.4, 0.4);
        send(level, k.x, surface, k.z, k.tier, WarheadType.BUNKER, 1);
    }

    static boolean isSoft(ServerLevel level, BlockPos p, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        if (s.getDestroySpeed(level, p) < 0) return false;
        if (s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) || s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES)) return true;
        if (s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.WOOL) || s.is(net.minecraft.tags.BlockTags.WOOL_CARPETS)
                || s.is(net.minecraft.tags.BlockTags.DOORS) || s.is(net.minecraft.tags.BlockTags.TRAPDOORS) || s.is(net.minecraft.tags.BlockTags.FLOWERS)
                || s.is(net.minecraft.tags.BlockTags.CANDLES) || s.is(net.minecraft.tags.BlockTags.BEDS) || s.is(net.minecraft.tags.BlockTags.BANNERS)) return true;
        if (s.getBlock() instanceof net.minecraft.world.level.block.TorchBlock || s.getBlock() instanceof net.minecraft.world.level.block.WallTorchBlock) return true;
        return s.getCollisionShape(level, p).isEmpty() && s.getDestroySpeed(level, p) <= 0.2F;
    }

    private static void shockStep(ServerLevel level, Task k, int age) {
        double r0 = k.r * age / (double) k.total, r1 = k.r * (age + 1) / (double) k.total;
        int columns = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        WarheadType wt = WarheadType.ofOrdinal(k.type);
        double inner = Missiles.craterRadius(k.tier) * Math.max(0.0, wt.crater) * 0.95;
        if (r1 < inner) r1 = r0;
        else r0 = Math.max(r0, inner);
        long t0 = WorkBudget.start();
        java.util.Set<LevelChunk> touched = new java.util.HashSet<>();
        try {
        for (double rr = r0 + 0.5; rr < r1 + 0.5 && columns < SHOCK_MAX_COLUMNS; rr += 1.0) {
            int n = (int) Math.max(8, Math.ceil(Math.PI * 2 * rr * 0.9));
            for (int i = 0; i < n && columns < SHOCK_MAX_COLUMNS && ((columns & 7) != 7 || WorkBudget.more(t0)); i++) {
                double a = Math.PI * 2 * i / n;
                int bx = (int) Math.floor(k.x + Math.cos(a) * rr), bz = (int) Math.floor(k.z + Math.sin(a) * rr);
                p.set(bx, level.getMinBuildHeight() + 1, bz);
                if (!level.hasChunkAt(p)) continue;
                columns++;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                int bottom = Math.max(level.getMinBuildHeight(), top - SHOCK_DEPTH);
                double nd = rr / k.r;
                for (int y = top; y >= bottom; y--) {
                    p.set(bx, y, bz);
                    BlockState s = level.getBlockState(p);
                    if (!isSoft(level, p, s)) continue;
                    int rating = Hardened.rating(level, p, s);
                    if (rating > 0 && Hardened.survives(rating, nd)) continue;
                    if (Config.Server.canBreak(level, p)) quietRemove(level, p.immutable(), s, touched);
                }
            }
        }
        } finally { quietFlush(level, touched); WorkBudget.done(t0); }

        double mid = (r0 + r1) * 0.5;
        for (int i = 0; i < 10; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            int bx = (int) Math.floor(k.x + Math.cos(a) * mid), bz = (int) Math.floor(k.z + Math.sin(a) * mid);
            if (!level.hasChunkAt(new BlockPos(bx, level.getMinBuildHeight() + 1, bz))) continue;
            int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            level.sendParticles(ParticleTypes.POOF, bx + 0.5, by + 1.0, bz + 0.5, 4, 2.0, 1.0, 2.0, 0.3);
            if (i % 5 == 0) level.playSound(null, bx + 0.5, by + 1.0, bz + 0.5, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 16.0F, 0.5F + level.random.nextFloat() * 0.3F);
        }
    }

    public static final int ST_ROD_FALL = 10, ST_ROD_HIT = 11, ST_AFTERSHOCK = 12, ST_HEMP = 13, ST_QUAKE = 14, ST_COLLAPSE = 15, ST_DUST = 16;
    public static final double ROD_ZONE = 30.0, ROD_SHOCK = 300.0, HEMP_RADIUS = 1000.0, SEISMIC_RADIUS = 150.0;
    public static final int ROD_FALL_TICKS = 200, HEMP_TICKS = 6000, SEISMIC_TICKS = 160;

    public static final int[] HEMP_WAVE_START = { 0, 50, 100, 170, 260 };
    public static final int[] HEMP_WAVE_TICKS = { 220, 260, 300, 340, 400 };
    public static final float[] HEMP_WAVE_POWER = { 1.0F, 0.9F, 0.8F, 0.7F, 0.6F };
    public static final int HEMP_WAVES_END = 660;

    public static double hempWave(int i, double age, double R) {
        double t = age - HEMP_WAVE_START[i];
        if (t < 0 || t > HEMP_WAVE_TICKS[i]) return -1.0;
        double u = t / HEMP_WAVE_TICKS[i];
        return R * (1.0 - Math.pow(1.0 - u, 2.2));
    }

    private static void startRod(ServerLevel level, FlightManager mgr, int gx, int gy, int gz, int tier, WarheadType t, boolean rf, double hold, long now) {
        Task k = task(ROD_FALL, gx + 0.5, gy, gz + 0.5, ROD_ZONE, 1, t, tier);
        k.refined = rf;
        rodImpact(level, mgr, k);
        mgr.release(level, gx, gz, hold, now + 900);
    }

    private static void rodImpact(ServerLevel level, FlightManager mgr, Task k) {
        int gx = (int) Math.floor(k.x), gy = (int) Math.floor(k.y), gz = (int) Math.floor(k.z);
        WarheadType t = WarheadType.ROD;
        double zone = k.refined ? ROD_ZONE * 1.25 : ROD_ZONE;
        Vec3 pos = new Vec3(k.x, k.y, k.z);
        hurt(level, pos, zone, ROD_SHOCK / zone, 1.0, 4.0, 1.5, 12);
        ShipStrike.blast(level, pos, zone, ShipStrike.powerFor(zone), k.owner != null ? k.owner : ShipStrike.shooter());
        shaft(level, gx, gy, gz, 12.0, 110);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < 3; dy++) for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            if (dx * dx + dz * dz > 49) continue;
            p.set(gx + dx, gy - 110 + dy, gz + dz);
            if (level.hasChunkAt(p) && level.getBlockState(p).isAir() && Config.Server.canBreak(level, p)) level.setBlock(p, Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        Crater crater = new Crater(gx, gy, gz, zone, 0.9, 3.0, 0.0, holdRadius(4, t));
        mgr.craters.add(crater);
        DamageRings.queue(level, crater, t);
        MapFeed.hit(level, "rod", 4, k.refined, k.x, gy, k.z, zone, k.owner != null ? k.owner : ShipStrike.shooter());
        Debris.blast(level, gx, gy, gz, zone, crater);
        mgr.tasks.add(task(SHOCK, k.x, k.y, k.z, ROD_SHOCK, 120, t, 4));
        mgr.tasks.add(task(FISSURE, k.x, k.y, k.z, 140.0, 60, t, 4));
        mgr.tasks.add(task(AFTER, k.x, k.y, k.z, 100.0, 440, t, 4));
        send(level, k.x, k.y, k.z, 4, t, ST_ROD_HIT);
        send(level, k.x, k.y, k.z, 4, t, ST_DUST);
    }

    private static void aftershock(ServerLevel level, Task k, int age) {
        if (age == 0 || age % 140 != 0) return;
        hurt(level, new Vec3(k.x, k.y, k.z), 20.0, k.r / 20.0, 0.0, 0.7, 0.0, 0);
        send(level, k.x, k.y, k.z, 3, WarheadType.ROD, ST_AFTERSHOCK);
    }

    private static void fissureStep(ServerLevel level, Task k, int age) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        double r = 30.0 + age * 2.3;
        long seed = (long) (k.x * 31 + k.z * 17);
        for (int i = 0; i < 14; i++) {
            double a = Math.PI * 2 * i / 14.0 + ((seed >> i) & 7) * 0.05 + Math.sin(r * 0.05 + i) * 0.06;
            for (double rr = r; rr < r + 2.4; rr += 1.0) {
                int bx = (int) Math.floor(k.x + Math.cos(a) * rr), bz = (int) Math.floor(k.z + Math.sin(a) * rr);
                for (int w = 0; w < 2; w++) {
                    int cx = bx + w, cz = bz;
                    p.set(cx, level.getMinBuildHeight() + 1, cz);
                    if (!level.hasChunkAt(p)) continue;
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
                    for (int d = 0; d < 6; d++) {
                        p.set(cx, top - 1 - d, cz);
                        BlockState s = level.getBlockState(p);
                        if (s.isAir() || !s.getFluidState().isEmpty() || s.getDestroySpeed(level, p) < 0 || Hardened.rating(level, p, s) > 0) continue;
                        if (Config.Server.canBreak(level, p)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static void hempDrain(ServerLevel level, Task k, int age) {
        List<BlockPos> sparks = new ArrayList<>();
        int r = (int) Math.ceil(k.r);
        int c0x = (int) Math.floor((k.x - r) / 16), c1x = (int) Math.floor((k.x + r) / 16);
        int c0z = (int) Math.floor((k.z - r) / 16), c1z = (int) Math.floor((k.z + r) / 16);
        double r2 = k.r * k.r;
        for (int cx = c0x; cx <= c1x; cx++) for (int cz = c0z; cz <= c1z; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (var be : new ArrayList<>(chunk.getBlockEntities().values())) {
                double dx = be.getBlockPos().getX() + 0.5 - k.x, dz = be.getBlockPos().getZ() + 0.5 - k.z;
                if (dx * dx + dz * dz > r2) continue;
                hempHit(level, be, sparks);
            }
        }

        ShipStrike.shipMachines(level, k.x, k.z, k.r, be -> hempHit(level, be, sparks));
        hempSparks(level, sparks, age, k);
    }

    private static void hempHit(ServerLevel level, net.minecraft.world.level.block.entity.BlockEntity be, List<BlockPos> sparks) {
        BlockPos bp = be.getBlockPos();
        if (be instanceof EmpVulnerable || be instanceof BusbarBlockEntity) sparks.add(bp);
        net.neoforged.neoforge.energy.IEnergyStorage cap = be instanceof EmpVulnerable ? null : level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, bp, be.getBlockState(), be, null);
        if (!(be instanceof EmpVulnerable) && (cap == null || cap.getEnergyStored() <= 0)) return;
        if (EmpField.shielded(level, bp)) return;
        if (be instanceof EmpVulnerable v) v.empDrain();
        else cap.extractEnergy(cap.getEnergyStored(), false);
    }

    private static void hempArcs(ServerLevel level, Task k, int age) {
        for (net.minecraft.server.level.ServerPlayer pl : level.players()) {
            double dx = pl.getX() - k.x, dz = pl.getZ() - k.z, dp = Math.hypot(dx, dz);
            if (dp > k.r + 60.0) continue;
            double front = -1;
            for (int i = 0; i < HEMP_WAVE_START.length; i++) {
                double r = hempWave(i, age, k.r);
                if (r >= 0 && Math.abs(r - dp) < 90.0 && (front < 0 || Math.abs(r - dp) < Math.abs(front - dp))) front = r;
            }
            if (front < 0) continue;
            double a = Math.atan2(dz, dx) + (level.random.nextDouble() - 0.5) * 120.0 / Math.max(60.0, front);
            int bx = (int) Math.floor(k.x + Math.cos(a) * front), bz = (int) Math.floor(k.z + Math.sin(a) * front);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) continue;
            bolt.moveTo(bx + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), bz + 0.5);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    private static void hempSparks(ServerLevel level, List<BlockPos> at, int age, Task k) {
        if (at.isEmpty()) return;
        if (age < HEMP_WAVES_END) {
            List<BlockPos> hit = new ArrayList<>();
            for (BlockPos bp : at) {
                double d = Math.hypot(bp.getX() + 0.5 - k.x, bp.getZ() + 0.5 - k.z);
                for (int i = 0; i < HEMP_WAVE_START.length; i++) {
                    double r = hempWave(i, age, k.r);
                    if (r >= 0 && Math.abs(d - r) < 24.0) { hit.add(bp); break; }
                }
            }
            at = hit;
            if (at.isEmpty()) return;
        }
        int n = Math.min(at.size(), age < HEMP_WAVES_END ? 40 : 3);
        java.util.Collections.shuffle(at, new java.util.Random(level.random.nextLong()));
        for (int i = 0; i < n; i++) {
            BlockPos bp = at.get(i);
            if (Hardened.layers(level, Vec3.atCenterOf(bp).add(0.0, 0.6, 0.0), Vec3.atCenterOf(bp).add(0.0, 24.0, 0.0)) >= 3) continue;
            boolean bus = level.getBlockEntity(bp) instanceof BusbarBlockEntity;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, bp.getX() + 0.5, bp.getY() + (bus ? 0.5 : 1.2), bp.getZ() + 0.5, bus ? 10 : 16, bus ? 0.5 : 0.9, 0.4, bus ? 0.5 : 0.9, 0.35);
            if (level.random.nextInt(bus ? 3 : 4) == 0)
                level.playSound(null, bp, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.25F, 1.8F + level.random.nextFloat() * 0.2F);
        }
    }

    private static void hempKillFlights(ServerLevel level, FlightManager mgr, Task k) {
        double r2 = k.r * k.r;
        for (Flight f : new ArrayList<>(mgr.flights())) {
            Vec3 p = f.position(level.getGameTime());
            double dx = p.x - k.x, dz = p.z - k.z;
            if (dx * dx + dz * dz > r2) continue;
            wreck(level, mgr, f, p, true);
        }
    }

    private static void wreckStep(ServerLevel level, FlightManager mgr, Task k, int age) {
        k.x += k.dx; k.y += k.r; k.z += k.dz;
        k.r = Math.max(-3.0, k.r - 0.08);
        k.dx *= 0.99; k.dz *= 0.99;
        for (net.minecraft.server.level.ServerPlayer pl : level.players()) {
            if (pl.distanceToSqr(k.x, k.y, k.z) > 1500.0 * 1500.0) continue;
            level.sendParticles(pl, ParticleTypes.LARGE_SMOKE, true, k.x, k.y, k.z, 3, 0.4, 0.4, 0.4, 0.02);
            if (age % 2 == 0) level.sendParticles(pl, ParticleTypes.FLAME, true, k.x, k.y, k.z, 2, 0.3, 0.3, 0.3, 0.01);
        }
        int bx = (int) Math.floor(k.x), bz = (int) Math.floor(k.z);
        boolean loaded = level.hasChunk(bx >> 4, bz >> 4);
        if (!loaded) { if (k.y <= level.getMinBuildHeight()) k.ticks = 1; return; }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        if (k.y > ground && k.ticks > 1) return;
        mgr.holdForTest(level, bx, bz, Missiles.craterRadius(1) + 4.0);
        blast(level, mgr, bx, ground, bz, 1, WarheadType.HE, false);
        k.ticks = 1;
    }

    public static boolean spectacle(WarheadType t) { return t == WarheadType.HE || t == WarheadType.HEAVY_BLAST || t == WarheadType.THERMOBARIC || t == WarheadType.FRAG || t == WarheadType.INCENDIARY; }

    private static void fragGlass(ServerLevel level, Vec3 c, double reach) {
        if (!Config.shockwaveBreaksGlass()) return;
        int R = (int) Math.ceil(reach), breaks = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) for (int dy = -Math.min(R, 8); dy <= Math.min(R, 16); dy++) {
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > reach * reach) continue;
            p.set((int) Math.floor(c.x) + dx, (int) Math.floor(c.y) + dy, (int) Math.floor(c.z) + dz);
            if (!level.hasChunkAt(p)) continue;
            BlockState s = level.getBlockState(p);
            if (!(s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) || s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES) || s.is(net.minecraft.tags.BlockTags.FLOWER_POTS))) continue;
            BlockPos hit = p.immutable();
            net.minecraft.world.phys.BlockHitResult ray = level.clip(new net.minecraft.world.level.ClipContext(c, Vec3.atCenterOf(hit),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (ray.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && !ray.getBlockPos().equals(hit)) continue;
            int rating = Hardened.rating(level, hit, s);
            if (rating > 0 && Hardened.survives(rating, Math.sqrt(d2) / reach)) continue;
            if (Config.Server.canBreak(level, hit)) level.setBlock(hit, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (breaks++ < 40) level.levelEvent(2001, hit, Block.getId(s));
        }
    }

    public static final int FUEL_TICKS = 40, SUCK_START = 28, ST_TBX_FUEL = 2;

    private static void suck(ServerLevel level, Task k) {
        double reach = k.r * 1.6;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(k.x - reach, k.y - 10, k.z - reach, k.x + reach, k.y + 30, k.z + reach))) {
            if (e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) continue;
            double dx = k.x - e.getX(), dz = k.z - e.getZ(), d = Math.sqrt(dx * dx + dz * dz);
            if (d < 1.0 || d > reach) continue;
            double f = 0.07 * (1.0 - d / reach) + 0.02;
            e.push(dx / d * f, 0.0, dz / d * f);
            e.hurtMarked = true;
        }
    }

    public static boolean farWave(WarheadType t, int tier) { return t == WarheadType.NUKE || t == WarheadType.THERMO || t == WarheadType.ANTIMATTER && tier >= 3 || t == WarheadType.RAILGUN || t == WarheadType.ORBITAL_ROD || t == WarheadType.ASTEROID && tier >= 3; }

    static double pieceShare(WarheadType t, int tier) { return t == WarheadType.ASTEROID && tier < 4 ? Math.pow(2.0, tier - 4) : 1.0; }

    public static boolean hasWave(WarheadType t, int tier) { return spectacle(t) || farWave(t, tier); }

    public static double waveReach(WarheadType t, int tier, double r) {
        if (t == WarheadType.THERMO) return 1000.0;
        if (t == WarheadType.NUKE) return 800.0;
        if (t == WarheadType.ANTIMATTER && tier >= 3) return tier >= 4 ? 1100.0 : 600.0;
        if (t == WarheadType.RAILGUN) return Balance.RG_WAVE_REACH;
        if (t == WarheadType.ORBITAL_ROD) return Balance.ROD_WAVE_REACH;
        if (t == WarheadType.ASTEROID) return tier >= 4 ? Balance.ASTEROID_WAVE_REACH : Balance.ASTEROID_WAVE_REACH / 3.0;
        return waveReach(r);
    }

    public static double[] surge(WarheadType t, int tier) {
        if (t == WarheadType.THERMO) return new double[] { 300.0, 40.0 };
        if (t == WarheadType.NUKE) return new double[] { 220.0, 30.0 };
        return null;
    }

    public static int waveArrival(double d, double reach, int ticks) {
        if (d >= reach) return -1;
        return (int) Math.round(ticks * (1.0 - Math.sqrt(1.0 - d / reach)));
    }

    static boolean shielded(ServerLevel level, double x0, double y0, double z0, double x, double y, double z) {
        double dx = x - x0, dz = z - z0, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 16) return false;
        int steps = (int) Math.min(64, d / 4);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 1; i < steps; i++) {
            double f = i / (double) steps;
            int bx = (int) Math.floor(x0 + dx * f), bz = (int) Math.floor(z0 + dz * f);
            p.set(bx, level.getMinBuildHeight() + 1, bz);
            if (!level.hasChunkAt(p)) continue;
            double line = (y0 + 3) + ((y + 1) - (y0 + 3)) * f;
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) > line + 2) return true;
        }
        return false;
    }

    public static double waveReach(double r) { return Math.max(40.0, r * 7.0); }
    public static int waveTicks(double reach) { return (int) Math.round(30 + reach / 10.0); }

    public static int waveTicks(WarheadType t, double reach) { return t == WarheadType.FRAG ? 16 : waveTicks(reach); }

    public static double waveRadius(double age, double reach, int ticks) {
        if (age < 0 || age > ticks) return -1.0;
        double u = age / ticks;
        return reach * (1.0 - Math.pow(1.0 - u, 2.0));
    }

    private static final java.util.Map<Task, java.util.Set<Integer>> PUSHED = Statics.server(new java.util.WeakHashMap<>());

    private static void waveStep(ServerLevel level, Task k, int age) {
        double r = waveRadius(age, k.r, k.total);
        if (r < 0) return;
        fratricide(level, k, age, Math.max(0.0, waveRadius(age - 1, k.r, k.total)), r);
        java.util.Set<Integer> done = PUSHED.computeIfAbsent(k, x -> new java.util.HashSet<>());
        List<LivingEntity> near = new ArrayList<>();
        if (k.r <= 200) near.addAll(level.getEntitiesOfClass(LivingEntity.class, new AABB(k.x - r - 3, k.y - 20, k.z - r - 3, k.x + r + 3, k.y + 40, k.z + r + 3)));
        else for (net.minecraft.world.entity.Entity e : level.getAllEntities()) if (e instanceof LivingEntity le) near.add(le);
        for (LivingEntity e : near) {
            if (done.contains(e.getId())) continue;
            double dx = e.getX() - k.x, dz = e.getZ() - k.z, d = Math.sqrt(dx * dx + dz * dz);
            if (d > r || d < 0.5 || Math.abs(e.getY() - k.y) > 120) continue;
            done.add(e.getId());
            if (e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) continue;
            double strength = ((k.r > 200 ? 0.2 : 0.35) + 1.6 * Math.pow(1.0 - d / k.r, 1.5)) * (k.tier >= 3 ? 1.5 : 1.0);
            if (k.type == WarheadType.THERMOBARIC.ordinal()) strength *= 1.6;
            if (k.type == WarheadType.FRAG.ordinal()) strength *= 0.4;
            if (k.type == WarheadType.INCENDIARY.ordinal()) strength *= 0.25;
            if (k.r > 200 && shielded(level, k.x, k.y, k.z, e.getX(), e.getY(), e.getZ())) strength *= 0.25;
            e.push(dx / d * strength, 0.25 + 0.35 * strength, dz / d * strength);
            e.hurtMarked = true;
        }
        if (age >= k.total - 1) PUSHED.remove(k);
    }

    private static void fratricide(ServerLevel level, Task k, int age, double r0, double r1) {
        WarheadType t = WarheadType.ofOrdinal(k.type);
        if (!farWave(t, k.tier)) return;
        double cr = Missiles.craterRadius(k.tier) * Math.max(0.3, t.crater), ball = cr * Balance.FRAT_BALL, reach = Math.min(k.r, cr * Balance.FRAT_REACH);
        boolean ballOn = age <= Balance.FRAT_BALL_TICKS;
        if (!ballOn && r0 > reach) return;
        FlightManager mgr = FlightManager.get(level);
        long now = level.getGameTime();
        double band = Math.min(r1, reach);
        int vaporised = 0, wrecked = 0;
        for (Flight f : new ArrayList<>(mgr.flights())) {
            Vec3 p = f.position(now);
            double dh = Math.hypot(p.x - k.x, p.z - k.z), up = p.y - k.y;
            if (up > Balance.FRAT_ALT || up < -64.0) continue;
            if (ballOn && dh * dh + up * up <= ball * ball) {
                level.sendParticles(ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                mgr.kill(level, f);
                vaporised++;
            } else if (dh >= r0 && dh < band) {
                wreck(level, mgr, f, p, false);
                wrecked++;
            }
        }
        for (Interceptors.Shot s : new ArrayList<>(mgr.shots)) {
            double u = Mth.clamp((now - s.start()) / (double) Math.max(1, s.duration()), 0.0, 1.0);
            Vec3 p = s.from().add(s.to().subtract(s.from()).scale(u));
            double dh = Math.hypot(p.x - k.x, p.z - k.z), up = p.y - k.y;
            if (up > Balance.FRAT_ALT || up < -64.0) continue;
            if ((ballOn && dh * dh + up * up <= ball * ball) || (dh >= r0 && dh < band)) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 12, 1.0, 1.0, 1.0, 0.05);
                mgr.shots.remove(s);
            }
        }
        if (vaporised + wrecked > 0) Wmd.LOGGER.info("[blast] {} at {} {}: {} missile(s) vaporised, {} wrecked by the blast", t.id, (int) k.x, (int) k.z, vaporised, wrecked);
    }

    private static void wreck(ServerLevel level, FlightManager mgr, Flight f, Vec3 p, boolean spark) {
        level.sendParticles(spark ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.FLAME, p.x, p.y, p.z, 60, 3.0, 3.0, 3.0, 0.4);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 40, 2.0, 2.0, 2.0, 0.1);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 6.0F, 0.6F);
        Vec3 v = f.position(level.getGameTime() + 1).subtract(p);
        Task w = new Task();
        w.kind = WRECK; w.total = w.ticks = 1200; w.type = WarheadType.HE.ordinal(); w.tier = 1;
        w.x = p.x; w.y = p.y; w.z = p.z; w.dx = v.x; w.dz = v.z; w.r = v.y;
        mgr.tasks.add(w);
        mgr.kill(level, f);
    }

    private static final int GLASS_DEPTH = 24;
    private static long slowColumnLogged = Long.MIN_VALUE / 2;
    private static long now(ServerLevel level) { return level.getGameTime(); }

    private static void glassStep(ServerLevel level, Task k, int age) {
        double gMax = k.r * 0.6;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int breaks = 0;
        double r0 = Math.max(0.0, waveRadius(age - 1, k.r, k.total)), r1 = waveRadius(age, k.r, k.total);
        if (r1 < 0 || r0 >= gMax) return;
        r1 = Math.min(r1, gMax);
        double area = Math.PI * (r1 * r1 - r0 * r0);
        int n = (int) Math.min(Balance.WAVE_GLASS_COLUMNS * (k.type == WarheadType.ASTEROID.ordinal() ? pieceShare(WarheadType.ASTEROID, k.tier) : 1.0), Math.max(1, area));
        long t0 = WorkBudget.start();
        java.util.Set<LevelChunk> touched = new java.util.HashSet<>();
        try {

            for (int i = 0; i < n && (i == 0 || WorkBudget.more(t0)); i++) {
                double rr = Math.sqrt(r0 * r0 + level.random.nextDouble() * (r1 * r1 - r0 * r0)), a = level.random.nextDouble() * Math.PI * 2;
                int bx = (int) Math.floor(k.x + Math.cos(a) * rr), bz = (int) Math.floor(k.z + Math.sin(a) * rr);
                if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
                long c0 = System.nanoTime();
                int before = breaks;
                breaks = glassColumn(level, k, bx, bz, p, breaks, touched);
                long cms = (System.nanoTime() - c0) / 1_000_000L;
                if (cms > 50 && now(level) - slowColumnLogged > 20) {
                    slowColumnLogged = now(level);
                    Wmd.LOGGER.warn("[impact-slow] far glass: ONE column at {} {} took {} ms ({} blocks broken, top {})", bx, bz, cms, breaks - before,
                            level.getBlockState(new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1, bz)));
                }
            }
        } finally { quietFlush(level, touched); WorkBudget.done(t0); }
    }

    private static void quietRemove(ServerLevel level, BlockPos p, BlockState s, java.util.Set<LevelChunk> touched) {
        if (s.hasBlockEntity() || Crater.needsHooks(s)) { level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS); return; }
        LevelChunk ch = level.getChunkAt(p);
        int lx = p.getX() & 15, ly = p.getY(), lz = p.getZ() & 15;
        ch.getSection(ch.getSectionIndex(ly)).setBlockState(lx, ly & 15, lz, Blocks.AIR.defaultBlockState(), false);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (Heightmap.Types ht : new Heightmap.Types[] { Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE })
            ch.getOrCreateHeightmapUnprimed(ht).update(lx, ly, lz, air);
        level.getChunkSource().blockChanged(p);
        level.getChunkSource().getLightEngine().checkBlock(p);
        touched.add(ch);
    }

    private static void quietFlush(ServerLevel level, java.util.Set<LevelChunk> touched) {
        for (LevelChunk ch : touched) {
            ch.setUnsaved(true);
            TargetSnapshot.changed(level, ch.getPos().x, ch.getPos().z);
        }
        touched.clear();
    }

    private static int glassColumn(ServerLevel level, Task k, int bx, int bz, BlockPos.MutableBlockPos p, int breaks, java.util.Set<LevelChunk> touched) {
        double rr = Math.hypot(bx + 0.5 - k.x, bz + 0.5 - k.z), lMax = k.r * 0.4;
        if (rr > k.r * 0.6) return breaks;
        double nd = rr / k.r;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        for (int y = top; y >= Math.max(level.getMinBuildHeight(), top - GLASS_DEPTH); y--) {
            p.set(bx, y, bz);
            BlockState s = level.getBlockState(p);
            boolean glass = s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) || s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES);
            boolean leaf = !glass && rr < lMax && s.is(net.minecraft.tags.BlockTags.LEAVES) && level.random.nextDouble() < (1.0 - rr / lMax) * 1.4;
            if (!glass && !leaf) continue;
            int rating = Hardened.rating(level, p, s);
            if (rating > 0 && Hardened.survives(rating, nd)) continue;
            if (Config.Server.canBreak(level, p)) quietRemove(level, p.immutable(), s, touched);
            if (breaks++ < 24) level.levelEvent(2001, p, Block.getId(s));
        }
        return breaks;
    }

    private static void seismicStep(ServerLevel level, FlightManager mgr, Task k, int age) {
        if (age == 0) {
            hurt(level, new Vec3(k.x, k.y, k.z), 10.0, 15.0, 0.0, 0.25, 0.0, 0);
            send(level, k.x, k.y, k.z, 4, WarheadType.SEISMIC, ST_QUAKE);
        }
        if (age == 100) send(level, k.x, k.y, k.z, 4, WarheadType.SEISMIC, ST_COLLAPSE);
        if (age < 100) return;
        for (int i = 0; i < 700; i++) {
            double a = level.random.nextDouble() * Math.PI * 2, d = k.r * Math.sqrt(level.random.nextDouble());
            int cx = (int) Math.floor(k.x + Math.cos(a) * d), cz = (int) Math.floor(k.z + Math.sin(a) * d);
            collapse(level, cx, cz);
            collapseSurface(level, cx, cz);
        }
    }

    private static boolean natural(BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || s.is(net.minecraft.tags.BlockTags.SAND) || s.is(net.minecraft.tags.BlockTags.LEAVES)
                || s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.SNOW) || s.is(net.minecraft.tags.BlockTags.ICE) || s.is(net.minecraft.tags.BlockTags.FLOWERS)
                || s.is(net.minecraft.tags.BlockTags.TERRACOTTA) || s.is(net.minecraft.tags.BlockTags.CORAL_PLANTS) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
                || s.is(Blocks.ICE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.VINE) || s.is(Blocks.DEAD_BUSH) || !s.getFluidState().isEmpty();
    }

    private static void collapseSurface(ServerLevel level, int bx, int bz) {
        if (!level.hasChunk(bx >> 4, bz >> 4)) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
        int removed = 0;
        for (int y = top; y > level.getMinBuildHeight() + 1 && removed < 5 && y > top - 120; y--) {
            p.set(bx, y, bz);
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            if (natural(s)) break;
            if (s.getDestroySpeed(level, p) < 0 || Hardened.rating(level, p, s) > 0) break;
            if (Config.Server.canBreak(level, p)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            removed++;
        }
    }

    private static void collapse(ServerLevel level, int bx, int bz) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(bx, level.getMinBuildHeight() + 1, bz);
        if (!level.hasChunkAt(p)) return;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        int bottom = Math.max(level.getMinBuildHeight() + 4, top - 80);
        for (int y = top - 4; y > bottom; y--) {
            p.set(bx, y, bz);
            if (!level.getBlockState(p).isAir()) continue;
            p.set(bx, y + 1, bz);
            BlockState roof = level.getBlockState(p);
            if (roof.isAir() || !roof.getFluidState().isEmpty() || roof.getDestroySpeed(level, p) < 0 || Hardened.rating(level, p, roof) > 0) continue;
            for (int d = 1; d <= 3; d++) {
                p.set(bx, y + d, bz);
                BlockState s = level.getBlockState(p);
                if (s.isAir() || !s.getFluidState().isEmpty() || s.getDestroySpeed(level, p) < 0 || Hardened.rating(level, p, s) > 0) break;
                if (Config.Server.canBreak(level, p)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            return;
        }
    }

    private static void drain(ServerLevel level, Task k) {
        int r = (int) Math.ceil(k.r);
        int c0x = (int) Math.floor((k.x - r) / 16), c1x = (int) Math.floor((k.x + r) / 16);
        int c0z = (int) Math.floor((k.z - r) / 16), c1z = (int) Math.floor((k.z + r) / 16);
        double r2 = k.r * k.r;
        for (int cx = c0x; cx <= c1x; cx++) for (int cz = c0z; cz <= c1z; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (var be : new ArrayList<>(chunk.getBlockEntities().values())) {
                if (!(be instanceof EmpVulnerable v)) continue;
                double dx = be.getBlockPos().getX() + 0.5 - k.x, dz = be.getBlockPos().getZ() + 0.5 - k.z;
                if (dx * dx + dz * dz <= r2) v.empDrain();
            }
        }
        ShipStrike.shipMachines(level, k.x, k.z, k.r, be -> { if (be instanceof EmpVulnerable v) v.empDrain(); });
    }

    private static void empFx(ServerLevel level, Task k, int age) {
        if (age % 10 == 0 && age < 400) {
            for (int i = 0; i < 6; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, d = k.r * 0.8 * Math.sqrt(level.random.nextDouble());
                int bx = (int) Math.floor(k.x + Math.cos(a) * d), bz = (int) Math.floor(k.z + Math.sin(a) * d);
                if (!level.hasChunkAt(new BlockPos(bx, level.getMinBuildHeight() + 1, bz))) continue;
                int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, bx + 0.5, by + 1.0, bz + 0.5, 12, 3.0, 1.5, 3.0, 0.4);
            }
        }
        if (age % 40 == 0 && age < 240) {
            double a = level.random.nextDouble() * Math.PI * 2, d = k.r * 0.6 * Math.sqrt(level.random.nextDouble());
            int bx = (int) Math.floor(k.x + Math.cos(a) * d), bz = (int) Math.floor(k.z + Math.sin(a) * d);
            if (level.hasChunkAt(new BlockPos(bx, level.getMinBuildHeight() + 1, bz))) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(bx + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), bz + 0.5);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }
        }
    }

    static boolean inChaff(FlightManager mgr, double x, double z) {
        for (Task k : mgr.tasks) {
            if (k.kind != CHAFF) continue;
            double dx = x - k.x, dz = z - k.z;
            if (dx * dx + dz * dz <= k.r * k.r) return true;
        }
        return false;
    }

    static ListTag saveAll(List<Task> tasks) {
        ListTag l = new ListTag();
        for (Task k : tasks) l.add(k.save());
        return l;
    }

    static void loadAll(ListTag l, List<Task> into) {
        for (Tag t : l) {
            try { into.add(Task.load((CompoundTag) t)); }
            catch (RuntimeException e) { Wmd.LOGGER.warn("[wmd] skipped an unreadable effect task in the save: {}", e.toString()); }
        }
    }

    static int clampTier(int t) { return Mth.clamp(t, 1, 4); }
}
