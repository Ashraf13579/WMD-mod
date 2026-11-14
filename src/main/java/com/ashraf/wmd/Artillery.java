package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

public final class Artillery {

    private Artillery() {}

    public static final int MORTAR = 0, HOWITZER = 1;

    public static final int ROCKET = 2, SUPER_GUN = 3;

    public static final int HE = 0, SMOKE = 1, ILLUM = 2, CLUSTER = 3;

    public static final int GUIDED = 4;
    public static final String[] TYPES = { "he", "smoke", "illum", "cluster", "guided" };

    public static final String[] SPECIAL = { "laser", "canister", "sensor", "pierce", "incendiary", "flak", "camera", "jamming", "rocket", "leaflet" };
    public static final int SPECIAL_BASE = 5, LASER = 5, CANISTER = 6, SENSOR = 7, PIERCE = 8, INCENDIARY = 9, FLAK = 10, CAMERA = 11, JAMMING = 12, ROCKET_ASSIST = 13,
            LEAFLET = 14, SUPER_NUKE = 15;

    public static final int GUNSHIP_105 = 16;

    public static final int PICK_RACK = 20;

    public static boolean isSpecial(int type) { return type >= SPECIAL_BASE && type < SPECIAL_BASE + SPECIAL.length; }

    public static String typeName(int type) {
        if (isSpecial(type)) return SPECIAL[type - SPECIAL_BASE];
        if (type == SUPER_NUKE) return "nuke";
        if (type == GUNSHIP_105) return "gunship";
        return TYPES[clampType(type)];
    }

    public static double specialBurst(int type) {
        return switch (type) {
            case SENSOR -> Balance.SENSOR_BURST;
            case CAMERA -> Balance.CAMERA_BURST;
            case JAMMING -> Balance.JAM_BURST;
            case LEAFLET -> Balance.LEAFLET_BURST;
            case FLAK -> Balance.FLAK_BURST;
            default -> 0.0;
        };
    }
    public static final String[] GUNS = { "mortar", "howitzer", "mlrs" };

    public static final double ILLUM_BURST = 40.0, CLUSTER_BURST = 18.0;

    public static final double MIN_ELEVATION = 3.0, MAX_ELEVATION_MORTAR = 88.5;

    public static String shellId(int gun, int type) {
        if (gun == ROCKET) return type == GUIDED ? "mlrs_pod_guided" : "mlrs_pod";
        if (gun == SUPER_GUN) return type == SUPER_NUKE ? "shell_super_nuke" : SuperGun.SHELL;
        if (isSpecial(type)) return "shell_howitzer_" + SPECIAL[type - SPECIAL_BASE];
        if (type == GUNSHIP_105) return "shell_howitzer_gunship";
        return "shell_" + GUNS[gun] + "_" + TYPES[clampType(type)];
    }

    public static String gunBlock(int gun) {
        if (gun == SUPER_GUN) return SuperGun.BLOCK;
        return gun >= 0 && gun < GUNS.length ? GUNS[gun] : null;
    }

    public static String gunNameKey(int gun) {
        String b = gunBlock(gun);
        return b != null ? "block.wmd." + b : "gui.wmd.cbr.gun_unknown";
    }

    public static boolean canFire(int gun, int type) {
        if (gun == SUPER_GUN) return type == HE || type == SUPER_NUKE;
        if (gun == ROCKET) return type == HE || type == GUIDED;
        if (isSpecial(type)) return gun == HOWITZER;
        return type >= 0 && type < TYPES.length;
    }

    public static int clampType(int t) { return Math.max(0, Math.min(TYPES.length - 1, t)); }

    public static int knownType(int t) { return t >= 0 && (t < TYPES.length || isSpecial(t) || t == SUPER_NUKE || t == GUNSHIP_105) ? t : clampType(t); }

    public static Item shellItem(int gun, int type) { return ItemProcessBlockEntity.wmd(shellId(gun, type)); }

