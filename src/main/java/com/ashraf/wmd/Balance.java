package com.ashraf.wmd;

public final class Balance {

    private Balance() {}

    public static final int PIPE_CAPACITY_MB = 1000;
    public static final int PIPE_RATE_MB = 100;

    public static final int[] PIPE_RATE_TIER = { 100, 300, 800 }, PIPE_CAPACITY_TIER = { 1_000, 3_000, 8_000 };
    public static final int CANISTER_MB = 4_000;
    public static final int FLOODGATE_INTERVAL = 20, FLOODGATE_RADIUS = 8, FLOODGATE_MAX = 64, FLOODGATE_TANK_MB = 4_000;

    public static final int LOW_POWER_GRACE = 40;

    public static final int RINGS_MAX_R = 320, RING_SPAN = 128;

    public static final int COLLAPSE_MAX_R = 256, COLLAPSE_SPAN = 512, COLLAPSE_MAX_FALLING = 400, COLLAPSE_HANG_LOAD = 6, COLLAPSE_HANG_MAX = 15_000;

    public static final int COLLAPSE_BODY_MIN = 24, COLLAPSE_MAX_BODIES = 8, COLLAPSE_BODY_MAX = 1500, COLLAPSE_BODY_LIFE = 12_000;

    public static final int COLLAPSE_STUCK_TICKS = 60;

    public static final int COLLAPSE_PIECE_MAX = 60_000, COLLAPSE_CRUMBLE_MIN = 256;

    public static final int COLLAPSE_DECOR_REACH = 6;

    public static final int COLLAPSE_BELOW = 2;

    public static final double LOADGUARD_RECOVER = 0.8;

    public static final int OFFLINE_LOG_TICKS = 1200;

    public static final int FLOW_GRACE = 20;

    public static final int SHIP_CHECK_DELAY = 3, SHIP_CHECKS_MAX = 64, SHIP_FRACTURE_COOLDOWN = 200, SHIP_FRACTURE_MIN_SPAN = 8, SHIP_MARK_DELAY = 10,
            SHIP_BURN_TICKS = 1200, SHIP_FIRE_EVERY = 5, SHIP_FIRE_VIEW = 512, SHIP_CRASH_T2 = 1000, SHIP_CRASH_T3 = 6000;

    public static final int[] WRECK_PIECES = { 2, 3, 4, 4 };
    public static final int WRECK_MAX = 64, WRECK_LIFE = 600, WRECK_VIEW = 1500;
    public static final double WRECK_KEEP = 0.6, WRECK_SCATTER = 0.5, WRECK_GRAVITY = 0.04, WRECK_DRAG = 0.98, WRECK_HURT_R = 2.5, WRECK_DAMAGE = 6.0;
    public static final float WRECK_FIRE = 0.6F;
    public static final double SHIP_FRACTURE_POWER = 10.0, SHIP_FRACTURE_LOSS = 0.25, SHIP_BURN_LOSS = 0.12, SHIP_CRASH_SPEED = 10.0, SHIP_CRASH_STOP = 2.0;
    public static final double COLLAPSE_FREE_DIST = 1.5;

    public static final int COLLAPSE_WAKE_TICKS = 100;

    public static final double DEBRIS_BLAST_REACH = 2.0, DEBRIS_BLAST_PUSH = 10.0;

    public static final double BLAST_POWER_R = 10.0, BLAST_POWER_MIN = 0.6, BLAST_POWER_MAX = 3.0;

    public static double blastPower(double r) { return Math.max(BLAST_POWER_MIN, Math.min(BLAST_POWER_MAX, r / BLAST_POWER_R)); }

    public static final double COLLAPSE_PUSH = 5.0, COLLAPSE_PUSH_HIGH = 0.45;

    public static final double COLLAPSE_PUSH_PER_BLOCK = 6.0, COLLAPSE_PUSH_MAX = 15.0, COLLAPSE_TOPPLE_R = 12.0;

    public static final int COLLAPSE_SHATTER_MIN = 60, COLLAPSE_RUBBLE_LIFE = 6000, COLLAPSE_SHATTER_DELAY = 160, COLLAPSE_CRACK_SPACING = 7;
    public static final double COLLAPSE_SHATTER_SPEED = 4.0;

    public static final double SHOCK_SPEED = 12.0, SHOCK_REACH_MUL = 2.5, SHOCK_MAX_R = 480.0, SHOCK_SHIP_POWER = 6.0;
    public static final int SHOCK_MAX_WAVES = 24;

    public static final int AFTERMATH_MAX = 40, AFTERMATH_TELL_R = 512, AFTERMATH_PARTICLES = 40;

    public static final int COLLAPSE_DUST_MIN = 24, COLLAPSE_DUST_SECONDS = 45, COLLAPSE_DUST_MERGE_TICKS = 100, COLLAPSE_DUST_ROLL = 100;
    public static final double COLLAPSE_DUST_MERGE = 12.0, COLLAPSE_DUST_MAX_R = 32.0;

    public static final int C4_COLLAPSE_DELAY = 10, C4_DUST_SECONDS = 20;
    public static final double C4_COLLAPSE_R = 8.0;

    public static final int DEPOSIT_MB = 1_000_000;
    public static final int PUMPJACK_MB_PER_30S = 1000;
    public static final int PUMPJACK_FE_PER_TICK = 400;
    public static final int PUMPJACK_TANK_MB = 8000;
    public static final int FEED_PUMP_MB_PER_TICK = 10, FEED_PUMP_FE_PER_TICK = 100, FEED_PUMP_TANK_MB = 8_000;

    public static final int CELL_FE_PER_TICK = 360, CELL_CYCLE_TICKS = 100, CELL_TANK_MB = 8_000, CELL_BRINE_MB = 500, CELL_CL_FROM_BRINE = 500, CELL_H2_FROM_BRINE = 250, CELL_CAUSTIC = 1;

    public static final int MGCELL_FE_PER_TICK = 600, MGCELL_CYCLE_TICKS = 100, MGCELL_TANK_MB = 8_000, MGCELL_MGCL_MB = 500, MGCELL_MG_FROM_MGCL = 2, MGCELL_CL_FROM_MGCL = 250;
    public static final int MGCELL_BRINE_MB = 1_000, MGCELL_BRINE_TICKS = 200, MGCELL_MG_FROM_BRINE = 1;
    public static final int CHLOR_FE_PER_TICK = 480, CHLOR_CYCLE_TICKS = 160, CHLOR_TANK_MB = 8_000, CHLOR_CONC = 2, CHLOR_COKE = 1, CHLOR_CL_MB = 500, CHLOR_TICL_MB = 500;
    public static final int KROLL_FE_PER_TICK = 600, KROLL_CYCLE_TICKS = 600, KROLL_TANK_MB = 8_000, KROLL_TICL_MB = 500, KROLL_MG = 2, KROLL_SPONGE = 1, KROLL_MGCL_MB = 500;

    public static final int RIG_FE_PER_TICK = 800, RIG_WATER_TANK_MB = 8_000, RIG_WATER_MB = 250, RIG_RAW_PER_BLOCK = 3, RIG_RADIUS = 32, RIG_DEPTH = 1024, RIG_SCAN_TICKS = 200;
    public static final int RIG_TICKS_STEEL = 200, RIG_TICKS_CARBIDE = 100, RIG_TICKS_DIAMOND = 60, RIG_BIT_BLOCKS_STEEL = 80, RIG_BIT_BLOCKS_CARBIDE = 250, RIG_BIT_BLOCKS_DIAMOND = 400;

    public static final int TURBINE_FE_PER_TICK = 2_500, TURBINE_FE_PER_MB_LPG = 3_000, TURBINE_FE_PER_MB_NAPHTHA = 2_500, TURBINE_FE_PER_MB_GASOLINE = 3_500, TURBINE_TANK_MB = 8_000, TURBINE_BUFFER_FE = 500_000, TURBINE_OUT_PER_TICK = 10_000, TURBINE_BLADE_LIFE = 144_000;
    public static final int CONVERTER_IN_BUFFER_FE = 200_000, CONVERTER_OUT_BUFFER_FE = 100_000, CONVERTER_OUT_PER_TICK = 10_000;

    public static final int RES_TANK_MB = 4_000, RES_BUFFER_FE = 480_000, RES_MAX_IN_FE = 40_000, RES_FLUID_MB = 1_000;
    public static final int RES_METALLURGY_TICKS = 3_600, RES_METALLURGY_FE = 1_000;
    public static final int RES_MINING_TICKS = 2_400, RES_MINING_FE = 800;
    public static final int RES_POWER_TICKS = 3_600, RES_POWER_FE = 1_000;

    public static final int RES_ROCKETRY_TICKS = 6_000, RES_ROCKETRY_FE = 1_000;
    public static final int RES_DEFENCE_TICKS = 6_000, RES_DEFENCE_FE = 1_000;
    public static final int RES_WARHEADS_TICKS = 6_000, RES_WARHEADS_FE = 1_200;

    public static final int RES_METALLURGY3_TICKS = 6_000, RES_METALLURGY3_FE = 1_200;
    public static final int RES_NUCLEAR_TICKS = 6_000, RES_NUCLEAR_FE = 1_500;
    public static final int RES_SPACE_TICKS = 6_000, RES_SPACE_FE = 2_000;
    public static final int RES_ANTIMATTER_TICKS = 24_000, RES_ANTIMATTER_FE = 30_000;
    public static final int RES_FUSION_TICKS = 12_000, RES_FUSION_FE = 8_000;

    public static final int ROAST_FE_PER_TICK = 600, ROAST_CYCLE_TICKS = 240, ROAST_TANK_MB = 4_000, ROAST_CONC = 2, ROAST_LPG_MB = 100, ROAST_OXIDE = 2;
    public static final int H2F_FE_PER_TICK = 800, H2F_CYCLE_TICKS = 200, H2F_TANK_MB = 4_000, H2F_OXIDE = 1, H2F_H2_MB = 250, H2F_POWDER = 2;
    public static final int SINTER_FE_PER_TICK = 600, SINTER_CYCLE_TICKS = 160, SINTER_INGOT_POWDER = 2, SINTER_ROD_POWDER = 9;

    public static final int UPGRADE_MAX_LEVEL = 8;

    public static final double UPGRADE_SPEED_PER_LEVEL = 0.25, UPGRADE_SPEED_FE_PER_LEVEL = 0.40, UPGRADE_THROUGHPUT_PER_LEVEL = 0.25, UPGRADE_EFFICIENCY_PER_LEVEL = 0.08, UPGRADE_BUFFER_PER_LEVEL = 0.5;
    public static final int UPGRADE_MUFFLER_SILENT = 4, CIWS_DRUM_EXTRA = 1_000;

    public static final int DUCT_INTERVAL = 10, DUCT_BATCH = 8, DUCT_TICKS_PER_BLOCK = 5, DUCT_MAX = 1024, SORTER_BUFFER = 64;

    public static final double ROBOT_SPEED = 0.15;
    public static final int ROBOT_BATTERY = 200_000, ROBOT_FE_PER_BLOCK = 400, ROBOT_JOB_RESERVE = 80_000, ROBOT_LOAD_TICKS = 60, ROBOT_HEALTH = 60;
    public static final int ROBOT_ZONE = 128, PLANNER_INTERVAL = 40, ROBOT_MAX_PER_CC = 6, ROBOT_PATH_NODES = 12_000, ROBOT_STAND_RADIUS = 7;
    public static final int DOCK_BUFFER = 400_000, DOCK_RECEIVE = 4_000, DOCK_CHARGE = 2_000, PLANNER_FE = 200;

    public static final double XL_SPEED = 0.1;
    public static final int XL_BATTERY = 600_000, XL_FE_PER_BLOCK = 1_200, XL_JOB_RESERVE = 240_000, XL_LOAD_TICKS = 100, XL_HEALTH = 200, XL_MAX_PER_CC = 3;
    public static final int XL_DOCK_BUFFER = 1_200_000, XL_DOCK_RECEIVE = 8_000, XL_DOCK_CHARGE = 4_000;
    public static final int DEPOT_CELLS = 8, DEPOT_FE_PER_MOVE = 20_000, DEPOT_BUFFER = 200_000, DEPOT_RECEIVE = 2_000, DEPOT_CRANE_TICKS = 120;
    public static final boolean COOK_OFF = true;

    public static final int LAB_HEAVY_WATER_TANK = 4_000, LAB_AUTO_HEAVY_WATER = 500;

    public static final int TP_FE_PER_JUMP = 10_000_000, TP_BUFFER = 20_000_000, TP_RECEIVE = 40_000;
    public static final int TP_LOAD_TICKS = 200, TP_CHARGE_TICKS = 200, TP_FADE_TICKS = 120, TP_COOLDOWN_TICKS = 6_000, TP_MAX_LINKS = 4;

    public static final double RUNNER_SPEED = 0.2;
    public static final int RUNNER_BATTERY = 60_000, RUNNER_FE_PER_BLOCK = 100, RUNNER_JOB_RESERVE = 20_000, RUNNER_LOAD_TICKS = 30, RUNNER_HEALTH = 30, RUNNER_MAX_PER_CC = 4;

    public static final int RUNNER_UP_STACKS = 4, RUNNER_UP_ITEMS = 8, RUNNER_UP_KEEP = 1728;
    public static final int CHARGER_BUFFER = 100_000, CHARGER_RECEIVE = 1_000, CHARGER_CHARGE = 500;
    public static final int HEAVY_WATER_FEED_PER_LEVEL = 10, HEAVY_WATER_RATIO = 20, HEAVY_WATER_FE_PER_LEVEL = 150, HEAVY_WATER_TANK_MB = 8_000;
    public static final int INTAKE_FE_PER_TICK = 150, INTAKE_TANK_MB = 16_000, INTAKE_MB_BRONZE = 50, INTAKE_MB_TITANIUM = 150, INTAKE_LIFE_BRONZE = 24_000, INTAKE_LIFE_TITANIUM = 72_000, INTAKE_MIN_SOURCES = 9;
    public static final int DESALTER_TANK_MB = 4000, DESALTER_BRINE_TANK_MB = 4000, FURNACE_TANK_MB = 16_000;
    public static final double DESALTER_YIELD = 0.95;

    public static final int RADAR_FE_PER_TICK = RadarBlockEntity.FE_PER_TICK;

    public static final int DESALTER_MB_PER_TICK = 5, DESALTER_FE_PER_TICK = 200;
    public static final int FURNACE_MB_PER_TICK = 5, FURNACE_FE_PER_TICK = 800;
    public static final int ADU_MB_PER_TICK = 5, ADU_FE_PER_TICK = 600;

    public static final int ADU_CRUDE_TANK_MB = 16_000, ADU_PRODUCT_TANK_MB = 8_000;
    public static final double[] ADU_YIELD = { 0.05, 0.15, 0.15, 0.20, 0.45 };
    public static final int VDU_MB_PER_TICK = 4, VDU_FE_PER_TICK = 600;

    public static final int VDU_FEED_TANK_MB = 16_000, VDU_PRODUCT_TANK_MB = 8_000;
    public static final double[] VDU_YIELD = { 0.30, 0.40, 0.30 };
    public static final int REFORMER_MB_PER_TICK = 3, REFORMER_FE_PER_TICK = 500;

    public static final int REFORMER_FEED_TANK_MB = 16_000, REFORMER_PRODUCT_TANK_MB = 8_000;
    public static final double[] REFORMER_YIELD = { 0.80, 0.10 };
    public static final int FCC_MB_PER_TICK = 3, FCC_FE_PER_TICK = 700;

    public static final int FCC_FEED_TANK_MB = 8_000, FCC_PRODUCT_TANK_MB = 8_000;
    public static final double[] FCC_YIELD = { 0.50, 0.20 };
    public static final int HYDROTREATER_MB_PER_TICK = 3, HYDROTREATER_FE_PER_TICK = 400;

    public static final int HDT_TANK_MB = 8_000;
    public static final double HDT_H2_PER_MB = 0.10, HDT_JET_YIELD = 0.95;
    public static final int ALKYLATION_MB_PER_TICK = 2, ALKYLATION_FE_PER_TICK = 400;

    public static final int GENERATOR_TANK_MB = 8_000, GENERATOR_FE_PER_MB = 5_000;

    public static final int TERMINAL_TANK_MB = 16_000;

    public static final int[] MISSILE_FUEL_MB = { 1_000, 2_000, 4_000 };

    public static final int STORAGE_TANK_MB = 256_000;

    public static final int TANK_HP_STORAGE = 300, TANK_HP_CORE = 100;
    public static final double TANK_LEAK = 0.6, TANK_FIRE = 0.3;

    public static final double TANK_BLAST_DMG = 40.0, TANK_BLAST_R_REF = 4.0, TANK_BLAST_PAD = 2.0, TANK_ROUND_DMG = 1.5;

    public static final double TANK_BURN_HP_STORAGE = 3.0, TANK_BURN_HP_CORE = 5.0, TANK_BURN_HP_FUEL = 5.0;

    public static final int TANK_LEAK_MB_STORAGE = 500, TANK_LEAK_MB_CORE = 40, TANK_LEAK_MB_FUEL = 20, TANK_LEAK_MB_DROP = 40;

    public static final double TANK_BLAST_MIN_FILL = 0.10;

    public static final double TANK_R_FUEL = 1.5, TANK_R_DROP = 2.0, TANK_R_CORE = 2.5, TANK_R_STORAGE = 4.0, TANK_R_GAS = 6.0, TANK_FIREBALL_GAS = 2.0;

    public static final float TANK_SHIP_POWER = 2.5F;

    public static final int TANK_POOL_TICKS = 600, TANK_OIL_TICKS = 1_200, TANK_BOILOVER_TICKS = 300, TANK_SHIP_FIRE_TICKS = 300, TANK_TOXIC_TICKS = 1_200;
    public static final double TANK_POOL_R = 2.0, TANK_BOILOVER_R = 2.5, TANK_STEAM_R = 6.0, TANK_TOXIC_R = 10.0;

    public static final int COAL_GEN_FE_PER_TICK = 200, COAL_GEN_BUFFER_FE = 400_000, COAL_GEN_OUT_PER_TICK = 2_000, COAL_GEN_FUEL_MAX = 64;

    public static final int BF_STEEL_IRON = 1, BF_STEEL_COAL = 3, BF_STEEL_TICKS = 200, BF_SMELT_PER_COAL = 8, BF_BLOWER_FE = 40, BF_BLOWER_SPEED = 2;

    public static final int BF_STEEL_COKE = 1, BF_SMELT_PER_COKE = 16;

    public static final int BF_BRONZE_COPPER = 3, BF_BRONZE_TIN = 1, BF_BRONZE_OUT = 4, BF_BRONZE_TICKS = 200;

    public static final int CO_COAL = 2, CO_TAR_MB = 50, CO_CYCLE_TICKS = 300, CO_TAR_TANK_MB = 4_000;

    public static final int SG_FE_PER_TICK = 50, SG_BUFFER_FE = 100_000, SG_OUT_PER_TICK = 500, SG_FUEL_MAX = 64, SG_TAR_TICKS_PER_MB = 8, SG_TAR_TANK_MB = 4_000;

    public static final int[] SOLAR_ARRAY_FE = { 0, 15, 65, 155, 290, 470 };
    public static final int SOLAR_MIN_N = 2, SOLAR_MAX_N = 5, SOLAR_BUFFER_FE = 10_000, SOLAR_OUT_PER_TICK = 1_000, SOLAR_SCAN_TICKS = 20;
    public static final double SOLAR_TRACK_DEG = 50.0, SOLAR_DUSK_DEG = 15.0, SOLAR_STOW_DEG = 20.0, SOLAR_RAIN = 0.5, SOLAR_THUNDER = 0.25;

