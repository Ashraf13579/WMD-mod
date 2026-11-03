package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class RackScreen extends AbstractContainerScreen<RackMenu> {

    private static final int DIM = WmdUi.TEXT_MUTED;
    private int chipX = Integer.MAX_VALUE;

    public RackScreen(RackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 288;
        this.inventoryLabelY = RackMenu.INV_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (!s.isActive()) continue;
            WmdUi.slot(g, x + s.x, y + s.y, i < RackMenu.TOTAL ? WmdUi.ORANGE : 0);
        }

        int n = menu.count(), total = menu.capacity();
        ItemStack icon = minecraft != null && minecraft.level != null ? new ItemStack(minecraft.level.getBlockState(menu.getPos()).getBlock()) : ItemStack.EMPTY;
        chipX = WmdUi.header(g, font, x, y, imageWidth, icon, title, Component.literal(n + " / " + total), n == 0 ? WmdUi.K_IDLE : WmdUi.K_GOOD, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {

        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, DIM, false);
        for (int row = 0; row < RackMenu.RACKS; row++) {
            if (!menu.has(row)) continue;
            int y = RackMenu.ROW_Y[row];
            var pose = g.pose();
            pose.pushPose();
            pose.translate(RackMenu.SLOT_X, y - 10, 0);
            pose.scale(0.75F, 0.75F, 1.0F);
            g.drawString(font, Component.translatable("gui.wmd.rack.row." + (row + 1)), 0, 0, DIM, false);
            pose.popPose();
            for (int i = 0; i < RackMenu.PER_RACK; i++) g.drawString(font, Integer.toString(i + 1), RackMenu.SLOT_X + i * RackMenu.SLOT_STEP + 6, y + 21, DIM, false);
        }
        var pose = g.pose();
        pose.pushPose();
        pose.translate(8, RackMenu.HINT_Y, 0);
        pose.scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, Component.translatable("gui.wmd.rack.hint"), 0, 0, DIM, false);
        pose.popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);

        if (menu.getCarried().isEmpty() && !WmdHelp.hovered(mouseX, mouseY) && WmdUi.over(mouseX, mouseY, chipX, topPos + 6, leftPos + imageWidth - 6 - chipX, 11))
            g.renderTooltip(font, Component.translatable("gui.wmd.rack.count", menu.count(), menu.capacity()), mouseX, mouseY);
    }
}
