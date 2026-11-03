package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientTsar {

    private ClientTsar() {}

    private static TsarEvent.Flash seen;
    private static boolean boomed, rolled;

    private static double strength(double d) {
        if (d <= Balance.TSAR_FLASH_R) return 1.0;
        return Math.max(0.0, 1.0 - (d - Balance.TSAR_FLASH_R) / (Balance.TSAR_SEEN_R - Balance.TSAR_FLASH_R)) * 0.7;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        TsarEvent.Flash f = TsarEvent.lastFlash;
        if (f != seen) { seen = f; boomed = false; rolled = false; }
        if (f == null || mc.level == null || mc.player == null) return;
        double d = mc.player.position().distanceTo(new Vec3(f.x(), f.y(), f.z())), age = mc.level.getGameTime() - f.t(), arrive = d / Balance.TSAR_SOUND_SPEED;
        if (age > arrive + 400) { TsarEvent.lastFlash = null; return; }
        float k = (float) strength(d);
        if (!boomed && age >= arrive) {
            boomed = true;
            ClientRailgun.flat((d > 1200.0 ? WmdSounds.TS_BOOM_FAR : WmdSounds.TS_BOOM).get(), Math.max(0.15F, k), 1.0F);
            T4Fx.shakeNear(new Vec3(f.x(), f.y(), f.z()), Balance.TSAR_SEEN_R, 3.0F * Math.max(0.2F, k));
        }
        if (!rolled && age >= arrive + 40) { rolled = true; ClientRailgun.flat(WmdSounds.TS_ROLL.get(), Math.max(0.1F, k * 0.9F), 1.0F); }
    }

    @SubscribeEvent
    static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        TsarEvent.Flash f = TsarEvent.lastFlash;
        if (f == null || mc.level == null || mc.player == null) return;
        double age = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false) - f.t();
        if (age < 0.0 || age > 60.0) return;
        double k = strength(mc.player.position().distanceTo(new Vec3(f.x(), f.y(), f.z()))) * Math.pow(1.0 - age / 60.0, 1.5);
        if (k <= 0.01) return;
        GuiGraphics g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (k * 255.0) << 24) | 0xFFF8EC);
    }
}
