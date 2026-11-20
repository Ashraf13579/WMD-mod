package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class PlasmaMortarServer {

    private PlasmaMortarServer() {}

    static final int MELT_AT = 30;

    private static final class Burn {
        final ResourceKey<Level> dim;
        final int x, y, z;
        final long start;
        final List<BlockPos> melted = new ArrayList<>();
        Burn(ResourceKey<Level> dim, int x, int y, int z, long start) { this.dim = dim; this.x = x; this.y = y; this.z = z; this.start = start; }
    }

    private static final List<Burn> BURNS = Statics.server(new ArrayList<>());

    static double holdRadius() { return Math.max(WarheadFx.holdRadius(WarheadType.PLASMA_BALL.tier, WarheadType.PLASMA_BALL), Balance.PMORT_FIRE_R + 8.0); }

    private static UUID realOwner(ArtShell s) {
        UUID o = s.owner();
        return o != null && (o.getMostSignificantBits() != 0L || o.getLeastSignificantBits() != 0L) ? o : null;
    }

    static void impact(ServerLevel level, ArtShell s, long now) {
        int gx = (int) Math.floor(s.tx()), gz = (int) Math.floor(s.tz());
        FlightManager fm = FlightManager.get(level);
        fm.holdForTest(level, gx, gz, holdRadius());
        int gy = ArtilleryManager.ground(level, gx, gz, s.ty(), true);
        Vec3 v = s.velocity(s.endTime());
        UUID prev = ShipStrike.begin(realOwner(s));
        try { WarheadFx.detonate(level, fm, gx, gy, gz, WarheadType.PLASMA_BALL.tier, WarheadType.PLASMA_BALL, v.x, v.z); }
        finally { ShipStrike.end(prev); }
        fm.setDirty();
        BURNS.add(new Burn(level.dimension(), gx, gy, gz, now));
        PacketDistributor.sendToPlayersNear(level, null, gx + 0.5, gy, gz + 0.5, ArtilleryManager.NEAR,
                new ArtImpactPayload(s.id(), s.gun(), s.type(), gx + 0.5, gy, gz + 0.5, ArtImpactPayload.IMPACT));
        UUID o = realOwner(s);
        if (o != null) CcBattleLog.add(level.getServer(), o, CcBattleLog.HIT, "log.wmd.plasma_mortar_hit", Integer.toString(gx), Integer.toString(gz));
        Wmd.LOGGER.info("Plasma mortar ball #{} of {} landed at {} {} {}", s.id(), s.ownerName(), gx, gy, gz);
    }

    static void endedEarly(ServerLevel level, ArtShell s, Vec3 p, long now) {
        FlightManager fm = FlightManager.get(level);
        int gx = (int) Math.floor(p.x), gy = (int) Math.floor(p.y), gz = (int) Math.floor(p.z);
        Vec3 v = s.velocity(now);
        UUID prev = ShipStrike.begin(realOwner(s));
        try { WarheadFx.detonate(level, fm, gx, gy, gz, WarheadType.PLASMA_BALL.tier, WarheadType.PLASMA_BALL, v.x, v.z); }
        finally { ShipStrike.end(prev); }
        fm.setDirty();
        PacketDistributor.sendToPlayersNear(level, null, p.x, p.y, p.z, ArtilleryManager.NEAR,
                new ArtImpactPayload(s.id(), s.gun(), s.type(), p.x, p.y, p.z, ArtImpactPayload.IMPACT));
        UUID o = realOwner(s);
        if (o != null) CcBattleLog.add(level.getServer(), o, CcBattleLog.HIT, "log.wmd.plasma_mortar_hit", Integer.toString(gx), Integer.toString(gz));
        Wmd.LOGGER.info("Plasma mortar ball #{} of {} struck a ship in flight at {} {} {}", s.id(), s.ownerName(), gx, gy, gz);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || BURNS.isEmpty()) return;
        long now = level.getGameTime();
        for (int i = 0; i < BURNS.size(); i++) {
            Burn b = BURNS.get(i);
            if (b.dim != level.dimension()) continue;
            long age = now - b.start;
            if (age == MELT_AT) { melt(level, b); fire(level, b); }
            else if (age > MELT_AT && age < Balance.PMORT_BURN && (age - MELT_AT) % Balance.PMORT_FIRE_RENEW == 0) fire(level, b);
            if (age > MELT_AT && age < Balance.PMORT_BURN && age % 20 == 0) scorch(level, b, age);
            if (age >= Balance.PMORT_BURN) { cool(level, b); BURNS.remove(i--); }
        }
    }

    private static void melt(ServerLevel level, Burn b) {
        int r = (int) Math.ceil(Balance.PMORT_MELT_R);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > Balance.PMORT_MELT_R - level.random.nextDouble() * 0.8) continue;
            int x = b.x + dx, z = b.z + dz;
            p.set(x, level.getMinBuildHeight() + 1, z);
            if (!level.hasChunkAt(p)) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (top < b.y - 12 || top > b.y + 6) continue;
            p.set(x, top, z);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty() || !s.isCollisionShapeFullBlock(level, p) || !BlockRules.blastChangeable(level, p, s)) continue;
            BlastWrites.set(level, p, magma, Block.UPDATE_ALL);
            b.melted.add(p.immutable());
        }
        level.playSound(null, b.x + 0.5, b.y + 0.5, b.z + 0.5, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 6.0F, 0.6F);
    }

    private static void fire(ServerLevel level, Burn b) {
        int r = (int) Math.ceil(Balance.PMORT_FIRE_R);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState fire = Wmd.NAPALM_FIRE.get().defaultBlockState();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > Balance.PMORT_FIRE_R || level.random.nextFloat() < 0.45F) continue;
            int x = b.x + dx, z = b.z + dz;
            p.set(x, level.getMinBuildHeight() + 1, z);
            if (!level.hasChunkAt(p)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y < b.y - 12 || y > b.y + 7) continue;
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (s.is(Wmd.NAPALM_FIRE.get())) { if (BlockRules.blastMay(level, p)) BlastWrites.set(level, p, fire, Block.UPDATE_ALL); continue; }
            if ((s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty() && NapalmFireBlock.canSit(level, p) && BlockRules.blastMay(level, p))
                BlastWrites.set(level, p, fire, Block.UPDATE_ALL);
        }
    }

    private static void scorch(ServerLevel level, Burn b, long age) {
        double R = Balance.PMORT_FIRE_R;
        AABB box = new AABB(b.x + 0.5 - R, b.y - 4, b.z + 0.5 - R, b.x + 0.5 + R, b.y + 6, b.z + 0.5 + R);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = e.getX() - (b.x + 0.5), dz = e.getZ() - (b.z + 0.5);
            if (dx * dx + dz * dz > R * R) continue;
            e.igniteForSeconds(Balance.PMORT_BURN_SEC);
            e.hurt(level.damageSources().inFire(), (float) Balance.PMORT_BURN_DMG);
        }
        if (age % 80 == 0) level.playSound(null, b.x + 0.5, b.y + 0.5, b.z + 0.5, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 5.0F, 0.5F);
    }

    private static void cool(ServerLevel level, Burn b) {
        double inner = Balance.PMORT_MELT_R * 0.5;
        for (BlockPos p : b.melted) {
            if (!level.hasChunkAt(p)) continue;
            BlockState s = level.getBlockState(p);
            if (!s.is(Blocks.MAGMA_BLOCK) || !BlockRules.blastChangeable(level, p, s)) continue;
            double d = Math.sqrt((p.getX() - b.x) * (p.getX() - b.x) + (p.getZ() - b.z) * (p.getZ() - b.z));
            BlastWrites.set(level, p, (d <= inner ? Blocks.OBSIDIAN : Blocks.BASALT).defaultBlockState(), Block.UPDATE_ALL);
        }
        level.playSound(null, b.x + 0.5, b.y + 0.5, b.z + 0.5, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 4.0F, 0.9F);
    }

    static int burning() { return BURNS.size(); }
}
