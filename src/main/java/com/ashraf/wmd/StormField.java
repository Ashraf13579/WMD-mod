package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class StormField {

    private StormField() {}

    static final int BUILD = Balance.STGEN_SPIN + Balance.STGEN_RAISE + Balance.STGEN_OPEN + Balance.STGEN_SEED;

    static final TicketType<ChunkPos> TICKET = TicketType.create("wmd_storm", java.util.Comparator.comparingLong(ChunkPos::toLong), 60);

    static final class Storm {
        final int id;
        final ResourceKey<Level> dim;
        final UUID owner;
        final BlockPos gen;
        final double x, y, z;
        final int r;
        final long start;
        final Vec3 tip;
        long cutAt = -1L;

        Storm(int id, ResourceKey<Level> dim, UUID owner, BlockPos gen, double x, double y, double z, int r, long start, Vec3 tip) {
            this.id = id; this.dim = dim; this.owner = owner; this.gen = gen; this.x = x; this.y = y; this.z = z; this.r = r; this.start = start; this.tip = tip;
        }

        long rainFrom() { return start + BUILD; }
        long rainTo() { long t = rainFrom() + Balance.STGEN_STORM; return cutAt >= 0 ? Math.min(t, Math.max(cutAt, start)) : t; }
        long gone() { return rainTo() + Math.max(Balance.STGEN_FADE, Balance.STGEN_JAM_AFTER); }
        boolean raining(long now) { return now >= rainFrom() && now < rainTo(); }
        boolean inside(double px, double pz) { double dx = px - x, dz = pz - z; return dx * dx + dz * dz <= (double) r * r; }

        StormPayload start() { return new StormPayload(StormPayload.START, id, tip, new Vec3(x, y, z), r, start, rainFrom(), rainTo()); }
    }

    private static final List<Storm> STORMS = Statics.server(new ArrayList<>());

    private static final Map<ResourceKey<Level>, Map<Long, long[]>> SKY = Statics.server(new HashMap<>());
    private static int nextId = 1;

    static int start(ServerLevel sl, BlockPos gen, UUID owner, int tx, int tz, int r, Vec3 tip) {
        int ground = sl.hasChunk(tx >> 4, tz >> 4) ? sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz)
                : sl.getChunkSource().getGenerator().getBaseHeight(tx, tz, Heightmap.Types.WORLD_SURFACE_WG, sl, sl.getChunkSource().randomState());
        double y = Math.min(sl.getMaxBuildHeight() - 8, Math.max(ground, sl.getSeaLevel()) + Balance.STGEN_CLOUD_H);
        Storm s = new Storm(nextId++, sl.dimension(), owner, gen.immutable(), tx + 0.5, y, tz + 0.5, r, sl.getGameTime(), tip);
        STORMS.add(s);
        hold(sl, s);
        send(sl, s, s.start());
        return s.id;
    }

    static void cut(ServerLevel sl, int id) {
        long now = sl.getGameTime();
        for (Storm s : STORMS)
            if (s.id == id && s.dim == sl.dimension() && s.cutAt < 0 && now < s.rainTo()) {
                s.cutAt = now;
                send(sl, s, new StormPayload(StormPayload.CUT, s.id, s.tip, new Vec3(s.x, s.y, s.z), s.r, now, 0L, 0L));
            }
    }

    static boolean live(Level level, int id) {
        long now = level.getGameTime();
        for (Storm s : STORMS) if (s.id == id && s.dim == level.dimension()) return now < s.rainTo();
        return false;
    }

    public static boolean shorted(ServerLevel level, BlockPos pos) {
        if (STORMS.isEmpty()) return false;
        long now = level.getGameTime();
        Vec3 w = null;
        for (Storm s : STORMS) {
            if (s.dim != level.dimension() || now < s.rainFrom() || now >= s.rainTo() + Balance.STGEN_JAM_AFTER) continue;
            if (w == null) w = SableBridge.worldPos(level, pos);
            if (s.inside(w.x, w.z)) {
                Vec3 cloud = new Vec3(w.x, s.y, w.z);
                if (CraftShield.shelters(level, w, cloud) || Forcefield.shelters(level, w, cloud)) return false;
                return openSky(level, pos);
            }
        }
        return false;
    }

    public static boolean raining(Level level, Vec3 p) {
        if (STORMS.isEmpty()) return false;
        long now = level.getGameTime();
        for (Storm s : STORMS) if (s.dim == level.dimension() && s.raining(now) && s.inside(p.x, p.z)) return true;
        return false;
    }

    static boolean openSky(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime(), key = pos.asLong();
        Map<Long, long[]> cache = SKY.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long[] c = cache.get(key);
        if (c != null && c[0] > now && c[0] - now <= 20) return c[1] != 0L;
        boolean open = scanSky(level, pos);
        if (cache.size() > 16000) cache.clear();
        cache.put(key, new long[] { now + 20, open ? 1L : 0L });
        return open;
    }

    private static boolean scanSky(ServerLevel level, BlockPos pos) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        if (top <= pos.getY() + 1) return true;
        if (top - pos.getY() > 48) return false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = pos.getY() + 1; y < top; y++) {
            BlockState st = level.getBlockState(m.set(pos.getX(), y, pos.getZ()));
            if (st.isAir() || st.getBlock() instanceof MachineBlock || st.getBlock() instanceof MachinePartBlock) continue;
            if (st.blocksMotion() || !st.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (STORMS.isEmpty() || !(event.getLevel() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        for (Iterator<Storm> it = STORMS.iterator(); it.hasNext(); ) {
            Storm s = it.next();
            if (s.dim != sl.dimension()) continue;
            if (now >= s.gone()) { it.remove(); continue; }
            if (now % 20 == 0 && s.cutAt < 0 && sl.isLoaded(s.gen) && !(sl.getBlockEntity(s.gen) instanceof StormGeneratorBlockEntity)) cut(sl, s.id);
            if (now < s.rainTo()) {
                if (now % 20 == 0) hold(sl, s);
                if (now % Balance.STGEN_RESEND == 0) send(sl, s, s.start());
                if (s.raining(now) && (now - s.rainFrom()) % Balance.STGEN_STRIKE_TICKS == 0) strike(sl, s);
            }
        }
    }

    private static void hold(ServerLevel sl, Storm s) {
        ChunkPos c = new ChunkPos(BlockPos.containing(s.x, 0, s.z));
        sl.getChunkSource().addRegionTicket(TICKET, c, s.r / 16 + 2, c);
    }

    private static void send(ServerLevel sl, Storm s, StormPayload p) {
        double r2 = Balance.STGEN_FX_RANGE * Balance.STGEN_FX_RANGE;
        for (ServerPlayer pl : sl.players()) {
            double dx = pl.getX() - s.x, dz = pl.getZ() - s.z, gx = pl.getX() - s.tip.x, gz = pl.getZ() - s.tip.z;
            if (dx * dx + dz * dz <= r2 || gx * gx + gz * gz <= r2) PacketDistributor.sendToPlayer(pl, p);
        }
    }

    private static void strike(ServerLevel sl, Storm s) {
        var rnd = sl.random;
        Vec3 hit = null;
        if (rnd.nextDouble() < Balance.STGEN_AIMED) hit = aimed(sl, s);
        if (hit == null) {
            double a = rnd.nextDouble() * Math.PI * 2.0, d = Math.sqrt(rnd.nextDouble()) * s.r;
            int x = (int) Math.floor(s.x + Math.cos(a) * d), z = (int) Math.floor(s.z + Math.sin(a) * d);
            if (!sl.hasChunk(x >> 4, z >> 4)) return;
            hit = new Vec3(x + 0.5, sl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
        }
        double a = rnd.nextDouble() * Math.PI * 2.0, d = rnd.nextDouble() * s.r * 0.5;
        Vec3 from = new Vec3(s.x + Math.cos(a) * d, s.y, s.z + Math.sin(a) * d);

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt != null) { bolt.moveTo(hit); bolt.setVisualOnly(true); }
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(hit, hit).inflate(Balance.STGEN_HIT_R), EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
            if (e.position().distanceTo(hit) > Balance.STGEN_HIT_R + 1.0) continue;
            if (CraftShield.shelters(sl, e.position(), from) || Forcefield.shelters(sl, e.position(), from)) continue;
            e.hurt(sl.damageSources().lightningBolt(), Balance.STGEN_DMG);
            if (!e.fireImmune()) e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), Balance.STGEN_FIRE_TICKS));
            if (bolt != null && !(e instanceof Player)) e.thunderHit(sl, bolt);
        }

        BlockPos hp = BlockPos.containing(hit);
        for (BlockPos q : BlockPos.betweenClosed(hp.offset(-2, -2, -2), hp.offset(2, 1, 2))) {
            BlockEntity be = sl.getBlockEntity(q);
            if (be instanceof MachinePartBlockEntity part && part.getController() != null) be = sl.getBlockEntity(part.getController());
            if (be instanceof EmpVulnerable v) {
                Vec3 wp = SableBridge.worldPos(sl, q);
                if (CraftShield.shelters(sl, wp, from) || Forcefield.shelters(sl, wp, from)) continue;
                v.empDrain();
            }
        }
        if (!CraftShield.shelters(sl, hit, from) && !Forcefield.shelters(sl, hit, from)) burn(sl, hp);
        sl.playSound(null, hit.x, hit.y, hit.z, WmdSounds.STORM_THUNDER.get(), SoundSource.WEATHER, Balance.STGEN_THUNDER_VOL, 0.9F + rnd.nextFloat() * 0.2F);
        sl.playSound(null, hit.x, hit.y, hit.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, Balance.SND_RELEASE, 0.5F + rnd.nextFloat() * 0.2F);
        send(sl, s, new StormPayload(StormPayload.STRIKE, s.id, hit, from, s.r, sl.getGameTime(), 0L, 0L));
    }

    private static Vec3 aimed(ServerLevel sl, Storm s) {
        List<Vec3> c = new ArrayList<>();
        AABB box = new AABB(s.x - s.r, sl.getMinBuildHeight(), s.z - s.r, s.x + s.r, s.y, s.z + s.r);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, EntitySelector.NO_CREATIVE_OR_SPECTATOR))
            if (e.isAlive() && s.inside(e.getX(), e.getZ()) && sl.canSeeSky(e.blockPosition().above())) {
                Vec3 cloud = new Vec3(e.getX(), s.y, e.getZ());
                if (!CraftShield.shelters(sl, e.position(), cloud) && !Forcefield.shelters(sl, e.position(), cloud)) c.add(e.position());
            }
        int cx0 = (int) Math.floor(s.x - s.r) >> 4, cx1 = (int) Math.floor(s.x + s.r) >> 4, cz0 = (int) Math.floor(s.z - s.r) >> 4, cz1 = (int) Math.floor(s.z + s.r) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!sl.hasChunk(cx, cz)) continue;
                LevelChunk ch = sl.getChunk(cx, cz);
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof EmpVulnerable) || be instanceof StormGeneratorBlockEntity) continue;
                    BlockPos p = be.getBlockPos();
                    if (s.inside(p.getX() + 0.5, p.getZ() + 0.5) && openSky(sl, p))
                        c.add(new Vec3(p.getX() + 0.5, sl.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()), p.getZ() + 0.5));
                }
            }
        return c.isEmpty() ? null : c.get(sl.random.nextInt(c.size()));
    }

    private static void burn(ServerLevel sl, BlockPos hp) {
        if (!sl.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK) || sl.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;
        for (int k = 0; k < 2; k++) {
            BlockPos p = k == 0 ? hp : hp.offset(sl.random.nextInt(3) - 1, 0, sl.random.nextInt(3) - 1);
            if (k == 1 && sl.random.nextFloat() > 0.3F) break;
            if (!sl.getBlockState(p).isAir() || !BlockRules.blastMay(sl, p) || !BaseFireBlock.canBePlacedAt(sl, p, Direction.UP)) continue;
            sl.setBlockAndUpdate(p, BaseFireBlock.getState(sl, p));
        }
    }
}
