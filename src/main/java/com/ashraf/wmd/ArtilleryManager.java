package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ArtilleryManager extends SavedData {

    private static final String NAME = "wmd_artillery";

    static final double NEAR = 1_536.0;

    static final double HEARD = 256.0;
    private static final int PREPARE_TICKS = 40;

    private record Sub(int gun, UUID owner, double x, double z, long at, int lastSeq, double cx, double cz) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("g", gun); if (owner != null) t.putUUID("o", owner); t.putDouble("x", x); t.putDouble("z", z); t.putLong("at", at); t.putInt("l", lastSeq);
            t.putDouble("cx", cx); t.putDouble("cz", cz);
            return t;
        }
        static Sub load(CompoundTag t) {
            return new Sub(t.getInt("g"), t.hasUUID("o") ? t.getUUID("o") : null, t.getDouble("x"), t.getDouble("z"), t.getLong("at"), t.getInt("l"), t.getDouble("cx"), t.getDouble("cz"));
        }
    }

    private final List<ArtShell> shells = new ArrayList<>();

    private final Map<UUID, java.util.Set<Long>> sent = new HashMap<>();
    static final int SHELL_SYNC = 5, TARGET_LEAD = 200;
    private final List<Sub> subs = new ArrayList<>();

    private final Map<UUID, long[]> spots = new HashMap<>();

    private final Map<Long, String> leaflets = new HashMap<>();
    private long nextId = 1;

    void leaflet(long id, String msg) { leaflets.put(id, msg); setDirty(); }

    String takeLeaflet(long id) { String m = leaflets.remove(id); if (m != null) setDirty(); return m == null ? "" : m; }

    public static ArtilleryManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ArtilleryManager::new, ArtilleryManager::load, null), NAME);
    }

    public List<ArtShell> shells() { return java.util.Collections.unmodifiableList(shells); }

    public ArtShell fire(ServerLevel level, UUID owner, String ownerName, int gun, int type, Vec3 muzzle, double tx, double ty, double tz, int dur, BlockPos gunPos,
                         int seq, boolean last) {
        if (shells.size() >= Config.Server.artMaxShells()) return null;
        ArtShell s = new ArtShell(nextId++, owner, ownerName, gun, type, muzzle.x, muzzle.y, muzzle.z, tx, ty, tz, level.getGameTime(), dur,
                gunPos == null ? 0L : gunPos.asLong(), seq, last, false);
        shells.add(s);
        setDirty();
        if (gunPos != null) HeatLog.fired(level, gunPos, owner);
        shareNew(level, level.getGameTime(), List.of(s));
        StrikeToasts.shellOut(level, s);
        return s;
    }

    public void setSpot(UUID owner, int x, int z, long until) {
        spots.put(owner, new long[] { x, z, until });
        setDirty();
    }

    boolean spotAt(UUID owner, double x, double z, long now) {
        long[] s = spots.get(owner);
        return s != null && now < s[2] && Math.hypot(s[0] + 0.5 - x, s[1] + 0.5 - z) <= Balance.ART_SPOT_RADIUS;
    }

    public ArtShell nearestShell(Vec3 gun, double range, UUID friendly, long now) {
        ArtShell best = null;
        double bd = range * range;
        for (ArtShell s : shells) {
            if (friendly != null && friendly.equals(s.owner())) continue;
            if (now < s.start() + 5) continue;
            double d = s.position(now).distanceToSqr(gun);
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    public boolean shootDown(ServerLevel level, long id, UUID shooter, String shooterName) {
        for (Iterator<ArtShell> it = shells.iterator(); it.hasNext(); ) {
            ArtShell s = it.next();
            if (s.id() != id) continue;
            it.remove();
            StrikeToasts.shellDone(level, s, false);
            Vec3 p = s.position(level.getGameTime());
            PacketDistributor.sendToPlayersNear(level, null, p.x, p.y, p.z, NEAR, new ArtImpactPayload(s.id(), s.gun(), s.type(), p.x, p.y, p.z, ArtImpactPayload.SHOT_DOWN));
            if (hasOwner(s.owner())) CcBattleLog.add(level.getServer(), s.owner(), CcBattleLog.LOST, "log.wmd.art_lost", "#" + itemKey(s), Long.toString(Math.round(p.x)), Long.toString(Math.round(p.z)));
            if (shooter != null) CcBattleLog.add(level.getServer(), shooter, CcBattleLog.INTERCEPT, "log.wmd.art_killed", "#" + itemKey(s), Long.toString(Math.round(p.x)), Long.toString(Math.round(p.z)));
            if (s.prepared()) FlightManager.get(level).release(level, (int) Math.floor(s.tx()), (int) Math.floor(s.tz()), holdRadius(s), level.getGameTime() + 100);
            setDirty();
            return true;
        }
        return false;
    }

    private static boolean hasOwner(UUID o) { return o != null && (o.getMostSignificantBits() != 0L || o.getLeastSignificantBits() != 0L); }

    static String itemKey(ArtShell s) { if (s.gun() == Artillery.ROCKET) return "item.wmd." + Mlrs.rocketId(s.type()); return "item.wmd." + Artillery.shellId(s.gun(), s.type()); }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ArtilleryManager m = level.getDataStorage().get(new SavedData.Factory<>(ArtilleryManager::new, ArtilleryManager::load, null), NAME);
            if (m != null && (!m.shells.isEmpty() || !m.subs.isEmpty() || !m.spots.isEmpty())) m.tick(level);
        }
    }

    private static boolean groundEffect(int type) {
        if (type == Artillery.GUIDED || SpecialShells.groundEffect(type)) return true;
        return type == Artillery.HE || type == Artillery.CLUSTER || type == Artillery.GUNSHIP_105;
    }

    private static double holdRadius(ArtShell s) { return (s.gun() == Artillery.MORTAR ? 16.0 : 24.0) + (s.type() == Artillery.CLUSTER ? clusterRadius(s.gun()) : 0.0); }

    private static double clusterRadius(int gun) { return gun == Artillery.MORTAR ? 6.0 : 9.0; }

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % SHELL_SYNC == 0 && !shells.isEmpty()) shareNew(level, now, shells);

        for (ProjectilePath.ShellHit h : ProjectilePath.shells(level, shells, now)) endEarly(level, h.shell(), h.hit(), now);

        if (Debris.anyLive(level)) for (int i = 0; i < shells.size(); i++) {
            ArtShell s = shells.get(i);
            if (now <= s.start() + 2 || now > s.endTime()) continue;
            Vec3 hit = Debris.hitAlong(level, s.position(now - 1), s.position(now));
            if (hit == null) continue;
            endEarly(level, s, new ProjectilePath.PathHit(hit, ProjectilePath.Kind.DEBRIS, null, "debris", "clip"), now);
            i--;
        }

        if (!ForcefieldBlockEntity.LOADED.isEmpty()) for (int i = 0; i < shells.size(); i++) {
            ArtShell s = shells.get(i);
            if (now <= s.start()) continue;
            Forcefield.Crossing c = Forcefield.crossing(level, s.position(now - 1), s.position(now));
            if (c == null || !Forcefield.shellHit(level, c, s.gun() == Artillery.SUPER_GUN ? Balance.SUPER_GUN_BLAST_TIER : 1, WarheadType.HE)) continue;
            shells.remove(i--);
            setDirty();
            gone(level, s, c.at());
            if (s.last()) logDone(level, s, (int) Math.floor(c.at().x), (int) Math.floor(c.at().z));
        }
        FlightManager fm = null;
        for (int i = 0; i < shells.size(); i++) {
            ArtShell s = shells.get(i);
            if (!s.prepared() && groundEffect(s.type()) && now >= s.endTime() - PREPARE_TICKS) {
                int gx = (int) Math.floor(s.tx()), gz = (int) Math.floor(s.tz());
                if (!level.hasChunkAt(new BlockPos(gx, level.getMinBuildHeight(), gz))) {
                    if (fm == null) fm = FlightManager.get(level);
                    fm.holdForTest(level, gx, gz, holdRadius(s));
                    s = s.asPrepared();
                    shells.set(i, s);
                    setDirty();
                }
            }
            if (now > s.start() + 5 && now < s.endTime()) {
                Vec3 at = s.position(now);
                int cx = (int) Math.floor(at.x), cz = (int) Math.floor(at.z);
                boolean dotEnd = s.type() == Artillery.LASER && Math.hypot(at.x - s.tx(), at.z - s.tz()) < Balance.LASER_CLEAR_END;
                if (!dotEnd && Math.hypot(at.x - s.ox(), at.z - s.oz()) > 16.0 && level.hasChunk(cx >> 4, cz >> 4)) {
                    int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
                    if (at.y < gy - 0.5) {
                        ArtShell hit = new ArtShell(s.id(), s.owner(), s.ownerName(), s.gun(), s.type(), s.ox(), s.oy(), s.oz(), at.x, gy, at.z,
                                s.start(), (int) Math.max(1, now - s.start()), s.gunPos(), s.seq(), s.last(), s.prepared());
                        shells.remove(i--);
                        setDirty();
                        impact(level, hit, now);
                        continue;
                    }
                }
            }
            if (s.type() == Artillery.FLAK && s.gun() == Artillery.HOWITZER) {
                Vec3 burst = SpecialShells.flakTick(level, s, now);
                if (burst != null) {
                    shells.remove(i--);
                    setDirty();
                    StrikeToasts.shellDoneAt(level, s, true, burst.x, burst.z);
                    gone(level, s, burst);
                    if (s.last()) logDone(level, s, (int) Math.floor(burst.x), (int) Math.floor(burst.z));
                    continue;
                }
            }
            if (now >= s.endTime()) {

                int gx = (int) Math.floor(s.tx()), gz = (int) Math.floor(s.tz());
                if (needsGround(s) && Config.Server.chunkLoading() && !level.hasChunk(gx >> 4, gz >> 4) && now < s.endTime() + FlightManager.IMPACT_WAIT) {
                    if (!s.prepared()) {
                        if (fm == null) fm = FlightManager.get(level);
                        fm.holdForTest(level, gx, gz, holdArea(s));
                        s = s.asPrepared();
                        shells.set(i, s);
                        setDirty();
                    }
                    continue;
                }
                ArtShell on = extend(level, s, now);
                if (on != null) { shells.set(i, on); setDirty(); continue; }
                shells.remove(i--);
                setDirty();
                impact(level, s, now);
            }
        }
        if (now % FlightManager.HOLD_REFRESH == 0 && Config.Server.chunkLoading()) {
            for (ArtShell s : shells) {
                if (!s.prepared()) continue;
                if (fm == null) fm = FlightManager.get(level);
                fm.holdForTest(level, (int) Math.floor(s.tx()), (int) Math.floor(s.tz()), holdArea(s));
            }
        }
        for (int i = 0; i < subs.size(); i++) {
            Sub b = subs.get(i);
            if (now < b.at()) continue;
            subs.remove(i--);
            setDirty();
            bomblet(level, b, now);
        }
        if (now % 200 == 0 && spots.values().removeIf(v -> now >= v[2])) setDirty();

        if (now % 200 == 100 && !leaflets.isEmpty() && leaflets.keySet().removeIf(id -> shells.stream().noneMatch(s -> s.id() == id))) setDirty();
    }

    private ArtShell extend(ServerLevel level, ArtShell s, long now) {
        if (!(needsGround(s) || s.type() == Artillery.SMOKE) || s.type() == Artillery.CANISTER) return null;
        int gx = (int) Math.floor(s.tx()), gz = (int) Math.floor(s.tz());
        if (!level.hasChunk(gx >> 4, gz >> 4) || ground(level, gx, gz, s.ty(), false) >= s.ty() - 1.0) return null;
        Vec3 p = s.position(s.endTime()), v = s.velocity(s.endTime());
        double g = Balance.ART_GRAVITY;
        for (int k = 1; k <= EXTEND_TICKS; k++) {
            double x = p.x + v.x * k, z = p.z + v.z * k, y = p.y + v.y * k - g * k * k * 0.5;
            int cx = (int) Math.floor(x), cz = (int) Math.floor(z);
            if (y < level.getMinBuildHeight()) return null;
            if (!level.hasChunk(cx >> 4, cz >> 4)) {
                return k <= 1 ? null : relaunch(level, s, p, x, Math.max(y, level.getMinBuildHeight()), z, k, now);
            }
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
            if (y <= gy) return relaunch(level, s, p, x, gy, z, k, now);
        }
        return null;
    }

    static final int EXTEND_TICKS = 600;

    private ArtShell relaunch(ServerLevel level, ArtShell s, Vec3 p, double x, double y, double z, int ticks, long now) {
        ArtShell on = new ArtShell(s.id(), s.owner(), s.ownerName(), s.gun(), s.type(), p.x, p.y, p.z, x, y, z, now, ticks, s.gunPos(), s.seq(), s.last(), false);
        for (ServerPlayer pl : level.players()) {
            java.util.Set<Long> got = sent.get(pl.getUUID());
            if (got != null && got.contains(s.id())) PacketDistributor.sendToPlayer(pl, new ArtShellExtendPayload(on));
        }
        return on;
    }

    private static boolean needsGround(ArtShell s) { return s.gun() == Artillery.ROCKET || s.gun() == Artillery.SUPER_GUN || s.type() == Artillery.HE || s.type() == Artillery.GUIDED || s.type() == Artillery.GUNSHIP_105 || SpecialShells.groundEffect(s.type()); }

    private static double holdArea(ArtShell s) {
        if (s.gun() == Artillery.ROCKET) return Mlrs.HOLD;
        if (s.gun() == Artillery.SUPER_GUN) return SuperGunServer.holdRadius();
        return holdRadius(s);
    }

    static int ground(ServerLevel level, int x, int z, double fallback, boolean load) {
        if (!level.hasChunk(x >> 4, z >> 4)) return (int) Math.floor(fallback);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private void impact(ServerLevel level, ArtShell s, long now) {
        StrikeToasts.shellDone(level, s, true);
        MusicTriggersHook.artillery(level, s);
        if (s.gun() == Artillery.ROCKET) { Mlrs.impact(this, level, s, now); return; }
        if (s.gun() == Artillery.SUPER_GUN) { SuperGunServer.impact(level, s, now); return; }
        int gx = (int) Math.floor(s.tx()), gz = (int) Math.floor(s.tz());
        FlightManager fm = FlightManager.get(level);
        if (Artillery.isSpecial(s.type())) {
            SpecialShells.impact(this, level, fm, s, now);
            if (s.last()) logDone(level, s, gx, gz);
            return;
        }
        switch (s.type()) {
            case Artillery.HE, Artillery.GUIDED, Artillery.GUNSHIP_105 -> {
                if (!s.prepared() && !level.hasChunkAt(new BlockPos(gx, level.getMinBuildHeight(), gz))) fm.holdForTest(level, gx, gz, holdRadius(s));
                int gy = ground(level, gx, gz, s.ty(), true);
                heBurst(level, fm, s, gx, gy, gz);
                if (s.last()) logDone(level, s, gx, gz);
            }
            case Artillery.CLUSTER -> {
                if (!s.prepared() && !level.hasChunkAt(new BlockPos(gx, level.getMinBuildHeight(), gz))) fm.holdForTest(level, gx, gz, holdRadius(s));
                send(level, s, s.tx(), s.ty(), s.tz());
                int n = Math.max(1, Balance.ART_CLUSTER_BOMBLETS);
                double rr = clusterRadius(s.gun());
                for (int k = 0; k < n; k++) {
                    double a = level.random.nextDouble() * Math.PI * 2.0, d = rr * Math.sqrt(level.random.nextDouble());
                    subs.add(new Sub(s.gun(), s.owner(), s.tx() + Math.cos(a) * d, s.tz() + Math.sin(a) * d, now + 8 + level.random.nextInt(10), k == n - 1 && s.last() ? Math.max(1, s.seq()) : 0, s.tx(), s.tz()));
                }

                if (s.prepared() || !level.hasChunkAt(new BlockPos(gx, level.getMinBuildHeight(), gz))) fm.release(level, gx, gz, holdRadius(s), now + 300);
                setDirty();
            }
            default -> {
                double y = s.type() == Artillery.SMOKE ? ground(level, gx, gz, s.ty(), false) : s.ty();
                send(level, s, s.tx(), y, s.tz());
                if (s.last()) logDone(level, s, gx, gz);
            }
        }
    }

    void heBurstAt(ServerLevel level, FlightManager fm, ArtShell s, int gx, int gy, int gz) { heBurst(level, fm, s, gx, gy, gz); }

    static void sendImpact(ServerLevel level, ArtShell s, double x, double y, double z) { send(level, s, x, y, z); }

    static int holdOf(ArtShell s) { return (int) Math.ceil(holdRadius(s)); }

    private void heBurst(ServerLevel level, FlightManager fm, ArtShell s, int gx, int gy, int gz) {
        boolean g105 = s.type() == Artillery.GUNSHIP_105;
        double r = g105 ? Balance.GUNSHIP_HE_RADIUS : s.gun() == Artillery.MORTAR ? Balance.MORTAR_HE_RADIUS : Balance.HOWITZER_HE_RADIUS;
        Vec3 pos = new Vec3(gx + 0.5, gy, gz + 0.5);
        WarheadFx.hurt(level, pos, r, g105 ? Balance.GUNSHIP_HE_REACH : 3.5, g105 ? Balance.GUNSHIP_HE_LETHAL : 0.6, g105 ? Balance.GUNSHIP_HE_DMG : s.gun() == Artillery.MORTAR ? 0.5 : 0.8, 0.6, 3,
                hasOwner(s.owner()) ? s.owner() : null);
        FusionReactorBlockEntity.blastAt(level, pos, r);
        AntimatterStoreBlockEntity.blastAt(level, pos, r);
        RailgunBlockEntity.blastAt(level, pos, r);
        Crater crater = new Crater(gx, gy, gz, r, 0.6, 0.4, 0.0, holdRadius(s));
        fm.craters.add(crater);
        DamageRings.queue(level, crater, WarheadType.HE);
        MapFeed.hit(level, g105 ? "gunship 105" : s.gun() == Artillery.MORTAR ? "mortar he" : "howitzer he", 0, false, gx + 0.5, gy, gz + 0.5, r, hasOwner(s.owner()) ? s.owner() : null);
        Debris.blast(level, gx, gy, gz, r, crater);
        fm.setDirty();
        ShipStrike.blast(level, pos, r, g105 ? (float) Balance.GUNSHIP_HE_SHIP_POWER : ShipStrike.powerForGun(s.gun()), hasOwner(s.owner()) ? s.owner() : null);
        send(level, s, pos.x, gy, pos.z);
    }

    void endEarly(ServerLevel level, ArtShell s, ProjectilePath.PathHit hit, long now) {
        int i = -1;
        for (int j = 0; j < shells.size(); j++) if (shells.get(j).id() == s.id()) { i = j; break; }
        if (i < 0) return;
        s = shells.remove(i);
        setDirty();
        Vec3 p = hit.pos();
        Vec3 v = s.velocity(now);
        if (v.lengthSqr() > 1.0E-6) p = p.subtract(v.normalize().scale(0.25));
        int gx = (int) Math.floor(p.x), gy = (int) Math.floor(p.y), gz = (int) Math.floor(p.z);
        StrikeToasts.shellDoneAt(level, s, true, p.x, p.z);
        FlightManager fm = FlightManager.get(level);
        if (s.prepared()) fm.release(level, (int) Math.floor(s.tx()), (int) Math.floor(s.tz()), holdArea(s), now + 100);
        UUID owner = hasOwner(s.owner()) ? s.owner() : null;
        ProjectilePath.log("shell #" + s.id() + " (" + itemKey(s) + ")", hit);
        if (s.gun() == Artillery.SUPER_GUN) SuperGunServer.endedEarly(level, s, p, now);
        else if (s.gun() == Artillery.ROCKET) Mlrs.burstAt(level, s, p);
        else {
            switch (s.type()) {
                case Artillery.HE, Artillery.GUIDED, Artillery.LASER, Artillery.ROCKET_ASSIST, Artillery.PIERCE, Artillery.GUNSHIP_105 -> heBurst(level, fm, s, gx, gy, gz);
                case Artillery.CLUSTER -> {
                    send(level, s, p.x, p.y, p.z);
                    WarheadFx.hurt(level, p, 1.5, 3.0, 0.6, 0.45, 1.0, 3, owner);
                    ShipStrike.blast(level, p, 1.5, (float) Balance.SHIP_BLAST_BOMBLET, owner);
                    int n = Math.max(1, Balance.ART_CLUSTER_BOMBLETS);
                    double rr = clusterRadius(s.gun());
                    for (int k = 0; k < n; k++) {
                        double a = level.random.nextDouble() * Math.PI * 2.0, d = rr * Math.sqrt(level.random.nextDouble());
                        subs.add(new Sub(s.gun(), s.owner(), p.x + Math.cos(a) * d, p.z + Math.sin(a) * d, now + 8 + level.random.nextInt(10), k == n - 1 && s.last() ? Math.max(1, s.seq()) : 0, p.x, p.z));
                    }
                }
                default -> send(level, s, p.x, p.y, p.z);
            }
            if (s.last() && s.type() != Artillery.CLUSTER) logDone(level, s, gx, gz);
        }
        if (owner != null && hit.kind() == ProjectilePath.Kind.SHIP && shipLogOk(owner, hit.shipId(), now))
            CcBattleLog.add(level.getServer(), owner, CcBattleLog.HIT, "log.wmd.ship_struck_art", hit.shipName(), "#" + itemKey(s), Integer.toString(gx), Integer.toString(gy), Integer.toString(gz));
        gone(level, s, p);
    }

    void gone(ServerLevel level, ArtShell s, Vec3 p) {
        for (ServerPlayer pl : level.players()) {
            java.util.Set<Long> got = sent.get(pl.getUUID());
            if (got == null || !got.remove(s.id())) continue;
            if (pl.distanceToSqr(p.x, p.y, p.z) <= NEAR * NEAR) continue;
            PacketDistributor.sendToPlayer(pl, new ArtImpactPayload(s.id(), s.gun(), s.type(), p.x, p.y, p.z, ArtImpactPayload.GONE));
        }
    }

    private static final Map<String, Long> SHIP_LOG = Statics.server(new HashMap<>());
    static final int SHIP_LOG_TICKS = 100;

    static boolean shipLogOk(UUID owner, UUID ship, long now) {
        String k = owner + "|" + ship;
        Long last = SHIP_LOG.get(k);
        if (last != null && now - last >= 0 && now - last < SHIP_LOG_TICKS) return false;
        if (SHIP_LOG.size() > 256) SHIP_LOG.clear();
        SHIP_LOG.put(k, now);
        return true;
    }

    private static void send(ServerLevel level, ArtShell s, double x, double y, double z) {
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, NEAR, new ArtImpactPayload(s.id(), s.gun(), s.type(), x, y, z, ArtImpactPayload.IMPACT));
    }

    private void bomblet(ServerLevel level, Sub b, long now) {
        int bx = (int) Math.floor(b.x()), bz = (int) Math.floor(b.z());
        if (level.hasChunkAt(new BlockPos(bx, level.getMinBuildHeight(), bz))) {
            int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            Vec3 pos = new Vec3(bx + 0.5, by, bz + 0.5);
            double r = b.gun() == Artillery.MORTAR ? 1.2 : 1.5;
            WarheadFx.hurt(level, pos, r, 3.0, 0.6, 0.45, 1.0, 3, hasOwner(b.owner()) ? b.owner() : null);
            FlightManager fm = FlightManager.get(level);
            Crater crater = new Crater(bx, by, bz, r, 0.5, 0.5, 0.0, r * 1.5 + 16.0);
            fm.craters.add(crater);
            DamageRings.queue(level, crater, WarheadType.CLUSTER);
            Debris.blast(level, bx, by, bz, r, crater, Balance.DEBRIS_BOMBLET_SHARE, false);
            fm.setDirty();
            ShipStrike.blast(level, pos, r, (float) Balance.SHIP_BLAST_BOMBLET, hasOwner(b.owner()) ? b.owner() : null);
            PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, 1500, new ImpactPayload(pos.x, pos.y, pos.z, 1, WarheadType.CLUSTER.ordinal(), WarheadFx.ST_BOMBLET));
        }
        if (b.lastSeq() > 0 && hasOwner(b.owner()))
            CcBattleLog.add(level.getServer(), b.owner(), CcBattleLog.HIT, "log.wmd.art_done", "#item.wmd." + Artillery.shellId(b.gun(), Artillery.CLUSTER), Integer.toString(b.lastSeq()),
                    Long.toString(Math.round(b.cx())), Long.toString(Math.round(b.cz())));
    }

    private static void logDone(ServerLevel level, ArtShell s, int x, int z) {
        if (!hasOwner(s.owner())) return;
        CcBattleLog.add(level.getServer(), s.owner(), CcBattleLog.HIT, "log.wmd.art_done", "#" + itemKey(s), Integer.toString(Math.max(1, s.seq())), Integer.toString(x), Integer.toString(z));
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sendTo(p);
    }

    @SubscribeEvent
    static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sendTo(p);
    }

    private static void sendTo(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        ArtilleryManager m = level.getDataStorage().get(new SavedData.Factory<>(ArtilleryManager::new, ArtilleryManager::load, null), NAME);
        if (m == null) { PacketDistributor.sendToPlayer(p, new ArtShellsPayload(List.of(), true)); return; }
        long now = level.getGameTime();
        java.util.Set<Long> got = new java.util.HashSet<>();
        m.sent.put(p.getUUID(), got);
        List<ArtShell> l = m.viewOf(p, m.shells, got, now, new FlightIntel.Radars(level, now), CommandCenter.ownersTrusting(p.getUUID()));
        PacketDistributor.sendToPlayer(p, new ArtShellsPayload(l, true));
    }

    private void shareNew(ServerLevel level, long now, List<ArtShell> list) {
        java.util.Set<UUID> here = new java.util.HashSet<>();
        for (ServerPlayer p : level.players()) here.add(p.getUUID());
        sent.keySet().retainAll(here);
        if (now % 100 == 0) {
            java.util.Set<Long> live = new java.util.HashSet<>();
            for (ArtShell s : shells) live.add(s.id());
            for (java.util.Set<Long> v : sent.values()) v.retainAll(live);
        }
        FlightIntel.Radars radars = new FlightIntel.Radars(level, now);
        for (ServerPlayer p : level.players()) {
            java.util.Set<Long> got = sent.computeIfAbsent(p.getUUID(), k -> new java.util.HashSet<>());
            List<ArtShell> l = viewOf(p, list, got, now, radars, CommandCenter.ownersTrusting(p.getUUID()));
            if (!l.isEmpty()) PacketDistributor.sendToPlayer(p, new ArtShellsPayload(l, false));
        }
    }

    private List<ArtShell> viewOf(ServerPlayer p, List<ArtShell> list, java.util.Set<Long> got, long now, FlightIntel.Radars radars, java.util.Set<UUID> team) {
        List<ArtShell> out = new ArrayList<>();
        for (ArtShell s : list) {
            if (out.size() >= ArtShellsPayload.MAX) break;
            if (got.contains(s.id()) || now > s.endTime()) continue;
            boolean mine = hasOwner(s.owner()) && FlightIntel.friendly(p, s.owner(), team);
            if (!mine) {
                Vec3 pos = s.position(now);
                double tdx = s.tx() - p.getX(), tdz = s.tz() - p.getZ();
                boolean landing = s.endTime() - now <= TARGET_LEAD && tdx * tdx + tdz * tdz <= NEAR * NEAR;
                if (!landing && !FlightIntel.near(p, pos.x, pos.z, 1.0) && !radars.seesPoint(p.getUUID(), team, pos.x, pos.z, Double.MAX_VALUE, 1.0)) continue;
            }
            got.add(s.id());

            double gx = s.ox() - p.getX(), gz = s.oz() - p.getZ(), heard = s.gun() == Artillery.SUPER_GUN ? Balance.SUPER_GUN_HEARD : HEARD;
            out.add(mine ? s : gx * gx + gz * gz <= heard * heard ? s.forStrangerAtGun() : s.forStranger());
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        for (ArtShell s : shells) l.add(s.save());
        tag.put("shells", l);
        ListTag b = new ListTag();
        for (Sub s : subs) b.add(s.save());
        tag.put("subs", b);
        ListTag sp = new ListTag();
        for (Map.Entry<UUID, long[]> e : spots.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("o", e.getKey());
            t.putLongArray("v", e.getValue());
            sp.add(t);
        }
        tag.put("spots", sp);
        CompoundTag lf = new CompoundTag();
        for (Map.Entry<Long, String> e : leaflets.entrySet()) lf.putString(Long.toString(e.getKey()), e.getValue());
        tag.put("leaflets", lf);
        tag.putLong("nextId", nextId);
        return tag;
    }

    public static ArtilleryManager load(CompoundTag tag, HolderLookup.Provider registries) {
        ArtilleryManager m = new ArtilleryManager();
        for (Tag t : tag.getList("shells", Tag.TAG_COMPOUND)) m.shells.add(ArtShell.load((CompoundTag) t));
        for (Tag t : tag.getList("subs", Tag.TAG_COMPOUND)) m.subs.add(Sub.load((CompoundTag) t));
        for (Tag t : tag.getList("spots", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            long[] v = c.getLongArray("v");
            if (c.hasUUID("o") && v.length == 3) m.spots.put(c.getUUID("o"), v);
        }
        CompoundTag lf = tag.getCompound("leaflets");
        for (String k : lf.getAllKeys()) { try { m.leaflets.put(Long.parseLong(k), lf.getString(k)); } catch (NumberFormatException ignored) {} }
        m.nextId = Math.max(1, tag.getLong("nextId"));
        return m;
    }
}