    public static final int WIND_FE_LOW = 60, WIND_FE_HIGH = 300, WIND_Y_LOW = 63, WIND_Y_HIGH = 200, WIND_CLEAR_R = 4, WIND_SCAN_TICKS = 100,
            WIND_BUFFER_FE = 20_000, WIND_OUT_PER_TICK = 1_000, WIND_TURN_TICKS = 96_000;
    public static final double WIND_GUST = 0.15, WIND_RAIN = 1.25, WIND_THUNDER = 1.5, WIND_BLOCK_PER = 0.05;

    public static final int SE_FE_PER_TICK = 600, SE_BUFFER_FE = 200_000, SE_OUT_PER_TICK = 2_000, SE_FUEL_MAX = 64, SE_WATER_TICKS_PER_MB = 2,
            SE_WATER_TANK_MB = 8_000;

    public static final int LG_FE_PIPED = 400, LG_MB_PER_TICK = 1, LG_TANK_MB = 4_000, LG_FE_PER_SOURCE = 25, LG_FE_PASSIVE_MAX = 100, LG_SCAN_TICKS = 40,
            LG_BUFFER_FE = 40_000, LG_OUT_PER_TICK = 1_000;
    public static final float SE_BURN_MULT = 2.0F * COAL_GEN_FE_PER_TICK / SE_FE_PER_TICK;

    public static final int GE_FE_PER_TICK = 1_250, GE_FE_PER_MB_GASOLINE = 3_500, GE_FE_PER_MB_REFORMATE = 4_000, GE_TANK_MB = 8_000,
            GE_BUFFER_FE = 300_000, GE_OUT_PER_TICK = 5_000;

    public static final int FP_FE_PER_TICK = 2_000, FP_FE_PER_MB = 4_500, FP_TANK_MB = 8_000, FP_BUFFER_FE = 400_000, FP_OUT_PER_TICK = 8_000;

    public static final int SM_FE_PER_TICK = 20, SM_CYCLE_TICKS = 80, SM_YIELD = 2;

    public static final int[] BAT_CAP = { 0, 2_500_000, 25_000_000, 100_000_000, 250_000_000 };
    public static final int[] BAT_IO = { 0, 10_000, 50_000, 150_000, 400_000 };
    public static final int BAT_T1_ROW = 5;

    public static final float SD_HEALTH = 60.0F, SD_ARMOR = 8.0F, SD_SPEED = 0.3F, SD_DAMAGE = 1.5F, SD_TURN = 14.0F, SD_HIT_CLOSE = 0.55F, SD_HIT_FAR = 0.2F;
    public static final int SD_RANGE = 24, SD_BURST = 20, SD_PAUSE = 14, SD_CHARGE_MAX = 1000, SD_DRAIN_TICKS = 60, SD_CHARGE_DUST = 50, SD_CHARGE_BLOCK = 450,
            SD_TELEPORT = 24;

    public static final float ST_DAMAGE = 2.0F, ST_TURN = 10.0F, ST_HIT_CLOSE = 0.6F, ST_HIT_FAR = 0.2F;
    public static final int ST_RANGE = 32, ST_BURST = 24, ST_PAUSE = 16, ST_FE_PER_TICK = 40, ST_FE_PER_VOLLEY = 400;

    public static final int EF_FE_PER_TICK = 20, EF_PULSE_TICKS = 20, EF_MAX_PIECES = 256, EF_FE_PER_PIECE = 10, EF_LIVE_TICKS = 30;
    public static final float EF_DAMAGE = 3.0F, EF_PUSH = 0.7F;

    public static final int MINE_ARM_TICKS = 60, CLAY_DELAY = 4;
    public static final float MINE_POWER = 3.0F, CLAY_TRIP = 6.0F, CLAY_TRIP_DEG = 25.0F, CLAY_DAMAGE = 24.0F, CLAY_RANGE = 12.0F, CLAY_CONE_DEG = 35.0F,
            CLAY_POWER = 1.5F;

    public static final int CCTV_DIRECT = 256, CCTV_HOP = 1000, CCTV_HOPS = 10, CCTV_MAX = 16, CCTV_VIEW = 48, CCTV_SEE = 40, CCTV_RELAY_FE = 10,
            SENSOR_BEAM = 32, SENSOR_CONE = 12, SENSOR_SNEAK = 6, CCTV_CUE = 48, CCTV_CUE_TICKS = 200, FLOOD_LEN = 24;
    public static final float SENSOR_CONE_DEG = 40.0F, CCTV_PAN = 70.0F;

    public static final int C4_LINKS = 8, C4_RANGE = 128, C4_FUSE = 60, C4_TIMER = 200, C4_DEPTH = 2, C4_RATING = 2,

            JAM_RADIUS = 40,
            JAM_CHARGE = 12_000, JAM_DUST = 600, JAM_BLOCK = 6_000, JAM_DEPLOY = 40,
            HACK_USE = 600,
            HACK_REACH = 5, HACK_TICKS = 6_000, HACK_COOLDOWN = 1_200,
            SEIS_THUMPS = 5, SEIS_THUMP_TICKS = 24, SEIS_RANGE = 64, SEIS_SHOW = 600, SEIS_COOLDOWN = 600;
    public static final float C4_POWER = 2.5F, SEIS_HALF_DEG = 45.0F;

    public static final int CC_SECURITY_FE = 200;

    public static final int PM_FE_PER_TICK = 20, PM_CYCLE_TICKS = 100, PM_NITRATE = 2, PM_CHARCOAL = 1, PM_GUNPOWDER = 3;

    public static final int FLARE_TANK_MB = 4_000, FLARE_FE_PER_MB = 1_000, FLARE_BUFFER_FE = 200_000, FLARE_OUT_PER_TICK = 2_000, FLARE_MB_PER_SEC = 10;

    public static final int CRUSH_FE_PER_TICK = 400, CRUSH_CYCLE_TICKS = 100, CRUSH_YIELD = 2;

    public static final int CRUSH_CHUNK_YIELD = 1;

    public static final int MILL_FE_PER_TICK = 800, MILL_CYCLE_TICKS = 200, MILL_PLATES = 2;
    public static final int WIRE_FE_PER_TICK = 400, WIRE_CYCLE_TICKS = 80, WIRE_PER_INGOT = 3;

    public static final int LEACH_FE_PER_TICK = 400, LEACH_CYCLE_TICKS = 200, LEACH_TANK_MB = 4_000, LEACH_CONC = 2, LEACH_CAUSTIC = 1, LEACH_WATER_MB = 500, LEACH_YELLOWCAKE = 1;

    public static final int CENT_FE_PER_TICK = 1_000, CENT_CYCLE_TICKS = 2_400, CENT_YELLOWCAKE = 4, CENT_ENRICHED = 1, CENT_DEPLETED = 3, CENT_NEIGHBOUR_BONUS_PCT = 50, CENT_MAX_NEIGHBOURS = 2;
    public static final int ARC_FE_PER_TICK = 720, ARC_W_CYCLE_TICKS = 300, ARC_TI_CYCLE_TICKS = 240, ARC_W_CONC = 3, ARC_TI_CONC = 1, ARC_TI_COKE = 0, ARC_CYCLES_PER_ELECTRODE = 6, ARC_INGOTS_PER_CYCLE = 1;
    public static final int ELECTRO_FE_PER_TICK = 300, ELECTRO_CYCLE_TICKS = 160, ELECTRO_BAUXITE_PER_CYCLE = 2, ELECTRO_CYCLES_PER_ELECTRODE = 8, ELECTRO_ALU_PER_CYCLE = 1;
    public static final int NITRO_FE_PER_TICK = 500, NITRO_CYCLE_TICKS = 100, NITRO_DUST_PER_CYCLE = 2, NITRO_NAPHTHA_MB = 250, NITRO_TANK_MB = 4_000, NITRO_HE_PER_CYCLE = 1;
    public static final int COKER_FE_PER_TICK = 600, COKER_CYCLE_TICKS = 100, COKER_BITUMEN_MB = 500, COKER_TANK_MB = 8_000, COKER_COKE_PER_CYCLE = 4;

    public static final int BAY_FETCH_TICKS = 240, BAY_ROLL_WELD_TICKS = 60, BAY_WELD_TICKS = 400, BAY_ROLL_CRANE_TICKS = 160, BAY_CRANE_TICKS = 480,
            BAY_ROLL_WARHEAD_TICKS = 40, BAY_WARHEAD_TICKS = 560, BAY_RETURN_TICKS = 240;
    public static final int BAY_TOTAL_TICKS = BAY_FETCH_TICKS + BAY_ROLL_WELD_TICKS + BAY_WELD_TICKS + BAY_ROLL_CRANE_TICKS + BAY_CRANE_TICKS
            + BAY_ROLL_WARHEAD_TICKS + BAY_WARHEAD_TICKS + BAY_RETURN_TICKS;

    public static final int BAY_ROLL_CLEAN_TICKS = 60, BAY_CLEAN_TICKS = 400, BAY_ROLL_PAINT_TICKS = 60, BAY_PAINT_TICKS = 700,
            BAY_ROLL_CRYO_TICKS = 60, BAY_CRYO_TICKS = 640, BAY_RETURN_PER_STATION = 120;

    public static final int BAY_AIR_YIELD = 2;
    public static final double BAY_AIR_TIME = 0.6;
    public static final int[] BAY_STATION_TICKS = { BAY_ROLL_CLEAN_TICKS + BAY_CLEAN_TICKS, BAY_ROLL_PAINT_TICKS + BAY_PAINT_TICKS, BAY_ROLL_CRYO_TICKS + BAY_CRYO_TICKS };

    public static int bayRunTicks(int stations) {
        int n = BAY_TOTAL_TICKS + stations * BAY_RETURN_PER_STATION;
        for (int i = 0; i < stations && i < BAY_STATION_TICKS.length; i++) n += BAY_STATION_TICKS[i];
        return n;
    }
    public static int bayRunFe(int stations) { return BAY_FE_PER_TICK * bayRunTicks(stations); }

    public static final int BAY_ROLL_STORE_TICKS = 200, BAY_STORE_TICKS = 360, BAY_RETURN_STORE_EXTRA = 240;
    public static int bayRunTicks(int stations, boolean storing) { return bayRunTicks(stations) + (storing ? BAY_ROLL_STORE_TICKS + BAY_STORE_TICKS + BAY_RETURN_STORE_EXTRA : 0); }

    public static final double[] WEIGHT_AIRFRAME = { 1.0, 2.0, 4.0, 8.0 }, WEIGHT_MOTOR = { 1.0, 1.5, 2.5, 5.0 }, WEIGHT_GUIDANCE = { 0.3, 0.4, 0.5, 0.7 };

    public static final double STAGE_WEIGHT_SHARE = 0.6, BOOSTER_WEIGHT = 2.0, BOOSTER_RANGE_LOSS = 0.07, BOOSTER_SPEED_LOSS = 0.02, BOOSTER_THRUST_SPEED = 0.06;

    public static final int BOOSTER_MIN = 2, BOOSTER_STD = 2;

    public static final int BOOSTER_FUEL_MB = 3000;
    public static final double WEIGHT_DIVERT = 2.8;
    public static double warheadWeight(String id) {
        if (id.startsWith(MirvBusItem.PREFIX)) {
            double w = MIRV_BUS_WEIGHT;
            for (String s : id.substring(MirvBusItem.PREFIX.length()).split("\\+")) if (!s.isEmpty()) w += warheadWeight(s);
            return w;
        }
        return switch (id) {
            case "decoy" -> 0.2;
            case "interceptor" -> 0.3;
            case "he", "incendiary" -> 0.5;
            case "frag" -> 0.6;
            case "chaff" -> 0.8;
            case "breacher", "emp" -> 0.9;
            case "cluster" -> 1.0;
            case "thermobaric" -> 1.4;
            case "bunker" -> 2.0;
            case "nuke" -> 2.4;
            case "thermo" -> 3.2;
            case "rod" -> 3.6;
            case "hemp" -> 2.6;
            case "seismic" -> 3.0;
            case "neutron" -> 2.2;
            case "antimatter" -> 4.0;
            case "flash", "chemical" -> 0.8;
            case "concussion" -> 1.2;
            default -> 0.5;
        };
    }

    public static final double[] BASE_RANGE = { 2000.0, 5000.0, 10000.0, 20000.0 }, SPEED = { 1.0, 1.3, 1.5, 1.8 };

    public static final double RANGE_EXPONENT = 1.2, RANGE_MIN = 0.3, RANGE_MAX = 1.3;

    public static final double[] HEALTH = { 100.0, 250.0, 600.0, 1500.0 };

    public static final double FUEL_BASE_MB = 250.0, FUEL_PER_BLOCK = 0.375;

    public static final double[] FUEL_RATE = { 3.0, 5.0, 10.0, 20.0 };

    public static final double DIVERT_RANGE = 0.75;

    public static final int LAB_RUN_TICKS = 600, LAB_EXCELLENT_TICKS = 460, LAB_BAD_TICKS = 260, LAB_MAX_DAMAGE = 3;

    public static final double[] LAB_GREEN_HALF = { 0.17, 0.14, 0.12, 0.10 }, LAB_DRIFT = { 1.4, 1.6, 1.7, 1.9 };
    public static final double LAB_RED_ZONE = 0.32, LAB_VALVE_STEP = 0.075, LAB_ACCEL = 0.0005, LAB_PUSH = 0.0035, LAB_MAX_SPEED = 0.0015, LAB_KICK = 0.0035;

    public static final int LAB_HAZARD_TICKS = 90;
    public static final double LAB_HAZARD_RADIUS = 5.0, LAB_HAZARD_DAMAGE = 8.0;

    public static final float[] LAB_BLAST = { 5.0F, 7.0F, 9.0F, 12.0F };

    public static final int LAB_LOAD_TICKS = 50, LAB_TRAVEL_TICKS = 60, LAB_TO_OVEN_TICKS = 90, LAB_PRESS_TICKS = 160, LAB_FILL_TICKS = 140, LAB_OVEN_TICKS = 160, LAB_STORE_TICKS = 120, LAB_HOME_TICKS = 150;
    public static final int LAB_RUN_FE = 120_000, LAB_BUFFER_FE = 1_000_000, LAB_RECEIVE_FE = 10_000;

    public static final double HARDENED_FALLOFF = 0.5, SHIELD_PER_POINT = 0.5;

    public static final double[] INT_SPEED = { 9.0, 10.5, 12.0, 13.5 }, INT_RANGE = { 1500.0, 2500.0, 4000.0, 6000.0 };
    public static final int INT_MIN_LEAD = 40, INT_MAX_FLIGHT = 500, INT_SHOT_FE = 40_000, INT_RELOAD_TICKS = 120, INT_BUFFER_FE = 600_000, INT_RECEIVE_FE = 20_000;

    public static final double INT_MIN_ELEV = 30.0, INT_MAX_ELEV = 88.0;

    public static final int SILO3_SLOTS = 3, SILO4_SLOTS = 4, BATTERY_SLOTS = 4, BATTERY_RELOAD_TICKS = 80, SILO_LID_TICKS = 40, SILO_LIFT_TICKS = 70, SILO_STOW_DELAY = 400;

    public static final int SILO3_RISE_TICKS = 12, SILO4_RISE_TICKS = 8;
    public static final double SILO_MIN_ELEV = 12.0, SILO_MAX_ELEV = 90.0;

    public static final boolean BAY_SABOTAGE = true;

    public static final double CIWS_RANGE = 1800.0, CIWS_HIT_CLOSE = 0.22, CIWS_HIT_FAR = 0.03, CIWS_AZ_RATE = 6.0, CIWS_EL_RATE = 4.0;
    public static final int CIWS_ROUNDS_PER_TICK = 5, CIWS_DRUM = 1000, CIWS_BOX = 500, CIWS_FIRE_FE = 1_600, CIWS_MOVE_FE = 100;
    public static final float CIWS_DAMAGE = 6.0F;

    public static final double CIWS_SHIP_RANGE = 600.0, CIWS_SHIP_MAX_HARDNESS = 5.0;
    public static final int CIWS_SHIP_BLOCKS = 5,
            CIWS_SHIP_BURST_TICKS = 8, CIWS_SHIP_ROUNDS = 30;
    public static final double[] INT_SHIP_POWER = { 5.0, 7.0, 9.0, 12.0 };
    public static final int SHIP_DESIGNATION_TICKS = 12_000, SHIP_DEFENCE_SCAN_TICKS = 20, SHIP_LOCK_AIM_RADIUS = 300, SHIP_INT_MIN_DIST = 80;

    public static final int DEBRIS_PUSH_TRIES = 5;

    public static final double DEBRIS_PUSH_SCALE = 1.4;

    public static final int DEBRIS_WAKE_TICKS = 20;
    public static final int DEBRIS_BLOB_MAX = 60, DEBRIS_TREE_MAX = 160, DEBRIS_SCAN_TICKS = 10, DEBRIS_SWEEP_TICKS = 200, DEBRIS_FALLBACK_MAX = 96;

    public static final double DEBRIS_PIECES_PER_R = 1.2, DEBRIS_PIECES_PER_R2 = 0.025, DEBRIS_PIECES_BASE = 3.0;
    public static final int DEBRIS_TREES_MAX = 100;

    public static final double DEBRIS_BIG_R = 60.0, DEBRIS_BIG_SHARE = 0.25;
    public static final double DEBRIS_BOMBLET_SHARE = 0.25;
    public static final double DEBRIS_MAX_HARDNESS = 6.0, DEBRIS_HURT_MIN_SPEED = 5.0, DEBRIS_DMG_PER_SPEED = 0.6, DEBRIS_DMG_MAX = 14.0;
    public static final double INT_REFINED_BONUS = 0.10;

    public static final int SEEKER_HEAT_MAX_TIER = 2;

    public static final int DIVERT_DODGES = 2;
    public static final double COUNTER_MATCH = 0.40, COUNTER_MISMATCH = 0.05;
    public static final double INT_KILL_RADIUS = 80.0, INT_KILL_EQUAL = 0.75, INT_KILL_LOWER = 0.40, INT_KILL_HIGHER = 1.0;
    public static final int BAY_FE_PER_TICK = 800, BAY_RUN_FE = BAY_FE_PER_TICK * BAY_TOTAL_TICKS, BAY_BUFFER_FE = 4_000_000, BAY_RECEIVE_FE = 20_000;

    public static final int SALVAGE_REGION_BLOCKS = 2000;
    public static final int SALVAGE_SPAWN_CLEAR = 1500;
    public static final double SALVAGE_TURRET_RANGE = 32.0;
    public static final int SALVAGE_TURRET_WINDUP = 30;
    public static final int SALVAGE_TURRET_BURST = 10;
    public static final int SALVAGE_TURRET_PAUSE = 20;
    public static final float SALVAGE_TURRET_DAMAGE = 3.0F;
    public static final double SALVAGE_TURRET_HIT_CLOSE = 0.55, SALVAGE_TURRET_HIT_FAR = 0.15;
    public static final float SALVAGE_TURRET_TURN = 9.0F;

    public static final int REACTOR_WATER_MB = 200, REACTOR_FUEL_TICKS = 72_000, REACTOR_FUEL_SLOTS = 4, REACTOR_TANK_MB = 16_000;
    public static final int REACTOR_HEAT_MAX = 1_000, REACTOR_HEAT_ALARM = 600, REACTOR_HEAT_STEAM = 800, REACTOR_HEAT_IMMINENT = 950, REACTOR_HEAT_OPERATING = 300;
    public static final double REACTOR_HEAT_RISE = 0.25, REACTOR_HEAT_FALL = 0.5;
    public static final int REACTOR_MELTDOWN_TIER = 2;
    public static final int REACTOR_WARN_RADIUS = 64, REACTOR_WARN_REPEAT_TICKS = 200;
    public static final int REACTOR_CHIPS = 2;

    public static final int TOWER_FE_PER_TICK = 400, TOWER_CHIPS = 1;

