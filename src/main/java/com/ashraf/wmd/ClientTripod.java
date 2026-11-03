package com.ashraf.wmd;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientTripod {

    private ClientTripod() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tripod_scope");
    private static final ResourceLocation SCOPE = ResourceLocation.withDefaultNamespace("textures/misc/spyglass_scope.png");
    private static final int[] ZOOMS = { 2, 4, 8 };

    private static BlockPos active;
    private static Marker cam;
    private static int zoom = 1;
    private static boolean on;
    private static float sentYaw = Float.NaN, sentPitch = Float.NaN;
    private static boolean hit;
    private static BlockPos hitPos = BlockPos.ZERO;
    private static int hitDist;

    public static BlockPos active() { return active; }

    private static LaserTripodBlockEntity tripod(Minecraft mc) {
        return active != null && mc.level != null && mc.level.getBlockEntity(active) instanceof LaserTripodBlockEntity t ? t : null;
    }

    public static void enter(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        active = pos;
        LaserTripodBlockEntity t = tripod(mc);
        on = t != null && t.lasing();
        if (t != null && on) mc.player.setYRot(t.yaw());
        if (t != null && on) mc.player.setXRot(t.pitch());
        cam = new Marker(EntityType.MARKER, mc.level);
        place(mc);
        mc.setCameraEntity(cam);
        mc.player.displayClientMessage(Component.translatable("hud.wmd.tripod.enter"), true);
    }

    public static void leave() {
        Minecraft mc = Minecraft.getInstance();
        active = null;
        cam = null;
        AreaViewClient.SCOPE.close();
        insetAt = -1L;
        sentYaw = sentPitch = Float.NaN;
        if (mc.player != null) mc.setCameraEntity(mc.player);
    }

    private static void place(Minecraft mc) {
        if (cam == null || mc.player == null || active == null) return;

        Vec3 s = LaserTripodBlockEntity.sightOf(mc.level, active, mc.level == null ? null : mc.level.getBlockState(active))
                .add(LaserTripodBlockEntity.dir(mc.player.getYRot(), mc.player.getXRot()).scale(0.6));
        cam.setPos(s.x, s.y, s.z);
        cam.xo = cam.xOld = s.x;
        cam.yo = cam.yOld = s.y;
        cam.zo = cam.zOld = s.z;
        cam.setYRot(mc.player.getYRot());
        cam.setXRot(mc.player.getXRot());
        cam.yRotO = mc.player.getYRot();
        cam.xRotO = mc.player.getXRot();
    }

    @SubscribeEvent
    static void frame(RenderFrameEvent.Pre event) {
        if (active != null) place(Minecraft.getInstance());
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (active == null) return;
        LocalPlayer p = mc.player;
        LaserTripodBlockEntity t = tripod(mc);
        if (p == null || t == null || !p.isAlive() || mc.screen != null || p.isShiftKeyDown()
                || p.distanceToSqr(Vec3.atCenterOf(active)) > Balance.TRIPOD_REACH * Balance.TRIPOD_REACH) { leave(); return; }
        on = t.lasing() || on && p.tickCount % 20 != 0;
        if (on && p.tickCount % 4 == 0 && (Math.abs(Mth.wrapDegrees(p.getYRot() - sentYaw)) > 0.15F || Math.abs(p.getXRot() - sentPitch) > 0.15F || Float.isNaN(sentYaw))) send(p, true);
        if (p.tickCount % 4 == 0) {
            Vec3 from = new Vec3(active.getX() + 0.5, active.getY() + 25.4 / 16.0, active.getZ() + 0.5);
            Vec3 dir = LaserTripodBlockEntity.dir(p.getYRot(), p.getXRot());
            Vec3 to = from.add(dir.scale(Balance.BEAM_RANGE));
            BlockHitResult b = mc.level.clip(new ClipContext(from.add(dir.scale(LaserTripodBlockEntity.CLEAR)), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cam));
            hit = b.getType() == HitResult.Type.BLOCK;
            if (hit) {
                Vec3 at = b.getLocation();
                if (SableBridge.isOnShip(mc.level, b.getBlockPos())) {
                    Vec3 w = SableBridge.toWorld(mc.level, at);
                    at = w == null || w.distanceToSqr(at) < 1.0E-6 ? SableBridge.worldPos(mc.level, b.getBlockPos()) : w;
                }
                hitPos = BlockPos.containing(at);
                hitDist = (int) Math.round(at.distanceTo(from));
                if (hitDist > Balance.BEAM_RANGE + 8) hit = false;
            }
        }
    }

    private static void send(LocalPlayer p, boolean laser) {
        sentYaw = p.getYRot();
        sentPitch = p.getXRot();
        PacketDistributor.sendToServer(new TripodAimPayload(active, p.getYRot(), p.getXRot(), laser));
    }

    @SubscribeEvent
    static void input(MovementInputUpdateEvent event) {
        if (active == null) return;
        var in = event.getInput();
        in.forwardImpulse = 0.0F;
        in.leftImpulse = 0.0F;
        in.jumping = false;
        in.up = in.down = in.left = in.right = false;
    }

    @SubscribeEvent
    static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (active == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        on = !on;
        send(mc.player, on);
        WmdUi.click();
    }

    @SubscribeEvent
    static void scroll(InputEvent.MouseScrollingEvent event) {
        if (active == null) return;
        event.setCanceled(true);
        zoom = Mth.clamp(zoom + (event.getScrollDeltaY() > 0 ? 1 : -1), 0, ZOOMS.length);
    }

    @SubscribeEvent
    static void fov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) {
        if (active != null && zoom > 0 && event.usedConfiguredFov()) event.setFOV(event.getFOV() / ZOOMS[zoom - 1]);
    }

    @SubscribeEvent
    static void hands(RenderHandEvent event) {
        if (active != null) event.setCanceled(true);
    }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(LAYER, ClientTripod::render); }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LaserTripodBlockEntity t = tripod(mc);
        if (t == null || mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int size = Math.min(w, h);
        int x0 = (w - size) / 2, y0 = (h - size) / 2, x1 = x0 + size, y1 = y0 + size;
        RenderSystem.enableBlend();
        g.blit(SCOPE, x0, y0, 0.0F, 0.0F, size, size, size, size);
        RenderSystem.disableBlend();
        g.fill(0, y1, w, h, 0xFF000000);
        g.fill(0, 0, w, y0, 0xFF000000);
        g.fill(0, y0, x0, y1, 0xFF000000);
        g.fill(x1, y0, w, y1, 0xFF000000);
        int cx = w / 2, cy = h / 2, red = on ? 0xFFFF2020 : 0xFFFF8080;
        g.fill(cx - 8, cy, cx - 1, cy + 1, red);
        g.fill(cx + 2, cy, cx + 9, cy + 1, red);
        g.fill(cx, cy - 8, cx + 1, cy - 1, red);
        g.fill(cx, cy + 2, cx + 1, cy + 9, red);
        if (on) g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFF3030);
        Font font = mc.font;

        Component range = hit ? Component.translatable("hud.wmd.tripod.range", String.format("%,d", hitDist), hitPos.getX(), hitPos.getY(), hitPos.getZ())
                : on && t.lasing() && t.beamLen() < Balance.BEAM_RANGE - 1 ? Component.translatable("hud.wmd.tripod.range_far", String.format("%,d", (int) Math.round(t.beamLen())))
                : Component.translatable("hud.wmd.tripod.no_range", Balance.BEAM_RANGE);
        Component state = Component.translatable(on ? "hud.wmd.tripod.lasing" : "hud.wmd.tripod.idle", zoom > 0 ? ZOOMS[zoom - 1] : 1,
                t.energy() * 100 / Math.max(1, Balance.TRIPOD_BUFFER));
        Component hint = Component.translatable("hud.wmd.tripod.hint");
        g.drawString(font, range, cx - font.width(range) / 2, cy + 16, hit ? 0xFFFFE0A0 : 0xFFB0B0B0, true);
        g.drawString(font, state, cx - font.width(state) / 2, cy + 28, on ? 0xFFFF6060 : 0xFFE0E0E0, true);
        g.drawString(font, hint, cx - font.width(hint) / 2, Math.min(h - 12, y1 - 24), 0xFFE0E0E0, true);
        if (on && t.lasing() && t.beamLen() > 2.0) inset(g, mc, t, w, y0);
    }

    private static int insetX = Integer.MIN_VALUE, insetZ;
    private static long insetAt = -1L, markersAt = -1L;

    private static void inset(GuiGraphics g, Minecraft mc, LaserTripodBlockEntity t, int w, int y0) {
        if (mc.level == null) return;
        net.minecraft.world.phys.Vec3 spot = LaserTripodBlockEntity.sightOf(mc.level, t.getBlockPos(), t.getBlockState()).add(LaserTripodBlockEntity.dir(t.yaw(), t.pitch()).scale(t.beamLen()));
        int sx = (int) Math.floor(spot.x), sz = (int) Math.floor(spot.z);
        long now = mc.level.getGameTime();
        var m = AreaViewClient.SCOPE;
        if (insetAt < 0 || now - insetAt > 200 || Math.abs(sx - insetX) > 16 || Math.abs(sz - insetZ) > 16) {
            insetX = sx; insetZ = sz; insetAt = now;
            m.request(AreaView.MODE_SCOPE, sx, sz, Balance.AREA_SCOPE_SIZE, false);
        } else if (m.ready() && now - markersAt >= 20) { markersAt = now; m.request(AreaView.MODE_SCOPE, m.cx(), m.cz(), m.size(), true); }
        int size = 112, x = w - size - 8, y = y0 + 8;
        g.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xC0101418);
        if (m.ready()) m.draw(g, x, y, size, size, 30.0F, 55.0F, 1.0F, sx, sz, Integer.MIN_VALUE, 0);
        else g.drawCenteredString(mc.font, Component.translatable(m.refused().isEmpty() ? "gui.wmd.desig.loading" : m.refused()), x + size / 2, y + size / 2 - 4, 0xFFB0B0B0);
        g.drawString(mc.font, Component.translatable("hud.wmd.tripod.inset"), x, y + size + 4, 0xFFE0E0E0, true);
    }
}
