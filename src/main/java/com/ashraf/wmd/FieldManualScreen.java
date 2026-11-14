package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class FieldManualScreen extends Screen {

    private static final int BOOK_W = 192, TOP = 2, TEXT_X = 36, TEXT_W = 114, TITLE_Y = 32, ROWS_Y = 44, ROW_H = 18, LAST_LINE_Y = 149;
    private static final int INK = WmdUi.TEXT, TITLE = WmdUi.BLUE_LIGHT, ARROW = WmdUi.TEXT_MUTED;

    private static final int PANEL_X = 26, PANEL_Y = 6, PANEL_W = 140, PANEL_H = 174;
    static final int ICON = 16, ARROW_W = 11, PLUS_W = 9, GAP = 2;

    private record Page(String id, String[] rows, Supplier<Object[]> args) {}

    private static List<Page> withAir(List<Page> base) {
        if (!AirGate.AVAILABLE) return base;
        List<Page> l = new java.util.ArrayList<>(base);
        l.add(new Page("air_search_radar", new String[] { "busbar > air_search_radar" },
                () -> new Object[] { n((int) Balance.ASR_RANGE), n(Balance.ASR_SWEEP_TICKS / 20), n(Balance.ASR_FE * 20), n((int) Balance.ASR_CLUTTER_RANGE), n((int) Balance.ASR_CLUTTER_AGL) }));
        l.add(new Page("sam_site", new String[] { "air_search_radar aam_radar > sam_site" },
                () -> new Object[] { n((int) Balance.SAM_RANGE), n((int) Balance.AAM_PITBULL), n(Balance.SAM_SHOT_FE) }));
        l.add(new Page("jet_power_core", new String[] { "busbar > jet_power_core", "~jet_fuel > jet_power_core" },
                () -> new Object[] { n(Balance.CORE_BUFFER_FE), n(Balance.CORE_APU_FE * 20), n((int) Math.round(Balance.CORE_APU_START * 100)) }));
        l.add(new Page("rf_power", new String[] { "busbar > rf_sender", "rf_sender > rf_receiver > busbar" },
                () -> new Object[] { n(Balance.RF_SEND_FE * 20), n(Balance.RF_OUT_FE * 20), Balance.RF_EFFICIENCY, n(Balance.RF_RANGE) }));
        l.add(new Page("service_arm", new String[] { "~jet_fuel ~diesel busbar > service_arm", "service_receptacle > jet_power_core" },
                () -> new Object[] { n((int) Balance.SA_REACH), n(Balance.SA_FUEL_RATE * 20), n(Balance.SA_GROUND_FE * 20), n(Balance.SA_FE_MOVE * 20) }));
        l.add(new Page("countermeasure_dispenser", new String[] { "flare_pack > countermeasure_dispenser", "chaff_pod > countermeasure_dispenser" },
                () -> new Object[] { n(Balance.AAM_DISPENSER_FLARES), n(Balance.AAM_DISPENSER_CHAFF), n(Balance.DISP_PER_ITEM) }));
        l.add(new Page("wing", new String[] { "wing_section > control_surface" },
                () -> new Object[] { n((int) Balance.WING_CS_MAX), n((int) Balance.WING_CS_RUDDER_MAX) }));
        l.add(new Page("air_stations", new String[] { "aam_heat > pylon", "aam_radar > launch_rail" },
                () -> new Object[] { n((int) Balance.AAM_HEAT_CONE), n((int) Balance.AAM_HEAT_LOCK_RANGE), n(Balance.STATION_LAUNCH_FE) }));
        l.add(new Page("weapons_bay", new String[] { "aam_arm > weapons_bay", "aam_radar > weapons_bay" },
                () -> new Object[] { n(Balance.WBAY_GROUP_MAX), n(Balance.WBAY_LAUNCH_DELAY) }));
        l.add(new Page("gun_pod", new String[] { "ciws + jet_power_core > gun_pod", "ciws_ammo > gun_pod" },
                () -> new Object[] { n(Balance.GPOD_ROUNDS), n(Balance.GPOD_BOX), n((int) Balance.GPOD_RANGE) }));
        l.add(new Page("rocket_pod", new String[] { "basic_circuit + aluminium_plate > rocket_pod", "he_charge + aluminium_plate > air_rocket" },
                () -> new Object[] { n(Balance.RPOD_TUBES), n(Balance.AIR_ROCKET_CRAFT), n((int) Balance.RPOD_RANGE_MIN), n((int) Balance.RPOD_RANGE_MAX) }));
        l.add(new Page("gunner", new String[] { "gun_pod + advanced_circuit > gun_turret", "advanced_circuit > gunner_station" },
                () -> new Object[] { n(Balance.GTUR_ROUNDS), n(Balance.GTUR_BOX), n((int) Balance.GTUR_RANGE) }));
        l.add(new Page("heavy_gun", new String[] { "howitzer + advanced_circuit > heavy_gun", "shell_howitzer_he > heavy_gun" },
                () -> new Object[] { n(Balance.HGUN_RACK), n((int) Balance.HGUN_RANGE) }));
        l.add(new Page("naval_guns", new String[] { "howitzer + advanced_circuit > deck_gun", "howitzer + chip_metallurgy_3 > battleship_turret" },
                () -> new Object[] { n(Balance.NGUN_RACK), n((int) Balance.NGUN_EYE_RANGE), n((int) Balance.NGUN_CON_RANGE), n(Balance.XGUN_RACK), n(Balance.XGUN_CHARGES),
                        n((int) Balance.XGUN_CON_RANGE), n(SuperGun.chargeRange(Balance.XGUN_HEAVY_CHARGES_MAX)) }));
        l.add(new Page("flight_check", new String[] { "laser_tablet > flight_check_tablet" },
                () -> new Object[] { n((int) Balance.FC_REACH), n((int) Math.round(Balance.FC_TW_MIN * 100)) }));
        l.add(new Page("airframe", new String[] { "laser_tablet > flight_check_tablet" },
                () -> new Object[] { n(Balance.REC_SHOW_TICKS / 20) }));
        l.add(new Page("autopilot", new String[] { "jet_cockpit" },
                () -> new Object[] { n(Balance.AP_FLOOR), n(Balance.AP_WP_MAX), n((int) Math.round(Balance.AL_DH)) }));
        l.add(new Page("survey_scanner", new String[] { "flight_check_tablet > survey_scanner" },
                () -> new Object[] { n((int) Balance.SCANNER_RANGE) }));
        l.add(new Page("gantry", new String[] { "survey_scanner engine_repair_kit > gantry_cabinet", "gantry_rail" },
                () -> new Object[] { n(Balance.GANTRY_SPAN_MIN), n(Balance.GANTRY_SPAN_MAX), n(Balance.GANTRY_FE) }));
        l.add(new Page("loadout", new String[0],
                () -> new Object[] { n((int) Balance.AD_RANGE), String.format("%.1f", Balance.LOADER_REACH), String.format("%.1f", Balance.HEAVY_LOADER_REACH), n(Balance.LOADER_TRACK_MAX) }));
        l.add(new Page("loadout_screen", new String[0],
                () -> new Object[] { n(Balance.LO_PRESETS) }));
        l.add(new Page("air_guns", new String[] { "air_search_radar > ciws" },
                () -> new Object[] { n((int) Balance.CIWS_JET_RANGE) }));
        l.add(new Page("jammer_pod", new String[] { "jet_power_core > jammer_pod" },
                () -> new Object[] { n((int) Math.round(Balance.AIRJAM_LOCK_FACTOR * 100)), n(Balance.JAM_POD_FE * 20) }));
        l.add(new Page("bay_air", new String[] { "airframe_t2 motor_t2 guidance_t2 warhead_frag > bay_core", "heat_seeker radar_seeker arm_seeker laser_seeker > aam_heat" },
                () -> new Object[] { Balance.BAY_AIR_YIELD, Math.round(Balance.AAM_REFINED_BONUS * 100) }));
        l.add(new Page("aam_arm", new String[] { "arm_seeker > aam_arm", "aam_arm > pylon" },
                () -> new Object[] { n((int) Balance.AAM_ARM_CONE), n((int) Balance.AAM_RANGE[AirMissiles.ARM]) }));
        l.add(new Page("ashm", new String[] { "airframe_t3 jet_engine guidance_t3 warhead_he_t1 > bay_core", "radar_seeker > ashm", "ashm > pylon weapons_bay" },
                () -> new Object[] { Balance.ASHM_BAY_YIELD, n((int) Math.round(Balance.ASHM_SPEED * 20)), n((int) Balance.ASHM_RANGE), n((int) Balance.ASHM_RADAR_RANGE) }));
        l.add(new Page("landing_gear", new String[] { "landing_gear > jet_cockpit" },
                () -> new Object[] { n(Balance.GEAR_CYCLE_TICKS / 20) }));
        l.add(new Page("tow_bar", new String[] { "tow_bar > landing_gear" },
                () -> new Object[] { Balance.TOW_SPEED_MAX, Balance.REVERSE_SPEED }));
        l.add(new Page("anchor_windlass", new String[] { "hawse_pipe > anchor_windlass" },
                () -> new Object[] { n(Balance.ANCHOR_WATER_MAX), n(Balance.ANCHOR_PIPES_MAX), n(Balance.ANCHOR_FE_WEIGH) }));
        l.add(new Page("chart_table", new String[] { "helm_station > chart_table" },
                () -> new Object[] { n(Balance.SP_WP_MAX), n(Balance.SP_MARGIN_SUB), n(Balance.SP_MARGIN_SHIP), n(Balance.CHART_FE) }));
        l.add(new Page("deck_console", new String[] { "deck_light catapult_track arresting_wire > deck_console", "service_arm ammo_depot gantry_cabinet > deck_console" },
                () -> new Object[] { n((int) Balance.FDC_RANGE), n(Balance.FDC_FE), n(Balance.FDC_FOLD_TICKS / 20) }));
        l.add(new Page("cargo_ramp", new String[] { "cargo_pallet cargo_pallet_heavy > cargo_ramp" },
                () -> new Object[] { n(Balance.RAMP_W_MIN), n(Balance.RAMP_W_MAX), Balance.PALLET_FALL, n((int) Balance.STATIC_LINE_MIN) }));
        l.add(new Page("deck_lift", new String[] { "deck_lift > landing_gear" },
                () -> new Object[] { n(Balance.LIFT_MIN), n(Balance.LIFT_MAX), n(Balance.LIFT_MAX_DROP) }));
        l.add(new Page("turntable", new String[] { "landing_gear > turntable" },
                () -> new Object[] { n(Balance.TT_MIN), n(Balance.TT_MAX), String.format("%.1f", Balance.TT_TICKS_90 / 20.0) }));
        l.add(new Page("helicopter", new String[] { "heli_turboshaft > heli_rotor", "heli_turboshaft > heli_tail" },
                () -> new Object[] { n(HeliRotorBlockEntity.LENGTHS[0]), n(HeliRotorBlockEntity.LENGTHS[1]), n(HeliRotorBlockEntity.LENGTHS[2]) }));
        l.add(new Page("jet_engines", new String[] { "jet_power_core > single_jet", "jet_engine > twin_jet" },
                () -> new Object[] { n((int) Balance.JET1_THRUST), n((int) Balance.JET2_THRUST), n((int) Balance.JETS_THRUST) }));
        l.add(new Page("turboprop", new String[] { "heli_turboshaft > turboprop", "heli_turboshaft > tiltrotor" },
                () -> new Object[] { n((int) Balance.TP_AIRFLOW) }));
        l.add(new Page("ship_drives", new String[] { "ship_turbine > waterjet", "bow_thruster > hull_penetrator" },
                () -> new Object[0]));
        l.add(new Page("cargo_hook", new String[] { "cargo_hook_light > cargo_hook_heavy" }, () -> new Object[0]));
        l.add(new Page("refuel", new String[] { "drogue_pod > refuel_probe" }, () -> new Object[0]));
        l.add(new Page("aircraft_light", new String[] { "nav_light > jet_cockpit", "nav_light > wing_section" }, () -> new Object[0]));
        l.add(new Page("lift_fan", new String[] { "turbofan > lift_fan", "drag_chute > landing_gear" },
                () -> new Object[] { n((int) Math.round(Balance.LIFT_ENGINE_SHARE * 100)), n((int) Balance.CHUTE_MIN_SPEED) }));
        l.add(new Page("carrier_ops", new String[] { "catapult_track > landing_gear", "arresting_wire > tailhook", "deck_light > jet_cockpit" },
                () -> new Object[] { n(Balance.CAT_MIN), n(Balance.CAT_MAX), n((int) Balance.CAT_END_SPEED), n((int) Balance.WIRE_RUNOUT) }));
        l.add(new Page("targeting_pod", new String[] { "laser_tripod + jet_power_core > targeting_pod", "targeting_pod > aam_agm" },
                () -> new Object[] { n((int) Balance.TPOD_RANGE) }));
        l.add(new Page("aam_agm", new String[] { "laser_seeker > aam_agm", "aam_agm > pylon", "laser_tripod > aam_agm" },
                () -> new Object[] { n((int) Balance.AGM_CONE), n((int) Balance.AAM_RANGE[AirMissiles.AGM]) }));
        l.add(new Page("nose_radar", new String[] { "jet_power_core > nose_radar", "nose_radar > aam_radar" },
                () -> new Object[] { n((int) Balance.NOSE_RANGE), n((int) Balance.NOSE_SEARCH_CONE), n((int) Balance.AIR_NOSE_LOCK_RANGE), n((int) Balance.AAM_PITBULL),
                        n(Balance.NOSE_FE * 20) }));
        l.add(new Page("jet_cockpit", new String[] { "jet_power_core > jet_cockpit" },
                () -> new Object[] { n(Balance.COCKPIT_FE * 20), n(Balance.EJECT_HOLD_TICKS / 20) }));
        l.add(new Page("flight_deck", new String[] { "jet_cockpit jet_cockpit advanced_circuit > flight_deck" },
                () -> new Object[] { n(Balance.FDK_FE * 20) }));
        return List.copyOf(l);
    }

    private record Chapter(String key, String first) {}

    private static final List<Chapter> CHAPTERS = List.of(new Chapter("industry", "overview"), new Chapter("missiles", "launch"), new Chapter("fuels", "oil_upgrades"),
            new Chapter("space", "satellites"), new Chapter("cc", "cc_building"), new Chapter("artillery", "mortar"), new Chapter("cruise", "cruise_missile"),
            new Chapter("naval", "sonar"), new Chapter("tools", "ghost"));

    private static final int TOC_Y = ROWS_Y, TOC_H = 11;

    private static String n(int v) { return String.format("%,d", v); }

    private static String rc(char letter, String id) { return ReactorStructure.counts().getOrDefault(letter, 0) + "*" + id; }

    private static int converterEfficiency() {
        try { return Config.GRID_CONVERTER_EFFICIENCY.get(); } catch (RuntimeException e) { return 50; }
    }

    private static final List<Page> PAGES = withAir(List.of(
            new Page("contents", new String[0], () -> new Object[0]),
            new Page("overview", new String[] {
                    "pumpjack > ~crude_oil > desalter > ~brine",
                    "chlor_alkali_cell > ~chlorine > chlorinator > ~titanium_tetrachloride",
                    "kroll_reactor > titanium_sponge > arc_furnace > titanium_ingot",
                    "research_station > chip_metallurgy_2 chip_mining_1 chip_power_2"}, () -> new Object[0]),
            new Page("power", new String[] { "coal_generator diesel_generator > busbar > crusher",
                    "minecraft:coal + ~minecraft:water > steam_engine > busbar" },
                    () -> new Object[] { n(Balance.COAL_GEN_FE_PER_TICK * 20), n(Balance.SE_FE_PER_TICK * 20), n(GeneratorEngineBlockEntity.FE_PER_SECOND) }),

            new Page("blast_furnace", new String[] {
                    "minecraft:iron_ingot + " + Balance.BF_STEEL_COAL + "*minecraft:coal > blast_furnace > steel_ingot",
                    "minecraft:raw_iron minecraft:sand > blast_furnace > minecraft:iron_ingot silicon"},
                    () -> new Object[] { n(Balance.BF_STEEL_COAL), n(Balance.BF_STEEL_TICKS / 20), n(Balance.BF_SMELT_PER_COAL), n(Balance.BF_BLOWER_SPEED), n(Balance.BF_BLOWER_FE * 20) }),

            new Page("early_machines", new String[] {
                    Balance.CO_COAL + "*minecraft:coal > coke_oven > coke ~coal_tar",
                    "minecraft:raw_iron > stamp_mill > " + Balance.SM_YIELD + "*crushed_iron"},
                    () -> new Object[] { n(Balance.CO_COAL), n(Balance.SG_FE_PER_TICK * 20), n(Balance.SM_YIELD) }),

            new Page("renewables", new String[] { "3*solar_cell + 2*minecraft:copper_ingot > solar_panel > busbar",
                    "3*steel_plate + machine_casing > wind_turbine > busbar" },
                    () -> new Object[] { n(Balance.SOLAR_ARRAY_FE[1] * 20), n(Balance.SOLAR_ARRAY_FE[2] * 20), n(Balance.SOLAR_ARRAY_FE[3] * 20),
                            n(Balance.SOLAR_ARRAY_FE[4] * 20), n(Balance.SOLAR_ARRAY_FE[5] * 20), n(Balance.WIND_FE_LOW * 20), n(Balance.WIND_FE_HIGH * 20),
                            n(Balance.WIND_Y_HIGH) }),
            new Page("lava_power", new String[] { "~minecraft:lava > lava_generator > busbar" },
                    () -> new Object[] { n(Balance.LG_FE_PIPED * 20), n(1000 / Balance.LG_MB_PER_TICK / 20), n(Balance.LG_FE_PER_SOURCE * 20), n(Balance.LG_FE_PASSIVE_MAX * 20) }),

            new Page("plates_wires", new String[] {
                    "9*steel_ingot > steel_block > rolling_mill > 2*steel_plate",
                    "minecraft:copper_ingot > wire_drawer > 3*copper_wire",
                    "aluminium_plate titanium_plate gold_wire aluminium_cable"},
                    () -> new Object[] { n(Balance.MILL_PLATES), n(Balance.WIRE_PER_INGOT) }),
            new Page("oil", new String[] { "oil_detector > pumpjack > ~crude_oil", "~crude_oil > desalter > ~desalted_crude + ~brine" },
                    () -> new Object[] { n(Balance.PUMPJACK_MB_PER_30S), n((int) Math.round((1.0 - Balance.DESALTER_YIELD) * 100)) }),
            new Page("refinery", new String[] { "~desalted_crude > furnace > ~hot_crude > atmospheric_column", "~fuel_oil > vacuum_column > ~bitumen", "~bitumen > coker > 4*petroleum_coke" },
                    () -> new Object[] { n(Balance.COKER_BITUMEN_MB), n(Balance.COKER_COKE_PER_CYCLE) }),
            new Page("deposits", new String[] { "ground_scanner > rutile_deposit > rutile_core_sample + rutile_chunk" }, () -> new Object[0]),
            new Page("research", new String[] { "rutile_core_sample + chip_blank > research_station > chip_metallurgy_2" }, () -> new Object[0]),
            new Page("chips_industry", new String[] {
                    "2*rutile_core_sample + 2*rutile_chunk + ~brine > chip_metallurgy_2",
                    "2*wolframite_core_sample + 4*minecraft:raw_iron > chip_mining_1",
                    "~naphtha + 2*titanium_ingot > chip_power_2"}, () -> new Object[0]),
            new Page("chips_weapons", new String[] {
                    "2*titanium_ingot + motor_t1 + guidance_t1 > chip_rocketry_2",
                    "2*titanium_ingot + interceptor_t1 + advanced_circuit > chip_defence_2",
                    "warhead_he_t1 + 4*magnesium_ingot + 2*nitrate_dust > chip_warheads_2"}, () -> new Object[0]),
            new Page("water", new String[] { "water_intake + bronze_impeller > ~minecraft:water" },
                    () -> new Object[] { n(Balance.INTAKE_MB_BRONZE), n(Balance.INTAKE_MB_TITANIUM), n(Balance.INTAKE_MIN_SOURCES) }),
            new Page("heavy_water", new String[] { "water_intake + upgrade_heavy_water > ~heavy_water" },
                    () -> new Object[] { n(Balance.UPGRADE_MAX_LEVEL), n(Balance.HEAVY_WATER_RATIO) }),
            new Page("ducts", new String[] { "supply_interface > item_duct > sorter" },
                    () -> new Object[] { n(Balance.DUCT_BATCH), String.valueOf(Balance.DUCT_INTERVAL / 20.0) }),
            new Page("robots", new String[] { "robot_dock + missile_robot > cc_planner" },
                    () -> new Object[] { n(Balance.ROBOT_ZONE) }),
            new Page("heavy", new String[] { "xl_dock + xl_transporter > missile_depot" },
                    () -> new Object[] { n(Balance.ROBOT_ZONE) }),
            new Page("autolab", new String[] { "water_intake > lab_core > bay_warhead" },
                    () -> new Object[] { n(Balance.LAB_AUTO_HEAVY_WATER) }),
            new Page("teleporter", new String[] { "link_card > missile_teleporter" },
                    () -> new Object[] { n(Balance.TP_FE_PER_JUMP / 1_000_000) }),
            new Page("upgrades", new String[] { "upgrade_speed upgrade_efficiency upgrade_muffler" },
                    () -> new Object[] { n((int) Math.round(Balance.UPGRADE_SPEED_PER_LEVEL * 100)), n((int) Math.round(Balance.UPGRADE_EFFICIENCY_PER_LEVEL * 100)),
                            n(Balance.UPGRADE_MUFFLER_SILENT), n(Balance.CIWS_DRUM_EXTRA) }),
            new Page("compat", new String[] { "item_duct > supply_interface" },
                    () -> new Object[0]),
            new Page("runner", new String[] { "runner_charger + parts_runner > bay_core" },
                    () -> new Object[] { n(Balance.RUNNER_MAX_PER_CC) }),
            new Page("laser", new String[] { "laser_tripod + laser_tablet > laser_seeker", "laser_receiver" },
                    () -> new Object[] { n(Balance.BEAM_RANGE), n(Balance.TABLET_RANGE), n(Balance.LWR_RADIUS) }),
            new Page("counters", new String[] { "camo_net > thermal_net", "smoke_canister > smoke_machine" },
                    () -> new Object[] { n(Balance.THERMAL_MAX_SPAN), n(Balance.THERMAL_MAX_SPAN), n(Balance.SMOKE_RADIUS), n(Balance.SMOKE_GROW_SECONDS), n(Balance.SMOKE_SECONDS) }),
            new Page("hypersonic", new String[] { "motor_hypersonic + laser_seeker > bay_core" },
                    () -> new Object[] { n((int) Balance.HYPER_SPEED), n(Balance.SEEKER_RADIUS), n(Balance.HYPER_WARN) }),
            new Page("designator", new String[] { "laser_designator_pod > satellite_facility" },
                    () -> new Object[] { n(Balance.SAT_OVERHEAD_SECONDS), n(Balance.SAT_PASS_MINUTES) }),
            new Page("hand", new String[] { "manpads_launcher + manpads_round", "javelin_launcher + javelin_round" },
                    () -> new Object[] { n(Balance.HAND_LOCK_TICKS / 20), n((int) Balance.MANPADS_RANGE), n((int) Balance.JAV_RANGE) }),
            new Page("bombs", new String[] { "bomb_rack + bomb_bay", "bomb_iron bomb_laser bomb_glide bomb_cluster" },
                    () -> new Object[] { n(Balance.SALVO_PULSE), n((int) Balance.LGB_RANGE), n((int) Balance.GLIDE_RANGE) }),
            new Page("heavy_bombs", new String[] { "bomb_delayed bomb_napalm", "bomb_penetrator bomb_daisy", "bomb_tallboy bomb_bouncing" },
                    () -> new Object[0]),
            new Page("tsar", new String[] { "3*warhead_thermo + 4*plutonium > bomb_tsar > bomb_bay" },
                    () -> new Object[] { n((int) Balance.TSAR_BURST_H), n(Balance.TSAR_CIWS_HITS), n(Balance.TSAR_INT_HIT_PCT) }),
            new Page("winter", new String[] { "warhead_nuke warhead_thermo bomb_tsar" },
                    () -> new Object[] { n(Balance.WINTER_THRESHOLD), n(Balance.WINTER_WINDOW / 24_000), n(Balance.WINTER_TICKS / 24_000) }),
            new Page("exotic", new String[] { "warhead_nanite warhead_gravity warhead_cryo > warhead_reactor" },
                    () -> new Object[] { n(Balance.NANITE_R), n(Balance.GRAV_R), n(Balance.CRYO_R) }),
            new Page("laser_defence", new String[] { "4*superconducting_coil + 2*advanced_circuit + chip_defence_2 > laser_defence" },
                    () -> new Object[] { n(Balance.LD_RANGE), n(Balance.LD_OPTICAL_R), n(Balance.LD_FIRE_FE) }),
            new Page("swarm", new String[] { "drone_avionics + drone_propulsion + minecraft:tnt > swarm_drone_pack",
                    "advanced_circuit + power_port > swarm_launcher" },
                    () -> new Object[] { n(Balance.SW_CELLS), n(Balance.SW_PACK), n(Balance.SW_RANGE), n(Balance.SW_SALVO_FE) }),
            new Page("deadhand", new String[] { "antimatter_cell + chip_warheads_2 + minecraft:iron_door > deadhand_bunker" },
                    () -> new Object[] { n(Balance.DH_MAX_PADS), n(Balance.DH_COUNTDOWN / 20) }),
            new Page("forcefield", new String[] { "4*superconducting_coil + antimatter_cell + chip_defence_2 > forcefield_generator" },
                    () -> new Object[] { n(Balance.FF_R_MIN), n(Balance.FF_R_MAX), n(Balance.FF_BOOT_PCT) }),

            new Page("base_defence", new String[] { "cam_pod + 2*tungsten_plate + advanced_circuit > sentry_turret", "fence_energizer > electric_fence" },
                    () -> new Object[] { n(Balance.ST_RANGE), n(Balance.EF_MAX_PIECES) }),
            new Page("mines", new String[] { "he_charge + minecraft:heavy_weighted_pressure_plate > pressure_mine", "2*he_charge + minecraft:tripwire_hook > claymore" },
                    () -> new Object[] { n(Balance.MINE_ARM_TICKS / 20), n((int) Balance.CLAY_RANGE) }),

            new Page("raid_kit", new String[] { "2*he_charge > c4_charge + detonator", "portable_jammer seismic_scanner hacking_device" },
                    () -> new Object[] { n(Balance.C4_RANGE), n(Balance.JAM_RADIUS), n(Balance.HACK_USE / 20), n(Balance.SEIS_RANGE) }),

            new Page("cctv", new String[] { "cctv_camera camera_relay > cc_security_monitor", "perimeter_sensor > floodlight + sentry_turret" },
                    () -> new Object[] { n(Balance.CCTV_DIRECT), n(Balance.CCTV_HOP) }),
            new Page("bomb_sight", new String[] { "bomb_sight > bomb_rack" },
                    () -> new Object[] { n(Balance.SIGHT_LINK) }),
            new Page("drone_line", new String[] { "drone_core + drone_storage > drone_t1 drone_t2", "drone_hangar > drone_t3 drone_t4" },
                    () -> new Object[] { n(Balance.DRONE_RUN_TICKS[0] / 20), n(Balance.DRONE_RUN_TICKS[3] / 20), n(Balance.DRONE_SHELVES) }),
            new Page("bomb_line", new String[] { "bomb_line_core + bomb_line_storage > bomb_iron bomb_laser", "bomb_line_cradle > bomb_tallboy bomb_daisy" },
                    () -> new Object[] { n(Balance.BOMB_RUN_TICKS[0] / 20), n(Balance.BOMB_RUN_TICKS[7] / 20), n(Balance.BOMB_NAPHTHA_MB) }),
            new Page("drone_hive", new String[] { "drone_t1 drone_t2 > drone_hive", "laser_tablet > drone_hive" },
                    () -> new Object[] { n(Balance.DRONE_T1_RANGE), n(Balance.DRONE_T2_RANGE) }),
            new Page("big_drone", new String[] { "drone_t3 > drone_catapult", "drone_t4 > drone_hangar runway_tile" },
                    () -> new Object[] { n(Balance.DRONE_T3_RANGE), n(Balance.DRONE_T4_RANGE) }),
            new Page("cc_drones", new String[] { "drone_catapult drone_hangar > cc_map_table", "payload_unit > cc_warning_board" },
                    () -> new Object[] { n(Balance.CC_DRONE_WARN_R) }),
            new Page("tv", new String[] { "cam_pod > tv_module > bay_core" },
                    () -> new Object[] { n((int) Balance.TV_CONE) }),
            new Page("seekers", new String[] { "laser_seeker heat_seeker arm_seeker > bay_core" },
                    () -> new Object[] { n(Balance.SEEKER_RADIUS), n(Balance.SEEKER_SECONDS), n(Balance.DIVERT_MIN), n(Balance.DIVERT_MAX) }),
            new Page("fluids", new String[] { "fluid_pipe > aluminium_pipe > titanium_pipe", "fluid_valve + canister > floodgate" },
                    () -> new Object[] { n(Balance.PIPE_RATE_TIER[0] * 20), n(Balance.PIPE_RATE_TIER[1] * 20), n(Balance.PIPE_RATE_TIER[2] * 20), n(Balance.CANISTER_MB) }),
            new Page("rig", new String[] { "~minecraft:water + drill_bit > mining_rig > 3*raw_titanium" },
                    () -> new Object[] { n(Balance.RIG_RADIUS), n(Balance.RIG_RAW_PER_BLOCK), n(Balance.RIG_WATER_MB) }),
            new Page("crusher", new String[] { "raw_titanium > crusher > 2*titanium_concentrate", "rutile_chunk > crusher > titanium_concentrate" },
                    () -> new Object[] { n(Balance.CRUSH_YIELD), n(Balance.CRUSH_CHUNK_YIELD) }),
            new Page("chlorine", new String[] { "~brine > chlor_alkali_cell > ~chlorine", "chlor_alkali_cell > ~hydrogen + caustic_soda" },
                    () -> new Object[] { n(Balance.CELL_BRINE_MB), n(Balance.CELL_CL_FROM_BRINE), n(Balance.CELL_H2_FROM_BRINE) }),
            new Page("chlorinator", new String[] { "2*titanium_concentrate + petroleum_coke + ~chlorine >", "chlorinator > ~titanium_tetrachloride" },
                    () -> new Object[] { n(Balance.CHLOR_CONC), n(Balance.CHLOR_COKE), n(Balance.CHLOR_CL_MB), n(Balance.CHLOR_TICL_MB) }),
            new Page("magnesium", new String[] { "~brine > magnesium_cell > magnesium_ingot", "~magnesium_chloride > magnesium_cell > 2*magnesium_ingot + ~chlorine" },
                    () -> new Object[] { n(Balance.MGCELL_BRINE_MB), n(Balance.MGCELL_MGCL_MB), n(Balance.MGCELL_MG_FROM_MGCL), n(Balance.MGCELL_CL_FROM_MGCL) }),
            new Page("kroll", new String[] { "~titanium_tetrachloride + 2*magnesium_ingot > kroll_reactor", "kroll_reactor > titanium_sponge + ~magnesium_chloride" },
                    () -> new Object[] { n(Balance.KROLL_TICL_MB), n(Balance.KROLL_MG), n(Balance.KROLL_SPONGE), n(Balance.KROLL_MGCL_MB), n(Balance.KROLL_CYCLE_TICKS / 20) }),
            new Page("arc", new String[] { "titanium_sponge + carbon_electrode > arc_furnace > titanium_ingot" },
                    () -> new Object[] { n(Balance.ARC_CYCLES_PER_ELECTRODE), n(Balance.ARC_W_CONC) }),
            new Page("turbine", new String[] { "4*titanium_ingot + chip_metallurgy_2 > turbine_blades", "~lpg + turbine_blades > gas_turbine > busbar" },
                    () -> new Object[] { n(Balance.TURBINE_FE_PER_TICK * 20), n(converterEfficiency()) }),

            new Page("tungsten", new String[] {
                    "raw_tungsten > crusher > " + Balance.CRUSH_YIELD + "*tungsten_concentrate",
                    Balance.ARC_W_CONC + "*tungsten_concentrate + carbon_electrode > arc_furnace > tungsten_ingot"},
                    () -> new Object[] { n(Balance.ARC_W_CONC), n(Balance.ARC_W_CYCLE_TICKS / 20) }),
            new Page("chips_late", new String[] {
                    "2*wolframite_core_sample + 2*tungsten_ingot + ~hydrogen > chip_metallurgy_3",
                    "2*uraninite_core_sample + 2*uraninite_chunk + caustic_soda > chip_nuclear_1"},
                    () -> new Object[] { n(Balance.REACTOR_CHIPS) }),
            new Page("tungsten_chain", new String[] {
                    Balance.ROAST_CONC + "*tungsten_concentrate + ~lpg > roaster > " + Balance.ROAST_OXIDE + "*tungsten_oxide",
                    Balance.H2F_OXIDE + "*tungsten_oxide + ~hydrogen > hydrogen_furnace > " + Balance.H2F_POWDER + "*tungsten_powder",
                    Balance.SINTER_INGOT_POWDER + "*tungsten_powder > sinter_press > tungsten_ingot tungsten_rod"},
                    () -> new Object[] { n(Balance.ROAST_LPG_MB), n(Balance.H2F_H2_MB), n(Balance.SINTER_INGOT_POWDER), n(Balance.SINTER_ROD_POWDER) }),
            new Page("tungsten_parts", new String[] {
                    "9*tungsten_ingot > tungsten_block > rolling_mill > " + Balance.MILL_PLATES + "*tungsten_plate",
                    "tungsten_plate + titanium_casing > tungsten_alloy_casing"},
                    () -> new Object[] { n(Balance.MILL_PLATES) }),

            new Page("uranium", new String[] {
                    "raw_uranium > crusher > " + Balance.CRUSH_YIELD + "*uranium_concentrate",
                    Balance.LEACH_CONC + "*uranium_concentrate + " + Balance.LEACH_CAUSTIC + "*caustic_soda + ~minecraft:water >",
                    "leach_tank > " + Balance.LEACH_YELLOWCAKE + "*yellowcake"},
                    () -> new Object[] { n(Balance.CRUSH_YIELD), n(Balance.LEACH_CONC), n(Balance.LEACH_WATER_MB), n(Balance.LEACH_YELLOWCAKE) }),
            new Page("centrifuge", new String[] {
                    Balance.CENT_YELLOWCAKE + "*yellowcake > centrifuge > " + Balance.CENT_ENRICHED + "*enriched_uranium + " + Balance.CENT_DEPLETED + "*depleted_uranium",
                    "3*enriched_uranium + titanium_plate + tungsten_rod > fuel_rod"},
                    () -> new Object[] { n(Balance.CENT_CYCLE_TICKS / 20), n(Balance.CENT_NEIGHBOUR_BONUS_PCT),
                            n(100 + Balance.CENT_NEIGHBOUR_BONUS_PCT * Balance.CENT_MAX_NEIGHBOURS) }),

            new Page("reactor_parts", new String[] {
                    rc('P', "reactor_plating") + " " + rc('F', "reactor_frame") + " " + rc('S', "reactor_core_shield") + " " + rc('R', "control_rod_drive") + " " + rc('C', "coolant_pump"),
                    "nuclear_reactor reactor_fuel_port " + rc('W', "reactor_water_port") + " " + rc('O', "reactor_steam_port") + " reactor_power_port"},
                    () -> new Object[0]),
            new Page("reactor_build", new String[0], () -> new Object[0]),
            new Page("reactor_run", new String[] { "fuel_rod > nuclear_reactor > ~steam + spent_fuel_rod" },
                    () -> new Object[] { n(Balance.REACTOR_PUMP_FE_PER_TICK * 20), n(Balance.REACTOR_FUEL_TICKS / 1200) }),
            new Page("steam_loop", new String[] {
                    "nuclear_reactor > ~steam > steam_turbine > busbar",
                    "~spent_steam > cooling_tower > ~minecraft:water"},
                    () -> new Object[] { n(Balance.PIPE_RATE_MB), n(Balance.STEAM_TURBINE_MB * Balance.STEAM_TURBINE_FE_PER_MB * 20), n(100 - Balance.TOWER_WATER_PCT) }),
            new Page("meltdown", new String[] { "nuclear_reactor" },
                    () -> new Object[] { n(Balance.REACTOR_HEAT_ALARM), n(Balance.REACTOR_HEAT_STEAM), n(Balance.REACTOR_HEAT_IMMINENT), n(Balance.REACTOR_HEAT_MAX) }),
            new Page("plutonium", new String[] {
                    Balance.REPRO_SPENT + "*spent_fuel_rod + " + Balance.REPRO_CAUSTIC + "*caustic_soda + ~minecraft:water >",
                    "reprocessing_plant > " + Balance.REPRO_PLUTONIUM + "*plutonium + " + Balance.REPRO_DEPLETED + "*depleted_uranium"},
                    () -> new Object[] { n(Balance.REPRO_CYCLE_TICKS / 20) }),

            new Page("fusion", new String[] {
                    "~deuterium + ~tritium + ~minecraft:water > fusion_reactor"},
                    () -> new Object[] { n(Balance.FUSION_MAX_FE * 20), n(Balance.FUSION_IGNITION_FE), n(Balance.FUSION_RAMP_TICKS / 20), n(Balance.FUSION_START_WATER_MB),
                            n(Balance.FUSION_START_FUEL_MB) }),

            new Page("fusion_fuel", new String[] {
                    "~heavy_water > chlor_alkali_cell > ~deuterium",
                    "breeder_rod > nuclear_reactor > irradiated_breeder_rod",
                    "irradiated_breeder_rod > reprocessing_plant > ~tritium"},
                    () -> new Object[] { n(Balance.MGCELL_LI_EVERY), n(Balance.BREEDER_TICKS / 1200), n(Balance.BREEDER_TRITIUM_MB) }),

            new Page("antimatter", new String[] {
                    "~hydrogen + ~minecraft:water + empty_trap_cell > antimatter_collider",
                    "antimatter_collider > antimatter_cell > antimatter_store"},
                    () -> new Object[] { n(Balance.AM_FE_PER_TICK * 20), n(Balance.AM_H2_MB * 20), n(Balance.AM_WATER_MB * 20), n(Balance.AM_CYCLE_TICKS / 1200),
                            n(Balance.AM_STORE_CELLS), n(Balance.AM_STORE_BACKUP_TICKS / 20) }),

            new Page("railgun", new String[] {
                    "tungsten_block + tungsten_plate + superconducting_coil > railgun_slug",
                    "railgun_slug > railgun"},
                    () -> new Object[] { n(Balance.RG_FE_PER_TICK * 20), n(Balance.RG_CHARGE_TICKS / 1200), n((int) Balance.RG_ABORT_REACH) }),

            new Page("salvage", new String[] {
                    "salvage_generator > salvage_turret",
                    "radar_component guidance_core_t4 satellite_part"},
                    () -> new Object[] { n(Balance.SALVAGE_REGION_BLOCKS), n(Balance.SALVAGE_SPAWN_CLEAR), n((int) Balance.SALVAGE_TURRET_RANGE) }),

            new Page("launch", new String[] { "missile > launch_terminal_1 + network_card > radar_station" },
                    () -> new Object[] { n((int) Balance.BASE_RANGE[0]), n((int) Balance.BASE_RANGE[1]), n((int) Balance.BASE_RANGE[2]) }),
            new Page("t4_rocket", new String[] { "stage_core + stage_upper + 2*stage_booster > launch_terminal_4" },
                    () -> new Object[] { String.valueOf(Balance.ALKYLATE_T4_RANGE_MULT) }),
            new Page("mirv", new String[] { "mirv_bus > stage_upper > launch_terminal_4" },
                    () -> new Object[] { n(Balance.MIRV_MAX), n(Balance.MIRV_SPREADS[0]), n(Balance.MIRV_SPREADS[Balance.MIRV_SPREADS.length - 1]) }),
            new Page("radar", new String[] { "radar_station + network_card" },
                    () -> new Object[] { n((int) Balance.RADAR_DETECT_RANGE) }),
            new Page("alarm", new String[] { "radar_station > alarm" },
                    () -> new Object[0]),
            new Page("bay", new String[] { "airframe_t1 motor_t1 guidance_t1 warhead_he_t1 > missile" },
                    () -> new Object[0]),
            new Page("rack", new String[] { "bay_rack + bay_rack_r" },
                    () -> new Object[0]),
            new Page("lab", new String[] { "lab_core lab_press lab_filler warhead_reactor lab_oven lab_storage" },
                    () -> new Object[0]),
            new Page("interceptors", new String[] { "interceptor_launcher interceptor_battery_t2 interceptor_silo_t3 interceptor_silo_t4" },
                    () -> new Object[] { n((int) Balance.INT_RANGE[0]), n((int) Balance.INT_RANGE[1]), n((int) Balance.INT_RANGE[2]), n((int) Balance.INT_RANGE[3]), n((int) Balance.CC_AIR_DEFENCE_RANGE_BONUS) }),
            new Page("ciws", new String[] { "ciws + ciws_ammo" },
                    () -> new Object[] { n((int) Balance.CIWS_RANGE), n((int) Balance.CRAM_RANGE), n((int) Math.round(Balance.CRAM_HIT * 100)) }),

            new Page("airships", new String[] { "radar_station + ciws" },
                    () -> new Object[0]),
            new Page("ship_defence", new String[] { "ciws + interceptor_launcher" },
                    () -> new Object[] { n(Balance.CIWS_SHIP_BLOCKS) }),
            new Page("lock", new String[0],
                    () -> new Object[0]),

            new Page("oil_upgrades", new String[] { "~naphtha > reformer > ~reformate + ~hydrogen", "~kerosene + ~hydrogen > hydrotreater > ~jet_fuel" },
                    () -> new Object[0]),

            new Page("gasoline_engine", new String[] { "~gasoline > gasoline_engine > busbar", "~reformate > gasoline_engine > busbar" },
                    () -> new Object[] { n(Balance.GE_FE_PER_TICK * 20), n(Balance.GE_FE_PER_MB_GASOLINE), n(Balance.GE_FE_PER_MB_REFORMATE) }),

            new Page("free_piston", new String[] { "~reformate > free_piston_generator > busbar" },
                    () -> new Object[] { n(Balance.FP_FE_PER_TICK * 20), n(Balance.FP_FE_PER_MB), n(Balance.GE_FE_PER_MB_REFORMATE) }),
            new Page("aluminium_he", new String[] { "2*raw_bauxite + carbon_electrode > electrolyser > aluminium_ingot", "2*nitrate_dust + ~naphtha > nitration_reactor > he_charge" },
                    () -> new Object[] { n(Balance.ELECTRO_CYCLES_PER_ELECTRODE), n(Balance.NITRO_NAPHTHA_MB) }),
            new Page("alkylation", new String[] { "~lpg + acid_catalyst > alkylation_unit > ~alkylate" },
                    () -> new Object[] { n(Balance.ALKYLATION_MB_PER_TICK * 20), n((int) Math.round(Balance.ALKYLATE_YIELD * 100)), n(Balance.ALKY_ACID_LPG_MB), n(Balance.ALKYLATION_FE_PER_TICK * 20) }),
            new Page("canisters", new String[] { "fuel_canister > alkylation_unit > alkylate_canister" },
                    () -> new Object[] { n(Balance.ALKYLATE_CANISTER_MB), String.valueOf(Balance.ALKYLATE_T4_RANGE_MULT) }),

            new Page("satellites", new String[] { "payload_unit > launch_terminal_4 > ground_station" },
                    () -> new Object[] { n(Balance.SAT_MAX), n(Balance.SAT_PASS_MINUTES), n(Balance.SAT_OVERHEAD_SECONDS / 60), n(Balance.SAT_LIFE_HOURS / 24) }),
            new Page("space_chip", new String[] { "satellite_part + 2*advanced_circuit + plutonium > chip_space_1", "plutonium + 4*tungsten_plate + chip_space_1 > rtg_cell" },
                    () -> new Object[0]),
            new Page("sat_facility", new String[] { "rtg_cell + minecraft:clock > satellite_facility > payload_unit" },
                    () -> new Object[] { n(Balance.SATFAC_TICKS / 20), n(Balance.SATFAC_FE_PER_TICK * 20) }),
            new Page("sat_launch", new String[] { "payload_unit > launch_terminal_4" },
                    () -> new Object[] { n(Balance.SAT_DOWNRANGE), n(Balance.SAT_MAX) }),
            new Page("orbit_suit", new String[] { "chip_space_1 + chip_fusion_1 > chip_space_2 > eva_torso" },
                    () -> new Object[] { n(Balance.O2_FULL / 1200) }),
            new Page("orbit_oxygen", new String[] { "~minecraft:water > oxygen_station > oxygen_tank" },
                    () -> new Object[] { n(Balance.O2_STATION_FE_PER_TICK * 20), n(Balance.O2_STATION_FILL_TICKS / 20) }),
            new Page("station", new String[] { "station_hull + titanium_casing > orbital_frame > station_kit_core", "oxygen_tank + carbon_electrode > life_support_unit > station_kit_habitat" },
                    () -> new Object[] { n((int) (Balance.STATION_BUFFER / 1_000_000L)), n(Balance.STATION_WING_FE), n(Balance.STATION_SUN_TICKS / 1200), n(Balance.STATION_ORBIT_TICKS / 1200) }),
            new Page("cargo", new String[] { "station_hull + solar_cell > cargo_pod > launch_terminal_4", "titanium_plate + ~jet_fuel > station_fuel_cell" },
                    () -> new Object[] { n(Balance.CARGO_SLOTS), n(Balance.REBOOST_BLOCKS_PER_CELL), n(Balance.REBOOST_TICKS_PER_1000 / 20), n(Balance.REBOOST_MAX) }),
            new Page("station_laser", new String[] { "weapon_core + power_core > station_kit_laser > launch_terminal_4" },
                    () -> new Object[] { n(Balance.LASER_SHOT_FE / 1_000_000), n(Balance.LASER_CHARGE_TICKS / 20), n(Balance.LASER_DMG), n(Balance.ASAT_STATION_DMG) }),
            new Page("rod", new String[] { "weapon_core + power_core > station_kit_rod > launch_terminal_4", "tungsten_block + guidance_t4 > orbital_rod > cargo_pod" },
                    () -> new Object[] { n(Balance.ROD_RACK_MAX), n(Balance.ROD_SHOT_FE / 1_000_000) }),
            new Page("mirror", new String[] { "orbital_frame + power_core > station_kit_mirror > launch_terminal_4" },
                    () -> new Object[] { n(Balance.MIRROR_BURN_TICKS / 1200), n(Balance.MIRROR_BURN_FE), n(Balance.MIRROR_WARN_R) }),
            new Page("abay", new String[] { "orbital_frame + power_core > station_kit_bay > launch_terminal_4", "4*antimatter_cell + dt_fuel_canister + tug_core > cargo_pod" },
                    () -> new Object[] { n(Balance.TUG_ANTIMATTER), n(Balance.TUG_BUILD_TICKS / 1200), n(Balance.BAY_BUILD_FE) }),
            new Page("turret", new String[] { "superconducting_coil + power_core > station_kit_turret > launch_terminal_4" },
                    () -> new Object[] { n(Balance.TURRET_SHOT_FE / 1000), n(20 / Balance.TURRET_SHOT_EVERY), n(Balance.TURRET_DMG), n((int) Balance.TURRET_RANGE) }),
            new Page("sensor", new String[] { "orbital_frame + power_core > station_kit_sensor > launch_terminal_4" },
                    () -> new Object[] { n(Balance.STATION_SENSOR_FE), n(Balance.SENSOR_GROUND_R * 2) }),
            new Page("orbit_capsule", new String[] { "crew_capsule > launch_terminal_4", "used_crew_capsule + 4*heat_shield_tile > crew_capsule" },
                    () -> new Object[] { n(Balance.CREW_SEATS), n(Balance.CREW_HOLD / 20), n(Balance.CREW_PAD_OFFSET) }),
            new Page("orbit_eva", new String[] { "eva_boots" },
                    () -> new Object[] { n(Balance.ORBIT_LOST_R) }),
            new Page("ground_station", new String[] { "ground_station" },
                    () -> new Object[] { n(Balance.GROUND_STATION_FE_PER_TICK * 20) }),
            new Page("nav_sat", new String[] { "minecraft:clock > payload_unit + uplink_cell" },
                    () -> new Object[0]),
            new Page("uplink", new String[] { "uplink_cell > bay_core > missile" },
                    () -> new Object[] { n((int) Balance.UPLINK_HOMING_RADIUS), n(Balance.UPLINK_HOMING_SECONDS) }),
            new Page("recon_sat", new String[] { "minecraft:spyglass > payload_unit" },
                    () -> new Object[] { n(Balance.RECON_IMPACT_BLUR) }),
            new Page("recon_map", new String[] { "cc_recon_wall" },
                    () -> new Object[] { n(Balance.RECON_SNAPSHOT_RADIUS * 2), n(Balance.RECON_RETAKE_SECONDS) }),
            new Page("cubesat", new String[] { "cubesat > warhead_cubesat > missile" },
                    () -> new Object[] { n(Balance.CUBESAT_MINUTES) }),
            new Page("recon_drone", new String[] { "recon_drone" },
                    () -> new Object[] { n(Balance.DRONE_RANGE), n(Balance.DRONE_LOITER_S) }),
            new Page("early_warning", new String[] { "early_warning_array" },
                    () -> new Object[] { n(Balance.EWA_RANGE), n(Balance.EWA_FE_PER_TICK * 20) }),
            new Page("asat", new String[] { "warhead_asat > stage_upper" },
                    () -> new Object[] { n(Balance.ASAT_CLIMB_S), n(Balance.ASAT_HIT_PCT) }),
            new Page("hacking", new String[] { "ground_station + cc_ew_rack" },
                    () -> new Object[] { n(Balance.SAT_HACK_SECONDS), n(Balance.SAT_HACK_FE_PER_TICK * 20), n(Balance.SAT_REKEY_COOLDOWN_S / 60) }),
            new Page("interceptor_sat", new String[] { "kill_vehicle_pack > payload_unit + cc_interceptor_console" },
                    () -> new Object[] { n(Balance.INT_SAT_KILL_PCT), n(Balance.INT_SAT_ZONE) }),

            new Page("cc_building", new String[] { "cc_building" },
                    () -> new Object[] { n(Balance.CC_BUILDING_FE_PER_TICK * 20) }),
            new Page("cc_reach", new String[] { "cc_comms_array" },
                    () -> new Object[] { n(Balance.CC_REACH) }),
            new Page("cc_map_table", new String[] { "cc_map_table" },
                    () -> new Object[0]),
            new Page("defcon", new String[0],
                    () -> new Object[] { n(CcDefcon.THREAT_RADIUS), n(CcDefcon.COUNTER_ARM_TICKS / 20) }),
            new Page("battle_log", new String[] { "cc_map_table > cc_map_printer" },
                    () -> new Object[0]),
            new Page("cc_warning_board", new String[] { "cc_warning_board" },
                    () -> new Object[0]),
            new Page("cc_recon_wall", new String[] { "cc_recon_wall" },
                    () -> new Object[0]),
            new Page("damage", new String[] { "cc_recon_wall cc_map_printer" },
                    () -> new Object[0]),
            new Page("cc_air_defence", new String[] { "cc_air_defence" },
                    () -> new Object[] { n((int) Balance.CC_AIR_DEFENCE_RANGE_BONUS) }),
            new Page("cc_launch", new String[] { "cc_launch_console" },
                    () -> new Object[0]),
            new Page("cc_retarget", new String[] { "cc_retarget_console + uplink_cell" },
                    () -> new Object[0]),
            new Page("cc_strike", new String[] { "cc_strike_planner" },
                    () -> new Object[0]),
            new Page("cc_cam", new String[] { "cam_pod > bay_core + cc_missile_cam" },
                    () -> new Object[0]),
            new Page("cc_globe", new String[] { "cc_sat_globe" },
                    () -> new Object[] { n(Balance.SAT_REKEY_COOLDOWN_S / 60) }),
            new Page("cc_ew_gps", new String[] { "cc_ew_rack cc_gps_rack target_designator" },
                    () -> new Object[] { n(Balance.CC_JAM_RADIUS) }),
            new Page("cc_alarm", new String[] { "cc_alarm_board" },
                    () -> new Object[] { n(CcDefcon.LOCKDOWN_TICKS / 20) }),
            new Page("cc_printer", new String[] { "minecraft:map > cc_map_printer > minecraft:filled_map" },
                    () -> new Object[0]),
            new Page("cc_comms", new String[] { "cc_comms_array + payload_unit" },
                    () -> new Object[0]),

            new Page("mortar", new String[] { "shell_mortar_he > mortar" },
                    () -> new Object[] { n(Balance.MORTAR_MIN), n(Balance.MORTAR_RANGE), n(Balance.MORTAR_RELOAD_TICKS / 20), n(Balance.MORTAR_MAGAZINE) }),
            new Page("howitzer", new String[] { "shell_howitzer_he + propellant_charge > howitzer" },
                    () -> new Object[] { n(Balance.HOWITZER_MIN), n(Balance.HOWITZER_RANGE), n(Balance.HOWITZER_CHARGES), n(Balance.HOWITZER_FE_PER_TICK * 20) }),
            new Page("shell_press", new String[] { "steel_plate + he_charge + minecraft:redstone > shell_press" },
                    () -> new Object[] { n(Balance.SHELL_PRESS_TICKS / 20), n(Balance.SHELL_PRESS_FE_PER_TICK * 20) }),
            new Page("shells", new String[] { "shell_mortar_he shell_mortar_smoke shell_mortar_illum shell_mortar_cluster" },
                    () -> new Object[] { n(Balance.ART_SMOKE_SECONDS), n(Balance.ART_ILLUM_SECONDS), n(Balance.ART_CLUSTER_BOMBLETS), n((int) Math.round(Balance.ART_SPOT_FACTOR * 100)) }),
            new Page("guided", new String[] { "shell_mortar_guided shell_howitzer_guided mlrs_pod_guided" },
                    () -> new Object[] { String.valueOf((int) Balance.GUIDED_SHELL_SPREAD), String.valueOf((int) Balance.MLRS_GUIDED_SPREAD) }),
            new Page("counter_battery", new String[] { "counter_battery_radar" },
                    () -> new Object[] { n(Balance.CBR_RANGE), n(Balance.CBR_ERROR), n(Balance.CBR_FE_PER_TICK * 20) }),
            new Page("chem_warheads", new String[] { "warhead_neutron warhead_flash > bay_core", "warhead_chemical > gas_mask" },
                    () -> new Object[] { n(Balance.GAS_TICKS / 1200) }),
            new Page("special_shells", new String[] { "shell_howitzer_laser shell_howitzer_flak > howitzer", "warhead_nuke > shell_super_nuke > super_gun" },
                    () -> new Object[] { n(Balance.CANISTER_MIN), n(Balance.CANISTER_MAX) }),
            new Page("art_console", new String[] { "cc_artillery_console" },
                    () -> new Object[0]),
            new Page("iron_dome", new String[] { "ciws > cc_iron_dome" },
                    () -> new Object[] { n(Balance.DOME_RADIUS_MIN), n(Balance.DOME_RADIUS_MAX), n(Balance.DOME_MARKER_TOL), n((int) Math.round(Balance.DOME_HIT * 100)) }),
            new Page("mlrs", new String[] { "mlrs_pod > mlrs" },
                    () -> new Object[] { n(Balance.MLRS_MIN), n(Balance.MLRS_RANGE), n(Balance.MLRS_RELOAD_TICKS / 20), n(Balance.MLRS_FE_PER_TICK * 20) }),
            new Page("super_gun", new String[] { "shell_super_he + super_charge > super_gun" },
                    () -> new Object[] { n(Balance.SUPER_GUN_MIN), n(Balance.SUPER_GUN_RANGE), n(Balance.SUPER_GUN_RELOAD_TICKS / 1200), n(Balance.SUPER_GUN_WARN_RANGE) }),
            new Page("camo", new String[] { "camo_net" },
                    () -> new Object[] { n(Balance.CAMO_MAX_SPAN), n(Balance.CAMO_MAX_SPAN) }),
            new Page("decoys", new String[] { "decoy_launcher decoy_radar" },
                    () -> new Object[] { n(Balance.DECOY_LIFE_MINUTES / 60) }),

            new Page("cruise_missile", new String[] { "airframe_t2 + jet_engine > cruise_missile" },
                    () -> new Object[] { n((int) Balance.CRUISE_RANGE[1]), n((int) Balance.CRUISE_RANGE[2]), n(Balance.CRUISE_ALT), n((int) Balance.CRUISE_RADAR_RANGE) }),
            new Page("cruise_launcher", new String[] { "cruise_missile + alkylate_canister > cruise_launcher" },
                    () -> new Object[] { n(Balance.CRUISE_WAYPOINTS), n(Balance.CRUISE_MIN_RANGE) }),

            new Page("sonar", new String[] { "hydrophone_array sonar_dome > sonar_console" },
                    () -> new Object[] { n((int) Balance.SILENT_SPEED), n((int) Balance.SONAR_LAYER_DEPTH), n((int) Balance.SONAR_LAYER_DB) }),
            new Page("sonar_dome", new String[] { "sonar_dome" },
                    () -> new Object[] { n((int) Balance.SONAR_DOME_RANGE), n((int) Balance.SONAR_PING_RANGE), n(Balance.SONAR_PING_SHOW / 20), n((int) Balance.SONAR_PING_DB), n(Balance.SONAR_DOME_FE * 20) }),
            new Page("hydrophone_array", new String[] { "hydrophone_array" },
                    () -> new Object[] { n((int) Balance.SONAR_ARRAY_RANGE), n(Balance.SONAR_ARRAY_FE * 20) }),
            new Page("sonar_console", new String[] { "sonar_console" },
                    () -> new Object[] { n(Balance.SONAR_CONSOLE_FE * 20) }),

            new Page("towed_array_winch", new String[] { "towed_array_winch" },
                    () -> new Object[] { n((int) Balance.TOWED_RANGE), n((int) Balance.TOWED_LENGTH), n((int) Balance.TOWED_DEAF_SPEED), n((int) Balance.TOWED_DEAF_TURN), n(Balance.TOWED_SETTLE_TICKS / 20), n(Balance.TOWED_FE * 20) }),
            new Page("listening_node", new String[] { "listening_node" },
                    () -> new Object[] { n((int) Balance.NODE_RANGE), n((int) (Balance.NODE_BATTERY / Balance.NODE_FE / 1200)), n(Balance.NODE_TURBINE_STILL), n(Balance.NODE_TURBINE_FLOW), n(Balance.NODE_FE) }),
            new Page("coastal_battery", new String[] { "cruise_missile + alkylate_canister > coastal_battery" },
                    () -> new Object[] { n((int) Balance.COASTAL_PICK), n((int) Balance.COASTAL_SKIM_ALT), n(Balance.COASTAL_FE * 20) }),
            new Page("dock_crane", new String[] { "dock_crane" },
                    () -> new Object[] { String.format("%.1f", DockCraneBlockEntity.reachMin()), String.format("%.1f", DockCraneBlockEntity.reachMax()), n((int) Balance.CRANE_DROP), n(Balance.CRANE_FE * 20) }),

            new Page("torpedo", new String[] { "torpedo_t1 torpedo_t2 torpedo_t3" },
                    () -> new Object[] { n((int) Math.round(Balance.TORP_SPEED[0] * 20)), n((int) Balance.TORP_RANGE[0]), n((int) Balance.TORP_ARM) }),
            new Page("torpedo_tube", new String[] { "torpedo_tube" },
                    () -> new Object[] { n(Balance.TUBE_FE * 20) }),
            new Page("torpedo_launcher_deck", new String[] { "torpedo_launcher_deck" },
                    () -> new Object[] { n((int) Balance.DECK_TRAIN_MAX), n(Balance.DECK_FE * 20) }),
            new Page("torpedo_rack", new String[] { "torpedo_rack" },
                    () -> new Object[0]),
            new Page("fire_control_console", new String[] { "sonar_console fire_control_console > torpedo_tube" },
                    () -> new Object[] { n((int) Balance.SALVO_FAN), n((int) Balance.GYRO_MAX), n((int) Balance.GYRO_STRAIGHT), n(Balance.FCC_FE * 20) }),
            new Page("launch_aboard", new String[] { "fire_control_console > launch_cell + coastal_battery" },
                    () -> new Object[] { n(Balance.FCC_ROWS) }),

            new Page("naval_reactor", new String[] { "fuel_rod > naval_reactor > spent_fuel_rod" },
                    () -> new Object[] { n(Balance.NR_FE_PER_TICK * 20), n(Balance.NR_FUEL_TICKS / 72_000), n(Balance.NR_QUIET_PCT), n((int) Balance.NR_HEAT_SCRAM) }),
            new Page("periscope", new String[] { "periscope" },
                    () -> new Object[] { String.valueOf(Balance.PERI_ZOOM_LOW), n((int) Balance.PERI_ZOOM_HIGH), String.valueOf(Balance.PERI_HEAD_UP) }),
            new Page("comms_buoy", new String[] { "comms_buoy" },
                    () -> new Object[] { n((int) Balance.COMMS_DEPTH), n((int) Balance.BUOY_CABLE) }),
            new Page("torpedo_att", new String[] { "torpedo_att > torpedo_tube" },
                    () -> new Object[] { n((int) Balance.ATT_DETECT), n((int) Math.round(Balance.TORP_SPEED[TorpedoKind.ATT.ordinal()] * 20)) }),
            new Page("escape_pod", new String[] { "escape_capsule > escape_pod" },
                    () -> new Object[] { n(Balance.POD_SEATS), n((int) Balance.POD_REACH) }),

            new Page("screw_propeller", new String[] { "helm_station > screw_propeller" },
                    () -> new Object[] { n(Balance.SCREW_FE * 20), n((Balance.SCREW_FE + Balance.SCREW_FE_LOAD) * 20) }),
            new Page("engine_telegraph", new String[] { "engine_telegraph > screw_propeller" },
                    () -> new Object[] { n((int) EngineTelegraphBlockEntity.RUDDER_CLICK) }),
            new Page("helm_station", new String[] { "helm_station > screw_propeller" },
                    () -> new Object[] { n(Balance.HELM_FE * 20) }),

            new Page("pump_jet", new String[] { "helm_station > pump_jet" },
                    () -> new Object[] { n(Balance.PJ_FE * 20), n((Balance.PJ_FE + Balance.PJ_FE_LOAD) * 20) }),
            new Page("sub_diesel", new String[] { "sub_diesel > snorkel_mast" },
                    () -> new Object[] { n(Balance.SD_FE_PER_TICK * 20) }),
            new Page("snorkel_mast", new String[] { "snorkel_mast > sub_diesel" },
                    () -> new Object[] { String.valueOf(Balance.SN_HEAD_UP) }),

            new Page("ducted_fan", new String[] { "engine_telegraph > ducted_fan" },
                    () -> new Object[] { n(Balance.EDF_FE * 20), n((Balance.EDF_FE + Balance.EDF_FE_LOAD) * 20), n((int) Balance.EDF_AIRFLOW) }),
            new Page("turbofan", new String[] { "hydrotreater > turbofan" },
                    () -> new Object[] { n(Balance.TF_STARTER_FE * 20), n((int) Balance.TF_AIRFLOW) }),
            new Page("engine_damage", new String[] { "turbofan > engine_repair_kit", "ducted_fan > engine_repair_kit" },
                    () -> new Object[] { n((int) Math.round(Balance.ENGINE_SMOKE * 100)), n((int) Math.round(Balance.ENGINE_SMOKE_THRUST * 100)),
                            n((int) Math.round(Balance.ENGINE_FIRE * 100)), n((int) Math.round(Balance.ENGINE_FIRE_THRUST * 100)), n((int) Balance.CONTRAIL_Y), n((int) Balance.CONTRAIL_SPEED) }),
            new Page("jato_rack", new String[] { "jato_booster > jato_rack" },
                    () -> new Object[] { n(Balance.JATO_BURN / 20), n(Balance.JATO_PROP_LIFE / 20) }),
            new Page("depth_charge", new String[] { "depth_charge > depth_charge_rack" },
                    () -> new Object[] { n((int) Math.round(Balance.DC_SINK * 20)), n((int) Balance.DC_DEPTH_MIN), n((int) Balance.DC_DEPTH_MAX), n(Balance.DC_RACK_FE * 20) }),
            new Page("kgun", new String[] { "depth_charge_rack > depth_charge_thrower > depth_charge" },
                    () -> new Object[] { n(Balance.KG_RELOAD_TICKS / 20), n(Balance.KG_FE * 20) }),
            new Page("sea_mines", new String[] { "mine_influence mine_bottom mine_captor" },
                    () -> new Object[] { n(Balance.SEAMINE_ARM_TICKS / 20) }),
            new Page("mine_influence", new String[] { "mine_influence" },
                    () -> new Object[] { n((int) Balance.MINE_INF_TRIGGER), n((int) Balance.MINE_INF_REACH) }),
            new Page("mine_bottom", new String[] { "torpedo_t1 > mine_bottom" },
                    () -> new Object[] { n((int) Balance.MINE_BOT_SIDE), n((int) Balance.MINE_BOT_LISTEN) }),
            new Page("mine_captor", new String[] { "torpedo_t2 > mine_captor" },
                    () -> new Object[] { n((int) Balance.MINE_CAP_RANGE) }),
            new Page("torpedo_t4", new String[] { "torpedo_t4" },
                    () -> new Object[] { n((int) Math.round(Balance.TORP_SPEED[3] * 20)), n((int) Balance.TORP_RANGE[3]) }),
            new Page("uuv", new String[] { "torpedo_tube > uuv" },
                    () -> new Object[] { n((int) Balance.TORP_RANGE[4]), n((int) Balance.UUV_RANGE), n(Balance.UUV_LIFE / 1200) }),
            new Page("launch_cell", new String[] { "fire_control_console launch_cell > asroc" },
                    () -> new Object[] { n((int) Balance.ASROC_MIN_RANGE), n((int) Balance.ASROC_RANGE), n((int) Balance.LC_MAX_DEPTH), n(Balance.LC_FE * 20) }),
            new Page("slbm", new String[] { "slbm > launch_cell" },
                    () -> new Object[] { n(MissileStats.range(new MissileBuild(3, 3, 3, 3, "he", false, false, false, false, 0, MissileBuild.KIND_SLBM))), n((int) Balance.SLBM_MIN_RANGE),
                            n((int) Math.round(SlbmEntity.climbed(Balance.SLBM_CLIMB_TICKS))) }),
            new Page("cell_cruise", new String[] { "cruise_missile cruise_capsule > launch_cell" },
                    () -> new Object[] { n(Balance.LC_CAPSULES) }),
            new Page("sonobuoy", new String[] { "sonar_console > sonobuoy" },
                    () -> new Object[] { n(Balance.SONOBUOY_TICKS / 1200), n((int) Balance.SONOBUOY_RANGE), n(Balance.SONOBUOY_EAR_DEPTH) }),
            new Page("noisemaker", new String[] { "noisemaker torpedo_t2" },
                    () -> new Object[] { n(Balance.TORP_DECOY_TICKS / 20) }),
            new Page("noisemaker_launcher", new String[] { "noisemaker > noisemaker_launcher" },
                    () -> new Object[] { n(Balance.DECOY_MAG), n((int) Balance.DECOY_AUTO_RANGE), n(Balance.DECOY_FE * Balance.DECOY_CHARGE_TICKS) }),
            new Page("torpedo_line", new String[] { "torpedo_line_shop torpedo_line_fitout torpedo_line_test > torpedo_t1" },
                    () -> new Object[] { n(Balance.TLINE_RUN_TICKS[0] / 20), n(Balance.TLINE_FE_PER_TICK * 20), n(Balance.TLINE_RACK) }),
            new Page("dredge_winch", new String[] { "nodule_field > dredge_winch > manganese_nodule" },
                    () -> new Object[] { n(Balance.NODULE_MIN_WATER), n(Balance.DREDGE_REACH), n(Balance.DREDGE_YIELD_MIN), n(Balance.DREDGE_YIELD_MAX), n(Balance.DREDGE_CABLE), n(Balance.DREDGE_FE * 20) }),
            new Page("collector_rig", new String[] { "seabed_crawler collector_rig > manganese_concentrate nickel_cobalt_concentrate" },
                    () -> new Object[] { n(Balance.CRAWLER_RADIUS), n(Balance.RIG_SEP_IN), n(Balance.RIG_MN_OUT), n(Balance.RIG_NICO_OUT), n(Balance.RIG_FE * 20) }),
            new Page("nodule_survey", new String[] { "sonar_dome sonar_console > nodule_field" },
                    () -> new Object[] { n(Balance.SURVEY_RANGE), n(Balance.SURVEY_KEEP_TICKS / 1200) }),
            new Page("sub_hull", new String[] { "sonar_console" },
                    () -> new Object[] { n((int) Math.round(Balance.SONAR_HULL_WARN * 100)), n(Balance.SONAR_HULL_TICKS / 20) }),
            new Page("deep_hull", new String[] { "manganese_ingot nickel_cobalt_ingot > manganese_hull_plate superalloy_hull deep_viewport" },
                    () -> new Object[] { n(Balance.DEEP_HULL_RATING[0]), n(Balance.DEEP_HULL_RATING[1]), n(Balance.DEEP_VIEWPORT_RATING), n(Balance.DEEP_FITTING_RATING),
                            n((int) Balance.DEEP_HULL_IMPLODE_BONUS[0]), n((int) Balance.DEEP_HULL_IMPLODE_BONUS[1]) }),
            new Page("anechoic", new String[] { "minecraft:black_wool steel_plate manganese_ingot > anechoic_tile" },
                    () -> new Object[] { n((int) Balance.ANECHOIC_MAX_DB), n((int) Math.round(Balance.ANECHOIC_PING_CUT * 100)), n(Balance.ANECHOIC_RATING) }),
            new Page("deep_sonar", new String[] { "nickel_cobalt_ingot advanced_circuit minecraft:prismarine_shard > deep_sonar_upgrade" },
                    () -> new Object[] { n((int) Balance.SONAR_DOME_RANGE), n((int) Balance.SONAR_DOME_RANGE_UP), n((int) Balance.SONAR_ARRAY_RANGE), n((int) Balance.SONAR_ARRAY_RANGE_UP) }),
            new Page("hull_penetrator", new String[] { "busbar steel_plate reinforced_concrete > hull_penetrator" },
                    () -> new Object[] { n(Balance.DEEP_FITTING_RATING) }),
            new Page("torpedo_net", new String[] { "steel_plate minecraft:chain > torpedo_net" },
                    () -> new Object[] { n(Balance.NET_MAX_LEN), n(Balance.NET_MAX_DEPTH), n(Balance.NET_AREA_PER_ITEM), n((int) Balance.TORP_NET_SHIP_SAFE) }),

            new Page("ghost", new String[] { "wrench fluid_pipe busbar" },
                    () -> new Object[0]),
            new Page("wrench", new String[] { "wrench" },
                    () -> new Object[0]),
            new Page("load_all", new String[] { "shell_mortar_he propellant_charge" },
                    () -> new Object[0]),
            new Page("config_client", new String[] { "field_manual" },
                    () -> new Object[0]),
            new Page("config_server", new String[0],
                    () -> new Object[0]),
            new Page("strike_toast", new String[0],
                    () -> new Object[0])));

    static int indexOf(String id) {
        if (id != null) for (int i = 0; i < PAGES.size(); i++) if (PAGES.get(i).id().equals(id)) return i;
        return 0;
    }

    static boolean hasPage(String id) { return id != null && PAGES.stream().anyMatch(p -> p.id().equals(id)); }

    private static Chapter chapterOf(int page) {
        Chapter c = null;
        if (page > 0) for (Chapter ch : CHAPTERS) if (indexOf(ch.first()) <= page) c = ch;
        return c;
    }

    private static int lastPage = 0;

    private int page;
    private Button forward, back, contents;

    private final Screen parent;

    private final List<int[]> hoverBoxes = new ArrayList<>();
    private final List<Object> hoverWhat = new ArrayList<>();

    public FieldManualScreen() { this(null, null); }

    public FieldManualScreen(Screen parent, String pageId) {
        super(Component.translatable("item.wmd.field_manual"));
        this.parent = parent;
        this.page = pageId != null ? indexOf(pageId) : Math.max(0, Math.min(PAGES.size() - 1, lastPage));
    }

    public static void open() { Minecraft.getInstance().setScreen(new FieldManualScreen()); }

    public static void open(Screen parent, String pageId) { Minecraft.getInstance().setScreen(new FieldManualScreen(parent, pageId)); }

    private int left() { return (width - BOOK_W) / 2; }

    @Override
    protected void init() {
        addRenderableWidget(new WmdUi.KitButton(width / 2 - 100, 196, 200, 20, parent != null ? CommonComponents.GUI_BACK : CommonComponents.GUI_DONE, b -> onClose()));
        forward = addRenderableWidget(new WmdUi.KitButton(left() + 116, 159, 23, 13, Component.literal(">"), b -> turn(1)));
        back = addRenderableWidget(new WmdUi.KitButton(left() + 43, 159, 23, 13, Component.literal("<"), b -> turn(-1)));
        contents = addRenderableWidget(new WmdUi.KitButton(left() + 68, 159, 46, 13, Component.translatable("manual.wmd.contents.button"), b -> goTo(0)));
        updateButtons();
    }

    private void turn(int d) { goTo(page + d); }

    private void goTo(int p) {
        page = Math.max(0, Math.min(PAGES.size() - 1, p));
        if (parent == null) lastPage = page;
        updateButtons();
    }

    private void updateButtons() {
        if (forward != null) forward.visible = page < PAGES.size() - 1;
        if (back != null) back.visible = page > 0;
        if (contents != null) contents.visible = page > 0;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (super.keyPressed(key, scan, mods)) return true;
        switch (key) {
            case 266, 263 -> { turn(-1); return true; }
            case 267, 262 -> { turn(1); return true; }
            case 268 -> { goTo(0); return true; }
            default -> { return false; }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && page == 0) {
            int ch = tocRow(mx, my);
            if (ch >= 0) { WmdUi.click(); goTo(indexOf(CHAPTERS.get(ch).first())); return true; }
        }
        return false;
    }

    private int tocRow(double mx, double my) {
        int x0 = left() + TEXT_X;
        for (int i = 0; i < CHAPTERS.size(); i++) if (WmdUi.over(mx, my, x0 - 2, TOC_Y + i * TOC_H - 2, TEXT_W + 4, TOC_H)) return i;
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0) { turn(sy > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        WmdUi.panel(g, left() + PANEL_X, TOP + PANEL_Y, PANEL_W, PANEL_H);
        WmdUi.divider(g, left() + TEXT_X, TITLE_Y - 4, TEXT_W);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Page p = PAGES.get(page);
        int x0 = left() + TEXT_X;

        Component pageMsg = Component.translatable("manual.wmd.page_short", page + 1, PAGES.size());
        g.drawString(font, pageMsg, left() - font.width(pageMsg) + BOOK_W - 44, 18, ARROW, false);
        Chapter chap = chapterOf(page);
        if (chap != null) WmdUi.drawFit(g, font, Component.translatable("manual.wmd.chapter." + chap.key()), x0, 18, TEXT_W - font.width(pageMsg) - 6, WmdUi.TEXT_DIM);

        Component title = Component.translatable("manual.wmd." + p.id() + ".title");
        int tw = font.width(title);
        float ts = tw > TEXT_W ? TEXT_W / (float) tw : 1.0F;
        g.pose().pushPose();
        g.pose().translate(x0 + (TEXT_W - tw * ts) / 2.0F, TITLE_Y, 0.0F);
        g.pose().scale(ts, ts, 1.0F);
        g.drawString(font, title, 0, 0, TITLE, false);
        g.pose().popPose();
        hoverBoxes.clear();
        hoverWhat.clear();
        if (page == 0) { drawContents(g, mouseX, mouseY); return; }

        for (int r = 0; r < p.rows().length; r++) drawRow(g, p.rows()[r], x0, ROWS_Y + r * ROW_H);

        int y = p.rows().length == 0 ? ROWS_Y : ROWS_Y + p.rows().length * ROW_H + 3;
        String body = Component.translatable("manual.wmd." + p.id() + ".body", p.args().get()).getString();
        for (String par : body.split("\n")) {
            for (FormattedCharSequence line : font.split(Component.literal(par), TEXT_W)) {
                if (y > LAST_LINE_Y) break;
                g.drawString(font, line, x0, y, INK, false);
                y += 9;
            }
        }

        for (int i = 0; i < hoverBoxes.size(); i++) {
            int[] b = hoverBoxes.get(i);
            if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                Object w = hoverWhat.get(i);
                if (w instanceof ItemStack st) g.renderTooltip(font, st, mouseX, mouseY);
                else g.renderTooltip(font, (Component) w, mouseX, mouseY);
                break;
            }
        }
    }

    private void drawContents(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = left() + TEXT_X, hover = tocRow(mouseX, mouseY);
        for (int i = 0; i < CHAPTERS.size(); i++) {
            Chapter c = CHAPTERS.get(i);
            int y = TOC_Y + i * TOC_H;
            if (i == hover) {
                g.fill(x0 - 2, y - 2, x0 + TEXT_W + 2, y + TOC_H - 2, 0xFF263040);
                g.fill(x0 - 2, y - 2, x0 - 1, y + TOC_H - 2, WmdUi.BLUE);
            }
            String num = Integer.toString(indexOf(c.first()) + 1);
            WmdUi.drawFit(g, font, Component.translatable("manual.wmd.chapter." + c.key()), x0 + 2, y, TEXT_W - font.width(num) - 8, i == hover ? WmdUi.BLUE_LIGHT : INK);
            g.drawString(font, num, x0 + TEXT_W - font.width(num), y, ARROW, false);
        }
        int hy = TOC_Y + CHAPTERS.size() * TOC_H + 2;
        if (hy <= LAST_LINE_Y) WmdUi.drawFit(g, font, Component.translatable("manual.wmd.contents.hint"), x0, hy, TEXT_W, WmdUi.TEXT_DIM);
    }

    static int tokenWidth(String t) { return t.equals(">") ? ARROW_W : t.equals("+") ? PLUS_W : ICON; }

    static int rowWidth(String[] toks) {
        int w = 0;
        for (int i = 0; i < toks.length; i++) {
            w += tokenWidth(toks[i]);
            if (i > 0 && tokenWidth(toks[i]) == ICON && tokenWidth(toks[i - 1]) == ICON) w += GAP;
        }
        return w;
    }

    private void drawRow(GuiGraphics g, String row, int x0, int y) {
        String[] toks = row.trim().split("\\s+");
        int x = x0 + (TEXT_W - rowWidth(toks)) / 2;
        for (int i = 0; i < toks.length; i++) {
            String t = toks[i];
            if (i > 0 && tokenWidth(t) == ICON && tokenWidth(toks[i - 1]) == ICON) x += GAP;
            if (t.equals(">")) arrow(g, x, y);
            else if (t.equals("+")) g.drawString(font, "+", x + (PLUS_W - font.width("+")) / 2 + 1, y + 4, ARROW, false);
            else if (t.startsWith("~")) fluid(g, t.substring(1), x, y);
            else item(g, t, x, y);
            x += tokenWidth(t);
        }
    }

    private static ResourceLocation id(String s) { return s.contains(":") ? ResourceLocation.parse(s) : ResourceLocation.fromNamespaceAndPath(Wmd.MODID, s); }

    private void item(GuiGraphics g, String tok, int x, int y) {
        int count = 1;
        int star = tok.indexOf('*');
        if (star > 0) { count = Integer.parseInt(tok.substring(0, star)); tok = tok.substring(star + 1); }
        ResourceLocation rl = id(tok);
        if (!BuiltInRegistries.ITEM.containsKey(rl)) {
            WmdUi.well(g, x + 2, y + 2, 12, 12);
            g.drawString(font, "?", x + 8 - font.width("?") / 2, y + 4, ARROW, false);
            hoverBoxes.add(new int[] { x, y, x + ICON, y + ICON });
            hoverWhat.add(Component.literal(rl.toString()));
            return;
        }
        ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(rl), count);
        g.renderItem(st, x, y);
        g.renderItemDecorations(font, st, x, y);
        hoverBoxes.add(new int[] { x, y, x + ICON, y + ICON });
        hoverWhat.add(st);
    }

    private void fluid(GuiGraphics g, String tok, int x, int y) {
        ResourceLocation rl = id(tok);
        Fluid f = BuiltInRegistries.FLUID.containsKey(rl) ? BuiltInRegistries.FLUID.get(rl) : Fluids.EMPTY;
        int colour = 0xFF808080;
        if (f == Fluids.WATER) colour = 0xFF3F76E4;
        for (WmdFluids.Entry e : WmdFluids.ALL) if (e.source().get() == f) colour = e.color() | 0xFF000000;
        g.fill(x + 6, y, x + 10, y + 2, 0xFF6B5A44);
        g.fill(x + 3, y + 2, x + 13, y + 16, 0xFF8E99A6);
        g.fill(x + 4, y + 3, x + 12, y + 15, 0xFF2A333E);
        g.fill(x + 4, y + 6, x + 12, y + 15, WmdUi.displayColour(colour));
        g.fill(x + 5, y + 7, x + 6, y + 13, 0x60FFFFFF);
        hoverBoxes.add(new int[] { x, y, x + ICON, y + ICON });
        hoverWhat.add(f == Fluids.EMPTY ? Component.literal(rl.toString()) : f.getFluidType().getDescription());
    }

    private static void arrow(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 7, x + 7, y + 9, ARROW);
        g.fill(x + 6, y + 4, x + 7, y + 12, ARROW);
        g.fill(x + 7, y + 5, x + 8, y + 11, ARROW);
        g.fill(x + 8, y + 6, x + 9, y + 10, ARROW);
        g.fill(x + 9, y + 7, x + 10, y + 9, ARROW);
    }

    @Override
    public void onClose() {
        if (parent != null && minecraft != null) { minecraft.setScreen(parent); return; }
        lastPage = page;
        super.onClose();
    }
}
