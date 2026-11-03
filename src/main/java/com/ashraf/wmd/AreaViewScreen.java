package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class AreaViewScreen extends Screen {

    private static final int LEFT = 150, ROW_H = 20, MARGIN = 8;
    private static final String P = "gui.wmd.desig.";

    private int cx, cz, ticks, satPick = -1;
    private float yaw = 30.0F, pitch = 55.0F, zoom = 1.0F;
    private double downX, downY;
    private boolean dragging, pressedInView;
    private EditBox xBox, zBox;

    public AreaViewScreen(int cx, int cz) { this(cx, cz, AreaView.MODE_SAT, Balance.AREA_VIEW_SIZE); }

    private final int mode, size;

    public AreaViewScreen(int cx, int cz, int mode, int size) {
        super(Component.translatable(mode == AreaView.MODE_CAM ? "gui.wmd.camera.title" : P + "title"));
        this.cx = cx;
        this.cz = cz;
        this.mode = mode;
        this.size = size;
    }

    @Override public boolean isPauseScreen() { return false; }

    private int W() { return Math.min(width - 16, 480); }
    private int H() { return Math.min(height - 16, 300); }
    private int x0() { return (width - W()) / 2; }
    private int y0() { return (height - H()) / 2; }
    private int vx() { return x0() + LEFT + 6; }
    private int vy() { return y0() + 26; }
    private int vw() { return W() - LEFT - 12; }
    private int vh() { return H() - 32; }

    private static List<OrbitMath.Sat> mySats() {
        List<OrbitMath.Sat> l = new ArrayList<>();
        var p = Minecraft.getInstance().player;
        if (p == null) return l;
        long now = OrbitView.now();
        for (OrbitMath.Sat s : OrbitView.sats()) if ("designator".equals(s.type()) && p.getUUID().equals(s.owner()) && now < s.endMs()) l.add(s);
        return l;
    }

    @Override
    protected void init() {
        int x = x0() + MARGIN, y = y0() + 30 + 4 * ROW_H + 8;
        xBox = addRenderableWidget(new EditBox(font, x, y, 50, 14, Component.literal("X")));
        zBox = addRenderableWidget(new EditBox(font, x + 54, y, 50, 14, Component.literal("Z")));
        xBox.setValue(Integer.toString(cx));
        zBox.setValue(Integer.toString(cz));
        addRenderableWidget(new WmdUi.KitButton(x + 108, y - 1, LEFT - 2 * MARGIN - 108, 16, Component.translatable(P + "view"), b -> recentre()));
        addRenderableWidget(new WmdUi.KitButton(x, y0() + H() - 26, LEFT - 2 * MARGIN, 16, Component.translatable(P + "stop"),
                b -> PacketDistributor.sendToServer(new DesignatePayload(false, 0, 0, 0)), true));
        if (satPick < 0) { List<OrbitMath.Sat> l = mySats(); if (!l.isEmpty()) satPick = l.get(0).id(); }
        AreaViewClient.MAIN.request(mode, cx, cz, size, false);
    }

    private void recentre() {
        try {
            cx = Integer.parseInt(xBox.getValue().trim());
            cz = Integer.parseInt(zBox.getValue().trim());
        } catch (NumberFormatException e) { return; }
        AreaViewClient.MAIN.request(mode, cx, cz, size, false);
    }

    @Override
    public void removed() { AreaViewClient.MAIN.close(); }

    @Override
    public void tick() {
        ticks++;
        var m = AreaViewClient.MAIN;
        if (m.ready() && ticks % 20 == 0) m.request(mode, m.cx(), m.cz(), m.size(), true);
        if (ticks % (m.ready() ? 1200 : 100) == 0) m.request(mode, cx, cz, size, false);
    }

    private static String mmss(long ms) { long s = Math.max(0L, ms / 1000L); return String.format("%d:%02d", s / 60, s % 60); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = x0(), y = y0(), W = W(), H = H();
        DesigStatusPayload st = DesigStatusPayload.last();
        boolean lit = st.satId() >= 0 && st.state() == SatDesignator.LIT;
        WmdUi.panel(g, x, y, W, H);
        WmdUi.header(g, font, x, y, W, new ItemStack(Wmd.LASER_DESIGNATOR_POD.get()), title,
                Component.translatable(P + "state." + (st.satId() < 0 ? "none" : st.state())), lit ? WmdUi.K_GOOD : st.satId() < 0 ? WmdUi.K_IDLE : WmdUi.K_WARN, lit);

        int ly = y + 30;
        List<OrbitMath.Sat> sats = mySats();
        long now = OrbitView.now();
        if (sats.isEmpty()) WmdUi.drawFit(g, font, Component.translatable(P + "no_sats"), x + MARGIN, ly + 4, LEFT - 2 * MARGIN, WmdUi.TEXT_MUTED);
        for (int i = 0; i < sats.size() && i < 4; i++) {
            OrbitMath.Sat s = sats.get(i);
            int ry = ly + i * ROW_H;
            boolean pick = s.id() == satPick, over = OrbitMath.isOverhead(s, cx + 0.5, cz + 0.5, now);
            if (pick) g.fill(x + 4, ry - 2, x + LEFT - 4, ry + ROW_H - 4, WmdUi.CARD);
            g.drawString(font, "#" + s.id(), x + MARGIN, ry, pick ? WmdUi.TEXT : WmdUi.TEXT_MUTED, false);
            Component when = over ? Component.translatable(P + "overhead", mmss(OrbitMath.overheadLeft(s, cx + 0.5, cz + 0.5, now)))
                    : Component.translatable(P + "next", mmss(OrbitMath.nextPass(s, cx + 0.5, cz + 0.5, now)));
            WmdUi.drawFit(g, font, when, x + MARGIN + 26, ry, LEFT - 2 * MARGIN - 26, over ? WmdUi.GOOD : WmdUi.TEXT_MUTED);
        }

        int sy = y + 30 + 4 * ROW_H + 30;
        if (st.satId() >= 0) {
            long since = System.currentTimeMillis() - DesigStatusPayload.receivedMs();
            WmdUi.drawFit(g, font, Component.translatable(P + "target", st.satId(), st.x(), st.z()), x + MARGIN, sy, LEFT - 2 * MARGIN, WmdUi.TEXT);
            Component t = st.state() == SatDesignator.LIT ? Component.translatable(P + "left", mmss(st.leftMs() - since))
                    : st.state() == SatDesignator.WAITING ? Component.translatable(P + "next", mmss(st.nextMs() - since)) : Component.translatable(P + "state." + st.state());
            WmdUi.drawFit(g, font, t, x + MARGIN, sy + 11, LEFT - 2 * MARGIN, lit ? WmdUi.GOOD : WmdUi.WARN);
            if (st.onShip()) WmdUi.drawFit(g, font, Component.translatable(P + "on_ship"), x + MARGIN, sy + 22, LEFT - 2 * MARGIN, WmdUi.BLUE_LIGHT);
        }
        int hy = y + H - 40;
        for (net.minecraft.util.FormattedCharSequence line : font.split(Component.translatable(P + "hint"), LEFT - 2 * MARGIN)) { g.drawString(font, line, x + MARGIN, hy, WmdUi.TEXT_DIM, false); hy += 10; }

        int vx = vx(), vy = vy(), vw = vw(), vh = vh();
        WmdUi.well(g, vx, vy, vw, vh);
        var m = AreaViewClient.MAIN;
        if (!m.refused().isEmpty()) {
            Component why = Component.translatable(m.refused());
            g.drawCenteredString(font, why, vx + vw / 2, vy + vh / 2 - 4, WmdUi.WARN);
        } else if (!m.ready()) g.drawCenteredString(font, Component.translatable(P + "loading"), vx + vw / 2, vy + vh / 2 - 4, WmdUi.TEXT_MUTED);
        else {
            int[] hover = WmdUi.over(mx, my, vx, vy, vw, vh) && !dragging ? m.pick(mx, my) : null;
            m.draw(g, vx, vy, vw, vh, yaw, pitch, zoom, st.satId() >= 0 ? st.x() : Integer.MIN_VALUE, st.z(),
                    hover == null ? Integer.MIN_VALUE : hover[0], hover == null ? 0 : hover[1]);
            if (hover != null) g.drawString(font, hover[0] + ", " + hover[1], vx + 4, vy + vh - 12, WmdUi.TEXT, true);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = x0(), ly = y0() + 30;
        List<OrbitMath.Sat> sats = mySats();
        for (int i = 0; i < sats.size() && i < 4; i++)
            if (button == 0 && WmdUi.over(mx, my, x + 4, ly + i * ROW_H - 2, LEFT - 8, ROW_H)) { satPick = sats.get(i).id(); WmdUi.click(); return true; }
        if (button == 0 && WmdUi.over(mx, my, vx(), vy(), vw(), vh())) { downX = mx; downY = my; dragging = false; pressedInView = true; return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pressedInView && button == 0) {
            if (Math.abs(mx - downX) + Math.abs(my - downY) > 3.0) dragging = true;
            if (dragging) { yaw += (float) dx * 0.6F; pitch = net.minecraft.util.Mth.clamp(pitch + (float) dy * 0.6F, 10.0F, 89.0F); }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (pressedInView && button == 0) {
            pressedInView = false;
            if (!dragging && AreaViewClient.MAIN.ready()) {
                int[] c = AreaViewClient.MAIN.pick(mx, my);
                if (c != null && satPick >= 0) { WmdUi.confirm(); PacketDistributor.sendToServer(new DesignatePayload(true, satPick, c[0], c[1])); }
            }
            dragging = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (WmdUi.over(mx, my, vx(), vy(), vw(), vh())) { zoom = net.minecraft.util.Mth.clamp(zoom * (sy > 0 ? 1.15F : 1.0F / 1.15F), 0.6F, 6.0F); return true; }
        return super.mouseScrolled(mx, my, sx, sy);
    }
}
