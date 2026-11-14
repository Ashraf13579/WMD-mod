package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

public final class ClientRwr {

    private ClientRwr() {}

    private static final List<RwrPayload> LAST = Statics.client(new ArrayList<>());
    private static long receivedAt = Long.MIN_VALUE;

    static void onReceive(RwrPayload p) {
        synchronized (LAST) {
            LAST.clear();
            if (!p.lines().isEmpty()) LAST.add(p);
        }
        receivedAt = System.currentTimeMillis();
    }

    public static List<RwrPayload.Line> lines() {
        synchronized (LAST) {
            if (LAST.isEmpty() || System.currentTimeMillis() - receivedAt > STALE_MS) return List.of();
            return LAST.get(0).lines();
        }
    }

    static final long STALE_MS = 3_000L;
}
