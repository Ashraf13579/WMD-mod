package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientHandTools {

    private ClientHandTools() {}

    private static final ResourceLocation HUD = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hand_tool_hud");

    static final class Fx {
        int tool, state, target = -1;
        Vec3 at = Vec3.ZERO;
        float k;
        long seen, throwAt = -100;
    }

    static final class Anim {
        float prong, prongO, sight, sightO, ring, ringO, ringV, cap, capO, pulse, pulseO, fan, fanO, trig, trigO, beam, kick, kickO;
        float shroud, shroudO, guides, guidesO, vents, ventsO, fins, finsO, finV, valve, valveO, needle, needleO;
        boolean arcing;
        long seen;
        float l(float o, float n, float pt) { return o + (n - o) * pt; }
    }

    private static final Map<Integer, Fx> FX = Statics.client(new HashMap<>());
    private static final Map<Integer, Anim> ANIM = Statics.client(new HashMap<>());
    private static final Map<Integer, Loop> LOOPS = Statics.client(new HashMap<>());

    static LivingEntity drawing;

    static Anim anim(int id) { return ANIM.get(id); }

    static void fx(HandToolFxPayload p) {
        ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) return;
        Fx f = FX.computeIfAbsent(p.player(), i -> new Fx());
        if (p.tool() == HandToolFxPayload.TRACTOR && p.state() == TractorBeam.THROW) {
            f.throwAt = lv.getGameTime();
            Anim a = ANIM.computeIfAbsent(p.player(), i -> new Anim());
            a.pulse = 1.0F; a.kick = 1.0F; a.cap = 0.0F;
        }
        f.tool = p.tool(); f.state = p.state(); f.target = p.target(); f.at = new Vec3(p.x(), p.y(), p.z()); f.k = p.k(); f.seen = lv.getGameTime();
    }

    @SubscribeEvent
    static void livingPre(RenderLivingEvent.Pre<?, ?> e) { drawing = e.getEntity(); }

    @SubscribeEvent
    static void livingPost(RenderLivingEvent.Post<?, ?> e) { drawing = null; }

    @SubscribeEvent
    static void tickPre(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null) return;
        if (p.isUsingItem() && p.getUseItem().getItem() instanceof TractorBeamItem) {
            boolean click = false;
            while (mc.options.keyAttack.consumeClick()) click = true;
            Fx f = FX.get(p.getId());
            if (click && f != null && (f.state == TractorBeam.HOLD || f.state == TractorBeam.PULL)) PacketDistributor.sendToServer(new HandToolKeyPayload(HandToolKeyPayload.THROW));
        }
    }

    @SubscribeEvent
    static void tickPost(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) { FX.clear(); ANIM.clear(); for (Loop l : LOOPS.values()) l.end(); LOOPS.clear(); return; }
        long now = lv.getGameTime();
        for (Iterator<Map.Entry<Integer, Fx>> it = FX.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Fx> en = it.next();
            if (now - en.getValue().seen > 40 && en.getValue().state != 0 || now - en.getValue().seen > 200) it.remove();
        }
        for (Player pl : lv.players()) {
            ItemStack main = pl.getMainHandItem(), off = pl.getOffhandItem();
            ItemStack s = HandTech.isTool(main) ? main : HandTech.isTool(off) ? off : ItemStack.EMPTY;
            if (s.isEmpty()) { ANIM.remove(pl.getId()); loop(pl, false, false); continue; }
            Anim a = ANIM.computeIfAbsent(pl.getId(), i -> new Anim());
            a.seen = now;
            Fx f = FX.get(pl.getId());
            boolean using = pl.isUsingItem() && pl.getUseItem() == s;
            if (s.getItem() instanceof TractorBeamItem) tractorAnim(a, f, using, pl);
            else cutterAnim(a, f, using, pl, s, lv);
            loop(pl, s.getItem() instanceof TractorBeamItem, using && (s.getItem() instanceof TractorBeamItem || !HandTech.overheated(s)));
        }
        ANIM.keySet().removeIf(id -> lv.getEntity(id) == null);
        LOOPS.entrySet().removeIf(en -> { if (lv.getEntity(en.getKey()) == null) { en.getValue().end(); return true; } return false; });
    }

    private static void tractorAnim(Anim a, Fx f, boolean using, Player pl) {
        a.prongO = a.prong; a.sightO = a.sight; a.ringO = a.ring; a.capO = a.cap; a.pulseO = a.pulse; a.fanO = a.fan; a.trigO = a.trig; a.kickO = a.kick;
        int st = f == null || f.tool != HandToolFxPayload.TRACTOR ? (using ? TractorBeam.SEARCH : TractorBeam.OFF) : f.state;
        if (!using && pl == Minecraft.getInstance().player) st = TractorBeam.OFF;
        boolean on = st == TractorBeam.SEARCH || st == TractorBeam.HOLD || st == TractorBeam.PULL, held = st == TractorBeam.HOLD || st == TractorBeam.PULL;
        a.prong = approach(a.prong, on ? (held ? 1.0F : 0.5F) : 0.0F, 0.12F);
        a.sight = approach(a.sight, on ? 1.0F : 0.0F, 0.2F);
        a.ringV = approach(a.ringV, on ? (st == TractorBeam.PULL ? 45.0F : 28.0F) : 0.0F, 2.0F);
        a.ring += a.ringV;
        a.cap = approach(a.cap, held ? 1.0F : 0.0F, held ? 1.0F / 7.0F : 0.5F);
        a.pulse = approach(a.pulse, 0.0F, 0.25F);
        a.kick = approach(a.kick, 0.0F, 0.2F);
        a.fan += on ? 30.0F : 4.0F;
        a.trig = approach(a.trig, using ? 1.0F : 0.0F, 0.34F);
        a.beam = approach(a.beam, on ? 1.0F : 0.0F, 0.25F);
        a.arcing = false;
    }

    private static void cutterAnim(Anim a, Fx f, boolean using, Player pl, ItemStack s, ClientLevel lv) {
        a.shroudO = a.shroud; a.guidesO = a.guides; a.ventsO = a.vents; a.finsO = a.fins; a.valveO = a.valve; a.needleO = a.needle; a.trigO = a.trig; a.kickO = a.kick;
        int st = f == null || f.tool != HandToolFxPayload.CUTTER ? (using ? PlasmaCutter.ARC : PlasmaCutter.OFF) : f.state;
        if (!using && pl == Minecraft.getInstance().player) st = PlasmaCutter.OFF;
        boolean arc = using && st != PlasmaCutter.OFF && st != PlasmaCutter.OVERHEAT && !HandTech.overheated(s);
        float heat = Math.min(1.0F, HandTech.heat(s) / (float) Balance.PCU_HEAT_MAX);
        a.valve = approach(a.valve, arc ? 1.0F : 0.0F, 0.34F);
        a.shroud = approach(a.shroud, arc && a.valve > 0.6F ? 1.0F : 0.0F, 0.25F);
        a.guides = approach(a.guides, arc && st == PlasmaCutter.BREACH ? 1.0F : 0.0F, 0.15F);
        a.vents = approach(a.vents, HandTech.overheated(s) ? 1.0F : 0.0F, HandTech.overheated(s) ? 0.25F : 0.08F);
        a.finV = approach(a.finV, 6.0F * heat + (arc ? 14.0F : 0.0F), 1.5F);
        a.fins += a.finV;
        a.needle = approach(a.needle, arc ? 0.55F + 0.45F * heat : 0.0F, 0.1F);
        a.trig = approach(a.trig, using ? 1.0F : 0.0F, 0.34F);
        a.kick = approach(a.kick, 0.0F, 0.2F);
        a.arcing = arc && a.shroud > 0.6F;
        if (a.arcing && f != null && (st == PlasmaCutter.CUT || st == PlasmaCutter.BREACH) && lv.random.nextInt(2) == 0)
            lv.addParticle(lv.random.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.ELECTRIC_SPARK, f.at.x, f.at.y, f.at.z,
                    (lv.random.nextDouble() - 0.5) * 0.3, lv.random.nextDouble() * 0.2, (lv.random.nextDouble() - 0.5) * 0.3);
        if (HandTech.overheated(s) && lv.random.nextInt(3) == 0) {
            float yaw = pl.yBodyRot * Mth.DEG_TO_RAD;
            lv.addParticle(ParticleTypes.CLOUD, pl.getX() + Mth.cos(yaw) * 0.55, pl.getY() + 1.05, pl.getZ() + Mth.sin(yaw) * 0.55, 0.0, 0.03, 0.0);
        }
    }

    private static float approach(float v, float t, float step) { return v < t ? Math.min(t, v + step) : Math.max(t, v - step); }

    private static void loop(Player pl, boolean tractor, boolean on) {
        Loop l = LOOPS.get(pl.getId());
        if (on && (l == null || l.isStopped() || l.tractor != tractor)) {
            if (l != null) l.end();
            l = new Loop(pl, tractor);
            LOOPS.put(pl.getId(), l);
            Minecraft.getInstance().getSoundManager().play(l);
        } else if (!on && l != null) { l.end(); LOOPS.remove(pl.getId()); }
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        final Player pl;
        final boolean tractor;
        boolean ending;
        float fade;

        Loop(Player pl, boolean tractor) {
            super(tractor ? WmdSounds.TB_HUM_LOOP.get() : WmdSounds.PC_ARC_LOOP.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.pl = pl; this.tractor = tractor;
            looping = true; delay = 0; volume = 0.01F;
            x = pl.getX(); y = pl.getY() + 1.2; z = pl.getZ();
        }

        void end() { ending = true; }

        @Override
        public void tick() {
            if (pl.isRemoved()) { stop(); return; }
            fade = Math.max(0.0F, Math.min(1.0F, fade + (ending ? -0.2F : 0.25F)));
            if (ending && fade <= 0.0F) { stop(); return; }
            x = pl.getX(); y = pl.getY() + 1.2; z = pl.getZ();
            Fx f = FX.get(pl.getId());
            boolean pull = tractor && f != null && f.state == TractorBeam.PULL;
            volume = Math.max(0.01F, Balance.SND_SMALL * fade);
            pitch = tractor ? (pull ? 1.1F : 1.0F) : 1.0F + 0.1F * (f != null ? f.k : 0.0F);
        }
    }

    static Vec3 muzzle(Player pl, float pt, boolean tractor) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 look = pl.getViewVector(pt);
        float yaw = Mth.lerp(pt, pl.yBodyRotO, pl.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
        if (pl == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vec3 eye = pl.getEyePosition(pt);
            Vec3 r1 = look.cross(new Vec3(0, 1, 0));
            if (r1.lengthSqr() < 1e-4) r1 = right; else r1 = r1.normalize();
            Vec3 up1 = r1.cross(look).normalize();
            return eye.add(look.scale(tractor ? 0.95 : 0.7)).add(r1.scale(tractor ? 0.22 : 0.26)).subtract(up1.scale(tractor ? 0.2 : 0.17));
        }
        Vec3 base = pl.getPosition(pt).add(0.0, pl.isCrouching() ? 1.05 : 1.3, 0.0);
        return base.add(right.scale(0.3)).add(look.scale(tractor ? 1.15 : 0.8));
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FX.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double tt = level.getGameTime() + pt;
        long now = level.getGameTime();
        ClientBlastGeo.beginFrame();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (Map.Entry<Integer, Fx> e : FX.entrySet()) {
            if (!(level.getEntity(e.getKey()) instanceof Player pl) || pl.isInvisible()) continue;
            Fx f = e.getValue();
            Anim a = ANIM.get(pl.getId());
            if (f.tool == HandToolFxPayload.TRACTOR) {
                float flash = Math.max(0.0F, 1.0F - (float) (tt - f.throwAt) / 8.0F);
                boolean on = a != null && a.beam > 0.05F && (f.state == TractorBeam.SEARCH || f.state == TractorBeam.HOLD || f.state == TractorBeam.PULL);
                if (!on && flash <= 0.0F) continue;
                Vec3 from = muzzle(pl, pt, true);
                Vec3 to = f.at;
                Entity t = f.target >= 0 ? level.getEntity(f.target) : null;
                if (t != null) to = t.getPosition(pt).add(0.0, t.getBbHeight() * 0.5, 0.0);
                if (f.state == TractorBeam.SEARCH) to = pl.getEyePosition(pt).add(pl.getViewVector(pt).scale(Balance.TRB_RANGE));
                tractorBeam(bb, cam, left, up, from, to, f.state, on ? a.beam : 0.0F, flash, tt, pl.getId(), t);
                if (on && f.state != TractorBeam.SEARCH) VeilBridge.glow(0x54524200L ^ pl.getId(), to.x, to.y, to.z, 0.35F, 0.75F, 1.0F, 4.0F, 1.1F, now);
                any = true;
            } else if (f.tool == HandToolFxPayload.CUTTER) {
                if (a == null || !a.arcing) continue;
                Vec3 from = muzzle(pl, pt, false);
                cutterArc(bb, cam, left, up, from, f.at, f.state, f.k, a.guides, tt, pl);
                VeilBridge.glow(0x50435500L ^ pl.getId(), f.at.x, f.at.y, f.at.z, 1.0F, 0.7F, 0.4F, 3.0F, 1.3F, now);
                any = true;
            }
        }
        ClientBlastGeo.draw(event, bb, any, true);
    }

    private static void tractorBeam(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Vec3 a, Vec3 b, int state, float on, float flash, double tt, int seed, Entity t) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.3) return;
        Vec3 dir = d.scale(1.0 / len);
        float fl = 0.88F + 0.12F * (float) Math.sin(tt * 2.3 + seed);
        if (state == TractorBeam.SEARCH) {
            ClientBlastGeo.streak(bb, cam, a.x, a.y, a.z, b.x, b.y, b.z, 0.03, 0.4F, 0.8F, 1.0F, 0.35F * on * fl, 0.0F);
            return;
        }
        int n = 10;
        for (int i = 0; i < n; i++) {
            double t0 = i / (double) n, t1 = (i + 1) / (double) n;
            Vec3 p0 = a.add(d.scale(t0)), p1 = a.add(d.scale(t1));
            ClientBlastGeo.streak(bb, cam, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, 0.08 + 0.35 * t1, 0.25F, 0.6F, 1.0F, 0.22F * on * fl, 0.22F * on * fl);
        }
        ClientBlastGeo.streak(bb, cam, a.x, a.y, a.z, b.x, b.y, b.z, 0.035, 0.75F, 0.95F, 1.0F, 0.9F * on, 0.6F * on);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up2 = dir.cross(side).normalize();
        double speed = state == TractorBeam.PULL ? 0.16 : 0.07;
        int rings = Math.max(3, (int) (len / 2.5));
        for (int r = 0; r < rings; r++) {
            double tt0 = 1.0 - ((tt * speed + r / (double) rings) % 1.0);
            Vec3 c = a.add(d.scale(tt0));
            ring(bb, cam, c, side, up2, 0.1 + 0.3 * tt0, 0.02, 0.45F, 0.85F, 1.0F, 0.75F * on * (float) Math.sin(Math.PI * tt0));
        }
        double cr = t != null ? Math.max(0.5, Math.max(t.getBbWidth(), t.getBbHeight()) * 0.7) : 0.9;
        for (int k = 0; k < 3; k++) {
            double ang = tt * 0.12 + k * Math.PI / 3.0;
            Vec3 ax1 = side.scale(Math.cos(ang)).add(up2.scale(Math.sin(ang))), ax2 = dir.cross(ax1).normalize();
            ring(bb, cam, b, ax1, ax2.scale(1.0), cr, 0.025, 0.4F, 0.8F, 1.0F, 0.55F * on);
        }
        ClientBlastGeo.disc(bb, cam, left, up, b.x, b.y, b.z, cr * 0.9, 0.3F, 0.65F, 1.0F, 0.18F * on);
        if (flash > 0.0F) {
            ClientBlastGeo.streak(bb, cam, a.x, a.y, a.z, b.x, b.y, b.z, 0.25 * flash, 0.9F, 0.97F, 1.0F, 0.9F * flash, 0.5F * flash);
            Vec3 c = a.add(d.scale(1.0 - flash));
            ring(bb, cam, c, side, up2, 0.2 + 0.6 * (1.0 - flash), 0.05, 0.85F, 0.95F, 1.0F, flash);
        }
    }

    private static void ring(BufferBuilder bb, Vec3 cam, Vec3 c, Vec3 u, Vec3 v, double r, double w, float cr, float cg, float cb, float al) {
        if (al <= 0.01F) return;
        int seg = 16;
        Vec3 prev = null;
        for (int i = 0; i <= seg; i++) {
            double ang = 2.0 * Math.PI * i / seg;
            Vec3 q = c.add(u.scale(Math.cos(ang) * r)).add(v.scale(Math.sin(ang) * r));
            if (prev != null) ClientBlastGeo.streak(bb, cam, prev.x, prev.y, prev.z, q.x, q.y, q.z, w, cr, cg, cb, al, al);
            prev = q;
        }
    }

    private static void cutterArc(BufferBuilder bb, Vec3 cam, Vector3f left, Vector3f up, Vec3 a, Vec3 b, int state, float k, float guides, double tt, Player pl) {
        long seed = (long) (tt * 1.0) * 31L + pl.getId();
        java.util.Random rnd = new java.util.Random(seed);
        jagged(bb, cam, a, b, rnd, 0.06, 0.04, 0.95F, 0.98F, 1.0F, 1.0F);
        jagged(bb, cam, a, b, rnd, 0.1, 0.09, 0.4F, 0.75F, 1.0F, 0.45F);
        if (guides > 0.5F && state == PlasmaCutter.BREACH) {
            Vec3 look = pl.getViewVector(1.0F), r = look.cross(new Vec3(0, 1, 0));
            r = r.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : r.normalize();
            for (int s = -1; s <= 1; s += 2) jagged(bb, cam, a.add(r.scale(0.07 * s)), b, rnd, 0.08, 0.025, 0.55F, 0.85F, 1.0F, 0.8F);
        }
        if (state == PlasmaCutter.CUT || state == PlasmaCutter.BREACH) {
            ClientBlastGeo.disc(bb, cam, left, up, b.x, b.y, b.z, 0.12 + 0.3 * k, 1.0F, 0.45F, 0.1F, 0.55F);
            ClientBlastGeo.disc(bb, cam, left, up, b.x, b.y, b.z, 0.08, 1.0F, 0.97F, 0.9F, 1.0F);
        } else ClientBlastGeo.disc(bb, cam, left, up, b.x, b.y, b.z, 0.06, 0.7F, 0.9F, 1.0F, 0.6F);
    }

    private static void jagged(BufferBuilder bb, Vec3 cam, Vec3 a, Vec3 b, java.util.Random rnd, double amp, double w, float r, float g, float bl, float al) {
        int n = 6;
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n, s = Math.sin(Math.PI * t) * amp;
            Vec3 q = a.add(b.subtract(a).scale(t)).add(i == n ? Vec3.ZERO : new Vec3((rnd.nextDouble() - 0.5) * s, (rnd.nextDouble() - 0.5) * s, (rnd.nextDouble() - 0.5) * s));
            ClientBlastGeo.streak(bb, cam, prev.x, prev.y, prev.z, q.x, q.y, q.z, w, r, g, bl, al, al);
            prev = q;
        }
    }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(HUD, ClientHandTools::hud); }

    private static void hud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui) return;
        ItemStack s = p.getMainHandItem();
        if (!HandTech.isTool(s)) return;
        boolean tractor = s.getItem() instanceof TractorBeamItem;
        Font f = mc.font;
        int x = 6, y = g.guiHeight() / 2 + 66, w = 104, h = 40;
        g.fill(x, y, x + w, y + h, 0xCC101820);
        g.fill(x, y, x + w, y + 1, 0xFF3D8BFF);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF22303E);
        g.drawString(f, Component.translatable(tractor ? "item.wmd.tractor_beam" : "item.wmd.plasma_cutter"), x + 5, y + 4, 0xFFBFD4EA, false);
        float k = HandTech.fe(s) / (float) HandTech.cap(s);
        String pc = Math.round(k * 100.0F) + " %";
        g.drawString(f, pc, x + w - 5 - f.width(pc), y + 4, 0xFF4AD7FF, false);
        int bx = x + 5, by = y + 16, bw = w - 10;
        g.fill(bx, by, bx + bw, by + 4, 0xFF0A1016);
        g.fill(bx + 1, by + 1, bx + 1 + Math.round((bw - 2) * k), by + 3, 0xFF4AD7FF);
        String st;
        int sc;
        Fx fx = FX.get(p.getId());
        if (tractor) {
            int state = fx == null || !p.isUsingItem() ? TractorBeam.OFF : fx.state;
            if (p.getCooldowns().isOnCooldown(s.getItem())) { st = "hud.wmd.tractor_beam.cooldown"; sc = 0xFFFF9A3C; }
            else if (state == TractorBeam.PULL) { st = "hud.wmd.tractor_beam.pull"; sc = 0xFF7FE0FF; }
            else if (state == TractorBeam.HOLD) { st = "hud.wmd.tractor_beam.hold"; sc = 0xFF7FE0FF; }
            else if (state == TractorBeam.SEARCH) { st = "hud.wmd.tractor_beam.search"; sc = 0xFFBFD4EA; }
            else { st = "hud.wmd.tractor_beam.ready"; sc = 0xFF7FFFB0; }
        } else {
            float hk = Math.min(1.0F, HandTech.heat(s) / (float) Balance.PCU_HEAT_MAX);
            int hc = HandTech.overheated(s) ? ((p.tickCount / 5) % 2 == 0 ? 0xFFFF4040 : 0xFF902020) : hk > 0.7F ? 0xFFFF9A3C : 0xFF3D8BFF;
            g.fill(bx, by + 6, bx + bw, by + 10, 0xFF0A1016);
            g.fill(bx + 1, by + 7, bx + 1 + Math.round((bw - 2) * hk), by + 9, hc);
            int state = fx == null || !p.isUsingItem() ? PlasmaCutter.OFF : fx.state;
            if (HandTech.overheated(s)) { st = "hud.wmd.plasma_cutter.overheat"; sc = hc; }
            else if (state == PlasmaCutter.BREACH) { st = "hud.wmd.plasma_cutter.breach"; sc = 0xFFFF9A3C; }
            else if (state == PlasmaCutter.CUT) { st = "hud.wmd.plasma_cutter.cut"; sc = 0xFFFF9A3C; }
            else { st = "hud.wmd.plasma_cutter.ready"; sc = 0xFF7FFFB0; }
        }
        g.drawString(f, Component.translatable(st), x + 5, y + 28, sc, false);
    }
}
