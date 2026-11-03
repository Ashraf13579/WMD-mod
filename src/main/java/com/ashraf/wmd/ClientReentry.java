package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientReentry {

    private ClientReentry() {}

    private static CrewCapsulePayloads.Burn burn;
    private static ClientLevel burnLevel, whiteLevel;
    private static Marker cam;
    private static boolean hidGui, unhideLater;
    private static float yaw, pitch;

    private static int overlay;
    private static boolean hold;
    private static long fadeStart = -1, whiteStart = -1, heldSince;

    static void descentStarted(long now) {
        if (overlay == 1 && hold) { hold = false; fadeStart = now; }
    }

    static void climbTop(ClientLevel level) {
        if (overlay == 2) return;
        overlay = 2; hold = true; whiteStart = level.getGameTime(); whiteLevel = level; fadeStart = -1; heldSince = 0;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        CrewCapsulePayloads.Burn nb = CrewCapsulePayloads.pendingBurn;
        if (nb != null && mc.level != null && mc.player != null) {
            CrewCapsulePayloads.pendingBurn = null;
            startBurn(mc, nb);
        }
        ClientLevel level = mc.level;
        if (level == null) return;
        if (burn != null) {
            if (level != burnLevel) stopBurn(mc);
            else {
                long el = level.getGameTime() - burn.start();
                var rnd = level.random;
                double x = burn.x() + 0.5, y = burn.y() + 0.5, z = burn.z() + 0.5;
                for (int i = 0; i < 3; i++)
                    level.addParticle(ParticleTypes.FLAME, x + rnd.nextGaussian() * 0.6, y - 0.4, z + rnd.nextGaussian() * 0.6, rnd.nextGaussian() * 0.05, -0.4, rnd.nextGaussian() * 0.05);
                VeilBridge.glow(0x4255524EL ^ burn.start(), x, y - 1.0, z, 1.0F, 0.55F, 0.25F, 10.0F, 2.5F, 0.4F, level.getGameTime());
                if (el == 2) ClientRailgun.flat(WmdSounds.TUG_LAUNCH.get(), 1.0F, 0.8F);
                if (el == burn.ticks() - Balance.CREW_PLASMA_TICKS) ClientRailgun.flat(WmdSounds.OR_CAPSULE_REENTRY.get(), 1.0F, 1.0F);
                if (!(mc.screen instanceof ClientCrewRide.Ride)) mc.setScreen(new ClientCrewRide.Ride());
            }
        }
        if (unhideLater && !ClientCapsuleDescent.riding()) { unhideLater = false; mc.options.hideGui = false; }

        if (overlay == 2 && hold && level != whiteLevel && !(mc.screen instanceof ReceivingLevelScreen)) { hold = false; fadeStart = level.getGameTime(); }
        if (overlay == 2 && hold && level == whiteLevel && ++heldSince > 600) { hold = false; fadeStart = level.getGameTime(); }
        if (overlay == 1 && hold && burn == null && ++heldSince > 600) { hold = false; fadeStart = level.getGameTime(); }
        if (!hold && fadeStart >= 0 && level.getGameTime() - fadeStart > Balance.CREW_FADE_TICKS) { overlay = 0; fadeStart = -1; }
    }

    private static void startBurn(Minecraft mc, CrewCapsulePayloads.Burn b) {
        burn = b;
        burnLevel = mc.level;
        overlay = 1; hold = true; fadeStart = -1; heldSince = 0;
        cam = new Marker(EntityType.MARKER, mc.level);
        cam.setPos(new Vec3(b.x() + 0.5, b.y() + 1.5, b.z() + 0.5));
        mc.setCameraEntity(cam);
        hidGui = !mc.options.hideGui;
        mc.options.hideGui = true;
        mc.setScreen(new ClientCrewRide.Ride());
    }

    private static void stopBurn(Minecraft mc) {
        Marker old = cam;
        burn = null;
        cam = null;
        burnLevel = null;
        if (hidGui) { if (ClientCapsuleDescent.riding()) unhideLater = true; else mc.options.hideGui = false; }
        hidGui = false;
        if (mc.player != null && old != null && mc.getCameraEntity() == old) mc.setCameraEntity(mc.player);
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        if (burn == null || cam == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != burnLevel) return;
        double t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double u = Mth.clamp((t - burn.start()) / burn.ticks(), 0.0, 1.2), shake = plasma(t) * 0.35;
        Vec3 c = new Vec3(burn.x() + 0.5, burn.y() + 1.5, burn.z() + 0.5);
        Vec3 eye = c.add(-10.0 - 26.0 * u + (Math.random() - 0.5) * shake, 4.0 - 14.0 * u + (Math.random() - 0.5) * shake, 8.0 + 12.0 * u);
        cam.setPos(eye);
        cam.xo = eye.x; cam.yo = eye.y; cam.zo = eye.z;
        Vec3 look = c.add(0.0, -6.0 * u, 0.0).subtract(eye);
        double h = Math.sqrt(look.x * look.x + look.z * look.z);
        yaw = (float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90.0);
        pitch = (float) -Math.toDegrees(Math.atan2(look.y, h));
        cam.setYRot(yaw); cam.setXRot(pitch); cam.yRotO = yaw; cam.xRotO = pitch;
    }

    @SubscribeEvent
    static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (burn == null || cam == null) return;
        event.setYaw(yaw);
        event.setPitch(pitch);
    }

    private static double plasma(double t) {
        if (burn == null) return 0.0;
        double el = t - burn.start();
        return Mth.clamp((el - (burn.ticks() - Balance.CREW_PLASMA_TICKS)) / Balance.CREW_PLASMA_TICKS, 0.0, 1.0);
    }

    @SubscribeEvent
    static void onFog(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || Orbit.isOrbit(mc.level) || !(ClientCrewRide.active() || ClientCapsuleDescent.riding())) return;
        double y = event.getCamera().getPosition().y;
        float k = (float) Mth.clamp((y - 250.0) / 500.0, 0.0, 1.0) * 0.9F;
        if (k <= 0.0F) return;
        event.setRed(event.getRed() * (1.0F - k));
        event.setGreen(event.getGreen() * (1.0F - k));
        event.setBlue(event.getBlue() * (1.0F - k) + 0.04F * k);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onScreen(ScreenEvent.Render.Post event) { draw(event.getGuiGraphics(), event.getPartialTick()); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onGui(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) draw(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    private static void draw(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        double now = mc.level != null ? mc.level.getGameTime() + pt : 0.0;
        double a = 0.0;
        int rgb = 0xFFA040;
        if (overlay == 1) {
            a = burn != null && mc.level == burnLevel ? plasma(now) : hold ? 1.0 : fadeStart >= 0 ? 1.0 - Mth.clamp((now - fadeStart) / Balance.CREW_FADE_TICKS, 0.0, 1.0) : 0.0;
            float fl = 0.85F + 0.15F * (float) Math.sin(now * 1.7) * (float) Math.sin(now * 0.63);
            rgb = ((int) (255 * fl) << 16) | ((int) (150 * fl) << 8) | (int) (70 * fl);
        } else if (overlay == 2) {
            a = hold ? (mc.level == whiteLevel ? Mth.clamp((now - whiteStart) / Balance.CREW_WHITE_TICKS, 0.0, 1.0) : 1.0)
                    : fadeStart >= 0 ? 1.0 - Mth.clamp((now - fadeStart) / Balance.CREW_FADE_TICKS, 0.0, 1.0) : 0.0;
            rgb = 0xF4F8FF;
        }
        double cloud = ClientCapsuleDescent.cloudWhite(now);
        if (cloud > a && overlay != 1) { a = cloud; rgb = 0xF4F8FF; }
        else if (cloud > 0.0 && overlay == 1 && !hold) a = Math.max(a, cloud);
        if (a <= 0.003) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int al = (int) (255 * Math.min(1.0, a));
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        g.fill(0, 0, w, h, (al << 24) | rgb);
        if (overlay == 1 && a > 0.2) {
            int core = (int) (al * 0.6);
            g.fillGradient(0, h / 4, w, h / 2, (0 << 24) | 0xFFE8C0, (core << 24) | 0xFFE8C0);
            g.fillGradient(0, h / 2, w, 3 * h / 4, (core << 24) | 0xFFE8C0, (0 << 24) | 0xFFE8C0);
        }
        g.pose().popPose();
    }
}
