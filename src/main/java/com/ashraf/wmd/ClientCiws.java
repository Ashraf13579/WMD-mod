package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientCiws {

    private ClientCiws() {}

    private static final double SPEED = 30.0;

    static final int ROUNDS = 5;
    private static final float PLAIN_LEN = 2.6F, PLAIN_W = 0.03F, TRACER_LEN = 7.5F, TRACER_W = 0.055F, TRACER_GLOW = 0.26F;

    private static final double SMALL_SPEED = 16.0;
    private static final float SMALL_PLAIN_LEN = 0.9F, SMALL_PLAIN_W = 0.012F, SMALL_TRACER_LEN = 2.4F, SMALL_TRACER_W = 0.022F, SMALL_TRACER_GLOW = 0.09F;
    static final int SMALL_TRACER_EVERY = 3;

    private static final class Round {
        final Vec3 from, dir; final double t0, len; final boolean tracer, small; final long key;
        Round(Vec3 from, Vec3 dir, double t0, double len, boolean tracer, long key) { this(from, dir, t0, len, tracer, false, key); }
        Round(Vec3 from, Vec3 dir, double t0, double len, boolean tracer, boolean small, long key) { this.from = from; this.dir = dir; this.t0 = t0; this.len = len; this.tracer = tracer; this.small = small; this.key = key; }
        double flown(double tt) { return (tt - t0) * (small ? SMALL_SPEED : SPEED); }
        float streakLen() { return small ? (tracer ? SMALL_TRACER_LEN : SMALL_PLAIN_LEN) : (tracer ? TRACER_LEN : PLAIN_LEN); }
    }

    static void smallRound(net.minecraft.world.level.Level lv, Vec3 from, Vec3 to, boolean tracer) {
        if (!(lv instanceof ClientLevel level)) return;
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.5) return;
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) len = Math.max(0.5, hit.getLocation().distanceTo(from));
        TRACERS.add(new Round(from, d.normalize(), level.getGameTime() + level.random.nextDouble() * 0.5, len, tracer, true, ++keySeq));
    }

    private static final List<Round> TRACERS = new ArrayList<>();
        private static long keySeq;
    private static float shake;

    private static final class GunSound extends AbstractTickableSoundInstance {
        private final BlockPos pos;

        GunSound(net.minecraft.sounds.SoundEvent ev, BlockPos pos, boolean loop) {
            super(ev, SoundSource.BLOCKS, RandomSource.create());
            this.pos = pos;
            this.looping = loop;
            this.volume = 9.0F;
            this.pitch = 1.0F;
            Vec3 w = ShipFrame.drawnWorld(Minecraft.getInstance().level, pos, new Vec3(pos.getX() + 0.5, pos.getY() + 3.0, pos.getZ() + 0.5), Float.NaN);
            this.x = w.x; this.y = w.y; this.z = w.z;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
        }

        void halt() { stop(); }

        @Override
        public void tick() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || !(level.getBlockEntity(pos) instanceof CiwsBlockEntity || level.getBlockEntity(pos) instanceof SalvageTurretBlockEntity)) { stop(); return; }
            Vec3 w = ShipFrame.drawnWorld(level, pos, new Vec3(pos.getX() + 0.5, pos.getY() + 3.0, pos.getZ() + 0.5), Float.NaN);
            this.x = w.x; this.y = w.y; this.z = w.z;
        }
    }

    private static final class Session {
        GunSound current;
        int phase;
        long since;
        int quiet;
    }

    private static final int START_TICKS = 29, QUIET_TICKS = 6;
    private static final Map<BlockPos, Session> SESSIONS = new HashMap<>();

    private static void play(Session s, net.minecraft.sounds.SoundEvent ev, BlockPos pos, boolean loop, int phase, long now) {
        if (s.current != null) s.current.halt();
        s.current = new GunSound(ev, pos, loop);
        s.phase = phase;
        s.since = now;
        Minecraft.getInstance().getSoundManager().play(s.current);
    }

    public static void fire(CiwsFirePayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 muzzle = new Vec3(p.fx(), p.fy(), p.fz()), eject = muzzle, side = new Vec3(1, 0, 0);
        if (level.getBlockEntity(p.pos()) instanceof CiwsBlockEntity be) {
            muzzle = be.muzzleWorld(); eject = be.ejectWorld(); side = be.sideWorld();
            be.recoil = 2.4F; be.heat = Math.min(1.0F, be.heat + 0.05F);
        }
        Vec3 to = new Vec3(p.tx(), p.ty(), p.tz());
        Vec3 d = to.subtract(muzzle);
        double len = d.length();
        if (len < 2.0) return;
        Vec3 dir = d.scale(1.0 / len);
        long t = level.getGameTime();
        var rnd = level.random;

        Session ss = SESSIONS.get(p.pos());
        if (ss == null) { ss = new Session(); SESSIONS.put(p.pos(), ss); play(ss, WmdSounds.CIWS_START.get(), p.pos(), false, 0, t); }
        else if (ss.phase == 2) play(ss, WmdSounds.CIWS_START.get(), p.pos(), false, 0, t);
        ss.quiet = 0;
        level.playLocalSound(muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F, 0.45F + rnd.nextFloat() * 0.1F, false);

        float flick = 0.75F + rnd.nextFloat() * 0.25F;
        VeilBridge.glow(0x2C15_0000_0000_0001L ^ p.pos().asLong(), muzzle.x + dir.x * 2, muzzle.y + dir.y * 2, muzzle.z + dir.z * 2, 1.0F, 0.72F, 0.3F, 13.0F + flick * 5.0F, flick, t);
        VeilBridge.flash(muzzle.x + dir.x * 3, muzzle.y + dir.y * 3, muzzle.z + dir.z * 3, 1.0F, 0.7F, 0.28F, 30.0F, 2.2F, 3);
        VeilBridge.bloomNear(muzzle.x, muzzle.y, muzzle.z, 90.0, 0.6F);
        for (int i = 0; i < 4; i++) {
            double sp = 0.18 + rnd.nextDouble() * 0.22, sx = (rnd.nextDouble() - 0.5) * 0.18, sy = (rnd.nextDouble() - 0.5) * 0.18, sz = (rnd.nextDouble() - 0.5) * 0.18;
            level.addParticle(ParticleTypes.FLAME, muzzle.x + dir.x * 1.4, muzzle.y + dir.y * 1.4, muzzle.z + dir.z * 1.4, dir.x * sp + sx, dir.y * sp + sy, dir.z * sp + sz);
        }
        level.addParticle(ParticleTypes.LARGE_SMOKE, muzzle.x + dir.x * 2.0, muzzle.y + dir.y * 2.0, muzzle.z + dir.z * 2.0, dir.x * 0.1, 0.03, dir.z * 0.1);
        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x + dir.x * 2.5, muzzle.y + dir.y * 2.5, muzzle.z + dir.z * 2.5, dir.x * 0.05, 0.05, dir.z * 0.05);

        for (int i = 0; i < ROUNDS; i++) {
            Vec3 spread = new Vec3((rnd.nextDouble() - 0.5) * 0.012, (rnd.nextDouble() - 0.5) * 0.012, (rnd.nextDouble() - 0.5) * 0.012);
            TRACERS.add(new Round(muzzle.add(dir.scale(2.5)), dir.add(spread).normalize(), t + i / (double) ROUNDS, Math.min(len + 40.0, Balance.CIWS_RANGE + 80.0),
                    i == ROUNDS - 1, keySeq++));
        }

        for (int i = 0; i < 4; i++) {
            Vec3 v = side.scale(0.18 + rnd.nextDouble() * 0.14).add(0, 0.12 + rnd.nextDouble() * 0.12, 0).add(dir.scale(-0.04 - rnd.nextDouble() * 0.05));
            level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)), eject.x, eject.y, eject.z, v.x, v.y, v.z);
        }
        level.playLocalSound(eject.x, eject.y, eject.z, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.5F, 1.8F, false);
        if (p.hit()) {
            level.addParticle(ParticleTypes.CRIT, to.x, to.y, to.z, 0.4, 0.4, 0.4);
            level.addParticle(ParticleTypes.LAVA, to.x, to.y, to.z, 0.0, 0.0, 0.0);
            level.addParticle(ParticleTypes.POOF, to.x, to.y, to.z, 0.0, 0.05, 0.0);
            VeilBridge.flash(to.x, to.y, to.z, 1.0F, 0.8F, 0.4F, 18.0F, 1.5F, 2);
        }

        var player = Minecraft.getInstance().player;
        if (player != null) {
            double dist = player.position().distanceTo(muzzle);
            if (dist < 70.0) shake = Math.max(shake, (float) (1.0 - dist / 70.0) * 0.55F + 0.04F);
        }
    }

    private static void stepSounds(long now) {
        Iterator<Map.Entry<BlockPos, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Session> e = it.next();
            Session s = e.getValue();
            s.quiet++;
            if (s.phase == 0 && s.quiet <= QUIET_TICKS && now - s.since >= START_TICKS) play(s, WmdSounds.CIWS_LOOP.get(), e.getKey(), true, 1, now);
            else if (s.phase < 2 && s.quiet > QUIET_TICKS) play(s, WmdSounds.CIWS_END.get(), e.getKey(), false, 2, now);
            else if (s.phase == 2 && now - s.since > 75) { if (s.current != null) s.current.halt(); it.remove(); }
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { TRACERS.clear(); SESSIONS.clear(); shake = 0; return; }
        shake *= 0.82F;
        stepSounds(level.getGameTime());
        long t = level.getGameTime();

        TRACERS.removeIf(rd -> rd.flown(t) > rd.len + rd.streakLen());
        for (Round rd : TRACERS) {
            if (!rd.tracer) continue;
            double f = rd.flown(t + 0.5);
            if (f < 0.0 || f > rd.len) continue;
            Vec3 h = rd.from.add(rd.dir.scale(f));
            if (rd.small) VeilBridge.glow(0x2C15_0002_0000_0000L ^ rd.key, h.x, h.y, h.z, 1.0F, 0.5F, 0.2F, 3.5F, 0.6F, t);
            else VeilBridge.glow(0x2C15_0001_0000_0000L ^ rd.key, h.x, h.y, h.z, 1.0F, 0.42F, 0.16F, 10.0F, 1.1F, t);
        }
        if (TRACERS.size() > 400) TRACERS.subList(0, TRACERS.size() - 400).clear();
    }

    @SubscribeEvent
    static void onRender(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES || TRACERS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        double tt = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var rnd = level.random;
        com.mojang.blaze3d.vertex.BufferBuilder bb = null;
        for (Round rd : TRACERS) {
            double f = rd.flown(tt);
            if (f <= 0.0) continue;
            double headD = Math.min(f, rd.len), tailD = Math.max(0.0, f - rd.streakLen());
            if (tailD >= headD) continue;
            Vec3 head = rd.from.add(rd.dir.scale(headD)).subtract(cam), tail = rd.from.add(rd.dir.scale(tailD)).subtract(cam);
            Vec3 side = rd.dir.cross(head);
            if (side.lengthSqr() < 1.0E-9) continue;
            side = side.normalize();
            if (bb == null) bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
            float sizzle = 0.7F + rnd.nextFloat() * 0.3F;
            if (rd.small) {
                if (rd.tracer) {
                    streak(bb, head, tail, side, SMALL_TRACER_GLOW, 1.0F, 0.4F, 0.14F, 0.4F * sizzle, 0.0F);
                    streak(bb, head, tail, side, SMALL_TRACER_W, 1.0F, 0.85F, 0.65F, 1.0F, 0.15F);
                } else {
                    streak(bb, head, tail, side, SMALL_PLAIN_W * 2.5F, 1.0F, 0.6F, 0.25F, 0.2F * sizzle, 0.0F);
                    streak(bb, head, tail, side, SMALL_PLAIN_W, 1.0F, 0.93F, 0.78F, 0.9F * sizzle, 0.0F);
                }
            } else if (rd.tracer) {
                streak(bb, head, tail, side, TRACER_GLOW, 1.0F, 0.35F, 0.12F, 0.45F * sizzle, 0.0F);
                streak(bb, head, tail, side, TRACER_W, 1.0F, 0.85F, 0.7F, 1.0F, 0.15F);
            } else {
                streak(bb, head, tail, side, PLAIN_W * 2.5F, 1.0F, 0.6F, 0.25F, 0.22F * sizzle, 0.0F);
                streak(bb, head, tail, side, PLAIN_W, 1.0F, 0.93F, 0.78F, 0.95F * sizzle, 0.0F);
            }
        }
        if (bb != null) ClientBlastGeo.draw(event, bb, true, true);
    }

    private static void streak(com.mojang.blaze3d.vertex.BufferBuilder bb, Vec3 head, Vec3 tail, Vec3 side, float w, float r, float g, float b, float a, float tailA) {
        Vec3 s = side.scale(w);
        bb.addVertex((float) (head.x - s.x), (float) (head.y - s.y), (float) (head.z - s.z)).setColor(r, g, b, a);
        bb.addVertex((float) (head.x + s.x), (float) (head.y + s.y), (float) (head.z + s.z)).setColor(r, g, b, a);
        bb.addVertex((float) (tail.x + s.x * 0.4), (float) (tail.y + s.y * 0.4), (float) (tail.z + s.z * 0.4)).setColor(r, g * 0.7F, b * 0.5F, a * tailA);
        bb.addVertex((float) (tail.x - s.x * 0.4), (float) (tail.y - s.y * 0.4), (float) (tail.z - s.z * 0.4)).setColor(r, g * 0.7F, b * 0.5F, a * tailA);
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (shake < 0.02F) return;
        var rnd = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.random;
        if (rnd == null) return;
        event.setPitch(event.getPitch() + (rnd.nextFloat() - 0.5F) * shake * 1.6F);
        event.setYaw(event.getYaw() + (rnd.nextFloat() - 0.5F) * shake * 1.6F);
        event.setRoll(event.getRoll() + (rnd.nextFloat() - 0.5F) * shake * 0.8F);
    }
}
