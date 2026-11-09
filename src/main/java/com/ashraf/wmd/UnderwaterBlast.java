package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class UnderwaterBlast {

    private UnderwaterBlast() {}

    public static final int TORPEDO = 0, T4 = 1, DEPTH_CHARGE = 2, MINE = 3;

    public static void go(ServerLevel sl, Vec3 at, int type, float power, UUID owner, Entity source) {
        at = inWater(sl, at);
        boolean wet = sl.getFluidState(BlockPos.containing(at)).is(FluidTags.WATER);
        double depth = NavalLayer.depthBelowSurface(sl, at);
        int top = NavalLayer.waterTop(sl, Mth.floor(at.x), Mth.floor(at.z));
        hurt(sl, at, power, owner, source);
        crater(sl, at, power, wet && top != NavalLayer.NO_WATER ? top : Crater.NO_SEA);
        NoiseNet.pulse(sl, at, Balance.TORP_DB + 40.0, NoiseNet.Kind.BLAST, owner, 60);
        if (wet) SeaMines.shock(sl, at, power);
        UnderwaterBlastPayload p = new UnderwaterBlastPayload(at.x, at.y, at.z, type, power, (float) depth, wet && top != NavalLayer.NO_WATER ? top + 1.0F : Float.NaN);
        PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, Balance.UW_FX_RANGE, p);
    }

    static Vec3 inWater(ServerLevel sl, Vec3 at) {
        BlockPos c = BlockPos.containing(at);
        if (sl.getFluidState(c).is(FluidTags.WATER)) return at;
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-2, -2, -2), c.offset(2, 2, 2))) {
            if (!sl.getFluidState(q).is(FluidTags.WATER)) continue;
            double d = q.distToCenterSqr(at) - (q.getY() > c.getY() ? 0.5 : 0.0);
            if (d < bd) { bd = d; best = q.immutable(); }
        }
        return best == null ? at : Vec3.atCenterOf(best);
    }

    private static void crater(ServerLevel sl, Vec3 at, float power, int seaTop) {
        double r = Balance.UW_CRATER_PER_POWER * power + 1.0;
        int x = Mth.floor(at.x), y = Mth.floor(at.y), z = Mth.floor(at.z);
        FusionReactorBlockEntity.blastAt(sl, at, r);
        AntimatterStoreBlockEntity.blastAt(sl, at, r);
        RailgunBlockEntity.blastAt(sl, at, r);
        FlightManager fm = FlightManager.get(sl);
        boolean sea = seaTop != Crater.NO_SEA;
        Crater c = new Crater(x, y, z, r, Balance.UW_CRATER_DEPTH, sea ? 0.0 : 0.4, 0.0, r * 1.5 + 16.0).sea(seaTop);
        fm.craters.add(c);
        DamageRings.queue(sl, c, WarheadType.HE);
        if (!sea) Debris.blast(sl, x, y, z, r, c);
        fm.setDirty();
    }

    private static void hurt(ServerLevel sl, Vec3 at, float power, UUID owner, Entity source) {
        double reach = Balance.UW_HURT_PER_POWER * power;
        Entity by = owner == null ? null : sl.getServer().getPlayerList().getPlayer(owner);
        DamageSource ds = sl.damageSources().explosion(source, by instanceof ServerPlayer sp ? sp : null);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach))) {
            double d = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0).distanceTo(at);
            if (d > reach || !e.isAlive()) continue;
            float k = (float) (1.0 - d / reach);
            e.hurt(ds, Balance.UW_DMG_PER_POWER * power * k);
            Vec3 push = e.position().subtract(at);
            if (push.lengthSqr() > 1e-4) e.push(push.normalize().scale(1.2 * k).add(0.0, 0.3 * k, 0.0));
            e.hurtMarked = true;
        }
    }
}
