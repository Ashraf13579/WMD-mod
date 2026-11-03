package com.ashraf.wmd;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientShakeScale {

    private ClientShakeScale() {}

    private static float yaw, pitch, roll;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void first(ViewportEvent.ComputeCameraAngles event) {
        yaw = event.getYaw();
        pitch = event.getPitch();
        roll = event.getRoll();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void last(ViewportEvent.ComputeCameraAngles event) {
        float k = ClientConfig.cameraShake();
        if (k >= 0.999F) return;
        event.setYaw(yaw + (event.getYaw() - yaw) * k);
        event.setPitch(pitch + (event.getPitch() - pitch) * k);
        event.setRoll(roll + (event.getRoll() - roll) * k);
    }
}
