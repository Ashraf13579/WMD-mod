package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class DepotScreen extends AbstractContainerScreen<DepotMenu> {

    private static final int DIM = WmdUi.TEXT_MUTED;
    private static final int GAUGE_X = 152, GAUGE_Y = 30, GAUGE_W = 12, GAUGE_H = 52;
    private int chipX = Integer.MAX_VALUE;

    public DepotScreen(DepotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 208;
        this.inventoryLabelY = DepotMenu.INV_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            WmdUi.slot(g, x + s.x, y + s.y, i < DepotMenu.CELLS ? WmdUi.BLUE : 0);
        }
        WmdUi.energyGauge(g, x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H, menu.energy() / (float) Balance.DEPOT_BUFFER);
        int n = menu.count();
        ItemStack icon = new ItemStack(Wmd.MISSILE_DEPOT_ITEM.get());
        chipX = WmdUi.header(g, font, x, y, imageWidth, icon, title, Component.literal(n + " / " + DepotMenu.CELLS), n == 0 ? WmdUi.K_IDLE : WmdUi.K_GOOD, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, DIM, false);
        var pose = g.pose();
        for (int row = 0; row < 2; row++) {
            pose.pushPose();
            pose.translate(DepotMenu.SLOT_X, DepotMenu.ROW_Y[row] - 9, 0);
            pose.scale(0.75F, 0.75F, 1.0F);
            g.drawString(font, Component.translatable("gui.wmd.depot.row." + row), 0, 0, DIM, false);
            pose.popPose();
        }
        boolean power = menu.energy() >= Balance.DEPOT_FE_PER_MOVE;
        WmdUi.drawFit(g, font, power ? Component.translatable("gui.wmd.depot.ready", menu.energy() / Balance.DEPOT_FE_PER_MOVE)
                : Component.translatable("gui.wmd.depot.no_power"), 8, DepotMenu.STATUS_Y, 140, power ? WmdUi.TEXT : WmdUi.ORANGE);
        pose.pushPose();
        pose.translate(8, DepotMenu.HINT_Y, 0);
        pose.scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, Component.translatable("gui.wmd.depot.hint", Balance.ROBOT_ZONE), 0, 0, DIM, false);
        pose.popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (!menu.getCarried().isEmpty() || WmdHelp.hovered(mouseX, mouseY)) return;
        if (WmdUi.over(mouseX, mouseY, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_W, GAUGE_H))
            g.renderTooltip(font, Component.translatable("gui.wmd.depot.power", String.format("%,d", menu.energy()), String.format("%,d", Balance.DEPOT_BUFFER), String.format("%,d", Balance.DEPOT_FE_PER_MOVE)), mouseX, mouseY);
        else if (WmdUi.over(mouseX, mouseY, chipX, topPos + 6, leftPos + imageWidth - 6 - chipX, 11))
            g.renderTooltip(font, Component.translatable("gui.wmd.depot.count", menu.count(), DepotMenu.CELLS), mouseX, mouseY);
    }
}
