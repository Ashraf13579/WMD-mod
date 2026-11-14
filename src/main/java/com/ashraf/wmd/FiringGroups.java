package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

final class FiringGroups {

    private FiringGroups() {}

    private record Shot(long station, long at) {}
    private static final Map<Long, Deque<Shot>> RIPPLE = Statics.server(new HashMap<>());

    private static List<AirStations.Station> stations(ServerLevel sl, UUID ship, CompoundTag lo, int g) {
        List<AirStations.Station> out = new ArrayList<>();
        long[] want = LoadoutPlan.groupStations(lo, g);
        if (want.length == 0) return out;
        List<AirStations.Station> all = AirStations.of(sl, ship);
        for (long w : want) for (AirStations.Station s : all) if (s.stationPos().asLong() == w) out.add(s);
        return out;
    }

    static boolean configured(CompoundTag lo, int g) { return LoadoutPlan.groupStations(lo, g).length > 0; }

    static String fire(ServerLevel sl, JetCockpitBlockEntity c, UUID ship, CompoundTag lo, int g, UUID lockId, AirMissiles.LockSource lock) {
        if (!AirGate.on() || ship == null || !configured(lo, g)) return null;
        int mode = LoadoutPlan.groupMode(lo, g);
        if (mode == LoadoutPlan.G_HOLD) {
            int left = GunPodBlockEntity.triggerAll(sl, ship, Balance.FG_HOLD_TICKS);
            int pods = 0;
            for (AirStations.Station s : stations(sl, ship, lo, g)) if (s instanceof RocketPodBlockEntity rp && rp.available() > 0) { rp.hold(sl, Balance.FG_HOLD_TICKS); pods++; }
            if (pods > 0) return "message.wmd.cockpit.group_rockets";
            return left < 0 ? "message.wmd.cockpit.group_nogun" : "message.wmd.cockpit.group_gun";
        }
        List<AirStations.Station> loaded = new ArrayList<>();
        for (AirStations.Station s : stations(sl, ship, lo, g)) if (s.loaded() >= 0) loaded.add(s);
        if (loaded.isEmpty()) return "message.wmd.cockpit.group_empty";
        if (mode == LoadoutPlan.G_RIPPLE) {

            int slot = 0;
            for (java.util.Iterator<AirStations.Station> it = loaded.iterator(); it.hasNext(); ) {
                AirStations.Station s = it.next();
                if (s instanceof RocketPodBlockEntity rp) { it.remove(); if (rp.ripple(sl, slot) == null) slot++; }
            }
            if (loaded.isEmpty()) return slot > 0 ? "message.wmd.cockpit.group_ripple" : "message.wmd.cockpit.group_empty";
            Deque<Shot> q = RIPPLE.computeIfAbsent(c.getBlockPos().asLong(), k -> new ArrayDeque<>());
            if (!q.isEmpty()) return "message.wmd.cockpit.group_busy";
            long now = sl.getGameTime();
            for (int i = 0; i < loaded.size(); i++) q.add(new Shot(loaded.get(i).stationPos().asLong(), now + (long) i * Balance.FG_RIPPLE_GAP));
            tick(sl, c, ship, lockId, lock);
            return "message.wmd.cockpit.group_ripple";
        }
        int n = mode == LoadoutPlan.G_PAIR ? Math.min(2, loaded.size()) : 1;
        if (mode == LoadoutPlan.G_PAIR && loaded.size() > 2) {
            AirStations.Station a = loaded.get(0), best = loaded.get(1);
            Vec3 pa = SableBridge.worldPos(sl, a.stationPos());
            double far = -1;
            for (int i = 1; i < loaded.size(); i++) { double d = SableBridge.worldPos(sl, loaded.get(i).stationPos()).distanceTo(pa); if (d > far) { far = d; best = loaded.get(i); } }
            loaded = List.of(a, best);
        }
        String why = null;
        int fired = 0;
        for (int i = 0; i < n; i++) {
            String w = loaded.get(i).fire(sl, lockId, lock);
            if (w == null) fired++; else why = w;
        }
        return fired > 0 ? "message.wmd.cockpit.group_fired" : why == null ? "message.wmd.cockpit.group_empty" : why;
    }

