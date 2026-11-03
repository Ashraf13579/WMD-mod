package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CcStrikePlannerBlockEntity extends CcConsoleBlockEntity {

    public static final int MIN_PADS = 1, MAX_PADS = CcBPayload.MAX_PADS;

    public static final int LEAD_TICKS = 100;

    public CcStrikePlannerBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_STRIKE_PLANNER_BE.get(), pos, state, Balance.CC_STRIKE_FE); }

    @Override public CcModule kind() { return CcModule.STRIKE_PLANNER; }

    @Override protected boolean wantsIntel() { return true; }

    @Override
    protected ListTag buildRows(ServerLevel level, UUID owner) {
        ListTag out = new ListTag();
        for (Tag t : CcLaunch.rows(level, owner)) out.add(t);
        for (ArtilleryGunBlockEntity g : ArtilleryGuns.gunsOf(owner)) {
            CompoundTag c = CcArtilleryConsoleBlockEntity.row(level, owner, g);
            c.putInt("k", 0);
            c.putBoolean("gun", true);
            out.add(c);
        }
        for (CruiseLauncherBlockEntity l : CruiseLaunchers.launchersOf(owner)) {
            CompoundTag c = CruiseLaunchers.row(level, owner, l);
            c.putInt("k", 0);
            out.add(c);
        }
        for (CcStrikeData.Shot s : CcStrikeData.get(level.getServer()).shotsOf(owner)) {
            CompoundTag c = new CompoundTag();
            c.putInt("k", 1);
            c.putLong("p", s.pos().asLong());
            c.putLong("fire", s.fireAt());
            c.putLong("impact", s.impactAt());
            c.putInt("st", s.state());
            c.putInt("x", s.x());
            c.putInt("z", s.z());
            out.add(c);
        }
        return out;
    }

    @Override
    protected Component act(ServerPlayer player, CcBPayload p) {
        ServerLevel sl = (ServerLevel) level;
        String P = "message.wmd.cc.strike.";
        CcStrikeData data = CcStrikeData.get(sl.getServer());
        if (p.action() == CcBPayload.COUNTER) {
            CcBuildingBlockEntity b = building();
            CcTarget t = b != null ? b.designated() : null;
            if (t == null || t.source() != CcTarget.SRC_COUNTER) return Component.translatable("message.wmd.cc.counter.none");
            Component r = counter(sl, player, getOwner(), data, t.x(), t.z());
            if (r == null) { b.disarmCounter(); arm(); }
            return r;
        }
        if (p.action() == CcBPayload.PACKAGE) {
            List<int[]> targets = new ArrayList<>();
            for (long v : new java.util.LinkedHashSet<>(p.pads())) targets.add(new int[] { Mth.clamp(CcBPayload.unpackX(v), -29_999_000, 29_999_000),
                    Mth.clamp(CcBPayload.unpackZ(v), -29_999_000, 29_999_000), kindAt(sl, getOwner(), CcBPayload.unpackX(v), CcBPayload.unpackZ(v)) });
            if (targets.isEmpty()) return Component.translatable("message.wmd.cc.package.none");
            Component r = strikePackage(sl, player, getOwner(), data, targets);
            if (r == null) arm();
            return r;
        }
        if (p.action() == CcBPayload.CANCEL) {
            int n = data.cancel(getOwner());
            arm();
            return Component.translatable(n > 0 ? P + "cancelled" : P + "nothing", n);
        }
        if (p.action() != CcBPayload.STRIKE) return null;
        Set<Long> uniq = new HashSet<>(p.pads());
        if (uniq.size() < MIN_PADS || uniq.size() > MAX_PADS) return Component.translatable(P + "count", MIN_PADS, MAX_PADS);
        int x = Mth.clamp(p.x(), -29_999_000, 29_999_000), z = Mth.clamp(p.z(), -29_999_000, 29_999_000);
        Component bad = plan(sl, player, getOwner(), data, new ArrayList<>(uniq), x, z, (p.arg() & 1L) != 0L);
        if (bad != null) return bad;
        arm();
        return null;
    }

    static Component plan(ServerLevel level, ServerPlayer player, UUID owner, CcStrikeData data, List<Long> pads, int x, int z) {
        return plan(level, player, owner, data, pads, x, z, false);
    }

    static Component plan(ServerLevel level, ServerPlayer player, UUID owner, CcStrikeData data, List<Long> pads, int x, int z, boolean jam) {
        String P = "message.wmd.cc.strike.";
        List<LaunchTerminalBlockEntity> ts = new ArrayList<>();
        List<Integer> etas = new ArrayList<>();
        List<GunPick> guns = new ArrayList<>();
        List<CruisePick> cruise = new ArrayList<>();
        int longest = 0, turn = 0;
        for (long l : pads) {
            BlockPos tp = BlockPos.of(l);
            ArtilleryGunBlockEntity gun = ArtilleryGuns.gunAt(level, tp, owner);
            if (gun != null) {
                Object o = evaluateGun(level, owner, data, gun, x, z, gun.picked());
                if (o instanceof Component why) return why;
                GunPick k = (GunPick) o;
                guns.add(k);
                longest = Math.max(longest, k.eta());
                continue;
            }
            CruiseLauncherBlockEntity cl = CruiseLaunchers.launcherAt(level, tp, owner);
            if (cl != null) {
                Object o = evaluateCruise(level, owner, data, cl, x, z);
                if (o instanceof Component why) return why;
                CruisePick k = (CruisePick) o;
                cruise.add(k);
                longest = Math.max(longest, k.eta());
                continue;
            }
            LaunchTerminalBlockEntity t = CcLaunch.terminalAt(level, tp, owner);
            if (t == null) return Component.translatable("message.wmd.cc.launch.no_terminal", tp.toShortString());
            CcReach.Result r = CcReach.check(level, owner, tp);
            if (!r.ok()) return Component.translatable("message.wmd.cc.launch.out_of_reach", tp.toShortString(), r.text());
            if (data.busy(level, tp)) return Component.translatable(P + "busy", tp.toShortString());
            int eta = t.predictTicks(x, z);
            if (eta < 0) return Component.translatable(P + "no_missile", tp.toShortString());
            if (t.getRange() <= 0) return Component.translatable(P + "no_radar", tp.toShortString());
            turn = Math.max(turn, t.radarTurnTicks(x, z));
            net.minecraft.world.phys.Vec3 pcw = t.padCentreWorld(); double d = Math.hypot(x + 0.5 - pcw.x, z + 0.5 - pcw.z);
            if (d < Missiles.MIN_DISTANCE) return Component.translatable(P + "too_close", tp.toShortString());
            MissileBuild b = t.shownBuild();
            if (b != null && d > MissileStats.range(b)) return Component.translatable(P + "too_far", tp.toShortString(), (int) d, MissileStats.range(b));
            ts.add(t);
            etas.add(eta);
            longest = Math.max(longest, eta);
        }
        long now = level.getGameTime();
        long impact = now + Math.max(LEAD_TICKS, turn + 40) + longest;
        List<long[]> shots = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) {
            ts.get(i).remoteAim(x, z);
            shots.add(new long[] { ts.get(i).getBlockPos().asLong(), impact - etas.get(i) });
        }
        for (GunPick k : guns) {
            k.g().remoteLay(x, z, k.type());
            shots.add(new long[] { k.g().getBlockPos().asLong(), impact - k.eta(), k.type() });
        }
        for (CruisePick k : cruise) {
            k.l().aimAt(x, z);
            shots.add(new long[] { k.l().getBlockPos().asLong(), impact - k.eta(), CcStrikeData.CRUISE });
        }
        boolean jammed = false;
        if (jam) {
            java.util.Set<Long> used = new java.util.HashSet<>();
            for (long[] sh : shots) used.add(sh[0]);
            outer:
            for (ArtilleryGunBlockEntity g : idleGuns(level, owner)) {
                if (g.gun() != Artillery.HOWITZER || used.contains(g.getBlockPos().asLong())) continue;
                for (int k = 0; k < Balance.RACK_SLOTS; k++) {
                    int pick = Artillery.PICK_RACK + k;
                    if (g.firedType(pick) != Artillery.JAMMING) continue;
                    if (!(evaluateGun(level, owner, data, g, x, z, pick) instanceof GunPick gp)) continue;
                    long fire = impact - Balance.JAM_FIRST_LEAD - gp.eta();
                    if (fire < now + g.busyTicks() + 2) continue;
                    g.remoteLay(x, z, pick);
                    shots.add(new long[] { g.getBlockPos().asLong(), fire, pick });
                    jammed = true;
                    break outer;
                }
            }
        }
        long id = data.add(level, owner, player.getGameProfile().getName(), shots, x, z, impact);
        Wmd.LOGGER.info("Strike planner: {} planned strike {} at {} {} with {} pads + {} guns, impact in {} s", player.getGameProfile().getName(), id, x, z, ts.size(),
                guns.size(), (impact - now) / 20);
        player.displayClientMessage(Component.translatable(P + "planned", ts.size(), x, z, OrbitMath.mmss((impact - now) * 50L)), false);
        if (!guns.isEmpty()) player.displayClientMessage(Component.translatable(P + "guns", guns.size()), false);
        if (!cruise.isEmpty()) player.displayClientMessage(Component.translatable(P + "cruise", cruise.size()), false);
        if (jam) player.displayClientMessage(Component.translatable(jammed ? P + "jam_first" : P + "jam_none"), false);
        return null;
    }

    record CruisePick(CruiseLauncherBlockEntity l, int eta) {}

    static Object evaluateCruise(ServerLevel level, UUID owner, CcStrikeData data, CruiseLauncherBlockEntity l, int x, int z) {
        String P = "message.wmd.cc.strike.";
        BlockPos lp = l.getBlockPos();
        if (l.getLevel() != level || !CruiseLaunchers.loaded(l)) return Component.translatable(P + "cruise_gone", lp.toShortString());
        CcReach.Result r = CcReach.check(level, owner, lp);
        if (!r.ok()) return Component.translatable("message.wmd.cc.launch.out_of_reach", lp.toShortString(), r.text());
        if (data.busy(level, lp)) return Component.translatable(P + "busy", lp.toShortString());
        int st = l.checkAt(x, z);
        if (st != CruiseLauncherBlockEntity.S_READY) return Component.translatable(P + "cruise_cannot", lp.toShortString(), Component.translatable("gui.wmd.cruise.st." + st));
        int eta = l.strikeTicks(x, z);
        if (eta < 0) return Component.translatable(P + "cruise_cannot", lp.toShortString(), Component.translatable("gui.wmd.cruise.st." + CruiseLauncherBlockEntity.S_NO_MISSILE));
        return new CruisePick(l, eta + 1);
    }

    record Pick(LaunchTerminalBlockEntity t, int eta, int turn) {}

    static Object evaluate(ServerLevel level, UUID owner, CcStrikeData data, LaunchTerminalBlockEntity t, int x, int z) {
        String P = "message.wmd.cc.strike.";
        BlockPos tp = t.getBlockPos();
        if (t.getLevel() != level || !CcLaunch.loaded(t)) return Component.translatable("message.wmd.cc.launch.no_terminal", tp.toShortString());
        if (t.getStage() != LaunchTerminalBlockEntity.Stage.READY) return Component.translatable("message.wmd.cc.counter.not_ready", tp.toShortString());
        CcReach.Result r = CcReach.check(level, owner, tp);
        if (!r.ok()) return Component.translatable("message.wmd.cc.launch.out_of_reach", tp.toShortString(), r.text());
        if (data.busy(level, tp)) return Component.translatable(P + "busy", tp.toShortString());
        int eta = t.predictTicks(x, z);
        if (eta < 0) return Component.translatable(P + "no_missile", tp.toShortString());
        if (t.getRange() <= 0) return Component.translatable(P + "no_radar", tp.toShortString());
        net.minecraft.world.phys.Vec3 pcw = t.padCentreWorld(); double d = Math.hypot(x + 0.5 - pcw.x, z + 0.5 - pcw.z);
        if (d < Missiles.MIN_DISTANCE) return Component.translatable(P + "too_close", tp.toShortString());
        MissileBuild b = t.shownBuild();
        if (b != null && d > MissileStats.range(b)) return Component.translatable(P + "too_far", tp.toShortString(), (int) d, MissileStats.range(b));
        return new Pick(t, eta, t.radarTurnTicks(x, z));
    }

    record GunPick(ArtilleryGunBlockEntity g, int eta, int type) {}

    static Object evaluateGun(ServerLevel level, UUID owner, CcStrikeData data, ArtilleryGunBlockEntity g, int x, int z, int type) {
        String P = "message.wmd.cc.strike.";
        BlockPos gp = g.getBlockPos();
        if (g.getLevel() != level || !ArtilleryGuns.loaded(g)) return Component.translatable("message.wmd.art_console.gone", gp.toShortString());
        CcReach.Result r = CcReach.check(level, owner, gp);
        if (!r.ok()) return Component.translatable("message.wmd.cc.launch.out_of_reach", gp.toShortString(), r.text());
        if (data.busy(level, gp)) return Component.translatable(P + "busy", gp.toShortString());
        int st = g.checkFor(x, z, type);
        if (st != ArtilleryGunBlockEntity.S_READY) return Component.translatable(P + "gun_cannot", gp.toShortString(), Component.translatable("gui.wmd.art.st." + st));
        int eta = g.flightTicksFor(x, z, type);
        if (eta < 0) return Component.translatable(P + "gun_cannot", gp.toShortString(), Component.translatable("gui.wmd.art.st." + ArtilleryGunBlockEntity.S_TOO_FAR));
        return new GunPick(g, eta + 1, type);
    }

    private static List<ArtilleryGunBlockEntity> idleGuns(ServerLevel level, UUID owner) {
        List<ArtilleryGunBlockEntity> l = new ArrayList<>();
        for (ArtilleryGunBlockEntity g : ArtilleryGuns.gunsOf(owner)) if (g.getLevel() == level && g.mission() == 0) l.add(g);
        return l;
    }

    private static List<LaunchTerminalBlockEntity> readyPads(ServerLevel level, UUID owner) {
        List<LaunchTerminalBlockEntity> l = new ArrayList<>();
        for (LaunchTerminalBlockEntity t : CcLaunch.terminalsOf(owner))
            if (t.getLevel() == level && t.getStage() == LaunchTerminalBlockEntity.Stage.READY) l.add(t);
        return l;
    }

    private static long schedule(ServerLevel level, ServerPlayer player, UUID owner, CcStrikeData data, List<List<Pick>> perTarget, List<int[]> targets) {
        List<List<GunPick>> none = new ArrayList<>();
        for (int i = 0; i < perTarget.size(); i++) none.add(List.of());
        return schedule(level, player, owner, data, perTarget, none, targets);
    }

    private static long schedule(ServerLevel level, ServerPlayer player, UUID owner, CcStrikeData data, List<List<Pick>> perTarget, List<List<GunPick>> gunsPer,
                                 List<int[]> targets) {
        int longest = 0, turn = 0;
        for (List<Pick> ps : perTarget) for (Pick k : ps) { longest = Math.max(longest, k.eta()); turn = Math.max(turn, k.turn()); }
        for (List<GunPick> gs : gunsPer) for (GunPick k : gs) longest = Math.max(longest, k.eta());
        long now = level.getGameTime();
        long impact = now + Math.max(LEAD_TICKS, turn + 40) + longest;
        for (int i = 0; i < perTarget.size(); i++) {
            List<Pick> ps = perTarget.get(i);
            List<GunPick> gs = gunsPer.get(i);
            if (ps.isEmpty() && gs.isEmpty()) continue;
            int[] tg = targets.get(i);
            List<long[]> shots = new ArrayList<>();
            for (Pick k : ps) {
                k.t().remoteAim(tg[0], tg[1]);
                shots.add(new long[] { k.t().getBlockPos().asLong(), impact - k.eta() });
            }
            for (GunPick k : gs) {
                k.g().remoteLay(tg[0], tg[1], k.type());
                shots.add(new long[] { k.g().getBlockPos().asLong(), impact - k.eta(), k.type() });
            }
            data.add(level, owner, player.getGameProfile().getName(), shots, tg[0], tg[1], impact);
        }
        return impact;
    }

    static Component counter(ServerLevel level, ServerPlayer player, UUID owner, CcStrikeData data, int x, int z) {
        List<Pick> picks = new ArrayList<>();
        Component firstWhy = null;
        int skipped = 0;
        for (LaunchTerminalBlockEntity t : readyPads(level, owner)) {
            Object o = evaluate(level, owner, data, t, x, z);
            if (o instanceof Pick k) picks.add(k);
            else { skipped++; if (firstWhy == null) firstWhy = (Component) o; }
        }
        List<GunPick> guns = new ArrayList<>();
        for (ArtilleryGunBlockEntity g : idleGuns(level, owner)) if (evaluateGun(level, owner, data, g, x, z, Artillery.HE) instanceof GunPick k) guns.add(k);
        if (picks.isEmpty() && guns.isEmpty()) return firstWhy != null ? Component.translatable("message.wmd.cc.counter.no_pads_why", firstWhy) : Component.translatable("message.wmd.cc.counter.no_pads");
        long impact = schedule(level, player, owner, data, List.of(picks), List.of(guns), List.of(new int[] { x, z }));
        if (!guns.isEmpty()) player.displayClientMessage(Component.translatable("message.wmd.cc.strike.guns", guns.size()), false);
        long now = level.getGameTime();
        Wmd.LOGGER.info("Strike planner: {} COUNTER-STRIKE at {} {} with {} pads, impact in {} s", player.getGameProfile().getName(), x, z, picks.size(), (impact - now) / 20);
        player.displayClientMessage(Component.translatable("message.wmd.cc.counter.planned", picks.size(), x, z, OrbitMath.mmss((impact - now) * 50L), skipped), false);
        CcBattleLog.add(level.getServer(), owner, CcBattleLog.COUNTER, "log.wmd.counter", Integer.toString(picks.size()), Integer.toString(x), Integer.toString(z));
        return null;
    }

    public static int priority(int kind) {
        return switch (kind) { case Recon.ICON_RADAR, Recon.ICON_DEFENCE -> 0; case Recon.ICON_LAUNCH -> 1; case Recon.ICON_STATION -> 2; default -> 3; };
    }

    private static int kindAt(ServerLevel level, UUID owner, int x, int z) {
        ReconData.Entry e = ReconData.get(level.getServer()).entry(owner);
        int[] ic = e != null && e.snap != null ? e.snap.icons() : new int[0];
        for (int i = 0; i + 2 < ic.length; i += 3) if (Math.abs(ic[i] - x) <= 2 && Math.abs(ic[i + 1] - z) <= 2) return ic[i + 2];
        return Recon.ICON_OTHER;
    }

    static Component strikePackage(ServerLevel level, ServerPlayer player, UUID owner, CcStrikeData data, List<int[]> targets) {
        CcBuildingBlockEntity home = null;
        for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner)) if (b.getLevel() == level) { home = b; break; }
        final BlockPos hc = home != null ? home.hallCentre() : player.blockPosition();
        List<int[]> ts = new ArrayList<>(targets);
        ts.sort((a, b) -> priority(a[2]) != priority(b[2]) ? Integer.compare(priority(a[2]), priority(b[2]))
                : Double.compare(Math.hypot(a[0] - hc.getX(), a[1] - hc.getZ()), Math.hypot(b[0] - hc.getX(), b[1] - hc.getZ())));
        List<LaunchTerminalBlockEntity> free = readyPads(level, owner);

        java.util.Map<LaunchTerminalBlockEntity, Pick[]> can = new java.util.LinkedHashMap<>();
        for (LaunchTerminalBlockEntity t : free) {
            Pick[] row = new Pick[ts.size()];
            for (int i = 0; i < ts.size(); i++) if (evaluate(level, owner, data, t, ts.get(i)[0], ts.get(i)[1]) instanceof Pick k) row[i] = k;
            can.put(t, row);
        }
        List<List<Pick>> per = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) per.add(new ArrayList<>());
        java.util.Set<LaunchTerminalBlockEntity> used = new java.util.HashSet<>();

        for (int i = 0; i < ts.size(); i++) {
            Pick best = null;
            for (java.util.Map.Entry<LaunchTerminalBlockEntity, Pick[]> e : can.entrySet())
                if (!used.contains(e.getKey()) && e.getValue()[i] != null && (best == null || e.getValue()[i].eta() < best.eta())) best = e.getValue()[i];
            if (best != null) { per.get(i).add(best); used.add(best.t()); }
        }

        boolean gave = true;
        while (gave && used.size() < can.size()) {
            gave = false;
            for (int i = 0; i < ts.size(); i++) {
                Pick best = null;
                for (java.util.Map.Entry<LaunchTerminalBlockEntity, Pick[]> e : can.entrySet())
                    if (!used.contains(e.getKey()) && e.getValue()[i] != null && (best == null || e.getValue()[i].eta() < best.eta())) best = e.getValue()[i];
                if (best != null) { per.get(i).add(best); used.add(best.t()); gave = true; }
            }
        }

        List<List<GunPick>> gunsPer = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) gunsPer.add(new ArrayList<>());
        int ti = 0, gunsUsed = 0;
        for (ArtilleryGunBlockEntity g : idleGuns(level, owner)) {
            for (int k = 0; k < ts.size(); k++) {
                int i = (ti + k) % ts.size();
                if (evaluateGun(level, owner, data, g, ts.get(i)[0], ts.get(i)[1], Artillery.HE) instanceof GunPick gp) {
                    gunsPer.get(i).add(gp);
                    gunsUsed++;
                    ti = i + 1;
                    break;
                }
            }
        }
        int covered = 0;
        for (int i = 0; i < per.size(); i++) if (!per.get(i).isEmpty() || !gunsPer.get(i).isEmpty()) covered++;
        if (covered == 0) return Component.translatable(free.isEmpty() && gunsUsed == 0 ? "message.wmd.cc.counter.no_pads" : "message.wmd.cc.package.no_cover");
        long impact = schedule(level, player, owner, data, per, gunsPer, ts);
        if (gunsUsed > 0) player.displayClientMessage(Component.translatable("message.wmd.cc.strike.guns", gunsUsed), false);
        long now = level.getGameTime();
        Wmd.LOGGER.info("Strike planner: {} STRIKE PACKAGE: {} of {} targets, {} pads, impact in {} s", player.getGameProfile().getName(), covered, ts.size(), used.size(), (impact - now) / 20);
        player.displayClientMessage(Component.translatable("message.wmd.cc.package.planned", covered, ts.size(), used.size(), OrbitMath.mmss((impact - now) * 50L)), false);
        for (int i = 0; i < ts.size(); i++) {
            int[] tg = ts.get(i);
            Component kind = Component.translatable("gui.wmd.recon.icon." + CcActionPayload.ICON_KEY[Math.max(0, Math.min(CcActionPayload.ICON_KEY.length - 1, tg[2]))]);
            player.displayClientMessage(per.get(i).isEmpty() && gunsPer.get(i).isEmpty() ? Component.translatable("message.wmd.cc.package.uncovered", kind, tg[0], tg[1])
                    : Component.translatable("message.wmd.cc.package.row", kind, tg[0], tg[1], per.get(i).size() + gunsPer.get(i).size()), false);
        }
        CcBattleLog.add(level.getServer(), owner, CcBattleLog.COUNTER, "log.wmd.package", Integer.toString(covered), Integer.toString(ts.size()), Integer.toString(used.size()));
        return null;
    }

    public List<CompoundTag> shots() {
        List<CompoundTag> l = new ArrayList<>();
        for (Tag t : rows()) if (t instanceof CompoundTag c && c.getInt("k") == 1) l.add(c);
        return l;
    }

    public List<CompoundTag> terminals() {
        List<CompoundTag> l = new ArrayList<>();
        for (Tag t : rows()) if (t instanceof CompoundTag c && c.getInt("k") == 0) l.add(c);
        return l;
    }

    public long nextImpact(Level level) {
        long now = level.getGameTime(), best = -1;
        for (CompoundTag c : shots()) if (c.getLong("impact") >= now && (best < 0 || c.getLong("impact") < best)) best = c.getLong("impact");
        return best;
    }
}
