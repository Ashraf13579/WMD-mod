package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientHemp {

    private ClientHemp() {}

    private static final int SKY_START = 160, SKY_TICKS = 1200, FLICKER_TICKS = 40, WAVES = 5;
    private static final double SKY = 250.0;

    private static final class Field {
        final double x, y, z, r;
        final long start, seed;
        final double[] camSide = new double[WAVES];
        Field(double x, double y, double z, double r, long start, long seed) { this.x = x; this.y = y; this.z = z; this.r = r; this.start = start; this.seed = seed; java.util.Arrays.fill(camSide, Double.NaN); }
        long end() { return start + WarheadFx.HEMP_TICKS; }
    }

    private static final List<Field> FIELDS = new ArrayList<>();
    private static final List<Object[]> LATER = new ArrayList<>();
    private static float jolt, blip;

    public static void burst(ClientLevel level, double x, double y, double z) {
        long now = level.getGameTime();
        FIELDS.add(new Field(x, y, z, WarheadFx.HEMP_RADIUS, now, Double.doubleToLongBits(x * 31.0 + z)));
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = Math.hypot(cam.x - x, cam.z - z);

        VeilBridge.flash(x, y + 4, z, 0.6F, 0.8F, 1.0F, 220.0F, 14.0F, 1.4F, 50);
        VeilBridge.bloomNear(x, y + 4, z, 2000.0, 2.0F);
        if (d < 400) for (int i = 0; i < 160; i++)
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, x + level.random.nextGaussian() * 2, y + 1 + level.random.nextDouble() * 6, z + level.random.nextGaussian() * 2,
                    level.random.nextGaussian() * 0.15, 1.0 + level.random.nextDouble() * 1.5, level.random.nextGaussian() * 0.15);

        if (!LaunchSounds.hemp(x, y + 20, z)) {
            WmdSound.play(SoundEvents.GENERIC_EXPLODE.value(), x, y + 20, z, 200.0F, 0.32F);
            WmdSound.play(SoundEvents.LIGHTNING_BOLT_THUNDER, x, y + 20, z, 200.0F, 0.45F);
        }
        if (d <= WarheadFx.HEMP_RADIUS) {
            later(now + 2, () -> { if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.playSound(WmdSounds.EMP_WHINE.get(), 1.0F, 1.0F); });
        }
    }

    private static void later(long tick, Runnable r) { LATER.add(new Object[] { tick, r }); }

    public static boolean any() { return !FIELDS.isEmpty() || ClientFxEmp.any(); }

    public static boolean darkAt(double x, double z) {
        if (ClientFxEmp.darkAt(x, z)) return true;
        if (FIELDS.isEmpty()) return false;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        long now = level.getGameTime();
        for (Field f : FIELDS) {
            double dx = x - f.x, dz = z - f.z;
            if (dx * dx + dz * dz > f.r * f.r || now < f.start) continue;
            long reboot = f.end() - EmpField.rebootDelay((int) Math.floor(x), (int) Math.floor(z));
            if (now < reboot) return true;
            long a = now - reboot;
            if (a < 4 || (a >= 10 && a < 13) || (a >= 20 && a < 22)) return true;
        }
        return false;
    }

    private static double ground(ClientLevel level, double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return Double.NaN;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { FIELDS.clear(); LATER.clear(); jolt = blip = 0; return; }
        long now = level.getGameTime();
        for (int i = LATER.size() - 1; i >= 0; i--) if ((long) LATER.get(i)[0] <= now) ((Runnable) LATER.remove(i)[1]).run();
        FIELDS.removeIf(f -> now > f.end() + FLICKER_TICKS + 10 || now < f.start - 100);
        jolt *= 0.85F; blip *= 0.88F;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        RandomSource rnd = level.random;
        for (Field f : FIELDS) {
            double age = now - f.start, dc = Math.hypot(cam.x - f.x, cam.z - f.z), ac = Math.atan2(cam.z - f.z, cam.x - f.x);
            if (age > WarheadFx.HEMP_WAVES_END) continue;
            for (int w = 0; w < WAVES; w++) {
                double r = WarheadFx.hempWave(w, age, f.r);
                float pw = WarheadFx.HEMP_WAVE_POWER[w];
                if (r < 0) { f.camSide[w] = Double.NaN; continue; }

                double side = dc - r;
                if (!Double.isNaN(f.camSide[w]) && f.camSide[w] > 0 && side <= 0 && dc <= f.r) {
                    jolt = Math.max(jolt, 2.2F * pw);
                    blip = Math.max(blip, 0.7F * pw);
                    VeilBridge.flash(cam.x, cam.y, cam.z, 0.55F, 0.8F, 1.0F, 40.0F, 6.0F * pw, 0.8F, 12);
                    WmdSound.play(WmdSounds.EMP_FRONT.get(), cam.x, cam.y, cam.z, 1.4F * pw, 1.0F);
                }
                f.camSide[w] = side;
                if (Math.abs(side) > 160.0) continue;
                double span = 140.0 / Math.max(40.0, r);

                int n = ClientConfig.count(45.0F * pw, rnd);
                for (int i = 0; i < n; i++) {
                    double a = ac + (rnd.nextDouble() - 0.5) * 2 * span, rr = r + rnd.nextGaussian() * 1.5;
                    double px = f.x + Math.cos(a) * rr, pz = f.z + Math.sin(a) * rr, gy = ground(level, px, pz);
                    if (Double.isNaN(gy)) continue;
                    level.addAlwaysVisibleParticle(ParticleTypes.ELECTRIC_SPARK, true, px, gy + 0.2 + rnd.nextDouble() * 2.5, pz,
                            rnd.nextGaussian() * 0.15, 0.15 + rnd.nextDouble() * 0.35, rnd.nextGaussian() * 0.15);
                    if (i % 6 == 0) level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, px, gy + 0.5, pz, rnd.nextGaussian() * 0.05, 0.2 + rnd.nextDouble() * 0.3, rnd.nextGaussian() * 0.05);
                }

                for (int i = -4; i <= 4; i++) {
                    double a = ac + i * 16.0 / Math.max(40.0, r), px = f.x + Math.cos(a) * r, pz = f.z + Math.sin(a) * r, gy = ground(level, px, pz);
                    if (Double.isNaN(gy)) continue;
                    float flick = 0.7F + 0.3F * rnd.nextFloat();
                    VeilBridge.glow(0x4E3D_0000_0000_0000L ^ (f.seed * 64 + w * 16 + i + 4), px, gy + 1.5, pz, 0.5F, 0.75F, 1.0F, 16.0F, 3.2F * pw * flick, 0.5F, now);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (FIELDS.isEmpty()) return;
        boolean ground = event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES, sky = event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY;
        if (!ground && !sky) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean drew = false;
        for (Field f : FIELDS) {
            double age = now - f.start;
            if (age < 0) continue;
            if (ground) drew |= groundWaves(b, level, cam, f, age, (long) now);
            else if (Math.hypot(cam.x - f.x, cam.z - f.z) < 8000.0) drew |= skyWeb(b, cam, f, age, (long) now);
        }
        MeshData md = b.build();
        if (!drew || md == null) { if (md != null) md.close(); return; }
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(event.getProjectionMatrix()), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        if (sky) RenderSystem.disableDepthTest(); else RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(md);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.restoreProjectionMatrix();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void v(BufferBuilder b, Vec3 cam, double x, double y, double z, float r, float g, float bl, float a) {
        b.addVertex((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, bl, a);
    }

    private static boolean groundWaves(BufferBuilder b, ClientLevel level, Vec3 cam, Field f, double age, long tick) {
        if (age > WarheadFx.HEMP_WAVES_END) return false;
        double far = Minecraft.getInstance().gameRenderer.getDepthFar() * 0.9;
        boolean drew = false;
        for (int w = 0; w < WAVES; w++) {
            double r = WarheadFx.hempWave(w, age, f.r);
            if (r < 1.0) continue;
            float pw = WarheadFx.HEMP_WAVE_POWER[w];
            double t = age - WarheadFx.HEMP_WAVE_START[w];
            float fade = (float) Math.min(1.0, (WarheadFx.HEMP_WAVE_TICKS[w] - t) / 50.0) * pw;
            float pulse = 0.8F + 0.2F * (float) Math.sin(tick * 0.9 + w);
            double h = 7.0 + 5.0 * pw;
            int seg = 360;
            double pa = 0, px = 0, pz = 0, pg = Double.NaN;
            for (int i = 0; i <= seg; i++) {
                double a = Math.PI * 2 * i / seg, x = f.x + Math.cos(a) * r, z = f.z + Math.sin(a) * r;
                double gy = Math.hypot(x - cam.x, z - cam.z) > far ? Double.NaN : ground(level, x, z);
                if (i > 0 && !Double.isNaN(gy) && !Double.isNaN(pg)) {

                    v(b, cam, px, pg - 0.3, pz, 0.6F, 0.85F, 1.0F, 0.75F * fade * pulse);
                    v(b, cam, x, gy - 0.3, z, 0.6F, 0.85F, 1.0F, 0.75F * fade * pulse);
                    v(b, cam, x, gy + h, z, 0.3F, 0.5F, 1.0F, 0.0F);
                    v(b, cam, px, pg + h, pz, 0.3F, 0.5F, 1.0F, 0.0F);

                    v(b, cam, px, pg + 0.05, pz, 1.0F, 1.0F, 1.0F, 0.9F * fade);
                    v(b, cam, x, gy + 0.05, z, 1.0F, 1.0F, 1.0F, 0.9F * fade);
                    v(b, cam, x, gy + 1.1, z, 0.7F, 0.9F, 1.0F, 0.0F);
                    v(b, cam, px, pg + 1.1, pz, 0.7F, 0.9F, 1.0F, 0.0F);
                    drew = true;
                }
                pa = a; px = x; pz = z; pg = gy;
            }

            double dc = Math.hypot(cam.x - f.x, cam.z - f.z);
            if (Math.abs(dc - r) > 150.0) continue;
            RandomSource rnd = RandomSource.create(f.seed ^ (tick / 2) * 31 + w);
            double ac = Math.atan2(cam.z - f.z, cam.x - f.x), span = 120.0 / Math.max(40.0, r);
            int veins = (int) (22 * pw);
            for (int k = 0; k < veins; k++) {
                double a = ac + (rnd.nextDouble() - 0.5) * 2 * span, x = f.x + Math.cos(a) * r, z = f.z + Math.sin(a) * r;
                double gy = ground(level, x, z);
                if (Double.isNaN(gy)) continue;
                double dir = a + Math.PI + (rnd.nextDouble() - 0.5) * 1.6, len = 3.0 + rnd.nextDouble() * 4.0;
                double y = gy + 0.3 + rnd.nextDouble() * 0.8;
                for (int s = 0; s < 6; s++) {
                    dir += (rnd.nextDouble() - 0.5) * 1.4;
                    double nx = x + Math.cos(dir) * len, nz = z + Math.sin(dir) * len, ng = ground(level, nx, nz);
                    if (Double.isNaN(ng)) break;
                    double ny = ng + 0.3 + rnd.nextDouble() * 1.2;
                    bolt(b, cam, x, y, z, nx, ny, nz, 0.18F, fade);
                    x = nx; y = ny; z = nz;
                    if (rnd.nextInt(4) == 0) {
                        double bdir = dir + (rnd.nextBoolean() ? 1 : -1) * (0.6 + rnd.nextDouble()), bl = len * 0.6;
                        double bx = x + Math.cos(bdir) * bl, bz = z + Math.sin(bdir) * bl, bg = ground(level, bx, bz);
                        if (!Double.isNaN(bg)) bolt(b, cam, x, y, z, bx, bg + 0.3, bz, 0.12F, fade * 0.7F);
                    }
                }
            }
        }
        return drew;
    }

    private static void bolt(BufferBuilder b, Vec3 cam, double x0, double y0, double z0, double x1, double y1, double z1, float w, float a) {
        double dx = x1 - x0, dz = z1 - z0, l = Math.max(1e-3, Math.hypot(dx, dz)), ox = -dz / l * w, oz = dx / l * w;
        v(b, cam, x0 + ox, y0, z0 + oz, 0.85F, 0.95F, 1.0F, a);
        v(b, cam, x1 + ox, y1, z1 + oz, 0.85F, 0.95F, 1.0F, a);
        v(b, cam, x1 - ox, y1, z1 - oz, 0.85F, 0.95F, 1.0F, a);
        v(b, cam, x0 - ox, y0, z0 - oz, 0.85F, 0.95F, 1.0F, a);
        v(b, cam, x0, y0 + w, z0, 0.85F, 0.95F, 1.0F, a);
        v(b, cam, x1, y1 + w, z1, 0.85F, 0.95F, 1.0F, a);
        v(b, cam, x1, y1 - w, z1, 0.85F, 0.95F, 1.0F, a);
        v(b, cam, x0, y0 - w, z0, 0.85F, 0.95F, 1.0F, a);
    }

    private static void sky(BufferBuilder b, Vec3 cam, double x, double y, double z, float r, float g, float bl, float a) {
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-3) l = 1e-3;
        double k = Math.min(SKY, Minecraft.getInstance().gameRenderer.getDepthFar() * 0.5) / l;
        b.addVertex((float) (dx * k), (float) (dy * k), (float) (dz * k)).setColor(r, g, bl, a);
    }

    private static boolean skyWeb(BufferBuilder b, Vec3 cam, Field f, double age, long tick) {
        double t = age - SKY_START;
        if (t < 0 || t > SKY_TICKS) return false;
        float env = (float) (Math.min(1.0, t / 60.0) * Math.min(1.0, (SKY_TICKS - t) / 300.0));
        if (env < 0.01F) return false;
        double top = f.y + 380.0;

        int n = 48;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            sky(b, cam, f.x, top, f.z, 0.45F, 0.65F, 1.0F, 0.35F * env);
            sky(b, cam, f.x, top, f.z, 0.45F, 0.65F, 1.0F, 0.35F * env);
            sky(b, cam, f.x + Math.cos(a0) * f.r, top - 120, f.z + Math.sin(a0) * f.r, 0.3F, 0.45F, 1.0F, 0.0F);
            sky(b, cam, f.x + Math.cos(a1) * f.r, top - 120, f.z + Math.sin(a1) * f.r, 0.3F, 0.45F, 1.0F, 0.0F);
        }

        RandomSource rnd = RandomSource.create(f.seed ^ (tick / 3) * 7919);
        int bolts = 4 + (int) (10 * env);
        for (int k = 0; k < bolts; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = f.r * 0.85 * Math.sqrt(rnd.nextDouble());
            double x = f.x + Math.cos(a) * d, z = f.z + Math.sin(a) * d, y = top - 40 - rnd.nextDouble() * 120;
            double dir = rnd.nextDouble() * Math.PI * 2, len = 25.0 + rnd.nextDouble() * 40.0;
            float al = (0.4F + 0.6F * rnd.nextFloat()) * env;
            for (int s = 0; s < 10; s++) {
                dir += (rnd.nextDouble() - 0.5) * 1.2;
                double nx = x + Math.cos(dir) * len, nz = z + Math.sin(dir) * len, ny = y + (rnd.nextDouble() - 0.5) * 20;
                double dx = nx - x, dz = nz - z, l = Math.max(1e-3, Math.hypot(dx, dz)), w = 3.0, ox = -dz / l * w, oz = dx / l * w;
                sky(b, cam, x + ox, y, z + oz, 0.85F, 0.95F, 1.0F, al);
                sky(b, cam, nx + ox, ny, nz + oz, 0.85F, 0.95F, 1.0F, al);
                sky(b, cam, nx - ox, ny, nz - oz, 0.6F, 0.75F, 1.0F, al * 0.3F);
                sky(b, cam, x - ox, y, z - oz, 0.6F, 0.75F, 1.0F, al * 0.3F);
                x = nx; y = ny; z = nz;
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onSkyFlash(ClientTickEvent.Pre event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || FIELDS.isEmpty()) return;
        long now = level.getGameTime();
        for (Field f : FIELDS) {
            long t = now - f.start - SKY_START;
            if (t >= 0 && t < SKY_TICKS / 2 && level.random.nextInt(t < 100 ? 8 : 30) == 0) level.setSkyFlashTime(2 + level.random.nextInt(3));
        }
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || FIELDS.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        double now = level.getGameTime() + event.getPartialTick();
        for (Field f : FIELDS) {
            double age = now - f.start, d = Math.hypot(cam.x - f.x, cam.z - f.z);
            if (age < 0 || age > SKY_START + SKY_TICKS || d > 3000.0) continue;
            float near = (float) (1.0 - d / 3000.0);
            float k = age < 10 ? (float) (1.0 - age / 10.0) * near
                    : (float) (Math.min(1.0, (age - 10) / 80.0) * Math.min(1.0, (SKY_START + SKY_TICKS - age) / 300.0)) * 0.35F * near;
            float r = age < 10 ? 0.85F : 0.35F, g = age < 10 ? 0.92F : 0.55F, bl = 1.0F;
            event.setRed(event.getRed() + (r - event.getRed()) * k);
            event.setGreen(event.getGreen() + (g - event.getGreen()) * k);
            event.setBlue(event.getBlue() + (bl - event.getBlue()) * k);
            return;
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (jolt < 0.05F) return;
        RandomSource rnd = RandomSource.create();
        event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5F) * jolt * 2.0F);
        event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5F) * jolt * 2.0F);
        event.setRoll(event.getRoll() + (rnd.nextFloat() - 0.5F) * jolt * 1.5F);
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (FIELDS.isEmpty() || !ClientConfig.hempGlitch()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        long now = level.getGameTime();
        float strength = blip;
        Field last = FIELDS.get(FIELDS.size() - 1);
        for (Field f : FIELDS) {
            long age = now - f.start;
            if (age < 0 || age > SKY_START + SKY_TICKS) continue;
            boolean inside = Math.hypot(mc.player.getX() - f.x, mc.player.getZ() - f.z) <= f.r;
            if (age < 40) strength = Math.max(strength, (inside ? 1.0F : 0.35F) * (1.0F - age / 40.0F));
            else if (inside && ((now * 7919L + f.seed) & 127L) == 0L) strength = Math.max(strength, 0.4F);
        }
        if (strength <= 0.02F) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        RandomSource rnd = level.random;
        if (now - last.start < 6) g.fill(0, 0, w, h, ((int) (200 * strength) << 24) | 0xE0F0FF);
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, ((int) (40 * strength) << 24));
        int bars = (int) (14 * strength) + 2;
        for (int i = 0; i < bars; i++) {
            int y = rnd.nextInt(h), bh = 1 + rnd.nextInt(Math.max(1, (int) (10 * strength) + 1)), x = rnd.nextInt(Math.max(1, w / 3));
            int grey = 120 + rnd.nextInt(136), alpha = (int) ((60 + rnd.nextInt(120)) * strength);
            int col = switch (rnd.nextInt(5)) { case 0 -> 0x00FFFF; case 1 -> 0x7AA0FF; default -> (grey << 16) | (grey << 8) | grey; };
            g.fill(x, y, w - rnd.nextInt(Math.max(1, w / 4)), y + bh, (alpha << 24) | col);
        }
    }
}
