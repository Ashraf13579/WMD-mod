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
    public static final int CRUDE_TANK_MB = 256_000;
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

    public static final double BOMB_G = 0.08, BOMB_DRAG = 0.995, IRON_SCATTER = 0.03, IRON_SCATTER_MAX = 12.0, LGB_RANGE = 200.0, LGB_STEER = 0.06,
            GLIDE_SPEED = 1.6, GLIDE_RANGE = 500.0, GLIDE_SCATTER = 8.0, GLIDE_SINK = 0.25, CARPET_OPEN_UP = 40.0, CARPET_DUD = 0.10, DUD_POWER = 2.5,
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

    public static final int SONAR_HULL_TICKS = 40, SONAR_HULL_LEAKS = 4;

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
    public static final double PERI_HEAD_UP = 3.6, PERI_ZOOM_LOW = 1.5, PERI_ZOOM_HIGH = 6.0, PERI_REACH = 3.0, PERI_WAKE_SPEED = 0.08;

    public static final double PERI_EYE_AHEAD = 0.3;

    public static final int BUOY_FE = 15;
    public static final double COMMS_DEPTH = 10.0, BUOY_CABLE = 64.0, BUOY_RISE = 0.3, BUOY_MAX_SPEED = 8.0, BUOY_DB = 70.0;

    public static final int ATT_SCAN_TICKS = 4, ATT_CLAIM_TICKS = 160;
    public static final double ATT_DETECT = 120.0, ATT_CPA = 24.0, ATT_TURN = 15.0, ATT_SEEK = 80.0, ATT_ARM = 4.0, ATT_FUSE = 3.0, ATT_DB = 95.0;
    public static final float ATT_BLAST = 2.5F;

    public static final int POD_SEATS = 4, POD_FE = 5, POD_CHECK_TICKS = 10, POD_FLOAT_TICKS = 6_000;
    public static final double POD_REACH = 3.0, POD_RISE = 0.35, POD_FALL = 0.12, POD_DRAFT = 1.6, POD_HEIGHT = 2.8;

    public static final float SND_AMBIENT = 0.12F, SND_SMALL = 0.35F, SND_MACHINE = 0.6F, SND_HEAVY = 0.9F, SND_GIANT = 1.5F, SND_SHOWPIECE = 2.5F;
    public static final float SND_CLICK = 0.6F, SND_MECH = 1.0F, SND_RELEASE = 2.0F, SND_ALARM = 4.0F;
}
