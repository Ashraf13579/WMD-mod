package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

public final class ClientCockpitBridge {

    private ClientCockpitBridge() {}

    private static final List<CockpitHudPayload> LAST = Statics.client(new ArrayList<>());
    private static long receivedAt = Long.MIN_VALUE;

    static void onHud(CockpitHudPayload p) {
        synchronized (LAST) { LAST.clear(); LAST.add(p); }
        receivedAt = System.currentTimeMillis();
    }

    public static CockpitHudPayload hud() {
        synchronized (LAST) {
            if (LAST.isEmpty() || System.currentTimeMillis() - receivedAt > 2_000L) return null;
            return LAST.get(0);
        }
    }

    public static float threatBearing(BlockPos cockpit) {
        CockpitHudPayload h = hud();
        if (h == null || !h.cockpit().equals(cockpit) || !h.has(CockpitHudPayload.F_POWER)) return Float.NaN;
        RwrPayload.Line best = null;
        for (RwrPayload.Line l : ClientRwr.lines())
            if (best == null || l.kind() > best.kind() || (l.kind() == best.kind() && l.dist() < best.dist())) best = l;
        if (best == null) return Float.NaN;
        return (float) (((best.bearing() - h.heading()) % 360.0 + 540.0) % 360.0 - 180.0);
    }
}
