package com.ashraf.wmd;

import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientCutscene {

    private ClientCutscene() {}

    private static CutscenePayload cur;
    private static long startTick;
    private static Marker cam;
    private static boolean hidGui, impacted, dhHidden, flew, boomed, roared, silenced, whined;
    private static int beats;
    private static float fov = 70.0F, roll;
    private static Vec3 pos = Vec3.ZERO;
    private static double[] lookAt = { 0, 0, 0 };
    private static Vec3 pivot;

    private static boolean photo, photoHint = true;
    private static double photoT;
    private static float photoYaw, photoPitch;
    private static Vec3 photoPos = Vec3.ZERO, photoOld = Vec3.ZERO;

    private static double time(float pt) { return photo ? photoT : ClientClock.now(pt) - startTick; }

    private static void togglePhoto() {
        if (cur == null) return;
        if (!photo) {
            photoT = ClientClock.now(0.0F) - startTick;
            photoPos = pos; photoOld = pos;
            Vec3 d = new Vec3(lookAt[0] - pos.x, lookAt[1] - pos.y, lookAt[2] - pos.z);
            photoYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            photoPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            photo = true;
        } else {
            startTick = ClientClock.ticks() - (long) photoT;
            photo = false;
        }
    }

    private static void photoTick(Minecraft mc) {
        com.mojang.blaze3d.platform.Window win = mc.getWindow();
        java.util.function.IntPredicate down = k -> com.mojang.blaze3d.platform.InputConstants.isKeyDown(win.getWindow(), k);
        double sp = down.test(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) ? 0.0 : down.test(org.lwjgl.glfw.GLFW.GLFW_KEY_R) ? 6.0 : 1.5;
        double yr = Math.toRadians(photoYaw), pr = Math.toRadians(photoPitch);
        Vec3 fwd = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)), side = new Vec3(Math.cos(yr), 0.0, Math.sin(yr));
        Vec3 mv = Vec3.ZERO;
        if (down.test(mc.options.keyUp.getKey().getValue())) mv = mv.add(fwd);
        if (down.test(mc.options.keyDown.getKey().getValue())) mv = mv.subtract(fwd);
        if (down.test(mc.options.keyLeft.getKey().getValue())) mv = mv.add(side);
        if (down.test(mc.options.keyRight.getKey().getValue())) mv = mv.subtract(side);
        if (down.test(mc.options.keyJump.getKey().getValue())) mv = mv.add(0.0, 1.0, 0.0);
        if (down.test(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)) mv = mv.add(0.0, -1.0, 0.0);
        photoOld = photoPos;
        if (mv.lengthSqr() > 1.0E-6) photoPos = photoPos.add(mv.normalize().scale(sp));
    }

    private static boolean rod() { return cur != null && cur.kind() == CutscenePayload.KIND_ROD; }
    private static boolean capture() { return cur != null && cur.kind() == CutscenePayload.KIND_CAPTURE; }
    private static boolean drop() { return cur != null && cur.kind() == CutscenePayload.KIND_DROP; }
    private static boolean tsar() { return cur != null && cur.kind() == CutscenePayload.KIND_TSAR; }
    private static boolean board() { return cur != null && cur.kind() == CutscenePayload.KIND_BOARD; }
    private static boolean stationCam() { return cur != null && cur.kind() == CutscenePayload.KIND_CAM; }

    static void watchStation(double x, double y, double z, double radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || unsafe(mc)) return;
        if (!mc.level.dimension().equals(Orbit.ORBIT)) { camUnavailable(); return; }
        start(mc, new CutscenePayload(CutscenePayload.KIND_CAM, (float) radius, 0.0F, 0.0F, (float) x, (float) y, (float) z, 0, Balance.STATION_CAM_TICKS, ""));
    }

    private static StationModuleBlockEntity modCam;
    private static final net.minecraft.resources.ResourceLocation LENS = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "textures/gui/lens_edge.png");

    static void camUnavailable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(mc.level.dimension().equals(Orbit.ORBIT)
                ? "message.wmd.station_cam.far" : "message.wmd.station_cam.orbit_only").withStyle(net.minecraft.ChatFormatting.YELLOW), true);
    }

    static StationModuleBlockEntity findModule(String type, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        net.minecraft.world.level.ChunkPos c = mc.player.chunkPosition();
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            if (!mc.level.hasChunk(c.x + dx, c.z + dz)) continue;
            for (var e : mc.level.getChunk(c.x + dx, c.z + dz).getBlockEntities().values())
                if (e instanceof StationModuleBlockEntity m && type.equals(m.type()) && Orbit.slotAt(e.getBlockPos().getX() + 0.5, e.getBlockPos().getZ() + 0.5) == slot) return m;
        }
        return null;
    }

    static void watchModule(StationModuleBlockEntity be) {
        var p = be.getBlockPos();
        watchStation(p.getX(), p.getY(), p.getZ(), 0.0);
    }

    private static Vec3 modWorld(StationModuleBlockEntity be, double fx, double fy, double fz) {
        boolean boom = Station.LASER.equals(be.type());
        float deg = switch (be.facing()) { case SOUTH -> -90.0F; case WEST -> 180.0F; case NORTH -> 90.0F; default -> 0.0F; };
        org.joml.Vector3f v = new org.joml.Vector3f((float) (fx / 16.0 - 0.5), (float) (fy / 16.0 - (boom ? 1.0 : 0.0)), (float) (fz / 16.0));
        new org.joml.Quaternionf().rotationY((float) Math.toRadians(deg)).transform(v);
        var p = be.getBlockPos();
        return new Vec3(p.getX() + 0.5 + v.x(), p.getY() + v.y(), p.getZ() + 0.5 + v.z());
    }

    static Vec3[] modCamSpot(StationModuleBlockEntity be) {
        String type = be.type();
        if (Station.ROD.equals(type)) return new Vec3[] { modWorld(be, 296.0, -30.0, 64.0), modWorld(be, 320.0, -420.0, 0.0) };
        if (Station.MIRROR.equals(type)) return new Vec3[] { modWorld(be, 500.0, 20.0, 96.0), modWorld(be, 480.0, -900.0, 0.0) };
        if (Station.BAY.equals(type)) return new Vec3[] { modWorld(be, 430.0, 240.0, 70.0), modWorld(be, 1400.0, 104.0, 0.0) };
        if (Station.LASER.equals(type)) {
            double r = Math.toRadians(be.aimRoll()), pt = Math.toRadians(be.aimPitch());
            double dx = Math.cos(pt), dy = Math.sin(pt) * Math.cos(r), dz = Math.sin(pt) * Math.sin(r);
            Vec3 eye = modWorld(be, 200.0, 40.0 + 58.0 * Math.cos(r), 58.0 * Math.sin(r));
            return new Vec3[] { eye, modWorld(be, 248.0 + dx * 4000.0, 40.0 + dy * 4000.0, dz * 4000.0) };
        }
        return new Vec3[] { modWorld(be, 0.0, 200.0, 60.0), modWorld(be, 0.0, 64.0, 0.0) };
    }

    private static void modCamScript() {
        Vec3[] s = modCamSpot(modCam);
        pos = s[0];
        lookAt = new double[] { s[1].x, s[1].y, s[1].z };
        fov = 110.0F;
        roll = 0.0F;
    }

    private static void camScript(double t) {
        CutscenePayload c = cur;
        double r = Math.max(12.0, c.sx()), ang = 0.6 + t * Math.PI * 2.0 / Balance.STATION_CAM_TURN;
        Vec3 m = new Vec3(c.dx(), c.dy(), c.dz());
        pos = m.add(Math.cos(ang) * r, r * (0.32 + 0.12 * Math.sin(t * 0.004)), Math.sin(ang) * r);
        lookAt = new double[] { m.x, m.y, m.z };
        fov = 62.0F;
        roll = 0.0F;
    }
    private static double hitT() { return cur.lead() + (rod() || drop() || tsar() ? 0 : Balance.RG_TRAVEL_TICKS); }
    private static double craterR() { return Missiles.craterRadius(4) * (drop() ? WarheadType.ASTEROID.crater : rod() ? WarheadType.ORBITAL_ROD.crater : tsar() ? WarheadType.TSAR.crater : WarheadType.RAILGUN.crater); }

    public static boolean active() { return cur != null; }

    public static Vec3 focus() { return cur == null ? Vec3.ZERO : new Vec3(cur.dx(), cur.dy(), cur.dz()); }

    private static boolean unsafe(Minecraft mc) {
        var p = mc.player;
        if (p == null || !p.isAlive()) return true;
        if (p.hurtTime > 0 || p.tickCount - p.getLastHurtByMobTimestamp() < 100 && p.getLastHurtByMob() != null) return true;
        return p.getLastHurtMob() != null && p.tickCount - p.getLastHurtMobTimestamp() < 100;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        CutscenePayload p = CutscenePayload.pending;
        if (p != null && mc.level != null && mc.player != null) {
            CutscenePayload.pending = null;
            if (!unsafe(mc) && !(mc.screen != null && !(mc.screen instanceof Cut) && mc.screen.isPauseScreen())) start(mc, p);
        }
        if (cur == null) {
            if (DhBridge.hiddenByUs()) { DhBridge.hideFar(false); dhHidden = false; Wmd.LOGGER.info("[cutscene] Distant Horizons was still hidden after a cutscene: given back"); }
            return;
        }
        if (mc.level == null || mc.player == null) { stop(); return; }
        if (photo) {
            if (mc.player.hurtTime > 0 || !(mc.screen instanceof Cut)) { stop(); return; }
            photoTick(mc);
            return;
        }
        long t = ClientClock.ticks() - startTick;
        if (t > cur.total() || mc.player.hurtTime > 0 || !(mc.screen instanceof Cut)) { stop(); return; }
        if (board()) { if (ClientCrewRide.active()) stop(); return; }
        if (stationCam()) return;
        if (capture()) {
            double u = t / (double) Math.max(1, cur.total());
            if (u < 0.4 && t % 100 == 0) ClientRailgun.flat(WmdSounds.TUG_ENGINE_LOOP.get(), 0.7F, 1.0F);
            if (!flew && u >= 0.42) { flew = true; ClientRailgun.flat(WmdSounds.TUG_CLAWS.get(), 1.0F, 1.0F); }
            if (!boomed && u >= 0.66) { boomed = true; ClientRailgun.flat(WmdSounds.TUG_NET.get(), 1.0F, 1.0F); }
            return;
        }
        double hit = hitT();
        if ((rod() || drop()) && !flew) { flew = true; ClientRailgun.flat(WmdSounds.REENTRY.get(), 1.0F, drop() ? 0.6F : 0.8F); }
        if ((rod() || drop()) && !boomed && t >= cur.lead() * 0.6) { boomed = true; ClientRailgun.flat(WmdSounds.SONIC_BOOM.get(), 1.0F, drop() ? 0.7F : 0.9F); }
        if (rod() && !silenced && t >= cur.lead() - 10) {
            silenced = true;
            mc.getSoundManager().stop();
        }
        if (rod() && !whined && t >= cur.lead() - 130) { whined = true; ClientRailgun.flat(WmdSounds.RD_WHINE.get(), 1.0F, 1.0F); }
        if (rod() && beats < 2 && t >= cur.lead() - 200 + 80 * beats && t < cur.lead() - 10) { ClientRailgun.flat(WmdSounds.RD_HEARTBEAT.get(), 0.9F, 1.0F + 0.12F * beats); beats++; }
        if ((rod() || drop()) && !roared && t >= cur.lead() - (drop() ? 220 : 115)) { roared = true; ClientRailgun.flat(drop() ? WmdSounds.AST_FALL.get() : WmdSounds.RD_FALL.get(), 1.0F, 1.0F); }
        if (!flew && t >= hit - 120 && cur.kind() == CutscenePayload.KIND_RAILGUN_REPLAY) { flew = true; ClientRailgun.flat(WmdSounds.RG_FLIGHT.get(), 1.0F, 1.0F); }
        if (!impacted && t >= hit) {
            impacted = true;
            double r = rod() && ClientRailgun.rodBroken(cur.dx(), cur.dz()) ? 40.0 : craterR();
            ClientSnapshot.spawnDebris(cur.dx(), cur.dy(), cur.dz(), r, ClientClock.ticks());
            if (cur.kind() == CutscenePayload.KIND_RAILGUN_REPLAY) ImpactFx.start(cur.dx(), cur.dy(), cur.dz(), 4, WarheadType.RAILGUN, 0);
            ClientReplay.hit();
            T4Fx.shakeNear(new Vec3(cur.dx(), cur.dy(), cur.dz()), 2000.0, 3.0F);
        }
    }

    private static void start(Minecraft mc, CutscenePayload p) {
        if (cur != null) stop();
        ClientReplay.cutscene(p);
        cur = p;
        modCam = p.kind() == CutscenePayload.KIND_CAM && p.sx() < 1.0F && mc.level != null
                && mc.level.getBlockEntity(net.minecraft.core.BlockPos.containing(p.dx(), p.dy(), p.dz())) instanceof StationModuleBlockEntity sm ? sm : null;
        impacted = false;
        flew = false; silenced = false; whined = false; beats = 0;
        boomed = false;
        roared = false;
        startTick = ClientClock.ticks();
        photo = false;
        cam = new Marker(EntityType.MARKER, mc.level);
        pos = mc.player.getEyePosition();
        cam.setPos(pos);
        mc.setCameraEntity(cam);
        hidGui = !mc.options.hideGui;
        mc.options.hideGui = true;
        mc.setScreen(new Cut());

        Vec3 m = new Vec3(p.sx(), p.sy(), p.sz());
        pivot = ClientRailgunShow.pivotNear(m);
        if (pivot == null) {
            Vec3 g = new Vec3(p.dx(), p.dy(), p.dz());
            Vec3 h = new Vec3(g.x - m.x, 0.0, g.z - m.z).normalize();
            pivot = m.subtract(h.scale(26.0)).subtract(0.0, 10.0, 0.0);
        }
    }

    private static int dropN = 1;
    private static double splitSeen;

    private static AsteroidBayPayloads.Fall dropFall;

    private static int dropPieces(double t) {
        for (Object[] e : AsteroidBayPayloads.FALLS.values()) {
            AsteroidBayPayloads.Fall p = (AsteroidBayPayloads.Fall) e[0];
            if (Math.abs(p.tx() + 0.5 - cur.dx()) > 2.0 || Math.abs(p.tz() + 0.5 - cur.dz()) > 2.0) continue;
            int n = Math.max(1, p.pieces());
            dropFall = p;
            if (n > dropN) { splitSeen = dropN == 1 && t < 5.0 ? -1000.0 : t; dropN = n; }
        }
        return dropN;
    }

    static void stop() {
        photo = false;
        modCam = null;
        ClientReplay.stopped();
        Minecraft mc = Minecraft.getInstance();
        dropN = 1;
        dropFall = null;
        cur = null;
        cam = null;
        fov = 70.0F; roll = 0.0F;
        ClientSnapshot.show = false;
        DhBridge.hideFar(false);
        dhHidden = false;
        if (hidGui) mc.options.hideGui = false;
        hidGui = false;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (mc.screen instanceof Cut) mc.setScreen(null);
    }

    static float ease(double x) { float k = (float) Mth.clamp(x, 0.0, 1.0); return k * k * (3.0F - 2.0F * k); }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        if (cur == null || cam == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { stop(); return; }
        double t = time(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (photo) {
            float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            pos = photoOld.lerp(photoPos, pt);
            double yr = Math.toRadians(photoYaw), pr = Math.toRadians(photoPitch);
            lookAt = new double[] { pos.x - Math.sin(yr) * Math.cos(pr) * 10.0, pos.y - Math.sin(pr) * 10.0, pos.z + Math.cos(yr) * Math.cos(pr) * 10.0 };
            roll = 0.0F;
        } else if (stationCam()) { if (modCam != null && !modCam.isRemoved()) modCamScript(); else camScript(t); } else if (board()) boardScript(t); else if (capture()) captureScript(t); else if (drop()) dropScript(slowCam(t)); else if (tsar()) tsarScript(t); else if (rod()) rodScript(t); else railgun(slowCam(t));

        double dc = Math.hypot(pos.x - cur.dx(), pos.z - cur.dz()), sr = ClientSnapshot.realR();
        boolean atTarget = !capture() && !board() && !stationCam() && (dc < sr * 1.5 || (ClientSnapshot.show && dc < sr * 3.0));

        ClientSnapshot.show = atTarget && ClientSnapshot.covers(cur.dx(), cur.dz()) && (ClientSnapshot.show || ClientSnapshot.complete() >= 0.85);
        if (ClientSnapshot.show != dhHidden) { DhBridge.hideFar(ClientSnapshot.show); dhHidden = ClientSnapshot.show; }
        cam.setPos(pos);
        cam.xo = pos.x; cam.yo = pos.y; cam.zo = pos.z;
        cam.xOld = pos.x; cam.yOld = pos.y; cam.zOld = pos.z;
        Vec3 d = new Vec3(lookAt[0] - pos.x, lookAt[1] - pos.y, lookAt[2] - pos.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        cam.setYRot(yaw); cam.setXRot(pitch); cam.yRotO = yaw; cam.xRotO = pitch;
    }

    private static double slowCam(double t) {
        double a = hitT() - Balance.CUT_SLOW_BEFORE, b = a + Balance.CUT_SLOW_TICKS, lag = Balance.CUT_SLOW_TICKS * (1.0 - Balance.CUT_SLOW_SPEED);
        if (t < a) return t;
        if (t < b) return a + (t - a) * Balance.CUT_SLOW_SPEED;
        return Math.min(t, t - lag + (t - b) * (Balance.CUT_CATCH_SPEED - 1.0));
    }

    @SubscribeEvent
    static void onRipples(RenderLevelStageEvent event) {
        if (cur == null || !impacted || capture() || board() || stationCam() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double t = time(event.getPartialTick().getGameTimeDeltaPartialTick(false)) - hitT();
        if (t < 0.0 || t > Balance.CUT_RIPPLE_TICKS + Balance.CUT_RIPPLE_GAP * Balance.CUT_RIPPLES) return;
        float cr = rod() ? 1.0F : drop() ? 1.0F : 0.55F, cg = rod() ? 0.35F : drop() ? 0.6F : 0.75F, cb = rod() ? 0.25F : drop() ? 0.25F : 1.0F;
        ClientBlastGeo.beginFrame();
        Vec3 c = event.getCamera().getPosition();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        double r0 = craterR();
        for (int k = 0; k < Balance.CUT_RIPPLES; k++) {
            double u = (t - k * Balance.CUT_RIPPLE_GAP) / Balance.CUT_RIPPLE_TICKS;
            if (u < 0.0 || u > 1.0) continue;
            double r = r0 * (1.0 + Balance.CUT_RIPPLE_REACH * Math.sqrt(u));
            float a = (float) (0.8 * (1.0 - u));
            int seg = 128;
            double px = 0, py = 0, pz = 0;
            for (int i = 0; i <= seg; i++) {
                double ang = i * Math.PI * 2 / seg, x = cur.dx() + Math.cos(ang) * r, z = cur.dz() + Math.sin(ang) * r;
                double y = ClientBlastGeo.ground(mc.level, x, z, cur.dy()) + 0.8;
                if (i > 0) ClientBlastGeo.streak(bb, c, px, py, pz, x, y, z, 1.5 + 4.0 * (1.0 - u), cr, cg, cb, a, a);
                px = x; py = y; pz = z;
            }
            any = true;
        }
        ClientBlastGeo.draw(event, bb, any, true);
    }

    static double floorY(Minecraft mc) {
        net.minecraft.client.multiplayer.ClientLevel lv = mc.level;
        return RailgunWreck.floor(cur.dx(), cur.dz(), cur.dy(), (bx, bz) -> lv != null && lv.hasChunk(bx >> 4, bz >> 4)
                ? lv.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) : ClientSnapshot.topAt(bx, bz));
    }

    @SubscribeEvent
    static void onRenderWreck(RenderLevelStageEvent event) {
        if (cur == null || rod() || capture() || drop() || tsar() || board() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double t = time(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        double hit = cur.lead() + Balance.RG_TRAVEL_TICKS;
        if (t < hit + Balance.RG_WRECK_CUT) return;
        if (t >= hit + Balance.RG_WRECK_DELAY && mc.level.hasChunk((int) Math.floor(cur.dx()) >> 4, (int) Math.floor(cur.dz()) >> 4)) return;
        Vec3 cam = event.getCamera().getPosition();
        double fy = floorY(mc);
        org.joml.Quaternionf q = RailgunWreck.orient(RailgunArc.dir(new Vec3(cur.sx(), cur.sy(), cur.sz()), new Vec3(cur.dx(), cur.dy(), cur.dz()), 1.0));
        com.mojang.blaze3d.vertex.PoseStack ps = event.getPoseStack();
        net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (RailgunWreck.Part part : RailgunWreck.PARTS) {
            ps.pushPose();
            ps.translate(cur.dx() - cam.x, fy - cam.y, cur.dz() - cam.z);
            ps.mulPose(q);
            ps.translate(-part.w() / 2.0F, part.y0(), -part.w() / 2.0F);
            ps.scale(part.w(), part.y1() - part.y0(), part.w());
            mc.getBlockRenderer().renderSingleBlock(part.state(), ps, buffers, part.glow() ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : net.minecraft.client.renderer.LightTexture.pack(0, 13),
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        buffers.endBatch();
    }

    private static void boardScript(double t) {
        Vec3 o = new Vec3(cur.sx(), cur.sy(), cur.sz());
        double r = cur.dx();
        double[] p = PadCrew.puppet(Math.min(t, PadCrew.ENTER - 1));
        Vec3 who = PadCrew.world(o, r, p[0], p[1] + 22.0, p[2]);
        double ph = Math.toRadians(PadCrew.DOOR_PHI);
        Vec3 c;
        roll = 0.0F;
        if (t < 40) {
            c = new Vec3(PadCrew.SX + Math.cos(ph + 0.9) * 72.0, PadCrew.PT + 36.0, PadCrew.SZ + Math.sin(ph + 0.9) * 72.0);
            lookAt = new double[] { who.x, who.y, who.z };
            fov = 62.0F;
        } else if (t < 110) {
            double q = ease((t - 40) / 70.0), a = ph - 0.6 - 0.9 * q, cy = PadCrew.PT + 20.0 + PadCrew.liftY(t);
            c = new Vec3(PadCrew.SX + Math.cos(a) * (66.0 - 14.0 * q), cy + 28.0 - 18.0 * q, PadCrew.SZ + Math.sin(a) * (66.0 - 14.0 * q));
            Vec3 car = PadCrew.world(o, r, PadCrew.SX, cy, PadCrew.SZ);
            lookAt = new double[] { car.x, car.y, car.z };
            fov = 66.0F;
        } else if (t < 180) {
            double f = ease((t - 110) / 70.0);
            c = PadCrew.arm(14.0 + (PadCrew.XW1 - 28.0) * f, PadCrew.FLOOR + 32.0, -36.0);
            lookAt = new double[] { who.x, who.y, who.z };
            fov = 64.0F;
        } else if (t < PadCrew.ENTER + 12) {
            c = PadCrew.arm(PadCrew.XW1 - 19.0, PadCrew.FLOOR + 30.0, 5.0);
            Vec3 h = PadCrew.hatchSpot(), hw = PadCrew.world(o, r, h.x, PadCrew.FLOOR + 14.0, h.z);
            lookAt = new double[] { hw.x, hw.y, hw.z };
            fov = 72.0F;
        } else {
            double q = ease((t - PadCrew.ENTER - 12) / 90.0);
            double a = Math.atan2(PadCrew.SZ - PadCrew.CZ, PadCrew.SX) + 0.35 + 0.0012 * (t - PadCrew.ENTER);
            double rad = 110.0 + 260.0 * q;
            c = new Vec3(Math.cos(a) * rad, PadCrew.CORE_TOP + 20.0 + 120.0 * q, PadCrew.CZ + Math.sin(a) * rad);
            Vec3 l = PadCrew.world(o, r, 0.0, PadCrew.CORE_TOP - 50.0 * q, PadCrew.CZ);
            lookAt = new double[] { l.x, l.y, l.z };
            fov = 64.0F;
        }
        pos = PadCrew.world(o, r, c.x, c.y, c.z);
    }

    private static void rodScript(double t) {
        CutscenePayload c = cur;
        Vec3 g = new Vec3(c.dx(), c.dy(), c.dz());
        double L = c.lead(), end = c.total(), R = craterR(), alt = c.sy() - c.dy();
        double baseAng = 0.7, shake = 0.0;
        if (t < L * 0.5) {
            double f = t / L, q = ease(t / (L * 0.5));
            Vec3 p = new Vec3(g.x, g.y + alt * ClientRodFx.heightLeft(f), g.z);
            pos = p.add(Math.cos(baseAng) * (26.0 - 6.0 * q), 34.0 - 10.0 * q, Math.sin(baseAng) * (26.0 - 6.0 * q));
            lookAt = new double[] { p.x, p.y - 40.0, p.z };
            fov = 74.0F;
            shake = 0.15 + 0.3 * q;
        } else if (t < L + 30) {

            double q = Math.min(1.0, (t - L * 0.5) / (L * 0.5));
            double ang = baseAng + Math.PI * 0.85;
            double off = R * (0.45 - 0.08 * ease(q)), hgt = R * (0.95 - 0.15 * ease(q));
            pos = g.add(Math.cos(ang) * off, hgt, Math.sin(ang) * off);
            double rodY = g.y + alt * Math.pow(Math.max(0.0, L - t) / L, 1.6);
            lookAt = t < L ? new double[] { g.x, g.y + Math.min(R * 0.3, (rodY - g.y) * 0.3), g.z } : new double[] { g.x, g.y + R * 0.1 * ease((t - L) / 20.0), g.z };
            fov = 70.0F;
            if (t >= L) shake = 2.6 * Math.max(0.0, 1.0 - (t - L) / 30.0);
        } else {
            double q = (t - L - 30) / Math.max(1.0, end - L - 30);
            double ang = baseAng + Math.PI * 0.85 + 1.1 * ease(q), rise = ease((q - 0.55) / 0.45);
            double off = R * (0.37 - 0.22 * rise), hgt = R * (0.8 + 0.6 * rise);
            pos = g.add(Math.cos(ang) * off, hgt, Math.sin(ang) * off);
            lookAt = new double[] { g.x, g.y + R * 0.1 - R * 0.2 * ease(q / 0.3), g.z };
            fov = 70.0F + 4.0F * (float) rise;
            shake = 0.25 * Math.max(0.0, 1.0 - q * 2.0);
        }
        roll = (float) (shake * Math.sin(t * 2.3) * 2.0);
        if (shake > 0) pos = pos.add(Math.sin(t * 3.1) * shake * 0.3, Math.sin(t * 4.7) * shake * 0.3, Math.cos(t * 3.7) * shake * 0.3);
    }

    private static void tsarScript(double t) {
        CutscenePayload c = cur;
        Vec3 g = new Vec3(c.dx(), c.dy(), c.dz()), b0 = new Vec3(c.sx(), c.sy(), c.sz());
        double L = Math.max(1.0, c.lead()), end = c.total(), R = craterR();
        Vec3 burst = new Vec3(b0.x, g.y + Balance.TSAR_BURST_H, b0.z), bomb = b0.lerp(burst, Math.min(1.0, t / L));
        double chute = Math.max(0.0, L - 100.0), shake = 0.0;
        if (t < chute) {
            double q = ease(t / Math.max(1.0, chute)), a = 0.6 + 0.8 * q;
            pos = bomb.add(Math.cos(a) * 14.0, 18.0, Math.sin(a) * 14.0);
            lookAt = new double[] { bomb.x, bomb.y - 25.0, bomb.z };
            fov = 70.0F;
        } else if (t < L + 40.0) {
            pos = g.add(Math.cos(0.6) * R * 0.45, R * 1.05, Math.sin(0.6) * R * 0.45);
            lookAt = new double[] { burst.x, g.y + Balance.TSAR_BURST_H * 0.6, burst.z };
            fov = 70.0F;
            shake = t >= L ? 0.6 * (1.0 - (t - L) / 40.0) : 0.0;
        } else {
            double q = ease((t - L - 40.0) / Math.max(1.0, end - L - 40.0)), ang = 0.6 + 0.9 * q;
            pos = g.add(Math.cos(ang) * R * (0.45 + 0.25 * q), R * (1.05 + 0.6 * q), Math.sin(ang) * R * (0.45 + 0.25 * q));
            lookAt = new double[] { g.x, g.y + Balance.TSAR_BURST_H * 0.6 * (1.0 - q) + R * 0.35 * q, g.z };
            fov = 70.0F + 6.0F * (float) q;
        }
        roll = (float) (shake * Math.sin(t * 2.3) * 2.0);
        if (shake > 0) pos = pos.add(Math.sin(t * 3.1) * shake * 0.3, Math.sin(t * 4.7) * shake * 0.3, Math.cos(t * 3.7) * shake * 0.3);
    }

    private static void dropScript(double t) {
        CutscenePayload c = cur;
        Vec3 g = new Vec3(c.dx(), c.dy(), c.dz()), s0 = new Vec3(c.sx(), c.sy(), c.sz());
        double L = c.lead(), end = c.total(), R = craterR(), shake = 0.0;
        Vec3 in = s0.subtract(g).normalize(), side = in.cross(new Vec3(0, 1, 0)).normalize();

        int n = dropPieces(t);
        double S = Balance.ASTEROID_SCATTER_MAX + Missiles.craterRadius(AsteroidDrop.tier(n)) * WarheadType.ASTEROID.crater;
        double sk = n > 1 ? ease((t - splitSeen) / 40.0) : 0.0;
        double startLeft = L;
        double dist0 = s0.distanceTo(g);
        if (t < L * 0.55) {
            double left = startLeft - t, frac = Math.pow(Math.max(0.0, left) / startLeft, 1.4);
            Vec3 rock = g.add(in.scale(dist0 * frac));
            double q = ease(t / (L * 0.55));
            pos = rock.add(side.scale(60.0 - 20.0 * q)).add(in.scale(40.0)).add(0.0, 20.0, 0.0);
            lookAt = new double[] { rock.x - in.x * 60.0, rock.y - in.y * 60.0, rock.z - in.z * 60.0 };
            if (sk > 0.0 && dropFall != null) {
                double now = startTick + t, pl = Math.max(0.0, dropFall.impact() - now);
                double sl = dropFall.split() == 0 ? 0.0 : dropFall.impact() - dropFall.split();
                Vec3 tg = new Vec3(dropFall.tx() + 0.5, dropFall.ty(), dropFall.tz() + 0.5), gc = Vec3.ZERO;
                Vec3[] ps = new Vec3[n];
                for (int i = 0; i < n; i++) { ps[i] = AsteroidDrop.piecePos(tg, dropFall.seed(), pl, i, n, sl); gc = gc.add(ps[i].scale(1.0 / n)); }
                double spread = 0.0;
                for (Vec3 p : ps) spread = Math.max(spread, p.distanceTo(gc));
                double D = 60.0 + spread * 1.5;
                Vec3 cp = gc.add(side.scale(D * 0.9)).add(in.scale(D * 0.7)).add(0.0, D * 0.5, 0.0);
                pos = pos.add(cp.subtract(pos).scale(sk));
                double[] cl = { gc.x - in.x * 40.0, gc.y - in.y * 40.0, gc.z - in.z * 40.0 };
                for (int k = 0; k < 3; k++) lookAt[k] += (cl[k] - lookAt[k]) * sk;
                if (sk > 0.99 && t > L * 0.33) {
                    Vec3 pc = ps[(int) ((t - L * 0.33) / 50.0) % n];
                    pos = pc.add(side.scale(34.0)).add(in.scale(22.0)).add(0.0, 14.0, 0.0);
                    lookAt = new double[] { pc.x - in.x * 50.0, pc.y - in.y * 50.0, pc.z - in.z * 50.0 };
                }
            }
            fov = 76.0F;
            shake = 0.2 + 0.4 * q;
        } else if (t < L + 40) {

            double q = Math.min(1.0, (t - L * 0.55) / (L * 0.45));
            Vec3 u = new Vec3(in.x, 0.0, in.z).normalize();
            double off = R * (0.45 - 0.08 * ease(q)), hgt = R * (0.95 - 0.15 * ease(q));
            off += (S * 0.08 - off) * sk;
            hgt += (S * (1.85 - 0.15 * ease(q)) - hgt) * sk;
            pos = g.add(u.scale(off)).add(0.0, hgt, 0.0);
            double left = Math.max(0.0, L - t), frac = Math.pow(left / startLeft, 1.4);
            Vec3 rock = g.add(in.scale(dist0 * frac));

            lookAt = t < L ? new double[] { g.x + (rock.x - g.x) * 0.3, g.y + (rock.y - g.y) * 0.3, g.z + (rock.z - g.z) * 0.3 } : new double[] { g.x, g.y + R * 0.1 * ease((t - L) / 20.0), g.z };
            fov = 70.0F;
            if (t >= L) shake = 3.2 * Math.max(0.0, 1.0 - (t - L) / 40.0);
        } else {
            double q = (t - L - 40) / Math.max(1.0, end - L - 40);
            Vec3 u = new Vec3(in.x, 0.0, in.z).normalize();
            double ang = Math.atan2(u.z, u.x) + 1.1 * ease(q), rise = ease((q - 0.55) / 0.45);
            double off = R * (0.37 - 0.22 * rise), hgt = R * (0.8 + 0.6 * rise);
            off += (S * (0.08 - 0.03 * rise) - off) * sk;
            hgt += (S * (1.7 + 0.15 * rise) - hgt) * sk;
            pos = g.add(Math.cos(ang) * off, hgt, Math.sin(ang) * off);
            lookAt = new double[] { g.x, g.y + R * 0.1 - R * 0.2 * ease(q / 0.3), g.z };
            fov = 70.0F + 4.0F * (float) rise;
            shake = 0.3 * Math.max(0.0, 1.0 - q * 2.0);
        }
        roll = (float) (shake * Math.sin(t * 2.3) * 2.0);
        if (shake > 0) pos = pos.add(Math.sin(t * 3.1) * shake * 0.3, Math.sin(t * 4.7) * shake * 0.3, Math.cos(t * 3.7) * shake * 0.3);
    }

    private static void captureScript(double t) {
        CutscenePayload c = cur;
        Vec3 rc = new Vec3(c.sx(), c.sy(), c.sz()), d = new Vec3(c.dx() - c.sx(), c.dy() - c.sy(), c.dz() - c.sz());
        double size = Math.max(5.0, d.length()), r = size / 2.0, u = t / Math.max(1, c.total());
        Vec3 a = d.normalize(), side = a.cross(new Vec3(0, 1, 0)).normalize(), up = new Vec3(0, 1, 0);
        if (u < 0.3) {
            double q = ease(u / 0.3), back = r + 6.0 + 70.0 * (1.0 - AsteroidRedirect.smooth(u / 0.45));
            Vec3 tug = rc.subtract(a.scale(back));
            pos = tug.subtract(a.scale(24.0 - 6.0 * q)).add(side.scale(10.0)).add(up.scale(8.0 - 2.0 * q));
            lookAt = new double[] { rc.x, rc.y, rc.z };
            fov = 62.0F;
        } else if (u < 0.65) {
            double q = ease((u - 0.3) / 0.35), ang = Math.PI * 0.15 + Math.PI * 0.35 * q, rad = r * 2.2 + 18.0;
            Vec3 o = side.scale(Math.cos(ang) * rad).subtract(a.scale(Math.sin(ang) * rad * 0.6 + r * 0.4)).add(up.scale(r * 0.4));
            pos = rc.add(o);
            lookAt = new double[] { rc.x - a.x * r * 0.9, rc.y, rc.z - a.z * r * 0.9 };
            fov = 58.0F;
        } else if (u < 0.9) {
            double q = ease((u - 0.65) / 0.25);
            pos = rc.add(side.scale(-(r * 1.7 + 6.0))).add(up.scale(r * 0.6 + 3.0 * q)).subtract(a.scale(r * 0.3));
            lookAt = new double[] { rc.x, rc.y, rc.z };
            fov = 64.0F - 6.0F * (float) q;
        } else {
            double q = ease((u - 0.9) / 0.1);
            pos = rc.add(side.scale(-(r * 1.7 + 6.0 + 60.0 * q))).add(up.scale(r * 0.6 + 3.0 + 20.0 * q)).subtract(a.scale(r * 0.3 + 30.0 * q));
            lookAt = new double[] { rc.x, rc.y, rc.z };
            fov = 58.0F + 10.0F * (float) q;
        }
        roll = 0.0F;
    }

    private static Vec3 slug(double f) { return RailgunArc.at(new Vec3(cur.sx(), cur.sy(), cur.sz()), new Vec3(cur.dx(), cur.dy(), cur.dz()), f); }

    private static void railgun(double t) {
        CutscenePayload c = cur;
        Vec3 m = new Vec3(c.sx(), c.sy(), c.sz()), g = new Vec3(c.dx(), c.dy(), c.dz()), p = pivot;
        Vec3 h = new Vec3(g.x - m.x, 0.0, g.z - m.z);
        h = h.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : h.normalize();
        Vec3 side = new Vec3(-h.z, 0.0, h.x);
        double shot = c.lead(), hit = c.lead() + Balance.RG_TRAVEL_TICKS, end = c.total();
        double R = Missiles.craterRadius(4) * WarheadType.RAILGUN.crater;
        double shake = 0.0;
        double baseAng = Math.atan2(h.z, h.x);
        if (t < shot) {
            double k = t / shot;
            if (k < 0.55) {
                double q = ease(k / 0.55);
                double ang = baseAng - Math.PI * 0.5 - 2.4 * q;
                double rad = 30.0 - 10.0 * q;
                pos = p.add(Math.cos(ang) * rad, -3.0 + 7.0 * q, Math.sin(ang) * rad);
                Vec3 lk = p.lerp(m, 0.15 + 0.35 * q);
                lookAt = new double[] { lk.x, lk.y, lk.z };
                fov = 64.0F - 8.0F * (float) q;
            } else {
                double q = ease((k - 0.55) / 0.45);
                pos = p.subtract(h.scale(15.0 - 5.0 * q)).add(side.scale(4.5 - 2.0 * q)).add(0.0, 6.0 - 1.5 * q, 0.0);
                lookAt = new double[] { m.x, m.y, m.z };
                fov = 56.0F - 14.0F * (float) q;
            }
            shake = 0.08 + 0.45 * k * k;
        } else if (t < shot + 16) {
            double q = (t - shot) / 16.0;
            pos = p.subtract(h.scale(10.0 + 6.0 * Math.min(1.0, q * 3.0))).add(side.scale(2.5)).add(0.0, 4.5, 0.0);
            Vec3 lk = slug(0.03 * q);
            lookAt = new double[] { lk.x, lk.y, lk.z };
            fov = t - shot < 3 ? 42.0F + 60.0F * (float) ((t - shot) / 3.0) : 102.0F - 30.0F * ease((t - shot - 3) / 13.0);
            shake = 1.4 * (1.0 - q);
        } else if (t < shot + 76) {
            double f = (t - shot) / Balance.RG_TRAVEL_TICKS;
            Vec3 s = slug(f), back = slug(Math.max(0.0, f - 0.025));
            Vec3 dir = s.subtract(back);
            dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : dir.normalize();
            pos = back.add(side.scale(9.0)).add(dir.scale(-14.0)).add(0.0, 2.0, 0.0);
            lookAt = new double[] { s.x, s.y, s.z };
            fov = 72.0F;
        } else if (t < hit + 30) {
            double q = (t - shot - 76) / Math.max(1.0, hit - shot - 76);
            double ang = baseAng + Math.PI * 0.85;
            double rad = R * 1.15 - R * 0.15 * ease(q), hgt = R * 0.75 - R * 0.1 * ease(q);
            pos = g.add(Math.cos(ang) * rad, hgt, Math.sin(ang) * rad);
            lookAt = new double[] { g.x, g.y + 6.0, g.z };
            fov = 66.0F;
            if (t >= hit) shake = 2.2 * Math.max(0.0, 1.0 - (t - hit) / 30.0);
        } else {
            double q = (t - hit - 30) / Math.max(1.0, end - hit - 30);
            double ang = baseAng + Math.PI * 0.85 + 1.3 * ease(q);
            double rad = q < 0.7 ? R * 1.0 - R * 0.15 * ease(q / 0.7) : R * 0.85 + R * 0.8 * ease((q - 0.7) / 0.3);
            double hgt = q < 0.7 ? R * 0.65 - R * 0.35 * ease(q / 0.7) : R * 0.3 + R * 0.7 * ease((q - 0.7) / 0.3);
            pos = g.add(Math.cos(ang) * rad, hgt, Math.sin(ang) * rad);
            lookAt = new double[] { g.x, g.y - R * 0.1, g.z };
            fov = 66.0F + 8.0F * ease((q - 0.7) / 0.3);
            shake = 0.25 * Math.max(0.0, 1.0 - q * 2.0);
        }
        roll = (float) (shake * Math.sin(t * 2.3) * 2.0);
        if (shake > 0) pos = pos.add(Math.sin(t * 3.1) * shake * 0.3, Math.sin(t * 4.7) * shake * 0.3, Math.cos(t * 3.7) * shake * 0.3);
    }

    @SubscribeEvent
    static void onFov(ViewportEvent.ComputeFov event) {
        if (cur != null && cam != null) event.setFOV(fov);
    }

    @SubscribeEvent
    static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (cur != null && cam != null) event.setRoll(roll);
    }

    @SubscribeEvent
    static void onFog(ViewportEvent.RenderFog event) {
        if (cur == null || !ClientSnapshot.show || event.getType() != FogType.NONE) return;

        double depthFar = Minecraft.getInstance().gameRenderer.getDepthFar() * 0.95;
        float far = (float) Math.min(ClientSnapshot.farR(), depthFar);
        float near = far * (float) Balance.RG_CUT_FOG_START;
        if (drop() || rod() || tsar()) {
            near = far * (float) Balance.CUT_DROP_FOG_START;
        } else if (impacted) {
            near = far * (float) Balance.CUT_CRATER_FOG_START;
        }
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(near);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (cur == null || capture() || drop() || tsar() || stationCam() || board() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double t = time(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (rod()) {
            ClientBlastGeo.beginFrame();
            Camera rc = event.getCamera();
            ClientRodFx.draw(event, rc.getPosition(), rc.getLeftVector(), rc.getUpVector(), cur.dx(), cur.dy(), cur.dz(), cur.lead() - t,
                    ClientRailgun.rodBroken(cur.dx(), cur.dz()), mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false));
            return;
        }
        double shot = cur.lead(), hit = shot + Balance.RG_TRAVEL_TICKS;
        if (t < shot) return;
        ClientBlastGeo.beginFrame();
        Camera camera = event.getCamera();
        Vec3 c = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        double f = Math.min(1.0, (t - shot) / Balance.RG_TRAVEL_TICKS);
        float fade = t < hit ? 1.0F : (float) Math.max(0.0, 1.0 - (t - hit) / 200.0);

        java.util.List<Vec3> pts = new java.util.ArrayList<>();
        for (int i = 0; i <= 40; i++) pts.add(slug(f * i / 40.0));
        if (t >= hit) pts.add(new Vec3(cur.dx(), floorY(mc), cur.dz()));
        ClientRailgunShow.electricTrail(bb, c, pts, Math.min(1.0, (t - shot) / Balance.RG_TRAVEL_TICKS), ClientClock.ticks(), ClientClock.now(event.getPartialTick().getGameTimeDeltaPartialTick(false)), fade);
        if (t < hit)
            ClientRailgunShow.slugDart(bb, c, left, up, slug(f), RailgunArc.dir(new Vec3(cur.sx(), cur.sy(), cur.sz()), new Vec3(cur.dx(), cur.dy(), cur.dz()), f),
                    ClientClock.now(event.getPartialTick().getGameTimeDeltaPartialTick(false)), 1.0);
        ClientBlastGeo.draw(event, bb, true, true);
    }

    static final class Cut extends Screen {

        Cut() { super(Component.translatable("gui.wmd.cutscene.title")); }

        @Override public boolean isPauseScreen() { return false; }
        @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_P && !stationCam()) { togglePhoto(); return true; }
            if (photo && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_H) { photoHint = !photoHint; return true; }
            if (photo && minecraft != null && minecraft.options.keyShift.matches(keyCode, scanCode)) return true;
            if (minecraft != null && minecraft.options.keyShift.matches(keyCode, scanCode)) { stop(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (!photo) return super.mouseDragged(mx, my, button, dx, dy);
            photoYaw += (float) dx * 0.35F;
            photoPitch = Mth.clamp(photoPitch + (float) dy * 0.35F, -89.0F, 89.0F);
            return true;
        }

        @Override
        public void onClose() { stop(); }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            if (cur == null || minecraft == null || minecraft.level == null) return;
            double t = time(pt);
            int w = width, h = height, bar = h / 9;
            if (photo) {
                if (photoHint) {
                    Component hint = Component.translatable("gui.wmd.cutscene.photo_hint");
                    g.fill(4, 4, 12 + font.width(hint), 18, 0x90000000);
                    g.drawString(font, hint, 8, 7, 0xFFE8F0FF, false);
                }
                return;
            }
            if (stationCam()) {
                if (modCam != null) {
                    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                    g.blit(LENS, 0, 0, w, h, 0.0F, 0.0F, 256, 256, 256, 256);
                    com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                    g.drawString(font, Component.translatable("gui.wmd.cutscene.cam_" + modCam.type()), 12, h - 20, 0xFF9FD0FF, false);
                    Component back = Component.translatable("gui.wmd.cutscene.station_cam_back");
                    g.drawString(font, back, w - font.width(back) - 12, h - 20, 0x80FFFFFF, false);
                    return;
                }
                g.fill(0, 0, w, bar, 0xFF000000);
                g.fill(0, h - bar, w, h, 0xFF000000);
                g.drawString(font, Component.translatable("gui.wmd.cutscene.station_cam"), 8, h - bar / 2 - 4, 0xFF9FD0FF, false);
                Component back = Component.translatable("gui.wmd.cutscene.station_cam_back");
                g.drawString(font, back, w - font.width(back) - 6, h - bar / 2 - 4, 0x80FFFFFF, false);
                return;
            }
            double shot = rod() ? -1000.0 : cur.lead(), hit = hitT();

            int grade = t < hit ? 0x182A50A0 : 0x1E5A2090;
            g.fill(0, 0, w, h, grade);

            float fl = (float) Math.max(0.0, 1.0 - Math.abs(t - shot) / 5.0) * 0.85F + (float) Math.max(0.0, 1.0 - Math.max(0.0, t - hit) / 14.0) * (t >= hit ? 1.0F : 0.0F);
            if (fl > 0.01F) g.fill(0, 0, w, h, ((int) (Math.min(1.0F, fl) * 235) << 24) | 0xEEF4FF);

            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);

            if (t > hit + 40 && t < cur.total() - 20) {
                float a = Math.min(1.0F, (float) (t - hit - 40) / 20.0F);
                int col = ((int) (a * 255) << 24) | 0xE8F0FF;
                Component big = Component.translatable(rod() ? "gui.wmd.cutscene.rod_title" : "gui.wmd.cutscene.railgun_title");
                Component small = rod() ? Component.translatable("gui.wmd.cutscene.rod_sub", Math.round(cur.dx()), Math.round(cur.dz()))
                        : Component.translatable("gui.wmd.cutscene.railgun_sub", cur.caption(), Math.round(cur.dx()), Math.round(cur.dz()));
                g.pose().pushPose();
                g.pose().translate(w / 2.0F, h - bar - 34.0F, 0.0F);
                g.pose().scale(2.5F, 2.5F, 1.0F);
                g.drawString(font, big, -font.width(big) / 2, 0, col, true);
                g.pose().popPose();
                g.drawString(font, small, (w - font.width(small)) / 2, h - bar - 10, col, true);
            }

            double fadeAt = cur.total() - 30;
            if (t > fadeAt) g.fill(0, 0, w, h, ((int) (Math.min(1.0, (t - fadeAt) / 30.0) * 255) << 24));
            Component skip = Component.translatable(stationCam() ? "gui.wmd.cutscene.skip" : "gui.wmd.cutscene.skip_photo");
            g.drawString(font, skip, w - font.width(skip) - 6, h - bar / 2 - 4, 0x80FFFFFF, false);
        }
    }
}
