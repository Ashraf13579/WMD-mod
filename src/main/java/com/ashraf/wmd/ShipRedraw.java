package com.ashraf.wmd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ShipRedraw {

    private ShipRedraw() {}

    static final int CHECK = 10;
    static final int[] REDRAW_AT = { 20, 60, 200 };

    private static final Map<UUID, Long> SEEN = Statics.client(new HashMap<>());

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Level lv = Minecraft.getInstance().level;
        if (lv == null || !SableBridge.active()) return;
        long now = lv.getGameTime();
        if (now % CHECK != 0) return;
        try {
            List<UUID> ids = SableClient.subLevels(lv);
            SEEN.keySet().retainAll(new HashSet<>(ids));
            for (UUID id : ids) {
                Long t = SEEN.get(id);
                if (t == null || now < t) { SEEN.put(id, now); continue; }
                long age = now - t;
                for (int a : REDRAW_AT) if (age >= a && age < a + CHECK) SableClient.redraw(lv, id);
            }
        } catch (Throwable t) {
            SableBridge.fail(t);
        }
    }
}
