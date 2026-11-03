package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class AlarmScreen extends AbstractContainerScreen<AlarmMenu> {

    private static final int TAB_X = -23, TAB_Y = 22, TAB_STEP = 24, TABS = 2;
    private final WmdUi.Smooth feSmooth = new WmdUi.Smooth(6.0F);

    public AlarmScreen(AlarmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 98;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new WmdUi.TabButton(leftPos + TAB_X, topPos + TAB_Y, "icon_lock", Component.translatable("gui.wmd.kit.tab.lock"),
                b -> minecraft.setScreen(new SecurityScreen(this, menu.getPos()))));
        addRenderableWidget(new WmdUi.TabButton(leftPos + TAB_X, topPos + TAB_Y + TAB_STEP, "icon_freq", Component.translatable("gui.wmd.kit.tab.freq"),
                b -> minecraft.setScreen(new FrequencyScreen(this, menu::getFrequency, f -> PacketDistributor.sendToServer(new SetAlarmPayload(menu.getPos(), f, false))))));
        addRenderableWidget(new WmdUi.KitButton(leftPos + 12, topPos + 33, 80, 18, Component.translatable("screen.wmd.alarm.test"),
                b -> PacketDistributor.sendToServer(new SetAlarmPayload(menu.getPos(), Math.max(1, menu.getFrequency()), true))));
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        if (WmdUi.over(mx, my, leftPos + TAB_X, topPos + TAB_Y, 23, TAB_STEP * (TABS - 1) + 22)) return false;
        return super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }

    private boolean powered() { return menu.getEnergy() >= AlarmBlockEntity.FE_PER_TICK; }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        boolean sounding = menu.isSounding();
        Component chip = Component.translatable(sounding ? "gui.wmd.kit.chip.sounding" : powered() ? "gui.wmd.kit.chip.ready" : "gui.wmd.kit.chip.no_power");
        int kind = sounding ? WmdUi.K_BAD : powered() ? WmdUi.K_GOOD : WmdUi.K_BAD;
        ItemStack icon = minecraft != null && minecraft.level != null ? new ItemStack(minecraft.level.getBlockState(menu.getPos()).getBlock()) : ItemStack.EMPTY;
        WmdUi.header(g, font, x, y, imageWidth, icon, title, chip, kind, sounding);

        int bx = x + 12, by = y + 86, w = imageWidth - 24;
        WmdUi.well(g, bx, by, w, 4);
        float f = feSmooth.to(Math.min(1.0F, menu.getEnergy() / (float) Math.max(1, AlarmBlockEntity.CAPACITY)));
        int fill = Math.round(w * f);
        if (f > 0 && fill == 0) fill = 1;
        if (fill > 0) {
            g.fill(bx, by, bx + fill, by + 4, WmdUi.CYAN);
            g.fill(bx, by, bx + fill, by + 1, 0xFFBFF4FA);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {

        boolean powered = powered();
        g.drawString(font, Component.translatable(menu.isSounding() ? "screen.wmd.alarm.sounding"
                : powered ? "screen.wmd.alarm.ready" : "screen.wmd.alarm.no_power"),
                12, 60, menu.isSounding() ? WmdUi.BAD : powered ? WmdUi.GOOD : WmdUi.BAD, false);
        g.drawString(font, menu.getEnergy() + " / " + AlarmBlockEntity.CAPACITY + " FE", 12, 74, WmdUi.TEXT_MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