    public static final int REACTOR_STEAM_MB = 200, REACTOR_STEAM_TANK_MB = 8_000, REACTOR_PUMP_FE_PER_TICK = 2_000, REACTOR_PUMP_BUFFER_FE = 400_000, REACTOR_SPENT_SLOTS = 4;
    public static final int STEAM_TURBINE_MB = 200, STEAM_TURBINE_FE_PER_MB = 50, STEAM_TURBINE_TANK_MB = 8_000, STEAM_TURBINE_BUFFER_FE = 2_000_000, STEAM_TURBINE_OUT_PER_TICK = 40_000;
    public static final int TOWER_CONDENSE_MB = 200, TOWER_WATER_PCT = 95, TOWER_TANK_MB = 8_000;

    public static final int REPRO_FE_PER_TICK = 1_200, REPRO_CYCLE_TICKS = 600, REPRO_TANK_MB = 4_000, REPRO_SPENT = 1, REPRO_CAUSTIC = 1, REPRO_WATER_MB = 500, REPRO_PLUTONIUM = 1, REPRO_DEPLETED = 2;

    public static final int SAT_MAX = 3, SAT_LIFE_HOURS = 168, SAT_PASS_MINUTES = 20, SAT_OVERHEAD_SECONDS = 300,       SAT_DOWNRANGE = 3_000;

    public static final double RADAR_DETECT_RANGE = 3_000.0;

    public static final int RECON_IMPACT_BLUR = 150, RECON_SNAPSHOT_STEP = 4, RECON_SNAPSHOT_RADIUS = 256, RECON_DOT_REFRESH_TICKS = 20, RECON_RETAKE_SECONDS = 5;

    public static final int RECON_FILL_IN_FLIGHT = 6, RECON_FILL_MAX_MSPT = 40;

    public static final double UPLINK_MISS_FACTOR = 0.5, UPLINK_MIN_MISS = 3.0, UPLINK_HOMING_RADIUS = 32.0, UPLINK_LOCK_MOVE = 2.0, UPLINK_MAX_STRETCH = 4.0;
    public static final int UPLINK_HOMING_SECONDS = 5, UPLINK_LOCK_SAMPLE_TICKS = 10;

    public static final int UPLINK_LOCK_LOST = 100;

    public static final int SEEKER_SECONDS = 12, SEEKER_RADIUS = 200, LASER_FOLLOW = 10, LASER_SPOT_TTL = 60, HEAT_WINDOW = 12_000, DIVERT_MIN = 100, DIVERT_MAX = 300,
            BEAM_RANGE = 750;

    public static final int TRIPOD_BUFFER = 300_000, TRIPOD_FE_PER_TICK = 50, TRIPOD_RECEIVE = 2_000, TRIPOD_REACH = 6, TRIPOD_IDLE = 40,
            LWR_RADIUS = 128, LWR_FE_PER_TICK = 5, LWR_BUFFER = 50_000, LWR_RECEIVE = 500, TABLET_RANGE = 5_000;

    public static final double HYPER_SPEED = 9.0, HYPER_APEX = 0.4, HYPER_RANGE = 1.0,       HYPER_CIWS = 0.2;

    public static final double HYPER_CLIMB = 3.0, HYPER_DIVE = 30.0, HYPER_V_START = 11.0;
    public static final int HYPER_WARN = 6, HYPER_BOOST_TICKS = 80;

    public static final double PAD_TILT_DEG = 30.0;

    public static final int RACK_LOAD = 4, BAY_ROWS = 3, BAY_LOAD = 3, RACK_FE = 1_000, BAY_FE = 2_000, RACK_IDLE = 25, BAY_IDLE = 100,
            SALVO_PULSE = 5, SALVO_GAP = 4, BAY_DOOR_TICKS = 20, BAY_DOOR_HOLD = 60;

    public static final double BOMB_G = 0.08, BOMB_DRAG = 0.995, IRON_SCATTER = 0.03, IRON_SCATTER_MAX = 12.0, LGB_RANGE = 300.0, LGB_STEER = 0.06,
            GLIDE_SPEED = 1.6, GLIDE_RANGE = 1_000.0, GLIDE_SCATTER = 8.0, GLIDE_SINK = 0.25, CARPET_OPEN_UP = 40.0, CARPET_DUD = 0.10, DUD_POWER = 2.5,
            HEAVY_GLASS = 60.0;
    public static final int GLIDE_WINGS = 6, CARPET_BOMBLETS = 40;

    public static final double BOMB_CIWS_HIT = 0.25, BOMB_RADAR_SEND = 3000.0;
    public static final int BOMB_RADAR_TICKS = 4;

    public static final int DELAY_MIN = 600, DELAY_MAX = 1200, NAPALM_STEP = 37, BOUNCE_SKIPS = 5;

    public static final int SIGHT_FE = 10, SIGHT_LINK = 16, SIGHT_STEP = 2, SIGHT_SIM_TICKS = 1200, SIGHT_PULSE = 4;
    public static final double SIGHT_LASER = 300.0, SIGHT_CROSS = 6.0, SIGHT_HUD = 6.0, SIGHT_DRAW = 256.0;

    public static final int[] DRONE_RUN_TICKS = { 600, 1_200, 3_600, 7_200 }, DRONE_RUN_FE = { 20_000, 40_000, 150_000, 400_000 };
    public static final int DRONE_TRAVEL_TICKS = 30, DRONE_LOAD_TICKS = 40, DRONE_STORE_TICKS = 100, DRONE_HANGAR_TICKS = 200, DRONE_HOME_TICKS = 60;
    public static final int DRONE_BUFFER_FE = 1_000_000, DRONE_RECEIVE_FE = 10_000, DRONE_SHELVES = 9;
    public static final double[] DRONE_STATION_SHARE = { 0.30, 0.25, 0.25, 0.20 };

    public static final int[] BOMB_RUN_TICKS = { 600, 800, 800, 900, 600, 900, 2_400, 3_600, 3_600, 2_400, 12_000 },
            BOMB_RUN_FE = { 15_000, 20_000, 20_000, 20_000, 15_000, 20_000, 60_000, 90_000, 90_000, 60_000, 400_000 };
    public static final int BOMB_TRAVEL_TICKS = 30, BOMB_LOAD_TICKS = 40, BOMB_STORE_TICKS = 100, BOMB_CRADLE_TICKS = 300, BOMB_HOME_TICKS = 60;
    public static final int BOMB_BUFFER_FE = 500_000, BOMB_RECEIVE_FE = 10_000, BOMB_PALLETS = 6, BOMB_NAPHTHA_MB = 500, BOMB_NAPHTHA_TANK = 4_000;
    public static final double[] BOMB_STATION_SHARE = { 0.35, 0.35, 0.30 };

    public static final int DRONE_T1_RANGE = 250, DRONE_T2_RANGE = 1_000, DRONE_T1_BATTERY = 3_600, DRONE_T2_BATTERY = 4_800, DRONE_T1_SHOT_TICKS = 20;
    public static final float DRONE_T1_HEALTH = 6.0F, DRONE_T2_HEALTH = 10.0F, DRONE_T1_DAMAGE = 2.0F, DRONE_T2_BLAST = 4.0F;
    public static final double DRONE_T1_SPEED = 0.6, DRONE_T2_SPEED = 0.9, DRONE_T1_SHOT_RANGE = 4.5;
    public static final int DRONE_CHARGE_FE = 2_000, HIVE_CHARGE_RATE = 100, HIVE_BUFFER_FE = 20_000, HIVE_RECEIVE_FE = 1_000;

    public static final double DRONE_T3_SPEED = 1.2, DRONE_T4_SPEED = 1.0, CHUTE_RATE = 0.12, T4_LOITER_R = 48.0;
    public static final int DRONE_T3_CRUISE = 60, DRONE_T4_CRUISE = 90, DRONE_T3_RANGE = 3_000, DRONE_T4_RANGE = 6_000, DRONE_T3_BATTERY = 7_200, DRONE_T4_BATTERY = 18_000;
    public static final float DRONE_T3_HEALTH = 20.0F, DRONE_T4_HEALTH = 40.0F;

    public static final int CC_DRONE_WARN_R = 1_500, CC_BOMB_WARN_R = 300, T4_LASE_RANGE = 200;

    public static final int FLASH_WHITE_TICKS = 160;
    public static final int FLASH_TICKS = 1_200, CONC_NAUSEA_TICKS = 2_400, GAS_TICKS = 3_600, GAS_MASK_FILTER = 600;
    public static final double NEUTRON_KILL = 2.5, FLASH_RANGE = 300.0, CONC_PUSH = 80.0, CONC_THROW = 4.0, CONC_GLASS = 200.0, GAS_RADIUS = 30.0, GAS_WIND = 0.04,
            GAS_DAMAGE = 2.0;
    public static final int RACK_SLOTS = 3, RACK_MAGAZINE = 8, CANISTER_MIN = 16, CANISTER_MAX = 96, CANISTER_FLECHETTES = 40, SENSOR_SUBS = 2, INCENDIARY_BURN = 2,
            CAMERA_SECONDS = 30, CAMERA_VIEW = 128, LEAFLET_CHARS = 128, JAM_FIRST_LEAD = 400;

    public static final double LASER_SHELL_MIN = 40.0, LASER_HIGH_ARC = 600.0, LASER_CLEAR_END = 3.0, LASER_RELAY_DIST = 4.0;
    public static final int LASER_RELAY_TICKS = 20;
    public static final double LASER_SHELL_RANGE = 200.0, CANISTER_CONE = 10.0, CANISTER_DAMAGE = 6.0, SENSOR_BURST = 150.0, SENSOR_SCAN = 60.0, FLAK_FUZE = 10.0,
            FLAK_DRONE_DAMAGE = 12.0, FLAK_RADIUS = 6.0, CAMERA_BURST = 60.0, JAM_BURST = 40.0, ROCKET_RANGE_MUL = 1.5, ROCKET_SPREAD_MUL = 1.6, LEAFLET_BURST = 30.0,
            LEAFLET_RADIUS = 64.0;

    public static final int LEAFLET_DROPS = 12;

    public static final double FLAK_MIN = 24.0;

    public static final double FLAK_BURST = 30.0;
    public static final int DRONE_PER_CC = 2, CATAPULT_FE = 5_000, CATAPULT_BUFFER_FE = 20_000, CATAPULT_RECEIVE_FE = 1_000, RUNWAY_W = 12, RUNWAY_L = 48, T4_LOITER_S = 120;
    public static final double NAPALM_BURN = 10.0, BOUNCE_MIN_SPEED = 0.6, BOUNCE_MAX_SLOPE = 0.6, BOUNCE_KEEP = 0.7, BOUNCE_SINK = 4.0, DAISY_PROBE = 1.4;

    public static final double TV_CONE = 12.0;

    public static final double TV_SLEW = 0.08, TV_SLEW_MIN = 1.5;

    public static final int CAM_TERRAIN_CHUNKS = 8, CAM_CHUNKS_PER_TICK = 4;
    public static final int TV_STEP = 4;

    public static final double HAND_LOCK_CONE = 3.0, MANPADS_RANGE = 600.0, MANPADS_SPEED = 5.0, MANPADS_TURN = 12.0, MANPADS_FUSE = 4.0,
            MANPADS_SHIP_POWER = 4.0, JAV_RANGE = 300.0, JAV_CLIMB = 50.0, JAV_SPEED = 3.0, JAV_TURN = 9.0, JAV_POWER = 3.5;
    public static final int HAND_LOCK_TICKS = 40, MANPADS_LIFE = 160, JAV_LIFE = 320, HAND_COOLDOWN = 30;

    public static final int DESIG_REFRESH = 10, AREA_VIEW_SIZE = 400, AREA_SAT_STEP = 2, AREA_SCOPE_SIZE = 64, AREA_CHUNKS_PER_TICK = 12;
    public static final int THERMAL_MAX_SPAN = 21, DECOY_ARM_PULL = 60;
    public static final int SMOKE_RADIUS = 40, SMOKE_HEIGHT = 20, SMOKE_GROW_SECONDS = 25, SMOKE_SECONDS = 60, SMOKE_COOLDOWN = 180, SMOKE_FE_PER_TICK = 50,
            SMOKE_FE_PER_LAUNCH = 15_000, SMOKE_CANISTERS_MAX = 16;
    public static final int GROUND_STATION_FE_PER_TICK = 600;

    public static final int SATFAC_TICKS = 2_400, SATFAC_FE_PER_TICK = 1_000, SATFAC_TITANIUM = 4, SATFAC_SILICON = 2, SATFAC_CIRCUITS = 2, SATFAC_ALUMINIUM = 4, SATFAC_GOLD_WIRE = 2;

    public static final int CC_TABLE_FE_PER_TICK = 800, CC_WARNING_FE = 200, CC_RECON_WALL_FE = 300, CC_AIR_DEFENCE_FE = 300;

    public static final double CC_AIR_DEFENCE_RANGE_BONUS = 1_000.0;

    public static final int CC_BUILDING_FE_PER_TICK = 800, CC_BUFFER_FE = 1_000_000;

    public static final int CC_REACH = 2_000;
    public static final int CC_LAUNCH_FE = 300, CC_RETARGET_FE = 200, CC_STRIKE_FE = 300, CC_CAM_FE = 200;

    public static final int CC_GLOBE_FE = 300, CC_EW_FE = 400, CC_GPS_FE = 200, CC_ALARM_FE = 100, CC_PRINTER_FE = 200, CC_COMMS_FE = 500;
    public static final int CC_JAM_RADIUS = 1_000, GROUND_STATION_JAM_FE = 600, CC_PRINTER_TICKS = 200, CC_GPS_SYNC_TICKS = 40, CC_ALARM_TICKS = 200;

    public static final int ASAT_HIT_PCT = 85, ASAT_CLIMB_S = 60;
    public static final int SAT_HACK_SECONDS = 90, SAT_HACK_FE_PER_TICK = 2_000, SAT_REKEY_COOLDOWN_S = 300;
    public static final int INT_SAT_KILL_PCT = 75, INT_SAT_ZONE = 3_000, CC_INTERCEPTOR_FE = 300;

    public static final int EWA_RANGE = 8_000, EWA_FE_PER_TICK = 1_500, CUBESAT_MINUTES = 30;
    public static final int DRONE_RANGE = 1_500, DRONE_LOITER_S = 120, DRONE_RADIUS = 96, DRONE_HEALTH = 10;
    public static final double DRONE_SPEED = 0.8;

    public static final int MORTAR_RANGE = 400, MORTAR_MIN = 32, MORTAR_RELOAD_TICKS = 40, MORTAR_LAY_TICKS = 20, MORTAR_MAGAZINE = 16;
    public static final int HOWITZER_RANGE = 2_000, HOWITZER_MIN = 100, HOWITZER_RELOAD_TICKS = 120, HOWITZER_LAY_TICKS = 60, HOWITZER_FE_PER_TICK = 200,
            HOWITZER_CHARGES = 5, HOWITZER_MAGAZINE = 8, HOWITZER_CHARGE_SLOT = 32;
    public static final int SHELL_PRESS_FE_PER_TICK = 400, SHELL_PRESS_TICKS = 200;

    public static final double GUNSHIP_HE_RADIUS = 6.0, GUNSHIP_HE_REACH = 3.5, GUNSHIP_HE_LETHAL = 0.6, GUNSHIP_HE_DMG = 1.0, GUNSHIP_HE_SHIP_POWER = 6.0,
            GUNSHIP_HE_FX = 2.2;
    public static final double ART_GRAVITY = 0.05, ART_SPREAD_PER_KM = 12.0, ART_MIN_SPREAD = 1.5, ART_SPOT_FACTOR = 0.35, MORTAR_HE_RADIUS = 2.0, HOWITZER_HE_RADIUS = 3.0;
    public static final int ART_SPOT_MINUTES = 10, ART_SPOT_RADIUS = 48, ART_SMOKE_SECONDS = 45, ART_ILLUM_SECONDS = 40, ART_CLUSTER_BOMBLETS = 7, ART_MAX_SHELLS = 256;

    public static final double ALKYLATE_YIELD = 0.6, ALKYLATE_T4_RANGE_MULT = 1.25, CRUISE_SPEED = 1.2, CRUISE_MISS_FACTOR = 0.5, CRUISE_RADAR_RANGE = 1_000.0, CRUISE_INT_MIN_ELEV = 2.0;
    public static final double[] CRUISE_RANGE = { 2_000.0, 3_000.0, 5_000.0 };
    public static final int ALKY_ACID_LPG_MB = 4_000, ALKY_TANK_MB = 8_000, ALKY_ACID_SLOT = 16, ALKYLATE_CANISTER_MB = 1_000, ALKY_FILL_TICKS = 40, ALKY_CANISTER_SLOT = 16;
    public static final int CRUISE_ALT = 30, CRUISE_FUEL_CANISTERS = 1, CRUISE_WAYPOINTS = 4, CRUISE_MIN_RANGE = 300, CRUISE_LAUNCHER_FE_PER_TICK = 300, CRUISE_RAISE_TICKS = 60,
            CRUISE_SALVO_GAP_TICKS = 40, CRUISE_HOLD_TICKS = 100, CRUISE_FUEL_SLOT = 16, SUPER_CHARGE_ALKYLATE_MB = 500;

    public static final int CBR_FE_PER_TICK = 800, CBR_RANGE = 3_000, CBR_TRACE_TICKS = 60, CBR_ERROR = 20, CBR_RETRACE_TICKS = 600, CBR_AUTO_ROUNDS = 3, CBR_KEEP = 6;
    public static final double CRAM_RANGE = 1_200.0, CRAM_HIT = 0.60, DOME_HIT = 0.95;
    public static final int CRAM_ROUNDS_PER_SHOT = 200, CRAM_BURST_FE = 16_000, CRAM_BURST_TICKS = 10;
    public static final int DOME_RADIUS_MIN = 64, DOME_RADIUS_MAX = 256, DOME_MARKER_TOL = 3, DOME_FE = 400, ART_CONSOLE_FE = 300;

    public static final int MLRS_RANGE = 8_000, MLRS_MIN = 400, MLRS_MAGAZINE = 4, MLRS_RIPPLE_TICKS = 5, MLRS_RELOAD_TICKS = 1_200, MLRS_LAY_TICKS = 80,
            MLRS_FE_PER_TICK = 600, MLRS_MOTOR_STEPS = 8;
    public static final double MLRS_SALVO_RADIUS = 28.0, MLRS_AIM_ERROR = 0.5, MLRS_HE_RADIUS = 3.5, MLRS_GUIDED_SPREAD = 2.0, GUIDED_SHELL_SPREAD = 1.0;
    public static final int CAMO_MAX_SPAN = 5, DECOY_LIFE_MINUTES = 120;

    public static final int SUPER_GUN_RANGE = 15_000, SUPER_GUN_MIN = 1_000, SUPER_GUN_CHARGES = 12, SUPER_GUN_RELOAD_TICKS = 6_000, SUPER_GUN_LAY_TICKS = 200,
            SUPER_GUN_FE_PER_TICK = 1_000, SUPER_GUN_MAGAZINE = 4, SUPER_GUN_CHARGE_SLOT = 48, SUPER_GUN_BLAST_TIER = 2;
    public static final int SUPER_GUN_WARN_RANGE = 20_000, SUPER_GUN_HEARD = 4_000, SUPER_GUN_SHAKE = 192;
    public static final double SUPER_GUN_SPREAD_MUL = 0.5;

    public static final double SHIP_BLAST_PER_RADIUS = 0.6, SHIP_BLAST_MIN = 3.0, SHIP_BLAST_MAX = 16.0, SHIP_BLAST_MORTAR = 3.0, SHIP_BLAST_HOWITZER = 4.0,
            SHIP_BLAST_ROCKET = 4.5, SHIP_BLAST_BOMBLET = 2.0, SHIP_BLAST_REACH_PER_POWER = 1.75, SHIP_BLAST_PUSH = 4.0, SHIP_PATH_ARM_BLOCKS = 8.0;

