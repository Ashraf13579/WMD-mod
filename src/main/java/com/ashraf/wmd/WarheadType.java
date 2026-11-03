package com.ashraf.wmd;

public enum WarheadType {

    HE("he",                       1, 1.00, 0.55, 3.0, 0.70, 1.0, 1.6,  8, 1.0, 1.5),
    INCENDIARY("incendiary",       1, 0.55, 0.35, 2.5, 0.40, 0.5, 2.4, 15, 3.0, 2.2),
    FRAG("frag",                   1, 0.35, 0.30, 5.0, 0.55, 1.5, 0.0,  0, 0.3, 1.8),
    DECOY("decoy",                 1, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 0.3),
    THERMOBARIC("thermobaric",     2, 1.40, 0.30, 4.0, 1.00, 1.3, 2.2, 14, 1.5, 3.2),
    CLUSTER("cluster",             2, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 3.4),
    BREACHER("breacher",           2, 0.30, 0.30, 1.6, 0.50, 1.0, 0.0,  0, 0.5, 1.0),
    CHAFF("chaff",                 2, 0.15, 0.20, 1.0, 0.30, 0.3, 0.0,  0, 0.0, 3.0),
    BUNKER("bunker",               3, 0.35, 3.00, 1.8, 0.60, 1.2, 1.0,  6, 0.3, 2.0),
    EMP("emp",                     3, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 4.0),
    NUKE("nuke",                   3, 1.60, 0.50, 4.5, 0.90, 1.6, 3.0, 20, 2.0, 6.5),

    INTERCEPTOR("interceptor",     1, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 0.3),

    THERMO("thermo",               4, 1.50, 0.45, 4.0, 0.85, 1.9, 3.0, 25, 2.5, 6.0),

    ROD("rod",                    3, 0.40, 0.85, 9.4, 0.90, 4.0, 0.0,  0, 3.0, 4.0),

    HEMP("hemp",                  4, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 1.0),

    SEISMIC("seismic",            4, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 2.0),

    ASAT("asat",                  4, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 0.3),

    CUBESAT("cubesat",            3, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 0.3),

    HEAVY_BLAST("heavy_blast",    2, 0.90, 0.50, 3.4, 0.70, 1.2, 1.4,  8, 1.0, 2.0),

    CARPET("carpet",              2, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 3.4),

    UPHEAVAL("upheaval",          1, 0.80, 0.70, 3.0, 0.70, 1.0, 0.0,  0, 0.6, 1.6),

    NAPALM("napalm",              2, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 2.4),

    PENETRATOR("penetrator",      3, 0.06, 1.00, 2.0, 0.90, 1.5, 0.0,  0, 0.3, 1.2),

    DAISY_CUTTER("daisy_cutter",  3, 0.10, 0.40, 30.0, 6.25, 2.0, 0.5, 6, 0.8, 3.0),

    EARTHQUAKE("earthquake",      3, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 1.6),

    DAM_BREAKER("dam_breaker",    2, 0.60, 1.00, 2.5, 0.70, 1.2, 0.0,  0, 0.2, 1.4),

    NEUTRON("neutron",            3, 0.05, 0.30, 1.0, 0.50, 0.5, 0.0,  0, 0.0, 2.6),
    FLASH("flash",                2, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 3.0),
    CONCUSSION("concussion",      2, 0.15, 0.40, 4.0, 0.20, 0.6, 0.0,  0, 0.3, 4.0),
    CHEMICAL("chemical",          2, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 2.0),

    PLASMA("plasma",              2, 1.60, 0.45, 4.0, 0.85, 1.4, 1.5,  6, 0.4, 5.5),

    ANTIMATTER("antimatter",      4, 1.90, 0.55, 4.5, 0.90, 2.0, 0.8,  4, 0.5, 6.5),

    RAILGUN("railgun",            4, 2.40, 0.70, 5.0, 1.00, 2.5, 0.4,  3, 0.6, 7.0),

    ORBITAL_ROD("orbital_rod",    4, 2.80, 0.60, 5.0, 1.00, 2.6, 0.6,  6, 1.0, 8.0),

    ASTEROID("asteroid",          4, 4.00, 0.55, 5.5, 1.00, 3.0, 1.2, 10, 1.0, 10.0),

    TSAR("tsar",                  4, 4.50, 0.45, 4.0, 0.85, 2.2, 3.0, 25, 2.5, 12.0),

    NANITE("nanite",              3, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 1.0),
    GRAVITY("gravity",            3, 0.25, 0.30, 1.2, 0.10, 0.8, 0.0,  0, 0.1, 1.2),
    CRYO("cryo",                  3, 0.00, 0.00, 0.0, 0.00, 0.0, 0.0,  0, 0.0, 1.4);

    public final String id;
    public final int tier;
    public final double crater, depth, reach, lethal, dmg, burn, scorch, area;
    public final int burnSec;

    WarheadType(String id, int tier, double crater, double depth, double reach, double lethal, double dmg, double burn, int burnSec, double scorch, double area) {
        this.id = id; this.tier = tier; this.crater = crater; this.depth = depth; this.reach = reach; this.lethal = lethal; this.dmg = dmg;
        this.burn = burn; this.burnSec = burnSec; this.scorch = scorch; this.area = area;
    }

    public static WarheadType of(String id) {
        for (WarheadType t : values()) if (t.id.equals(id)) return t;
        return HE;
    }

    public static WarheadType ofOrdinal(int i) { return i >= 0 && i < values().length ? values()[i] : HE; }

    public boolean bombOnly() {
        return this == HEAVY_BLAST || this == CARPET || this == UPHEAVAL || this == NAPALM || this == PENETRATOR || this == DAISY_CUTTER || this == EARTHQUAKE || this == DAM_BREAKER || this == TSAR;
    }

    public boolean machineOnly() { return this == PLASMA || this == RAILGUN || this == ORBITAL_ROD || this == ASTEROID; }

    public boolean mushroom() { return this == NUKE || this == THERMO || this == TSAR; }
}