    public static int range(int gun) { if (gun == ROCKET) return Balance.MLRS_RANGE; return gun == MORTAR ? Balance.MORTAR_RANGE : Balance.HOWITZER_RANGE; }

    public static int minRange(int gun) { if (gun == ROCKET) return Balance.MLRS_MIN; return gun == MORTAR ? Balance.MORTAR_MIN : Balance.HOWITZER_MIN; }

    public static int reload(int gun) { if (gun == ROCKET) return Balance.MLRS_RIPPLE_TICKS; return gun == MORTAR ? Balance.MORTAR_RELOAD_TICKS : Balance.HOWITZER_RELOAD_TICKS; }

    public static double velocity(int gun, int charge) {
        if (gun == ROCKET) return Mlrs.velocity(charge);
        double r = gun == MORTAR ? Balance.MORTAR_RANGE : Balance.HOWITZER_RANGE * Math.max(1, Math.min(Balance.HOWITZER_CHARGES, charge)) / (double) Balance.HOWITZER_CHARGES;
        return Math.sqrt(Balance.ART_GRAVITY * r * 1.02);
    }

    public static int chargeRange(int charge) { return Balance.HOWITZER_RANGE * Math.max(1, Math.min(Balance.HOWITZER_CHARGES, charge)) / Balance.HOWITZER_CHARGES; }

    public static double elevation(double v, double x, double y, boolean high) {
        double g = Balance.ART_GRAVITY;
        if (x < 0.5) return high ? 89.0 : Double.NaN;
        double v2 = v * v, disc = v2 * v2 - g * (g * x * x + 2.0 * y * v2);
        if (disc < 0.0) return Double.NaN;
        double s = Math.sqrt(disc);
        double t = Math.atan((v2 + (high ? s : -s)) / (g * x));
        double deg = Math.toDegrees(t);
        if (high && deg > MAX_ELEVATION_MORTAR) return Double.NaN;
        if (!high && deg < MIN_ELEVATION) deg = Math.max(deg, MIN_ELEVATION);
        return deg;
    }

    public static int flightTicks(double v, double x, double elevationDeg) {
        double c = Math.cos(Math.toRadians(elevationDeg));
        return (int) Math.max(10, Math.round(x / Math.max(0.05, v * c)));
    }

    public static int autoCharge(double x, double y) {
        for (int k = 1; k <= Balance.HOWITZER_CHARGES; k++) if (!Double.isNaN(elevation(velocity(HOWITZER, k), x, y, false))) return k;
        return -1;
    }

    public static double spread(double d, boolean spotted) {
        double r = Math.max(Balance.ART_MIN_SPREAD, d / 1000.0 * Balance.ART_SPREAD_PER_KM);
        return spotted ? Math.max(Balance.ART_MIN_SPREAD * 0.5, r * Balance.ART_SPOT_FACTOR) : r;
    }

    public static boolean spotted(MinecraftServer server, ArtilleryManager mgr, UUID owner, double x, double z, long gameTime) {
        if (server == null || owner == null) return false;
        if (mgr != null && mgr.spotAt(owner, x, z, gameTime)) return true;
        long now = System.currentTimeMillis(), recent = Balance.ART_SPOT_MINUTES * 60_000L;
        CcTarget t = CcTarget.of(owner);
        if (t != null && t.source() == CcTarget.SRC_DESIGNATOR && now - t.timeMs() < recent && Math.hypot(t.x() + 0.5 - x, t.z() + 0.5 - z) <= Balance.ART_SPOT_RADIUS) return true;
        if (Drones.sees(server, owner, x, z)) return true;
        ReconData.Entry e = ReconData.get(server).entry(owner);
        if (e != null && e.snap != null && now - e.snap.timeMs() < recent) {
            ReconData.Snapshot s = e.snap;
            double span = (double) s.step() * s.n();
            if (x >= s.x0() && x < s.x0() + span && z >= s.z0() && z < s.z0() + span) return true;
        }
        return false;
    }
}
