package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;

public final class CcBattleLogView {

    private CcBattleLogView() {}

    private static volatile BlockPos pos;
    private static volatile List<CcBattleLog.Event> events = List.of();

    public static void set(CcBattleLogPayload p) {
        pos = p.pos();
        events = List.copyOf(p.events());
    }

    public static List<CcBattleLog.Event> eventsFor(BlockPos at) {
        return at != null && at.equals(pos) ? events : List.of();
    }

    public static void clear() { pos = null; events = List.of(); }
}