    static void tick(ServerLevel sl, JetCockpitBlockEntity c, UUID ship, UUID lockId, AirMissiles.LockSource lock) {
        Deque<Shot> q = RIPPLE.get(c.getBlockPos().asLong());
        if (q == null || q.isEmpty() || !AirGate.on() || ship == null) { if (q != null && q.isEmpty()) RIPPLE.remove(c.getBlockPos().asLong()); return; }
        long now = sl.getGameTime();
        List<AirStations.Station> all = AirStations.of(sl, ship);
        while (!q.isEmpty() && q.peek().at() <= now) {
            Shot s = q.poll();
            for (AirStations.Station st : all) if (st.stationPos().asLong() == s.station() && st.loaded() >= 0) { st.fire(sl, lockId, lock); break; }
        }
        if (q.isEmpty()) RIPPLE.remove(c.getBlockPos().asLong());
    }

    static AirStations.Station firstLoaded(ServerLevel sl, UUID ship, CompoundTag lo, int g) {
        if (ship == null || !configured(lo, g)) return null;
        for (AirStations.Station s : stations(sl, ship, lo, g)) if (s.loaded() >= 0) return s;
        return null;
    }

    static int hudKind(ServerLevel sl, UUID ship, CompoundTag lo, int g) {
        if (ship == null || !configured(lo, g)) return -2;
        if (LoadoutPlan.groupMode(lo, g) == LoadoutPlan.G_HOLD) return -1;
        for (AirStations.Station s : stations(sl, ship, lo, g)) if (s.loaded() >= 0) return s.loaded();
        return -1;
    }

    static void sendStores(ServerPlayer sp, JetCockpitBlockEntity c, UUID ship, CompoundTag lo, int selected) {
        if (!AirGate.on() || ship == null) return;
        ServerLevel sl = sp.serverLevel();
        CompoundTag t = new CompoundTag();
        t.putBoolean("stores", true);
        t.putUUID("ship", ship);
        t.putInt("sel", selected);
        ListTag gs = new ListTag();
        for (int g = 0; g < LoadoutPlan.GROUPS; g++) {
            CompoundTag e = new CompoundTag();
            List<AirStations.Station> st = stations(sl, ship, lo, g);
            int loaded = 0, kind = -1;
            for (AirStations.Station s : st) if (s.loaded() >= 0) { loaded++; if (kind < 0) kind = s.loaded(); }
            e.putInt("n", LoadoutPlan.groupStations(lo, g).length);
            e.putInt("loaded", loaded);
            e.putInt("mode", LoadoutPlan.groupMode(lo, g));
            if (kind >= 0) e.put("item", AirStores.stack(kind, 1).saveOptional(sl.registryAccess()));
            gs.add(e);
        }
        t.put("groups", gs);
        t.putInt("gun", GunPodBlockEntity.roundsAboard(sl, ship));
        int rk = 0, rkPods = 0;
        for (AirStations.Station s : AirStations.of(sl, ship)) if (s instanceof RocketPodBlockEntity rp) { rk += rp.rockets(); rkPods++; }
        if (rkPods > 0) { t.putInt("rockets", rk); t.putInt("rocketTubes", rkPods * Balance.RPOD_TUBES); }
        int[] cm = CountermeasureDispenserBlockEntity.loadOn(sl, ship);
        if (cm != null && cm.length >= 2) { t.putInt("flares", cm[0]); t.putInt("chaff", cm[1]); }
        AmmoDepotBlockEntity d = AmmoDepots.nearest(sl, SableBridge.worldPos(sl, c.getBlockPos()), sp.getUUID());
        if (d != null) {
            t.putLong("depot", d.getBlockPos().asLong());
            t.putBoolean("parked", !AirPicture.isAircraft(sl, ship));
            t.putInt("depotMode", d.modeFor(ship));
        }
        PacketDistributor.sendToPlayer(sp, new LoadoutPayload.Show(t));
    }
}
