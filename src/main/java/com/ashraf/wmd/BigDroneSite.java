package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public interface BigDroneSite extends Owned {

    int A_LAUNCH = 0, A_RECALL = 1, A_WATCH = 2, A_DESIGNATED = 3, A_ROUTE = 4;

    int S_READY = 0, S_EMPTY = 1, S_NO_TARGET = 2, S_OUT = 3, S_NO_POWER = 4, S_NO_RUNWAY = 5, S_FLIGHT_T3 = 6;

    BlockPos sitePos();
    boolean isHangar();

    default boolean isHangarModule() { return isHangar() && this instanceof DroneModuleBlockEntity m && m.kind() == DroneKind.HANGAR; }

    default int siteRange() { return isHangar() ? Balance.DRONE_T4_RANGE : Balance.DRONE_T3_RANGE; }
    ItemStackHandler droneSlots();
    ItemStackHandler bombSlots();
    DroneMission mission();
    void setMission(DroneMission m);

    int outId();
    int siteStatus();

    void act(int action, int[] data, ServerPlayer by);

    default ItemStack[] bombStacks() {
        ItemStackHandler h = bombSlots();
        ItemStack[] s = new ItemStack[h.getSlots()];
        for (int i = 0; i < s.length; i++) s[i] = h.getStackInSlot(i).copy();
        return s;
    }

    default void returnBombs(int[] bombs) {
        if (bombs == null) return;
        ItemStackHandler h = bombSlots();
        for (int b : bombs) {
            if (b < 0) continue;
            ItemStack s = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, BombType.ofOrdinal(b).id)));
            for (int i = 0; i < h.getSlots() && !s.isEmpty(); i++) if (h.getStackInSlot(i).isEmpty() && h.isItemValid(i, s)) { h.setStackInSlot(i, s); s = ItemStack.EMPTY; }
        }
    }
}
