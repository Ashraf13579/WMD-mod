package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.world.entity.player.Player;

public interface Owned {

    UUID getOwner();

    String getOwnerName();

    void claim(Player player);

    SecurityData security();
}
