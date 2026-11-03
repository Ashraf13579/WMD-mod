package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientTv {

    private ClientTv() {}

    public static final KeyMapping TV_KEY = new KeyMapping("key.wmd.tv_view", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.wmd");

    private static final Map<Long, int[]> WINDOWS = new HashMap<>();
    private static boolean held;
    private static double mouseX, mouseY;
    private static int guiW = 1, guiH = 1;
    private static long lastSend;

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Keys {
        @SubscribeEvent
        static void register(RegisterKeyMappingsEvent event) { event.register(TV_KEY); }
    }

    public static void onWindow(TvWindowPayload p) {
        if (WINDOWS.size() > 64) WINDOWS.keySet().removeIf(id -> !ClientFlights.FLIGHTS.containsKey(id));
        WINDOWS.put(p.id(), new int[] { p.cx(), p.cz(), p.cruise() ? 1 : 0 });
    }

    static Flight steerable() {
        long id = ClientMissileCam.watching();
        Flight f = id > 0 ? ClientFlights.FLIGHTS.get(id) : null;
        Minecraft mc = Minecraft.getInstance();
        if (f == null || mc.level == null || !WINDOWS.containsKey(id) || mc.level.getGameTime() >= f.endTime() - 10) return null;
        return f;
    }

    private static boolean isTv(Flight f) { return f.build() != null && MissileBuild.SEEK_TV.equals(f.build().seeker()); }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { WINDOWS.clear(); held = false; return; }
        while (TV_KEY.consumeClick()) {
            if (mc.screen != null) continue;
            String me = mc.player.getUUID().toString();
            Flight best = null;
            long now = mc.level.getGameTime();
            for (Flight f : ClientFlights.FLIGHTS.values())
                if (isTv(f) && me.equals(f.ownerId()) && now >= f.start() && now < f.endTime() && (best == null || f.start() > best.start())) best = f;
            if (best == null) mc.player.displayClientMessage(Component.translatable("message.wmd.tv.none"), true);
            else ClientMissileCam.start(best.id());
        }
        Flight f = steerable();
        if (f == null) { held = false; return; }
        long now = mc.level.getGameTime();
        if (!held || now - lastSend < Balance.TV_STEP) return;
        double[] a = aim(f);
        if (a == null) return;
        lastSend = now;
        PacketDistributor.sendToServer(new TvSteerPayload(f.id(), (int) Math.floor(a[0]), (int) Math.floor(a[1]), false));
    }

    static boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || steerable() == null) return false;
        held = true; mouseX = mx; mouseY = my;
        return true;
    }

    static void mouseReleased(int button) { if (button == 0) held = false; }

    private static Vec3[] basis() {
        double yaw = Math.toRadians(ClientMissileCam.camYaw()), pitch = Math.toRadians(ClientMissileCam.camPitch());
        Vec3 fw = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 rt = fw.cross(new Vec3(0, 1, 0));
        rt = rt.lengthSqr() < 1.0E-8 ? new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)) : rt.normalize();
        return new Vec3[] { fw, rt, rt.cross(fw).normalize() };
    }

    private static double tanHalf() { return Math.tan(Math.toRadians(Minecraft.getInstance().options.fov().get()) * 0.5); }

    private static double[] aim(Flight f) {
        Vec3[] b = basis();
        double th = tanHalf(), hh = guiH * 0.5;
        Vec3 ray = b[0].add(b[1].scale((mouseX - guiW * 0.5) / hh * th)).add(b[2].scale((hh - mouseY) / hh * th));
        Vec3 eye = ClientMissileCam.camPos();
        if (ray.y > -1.0E-4) return null;
        double t = (f.endY() - eye.y) / ray.y;
        if (t < 0) return null;
        double x = eye.x + ray.x * t, z = eye.z + ray.z * t;
        int[] w = WINDOWS.get(f.id());
        if (w != null && w[2] == 0) {
            double r = radius(f), dx = x - (f.tx() + 0.5), dz = z - (f.tz() + 0.5), d = Math.hypot(dx, dz);
            if (d > r && d > 1.0E-6) { x = f.tx() + 0.5 + dx / d * r; z = f.tz() + 0.5 + dz / d * r; }
        }
        return new double[] { x, z };
    }

    private static double radius(Flight f) {
        Minecraft mc = Minecraft.getInstance();
        double y = f.position(mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false)).y;
        return Math.tan(Math.toRadians(Balance.TV_CONE)) * Math.max(0.0, y - f.endY());
    }

    private static int[] project(Vec3 p) {
        Vec3[] b = basis();
        Vec3 v = p.subtract(ClientMissileCam.camPos());
        double z = v.dot(b[0]);
        if (z < 0.5) return null;
        double th = tanHalf(), hh = guiH * 0.5;
        return new int[] { (int) Math.round(guiW * 0.5 + v.dot(b[1]) / z / th * hh), (int) Math.round(hh - v.dot(b[2]) / z / th * hh) };
    }

    static void render(GuiGraphics g, Font font, int width, int height, int mx, int my, Flight watched) {
        guiW = width; guiH = height;
        if (held) { mouseX = mx; mouseY = my; }
        if (watched == null || !isTv(watched) || watched.isCruise()) return;
        Flight f = steerable();
        Component hint;
        if (f == null) {
            hint = Component.translatable("gui.wmd.tv.wait");
        } else {
            int[] w = WINDOWS.get(f.id());
            if (w != null && w[2] == 0) {
                double r = radius(f);
                for (int i = 0; i < 72; i++) {
                    double a = Math.PI * 2.0 * i / 72;
                    int[] s = project(new Vec3(f.tx() + 0.5 + Math.cos(a) * r, f.endY(), f.tz() + 0.5 + Math.sin(a) * r));
                    if (s != null) g.fill(s[0] - 1, s[1] - 1, s[0] + 1, s[1] + 1, WmdUi.BLUE);
                }
            }
            int[] t = project(new Vec3(f.tx() + 0.5, f.endY(), f.tz() + 0.5));
            if (t != null) {
                g.fill(t[0] - 3, t[1], t[0] + 4, t[1] + 1, WmdUi.ORANGE);
                g.fill(t[0], t[1] - 3, t[0] + 1, t[1] + 4, WmdUi.ORANGE);
            }
            double[] a = aim(f);
            int[] c = a == null ? null : project(new Vec3(a[0], f.endY(), a[1]));
            if (c != null) {
                int col = held ? WmdUi.ORANGE : WmdUi.TEXT;
                g.fill(c[0] - 8, c[1], c[0] - 2, c[1] + 1, col);
                g.fill(c[0] + 3, c[1], c[0] + 9, c[1] + 1, col);
                g.fill(c[0], c[1] - 8, c[0] + 1, c[1] - 2, col);
                g.fill(c[0], c[1] + 3, c[0] + 1, c[1] + 9, col);
            }
            hint = Component.translatable("gui.wmd.tv.steer");
        }
        int hw = Math.min(font.width(hint), width - 20), cx = width / 2;
        g.fill(cx - hw / 2 - 6, height - 42, cx + hw / 2 + 6, height - 28, 0xB80F1318);
        g.fill(cx - hw / 2 - 6, height - 42, cx + hw / 2 + 6, height - 41, WmdUi.BLUE);
        WmdUi.drawFit(g, font, hint, cx - hw / 2, height - 38, hw, f == null ? WmdUi.TEXT_MUTED : WmdUi.TEXT);
    }
}