    public static final double SHIP_UW_HOLE_PER_POWER = 0.25;

    public static final double SHIP_UW_TOUGH_CAP = 24.0, SHIP_UW_TOUGH_PER_RATING = 2.0;
    public static final int SHIP_UW_HOLE_MAX = 400;
    public static final int SHIP_BLAST_MAX_SHIPS = 3, SHIP_BLAST_MAX_PER_TICK = 4, SHIP_BLAST_QUEUE = 32, SHIP_BLAST_QUEUE_TICKS = 20,
            SHIP_PATH_MAX_SEGMENTS = 256, SHIP_PATH_MAX_EXACT = 32, SHIP_PATH_ARM_TICKS = 20;

    public static final int SABLE_LARGE_SHIP_MIN_BLOCKS = 200;
    public static final double SABLE_LARGE_SHIP_MIN_MASS = 400.0;

    public static final int RADAR_MAX_SHIP_CONTACTS = 8, RADAR_MAX_SHIP_LOCKS = 2;

    public static final double CC_NAVAL_RANGE = 200.0;
    public static final int AIRSHIP_FORGET_TICKS = 6_000, AIRSHIP_WARN_PER_MIN = 4, AIRSHIP_REFRESH_TICKS = 60, AIRSHIP_HEADING_SECONDS = 30;

    public static final float FUSION_ARC_DAMAGE = 4.0f, FUSION_ARC_KNOCKBACK = 0.6f;

    public static final int BUS_NET_MAX_FLOW = 1_000_000;

    public static final int FUSION_MAX_FE = 100_000, FUSION_SOCKET_OUT = 40_000, FUSION_BUFFER_FE = 4_000_000;
    public static final int FUSION_IGNITION_FE = 50_000_000, FUSION_CHARGE_IN = 120_000;
    public static final int FUSION_COOLDOWN_TICKS = 80, FUSION_IGNITION_TICKS = 80, FUSION_RAMP_TICKS = 2_400, FUSION_SHUTDOWN_TICKS = 200, FUSION_QUENCH_TICKS = 100;
    public static final double FUSION_D_MB = 5.0, FUSION_T_MB = 2.0, FUSION_WATER_MB = 100.0;
    public static final int FUSION_MIN_PCT = 10, FUSION_FUEL_TANK_MB = 16_000, FUSION_WATER_TANK_MB = 32_000;
    public static final int FUSION_START_FUEL_MB = 1_000, FUSION_START_WATER_MB = 4_000;

    public static final int CELL_HW_MB = 500, CELL_D2_FROM_HW = 500, MGCELL_LI_EVERY = 4;
    public static final int BREEDER_SLOTS = 4, BREEDER_TICKS = 6_000, BREEDER_TRITIUM_MB = 4_000;

    public static final int AM_FE_PER_TICK = 40_000, AM_CYCLE_TICKS = 24_000, AM_H2_MB = 2, AM_WATER_MB = 50, AM_TANK_MB = 16_000;
    public static final int AM_STORE_FE = 2_000, AM_STORE_CELLS = 8, AM_STORE_BACKUP_TICKS = 1_200;

    public static final int RG_FE_PER_TICK = 40_000, RG_SLEW_TICKS = 60, RG_LOAD_TICKS = 160, RG_CHARGE_TICKS = 6_000, RG_SPINUP_TICKS = 600, RG_FINAL_TICKS = 1_200;

    public static final int RG_AFTER_TICKS = 1_200, RG_ABORT_TICKS = 100, RG_TRAVEL_TICKS = 140, RG_CUTSCENE_LEAD = 160, RG_CUTSCENE_TICKS = 500;

    public static final double RG_ARC_APEX_PER_BLOCK = 0.3, RG_ARC_APEX_MIN = 220.0, RG_ARC_APEX_MAX = 1_400.0;

    public static final int RG_SNAP_R = 256, RG_SNAP_DEPTH = 16, RG_SNAP_CHUNKS_PER_TICK = 6, RG_SNAP_PENDING = 12, RG_FAST_CHUNKS_PER_TICK = 12;

    public static final int RG_HOLD_RAMP_START = 48, RG_HOLD_RAMP_STEP = 16, RG_HOLD_RAMP_TICKS = 100;

    public static final int WAVE_GLASS_COLUMNS = 1500;

    public static final double FRAT_BALL = 1.2, FRAT_REACH = 4.0, FRAT_ALT = 400.0;
    public static final int FRAT_BALL_TICKS = 60;

    public static final double BLAST_ITEM_CLEAR = 1.2;

    public static final double AIRBURST_H = 1.2, AIRBURST_REACH = 1.4, AIRBURST_LETHAL = 0.8, AIRBURST_SCORCH = 1.4, AIRBURST_WAVE = 1.3, AIRBURST_DEPTH = 0.12, AIRBURST_OUTER = 1.25;

    public static final int VACUUM_SPARKS_MAX = 220;
    public static final double RG_SCORCH_LEN = 32.0, RG_SCORCH_HALF_DEG = 22.0;

    public static final int RG_CUT_DEBRIS = 420;

    public static final int RG_SNAP_FAR_R = 512, RG_SNAP_FAR_CELL = 4, RG_SNAP_FAR_PER_TICK = 4, RG_SNAP_FAR_PENDING = 8;
    public static final double RG_CUT_FOG_START = 0.62;
    public static final double RG_CUT_GRAVITY = 30.0;
    public static final double RG_SLEW_DEG_PER_TICK = 1.5, RG_EL_MIN = 8.0, RG_EL_MAX = 55.0, RG_ABORT_REACH = 12.0;
    public static final double RG_CORNER_YAW = 20.0, RG_CORNER_MIN_PITCH = 15.0;

    public static final double RG_SHOW_DIAL_R = 11.0, RG_SHOW_STORM_H = 55.0, RG_SHOW_DARK_R = 120.0, RG_SHOW_FLASH_R = 200.0;
    public static final double RG_STORM_CHANCE = 0.06, RG_STORM_R0 = 12.0, RG_STORM_R1 = 40.0;

    public static final int RG_SHAFT_DEPTH = 64, RG_SHAFT_R = 6;

    public static final int RG_BLOOM_TICKS = 30, RG_TRACER_TICKS = 600, RG_CROWN_TICKS = 140, RG_STEAM_TICKS = 2_400;
    public static final double RG_BOOM_SPEED = 17.0, RG_BOOM_MIN = 150.0;

    public static final double STRIKE_REPORT_R = 600.0;

    public static final int CUT_SLOW_BEFORE = 8, CUT_SLOW_TICKS = 32, CUT_RIPPLES = 3, CUT_RIPPLE_TICKS = 90, CUT_RIPPLE_GAP = 14;
    public static final double CUT_SLOW_SPEED = 0.3, CUT_CATCH_SPEED = 1.5, CUT_RIPPLE_REACH = 3.0;
    public static final double RG_WAVE_REACH = 1_500.0;

    public static final int RG_SURGE_TICKS = 60, RG_SURGE_PUFFS = 2200;
    public static final double RG_SURGE_EDGE = 1.06;

    public static final int RG_WRECK_DELAY = 200, RG_WRECK_TICKS = 6_000, RG_WRECK_CUT = 40;

    public static final int ORBIT_SLOT_SPACING = 4_096, ORBIT_Y = 200, ORBIT_BUILD_R = 96;

    public static final int STATION_WRECK_BURSTS = 4, STATION_WRECK_TICKS = 40, STATION_WRECK_FX_RANGE = 640, STATION_BURN_WARN = 200, STATION_EVA_LOST = 96,
            STATION_FALL_TICKS = 360;
    public static final double STATION_EVA_DRIFT = 0.02, STATION_EVA_DRIFT_MAX = 1.0;

    public static final int O2_FULL = 72_000, O2_BREATH = 20, O2_THRUST_MULT = 3, O2_CABIN_REFILL = 200, ORBIT_LOST_R = 96, ORBIT_LOST_Y = 64, ORBIT_LOST_SECONDS = 10;
    public static final float VACUUM_DAMAGE = 4.0F;

    public static final int O2_STATION_FE_PER_TICK = 2_000, O2_STATION_FILL = 60, O2_STATION_FILL_TICKS = 1_200, O2_STATION_WATER_MB = 2, O2_STATION_WATER_EVERY = 10,
            O2_STATION_TANK_MB = 8_000, RES_SPACE2_TICKS = 12_000, RES_SPACE2_FE = 20_000;

    public static final int CREW_SEATS = 4, CREW_HOLD = 600, CREW_CUT_TICKS = 1600;
    public static final double CREW_LIFT_R = 2.5;

    public static final int CREW_PAD_OFFSET = 24, CREW_DESCENT_TICKS = 500, CREW_SEEN_R = 1_000;

    public static final int CREW_BURN_TICKS = 300, CREW_PLASMA_TICKS = 120, CREW_BURN_WAIT = 200, CREW_PRELOAD_R = 5, CREW_FADE_TICKS = 40, CREW_WHITE_TICKS = 20;

    public static final int CREW_LAND_SEARCH = 24;

    public static final int STATION_ORBIT_TICKS = 24_000, STATION_SUN_TICKS = 15_600;
    public static final long STATION_BUFFER = 12_000_000L;

    public static final long STATION_BATTERY_FE = 25_000_000L;
    public static final int STATION_WING_FE = 4_000, STATION_CORE_FE = 200, STATION_HAB_FE = 1_000;
    public static final int STATION_VENT_TICKS = 40, STATION_AIR_BACK_TICKS = 600, STATION_AIR_DRAIN_TICKS = 6_000, STATION_AIRLOCK_TICKS = 100;

    public static final int STATION_ARRIVE_TICKS = 240, STATION_DEPLOY_TICKS = 600, STATION_DOCK_TICKS = 60;

    public static final int STATION_O2_FE = 34;

    public static final int STATION_HP_CORE = 400, STATION_HP_HABITAT = 300, STATION_HP_WING = 150, STATION_BLAST_DMG = 20;
    public static final int STATION_ROOM_MAX = 2_000, STATION_ROOM_EVERY = 100;

    public static final int RES_SPACE3_TICKS = 24_000, RES_SPACE3_FE = 30_000;

    public static final int CARGO_SLOTS = 27, CARGO_ARRIVE_TICKS = 240, CARGO_RELEASE_TICKS = 400;

    public static final int STATION_HP_SENSOR = 150, STATION_SENSOR_FE = 1_500, SENSOR_DEPLOY_TICKS = 300, SENSOR_GROUND_R = 1_024;

    public static final int STATION_HP_LASER = 300, STATION_LASER_FE = 300, LASER_SHOT_FE = 2_000_000, LASER_CHARGE_TICKS = 400, LASER_LOCK_TICKS = 30,
            LASER_FIRE_TICKS = 50, LASER_HIT_AT = 20, LASER_COOL_TICKS = 1_200, LASER_DMG = 150, LASER_BOOST_TICKS = 400, ASAT_STATION_R = 2_000, ASAT_STATION_DMG = 250;

    public static final int BOARD_CUT_TICKS = 600;

    public static final int TSAR_CHUTE_DELAY = 40, TSAR_CIWS_HITS = 4, TSAR_INT_HIT_PCT = 75, TSAR_LIFE = 6_000, TSAR_SNAP_R = 384, TSAR_SNAP_FAR_R = 1024,
            TSAR_CUT_LEAD = 300, TSAR_CUT_AFTER = 500, TSAR_INT_EVERY = 100;
    public static final double TSAR_CHUTE_SPEED = 0.1, TSAR_BURST_H = 60.0, TSAR_FIZZLE_POWER = 10.0, TSAR_FLASH_R = 3_000.0, TSAR_SEEN_R = 12_000.0,
            TSAR_SOUND_SPEED = 17.0;
    public static final int MIRV_MAX = 3, MIRV_SPREAD_DEFAULT = 150;
    public static final int[] MIRV_SPREADS = { 50, 100, 150, 250, 400, 500 };
    public static final double MIRV_BUS_WEIGHT = 1.0;
    public static final int WINTER_W_NUKE = 1, WINTER_W_THERMO = 2, WINTER_W_TSAR = 5, WINTER_THRESHOLD = 5, WINTER_WINDOW = 48_000, WINTER_TICKS = 72_000,
            WINTER_FADE_TICKS = 2_400, WINTER_SNOW_TRIES = 6, WINTER_SNOW_MAX = 3, WINTER_MELT_TICKS = 24_000;
    public static final double WINTER_FOG = 140.0, WINTER_CROP_SPEED = 0.5;
    public static final int RDV_SIGHT = 1_000, RDV_CLOSE = 300, RDV_MATCH = 150, RDV_MATCH_GAP = 30;
    public static final double RDV_RAM_SHARE = 0.6, RDV_EQUAL = 0.1, RDV_EQUAL_LOSS = 0.7;

    public static final int STATION_HP_TURRET = 200, STATION_TURRET_FE = 100, TURRET_SHOT_FE = 40_000, TURRET_SHOT_EVERY = 5, TURRET_DMG = 8,
            TURRET_SYNC_TICKS = 3, TURRET_INTERCEPT_HITS = 12, TURRET_INTERCEPT_WINDOW = 400;
    public static final double TURRET_BOLT_SPEED = 6.0, TURRET_RANGE = 300.0, TURRET_MUZZLE = 4.2, TURRET_DOWN_DEG = 30.0;
    public static final float TURRET_PLAYER_DMG = 4.0F, TURRET_PITCH_UP = 25.0F, TURRET_PITCH_DOWN = 90.0F;
    public static final int LASER_CLOSE_SHOT_FE = 500_000, LASER_CLOSE_CHARGE_TICKS = 100, LASER_CLOSE_LOCK_TICKS = 6, LASER_CLOSE_DMG = 100, LASER_CLOSE_COOL_TICKS = 300;

    public static final int STATION_HP_ROD = 400, STATION_ROD_FE = 500, ROD_RACK_MAX = 3;

    public static final int STATION_HP_MIRROR = 300, STATION_MIRROR_FE = 200;

    public static final int MIRROR_DEPLOY_TICKS = 1_200, MIRROR_FOLD_TICKS = 1_200, MIRROR_BURN_TICKS = 6_000, MIRROR_COOL_TICKS = 6_000,
            MIRROR_MIN_COOL = 600, MIRROR_BURN_FE = 1_500, MIRROR_TARGET_R = 1_000, MIRROR_WARN_R = 200;
    public static final double MIRROR_SPEED = 0.05;

    public static final int MIRROR_LAVA_R = 3, MIRROR_LAVA_S = 60, MIRROR_FIRE_R = 20, MIRROR_FIRE_TRIES = 6, MIRROR_IGNITE_TICKS = 100;
    public static final int MIRROR_SPOT_R = 8, MIRROR_FIRE_S = 2, MIRROR_WOOD_S = 10, MIRROR_GLASS_S = 20, MIRROR_MAGMA_S = 40, MIRROR_COOL_EVERY = 10,
            MIRROR_SEE_R = 6_000;
    public static final float MIRROR_HURT = 8.0F;

    public static final int MIRROR_FF_FE = 1_000_000;

    public static final int STATION_HP_BAY = 600, STATION_BAY_FE = 300, BAY_BUILD_FE = 3_000, TUG_BUILD_TICKS = 72_000,
            TUG_ANTIMATTER = 4, TUG_DT = 1, TUG_CORES = 1;
    public static final long BAY_BUILD_MIN_BUFFER = 2_000_000L;

    public static final int ASTEROID_PASS_MIN = 216_000, ASTEROID_PASS_MAX = 360_000, ASTEROID_WINDOW = 36_000, ASTEROID_SIZE_MIN = 15, ASTEROID_SIZE_MAX = 25,
            TUG_LAUNCH_TICKS = 1_200, TUG_DOORS_AT = 200, TUG_LEAVE_AT = 400, TUG_CHASE_TICKS = 24_000, TUG_CAPTURE_TICKS = 600, TUG_PUSH_TICKS = 144_000,
            TUG_RETURN_TICKS = 12_000, TUG_HITS = 3, ASTEROID_PARK_GAP = 30;

    public static final int ASTEROID_TARGET_R = 1_000, ASTEROID_FALL_TICKS = 12_000, ASTEROID_ENTRY_ALT = 9_000, ASTEROID_CUT_LEAD = 800, ASTEROID_CUT_AFTER = 400,
            ASTEROID_INT_WINDOW = 3_600, ASTEROID_INT_R = 2_000, ASTEROID_INT_EVERY = 600, ASTEROID_MAX_SPLITS = 3, ASTEROID_SCATTER_MIN = 100,
            ASTEROID_SCATTER_MAX = 200, ASTEROID_WAVE_REACH = 2_500, ASTEROID_DUST_R = 3_000, ASTEROID_DUST_TICKS = 6_000, ASTEROID_SEA_WAVE_R = 600;
    public static final double ASTEROID_SEA_WAVE_SPEED = 1.0;

    public static final int ASTEROID_SNAP_R = 384, ASTEROID_SNAP_FAR_R = 1024;

    public static final int RG_SNAP_DISK_INFLIGHT = 48, RG_SNAP_DISK_PER_TICK = 32;

    public static final double CUT_DROP_FOG_START = 0.9;

    public static final int ROD_SNAP_R = 320, ROD_SNAP_FAR_R = 1024;

    public static final int ROD_LASERS = 8, ROD_LASER_WINDOW = 3_600, ROD_LASER_UNITE = 200;

    public static final int STATION_CAM_TICKS = 12_000, STATION_CAM_TURN = 1_200;

    public static final int ROD_RELEASE_CAM_TICKS = 240, ROD_RELEASE_CAM_R = 300, ROD_AWAY_TICKS = 200;

    public static final int ASTEROID_DROP_CAM_TICKS = 300, ASTEROID_SHADOW_TICKS = 600, ASTEROID_SKY_TICKS = 1_200;
    public static final double ASTEROID_QUAKE_SPEED = 60.0;

    public static final int ASTEROID_SMOKE_EVERY = 6, ASTEROID_SMOKE_TICKS = 3_600, ASTEROID_SMOKE_MAX = 900, ASTEROID_CURTAIN_TICKS = 260,
            ASTEROID_DOME_TICKS = 400;
    public static final double ASTEROID_CURTAIN_R = 320.0;
    public static final double STATION_CAM_R = 34.0, STATION_CAM_R_BAY = 52.0;
    public static final double ROD_LASER_R = 150.0, ROD_LASER_SKY = 320.0;

    public static final double RAMP_MSPT = 40.0, RAMP_LOADED = 0.9;
    public static final int RAMP_STEP_CHUNKS = 120;

    public static final double FAST_CRATER_R = 60.0, BIG_HOLD_R = 96.0;

    public static final double WORK_MS_MAX = 18.0, WORK_MS_MIN = 3.0, WORK_MSPT_LOW = 25.0, WORK_MSPT_HIGH = 50.0;
    public static final int WORK_CAP_MUL = 3;

    public static final double CRATER_WET_SHARE = 0.3;

    public static final int HOLD_RELEASE_PER_TICK = 10;

    public static final int RG_SNAP_MAX_DEPTH = 64;

    public static final int RG_SNAP_WALL_MAX = 160;

    public static final double CUT_CRATER_FOG_START = 0.8;

    public static final int ROD_ARM_TICKS = 400, ROD_LOAD_TICKS = 1_200, ROD_SWING_TICKS = 1_200, ROD_SPIN_TICKS = 1_200, ROD_FINAL_TICKS = 2_000,
            ROD_SILENCE_TICKS = 800, ROD_FALL_TICKS = 400, ROD_COOL_TICKS = 12_000, ROD_BACK_TICKS = 400, ROD_ABORT_COOL_TICKS = 1_200,
            ROD_SHOT_FE = 4_000_000, ROD_TARGET_R = 1_000;

