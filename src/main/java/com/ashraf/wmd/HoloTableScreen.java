package com.ashraf.wmd;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class HoloTableScreen extends CcScreen {

    public HoloTableScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override
    protected void init() {
        super.init();
        if (menu.machine() instanceof HoloTableBlockEntity t)
            addActionTab("icon_camera", Component.translatable("gui.wmd.holot.tab"), () -> minecraft.setScreen(new HoloControlScreen(this, t.getBlockPos())));
    }
}
