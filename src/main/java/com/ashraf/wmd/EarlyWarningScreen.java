package com.ashraf.wmd;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

public class EarlyWarningScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 172, PH = 172, HEAD_Y = 46, ROW_Y = 60, ROW_H = 22;

    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, BAD = WmdUi.BAD;

    public EarlyWarningScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private EarlyWarningArrayBlockEntity array() { return menu.machine() instanceof EarlyWarningArrayBlockEntity a ? a : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return array() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        if (menu.machine() instanceof BlockEntity be)
            addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, be.getBlockPos())));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        EarlyWarningArrayBlockEntity a = array();
        if (a == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        fit(g, Component.translatable("gui.wmd.ewa.panel"), cx, y + 6, TEXT);
        boolean on = a.isRunning();
        Component onTxt = Component.translatable(on ? "gui.wmd.ewa.online" : "gui.wmd.ewa.offline");
        int chipW = Math.min(font.width(onTxt), PW - 10 - 14) + 15;
        WmdUi.chipRight(g, font, cx + (chipW + 1) / 2, y + 16, onTxt, on ? WmdUi.K_GOOD : WmdUi.K_BAD, on, PW - 10);
        fit(g, Component.translatable("gui.wmd.ewa.range", String.format("%,d", Balance.EWA_RANGE)), cx, y + 30, GREY);
        WmdUi.divider(g, x + 5, y + HEAD_Y - 4, PW - 10);
        fit(g, Component.translatable("gui.wmd.ewa.last"), cx, y + HEAD_Y, TEXT);
        List<long[]> l = a.recent();
        if (l.isEmpty()) { fit(g, Component.translatable("gui.wmd.ewa.none"), cx, y + ROW_Y + 4, GREY); return; }
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        double ax = a.getBlockPos().getX() + 0.5, az = a.getBlockPos().getZ() + 0.5;
        for (int i = 0; i < l.size() && i < EarlyWarningArrayBlockEntity.WARN_KEEP; i++) {
            long[] w = l.get(l.size() - 1 - i);
            int ry = y + ROW_Y + i * ROW_H;
            long dist = Math.round(Math.hypot(w[0] - ax, w[1] - az));
            if (w[6] == 2) {
                fit(g, Component.translatable("gui.wmd.ewa.row_airship", w[0], w[1], String.format("%,d", dist)), cx, ry, TEXT);
                fit(g, Component.translatable(w[3] == 0 ? "gui.wmd.ewa.row_airship_hostile" : "gui.wmd.ewa.row_airship_neutral", w[2]), cx, ry + 10, w[3] == 0 ? BAD : GREY);
                continue;
            }
            fit(g, Component.translatable("gui.wmd.ewa.row", w[0], w[1], String.format("%,d", dist)), cx, ry, TEXT);
            Component second;
            int col = GREY;
            if (w[6] == 1) second = Component.translatable("gui.wmd.ewa.row_orbit");
            else if (now < w[5]) { second = Component.translatable("gui.wmd.ewa.row_impact", w[2], w[3], (w[5] - now + 19) / 20); col = BAD; }
            else second = Component.translatable("gui.wmd.ewa.row_done", w[2], w[3]);
            fit(g, second, cx, ry + 10, col);
        }
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) {
        int maxW = PW - 10;
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
