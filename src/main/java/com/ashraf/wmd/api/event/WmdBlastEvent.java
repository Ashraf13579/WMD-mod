package com.ashraf.wmd.api.event;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

public class WmdBlastEvent extends Event {
    private final ServerLevel level;
    private final double x, y, z, radius;
    private final String kind, warhead;
    private final UUID shooter;

    public WmdBlastEvent(ServerLevel level, double x, double y, double z, double radius, String kind, String warhead, UUID shooter) {
        this.level = level; this.x = x; this.y = y; this.z = z; this.radius = radius; this.kind = kind; this.warhead = warhead; this.shooter = shooter;
    }

    public ServerLevel level() { return level; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double radius() { return radius; }
    public String kind() { return kind; }
    public String warhead() { return warhead; }
    public UUID shooter() { return shooter; }
}
