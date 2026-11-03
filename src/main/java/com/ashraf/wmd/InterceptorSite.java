package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.items.ItemStackHandler;

public interface InterceptorSite {
    ItemStackHandler magazineHandler();
    int slotCount();
    int getFrequency();
    void setFrequency(int f);
    int energy();
    int cooldownTicks();
    int trackedCount();
    int loaded();
    UUID getOwner();
    int linkState();
    BlockPos getBlockPos();

    default void shipShot(int fe, int reloadTicks) {}
}
