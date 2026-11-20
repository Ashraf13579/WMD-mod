package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

final class WardenMeltdown {

    private WardenMeltdown() {}

    public static final int OFF = 0;

    static BlockPos key(WardenEntity w) { return new BlockPos(w.getId(), -30_000, 0); }

    static void begin(WardenEntity w, ServerLevel sl, WardenState d) {
        float scale = w.maxHp / Balance.WARDEN_HP;
        w.shieldMax = Balance.WARDEN_SHIELD_HP * scale;
        w.enrageLeft = Balance.WARDEN_ENRAGE_TICKS;
        w.railWait = 200;
        w.nodesSeen = -1;
        int s = Balance.WARDEN_ENRAGE_TICKS / 20;
        for (ServerPlayer p : w.barPlayers()) p.displayClientMessage(Component.translatable("message.wmd.warden.meltdown_rule", s / 60, String.format("%02d", s % 60)), false);
    }

    static void raise(WardenEntity w, ServerLevel sl) {

        int nodes = liveNodes(sl, WardenState.get(sl.getServer()));
        w.nodesSeen = nodes;
        if (nodes == 0) { w.shieldHp = 0.0F; w.setShield(ForcefieldBlockEntity.DOWN); w.setShieldPct(0); return; }
        w.shieldHp = cap(w, nodes);
        w.setShield(ForcefieldBlockEntity.UP);
        w.setShieldPct(Math.round(100.0F * w.shieldHp / w.shieldMax));
        WardenFight.sound(sl, w.getX(), w.getY() + 8.0, w.getZ(), WmdSounds.FF_BOOT.get(), Balance.SND_HEAVY, 0.8F);
    }

    static int liveNodes(ServerLevel sl, WardenState d) {
        int n = 0;
        for (int[] c : WardenBunker.NODES) {
            BlockPos p = new BlockPos(d.ox + c[0], d.oy + c[1], d.oz + c[2]);
            if (sl.isLoaded(p) && sl.getBlockState(p).is(Wmd.WARDEN_NODE.get())) n++;
        }
        return n;
    }

    static boolean underDome(WardenEntity w, net.minecraft.world.entity.Entity attacker) {
        if (attacker == null || attacker == w) return false;
        double r = Balance.WARDEN_SHIELD_R - 1.0;
        return attacker.position().distanceToSqr(w.position().add(0.0, 4.0, 0.0)) < r * r;
    }

    static float cap(WardenEntity w, int nodes) { return w.shieldMax * Math.min(1.0F, nodes / (float) WardenBunker.NODES.length); }

    static boolean absorb(WardenEntity w, ServerLevel sl, Vec3 from, float dmg) {
        int st = w.shield();
        if (st != ForcefieldBlockEntity.UP && st != ForcefieldBlockEntity.CRACK) return false;
        w.shieldHp = Math.max(0.0F, w.shieldHp - dmg);
        Vec3 c = w.position();
        Vec3 dir = from == null ? w.forward() : from.subtract(c);
        if (dir.lengthSqr() < 1.0E-6) dir = w.forward();
        dir = dir.normalize();
        if (w.shieldHp <= 0.0F) {
            w.setShield(ForcefieldBlockEntity.DOWN);
            w.shieldDownT = 0;
            fx(sl, w, Forcefield.FX_SHATTER, dir, 1.0F);
            for (ServerPlayer p : w.barPlayers()) p.displayClientMessage(Component.translatable("message.wmd.warden.shield_down"), true);
        } else if (st == ForcefieldBlockEntity.UP && w.shieldHp < w.shieldMax * Balance.WARDEN_SHIELD_CRACK) {
            w.setShield(ForcefieldBlockEntity.CRACK);
            fx(sl, w, Forcefield.FX_CRACK, dir, 1.0F);
        } else {
            fx(sl, w, Forcefield.FX_HIT, dir, Math.min(1.0F, dmg / 60.0F));
        }
        w.setShieldPct((int) (w.shieldHp * 100.0F / Math.max(1.0F, w.shieldMax)));
        return true;
    }

