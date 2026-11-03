package com.ashraf.wmd;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class CounterBatteryScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 172, PH = 196, ROW_Y = 64, ROW_H = 22;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, BAD = WmdUi.BAD, BLUE = WmdUi.BLUE_LIGHT;
    private static final String P = "gui.wmd.cbr.";

    public CounterBatteryScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private CounterBatteryRadarBlockEntity radar() { return menu.machine() instanceof CounterBatteryRadarBlockEntity r ? r : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return radar() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        CounterBatteryRadarBlockEntity r = radar();
        if (r != null) addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, r.getBlockPos())));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        CounterBatteryRadarBlockEntity r = radar();
        if (r == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        boolean on = r.isRunning(), track = on && r.tracking(now);
        Component chip = Component.translatable(!on ? P + "offline" : track ? P + "tracking" : P + "scanning");
        WmdUi.header(g, font, x, y, PW, new ItemStack(r.getBlockState().getBlock().asItem()), Component.translatable("block.wmd.counter_battery_radar"),
                chip, !on ? WmdUi.K_BAD : track ? WmdUi.K_WARN : WmdUi.K_GOOD, track);
        fit(g, Component.translatable(P + "range", String.format("%,d", Balance.CBR_RANGE), Balance.CBR_TRACE_TICKS / 20), cx, y + 26, GREY);
        boolean armed = r.defcon() <= 1;
        fit(g, Component.translatable(armed ? P + "auto_armed" : P + "auto_off", Balance.CBR_AUTO_ROUNDS), cx, y + 37, armed ? BAD : GREY);
        WmdUi.divider(g, x + 5, y + 48, PW - 10);
        fit(g, Component.translatable(P + "last"), cx, y + 52, TEXT);
        List<CompoundTag> l = r.traces();
        if (l.isEmpty()) { fit(g, Component.translatable(P + "none"), cx, y + ROW_Y + 4, GREY); return; }
        java.time.format.DateTimeFormatter hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        for (int i = 0; i < l.size() && i < Balance.CBR_KEEP; i++) {
            CompoundTag c = l.get(l.size() - 1 - i);
            int ry = y + ROW_Y + i * ROW_H;
            int gi = c.getInt("g");
            Component gun = Component.translatable(Artillery.gunNameKey(gi));
            WmdUi.led(g, x + 7, ry + 1, i == 0 && track ? WmdUi.K_BAD : WmdUi.K_WARN, i == 0 && track);
            drawLeft(g, Component.translatable(P + "row", gun, c.getString("by"), c.getInt("x"), c.getInt("z"), c.getInt("err")), x + 15, ry, PW - 20, i == 0 ? TEXT : GREY);
            String time = hm.format(java.time.Instant.ofEpochMilli(c.getLong("ms")).atZone(java.time.ZoneId.systemDefault()));
            drawLeft(g, Component.translatable(P + "row2", time, c.getInt("n")), x + 15, ry + 10, PW - 20, i == 0 ? BLUE : GREY);
        }
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) {
        int maxW = PW - 10, w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private void drawLeft(GuiGraphics g, Component text, int x, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, x, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
