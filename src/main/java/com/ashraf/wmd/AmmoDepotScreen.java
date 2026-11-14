package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class AmmoDepotScreen extends AbstractContainerScreen<AmmoDepotMenu> {

    static final int PW = 176, PH = 234;

    public AmmoDepotScreen(AmmoDepotMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = PW;
        imageHeight = PH;
        inventoryLabelY = AmmoDepotMenu.INV_Y - 11;
        titleLabelY = -100;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, topPos + 22, "icon_process", Component.translatable("gui.wmd.loadout.tab.loadout"), b -> {
            CompoundTag a = new CompoundTag();
            a.putString("a", "refresh");
            onClose();
            PacketDistributor.sendToServer(new LoadoutPayload.Act(menu.depot(), a));
        }));
        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, topPos + 46, "icon_upgrades", Component.translatable("gui.wmd.loadout.tab.store"), b -> { }));
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        WmdUi.panel(g, leftPos, topPos, imageWidth, imageHeight);
        int n = 0;
        for (int i = 0; i < Balance.AD_SLOTS; i++) n += menu.getSlot(i).getItem().getCount();
        WmdUi.header(g, font, leftPos, topPos, imageWidth, new ItemStack(AirBlocks.AMMO_DEPOT_ITEM.get()), title,
                Component.translatable("gui.wmd.loadout.chip_rounds", n), n > 0 ? WmdUi.K_GOOD : WmdUi.K_WARN, false);
        for (Slot s : menu.slots) WmdUi.slot(g, leftPos + s.x, topPos + s.y, s.index < Balance.AD_SLOTS ? WmdUi.BLUE : 0);
        WmdUi.divider(g, leftPos + 8, topPos + AmmoDepotMenu.INV_Y - 14, imageWidth - 16);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, WmdUi.TEXT_MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
