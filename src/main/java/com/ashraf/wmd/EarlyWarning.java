package com.ashraf.wmd;

import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class EarlyWarning {

    private EarlyWarning() {}

    static void onLaunch(ServerLevel level, Flight f, Set<UUID> warned) {
        if (f == null || (f.build() != null && f.build().isInterceptor())) return;
        var arrays = EarlyWarningArrayBlockEntity.all();
        if (arrays.isEmpty()) return;
        MinecraftServer server = level.getServer();
        double r2 = (double) Balance.EWA_RANGE * Balance.EWA_RANGE;
        boolean sat = Recon.climbsToOrbit(f);
        for (EarlyWarningArrayBlockEntity a : arrays) {
            UUID o = a.getOwner();
            if (a.isRemoved() || a.getLevel() != level || o == null || !a.isRunning() || warned.contains(o)) continue;
            net.minecraft.world.phys.Vec3 aw = SableBridge.worldPos(level, a.getBlockPos());
            double dx = f.ox() - aw.x, dz = f.oz() - aw.z;
            if (dx * dx + dz * dz > r2) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(o);
            if (f.isOwnedBy(o, p != null ? p.getGameProfile().getName() : a.getOwnerName())) continue;
            warned.add(o);
            long[] imp = Recon.blurred(f.fx(), f.fz(), level.random);
            CommandCenter.addWarning(server, o, CommandCenter.Warning.ofFlight(f, imp, sat, Recon.intel(server, o, f.ox(), f.oz())));
            if (p != null) Recon.warnAt(p, f.ox(), f.oz(), imp[0], imp[1], Math.max(0, f.duration() / 20), sat);
            a.noteWarning(Math.round(f.ox()), Math.round(f.oz()), imp[0], imp[1], f.start(), f.endTime(), sat);
        }
    }

    static void testWarn(ServerLevel level, EarlyWarningArrayBlockEntity a, int dist) {
        UUID o = a.getOwner();
        if (o == null) return;
        long now = level.getGameTime();
        int ox = a.getBlockPos().getX(), oz = a.getBlockPos().getZ() - dist;
        long[] imp = Recon.blurred(a.getBlockPos().getX(), a.getBlockPos().getZ(), level.random);
        CommandCenter.addWarning(level.getServer(), o, new CommandCenter.Warning(-now, ox, oz, (int) imp[0], (int) imp[1], now, now + 1200, false));
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(o);
        if (p != null) Recon.warnAt(p, ox, oz, imp[0], imp[1], 60, false);
        a.noteWarning(ox, oz, imp[0], imp[1], now, now + 1200, false);
    }
}