    private static void fx(ServerLevel sl, WardenEntity w, int kind, Vec3 dir, float power) {
        Vec3 c = w.position();
        Forcefield.Fx p = new Forcefield.Fx(key(w), kind, (float) c.x, (float) c.y, (float) c.z, Balance.WARDEN_SHIELD_R, (float) dir.x, (float) dir.y, (float) dir.z, power);
        if (kind == Forcefield.FX_SHATTER) PacketDistributor.sendToPlayersInDimension(sl, p);
        else PacketDistributor.sendToPlayersNear(sl, null, c.x, c.y, c.z, Balance.FF_FX_RANGE + Balance.WARDEN_SHIELD_R, p);
    }

    static void tick(WardenEntity w, ServerLevel sl, WardenState d, Player t) {
        long now = sl.getGameTime();
        int nodes = 0;
        for (int[] n : WardenBunker.NODES) {
            BlockPos p = new BlockPos(d.ox + n[0], d.oy + n[1], d.oz + n[2]);
            if (!sl.isLoaded(p) || !sl.getBlockState(p).is(Wmd.WARDEN_NODE.get())) continue;
            nodes++;
            if (now % 10 == 0 && w.shield() != ForcefieldBlockEntity.DOWN) {
                Vec3 a = Vec3.atCenterOf(p), b = w.position().add(0.0, 9.0, 0.0);
                for (int i = 1; i < 10; i++) {
                    Vec3 q = a.lerp(b, i / 10.0).add((sl.random.nextDouble() - 0.5) * 0.6, (sl.random.nextDouble() - 0.5) * 0.6, (sl.random.nextDouble() - 0.5) * 0.6);
                    sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
        int st = w.shield();
        if (w.nodesSeen < 0) w.nodesSeen = nodes;
        if (nodes < w.nodesSeen) {
            w.nodesSeen = nodes;
            if (st == ForcefieldBlockEntity.UP || st == ForcefieldBlockEntity.CRACK) {
                Vec3 dir = w.forward();
                if (nodes == 0) {
                    w.shieldHp = 0.0F;
                    w.setShield(ForcefieldBlockEntity.DOWN);
                    w.shieldDownT = 0;
                    fx(sl, w, Forcefield.FX_SHATTER, dir, 1.0F);
                } else {
                    w.shieldHp = Math.min(w.shieldHp, cap(w, nodes));
                    if (w.shieldHp < w.shieldMax * Balance.WARDEN_SHIELD_CRACK && st == ForcefieldBlockEntity.UP) w.setShield(ForcefieldBlockEntity.CRACK);
                    fx(sl, w, Forcefield.FX_CRACK, dir, 1.0F);
                }
                w.setShieldPct((int) (w.shieldHp * 100.0F / Math.max(1.0F, w.shieldMax)));
                st = w.shield();
            }
            WardenFight.sound(sl, w.getX(), w.getY() + 8.0, w.getZ(), WmdSounds.RG_ABORT.get(), Balance.SND_RELEASE, 0.7F);
            for (ServerPlayer p : w.barPlayers()) p.displayClientMessage(Component.translatable(nodes == 0 ? "message.wmd.warden.nodes_gone" : "message.wmd.warden.node_down", nodes, WardenBunker.NODES.length), true);
        }
        if (now % 20 == 0 && (st == ForcefieldBlockEntity.UP || st == ForcefieldBlockEntity.CRACK) && nodes > 0) {
            w.shieldHp = Math.min(cap(w, nodes), Math.max(w.shieldHp, Math.min(cap(w, nodes), w.shieldHp + nodes * Balance.WARDEN_NODE_REGEN * w.shieldMax)));
            w.setShieldPct((int) (w.shieldHp * 100.0F / Math.max(1.0F, w.shieldMax)));
        }
        if (st == ForcefieldBlockEntity.DOWN && ++w.shieldDownT >= Balance.WARDEN_SHIELD_DOWN && nodes > 0) {
            w.shieldHp = Math.min(cap(w, nodes), w.shieldMax * Math.min(1.0F, nodes * Balance.WARDEN_SHIELD_REUP));
            w.setShield(w.shieldHp < w.shieldMax * Balance.WARDEN_SHIELD_CRACK ? ForcefieldBlockEntity.CRACK : ForcefieldBlockEntity.UP);
            w.setShieldPct((int) (w.shieldHp * 100.0F / Math.max(1.0F, w.shieldMax)));
            WardenFight.sound(sl, w.getX(), w.getY() + 8.0, w.getZ(), WmdSounds.FF_BOOT.get(), Balance.SND_HEAVY, 1.0F);
            for (ServerPlayer p : w.barPlayers()) p.displayClientMessage(Component.translatable("message.wmd.warden.shield_up", nodes), true);
        }
        rail(w, sl, t, now);
        enrage(w, sl, now);
    }

    private static void rail(WardenEntity w, ServerLevel sl, Player t, long now) {
        if (w.railT < 0) {
            if (--w.railWait > 0 || t == null) return;
            w.railT = 0;
            w.railCoreDmg = 0.0F;
            w.railAim = null;
            WardenFight.sound(sl, w.getX(), w.getY() + 12.0, w.getZ(), WmdSounds.RG_CHARGE_LOOP.get(), Balance.SND_SHOWPIECE, 1.0F);
            for (ServerPlayer p : w.barPlayers()) p.displayClientMessage(Component.translatable("message.wmd.warden.rail_charge"), true);
        }
        w.railT++;
        w.setRail(w.railT);
        if (w.railT % 20 == 0 && w.railT < Balance.WARDEN_RAIL_CHARGE - Balance.WARDEN_RAIL_LOCK)
            WardenFight.sound(sl, w.getX(), w.getY() + 13.0, w.getZ(), WmdSounds.RG_SPARK.get(), Balance.SND_HEAVY, 0.8F + 0.6F * w.railT / Balance.WARDEN_RAIL_CHARGE);
        int fireAt = Balance.WARDEN_RAIL_CHARGE;
        if (w.railT == fireAt - Balance.WARDEN_RAIL_LOCK && t != null) {
            w.railAim = t.getEyePosition().subtract(0.0, 0.4, 0.0);
            WardenFight.sound(sl, w.getX(), w.getY() + 12.0, w.getZ(), WmdSounds.RG_COUNTDOWN.get(), Balance.SND_ALARM, 1.2F);
        }
        if (w.railCoreDmg >= Balance.WARDEN_RAIL_BREAK) {
            w.railT = -1;
            w.setRail(0);
            w.railWait = Balance.WARDEN_RAIL_PERIOD;
            Vec3 m = w.railMuzzle();
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.x, m.y, m.z, 40, 1.0, 1.0, 1.0, 0.3);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 20, 1.0, 1.0, 1.0, 0.05);
            WardenFight.sound(sl, m.x, m.y, m.z, WmdSounds.RG_ABORT.get(), Balance.SND_RELEASE, 1.0F);
            for (ServerPlayer p : w.barPlayers()) p.displayClientMessage(Component.translatable("message.wmd.warden.rail_broken"), true);
            return;
        }
        if (w.railT < fireAt) return;

        Vec3 from = w.railMuzzle();
        Vec3 aim = w.railAim == null ? from.add(w.forward().scale(40.0)) : w.railAim;
        Vec3 dir = aim.subtract(from);
        if (dir.lengthSqr() < 1.0E-6) dir = w.forward();
        dir = dir.normalize();
        Vec3 end = from.add(dir.scale(Balance.WARDEN_RAIL_RANGE));
        var hit = SableBridge.clipWorld(sl, new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, w), null);
        if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();
        double w2 = Balance.WARDEN_RAIL_W * Balance.WARDEN_RAIL_W;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(Balance.WARDEN_RAIL_W), e -> e != w)) {
            Vec3 c = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
            Vec3 ab = end.subtract(from);
            double u = Math.max(0.0, Math.min(1.0, c.subtract(from).dot(ab) / Math.max(1.0E-6, ab.lengthSqr())));
            if (from.add(ab.scale(u)).distanceToSqr(c) <= w2) { e.hurt(w.damageSources().mobAttack(w), Balance.WARDEN_RAIL_DMG); e.igniteForSeconds(5.0F); }
        }
        w.ownFire = true;
        try { WarheadFx.hurt(sl, end, Balance.WARDEN_RAIL_R, 2.0, 0.0, 1.0, 1.0, 4); } finally { w.ownFire = false; }
        WardenFight.sound(sl, from.x, from.y, from.z, WmdSounds.RG_SHOT.get(), Balance.SND_SHOWPIECE, 1.0F);
        WardenFxPayload pl = new WardenFxPayload(WardenFxPayload.RAIL, (float) from.x, (float) from.y, (float) from.z, (float) end.x, (float) end.y, (float) end.z, 12);
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(from) < 200.0 * 200.0) PacketDistributor.sendToPlayer(p, pl);
        w.railT = -1;
        w.setRail(0);
        w.railWait = Balance.WARDEN_RAIL_PERIOD;
    }

    private static boolean sees(ServerLevel sl, WardenEntity w, Vec3 from, Vec3 to) {
        var hit = SableBridge.clipWorld(sl, new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, w), null);
        return hit.getType() == HitResult.Type.MISS;
    }

    private static void enrage(WardenEntity w, ServerLevel sl, long now) {
        w.enrageLeft--;
        if (now % 20 == 0) {
            int s = Math.max(0, w.enrageLeft / 20);
            w.barName(Component.translatable("entity.wmd.warden.meltdown", s / 60, String.format("%02d", s % 60)));
            if (s <= 30) for (ServerPlayer p : w.barPlayers()) p.displayClientMessage(Component.translatable("message.wmd.warden.enrage", s / 60, String.format("%02d", s % 60)), true);
        }
        if (w.enrageLeft == 600) w.music("boss_enrage");
        if (w.enrageLeft <= 200 && w.enrageLeft % 40 == 0) WardenFight.sound(sl, w.getX(), w.getY() + 10.0, w.getZ(), WmdSounds.SIREN.get(), Balance.SND_ALARM, 1.2F);
        if (w.enrageLeft > 0) return;

        Vec3 c = w.local(0.0, 8.6, 3.6);
        w.ownFire = true;
        try { WarheadFx.hurt(sl, c, Balance.WARDEN_PURGE_R, 1.0, 0.5, 2.0, 1.0, 10); } finally { w.ownFire = false; }

        for (ServerPlayer p : sl.players()) {
            if (p.isCreative() || p.isSpectator() || p.distanceToSqr(c) > Balance.WARDEN_PURGE_R * Balance.WARDEN_PURGE_R) continue;
            if (!sees(sl, w, c, p.getEyePosition()) && !sees(sl, w, c, p.position().add(0.0, p.getBbHeight() * 0.5, 0.0))) continue;
            p.hurt(w.damageSources().sonicBoom(w), Balance.WARDEN_PURGE_DMG);
        }
        WardenFight.sound(sl, c.x, c.y, c.z, WmdSounds.NUKE_BOOM.get(), Balance.SND_SHOWPIECE, 1.0F);
        WardenFxPayload pl = new WardenFxPayload(WardenFxPayload.PURGE, (float) c.x, (float) c.y, (float) c.z, (float) c.x, (float) c.y, (float) c.z, 60);
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(c) < 256.0 * 256.0) PacketDistributor.sendToPlayer(p, pl);
        for (ServerPlayer p : w.barPlayers()) p.displayClientMessage(Component.translatable("message.wmd.warden.purge"), false);
        w.music("boss_purge");

        w.enrageLeft = Balance.WARDEN_ENRAGE_TICKS;
        w.railT = -1;
        w.setRail(0);
        raise(w, sl);
    }
}
