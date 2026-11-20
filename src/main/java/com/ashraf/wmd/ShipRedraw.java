package com.ashraf.wmd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ShipRedraw {

    private ShipRedraw() {}

    static final int CHECK = 10;
    static final int[] REDRAW_AT = { 20, 60, 200 };

    static final int SETTLE = 30;

    static final int MIN_GAP = 100;

    static final int PER_CHECK = 1;

    static final double HYST = 16.0;

    private static final class Seen {
        final long first;
        long sig;
        long dirtySince = -1L;
        String why = "";
        long last = Long.MIN_VALUE / 2;
        int band;

        Seen(long first, long sig, int band) { this.first = first; this.sig = sig; this.band = band; }
    }

    private static final Map<UUID, Seen> SEEN = Statics.client(new HashMap<>());

    private static volatile boolean off;

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Level lv = mc.level;
        if (lv == null || off || !SableBridge.active() || !ClientConfig.shipRedraw()) return;
        long now = lv.getGameTime();
        if (now % CHECK != 0) return;
        try {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            double view = mc.options.getEffectiveRenderDistance() * 16.0;
            List<UUID> ids = SableClient.subLevels(lv);
            SEEN.keySet().retainAll(new HashSet<>(ids));
            int budget = PER_CHECK;
            for (UUID id : ids) {
                long sig = SableClient.signature(lv, id);
                double d = Math.sqrt(SableClient.distSq(lv, id, cam.x, cam.y, cam.z));
                Seen s = SEEN.get(id);
                if (s == null || now < s.first) { SEEN.put(id, new Seen(now, sig, band(-1, d, view))); continue; }
                long age = now - s.first;
                String why = null;
                for (int a : REDRAW_AT) if (age >= a && age < a + CHECK) why = "new";
                if (sig != s.sig) { s.sig = sig; s.dirtySince = now; s.why = "reload"; }
                int b = band(s.band, d, view);
                if (b != s.band) {
                    if (s.band >= 0 && s.dirtySince < 0) { s.dirtySince = now; s.why = b == 0 ? "view-in" : "view-out"; }
                    s.band = b;
                }
                if (why == null && s.dirtySince >= 0 && now - s.dirtySince >= SETTLE && now - s.last >= MIN_GAP) {
                    if (budget <= 0) continue;
                    budget--;
                    why = s.why;
                }
                if (why == null) continue;
                SableClient.redraw(lv, id);
                s.dirtySince = -1L;
                s.last = now;
                Wmd.LOGGER.info("[ship-redraw] craft {} {} (seen {} s ago, {} blocks away, view {})", shortId(id), why, age / 20,
                        Double.isNaN(d) ? "?" : Long.toString(Math.round(d)), Math.round(view));
            }
        } catch (Throwable t) {
            off(t);
        }
    }

    static int band(int prev, double d, double view) {
        if (Double.isNaN(d)) return prev;
        if (d < view - HYST) return 0;
        if (d > view + HYST) return 1;
        return prev >= 0 ? prev : (d <= view ? 0 : 1);
    }

    @SubscribeEvent
    static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmdredraw").executes(c -> redrawAll(c.getSource())));
    }

    static int redrawAll(CommandSourceStack src) {
        Level lv = Minecraft.getInstance().level;
        if (lv == null || !SableBridge.active()) { src.sendFailure(Component.literal("No ships to redraw: Sable is not active.")); return 0; }
        if (off) { src.sendFailure(Component.literal("Ship redraw is switched off after an error this session (see the log).")); return 0; }
        try {
            int n = 0;
            long now = lv.getGameTime();
            for (UUID id : SableClient.subLevels(lv)) {
                if (!SableClient.redraw(lv, id)) continue;
                n++;
                Seen s = SEEN.get(id);
                if (s != null) { s.dirtySince = -1L; s.last = now; }
            }
            final int count = n;
            Wmd.LOGGER.info("[ship-redraw] /wmdredraw: {} craft(s)", count);
            src.sendSuccess(() -> Component.literal("Redrew " + count + (count == 1 ? " ship." : " ships.")), false);
            return count;
        } catch (Throwable t) {
            off(t);
            src.sendFailure(Component.literal("Ship redraw failed and is switched off for this session (see the log)."));
            return 0;
        }
    }

    private static void off(Throwable t) {
        if (off) return;
        off = true;
        Wmd.LOGGER.error("Ship redraw switched off for this session after an error (the Sable bridge and everything else carries on)", t);
    }

    private static String shortId(UUID id) {
        String s = id.toString();
        return s.substring(0, Math.min(8, s.length()));
    }
}
