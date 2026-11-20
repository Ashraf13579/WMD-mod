package com.ashraf.wmd.api.event;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

public class WmdCraftDownedEvent extends Event {
    private final ServerLevel level;
    private final UUID craft, owner, shooter;
    private final int blocks;
    private final boolean aircraft;

    public WmdCraftDownedEvent(ServerLevel level, UUID craft, UUID owner, UUID shooter, int blocks, boolean aircraft) {
        this.level = level; this.craft = craft; this.owner = owner; this.shooter = shooter; this.blocks = blocks; this.aircraft = aircraft;
    }

    public ServerLevel level() { return level; }
    public UUID craft() { return craft; }
    public UUID owner() { return owner; }
    public UUID shooter() { return shooter; }
    public int blocks() { return blocks; }
    public boolean aircraft() { return aircraft; }
}
