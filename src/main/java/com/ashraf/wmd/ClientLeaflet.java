package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class ClientLeaflet {

    private ClientLeaflet() {}

    static void show(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTitle(Component.translatable("message.wmd.leaflet.title"));
        mc.gui.setSubtitle(Component.literal(msg.length() > 60 ? msg.substring(0, 57) + "..." : msg));
    }
}
