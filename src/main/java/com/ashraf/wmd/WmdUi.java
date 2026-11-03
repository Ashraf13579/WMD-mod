package com.ashraf.wmd;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

public final class WmdUi {

    private WmdUi() {}

    public static final int TEXT = 0xFFDCE3EA, TEXT_MUTED = 0xFF8E99A6, TEXT_DIM = 0xFF6F7C8A;
    public static final int BLUE = 0xFF3A7BD5, BLUE_LIGHT = 0xFF6FA8FF, ORANGE = 0xFFD58A3A, ORANGE_LIGHT = 0xFFF2C08A, CYAN = 0xFF3AC2D5;
    public static final int GOOD = 0xFF8FE3A8, BAD = 0xFFFF8A8A, WARN = 0xFFF2C08A;
    public static final int WELL = 0xFF0F1318, DIVIDER = 0xFF2E3742, CARD = 0xFF202833;

    public static final int EV_ROD = 0xFFE24A3B, EV_ASTEROID = 0xFFFF8C2E, EV_RAILGUN = 0xFF4DA6FF;

    public static void eventBar(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component line, int y, int col, int event) {
        int lw = font.width(line) + 20, x = (g.guiWidth() - lw) / 2;
        g.fill(x, y, x + lw, y + 14, 0xC00F1318);
        g.fill(x, y, x + lw, y + 1, event);
        g.fill(x, y, x + 4, y + 14, event);
        g.drawString(font, line, x + 12, y + 3, col, false);
    }

    public interface Kit {}

    public static final int K_IDLE = 0, K_GOOD = 1, K_BAD = 2, K_WARN = 3, K_INFO = 4;

    private static final int[] CHIP_BG = { 0xFF2A3340, 0xFF1F4D2E, 0xFF4D1F22, 0xFF4D3A1F, 0xFF1F3550 };
    private static final int[] CHIP_FG = { 0xFFAFC0D2, GOOD, BAD, WARN, BLUE_LIGHT };
    private static final int[] LED = { 0xFF7D8996, 0xFF35D25A, 0xFFE0372D, 0xFFE0A030, 0xFF3A9BF5 };

