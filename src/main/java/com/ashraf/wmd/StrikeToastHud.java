package com.ashraf.wmd;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class StrikeToastHud {

    private StrikeToastHud() {}

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "strike_toast");
    private static final String P = "hud.wmd.strike.";
    private static final long SLIDE_MS = 250L;
    private static final int W = 176;

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(ID, StrikeToastHud::render); }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { StrikeToastView.clear(); }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        StrikeToastPayload c = StrikeToastView.live();
        if (c == null || mc.player == null || !ClientConfig.strikeToast()) return;
        if (StrikeToastView.fresh) {
            StrikeToastView.fresh = false;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F, 1.0F));
        }
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        Font font = mc.font;
        int total = c.hits() + c.intercepted();
        int kind = c.intercepted() == 0 ? WmdUi.K_GOOD : c.hits() == 0 ? WmdUi.K_BAD : WmdUi.K_WARN;
        Component title = Component.translatable(P + "title." + c.kind());
        Component l1 = Component.translatable(P + "hits", c.hits(), total);
        Component l2 = Component.translatable(P + "intercepted", c.intercepted());
        Component l3 = c.hasPos() ? Component.translatable(P + "at", c.x(), c.z()) : null;
        Component l4 = c.bda() < 0 ? null : c.bda() > 0 ? Component.translatable(P + "bda", c.bda()) : Component.translatable(P + "no_bda");
        Component l0 = Component.translatable(P + "what." + c.kind(), c.missiles(), c.rounds());
        int h = 56 + (l3 != null ? 10 : 0) + (l4 != null ? 10 : 0);
        int sw = g.guiWidth(), sh = g.guiHeight();
        int w = Math.min(W, sw - 8);

        long age = StrikeToastView.age();
        float in = WmdUi.easeOut(age / (float) SLIDE_MS), out = WmdUi.easeOut((StrikeToastView.SHOW_MS - age) / (float) SLIDE_MS);
        float k = Math.min(in, out);
        int x = sw - 4 - Math.round(w * k), y = Math.max(4, sh / 2 - 70);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        card(g, x, y, w, h, kind);
        WmdUi.chip(g, font, x + 5, y + 5, title, kind, false);
        int ty = y + 21;
        WmdUi.drawFit(g, font, l0, x + 7, ty, w - 12, WmdUi.TEXT_MUTED);
        ty += 11;
        WmdUi.drawFit(g, font, l1, x + 7, ty, w - 12, c.hits() > 0 ? WmdUi.GOOD : WmdUi.TEXT_MUTED);
        ty += 10;
        WmdUi.drawFit(g, font, l2, x + 7, ty, w - 12, c.intercepted() > 0 ? WmdUi.BAD : WmdUi.TEXT_DIM);
        ty += 10;
        if (l3 != null) { WmdUi.drawFit(g, font, l3, x + 7, ty, w - 12, WmdUi.TEXT); ty += 10; }
        if (l4 != null) WmdUi.drawFit(g, font, l4, x + 7, ty, w - 12, c.bda() > 0 ? WmdUi.BLUE_LIGHT : WmdUi.TEXT_DIM);

        float left = 1.0F - Math.min(1.0F, age / (float) StrikeToastView.SHOW_MS);
        g.fill(x + 1, y + h - 2, x + 1 + Math.round((w - 2) * left), y + h - 1, WmdUi.BLUE);
        g.pose().popPose();
    }

    private static void card(GuiGraphics g, int x, int y, int w, int h, int kind) {
        int top = kind == WmdUi.K_GOOD ? WmdUi.GOOD : kind == WmdUi.K_BAD ? WmdUi.BAD : WmdUi.WARN;
        g.fill(x, y, x + w, y + h, 0xE00F1318);
        g.fill(x, y, x + w, y + 1, top);
        g.fill(x, y + h - 1, x + w, y + h, 0x803A4552);
        g.fill(x, y + 1, x + 1, y + h - 1, 0x803A4552);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, 0x803A4552);
    }
}
