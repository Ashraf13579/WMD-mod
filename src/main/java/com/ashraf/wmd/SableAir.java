package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class SableAir {

    private SableAir() {}

    static final int AIR_PRIORITY = 500;

    static double thinning(double sea, int ceiling) {
        if (ceiling <= 0) return Balance.AIR_PRESSURE_K;
        double span = Math.max(1.0, ceiling - sea);
        return Math.min(Balance.AIR_PRESSURE_K, Balance.AIR_K_REF * (Balance.AIR_CEILING_DEFAULT - sea) / span);
    }

    static List<double[]> points(double sea, double minY, double top, double k, double fade) {
        List<double[]> pts = new ArrayList<>();
        double kLow = Balance.AIR_PRESSURE_K;
        double alt = Math.max(minY, Math.log(Balance.AIR_MAX_P) / -kLow + sea);
        double smooth = top - fade, step = 200.0;
        if (alt < sea) {
            double p = Math.exp(-kLow * (alt - sea));
            pts.add(new double[] { alt, p, -kLow * p });
            alt = sea;
        }
        pts.add(new double[] { alt, Math.exp(-k * (alt - sea)), -(alt <= sea ? kLow : k) * Math.exp(-k * (alt - sea)) });
        if (k < kLow && alt <= sea && sea + Balance.AIR_SEA_BRIDGE < smooth) alt = sea + Balance.AIR_SEA_BRIDGE;
        else alt = alt < smooth && alt + step >= smooth ? smooth : alt + step;
        while (true) {
            double p = Math.exp(-k * (alt - sea));
            pts.add(new double[] { alt, p, -k * p });
            if (alt < smooth && alt + step >= smooth) alt = smooth;
            else if (alt >= smooth) break;
            else alt += step;
        }
        double ps = pts.get(pts.size() - 1)[1];
        pts.add(new double[] { top, 0.0, -2.0 * ps / fade });
        return pts;
    }

    static List<double[]> pointsFor(Level level) {
        double sea = level.getSeaLevel(), minY = level.dimensionType().minY();
        int c = Config.Server.airCeiling();
        if (c <= 0 || level.dimension() != Level.OVERWORLD) return points(sea, minY, minY + level.dimensionType().logicalHeight(), Balance.AIR_PRESSURE_K, 40.0);
        return points(sea, minY, Math.max(c, sea + 2.0 * Balance.AIR_CEIL_FADE), thinning(sea, c), Balance.AIR_CEIL_FADE);
    }

    static double eval(List<double[]> pts, double y) {
        if (pts.isEmpty()) return 1.0;
        int i = -1;
        for (double[] p : pts) { if (y < p[0]) break; i++; }
        if (i == -1) return pts.get(0)[1];
        if (i >= pts.size() - 1) return pts.get(pts.size() - 1)[1];
        double[] a = pts.get(i), b = pts.get(i + 1);
        double rx = b[0] - a[0], ry = b[1] - a[1], t = (y - a[0]) / rx;
        double cubic = (a[2] + b[2]) * rx - 2.0 * ry, quad = 3.0 * ry - (2.0 * a[2] + b[2]) * rx, lin = rx * a[2];
        return Math.max(((cubic * t + quad) * t + lin) * t + a[1], 0.0);
    }

    static double pressure(Level level, double x, double y, double z) {
        double[] ph = SableBridge.physicsAt(level, new net.minecraft.world.phys.Vec3(x, y, z));
        if (ph != null) return ph[0];
        return eval(pointsFor(level), y);
    }

    static boolean apply(MinecraftServer server) {
        if (server == null || Config.Server.airCeiling() <= 0) return false;
        ServerLevel ow = server.overworld();
        return ow != null && SableBridge.applyAir(ow, pointsFor(ow), AIR_PRIORITY);
    }

    @SubscribeEvent
    static void starting(ServerStartingEvent event) {
        if (apply(event.getServer())) Wmd.LOGGER.info("WMD air: Sable's overworld air now ends at y {} (config airCeiling; all Sable craft feel it)", Config.Server.airCeiling());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void sync(OnDatapackSyncEvent event) { apply(event.getPlayerList().getServer()); }
}
