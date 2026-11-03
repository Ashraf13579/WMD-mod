package com.ashraf.wmd;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientEva {

    private ClientEva() {}

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "eva_hud");
    private static boolean sent;
    private static boolean firing;
    private static Breath breath;
    private static Hum hum;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean fire = false;
        if (p != null && Orbit.isOrbit(p.level()) && !p.isSpectator() && !p.getAbilities().flying && p.getVehicle() == null && !p.onGround()
                && EvaSuitItem.fullSuit(p) && EvaSuitItem.oxygen(EvaSuitItem.torso(p)) > 0 && mc.screen == null) {
            Vec3 v = p.getDeltaMovement();
            if (mc.options.keyJump.isDown() && v.y < Balance.EVA_MAX_UP) { v = v.add(0.0, Balance.EVA_THRUST_UP, 0.0); fire = true; }
            if (mc.options.keySprint.isDown() && mc.options.keyUp.isDown()) {
                Vec3 look = p.getLookAngle();
                Vec3 nv = v.add(look.scale(Balance.EVA_THRUST_FWD));
                if (nv.length() <= Balance.EVA_MAX_SPEED || nv.length() < v.length()) v = nv;
                fire = true;
            }
            if (mc.options.keyShift.isDown()) { v = v.scale(Balance.EVA_BRAKE).add(0.0, -0.01, 0.0); fire = true; }
            if (fire) { p.setDeltaMovement(v); p.hurtMarked = true; p.resetFallDistance(); }
        }
        firing = fire;
        if (fire != sent && p != null) { sent = fire; PacketDistributor.sendToServer(new EvaThrustPayload(fire)); }
        if (breathing(p) && (breath == null || !mc.getSoundManager().isActive(breath))) { breath = new Breath(); mc.getSoundManager().play(breath); }
        if (cabin(p) && (hum == null || !mc.getSoundManager().isActive(hum))) { hum = new Hum(); mc.getSoundManager().play(hum); }
    }

    static boolean breathing(LocalPlayer p) {
        return p != null && Orbit.isOrbit(p.level()) && !p.isSpectator() && p.getVehicle() == null && !StationCabinPayload.cabinNow
                && EvaSuitItem.fullSuit(p) && EvaSuitItem.oxygen(EvaSuitItem.torso(p)) > 0;
    }

    static boolean cabin(LocalPlayer p) { return p != null && Orbit.isOrbit(p.level()) && StationCabinPayload.cabinNow; }

    private static final class Hum extends AbstractTickableSoundInstance {
        private float fade;

        Hum() {
            super(WmdSounds.ST_HAB_HUM_LOOP.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            volume = 0.01F;
        }

        @Override
        public void tick() {
            boolean on = cabin(Minecraft.getInstance().player);
            fade = Math.max(0.0F, Math.min(1.0F, fade + (on ? 0.025F : -0.025F)));
            if (!on && fade <= 0.0F) { stop(); return; }
            volume = Math.max(0.01F, 0.5F * fade);
        }
    }

    private static final class Breath extends AbstractTickableSoundInstance {
        private float fade;

        Breath() {
            super(WmdSounds.OR_EVA_BREATH_LOOP.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            volume = 0.01F;
        }

        @Override
        public void tick() {
            boolean on = breathing(Minecraft.getInstance().player);
            fade = Math.max(0.0F, Math.min(1.0F, fade + (on ? 0.05F : -0.05F)));
            if (!on && fade <= 0.0F) { stop(); return; }
            volume = Math.max(0.01F, 0.45F * fade);
        }
    }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(ID, ClientEva::hud); }

    private static void hud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !Orbit.isOrbit(p.level())) return;
        var torso = EvaSuitItem.torso(p);
        boolean suit = EvaSuitItem.fullSuit(p);
        Font f = mc.font;
        int x = 6, y = g.guiHeight() / 2 - 26, w = 104, h = 40;
        g.fill(x, y, x + w, y + h, 0xCC101820);
        g.fill(x, y, x + w, y + 1, 0xFF3D8BFF);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF22303E);
        g.drawString(f, Component.translatable("hud.wmd.eva.title"), x + 5, y + 4, 0xFFBFD4EA, false);
        if (StationCabinPayload.cabinNow) {
            String s = Component.translatable("hud.wmd.eva.cabin").getString();
            g.drawString(f, s, x + w - 5 - f.width(s), y + 4, 0xFF7FFFB0, false);
        }
        if (!suit || torso.isEmpty()) {
            boolean blink = (p.tickCount / 6) % 2 == 0;
            if (StationCabinPayload.cabinNow) { g.drawString(f, Component.translatable("hud.wmd.eva.cabin_air"), x + 5, y + 18, 0xFF7FFFB0, false); return; }
            g.drawString(f, Component.translatable("hud.wmd.eva.vacuum"), x + 5, y + 18, blink ? 0xFFFF4040 : 0xFF802020, false);
            return;
        }
        int o2 = EvaSuitItem.oxygen(torso);
        float k = o2 / (float) Balance.O2_FULL;
        int col = o2 < 1_200 ? ((p.tickCount / 5) % 2 == 0 ? 0xFFFF4040 : 0xFF902020) : o2 < 6_000 ? 0xFFFF9A3C : 0xFF4AA3FF;
        int bx = x + 5, by = y + 16, bw = w - 10;
        g.fill(bx, by, bx + bw, by + 7, 0xFF0A1016);
        g.fill(bx + 1, by + 1, bx + 1 + Math.round((bw - 2) * k), by + 6, col);
        g.drawString(f, "O2 " + EvaSuitItem.clock(o2), x + 5, y + 27, col, false);
        int tx = x + w - 5;
        if (firing) { String s = Component.translatable("hud.wmd.eva.thrust").getString(); tx -= f.width(s); g.drawString(f, s, tx, y + 27, 0xFF7FE0FF, false); tx -= 4; }
        if (EvaSuitItem.suitBoots(p) && p.onGround() && !StationCabinPayload.cabinNow) { String s = Component.translatable("hud.wmd.eva.mag").getString(); tx -= f.width(s); g.drawString(f, s, tx, y + 4, 0xFF7FFFB0, false); }
    }
}
