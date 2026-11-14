package com.ashraf.wmd;

import java.util.BitSet;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientGunner {

    private ClientGunner() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gunner_camera");

    private static GunnerPayloads.Hud hud;
    private static Marker cam;
    private static BlockPos station;
    private static int zoom;
    private static Vec3 lock, aim;

    private static boolean free;
    private static float forcedYaw, forcedPitch;
    private static long enteredAt;
    private static boolean podView;
    private static int quiet, buttons;
    private static final boolean[] WAS = new boolean[3];

    static void onHud(GunnerPayloads.Hud p) { hud = p; podView = p.has(GunnerStationBlockEntity.F_POD); }

    static void onAim(GunnerPayloads.Aim p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(p.pos()) instanceof GunnerGun t) t.clientAim(p.yaw(), p.elev(), p.deploy(), p.flags());
    }

    static void onFire(GunnerPayloads.Fire p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (p.kind() == HeavyGunBlockEntity.SHOT_KIND) { heavyShot(level, p); return; }
        if (p.kind() >= NavalGunBlockEntity.SHOT_KIND && p.kind() < NavalGunBlockEntity.HEAVY_KIND + 3) { ClientNavalGun.shot(level, p); return; }
        if (level.getBlockEntity(p.pos()) instanceof GunTurretBlockEntity t) t.clientKick();
        Vec3 m = new Vec3(p.fx(), p.fy(), p.fz()), to = new Vec3(p.tx(), p.ty(), p.tz());
        logMiss("turret", m, to, level.getGameTime());
        Vec3 d = to.subtract(m);
        double len = d.length();
        if (len < 0.5) return;
        Vec3 dir = d.scale(1.0 / len);
        var rnd = level.random;
        float flick = 0.7F + rnd.nextFloat() * 0.3F;
        VeilBridge.flash(m.x + dir.x * 0.6, m.y + dir.y * 0.6, m.z + dir.z * 0.6, 1.0F, 0.72F, 0.3F, 9.0F, 1.1F * flick, 2);

        if (cam == null && rnd.nextInt(4) == 0)
            level.addParticle(ParticleTypes.SMOKE, m.x + dir.x * 0.8, m.y + dir.y * 0.8, m.z + dir.z * 0.8, dir.x * 0.05, 0.02, dir.z * 0.05);
        ClientCiws.smallRound(level, m.add(dir.scale(0.5)), to, true);

        Vec3 ej = m.subtract(dir.scale(1.1));
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 v = side.scale(-0.12 - rnd.nextDouble() * 0.08).add(0, -0.05, 0);
        level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)), ej.x, ej.y, ej.z, v.x, v.y, v.z);
        if (rnd.nextBoolean()) level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.IRON_NUGGET)), ej.x, ej.y, ej.z, v.x * 0.6, -0.08, v.z * 0.6);

        if (p.kind() == GunTurretBlockEntity.HIT_GROUND) {
            BlockState bs = level.getBlockState(BlockPos.containing(to.add(dir.scale(0.15))));
            if (bs.isAir()) bs = level.getBlockState(BlockPos.containing(to.add(dir.scale(0.5))));
            if (!bs.isAir()) for (int i = 0; i < 4; i++)
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, bs), to.x, to.y + 0.1, to.z, (rnd.nextDouble() - 0.5) * 0.3, 0.15 + rnd.nextDouble() * 0.2, (rnd.nextDouble() - 0.5) * 0.3);
            level.addParticle(ParticleTypes.POOF, to.x, to.y + 0.2, to.z, 0.0, 0.04, 0.0);
        } else if (p.kind() == GunTurretBlockEntity.HIT_TARGET) {
            level.addParticle(ParticleTypes.CRIT, to.x, to.y, to.z, 0.4, 0.4, 0.4);
            level.addParticle(ParticleTypes.POOF, to.x, to.y, to.z, 0.0, 0.05, 0.0);
            VeilBridge.flash(to.x, to.y, to.z, 1.0F, 0.8F, 0.4F, 10.0F, 1.0F, 2);
        }
    }

    private static void heavyShot(ClientLevel level, GunnerPayloads.Fire p) {
        Vec3 m = new Vec3(p.fx(), p.fy(), p.fz()), to = new Vec3(p.tx(), p.ty(), p.tz());
        Vec3 d = to.subtract(m);
        if (d.lengthSqr() < 1.0E-6) return;
        Vec3 dir = d.normalize();
        if (level.getBlockEntity(p.pos()) instanceof HeavyGunBlockEntity h) h.clientKick();
        WmdSound.playNF(WmdSounds.HG_SHOT.get(), WmdSounds.HG_SHOT_FAR.get(), LaunchSounds.FAR_FROM, m.x, m.y, m.z, 14.0F, 0.95F + level.random.nextFloat() * 0.1F);
        var rnd = level.random;
        long t = level.getGameTime();
        VeilBridge.glow(0x48E0_0000_0000_0001L ^ p.pos().asLong(), m.x + dir.x * 1.5, m.y + dir.y * 1.5, m.z + dir.z * 1.5, 1.0F, 0.72F, 0.3F, 12.0F, 1.0F, t);
        VeilBridge.flash(m.x + dir.x * 2.0, m.y + dir.y * 2.0, m.z + dir.z * 2.0, 1.0F, 0.7F, 0.28F, 26.0F, 2.0F, 3);
        for (int i = 0; i < 6; i++) {
            double sp = 0.15 + rnd.nextDouble() * 0.25;
            level.addParticle(ParticleTypes.FLAME, m.x + dir.x, m.y + dir.y, m.z + dir.z, dir.x * sp + (rnd.nextDouble() - 0.5) * 0.1, dir.y * sp + (rnd.nextDouble() - 0.5) * 0.1, dir.z * sp + (rnd.nextDouble() - 0.5) * 0.1);
        }

        if (cam == null) {
            for (int i = 0; i < 4; i++)
                level.addParticle(ParticleTypes.LARGE_SMOKE, m.x + dir.x * (1.5 + i * 0.6), m.y + dir.y * (1.5 + i * 0.6), m.z + dir.z * (1.5 + i * 0.6), dir.x * 0.06, 0.02, dir.z * 0.06);
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, m.x + dir.x * 2.5, m.y + dir.y * 2.5, m.z + dir.z * 2.5, dir.x * 0.04, 0.04, dir.z * 0.04);
        }
        Vec3 ej = m.subtract(dir.scale(2.6));
        level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_INGOT)), ej.x, ej.y, ej.z, -dir.x * 0.08, -0.05, -dir.z * 0.08);
        level.addParticle(ParticleTypes.SMOKE, ej.x, ej.y, ej.z, 0.0, 0.03, 0.0);
    }

    private static long nextMissLog;

    static void logMiss(String what, Vec3 from, Vec3 hit, long now) {
        if (cam == null || aim == null || now < nextMissLog) return;
        nextMissLog = now + 20;
        Wmd.LOGGER.info(String.format(java.util.Locale.ROOT, "[gunner-aim] %s %s: aim (%.1f, %.1f, %.1f) hit (%.1f, %.1f, %.1f) miss %.1f blocks (sideways %.1f) from (%.1f, %.1f, %.1f) cam (%.1f, %.1f, %.1f)",
                what, free ? "FREE" : "HELD", aim.x, aim.y, aim.z, hit.x, hit.y, hit.z, hit.distanceTo(aim), sideMiss(from, hit, aim), from.x, from.y, from.z, cam.getX(), cam.getY(), cam.getZ()));
    }

    private static double sideMiss(Vec3 from, Vec3 hit, Vec3 aim) {
        Vec3 d = aim.subtract(from);
        if (d.lengthSqr() < 1.0E-6) return 0.0;
        d = d.normalize();
        Vec3 r = hit.subtract(from);
        return r.subtract(d.scale(r.dot(d))).length();
    }

    static boolean ownShot(Vec3 muzzle) { return cam != null && cam.position().distanceToSqr(muzzle) < 144.0; }

    static BlockPos seated() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() instanceof GunnerSeatEntity s ? s.station() : null;
    }

    public static boolean active() { return cam != null; }

    private static void enter(Minecraft mc, BlockPos st) {
        station = st;
        zoom = 0; lock = null; aim = null; podView = false; free = false;
        enteredAt = mc.level.getGameTime();
        cam = new Marker(EntityType.MARKER, mc.level);
        mc.setCameraEntity(cam);
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(WmdSounds.CCTV_SWITCH.get(), 1.1F, 0.6F));
    }

    private static void leave(Minecraft mc) {
        if (cam == null) return;
        cam = null; station = null; lock = null; aim = null; hud = null;
        if (mc.player != null) mc.setCameraEntity(mc.player);
    }

    public static void clearStatics() { cam = null; station = null; lock = null; aim = null; hud = null; }

    @SubscribeEvent
    static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { clearStatics(); }

    private static GunnerGun focus(Minecraft mc) {
        if (hud == null || hud.focus() == GunnerStationBlockEntity.NONE || mc.level == null) return null;
        return mc.level.getBlockEntity(BlockPos.of(hud.focus())) instanceof GunnerGun t ? t : null;
    }

    private static Vec3 lens(Minecraft mc) {
        if (podView && hud != null && hud.pod() != GunnerStationBlockEntity.NONE && mc.level.getBlockEntity(BlockPos.of(hud.pod())) instanceof TargetingPodBlockEntity tp) {
            Vec3 l = tp.lensLocal();
            return ShipFrame.drawnWorld(mc.level, tp.getBlockPos(), l, Float.NaN);
        }
        GunnerGun t = focus(mc);
        if (t == null) return null;
        float[] s = t.shownPose();
        Vec3 l = t.lensPlot(s[0], s[1], s[2]);
        return ShipFrame.drawnWorld(mc.level, BlockPos.of(hud.focus()), l, Float.NaN);
    }

    private static Vec3 cast(Minecraft mc, Vec3 from, Vec3 look) {
        GunnerGun fg = focus(mc);
        double range = fg != null ? fg.eyeRange() : Balance.GTUR_RANGE;
        Vec3 to = from.add(look.scale(range));
        BlockPos own = hud != null && hud.focus() != GunnerStationBlockEntity.NONE ? BlockPos.of(hud.focus()) : station;
        BlockHitResult h = ShipFrame.clipPastShip(mc.level, own, new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
        if (h.getType() != HitResult.Type.BLOCK || h.getLocation().distanceToSqr(from) > (range + 4.0) * (range + 4.0)) return to;
        return h.getLocation();
    }

    static Vec3 viewDir(Minecraft mc) { return Vec3.directionFromRotation(mc.player.getXRot(), mc.player.getYRot()); }

    private static void setLook(Minecraft mc, Vec3 d) {
        if (d.lengthSqr() < 1.0E-10) return;
        d = d.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) Math.toDegrees(-Math.asin(Mth.clamp(d.y, -1.0, 1.0)));
        mc.player.setYRot(yaw); mc.player.yRotO = yaw;
        mc.player.setXRot(pitch); mc.player.xRotO = pitch;
    }

    private static double ahead(Minecraft mc) {
        if (podView) return 0.0;
        GunnerGun g = focus(mc);
        return g == null ? 0.0 : g instanceof HeavyGunBlockEntity ? Balance.GUNNER_CAM_AHEAD_HEAVY : Balance.GUNNER_CAM_AHEAD_TURRET;
    }

    private static Vec3 eyePoint(Minecraft mc) {
        Vec3 w = lens(mc);
        if (w == null) return mc.player.getEyePosition();
        return w.add(viewDir(mc).scale(ahead(mc)));
    }

    private static boolean clampLook(Minecraft mc) {
        if (podView || hud == null || mc.level == null) return false;
        GunnerGun g = focus(mc);
        if (g == null) return false;
        BlockPos gp = BlockPos.of(hud.focus());
        Vec3 o = Vec3.atCenterOf(gp), w0 = ShipFrame.drawnWorld(mc.level, gp, o, Float.NaN);
        Vec3 ex = ShipFrame.drawnWorld(mc.level, gp, o.add(1.0, 0.0, 0.0), Float.NaN).subtract(w0);
        Vec3 ey = ShipFrame.drawnWorld(mc.level, gp, o.add(0.0, 1.0, 0.0), Float.NaN).subtract(w0);
        Vec3 ez = ShipFrame.drawnWorld(mc.level, gp, o.add(0.0, 0.0, 1.0), Float.NaN).subtract(w0);
        Vec3 look = viewDir(mc);
        Vec3 dp = new Vec3(look.dot(ex), look.dot(ey), look.dot(ez));
        Vec3 cp = g.clampAimPlot(dp, Balance.GUNNER_FIELD_MARGIN);
        if (cp == dp || cp.distanceToSqr(dp) < 1.0E-10) return false;
        Vec3 cw = ex.scale(cp.x).add(ey.scale(cp.y)).add(ez.scale(cp.z));
        if (cw.lengthSqr() < 1.0E-8) return false;
        setLook(mc, cw);
        return true;
    }

    private static void place(Minecraft mc) {
        if (cam == null || mc.player == null) return;
        Vec3 l = lens(mc), base = l != null ? l : mc.player.getEyePosition();
        double ahead = l != null ? ahead(mc) : 0.0;
        if (lock != null) {
            float py = mc.player.getYRot(), pp = mc.player.getXRot();
            if (Math.abs(Mth.wrapDegrees(py - forcedYaw)) > 0.01F || Math.abs(pp - forcedPitch) > 0.01F) {
                Vec3 v = viewDir(mc);
                lock = cast(mc, base.add(v.scale(ahead)), v);
            } else {
                Vec3 d = lock.subtract(base);
                if (d.length() > ahead + 0.25) setLook(mc, d);
            }
        }
        if (clampLook(mc) && lock != null) {
            Vec3 v = viewDir(mc);
            lock = cast(mc, base.add(v.scale(ahead)), v);
        }
        forcedYaw = mc.player.getYRot(); forcedPitch = mc.player.getXRot();
        Vec3 w = base.add(viewDir(mc).scale(ahead));
        cam.setPos(w.x, w.y, w.z);
        cam.xo = cam.xOld = w.x;
        cam.yo = cam.yOld = w.y;
        cam.zo = cam.zOld = w.z;
        cam.setYRot(mc.player.getYRot());
        cam.setXRot(mc.player.getXRot());
        cam.yRotO = mc.player.getYRot();
        cam.xRotO = mc.player.getXRot();
    }

    @SubscribeEvent
    static void frame(RenderFrameEvent.Pre event) {
        if (cam != null) place(Minecraft.getInstance());
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
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos st = AirGate.on() ? seated() : null;
        if (st == null || mc.level == null || mc.player == null || !mc.player.isAlive()) { leave(mc); return; }
        if (cam == null || !st.equals(station)) { leave(mc); enter(mc, st); }
        if (mc.screen != null) return;

        if (pressed(0, down(ClientCockpit.LOCK))) buttons |= GunnerStationBlockEntity.B_NEXT;
        if (pressed(1, down(ClientCockpit.PODCAM))) buttons |= GunnerStationBlockEntity.B_POD;
        Vec3 eye = eyePoint(mc);

        if (!free && lock == null) { lock = cast(mc, eye, viewDir(mc)); forcedYaw = mc.player.getYRot(); forcedPitch = mc.player.getXRot(); }
        aim = lock != null ? lock : cast(mc, eye, viewDir(mc));
        boolean fire = down(mc.options.keyAttack);
        if (buttons != 0 || ++quiet >= 1) {
            PacketDistributor.sendToServer(new GunnerPayloads.Input(st, (float) aim.x, (float) aim.y, (float) aim.z, fire, buttons));
            buttons = 0; quiet = 0;
        }
    }

    @SubscribeEvent
    static void input(MovementInputUpdateEvent event) {
        if (cam == null) return;
        var in = event.getInput();
        in.forwardImpulse = 0.0F;
        in.leftImpulse = 0.0F;
        in.jumping = false;
        in.up = in.down = in.left = in.right = false;
    }

    @SubscribeEvent
    static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (cam == null) return;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void scroll(InputEvent.MouseScrollingEvent event) {
        if (cam == null) return;
        event.setCanceled(true);
        zoom = Mth.clamp(zoom + (event.getScrollDeltaY() > 0 ? 1 : -1), 0, Balance.GUNNER_ZOOMS.length - 1);
    }

    @SubscribeEvent
    static void fov(ViewportEvent.ComputeFov event) {
        if (cam != null && event.usedConfiguredFov()) event.setFOV(event.getFOV() / Balance.GUNNER_ZOOMS[zoom]);
    }

    @SubscribeEvent
    static void hands(RenderHandEvent event) { if (cam != null) event.setCanceled(true); }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { if (!AirGate.AVAILABLE) return; event.registerAboveAll(LAYER, ClientGunner::render); }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (cam == null || mc.options.hideGui || mc.level == null || mc.player == null) return;
        Font font = mc.font;
        int w = g.guiWidth(), hh = g.guiHeight(), cx = w / 2, cy = hh / 2;
        GunnerPayloads.Hud h = hud != null && hud.station().equals(station) ? hud : null;
        boolean blocked = h != null && h.has(GunnerStationBlockEntity.F_BLOCKED), hit = h != null && h.has(GunnerStationBlockEntity.F_HIT);
        int frame = 0xE0101418, line = blocked ? 0xFFFF5C5C : 0xFF7FD8FF, dim = blocked ? 0xB0FF5C5C : 0xB07FD8FF;

        g.fill(0, 0, w, 18, frame);
        g.fill(0, hh - 30, w, hh, frame);
        ApHud.renderGunner(g, font, null);
        int m = 26, k = 18;
        g.fill(m, 24, m + k, 25, line); g.fill(m, 24, m + 1, 24 + k, line);
        g.fill(w - m - k, 24, w - m, 25, line); g.fill(w - m - 1, 24, w - m, 24 + k, line);
        g.fill(m, hh - 37, m + k, hh - 36, line); g.fill(m, hh - 37 - k, m + 1, hh - 36, line);
        g.fill(w - m - k, hh - 37, w - m, hh - 36, line); g.fill(w - m - 1, hh - 37 - k, w - m, hh - 36, line);

        int r = 5;
        g.fill(cx - 60, cy, cx - 10, cy + 1, dim); g.fill(cx + 10, cy, cx + 60, cy + 1, dim);
        g.fill(cx, cy - 44, cx + 1, cy - 10, dim); g.fill(cx, cy + 10, cx + 1, cy + 44, dim);
        for (int i = 1; i <= 3; i++) g.fill(cx - 3, cy + 10 + i * 8, cx + 4, cy + 11 + i * 8, dim);
        g.fill(cx, cy, cx + 1, cy + 1, line);
        if (lock != null) { g.fill(cx - r - 4, cy - r - 4, cx + r + 5, cy - r - 3, line); g.fill(cx - r - 4, cy + r + 4, cx + r + 5, cy + r + 5, line);
            g.fill(cx - r - 4, cy - r - 4, cx - r - 3, cy + r + 5, line); g.fill(cx + r + 4, cy - r - 4, cx + r + 5, cy + r + 5, line); }
        if (blocked) for (int i = -8; i <= 8; i++) { g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, line); g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, line); }
        if (hit) { Component t = Component.translatable("hud.wmd.gunner.hit"); g.drawString(font, t, cx + 14, cy - 14, WmdUi.ORANGE_LIGHT, false); }

        Component title = Component.translatable("hud.wmd.gunner.title");
        g.drawString(font, title, 8, 5, WmdUi.TEXT, false);
        int x = 8 + font.width(title) + 10;
        String sel = h == null ? "--" : h.count() == 0 ? "--" : h.selected() < 0 ? Component.translatable("hud.wmd.gunner.all").getString()
                : (h.has(GunnerStationBlockEntity.F_HEAVY) ? "H" : "T") + (h.selected() + 1) + "/" + h.count();
        g.drawString(font, sel, x, 5, WmdUi.CYAN, false);
        x += font.width(sel) + 10;
        g.drawString(font, "x" + (int) Balance.GUNNER_ZOOMS[zoom], x, 5, WmdUi.CYAN, false);
        Component st = Component.translatable(state(h));
        int stc = blocked || (h != null && (h.has(GunnerStationBlockEntity.F_EMPTY) || h.has(GunnerStationBlockEntity.F_NOT_OWNER))) ? 0xFFFF5C5C
                : h != null && h.has(GunnerStationBlockEntity.F_FIRING) ? WmdUi.ORANGE_LIGHT : WmdUi.TEXT_MUTED;
        g.drawString(font, st, w - 8 - font.width(st), 5, stc, false);
        if (podView) { Component pv = Component.translatable("hud.wmd.gunner.pod_view"); g.drawString(font, pv, cx - font.width(pv) / 2, 5, WmdUi.ORANGE_LIGHT, false); }

        String rds = h == null ? "--" : h.rounds() + " / " + h.roundsMax();
        g.drawString(font, Component.translatable("hud.wmd.gunner.rounds", rds), 8, hh - 25, WmdUi.TEXT, false);
        String rng = aim == null ? "--" : String.format("%,d", Math.round(aim.distanceTo(cam.position())));
        g.drawString(font, Component.translatable("hud.wmd.gunner.range", rng), 8, hh - 13, WmdUi.TEXT_MUTED, false);
        if (h != null && h.has(GunnerStationBlockEntity.F_HEAVY) && aim != null) {
            String tof = String.format("%.1f", Math.max(2.0, Math.ceil(aim.distanceTo(cam.position()) / (focus(mc) != null ? focus(mc).eyeSpeed() : Balance.HGUN_SPEED))) / 20.0);
            g.drawString(font, Component.translatable("hud.wmd.gunner.tof", tof), 8 + font.width(Component.translatable("hud.wmd.gunner.range", rng)) + 14, hh - 13, WmdUi.CYAN, false);
        }
        Component hint = Component.translatable("hud.wmd.gunner.hint", ClientCockpit.LOCK.getTranslatedKeyMessage(), ClientCockpit.PODCAM.getTranslatedKeyMessage());
        g.drawString(font, hint, w - 8 - font.width(hint) - 90, hh - 19, WmdUi.TEXT_DIM, false);
        dial(g, font, h, w - 48, hh - 76);
        if (mc.level.getGameTime() - enteredAt < 4) g.fill(0, 0, w, hh, 0xFF000000);
    }

    private static String state(GunnerPayloads.Hud h) {
        if (h == null) return "hud.wmd.gunner.state.link";
        if (h.has(GunnerStationBlockEntity.F_NO_CRAFT)) return "hud.wmd.gunner.state.no_craft";
        if (h.has(GunnerStationBlockEntity.F_NOT_OWNER)) return "hud.wmd.gunner.state.not_owner";
        if (h.has(GunnerStationBlockEntity.F_NO_TURRET)) return "hud.wmd.gunner.state.no_turret";
        if (h.has(GunnerStationBlockEntity.F_EMPTY)) return "hud.wmd.gunner.state.empty";
        if (h.has(GunnerStationBlockEntity.F_BLOCKED)) return "hud.wmd.gunner.state.arc";
        if (h.has(GunnerStationBlockEntity.F_FIRING)) return "hud.wmd.gunner.state.firing";
        if (h.has(GunnerStationBlockEntity.F_READY)) return !free && lock != null ? "hud.wmd.gunner.state.locked" : "hud.wmd.gunner.state.ready";
        if (h.has(GunnerStationBlockEntity.F_DEPLOYING)) return "hud.wmd.gunner.state.deploying";
        return "hud.wmd.gunner.state.stowed";
    }

    private static void dial(GuiGraphics g, Font font, GunnerPayloads.Hud h, int x0, int y0) {
        int R = 34;
        g.fill(x0 - R - 6, y0 - R - 6, x0 + R + 7, y0 + R + 16, 0xC0101418);
        for (int a = 0; a < 360; a += 6) {
            double t = Math.toRadians(a);
            int px = x0 + (int) Math.round(Math.sin(t) * R), py = y0 + (int) Math.round(Math.cos(t) * R);
            g.fill(px, py, px + 1, py + 1, 0x807FD8FF);
        }
        if (h != null && h.has(GunnerStationBlockEntity.F_ARC) && h.arc().length > 0) {
            BitSet b = BitSet.valueOf(h.arc());
            for (int c = 0; c < GunTurretBlockEntity.COLS; c++)
                for (int r = 0; r < GunTurretBlockEntity.ROWS; r++) {
                    if (!b.get(c * GunTurretBlockEntity.ROWS + r)) continue;
                    double t = Math.toRadians(c * Balance.GTUR_ARC_STEP), rad = R * (1.0 - r * Balance.GTUR_ARC_STEP / 90.0);
                    int px = x0 + (int) Math.round(Math.sin(t) * rad), py = y0 + (int) Math.round(Math.cos(t) * rad);
                    g.fill(px - 1, py - 1, px + 1, py + 1, 0xC0FF5C5C);
                }
        }
        if (h != null && h.focus() != GunnerStationBlockEntity.NONE) {
            double t = Math.toRadians(h.yaw()), rad = R * (1.0 - h.elev() / 90.0);
            int px = x0 + (int) Math.round(Math.sin(t) * rad), py = y0 + (int) Math.round(Math.cos(t) * rad);
            g.fill(px - 2, py - 2, px + 2, py + 2, 0xFF7FD8FF);
        }
        Component aft = Component.translatable("hud.wmd.gunner.aft");
        g.drawString(font, aft, x0 - font.width(aft) / 2, y0 + R + 5, WmdUi.TEXT_DIM, false);
    }
}
