package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class TeleporterScreen extends AbstractContainerScreen<TeleporterMenu> {

    private static final int DIM = WmdUi.TEXT_MUTED;
    private static final int ROW_X = 40, ROW_Y = 26, ROW_W = 106, ROW_H = 13, ROW_STEP = 15, X_W = 10;
    private static final int GAUGE_X = 152, GAUGE_Y = 26, GAUGE_W = 12, GAUGE_H = 58;
    private static final int JUMP_X = 40, JUMP_Y = 88, JUMP_W = 60, STATUS_Y = 107, BAR_Y = 120;
    private Button jump;

    public TeleporterScreen(TeleporterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = TeleporterMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        jump = addRenderableWidget(new WmdUi.KitButton(leftPos + JUMP_X, topPos + JUMP_Y, JUMP_W, 14, Component.translatable("gui.wmd.tp.jump"), b -> press(4)));
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        WmdUi.slot(g, x + TeleporterMenu.SLOT_X, y + TeleporterMenu.SLOT_Y, WmdUi.BLUE);
        for (int i = 1; i < menu.slots.size(); i++) WmdUi.slot(g, x + menu.slots.get(i).x, y + menu.slots.get(i).y, 0);
        for (int i = 0; i < 4; i++) {
            int ry = y + ROW_Y + i * ROW_STEP;
            WmdUi.well(g, x + ROW_X, ry, ROW_W, ROW_H);
            if (i < menu.links().size() && i == menu.selected()) {
                g.fill(x + ROW_X - 1, ry - 1, x + ROW_X + ROW_W + 1, ry, WmdUi.BLUE);
                g.fill(x + ROW_X - 1, ry + ROW_H, x + ROW_X + ROW_W + 1, ry + ROW_H + 1, WmdUi.BLUE);
            }
        }
        WmdUi.energyGauge(g, x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H, menu.energy() / (float) Balance.TP_BUFFER);
        boolean running = menu.phase() == MissileTeleporterBlockEntity.CHARGING || menu.phase() == MissileTeleporterBlockEntity.FADING || menu.phase() == MissileTeleporterBlockEntity.LOADING;
        WmdUi.progressBar(g, x + 8, y + BAR_Y, 160, 5, menu.progress(), running);
        int ph = menu.phase();
        int kind = ph == MissileTeleporterBlockEntity.READY ? WmdUi.K_GOOD : ph == MissileTeleporterBlockEntity.EMPTY ? WmdUi.K_IDLE : WmdUi.K_INFO;
        WmdUi.header(g, font, x, y, imageWidth, new ItemStack(Wmd.MISSILE_TELEPORTER_ITEM.get()), title, Component.translatable("gui.wmd.tp.phase." + ph), kind, running);
        if (jump != null) jump.active = ph == MissileTeleporterBlockEntity.READY && menu.cooldown() == 0 && !menu.links().isEmpty();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, DIM, false);
        small(g, Component.translatable("gui.wmd.tp.chamber"), TeleporterMenu.SLOT_X - 4, TeleporterMenu.SLOT_Y - 8, DIM);
        small(g, Component.translatable("gui.wmd.tp.chamber_hint"), 8, TeleporterMenu.SLOT_Y + 22, DIM);
        for (int i = 0; i < 4; i++) {
            int ry = ROW_Y + i * ROW_STEP;
            if (i >= menu.links().size()) { small(g, Component.translatable("gui.wmd.tp.no_link", i + 1), ROW_X + 3, ry + 4, WmdUi.TEXT_DIM); continue; }
            BlockPos p = menu.links().get(i);
            int st = menu.linkState(i);
            small(g, Component.literal((i + 1) + "  " + p.getX() + ", " + p.getZ()), ROW_X + 3, ry + 1, WmdUi.TEXT);
            small(g, Component.translatable("gui.wmd.tp.st." + st), ROW_X + 3, ry + 7, st == 0 ? 0xFF56C46A : 0xFFE08A4A);
            g.drawString(font, "x", ROW_X + ROW_W - X_W + 2, ry + 2, WmdUi.TEXT_MUTED, false);
        }
        small(g, Component.translatable("gui.wmd.tp.cost", String.format("%,d", Balance.TP_FE_PER_JUMP / 1_000_000)), JUMP_X + JUMP_W + 4, JUMP_Y + 4, DIM);
        int ph = menu.phase(), res = menu.lastResult();
        Component status;
        int kind;
        if (ph == MissileTeleporterBlockEntity.CHARGING || ph == MissileTeleporterBlockEntity.FADING) { status = Component.translatable("gui.wmd.tp.status.jumping"); kind = WmdUi.K_INFO; }
        else if (menu.cooldown() > 0) { status = Component.translatable("gui.wmd.tp.status.cooldown", menu.cooldown()); kind = WmdUi.K_WARN; }
        else if (res > 0 && res < TeleporterMenu.NO_LINK) { status = Component.translatable("gui.wmd.tp.st." + res); kind = WmdUi.K_BAD; }
        else if (ph == MissileTeleporterBlockEntity.READY) { status = Component.translatable(menu.energy() >= Balance.TP_FE_PER_JUMP ? "gui.wmd.tp.status.ready" : "gui.wmd.tp.status.charging_buffer"); kind = WmdUi.K_GOOD; }
        else { status = Component.translatable("gui.wmd.tp.status." + (ph == MissileTeleporterBlockEntity.LOADING ? "loading" : "empty")); kind = WmdUi.K_IDLE; }
        WmdUi.led(g, 8, STATUS_Y + 1, kind, kind == WmdUi.K_INFO);
        WmdUi.drawFit(g, font, status, 16, STATUS_Y, 152, kind == WmdUi.K_BAD ? 0xFFE08A4A : WmdUi.TEXT);
    }

    private void small(GuiGraphics g, Component c, int x, int y, int colour) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, c, 0, 0, colour, false);
        pose.popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < menu.links().size(); i++) {
                int rx = leftPos + ROW_X, ry = topPos + ROW_Y + i * ROW_STEP;
                if (WmdUi.over(mx, my, rx + ROW_W - X_W, ry, X_W, ROW_H)) { press(8 + i); menu.links().remove(i); WmdUi.click(); return true; }
                if (WmdUi.over(mx, my, rx, ry, ROW_W - X_W, ROW_H)) { press(i); WmdUi.click(); return true; }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (!menu.getCarried().isEmpty() || WmdHelp.hovered(mouseX, mouseY)) return;
        if (WmdUi.over(mouseX, mouseY, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_W, GAUGE_H))
            g.renderTooltip(font, Component.translatable("gui.wmd.tp.power", String.format("%,d", menu.energy()), String.format("%,d", Balance.TP_BUFFER), String.format("%,d", Balance.TP_FE_PER_JUMP)), mouseX, mouseY);
    }
}
