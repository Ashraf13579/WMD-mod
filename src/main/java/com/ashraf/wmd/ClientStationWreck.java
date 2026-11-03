package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientStationWreck {

    private ClientStationWreck() {}

    private static final class Blast {
        final Vec3 mid; final float ox, oz, size; final long t0; final long seed;
        final double[][] at = new double[Balance.STATION_WRECK_BURSTS][];
        Blast(StationWreck.Fx p, long now) { mid = new Vec3(p.x(), p.y(), p.z()); ox = p.ox(); oz = p.oz(); size = p.size(); t0 = now; seed = Double.doubleToLongBits(p.x() * 31 + p.z()); }
    }

    private static final class Fall {
        final Vec3 mid; final float ox, oz, size; final long t0; final BlockPos ctrl; final StationModuleBlockEntity ghost;
        Fall(StationWreck.Fx p, long now, StationModuleBlockEntity ghost) { mid = new Vec3(p.x(), p.y(), p.z()); ox = p.ox(); oz = p.oz(); size = p.size(); t0 = now; ctrl = p.ctrl(); this.ghost = ghost; }

        Vec3 at(double t) {
            double out = 0.08 * t + 0.0004 * t * t, drop = t < 60 ? 0.0 : 0.012 * (t - 60) * (t - 60);
            return mid.add(ox * out, -drop, oz * out);
        }
    }

    private static final List<Blast> BLASTS = new ArrayList<>();
    private static final List<Fall> FALLS = new ArrayList<>();
    private static ClientLevel seenLevel;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { BLASTS.clear(); FALLS.clear(); StationWreck.PENDING.clear(); seenLevel = null; return; }
        if (level != seenLevel) { seenLevel = level; BLASTS.clear(); FALLS.clear(); }
        long now = ClientClock.ticks();
        for (StationWreck.Fx p; (p = StationWreck.PENDING.poll()) != null; ) {
            if (p.kind() == StationWreck.BLAST) BLASTS.add(new Blast(p, now));
            else {
                BlockState st = Block.stateById(p.state());
                if (!(st.getBlock() instanceof StationModuleBlock)) st = Wmd.STATION_MODULE.get().defaultBlockState();
                StationModuleBlockEntity ghost = new StationModuleBlockEntity(p.ctrl(), st);
                ghost.setLevel(level);
                if (p.tag() != null) ghost.handleUpdateTag(p.tag(), level.registryAccess());
                FALLS.add(new Fall(p, now, ghost));
            }
        }
        var rnd = level.random;
        for (Iterator<Blast> it = BLASTS.iterator(); it.hasNext(); ) {
            Blast b = it.next();
            long el = now - b.t0;
            if (el > Balance.STATION_WRECK_TICKS + 80) { it.remove(); continue; }
            for (int k = 0; k < Balance.STATION_WRECK_BURSTS; k++) {
                if (el != (long) k * Balance.STATION_WRECK_TICKS / Balance.STATION_WRECK_BURSTS) continue;
                double x = b.mid.x + (rnd.nextDouble() - 0.5) * b.size * 1.2, y = b.mid.y + (rnd.nextDouble() - 0.5) * b.size, z = b.mid.z + (rnd.nextDouble() - 0.5) * b.size * 1.2;
                b.at[k] = new double[] { x, y, z, now };
                VeilBridge.flash(x, y, z, 1.0F, 0.7F, 0.35F, b.size * 6.0F + 12.0F, 5.0F, 20);
                for (int i = 0; i < 24; i++)
                    level.addParticle(i % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.ELECTRIC_SPARK, x, y, z, rnd.nextGaussian() * 0.35, rnd.nextGaussian() * 0.35, rnd.nextGaussian() * 0.35);
                level.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0.0, 0.0, 0.0);
                T4Fx.shakeNear(new Vec3(x, y, z), 80.0, 0.5F);
            }
            if (el < Balance.STATION_WRECK_TICKS + 60)
                for (int i = 0; i < 3; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    double x = b.mid.x + Math.cos(a) * b.size * 0.6, y = b.mid.y + (rnd.nextDouble() - 0.5) * b.size, z = b.mid.z + Math.sin(a) * b.size * 0.6;
                    level.addParticle(ParticleTypes.CLOUD, x, y, z, Math.cos(a) * 0.35 + b.ox * 0.1, (rnd.nextDouble() - 0.5) * 0.1, Math.sin(a) * 0.35 + b.oz * 0.1);
                }
        }
        for (Iterator<Fall> it = FALLS.iterator(); it.hasNext(); ) {
            Fall f = it.next();
            long el = now - f.t0;
            if (el > Balance.STATION_FALL_TICKS) { it.remove(); continue; }
            Vec3 p = f.at(el);
            if (el < 80 && rnd.nextFloat() < 0.6F)
                level.addParticle(rnd.nextBoolean() ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.LARGE_SMOKE, p.x - f.ox * f.size * 0.5, p.y, p.z - f.oz * f.size * 0.5,
                        rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.05);
            if (el > 150) for (int i = 0; i < 2; i++)
                level.addParticle(i == 0 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, p.x + rnd.nextGaussian() * f.size * 0.3, p.y + rnd.nextGaussian() * f.size * 0.3, p.z + rnd.nextGaussian() * f.size * 0.3, 0.0, 0.25, 0.0);
        }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (FALLS.isEmpty() && BLASTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double t = ClientClock.now(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        Vec3 cam = event.getCamera().getPosition();
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES && !FALLS.isEmpty()) {
            PoseStack ps = event.getPoseStack();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            for (Fall f : FALLS) {
                double el = t - f.t0;
                Vec3 p = f.at(el);
                var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(f.ghost);
                if (renderer == null) continue;
                Vec3 pivot = f.mid.subtract(f.ctrl.getX(), f.ctrl.getY(), f.ctrl.getZ());
                ps.pushPose();
                ps.translate(p.x - pivot.x - cam.x, p.y - pivot.y - cam.y, p.z - pivot.z - cam.z);
                ps.translate(pivot.x, pivot.y, pivot.z);
                ps.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(el * 1.1), -f.oz, 0.0F, f.ox));
                ps.mulPose(new Quaternionf().rotationY((float) Math.toRadians(el * 0.4)));
                ps.translate(-pivot.x, -pivot.y, -pivot.z);
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
                renderer.render(f.ghost, pt, ps, buffers, light, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            buffers.endBatch();
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            Vector3f left = event.getCamera().getLeftVector(), up = event.getCamera().getUpVector();
            ClientBlastGeo.beginFrame();
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            boolean any = false;
            for (Blast b : BLASTS)
                for (double[] a : b.at) {
                    if (a == null) continue;
                    double age = t - a[3];
                    if (age < 0 || age > 24) continue;
                    double u = age / 24.0, r = b.size * (0.4 + 0.9 * Math.min(1.0, age / 4.0));
                    float fade = (float) Math.pow(1.0 - u, 1.4);
                    ClientBlastGeo.disc(bb, cam, left, up, a[0], a[1], a[2], r * 1.4, 1.0F, 0.45F, 0.12F, 0.55F * fade);
                    ClientBlastGeo.disc(bb, cam, left, up, a[0], a[1], a[2], r * 0.7, 1.0F, 0.9F, 0.6F, 0.9F * fade);
                    any = true;
                }
            for (Fall f : FALLS) {
                double el = t - f.t0;
                if (el < 120) continue;
                double k = Math.min(1.0, (el - 120) / 80.0), fade = el > Balance.STATION_FALL_TICKS - 40 ? (Balance.STATION_FALL_TICKS - el) / 40.0 : 1.0;
                Vec3 p = f.at(el), q = f.at(el - 14);
                ClientBlastGeo.disc(bb, cam, left, up, p.x, p.y, p.z, f.size * (0.9 + 0.5 * k), 1.0F, 0.55F, 0.18F, (float) (0.8 * k * fade));
                ClientBlastGeo.disc(bb, cam, left, up, p.x, p.y, p.z, f.size * 0.45, 1.0F, 0.92F, 0.7F, (float) (0.9 * k * fade));
                for (int i = 1; i <= 10; i++) {
                    Vec3 s = p.add(q.subtract(p).scale(i / 10.0 * 2.0));
                    ClientBlastGeo.disc(bb, cam, left, up, s.x, s.y, s.z, f.size * 0.5 * (1.0 - i / 12.0), 1.0F, 0.5F, 0.15F, (float) (0.5 * k * fade * (1.0 - i / 11.0)));
                }
                any = true;
            }
            ClientBlastGeo.draw(event, bb, any, true);
        }
    }

    static void burn(StationModuleBlockEntity be, boolean habitat) {
        var level = be.getLevel();
        if (level == null) return;
        var rnd = level.random;
        BlockPos c = be.getBlockPos();
        var n = be.facing().getNormal();
        double cx, cy, cz;
        if (habitat) { double a = rnd.nextDouble() * Station.HAB_LEN; cx = c.getX() + 0.5 + n.getX() * a; cy = c.getY() + 3.0; cz = c.getZ() + 0.5 + n.getZ() * a; }
        else { cx = c.getX() + 0.5; cy = c.getY() + 4.5; cz = c.getZ() + 0.5; }
        if (rnd.nextFloat() < 0.25F) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 3.6, y = (rnd.nextDouble() - 0.5) * 6.0;
            double x = cx + (habitat ? -n.getZ() : 1) * Math.cos(a) * r, z = cz + (habitat ? n.getX() : 1) * Math.sin(a) * r;
            float pick = rnd.nextFloat();
            level.addParticle(pick < 0.5F ? ParticleTypes.LARGE_SMOKE : pick < 0.8F ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.FLAME, x, cy + y, z,
                    Math.cos(a) * 0.05, 0.02, Math.sin(a) * 0.05);
        }
        VeilBridge.glow(0x5752_0000_0000_0000L ^ c.asLong(), cx, cy, cz, 1.0F, 0.45F, 0.15F, 10.0F, 0.6F + 0.3F * rnd.nextFloat(), level.getGameTime());
    }
}
