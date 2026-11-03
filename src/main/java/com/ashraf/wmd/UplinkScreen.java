package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class UplinkScreen extends Screen {

    private static final int W = 208, H = 150, ROWS = 4, ROW_H = 14;
    private static final int TEXT = WmdUi.TEXT, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, BLUE = WmdUi.BLUE_LIGHT;
    private static final String P = "gui.wmd.uplink.";

    private final Screen parent;
    private final TerminalMenu menu;

    public UplinkScreen(Screen parent, TerminalMenu menu) {
        super(Component.translatable(P + "title"));
        this.parent = parent;
        this.menu = menu;
    }

    private List<Flight> mine() {
        List<Flight> out = new ArrayList<>();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return out;
        UUID me = minecraft.player.getUUID();
        long now = minecraft.level.getGameTime();
        for (Flight f : ClientFlights.FLIGHTS.values()) {
            if (!f.build().uplink() || Satellites.toOrbit(f.build()) || now >= f.endTime()) continue;
            if (!f.isOwnedBy(me, minecraft.player.getGameProfile().getName())) continue;
            out.add(f);
        }
        out.sort((a, b) -> Long.compare(a.id(), b.id()));
        return out.size() > ROWS ? out.subList(0, ROWS) : out;
    }

    @Override
    protected void init() {
        int left = (width - W) / 2, top = (height - H) / 2;
        addRenderableWidget(new WmdUi.KitButton(left + 8, top + H - 22, W - 16, 16, Component.translatable("gui.wmd.security.back"), b -> onClose()));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int left = (width - W) / 2, top = (height - H) / 2;
        WmdUi.panel(g, left, top, W, H);
        fitted(g, title, left + 8, top + 6, TEXT);
        WmdUi.divider(g, left + 8, top + 16, W - 16);
        WmdHelp.button(g, left + W - 8 - WmdHelp.SIZE, top + 4);
        boolean cov = menu.uplinkCoverage();
        fitted(g, Component.translatable(cov ? P + "coverage_on" : P + "coverage_off"), left + 8, top + 20, cov ? GOOD : BAD);
        fitted(g, Component.translatable(P + "flights"), left + 8, top + 34, TEXT);
        List<Flight> list = mine();
        long now = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        int y = top + 46;
        WmdUi.well(g, left + 6, top + 45, W - 12, ROWS * ROW_H);
        if (list.isEmpty()) g.drawString(font, Component.translatable(P + "none"), left + 10, y + 1, WmdUi.TEXT_DIM, false);
        for (Flight f : list) {
            Component type = Component.translatable("warhead.wmd." + f.build().warheadType());
            Component row = Component.translatable(P + "row", f.id(), f.tier(), type, f.tx(), f.tz(), Math.max(0L, (f.endTime() - now) / 20));
            fitted(g, row, left + 8, y + 1, (f.uflags() & Flight.U_LOCKED) != 0 ? WmdUi.WARN : TEXT);
            y += ROW_H;
        }
        fitted(g, Component.translatable(P + "use_cc"), left + 8, top + 46 + ROWS * ROW_H + 6, BLUE);
    }

    private void fitted(GuiGraphics g, Component text, int x, int y, int colour) {
        int width = W - 16;
        float sc = Math.min(1.0F, (float) width / Math.max(1, font.width(text)));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y + (1.0F - sc) * 4.0F, 0);
        pose.scale(sc, sc, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        pose.popPose();
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
