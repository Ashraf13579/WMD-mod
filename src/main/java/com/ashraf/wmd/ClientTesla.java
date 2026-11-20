package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientTesla {

    private ClientTesla() {}

    private static final ByteBufferBuilder GLOW = new ByteBufferBuilder(1 << 16);

    private record Strike(List<Vec3> chain, int water, long start, long seed) {}
    private record Stream(Vec3 c, double r, long seen, boolean strong) {}

    private static final List<Strike> STRIKES = Statics.client(new ArrayList<>());
    private static final Map<Long, Stream> STREAMS = Statics.client(new HashMap<>());

    static void strike(TeslaStrikePayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || p.chain().size() < 2) return;
        if (STRIKES.size() > 16) STRIKES.remove(0);
        STRIKES.add(new Strike(p.chain(), p.water(), level.getGameTime(), level.random.nextLong()));
    }

    static void streamers(BlockPos pos, Vec3 toroid, double r, long now, boolean strong) {
        if (STREAMS.size() > 64) STREAMS.clear();
        STREAMS.put(pos.asLong(), new Stream(toroid, r, now, strong));
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (STRIKES.isEmpty() && STREAMS.isEmpty())) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long tick = level.getGameTime();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f lv = camera.getLeftVector(), uv = camera.getUpVector();
        BufferBuilder bb = new BufferBuilder(GLOW, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Iterator<Strike> it = STRIKES.iterator(); it.hasNext();) {
            Strike s = it.next();
            double age = now - s.start();
            if (age > Balance.TT_ARC_TICKS) { it.remove(); continue; }
            float al = (float) (1.0 - age / Balance.TT_ARC_TICKS);
            RandomSource r = RandomSource.create(s.seed() + tick);
            for (int i = 0; i + 1 < s.chain().size(); i++) {
                Vec3 a = s.chain().get(i), b = s.chain().get(i + 1);
                double w = 0.09 * (1.0 - 0.12 * i);
                bolt(bb, cam, a, b, r, w, al, 10);
                bolt(bb, cam, a, b, r, w * 0.5, al * 0.6F, 8);
                ClientBlastGeo.disc(bb, cam, lv, uv, b.x, b.y, b.z, 0.6 + 0.4 * al, 0.8F, 0.9F, 1.0F, 0.8F * al, 12);
                if (age < 2.0) VeilBridge.glow(0x5445534CL ^ s.seed() ^ i, b.x, b.y, b.z, 0.6F, 0.8F, 1.0F, 10.0F, 2.5F * al, tick);
                if ((s.water() >> i & 1) != 0)
                    for (int k = 0; k < 4; k++) bolt(bb, cam, b, b.add(r.nextGaussian() * 1.6, -0.3 - r.nextDouble() * 0.6, r.nextGaussian() * 1.6), r, 0.04, al, 5);
            }
            any = true;
        }
        for (Iterator<Stream> it = STREAMS.values().iterator(); it.hasNext();) {
            Stream s = it.next();
            if (tick - s.seen() > 3) { it.remove(); continue; }
            if (s.c().distanceToSqr(cam) > 128.0 * 128.0) continue;
            RandomSource r = RandomSource.create(Double.doubleToLongBits(s.c().x * 7.0 + s.c().z) + tick / 2);
            int n = s.strong() ? 6 : 3;
            for (int k = 0; k < n; k++) {
                double a = r.nextDouble() * Math.PI * 2.0;
                Vec3 p0 = s.c().add(Math.cos(a) * s.r(), (r.nextDouble() - 0.5) * 0.3, Math.sin(a) * s.r());
                double len = (s.strong() ? 1.0 : 0.6) + r.nextDouble() * 1.4;
                Vec3 p1 = p0.add(Math.cos(a) * len, (r.nextDouble() - 0.3) * len, Math.sin(a) * len);
                bolt(bb, cam, p0, p1, r, 0.03, s.strong() ? 0.85F : 0.55F, 6);
            }
            any = true;
        }
        ClientBlastGeo.draw(event, bb, any, true);
    }

    private static void bolt(BufferBuilder bb, Vec3 cam, Vec3 a, Vec3 b, RandomSource r, double w, float al, int n) {
        Vec3 prev = a;
        double len = a.distanceTo(b);
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n, k = len * 0.1 * (1.0 - Math.abs(2.0 * t - 1.0));
            Vec3 p = i == n ? b : a.add(b.subtract(a).scale(t)).add(r.nextGaussian() * k, r.nextGaussian() * k, r.nextGaussian() * k);
            ClientBlastGeo.streak(bb, cam, prev.x, prev.y, prev.z, p.x, p.y, p.z, w, 0.65F, 0.85F, 1.0F, al, al);
            prev = p;
        }
    }
}
