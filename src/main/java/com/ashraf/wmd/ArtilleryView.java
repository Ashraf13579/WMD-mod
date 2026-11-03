package com.ashraf.wmd;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ArtilleryView {

    private ArtilleryView() {}

    static final Map<Long, ArtShell> SHELLS = new ConcurrentHashMap<>();

    static final Queue<ArtShell> FIRED = new ConcurrentLinkedQueue<>();

    static final Queue<ArtImpactPayload> IMPACTS = new ConcurrentLinkedQueue<>();

    public static void onShells(ArtShellsPayload p) {
        if (p.replace()) SHELLS.clear();
        for (ArtShell s : p.shells()) {
            SHELLS.put(s.id(), s);
            if (!p.replace()) FIRED.add(s);
        }
    }

    public static void onExtend(ArtShell s) { if (SHELLS.containsKey(s.id())) SHELLS.put(s.id(), s); }

    public static void onImpact(ArtImpactPayload p) {
        SHELLS.remove(p.id());
        if (p.stage() == ArtImpactPayload.GONE) return;
        if (IMPACTS.size() < 256) IMPACTS.add(p);
    }

    public static Collection<ArtShell> shells() { return SHELLS.values(); }

    public static void clear() { SHELLS.clear(); FIRED.clear(); IMPACTS.clear(); }
}
