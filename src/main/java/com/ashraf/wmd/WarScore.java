package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class WarScore {

    private WarScore() {}

    static final int C_BLOCKS = 0, C_MACHINES = 1, C_KILLS = 2, C_INTERCEPTS = 3, C_CRAFTS = 4, C_TREATY = 5, CATS = 6;
    static final long KILL_TRACE_MS = 10_000L;

    static final TagKey<Block> T2 = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "war_value_t2")),
            T3 = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "war_value_t3")),
            T4 = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "war_value_t4"));

    private static final Map<UUID, long[]> BLOCKS = Statics.server(new HashMap<>());
    private static final Map<UUID, Long> SHOOTER_NATION_AT = Statics.server(new HashMap<>());
    private static final Map<UUID, UUID> SHOOTER_NATION = Statics.server(new HashMap<>());
    private static final Map<UUID, Object[]> LAST_HIT = Statics.server(new HashMap<>());
    private static final Map<String, Long> KILLED = Statics.server(new HashMap<>());

    static void award(MinecraftServer server, UUID by, UUID against, int cat, int pts) {
        if (server == null || by == null || against == null || by.equals(against) || pts == 0) return;
        for (WarData.War w : WarData.get(server).wars) {
            if (w.state != WarData.ACTIVE) continue;
            int s = w.sideOf(by), o = w.sideOf(against);
            if (s == 0 || o == 0 || s == o) continue;
            w.points[s] += pts;
            w.cats[s][cat] += pts;
            WarData.get(server).setDirty();
            return;
        }
    }

    private static UUID nationOf(MinecraftServer server, UUID player) {
        if (player == null) return null;
        long t = WarManager.now();
        Long at = SHOOTER_NATION_AT.get(player);
        if (at == null || t - at > 1_000L) { SHOOTER_NATION.put(player, OpacBridge.nationOf(server, player)); SHOOTER_NATION_AT.put(player, t); }
        return SHOOTER_NATION.get(player);
    }

    static void block(ServerLevel level, long pos, UUID shooter) {
        if (VnWars.anyWar && shooter != null) { vnBlock(level, pos, shooter); return; }
        if (!WarGate.anyActive || shooter == null) return;
        MinecraftServer server = level.getServer();
        UUID by = nationOf(server, shooter);
        if (by == null) return;
        BlockPos p = BlockPos.of(pos);
        if (!WarGate.blastAllowedFor(level, p, by)) return;
        long[] b = BLOCKS.computeIfAbsent(shooter, k -> new long[3]);
        long t = WarManager.now();
        if (t - b[1] > Balance.WAR_BLAST_WINDOW_MS) { b[1] = t; b[2] = 0; }
        if (++b[0] < Balance.WAR_BLOCKS_PER_POINT) return;
        b[0] = 0;
        if (b[2] >= Balance.WAR_BLAST_CAP) return;
        b[2]++;
        award(server, by, OpacBridge.nationAt(level, p), C_BLOCKS, 1);
    }

    private static final Map<Long, String> VN_CHUNK = Statics.server(new HashMap<>());
    private static long vnTick = Long.MIN_VALUE;

    private static void vnBlock(ServerLevel level, long pos, UUID shooter) {
        String by = VnBridge.countryOf(shooter);
        if (by == null) return;
        long tick = level.getGameTime();
        if (tick != vnTick) { VN_CHUNK.clear(); vnTick = tick; }
        BlockPos p = BlockPos.of(pos);
        String here = VN_CHUNK.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4), k -> { String c = VnBridge.countryAt(level, p); return c == null ? "" : c; });
        if (here.isEmpty() || !VnWars.fighting(by, here)) return;
        long[] b = BLOCKS.computeIfAbsent(shooter, k -> new long[3]);
        long t = WarManager.now();
        if (t - b[1] > Balance.WAR_BLAST_WINDOW_MS) { b[1] = t; b[2] = 0; }
        if (++b[0] < Balance.WAR_BLOCKS_PER_POINT) return;
        b[0] = 0;
        if (b[2] >= Balance.WAR_BLAST_CAP) return;
        b[2]++;
        VnWars.count(by, here, C_BLOCKS, 1);
    }

    static void machine(ServerLevel level, BlockState state, UUID shooter, UUID owner) {
        if (VnWars.anyWar && shooter != null && owner != null) { VnWars.countPlayers(shooter, owner, C_MACHINES, value(state)); return; }
        if (!WarGate.anyActive || shooter == null || owner == null) return;
        MinecraftServer server = level.getServer();
        award(server, nationOf(server, shooter), OpacBridge.nationOf(server, owner), C_MACHINES, value(state));
    }

    static int value(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof CcBuildingBlock) return Balance.WAR_PTS_CC_CORE;
        if (b instanceof CcModuleBlock) return Balance.WAR_PTS_CC_MODULE;
        if (b instanceof LaunchTerminalBlock || b instanceof InterceptorSiloBlock || b instanceof InterceptorSiloT4Block || b instanceof RadarStationBlock
                || b instanceof NuclearReactorBlock || b instanceof NavalReactorBlock || b instanceof FusionReactorBlock) return Balance.WAR_PTS_BIG;
        if (s.is(T4)) return Balance.WAR_PTS_T4;
        if (s.is(T3)) return Balance.WAR_PTS_T3;
        if (s.is(T2)) return Balance.WAR_PTS_T2;
        return Balance.WAR_PTS_T1;
    }

    static void intercept(ServerLevel level, UUID defender, Flight f) {
        if (f != null) WmdApiImpl.intercept(level, "missile", f.tier(), defender, CcBattleLog.ownerOf(level.getServer(), f));
        if (f != null) WmdTrigger.fire(level.getServer(), defender, "intercept");
        if (VnWars.anyWar && defender != null && f != null) {
            VnWars.countPlayers(defender, CcBattleLog.ownerOf(level.getServer(), f), C_INTERCEPTS, f.tier() >= 3 ? Balance.WAR_PTS_INTERCEPT_BIG : Balance.WAR_PTS_INTERCEPT);
            return;
        }
        if (!WarGate.anyActive || defender == null || f == null) return;
        MinecraftServer server = level.getServer();
        award(server, nationOf(server, defender), nationOf(server, CcBattleLog.ownerOf(server, f)), C_INTERCEPTS, f.tier() >= 3 ? Balance.WAR_PTS_INTERCEPT_BIG : Balance.WAR_PTS_INTERCEPT);
    }

    static void intercept(ServerLevel level, UUID defender, UUID attacker) {
        WmdApiImpl.intercept(level, "other", 0, defender, attacker);
        if (VnWars.anyWar && defender != null && attacker != null) { VnWars.countPlayers(defender, attacker, C_INTERCEPTS, Balance.WAR_PTS_INTERCEPT); return; }
        if (!WarGate.anyActive || defender == null || attacker == null) return;
        MinecraftServer server = level.getServer();
        award(server, nationOf(server, defender), nationOf(server, attacker), C_INTERCEPTS, Balance.WAR_PTS_INTERCEPT);
    }

    static void craft(ServerLevel level, UUID shooter, UUID ship, int blocks) {
        WmdApiImpl.craftDowned(level, ship, shooter, blocks);
        if (VnWars.anyWar && shooter != null && ship != null) {
            VnWars.countPlayers(shooter, Crafts.owner(level, ship), C_CRAFTS, AirGate.on() && AirPicture.isAircraft(level, ship) ? Balance.WAR_PTS_AIRCRAFT : Balance.WAR_PTS_SHIP);
            return;
        }
        if (!WarGate.anyActive || shooter == null || ship == null) return;
        MinecraftServer server = level.getServer();
        UUID by = nationOf(server, shooter), owner = Crafts.owner(level, ship), against = nationOf(server, owner);
        if (by == null || against == null) return;
        for (WarData.War w : WarData.get(server).wars) {
            if (w.state != WarData.ACTIVE || w.sideOf(by) == 0 || w.sideOf(against) == 0 || w.sideOf(by) == w.sideOf(against)) continue;
            if (!w.craftsScored.add(ship)) return;
            break;
        }
        int pts = AirGate.on() && AirPicture.isAircraft(level, ship) ? Balance.WAR_PTS_AIRCRAFT
                : Math.max(Balance.WAR_PTS_SHIP, Math.min(Balance.WAR_PTS_SHIP_MAX, Balance.WAR_PTS_SHIP * (blocks / Balance.WAR_SHIP_BLOCKS)));
        award(server, by, against, C_CRAFTS, pts);
    }

    static void treaty(MinecraftServer server, UUID nation, UUID against, int pts) { award(server, nation, against, C_TREATY, pts); }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (!(WarGate.anyActive || VnWars.anyWar) || !(event.getEntity() instanceof ServerPlayer victim)) return;
        UUID shooter = event.getSource().getEntity() instanceof Player p ? p.getUUID() : ShipStrike.shooter();
        if (shooter != null && !shooter.equals(victim.getUUID())) LAST_HIT.put(victim.getUUID(), new Object[] { shooter, WarManager.now() });
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(WarGate.anyActive || VnWars.anyWar) || !(event.getEntity() instanceof ServerPlayer victim)) return;
        try {
            MinecraftServer server = victim.server;
            UUID killer = event.getSource().getEntity() instanceof Player p ? p.getUUID() : null;
            Object[] last = LAST_HIT.remove(victim.getUUID());
            if (killer == null && last != null && WarManager.now() - (Long) last[1] <= KILL_TRACE_MS) killer = (UUID) last[0];
            if (killer == null || killer.equals(victim.getUUID())) return;
            if (VnWars.anyWar) {
                String vk = "vn|" + victim.getUUID();
                Long vp = KILLED.get(vk);
                long vt = WarManager.now();
                if (vp != null && vt - vp < Balance.WAR_KILL_REPEAT_MS) return;
                KILLED.put(vk, vt);
                VnWars.countPlayers(killer, victim.getUUID(), C_KILLS, Balance.WAR_PTS_KILL);
                return;
            }
            UUID by = nationOf(server, killer), against = nationOf(server, victim.getUUID());
            WarData.War w = by == null || against == null ? null : WarManager.between(server, by, against);
            if (w == null || w.state != WarData.ACTIVE) return;
            String key = w.id + "|" + victim.getUUID();
            Long prev = KILLED.get(key);
            long t = WarManager.now();
            if (prev != null && t - prev < Balance.WAR_KILL_REPEAT_MS) return;
            KILLED.put(key, t);
            award(server, by, against, C_KILLS, Balance.WAR_PTS_KILL);
        } catch (RuntimeException e) { Wmd.LOGGER.warn("[war] kill score failed: {}", e.toString()); }
    }

    static int winnerByScore(WarData.War w) {
        int a = w.points[1], b = w.points[2];
        if (a >= Balance.WAR_WIN_MIN && a >= b * Balance.WAR_WIN_MARGIN && a > b) return 1;
        if (b >= Balance.WAR_WIN_MIN && b >= a * Balance.WAR_WIN_MARGIN && b > a) return 2;
        return 0;
    }

    static String line(WarData.War w, int side) {
        int[] c = w.cats[side];
        return w.points[side] + " pts (blocks " + c[C_BLOCKS] + ", machines " + c[C_MACHINES] + ", kills " + c[C_KILLS] + ", intercepts " + c[C_INTERCEPTS]
                + ", craft " + c[C_CRAFTS] + (c[C_TREATY] != 0 ? ", treaty " + c[C_TREATY] : "") + ")";
    }
}