    public static final int ROD_SHAFT_R = 7, ROD_WAVE_REACH = 1_800, ROD_FALL_ALT = 1_500, ROD_CUT_AFTER = 320, ROD_BREAK_PIECES = 3, ROD_BREAK_TIER = 3,
            ROD_BREAK_SPREAD = 70;
    public static final int REBOOST_BLOCKS_PER_CELL = 4_000, REBOOST_TICKS_PER_1000 = 400, REBOOST_MAX = 20_000, FUEL_CELLS_PER_CANISTER = 2;

    public static final int PLANET_NEAR_PX = 1_024, PLANET_NEAR_BLOCKS = 2, PLANET_FAR_PX = 512, PLANET_FAR_BLOCKS = 32, PLANET_READS_PER_TICK = 24, PLANET_TILES_PER_SEND = 6;
    public static final double PLANET_ALT = 1_500.0;
    public static final double ORBIT_GRAVITY = 0.2, EVA_THRUST_UP = 0.035, EVA_MAX_UP = 0.5, EVA_THRUST_FWD = 0.03, EVA_MAX_SPEED = 0.8, EVA_BRAKE = 0.88;
    public static final int RES_RAILGUN_TICKS = 24_000, RES_RAILGUN_FE = 30_000;

    public static final int FF_BUFFER_FE = 50_000_000, FF_CHARGE_IN = 40_000, FF_HOLD_DIV = 4, FF_R_MIN = 32, FF_R_MAX = 128, FF_R_STEP = 16, FF_R_DEFAULT = 96,
            FF_BOOT_PCT = 25, FF_BOOT_TICKS = 220;

    public static final int FF_HIT_FE = 1_000_000, FF_DRAIN_PER_R2 = 2_500, FF_LOW_PCT = 20;

    public static final int FF_CRACK_TICKS = 80, FF_SHARD_TICKS = 160, FF_DOWN_TICKS = 24_000, FF_REBOOT_PCT = 50;

    public static final int FF_FX_RANGE = 1_024;

    public static final int SW_CELLS = 49, SW_PACK = 7, SW_RANGE = 1_500, SW_SPREAD = 24, SW_RIPPLE = 2, SW_RISE = 16, SW_CRUISE = 28, SW_BUFFER_FE = 4_000_000,
            SW_CHARGE_IN = 20_000, SW_SALVO_FE = 2_000_000;
    public static final double SW_SPEED = 1.6, SW_DIVE = 1.8;

    public static final double SW_VIEW = 1024.0;
    public static final float SW_BLAST = 2.5F;

    public static final int SW_CIWS_RANGE = 600, SW_CIWS_ROUNDS = 40, SW_CIWS_FE = 4_000, SW_CIWS_GAP = 6, SW_LASER_TICKS = 3;
    public static final double SW_CIWS_HIT = 0.7;

    public static final int DH_COUNTDOWN = 2_400, DH_MAX_PADS = 8, DH_LINK_RANGE = 2_000, DH_DOOR_TICKS = 20;

    public static final int DH_RADAR_WAIT = 1_200;

    public static final int LD_RANGE = 1_000, LD_BUFFER_FE = 3_000_000, LD_CHARGE_IN = 60_000, LD_FIRE_FE = 50_000, LD_CHARGE_TICKS = 10, LD_COOL_TICKS = 30,
            LD_BOMB_TICKS = 20;
    public static final float LD_DMG = 15.0F;
    public static final double LD_RAIN = 0.5, LD_AZ_RATE = 12.0, LD_EL_RATE = 8.0;

    public static final int LD_OPTICAL_R = 400;

    public static final int NANITE_R = 24, NANITE_TICKS = 2_400, NANITE_TRIES = 96, NANITE_EAT = 24;

    public static final int NANITE_DISSOLVE = 30, EXOTIC_FX_RANGE = 320;
    public static final float NANITE_DMG = 2.0F;

    public static final int GRAV_R = 30, GRAV_TICKS = 600, GRAV_LIFT_H = 6, GRAV_BLOCKS = 400;
    public static final double GRAV_PULL = 0.12, GRAV_MAX_V = 1.2, GRAV_THROW = 4.5, GRAV_THROW_UP = 1.3;
    public static final float GRAV_THROW_DMG = 14.0F;

    public static final int CRYO_R = 40, CRYO_TICKS = 1_200, CRYO_SPREAD = 60, CRYO_THAW_HEAT = 400, CRYO_SLOW = 1;
    public static final double FF_SOUND_SPEED = 17.0;

    public static final double SUBMERGED_MARGIN = 0.5, UNDERWATER_MARGIN = 1.0, SUB_IMPLODE_REACH = 6.0, SUB_IMPLODE_POWER = 12.0, SUB_IMPLODE_MIN_POWER = 6.0,
            SUB_IMPLODE_PER_DEPTH = 0.1, NOISE_FLOOR_DB = 20.0, WATER_DRAG = 0.92, SINK_SPEED = 0.4,
            WATER_TOUCH = 0.25;
    public static final int NOISE_TTL = 40, NOISE_MAX = 512;

    public static final int[] DEEP_HULL_RATING = { 200, 400 };
    public static final float[] DEEP_HULL_CRACK = { 0.05F, 0.03F };
    public static final double[] DEEP_HULL_IMPLODE_BONUS = { 10.0, 20.0 };
    public static final int DEEP_VIEWPORT_RATING = 200, DEEP_FITTING_RATING = 400, DEEP_HULL_RECHECK_TICKS = 200;

    public static final double TORP_NET_TEAR = 2.5, TORP_NET_SHIP_SAFE = 4.0, TORP_NET_T4_HOLE = 1.2;
    public static final int NET_AREA_PER_ITEM = 4, NET_MAX_LEN = 32, NET_MAX_DEPTH = 16, NET_LAYER_GAP = 3;
    public static final double NET_AIM_RANGE = 48.0, NET_FAR = 28.0;
    public static final float DEEP_VIEWPORT_CRACK = 0.05F, DEEP_FITTING_CRACK = 0.05F;

    public static final double ANECHOIC_MAX_DB = 15.0, ANECHOIC_PING_CUT = 0.40, SONAR_DOME_RANGE_UP = 3_000.0, SONAR_ARRAY_RANGE_UP = 6_000.0, SONAR_FIX_ERR_UP = 0.015;
    public static final int ANECHOIC_RATING = 150, ANECHOIC_RECHECK_TICKS = 600, ANECHOIC_SCAN_MAX = 400_000;
    public static final float ANECHOIC_CRACK = 0.05F;

    public static final int SONAR_DOME_FE = 400, SONAR_ARRAY_FE = 600, SONAR_CONSOLE_FE = 100, SONAR_SCAN_TICKS = 20, SONAR_PING_FE = 100_000, SONAR_PING_COOLDOWN = 200,
            SONAR_PING_SHOW = 600, SONAR_PING_TICKS = 40, SHIP_NOISE_TICKS = 20, SONAR_WATERFALL_ROWS = 40, SONAR_KEEP = 24;

    public static final int SONAR_HULL_TICKS = 200, SONAR_HULL_LEAKS = 4;

    public static final int HULL_SCAN_MAX = 250_000, HULL_SCAN_CELLS = 20_000, HULL_HOLE_JOIN = 3;
    public static final double SONAR_HULL_WARN = 0.9;
    public static final double SONAR_DOME_RANGE = 2_000.0, SONAR_ARRAY_RANGE = 4_000.0, SONAR_PING_RANGE = 1_500.0, SONAR_PING_DB = 140.0, SONAR_FIX_MIN_DEG = 15.0,
            SONAR_FIX_ERR = 0.03, SONAR_LAYER_DEPTH = 60.0, SONAR_LAYER_DB = 15.0, SHIP_NOISE_BASE = 75.0, SHIP_NOISE_PER_SPEED = 6.0, SHIP_NOISE_SPEED_MAX = 30.0,
            SHIP_NOISE_SILENT = 55.0, SHIP_NOISE_REF_BLOCKS = 200.0, SILENT_SPEED = 2.0,
            STOPPED_SPEED = 0.2, DEPTH_HOLD_SPEED = 0.5, SONAR_WRECK_MERGE = 12.0;

    public static final double[] TORP_SPEED = { 1.0, 0.9, 0.8, 4.0, 0.4, 1.6 }, TORP_RANGE = { 400.0, 500.0, 600.0, 1_000.0, 1_500.0, 160.0 };

    public static final float[] TORP_POWER = { 8.0F, 9.0F, 10.0F, 12.0F, 0.0F, 0.0F };

    public static final double TORP_T4_DB = 130.0, UUV_DB = 70.0, UUV_RANGE = 1_500.0;

    public static final double T4_SEARCH_DIST = 80.0, T4_SEARCH_AT = 0.8, T4_SEARCH_SPEED = 1.0, T4_SEEK_RANGE = 100.0, T4_SEEK_CONE = 60.0,
            T4_TURN_RATE = 8.0, T4_SPRINT_TURN = 3.0, T4_PING_DB = 120.0;
    public static final int T4_SEARCH_TICKS = 40;

    public static final double UW_HURT_PER_POWER = 1.0, UW_SHALLOW = 10.0, UW_MID = 40.0;

    public static final double UW_CRATER_PER_POWER = 0.5, UW_CRATER_DEPTH = 0.8;
    public static final float UW_DMG_PER_POWER = 3.0F;
    public static final int UW_FX_RANGE = 512;
    public static final int UUV_LIFE = 12_000;
    public static final double TORP_ARM = 15.0, TORP_KEEL_DEPTH = 6.0, TORP_KEEL_SIDE = 1.0, TORP_MIN_DEPTH = 2.0, TORP_MAX_VY = 0.15, TORP_DB = 105.0,
            TORP_REACH = 10.0, DECK_TRAIN_SPEED = 3.0, DECK_TRAIN_MAX = 150.0, DECK_DROP_SPEED = 0.5, DECK_DEPTH = 4.0, TORP_MAX_DEPTH = 120.0;
    public static final float TORP_ENTITY_POWER = 4.0F;
    public static final int TUBE_FE = 40, TUBE_CHARGE_TICKS = 100, TUBE_RELOAD_TICKS = 200, DECK_FE = 40, DECK_CHARGE_TICKS = 60, TORP_RACK_FE = 20, TORP_NOISE_TICKS = 20;

    public static final int[] TLINE_RUN_TICKS = { 1200, 1600, 2000, 2400, 1800, 900 };
    public static final int TLINE_FE_PER_TICK = 100, TLINE_BUFFER_FE = 250_000, TLINE_RECEIVE_FE = 10_000, TLINE_RACK = 4,
            TLINE_LOAD_TICKS = 40, TLINE_TRAVEL_TICKS = 80, TLINE_MATE_TICKS = 60, TLINE_HOME_TICKS = 80;
    public static final double[] TLINE_STATION_SHARE = { 0.31, 0.34, 0.23, 0.12 };

    public static final double GYRO_STRAIGHT = 10.0, GYRO_RATE = 6.0, GYRO_MAX = 90.0, TORP_KEEL_RUN = 4.0, TORP_SUB_EXTRA = 2.0, FCC_TORP_RANGE = 800.0;
    public static final int FCC_FE = 100, FCC_TICKS = 10, SALVO_FAN = 3, FCC_ROWS = 6;

    public static final double TORP_SEEK_RANGE = 200.0, TORP_SEEK_CONE = 50.0, TORP_ACTIVE_RANGE = 48.0, TORP_TURN_RATE = 5.0, TORP_PING_DB = 95.0,
            TORP_WIRE_LEN = 450.0, TORP_DECOY_DB = 120.0;
    public static final int TORP_SEEK_TICKS = 5, TORP_LOST_TICKS = 60, TORP_DECOY_TICKS = 600;

    public static final int DECOY_MAG = 4, DECOY_FE = 20, DECOY_CHARGE_TICKS = 40, DECOY_SCAN_TICKS = 10;
    public static final double DECOY_AUTO_RANGE = 48.0;

    public static final double DC_SINK = 0.4, DC_DEPTH_MIN = 4.0, DC_DEPTH_MAX = 120.0, DC_DEPTH_DEFAULT = 15.0, DC_REACH = 8.0;
    public static final float DC_POWER = 6.0F, DC_ENTITY_POWER = 3.0F;

    public static final int DC_SOURCE_PASS_TICKS = 20;
    public static final int DC_ARM_TICKS = 10, DC_RACK_FE = 40, DC_RELEASE_TICKS = 10, DC_PATTERN_TICKS = 20;

    public static final int SEAMINE_ARM_TICKS = 400, MINE_SCAN_TICKS = 4, MINE_SYMPATHY_TICKS = 6;
    public static final double SEAMINE_DEPTH_MIN = 2.0, SEAMINE_DEPTH_MAX = 120.0, SEAMINE_DEPTH_DEFAULT = 6.0, SEAMINE_DEPTH_CAPTOR = 30.0, SEAMINE_CABLE_MIN = 1.5,
            SEAMINE_CABLE_MAX = 120.0, MINE_INF_TRIGGER = 8.0, MINE_INF_REACH = 16.0, MINE_BOT_SIDE = 6.0, MINE_BOT_LISTEN = 24.0, MINE_BOT_SPEED = 0.8,
            MINE_BOT_PROX = 2.0, MINE_CAP_RANGE = 48.0, MINE_SYMPATHY_PER_POWER = 0.75;
    public static final float MINE_INF_POWER = 9.0F;

    public static final double MINE_LAY_REACH = 1.6, MINE_LAY_THROW = 0.25, MINE_LAY_SINK = 0.25, MINE_LAY_HULL_CHECK = 24.0, MINE_LAY_RETURN_RANGE = 64.0,
            MINE_LAY_PAYOUT = 0.3;
    public static final int MINE_LAY_MIN_WATER = 8, MINE_LAY_MAX_TICKS = 2400, MINE_LAY_COOLDOWN = 20, MINE_LAY_PAYOUT_DELAY = 10;
    public static final int[] MINE_LAY_DEPTHS = { 3, 6, 10, 15, 20, 30, 45, 60, 90 };

    public static final double KG_SPEED = 1.05;
    public static final int KG_FE = 40, KG_FIRE_TICKS = 10, KG_RELOAD_TICKS = 60;

    public static final double SONOBUOY_RANGE = 1_000.0;
    public static final int SONOBUOY_TICKS = 12_000, SONOBUOY_EAR_DEPTH = 30;

    public static final double TOWED_RANGE = 3_000.0, TOWED_LENGTH = 48.0, TOWED_ARRAY_LEN = 16.0, TOWED_STREAM_SPEED = 0.2, TOWED_SINK = 6.0, TOWED_MIN_OUT = 0.75,
            TOWED_DEAF_SPEED = 6.0, TOWED_DEAF_TURN = 3.0, NODE_RANGE = 1_500.0, NODE_LOW = 0.10, NODE_WAKE = 0.25,
            COASTAL_PICK = 48.0, COASTAL_SKIM_ALT = 4.0, COASTAL_LAUNCH_DB = 90.0, CRANE_DROP = 16.0, CRANE_SLEW_SPEED = 1.5, CRANE_LUFF_SPEED = 0.6, CRANE_HOOK_SPEED = 0.12;
    public static final float COASTAL_TRAIN_SPEED = 1.5F;
    public static final int TOWED_FE = 300, TOWED_SETTLE_TICKS = 200, NODE_FE = 16, NODE_TURBINE_STILL = 8, NODE_TURBINE_FLOW = 24, COASTAL_FE = 300,
            COASTAL_RAISE_TICKS = 40, COASTAL_SALVO_GAP = 30, COASTAL_HOLD_TICKS = 100, CRANE_FE = 120, CRANE_LATCH_TICKS = 20, CRANE_SCAN_TICKS = 40;
    public static final long NODE_BATTERY = 1_152_000L;

    public static final double LC_MAX_DEPTH = 40.0, ASROC_MIN_RANGE = 60.0, ASROC_RANGE = 1_200.0, ASROC_RISE = 0.8, ASROC_SPEED = 3.0;

    public static final double ASROC_CHUTE_SPEED = 0.35, ASROC_CHUTE_DRIFT = 0.1, ASROC_SEP_ALT = 18.0;

    public static final double ASROC_STANDOFF = 80.0;
    public static final int ASROC_HANG_TICKS = 12;
    public static final int LC_FE = 60, LC_OPEN_TICKS = 30;

    public static final double SLBM_RANGE_K = 0.8, SLBM_MIN_RANGE = 500.0, SLBM_RISE = 0.7;
    public static final int SLBM_HANG_TICKS = 14, SLBM_POP_AT = 3, SLBM_SPIKE_TICKS = 6, SLBM_CLIMB_TICKS = 50;

    public static final double CAPSULE_RISE = 0.6, CAPSULE_POP = 1.5, CAPSULE_SINK = 0.08;
    public static final int CAPSULE_HANG_TICKS = 8, CAPSULE_SINK_TICKS = 200, LC_CAPSULES = 4;

    public static final int NODULE_MIN_WATER = 24, NODULE_R_MIN = 4, NODULE_R_MAX = 8;

    public static final int NODULE_RETRO_PER_TICK = 2, NODULE_RETRO_RARITY = 4;
    public static final double NODULE_DENSITY = 0.55;
    public static final int DREDGE_FE = 30, DREDGE_TILT_TICKS = 40, DREDGE_GRAB_TICKS = 30, DREDGE_PAUSE_TICKS = 40, DREDGE_REACH = 3, DREDGE_CABLE = 128,
            DREDGE_YIELD_MIN = 2, DREDGE_YIELD_MAX = 3, DREDGE_SLOT = 64;
    public static final double DREDGE_LOWER = 0.25, DREDGE_HAUL = 0.15;
    public static final int RIG_FE = 120, RIG_SEP_TICKS = 100, RIG_SEP_IN = 4, RIG_MN_OUT = 3, RIG_NICO_OUT = 1, RIG_HOPPER = 64, RIG_RISER = 160;
    public static final int CRAWLER_RADIUS = 24, CRAWLER_COLLECT_TICKS = 20, CRAWLER_YIELD_MIN = 3, CRAWLER_YIELD_MAX = 4, CRAWLER_PATH_NODES = 1500,
            CRAWLER_SCAN_ROWS = 4;
    public static final double CRAWLER_SPEED = 0.12, CRAWLER_SINK = 0.3;

    public static final int SURVEY_FE = 100_000, SURVEY_RANGE = 256, SURVEY_KEEP_TICKS = 72_000, SURVEY_MAX_MARKS = 64, SURVEY_JOIN = 12, SURVEY_HINT_TICKS = 100;

    public static final int NR_FE_PER_TICK = 2_500, NR_FUEL_SLOTS = 2, NR_SPENT_SLOTS = 2, NR_FUEL_TICKS = 288_000, NR_BUFFER_FE = 1_000_000, NR_OUT_PER_TICK = 10_000,
            NR_WATER_TANK_MB = 4_000, NR_QUIET_PCT = 30, NR_DECAY_TICKS = 1_200, NR_MELTDOWN_TIER = 1;
    public static final int[] NR_LEVELS = { 0, 30, 50, 75, 100 };

    public static final int NR_SEA_FILL_MB = 40, NR_LEAK_MB = 10, NR_TANK_COOL_PCT = 25;

    public static final int DC_MAX_FE_PER_TICK = 2_500, DC_BUFFER_FE = 10_000, DC_WINDOW_TICKS = 20, DC_MIN_FE_PER_TICK = 50, DC_RPM_DEFAULT = 64;
    public static final int[] DC_RPMS = { 16, 32, 64, 128 };
    public static final float DC_SU_PER_FE = 32_768.0F / 2_500.0F;
    public static final double NR_CRACK_REACH = 2.5;
    public static final double NR_WATER_MB = 4.0, NR_HEAT_MAX = 1_000.0, NR_HEAT_SCRAM = 900.0, NR_HEAT_ALARM = 600.0, NR_HEAT_STEAM = 800.0, NR_HEAT_OPERATING = 300.0,
            NR_HEAT_RISE = 0.6, NR_HEAT_FALL = 1.0, NR_DECAY_RISE = 0.2, NR_PUMP_DB = 80.0, NR_PUMP_DB_FULL = 88.0;

