package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class LaunchShake {

    private static final double REACH = 64.0;
    private static final float MAX_DEGREES = 1.4F;

    private LaunchShake() {}

    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Vec3 cam = event.getCamera().getPosition();
        float strength = ImpactFx.shake(cam);
        for (double[] l : LaunchTerminalBlockEntity.CLIENT_LAUNCHES.values()) {
            double d = cam.distanceTo(new Vec3(l[0], l[1], l[2]));
            if (d > REACH) continue;
            double t = l[3];

            float time = (float) (t < 20 ? t / 20.0 : (t < 90 ? 1.0 : Math.max(0.0, 1.0 - (t - 90) / 110.0)));
            float s = (float) (1.0 - d / REACH) * time;
            strength = Math.max(strength, Math.min(1.0F, s * s * 1.6F + s * 0.4F));
        }
        if (strength <= 0.0F || Minecraft.getInstance().level == null) return;
        strength = Math.min(strength, 3.0F);
        double now = Minecraft.getInstance().level.getGameTime() + event.getPartialTick();
        float a = strength * MAX_DEGREES;
        event.setPitch(event.getPitch() + a * (float) (Math.sin(now * 2.9) * 0.6 + Math.sin(now * 7.3) * 0.4));
        event.setYaw(event.getYaw() + a * (float) (Math.sin(now * 3.7 + 1.0) * 0.5 + Math.sin(now * 6.1) * 0.3));
        event.setRoll(event.getRoll() + a * 0.6F * (float) Math.sin(now * 5.3 + 2.0));
    }
}
