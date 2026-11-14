package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Debris {

    private Debris() {}

    private record Job(List<BlockPos> blocks, BlockPos anchor, Vec3 vel, Vec3 off, BlockState look, boolean tree) {}

    private static long lastCalLog;

    private static final int FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;

    private static final class Piece {
        final UUID id;
        long until;
        final BlockState look;
        final int blocks;
        final Vec3 vel, off;
        int pushLeft;
        long pushAt, checkAt;
        Vec3 last, dir = Vec3.ZERO, velNow = Vec3.ZERO;
        AABB box;
        double speed, measured = -1.0;
        boolean collapse;
        Vec3 first;
        boolean freed;
        double peak;
        long landedAt;
        boolean cracked;
        long born;

        Piece(UUID id, long until, BlockState look, int blocks, Vec3 vel, Vec3 off, int pushLeft) {
            this.id = id; this.until = until; this.look = look; this.blocks = blocks; this.vel = vel; this.off = off; this.pushLeft = pushLeft;
        }
    }

    private static final class State {
        final ArrayDeque<Job> queue = new ArrayDeque<>();
        final List<Piece> live = new ArrayList<>();
        final Set<UUID> seen = new HashSet<>();
        boolean swept;
    }

    private static final Map<ResourceKey<Level>, State> STATES = new HashMap<>();

    private static State state(ServerLevel level) { return STATES.computeIfAbsent(level.dimension(), k -> new State()); }

    static void clearStatics() { STATES.clear(); lastCalLog = 0L; }

    static int liveCount(ServerLevel level) { State st = STATES.get(level.dimension()); return st == null ? 0 : st.live.size(); }

    static void adopt(ServerLevel level, UUID id, long until, BlockState look, int blocks, Vec3 vel, Vec3 off) {
        Piece p = new Piece(id, until, look, blocks, vel, off, vel.lengthSqr() > 0.0 ? 10 : 0);
        p.collapse = true;
        p.born = level.getGameTime();
        state(level).live.add(p);
    }

    public static int blast(ServerLevel level, int gx, int gy, int gz, double r, Crater crater) {
        return blast(level, gx, gy, gz, r, crater, 1.0, true);
    }

    public static int blast(ServerLevel level, int gx, int gy, int gz, double r, Crater crater, double share, boolean allowTrees) {
        if (level == null || r <= 0.0 || !Config.Server.debris() || !Config.Server.blockDamage()) return 0;
        RandomSource rnd = level.random;

        double raw = Math.round(Balance.DEBRIS_PIECES_PER_R * r + Balance.DEBRIS_PIECES_PER_R2 * r * r) + Balance.DEBRIS_PIECES_BASE;

        int pieces = Mth.clamp((int) Math.round(Math.min(raw, Config.Server.debrisPerBlast()) * share), 1, Config.Server.debrisPerBlast());
        if (r >= Balance.DEBRIS_BIG_R) pieces = Math.max(1, (int) Math.round(pieces * Balance.DEBRIS_BIG_SHARE));
        pieces = LoadGuard.scale(pieces, 0.0);
        if (pieces <= 0) return 0;
        double k = Mth.clamp(Math.sqrt(r / 10.0), 0.75, 1.8) * Config.Server.debrisSpeed();
        if (!SableBridge.debrisActive()) return fallback(level, gx, gy, gz, r, pieces * 2, k, rnd);
        int maxBlocks = Mth.clamp((int) Math.round(r * 1.5), 3, Balance.DEBRIS_BLOB_MAX);
        int trees = allowTrees && r >= 3.0 ? Math.min(Balance.DEBRIS_TREES_MAX, Math.max(1, pieces / 5)) : 0;
        State st = state(level);
        Set<BlockPos> taken = new HashSet<>();
        int planned = 0;

        for (int tries = 0; planned < pieces && tries < pieces * 4; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2.0, d = r * 0.85 * Math.sqrt(rnd.nextDouble());
            int x = gx + Mth.floor(Math.cos(a) * d + 0.5), z = gz + Mth.floor(Math.sin(a) * d + 0.5);
            if (!level.hasChunkAt(new BlockPos(x, gy, z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (Math.abs(top - gy) > r + 2.0) continue;
            BlockPos seed = new BlockPos(x, top, z);
            for (int dig = 1; dig < 3 && taken.contains(seed); dig++) seed = new BlockPos(x, top - dig, z);
            BlockState look = level.getBlockState(seed);
            if (taken.contains(seed) || !ground(level, seed, look)) continue;

            float roll = rnd.nextFloat();
            int want = roll < 0.55F ? Mth.nextInt(rnd, 1, 3)
                    : roll < 0.85F ? Mth.nextInt(rnd, 3, Math.max(4, maxBlocks / 3))
                    : Mth.nextInt(rnd, Math.max(3, maxBlocks / 3), maxBlocks);
            List<BlockPos> blob = gather(level, seed, want, false, taken);
            if (blob.isEmpty()) continue;
            Vec3 out = outward(gx, gz, x, z, rnd);

            float throwRoll = rnd.nextFloat();
            double h, v;
            if (throwRoll < 0.50F) { h = 1.0 + rnd.nextDouble() * 3.0; v = 3.0 + rnd.nextDouble() * 3.0; }
            else if (throwRoll < 0.85F) { h = 4.0 + rnd.nextDouble() * 4.0; v = 5.0 + rnd.nextDouble() * 4.0; }
            else { h = 8.0 + rnd.nextDouble() * 5.0; v = 8.0 + rnd.nextDouble() * 4.0; }
            h *= k;
            v *= k;
            Vec3 off = randomUnit(rnd).scale(0.15 + rnd.nextDouble() * 0.35);
            st.queue.add(new Job(blob, seed, new Vec3(out.x * h, v, out.z * h), off, look, false));
            planned++;
        }

        for (int tries = 0, made = 0; made < trees && tries < trees * 8; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2.0, d = r * (0.3 + rnd.nextDouble() * 1.3);
            int x = gx + Mth.floor(Math.cos(a) * d + 0.5), z = gz + Mth.floor(Math.sin(a) * d + 0.5);
            BlockPos log = findTrunk(level, x, z, taken);
            if (log == null || Math.abs(log.getY() - gy) > r + 12.0) continue;
            List<BlockPos> tree = gather(level, log, Balance.DEBRIS_TREE_MAX, true, taken);
            if (tree.size() < 4) continue;
            Vec3 out = outward(gx, gz, log.getX(), log.getZ(), rnd);
            boolean inside = d < r;
            double h = (inside ? 4.0 + rnd.nextDouble() * 6.0 : 2.0 + rnd.nextDouble() * 3.0) * k;
            double v = (inside ? 6.0 + rnd.nextDouble() * 6.0 : 1.0 + rnd.nextDouble() * 2.0) * k;
            Vec3 off = new Vec3(0.0, 2.0 + rnd.nextDouble() * 1.5, 0.0);
            st.queue.add(new Job(tree, log, new Vec3(out.x * h, v, out.z * h), off, level.getBlockState(log), true));
            planned++;
            made++;
        }
        if (planned > 0 && crater != null) crater.delay(st.queue.size() / Config.Server.debrisPerTick() + 2);
        return planned;
    }

    private static boolean ground(ServerLevel level, BlockPos p, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) return false;
        float hard = s.getDestroySpeed(level, p);
        if (hard < 0.0F || hard > Balance.DEBRIS_MAX_HARDNESS) return false;
        if (s.getCollisionShape(level, p).isEmpty()) return false;
        return Hardened.rating(level, p, s) == 0 && Config.Server.canBreak(level, p);
    }

    private static boolean treePart(ServerLevel level, BlockPos p, BlockState s) {
        if (!(s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) || s.hasBlockEntity() || !s.getFluidState().isEmpty()) return false;
        return Config.Server.canBreak(level, p);
    }

    private static BlockPos findTrunk(ServerLevel level, int x, int z, Set<BlockPos> taken) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return null;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = top; y > top - 24 && y > level.getMinBuildHeight(); y--) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (s.is(BlockTags.LOGS)) return taken.contains(p) ? null : p.immutable();
            if (!s.is(BlockTags.LEAVES) && !s.isAir()) return null;
            if (s.is(BlockTags.LEAVES)) {
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    p.set(x + dx, y, z + dz);
                    if (level.hasChunkAt(p) && level.getBlockState(p).is(BlockTags.LOGS) && !taken.contains(p)) return p.immutable();
                }
                p.set(x, y, z);
            }
        }
        return null;
    }

    private static final int[][] FACES = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };

    private static List<BlockPos> gather(ServerLevel level, BlockPos seed, int want, boolean tree, Set<BlockPos> taken) {
        List<BlockPos> out = new ArrayList<>(want);
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        open.add(seed);
        visited.add(seed);
        double br = Math.cbrt(want * 0.75 / Math.PI) + 0.9, br2 = br * br;
        while (!open.isEmpty() && out.size() < want) {
            BlockPos p = open.poll();
            out.add(p);
            taken.add(p);
            boolean pLog = tree && level.getBlockState(p).is(BlockTags.LOGS);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                int n = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                if (n == 0 || (n > 1 && !pLog)) continue;
                BlockPos q = p.offset(dx, dy, dz);
                if (!visited.add(q) || taken.contains(q) || !level.hasChunkAt(q)) continue;
                BlockState s = level.getBlockState(q);
                if (tree) {
                    if (!treePart(level, q, s) || (n > 1 && !s.is(BlockTags.LOGS))) continue;
                    double hx = q.getX() - seed.getX(), hz = q.getZ() - seed.getZ(), lim = s.is(BlockTags.LOGS) ? 4.5 : 3.5;
                    if (hx * hx + hz * hz > lim * lim || q.getY() < seed.getY() - 32) continue;
                } else {
                    if (!ground(level, q, s) || q.getY() > seed.getY() + 1 || q.distSqr(seed) > br2) continue;
                }
                open.add(q);
            }
        }
        return out;
    }

    private static Vec3 outward(int gx, int gz, int x, int z, RandomSource rnd) {
        double dx = x - gx, dz = z - gz, l = Math.sqrt(dx * dx + dz * dz);
        if (l < 0.5) { double a = rnd.nextDouble() * Math.PI * 2.0; return new Vec3(Math.cos(a), 0.0, Math.sin(a)); }
        return new Vec3(dx / l, 0.0, dz / l);
    }

    private static Vec3 randomUnit(RandomSource rnd) {
        Vec3 v = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian());
        double l = v.length();
        return l < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : v.scale(1.0 / l);
    }

    private static int fallback(ServerLevel level, int gx, int gy, int gz, double r, int n, double k, RandomSource rnd) {
        n = Math.min(n, Balance.DEBRIS_FALLBACK_MAX);
        int made = 0;
        for (int tries = 0; made < n && tries < n * 3; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2.0, d = r * 0.85 * Math.sqrt(rnd.nextDouble());
            int x = gx + Mth.floor(Math.cos(a) * d + 0.5), z = gz + Mth.floor(Math.sin(a) * d + 0.5);
            if (!level.hasChunkAt(new BlockPos(x, gy, z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (Math.abs(top - gy) > r + 2.0) continue;
            BlockPos p = new BlockPos(x, top, z);
            BlockState s = level.getBlockState(p);
            if (!ground(level, p, s)) continue;
            FallingBlockEntity e = FallingBlockEntity.fall(level, p, s);
            e.disableDrop();
            if (Config.Server.debrisHurts()) e.setHurtsEntities(1.0F, 10);
            Vec3 out = outward(gx, gz, x, z, rnd);
            double h = (5.0 + rnd.nextDouble() * 8.0) * k / 20.0, v = (9.0 + rnd.nextDouble() * 9.0) * k / 20.0;
            e.setDeltaMovement(out.x * h, v, out.z * h);
            e.hurtMarked = true;
            made++;
        }
        return made;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        long pt0 = ImpactProfiler.start();
        try { tickDebris(event); } finally { ImpactProfiler.add(ImpactProfiler.DEBRIS, pt0); }
    }

    private static void tickDebris(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State st = STATES.get(level.dimension());
        if (!SableBridge.debrisActive()) {
            if (st != null) { st.queue.clear(); st.live.clear(); st.seen.clear(); }
            return;
        }
        if (st == null) st = state(level);
        long now = level.getGameTime();
        if (!st.swept || now % Balance.DEBRIS_SWEEP_TICKS == 0) { st.swept = true; sweep(level, st, now); }
        if (st.queue.isEmpty() && st.live.isEmpty()) return;

        int perTick = Config.Server.debrisPerTick();
        for (int n = 0; n < perTick && !st.queue.isEmpty(); n++) assemble(level, st, st.queue.poll(), now);

        boolean hurts = Config.Server.debrisHurts();
        Iterator<Piece> it = st.live.iterator();
        while (it.hasNext()) {
            Piece p = it.next();
            if (p.pushLeft > 0 && now >= p.pushAt) {
                if (SableBridge.debrisPush(level, p.id, p.vel.scale(Balance.DEBRIS_PUSH_SCALE), p.off)) { p.pushLeft = 0; p.checkAt = now + 2; }
                else if (--p.pushLeft == 0) SableBridge.debrisWake(level, p.id);
            }

            int wakeEvery = p.collapse ? Balance.COLLAPSE_WAKE_TICKS : Balance.DEBRIS_WAKE_TICKS;
            if (p.pushLeft == 0 && p.speed < 0.05 && (now + p.blocks) % wakeEvery == 0) SableBridge.debrisWake(level, p.id);
            AABB b = SableBridge.debrisBounds(level, p.id);
            if (b == null) { it.remove(); continue; }
            if (now >= p.until) { vanish(level, p.look, p.blocks, b); removeBody(level, p.id); it.remove(); continue; }
            Vec3 c = b.getCenter();
            if (p.last != null) {
                Vec3 step = c.subtract(p.last);
                p.velNow = step.scale(20.0);
                p.speed = step.length() * 20.0;
                if (p.speed > 1.0E-3) p.dir = step.normalize();
            }
            p.last = c;
            p.box = b;
            if (p.collapse && p.first == null) p.first = c;
            if (p.collapse && !p.freed && c.distanceTo(p.first) >= Balance.COLLAPSE_FREE_DIST) p.freed = true;
            if (p.collapse) p.peak = Math.max(p.peak, p.speed);

            if (p.collapse && !p.cracked && p.landedAt == 0 && p.blocks >= Balance.COLLAPSE_SHATTER_MIN && p.peak >= Balance.COLLAPSE_SHATTER_SPEED && p.speed < 1.0) {
                p.landedAt = now;
                Aftermath.collapseDust(level, b.getCenter().x, b.minY, b.getCenter().z, (float) (Math.max(b.getXsize(), b.getZsize()) * 0.5 + 2.0));
            }
            if (p.collapse && !p.cracked && p.landedAt > 0 && now - p.landedAt >= Balance.COLLAPSE_SHATTER_DELAY) crack(level, st, p, b, now);
            if (p.collapse && !p.freed && now - p.born > Balance.COLLAPSE_STUCK_TICKS) {
                Wmd.LOGGER.info("[collapse] a stuck piece of {} blocks went as dust", p.blocks);
                vanish(level, p.look, p.blocks, b);
                removeBody(level, p.id);
                it.remove();
                continue;
            }
            if (p.checkAt > 0 && now >= p.checkAt) calibrate(level, p, now);
            if (hurts && p.speed >= Balance.DEBRIS_HURT_MIN_SPEED) hurt(level, p, b);
        }
        if (now % Balance.DEBRIS_SCAN_TICKS == 0 && !st.live.isEmpty()) fragments(level, st);
        if (st.live.isEmpty() && st.queue.isEmpty()) st.seen.clear();
    }

    private static void assemble(ServerLevel level, State st, Job j, long now) {
        int max = Config.Server.debrisMaxLive();
        if (max <= 0) return;

        List<BlockPos> from = new ArrayList<>(j.blocks().size());
        List<BlockState> states = new ArrayList<>(j.blocks().size());
        int y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
        for (BlockPos p : j.blocks()) {
            if (!level.hasChunkAt(p)) continue;
            BlockState s = level.getBlockState(p);
            if (!(ground(level, p, s) || treePart(level, p, s))) continue;
            from.add(p);
            states.add(s);
            y0 = Math.min(y0, p.getY());
            y1 = Math.max(y1, p.getY());
        }
        if (from.isEmpty()) return;
        if (st.seen.isEmpty()) for (SableBridge.DebrisScan s : SableBridge.debrisScan(level)) st.seen.add(s.id());
        while (st.live.size() >= max) {
            Piece old = st.live.remove(0);
            AABB b = SableBridge.debrisBounds(level, old.id);
            if (b != null) vanish(level, old.look, old.blocks, b);
            removeBody(level, old.id);
        }

        for (BlockPos p : from) level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
        int base = j.tree() ? 1 : y1 - y0 + 2, dy = base;
        for (int t = base; t <= base + 6; t++) if (allFree(level, from, t)) { dy = t; break; }
        List<BlockPos> to = new ArrayList<>(from.size());
        for (int i = 0; i < from.size(); i++) {
            BlockPos q = from.get(i).above(dy);
            if (level.isOutsideBuildHeight(q) || !level.hasChunkAt(q) || !level.getBlockState(q).isAir()) continue;
            level.setBlock(q, states.get(i), FLAGS);
            to.add(q);
        }
        if (to.isEmpty()) return;
        long life = Config.Server.debrisLifetimeSeconds() * 20L;
        long until = now + Math.max(40L, Math.round(life * (0.75 + level.random.nextDouble() * 0.5)));
        BlockPos anchor = to.contains(j.anchor().above(dy)) ? j.anchor().above(dy) : to.get(0);
        UUID id = SableBridge.debrisSpawn(level, anchor, to, until);
        if (id == null) {
            for (BlockPos q : to) level.setBlock(q, Blocks.AIR.defaultBlockState(), FLAGS);
            return;
        }
        st.seen.add(id);
        Piece p = new Piece(id, until, j.look(), to.size(), j.vel(), j.off(), Balance.DEBRIS_PUSH_TRIES);
        p.pushAt = now + 1;
        st.live.add(p);
    }

    private static void crack(ServerLevel level, State st, Piece p, AABB b, long now) {
        p.cracked = true;
        p.until = now + Math.round(Balance.COLLAPSE_RUBBLE_LIFE * (0.9 + level.random.nextDouble() * 0.2));
        SableBridge.debrisAdopt(level, p.id, p.until);
        int cut = SableBridge.debrisCrack(level, p.id, Balance.COLLAPSE_CRACK_SPACING);
        if (cut > 0) vanish(level, p.look, Math.min(60, cut), b);
        AABB near = b.inflate(2.0);
        for (Piece q : st.live) if (q != p && q.box != null && q.box.intersects(near)) SableBridge.debrisWake(level, q.id);
    }

    static boolean anyLive(ServerLevel level) {
        State st = STATES.get(level.dimension());
        return st != null && !st.live.isEmpty();
    }

    static Vec3 hitAlong(ServerLevel level, Vec3 from, Vec3 to) {
        State st = STATES.get(level.dimension());
        if (st == null || st.live.isEmpty()) return null;
        AABB seg = new AABB(from, to).inflate(0.5);
        Vec3 best = null;
        double bd = Double.MAX_VALUE;
        for (Piece p : st.live) {
            if (p.box == null || !p.box.intersects(seg)) continue;
            Vec3 a = SableBridge.toShip(level, p.id, from), b = SableBridge.toShip(level, p.id, to);
            if (a == null || b == null) continue;
            BlockHitResult r = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (r.getType() == HitResult.Type.MISS) continue;
            Vec3 w = SableBridge.toWorld(level, r.getLocation());
            if (w == null || w.distanceToSqr(r.getLocation()) < 1.0E-6) w = SableBridge.worldPos(level, r.getBlockPos());
            if (w == null || !p.box.inflate(2.0).contains(w)) continue;
            double d = w.distanceToSqr(from);
            if (d < bd) { bd = d; best = w; }
        }
        return best;
    }

    static void blastHit(ServerLevel level, Vec3 pos, double r) {
        State st = STATES.get(level.dimension());
        if (st == null || st.live.isEmpty() || r < 1.0) return;
        long now = level.getGameTime();
        double reach = r * Balance.DEBRIS_BLAST_REACH;
        for (Piece p : st.live) {
            AABB b = p.box;
            if (b == null) continue;
            double qx = Mth.clamp(pos.x, b.minX, b.maxX), qy = Mth.clamp(pos.y, b.minY, b.maxY), qz = Mth.clamp(pos.z, b.minZ, b.maxZ);
            double d = Math.sqrt((qx - pos.x) * (qx - pos.x) + (qy - pos.y) * (qy - pos.y) + (qz - pos.z) * (qz - pos.z));
            if (d > reach) continue;
            if (d <= r && !p.cracked && p.blocks >= Balance.COLLAPSE_SHATTER_MIN) crack(level, st, p, b, now);
            Vec3 away = b.getCenter().subtract(pos);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : away.normalize();
            double speed = Balance.DEBRIS_BLAST_PUSH * Balance.blastPower(r) * (1.0 - d / reach);
            Vec3 vel = away.scale(speed).add(0.0, speed * 0.4, 0.0);
            Vec3 off = new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.5);
            SableBridge.debrisWake(level, p.id);
            SableBridge.debrisPush(level, p.id, vel.scale(Balance.DEBRIS_PUSH_SCALE), off);
        }
    }

    private static void calibrate(ServerLevel level, Piece p, long now) {
        p.checkAt = 0L;
        double want = Math.hypot(p.vel.x, p.vel.z), got = Math.hypot(p.velNow.x, p.velNow.z);
        p.measured = got;
        if (want < 2.0 || now - lastCalLog < 600L) return;
        lastCalLog = now;
        Wmd.LOGGER.info(String.format("Debris throw check: wanted %.1f blocks/s sideways, measured %.1f (push scale %.2f)", want, got, Balance.DEBRIS_PUSH_SCALE));
    }

    private static boolean allFree(ServerLevel level, List<BlockPos> from, int dy) {
        for (BlockPos p : from) {
            BlockPos q = p.above(dy);
            if (level.isOutsideBuildHeight(q) || !level.hasChunkAt(q) || !level.getBlockState(q).isAir()) return false;
        }
        return true;
    }

    private static void hurt(ServerLevel level, Piece p, AABB b) {
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, b.inflate(0.4), LivingEntity::isAlive);
        if (near.isEmpty()) return;
        float dmg = (float) Math.min(Balance.DEBRIS_DMG_MAX, Balance.DEBRIS_DMG_PER_SPEED * p.speed * Math.cbrt(p.blocks));
        for (LivingEntity e : near) {
            if (!touches(level, p.id, e)) continue;
            if (e.hurt(level.damageSources().fallingBlock(null), dmg)) {
                e.push(p.dir.x * 0.6, 0.3, p.dir.z * 0.6);
                e.hurtMarked = true;
            }
        }
    }

    private static boolean touches(ServerLevel level, UUID id, LivingEntity e) {
        double h = e.getBbHeight();
        for (double f : new double[] { 0.15, 0.5, 0.9 }) {
            Vec3 q = SableBridge.toShip(level, id, e.position().add(0.0, h * f, 0.0));
            if (q == null) return false;
            BlockPos c = BlockPos.containing(q);
            if (!level.isLoaded(c)) return false;
            if (!level.getBlockState(c).isAir()) return true;
            for (int[] d : FACES) if (!level.getBlockState(c.offset(d[0], d[1], d[2])).isAir()) return true;
        }
        return false;
    }

    private static void vanish(ServerLevel level, BlockState look, int blocks, AABB b) {
        Vec3 c = b.getCenter();
        BlockState s = look == null || look.isAir() ? Blocks.DIRT.defaultBlockState() : look;
        int n = Math.min(60, 12 + blocks);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), c.x, c.y, c.z, n, b.getXsize() * 0.3, b.getYsize() * 0.3, b.getZsize() * 0.3, 0.15);
        level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, Math.min(12, 3 + blocks / 6), b.getXsize() * 0.25, b.getYsize() * 0.25, b.getZsize() * 0.25, 0.02);
        level.playSound(null, c.x, c.y, c.z, s.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    private static void fragments(ServerLevel level, State st) {
        for (SableBridge.DebrisScan s : SableBridge.debrisScan(level)) {
            if (!st.seen.add(s.id())) continue;
            Piece parent = null;
            for (Piece p : st.live) {
                if (p.id.equals(s.splitFrom()) || (p.box != null && p.box.inflate(3.0).intersects(s.bounds()))) { parent = p; break; }
            }
            if (parent == null) continue;
            if (!parent.id.equals(s.splitFrom())) {
                SableBridge.ShipInfo info = SableBridge.shipInfo(level, s.id());
                if (info == null || info.blockCount() > Balance.DEBRIS_TREE_MAX) continue;
            }
            if (SableBridge.debrisAdopt(level, s.id(), parent.until))
                st.live.add(new Piece(s.id(), parent.until, parent.look, Math.max(1, parent.blocks / 3), Vec3.ZERO, Vec3.ZERO, 0));
        }
    }

    private static final Set<UUID> NOT_REMNANT = Statics.server(new HashSet<>());

    private static final java.util.Map<UUID, Long> GONE = Statics.server(new java.util.HashMap<>());
    private static final long GONE_TICKS = 6_000L;

    private static boolean removeBody(ServerLevel level, UUID id) {
        GONE.put(id, level.getGameTime() + GONE_TICKS);
        return SableBridge.debrisRemove(level, id);
    }

    private static boolean oursLately(UUID parent, Set<UUID> mine, long now) {
        if (parent == null) return false;
        if (mine.contains(parent)) return true;
        Long t = GONE.get(parent);
        return t != null && t >= now;
    }

    private static void sweep(ServerLevel level, State st, long now) {
        Set<UUID> mine = new HashSet<>();
        for (Piece p : st.live) mine.add(p.id);
        GONE.values().removeIf(v -> v < now);
        for (SableBridge.DebrisScan s : SableBridge.debrisScan(level)) {
            if (mine.contains(s.id())) continue;
            if (s.until() < 0) {

                if (!NOT_REMNANT.contains(s.id()) && oursLately(s.splitFrom(), mine, now) && SableBridge.debrisRemnant(level, s.id())) {
                    Wmd.LOGGER.info("[debris] a lost remnant {} went (untagged natural lump)", s.id());
                    vanish(level, Blocks.DIRT.defaultBlockState(), 4, s.bounds());
                    removeBody(level, s.id());
                } else NOT_REMNANT.add(s.id());
                continue;
            }
            boolean child = s.splitFrom() != null && mine.contains(s.splitFrom());
            if (child && s.until() > now) { st.live.add(new Piece(s.id(), s.until(), Blocks.DIRT.defaultBlockState(), 4, Vec3.ZERO, Vec3.ZERO, 0)); continue; }
            vanish(level, Blocks.DIRT.defaultBlockState(), 4, s.bounds());
            removeBody(level, s.id());
        }
    }

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").requires(TestCmds::test).then(Commands.literal("debris").requires(TestCmds::test)
                        .executes(c -> test(c.getSource(), 10))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 160)).executes(c -> test(c.getSource(), IntegerArgumentType.getInteger(c, "radius"))))
                        .then(Commands.literal("at").requires(TestCmds::test).then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(c -> testAt(c.getSource(), net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(c, "pos"), 10))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 160)).executes(c -> testAt(c.getSource(),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(c, "pos"), IntegerArgumentType.getInteger(c, "radius"))))))
                        .then(Commands.literal("status").executes(c -> status(c.getSource())))
                        .then(Commands.literal("clear").executes(c -> clear(c.getSource()))))));
    }

    private static int test(CommandSourceStack src, int radius) {
        if (!(src.getEntity() instanceof ServerPlayer pl)) { src.sendFailure(Component.literal("Run this as a player (it throws debris where you look).")); return 0; }
        ServerLevel level = pl.serverLevel();
        Vec3 eye = pl.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(pl.getLookAngle().scale(256.0)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, pl));
        if (hit.getType() != HitResult.Type.BLOCK) { src.sendFailure(Component.literal("Look at the ground (256 blocks).")); return 0; }
        if (!Config.Server.debris()) { src.sendFailure(Component.literal("explosions.explosionDebris is off in the server config.")); return 0; }
        BlockPos p = hit.getBlockPos();
        int n = blast(level, p.getX(), p.getY() + 1, p.getZ(), radius, null);
        String how = SableBridge.debrisActive() ? "Sable pieces (assembled over the next ticks; /wmd dev debris status shows their speed)" : "falling blocks (Sable not active)";
        src.sendSuccess(() -> Component.literal("Debris test r=" + radius + " at " + p.toShortString() + ": " + n + " " + how + "."), false);
        return n;
    }

    private static int testAt(CommandSourceStack src, BlockPos pos, int radius) {
        ServerLevel level = src.getLevel();
        if (!level.hasChunkAt(pos)) { src.sendFailure(Component.literal("That spot is not loaded.")); return 0; }
        if (!Config.Server.debris()) { src.sendFailure(Component.literal("explosions.explosionDebris is off in the server config.")); return 0; }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        int n = blast(level, pos.getX(), y, pos.getZ(), radius, null);
        src.sendSuccess(() -> Component.literal("Debris test r=" + radius + " at " + pos.getX() + " " + y + " " + pos.getZ() + ": " + n + " pieces ("
                + (SableBridge.debrisActive() ? "Sable" : "falling blocks") + ")."), true);
        return n;
    }

    private static int status(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        State st = STATES.get(level.dimension());
        int live = st == null ? 0 : st.live.size(), queued = st == null ? 0 : st.queue.size();
        double mspt = level.getServer().getAverageTickTimeNanos() / 1.0e6;
        src.sendSuccess(() -> Component.literal(String.format("Debris: %d flying, %d queued (max %d, %d made per tick); server tick %.1f ms (50 = full); push scale %.3f; Sable ",
                live, queued, Config.Server.debrisMaxLive(), Config.Server.debrisPerTick(), mspt, Balance.DEBRIS_PUSH_SCALE)
                + (SableBridge.debrisActive() ? "on" : "off") + "."), false);
        if (st == null) return 0;
        long now = level.getGameTime();
        int i = 0;
        for (Piece p : st.live) {
            if (++i > 12) { src.sendSuccess(() -> Component.literal("  ... " + (live - 12) + " more"), false); break; }
            String line = String.format("  %s  %d blocks  now %.1f blocks/s  %ds left  wanted %.1f sideways, got %s", p.id.toString().substring(0, 8), p.blocks, p.speed,
                    Math.max(0L, (p.until - now) / 20L), Math.hypot(p.vel.x, p.vel.z), p.measured < 0 ? "-" : String.format("%.1f", p.measured));
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return live;
    }

    private static int clear(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        State st = STATES.get(level.dimension());
        int n = 0;
        if (st != null) {
            st.queue.clear();
            for (Piece p : st.live) {
                AABB b = SableBridge.debrisBounds(level, p.id);
                if (b != null) vanish(level, p.look, p.blocks, b);
                if (removeBody(level, p.id)) n++;
            }
            st.live.clear();
            st.seen.clear();
        }
        final int removed = n;
        src.sendSuccess(() -> Component.literal("Debris cleared: " + removed + " pieces removed."), false);
        return removed;
    }
}
