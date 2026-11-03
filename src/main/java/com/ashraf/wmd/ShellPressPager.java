package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

final class ShellPressPager {

    private ShellPressPager() {}

    static final int PAGE = 9, BAR = 14, BTN = 14;
    private static int page;

    static boolean paged() { return ShellPressScreen.rows() > PAGE; }

    static int pages() { return (ShellPressScreen.rows() + PAGE - 1) / PAGE; }

    static void show(int mode) { page = Math.max(0, Math.min(pages() - 1, mode / PAGE)); }

    static boolean hidden(int m) { return paged() && m / PAGE != page; }

    static int shift(int m) { return paged() ? page * PAGE : 0; }

    static int drawBar(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY) {
        page = Math.max(0, Math.min(pages() - 1, page));
        button(g, font, x + 6, y, "<", page > 0, mouseX, mouseY);
        button(g, font, x + w - 6 - BTN, y, ">", page < pages() - 1, mouseX, mouseY);
        Component t = Component.translatable("gui.wmd.shell_press.page", page + 1, pages());
        int tw = Math.min(font.width(t), w - 2 * (BTN + 10));
        WmdUi.drawFit(g, font, t, x + (w - tw) / 2, y + 3, w - 2 * (BTN + 10), WmdUi.TEXT_MUTED);
        return y + BAR;
    }

    private static void button(GuiGraphics g, Font font, int x, int y, String s, boolean on, int mx, int my) {
        WmdUi.button(g, font, x, y, BTN, BTN - 1, Component.literal(s), on ? WmdUi.B_NORMAL : WmdUi.B_DISABLED, on && WmdUi.over(mx, my, x, y, BTN, BTN - 1));
    }

    static boolean click(double mx, double my, int x, int y, int w) {
        if (!paged()) return false;
        int np = page;
        if (WmdUi.over(mx, my, x + 6, y, BTN, BTN - 1) && page > 0) np = page - 1;
        else if (WmdUi.over(mx, my, x + w - 6 - BTN, y, BTN, BTN - 1) && page < pages() - 1) np = page + 1;
        if (np == page) return false;
        page = np;
        WmdUi.click();
        return true;
    }
}
