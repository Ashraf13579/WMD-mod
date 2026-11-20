package com.ashraf.wmd;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientWarZone {

    private ClientWarZone() {}

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "war_zone");
    private static byte state;

    static void onPayload(WarZonePayload p) { state = p.state(); }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(ID, ClientWarZone::render); }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { state = 0; }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (state == 0 || mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        Component line = Component.translatable(state == 1 ? "hud.wmd.war.open" : "hud.wmd.war.closed");
        int w = mc.font.width(line), x = (g.guiWidth() - w) / 2, y = 22;
        g.fill(x - 4, y - 2, x + w + 4, y + 10, 0x90000000);
        g.drawString(mc.font, line, x, y, state == 1 ? 0xFFFF5040 : 0xFFB0B8C0, false);
    }
}
