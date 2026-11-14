package com.ashraf.wmd;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientAutopilot {

    private ClientAutopilot() {}

    static final KeyMapping AUTOPILOT = new KeyMapping("key.wmd.jet.autopilot", ClientCockpit.IN_COCKPIT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, "key.categories.wmd_jet");

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Keys {
        @SubscribeEvent
        static void register(RegisterKeyMappingsEvent event) { if (!AirGate.AVAILABLE) return; event.register(AUTOPILOT); }
    }

    private static final double AP_VIEW = 0.32;
    private static Marker cam;
    private static BlockPos cockpit;
    private static int seatIdx, held, panTicks;
    private static boolean down, wantScreen, backing, quickSent;
    private static long panStart;
    private static float fromYaw, fromPitch;

    public static boolean active() { return cam != null; }

    static void clearStatics() { cam = null; cockpit = null; held = 0; down = false; wantScreen = false; backing = false; }

    @SubscribeEvent
    static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { clearStatics(); }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (!AirGate.on()) return;
        Minecraft mc = Minecraft.getInstance();
        CockpitSeatEntity s = ClientCockpit.seated();
        if (s == null) { if (cam != null) end(mc); held = 0; down = false; lastReason = 0; lastMode = Autopilot.OFF; return; }
        tone(mc, s);
        if (cam != null) {
            if (backing && mc.screen == null && ticksSince(mc) >= Balance.AP_PAN_BACK) end(mc);
            if (!backing && wantScreen && mc.screen == null && ticksSince(mc) >= Balance.AP_PAN_TICKS && ClientAutopilotBridge.state(cockpit) != null) {
                wantScreen = false;
                mc.setScreen(new AutopilotScreen(cockpit));
            }
            return;
        }
        boolean now = mc.screen == null && keyDown(mc, AUTOPILOT);
        if (now) {
            held++;
            if (held == Balance.AP_QUICK_HOLD && !quickSent) { quickSent = true; PacketDistributor.sendToServer(new ApPayloads.Ask(s.cockpit(), ApPayloads.QUICK, 0, new CompoundTag())); }
        } else {
            if (down && held < Balance.AP_QUICK_HOLD) start(mc, s);
            held = 0; quickSent = false;
        }
        down = now;
    }

    private static int lastReason, lastMode;

    private static void tone(Minecraft mc, CockpitSeatEntity s) {
        ApHudPayload a = ClientAutopilotBridge.hud(s.cockpit());
        int r = a == null ? 0 : a.reason(), m = a == null ? lastMode : a.mode();
        if (r != lastReason && r != Autopilot.R_NONE) {
            boolean off = r <= Autopilot.R_EMPTY || r == Autopilot.R_OFF;
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(off ? WmdSounds.AP_DISCONNECT.get() : WmdSounds.COCKPIT_SWITCH.get(), off ? 1.0F : 1.2F, 0.8F));
        }
        boolean flying = m != Autopilot.OFF && m != Autopilot.PARKED, was = lastMode != Autopilot.OFF && lastMode != Autopilot.PARKED;
        if (flying && !was) mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(WmdSounds.AP_ENGAGE.get(), 1.0F, 0.7F));
        lastReason = r; lastMode = m;
    }

    private static boolean keyDown(Minecraft mc, KeyMapping k) {
        InputConstants.Key key = k.getKey();
        long win = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getValue() >= 0 && InputConstants.isKeyDown(win, key.getValue());
    }

    private static int ticksSince(Minecraft mc) { return mc.level == null ? 999 : (int) (mc.level.getGameTime() - panStart); }

    private static void start(Minecraft mc, CockpitSeatEntity s) {
        if (mc.level == null || mc.player == null || ClientGunner.active() || ClientPodCam.active()) return;
        cockpit = s.cockpit(); seatIdx = s.seatIndex();
        cam = new Marker(EntityType.MARKER, mc.level);
        panStart = mc.level.getGameTime();
        fromYaw = mc.player.getYRot(); fromPitch = mc.player.getXRot();
        wantScreen = true; backing = false;
        mc.setCameraEntity(cam);
        place(mc, 0.0F);
        PacketDistributor.sendToServer(new ApPayloads.Ask(cockpit, ApPayloads.OPEN, 0, new CompoundTag()));
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(WmdSounds.COCKPIT_SWITCH.get(), 1.3F, 0.4F));
    }

    static void back() {
        Minecraft mc = Minecraft.getInstance();
        if (cam == null || mc.level == null) return;
        backing = true; wantScreen = false;
        panStart = mc.level.getGameTime();
    }

    private static void end(Minecraft mc) {
        cam = null; backing = false; wantScreen = false;
        if (mc.player != null) mc.setCameraEntity(mc.player);
    }

    private static double[] screenLocal(JetCockpitBlockEntity be) {
        if (be.deck()) return new double[] { 0.0, 11.1 / 16.0, 11.5 / 16.0, 0.0, 0.866, -0.5 };
        if (be.tandem() && seatIdx == 1) return new double[] { -3.3 / 16.0, 9.0 / 16.0, 25.3 / 16.0, 0.0, 0.0, -1.0 };
        double pf = (be.tandem() ? 32.0 : 0.0) + 26.0;
        return new double[] { -3.3 / 16.0, 9.8 / 16.0, (pf - 0.6) / 16.0, 0.0, 0.0, -1.0 };
    }

    private static void place(Minecraft mc, float pt) {
        if (cam == null || mc.player == null || mc.level == null || !(mc.level.getBlockEntity(cockpit) instanceof JetCockpitBlockEntity be)) return;
        double[] s = screenLocal(be);
        Vec3 c = be.plotPoint(s[0], s[1], s[2]), n = be.plotPoint(s[0] + s[3], s[1] + s[4], s[2] + s[5]);

        Vec3 cw = ShipFrame.drawnWorld(mc.level, cockpit, c, pt), nw = ShipFrame.drawnWorld(mc.level, cockpit, n, pt);
        if (cw == null || nw == null) return;
        Vec3 out = nw.subtract(cw).normalize();
        Vec3 to = cw.add(out.scale(AP_VIEW));
        Vec3 from = mc.player.getEyePosition(pt);
        float t = (ticksSince(mc) + pt) / (backing ? Balance.AP_PAN_BACK : Balance.AP_PAN_TICKS);
        float k = WmdUi.easeOut(Mth.clamp(t, 0.0F, 1.0F));
        if (backing) k = 1.0F - k;
        Vec3 p = from.lerp(to, k);
        Vec3 d = cw.subtract(to);
        float yawTo = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitchTo = (float) Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z)));
        float yaw = fromYaw + Mth.wrapDegrees(yawTo - fromYaw) * k, pitch = Mth.lerp(k, fromPitch, pitchTo);
        cam.setPos(p.x, p.y, p.z);
        cam.xo = cam.xOld = p.x; cam.yo = cam.yOld = p.y; cam.zo = cam.zOld = p.z;
        cam.setYRot(yaw); cam.setXRot(pitch); cam.yRotO = yaw; cam.xRotO = pitch;
    }

    @SubscribeEvent
    static void frame(RenderFrameEvent.Pre event) { if (cam != null) place(Minecraft.getInstance(), event.getPartialTick().getGameTimeDeltaPartialTick(true)); }

    @SubscribeEvent
    static void hands(RenderHandEvent event) { if (cam != null) event.setCanceled(true); }

    @SubscribeEvent
    static void tabList(RenderGuiLayerEvent.Pre event) {
        if (AirGate.on() && event.getName().equals(VanillaGuiLayers.TAB_LIST) && ClientCockpit.seated() != null && AUTOPILOT.getKey().getValue() == GLFW.GLFW_KEY_TAB) event.setCanceled(true);
    }
}
