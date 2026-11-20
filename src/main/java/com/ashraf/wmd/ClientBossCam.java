package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientBossCam {

    private ClientBossCam() {}

    private static WardenCinePayload cur;
    private static Marker cam;
    private static boolean hidGui;
    private static Vec3 lastWarden = Vec3.ZERO;

    public static boolean active() { return cur != null; }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { if (cur != null) stop(); WardenCinePayload.PENDING.clear(); return; }
        for (WardenCinePayload p; (p = WardenCinePayload.PENDING.poll()) != null; ) start(mc, p);
        if (cur != null && mc.level.getGameTime() >= cur.end()) stop();
    }

    private static void start(Minecraft mc, WardenCinePayload p) {
        if (cur != null) stop();
        if (ClientCutscene.active()) ClientCutscene.stop();
        cur = p;
        lastWarden = new Vec3(p.cx(), p.cy(), p.cz());
        cam = new Marker(EntityType.MARKER, mc.level);
        cam.setPos(mc.player.getEyePosition());
        mc.setCameraEntity(cam);
        hidGui = !mc.options.hideGui;
        mc.options.hideGui = true;
        mc.setScreen(new Shot());
    }

    static void stop() {
        Minecraft mc = Minecraft.getInstance();
        cur = null;
        cam = null;
        if (hidGui) mc.options.hideGui = false;
        hidGui = false;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (mc.screen instanceof Shot) mc.setScreen(null);
    }

    @SubscribeEvent
    static void onScreen(ScreenEvent.Opening event) {
        if (cur != null && !(event.getNewScreen() instanceof Shot)) event.setCanceled(true);
    }

    private static float ease(double x) { float k = (float) Mth.clamp(x, 0.0, 1.0); return k * k * (3.0F - 2.0F * k); }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        if (cur == null || cam == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { stop(); return; }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double t = mc.level.getGameTime() + pt - cur.start(), len = Math.max(1, cur.end() - cur.start()), u = Mth.clamp(t / len, 0.0, 1.0);
        Entity e = mc.level.getEntity(cur.warden());
        Vec3 w = e != null ? e.getPosition(pt) : lastWarden;
        if (e != null) lastWarden = w;
        double yr = Math.toRadians(cur.yaw());
        Vec3 f = new Vec3(-Math.sin(yr), 0.0, Math.cos(yr)), r = new Vec3(-Math.cos(yr), 0.0, -Math.sin(yr));
        Vec3 pos, look;
        switch (cur.kind()) {
            case WardenCine.INTRO -> {
                pos = new Vec3(cur.cx(), cur.floorY() + 2.0 + 3.0 * u, cur.cz()).add(f.scale(19.0 - 4.0 * ease(u))).add(r.scale(5.0 - 3.0 * u));
                look = new Vec3(w.x, Math.max(cur.floorY() - 4.0, w.y + 10.6), w.z);
            }
            case WardenCine.OVERDRIVE -> {
                double a = yr + Math.toRadians(20.0 + 90.0 * ease(u)), rad = 17.0 - 3.0 * u;
                pos = new Vec3(w.x - Math.sin(a) * rad, w.y + 9.0, w.z + Math.cos(a) * rad);
                look = w.add(0.0, 9.5, 0.0);
            }
            case WardenCine.MELTDOWN -> {
                Vec3 low = w.add(f.scale(9.0)).add(0.0, 3.0, 0.0), high = w.add(f.scale(22.0)).add(r.scale(6.0)).add(0.0, 16.0, 0.0);
                float k = ease((u - 0.45) / 0.55);
                pos = low.lerp(high, k);
                look = w.add(f.scale(3.0)).add(0.0, 9.0, 0.0).lerp(w.add(0.0, 8.0, 0.0), k);
            }
            default -> {
                double a = yr + Math.toRadians(200.0 + 60.0 * u), rad = 20.0 + 6.0 * u;
                pos = new Vec3(w.x - Math.sin(a) * rad, w.y + 8.0 + 6.0 * u, w.z + Math.cos(a) * rad);
                look = w.add(0.0, 4.0 - 2.0 * u, 0.0);
            }
        }
        pos = new Vec3(Mth.clamp(pos.x, cur.ox() - 26.0, cur.ox() + 26.0), Mth.clamp(pos.y, cur.floorY() + 1.0, cur.floorY() + 24.0), Mth.clamp(pos.z, cur.oz() - 26.0, cur.oz() + 26.0));
        cam.setPos(pos);
        cam.xo = pos.x; cam.yo = pos.y; cam.zo = pos.z;
        cam.xOld = pos.x; cam.yOld = pos.y; cam.zOld = pos.z;
        Vec3 d = look.subtract(pos);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        cam.setYRot(yaw); cam.setXRot(pitch); cam.yRotO = yaw; cam.xRotO = pitch;
    }

    static final class Shot extends Screen {

        Shot() { super(Component.translatable("gui.wmd.warden.cine")); }

        @Override public boolean isPauseScreen() { return false; }
        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { }
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return true; }
        @Override public boolean mouseClicked(double mx, double my, int button) { return true; }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            if (cur == null || minecraft == null || minecraft.level == null) return;
            double t = minecraft.level.getGameTime() + pt - cur.start(), len = Math.max(1, cur.end() - cur.start());
            float bars = ease(Math.min(t / 10.0, (len - t) / 10.0));
            int bar = (int) (height / 8.0F * bars);
            g.fill(0, 0, width, bar, 0xFF000000);
            g.fill(0, height - bar, width, height, 0xFF000000);
            String key = switch (cur.kind()) { case WardenCine.INTRO -> "intro"; case WardenCine.OVERDRIVE -> "overdrive"; case WardenCine.MELTDOWN -> "meltdown"; default -> "defeat"; };
            double from = cur.kind() == WardenCine.INTRO ? len - 50.0 : len * 0.35;
            if (t >= from) {
                int a = (int) (255 * Mth.clamp(Math.min((t - from) / 12.0, (len - t) / 8.0), 0.0, 1.0));
                if (a > 8) {
                    g.pose().pushPose();
                    g.pose().translate(width / 2.0F, height * 0.22F, 0.0F);
                    g.pose().scale(3.0F, 3.0F, 1.0F);
                    Component title = Component.translatable("gui.wmd.warden.cine." + key);
                    g.drawString(font, title, -font.width(title) / 2, 0, (a << 24) | 0xE6ECF4, false);
                    g.pose().popPose();
                    Component sub = Component.translatable("gui.wmd.warden.cine." + key + ".sub");
                    g.drawString(font, sub, width / 2 - font.width(sub) / 2, (int) (height * 0.22F) + 32, (a << 24) | 0x9FB0C4, false);
                }
            }
        }
    }
}
