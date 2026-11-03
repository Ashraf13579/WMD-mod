package com.ashraf.wmd;

import net.minecraft.world.Container;

public interface GuiItemSlots extends GuiMachine {

    Container slotContainer();

    int slotCount();

    int firstSlotTank();

    int slotMax(int i);
}
