package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class SableImpact {

    private SableImpact() {}

    private static final Map<Level, long[]> ANY = Statics.server(new java.util.WeakHashMap<>());

    static boolean anyShips(ServerLevel level) {
        long now = level.getGameTime();
        long[] c = ANY.get(level);
        if (c != null && c[0] == now) return c[1] != 0;
        SubLevelContainer con = SubLevelContainer.getContainer((Level) level);
        boolean any = con != null && !con.getAllSubLevels().isEmpty();
        ANY.put(level, new long[] { now, any ? 1 : 0 });
        return any;
    }

    static int collateral(ServerLevel level, Vec3 pos, double crater, float power, UUID shooter) {
        double cap = Config.Server.maxCraterRadius();
        power = (float) Math.min(power, cap / Math.max(0.1, Balance.SHIP_BLAST_REACH_PER_POWER));
        double near = Balance.SHIP_BLAST_REACH_PER_POWER * power;
        double reach = Math.max(near, Math.min(crater, cap));
        List<SableBridge.ShipInfo> ships = SableBridge.shipsIntersecting(level, new AABB(pos, pos).inflate(reach));
        if (ships.isEmpty()) return 0;
        record Cand(SableBridge.ShipInfo s, double d) {}
        List<Cand> cands = new ArrayList<>();
        for (SableBridge.ShipInfo s : ships) {
            double d = distToBox(s.worldBounds(), pos);
            if (d > reach || ShipStrike.spare(level, s.id(), shooter)) continue;
            cands.add(new Cand(s, d));
        }
        if (cands.isEmpty()) return 0;
        cands.sort(Comparator.comparingDouble(Cand::d));
        if (cands.size() > Math.max(1, Balance.SHIP_BLAST_MAX_SHIPS)) cands = new ArrayList<>(cands.subList(0, Math.max(1, Balance.SHIP_BLAST_MAX_SHIPS)));
        Set<UUID> allowed = new HashSet<>();
        for (Cand c : cands) if (SubHits.mayHole(level, c.s())) allowed.add(c.s().id());
        for (Cand c : cands) ShipBreakup.hit(level, c.s(), c.d() <= near ? pos : closest(c.s().worldBounds(), pos), power);
        for (Cand c : cands) SubHits.hit(level, c.s(), c.d() <= near ? pos : closest(c.s().worldBounds(), pos), power, c.d());
        int n = 0;
        if (cands.get(0).d() <= near) {
            explode(level, pos, power, allowed);
            for (Cand c : cands) if (c.d() <= near && allowed.contains(c.s().id())) underwater(level, c.s(), pos, power);
            n++;
            for (Cand c : cands) if (c.d() <= near) push(level, c.s(), pos, power);
        }
        for (Cand c : cands) {
            if (c.d() <= near) continue;
            float p = (float) Math.max(1.0, power * (1.0 - (c.d() - near) / Math.max(1.0, reach - near)));
            Vec3 at = closest(c.s().worldBounds(), pos);
            if (allowed.contains(c.s().id())) { explode(level, at, p, Set.of(c.s().id())); underwater(level, c.s(), at, p); }
            push(level, c.s(), at, p);
            n++;
        }
        return n;
    }

    static void underwater(ServerLevel level, SableBridge.ShipInfo ship, Vec3 world, float power) {
        if (ship == null || level.getFluidState(BlockPos.containing(world)).isEmpty()) return;
        Vec3 p = SableBridge.toShip(level, ship.id(), world);
        if (p == null) { Wmd.LOGGER.info("[ship-blast] underwater: no plot point for ship {} at {}", ship.id(), world); return; }

        int n = carve(level, ship.id(), p, power);
        if (n > 0) level.playSound(null, world.x, world.y, world.z, WmdSounds.HULL_BREACH.get(), net.minecraft.sounds.SoundSource.BLOCKS, 3.0F, 1.0F);
        Wmd.LOGGER.info("[ship-blast] underwater: ship {} power {} at {} (plot {}) -> {} block(s) broken", ship.id(), power, BlockPos.containing(world).toShortString(),
                BlockPos.containing(p).toShortString(), n);
    }

    static int carve(ServerLevel level, UUID shipId, Vec3 p, float power) {
        if (!Config.Server.blockDamage()) return 0;
        double r = Balance.SHIP_UW_HOLE_PER_POWER * power + 1.0;
        int ri = (int) Math.ceil(r), n = 0;
        BlockPos c = BlockPos.containing(p);
        Map<Long, Boolean> mine = new HashMap<>();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-ri, -ri, -ri), c.offset(ri, ri, ri))) {
            double d = Math.sqrt(q.distToCenterSqr(p));
            if (d > r || level.isOutsideBuildHeight(q.getY())) continue;
            LevelChunk ch = level.getChunkSource().getChunkNow(q.getX() >> 4, q.getZ() >> 4);
            if (ch == null) continue;
            BlockState st = ch.getBlockState(q);
            if (st.isAir() || st.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) continue;
            long key = net.minecraft.world.level.ChunkPos.asLong(q.getX() >> 4, q.getZ() >> 4);
            final BlockPos qq = q.immutable();
            if (!mine.computeIfAbsent(key, k -> shipId.equals(SableBridge.shipIdAt(level, qq)))) continue;
            if (st.getDestroySpeed(level, q) < 0.0F) continue;
            float res = hullToughness(level, q, st);
            if (power * (0.7F + 0.6F * level.random.nextFloat()) - 0.75 * d - 0.3 * (res + 0.3) <= 0.0) continue;
            Vec3 w = SableBridge.toWorld(level, Vec3.atCenterOf(q));
            if (!Config.Server.canBreakShip(level, BlockPos.containing(w))) continue;
            if (ch.getBlockEntity(q) instanceof net.minecraft.world.Container box) {
                for (int i = 0; i < box.getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack s = box.getItem(i);
                    if (!s.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, w.x, w.y, w.z, s);
                }
                box.clearContent();
            }
            level.setBlock(qq, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
            if (n++ % 4 == 0) level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, st), w.x, w.y, w.z, 6, 0.4, 0.4, 0.4, 0.05);
            if (n >= Balance.SHIP_UW_HOLE_MAX) break;
        }
        return n;
    }

    static float hullToughness(ServerLevel level, BlockPos q, BlockState st) {
        float res = st.getBlock().getExplosionResistance();
        int rating = Hardened.rating(level, q, st);
        if (rating > 0) return (float) (Balance.SHIP_UW_TOUGH_CAP + Balance.SHIP_UW_TOUGH_PER_RATING * rating);
        return (float) Math.min(res, Balance.SHIP_UW_TOUGH_CAP);
    }

    static void explode(ServerLevel level, Vec3 at, float power, Set<UUID> allowed) {
        Level.ExplosionInteraction mode = Config.Server.blockDamage() ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE;

        level.explode(null, null, new ShipOnly(level, allowed), at.x, at.y, at.z, power, false, mode, false, net.minecraft.core.particles.ParticleTypes.BUBBLE,
                net.minecraft.core.particles.ParticleTypes.BUBBLE, net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(net.minecraft.sounds.SoundEvents.EMPTY));
    }

    private static final class ShipOnly extends ExplosionDamageCalculator {
        private final ServerLevel level;
        private final Set<UUID> allowed;
        private final Map<Long, UUID> chunkShip = new HashMap<>();
        private static final UUID NONE = new UUID(0L, 0L);

        ShipOnly(ServerLevel level, Set<UUID> allowed) { this.level = level; this.allowed = allowed; }

        private boolean allowedBlock(BlockPos pos) {
            long key = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            UUID id = chunkShip.computeIfAbsent(key, k -> { UUID u = SableBridge.shipIdAt(level, pos); return u == null ? NONE : u; });
            return id != NONE && allowed.contains(id);
        }

        @Override
        public boolean shouldBlockExplode(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state, float power) {
            if (!allowedBlock(pos)) return false;
            if (!Config.Server.canBreakShip(level, BlockPos.containing(SableBridge.worldPos(level, pos)))) return false;
            return super.shouldBlockExplode(explosion, reader, pos, state, power);
        }

        @Override
        public java.util.Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state,
                                                                    net.minecraft.world.level.material.FluidState fluid) {
            if (fluid.isEmpty()) return super.getBlockExplosionResistance(explosion, reader, pos, state, fluid);
            return state.isAir() || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock ? java.util.Optional.empty()
                    : java.util.Optional.of(state.getExplosionResistance(reader, pos, explosion));
        }

        private boolean onShip(Entity e) {
            UUID id = SableBridge.shipUnder(e);
            return id != null && allowed.contains(id);
        }

        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) { return onShip(entity) && super.shouldDamageEntity(explosion, entity); }

        @Override
        public float getKnockbackMultiplier(Entity entity) { return onShip(entity) ? super.getKnockbackMultiplier(entity) : 0.0F; }
    }

    private static boolean pushBroken;

    static void shove(ServerLevel level, SableBridge.ShipInfo ship, Vec3 at, float power) { push(level, ship, at, power); }

    private static void push(ServerLevel level, SableBridge.ShipInfo ship, Vec3 at, float power) {
        if (pushBroken || Balance.SHIP_BLAST_PUSH <= 0.0) return;
        try {
            SubLevelContainer c = SubLevelContainer.getContainer((Level) level);
            SubLevel s = c == null ? null : c.getSubLevel(ship.id());
            if (!(s instanceof ServerSubLevel ss) || ss.isRemoved()) return;
            Vec3 p = SableBridge.toShip(level, ship.id(), at), mid = SableBridge.toShip(level, ship.id(), ship.worldCenter());
            if (p == null || mid == null) return;
            Vec3 dir = mid.subtract(p);
            if (dir.lengthSqr() < 1.0E-6) dir = new Vec3(0.0, 1.0, 0.0);
            RigidBodyHandle h = RigidBodyHandle.of(ss);
            if (h == null || !h.isValid()) return;
            h.applyImpulseAtPoint(p, dir.normalize().scale(Balance.SHIP_BLAST_PUSH * power));
        } catch (Throwable t) {
            pushBroken = true;
            Wmd.LOGGER.warn("WMD ship collateral: pushing a Sable ship failed, ship pushes are off for this session (blasts still damage ships)", t);
        }
    }

    private static double distToBox(AABB b, Vec3 p) { return closest(b, p).distanceTo(p); }

    private static Vec3 closest(AABB b, Vec3 p) {
        return new Vec3(Math.max(b.minX, Math.min(b.maxX, p.x)), Math.max(b.minY, Math.min(b.maxY, p.y)), Math.max(b.minZ, Math.min(b.maxZ, p.z)));
    }

    private static final Map<String, long[]> FRIENDLY = Statics.server(new HashMap<>());
    static final int FRIENDLY_CACHE_TICKS = 40;

    static boolean friendly(ServerLevel level, UUID ship, UUID shooter) {
        String key = ship + "|" + shooter;
        long now = level.getGameTime();
        long[] c = FRIENDLY.get(key);
        if (c != null && now - c[0] >= 0 && now - c[0] < FRIENDLY_CACHE_TICKS) return c[1] != 0;
        boolean any = false, all = true;
        for (BlockPos p : SableBridge.blockEntitiesOn(level, ship)) {
            if (!(level.getBlockEntity(p) instanceof Owned o) || o.getOwner() == null) continue;
            any = true;
            if (!Access.trusts(o, shooter) && !ShipStrike.sameSide(shooter, o.getOwner())) { all = false; break; }
        }
        if (all) for (ServerPlayer pl : level.players()) {
            if (pl.isSpectator() || !ship.equals(SableBridge.shipUnder(pl))) continue;
            any = true;
            if (!ShipStrike.sameSide(shooter, pl.getUUID())) { all = false; break; }
        }
        boolean f = any && all;
        if (FRIENDLY.size() > 512) FRIENDLY.clear();
        FRIENDLY.put(key, new long[] { now, f ? 1 : 0 });
        return f;
    }

    static void machinesInField(ServerLevel level, double x, double z, double r, Consumer<BlockEntity> fn) {
        if (!anyShips(level)) return;
        AABB box = new AABB(x - r, level.getMinBuildHeight() - 1024, z - r, x + r, level.getMaxBuildHeight() + 4096, z + r);
        double r2 = r * r;
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(level, box)) {
            for (BlockPos p : SableBridge.blockEntitiesOn(level, s.id())) {
                Vec3 w = SableBridge.worldPos(level, p);
                double dx = w.x - x, dz = w.z - z;
                if (dx * dx + dz * dz > r2) continue;
                BlockEntity be = level.getBlockEntity(p);
                if (be != null && !be.isRemoved()) fn.accept(be);
            }
        }
    }

    static final int AUTO = 0, CLIP = 1, WALK = 2;
    static int mode = AUTO;
    private static boolean clipBroken;

    static String modeName() { return mode == CLIP ? "clip" : mode == WALK ? "walk" : "auto"; }

    static ProjectilePath.PathHit exact(ServerLevel level, Vec3 from, Vec3 to, List<SableBridge.ShipInfo> ships) {
        if (ships.isEmpty() || from.distanceToSqr(to) < 1.0E-8) return null;
        if (mode != WALK && !clipBroken) {
            try {
                ProjectilePath.PathHit h = viaClip(level, from, to, ships);
                if (h != null || mode == CLIP) return h;
            } catch (Throwable t) {
                clipBroken = true;
                Wmd.LOGGER.warn("WMD path test: Sable's clip overwrite is not usable ({}); ships are tested with the voxel walk from now on", t.toString());
            }
        }
        return viaWalk(level, from, to, ships);
    }

    private static ProjectilePath.PathHit viaClip(ServerLevel level, Vec3 from, Vec3 to, List<SableBridge.ShipInfo> ships) {
        Set<UUID> ids = new HashSet<>();
        for (SableBridge.ShipInfo s : ships) ids.add(s.id());
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        ClipContextExtension ext = (ClipContextExtension) (Object) ctx;
        ext.sable$setIgnoreMainLevel(true);
        ext.sable$setSubLevelIgnoring(s -> s == null || !ids.contains(s.getUniqueId()));
        BlockHitResult r = level.clip(ctx);
        if (r == null || r.getType() != HitResult.Type.BLOCK) return null;
        Vec3 loc = r.getLocation();
        UUID id = SableBridge.shipIdAt(level, r.getBlockPos());
        if (SableBridge.isOnShip(level, BlockPos.containing(loc))) loc = SableBridge.toWorld(level, loc);
        if (!new AABB(from, to).inflate(2.0).contains(loc)) return null;
        SableBridge.ShipInfo hit = null;
        for (SableBridge.ShipInfo s : ships) if (s.id().equals(id)) hit = s;
        if (hit == null) for (SableBridge.ShipInfo s : ships) if (s.worldBounds().inflate(1.0).contains(loc)) { hit = s; break; }
        if (hit == null) return null;
        return new ProjectilePath.PathHit(loc, ProjectilePath.Kind.SHIP, hit.id(), hit.name(), "clip");
    }

    private static ProjectilePath.PathHit viaWalk(ServerLevel level, Vec3 from, Vec3 to, List<SableBridge.ShipInfo> ships) {
        ProjectilePath.PathHit best = null;
        double bd = Double.MAX_VALUE;
        for (SableBridge.ShipInfo s : ships) {
            Vec3 a = SableBridge.toShip(level, s.id(), from), b = SableBridge.toShip(level, s.id(), to);
            if (a == null || b == null) continue;
            Vec3 plot = walk(level, a, b);
            if (plot == null) continue;
            Vec3 w = SableBridge.toWorld(level, plot);
            double d = w.distanceToSqr(from);
            if (d < bd) { bd = d; best = new ProjectilePath.PathHit(w, ProjectilePath.Kind.SHIP, s.id(), s.name(), "walk"); }
        }
        return best;
    }

    static Vec3 walk(ServerLevel level, Vec3 a, Vec3 b) {
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) return null;
        int x = (int) Math.floor(a.x), y = (int) Math.floor(a.y), z = (int) Math.floor(a.z);
        int ex = (int) Math.floor(b.x), ey = (int) Math.floor(b.y), ez = (int) Math.floor(b.z);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0, sy = dy > 0 ? 1 : dy < 0 ? -1 : 0, sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tdx = sx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx), tdy = sy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy), tdz = sz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double tmx = sx == 0 ? Double.MAX_VALUE : (sx > 0 ? (x + 1 - a.x) : (a.x - x)) * tdx;
        double tmy = sy == 0 ? Double.MAX_VALUE : (sy > 0 ? (y + 1 - a.y) : (a.y - y)) * tdy;
        double tmz = sz == 0 ? Double.MAX_VALUE : (sz > 0 ? (z + 1 - a.z) : (a.z - z)) * tdz;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int max = (int) Math.ceil(len) * 3 + 6;
        for (int i = 0; i < max; i++) {
            p.set(x, y, z);
            LevelChunk ch = level.isOutsideBuildHeight(y) ? null : level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (ch != null) {
                BlockState st = ch.getBlockState(p);
                if (!st.isAir()) {
                    VoxelShape sh = st.getCollisionShape(level, p);
                    if (!sh.isEmpty()) {
                        BlockHitResult r = sh.clip(a, b, p.immutable());
                        if (r != null && r.getType() == HitResult.Type.BLOCK) return r.getLocation();
                    }
                }
            }
            if (x == ex && y == ey && z == ez) break;
            if (tmx < tmy && tmx < tmz) { if (tmx > 1.0) break; x += sx; tmx += tdx; }
            else if (tmy < tmz) { if (tmy > 1.0) break; y += sy; tmy += tdy; }
            else { if (tmz > 1.0) break; z += sz; tmz += tdz; }
        }
        return null;
    }

    static int breakAlong(ServerLevel level, UUID shipId, Vec3 from, Vec3 to, int maxBlocks, float maxHardness, UUID shooter) {
        SableBridge.ShipInfo ship = SableBridge.shipInfo(level, shipId);
        if (ship == null || maxBlocks <= 0) return 0;
        AABB box = ship.worldBounds().inflate(1.5);
        Vec3 in = box.contains(from) ? from : box.clip(from, to).orElse(null);
        if (in == null) return 0;
        Vec3 a = SableBridge.toShip(level, shipId, in);
        Vec3 b = SableBridge.toShip(level, shipId, box.contains(to) ? to : box.clip(to, from).orElse(to));
        if (a == null || b == null) return 0;
        int[] count = { 0 };
        walkCells(a, b, p -> {
            net.minecraft.world.level.chunk.LevelChunk ch = level.isOutsideBuildHeight(p.getY()) ? null : level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
            if (ch == null) return true;
            net.minecraft.world.level.block.state.BlockState st = ch.getBlockState(p);
            if (st.isAir()) return true;
            if (st.getCollisionShape(level, p).isEmpty()) return true;
            float h = st.getDestroySpeed(level, p);
            if (h < 0.0F || h > maxHardness) return false;
            Vec3 w = SableBridge.toWorld(level, Vec3.atCenterOf(p));
            if (!Config.Server.canBreakShip(level, BlockPos.containing(w))) return false;
            if (ch.getBlockEntity(p) instanceof net.minecraft.world.Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack s = c.getItem(i);
                    if (!s.isEmpty()) net.minecraft.world.Containers.dropItemStack(level, w.x, w.y, w.z, s);
                }
                c.clearContent();
            }
            level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, st), w.x, w.y, w.z, 6, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, w.x, w.y, w.z, st.getSoundType().getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 0.9F + level.random.nextFloat() * 0.2F);
            return ++count[0] < maxBlocks;
        });
        return count[0];
    }

    private static void walkCells(Vec3 a, Vec3 b, java.util.function.Predicate<BlockPos> visitor) {
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) return;
        int x = (int) Math.floor(a.x), y = (int) Math.floor(a.y), z = (int) Math.floor(a.z);
        int ex = (int) Math.floor(b.x), ey = (int) Math.floor(b.y), ez = (int) Math.floor(b.z);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0, sy = dy > 0 ? 1 : dy < 0 ? -1 : 0, sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tdx = sx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx), tdy = sy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy), tdz = sz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double tmx = sx == 0 ? Double.MAX_VALUE : (sx > 0 ? (x + 1 - a.x) : (a.x - x)) * tdx;
        double tmy = sy == 0 ? Double.MAX_VALUE : (sy > 0 ? (y + 1 - a.y) : (a.y - y)) * tdy;
        double tmz = sz == 0 ? Double.MAX_VALUE : (sz > 0 ? (z + 1 - a.z) : (a.z - z)) * tdz;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int max = (int) Math.ceil(len) * 3 + 6;
        for (int i = 0; i < max; i++) {
            p.set(x, y, z);
            if (!visitor.test(p.immutable())) return;
            if (x == ex && y == ey && z == ez) return;
            if (tmx < tmy && tmx < tmz) { if (tmx > 1.0) return; x += sx; tmx += tdx; }
            else if (tmy < tmz) { if (tmy > 1.0) return; y += sy; tmy += tdy; }
            else { if (tmz > 1.0) return; z += sz; tmz += tdz; }
        }
    }

    static void clearCaches() {
        FRIENDLY.clear();
        ANY.clear();
    }
}
