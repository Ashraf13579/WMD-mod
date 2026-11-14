package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class ClientAutopilotBridge {

    private ClientAutopilotBridge() {}

    private static final List<ApHudPayload> HUD = Statics.client(new ArrayList<>());
    private static final List<ApPayloads.State> STATE = Statics.client(new ArrayList<>());
    private static long hudAt = Long.MIN_VALUE, stateAt = Long.MIN_VALUE;

    static volatile Runnable onStateHook;

    static void onHud(ApHudPayload p) {
        synchronized (HUD) { HUD.clear(); HUD.add(p); }
        hudAt = System.currentTimeMillis();
    }

    static void onState(ApPayloads.State p) {
        synchronized (STATE) { STATE.clear(); STATE.add(p); }
        stateAt = System.currentTimeMillis();
        Runnable r = onStateHook;
        if (r != null) r.run();
    }

    public static ApHudPayload hud(BlockPos cockpit) {
        synchronized (HUD) {
            if (HUD.isEmpty() || System.currentTimeMillis() - hudAt > 2_000L) return null;
            ApHudPayload p = HUD.get(0);
            return cockpit == null || p.cockpit().equals(cockpit) ? p : null;
        }
    }

    public static boolean flying(BlockPos cockpit) {
        ApHudPayload p = hud(cockpit);
        return p != null && p.mode() != Autopilot.OFF && p.mode() != Autopilot.PARKED;
    }

    public static CompoundTag state(BlockPos cockpit) {
        synchronized (STATE) {
            if (STATE.isEmpty() || !STATE.get(0).cockpit().equals(cockpit)) return null;
            return STATE.get(0).data();
        }
    }

    static long stateAt() { return stateAt; }
}
