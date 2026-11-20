package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class ExoRadialScreen extends Screen {

    private record Seg(int what, String key, boolean on) {}

    private final List<Seg> segs = new ArrayList<>();
    private int hover = -1;

    ExoRadialScreen() { super(Component.translatable("gui.wmd.exo_radial")); }

    @Override
    protected void init() {
        segs.clear();
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (ExoModule.CANNON.fitted(p)) segs.add(new Seg(0, "cannon", ClientExoMods.weapon == 0));
        if (ExoModule.MISSILES.fitted(p)) segs.add(new Seg(1, "missiles", ClientExoMods.weapon == 1));
        if (ExoModule.THERMAL.fitted(p)) segs.add(new Seg(2, "thermal", ClientExoMods.thermalToggled()));
        if (ExoModule.CLOAK.fitted(p)) segs.add(new Seg(3, "cloak", ClientExoMods.cloaked()));
        if (ExoModule.WINGS.fitted(p)) segs.add(new Seg(4, "hover", ClientExoMods.hovering()));
        if (segs.isEmpty()) segs.add(new Seg(-1, "none", false));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        long win = mc.getWindow().getWindow();
        if (!InputConstants.isKeyDown(win, ClientExoMods.RADIAL.getKey().getValue())) choose();
    }

    private void choose() {
        if (hover >= 0 && hover < segs.size() && segs.get(hover).what() >= 0) { ClientExoMods.toggle(segs.get(hover).what()); WmdUi.click(); }
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) { choose(); return true; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int cx = width / 2, cy = height / 2, n = segs.size();
        double dx = mx - cx, dy = my - cy;
        hover = -1;
        if (dx * dx + dy * dy > 18 * 18 && n > 0) {
            double a = Math.atan2(dy, dx) + Math.PI / 2;
            if (a < 0) a += Math.PI * 2;
            hover = (int) Math.floor((a + Math.PI / n) / (Math.PI * 2 / n)) % n;
        }
        g.fill(cx - 16, cy - 16, cx + 16, cy + 16, 0xC0101820);
        g.drawCenteredString(font, Component.translatable("gui.wmd.exo_radial.title"), cx, cy - 4, WmdUi.TEXT_MUTED);
        int r = 62;
        for (int i = 0; i < n; i++) {
            Seg s = segs.get(i);
            double a = Math.PI * 2 * i / n - Math.PI / 2;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            boolean h = i == hover;
            int w = 70, hh = 22;
            g.fill(x - w / 2 - 1, y - hh / 2 - 1, x + w / 2 + 1, y + hh / 2 + 1, h ? WmdUi.BLUE : 0xFF2E3742);
            g.fill(x - w / 2, y - hh / 2, x + w / 2, y + hh / 2, h ? 0xF02D3A4A : 0xE0141A22);
            int col = s.on() ? WmdUi.CYAN : h ? WmdUi.TEXT : WmdUi.TEXT_MUTED;
            Component label = Component.translatable("gui.wmd.exo_radial." + s.key());
            WmdUi.drawFit(g, font, label, x - w / 2 + 4, y - 4, w - 8, col);
            if (s.on()) WmdUi.led(g, x + w / 2 - 6, y - hh / 2 + 2, WmdUi.K_GOOD, false);
        }
        g.drawCenteredString(font, Component.translatable("gui.wmd.exo_radial.hint"), cx, cy + r + 22, WmdUi.TEXT_DIM);
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    static float clamp(float v) { return Mth.clamp(v, 0, 1); }
}
