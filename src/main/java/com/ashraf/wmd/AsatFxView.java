package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

public final class AsatFxView {

    private AsatFxView() {}

    public static final long LIFE_MS = 20_000L;

    public record Event(OrbitMath.Sat sat, long ms, boolean hit) {}

    private static final List<Event> EVENTS = Statics.client(new ArrayList<>());

    public static synchronized void add(AsatFxPayload p) {
        EVENTS.add(new Event(p.sat(), p.serverMs(), p.hit()));
        while (EVENTS.size() > 8) EVENTS.remove(0);
    }

    public static synchronized List<Event> live(long nowMs) {
        EVENTS.removeIf(e -> nowMs - e.ms() > LIFE_MS || nowMs < e.ms() - 60_000L);
        return EVENTS.isEmpty() ? List.of() : List.copyOf(EVENTS);
    }

    public static synchronized void clear() { EVENTS.clear(); }
}
