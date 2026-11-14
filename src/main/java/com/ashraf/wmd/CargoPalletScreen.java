package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class CargoPalletScreen extends AbstractContainerScreen<CargoPalletMenu> {

    static final int PW = 176;

    public CargoPalletScreen(CargoPalletMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = PW;
        imageHeight = menu.invY() + 82;
        inventoryLabelY = menu.invY() - 11;
        titleLabelY = -100;
    }

    private void button(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void init() {
        super.init();
        int y = topPos + 22;
        for (int c = 0; c < menu.compartmentCount(); c++) {
            final int k = c;
            addRenderableWidget(new WmdUi.TabButton(leftPos - 23, y, "icon_upgrades", Component.translatable("gui.wmd.pallet.tab.compartment", c + 1, menu.compartmentCount()),
                    b -> button(CargoPalletMenu.B_COMP + k)));
            y += 24;
        }
        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, y, "icon_process", Component.translatable("gui.wmd.pallet.tab.unpack", (int) Balance.AD_RANGE),
                b -> button(CargoPalletMenu.B_UNPACK)));
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        WmdUi.panel(g, leftPos, topPos, imageWidth, imageHeight);
        int n = 0, slots = menu.slots.size() - 36;
        for (int i = 0; i < slots; i++) if (!menu.getSlot(i).getItem().isEmpty()) n++;
        Component chip = menu.compartmentCount() > 1
                ? Component.translatable("gui.wmd.pallet.chip_comp", menu.compartment() + 1, menu.compartmentCount(), n, slots)
                : Component.translatable("gui.wmd.pallet.chip", n, slots);
        WmdUi.header(g, font, leftPos, topPos, imageWidth, new ItemStack(CargoBlocks.palletItem(menu.heavy())), title, chip, n > 0 ? WmdUi.K_GOOD : WmdUi.K_IDLE, false);
        for (Slot s : menu.slots) WmdUi.slot(g, leftPos + s.x, topPos + s.y, s.index < slots ? WmdUi.BLUE : 0);
        WmdUi.divider(g, leftPos + 8, topPos + menu.invY() - 14, imageWidth - 16);
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
