package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class ClientSeaPilotBridge {

    private ClientSeaPilotBridge() {}

    private static final List<SeaPilotPayloads.Hud> HUD = Statics.client(new ArrayList<>());
    private static final List<SeaPilotPayloads.State> STATE = Statics.client(new ArrayList<>());
    private static final List<SeaPilotPayloads.Tile> TILE = Statics.client(new ArrayList<>());
    private static long hudAt = Long.MIN_VALUE;

    static volatile Consumer<SeaPilotPayloads.State> onStateHook;

    static volatile Runnable onTileHook;

    static void onHud(SeaPilotPayloads.Hud p) {
        synchronized (HUD) { HUD.clear(); HUD.add(p); }
        hudAt = System.currentTimeMillis();
    }

    static void onState(SeaPilotPayloads.State p) {
        synchronized (STATE) { STATE.clear(); STATE.add(p); }
        Consumer<SeaPilotPayloads.State> r = onStateHook;
        if (r != null) r.accept(p);
    }

    static void onTile(SeaPilotPayloads.Tile p) {
        synchronized (TILE) { TILE.clear(); TILE.add(p); }
        Runnable r = onTileHook;
        if (r != null) r.run();
    }

    public static SeaPilotPayloads.Hud hud(BlockPos helm) {
        synchronized (HUD) {
            if (HUD.isEmpty() || System.currentTimeMillis() - hudAt > 2_000L) return null;
            SeaPilotPayloads.Hud p = HUD.get(0);
            return helm == null || p.helm().equals(helm) ? p : null;
        }
    }

    public static CompoundTag state(BlockPos src) {
        synchronized (STATE) {
            if (STATE.isEmpty() || !STATE.get(0).pos().equals(src)) return null;
            return STATE.get(0).data();
        }
    }

    public static SeaPilotPayloads.Tile tile(BlockPos src) {
        synchronized (TILE) {
            if (TILE.isEmpty() || !TILE.get(0).pos().equals(src)) return null;
            return TILE.get(0);
        }
    }
}
