package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

public final class FusionArcs {

    private FusionArcs() {}

    public static final class Live {
        public final FusionArcPayload.Arc arc;
        public long born = -1L;
        Live(FusionArcPayload.Arc arc) { this.arc = arc; }

        public int life() { return arc.kind() == 0 ? 5 : arc.kind() == 1 ? 7 : 12; }
    }

    public static final List<Live> LIVE = Statics.client(new ArrayList<>());

    public static void add(FusionArcPayload p) {
        for (FusionArcPayload.Arc a : p.arcs()) LIVE.add(new Live(a));
        while (LIVE.size() > 96) LIVE.remove(0);
    }
}
