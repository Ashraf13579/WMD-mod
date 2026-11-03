package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MirrorBurn {

    private MirrorBurn() {}

    private static final Map<Long, int[]> HEAT = new HashMap<>();
    private static long clock;

    static void clearStatics() { HEAT.clear(); clock = 0L; }

    private static long key(ServerLevel l, int x, int z) { return ((long) l.dimension().location().hashCode() << 52) ^ BlockPos.asLong(x, 0, z); }

    static void second(MinecraftServer server) {
        clock++;
        for (double[] sp : MirrorFocus.spots()) {
            int slot = (int) sp[0];
            ServerLevel gl = server.getLevel(Orbit.ground(server, slot).dim());
            if (gl == null) continue;
            boolean burn = (int) sp[1] == MirrorFocus.BURNING;
            boolean igniting = burn && sp.length > 4 && server.overworld().getGameTime() - (long) sp[4] < Balance.MIRROR_IGNITE_TICKS;
            if (igniting) burn = false;
            int x = (int) Math.floor(sp[2]), z = (int) Math.floor(sp[3]);
            int y = gl.hasChunkAt(new BlockPos(x, 64, z)) ? gl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : 64;

            boolean shielded = false;
            ForcefieldBlockEntity dome = burn && !ForcefieldBlockEntity.LOADED.isEmpty() ? Forcefield.domeOver(gl, new net.minecraft.world.phys.Vec3(x + 0.5, y, z + 0.5)) : null;
            if (dome != null) {
                net.minecraft.world.phys.Vec3 c = dome.centre();
                double h2 = (x + 0.5 - c.x) * (x + 0.5 - c.x) + (z + 0.5 - c.z) * (z + 0.5 - c.z);
                double shell = c.y + Math.sqrt(Math.max(0.0, (double) dome.radius() * dome.radius() - h2));
                dome.absorb(gl, new net.minecraft.world.phys.Vec3(x + 0.5, shell, z + 0.5).subtract(c).normalize(), Balance.MIRROR_FF_FE, false);
                burn = false;
                shielded = true;
                y = (int) Math.max(y, shell);
            }
            if (burn) {
                if (clock % 5 == 0) FlightManager.get(gl).holdForTest(gl, x, z, Balance.MIRROR_SPOT_R + 8);
                if (clock % 5 == 0) gl.playSound(null, x + 0.5, y, z + 0.5, WmdSounds.MR_BURN_LOOP.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
                burn(gl, sp[2], sp[3]);
            }
            MirrorPayloads.Beam beam = new MirrorPayloads.Beam(slot, burn || shielded ? 2 : igniting ? 3 : 1, (float) sp[2], y, (float) sp[3]);
            long r2 = (long) Balance.MIRROR_SEE_R * Balance.MIRROR_SEE_R;
            for (ServerPlayer p : gl.players()) {
                double dx = p.getX() - sp[2], dz = p.getZ() - sp[3];
                if (dx * dx + dz * dz <= r2) PacketDistributor.sendToPlayer(p, beam);
            }
        }
        if (clock % Balance.MIRROR_COOL_EVERY == 0)
            for (Iterator<int[]> it = HEAT.values().iterator(); it.hasNext(); ) { int[] h = it.next(); if (--h[0] <= 0) it.remove(); }
    }

    private static void burn(ServerLevel gl, double cx, double cz) {
        int r = Balance.MIRROR_SPOT_R;
        boolean guard = Config.Server.protectSpawnRadius() > 0 || Config.Server.respectClaims();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x0 = (int) Math.floor(cx), z0 = (int) Math.floor(cz);
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dz * dz > r * r) continue;
            int x = x0 + dx, z = z0 + dz;
            p.set(x, 64, z);
            if (!gl.hasChunkAt(p)) continue;
            int[] h = HEAT.computeIfAbsent(key(gl, x, z), k -> new int[1]);
            h[0] += 2;
            int s = h[0] / 2;
            int top = gl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            p.set(x, top, z);
            BlockState st = gl.getBlockState(p);
            if (guard && !Config.Server.canBreak(gl, p)) continue;
            if (st.hasBlockEntity() || st.getDestroySpeed(gl, p) < 0 || Hardened.rating(gl, p, st) > 0) continue;
            if (st.is(Blocks.WATER) || st.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                if (s >= 3) {
                    gl.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    gl.sendParticles(ParticleTypes.CLOUD, x + 0.5, top + 1.0, z + 0.5, 4, 0.4, 0.3, 0.4, 0.05);
                    if (gl.random.nextFloat() < 0.2F) gl.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 0.8F);
                }
                continue;
            }
            BlockState to = null;
            boolean core = dx * dx + dz * dz <= Balance.MIRROR_LAVA_R * Balance.MIRROR_LAVA_R;
            if (core && s >= Balance.MIRROR_LAVA_S && (st.is(Blocks.MAGMA_BLOCK) || st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.DIRT) || st.is(Blocks.COARSE_DIRT)
                    || st.is(Blocks.GRAVEL) || st.is(Blocks.COBBLESTONE) || st.is(Blocks.GLASS) || st.is(BlockTags.SAND))) to = Blocks.LAVA.defaultBlockState();
            else if (s >= Balance.MIRROR_WOOD_S && (st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.is(BlockTags.PLANKS) || st.is(BlockTags.WOOL)
                    || st.is(BlockTags.FLOWERS) || st.canBeReplaced() || st.ignitedByLava())) to = Blocks.AIR.defaultBlockState();
            else if (s >= Balance.MIRROR_MAGMA_S && (st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(Blocks.COBBLESTONE) || st.is(BlockTags.DIRT) || st.is(Blocks.COARSE_DIRT)
                    || st.is(Blocks.GRAVEL) || st.is(Blocks.BLACKSTONE))) to = Blocks.MAGMA_BLOCK.defaultBlockState();
            else if (s >= Balance.MIRROR_GLASS_S && st.is(BlockTags.SAND)) to = Blocks.GLASS.defaultBlockState();
            else if (s >= Balance.MIRROR_GLASS_S && (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM)))
                to = Blocks.COARSE_DIRT.defaultBlockState();
            if (to != null && !st.is(to.getBlock())) { gl.setBlock(p, to, Block.UPDATE_ALL); top = to.isAir() ? top - 1 : top; }
            if (s >= Balance.MIRROR_FIRE_S && gl.random.nextFloat() < 0.35F) {
                BlockPos up = new BlockPos(x, top + 1, z);
                if (gl.getBlockState(up).isAir()) gl.setBlock(up, BaseFireBlock.getState(gl, up), Block.UPDATE_ALL);
            }
            if (gl.random.nextFloat() < 0.25F) gl.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5, top + 1.2, z + 0.5, 1, 0.2, 0.3, 0.2, 0.02);
        }

        for (int i = 0; i < Balance.MIRROR_FIRE_TRIES; i++) {
            double a = gl.random.nextDouble() * Math.PI * 2.0, d = r + gl.random.nextDouble() * (Balance.MIRROR_FIRE_R - r);
            int x = (int) Math.floor(cx + Math.cos(a) * d), z = (int) Math.floor(cz + Math.sin(a) * d);
            if (!gl.hasChunkAt(new BlockPos(x, 64, z))) continue;
            BlockPos topP = new BlockPos(x, gl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z), up = topP.above();
            BlockState ts = gl.getBlockState(topP);
            if (!(ts.is(BlockTags.LEAVES) || ts.is(BlockTags.LOGS) || ts.is(BlockTags.PLANKS) || ts.is(BlockTags.WOOL) || ts.ignitedByLava())) continue;
            if (guard && !Config.Server.canBreak(gl, up)) continue;
            if (gl.getBlockState(up).isAir()) gl.setBlock(up, BaseFireBlock.getState(gl, up), Block.UPDATE_ALL);
        }

        AABB box = new AABB(cx - r - 0.5, -64, cz - r - 0.5, cx + r + 0.5, 400, cz + r + 0.5);
        for (LivingEntity e : gl.getEntitiesOfClass(LivingEntity.class, box)) {
            double dx = e.getX() - cx, dz = e.getZ() - cz;
            if (dx * dx + dz * dz > (r + 0.5) * (r + 0.5)) continue;
            int ground = gl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(e.getX()), (int) Math.floor(e.getZ()));
            if (e.getY() < ground - 2.0) continue;
            e.igniteForSeconds(5.0F);
            e.hurt(gl.damageSources().inFire(), Balance.MIRROR_HURT);
        }
    }
}
