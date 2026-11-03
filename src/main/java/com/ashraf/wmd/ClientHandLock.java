package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientHandLock {

    private ClientHandLock() {}

    private static HandLockPayload state;
    private static long at;

    public static void onState(HandLockPayload p) {
        state = p;
        Minecraft mc = Minecraft.getInstance();
        at = mc.level == null ? 0 : mc.level.getGameTime();
    }

    private static boolean aiming(Player p) { return p != null && p.isUsingItem() && p.getUseItem().getItem() instanceof HandLauncherItem; }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !aiming(mc.player)) { state = null; return; }
        if (state == null || state.target() == 0 || mc.isPaused()) return;
        long t = mc.level.getGameTime();
        if (state.locked()) {
            if (t % 3 == 0) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.35F, 1.9F);
        } else {
            float k = state.ticks() / (float) Balance.HAND_LOCK_TICKS;
            int every = Math.max(3, 9 - (int) (k * 6));
            if (t % every == 0) mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 0.6F + 0.8F * k);
        }
    }

    @SubscribeEvent
    static void onHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !aiming(mc.player) || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2, cy = h / 2;
        boolean has = state != null && state.target() != 0 && mc.level.getGameTime() - at < 10;
        boolean locked = has && state.locked();
        float k = has ? state.ticks() / (float) Balance.HAND_LOCK_TICKS : 0.0F;
        int r = (int) (30 - 14 * k), col = locked ? WmdUi.ORANGE : has ? WmdUi.BLUE_LIGHT : WmdUi.TEXT_MUTED, L = 7;

        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
            int x = cx + sx * r, y = cy + sy * r;
            g.fill(Math.min(x, x - sx * L), y, Math.max(x, x - sx * L) + 1, y + 1, col);
            g.fill(x, Math.min(y, y - sy * L), x + 1, Math.max(y, y - sy * L) + 1, col);
        }
        Component line;
        if (!has) line = Component.translatable("gui.wmd.hand.searching");
        else if (locked) line = Component.translatable("gui.wmd.hand.locked", Component.translatable("gui.wmd.hand.type." + state.target()), state.dist());
        else line = Component.translatable("gui.wmd.hand.tracking", Component.translatable("gui.wmd.hand.type." + state.target()), state.dist());
        int lw = mc.font.width(line), y0 = cy + 40;
        g.fill(cx - lw / 2 - 6, y0 - 3, cx + lw / 2 + 6, y0 + 15, 0xB80F1318);
        g.fill(cx - lw / 2 - 6, y0 - 3, cx + lw / 2 + 6, y0 - 2, locked ? WmdUi.ORANGE : WmdUi.BLUE);
        g.drawString(mc.font, line, cx - lw / 2, y0, locked ? WmdUi.ORANGE : WmdUi.TEXT, false);
        if (has && !locked) {
            g.fill(cx - lw / 2, y0 + 11, cx + lw / 2, y0 + 13, 0xFF26303B);
            g.fill(cx - lw / 2, y0 + 11, cx - lw / 2 + (int) (lw * k), y0 + 13, WmdUi.BLUE_LIGHT);
        }
    }
}
