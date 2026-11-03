package com.ashraf.wmd;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class FlashBlindHud {

    private FlashBlindHud() {}

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "flash_blind");

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(ID, FlashBlindHud::render); }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { FlashBlindView.clear(); }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        float a = FlashBlindView.alpha(net.minecraft.Util.getMillis());
        if (a <= 0.003F || Minecraft.getInstance().player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, ((int) (a * 255.0F) << 24) | 0xFFFFFF);

        float b = Math.min(1.0F, a * 1.6F) * 0.35F;
        int r = Math.min(w, h) / 3;
        g.fill(w / 2 - r, h / 2 - r / 2, w / 2 + r, h / 2 + r / 2, ((int) (b * 255.0F) << 24) | 0xFFE8B0);
    }
}