    public static final int PERI_FE = 20, PERI_RAISE_TICKS = 40, MAST_BEAT = 20, MAST_TTL = 60;
    public static final float PERI_STAGE_PX = 28.0F;

    public static final double PERI_AIR_RANGE = 300.0;

    public static final double STORE_DROP_KICK = 0.15;

    public static final int ATORP_CHUTE_DELAY = 10, ATORP_MAX_AIR_TICKS = 2400;
    public static final double ATORP_CHUTE_FALL = 0.5, ATORP_CHUTE_DRIFT = 0.1;
    public static final double PERI_HEAD_UP = 3.6, PERI_ZOOM_LOW = 1.5, PERI_ZOOM_HIGH = 6.0, PERI_REACH = 3.0, PERI_WAKE_SPEED = 0.08;

    public static final double PERI_EYE_AHEAD = 0.3;

    public static final int BUOY_FE = 15;
    public static final double COMMS_DEPTH = 10.0, BUOY_CABLE = 64.0, BUOY_RISE = 0.3, BUOY_MAX_SPEED = 8.0, BUOY_DB = 70.0;

    public static final int ATT_SCAN_TICKS = 4, ATT_CLAIM_TICKS = 160;
    public static final double ATT_DETECT = 120.0, ATT_CPA = 24.0, ATT_TURN = 15.0, ATT_SEEK = 80.0, ATT_ARM = 4.0, ATT_FUSE = 3.0, ATT_DB = 95.0;
    public static final float ATT_BLAST = 2.5F;

    public static final int POD_SEATS = 4, POD_FE = 5, POD_CHECK_TICKS = 10, POD_FLOAT_TICKS = 6_000;
    public static final double POD_REACH = 3.0, POD_RISE = 0.35, POD_FALL = 0.12, POD_DRAFT = 1.6, POD_HEIGHT = 2.8;

    public static final double[] PROP_STEP_FRAC = { -0.6, -0.4, -0.2, 0.0, 0.25, 0.5, 0.8, 1.0 };
    public static final int PROP_STEP_STOP = 3, PROP_BEAT = 20, PROP_INPUT_TTL = 60, PROP_REPLY_DELAY = 16;
    public static final double PROP_RUDDER_MAX = 35.0, PROP_PLANES_MAX = 25.0, PROP_SURFACE_RATE = 20.0, PROP_RUDDER_WASH = 0.35, PROP_RUDDER_SPEED = 2.0,
            PROP_PLANES_WASH = 0.30, PROP_PLANES_SPEED = 1.5;

    public static final double SCREW_THRUST = 150.0, SCREW_PITCH_MAX = 32.0, SCREW_PITCH_MIN = -24.0, SCREW_PITCH_RATE = 15.0,
            SCREW_DB_IDLE = 70.0, SCREW_DB_LOAD = 30.0, SCREW_DB_CAV = 15.0, SCREW_SU_PER_FE = 16.0;
    public static final int SCREW_FE = 20, SCREW_FE_LOAD = 230, SCREW_SPOOL_UP = 60, SCREW_SPOOL_DOWN = 80, SCREW_DECLUTCH = 200;

    public static final double AIR_PRESSURE_K = 0.004, AIR_PRESSURE_MAX = 1.2, AIR_TOP_FADE = 40.0;

    public static final int AIR_CEILING_DEFAULT = 1100;
    public static final double AIR_K_REF = 0.0015, AIR_MAX_P = 1.5, AIR_CEIL_FADE = 60.0, JET_AIR_EXP = 1.25;

    public static final double AIR_SEA_BRIDGE = 50.0;

    public static final double CIWS_JET_RANGE = 600.0, AIRGUN_SIZE_MIN = 0.4, AIRGUN_SIZE_MAX = 1.5, AIRGUN_SPEED_REF = 30.0, AIRGUN_SPEED_MIN = 0.25,
            AIRGUN_CHEW_PAST = 12.0;
    public static final int CIWS_JET_ROUNDS = 20, CIWS_JET_BURST_TICKS = 4, CIWS_JET_MAX_BLOCKS = 4;

    public static final double FLAK_JET_REACH = 20.0;
    public static final int FLAK_JET_FRAGS = 12, FLAK_JET_MAX_BLOCKS = 3;

    public static final double FC_REACH = 64.0, FC_GRAVITY = 9.81, FC_LIFT_K = 3.5, FC_CRUISE_AOA = 10.0, FC_CRUISE_SHARE = 0.8, FC_TW_MIN = 0.25, FC_TW_GOOD = 0.5,
            FC_FUEL_WARN_S = 120.0;
    public static final int FC_REFRESH_TICKS = 20;

    public static final int REC_MAX_BLOCKS = 32_768, REC_REFRESH_TICKS = 40, REC_RELINK_SAMPLE = 400, REC_SHOW_MAX = 2_000, REC_SHOW_TICKS = 600;
    public static final double REC_RELINK_MATCH = 0.6;

    public static final int CRAFT_SCAN_MAX = 2_000_000, REC_ADDED_CACHE = 100, SCREEN_ASK_TICKS = 4;

    public static final double SCANNER_RANGE = 24.0, SCANNER_PARKED = 0.2, SCANNER_DOOR_OPEN = 100.0, SCANNER_ARM_DOWN = -90.0, SCANNER_YAW_DEG_PER_TICK = 6.0,
            SCANNER_FAN_LEN = 6.0, SCANNER_FAN_DEG = 50.0;
    public static final int SCANNER_FE = 40, SCANNER_CYCLE = 120, SCANNER_DOORS = 10, SCANNER_ARM = 30, SCANNER_RESULT_AT = 90, SCANNER_RESCAN_TICKS = 1200;

    public static final int GANTRY_FE = 800, GANTRY_LEN_MIN = 3, GANTRY_LEN_MAX = 64, GANTRY_SPAN_MIN = 5, GANTRY_SPAN_MAX = 50, GANTRY_RAIL_UP = 17,
            GANTRY_REFRESH = 100, GANTRY_WELD_TICKS = 24, GANTRY_ENGINE_TICKS = 60, GANTRY_RELOAD_TICKS = 30, GANTRY_TOTE = 8, GANTRY_FESTOONS = 6;
    public static final double GANTRY_PARKED = 0.2, GANTRY_BRIDGE_SPEED = 0.12, GANTRY_TROLLEY_SPEED = 0.15, GANTRY_DOOR_OPEN = 100.0, GANTRY_HATCH_OPEN = 100.0;
    public static final float GANTRY_MAST_SPEED = 0.15F, GANTRY_ARM_SPEED = 4.0F, GANTRY_MAST_MAX = 180.0F;

    public static final double GANTRY_HANDOVER_OUT = 0.6;

    public static final int WELD_FX_HOLD = 3, WELD_FX_AFTERGLOW = 20, WELD_FX_SPARKS = 3;
    public static final double WELD_FX_RANGE = 96.0;

    public static final int GPOD_ROUNDS = 2_000, GPOD_BOX = 100,
            GPOD_FE = 40, GPOD_SPIN_UP = 8, GPOD_ROUNDS_PER_BURST = 6, GPOD_BURST_TICKS = 2, GPOD_MAX_BLOCKS = 3,
            GPOD_SOUND_TICKS = 8, GPOD_HATCH_TICKS = 30, GPOD_HOLD_MAX = 10;
    public static final double GPOD_RANGE = 600.0, GPOD_CONE = 1.5, GPOD_BARREL_RATE = 1.6;

    public static final int RPOD_TUBES = 7, AIR_ROCKET_CRAFT = 4, RPOD_RIPPLE_TICKS = 3, RPOD_FE = 200, RPOD_OPEN_TICKS = 8, RPOD_CLOSE_TICKS = 60,
            RPOD_LOAD_TICKS = 6, RPOD_BOOST = 6, RPOD_BURN = 24, RPOD_LIFE = 180, RPOD_MAX_PER_OWNER = 48, RPOD_MAX_PER_DIM = 160, RPOD_HOLD_TICKS = 6;
    public static final double RPOD_KICK = 1.5, RPOD_SPEED = 7.0, RPOD_DRAG = 0.015, RPOD_G_BURN = 0.012, RPOD_SPREAD = 0.8, RPOD_ARM = 15.0,
            RPOD_RANGE_MIN = 40.0, RPOD_RANGE_MAX = 700.0;

    public static final int RPOD_LAUNCH_SMOKE = 1;

    public static final int RPOD_CHUNK_LOADS = 6;
    public static final double RPOD_AIR_CLEAR = 24.0;
    public static final double RPOD_LAUNCH_SMOKE_SIZE = 0.3, RPOD_TRAIL_SIZE = 0.45;
    public static final double AIRCRAFT_MAX_SPEED = clampFlag("wmd.aircraftMaxSpeed", 90.0, 20.0, 400.0), AIRCRAFT_CAP_FADE = 10.0;

    static double clampFlag(String key, double def, double lo, double hi) {
        try {
            String s = System.getProperty(key);
            double v = s == null ? def : Double.parseDouble(s.trim());
            return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : def;
        } catch (RuntimeException e) {
            return def;
        }
    }
    public static final double EDF_THRUST = 1_200.0, EDF_AIRFLOW = 40.0, EDF_VANE_MAX = 25.0, EDF_VANE_ROLL = 18.0, EDF_VANE_RATE = 100.0, EDF_VANE_SIDE = 0.3;
    public static final int EDF_FE = 15, EDF_FE_LOAD = 185, EDF_SPOOL_UP = 30, EDF_SPOOL_DOWN = 50, EDF_COAST = 80;
    public static final double TF_THRUST = 3_600.0, TF_AIRFLOW = 90.0, TF_IDLE_N1 = 0.2, TF_FUEL_IDLE = 0.08, TF_FUEL_FULL = 0.42, TF_NOZZLE_MAX = 10.0, TF_NOZZLE_RATE = 33.0;
    public static final int TF_STARTER_FE = 400, TF_START_TICKS = 40, TF_SPOOL_UP = 60, TF_SPOOL_DOWN = 100, TF_SHUTDOWN = 200, TF_TANK = 4000;

    public static final double JET1_THRUST = 2_400.0, JET1_AB = 4_800.0, JET2_THRUST = 4_800.0, JET2_AB = 9_600.0, JETS_THRUST = 4_000.0;
    public static final double JET_FUEL_PER_KT = 0.42 / 3.6, JET_FUEL_IDLE_SHARE = 0.2, JET_AB_FUEL = 3.0, JET_AIRFLOW = 95.0, JET_FLOW_EXP = 3.0, JET_DIAMOND_START = 0.35, JET_DIAMOND_GAP = 0.5, JET_DIAMOND_R = 0.2, JET_VEC_MAX = 15.0,
            JET_VFLAP_MAX = 8.0, JET_NOZZLE_RATE = 40.0, JETS_HEAT = 0.5;
    public static final int JET_STARTER_FE = 300, JET_START_TICKS = 40, JET_SPOOL_UP = 50, JET_SPOOL_DOWN = 90;

    public static final int ENGINE_HP = 100;
    public static final double ENGINE_SMOKE = 0.6, ENGINE_FIRE = 0.3, ENGINE_SMOKE_THRUST = 0.85, ENGINE_FIRE_THRUST = 0.5, ENGINE_REPAIR_SPEED = 1.0,
            ENGINE_BLAST_DMG = 15.0, ENGINE_BLAST_PAD = 2.0, ENGINE_RAY_DMG = 6.0, ENGINE_RAY_REACH = 1.0, CONTRAIL_Y = 180.0, CONTRAIL_SPEED = 30.0,
            CONTRAIL_AB_SIZE = 1.3, CONTRAIL_BACK = 7.0, ENGINE_SMOKE_SIZE = 0.25, ENGINE_FIRE_SMOKE_SIZE = 0.4;

    public static final double BELLY_MAX = 20.0, BELLY_FADE = 5.0, BELLY_GAP = 1.2;

    public static final double CRASH_TRIGGER = 8.0, CRASH_MIN = 15.0, CRASH_R_MIN = 1.0, CRASH_R_PER = 0.08, CRASH_R_MAX = 4.5, CRASH_ENGINE_DMG = 2.0,
            CRASH_HURT_PER = 0.5, CRASH_HURT_MIN = 0.0, GEAR_SINK_MAX = 12.0;
    public static final int CRASH_COOLDOWN = 10, CRASH_MAX_BLOCKS = 80, CRASH_SCAN_MAX = 200_000;

    public static final double CRASH_TOUCH = 1.5;
    public static final int BELLY_CHECK = 10;
    public static final int CONTRAIL_EVERY = 2;

    public static final double LIFT_THRUST = 6_000.0, LIFT_ENGINE_SHARE = 0.7, LIFT_LEVEL_K = 8.0, LIFT_LEVEL_D = 4.0, LIFT_LEVEL_MAX = 10.0,
            LIFT_LOUVRE_TILT = 20.0, LIFT_LOUVRE_RATE = 60.0, LIFT_LOUVRE_SIDE = 1.0, LIFT_LOUVRE_OPEN = 80.0;
    public static final int LIFT_SPOOL_UP = 40, LIFT_SPOOL_DOWN = 60;
    public static final double CHUTE_MIN_SPEED = 6.0, CHUTE_DRAG_K = 0.012, CHUTE_MAX_DECEL = 14.0, CHUTE_RELEASE_SPEED = 2.0, CHUTE_SCALE = 1.4,
            CHUTE_TRAIL = 7.0, CHUTE_LID_OPEN = -110.0;
    public static final int CHUTE_REQUEST_TICKS = 40, CHUTE_MAX_TICKS = 400, CHUTE_OPEN_TICKS = 12;

    public static final double CHUTE_RIP_SPEED = 50.0;

    public static final double[] HELI_LIFT = { 4_000.0, 7_000.0, 10_000.0 };

    public static final double HELI_LEVEL_K = 8.0, HELI_LEVEL_D = 4.0, HELI_LEVEL_MAX = 10.0;

    public static final double HELI_LEVEL_I = 0.02, HELI_LEVEL_IMAX = 5.0, HELI_UPSET_DEG = 60.0;

    public static final double LIFT_LEVEL_I = 0.02, LIFT_LEVEL_IMAX = 5.0;

    public static final double HELI_TILT_FWD = 35.0;

    public static final double HELI_YAW_DEAD = 1.5;

    public static final double HELI_VY_I = 0.004, HELI_TRIM_DOWN = 0.5, HELI_TRIM_UP = 3.0;
    public static final double HELI_COLL_DEAD = 0.05, HELI_CLIMB = 10.0, HELI_VY_K = 0.12, HELI_YAW_RATE = 40.0, HELI_YAW_K = 1.0 / 30.0, HELI_TAIL_PER_MASS = 12.0;
    public static final double HELI_TILT = 20.0, HELI_HOLD = 400.0, HELI_HOLD_MAX = 0.5, HELI_TANDEM_YAW = 0.15, HELI_TAIL_THRUST = 4_000.0, HELI_PITCH_SHOW = 10.0;
    public static final int HELI_SPOOL_UP = 80, HELI_SPOOL_DOWN = 120, TURBO_SPOOL_UP = 60, TURBO_SPOOL_DOWN = 100, TURBO_FE = 30;

    public static final double TP_THRUST = 2_000.0, TP_AIRFLOW = 45.0, TP_CONTRA = 1.25, TP_CONTRA_FE = 1.5;
    public static final int[] TP_BLADES = { 3, 4, 6 };
    public static final double[] TP_BLADE_SHARE = { 0.9, 1.0, 1.1 };
    public static final int TP_FE = 20, TP_SPOOL_UP = 60, TP_SPOOL_DOWN = 100;
    public static final double TR_THRUST = 3_000.0, TR_AIRFLOW = 55.0, TR_HOVER_DEG = 45.0;
    public static final int TR_FE = 25, TR_TILT_TICKS = 80, TR_SPOOL_UP = 80, TR_SPOOL_DOWN = 120;
    public static final double GT_FUEL_IDLE = 0.1, GT_FUEL_FULL = 0.6;
    public static final int GT_TANK = 8_000, GT_START_FE = 200, GT_START_TICKS = 100, GT_SPOOL_UP = 100, GT_SPOOL_DOWN = 160;
    public static final double WJ_THRUST = 220.0, WJ_ASTERN_SHARE = 0.45, WJ_STEER_MAX = 25.0, WJ_STEER_RATE = 40.0;
    public static final double AZ_THRUST = 260.0, AZ_STEER_MAX = 45.0, AZ_SLEW = 30.0;
    public static final int AZ_FE = 30, AZ_FE_LOAD = 300;
    public static final double BOW_THRUST = 60.0;

    public static final int DOWNWASH_RANGE = 14, STROBE_PERIOD = 24, STROBE_ON = 2, NAV_SCAN = 100;
    public static final double VAPOUR_ACCEL = 28.0, VAPOUR_SPEED = 35.0, TYRE_SMOKE_SPEED = 12.0;

    public static final double TRAIL_G0 = 1.6, TRAIL_G_SPAN = 2.4, TRAIL_SPEED = 30.0, TRAIL_W0 = 0.05, TRAIL_W1 = 0.18, TRAIL_ALPHA = 0.55, TRAIL_RANGE = 320.0;
    public static final int TRAIL_LIFE = 60, TRAIL_FORGET = 40;

    public static final int NL_STROBE_PERIOD = 24, NL_BEACON_PERIOD = 20, NL_SCAN = 20, NL_VIEW = 256, NL_PRESS_GAP = 3;
    public static final float NL_POOL_R = 2.5F, NL_POOL_B = 0.5F, NL_STROBE_R = 9.0F, NL_STROBE_B = 2.5F, NL_BEACON_R = 6.0F, NL_BEACON_B = 1.5F,
            NL_SPOT_ANGLE = 17.0F, NL_SPOT_DIST = 24.0F, NL_SPOT_B = 1.6F, NL_GLINT_FROM = 24.0F, NL_GLINT_FADE = 16.0F, NL_GLINT_PX = 0.0022F,
            NL_GLINT_FLASH = 2.5F, NL_REACH = 8.0F;
    public static final float[] NL_BRIGHT = { 0.5F, 1.0F, 1.6F };

    public static final double[] HOOK_LEN = { 16.0, 32.0 }, HOOK_LIFT = { 4_000.0, 20_000.0 };
    public static final double HOOK_REEL = 4.0, HOOK_MIN_LOADED = 2.5, HOOK_BETA = 0.2, HOOK_G = 9.81, HOOK_MOB_MASS = 80.0, HOOK_CATCH = 1.3;

    public static final double HOOK_BIAS_MAX = 2.0, HOOK_MAX_DV = 1.5;

    public static final double HOOK_SPRING_W = 4.0, HOOK_DAMP = 0.9;

    public static final double HOOK_ENTITY_MAX = 4.0, HOOK_PART = 8.0;
    public static final int HOOK_SCAN = 5;

    public static final double HOSE_LEN = 32.0, HOSE_REEL = 3.0, HOSE_SAG = 0.12, DROGUE_CATCH = 1.2, DROGUE_BREAK = 2.6;
    public static final int REFUEL_RATE = 10, PROBE_TICKS = 30, DROGUE_RED_TICKS = 60;

    public static final int FUEL_TANK_MB = 4_000, DROP_TANK_MB = 12_000, TANK_FEED_MB = 200, DROP_TANK_MAX_TICKS = 600;
    public static final double DROP_TANK_G = 0.04, DROP_TANK_DRAG = 0.98;

    public static final int SA_JET_MB = 16_000, SA_DIESEL_MB = 8_000, SA_FUEL_RATE = 25, SA_GROUND_FE = 2_000, SA_FE_MOVE = 400, SA_FE_PUMP = 100,
            SA_BUFFER_FE = 400_000, SA_RECEIVE_FE = 4_000, SA_PARK_TICKS = 60, SA_SCAN_TICKS = 20, SA_BREAK_COOLDOWN = 100, SA_FULL_TICKS = 40;
    public static final double SA_REACH = 15.0, SA_PARK_MOVE = 0.6, SA_BREAK_DIST = 1.5, SA_BREAK_STEP = 0.4;

