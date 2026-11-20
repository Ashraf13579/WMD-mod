package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ExoMods {

    private ExoMods() {}

    public static final int THERMAL_ON = 10, THERMAL_OFF = 11, HOVER_ON = 12, HOVER_OFF = 13, MAG_ON = 14, MAG_OFF = 15, RF_ON = 16, RF_OFF = 17,
            CANNON_CHARGE = 18, CANNON_CANCEL = 19, MSL_FIRE = 20, BARRIER = 21, CLOAK_ON = 22, CLOAK_OFF = 23, FIRE_CANNON = 30, FIRE_MISSILES = 31,
            WINGS_ARM = 32, WINGS_DISARM = 33, THRUST_ON = 34, THRUST_OFF = 35;

    private static final Set<UUID> THERMAL = Statics.server(new HashSet<>()), CLOAK = Statics.server(new HashSet<>()), HOVER = Statics.server(new HashSet<>()),
            THRUST = Statics.server(new HashSet<>()), MAG = Statics.server(new HashSet<>()), ARMED = Statics.server(new HashSet<>()),
            RF_LINK = Statics.server(new HashSet<>());
    private static final Map<UUID, Long> CLOAK_REARM = Statics.server(new HashMap<>()), CANNON_COOL = Statics.server(new HashMap<>()),
            MSL_COOL = Statics.server(new HashMap<>()), CANNON_START = Statics.server(new HashMap<>());

    private static final Set<UUID> ARMED_CLIENT = Statics.client(new HashSet<>()), HOVER_CLIENT = Statics.client(new HashSet<>());
    private static final List<Rocket> ROCKETS = Statics.server(new ArrayList<>());
    private static final List<Bolt> BOLTS = Statics.server(new ArrayList<>());
    private static long nextRocket;

    private static final ResourceLocation SWIM = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "exo_rebreather_swim");

    static final class Rocket {
        final long id;
        final UUID owner;
        final Level level;
        final int target;
        final Vec3 aim;
        Vec3 pos, vel;
        int age, delay;

        Rocket(long id, UUID owner, Level level, int target, Vec3 aim, Vec3 pos, Vec3 vel, int delay) {
            this.id = id; this.owner = owner; this.level = level; this.target = target; this.aim = aim; this.pos = pos; this.vel = vel; this.delay = delay;
        }
    }

    static boolean wingsFly(LivingEntity e) {
        if (!ExoModule.WINGS.live(e)) return false;
        Set<UUID> armed = e.level().isClientSide ? ARMED_CLIENT : ARMED, hover = e.level().isClientSide ? HOVER_CLIENT : HOVER;
        return armed.contains(e.getUUID()) && !hover.contains(e.getUUID());
    }

    static void clientWings(UUID id, boolean armed, boolean hover) {
        if (armed) ARMED_CLIENT.add(id); else ARMED_CLIENT.remove(id);
        if (hover) HOVER_CLIENT.add(id); else HOVER_CLIENT.remove(id);
    }

    static void flightTick(LivingEntity e) {
        if (!(e instanceof ServerPlayer p) || ExoSuit.free(p)) return;
        pay(p, THRUST.contains(p.getUUID()) ? Balance.EXO_WING_FE : Balance.EXO_WING_GLIDE_FE);
    }

    private static void pay(ServerPlayer p, int fe) {
        ItemStack c = ExoSuitItem.chest(p);
        int have = ExoSuitItem.fe(c);
        if (have > 0) ExoSuitItem.setFe(c, have - Math.min(have, fe));
    }

    static void action(ServerPlayer p, int a, int arg) {
        if (p.isSpectator() || !p.isAlive()) return;
        UUID u = p.getUUID();
        long now = p.serverLevel().getGameTime();
        switch (a) {
            case THERMAL_ON -> { if (ExoModule.THERMAL.live(p) && THERMAL.add(u)) fx(p, THERMAL_ON); }
            case THERMAL_OFF -> { if (THERMAL.remove(u)) fx(p, THERMAL_OFF); }
            case CLOAK_ON -> {
                if (!ExoModule.CLOAK.live(p)) return;
                if (CLOAK_REARM.getOrDefault(u, Long.MIN_VALUE) > now) {
                    p.displayClientMessage(Component.translatable("message.wmd.exo_mod.cloak_rearm").withStyle(ChatFormatting.YELLOW), true);
                    return;
                }
                if (CLOAK.add(u)) fx(p, CLOAK_ON);
            }
            case CLOAK_OFF -> dropCloak(p, now, false);
            case HOVER_ON -> { if (ExoModule.WINGS.live(p) && HOVER.add(u)) { p.stopFallFlying(); fx(p, HOVER_ON); } }
            case HOVER_OFF -> { if (HOVER.remove(u)) fx(p, HOVER_OFF); }
            case MAG_ON -> { if (ExoModule.MAGGRIP.live(p) && MAG.add(u)) { p.resetFallDistance(); fx(p, MAG_ON); } }
            case MAG_OFF -> { if (MAG.remove(u)) fx(p, MAG_OFF); }
            case WINGS_ARM -> { if (ExoModule.WINGS.live(p)) ARMED.add(u); }
            case WINGS_DISARM -> ARMED.remove(u);
            case THRUST_ON -> { if (ExoModule.WINGS.live(p)) THRUST.add(u); }
            case THRUST_OFF -> THRUST.remove(u);
            case CANNON_CHARGE -> { if (ExoModule.CANNON.live(p) && CANNON_START.putIfAbsent(u, now) == null) fx(p, CANNON_CHARGE); }
            case CANNON_CANCEL -> { if (CANNON_START.remove(u) != null) fx(p, CANNON_CANCEL); }
            case FIRE_CANNON -> {
                Long st = CANNON_START.remove(u);
                int elapsed = st == null ? 0 : (int) Math.min(Balance.EXO_CANNON_CHARGE, Math.max(0L, now - st) + 1L);
                fireCannon(p, Mth.clamp(Math.min(arg, elapsed), 0, Balance.EXO_CANNON_CHARGE), now);
            }
            case FIRE_MISSILES -> fireMissiles(p, arg, now);
            default -> {}
        }
    }

    private static void dropCloak(ServerPlayer p, long now, boolean forced) {
        if (!CLOAK.remove(p.getUUID())) return;
        CLOAK_REARM.put(p.getUUID(), now + Balance.EXO_CLOAK_REARM);
        fx(p, CLOAK_OFF);
        if (forced) p.displayClientMessage(Component.translatable("message.wmd.exo_mod.cloak_drop").withStyle(ChatFormatting.YELLOW), true);
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        UUID u = p.getUUID();
        long now = p.serverLevel().getGameTime();
        boolean free = ExoSuit.free(p);

        if (!ExoModule.THERMAL.live(p) && THERMAL.remove(u)) fx(p, THERMAL_OFF);
        if (!ExoModule.CLOAK.live(p)) dropCloak(p, now, false);
        if (!ExoModule.WINGS.live(p)) { ARMED.remove(u); THRUST.remove(u); if (HOVER.remove(u)) fx(p, HOVER_OFF); }
        if (!ExoModule.MAGGRIP.live(p) && MAG.remove(u)) fx(p, MAG_OFF);
        if (!ExoModule.CANNON.live(p) && CANNON_START.remove(u) != null) fx(p, CANNON_CANCEL);
        if (p.onGround()) { ARMED.remove(u); THRUST.remove(u); }

        if (!free) {
            if (THERMAL.contains(u)) pay(p, Balance.EXO_THERMAL_FE);
            if (CLOAK.contains(u)) pay(p, Balance.EXO_CLOAK_FE);
            if (HOVER.contains(u)) pay(p, Balance.EXO_WING_HOVER_FE);
            if (MAG.contains(u)) pay(p, Balance.EXO_MAG_FE);
        }
        if (CLOAK.contains(u)) Cloak.personal(p, owned(p), now);
        if (HOVER.contains(u)) p.resetFallDistance();
        if (MAG.contains(u)) p.resetFallDistance();

        boolean breathe = ExoModule.REBREATHER.live(p);
        if (breathe && p.isUnderWater()) {
            p.setAirSupply(p.getMaxAirSupply());
            if (!free) pay(p, Balance.EXO_REBREATH_FE);
        }
        mod(p, NeoForgeMod.SWIM_SPEED, SWIM, breathe, Balance.EXO_SWIM);

        if (now % 5 == 0) rf(p, now);
    }

    private static void rf(ServerPlayer p, long now) {
        ItemStack c = ExoSuitItem.chest(p);
        int ch = c.isEmpty() ? 0 : c.getOrDefault(ExoSuitBlocks.EXO_RF.get(), 0);
        boolean was = RF_LINK.contains(p.getUUID());
        int got = 0;
        if (ch > 0 && ExoModule.RF.fitted(p) && ExoSuitItem.fullSet(p) && !ExoSuit.locked(p, now)) {
            int want = Math.min(Balance.EXO_RF_FE * 5, Balance.EXO_FE_CAP - ExoSuitItem.fe(c));
            if (want > 0) got = RfLinkBlockEntity.pullBuiltIn(p.serverLevel(), owned(p), p.position().add(0.0, 1.6, 0.0), ch, want, now);
            if (got > 0) ExoSuitItem.setFe(c, ExoSuitItem.fe(c) + got);
        }
        boolean link = got > 0;
        if (link != was) {
            if (link) RF_LINK.add(p.getUUID()); else RF_LINK.remove(p.getUUID());
            fx(p, link ? RF_ON : RF_OFF);
        }
    }

    private static void mod(ServerPlayer p, Holder<Attribute> a, ResourceLocation id, boolean on, double v) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) return;
        boolean has = inst.hasModifier(id);
        if (on && !has) inst.addTransientModifier(new AttributeModifier(id, v, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else if (!on && has) inst.removeModifier(id);
    }

    static Owned owned(ServerPlayer p) {
        UUID id = p.getUUID();
        String name = p.getGameProfile().getName();
        SecurityData sec = new SecurityData();
        return new Owned() {
            @Override public UUID getOwner() { return id; }
            @Override public String getOwnerName() { return name; }
            @Override public void claim(Player player) {}
            @Override public SecurityData security() { return sec; }
        };
    }

    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) dropCloak(p, p.serverLevel().getGameTime(), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        long now = p.serverLevel().getGameTime();
        DamageSource src = event.getSource();
        if (ExoModule.BARRIER.live(p) && ExoSuitItem.charge(p) >= Balance.EXO_BARRIER_MIN) {
            boolean shot = projectile(src), blast = src.is(DamageTypeTags.IS_EXPLOSION);
            if (shot || blast) {
                float amt = event.getAmount();
                float stop = shot ? amt : amt * (float) (1.0 - Balance.EXO_BARRIER_BLAST);
                ItemStack c = ExoSuitItem.chest(p);
                int fe = ExoSuitItem.fe(c);
                int cost = ExoSuit.free(p) ? 0 : (int) Math.ceil(stop * Balance.EXO_BARRIER_FE);
                if (cost > fe) stop = fe / (float) Balance.EXO_BARRIER_FE;
                if (stop > 0.0F) {
                    if (!ExoSuit.free(p)) ExoSuitItem.setFe(c, fe - Math.min(fe, cost));
                    fx(p, BARRIER);
                    if (stop >= amt) { event.setCanceled(true); return; }
                    event.setAmount(amt - stop);
                }
            }
        }
        if (event.getAmount() > 0.0F) dropCloak(p, now, true);
    }

    static boolean projectile(DamageSource src) {
        if (src.getDirectEntity() instanceof Projectile || src.is(DamageTypeTags.IS_PROJECTILE)) return true;
        return src.typeHolder().unwrapKey().map(k -> "tacz".equals(k.location().getNamespace())).orElse(false)
                || (src.getDirectEntity() != null && src.getDirectEntity().getClass().getName().startsWith("com.tacz."));
    }

    private static void fireCannon(ServerPlayer p, int ticks, long now) {
        if (!ExoModule.CANNON.live(p)) { fx(p, CANNON_CANCEL); return; }
        if (CANNON_COOL.getOrDefault(p.getUUID(), Long.MIN_VALUE) > now) { fx(p, CANNON_CANCEL); return; }
        float k = ticks / (float) Balance.EXO_CANNON_CHARGE;
        if (k < Balance.EXO_CANNON_MIN) { fx(p, CANNON_CANCEL); return; }
        ItemStack c = ExoSuitItem.chest(p);
        int cost = (int) (Balance.EXO_CANNON_FE * k);
        if (!ExoSuit.free(p)) {
            if (ExoSuitItem.fe(c) < cost) {
                p.displayClientMessage(Component.translatable("message.wmd.exo_mod.no_charge").withStyle(ChatFormatting.RED), true);
                fx(p, CANNON_CANCEL);
                return;
            }
            ExoSuitItem.setFe(c, ExoSuitItem.fe(c) - cost);
        }
        CANNON_COOL.put(p.getUUID(), now + Balance.EXO_CANNON_COOL);
        dropCloak(p, now, true);

        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle(), end = eye.add(look.scale(Balance.EXO_CANNON_RANGE));
        BlockHitResult bh = SableBridge.clipWorld(sl, new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p), null);
        Vec3 aim = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        Vec3 from = muzzle(p);
        Vec3 d = aim.subtract(from);
        double dist = d.length();
        if (dist < 0.5) { d = look; dist = 0.5; }
        Bolt b = new Bolt(++nextRocket, p.getUUID(), p.getId(), sl, from, d.normalize(), k, Math.min(Balance.EXO_CANNON_RANGE + 2.0, dist + 0.5));
        BOLTS.add(b);
        send(sl, new ExoBeamPayload(b.id, p.getId(), from, b.dir, k, (float) b.max, false));
        sl.playSound(null, from.x, from.y, from.z, WmdSounds.EMP_ZAP.get(), SoundSource.PLAYERS, Balance.SND_RELEASE, 1.35F);
        sl.playSound(null, from.x, from.y, from.z, WmdSounds.FUSION_DISCHARGE.get(), SoundSource.PLAYERS, Balance.SND_RELEASE, 1.6F);
    }

    static Vec3 muzzle(Player pl) {
        float yaw = pl.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
        return pl.position().add(0.0, pl.isCrouching() ? 1.0 : 1.3, 0.0).add(right.scale(0.5)).add(pl.getLookAngle().scale(0.95));
    }

    static final class Bolt {
        final long id;
        final UUID owner;
        final int shooter;
        final Level level;
        final Vec3 start, dir;
        final float k;
        final double max;
        double flown;

        Bolt(long id, UUID owner, int shooter, Level level, Vec3 start, Vec3 dir, float k, double max) {
            this.id = id; this.owner = owner; this.shooter = shooter; this.level = level; this.start = start; this.dir = dir; this.k = k; this.max = max;
        }
    }

    private static void send(ServerLevel sl, ExoBeamPayload pay) {
        Vec3 at = new Vec3(pay.x(), pay.y(), pay.z());
        double r2 = Balance.EXO_FX_RANGE * Balance.EXO_FX_RANGE * 4;
        for (ServerPlayer o : sl.players()) if (o.distanceToSqr(at) <= r2) PacketDistributor.sendToPlayer(o, pay);
    }

    private static void tickBolts(ServerLevel sl) {
        for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            if (b.level != sl) continue;
            Vec3 a = b.start.add(b.dir.scale(b.flown));
            double step = Math.min(Balance.EXO_CANNON_SPEED, b.max - b.flown);
            Vec3 z = b.start.add(b.dir.scale(b.flown + step));
            if (!sl.isLoaded(BlockPos.containing(z))) { it.remove(); send(sl, new ExoBeamPayload(b.id, b.shooter, a, b.dir, b.k, (float) b.flown, true)); continue; }
            BlockHitResult bh = SableBridge.clipWorld(sl, new ClipContext(a, z, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) null), null);
            Vec3 hit = bh.getType() == HitResult.Type.MISS ? null : bh.getLocation();
            Vec3 segEnd = hit != null ? hit : z;
            Entity owner = sl.getPlayerByUUID(b.owner);
            EntityHitResult eh = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(sl, owner, a, segEnd, new AABB(a, segEnd).inflate(0.6),
                    e -> e.isAlive() && e.isPickable() && !e.isSpectator() && e != owner);
            Entity first = eh == null ? null : eh.getEntity();
            if (first != null) hit = eh.getLocation();
            b.flown += step;
            if (hit == null && b.flown < b.max - 1e-3) continue;
            it.remove();
            Vec3 at = hit != null ? hit : z;
            ServerPlayer sp = owner instanceof ServerPlayer x ? x : null;
            if (first != null) first.hurt(sp != null ? sl.damageSources().indirectMagic(sp, sp) : sl.damageSources().magic(), (float) (Balance.EXO_CANNON_DMG * b.k));
            if (sp != null) blast(sl, sp, at, b.k, first);
            send(sl, new ExoBeamPayload(b.id, b.shooter, b.start, b.dir, b.k, (float) at.distanceTo(b.start), true));
            sl.playSound(null, at.x, at.y, at.z, WmdSounds.FUSION_DISCHARGE.get(), SoundSource.PLAYERS, Balance.SND_MECH, 0.9F);
            sl.playSound(null, at.x, at.y, at.z, WmdSounds.EMP_ZAP.get(), SoundSource.PLAYERS, Balance.SND_MECH, 1.3F);
        }
    }

    private static void blast(ServerLevel sl, ServerPlayer p, Vec3 at, float k, Entity skip) {
        double r = Balance.EXO_CANNON_BLAST_R * Math.max(0.5, k);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r), e -> e != skip && e != p && e.isAlive()))
            e.hurt(sl.damageSources().explosion(p, p), (float) (Balance.EXO_CANNON_BLAST_DMG * k * (1.0 - e.position().distanceTo(at) / (r + 1.0))));
        int ri = (int) Math.ceil(r * 0.7);
        BlockPos c = BlockPos.containing(at);
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-ri, -ri, -ri), c.offset(ri, ri, ri))) {
            if (q.distToCenterSqr(at) > r * r * 0.5) continue;
            BlockState s = sl.getBlockState(q);
            if (s.isAir() || sl.getBlockEntity(q) != null) continue;
            float h = s.getDestroySpeed(sl, q);
            if (h < 0 || h > Balance.EXO_CANNON_WEAK || !BlockRules.blastRemovable(sl, q, s)) continue;
            sl.destroyBlock(q, false, p);
        }
    }

    private static void fireMissiles(ServerPlayer p, int target, long now) {
        if (!ExoModule.MISSILES.live(p)) return;
        if (MSL_COOL.getOrDefault(p.getUUID(), Long.MIN_VALUE) > now) return;
        ItemStack c = ExoSuitItem.chest(p);
        int ammo = c.getOrDefault(ExoSuitBlocks.EXO_AMMO.get(), 0);
        if (ammo <= 0 && !p.isCreative()) {
            p.displayClientMessage(Component.translatable("message.wmd.exo_mod.no_ammo").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!ExoSuit.free(p)) {
            if (ExoSuitItem.fe(c) < Balance.EXO_MSL_FE) { p.displayClientMessage(Component.translatable("message.wmd.exo_mod.no_charge").withStyle(ChatFormatting.RED), true); return; }
            ExoSuitItem.setFe(c, ExoSuitItem.fe(c) - Balance.EXO_MSL_FE);
        }
        if (!p.isCreative()) c.set(ExoSuitBlocks.EXO_AMMO.get(), 0);
        MSL_COOL.put(p.getUUID(), now + Balance.EXO_MSL_COOL);
        dropCloak(p, now, true);
        ServerLevel sl = p.serverLevel();
        Entity t = target >= 0 ? sl.getEntity(target) : null;
        if (t != null && (t.distanceTo(p) > Balance.EXO_MSL_RANGE * 1.5 || t == p || t.isSpectator()
                || (t instanceof Player tp && Cloak.playerHidden(sl, tp) && !Cloak.revealedTo(sl, tp.getUUID(), p.getUUID())))) t = null;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        BlockHitResult bh = SableBridge.clipWorld(sl, new ClipContext(eye, eye.add(look.scale(Balance.EXO_MSL_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p), null);
        Vec3 aim = bh.getLocation();
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
        Vec3 from = p.position().add(0.0, 1.75, 0.0).add(side.scale(0.4));
        for (int i = 0; i < Balance.EXO_MSL_SALVO; i++) {
            Vec3 up = new Vec3(0.0, 0.35 + 0.05 * i, 0.0);
            Vec3 v = look.scale(0.6).add(up).add(side.scale((i % 3 - 1) * 0.12));
            ROCKETS.add(new Rocket(++nextRocket, p.getUUID(), sl, t == null ? -1 : t.getId(), aim, from, v, i * Balance.EXO_MSL_GAP));
        }
        fx(p, MSL_FIRE);
        sl.playSound(null, from.x, from.y, from.z, WmdSounds.JET_AB_LIGHT.get(), SoundSource.PLAYERS, Balance.SND_MECH, 1.6F);
    }

    static boolean load(ServerPlayer p, ItemStack pack) {
        if (!ExoModule.MISSILES.fitted(p)) return false;
        ItemStack c = ExoSuitItem.chest(p);
        if (c.getOrDefault(ExoSuitBlocks.EXO_AMMO.get(), 0) >= Balance.EXO_MSL_SALVO) return false;
        c.set(ExoSuitBlocks.EXO_AMMO.get(), Balance.EXO_MSL_SALVO);
        if (!p.isCreative()) pack.shrink(1);
        p.serverLevel().playSound(null, p.blockPosition(), WmdSounds.CAM_SERVO.get(), SoundSource.PLAYERS, Balance.SND_SMALL, 1.2F);
        return true;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!BOLTS.isEmpty()) tickBolts(sl);
        if (ROCKETS.isEmpty()) return;
        List<float[]> shown = new ArrayList<>();
        for (Iterator<Rocket> it = ROCKETS.iterator(); it.hasNext(); ) {
            Rocket r = it.next();
            if (r.level != sl) continue;
            if (r.delay > 0) { r.delay--; continue; }
            r.age++;
            Entity t = r.target >= 0 ? sl.getEntity(r.target) : null;
            if (t != null && (t.isSpectator() || (t instanceof Player tp && Cloak.playerHidden(sl, tp) && !Cloak.revealedTo(sl, tp.getUUID(), r.owner)))) t = null;
            Vec3 goal = t != null && t.isAlive() ? t.getBoundingBox().getCenter() : r.aim;
            Vec3 want = goal.subtract(r.pos).normalize().scale(Balance.EXO_MSL_SPEED);
            r.vel = r.age < 4 ? r.vel.add(0.0, -0.02, 0.0) : r.vel.add(want.subtract(r.vel).scale(Balance.EXO_MSL_TURN));
            Vec3 next = r.pos.add(r.vel);
            boolean end = r.age > Balance.EXO_MSL_LIFE || !sl.isLoaded(BlockPos.containing(next));
            Vec3 at = next;
            BlockHitResult bh = SableBridge.clipWorld(sl, new ClipContext(r.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) null), null);
            if (bh.getType() != HitResult.Type.MISS) { at = bh.getLocation(); end = true; }
            Entity owner = sl.getPlayerByUUID(r.owner);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(r.pos, at).inflate(0.5), e -> e.isAlive() && e != owner)) {
                if (e.getBoundingBox().inflate(0.3).clip(r.pos, at).isPresent()) { at = e.getBoundingBox().getCenter(); end = true; break; }
            }
            r.pos = at;
            if (end) {
                it.remove();
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.5), e -> e.isAlive() && e != owner))
                    e.hurt(owner instanceof Player op ? sl.damageSources().playerAttack(op) : sl.damageSources().magic(), (float) Balance.EXO_MSL_DMG);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                sl.playSound(null, at.x, at.y, at.z, WmdSounds.EMP_ZAP.get(), SoundSource.PLAYERS, Balance.SND_MECH, 1.8F);
                shown.add(new float[] { (float) r.id, (float) at.x, (float) at.y, (float) at.z, 1.0F });
            } else shown.add(new float[] { (float) r.id, (float) at.x, (float) at.y, (float) at.z, 0.0F });
        }
        if (shown.isEmpty()) return;
        ExoRocketsPayload pay = ExoRocketsPayload.of(shown);
        for (ServerPlayer o : sl.players()) {
            boolean near = false;
            for (float[] f : shown) if (o.distanceToSqr(f[1], f[2], f[3]) <= Balance.EXO_FX_RANGE * Balance.EXO_FX_RANGE * 4) { near = true; break; }
            if (near) PacketDistributor.sendToPlayer(o, pay);
        }
    }

    static void fx(ServerPlayer p, int a) { ExoSuit.fx(p, a); }

    @SubscribeEvent
    static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID u = event.getEntity().getUUID();
        THERMAL.remove(u); CLOAK.remove(u); HOVER.remove(u); THRUST.remove(u); MAG.remove(u); ARMED.remove(u); RF_LINK.remove(u);
        CANNON_START.remove(u);
    }

    @SubscribeEvent
    static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        Level lv = event.getLevel() instanceof Level l ? l : null;
        if (lv == null) return;
        ROCKETS.removeIf(r -> r.level == lv);
        BOLTS.removeIf(b -> b.level == lv);
    }
}