    public static ResourceLocation sprite(String name) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "kit/" + name); }

    private static final ResourceLocation PANEL = sprite("panel"), DRAWER = sprite("drawer"), WELL_S = sprite("well"), SLOT = sprite("slot"), HEADER = sprite("header");
    private static final ResourceLocation TAB = sprite("tab"), TAB_HOVER = sprite("tab_hover"), TAB_SEL = sprite("tab_selected");
    private static final ResourceLocation BTN = sprite("button"), BTN_HOVER = sprite("button_hover"), BTN_OFF = sprite("button_disabled"), BTN_DANGER = sprite("button_danger");

    public static float time() { return (Util.getMillis() % 3_600_000L) / 1000.0F; }

    public static float easeOut(float t) { t = Mth.clamp(t, 0.0F, 1.0F); float u = 1.0F - t; return 1.0F - u * u * u; }

    public static float pulse(float period) { return 0.5F + 0.5F * Mth.sin(time() * Mth.TWO_PI / period); }

    public static final class Smooth {
        private float v = Float.NaN;
        private long last;
        private final float rate;

        public Smooth(float rate) { this.rate = rate; }

        public float to(float target) {
            long now = Util.getMillis();
            if (Float.isNaN(v)) { v = target; last = now; return v; }
            float dt = Math.min(0.25F, (now - last) / 1000.0F);
            last = now;
            v += (target - v) * (1.0F - (float) Math.exp(-dt * rate));
            if (Math.abs(target - v) < 0.0005F) v = target;
            return v;
        }

        public float cycle(float target) {
            if (!Float.isNaN(v) && target < v - 0.25F) v = target;
            return to(target);
        }

        public float value() { return Float.isNaN(v) ? 0.0F : v; }
    }

    private static void ui(net.minecraft.sounds.SoundEvent recorded, float volume, net.minecraft.sounds.SoundEvent vanilla, float vanillaPitch, float vanillaVolume) {
        boolean on = ClientConfig.newSounds();
        Minecraft.getInstance().getSoundManager().play(on ? SimpleSoundInstance.forUI(recorded, 1.0F, volume) : SimpleSoundInstance.forUI(vanilla, vanillaPitch, vanillaVolume));
    }

    public static void click() { ui(WmdSounds.UI_CLICK.get(), 0.8F, SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.6F); }

    public static void tab() { ui(WmdSounds.UI_TAB.get(), 0.8F, SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.6F); }

    public static void confirm() { ui(WmdSounds.UI_CONFIRM.get(), 0.9F, SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 0.8F); }

    public static void deny() { ui(WmdSounds.UI_DENY.get(), 0.8F, SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 0.5F); }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) { g.blitSprite(PANEL, x, y, w, h); }

    public static void drawer(GuiGraphics g, int x, int y, int w, int h) { g.blitSprite(DRAWER, x, y, w, h); }

    public static void well(GuiGraphics g, int x, int y, int w, int h) { g.blitSprite(WELL_S, x - 1, y - 1, w + 2, h + 2); }

    public static void slot(GuiGraphics g, int x, int y, int rim) {
        if (rim != 0) g.fill(x - 2, y - 2, x + 18, y + 18, rim);
        g.blitSprite(SLOT, x - 1, y - 1, 18, 18);
    }

    public static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, DIVIDER);
        g.fill(x, y + 1, x + w, y + 2, 0x30FFFFFF & 0x18FFFFFF);
    }

    public static void card(GuiGraphics g, Font font, int x, int y, int w, Component label, Component value, int valueColour) {
        g.fill(x, y, x + w, y + 21, CARD);
        drawFit(g, font, label, x + 4, y + 2, w - 8, TEXT_MUTED);
        drawFit(g, font, value, x + 4, y + 11, w - 8, valueColour);
    }

    public static int header(GuiGraphics g, Font font, int x, int y, int w, ItemStack icon, Component title, Component chip, int kind, boolean pulse) {
        g.blitSprite(HEADER, x + 3, y + 3, w - 6, 17);
        int tx = x + 8;
        if (icon != null && !icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x + 6, y + 5, 0);
            g.pose().scale(0.75F, 0.75F, 1.0F);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
            tx = x + 21;
        }
        int help = WmdHelp.wantsHeaderButton(x, y) ? WmdHelp.ROOM : 0;
        if (help > 0) WmdHelp.button(g, x + w - 6 - WmdHelp.SIZE, y + 6);
        int cx = x + w - 6 - help;
        if (chip != null) {

            boolean compact = font.width(title) + 4 + Math.min(font.width(chip), 76) + 15 > x + w - 6 - help - tx;
            if (compact) {
                cx = x + w - 6 - help - 11;
                g.fill(cx + 1, y + 6, cx + 10, y + 17, CHIP_BG[kind]);
                g.fill(cx, y + 7, cx + 11, y + 16, CHIP_BG[kind]);
                led(g, cx + 3, y + 9, kind, pulse);
            } else cx = chipRight(g, font, x + w - 6 - help, y + 6, chip, kind, pulse, 90);
        }
        drawFit(g, font, title, tx, y + 7, cx - 4 - tx, TEXT, 0.72F);
        return cx;
    }

    public static int chipRight(GuiGraphics g, Font font, int rx, int y, Component text, int kind, boolean pulse, int maxW) {
        int tw = Math.min(font.width(text), maxW - 14);
        int w = tw + 15, x = rx - w;
        chipAt(g, font, x, y, w, text, kind, pulse);
        return x;
    }

    public static int chip(GuiGraphics g, Font font, int x, int y, Component text, int kind, boolean pulse) {
        int w = font.width(text) + 15;
        chipAt(g, font, x, y, w, text, kind, pulse);
        return w;
    }

    private static void chipAt(GuiGraphics g, Font font, int x, int y, int w, Component text, int kind, boolean pulse) {
        int bg = CHIP_BG[kind];
        g.fill(x + 1, y, x + w - 1, y + 11, bg);
        g.fill(x, y + 1, x + w, y + 10, bg);
        led(g, x + 4, y + 3, kind, pulse);
        drawFit(g, font, text, x + 11, y + 2, w - 14, CHIP_FG[kind]);
    }

    public static void led(GuiGraphics g, int x, int y, int kind, boolean pulse) {
        int c = LED[kind];
        float k = !pulse ? 1.0F : kind == K_BAD ? (pulse(0.8F) > 0.5F ? 1.0F : 0.35F) : 0.55F + 0.45F * pulse(1.6F);
        g.fill(x, y, x + 5, y + 5, 0xFF0A0D11);
        g.fill(x + 1, y + 1, x + 4, y + 4, scale(c, k));
        if (k > 0.8F) g.fill(x - 1, y + 2, x + 6, y + 3, (c & 0x00FFFFFF) | 0x30000000);
    }

    public static void energyGauge(GuiGraphics g, int x, int y, int w, int h, float frac) {
        well(g, x, y, w, h);
        int fill = Math.round(h * Mth.clamp(frac, 0.0F, 1.0F));
        if (frac > 0 && fill == 0) fill = 1;
        for (int py = 0; py < fill; py++) {
            int yy = y + h - 1 - py;
            if (py % 4 == 3) continue;
            float t = py / (float) h;
            g.fill(x + 1, yy, x + w - 1, yy + 1, lerp(0xFF1E7F8E, CYAN, t));
        }
        if (fill > 1) g.fill(x + 1, y + h - fill, x + w - 1, y + h - fill + 1, 0xFFBFF4FA);
        ticks(g, x, y, w, h);
    }

    public static void fluidGauge(GuiGraphics g, int x, int y, int w, int h, Fluid fluid, int colour, float frac, int rim) {
        if (rim != 0) g.fill(x - 2, y - 2, x + w + 2, y + h + 2, rim);
        well(g, x, y, w, h);
        int fill = Math.round(h * Mth.clamp(frac, 0.0F, 1.0F));
        if (frac > 0 && fill == 0) fill = 1;
        if (fill > 0) {
            TextureAtlasSprite sp = stillSprite(fluid);
            g.enableScissor(x, y + h - fill, x + w, y + h);
            colour = displayColour(colour);
            g.setColor(((colour >> 16) & 255) / 255.0F, ((colour >> 8) & 255) / 255.0F, (colour & 255) / 255.0F, 1.0F);
            for (int ty = y + h - 16; ty > y + h - fill - 16; ty -= 16)
                for (int tx = x; tx < x + w; tx += 16) g.blit(tx, ty, 0, 16, 16, sp);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            g.disableScissor();
            g.fill(x, y + h - fill, x + w, y + h - fill + 1, (scale(colour | 0xFF000000, 1.6F) & 0x00FFFFFF) | 0xC0000000);
        }
        ticks(g, x, y, w, h);
    }

    public static int displayColour(int c) {
        int r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255, max = Math.max(r, Math.max(gg, b));
        if (max >= 96) return c;
        if (max == 0) return 0xFF303030;
        float k = 96.0F / max;
        return 0xFF000000 | (Math.min(255, (int) (r * k)) << 16) | (Math.min(255, (int) (gg * k)) << 8) | Math.min(255, (int) (b * k));
    }

    private static TextureAtlasSprite stillSprite(Fluid fluid) {
        ResourceLocation tex = ResourceLocation.withDefaultNamespace("block/water_still");
        try {
            ResourceLocation t = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid).getStillTexture();
            if (t != null) tex = t;
        } catch (RuntimeException ignored) {}
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(tex);
    }

    private static void ticks(GuiGraphics g, int x, int y, int w, int h) {
        for (int k = 1; k < 4; k++) g.fill(x + w - Math.max(3, w / 4), y + h * k / 4, x + w, y + h * k / 4 + 1, 0x40FFFFFF);
    }

    public static void progressBar(GuiGraphics g, int x, int y, int w, int h, float frac, boolean running) {
        well(g, x, y, w, h);
        int fill = Math.round(w * Mth.clamp(frac, 0.0F, 1.0F));
        for (int px = 0; px < fill; px++) g.fill(x + px, y, x + px + 1, y + h, lerp(BLUE, ORANGE, px / (float) Math.max(1, w - 1)));
        if (fill > 0) {
            g.fill(x, y, x + fill, y + 1, 0x40FFFFFF);
            if (running && fill > 6) {
                int band = 10, span = fill + band;
                int sx = x - band + (int) ((time() * 48.0F) % span);
                g.enableScissor(x, y, x + fill, y + h);
                g.fill(sx, y, sx + band, y + h, 0x38FFFFFF);
                g.fill(sx + 3, y, sx + band - 3, y + h, 0x30FFFFFF);
                g.disableScissor();
            }
        }

        int tip = fill >= w ? ORANGE : 0xFF3A4552;
        int my = y + h / 2;
        for (int i = 0; i < 4; i++) g.fill(x + w + 2 + i, my - 3 + i, x + w + 3 + i, my + 4 - i, tip);
    }

    public static void tab(GuiGraphics g, int x, int y, String icon, boolean selected, boolean hover) {
        if (selected) g.blitSprite(TAB_SEL, x, y, 26, 22);
        else g.blitSprite(hover ? TAB_HOVER : TAB, x + 2, y, 21, 22);
        g.blitSprite(sprite(icon), x + (selected ? 6 : 7), y + 5, 12, 12);
    }

    public static final int B_NORMAL = 0, B_DISABLED = 1, B_DANGER = 2;

    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component text, int style, boolean hover) {
        ResourceLocation s = style == B_DISABLED ? BTN_OFF : style == B_DANGER ? BTN_DANGER : hover ? BTN_HOVER : BTN;
        g.blitSprite(s, x, y, w, h);
        int c = style == B_DISABLED ? TEXT_DIM : TEXT;
        int tw = Math.min(font.width(text), w - 6);
        drawFit(g, font, text, x + (w - tw) / 2, y + (h - 8) / 2, w - 6, c);
    }

    public static class KitButton extends net.minecraft.client.gui.components.Button {
        private final boolean danger;

        public KitButton(int x, int y, int w, int h, Component text, OnPress onPress) { this(x, y, w, h, text, onPress, false); }

        public KitButton(int x, int y, int w, int h, Component text, OnPress onPress, boolean danger) {
            super(x, y, w, h, text, onPress, DEFAULT_NARRATION);
            this.danger = danger;
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sm) { if (danger) confirm(); else click(); }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!active && visible && button == 0 && isMouseOver(mx, my)) deny();
            return super.mouseClicked(mx, my, button);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int style = !active ? B_DISABLED : danger ? B_DANGER : B_NORMAL;
            button(g, Minecraft.getInstance().font, getX(), getY(), width, height, getMessage(), style, isHoveredOrFocused() && active);
        }
    }

    public static class TabButton extends net.minecraft.client.gui.components.Button {
        private final String icon;

        public TabButton(int x, int y, String icon, Component name, OnPress onPress) {
            super(x, y, 23, 22, name, onPress, DEFAULT_NARRATION);
            this.icon = icon;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(name));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) { tab(g, getX(), getY(), icon, false, isHoveredOrFocused()); }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sm) { tab(); }
    }

    public static boolean drawFit(GuiGraphics g, Font font, Component text, int x, int y, int maxW, int colour) { return drawFit(g, font, text, x, y, maxW, colour, 0.8F); }

    public static boolean drawFit(GuiGraphics g, Font font, Component text, int x, int y, int maxW, int colour, float minScale) {
        int w = font.width(text);
        if (w <= maxW || maxW <= 0) { g.drawString(font, text, x, y, colour, false); return false; }
        float s = maxW / (float) w;
        if (s >= minScale) {
            g.pose().pushPose();
            g.pose().translate(x, y + (1.0F - s) * 4.0F, 0.0F);
            g.pose().scale(s, s, 1.0F);
            g.drawString(font, text, 0, 0, colour, false);
            g.pose().popPose();
            return false;
        }
        net.minecraft.network.chat.FormattedText cut = font.substrByWidth(text, maxW - font.width("..."));
        g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(net.minecraft.network.chat.FormattedText.composite(cut, net.minecraft.network.chat.FormattedText.of("..."))), x, y, colour, false);
        return true;
    }

    public static boolean over(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    public static int lerp(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static int scale(int c, float k) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * k)), gg = Math.min(255, (int) (((c >> 8) & 255) * k)), b = Math.min(255, (int) ((c & 255) * k));
        return (c & 0xFF000000) | (r << 16) | (gg << 8) | b;
    }
}
