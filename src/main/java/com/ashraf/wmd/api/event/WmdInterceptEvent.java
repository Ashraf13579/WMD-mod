package com.ashraf.wmd.api.event;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

public class WmdInterceptEvent extends Event {
    private final ServerLevel level;
    private final String what;
    private final int tier;
    private final UUID defender, attacker;

    public WmdInterceptEvent(ServerLevel level, String what, int tier, UUID defender, UUID attacker) {
        this.level = level; this.what = what; this.tier = tier; this.defender = defender; this.attacker = attacker;
    }

    public ServerLevel level() { return level; }
    public String what() { return what; }
    public int tier() { return tier; }
    public UUID defender() { return defender; }
    public UUID attacker() { return attacker; }
}
