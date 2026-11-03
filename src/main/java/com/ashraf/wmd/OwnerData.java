package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class OwnerData {
    private UUID id;
    private String name = "";
    private final SecurityData security = new SecurityData();

    public SecurityData security() { return security; }

    public UUID id() { return id; }
    public String name() { return name; }

    public void claim(Player p) { id = p.getUUID(); name = p.getGameProfile().getName(); }

    public void write(CompoundTag tag) {
        if (id != null) tag.putUUID("owner", id);
        tag.putString("ownerName", name);
        security.write(tag);
    }

    public void read(CompoundTag tag) {
        id = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        name = tag.getString("ownerName");
        security.read(tag);
    }
}
