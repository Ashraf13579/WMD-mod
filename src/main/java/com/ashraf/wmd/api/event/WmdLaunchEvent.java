package com.ashraf.wmd.api.event;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

public class WmdLaunchEvent extends Event {
    private final ServerLevel level;
    private final long flightId;
    private final int tier, targetX, targetZ;
    private final double fromX, fromZ;
    private final UUID owner;
    private final String warhead;
    private final boolean cruise;

    public WmdLaunchEvent(ServerLevel level, long flightId, int tier, double fromX, double fromZ, int targetX, int targetZ, UUID owner, String warhead, boolean cruise) {
        this.level = level; this.flightId = flightId; this.tier = tier; this.fromX = fromX; this.fromZ = fromZ; this.targetX = targetX; this.targetZ = targetZ;
        this.owner = owner; this.warhead = warhead; this.cruise = cruise;
    }

    public ServerLevel level() { return level; }
    public long flightId() { return flightId; }
    public int tier() { return tier; }
    public double fromX() { return fromX; }
    public double fromZ() { return fromZ; }
    public int targetX() { return targetX; }
    public int targetZ() { return targetZ; }
    public UUID owner() { return owner; }
    public String warhead() { return warhead; }
    public boolean cruise() { return cruise; }
}
