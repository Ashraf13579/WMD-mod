package com.ashraf.wmd;

import org.joml.Matrix4f;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class ClientOrbitSky {

    private ClientOrbitSky() {}

    private static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static VertexBuffer stars;

    static final DimensionSpecialEffects EFFECTS = new DimensionSpecialEffects(Float.NaN, false, DimensionSpecialEffects.SkyType.NONE, false, false) {
        @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) { return Vec3.ZERO; }
        @Override public boolean isFoggyAt(int x, int z) { return false; }
        @Override public float[] getSunriseColor(float timeOfDay, float partialTicks) { return null; }

        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView, Camera camera, Matrix4f projection, boolean foggy, Runnable setupFog) {
            drawSky(modelView, projection);
            return true;
        }

        @Override
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double x, double y, double z, Matrix4f modelView, Matrix4f projection) { return true; }

        @Override
        public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture light, double x, double y, double z) { return true; }

        @Override
        public boolean tickRain(ClientLevel level, int ticks, Camera camera) { return true; }
    };

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Reg {
        @SubscribeEvent
        static void effects(RegisterDimensionSpecialEffectsEvent event) { event.register(Orbit.ORBIT.location(), EFFECTS); }
    }

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Fog {

        @SubscribeEvent
        static void color(ViewportEvent.ComputeFogColor event) {
            if (!Orbit.isOrbit(Minecraft.getInstance().level)) return;
            event.setRed(0.0F); event.setGreen(0.0F); event.setBlue(0.0F);
        }

        @SubscribeEvent
        static void fog(ViewportEvent.RenderFog event) {
            if (!Orbit.isOrbit(Minecraft.getInstance().level)) return;
            event.setNearPlaneDistance(event.getFarPlaneDistance() * 4.0F);
            event.setFarPlaneDistance(event.getFarPlaneDistance() * 5.0F);
            event.setCanceled(true);
        }
    }

    private static void buildStars() {
        RandomSource r = RandomSource.create(10_842L);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (int i = 0; i < 3_000; i++) {
            double x = r.nextFloat() * 2.0F - 1.0F, y = r.nextFloat() * 2.0F - 1.0F, z = r.nextFloat() * 2.0F - 1.0F;
            double size = 0.1F + r.nextFloat() * (r.nextFloat() < 0.05F ? 0.35F : 0.12F);
            double d = x * x + y * y + z * z;
            if (d <= 0.01 || d >= 1.0) continue;
            d = 1.0 / Math.sqrt(d);
            x *= d; y *= d; z *= d;
            double px = x * 100.0, py = y * 100.0, pz = z * 100.0;
            double yaw = Math.atan2(x, z), sy = Math.sin(yaw), cy = Math.cos(yaw);
            double pitch = Math.atan2(Math.sqrt(x * x + z * z), y), sp = Math.sin(pitch), cp = Math.cos(pitch);
            double rot = r.nextDouble() * Math.PI * 2.0, sr = Math.sin(rot), cr = Math.cos(rot);
            for (int k = 0; k < 4; k++) {
                double a = ((k & 2) - 1) * size, c = ((k + 1 & 2) - 1) * size;
                double e = a * cr - c * sr, f = c * cr + a * sr;
                double g = e * sp, h = -e * cp;
                b.addVertex((float) (px + h * sy - f * cy), (float) (py + g), (float) (pz + f * sy + h * cy));
            }
        }
        stars = new VertexBuffer(VertexBuffer.Usage.STATIC);
        stars.bind();
        stars.upload(b.buildOrThrow());
        VertexBuffer.unbind();
    }

    static void drawSky(Matrix4f modelView, Matrix4f projection) {
        if (stars == null) buildStars();
        PoseStack ps = new PoseStack();
        ps.mulPose(modelView);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = ps.last().pose();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.95F);
        stars.bind();
        stars.drawWithShader(m, projection, GameRenderer.getPositionShader());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        long t = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        float phase = OrbitClock.phase(t) / (float) Balance.STATION_SUN_TICKS;
        float dusk = phase >= 1.0F ? 0.0F : Math.min(1.0F, Math.min(phase, 1.0F - phase) * 18.0F);
        float lit = 0.18F + 0.82F * dusk;
        RenderSystem.setShaderColor(lit, lit, Math.min(1.0F, lit + 0.12F), 1.0F);

        if (!ClientPlanet.draw(m)) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder p = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
            p.addVertex(m, 0.0F, -30.0F, 0.0F).setColor(0.03F, 0.07F, 0.14F, 1.0F);
            for (int i = 0; i <= 48; i++) {
                double a = Math.PI * 2.0 * i / 48;
                p.addVertex(m, (float) (Math.cos(a) * 300.0), -30.0F, (float) (Math.sin(a) * 300.0)).setColor(0.09F, 0.2F, 0.4F, 1.0F);
            }
            BufferUploader.drawWithShader(p.buildOrThrow());
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            BufferBuilder limb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < 48; i++) {
                double a0 = Math.PI * 2.0 * i / 48, a1 = Math.PI * 2.0 * (i + 1) / 48;
                float x0 = (float) (Math.cos(a0) * 300.0), z0 = (float) (Math.sin(a0) * 300.0), x1 = (float) (Math.cos(a1) * 300.0), z1 = (float) (Math.sin(a1) * 300.0);
                limb.addVertex(m, x0, -30.0F, z0).setColor(0.35F, 0.65F, 1.0F, 0.85F);
                limb.addVertex(m, x1, -30.0F, z1).setColor(0.35F, 0.65F, 1.0F, 0.85F);
                limb.addVertex(m, x1, 14.0F, z1).setColor(0.2F, 0.45F, 1.0F, 0.0F);
                limb.addVertex(m, x0, 14.0F, z0).setColor(0.2F, 0.45F, 1.0F, 0.0F);
            }
            BufferUploader.drawWithShader(limb.buildOrThrow());
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (phase >= 1.0F) {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            return;
        }

        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-90.0F));
        ps.mulPose(Axis.XP.rotationDegrees(-100.0F + 200.0F * phase));
        Matrix4f sm = ps.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SUN);
        float s = 18.0F;
        BufferBuilder sun = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        sun.addVertex(sm, -s, 100.0F, -s).setUv(0.0F, 0.0F);
        sun.addVertex(sm, s, 100.0F, -s).setUv(1.0F, 0.0F);
        sun.addVertex(sm, s, 100.0F, s).setUv(1.0F, 1.0F);
        sun.addVertex(sm, -s, 100.0F, s).setUv(0.0F, 1.0F);
        BufferUploader.drawWithShader(sun.buildOrThrow());
        ps.popPose();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }
}
