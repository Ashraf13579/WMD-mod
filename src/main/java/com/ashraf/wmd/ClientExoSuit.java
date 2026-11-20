package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientExoSuit {

    private ClientExoSuit() {}

    static final class Anim {
        float core, prevCore, shoulder, prevShoulder, shin, prevShin, jet, prevJet, heat, flame, prevFlame;
        int boostT = 99, leapT = 99, brakeT = 99, crouchT, empT = 999;
        boolean hover, crouch;
        long seen;

        float core(float pt) { return prevCore + (core - prevCore) * pt; }
        float shoulder(float pt) { return prevShoulder + (shoulder - prevShoulder) * pt; }
        float shin(float pt) { return prevShin + (shin - prevShin) * pt; }
        float jet(float pt) { return prevJet + (jet - prevJet) * pt; }
        float flame(float pt) { return prevFlame + (flame - prevFlame) * pt; }
    }

    private static final Map<Integer, Anim> ANIM = Statics.client(new HashMap<>());

    static Anim anim(Entity e) { return ANIM.get(e.getId()); }

    private static Anim of(int id) { return ANIM.computeIfAbsent(id, k -> new Anim()); }

    private static boolean wasGround = true, wasJump, boosted, braked, hovering;
    private static int crouchT, airT, hoverT;
    private static long coolUntil, lastTick;

    static void clearStatics() {
        ANIM.clear();
        wasGround = true; wasJump = false; boosted = false; braked = false; hovering = false;
        crouchT = 0; airT = 0; hoverT = 0; coolUntil = 0; lastTick = 0;
    }
    private static final ResourceLocation HUD = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "exo_hud");

    static void fx(ExoFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entity());
        if (!(e instanceof Player pl)) return;
        if (p.action() >= ExoMods.THERMAL_ON) { ClientExoMods.act(pl, p.action()); return; }
        if (pl == mc.player && p.action() != ExoSuit.EMP) return;
        act(pl, p.action());
    }

    static void act(Player pl, int a) {
        Anim an = of(pl.getId());
        switch (a) {
            case ExoSuit.BOOST -> { an.boostT = 0; an.heat = Math.min(1.0F, an.heat + 0.3F); play(pl, WmdSounds.OR_EVA_THRUST.get(), Balance.SND_MECH, 1.25F); }
            case ExoSuit.LEAP -> { an.leapT = 0; an.crouch = false; an.crouchT = 0; an.heat = Math.min(1.0F, an.heat + 0.2F); play(pl, WmdSounds.JET_AB_LIGHT.get(), Balance.SND_MECH, 1.4F); }
            case ExoSuit.HOVER_ON -> { an.hover = true; play(pl, WmdSounds.OR_EVA_THRUST.get(), Balance.SND_MECH, 1.0F); play(pl, WmdSounds.EXO_SERVO.get(), Balance.SND_SMALL, 1.1F); }
            case ExoSuit.HOVER_OFF -> { if (an.hover) play(pl, WmdSounds.PUMP_JET_STOP.get(), Balance.SND_SMALL, 1.4F); an.hover = false; }
            case ExoSuit.BRAKE -> { an.brakeT = 0; play(pl, WmdSounds.OR_EVA_THRUST.get(), Balance.SND_MECH, 0.85F); }
            case ExoSuit.CROUCH_ON -> { an.crouch = true; an.crouchT = 0; play(pl, WmdSounds.EXO_SERVO.get(), Balance.SND_SMALL, 0.9F); }
            case ExoSuit.CROUCH_OFF -> { an.crouch = false; an.crouchT = 0; }
            case ExoSuit.EMP -> { an.empT = 0; an.hover = false; play(pl, WmdSounds.EMP_ZAP.get(), Balance.SND_MECH, 1.4F); }
            default -> {}
        }
    }

    private static void play(Player pl, SoundEvent s, float vol, float pitch) {
        if (pl.level() instanceof ClientLevel cl) cl.playLocalSound(pl.getX(), pl.getY() + 1.0, pl.getZ(), s, SoundSource.PLAYERS, vol, pitch, false);
    }

    private static void send(LocalPlayer p, int a) {
        PacketDistributor.sendToServer(new ExoJetPayload(a));
        act(p, a);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { clearStatics(); return; }
        control(mc, p);

        long now = mc.level.getGameTime();
        for (Player pl : mc.level.players()) if (ExoSuitItem.anyPiece(pl)) step(pl, of(pl.getId()), now);
        for (Iterator<Map.Entry<Integer, Anim>> it = ANIM.entrySet().iterator(); it.hasNext(); ) if (now - it.next().getValue().seen > 40) it.remove();
    }

    private static void control(Minecraft mc, LocalPlayer p) {
        boolean ground = p.onGround();
        boolean jump = mc.options.keyJump.isDown();
        boolean edge = jump && !wasJump;
        boolean able = mc.screen == null && !p.isSpectator() && !p.getAbilities().flying && p.getVehicle() == null && !p.isInWater() && !p.isFallFlying()
                && ExoSuitItem.powered(p);
        float charge = ExoSuitItem.charge(p);
        double mult = charge < Balance.EXO_LOW ? Balance.EXO_LOW_THRUST : 1.0;
        long t = p.tickCount;
        if (t < lastTick) { hovering = false; hoverT = 0; crouchT = 0; coolUntil = 0; }
        lastTick = t;
        if (ground) { airT = 0; boosted = false; braked = false; } else airT++;
        if (!able) {
            if (hovering) { hovering = false; send(p, ExoSuit.HOVER_OFF); coolUntil = t + Math.min(Balance.EXO_HOVER_COOL, hoverT); }
            if (crouchT > 0) { crouchT = 0; send(p, ExoSuit.CROUCH_OFF); }
            wasGround = ground; wasJump = jump;
            return;
        }
        Vec3 v = p.getDeltaMovement();

        if (ground && p.isShiftKeyDown()) { if (crouchT++ == 0) send(p, ExoSuit.CROUCH_ON); }
        else if (crouchT > 0 && !(edge && wasGround)) { crouchT = 0; send(p, ExoSuit.CROUCH_OFF); }
        if (edge && wasGround && crouchT >= Balance.EXO_LEAP_CHARGE) {
            Vec3 look = p.getLookAngle();
            Vec3 h = new Vec3(look.x, 0.0, look.z);
            h = h.lengthSqr() < 1e-4 ? Vec3.ZERO : h.normalize().scale(Balance.EXO_LEAP_FWD * mult);
            p.setDeltaMovement(h.x, Balance.EXO_LEAP_VY * mult, h.z);
            p.hurtMarked = true;
            crouchT = 0;
            send(p, ExoSuit.LEAP);
        } else if (edge && !ground && !wasGround && airT > 3 && !boosted) {
            p.setDeltaMovement(v.x, Math.max(v.y, Balance.EXO_BOOST_VY * mult), v.z);
            p.hurtMarked = true;
            boosted = true;
            send(p, ExoSuit.BOOST);
        }

        boolean want = jump && !ground && airT > 6 && p.getDeltaMovement().y < 0.05 && t >= coolUntil;
        if (want && !hovering && hoverT == 0) { hovering = true; send(p, ExoSuit.HOVER_ON); }
        if (hovering) {
            hoverT++;
            if (!want || hoverT >= Balance.EXO_HOVER_MAX || ground) {
                hovering = false;
                coolUntil = t + Math.min(Balance.EXO_HOVER_COOL, hoverT);
                send(p, ExoSuit.HOVER_OFF);
            } else {
                Vec3 w = p.getDeltaMovement();
                boolean sputter = mult < 1.0 && (t / 3) % 2 == 0;
                double vy = sputter ? Math.max(w.y, -0.12) : Math.min(Balance.EXO_HOVER_VY, Math.max(w.y + 0.12, 0.0));
                p.setDeltaMovement(w.x, vy, w.z);
                p.resetFallDistance();
                p.hurtMarked = true;
            }
        }
        if (!jump || ground) hoverT = hovering ? hoverT : 0;

        Vec3 w = p.getDeltaMovement();
        if (!ground && !hovering && !braked && w.y < -Balance.EXO_BRAKE_SPEED && p.fallDistance > Balance.EXO_FALL_FREE && nearGround(p)) {
            p.setDeltaMovement(w.x * 0.6, Balance.EXO_BRAKE_VY, w.z * 0.6);
            p.hurtMarked = true;
            braked = true;
            send(p, ExoSuit.BRAKE);
        }
        wasGround = ground; wasJump = jump;
    }

    private static boolean nearGround(LocalPlayer p) {
        Vec3 a = p.position(), b = a.add(0.0, -Balance.EXO_BRAKE_H, 0.0);
        return p.level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p)).getType() != HitResult.Type.MISS;
    }

    private static void step(Player pl, Anim an, long now) {
        an.seen = now;
        an.prevCore = an.core; an.prevShoulder = an.shoulder; an.prevShin = an.shin; an.prevJet = an.jet; an.prevFlame = an.flame;
        int fe = ExoSuitItem.fe(ExoSuitItem.chest(pl));
        float charge = fe / (float) Balance.EXO_FE_CAP;
        boolean low = charge < Balance.EXO_LOW;
        an.boostT++; an.leapT++; an.brakeT++; an.empT++;
        if (an.crouch) an.crouchT++;
        boolean firing = an.boostT < 8 || an.leapT < 8 || an.brakeT < 6 || an.hover;
        an.core += fe <= 0 ? 0.0F : low ? Balance.EXO_CORE_LOW : firing ? Balance.EXO_CORE_JET : Balance.EXO_CORE_IDLE;
        an.shoulder = approach(an.shoulder, firing ? 1.0F : 0.0F, 0.1F);
        float shinT = an.crouch ? Math.min(1.0F, an.crouchT / (float) Balance.EXO_LEAP_CHARGE) : an.brakeT < 4 ? 0.3F : 0.0F;
        an.shin = approach(an.shin, shinT, an.crouch ? 0.06F : 0.5F);
        float jetT;
        if (an.boostT < 10 || an.leapT < 10) jetT = -Balance.EXO_JET_BOOST_DEG;
        else if (an.hover) {
            Vec3 d = pl.getDeltaMovement();
            float yaw = pl.getYRot() * Mth.DEG_TO_RAD;
            double fwd = -d.x * Mth.sin(yaw) + d.z * Mth.cos(yaw);
            jetT = Mth.clamp((float) (-fwd * 60.0), -Balance.EXO_JET_HOVER_DEG, Balance.EXO_JET_HOVER_DEG);
        } else jetT = 0.0F;
        if (low && fe > 0 && firing) jetT += (pl.getRandom().nextFloat() - 0.5F) * 12.0F;
        an.jet = approach(an.jet, jetT, 3.0F);
        float fl = an.boostT < 8 || an.leapT < 8 ? 1.0F : an.brakeT < 6 ? 0.8F : an.hover ? 0.6F + pl.getRandom().nextFloat() * 0.2F : 0.0F;
        if (low && fl > 0.0F && pl.getRandom().nextBoolean()) fl = 0.0F;
        an.flame = fl;
        if (an.hover) an.heat = Math.min(1.0F, an.heat + 1.0F / Balance.EXO_HOVER_MAX);
        else an.heat = Math.max(0.0F, an.heat - 1.0F / Balance.EXO_NOZZLE_COOL);
        if (!an.hover && an.heat > 0.9F && now % 3 == 0 && pl.level() instanceof ClientLevel cl) {
            float yaw = pl.yBodyRot * Mth.DEG_TO_RAD;
            double bx = Mth.sin(yaw) * 0.4, bz = -Mth.cos(yaw) * 0.4;
            cl.addParticle(ParticleTypes.CLOUD, pl.getX() + bx, pl.getY() + 0.75, pl.getZ() + bz, 0.0, 0.03, 0.0);
        }
        if (an.hover && now % 24 == 0) play(pl, WmdSounds.OR_EVA_THRUST.get(), Balance.SND_MECH, 1.0F);
    }

    private static float approach(float v, float t, float step) { return v < t ? Math.min(t, v + step) : Math.max(t, v - step); }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(HUD, ClientExoSuit::hud); }

    private static void hud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !ExoSuitItem.fullSet(p)) return;
        Font f = mc.font;
        int x = 6, y = g.guiHeight() / 2 + 20, w = 104, h = 40;
        g.fill(x, y, x + w, y + h, 0xCC101820);
        g.fill(x, y, x + w, y + 1, 0xFF3D8BFF);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF22303E);
        g.drawString(f, Component.translatable("hud.wmd.exo.title"), x + 5, y + 4, 0xFFBFD4EA, false);
        int fe = ExoSuitItem.fe(ExoSuitItem.chest(p));
        float k = fe / (float) Balance.EXO_FE_CAP;
        int col = k < 0.05F ? ((p.tickCount / 5) % 2 == 0 ? 0xFFFF4040 : 0xFF902020) : k < Balance.EXO_LOW ? 0xFFFF9A3C : 0xFF4AD7FF;
        int bx = x + 5, by = y + 16, bw = w - 10;
        g.fill(bx, by, bx + bw, by + 7, 0xFF0A1016);
        g.fill(bx + 1, by + 1, bx + 1 + Math.round((bw - 2) * k), by + 6, col);
        String pc = Math.round(k * 100.0F) + " %";
        g.drawString(f, pc, x + w - 5 - f.width(pc), y + 4, col, false);
        Anim an = anim(p);
        String st;
        int sc;
        if (an != null && an.empT < Balance.EXO_EMP_LOCK) { st = "hud.wmd.exo.emp"; sc = (p.tickCount / 5) % 2 == 0 ? 0xFFFF4040 : 0xFF902020; }
        else if (fe <= 0) { st = "hud.wmd.exo.empty"; sc = 0xFFFF4040; }
        else if (hovering) { st = "hud.wmd.exo.hover"; sc = 0xFF7FE0FF; }
        else if (p.tickCount < coolUntil) { st = "hud.wmd.exo.cooling"; sc = 0xFFFF9A3C; }
        else if (crouchT > 0) { st = crouchT >= Balance.EXO_LEAP_CHARGE ? "hud.wmd.exo.leap_ready" : "hud.wmd.exo.leap_charge"; sc = 0xFF7FFFB0; }
        else { st = "hud.wmd.exo.ready"; sc = 0xFF7FFFB0; }
        g.drawString(f, Component.translatable(st), x + 5, y + 27, sc, false);
    }
}
