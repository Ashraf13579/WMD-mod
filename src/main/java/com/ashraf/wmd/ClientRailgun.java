package com.ashraf.wmd;

import java.util.Iterator;
import java.util.Map;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientRailgun {

    private ClientRailgun() {}

    static void flat(net.minecraft.sounds.SoundEvent e, float vol, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(e.getLocation(), net.minecraft.sounds.SoundSource.AMBIENT,
                vol, pitch, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom(), false, 0, net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
    }

    private static final class GunLoop extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private final RailgunBlockEntity be;
        private final boolean fin;
        GunLoop(RailgunBlockEntity be, boolean fin) {
            super(fin ? WmdSounds.RG_FINAL_LOOP.get() : WmdSounds.RG_SLEW_LOOP.get(), net.minecraft.sounds.SoundSource.BLOCKS, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            this.be = be;
            this.fin = fin;
            this.looping = true;
            this.volume = fin ? 2.0F : 3.0F;
            double[] c = be.worldPoint(0.0, fin ? 6.0 : 3.0, 7.0);
            this.x = c[0]; this.y = c[1]; this.z = c[2];
        }
        boolean on() { return fin ? finalMinute(be) : be.state() == RailgunBlockEntity.SLEW; }
        @Override public void tick() {
            if (be.isRemoved() || !on()) { stop(); return; }
            if (fin) { float k = 1.0F - be.secondsToShot() / (float) (Balance.RG_FINAL_TICKS / 20); volume = 2.0F + 3.0F * k; pitch = 0.95F + 0.15F * k; }
        }
    }

    private static final class HumLoop extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private final RailgunBlockEntity be;
        HumLoop(RailgunBlockEntity be) {
            super(WmdSounds.RG_HUM_LOOP.get(), net.minecraft.sounds.SoundSource.BLOCKS, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            this.be = be;
            this.looping = true;
            this.volume = 1.5F;
            this.pitch = 0.7F;
            double[] c = be.worldPoint(0.0, 4.0, 7.0);
            this.x = c[0]; this.y = c[1]; this.z = c[2];
        }
        @Override public void tick() {
            if (be.isRemoved() || be.state() != RailgunBlockEntity.CHARGE) { stop(); return; }
            float ch = be.charge01();
            volume = 1.5F + 2.5F * ch;
            pitch = 0.7F + 0.6F * ch;
        }
    }

    private static final Map<Long, HumLoop> HUM = new java.util.HashMap<>();

    static boolean finalMinute(RailgunBlockEntity be) { return be.state() == RailgunBlockEntity.CHARGE && be.secondsToShot() <= Balance.RG_FINAL_TICKS / 20; }

    private static final Map<Long, GunLoop> SLEW = new java.util.HashMap<>(), FINAL = new java.util.HashMap<>();
    private static final Map<Long, long[]> HEARD = new java.util.HashMap<>();

    @SubscribeEvent
    static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || RailgunEventPayload.EVENTS.isEmpty()) { HEARD.clear(); return; }
        long now = mc.level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Map.Entry<Long, Object[]> en : RailgunEventPayload.EVENTS.entrySet()) {
            RailgunEventPayload p = (RailgunEventPayload) en.getValue()[0];
            long[] h = HEARD.computeIfAbsent(en.getKey(), k -> new long[] { Long.MIN_VALUE, Long.MIN_VALUE, -1, Long.MIN_VALUE });
            long impact = p.fireTick() + Balance.RG_TRAVEL_TICKS;
            if (p.stage() == RailgunEventPayload.FIRED) {
                double dg = Math.sqrt(cam.distanceToSqr(p.mx(), p.my(), p.mz()));
                if (h[0] != p.fireTick() && Math.abs(now - p.fireTick()) < 40) {
                    h[0] = p.fireTick();
                    if (dg > 200.0) flat(WmdSounds.RG_SHOT_FAR.get(), (float) Math.max(0.25, 1.0 - dg / 6000.0), 0.9F);
                }
                if (h[3] != p.fireTick() && dg > Balance.RG_BOOM_MIN && now >= p.fireTick() + dg / Balance.RG_BOOM_SPEED && now < p.fireTick() + dg / Balance.RG_BOOM_SPEED + 40) {
                    h[3] = p.fireTick();
                    flat(dg < 1500.0 ? WmdSounds.SONIC_BOOM.get() : WmdSounds.SONIC_BOOM_FAR.get(), (float) Math.max(0.3, 1.0 - dg / 6000.0), 0.75F);
                }
                double dt = Math.sqrt(cam.distanceToSqr(p.tx() + 0.5, p.ty(), p.tz() + 0.5));
                if (h[1] != p.fireTick() && now >= impact - 120 && now < impact && (ClientCutscene.active() || dt < 500.0)) {
                    h[1] = p.fireTick();
                    flat(WmdSounds.RG_FLIGHT.get(), ClientCutscene.active() ? 1.0F : (float) Math.max(0.3, 1.0 - dt / 500.0), 1.0F);
                }
            } else if (p.stage() == RailgunEventPayload.CHARGING || p.stage() == RailgunEventPayload.FINAL) {
                long left = impact - now;
                if (left > 0 && left <= Balance.RG_FINAL_TICKS && left / 100 != h[2]) {
                    h[2] = left / 100;
                    flat(WmdSounds.RG_COUNTDOWN.get(), 0.6F, left <= 200 ? 1.15F : 1.0F);
                }
            }
        }
    }

    static void tickGun(RailgunBlockEntity be, Level level) {
        long gk = be.getBlockPos().asLong();
        if (be.state() == RailgunBlockEntity.SLEW) {
            GunLoop sl = SLEW.get(gk);
            if (sl == null || sl.isStopped()) { sl = new GunLoop(be, false); SLEW.put(gk, sl); Minecraft.getInstance().getSoundManager().play(sl); }
        } else SLEW.remove(gk);
        if (be.state() == RailgunBlockEntity.CHARGE) {
            HumLoop h = HUM.get(gk);
            if (h == null || h.isStopped()) { h = new HumLoop(be); HUM.put(gk, h); Minecraft.getInstance().getSoundManager().play(h); }
        } else HUM.remove(gk);
        if (finalMinute(be)) {
            GunLoop fl = FINAL.get(gk);
            if (fl == null || fl.isStopped()) { fl = new GunLoop(be, true); FINAL.put(gk, fl); Minecraft.getInstance().getSoundManager().play(fl); }
        } else FINAL.remove(gk);
        if (ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)) return;
        ClientRailgunShow.tick(be, level);
        long t = level.getGameTime();
        long key = be.getBlockPos().asLong() ^ 0x5241_494C_0000_0000L;
        RandomSource rnd = level.random;
        int st = be.state();
        float ch = st == RailgunBlockEntity.CHARGE ? be.charge01() : 0.0F;
        boolean fin = st == RailgunBlockEntity.CHARGE && be.secondsToShot() <= Balance.RG_FINAL_TICKS / 20;
        double[][] banks = { be.worldPoint(-6.2, 4.0, 0.8), be.worldPoint(6.2, 4.0, 0.8), be.worldPoint(-6.2, 4.0, 13.2), be.worldPoint(6.2, 4.0, 13.2) };
        if (st == RailgunBlockEntity.CHARGE || st == RailgunBlockEntity.LOAD || st == RailgunBlockEntity.SLEW) {
            int head = RailgunBlockEntity.colHead(t, ch);
            for (int i = 0; i < 4; i++) {
                VeilBridge.machineGlow(key + i, banks[i][0], banks[i][1], banks[i][2], 0.45F, 0.7F, 1.0F, 4.0F + 6.0F * ch, 0.3F + 2.5F * ch, 0.5F, t);
                if (head < 6 && !fin) {
                    double[] hp = be.worldPoint(i % 2 == 0 ? -6.19 : 6.19, RailgunBlockEntity.colCellY(head), i < 2 ? 0.81 : 13.19);
                    VeilBridge.machineGlow(key + 40 + i, hp[0], hp[1], hp[2], 0.5F, 0.75F, 1.0F, 5.0F, 1.5F + 2.5F * ch, 0.5F, t);
                }
            }
            if (rnd.nextFloat() < 0.02F + 0.25F * ch) {
                double[] b = banks[rnd.nextInt(4)];
                double ax = b[0] + rnd.nextGaussian() * 1.2, ay = b[1] + rnd.nextGaussian() * 2.0, az = b[2] + rnd.nextGaussian() * 1.2;
                VeilBridge.flash(ax, ay, az, 0.6F, 0.8F, 1.0F, 5.0F + 6.0F * ch, 2.5F, 0.3F, 4);
                for (int i = 0; i < 5; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, ax, ay, az, rnd.nextGaussian() * 0.15, rnd.nextGaussian() * 0.15, rnd.nextGaussian() * 0.15);
            }
            double[] m = be.muzzle();
            if (fin) {
                float k = 1.0F - be.secondsToShot() / (float) (Balance.RG_FINAL_TICKS / 20);
                VeilBridge.machineGlow(key + 10, m[0], m[1], m[2], 0.7F, 0.85F, 1.0F, 6.0F + 14.0F * k, 1.0F + 6.0F * k, 0.8F, t);
                if (rnd.nextFloat() < 0.15F + 0.5F * k) {
                    double s = 4.0 + rnd.nextDouble() * 24.0;
                    double[] c = be.worldPoint(0.0, 8.0, 7.0);
                    double px = c[0] + (m[0] - c[0]) * s / 28.0, py = c[1] + (m[1] - c[1]) * s / 28.0, pz = c[2] + (m[2] - c[2]) * s / 28.0;
                    VeilBridge.flash(px, py + 0.6, pz, 0.7F, 0.85F, 1.0F, 8.0F, 3.0F, 0.4F, 5);
                    for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py + 0.6, pz, rnd.nextGaussian() * 0.2, rnd.nextDouble() * 0.2, rnd.nextGaussian() * 0.2);
                }
                double[][] str = { be.worldPoint(-3.5, 3.1, 3.13), be.worldPoint(3.5, 3.1, 3.13) };
                for (int i = 0; i < 2; i++)
                    if (((t / 5) + i) % 2 == 0) VeilBridge.machineGlow(key + 20 + i, str[i][0], str[i][1], str[i][2], 1.0F, 0.15F, 0.15F, 8.0F, 2.5F, 0.4F, t);
            }
        }
        if (st == RailgunBlockEntity.AFTER) {
            int at = be.stateTicks();
            double[] m = be.muzzle();
            if (at <= 2) {
                VeilBridge.flash(m[0], m[1], m[2], 0.85F, 0.95F, 1.0F, 120.0F, 14.0F, 1.2F, 30);
                double[] g = be.worldPoint(0.0, 0.3, 7.0);
                VeilBridge.shock(g[0], g[1], g[2]);
                for (int i = 0; i < 60; i++) level.addParticle(ParticleTypes.END_ROD, m[0], m[1], m[2], rnd.nextGaussian() * 0.8, rnd.nextGaussian() * 0.8, rnd.nextGaussian() * 0.8);
                for (int i = 0; i < 40; i++) level.addParticle(ParticleTypes.CLOUD, m[0], m[1], m[2], rnd.nextGaussian() * 0.4, rnd.nextGaussian() * 0.3, rnd.nextGaussian() * 0.4);
            }
            if (at < Balance.RG_AFTER_TICKS * 0.6) {
                double[] c = be.worldPoint(0.0, 8.0, 7.0);
                float k = 1.0F - at / (Balance.RG_AFTER_TICKS * 0.6F);
                VeilBridge.machineGlow(key + 30, (c[0] + m[0]) / 2, (c[1] + m[1]) / 2, (c[2] + m[2]) / 2, 1.0F, 0.45F, 0.15F, 10.0F, 3.0F * k, 0.4F, t);
                if (rnd.nextFloat() < 0.4F * k) {
                    double s = rnd.nextDouble();
                    level.addParticle(ParticleTypes.CLOUD, c[0] + (m[0] - c[0]) * s, c[1] + (m[1] - c[1]) * s + 0.8, c[2] + (m[2] - c[2]) * s, 0.0, 0.05, 0.0);
                }
            }
        }
        if (st == RailgunBlockEntity.ABORT && be.stateTicks() < 60 && rnd.nextFloat() < 0.6F) {
            double[] b = banks[rnd.nextInt(4)];
            VeilBridge.flash(b[0], b[1] - 2.0, b[2], 0.6F, 0.8F, 1.0F, 14.0F, 4.0F, 0.5F, 6);
            for (int i = 0; i < 10; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, b[0], b[1], b[2], rnd.nextGaussian() * 0.4, rnd.nextGaussian() * 0.4, rnd.nextGaussian() * 0.4);
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { RailgunEventPayload.EVENTS.clear(); ROD_HEARD.clear(); }

    private static final Map<Long, Integer> ROD_HEARD = new java.util.HashMap<>();

    static boolean rodBroken(double x, double z) {
        for (Object[] e : RailgunEventPayload.EVENTS.values()) {
            RailgunEventPayload p = (RailgunEventPayload) e[0];
            if (p.kind() == RailgunEventPayload.KIND_ROD && p.stage() == RailgunEventPayload.FIRED && Math.abs(p.tx() - x) < 64 && Math.abs(p.tz() - z) < 64) return true;
        }
        return false;
    }

    @SubscribeEvent
    static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || RailgunEventPayload.EVENTS.isEmpty() || mc.options.hideGui) return;
        long now = mc.level.getGameTime();
        GuiGraphics g = event.getGuiGraphics();
        int y = 4, w = g.guiWidth();
        for (Iterator<Map.Entry<Long, Object[]>> it = RailgunEventPayload.EVENTS.entrySet().iterator(); it.hasNext(); ) {
            Object[] e = it.next().getValue();
            RailgunEventPayload p = (RailgunEventPayload) e[0];
            if ((long) e[1] < 0) e[1] = now;
            long age = now - (long) e[1];
            if (p.stage() == RailgunEventPayload.ABORTED && age > 120 || p.stage() == RailgunEventPayload.FIRED && age > Balance.RG_TRAVEL_TICKS + Balance.RG_TRACER_TICKS) { it.remove(); continue; }
            if (p.stage() == RailgunEventPayload.FIRED && age > 120) continue;
            if (p.kind() == RailgunEventPayload.KIND_ROD && p.stage() == RailgunEventPayload.FIRED) {
                WmdUi.eventBar(g, mc.font, Component.translatable("gui.wmd.rod.event_broken", p.tx(), p.tz()), y, WmdUi.WARN, WmdUi.EV_ROD);
                y += 17;
                continue;
            }
            if (age > 400) { it.remove(); continue; }
            Component line;
            int col;
            if (p.kind() == RailgunEventPayload.KIND_ROD) {
                if (p.stage() == RailgunEventPayload.ABORTED) { line = Component.translatable("gui.wmd.rod.event_aborted"); col = WmdUi.GOOD; }
                else {
                    long left = Math.max(0, p.fireTick() - now);
                    line = Component.translatable("gui.wmd.rod.event", p.tx(), p.tz(), OrbitMath.mmss(left * 50L));

                    col = p.stage() == RailgunEventPayload.CHARGING ? WmdUi.BLUE_LIGHT : ((now / 10) % 2 == 0) ? WmdUi.BAD : WmdUi.TEXT;
                }
            }
            else if (p.stage() == RailgunEventPayload.FIRED) { line = Component.translatable("gui.wmd.railgun.event_fired", p.owner(), p.tx(), p.tz()); col = WmdUi.BAD; }
            else if (p.stage() == RailgunEventPayload.ABORTED) { line = Component.translatable("gui.wmd.railgun.event_aborted", p.owner()); col = WmdUi.GOOD; }
            else {
                long left = Math.max(0, p.fireTick() + Balance.RG_TRAVEL_TICKS - now);
                boolean fin = p.stage() == RailgunEventPayload.FINAL || left <= Balance.RG_FINAL_TICKS;
                line = Component.translatable("gui.wmd.railgun.event_charging", p.owner(), p.tx(), p.tz(), OrbitMath.mmss(left * 50L));
                col = fin ? (((now / 10) % 2 == 0) ? WmdUi.BAD : WmdUi.TEXT) : WmdUi.BLUE_LIGHT;
            }
            WmdUi.eventBar(g, mc.font, line, y, col, p.kind() == RailgunEventPayload.KIND_ROD ? WmdUi.EV_ROD : WmdUi.EV_RAILGUN);
            y += 17;
        }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || RailgunEventPayload.EVENTS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        ClientBlastGeo.beginFrame();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Object[] e : RailgunEventPayload.EVENTS.values()) {
            RailgunEventPayload p = (RailgunEventPayload) e[0];
            double x = p.tx() + 0.5, y = p.ty() + 0.3, z = p.tz() + 0.5;
            if (p.kind() == RailgunEventPayload.KIND_ROD) {
                double ticksLeft = p.fireTick() - now;
                boolean broken = p.stage() == RailgunEventPayload.FIRED;
                if (!ClientCutscene.active()) ClientRodFx.draw(event, cam, left, up, x, y, z, ticksLeft, broken, now);
                if (ticksLeft > 0 && ticksLeft <= 60 && rodDimple(bb, level, cam, x, y, z, ticksLeft)) any = true;
                if (ticksLeft > 0 && ticksLeft <= 200) rodWind(level, cam, x, z, ticksLeft);
                int heard = ROD_HEARD.getOrDefault(p.gun(), 0);
                if (ticksLeft <= Balance.ROD_FALL_TICKS && ticksLeft > 0 && (heard & 1) == 0) {
                    heard |= 1;
                    flat(Math.hypot(cam.x - x, cam.z - z) < 600.0 ? WmdSounds.REENTRY.get() : WmdSounds.REENTRY_FAR.get(), 1.0F, 0.8F);
                }
                if (ticksLeft <= Balance.ROD_FALL_TICKS * 0.4 && ticksLeft > 0 && (heard & 2) == 0) {
                    heard |= 2;
                    flat(Math.hypot(cam.x - x, cam.z - z) < 600.0 ? WmdSounds.SONIC_BOOM.get() : WmdSounds.SONIC_BOOM_FAR.get(), 1.0F, 0.9F);
                }
                if (ticksLeft <= 115 && ticksLeft > 0 && (heard & 4) == 0 && !ClientCutscene.active()) {
                    heard |= 4;
                    flat(Math.hypot(cam.x - x, cam.z - z) < 600.0 ? WmdSounds.RD_FALL.get() : WmdSounds.RD_FALL_FAR.get(), 1.0F, 1.0F);
                }
                double dTarget = Math.hypot(cam.x - x, cam.z - z);
                if (ticksLeft <= 130 && ticksLeft > 0 && (heard & 8) == 0 && !ClientCutscene.active() && dTarget < 1500.0) {
                    heard |= 8;
                    flat(WmdSounds.RD_WHINE.get(), (float) Math.max(0.35, 1.0 - dTarget / 1500.0), 1.0F);
                }
                for (int b = 0; b < 2; b++) {
                    int bit = 16 << b;
                    if (ticksLeft <= 200 - 80 * b && ticksLeft > 0 && (heard & bit) == 0 && !ClientCutscene.active() && dTarget < 600.0) {
                        heard |= bit;
                        flat(WmdSounds.RD_HEARTBEAT.get(), 0.9F, 1.0F + 0.12F * b);
                    }
                }
                ROD_HEARD.put(p.gun(), heard);
                if (broken) continue;
            }
            if (p.stage() == RailgunEventPayload.FIRED) {
                double hitAge = now - (p.fireTick() + Balance.RG_TRAVEL_TICKS);
                if (hitAge >= 0.0 && hitAge < Balance.RG_CROWN_TICKS && cam.distanceTo(new Vec3(x, y, z)) < 1500.0) {
                    double fy = RailgunWreck.floor(x, z, y, (bx, bz) -> level.hasChunk(bx >> 4, bz >> 4)
                            ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) : ClientSnapshot.topAt(bx, bz));
                    any |= ClientRailgunShow.crown(bb, cam, x, fy, z, hitAge, p.gun());
                }
                if (ClientCutscene.active()) continue;
                long age = (long) e[1] < 0 ? 0 : (long) (now - (long) e[1]);
                Vec3 m0 = new Vec3(p.mx(), p.my(), p.mz()), t0 = new Vec3(x, y, z);
                double f = Math.min(1.0, age / (double) Balance.RG_TRAVEL_TICKS);
                float fade = age < Balance.RG_TRAVEL_TICKS ? 1.0F : (float) Math.max(0.0, 1.0 - (age - Balance.RG_TRAVEL_TICKS) / (double) Balance.RG_TRACER_TICKS);
                if (fade <= 0.0F) continue;

                java.util.List<Vec3> pts = new java.util.ArrayList<>();
                for (int i = 0; i <= 40; i++) pts.add(ClientRailgunShow.near(cam, RailgunArc.at(m0, t0, f * i / 40.0)));
                if (f >= 1.0) pts.add(ClientRailgunShow.near(cam, new Vec3(x, RailgunWreck.floor(x, z, y, (bx, bz) -> level.hasChunk(bx >> 4, bz >> 4)
                        ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) : ClientSnapshot.topAt(bx, bz)), z)));
                ClientRailgunShow.electricTrail(bb, cam, pts, f, level.getGameTime(), now, fade);
                if (f < 1.0) {
                    Vec3 sl = RailgunArc.at(m0, t0, f), sn = ClientRailgunShow.near(cam, sl);
                    ClientRailgunShow.slugDart(bb, cam, left, up, sn, RailgunArc.dir(m0, t0, f), now, Math.max(0.5, sn.distanceTo(cam) / Math.max(1.0, sl.distanceTo(cam))));
                }
                any = true;
                continue;
            }
            if (p.stage() != RailgunEventPayload.CHARGING && p.stage() != RailgunEventPayload.FINAL) continue;
            boolean fin = p.stage() == RailgunEventPayload.FINAL;
            if (p.kind() == RailgunEventPayload.KIND_ROD) {
                Vec3 dir = new Vec3(x - cam.x, 6000.0, z - cam.z).normalize();
                double tw = 0.8 + 0.2 * Math.sin(now * 0.7);
                ClientBlastGeo.disc(bb, cam, left, up, cam.x + dir.x * 300.0, cam.y + dir.y * 300.0, cam.z + dir.z * 300.0, 2.2 * tw, 1.0F, 0.97F, 0.9F, 0.95F, 12);
                ClientBlastGeo.disc(bb, cam, left, up, cam.x + dir.x * 300.0, cam.y + dir.y * 300.0, cam.z + dir.z * 300.0, 6.0 * tw, 1.0F, 0.85F, 0.7F, 0.25F, 14);

                if (Math.hypot(cam.x - x, cam.z - z) < 2500.0) rodLasers(bb, cam, left, up, x, y, z, p.fireTick() - now, now);
                any = true;
                continue;
            }

            if (Math.hypot(cam.x - x, cam.z - z) < 2500.0) {
                double pw = fin ? 1.6 + 0.6 * Math.sin(now * 0.3) : 0.5;
                float pa = fin ? 0.7F : 0.3F;
                ClientBlastGeo.streak(bb, cam, x, y, z, x, y + 420.0, z, pw * 3.0, 0.45F, 0.65F, 1.0F, 0.35F * pa, 0.0F);
                ClientBlastGeo.streak(bb, cam, x, y, z, x, y + 420.0, z, pw, 0.85F, 0.93F, 1.0F, pa, 0.0F);
                any = true;
            }
            if (Math.hypot(cam.x - x, cam.z - z) > 900.0) continue;
            double spin = now * (fin ? 0.03 : 0.008), pulse = 0.5 + 0.5 * Math.sin(now * (fin ? 0.35 : 0.12));
            float a = (float) (0.55 + 0.35 * pulse);
            float r = fin ? 0.75F : 0.55F, gg = fin ? 0.8F : 0.78F, b = 1.0F;
            double R = 24.0 + 2.0 * pulse, w = 0.18;

            ring(bb, cam, x, y, z, R, 64, w * 1.6, r, gg, b, a);
            ring(bb, cam, x, y, z, 6.0, 32, w, r, gg, b, a);
            for (int i = 0; i < 48; i++) {
                double ang = spin + i * Math.PI * 2 / 48;
                double r0 = 7.0, r1 = i % 4 == 0 ? R + 6.0 : R - 2.0;
                ClientBlastGeo.streak(bb, cam, x + Math.cos(ang) * r0, y, z + Math.sin(ang) * r0, x + Math.cos(ang) * r1, y, z + Math.sin(ang) * r1, w * 0.6, r, gg, b, a * 0.8F, a * 0.25F);
            }

            for (int i = 0; i < 4; i++) {
                double ang = -spin * 1.5 + i * Math.PI / 2;
                double bx = x + Math.cos(ang) * (R + 4.0), bz = z + Math.sin(ang) * (R + 4.0);
                box(bb, cam, bx, y, bz, 3.0, 6.0, w, r, gg, b, a * 0.9F);
            }
            for (int i = 0; i < 6; i++) {
                double ang = spin * 0.5 + i * Math.PI / 3;
                double sx = x + Math.cos(ang) * 60.0, sy = y + 90.0, sz = z + Math.sin(ang) * 60.0;
                ClientBlastGeo.streak(bb, cam, sx, sy, sz, x, y + 1.0, z, fin ? 0.9 : 0.5, r, gg, b, 0.15F * a, 0.6F * a);
                ClientBlastGeo.disc(bb, cam, left, up, sx, sy, sz, 2.0, r, gg, b, 0.6F * a, 10);
            }
            ClientBlastGeo.disc(bb, cam, left, up, x, y + 1.0, z, 3.0 + 2.0 * pulse, 0.85F, 0.92F, 1.0F, 0.7F * a, 14);
            any = true;
        }
        ClientBlastGeo.draw(event, bb, any, true);
    }

    private static void rodLasers(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, double x, double y, double z, double toImpact, double now) {

        double ticksLeft = toImpact - Balance.ROD_FALL_TICKS;
        if (ticksLeft <= 0.0) {
            double u = -ticksLeft / 20.0;
            if (u >= 1.0 || toImpact <= 0.0) return;
            float a = (float) (1.0 - u);
            ring(bb, cam, x, y + 0.3, z, 6.0 + 40.0 * u, 64, 0.6 + 0.8 * a, 1.0F, 0.2F, 0.15F, a);
            ClientBlastGeo.streak(bb, cam, x, y, z, x, y + Balance.ROD_LASER_SKY * 1.5, z, 3.0 * a, 1.0F, 0.55F, 0.5F, 0.9F * a, 0.0F);
            return;
        }
        double k = 1.0 - Math.min(1.0, Math.max(0.0, (ticksLeft - Balance.ROD_LASER_UNITE) / (Balance.ROD_LASER_WINDOW - Balance.ROD_LASER_UNITE)));
        double rg = Balance.ROD_LASER_R * Math.pow(1.0 - k, 1.3), rs = rg * 1.6 + 30.0 * (1.0 - k);
        double spin = now * (0.006 + 0.045 * k * k);
        boolean united = ticksLeft <= Balance.ROD_LASER_UNITE;
        double beat = Math.pow(Math.max(0.0, Math.sin(now * (0.25 + 0.35 * k))), 6.0);
        float a = (float) (0.55 + 0.4 * k + 0.2 * beat);
        if (!united) for (int i = 0; i < Balance.ROD_LASERS; i++) {
            double ang = spin + i * Math.PI * 2.0 / Balance.ROD_LASERS;
            double gx = x + Math.cos(ang) * rg, gz = z + Math.sin(ang) * rg;
            double sx = x + Math.cos(ang - 0.35) * rs, sz = z + Math.sin(ang - 0.35) * rs, sy = y + Balance.ROD_LASER_SKY;
            ClientBlastGeo.streak(bb, cam, sx, sy, sz, gx, y + 0.5, gz, 2.4, 1.0F, 0.12F, 0.08F, 0.10F * a, 0.30F * a);
            ClientBlastGeo.streak(bb, cam, sx, sy, sz, gx, y + 0.5, gz, 0.6, 1.0F, 0.45F, 0.40F, 0.55F * a, 0.95F * a);
            ClientBlastGeo.disc(bb, cam, left, up, gx, y + 0.8, gz, 2.6 + 1.2 * beat, 1.0F, 0.2F, 0.12F, 0.8F * a, 12);
            ring(bb, cam, gx, y + 0.3, gz, 3.5 + 1.5 * beat, 16, 0.25, 1.0F, 0.15F, 0.1F, 0.6F * a);
        }
        if (k > 0.85 || united) {
            double u = united ? 1.0 : (k - 0.85) / 0.15, w = (0.8 + 2.2 * u) * (1.0 + 0.35 * beat);
            ClientBlastGeo.streak(bb, cam, x, y, z, x, y + Balance.ROD_LASER_SKY * 1.5, z, w * 3.0, 1.0F, 0.1F, 0.06F, 0.25F * (float) u, 0.0F);
            ClientBlastGeo.streak(bb, cam, x, y, z, x, y + Balance.ROD_LASER_SKY * 1.5, z, w, 1.0F, 0.55F, 0.5F, 0.9F * (float) u, 0.1F * (float) u);
            ring(bb, cam, x, y + 0.3, z, 6.0 + 10.0 * beat, 48, 0.4, 1.0F, 0.15F, 0.1F, (float) (0.8 * u));
            ClientBlastGeo.disc(bb, cam, left, up, x, y + 1.0, z, 4.0 + 3.0 * beat, 1.0F, 0.35F, 0.25F, 0.9F * (float) u, 14);
        }
    }

    private static boolean rodDimple(BufferBuilder bb, ClientLevel level, Vec3 cam, double x, double y, double z, double ticksLeft) {
        double u = 1.0 - ticksLeft / 60.0, r = 8.0 + 112.0 * Math.pow(u, 0.8);
        float a = (float) (0.55 * Math.sin(Math.PI * Math.min(1.0, u * 1.1)));
        if (a <= 0.01F || Math.hypot(cam.x - x, cam.z - z) > 1500.0) return false;
        for (int i = 0; i < 48; i++) {
            double ang = i * Math.PI * 2.0 / 48, px = x + Math.cos(ang) * r, pz = z + Math.sin(ang) * r;
            double gy = ClientBlastGeo.ground(level, px, pz, y);
            ClientBlastGeo.streak(bb, cam, px, gy + 0.5, pz, px + Math.cos(ang) * 6.0, gy + 2.5, pz + Math.sin(ang) * 6.0, 4.0, 0.62F, 0.56F, 0.48F, a, 0.0F);
        }
        if (level.random.nextFloat() < 0.8F) {
            double ang = level.random.nextDouble() * Math.PI * 2.0, px = x + Math.cos(ang) * r, pz = z + Math.sin(ang) * r;
            level.addParticle(ParticleTypes.CLOUD, px, ClientBlastGeo.ground(level, px, pz, y) + 1.0, pz, Math.cos(ang) * 0.8, 0.15, Math.sin(ang) * 0.8);
        }
        return true;
    }

    private static void rodWind(ClientLevel level, Vec3 cam, double x, double z, double ticksLeft) {
        double d = Math.hypot(cam.x - x, cam.z - z);
        if (d > 600.0 || d < 4.0) return;
        double k = (1.0 - ticksLeft / 200.0) * (1.0 - d / 600.0);
        int n = level.random.nextFloat() < k * 2.0 ? 2 : 0;
        for (int i = 0; i < n; i++) {
            double px = cam.x + (level.random.nextDouble() - 0.5) * 24.0, pz = cam.z + (level.random.nextDouble() - 0.5) * 24.0;
            double vx = (x - px) / Math.max(1.0, Math.hypot(x - px, z - pz)), vz = (z - pz) / Math.max(1.0, Math.hypot(x - px, z - pz));
            level.addParticle(ParticleTypes.WHITE_ASH, px, cam.y - 1.0 + level.random.nextDouble() * 3.0, pz, vx * (0.3 + k), 0.02, vz * (0.3 + k));
        }
    }

    @SubscribeEvent
    static void onRodSky(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || RailgunEventPayload.EVENTS.isEmpty() || ClientCutscene.active()) return;
        Vec3 cam = event.getCamera().getPosition();
        double now = mc.level.getGameTime(), best = 0.0;
        for (Object[] e : RailgunEventPayload.EVENTS.values()) {
            RailgunEventPayload p = (RailgunEventPayload) e[0];
            if (p.kind() != RailgunEventPayload.KIND_ROD || p.stage() == RailgunEventPayload.ENDED || p.stage() == RailgunEventPayload.ABORTED) continue;
            double left = p.fireTick() - now, d = Math.hypot(cam.x - p.tx(), cam.z - p.tz());
            if (left <= 0 || left > 1200 || d > 1500.0) continue;
            best = Math.max(best, (1.0 - left / 1200.0) * (1.0 - d / 1500.0));
        }
        if (best <= 0.01) return;
        float k = (float) (0.4 * best);
        event.setRed(event.getRed() + (0.42F - event.getRed()) * k);
        event.setGreen(event.getGreen() + (0.2F - event.getGreen()) * k);
        event.setBlue(event.getBlue() + (0.18F - event.getBlue()) * k);
    }

    @SubscribeEvent
    static void onZoneHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || RailgunEventPayload.EVENTS.isEmpty() || mc.options.hideGui || ClientCutscene.active()) return;
        double now = mc.level.getGameTime();
        for (Object[] e : RailgunEventPayload.EVENTS.values()) {
            RailgunEventPayload p = (RailgunEventPayload) e[0];
            if (p.kind() != RailgunEventPayload.KIND_ROD || p.stage() == RailgunEventPayload.ENDED || p.stage() == RailgunEventPayload.ABORTED) continue;
            double left = p.fireTick() - now, dx = mc.player.getX() - (p.tx() + 0.5), dz = mc.player.getZ() - (p.tz() + 0.5), d = Math.hypot(dx, dz);
            if (left <= 0 || left > 1200 || d > WarheadFx.ROD_SHOCK) continue;
            GuiGraphics g = event.getGuiGraphics();
            int w = g.guiWidth(), h = g.guiHeight();
            double beat = Math.pow(Math.max(0.0, Math.sin(now * (0.3 + 0.3 * (1.0 - left / 1200.0)))), 6.0);
            int a = (int) (60 + 120 * beat), edge = Math.max(12, w / 14);
            for (int i = 0; i < edge; i += 2) {
                int c = ((int) (a * (1.0 - i / (double) edge)) << 24) | 0xC01010;
                g.fill(i, i, w - i, i + 2, c); g.fill(i, h - i - 2, w - i, h - i, c);
                g.fill(i, i, i + 2, h - i, c); g.fill(w - i - 2, i, w - i, h - i, c);
            }

            double away = Math.toDegrees(Math.atan2(-dx, dz)) + 180.0, rel = net.minecraft.util.Mth.wrapDegrees(away - mc.player.getYRot() + 180.0);
            String[] arrows = { "\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196" };
            String arrow = arrows[Math.floorMod((int) Math.round(rel / 45.0), 8)];
            Component line = Component.translatable("gui.wmd.rod.zone_out", arrow, (int) Math.ceil(WarheadFx.ROD_SHOCK - d), (int) Math.ceil(left / 20.0));
            g.pose().pushPose();
            g.pose().translate(w / 2.0F, h * 0.3F, 0.0F);
            g.pose().scale(1.6F, 1.6F, 1.0F);
            g.drawString(mc.font, line, -mc.font.width(line) / 2, 0, 0xFFFF5040, true);
            g.pose().popPose();
            return;
        }
    }

    private static void ring(BufferBuilder bb, Vec3 cam, double x, double y, double z, double r, int n, double w, float cr, float cg, float cb, float a) {
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            ClientBlastGeo.streak(bb, cam, x + Math.cos(a0) * r, y, z + Math.sin(a0) * r, x + Math.cos(a1) * r, y, z + Math.sin(a1) * r, w, cr, cg, cb, a, a);
        }
    }

    private static void box(BufferBuilder bb, Vec3 cam, double x, double y, double z, double s, double h, double w, float cr, float cg, float cb, float a) {
        double[][] c = { { -s, -s }, { s, -s }, { s, s }, { -s, s } };
        for (int i = 0; i < 4; i++) {
            double[] p = c[i], q = c[(i + 1) % 4];
            ClientBlastGeo.streak(bb, cam, x + p[0], y, z + p[1], x + q[0], y, z + q[1], w, cr, cg, cb, a, a);
            ClientBlastGeo.streak(bb, cam, x + p[0], y + h, z + p[1], x + q[0], y + h, z + q[1], w, cr, cg, cb, a, a);
            ClientBlastGeo.streak(bb, cam, x + p[0], y, z + p[1], x + p[0], y + h, z + p[1], w, cr, cg, cb, a, a * 0.4F);
        }
    }
}
