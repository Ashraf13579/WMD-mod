package com.ashraf.wmd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class StationLife {

    private StationLife() {}

    public static final int SHUT = 0, OPEN = 1, RED = 2;
    private static final Map<Integer, Boolean> SUN = new HashMap<>();
    private static final Map<Integer, Boolean> BREACHED = new HashMap<>();
    private static final Map<Integer, Boolean> NO_POWER = new HashMap<>();
    private static final Map<Long, Long> CYCLES = new HashMap<>();

    static void clearStatics() { SUN.clear(); BREACHED.clear(); NO_POWER.clear(); CYCLES.clear(); }

    static boolean powered(int slot) { return !NO_POWER.getOrDefault(slot, false); }

    static void forget(int slot) { SUN.remove(slot); NO_POWER.remove(slot); StationRooms.forget(slot); }

    static boolean breached(int id) { return BREACHED.getOrDefault(id, false); }

    static final String BUNK = "wmd_station_bunk";

    static int drawOxygen(ServerLevel level, int slot, int want) {
        if (slot < 0) return 0;
        Station.Data d = Station.Data.get(level.getServer());
        long buf = d.energy.getOrDefault(slot, 0L);
        int got = (int) Math.min(want, buf / Balance.STATION_O2_FE);
        if (got <= 0) return 0;
        d.energy.put(slot, buf - (long) got * Balance.STATION_O2_FE);
        d.setDirty();
        return got;
    }

    @SubscribeEvent
    static void onRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer p) || !Orbit.isOrbit(p.level())) return;
        long b = p.getPersistentData().getLong(BUNK);
        boolean ok = b != 0L && p.serverLevel().getBlockState(BlockPos.of(b)).is(Wmd.STATION_BUNK.get()) && cabinAir(p);
        if (ok) return;
        ServerLevel earth = p.server.overworld();
        BlockPos s = earth.getSharedSpawnPos();
        p.teleportTo(earth, s.getX() + 0.5, earth.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.getX(), s.getZ()), s.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD, null, 0.0F, false, false);
        p.getPersistentData().remove(BUNK);
        p.displayClientMessage(Component.translatable("message.wmd.station.bunk_lost").withStyle(ChatFormatting.GOLD), false);
    }

    public static boolean sunlit(long gameTime) { return OrbitClock.phase(gameTime) < Balance.STATION_SUN_TICKS; }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Orbit.isOrbit(level) || level.getGameTime() % 20 != 0) return;
        Station.Data d = Station.Data.get(level.getServer());
        AsteroidRedirect.second(level, d);
        Rendezvous.tick(level, d);
        if (level.getGameTime() % 200 == 0) AsteroidRedirect.resync(level, d);
        for (Map.Entry<Integer, List<Station.Module>> e : d.slots.entrySet()) {
            Station.Module core = null;
            for (Station.Module m : e.getValue()) if (Station.CORE.equals(m.type())) core = m;
            StationChunks.update(level, d, e.getKey(), e.getValue(), core);
            if (core == null || !level.isLoaded(core.origin())) continue;
            tick(level, d, e.getKey(), e.getValue());
        }
    }

    private static void tick(ServerLevel level, Station.Data d, int slot, List<Station.Module> all) {
        long now = level.getGameTime();
        boolean sun = sunlit(now);
        for (Station.Module m : all) if (Station.CORE.equals(m.type()) && m.built()) { Reboost.tick(level, d, slot, m, now); break; }
        if (now % 100 == 0) for (Station.Module m : all) if (Station.CORE.equals(m.type()) && m.built()) Station.poleLadder(level, m, true);
        for (Station.Module m : all) if (m.built()) Station.moveRodRack(level, m, now % 100 == 0);
        SensorArray.tick(level, slot, all, now);

        for (Station.Module m : new java.util.ArrayList<>(all)) {
            if (m.built() || now < m.arrive() + Balance.STATION_ARRIVE_TICKS) continue;
            java.util.UUID owner = level.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be ? be.owner() : null;
            if (Station.latch(level, slot, m, owner)) {
                level.playSound(null, Station.controller(m), WmdSounds.ST_DOCK_CLAMP.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
                tell(level, slot, Component.translatable("message.wmd.station.docked", name(m)).withStyle(ChatFormatting.AQUA));
            } else if ((now - m.arrive()) % 600 == 0) tell(level, slot, Component.translatable("message.wmd.station.blocked", name(m)).withStyle(ChatFormatting.GOLD));
        }
        List<Station.Module> mods = new java.util.ArrayList<>();
        for (Station.Module m : all) if (m.built()) mods.add(m);
        if (now % Balance.STATION_ROOM_EVERY == 0) StationRooms.recompute(level, slot, mods);
        net.minecraft.server.MinecraftServer server = level.getServer();

        int wings = 0, use = 0;
        long before = d.energy.getOrDefault(slot, Balance.STATION_BUFFER / 2);
        for (Station.Module m : mods) {
            switch (m.type()) {
                case Station.WING -> { if (!Station.wrecked(server, m) && level.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be && now >= be.deploy() + Balance.STATION_DEPLOY_TICKS) wings++; }
                case Station.CORE -> use += Balance.STATION_CORE_FE;
                case Station.HABITAT -> use += Balance.STATION_HAB_FE;
                case Station.SENSOR -> { if (SensorArray.ready(level, m, now)) use += Balance.STATION_SENSOR_FE; }
                case Station.LASER -> { if (!Station.wrecked(server, m)) use += Balance.STATION_LASER_FE; }
                case Station.TURRET -> { if (!Station.wrecked(server, m)) use += Balance.STATION_TURRET_FE; }
                case Station.ROD -> { if (!Station.wrecked(server, m)) use += Balance.STATION_ROD_FE; }
                case Station.MIRROR -> { if (!Station.wrecked(server, m)) use += Balance.STATION_MIRROR_FE + (MirrorFocus.burning(slot) ? Balance.MIRROR_BURN_FE : 0); }
                case Station.BAY -> { if (!Station.wrecked(server, m)) use += Balance.STATION_BAY_FE + (AsteroidBay.drawing(d, m, before) ? Balance.BAY_BUILD_FE : 0); }
                default -> {}
            }
        }
        int make = sun ? wings * Balance.STATION_WING_FE : 0;
        long cap = Station.capacity(server, slot);
        long buf = Math.max(0L, Math.min(cap, d.energy.getOrDefault(slot, Balance.STATION_BUFFER / 2) + 20L * (make - use)));
        if (now % 100 == 0) Station.syncBatteries(level, slot, buf, cap, make < use);
        d.energy.put(slot, buf);
        boolean powered = buf > 0 || make >= use;
        if (!powered && !NO_POWER.getOrDefault(slot, false)) tell(level, slot, Component.translatable("message.wmd.station.no_power").withStyle(ChatFormatting.GOLD));
        NO_POWER.put(slot, !powered);
        AsteroidBay.tick(level, d, slot, mods, before, powered);

        Map<Integer, Integer> mask = new HashMap<>();
        for (Station.Module m : mods) {
            int bits = 0;
            for (Station.HullCell c : Station.hull(m)) {
                if (!level.isLoaded(c.pos())) continue;
                BlockState st = level.getBlockState(c.pos());
                if (st.isCollisionShapeFullBlock(level, c.pos()) || StationRooms.closedHatch(st)) continue;
                if (st.is(Wmd.STATION_HATCH.get()) && StationRooms.hatchSealed(slot, StationRooms.lower(c.pos(), st))) continue;
                bits |= 1 << c.face();
            }
            boolean wreck = Station.wrecked(server, m);
            if (wreck && !Station.boom(m.type())) bits = 0xFF;
            mask.put(m.id(), bits);
            boolean br = bits != 0, was = BREACHED.getOrDefault(m.id(), false);
            if (br && !was) tell(level, slot, Component.translatable("message.wmd.station.breach", name(m)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            if (br && !was) pa(level, slot, WmdSounds.PA_BREACH.get());
            if (!br && was) tell(level, slot, Component.translatable("message.wmd.station.sealed", name(m)).withStyle(ChatFormatting.GREEN));
            BREACHED.put(m.id(), br);
            if (level.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity wbe) wbe.setWrecked(wreck);
            if (br && !was) level.playSound(null, Station.controller(m), WmdSounds.ST_AIR_RUSH.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
            if (br && now % 40 == 0) level.playSound(null, Station.controller(m), WmdSounds.ST_BREACH_KLAXON.get(), SoundSource.BLOCKS, 2.5F, 1.0F);
        }

        for (Station.Module m : mods) {
            if (Station.boom(m.type())) continue;
            float a = d.air.getOrDefault(m.id(), 1.0F);
            if (mask.getOrDefault(m.id(), 0) != 0 || Station.wrecked(level.getServer(), m)) a -= 20.0F / Balance.STATION_VENT_TICKS;
            else if (powered) a += 20.0F / Balance.STATION_AIR_BACK_TICKS;
            else a -= 20.0F / Balance.STATION_AIR_DRAIN_TICKS;
            d.air.put(m.id(), Math.max(0.0F, Math.min(1.0F, a)));
        }
        d.setDirty();

        Map<Integer, int[]> ports = new HashMap<>();
        for (Station.Module m : mods) {
            Map<Integer, List<BlockPos>> ops = Station.openings(m);
            int[] st = new int[8];
            boolean meBreached = mask.getOrDefault(m.id(), 0) != 0;
            for (Map.Entry<Integer, List<BlockPos>> o : ops.entrySet()) {
                int k = o.getKey();
                Station.Module nb = neighbour(mods, m, k);
                boolean nbBreached = nb != null && mask.getOrDefault(nb.id(), 0) != 0;
                long key = ((long) m.id() << 8) | k;
                boolean cycling = CYCLES.getOrDefault(key, 0L) > now;
                boolean seal;
                int show;
                if (Station.CORE.equals(m.type()) && k == 4 && capsuleOn(level, m)) { seal = false; show = OPEN; }
                else if (nb != null && Station.boom(nb.type())) { seal = false; show = SHUT; }
                else if (Station.CORE.equals(m.type()) && k < 4 && nb == null && Boarding.cutThrough(level, slot, k)) { seal = false; show = OPEN; }
                else if (Station.HABITAT.equals(m.type()) && k == 5 && m.port() == 0 && nb == null && Boarding.cutThrough(level, slot, Station.DOCK)) { seal = false; show = OPEN; }
                else if (nb != null && Station.tube(m.type()) && k == 0) { seal = false; show = SHUT; }
                else if (nb != null && !meBreached && !nbBreached) { seal = false; show = OPEN; }
                else if (nb == null && !meBreached && StationRooms.openingSealed(slot, m.id(), k)) { seal = false; show = OPEN; }
                else { seal = !cycling; show = cycling ? OPEN : (meBreached || nbBreached ? RED : SHUT); }
                if (meBreached && show != OPEN) show = RED;
                if (k < st.length) st[k] = show;
                setSeals(level, m, o.getValue(), seal);
            }
            ports.put(m.id(), st);
        }

        if (SUN.get(slot) == null || SUN.get(slot) != sun) {
            for (Station.Module m : mods) for (Station.HullCell c : Station.hull(m)) {
                BlockState st = level.getBlockState(c.pos());
                if (st.is(Wmd.STATION_LIGHT.get()) && st.getValue(StationHullBlock.Light.NIGHT) == sun)
                    level.setBlock(c.pos(), st.setValue(StationHullBlock.Light.NIGHT, !sun), Block.UPDATE_CLIENTS);
            }
            SUN.put(slot, sun);
        }
        for (Station.Module m : mods)
            if (level.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be) {
                be.setState(ports.getOrDefault(m.id(), new int[5]), mask.getOrDefault(m.id(), 0));
                if (Station.ROD.equals(m.type())) be.setRods(d.rods.getOrDefault(m.id(), 0));
                if (Station.WING.equals(m.type()) && be.deploy() > 0L && now < be.deploy() + Balance.STATION_DEPLOY_TICKS && (now - be.deploy()) % 60 < 20)
                    level.playSound(null, Station.controller(m), WmdSounds.ST_WING_DEPLOY.get(), SoundSource.BLOCKS, 2.0F, 0.9F + 0.2F * (now - be.deploy()) / Balance.STATION_DEPLOY_TICKS);
            }
    }

    static Station.Module neighbour(List<Station.Module> mods, Station.Module m, int k) {
        if (Station.tube(m.type()) && k == 0) {
            for (Station.Module o : mods) if (o.id() == m.host()) return o;
            return null;
        }
        for (Station.Module o : mods) if (o.host() == m.id() && o.port() == k) return o;
        return null;
    }

    static boolean capsuleOn(ServerLevel level, Station.Module core) {
        BlockPos c = Station.world(core, 0, 9, 0);
        if (!level.getBlockState(c).is(Wmd.CREW_CAPSULE_BLOCK.get())) return false;
        return !(level.getBlockEntity(c) instanceof CrewCapsuleBlockEntity be) || be.boardSlot() < 0 || be.cutDone(level.getGameTime());
    }

    private static void setSeals(ServerLevel level, Station.Module m, List<BlockPos> cells, boolean seal) {
        BlockState s = Wmd.STATION_SEAL.get().defaultBlockState();
        for (BlockPos l : cells) {
            BlockPos p = Station.world(m, l.getX(), l.getY(), l.getZ());
            if (!level.isLoaded(p)) continue;
            BlockState st = level.getBlockState(p);
            if (seal && st.isAir() && level.getEntitiesOfClass(LivingEntity.class, new AABB(p)).isEmpty()) level.setBlock(p, s, Block.UPDATE_CLIENTS);
            else if (!seal && st.is(Wmd.STATION_SEAL.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    static boolean airlock(ServerLevel level, BlockPos pos) {
        int slot = Orbit.slotAt(pos.getX(), pos.getZ());
        if (slot < 0) return false;
        for (Station.Module m : Station.modules(level.getServer(), slot)) {
            BlockPos l = Station.local(m, pos);
            for (Map.Entry<Integer, List<BlockPos>> o : Station.openings(m).entrySet())
                if (o.getValue().contains(l)) {
                    CYCLES.put(((long) m.id() << 8) | o.getKey(), level.getGameTime() + Balance.STATION_AIRLOCK_TICKS);
                    for (BlockPos c : o.getValue()) {
                        BlockPos p = Station.world(m, c.getX(), c.getY(), c.getZ());
                        if (level.getBlockState(p).is(Wmd.STATION_SEAL.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                    level.playSound(null, pos, WmdSounds.ST_AIRLOCK.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
                    return true;
                }
        }
        return false;
    }

    public static void blast(ServerLevel level, double x, double y, double z, double r) {
        if (!Orbit.isOrbit(level) || r < 1.0) return;
        int slot = Orbit.slotAt(x, z);
        if (slot < 0) return;
        Station.Data d = Station.Data.get(level.getServer());
        double reach = 2.5 * r;
        for (Station.Module m : Station.modules(level.getServer(), slot)) {
            if (!m.built()) continue;
            BlockPos c = Station.middle(m);
            double dist = Math.sqrt(c.distToCenterSqr(x, y, z));
            if (dist >= reach) continue;
            int dmg = (int) Math.round(Balance.STATION_BLAST_DMG * r * (1.0 - dist / reach));
            if (dmg > 0) damage(level, slot, m, dmg);
        }
    }

    static int damage(ServerLevel level, int slot, Station.Module m, int dmg) {
        Station.Data d = Station.Data.get(level.getServer());
        int before = d.health.getOrDefault(m.id(), Station.maxHealth(m.type())), after = before - dmg;
        d.health.put(m.id(), after);
        d.setDirty();
        if (before > 0 && after <= 0) {
            tell(level, slot, Component.translatable("message.wmd.station.wrecked", name(m)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            level.playSound(null, Station.middle(m), WmdSounds.OR_CAPSULE_TOUCHDOWN.get(), SoundSource.BLOCKS, 4.0F, 0.5F);
            StationWreck.start(level, slot, m);
        }
        return after;
    }

    @SubscribeEvent
    static void onExplosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (ClaimGuard.probing() || !(event.getLevel() instanceof ServerLevel level) || !Orbit.isOrbit(level)) return;
        var c = event.getExplosion().center();
        blast(level, c.x, c.y, c.z, event.getExplosion().radius());
    }

    static Station.Module inside(ServerPlayer p) {
        if (!Orbit.isOrbit(p.level())) return null;
        int slot = Orbit.slotAt(p.getX(), p.getZ());
        if (slot < 0) return null;
        BlockPos feet = p.blockPosition(), head = BlockPos.containing(p.getEyePosition());
        List<Station.Module> mods = Station.modules(p.server, slot);
        for (Station.Module m : mods)
            if (Station.interior(m, Station.local(m, feet)) || Station.interior(m, Station.local(m, head))) return m;
        int r = StationRooms.roomModule(slot, feet);
        if (r < 0) r = StationRooms.roomModule(slot, head);
        for (Station.Module m : mods) if (m.id() == r) return m;
        return null;
    }

    static boolean cabinAir(ServerPlayer p) {
        Station.Module m = inside(p);
        return m != null && Station.Data.get(p.server).air.getOrDefault(m.id(), 1.0F) > 0.0F;
    }

    static Component name(Station.Module m) { return Component.translatable("station.wmd.module." + m.type()); }

    static void pa(ServerLevel level, int slot, net.minecraft.sounds.SoundEvent line) {
        for (ServerPlayer p : level.players())
            if (Orbit.slotAt(p.getX(), p.getZ()) == slot) p.playNotifySound(line, SoundSource.VOICE, 1.0F, 1.0F);
    }

    static void tell(ServerLevel level, int slot, Component msg) {
        Set<ServerPlayer> seen = new HashSet<>();
        for (ServerPlayer p : level.players())
            if (Orbit.slotAt(p.getX(), p.getZ()) == slot && seen.add(p)) p.displayClientMessage(msg, false);
    }
}
