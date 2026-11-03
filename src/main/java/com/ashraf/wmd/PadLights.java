package com.ashraf.wmd;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class PadLights {

    private PadLights() {}

    private static final float BRIGHT = 0.35F, RADIUS = 0.55F;

    private static final int PER_PAD = 14, TOTAL = 40;
    private static long budgetTick = -1;
    private static int budgetUsed;

    private record Lamp(String kind, float x, float y, float z, float r, float g, float b, float radius, float brightness) {}

    private static final Map<Integer, List<Lamp>> CACHE = new HashMap<>();
    private static final Map<BlockPos, Long> LAST = new HashMap<>();

    @SubscribeEvent
    static void onReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> CACHE.clear());
    }

    private static List<Lamp> lamps(int tier) {
        return CACHE.computeIfAbsent(tier, t -> {
            List<Lamp> out = new ArrayList<>();
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mesh/pad_t" + t + ".lights.json");
            try {
                Resource res = Minecraft.getInstance().getResourceManager().getResource(file).orElse(null);
                if (res == null) return out;
                try (Reader r = new InputStreamReader(res.open(), java.nio.charset.StandardCharsets.UTF_8)) {
                    JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
                    for (JsonElement e : arr) {
                        var o = e.getAsJsonObject();
                        out.add(new Lamp(o.get("kind").getAsString(), o.get("x").getAsFloat(), o.get("y").getAsFloat(), o.get("z").getAsFloat(),
                                o.get("r").getAsFloat(), o.get("g").getAsFloat(), o.get("b").getAsFloat(), o.get("radius").getAsFloat(), o.get("b_").getAsFloat()));
                    }
                }
            } catch (Exception ex) {
                Wmd.LOGGER.error("Pad lights {} could not be read", file, ex);
            }
            return out;
        });
    }

    private static float night(ClientLevel level) {
        long t = level.getDayTime() % 24000L;
        if (t >= 13000 && t <= 23000) return 1.0F;
        if (t > 12000 && t < 13000) return (t - 12000) / 1000.0F;
        if (t > 23000) return (24000 - t) / 1000.0F;
        return 0.0F;
    }

    public static void update(LaunchTerminalBlockEntity be, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !VeilBridge.active()) return;
        long now = level.getGameTime();
        BlockPos pos = be.getBlockPos();
        Long last = LAST.get(pos);
        if (last != null && last == now) return;
        LAST.put(pos, now);
        if (LAST.size() > 64) LAST.keySet().removeIf(p -> p.distSqr(pos) > 100000);
        List<Lamp> list = lamps(be.getTier());
        if (list.isEmpty()) return;
        float night = night(level);
        float hatch = be.hatchOpen(partialTick);
        boolean busy = hatch > 0.05F || be.hoseConnected();
        float on = Math.max(night, busy ? 0.85F : 0.0F);
        float blink = (now / 12) % 2 == 0 ? 1.0F : 0.3F;
        float theta = (float) Math.toRadians(180.0F - be.getFacing().toYRot());
        double ox = pos.getX() + 0.5, oy = pos.getY(), oz = pos.getZ() + 0.5;
        if (budgetTick != now) { budgetTick = now; budgetUsed = 0; }
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        List<Integer> order = new ArrayList<>(list.size());
        for (int j = 0; j < list.size(); j++) order.add(j);
        final List<Lamp> all = list;
        final double fox = ox, foy = oy, foz = oz;
        final float fth = theta;
        order.sort((a, b2) -> Double.compare(dist2(all.get(a), fox, foy, foz, fth, cam), dist2(all.get(b2), fox, foy, foz, fth, cam)));
        int submitted = 0;
        for (int oi = 0; oi < order.size(); oi++) {
            int i = order.get(oi);
            Lamp l = list.get(i);
            if (submitted >= PER_PAD || budgetUsed >= TOTAL) break;
            float k;
            switch (l.kind()) {
                case "flood", "work" -> k = on;
                case "beacon" -> k = (0.4F + 0.6F * night) * blink;
                case "shaft" -> k = Mth.clamp(hatch * 1.5F, 0.0F, 1.0F);
                default -> k = 0.7F;
            }
            if (k > 0.04F) {
                submitted++; budgetUsed++;
                Vector3f p = new Vector3f(l.x(), l.y(), l.z()).rotateY(theta);
                long key = 0x5D00_0000_0000_0000L ^ (pos.asLong() * 131L + i);
                VeilBridge.machineGlow(key, ox + p.x, oy + p.y, oz + p.z, l.r(), l.g(), l.b(), l.radius() * RADIUS, l.brightness() * k * BRIGHT, "flood".equals(l.kind()) ? 0.06F : 0.0F, now);
            }
        }
    }

    private static double dist2(Lamp l, double ox, double oy, double oz, float theta, net.minecraft.world.phys.Vec3 cam) {
        Vector3f p = new Vector3f(l.x(), l.y(), l.z()).rotateY(theta);
        double dx = ox + p.x - cam.x, dy = oy + p.y - cam.y, dz = oz + p.z - cam.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
