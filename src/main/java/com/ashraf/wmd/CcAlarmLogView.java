package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;

public final class CcAlarmLogView {

    private CcAlarmLogView() {}

    private static volatile BlockPos pos;
    private static volatile List<CcAlarms.Event> events = List.of();

    public static void set(CcAlarmLogPayload p) {
        pos = p.pos();
        events = List.copyOf(p.events());
    }

    public static List<CcAlarms.Event> eventsFor(BlockPos at) {
        return at != null && at.equals(pos) ? events : List.of();
    }

    public static void clear() { pos = null; events = List.of(); }
}
