package com.ashraf.wmd;

import net.minecraft.client.Minecraft;

public final class ClientCamera {

    private ClientCamera() {}

    public static void open(CameraShellPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new AreaViewScreen(p.x(), p.z(), AreaView.MODE_CAM, Balance.CAMERA_VIEW));
    }
}
