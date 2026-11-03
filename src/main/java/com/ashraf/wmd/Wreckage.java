package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
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
public final class Wreckage {

    private Wreckage() {}

    private static final class Piece {
        final ServerLevel level;
        double x, y, z, vx, vy, vz;
        int age;

        Piece(ServerLevel level, double x, double y, double z, double vx, double vy, double vz) {
            this.level = level; this.x = x; this.y = y; this.z = z; this.vx = vx; this.vy = vy; this.vz = vz;
        }
    }

    private static final List<Piece> PIECES = Statics.server(new ArrayList<>());

    static void spawn(ServerLevel level, Vec3 at, Vec3 vel, int tier) {
        int n = Balance.WRECK_PIECES[Math.max(0, Math.min(Balance.WRECK_PIECES.length - 1, tier - 1))];
        RandomSource r = level.random;
        float[] v = new float[n * 3];
        for (int i = 0; i < n && PIECES.size() < LoadGuard.scale(Balance.WRECK_MAX, 0.25); i++) {
            double vx = vel.x * Balance.WRECK_KEEP + (r.nextDouble() - 0.5) * Balance.WRECK_SCATTER, vy = vel.y * Balance.WRECK_KEEP + r.nextDouble() * Balance.WRECK_SCATTER * 0.5,
                    vz = vel.z * Balance.WRECK_KEEP + (r.nextDouble() - 0.5) * Balance.WRECK_SCATTER;
            PIECES.add(new Piece(level, at.x, at.y, at.z, vx, vy, vz));
            v[i * 3] = (float) vx; v[i * 3 + 1] = (float) vy; v[i * 3 + 2] = (float) vz;
        }
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, Balance.WRECK_VIEW, new WreckPayload(at.x, at.y, at.z, v));
    }

    static void hitSparks(ServerLevel level, Vec3 at) {
        for (ServerPlayer pl : level.players()) {
            if (pl.distanceToSqr(at) > Balance.WRECK_VIEW * (double) Balance.WRECK_VIEW) continue;
            level.sendParticles(pl, ParticleTypes.CRIT, true, at.x, at.y, at.z, 14, 0.5, 0.5, 0.5, 0.5);
            level.sendParticles(pl, ParticleTypes.SMOKE, true, at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.02);
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (PIECES.isEmpty() || !(event.getLevel() instanceof ServerLevel sl)) return;
        for (Iterator<Piece> it = PIECES.iterator(); it.hasNext(); ) {
            Piece p = it.next();
            if (p.level != sl) continue;
            p.vy -= Balance.WRECK_GRAVITY;
            p.vx *= Balance.WRECK_DRAG; p.vy *= Balance.WRECK_DRAG; p.vz *= Balance.WRECK_DRAG;
            p.x += p.vx; p.y += p.vy; p.z += p.vz;
            p.age++;
            BlockPos col = BlockPos.containing(p.x, sl.getMinBuildHeight(), p.z);
            if (p.age > Balance.WRECK_LIFE || p.y < sl.getMinBuildHeight() || !sl.hasChunkAt(col)) { it.remove(); continue; }
            int top = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
            if (p.y > top) continue;
            it.remove();
            land(sl, new Vec3(p.x, top, p.z));
        }
    }

    private static void land(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 8, 0.5, 0.4, 0.5, 0.03);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5F, 1.3F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(Balance.WRECK_HURT_R)))
            e.hurt(level.damageSources().fallingBlock(null), (float) Balance.WRECK_DAMAGE);
        BlockPos up = BlockPos.containing(at);
        BlockState below = level.getBlockState(up.below());
        if (level.random.nextFloat() < Balance.WRECK_FIRE && level.getBlockState(up).isAir() && below.isFaceSturdy(level, up.below(), net.minecraft.core.Direction.UP)
                && BlockRules.blastMay(level, up))
            level.setBlock(up, Blocks.FIRE.defaultBlockState(), 3);
    }
}
