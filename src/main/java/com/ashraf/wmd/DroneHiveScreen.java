package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class DroneHiveScreen extends AbstractContainerScreen<DroneHiveMenu> {

    private static final int BTN_X = 98, BTN_W = 38, MODE_X = 140, MODE_W = 30;

    public DroneHiveScreen(DroneHiveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = DroneHiveMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, topPos + 22, "icon_lock", Component.translatable("gui.wmd.kit.tab.lock"),
                b -> minecraft.setScreen(new SecurityScreen(this, menu.getPos()))));
        for (int i = 0; i < DroneHiveBlockEntity.BAYS; i++) {
            final int bay = i;
            int y = topPos + DroneHiveMenu.ROW_Y + i * DroneHiveMenu.ROW_H + 4;
            addRenderableWidget(new WmdUi.KitButton(leftPos + BTN_X, y, BTN_W, 14, Component.empty(), b -> press(bay * 2)) {
                @Override public Component getMessage() { return Component.translatable(menu.out(bay) ? "gui.wmd.hive.recall" : "gui.wmd.hive.launch"); }
            });
            addRenderableWidget(new WmdUi.KitButton(leftPos + MODE_X, y, MODE_W, 14, Component.empty(), b -> press(bay * 2 + 1)) {
                @Override public Component getMessage() { return Component.translatable(modeKey(bay)); }
            });
        }
    }

    private String modeKey(int bay) {
        int t = menu.dockedTier(bay), m = menu.mode(bay);
        if (t == 2) return m == 1 ? "gui.wmd.hive.mode.fly" : "gui.wmd.hive.mode.auto";
        return m == 1 ? "gui.wmd.hive.mode.players" : m == 2 ? "gui.wmd.hive.mode.mobs" : "gui.wmd.hive.mode.both";
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        WmdUi.divider(g, x + 8, y + 15, imageWidth - 16);
        WmdHelp.button(g, x + imageWidth - 18, y + 4);
        for (int i = 0; i < DroneHiveBlockEntity.BAYS; i++) {
            int ry = y + DroneHiveMenu.ROW_Y + i * DroneHiveMenu.ROW_H;
            WmdUi.slot(g, x + 8, ry + 4, WmdUi.BLUE);
            WmdUi.well(g, x + 30, ry + 21, 62, 3);
            int fill = (int) Math.round(62 * Math.min(1.0, menu.charge(i) / 1000.0));
            if (fill > 0 && menu.dockedTier(i) > 0) g.fill(x + 30, ry + 21, x + 30 + fill, ry + 24, WmdUi.CYAN);
            if (i < DroneHiveBlockEntity.BAYS - 1) WmdUi.divider(g, x + 8, ry + DroneHiveMenu.ROW_H - 1, imageWidth - 16);
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) WmdUi.slot(g, x + 8 + col * 18, y + DroneHiveMenu.INV_Y + row * 18, 0);
        for (int col = 0; col < 9; col++) WmdUi.slot(g, x + 8 + col * 18, y + DroneHiveMenu.HOTBAR_Y, 0);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        WmdUi.drawFit(g, font, title, 8, 5, 110, WmdUi.TEXT);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, WmdUi.TEXT_MUTED, false);
        Component power = Component.translatable("gui.wmd.hive.power", menu.energy() / 1000, Balance.HIVE_BUFFER_FE / 1000);
        small(g, power, 112, 6, WmdUi.TEXT_MUTED, 40);
        for (int i = 0; i < DroneHiveBlockEntity.BAYS; i++) {
            int ry = DroneHiveMenu.ROW_Y + i * DroneHiveMenu.ROW_H;
            int t = menu.dockedTier(i);
            Component name = menu.out(i) ? Component.translatable("gui.wmd.hive.bay_out", i + 1)
                    : t == 0 ? Component.translatable("gui.wmd.hive.bay_empty", i + 1) : Component.translatable("gui.wmd.hive.bay", i + 1, Component.translatable("item.wmd.drone_t" + t));
            small(g, name, 30, ry + 3, WmdUi.TEXT, 66);
            Component state = menu.out(i) ? Component.translatable("gui.wmd.hive.state.out")
                    : t == 0 ? Component.translatable("gui.wmd.hive.state.empty")
                    : menu.charge(i) >= 1000 ? Component.translatable("gui.wmd.hive.state.ready") : Component.translatable("gui.wmd.hive.state.charging", menu.charge(i) / 10);
            small(g, state, 30, ry + 12, menu.out(i) ? WmdUi.GOOD : menu.charge(i) >= 1000 && t > 0 ? WmdUi.GOOD : WmdUi.TEXT_MUTED, 66);
        }
    }

    private void small(GuiGraphics g, Component c, int x, int y, int colour, int width) {
        float sc = Math.min(0.75F, (float) width / Math.max(1, font.width(c)));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y + (1.0F - sc) * 4.0F, 0);
        pose.scale(sc, sc, 1.0F);
        g.drawString(font, c, 0, 0, colour, false);
        pose.popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
