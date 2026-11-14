package com.ashraf.wmd;

import org.lwjgl.glfw.GLFW;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.DeltaTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientHelm {

    private ClientHelm() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "helm_hud");

    private static HelmHudPayload hud;
    private static long hudAt, seatedAt = -1;
    private static boolean wasUp, wasDown, leave;
    private static int throttle, rudder, planes, bow, lastRudder, lastPlanes, lastBow, quiet;

    static final IKeyConflictContext IN_HELM = new IKeyConflictContext() {
        @Override public boolean isActive() { return seatedHelm() != null; }
        @Override public boolean conflicts(IKeyConflictContext other) { return other == this; }
    };
    static final KeyMapping BOW_LEFT = new KeyMapping("key.wmd.helm.bow_left", IN_HELM, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.wmd_helm"),
            BOW_RIGHT = new KeyMapping("key.wmd.helm.bow_right", IN_HELM, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.wmd_helm");

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Keys {
        @SubscribeEvent
        static void register(RegisterKeyMappingsEvent event) { if (!AirGate.AVAILABLE) return; event.register(BOW_LEFT); event.register(BOW_RIGHT); }
    }

    private static boolean held(KeyMapping k) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !AirGate.AVAILABLE) return false;
        InputConstants.Key key = k.getKey();
        long win = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(win, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getValue() >= 0 && InputConstants.isKeyDown(win, key.getValue());
    }

    static BlockPos seatedHelm() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() instanceof HelmSeatEntity s ? s.helm() : null;
    }

    static void onHud(HelmHudPayload p) {
        hud = p;
        hudAt = System.currentTimeMillis();
    }

    @SubscribeEvent
    static void input(MovementInputUpdateEvent event) {
        if (seatedHelm() == null) return;
        var in = event.getInput();
        if (in.up && !wasUp) throttle++;
        if (in.down && !wasDown) throttle--;
        wasUp = in.up; wasDown = in.down;
        rudder = (in.right ? 1 : 0) - (in.left ? 1 : 0);
        planes = (in.shiftKeyDown ? 1 : 0) - (in.jumping ? 1 : 0);
        bow = (held(BOW_RIGHT) ? 1 : 0) - (held(BOW_LEFT) ? 1 : 0);
        in.forwardImpulse = 0.0F;
        in.leftImpulse = 0.0F;
        in.jumping = false;
        in.shiftKeyDown = false;
        in.up = in.down = in.left = in.right = false;
    }

    @SubscribeEvent
    static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (seatedHelm() == null || !event.isUseItem()) return;
        leave = true;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        BlockPos helm = seatedHelm();
        if (helm == null) {
            if (seatedAt >= 0) { seatedAt = -1; hud = null; ShipHud.reset(); }
            throttle = 0; wasUp = wasDown = false; leave = false;
            return;
        }
        if (seatedAt < 0) { seatedAt = System.currentTimeMillis(); ShipHud.reset(); }
        boolean change = throttle != 0 || leave || rudder != lastRudder || planes != lastPlanes || bow != lastBow;
        if (change || ++quiet >= 10) {
            PacketDistributor.sendToServer(new HelmInputPayload(helm, Math.max(-2, Math.min(2, throttle)), rudder, planes, bow, leave));
            throttle = 0; leave = false; lastRudder = rudder; lastPlanes = planes; lastBow = bow; quiet = 0;
        }
    }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(LAYER, ClientHelm::render); }

    private static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || seatedHelm() == null) return;
        long now = System.currentTimeMillis();
        if (hud == null || now - hudAt > 2_000L) return;
        float slide = Math.min(1.0F, (now - Math.max(seatedAt, 0)) / 250.0F);
        ShipHud.render(g, mc.font, hud, slide);
        float t = (now - seatedAt) / 1000.0F;
        ShipHud.hints(g, mc.font, t < 7.0F ? 1.0F : Math.max(0.0F, 1.0F - (t - 7.0F)));
        ClientSeaPilot.renderHud(g, mc.font, slide);
    }
}
