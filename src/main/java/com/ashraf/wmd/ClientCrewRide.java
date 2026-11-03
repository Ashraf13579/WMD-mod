package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientCrewRide {

    private ClientCrewRide() {}

    private static long flight = -1, started;
    private static boolean seen;
    private static Marker cam;
    private static boolean hidGui;
    private static Vec3 pos = Vec3.ZERO, look = Vec3.ZERO;
    private static float yaw, pitch;

    public static boolean active() { return flight >= 0; }

    static final class Ride extends Screen {
        Ride() { super(Component.empty()); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            Component c = Component.translatable("hud.wmd.crew.ride");
            g.drawString(font, c, (width - font.width(c)) / 2, height - 28, 0xFFBFD4EA, true);
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        CrewRidePayload p = CrewRidePayload.pending;
        if (p != null && mc.level != null && mc.player != null) {
            CrewRidePayload.pending = null;
            if (p.start()) start(mc, p.flightId()); else stop();
        }
        if (flight >= 0 && (mc.level == null || mc.player == null)) stop();
        if (flight >= 0 && !(mc.screen instanceof Ride)) mc.setScreen(new Ride());
    }

    private static void start(Minecraft mc, long id) {
        if (flight >= 0) stop();
        flight = id;
        seen = false;
        started = mc.level.getGameTime();
        cam = new Marker(EntityType.MARKER, mc.level);
        pos = mc.player.getEyePosition();
        cam.setPos(pos);
        mc.setCameraEntity(cam);
        hidGui = !mc.options.hideGui;
        mc.options.hideGui = true;
        mc.setScreen(new Ride());
    }

    static void stop() {
        Minecraft mc = Minecraft.getInstance();
        flight = -1;
        cam = null;
        if (hidGui) mc.options.hideGui = false;
        hidGui = false;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (mc.screen instanceof Ride) mc.setScreen(null);
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        if (flight < 0 || cam == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { stop(); return; }
        double t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Flight f = ClientFlights.FLIGHTS.get(flight);
        if (f != null) {
            seen = true;
            Vec3 p = f.position(t), ahead = f.position(t + 10.0).subtract(p);
            Vec3 dir = ahead.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : ahead.normalize();
            double age = t - started, k = Mth.clamp(age / 400.0, 0.0, 1.0);
            double swing = age * 0.004;
            Vec3 side = new Vec3(Math.cos(swing), 0.0, Math.sin(swing));
            double back = 6.0 + 30.0 * k, out = 9.0 + 14.0 * k;
            pos = p.subtract(dir.scale(back)).add(side.scale(out)).add(0.0, -4.0 + 6.0 * k, 0.0);
            look = p.add(dir.scale(4.0));
        } else if (seen) ClientReentry.climbTop(mc.level);
        cam.setPos(pos);
        Vec3 d = look.subtract(pos);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        pitch = (float) -Math.toDegrees(Math.atan2(d.y, h));
        cam.setYRot(yaw); cam.setXRot(pitch); cam.yRotO = yaw; cam.xRotO = pitch;
        cam.xo = pos.x; cam.yo = pos.y; cam.zo = pos.z;
    }

    @SubscribeEvent
    static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (flight < 0) return;
        event.setYaw(yaw);
        event.setPitch(pitch);
    }
}
