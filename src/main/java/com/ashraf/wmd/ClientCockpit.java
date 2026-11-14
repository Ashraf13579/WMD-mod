package com.ashraf.wmd;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientCockpit {

    private ClientCockpit() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cockpit_hud");

    static final IKeyConflictContext IN_COCKPIT = new IKeyConflictContext() {
        @Override public boolean isActive() { return seated() != null; }
        @Override public boolean conflicts(IKeyConflictContext other) { return other == this; }
    };

    private static KeyMapping key(String name, int code) { return new KeyMapping("key.wmd.jet." + name, IN_COCKPIT, InputConstants.Type.KEYSYM, code, "key.categories.wmd_jet"); }

    static final KeyMapping PITCH_DOWN = key("pitch_down", GLFW.GLFW_KEY_W), PITCH_UP = key("pitch_up", GLFW.GLFW_KEY_S),
            ROLL_LEFT = key("roll_left", GLFW.GLFW_KEY_A), ROLL_RIGHT = key("roll_right", GLFW.GLFW_KEY_D),
            YAW_LEFT = key("yaw_left", GLFW.GLFW_KEY_Q), YAW_RIGHT = key("yaw_right", GLFW.GLFW_KEY_E),
            THR_UP = key("throttle_up", GLFW.GLFW_KEY_SPACE), THR_DOWN = key("throttle_down", GLFW.GLFW_KEY_LEFT_SHIFT),
            LOCK = key("lock", GLFW.GLFW_KEY_R), FLARES = key("flares", GLFW.GLFW_KEY_F), RADAR = key("radar", GLFW.GLFW_KEY_X),
            EJECT = key("eject", GLFW.GLFW_KEY_Z), MINIMAL = key("minimal_hud", GLFW.GLFW_KEY_H),
            STN1 = key("station1", GLFW.GLFW_KEY_1), STN2 = key("station2", GLFW.GLFW_KEY_2), STN3 = key("station3", GLFW.GLFW_KEY_3), STN4 = key("station4", GLFW.GLFW_KEY_4),
            JAMMER = key("jammer", GLFW.GLFW_KEY_J),
            CHECK = key("flight_check", GLFW.GLFW_KEY_C),
            GUN = key("gun", GLFW.GLFW_KEY_G),
            GEAR = key("gear", GLFW.GLFW_KEY_V), BRAKE = key("brake", GLFW.GLFW_KEY_B),
            DESIG = key("designate", GLFW.GLFW_KEY_T), PODCAM = key("pod_camera", GLFW.GLFW_KEY_K), AGMMODE = key("agm_mode", GLFW.GLFW_KEY_M),
            HOVER = key("hover", GLFW.GLFW_KEY_U), CHUTE = key("drag_chute", GLFW.GLFW_KEY_N),
            HOOK = key("cargo_hook", GLFW.GLFW_KEY_L), REFUEL = key("refuel", GLFW.GLFW_KEY_P),
            JETTISON = key("jettison", GLFW.GLFW_KEY_O),
            STORES = key("stores", GLFW.GLFW_KEY_I),
            RAMP = key("cargo_ramp", GLFW.GLFW_KEY_Y);

    static final KeyMapping THERMAL = new KeyMapping("key.wmd.jet.pod_thermal", IN_COCKPIT, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, "key.categories.wmd_jet");

    static final KeyMapping REVERSE = key("reverse", GLFW.GLFW_KEY_LEFT_CONTROL);

    private static final KeyMapping[] ALL = { PITCH_DOWN, PITCH_UP, ROLL_LEFT, ROLL_RIGHT, YAW_LEFT, YAW_RIGHT, THR_UP, THR_DOWN, LOCK, FLARES, RADAR, EJECT, MINIMAL, STN1, STN2, STN3, STN4, JAMMER, CHECK, GUN,
            GEAR, BRAKE, DESIG, PODCAM, AGMMODE, HOVER, CHUTE, HOOK, REFUEL, JETTISON, STORES, RAMP, THERMAL, REVERSE };

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Keys {
        @SubscribeEvent
        static void register(RegisterKeyMappingsEvent event) { if (!AirGate.AVAILABLE) return; for (KeyMapping k : ALL) event.register(k); }
    }

    private static long seatedAt = -1;
    private static net.minecraft.world.phys.Vec3 slewDir;
    private static float thr;
    private static boolean ab, thrPrimed, minimal;
    private static final int HOOK_HOLD = 20;
    private static int hookHold, rampHold, gearHold;
    private static final int GEAR_HOOK_HOLD = 10;
    private static int abHold, ejectHold, buttons, station, lastP, lastR, lastY, quiet, voiceCd, lockCd, launchCd, pingCd;
    private static float lastThr = -1;
    private static boolean lastAb, ejectSent, lastGun, lastBrake, lastRev;
    private static final boolean[] WAS = new boolean[ALL.length];
    private static int lastLineCount;
    private static HumSound hum;

    public static boolean inJetSeat() { return AirGate.on() && seated() != null; }

    static CockpitSeatEntity seated() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() instanceof CockpitSeatEntity s ? s : null;
    }

    private static boolean down(KeyMapping k) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return false;
        InputConstants.Key key = k.getKey();
        long win = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(win, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getValue() >= 0 && InputConstants.isKeyDown(win, key.getValue());
    }

    private static boolean pressed(int i, boolean now) {
        boolean e = now && !WAS[i];
        WAS[i] = now;
        return e;
    }

    @SubscribeEvent
    static void input(MovementInputUpdateEvent event) {
        if (!AirGate.on()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean jetSeat = seated() != null;
        boolean chute = mc.player != null && mc.player.getVehicle() instanceof EjectionSeatEntity e && !e.onGround();
        if (!jetSeat && !chute) return;
        var in = event.getInput();
        in.forwardImpulse = 0.0F;
        in.leftImpulse = 0.0F;
        in.jumping = false;
        in.shiftKeyDown = false;
        in.up = in.down = in.left = in.right = false;
    }

    @SubscribeEvent
    static void noInventory(ClientTickEvent.Pre event) {
        if (!AirGate.on() || seated() == null) return;
        Minecraft mc = Minecraft.getInstance();
        while (mc.options.keyInventory.consumeClick()) {  }

        if (mc.options.keyChat.same(DESIG)) while (mc.options.keyChat.consumeClick()) {  }
    }

    private static final java.util.Set<net.minecraft.resources.ResourceLocation> FLY_HIDE = java.util.Set.of(
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, net.neoforged.neoforge.client.gui.VanillaGuiLayers.SELECTED_ITEM_NAME,
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.EXPERIENCE_BAR, net.neoforged.neoforge.client.gui.VanillaGuiLayers.EXPERIENCE_LEVEL,
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.JUMP_METER, net.neoforged.neoforge.client.gui.VanillaGuiLayers.VEHICLE_HEALTH);

    @SubscribeEvent
    static void hideHotbar(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        if (AirGate.on() && FLY_HIDE.contains(event.getName()) && seated() != null) event.setCanceled(true);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST, receiveCanceled = true)
    static void scroll(InputEvent.MouseScrollingEvent event) {
        if (!AirGate.on() || seated() == null || event.getScrollDeltaY() == 0.0 || ClientPodCam.active()) return;
        CockpitHudPayload h = ClientCockpitBridge.hud();
        int n = Math.max(1, Math.min(Balance.JET_WEAPON_ENTRIES, h == null ? 4 : h.stnCount()));
        int cur = station > 0 ? station - 1 : (h == null ? 0 : h.station());
        int next = Math.floorMod(cur + (event.getScrollDeltaY() < 0 ? 1 : -1), n);
        station = next + 1;
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (!AirGate.on() || seated() == null) return;
        if (event.isAttack()) buttons |= JetCockpitBlockEntity.B_FIRE;
        else if (event.isUseItem()) buttons |= JetCockpitBlockEntity.B_LEAVE;
        else return;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (!AirGate.on()) return;
        CockpitSeatEntity s = seated();
        if (s == null) {
            if (seatedAt >= 0) { seatedAt = -1; thrPrimed = false; ab = false; abHold = ejectHold = 0; buttons = 0; ejectSent = false; }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (seatedAt < 0) seatedAt = System.currentTimeMillis();
        CockpitHudPayload h = ClientCockpitBridge.hud();
        if (!thrPrimed && h != null && h.cockpit().equals(s.cockpit())) { thr = h.throttle(); ab = h.has(CockpitHudPayload.F_AB); thrPrimed = true; }
        boolean[] d = new boolean[ALL.length];
        for (int i = 0; i < ALL.length; i++) d[i] = down(ALL[i]);
        int p = (d[0] ? 1 : 0) - (d[1] ? 1 : 0), r = (d[3] ? 1 : 0) - (d[2] ? 1 : 0), y = (d[5] ? 1 : 0) - (d[4] ? 1 : 0);

        float rate = (float) (Balance.COCKPIT_THROTTLE_RATE / 20.0);
        if (d[6] && !d[7]) {
            if (thr < 1.0F) { thr = Math.min(1.0F, thr + rate); abHold = 0; }
            else if (!ab && ++abHold >= Balance.COCKPIT_AB_HOLD) { ab = true; ui(WmdSounds.COCKPIT_SWITCH.get(), 0.9F, 0.6F); }
        } else abHold = 0;
        if (d[7] && !d[6]) { if (ab) ab = false; else thr = Math.max(0.0F, thr - rate); }
        if (ClientAutopilotBridge.flying(s.cockpit())) {
            ApHudPayload ah = ClientAutopilotBridge.hud(s.cockpit());
            if (d[6] || d[7] || p != 0 || r != 0 || y != 0) buttons |= JetCockpitBlockEntity.B_TAKE;
            else if (ah != null) { thr = ah.throttle(); ab = false; }
        }

        if (pressed(8, d[8])) buttons |= JetCockpitBlockEntity.B_LOCK;
        if (pressed(9, d[9])) buttons |= JetCockpitBlockEntity.B_FLARES;
        if (pressed(10, d[10])) buttons |= JetCockpitBlockEntity.B_RADAR;
        if (pressed(17, d[17])) buttons |= JetCockpitBlockEntity.B_JAM;
        if (pressed(18, d[18])) buttons |= JetCockpitBlockEntity.B_CHECK;

        if (d[20]) { if (++gearHold == GEAR_HOOK_HOLD) buttons |= JetCockpitBlockEntity.B_TAILHOOK; }
        else { if (gearHold > 0 && gearHold < GEAR_HOOK_HOLD) buttons |= JetCockpitBlockEntity.B_GEAR; gearHold = 0; }
        if (pressed(22, d[22])) buttons |= JetCockpitBlockEntity.B_DESIG;
        if (pressed(24, d[24])) buttons |= JetCockpitBlockEntity.B_AGMMODE;
        if (pressed(25, d[25])) buttons |= JetCockpitBlockEntity.B_HOVER;
        if (pressed(26, d[26])) buttons |= JetCockpitBlockEntity.B_CHUTE;
        if (pressed(30, d[30])) buttons |= JetCockpitBlockEntity.B_STORES;

        if (d[27]) { if (++hookHold == HOOK_HOLD) buttons |= JetCockpitBlockEntity.B_HOOK_REL; }
        else { if (hookHold > 0 && hookHold < HOOK_HOLD) buttons |= JetCockpitBlockEntity.B_HOOK; hookHold = 0; }
        if (pressed(28, d[28])) buttons |= JetCockpitBlockEntity.B_REFUEL;
        if (pressed(29, d[29])) buttons |= JetCockpitBlockEntity.B_JETTISON;

        if (d[31]) { if (++rampHold == Balance.RAMP_STICK_HOLD) buttons |= JetCockpitBlockEntity.B_RAMP_DROP; }
        else { if (rampHold > 0 && rampHold < Balance.RAMP_STICK_HOLD) buttons |= JetCockpitBlockEntity.B_RAMP; rampHold = 0; }
        if (pressed(23, d[23])) ClientPodCam.toggle(s, h);
        WAS[21] = d[21];
        if (pressed(12, d[12])) minimal = !minimal;
        for (int k = 0; k < 4; k++) if (pressed(13 + k, d[13 + k])) station = k + 1;
        WAS[11] = d[11];
        if (d[11]) { if (++ejectHold >= Balance.EJECT_HOLD_TICKS && !ejectSent) { buttons |= JetCockpitBlockEntity.B_EJECT; ejectSent = true; } }
        else { ejectHold = 0; ejectSent = false; }

        boolean gunNow = d[19] || (h != null && h.stnKind() == AirStores.GUN && mc.options.keyAttack.isDown() && mc.screen == null);
        net.minecraft.world.phys.Vec3 sd = ClientPodCam.takeSlew();
        if (sd != null) { buttons |= JetCockpitBlockEntity.B_SLEW; slewDir = sd; }
        boolean change = buttons != 0 || station != 0 || p != lastP || r != lastR || y != lastY || Math.abs(thr - lastThr) > 0.005F || ab != lastAb || gunNow != lastGun
                || d[21] != lastBrake || d[33] != lastRev;
        if (change || ++quiet >= 10) {
            net.minecraft.world.phys.Vec3 slewD = (buttons & JetCockpitBlockEntity.B_SLEW) != 0 ? slewDir : null;
            net.minecraft.world.phys.Vec3 aim = (buttons & (JetCockpitBlockEntity.B_DESIG | JetCockpitBlockEntity.B_SLEW)) != 0 ? ClientPodCam.eye() : null;
            net.minecraft.world.phys.Vec3 aimD = aim == null ? null : slewD != null && (buttons & JetCockpitBlockEntity.B_DESIG) == 0 ? slewD : ClientPodCam.lookDir();
            if (d[33]) buttons |= JetCockpitBlockEntity.B_REVERSE;
            PacketDistributor.sendToServer(new CockpitInputPayload(s.cockpit(), s.seatIndex(), p, r, y, thr, ab, buttons, station, gunNow, d[21],
                    aim == null ? Float.NaN : (float) aim.x, aim == null ? Float.NaN : (float) aim.y, aim == null ? Float.NaN : (float) aim.z,
                    aimD == null ? Float.NaN : (float) aimD.x, aimD == null ? Float.NaN : (float) aimD.y, aimD == null ? Float.NaN : (float) aimD.z));
            buttons = 0; station = 0; lastP = p; lastR = r; lastY = y; lastThr = thr; lastAb = ab; lastGun = gunNow; lastBrake = d[21]; lastRev = d[33]; quiet = 0;
        }
        sounds(mc, s, h);
    }

    private static void ui(SoundEvent e, float pitch, float vol) { Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, vol)); }

    private static void sounds(Minecraft mc, CockpitSeatEntity s, CockpitHudPayload h) {
        boolean power = h != null && h.cockpit().equals(s.cockpit()) && h.has(CockpitHudPayload.F_POWER);
        if (power && (hum == null || hum.isStopped())) { hum = new HumSound(); mc.getSoundManager().play(hum); }
        if (voiceCd > 0) voiceCd--;
        if (lockCd > 0) lockCd--;
        if (launchCd > 0) launchCd--;
        if (pingCd > 0) pingCd--;
        if (!power) return;
        boolean voice = Config.cockpitVoice();
        List<RwrPayload.Line> lines = ClientRwr.lines();
        boolean lock = false, launch = false;
        for (RwrPayload.Line l : lines) { if (l.kind() == Rwr.LOCK) lock = true; if (l.kind() == Rwr.LAUNCH) launch = true; }
        if (lines.size() > lastLineCount && pingCd == 0) { ui(WmdSounds.RWR_SEARCH.get(), 1.0F, 0.7F); pingCd = 40; }
        lastLineCount = lines.size();
        if (launch && launchCd == 0) { ui(WmdSounds.RWR_LAUNCH.get(), 1.0F, 0.9F); launchCd = 10; }
        else if (lock && lockCd == 0) { ui(WmdSounds.RWR_LOCK.get(), 1.0F, 0.8F); lockCd = 20; }
        if (launch && voice && voiceCd == 0) { ui(WmdSounds.VOICE_MISSILE.get(), 1.0F, 1.0F); voiceCd = 80; }
        if (pullUp(h) && voice && voiceCd == 0) { ui(WmdSounds.VOICE_PULL_UP.get(), 1.0F, 1.0F); voiceCd = 50; }

        if (h.has(CockpitHudPayload.F_GROWL) && mc.player != null && mc.player.tickCount % 4 == 0) ui(WmdSounds.RWR_SEARCH.get(), 0.6F, 0.45F);
    }

    static boolean pullUp(CockpitHudPayload h) {
        return h != null && h.agl() < Balance.COCKPIT_PULLUP_AGL && h.vy() < -1.0F && h.agl() / -h.vy() < Balance.COCKPIT_PULLUP_TTI;
    }

    private static final class HumSound extends AbstractTickableSoundInstance {
        HumSound() {
            super(WmdSounds.COCKPIT_HUM.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            looping = true; delay = 0; volume = 0.0F; relative = true; attenuation = Attenuation.NONE;
        }
        @Override public void tick() {
            CockpitSeatEntity s = seated();
            CockpitHudPayload h = ClientCockpitBridge.hud();
            boolean on = AirGate.on() && s != null && h != null && h.has(CockpitHudPayload.F_POWER);
            volume = Math.max(0.0F, Math.min(Balance.SND_AMBIENT, volume + (on ? 0.01F : -0.02F)));
            if (!on && volume <= 0.0F) stop();
        }
    }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { if (!AirGate.AVAILABLE) return; event.registerAboveAll(LAYER, ClientCockpit::render); }

    private static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!AirGate.on() || mc.options.hideGui) return;
        CockpitSeatEntity s = seated();
        if (s == null) return;
        CockpitHudPayload h = ClientCockpitBridge.hud();
        if (h == null || !h.cockpit().equals(s.cockpit())) return;
        if (ClientAutopilot.active()) return;
        long now = System.currentTimeMillis();
        float slide = Math.min(1.0F, (now - Math.max(seatedAt, 0)) / 250.0F);
        float t = (now - seatedAt) / 1000.0F;
        JetHud.render(g, mc.font, h, ClientRwr.lines(), minimal, thr, ab, ejectHold / (float) Balance.EJECT_HOLD_TICKS, slide,
                t < 8.0F ? 1.0F : Math.max(0.0F, 1.0F - (t - 8.0F)), dt.getGameTimeDeltaPartialTick(true));
        if (!minimal) ApHud.render(g, mc.font, s.cockpit());
    }

    static BlockPos seatedCockpit() { CockpitSeatEntity s = seated(); return s == null ? null : s.cockpit(); }
}
