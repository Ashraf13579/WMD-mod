package com.ashraf.wmd.api.event;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

public class WmdNuclearDetonationEvent extends Event {
    private final ServerLevel level;
    private final int x, z;
    private final String warhead;
    private final UUID shooter;
    private final boolean accident;

    public WmdNuclearDetonationEvent(ServerLevel level, int x, int z, String warhead, UUID shooter, boolean accident) {
        this.level = level; this.x = x; this.z = z; this.warhead = warhead; this.shooter = shooter; this.accident = accident;
    }

    public ServerLevel level() { return level; }
    public int x() { return x; }
    public int z() { return z; }
    public String warhead() { return warhead; }
    public UUID shooter() { return shooter; }
    public boolean accident() { return accident; }
}
