package com.ashraf.wmd;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

final class ClientPallets {

    private ClientPallets() {}

    static final int FORGET = 40;

    record Far(Vec3 pos, boolean chute, boolean heavy, long at) {}

    static final Map<Integer, Far> FAR = new ConcurrentHashMap<>();

    static void update(PalletFarPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        FAR.put(p.id(), new Far(new Vec3(p.x(), p.y(), p.z()), p.chute(), p.heavy(), mc.level.getGameTime()));
        FAR.entrySet().removeIf(e -> mc.level.getGameTime() - e.getValue().at() > FORGET);
    }

    static Map<Integer, Far> live(long now) {
        FAR.entrySet().removeIf(e -> now - e.getValue().at() > FORGET);
        return FAR;
    }
}
