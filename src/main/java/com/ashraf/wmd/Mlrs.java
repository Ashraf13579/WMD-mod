package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Mlrs {

    private Mlrs() {}

    static final double YAW_Z = 44.0, HINGE_Y = 36.0, HINGE_Z = 94.0, MOUTH_Z = -4.4;

    static final double MAX_LIFT = 60.0;

    static final double[][] TUBES = {
            { -25.1, 56.0 }, { -16.1, 56.0 }, { -7.1, 56.0 }, { -25.1, 46.0 }, { -16.1, 46.0 }, { -7.1, 46.0 },
            { 25.1, 56.0 }, { 16.1, 56.0 }, { 7.1, 56.0 }, { 25.1, 46.0 }, { 16.1, 46.0 }, { 7.1, 46.0 } };

    static final double ROCKET_LEN_PX = 60.0;

    static final double HOLD = 24.0;

    public static double velocity(int k) {
        int n = Math.max(1, Balance.MLRS_MOTOR_STEPS);
        double r = Balance.MLRS_RANGE * (double) Math.max(1, Math.min(n, k)) / n;
        return Math.sqrt(Balance.ART_GRAVITY * r * 1.02);
    }

    static double[] aim(double x, double y) {
        for (int k = 1; k <= Math.max(1, Balance.MLRS_MOTOR_STEPS); k++) {
            double el = Artillery.elevation(velocity(k), x, y, false);
            if (!Double.isNaN(el) && el <= MAX_LIFT) return new double[] { k, el };
        }
        return null;
    }

    static double[] mouth(int tube, double e) {
        double[] t = TUBES[Math.max(0, Math.min(TUBES.length - 1, tube))];
        double c = Math.cos(Math.toRadians(e)), s = Math.sin(Math.toRadians(e));
        double n = HINGE_Z - MOUTH_Z, h = t[1] - HINGE_Y;
        double n2 = n * c - h * s, h2 = n * s + h * c;
        return new double[] { t[0], HINGE_Y + h2, YAW_Z - (HINGE_Z - n2) };
    }

    static double ramAlpha(double e) {
        double c = Math.cos(Math.toRadians(e)), s = Math.sin(Math.toRadians(e));
        double ay = 36.0 + 70.0 * s + c, az = 94.0 - 70.0 * c + s;
        return Math.toDegrees(Math.atan2(ay - 35.5, 72.0 - az));
    }

    static final double RAM_ALPHA0 = ramAlpha(0.0);

    public static boolean navGuided(MinecraftServer server, UUID owner) { return Uplink.active(server, owner); }

    static boolean pressCasing(ItemStack s) { return s.is(ItemProcessBlockEntity.wmd("aluminium_casing")) || s.is(ItemProcessBlockEntity.wmd("mlrs_pod")); }

    static boolean pressFuze(ItemStack s) { return s.is(ItemProcessBlockEntity.wmd("guidance_t1")) || s.is(ItemProcessBlockEntity.wmd("propellant_charge")); }

    private static boolean hasOwner(UUID o) { return o != null && (o.getMostSignificantBits() != 0L || o.getLeastSignificantBits() != 0L); }

    static void impact(ArtilleryManager mgr, ServerLevel level, ArtShell s, long now) {
        int gx = (int) Math.floor(s.tx()), gz = (int) Math.floor(s.tz());
        FlightManager fm = FlightManager.get(level);
        BlockPos probe = new BlockPos(gx, level.getMinBuildHeight(), gz);
        if (!s.prepared() && !level.hasChunkAt(probe)) fm.holdForTest(level, gx, gz, HOLD);

        int gy = ArtilleryManager.ground(level, gx, gz, s.ty(), true);
        burst(level, fm, s, gx, gy, gz);
    }

    static void burstAt(ServerLevel level, ArtShell s, Vec3 p) {
        burst(level, FlightManager.get(level), s, (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
    }

    private static void burst(ServerLevel level, FlightManager fm, ArtShell s, int gx, int gy, int gz) {
        double r = Balance.MLRS_HE_RADIUS;
        Vec3 pos = new Vec3(gx + 0.5, gy, gz + 0.5);
        WarheadFx.hurt(level, pos, r, 3.5, 0.6, 0.9, 0.6, 3, hasOwner(s.owner()) ? s.owner() : null);
        Crater crater = new Crater(gx, gy, gz, r, 0.6, 0.4, 0.0, HOLD);
        fm.craters.add(crater);
        MapFeed.hit(level, "m270 rocket", 0, false, gx + 0.5, gy, gz + 0.5, r, hasOwner(s.owner()) ? s.owner() : null);
        Debris.blast(level, gx, gy, gz, r, crater);
        fm.setDirty();
        ShipStrike.blast(level, pos, r, ShipStrike.powerForGun(Artillery.ROCKET), hasOwner(s.owner()) ? s.owner() : null);
        PacketDistributor.sendToPlayersNear(level, null, pos.x, gy, pos.z, ArtilleryManager.NEAR,
                new ArtImpactPayload(s.id(), s.gun(), s.type(), pos.x, gy, pos.z, ArtImpactPayload.IMPACT));
        if (s.last() && hasOwner(s.owner()))
            CcBattleLog.add(level.getServer(), s.owner(), CcBattleLog.HIT, "log.wmd.art_done", "#item.wmd." + rocketId(s.type()), Integer.toString(Math.max(1, s.seq())),
                    Integer.toString(gx), Integer.toString(gz));
    }

    static String rocketId(int type) { return type == Artillery.GUIDED ? "mlrs_rocket_guided" : "mlrs_rocket"; }
}
