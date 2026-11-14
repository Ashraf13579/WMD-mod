package com.ashraf.wmd;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientSeaPilot {

    private ClientSeaPilot() {}

    static final int QUICK_TICKS = 12;
    static final KeyMapping AUTOPILOT = new KeyMapping("key.wmd.helm.autopilot", ClientHelm.IN_HELM, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, "key.categories.wmd_helm");
    private static int held = -1;

    static {
        ClientSeaPilotBridge.onStateHook = p -> Minecraft.getInstance().execute(() -> onState(p));
    }

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Keys {
        private Keys() {}

        @SubscribeEvent
        static void register(RegisterKeyMappingsEvent event) { if (!AirGate.AVAILABLE) return; event.register(AUTOPILOT); }
    }

    private static void onState(SeaPilotPayloads.State p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof ChartScreen s && s.src().equals(p.pos())) { s.onState(); return; }
        if (p.open() && mc.screen == null) mc.setScreen(new ChartScreen(p.pos(), p.pos().equals(ClientHelm.seatedHelm())));
    }

    private static boolean down() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return false;
        InputConstants.Key k = AUTOPILOT.getKey();
        long win = mc.getWindow().getWindow();
        if (k.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(win, k.getValue()) == GLFW.GLFW_PRESS;
        return k.getValue() >= 0 && InputConstants.isKeyDown(win, k.getValue());
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        BlockPos helm = ClientHelm.seatedHelm();
        if (helm == null || !AirGate.AVAILABLE) { held = -1; return; }
        if (down()) {
            if (held >= 0) held++;
            else held = 0;
            if (held == QUICK_TICKS) PacketDistributor.sendToServer(new SeaPilotPayloads.Ask(helm, SeaPilotPayloads.QUICK, new CompoundTag()));
        } else {
            if (held >= 0 && held < QUICK_TICKS) {
                CompoundTag t = new CompoundTag();
                t.putBoolean("open", true);
                PacketDistributor.sendToServer(new SeaPilotPayloads.Ask(helm, SeaPilotPayloads.OPEN, t));
            }
            held = -1;
        }
    }

    static void renderHud(GuiGraphics g, Font font, float slide) {
        BlockPos helm = ClientHelm.seatedHelm();
        SeaPilotPayloads.Hud h = helm == null ? null : ClientSeaPilotBridge.hud(helm);
        if (h == null) return;
        boolean on = h.mode() != SeaPilot.OFF;
        Component l1, l2;
        if (on) {
            String m = SeaPilot.MODES[Math.max(0, Math.min(3, h.mode()))];
            Component spd = Component.translatable("gui.wmd.sp.speed" + Math.max(0, Math.min(3, h.speed())));
            String dep = h.depth() >= 0.0F ? " · D " + Math.round(h.depth()) : "";
            if (h.mode() == SeaPilot.ROUTE)
                l1 = Component.translatable("hud.wmd.sp.route", h.leg() + 1, h.legs(), dist(h.dist()), spd).append(dep);
            else if (h.mode() == SeaPilot.STATION)
                l1 = h.dist() > 0 && h.dist() <= 32 ? Component.translatable("hud.wmd.sp.on_station").append(dep) : Component.translatable("hud.wmd.sp.station", dist(h.dist())).append(dep);
            else l1 = Component.translatable("hud.wmd.sp.hold", String.format("%03d", Math.round(h.course()) % 360), spd).append(dep);
            l1 = Component.translatable("hud.wmd.sp.ap", Component.translatable("gui.wmd.sp.tab_" + m)).append(" · ").append(l1);
            String keel = Float.isNaN(h.clearance()) ? "?" : (h.clearance() >= 0 ? "+" : "") + Math.round(h.clearance());
            l2 = Component.translatable("hud.wmd.sp.keel", keel);
            if (h.crushPct() >= 0) l2 = l2.copy().append("   ").append(Component.translatable("hud.wmd.sp.crush", h.crushPct()));
            if (h.capped()) l2 = l2.copy().append("   ").append(Component.translatable("hud.wmd.sp.capped"));
        } else {
            l1 = Component.translatable("hud.wmd.sp.off", Component.translatable("hud.wmd.sp.why_" + SeaPilot.REASONS[Math.max(0, Math.min(SeaPilot.REASONS.length - 1, h.reason()))]));
            l2 = Component.translatable("hud.wmd.sp.retake");
        }
        int w = Math.max(font.width(l1), font.width(l2)) + 14, x = g.guiWidth() / 2 - w / 2;
        int y = 44 - Math.round((1.0F - WmdUi.easeOut(slide)) * 60);
        g.fill(x, y, x + w, y + 24, 0xC0101418);
        g.fill(x, y, x + w, y + 1, on ? WmdUi.GOOD : WmdUi.WARN);
        boolean warn = on && (h.capped() || h.crushPct() >= Math.round(Balance.SP_CRUSH_WARN * 100) || (!Float.isNaN(h.clearance()) && h.clearance() < Balance.SP_MARGIN_SHIP));
        g.drawString(font, l1, x + 7, y + 4, on ? WmdUi.GOOD : (WmdUi.pulse(0.6F) > 0.5F ? WmdUi.WARN : WmdUi.ORANGE_LIGHT), false);
        g.drawString(font, l2, x + 7, y + 14, warn ? WmdUi.WARN : WmdUi.TEXT_MUTED, false);
    }

    static String dist(float d) { return d < 0 ? "-" : d >= 1000 ? String.format("%.1fk", d / 1000.0F) : Integer.toString(Math.round(d)); }
}
