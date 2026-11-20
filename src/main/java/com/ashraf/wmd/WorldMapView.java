package com.ashraf.wmd;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.neoforged.neoforge.network.PacketDistributor;

public final class WorldMapView {

    private WorldMapView() {}

    private static final int TILE_MAX = 600, OUTSTANDING = 24, REASK_TICKS = 60, GIVE_UP_TICKS = 100, SEND_GAP = 4;

    record Key(int lvl, int tx, int tz) {}

    public static final class Entry {
        public final byte[] ids;
        public final boolean complete;
        final long got;
        Entry(byte[] ids, boolean complete, long got) { this.ids = ids; this.complete = complete; this.got = got; }
    }

    private static final Map<Key, Entry> TILES = Statics.client(new LinkedHashMap<Key, Entry>(256, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Entry> e) { return size() > TILE_MAX; }
    });
    private static final Map<Key, Long> ASKED = Statics.client(new HashMap<Key, Long>());
    private static String dim = "";
    private static boolean available, disabled;
    private static int progress = 100;
    private static long lastSend = -100, now;

    public static volatile int version;

    public static boolean available() { return available && !disabled; }

    public static int progress() { return progress; }

    public static Entry tile(int lvl, int tx, int tz) { return TILES.get(new Key(lvl, tx, tz)); }

    static void clearStatics() {
        TILES.clear(); ASKED.clear();
        dim = ""; available = false; disabled = false; progress = 100; lastSend = -100; now = 0; version++;
    }

    static void accept(WorldMapPayloads.Tile p) {
        if ((p.flags() & WorldMap.F_DISABLED) != 0) { disabled = true; available = false; version++; return; }
        if (!p.dim().equals(dim)) return;
        Key k = new Key(p.lvl(), p.tx(), p.tz());
        ASKED.remove(k);
        boolean outside = (p.flags() & WorldMap.F_OUTSIDE) != 0, complete = (p.flags() & WorldMap.F_COMPLETE) != 0 || outside;
        byte[] ids = p.ids().length == WorldMap.TILE * WorldMap.TILE ? p.ids() : new byte[WorldMap.TILE * WorldMap.TILE];
        TILES.put(k, new Entry(ids, complete, now));
        if (!outside) available = true;
        progress = p.pct();
        version++;
    }

    public static void want(String dimension, long tick, int lvl, List<int[]> tiles) {
        if (!dimension.equals(dim)) { dim = dimension; TILES.clear(); ASKED.clear(); available = false; disabled = false; progress = 100; }
        now = tick;
        if (disabled || tick - lastSend < SEND_GAP) return;
        ASKED.values().removeIf(t -> tick < t || tick - t > GIVE_UP_TICKS);
        int open = ASKED.size();
        int[] buf = new int[16];
        int n = 0;
        for (int[] t : tiles) {
            if (n >= 8 || open + n >= OUTSTANDING) break;
            Key k = new Key(lvl, t[0], t[1]);
            Entry e = TILES.get(k);
            if (ASKED.containsKey(k)) continue;
            if (e != null && (e.complete || tick - e.got < REASK_TICKS)) continue;
            buf[2 * n] = t[0]; buf[2 * n + 1] = t[1];
            n++;
            ASKED.put(k, tick);
        }
        if (n == 0) return;
        lastSend = tick;
        PacketDistributor.sendToServer(new WorldMapPayloads.Request(lvl, java.util.Arrays.copyOf(buf, 2 * n)));
    }
}