    public static final float SA_SLEW = 1.5F, SA_LUFF = 1.125F, SA_KNUCKLE = 2.25F, SA_TELE = 2.25F, SA_WRIST = 3.0F, SA_LID = 0.0225F;

    public static final double SA_BLEND = 0.7;

    public static final double SA_BLEND_PIT = 0.85;

    public static final double SA_FOLLOW = 20.0;
    public static final int BOW_FE = 10, BOW_FE_LOAD = 120, BOW_SPOOL = 30;
    public static final double JATO_THRUST = 14_400.0, JATO_GIMBAL = 8.0, JATO_GIMBAL_RATE = 53.0, JATO_KICK = 0.1;
    public static final int JATO_BURN = 200, JATO_RELEASE_DELAY = 8, JATO_PROP_LIFE = 600;
    public static final float JATO_TUMBLE_DEG = 90.0F;

    public static final double PJ_THRUST = 240.0, PJ_ASTERN_SHARE = 0.4, PJ_VANE_AHEAD = 14.0, PJ_VANE_ASTERN = -22.0, PJ_VANE_RATE = 18.0, PJ_FLAP_MAX = 25.0,
            PJ_VEC_MAX = 7.0, PJ_VEC_RATE = 12.0, PJ_VEC_SIDE = 1.0, PJ_DB_IDLE = 50.0, PJ_DB_LOAD = 25.0;
    public static final int PJ_FE = 30, PJ_FE_LOAD = 1_000, PJ_SPOOL_UP = 80, PJ_SPOOL_DOWN = 120, PJ_DECLUTCH = 200;

    public static final int SD_FE_PER_TICK = 500, SD_FE_PER_MB = 5_000, SD_TANK_MB = 8_000, SD_BUFFER_FE = 200_000, SD_OUT_PER_TICK = 2_000, SD_CRANK_TICKS = 40,
            SD_STALL_TICKS = 60;
    public static final double SD_AUTO_LOW = 0.5, SD_AUTO_HIGH = 0.95, SD_DB = 118.0;

    public static final int SN_FE = 15, SN_RAISE_TICKS = 90, SN_SLAM_TICKS = 12, SN_AUTO_DOWN_TICKS = 100, SN_DEEP_TICKS = 40, SN_AIR_TTL = 4;
    public static final float SN_STAGE1_PX = 26.0F, SN_STAGE2_PX = 22.0F;
    public static final double SN_HEAD_UP = 2.9, SN_AWASH = 0.5, SN_SLAM_CHANCE = 0.25, SN_WAKE_SPEED = 0.08;

    public static final int HELM_FE = 10, HELM_HUD_TICKS = 4, HELM_BALLAST_TICKS = 10;
    public static final double HELM_BALLAST_RATE = 0.05;

    public static final int COCKPIT_FE = 8;

    public static final int COCKPIT_HUD_TICKS = 2, COCKPIT_AXIS_TICKS = 6;

    public static final double COCKPIT_THROTTLE_RATE = 0.5;
    public static final int COCKPIT_AB_HOLD = 10;

    public static final double COCKPIT_LOCK_CONE = 30.0, COCKPIT_LOCK_KEEP = 45.0;

    public static final int COCKPIT_GUN_RETRIGGER = 4, COCKPIT_GUN_HOLD = 6;
    public static final int COCKPIT_RADAR_TICKS = 10;

    public static final double COCKPIT_LEAVE_SPEED = 2.0;

    public static final float COCKPIT_PULLUP_AGL = 60.0F, COCKPIT_PULLUP_TTI = 4.0F;

    public static final int EJECT_HOLD_TICKS = 20, EJECT_ROCKET_TICKS = 6, EJECT_CHUTE_OPEN_TICKS = 16, EJECT_PROP_TICKS = 600, EJECT_MAX_TICKS = 2400;
    public static final double EJECT_KICK = 0.75, EJECT_ROCKET_ACC = 0.11, EJECT_GRAVITY = 0.06, EJECT_CHUTE_FALL = 0.12, EJECT_CANOPY_KICK = 0.9;

    public static final double AP_BANK_MAX = 30.0, AP_ORBIT_BANK_MAX = 50.0, AP_PITCH_MAX = 15.0, AP_LOOKAHEAD = 4.0, AP_MIN_SPEED_K = 1.35,
            AP_MIN_SPEED_FALLBACK = 35.0, AP_STALL_LETGO = 1.05, AP_WP_REACH = 32.0, AP_WP_REACH_S = 1.2, AP_ORBIT_R_MIN = 48.0, AP_ORBIT_R_MAX = 400.0,
            AP_ORBIT_R_DEF = 120.0, AP_ROUTE_RANGE = 4000.0;

    public static final double AP_ORBIT_R_MARGIN = 1.1;

    public static final int AP_LAUNCH_GRACE = 100;
    public static final double AP_LAUNCH_BANK = 15.0;
    public static final float AP_FUEL_MIN = 0.08F;
    public static final int AP_FLOOR = 24, AP_FLOOR_MIN = 12, AP_FLOOR_MAX = 64, AP_WP_MAX = 12, AP_EMPTY_TICKS = 200, AP_OWN_DROP_TICKS = 60,
            AP_STALL_REFRESH = 200, AP_REASON_TICKS = 60, AP_PAN_TICKS = 8, AP_PAN_BACK = 6, AP_QUICK_HOLD = 12, AP_ASK_GAP = 4;
    public static final double AP_K_HDG = 1.5, AP_K_ROLL = 0.05, AP_K_ROLL_D = 0.4, AP_K_YAW = 0.02, AP_YAW_MAX = 0.3, AP_K_HDG_D = 0.5, AP_K_SLIP = 0.015, AP_K_SLIP_D = 0.004, AP_TRACK_MIN_SPEED = 5.0, AP_K_ALT = 0.2, AP_K_PITCH = 0.06,
            AP_K_PITCH_D = 0.3, AP_K_SPD = 0.03, AP_K_SPD_I = 0.002, AP_K_ORBIT = 60.0;

    public static final int AL_RESCAN_TICKS = 100;
    public static final double AP_K_PITCH_I = 0.0015, AP_PITCH_TRIM_MAX = 0.95;

    public static final double AP_PITCH_I_BAND = 15.0, AP_PULLUP_AGL = 25.0, AP_PULLUP_SINK = 4.0, AP_E_BAND = 2.0, AP_K_THR_G = 0.12, AP_K_PITCH_UNWIND = 3.0, AP_STICK_LIMIT = 0.9, AP_STICK_EASE = 0.7, AP_STICK_SHORT = 1.0, AP_STICK_SHORT_MAX = 8.0, AP_STICK_STEADY = 0.05, AP_STICK_MAX_K = 2.2, AP_STICK_ROLL = 35.0, AP_STICK_SPD_ADD = 4.0, AP_STICK_SPD_DECAY = 0.01, AL_GS_UP = 1.0, AL_GS_THR_DEAD = 1.5, AL_K_GS_THR = 0.06, AL_GATE_SPD_FAST = 0.35, AL_GATE_SPD_SLOW = 0.10,
            AL_APPROACH_MAX = 100.0, AL_TERRAIN_R = 600.0, AL_TERRAIN_MARGIN = 30.0, AL_FINAL_MAX = 1200.0, AL_GLIDE_CLEAR = 10.0, AL_GLIDE_CLEAR_D = 300.0, AL_XT_V = 48.0, AL_SCAN_KNOWN = 0.5, AL_STOP_MARGIN = 1.15, AL_STOP_PLAN_SLACK = 1.1, AP_BANK_LIFT_K = 1.1, AP_BANK_LIFT_MIN = 10.0, AP_LETGO_LIFT_K = 0.9, AL_APPROACH_HARD = 300.0, AL_PATH_STEP = 32.0, AL_SLOPE_MAX = 7.0, AP_VS_DOWN = 8.0, AP_VS_UP = 12.0, AP_PROTECT_ALT = 25.0, AP_PROTECT_SINK = 12.0, AP_BANK_SAFE = 15.0;

    public static final int AP_LOG_EVERY = 40;
    public static final double AL_PATTERN = 30.0, AL_SLOPE = Balance.BEACON_SLOPE, AL_DH = 15.0, AL_FLARE = 6.0, AL_RETARD = 2.0, AL_VREF = 1.4, AL_SINK_MAX = 1.5,
            AL_BANK_MAX = 15.0, AL_K_XT = 1.0, AL_K_XTA = 0.8, AL_XT_MAX = 30.0, AL_PLAN_BANK = 30.0, AL_PLAN_V_PAD = 1.05, AL_PLAN_SHRINK = 1.3, AL_ENTRY_K_MAX = 2.4, AL_PLAN_R_MARGIN = 1.15, AL_TURN_BANK = 35.0, AL_DIRECT_SIDE = 25.0, AL_DIRECT_HDG = 20.0, AL_DIRECT_SPD = 1.3, AL_PLAN_V_MAX = 1.4, AL_FINAL_OVERSPEED = 1.4, AL_FINAL_CLEAR = 8.0, AL_FINAL_CLEAR_FROM = 60.0, AL_K_GS = 0.4, AL_GATE_SIDE = 3.0, AL_GATE_HDG = 10.0, AL_GATE_GS = 1.0, AL_GATE_SPD = 0.15, AL_GA_CLIMB = 8.0,
            AL_CIRCUIT_OUT = 120.0, AL_CIRCUIT_SIDE = 140.0, AL_LEG_REACH = 20.0, AL_K_ROLLOUT_H = 0.05, AL_K_ROLLOUT_X = 0.15, AL_STOP_SPEED = 0.6,
            AL_CLEAR_W = 4.0, AL_BEACON_ROLL = 60.0, AL_ROLL_START_SPEED = 2.0, AL_LIFTOFF_SPEED = 15.0, AL_STRIP_MIN = 40.0, AL_STRIP_MAX = 2_000.0, AL_STRIP_HERE = 200.0, AL_TD_IN = 10.0,
            AL_BEACON_NEAR = 150.0, AL_EYE_DEF = 2.5, AL_BINGO_MARGIN = 0.15;
    public static final int AL_GA_MAX = 2, AL_BRAKE_DELAY = 10, AL_BOLTER_TICKS = 40;

    public static final int FDK_FE = 12, FDK_BELL_TICKS = 20;
    public static final float FDK_SEAT_BACK = 5.0F;

    public static final int RF_SEND_FE = 100_000, RF_OUT_FE = 80_000, RF_EFFICIENCY = 80, RF_BUFFER_FE = 1_000_000, RF_RANGE = 1024,
            RF_LINK_HOLD = 20, RF_REG_TICKS = 20, RF_JAM_TICKS = 20;
    public static final float RF_AIM_EASE = 0.12F;

    public static final double CABIN_RANGE = 24.0, CABIN_DIST = 2.5;
    public static final float SND_AMBIENT = 0.12F, SND_SMALL = 0.35F, SND_MACHINE = 0.6F, SND_HEAVY = 0.9F, SND_GIANT = 1.5F, SND_SHOWPIECE = 2.5F;
    public static final float SND_CLICK = 0.6F, SND_MECH = 1.0F, SND_RELEASE = 2.0F, SND_ALARM = 4.0F;

    public static final int AIR_SCAN_TICKS = 20, AIR_PICTURE_MAX = 256;
    public static final double AIR_MIN_AGL = 6.0, AIR_MIN_SPEED = 8.0, AIR_SIZE_REF = 400.0, AIR_SIZE_MIN = 0.6, AIR_SIZE_MAX = 1.5, AIR_DETECT_BASE = 1_500.0,
            AIR_NOSE_LOCK_RANGE = 2_000.0, AIR_NOSE_GIMBAL = 60.0;

    public static final double[] AAM_SPEED = { 7.0, 9.0, 8.0, 6.0 }, AAM_RANGE = { 800.0, 2_000.0, 2_500.0, 2_500.0 }, AAM_LAT = { 1.6, 1.0, 0.5, 0.6 },
            AAM_FUZE = { 4.0, 6.0, 0.0, 0.0 }, AAM_DECAY = { 0.015, 0.01, 0.01, 0.01 };
    public static final int[] AAM_BURN = { 70, 170, 240, 350 };

    public static final double TPOD_RANGE = 3_000.0, TPOD_GIMBAL_UP = 30.0;

    public static final int TPOD_RAY_LOADS = 24;

    public static final int TPOD_MEM_TICKS = 600, TPOD_SLEW_LOADS = 4;
    public static final double TPOD_TRACK_DEG = 1.0;
    public static final double TPOD_RAY_AIR = 48.0;

    public static final double TPOD_TRACK_R = 1.5;

    public static final double GUNNER_CAM_AHEAD_TURRET = 2.0, GUNNER_CAM_AHEAD_HEAVY = 5.0;

    public static final double GUNNER_FIELD_MARGIN = 1.0;

    public static final int JET_WEAPON_ENTRIES = 8;

    public static final double TPOD_CAM_AZ = 40.0, TPOD_CAM_EL_MIN = 0.0, TPOD_CAM_EL_MAX = 85.0, TPOD_CAM_EL_DEF = 25.0;

    public static final double TPOD_THERMAL_RANGE = 256.0;
    public static final int TPOD_THERMAL_MAX = 48;
    public static final int TPOD_TRANS_TICKS = 7;
    public static final int TPOD_FE = 40, TPOD_LOS_TICKS = 10;

    public static final double[] TPOD_ZOOMS = { 2.0, 4.0, 8.0, 16.0 };
    public static final double TPOD_AIM_REACH = 24.0;

    public static final int GEAR_CYCLE_TICKS = 60;

    public static final float GEAR_LEG_TRAVEL = 6.2F, GEAR_OLEO_TRAVEL = 6.2F;
    public static final double GEAR_STRUT = 0.535, GEAR_WHEEL_R = 0.19, GEAR_STROKE = 0.25, GEAR_REST_COMP = 0.12, GEAR_G = 9.81, GEAR_ZETA = 0.6, GEAR_MAX_G = 4.0,
            GEAR_SIDE_GRIP = 6.0, GEAR_ROLL = 0.05, GEAR_BRAKE = 2.0, GEAR_BRAKE_MU = 0.6, GEAR_MU = 0.9, GEAR_PARK_SPEED = 3.0, GEAR_STEER_DEG = 35.0;

    public static final float GEAR_STEER_SENSE = -1.0F;

    public static final int JET_DIAMONDS = 4;

    public static final double JET_PLUME_LEN_DRY = 1.0, JET_PLUME_LEN_AB = 4.0, JET_PLUME_R = 0.3, JET_PLUME_FLARE = 0.6, JET_CORE_SHARE = 0.35;
    public static final int WING_ROW_MAX = 32;

    public static final float WING_LIFT_SCALAR = 0.0F, WING_PLATE_SCALAR = 3.5F;
    public static final double GEAR_STICK = 0.5;
    public static final int GEAR_AIR_TICKS = 10;

    public static final double TOW_LEN_MIN = 1.9, TOW_LEN_MAX = 4.5, TOW_SPEED_MAX = 3.0, TOW_GAIN = 2.0, TOW_TAU = 0.25, TOW_ACCEL = 2.0, TOW_STEER_DEG = 80.0,
            TOW_SNAP = 2.5, TOW_WALK = 0.7, REVERSE_SPEED = 2.5, REVERSE_ACCEL = 1.5;
    public static final int TOW_AIR_TICKS = 20;

    public static final double TOW_HAND_Y = 0.9;
    public static final float TOW_BAR_PX0 = 30.0F, TOW_STAGE_PX = 16.0F;

    public static final float[] GEAR_AXLE_PX = { 8.6F, 8.9F, 10.7F, 10.7F }, GEAR_R_PX = { 3.0F, 4.5F, 6.5F, 6.5F }, GEAR_CANT_PIVOT_PX = { 1.8F, 0.5F, 0.0F, 0.0F }, GEAR_SIDE_PX = { 0.0F, 0.0F, -6.0F, 0.0F };
    public static final float GEAR_M_STAGE = 3.5F, GEAR_CANT_DEG = 35.0F;

    public static final float[] GEAR_AFT_PX = { 0.0F, 0.0F, 0.0F, 8.0F }, GEAR_LOAD_PER_TYRE = { 1.0F, 1.5F, 3.0F, 3.0F };
    public static final double[] GEAR_SINK_BY_SIZE = { 12.0, 12.0, 14.0, 16.0 };
    public static final float GEAR_BOGIE_STAGE = 6.0F, GEAR_BOGIE_TILT = 8.0F;

    public static final int HOOK_TICKS = 20, WIRE_SPAN = 22,
            WIRE_MAX_TICKS = 160, WIRE_RESET_TICKS = 60, WIRE_FE = 20_000;

    public static final double HOOK_DROP = 1.16, HOOK_AFT = -0.15, WIRE_UP = 0.18, WIRE_CATCH_LOW = 1.2, WIRE_CATCH_HIGH = 0.35, WIRE_RUNOUT = 4.0, WIRE_STOP_SPEED = 0.6;
    public static final int CAT_MIN = 4, CAT_MAX = 16, CAT_FE = 50_000, CAT_BUFFER = 20_000, CAT_RECEIVE = 500, CAT_SPOOL_TICKS = 40, CAT_MAX_TICKS = 60, CAT_RETURN_TICKS = 50;
    public static final double CAT_END_SPEED = 45.0, CAT_HOOK_R = 0.8, BEACON_SLOPE = 3.5, BEACON_RANGE = 600.0, BEACON_CONE = 20.0;

    public static final int LIFT_MIN = 5, LIFT_MAX = 48, LIFT_MAX_DROP = 16, LIFT_FE = 8, LIFT_BUFFER = 300_000, LIFT_RECEIVE = 2_000, LIFT_BASE_TICKS = 40,
            LIFT_AREA_REF = 1_024, LIFT_SCAN_TICKS = 100, LIFT_POST_EVERY = 8;
    public static final double LIFT_TICKS_PER_BLOCK = 10.0;

    public static final double TT_HOLD_HZ = 1.5, TT_HOLD_MAX = 1.0;
    public static final int TT_MIN = 7, TT_MAX = 31, TT_TICKS_90 = 80, TT_FE = 40, TT_BUFFER = 300_000, TT_RECEIVE = 2_000, TT_SCAN_TICKS = 100, TT_CHECK_TICKS = 5;
    public static final double TT_R_REF = 5.0, TT_LAG_DEG = 10.0, TT_EDGE = 0.25, TT_CLEAR_R = 28.0, TT_MENU_REACH = 48.0;

    public static final double AGM_CONE = 30.0;

    public static final double AGM_GPS_MISS = 8.0, AGM_INS_MISS = 20.0;

    public static final double ASHM_SPEED = 3.0, ASHM_RANGE = 3_000.0, ASHM_MIN_RANGE = 150.0, ASHM_DROP = 4.0, ASHM_DESCENT_DEG = 20.0, ASHM_TERMINAL = 16.0,
            ASHM_RADAR_RANGE = 450.0, ASHM_SEEKER_ON = 600.0, ASHM_WINDOW = 150.0, ASHM_CHAFF_REACH = 250.0, ASHM_CHAFF_OFFSET = 30.0;
    public static final int ASHM_MAX_PER_OWNER = 6, ASHM_BAY_YIELD = 1;

    public static final float AAM_BURST_VOLUME = 6.0F;

