package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

public class GeneratorScreen extends AbstractContainerScreen<GeneratorMenu> {

    private static final int GAUGE_X = 152, GAUGE_Y = 22, GAUGE_W = 14, GAUGE_H = 47;

    private static final int FUEL_X = 26, FUEL_Y = 22, FUEL_W = 16, FUEL_H = 47;
    private static final int DIESEL = 0xFFC89830;

    private final WmdUi.Smooth feSmooth = new WmdUi.Smooth(6.0F), fuelSmooth = new WmdUi.Smooth(6.0F);

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);

        Component chip;
        int kind;
        if (menu.isBurning()) { chip = Component.translatable("gui.wmd.kit.chip.burning"); kind = WmdUi.K_GOOD; }
        else if (menu.getFuelMb() < 1) { chip = Component.translatable("gui.wmd.kit.chip.no_fuel"); kind = WmdUi.K_BAD; }
        else { chip = Component.translatable("gui.wmd.kit.chip.idle"); kind = WmdUi.K_IDLE; }
        WmdUi.header(g, font, x, y, imageWidth, new ItemStack(Wmd.GENERATOR_ENGINE_ITEM.get()), title, chip, kind, menu.isBurning());

        for (Slot slot : menu.slots) WmdUi.slot(g, x + slot.x, y + slot.y, 0);

        WmdUi.well(g, x + FUEL_X, y + FUEL_Y, FUEL_W, FUEL_H);
        float fuel = fuelSmooth.to(Math.min(1.0F, menu.getFuelMb() / (float) Balance.GENERATOR_TANK_MB));
        int dieselH = Math.round(FUEL_H * fuel);
        if (fuel > 0 && dieselH == 0) dieselH = 1;
        if (dieselH > 0) {
            g.fill(x + FUEL_X, y + FUEL_Y + FUEL_H - dieselH, x + FUEL_X + FUEL_W, y + FUEL_Y + FUEL_H, DIESEL);
            g.fill(x + FUEL_X, y + FUEL_Y + FUEL_H - dieselH, x + FUEL_X + FUEL_W, y + FUEL_Y + FUEL_H - dieselH + 1, 0xC0F2D890);
        }

        WmdUi.energyGauge(g, x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H,
                feSmooth.to(Math.min(1.0F, menu.getEnergy() / (float) GeneratorEngineBlockEntity.CAPACITY)));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {

        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, WmdUi.TEXT_MUTED, false);

        int tx = 50;
        g.drawString(font, String.format("%,d FE", menu.getEnergy()), tx, 26, WmdUi.TEXT, false);
        g.drawString(font, String.format("of %,d", GeneratorEngineBlockEntity.CAPACITY), tx, 36, WmdUi.TEXT_MUTED, false);

        Component status;
        int colour;
        if (menu.isBurning()) {
            status = Component.translatable("block.wmd.generator_engine.output",
                    String.format("%,d", GeneratorEngineBlockEntity.FE_PER_SECOND));
            colour = WmdUi.GOOD;
        } else if (menu.getFuelMb() < 1) {
            status = Component.translatable("block.wmd.generator_engine.no_fuel");
            colour = WmdUi.BAD;
        } else {
            status = Component.translatable("block.wmd.generator_engine.idle");
            colour = WmdUi.TEXT_MUTED;
        }
        WmdUi.drawFit(g, font, status, tx, 52, GAUGE_X - 4 - tx, colour);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);

        int fx = leftPos + FUEL_X, fy = topPos + FUEL_Y;
        if (mouseX >= fx && mouseX < fx + FUEL_W && mouseY >= fy && mouseY < fy + FUEL_H) {
            g.renderTooltip(font, Component.translatable("block.wmd.generator_engine.diesel",
                    String.format("%,d", menu.getFuelMb()), String.format("%,d", Balance.GENERATOR_TANK_MB)), mouseX, mouseY);
        }

        int gx = leftPos + GAUGE_X, gy = topPos + GAUGE_Y;
        if (mouseX >= gx && mouseX < gx + GAUGE_W && mouseY >= gy && mouseY < gy + GAUGE_H) {
            g.renderTooltip(font, Component.literal(String.format("%,d / %,d FE", menu.getEnergy(), GeneratorEngineBlockEntity.CAPACITY)),
                    mouseX, mouseY);
        }
    }
}
