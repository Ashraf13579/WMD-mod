package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

public final class CcReach {

    private CcReach() {}

    public record Result(boolean ok, String key, Object[] args, double nearest) {
        public Component text() { return Component.translatable(key, args); }
    }

    public static boolean reach(Level level, UUID owner, double x, double z) { return check(level, owner, x, z).ok(); }

    public static boolean reach(Level level, UUID owner, net.minecraft.core.BlockPos machine) { return check(level, owner, machine).ok(); }

    public static Result check(Level level, UUID owner, net.minecraft.core.BlockPos machine) {
        net.minecraft.world.phys.Vec3 w = SableBridge.worldPos(level, machine);
        return check(level, owner, w.x, w.z);
    }

    public static Result check(Level level, UUID owner, double x, double z) {
        String P = "message.wmd.cc.reach.";
        List<CcBuildingBlockEntity> rooms = CommandCenter.activeBuildings(owner);
        if (rooms.isEmpty()) return new Result(false, P + "no_room", new Object[0], -1.0);
        boolean comms = false;
        double best = Double.MAX_VALUE;
        for (CcBuildingBlockEntity t : rooms) {
            if (t.moduleCount(CcModule.COMMS_ARRAY) > 0) comms = true;
            if (t.getLevel() != level) continue;
            net.minecraft.world.phys.Vec3 hc = SableBridge.worldPos(level, t.hallCentre());
            double d = Math.hypot(hc.x - x, hc.z - z);
            best = Math.min(best, d);
        }
        double nearest = best == Double.MAX_VALUE ? -1.0 : best;
        if (comms && relay(level.getServer(), owner)) return new Result(true, P + "global", new Object[0], nearest);
        if (nearest >= 0 && nearest <= Balance.CC_REACH) return new Result(true, P + "in_range", new Object[] { (int) nearest, Balance.CC_REACH }, nearest);
        if (nearest < 0) return new Result(false, P + "other_dimension", new Object[0], -1.0);
        return new Result(false, comms ? P + "too_far_no_relay" : P + "too_far", new Object[] { (int) nearest, Balance.CC_REACH }, nearest);
    }

    public static boolean relay(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return false;
        long now = System.currentTimeMillis();
        for (OrbitMath.Sat s : OrbitData.get(server).sats())
            if (owner.equals(s.owner()) && s.type().equals("relay") && now < s.endMs() && OrbitData.controlled(s)) return true;
        return false;
    }
}