    public static final float AAM_FAR_VOLUME = 12.0F;
    public static final int AAM_BOOST_TICKS = 10, AAM_MAX_TICKS = 600, AAM_MAX_PER_OWNER = 16, AAM_MAX_PER_DIM = 64;
    public static final double AAM_LAUNCH_SPEED = 1.5, AAM_MIN_SPEED = 2.0, AAM_REACH_SPARE = 1.25, AAM_PN = 3.0, AAM_PURSUIT_DEG = 60.0, AAM_SEEKER_GIMBAL = 60.0,
            AAM_CONTACT = 1.5, AAM_HEAT_LOCK_RANGE = 700.0, AAM_HEAT_CONE = 25.0, AAM_PITBULL = 250.0, AAM_FX_RANGE = 1_024.0, AAM_DEV_PICK_DEG = 10.0;

    public static final double AAM_DECOY_REACH = 300.0;

    public static final int AAM_LAUNCH_SMOKE = 4;
    public static final double AAM_LAUNCH_SMOKE_SIZE = 0.35;

    public static final double HEAT_SIG_COLD = 0.5, HEAT_SIG_TURBOFAN = 1.0, HEAT_SIG_HOT = 1.5;

    public static final double HEAT_REAR_HALF_ANGLE = 60.0, HEAT_REAR_HOT_BONUS = 20.0, HEAT_REAR_MIN_SPEED = 0.05;

    public static final double AIR_NOISE_AGL = 20.0, AIR_NOISE_DB_FAN = 100.0, AIR_NOISE_DB_TURBOFAN = 118.0, AIR_NOISE_DB_JATO = 125.0;
    public static final int AAM_DECOY_TICKS = 60, AAM_DECOY_COOLDOWN = 20, AAM_DISPENSER_FLARES = 30, AAM_DISPENSER_CHAFF = 30;

    public static final int FLARE_FX_PAIRS = 4, FLARE_FX_GAP = 3, FLARE_FX_BURN = 80, FLARE_FX_FADE = 20, FLARE_FX_TRAIL = 300, FLARE_FX_MAX = 64;
    public static final double FLARE_FX_KEEP = 0.85, FLARE_FX_EJECT = 0.45, FLARE_FX_DRAG = 0.93, FLARE_FX_GRAV = 0.035, FLARE_FX_WIND = 0.012, FLARE_FX_SINK = 0.006,
            FLARE_FX_CORE = 0.35, FLARE_FX_HALO = 3.0, FLARE_FX_NEAR = 60.0, FLARE_FX_RANGE = 1_024.0, FLARE_FX_TRAIL_W0 = 0.10, FLARE_FX_TRAIL_GROW = 0.006,
            FLARE_FX_TRAIL_ALPHA = 0.8;

    public static final double[] AAM_FRAG_RADIUS = { 6.0, 8.0, 5.0, 5.0 }, AAM_FRAG_POWER = { 3.0, 4.0, 4.5, 4.5 }, AAM_FRAG_DAMAGE = { 18.0, 22.0, 24.0, 24.0 };
    public static final int AAM_FRAG_RAYS = 6, AAM_FRAG_BLOCKS = 2;
    public static final double AAM_FRAG_CONE = 35.0, AAM_FRAG_DEPTH = 3.0;

    public static final double AAM_REFINED_BONUS = 0.20;

    public static final int RWR_SEARCH_TTL = 60, RWR_LOCK_TTL = 30, RWR_LAUNCH_TTL = 40, RWR_MAX_EVENTS = 12, RWR_MAX_AIRCRAFT = 256, RWR_SYNC_TICKS = 10;

    public static final int ASR_FE = 1_000, ASR_SWEEP_TICKS = 40, ASR_MAX_CONTACTS = 16, ASR_SPIN_UP = 60, ASR_SPIN_DOWN = 80;
    public static final double ASR_RANGE = 2_500.0, ASR_CLUTTER_RANGE = 300.0, ASR_CLUTTER_AGL = 20.0, ASR_MASK_STEP = 16.0;

    public static final int CORE_BUFFER_FE = 2_500_000, CORE_RECEIVE_FE = 2_000, CORE_APU_FE = 1_500, CORE_APU_TICKS_PER_MB = 2, CORE_TANK_MB = 16_000,
            CORE_DRIVE_FE = 400, CORE_SHIP_TICKS = 20;
    public static final double CORE_APU_START = 0.9;
    public static final float CORE_LOW = 0.10F;

    public static final int STATION_LAUNCH_FE = 1_000, STATION_COOLDOWN = 10, STATION_SHIP_TICKS = 20, STATION_DROP_TICKS = 4, STATION_SLIDE_TICKS = 4;

    public static final double STATION_PYLON_MAX_G = 4.0, STATION_PYLON_MIN_G = 0.0, STATION_DROP_G = 0.0275, RAIL_KICK = 1.0;
    public static final int RAIL_TWIN = 2;
    public static final float RAIL_TWIN_DX_PX = 3.5F;

    public static final int NOSE_FE = 150, NOSE_SCAN_TICKS = 10;
    public static final double NOSE_RANGE = 2_000.0, NOSE_SEARCH_CONE = 60.0;
    public static final float NOSE_ARRAY_DEG = 30.0F;

    public static final int DISP_PER_ITEM = 10, DISP_HATCH_TICKS = 10;

    public static final float WING_CS_MAX = 20.0F, WING_CS_RUDDER_MAX = 30.0F, WING_CS_FLAP_MAX = 30.0F, WING_CS_RATE = 60.0F;
    public static final int WING_CS_SHIP_TICKS = 40;
    public static final double WING_CS_SIGN = 1.0;

    public static final int WBAY_GROUP_MAX = 4, WBAY_RUN_SCAN = 16, WBAY_DOOR_TICKS = 6, WBAY_TRAP_AT = 5, WBAY_TRAP_TICKS = 4, WBAY_LAUNCH_DELAY = 9,
            WBAY_TRAP_BACK_TICKS = 6, WBAY_DOOR_HOLD = 10, WBAY_DOOR_CLOSE_TICKS = 10;

    public static final double AAM_ARM_CONE = 30.0, AAM_STORE_SHOT_OFF = 0.5;

    public static final double SAM_REFIRE_CLIMB = 1.0;

    public static final double WRECK_NEAR = 12.0;
    public static final int SAM_CLAIM_TICKS = 40;
    public static final double SAM_RANGE = 2_000.0, SAM_MIN_RANGE = 40.0, SAM_SLEW_DEG = 3.0, SAM_RAISE_DEG = 2.0, SAM_LOFT = 20.0, SAM_EL_MIN = 30.0, SAM_EL_MAX = 60.0,
            SAM_AIM_TOL = 2.0, SAM_BOOST = 1.5, SAM_GUIDE_SPARE = 1.15;
    public static final int SAM_FE = 120, SAM_BUFFER_FE = 120_000, SAM_RECEIVE_FE = 2_000, SAM_SHOT_FE = 20_000, SAM_SCAN_TICKS = 10, SAM_SALVO_GAP = 15,
            SAM_RELOOK = 100, SAM_GUIDE_HOLD = 10, SAM_STOW_DELAY = 160, SAM_HATCH_TICKS = 10;
    public static final float SAM_LAUNCH_VOLUME = 12.0F;

    public static final double AIRJAM_LOCK_FACTOR = 0.5;
    public static final int AIRJAM_HOLD = 10;

    public static final int JAM_POD_FE = 60;
    public static final float JAM_POD_FAN_GAIN = 0.8F, JAM_POD_FAN_MAX = 2.5F;

    public static final int AD_SLOTS = 54, AD_MAX_LOADERS = 8, AD_FE = 10, AD_BUFFER = 4_000, AD_RECEIVE = 200, AD_DOOR_TICKS = 20, AD_DOOR_HOLD = 60;
    public static final double AD_RANGE = 48.0, AD_RANGE_Y = 24.0;

    public static final double AD_LIST_NO_LOADER = 16.0;
    public static final float AD_FAN_RAD = 0.35F;

    public static final int AD_ORDER_TICKS = 6_000, AD_ORDER_IDLE = 200, AD_SCAN_TICKS = 40;

    public static final int LO_PRESETS = 8, LO_GROUP_MAX = 16, LO_REFRESH_TICKS = 20, LO_VIEW_MAX = 128;
    public static final double LO_REACH = 12.0;

    public static final int FG_RIPPLE_GAP = 6, FG_HOLD_TICKS = 10;

    public static final int LOADER_FE = 120, HEAVY_LOADER_FE = 240, LOADER_BUFFER = 20_000, LOADER_RECEIVE = 1_000, LOADER_RACK = 4, LOADER_SUPPLY_BASE = 20;
    public static final double LOADER_REACH = 9.4, HEAVY_LOADER_REACH = 13.0, LOADER_SUPPLY_SPEED = 0.2;

    public static final double LOADER_E_MIN = -15.0;

    public static final int LOADER_POLL = 20;
    public static final double LOADER_PARKED = 0.5;

    public static final int LD_HATCH = 12, LD_TABLE = 16, LD_HOIST = 16, LD_SWING = 20, LD_GRIP = 6, LD_LIFT = 12, LD_CARRY = 24, LD_PUSH = 10, LD_RELEASE = 8, LD_RETURN = 20;
    public static final float LOADER_HEAVY_SLOW = 1.5F;

    public static final int LOADER_TRACK_MAX = 48;
    public static final double LOADER_TRACK_SPEED = 0.15;

    public static final int GTUR_ROUNDS = 2_000, GTUR_BOX = 50,
            GTUR_SHOT_TICKS = 2, GTUR_SOUND_TICKS = 4;

    public static final double GTUR_RATE = 30.0;
    public static final double GTUR_RANGE = 500.0, GTUR_SPREAD = 0.5, GTUR_AIR_CONE = 1.5, GTUR_CHEW_CHANCE = 0.25;

    public static final double GUN_GROUND_CHEW = 0.25;
    public static final float GTUR_DMG = 8.0F;

    public static final double GTUR_YAW_RATE = 6.0, GTUR_ELEV_RATE = 4.5, GTUR_ALIGN_DEG = 3.0, GTUR_ELEV_MAX = 90.0;

    public static final double GTUR_ELEV_MIN = 30.0;

    public static final int MESH_MIP_LEVELS = 3;

    public static final int GHOST_MODEL_ALPHA = 90;

    public static final double GHOST_MODEL_CLEAR = 1.5;

    public static final int GTUR_DEPLOY_TICKS = 24, GTUR_CLAIM_TICKS = 30, GTUR_STOW_DELAY = 200, GTUR_FE_MOVE = 20, GTUR_FE_FIRE = 60;

    public static final int GTUR_ARC_STEP = 4, GTUR_ARC_COLS = 10, GTUR_ARC_REFRESH = 100;
    public static final double GTUR_ARC_REACH = 48.0;

    public static final int GTUR_SYNC_TICKS = 3;
    public static final double GTUR_SYNC_RANGE = 160.0;

    public static final int GUNNER_INPUT_GAP = 1, GUNNER_HUD_TICKS = 5, GUNNER_HELMET_RAY = 4;
    public static final double GUNNER_AIM_SLACK = 16.0;
    public static final double[] GUNNER_ZOOMS = { 1.0, 2.0, 4.0, 8.0 };

    public static final int HGUN_RACK = 12, HGUN_RELOAD_TICKS = 40, HGUN_HAND_TICKS = 20, HGUN_DOOR_TICKS = 20, HGUN_RUN_TICKS = 16, HGUN_TRAY_TICKS = 12,
            HGUN_RAM_TICKS = 8, HGUN_FE_LAY = 20, HGUN_FE_SHOT = 400;
    public static final double HGUN_SPEED = 25.0, HGUN_RANGE_MIN = 16.0, HGUN_RANGE = 600.0, HGUN_YAW_MAX = 60.0, HGUN_ELEV_UP = 10.0, HGUN_ELEV_DOWN = 50.0,
            HGUN_YAW_RATE = 3.0, HGUN_ELEV_RATE = 3.0, HGUN_ALIGN_DEG = 1.5;

    public static final int RAMP_W_MIN = 2, RAMP_W_MAX = 7, RAMP_H_MIN = 2, RAMP_H_MAX = 6, RAMP_ROWS_MIN = 2, RAMP_ROWS_MAX = 4, RAMP_ROWS_DEF = 3;
    public static final int RAMP_FE_PER_TILE = 100, RAMP_BUFFER = 40_000, RAMP_RECEIVE = 2_000;

    public static final int RAMP_CORE_CHARGE = 20_000, RAMP_CORE_CHARGE_TICKS = 20;

    public static final double RAMP_WALK_SNAP = 0.8;

    public static final double LIFT_WALK_SNAP = 1.2;

    public static final double STATIC_CABLE_MAX = 12.0, STATIC_CABLE_DROP = 0.62, STATIC_DOOR_R = 4.0, STATIC_SEARCH_R = 24.0;
    public static final int STATIC_TAUT_TICKS = 24, STATIC_SPENT_MAX = 8;
    public static final int RAMP_TOE_TICKS = 10, RAMP_LOCK_TICKS = 14, RAMP_SCAN_TICKS = 40, RAMP_LANE = 24, RAMP_ACT_GAP = 4;
    public static final double RAMP_LEAF_RATE = 1.5, RAMP_DOOR_RATE = 3.75, RAMP_GROUND_MAX = 40.0, RAMP_CARRY_SPEED = 2.0;

    public static final int[] PALLET_SLOTS = { 27, 108 };
    public static final double[] PALLET_ROLL_ACC = { 0.00625, 0.0125 }, PALLET_ROLL_MAX = { 0.15, 0.25 };
    public static final int PALLET_GAP_TICKS = 10, PALLET_CHUTE_DELAY = 10, PALLET_CHUTE_OPEN_TICKS = 16, PALLET_SMOKE_TICKS = 1200, PALLET_FLAT_TICKS = 600,
            PALLET_MAX_ROLL_TICKS = 400, STATIC_LINE_TICKS = 60, RAMP_STICK_HOLD = 20;
    public static final double PALLET_FALL = 0.3, PALLET_GRAVITY = 0.06, PALLET_DRAG = 0.05, PALLET_MIN_CHUTE = 6.0, STATIC_LINE_MIN = 20.0;

    public static final int RAMP_ROLLER_TICKS = 160;

    public static final int PALLET_FAR_TICKS = 5;
    public static final double PALLET_FAR_NEAR = 200.0, PALLET_FAR_RANGE = 2_000.0;
    public static final double[] PALLET_MASS = { 200.0, 800.0 };
    public static final double PALLET_MASS_STACK = 10.0;

    public static final int HDOOR_W_MIN = 3, HDOOR_W_MAX = 45, HDOOR_STEP_MAX = 16, HDOOR_DROP_MAX = 16, HDOOR_TICKS_PER_ROW = 10;
    public static final int HDOOR_FE_PER_CELL = 100, HDOOR_BUFFER = 20_000, HDOOR_RECEIVE = 2_000;
    public static final int HDOOR_SCAN_TICKS = 20, HDOOR_BLOCKED_RECHECK = 5, HDOOR_CLICK_GAP = 6, HDOOR_HEAVY_FROM = 10;

    public static final double FDC_RANGE = 64.0, FDC_RANGE_Y = 32.0, FDC_REACH = 8.0;
    public static final int FDC_FE = 20, FDC_SCAN_TICKS = 40, FDC_FOLD_TICKS = 200, FDC_MAX_CRAFT = 12, FDC_MAX_LIGHTS = 96, FDC_MAX_EACH = 16, FDC_PRESS_GAP = 10,
            FDC_CONFIRM_TICKS = 40, FDC_LID_TICKS = 26;

    public static final int NGUN_RACK = 12, NGUN_RELOAD_TICKS = 60, NGUN_HOIST_TICKS = 24, NGUN_FE_LAY = 20, NGUN_FE_SHOT = 400, NGUN_ORDER_TICKS = 200,
            NGUN_CRANE_STACK = 12;
    public static final double NGUN_EYE_SPEED = 25.0, NGUN_EYE_MIN = 16.0, NGUN_EYE_RANGE = 600.0, NGUN_CON_RANGE = 1_500.0, NGUN_TRAIN_MAX = 150.0,
            NGUN_ELEV_UP = 70.0, NGUN_ELEV_DOWN = 5.0, NGUN_TRAIN_RATE = 3.0, NGUN_ELEV_RATE = 2.5, NGUN_ALIGN_DEG = 1.0;

    public static final int XGUN_RACK = 18, XGUN_CHARGES = 12, XGUN_RELOAD_TICKS = 160, XGUN_HEAVY_RELOAD_TICKS = 1_800, XGUN_RIPPLE_TICKS = 3, XGUN_HOIST_TICKS = 40,
            XGUN_FE_LAY = 60, XGUN_FE_SALVO = 1_200, XGUN_HEAVY_CHARGES_MAX = 4, XGUN_CRANE_STACK = 16;
    public static final double XGUN_EYE_SPEED = 20.0, XGUN_EYE_MIN = 24.0, XGUN_EYE_RANGE = 800.0, XGUN_CON_RANGE = 2_500.0, XGUN_HEAVY_MIN = 400.0,
            XGUN_TRAIN_MAX = 150.0, XGUN_ELEV_UP = 45.0, XGUN_ELEV_DOWN = 2.0, XGUN_TRAIN_RATE = 1.0, XGUN_ELEV_RATE = 0.8, XGUN_ALIGN_DEG = 0.8;
    public static final double GUNNER_EYE_MAX = 800.0;

    public static final int ANCHOR_WATER_MAX = 64, ANCHOR_AIR_MAX = 16, ANCHOR_PIPES_MAX = 16, ANCHOR_CHAIN_MAX = 96, ANCHOR_FE_WEIGH = 40, ANCHOR_DIG_TICKS = 20,
            ANCHOR_REJOIN_TICKS = 20, ANCHOR_BEAT = 10, ANCHOR_TTL = 40, ANCHOR_PRESS_GAP = 10, ANCHOR_BUFFER = 8_000;
    public static final double ANCHOR_DROP_SPEED = 4.0, ANCHOR_WEIGH_SPEED = 2.0, ANCHOR_DIG_SPEED = 1.5, ANCHOR_SWING_R = 2.0, ANCHOR_SLIP = 2.0,
            ANCHOR_SETTLE_S = 6.0, ANCHOR_ZETA = 1.0, ANCHOR_HOLD_G = 0.6, ANCHOR_DRAG_G = 0.15, ANCHOR_YAW_DAMP = 1.0, ANCHOR_DRAG_THROTTLE = 0.5,
            ANCHOR_PARKED = 0.5, ANCHOR_REACH = 8.0;

    public static final int SP_WP_MAX = 8, SP_STATION_R_DEF = 8, SP_STATION_R_MIN = 4, SP_STATION_R_MAX = 32, SP_MARGIN_SUB = 6, SP_MARGIN_SHIP = 3,
            SP_MARGIN_MIN = 3, SP_MARGIN_MAX = 16, SP_GROUND_TICKS = 100, SP_GROUND_GRACE = 100, SP_EMPTY_TICKS = 200, SP_TRACK_TICKS = 100, SP_TRACK_MAX = 256,
            SP_REASON_TICKS = 80, SP_DEPTH_MAX = 400, SP_HUD_TICKS = 10, SP_STATE_GAP = 10, SP_PRESS_GAP = 4, SP_TILE_GAP = 8;
    public static final double SP_WP_REACH = 24.0, SP_LOOKAHEAD_S = 8.0, SP_LOOKAHEAD_MIN = 16.0, SP_CRUSH_CAP = 0.90, SP_CRUSH_WARN = 0.80,
            SP_RUDDER_DEG = 25.0, SP_RUDDER_DAMP = 0.08, SP_DEPTH_FULL = 6.0, SP_DEPTH_DAMP = 0.15, SP_GROUND_SPEED = 0.3, SP_ROUTE_RANGE = 2_048.0;

    public static final int CHART_FE = 20, CHART_GLASS_W = 42, CHART_GLASS_D = 18, CHART_SYNC_TICKS = 20, CHART_PAGE_W = 840, CHART_PAGE_D = 360;
    public static final double CHART_REACH = 8.0, CHART_PAGE_EDGE = 0.40, CHART_RANGE = 2_048.0;
}
