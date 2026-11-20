package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientExoMods {

    private ClientExoMods() {}

    static final KeyMapping FIRE = new KeyMapping("key.wmd.exo_fire", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.wmd_exo");
    static final KeyMapping RADIAL = new KeyMapping("key.wmd.exo_radial", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.wmd_exo");

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Keys {
        @SubscribeEvent
        static void register(RegisterKeyMappingsEvent event) { event.register(FIRE); event.register(RADIAL); }
    }

    static final class MAnim {
        float thermal, rf, mag, barrel, orb, pod, lid, wings, wpod, recoil;
        float pThermal, pRf, pMag, pBarrel, pOrb, pPod, pLid, pWings, pWpod, pRecoil;
        boolean thermalOn, rfOn, magOn, charging, hover;
        int podT = 999, fireT = 999, barrierT = 999;
        long seen;

        static float l(float a, float b, float pt) { return a + (b - a) * pt; }
    }

    private static final Map<Integer, MAnim> ANIM = Statics.client(new HashMap<>());

    private static final Map<Long, ClientBolt> BOLTS = Statics.client(new HashMap<>());

    static final class ClientBolt {
        final long id;
        final int shooter;
        final Vec3 start, dir;
        final float k;
        final double t0, max;
        double end = -1.0, endT;
        boolean flashed, burst;

        ClientBolt(long id, int shooter, Vec3 start, Vec3 dir, float k, double t0, double max) {
            this.id = id; this.shooter = shooter; this.start = start; this.dir = dir; this.k = k; this.t0 = t0; this.max = max;
        }

        double flown(double tt) { return Math.max(0.0, Math.min(Balance.EXO_CANNON_SPEED * (tt - t0), end >= 0 ? end : max)); }

        Vec3 at(double tt) { return start.add(dir.scale(flown(tt))); }

        boolean arrived(double tt) { return end >= 0 && Balance.EXO_CANNON_SPEED * (tt - t0) >= end; }

        double R() { return Balance.EXO_CANNON_BALL_R + Balance.EXO_CANNON_BALL_K * k; }
    }

    private static final Map<Long, ArrayDeque<double[]>> ROCKETS = Statics.client(new HashMap<>());

    static MAnim anim(Entity e) { return ANIM.get(e.getId()); }

    private static MAnim of(int id) { return ANIM.computeIfAbsent(id, k -> new MAnim()); }

    static int weapon;
    private static boolean thermal, cloak, hover, armed, thrust, mag, wasFire, wasJump;
    private static int charge, airJumpT = 99;

    private static boolean seeing;

    public static boolean thermalVisor() { return seeing; }

    static boolean thermalToggled() { return thermal; }

    static boolean hovering() { return hover; }
    static boolean cloaked() { return cloak; }

    static void send(int a, int arg) { PacketDistributor.sendToServer(new ExoModPayload(a, arg)); }

    static void toggle(int what) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        switch (what) {
            case 0 -> weapon = 0;
            case 1 -> weapon = 1;
            case 2 -> { thermal = !thermal; send(thermal ? ExoMods.THERMAL_ON : ExoMods.THERMAL_OFF, 0); act(p, thermal ? ExoMods.THERMAL_ON : ExoMods.THERMAL_OFF); }
            case 3 -> { cloak = !cloak; send(cloak ? ExoMods.CLOAK_ON : ExoMods.CLOAK_OFF, 0); }
            case 4 -> {
                hover = !hover;
                ExoMods.clientWings(p.getUUID(), armed, hover);
                send(hover ? ExoMods.HOVER_ON : ExoMods.HOVER_OFF, 0);
                act(p, hover ? ExoMods.HOVER_ON : ExoMods.HOVER_OFF);
                if (hover && p.isFallFlying()) p.stopFallFlying();
            }
            default -> {}
        }
    }

    static void act(Player pl, int a) {
        MAnim m = of(pl.getId());
        LocalPlayer me = Minecraft.getInstance().player;
        boolean self = pl == me;
        switch (a) {
            case ExoMods.THERMAL_ON -> m.thermalOn = true;
            case ExoMods.THERMAL_OFF -> { m.thermalOn = false; if (self) thermal = false; }
            case ExoMods.RF_ON -> m.rfOn = true;
            case ExoMods.RF_OFF -> m.rfOn = false;
            case ExoMods.MAG_ON -> m.magOn = true;
            case ExoMods.MAG_OFF -> m.magOn = false;
            case ExoMods.HOVER_ON -> m.hover = true;
            case ExoMods.HOVER_OFF -> { m.hover = false; if (self && hover) { hover = false; ExoMods.clientWings(pl.getUUID(), armed, false); } }
            case ExoMods.CANNON_CHARGE -> { m.charging = true; play(pl, WmdSounds.FUSION_CHARGE.get(), Balance.SND_SMALL, 1.6F); }
            case ExoMods.CANNON_CANCEL -> { m.charging = false; if (self) charge = 0; }
            case ExoMods.MSL_FIRE -> m.podT = 0;
            case ExoMods.BARRIER -> { m.barrierT = 0; play(pl, WmdSounds.FF_HIT.get(), Balance.SND_SMALL, 1.5F); }
            case ExoMods.CLOAK_ON -> { if (self) cloak = true; play(pl, WmdSounds.FF_DOME_UP.get(), Balance.SND_SMALL, 1.6F); }
            case ExoMods.CLOAK_OFF -> { if (self) cloak = false; }
            default -> {}
        }
    }

    private static void play(Player pl, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        if (pl.level() instanceof ClientLevel cl) cl.playLocalSound(pl.getX(), pl.getY() + 1.0, pl.getZ(), s, SoundSource.PLAYERS, vol, pitch, false);
    }

    static void beam(ExoBeamPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        Vec3 start = new Vec3(p.x(), p.y(), p.z()), dir = new Vec3(p.dx(), p.dy(), p.dz());
        if (!p.end()) {
            BOLTS.put(p.id(), new ClientBolt(p.id(), p.shooter(), start, dir, p.k(), now, p.dist()));
            Entity e = mc.level.getEntity(p.shooter());
            if (e instanceof Player pl) { MAnim m = of(pl.getId()); m.charging = false; m.fireT = 0; if (pl == mc.player) charge = 0; }
        } else {
            ClientBolt b = BOLTS.computeIfAbsent(p.id(), k -> new ClientBolt(p.id(), p.shooter(), start, dir, p.k(), now - p.dist() / Balance.EXO_CANNON_SPEED, p.dist()));
            b.end = p.dist();
            b.endT = b.t0 + b.end / Balance.EXO_CANNON_SPEED;
        }
    }

    static void rockets(ExoRocketsPayload p) {
        for (int i = 0; i < p.ids().length; i++) {
            ArrayDeque<double[]> q = ROCKETS.computeIfAbsent(p.ids()[i], k -> new ArrayDeque<>());
            q.addLast(new double[] { p.pos()[i * 3], p.pos()[i * 3 + 1], p.pos()[i * 3 + 2], p.end()[i] ? 1 : 0, 0 });
            while (q.size() > 8) q.removeFirst();
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            ANIM.clear(); BOLTS.clear(); ROCKETS.clear(); thermal = cloak = hover = armed = thrust = mag = seeing = false;
            return;
        }

        seeing = thermal && p.isAlive() && ExoModule.THERMAL.live(p);
        if (thermal && !p.isAlive()) thermal = false;
        boolean free = mc.screen == null && !p.isSpectator();
        if (free && RADIAL.isDown() && ExoSuitItem.fullSet(p) && !(mc.screen instanceof ExoRadialScreen)) mc.setScreen(new ExoRadialScreen());
        weapons(mc, p, free);
        wings(mc, p);
        magGrip(mc, p);
        long now = mc.level.getGameTime();
        for (Player pl : mc.level.players()) if (ExoSuitItem.anyPiece(pl)) step(pl, of(pl.getId()), now);
        ANIM.values().removeIf(m -> now - m.seen > 40);
        BOLTS.values().removeIf(b -> (b.end >= 0 && now > b.endT + BURST + 2) || now > b.t0 + 200);
        for (Iterator<ArrayDeque<double[]>> it = ROCKETS.values().iterator(); it.hasNext(); ) {
            ArrayDeque<double[]> q = it.next();
            double[] last = q.peekLast();
            if (last == null || ++last[4] > (last[3] > 0.5 ? 10 : 6)) it.remove();
        }
    }

    private static void weapons(Minecraft mc, LocalPlayer p, boolean free) {
        boolean fire = free && FIRE.isDown();
        if (weapon == 0) {
            boolean ok = ExoModule.CANNON.live(p);
            if (fire && ok) {
                if (charge == 0) { send(ExoMods.CANNON_CHARGE, 0); act(p, ExoMods.CANNON_CHARGE); }
                charge = Math.min(Balance.EXO_CANNON_CHARGE, charge + 1);
            } else if (wasFire && charge > 0) {
                send(ExoMods.FIRE_CANNON, charge);
                if (charge < Balance.EXO_CANNON_CHARGE * Balance.EXO_CANNON_MIN) { act(p, ExoMods.CANNON_CANCEL); charge = 0; }
            }
        } else if (fire && !wasFire && ExoModule.MISSILES.live(p)) {
            send(ExoMods.FIRE_MISSILES, target(mc, p));
        }
        wasFire = fire;
    }

    private static boolean targetable(Entity e, LocalPlayer p) {
        if (e == null || !e.isAlive() || !e.isPickable() || e == p || e.isSpectator()) return false;
        return !(e instanceof Player pl && ClientCloak.playerHidden(pl.getUUID()));
    }

    private static int target(Minecraft mc, LocalPlayer p) {
        if (targetable(mc.crosshairPickEntity, p) && mc.crosshairPickEntity.distanceTo(p) <= Balance.EXO_MSL_RANGE) return mc.crosshairPickEntity.getId();
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(Balance.EXO_MSL_RANGE));
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, new AABB(eye, end).inflate(2.0), e -> targetable(e, p));
        return eh == null ? -1 : eh.getEntity().getId();
    }

    private static void wings(Minecraft mc, LocalPlayer p) {
        boolean live = ExoModule.WINGS.live(p);
        boolean jump = mc.options.keyJump.isDown() && mc.screen == null;
        boolean edge = jump && !wasJump;
        wasJump = jump;
        airJumpT++;
        if (!live || p.onGround() || p.isInWater()) {
            if (armed) { armed = false; send(ExoMods.WINGS_DISARM, 0); }
            if (thrust) { thrust = false; send(ExoMods.THRUST_OFF, 0); }
            if (hover && (p.onGround() || !live)) { hover = false; send(ExoMods.HOVER_OFF, 0); act(p, ExoMods.HOVER_OFF); }
            ExoMods.clientWings(p.getUUID(), false, hover);
            return;
        }
        if (edge && !p.isFallFlying() && !hover) {
            if (airJumpT <= 7) {
                armed = true;
                ExoMods.clientWings(p.getUUID(), true, false);
                send(ExoMods.WINGS_ARM, 0);
                p.startFallFlying();
                if (mc.getConnection() != null) mc.getConnection().send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                play(p, WmdSounds.EXO_SERVO.get(), Balance.SND_SMALL, 1.1F);
            }
            airJumpT = 0;
        }
        boolean push = p.isFallFlying() && mc.options.keyUp.isDown() && mc.screen == null;
        if (push) {
            Vec3 v = p.getDeltaMovement().add(p.getLookAngle().scale(Balance.EXO_WING_THRUST * (ExoSuitItem.charge(p) < Balance.EXO_LOW ? Balance.EXO_LOW_THRUST : 1.0)));
            if (v.length() > Balance.EXO_WING_SPEED) v = v.normalize().scale(Balance.EXO_WING_SPEED);
            p.setDeltaMovement(v);
            p.hurtMarked = true;
        }
        if (push != thrust) { thrust = push; send(push ? ExoMods.THRUST_ON : ExoMods.THRUST_OFF, 0); }
        if (hover) {
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            double f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0), s = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
            double hs = Balance.EXO_WING_HOVER_SPEED;
            double vx = (-Mth.sin(yaw) * f + Mth.cos(yaw) * s) * hs, vz = (Mth.cos(yaw) * f + Mth.sin(yaw) * s) * hs;
            double vy = jump ? 0.15 : mc.options.keyShift.isDown() ? -0.15 : 0.0;
            p.setDeltaMovement(vx, vy, vz);
            p.resetFallDistance();
            p.hurtMarked = true;
        }
    }

    private static void magGrip(Minecraft mc, LocalPlayer p) {
        boolean live = ExoModule.MAGGRIP.live(p) && !p.isFallFlying() && !hover && !p.isInWater() && mc.screen == null;
        boolean cling = false;
        if (live && !p.onGround()) {
            Vec3 v = p.getDeltaMovement();
            boolean ceiling = mc.options.keyJump.isDown() && !p.level().getBlockState(BlockPos.containing(p.getX(), p.getBoundingBox().maxY + 0.15, p.getZ())).isAir();
            if (p.horizontalCollision && (mc.options.keyUp.isDown() || mc.options.keyShift.isDown())) {
                p.setDeltaMovement(v.x, mc.options.keyShift.isDown() ? 0.0 : Balance.EXO_MAG_CLIMB, v.z);
                cling = true;
            } else if (ceiling) {
                p.setDeltaMovement(Mth.clamp(v.x, -Balance.EXO_MAG_CREEP, Balance.EXO_MAG_CREEP), 0.08, Mth.clamp(v.z, -Balance.EXO_MAG_CREEP, Balance.EXO_MAG_CREEP));
                cling = true;
            }
            if (cling) { p.resetFallDistance(); p.hurtMarked = true; }
        }
        if (cling != mag) { mag = cling; send(cling ? ExoMods.MAG_ON : ExoMods.MAG_OFF, 0); act(p, cling ? ExoMods.MAG_ON : ExoMods.MAG_OFF); }
    }

    private static void step(Player pl, MAnim m, long now) {
        m.seen = now;
        m.pThermal = m.thermal; m.pRf = m.rf; m.pMag = m.mag; m.pBarrel = m.barrel; m.pOrb = m.orb; m.pPod = m.pod; m.pLid = m.lid; m.pWings = m.wings; m.pWpod = m.wpod; m.pRecoil = m.recoil;
        m.podT++; m.fireT++; m.barrierT++;
        boolean self = pl == Minecraft.getInstance().player;
        if (self) m.charging = charge > 0;
        m.thermal = ap(m.thermal, m.thermalOn ? 1 : 0, 0.15F);
        m.rf = ap(m.rf, m.rfOn ? 1 : 0, 0.08F);
        m.mag = ap(m.mag, m.magOn ? 1 : 0, 0.25F);
        boolean up = m.charging || m.fireT < 20;
        m.barrel = ap(m.barrel, up ? 1 : 0, 0.2F);
        m.orb = m.charging ? Math.min(1.0F, m.orb + 1.0F / Balance.EXO_CANNON_CHARGE) : m.fireT < 2 ? m.orb : Math.max(0.0F, m.orb - 0.34F);
        m.recoil = m.fireT < 3 ? 1.0F - m.fireT / 3.0F : 0.0F;
        boolean podUp = m.podT < Balance.EXO_MSL_SALVO * Balance.EXO_MSL_GAP + 14;
        m.pod = ap(m.pod, podUp ? 1 : 0, 0.2F);
        m.lid = ap(m.lid, podUp && m.pod > 0.6F ? 1 : 0, 0.25F);
        boolean open = pl.isFallFlying() || m.hover;
        m.wings = ap(m.wings, open ? 1 : 0, 1.0F / Balance.EXO_WING_UNFOLD);
        m.wpod = ap(m.wpod, m.hover ? 1 : 0, 0.15F);
    }

    private static float ap(float v, float t, float s) { return v < t ? Math.min(t, v + s) : Math.max(t, v - s); }

    @SubscribeEvent
    static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player pl) || !(event.getRenderer().getModel() instanceof HumanoidModel<?> hm)) return;
        MAnim m = ANIM.get(pl.getId());
        if (m != null && (m.charging || m.fireT < 20) && ExoModule.CANNON.fitted(pl)) { hm.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW; hm.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW; }
    }

    static Vec3 muzzle(Player pl, float pt) {
        Vec3 base = pl.getPosition(pt).add(0.0, pl.isCrouching() ? 1.0 : 1.3, 0.0);
        float yaw = Mth.lerp(pt, pl.yBodyRotO, pl.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
        Vec3 look = pl.getViewVector(pt);
        return base.add(right.scale(0.5)).add(look.scale(0.95));
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || (ANIM.isEmpty() && BOLTS.isEmpty() && ROCKETS.isEmpty())) return;
        boolean needDraw = !BOLTS.isEmpty() || !ROCKETS.isEmpty();
        if (!needDraw) for (MAnim m : ANIM.values()) if (m.orb > 0.02F || m.pOrb > 0.02F || m.barrierT < Balance.EXO_BARRIER_FLASH) { needDraw = true; break; }
        if (!needDraw) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double tt = level.getGameTime() + pt;
        long now = level.getGameTime();
        ClientBlastGeo.beginFrame();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Map.Entry<Integer, MAnim> e : ANIM.entrySet()) {
            Entity en = level.getEntity(e.getKey());
            if (!(en instanceof Player pl) || pl.isInvisible() || ClientCloak.playerHidden(pl.getUUID())) continue;
            MAnim m = e.getValue();
            float orb = MAnim.l(m.pOrb, m.orb, pt);
            if (orb > 0.02F) {
                Vec3 c = muzzle(pl, pt);
                if (pl == mc.player && firstPerson) c = c.add(pl.getViewVector(pt).scale(0.4));
                ClientPlasmaMortar.ball(bb, cam, left, up, c, 0.05 + 0.2 * orb, tt * 1.6, pl.getId() * 7919L, orb);
                VeilBridge.glow(0x45584F00L ^ pl.getId(), c.x, c.y, c.z, 0.4F, 0.8F, 1.0F, 2.0F + 4.0F * orb, 1.2F * orb, now);
                any = true;
            }
            if (m.barrierT < Balance.EXO_BARRIER_FLASH) {
                float a = 1.0F - m.barrierT / (float) Balance.EXO_BARRIER_FLASH;
                Vec3 c = pl.getPosition(pt).add(0.0, 0.95, 0.0);
                hexBubble(bb, cam, c, 1.15 + 0.1 * (1 - a), a, tt);
                any = true;
            }
        }
        for (ClientBolt b : BOLTS.values()) {
            if (b.start.distanceTo(cam) > Balance.EXO_FX_RANGE * 3) continue;
            bolt(bb, cam, left, up, b, tt, now);
            any = true;
        }
        for (ArrayDeque<double[]> q : ROCKETS.values()) {
            double[] last = q.peekLast();
            if (last == null) continue;
            double[] prev = null;
            int i = 0;
            for (double[] s : q) {
                if (prev != null) ClientBlastGeo.streak(bb, cam, prev[0], prev[1], prev[2], s[0], s[1], s[2], 0.06 + 0.012 * i, 0.75F, 0.75F, 0.8F, 0.08F * i, 0.08F * (i + 1));
                prev = s;
                i++;
            }
            if (last[3] > 0.5) ClientBlastGeo.disc(bb, cam, left, up, last[0], last[1], last[2], 0.5 + last[4] * 0.12, 1.0F, 0.7F, 0.3F, (float) Math.max(0.0, 0.9 - last[4] * 0.09));
            else {
                ClientBlastGeo.disc(bb, cam, left, up, last[0], last[1], last[2], 0.22, 1.0F, 0.55F, 0.15F, 0.9F);
                ClientBlastGeo.disc(bb, cam, left, up, last[0], last[1], last[2], 0.09, 1.0F, 0.95F, 0.8F, 1.0F);
            }
            any = true;
        }
        ClientBlastGeo.draw(event, bb, any, true);
    }

    static final int BURST = 14;
    private static final int TRAIL = 16, SPARKS = 6;
    private static final double TRAIL_STEP = 0.18, SPARK_LIFE = 12.0;

    private static void bolt(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, ClientBolt b, double tt, long now) {
        double R = b.R(), age = tt - b.t0;
        if (age < 5) {
            float f = (float) (1.0 - age / 5.0);
            ClientBlastGeo.disc(bb, cam, left, up, b.start.x, b.start.y, b.start.z, R * 4.0 * f + 0.1, 0.4F, 0.8F, 1.0F, 0.55F * f);
            ClientBlastGeo.disc(bb, cam, left, up, b.start.x, b.start.y, b.start.z, R * 1.8 * f + 0.05, 1.0F, 1.0F, 1.0F, 0.9F * f);
            if (!b.flashed) { b.flashed = true; VeilBridge.flash(b.start.x, b.start.y, b.start.z, 0.45F, 0.85F, 1.0F, 8.0F, 2.0F, 0.5F, 4); }
        }
        if (!b.arrived(tt)) {
            Vec3 p = b.at(tt);
            double ax = -b.dir.z, az = b.dir.x, al = Math.sqrt(ax * ax + az * az);
            if (al < 1e-6) { ax = 1.0; az = 0.0; al = 1.0; }
            ax /= al; az /= al;
            double bxv = b.dir.y * az, byv = b.dir.z * ax - b.dir.x * az, bzv = -b.dir.y * ax;
            for (int i = TRAIL; i >= 1; i--) {
                double t = tt - i * TRAIL_STEP;
                if (t < b.t0) continue;
                Vec3 q = b.at(t);
                float f = 1.0F - i / (float) (TRAIL + 1);
                ClientBlastGeo.disc(bb, cam, left, up, q.x, q.y, q.z, R * (0.3 + 0.85 * f), 0.5F - 0.05F * f, 0.4F + 0.5F * f, 1.0F, 0.55F * f * f);
            }
            for (int st = 0; st < 2; st++) {
                double lx = p.x, ly = p.y, lz = p.z;
                float la = 0.65F;
                for (int i = 1; i <= TRAIL; i++) {
                    double t = tt - i * TRAIL_STEP;
                    if (t < b.t0) break;
                    Vec3 q = b.at(t);
                    float f = 1.0F - i / (float) (TRAIL + 1);
                    double ph = tt * 0.9 + i * 0.55 + st * Math.PI, off = R * 0.85 * f, co = Math.cos(ph) * off, so = Math.sin(ph) * off;
                    double qx = q.x + ax * co + bxv * so, qy = q.y + byv * so, qz = q.z + az * co + bzv * so;
                    float qa = 0.65F * f;
                    ClientBlastGeo.streak(bb, cam, lx, ly, lz, qx, qy, qz, R * 0.07, st == 0 ? 0.45F : 0.72F, st == 0 ? 0.88F : 0.5F, 1.0F, la, qa);
                    lx = qx; ly = qy; lz = qz; la = qa;
                }
            }
            for (int k = 0; k < SPARKS; k++) {
                double born = Math.floor((tt - k * 1.7) / SPARK_LIFE) * SPARK_LIFE + k * 1.7, sa = tt - born;
                if (born < b.t0 + 0.5) continue;
                Vec3 q = b.at(born);
                long hk = b.id * 131L + (long) born * 7L + k;
                double sx = (ClientPlasmaMortar.h(hk) - 0.5) * R * 1.6, sz = (ClientPlasmaMortar.h(hk + 1) - 0.5) * R * 1.6;
                double fx = q.x + sx + (ClientPlasmaMortar.h(hk + 2) - 0.5) * 0.04 * sa, fy = q.y - 0.012 * sa * sa, fz = q.z + sz + (ClientPlasmaMortar.h(hk + 3) - 0.5) * 0.04 * sa;
                float life = (float) (1.0 - sa / SPARK_LIFE);
                ClientBlastGeo.disc(bb, cam, left, up, fx, fy, fz, R * 0.24, 0.75F, 0.55F, 1.0F, 0.85F * life);
                ClientBlastGeo.disc(bb, cam, left, up, fx, fy, fz, R * 0.09, 1.0F, 0.95F, 1.0F, 0.9F * life);
            }
            ClientPlasmaMortar.ball(bb, cam, left, up, p, R, tt, b.id, 1.0F);
            VeilBridge.glow(0x424F4C54L ^ b.id, p.x, p.y, p.z, 0.4F, 0.75F, 1.0F, 4.0F + 4.0F * b.k, 1.6F, now);
            return;
        }
        double ba = tt - b.endT;
        if (ba > BURST) return;
        Vec3 c = b.start.add(b.dir.scale(b.end));
        float fade = (float) Math.max(0.0, 1.0 - ba / BURST);
        if (!b.burst) { b.burst = true; VeilBridge.flash(c.x, c.y, c.z, 0.45F, 0.85F, 1.0F, 10.0F + 8.0F * b.k, 3.0F, 0.6F, 8); }
        ClientPlasmaMortar.ball(bb, cam, left, up, c, R * (1.0 + ba * 0.45), tt, b.id, fade);
        ClientBlastGeo.disc(bb, cam, left, up, c.x, c.y, c.z, R * (2.0 + ba * 0.9), 0.4F, 0.75F, 1.0F, 0.35F * fade);
        for (int i = 0; i < 10; i++) {
            long hk = b.id * 977L + i;
            double yaw = ClientPlasmaMortar.h(hk) * Math.PI * 2, pit = Math.asin(ClientPlasmaMortar.h(hk + 1) * 2.0 - 1.0), sp = 0.12 + ClientPlasmaMortar.h(hk + 2) * 0.12;
            double dist = sp * ba;
            double fx = c.x + Math.cos(pit) * Math.cos(yaw) * dist, fy = c.y + Math.sin(pit) * dist - 0.01 * ba * ba, fz = c.z + Math.cos(pit) * Math.sin(yaw) * dist;
            ClientBlastGeo.disc(bb, cam, left, up, fx, fy, fz, R * 0.3, 0.6F, 0.85F, 1.0F, 0.9F * fade);
        }
        VeilBridge.glow(0x42555253L ^ b.id, c.x, c.y, c.z, 0.45F, 0.8F, 1.0F, 6.0F + 6.0F * b.k, 2.4F * fade, now);
    }

    private static void hexBubble(BufferBuilder bb, Vec3 cam, Vec3 c, double r, float a, double tt) {
        int mer = 8, par = 5, seg = 12;
        for (int i = 0; i < mer; i++) {
            double ph = Math.PI * 2 * i / mer + tt * 0.02;
            Vec3 prev = null;
            for (int j = 0; j <= seg; j++) {
                double th = Math.PI * j / seg;
                Vec3 q = c.add(Math.sin(th) * Math.cos(ph) * r, Math.cos(th) * r * 1.2, Math.sin(th) * Math.sin(ph) * r);
                if (prev != null) ClientBlastGeo.streak(bb, cam, prev.x, prev.y, prev.z, q.x, q.y, q.z, 0.02, 0.35F, 0.85F, 1.0F, 0.55F * a, 0.55F * a);
                prev = q;
            }
        }
        for (int j = 1; j < par; j++) {
            double th = Math.PI * j / par;
            Vec3 prev = null;
            for (int i = 0; i <= 16; i++) {
                double ph = Math.PI * 2 * i / 16;
                Vec3 q = c.add(Math.sin(th) * Math.cos(ph) * r, Math.cos(th) * r * 1.2, Math.sin(th) * Math.sin(ph) * r);
                if (prev != null) ClientBlastGeo.streak(bb, cam, prev.x, prev.y, prev.z, q.x, q.y, q.z, 0.02, 0.35F, 0.85F, 1.0F, 0.45F * a, 0.45F * a);
                prev = q;
            }
        }
    }
}
