package com.ashraf.wmd.jei;

import com.ashraf.wmd.Balance;
import com.ashraf.wmd.BombType;
import com.ashraf.wmd.DroneCatapultBlockEntity;
import com.ashraf.wmd.DroneHiveBlockEntity;
import com.ashraf.wmd.DroneModuleBlockEntity;

import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class WmdInfo {

    private WmdInfo() {}

    private static void info(IRecipeRegistration reg, String id, Object... args) {
        ItemStack s = Proc.stack(id, 1);
        if (!s.isEmpty()) reg.addItemStackInfo(s, Component.translatable("jei.wmd.info." + id, args));
    }

    public static void register(IRecipeRegistration reg) {
        info(reg, "ciws", Math.round(Balance.CRAM_HIT * 100), Proc.n((long) Balance.CRAM_RANGE), Balance.CRAM_ROUNDS_PER_SHOT, Math.round(Balance.DOME_HIT * 100));
        info(reg, "camo_net", Balance.CAMO_MAX_SPAN, Balance.CAMO_MAX_SPAN);
        info(reg, "decoy_launcher", Balance.DECOY_LIFE_MINUTES / 60);
        info(reg, "decoy_radar", Balance.DECOY_LIFE_MINUTES / 60);
        info(reg, "thermal_net", Balance.THERMAL_MAX_SPAN, Balance.THERMAL_MAX_SPAN);
        info(reg, "smoke_machine", Balance.SMOKE_RADIUS, Balance.SMOKE_GROW_SECONDS, Balance.SMOKE_SECONDS, Balance.SMOKE_COOLDOWN);
        info(reg, "smoke_canister");
        info(reg, "alkylate_canister", Proc.n(Balance.ALKYLATE_CANISTER_MB), Balance.CRUISE_FUEL_CANISTERS, Proc.n(Balance.SUPER_CHARGE_ALKYLATE_MB),
                String.format("%.2f", Balance.ALKYLATE_T4_RANGE_MULT));
        info(reg, "fuel_canister", Proc.n(Balance.ALKYLATE_CANISTER_MB));
        info(reg, "acid_catalyst", Proc.n(Balance.ALKY_ACID_LPG_MB));
        for (String guided : new String[] { "shell_mortar_guided", "shell_howitzer_guided", "mlrs_pod_guided" }) {
            ItemStack s = Proc.stack(guided, 1);
            if (!s.isEmpty()) reg.addItemStackInfo(s, Component.translatable("jei.wmd.info.guided_ammo"));
        }
        info(reg, "jet_engine");
        info(reg, "cruise_missile", Balance.CRUISE_FUEL_CANISTERS, Balance.CRUISE_ALT, Proc.n((long) Balance.CRUISE_RADAR_RANGE), Balance.CRUISE_WAYPOINTS);
        info(reg, "super_charge", Proc.n(Balance.SUPER_CHARGE_ALKYLATE_MB));
        info(reg, "propellant_charge");
        info(reg, "shell_super_he");
        info(reg, "stage_core");
        info(reg, "stage_upper");
        info(reg, "stage_booster", Balance.BOOSTER_MIN, Proc.n(Balance.BOOSTER_FUEL_MB));
        info(reg, "payload_unit");

        info(reg, "upgrade_speed", Math.round(Balance.UPGRADE_SPEED_PER_LEVEL * 100), Math.round(Balance.UPGRADE_SPEED_FE_PER_LEVEL * 100), Balance.UPGRADE_MAX_LEVEL);
        info(reg, "upgrade_efficiency", Math.round(Balance.UPGRADE_EFFICIENCY_PER_LEVEL * 100), Balance.UPGRADE_MAX_LEVEL);
        info(reg, "upgrade_throughput", Math.round(Balance.UPGRADE_THROUGHPUT_PER_LEVEL * 100), Balance.UPGRADE_MAX_LEVEL);
        info(reg, "upgrade_buffer", Math.round(Balance.UPGRADE_BUFFER_PER_LEVEL * 100), Balance.UPGRADE_MAX_LEVEL);
        info(reg, "upgrade_muffler", Balance.UPGRADE_MUFFLER_SILENT);
        info(reg, "upgrade_auto_eject");
        info(reg, "upgrade_ciws_drum", Proc.n(Balance.CIWS_DRUM_EXTRA));
        info(reg, "upgrade_heavy_water", Balance.HEAVY_WATER_RATIO);
        info(reg, "item_duct", Balance.DUCT_BATCH, String.valueOf(Balance.DUCT_INTERVAL / 20.0));
        info(reg, "sorter");
        info(reg, "supply_interface");
        info(reg, "fluid_pipe", Balance.PIPE_RATE_TIER[0] * 20);
        info(reg, "aluminium_pipe", Balance.PIPE_RATE_TIER[1] * 20);
        info(reg, "titanium_pipe", Balance.PIPE_RATE_TIER[2] * 20);
        info(reg, "fluid_valve");
        info(reg, "canister", Proc.n(Balance.CANISTER_MB));
        info(reg, "floodgate", Balance.FLOODGATE_RADIUS, Balance.FLOODGATE_MAX);
        info(reg, "missile_robot", Balance.ROBOT_MAX_PER_CC, Balance.ROBOT_ZONE);
        info(reg, "robot_dock");
        info(reg, "cc_planner", Balance.ROBOT_ZONE);
        info(reg, "xl_transporter", Balance.XL_MAX_PER_CC);
        info(reg, "xl_dock");
        info(reg, "missile_depot", Balance.DEPOT_CELLS, Balance.ROBOT_ZONE, Proc.n(Balance.DEPOT_FE_PER_MOVE));
        info(reg, "missile_teleporter", Proc.n(Balance.TP_FE_PER_JUMP), Balance.TP_COOLDOWN_TICKS / 1200, Balance.TP_MAX_LINKS);
        info(reg, "link_card", Balance.TP_MAX_LINKS);
        info(reg, "parts_runner", Balance.RUNNER_MAX_PER_CC);
        info(reg, "runner_charger");
        info(reg, "runner_upgrade_kit", Balance.RUNNER_UP_STACKS, Balance.RUNNER_UP_ITEMS);
        info(reg, "laser_seeker", Balance.SEEKER_RADIUS);
        info(reg, "heat_seeker", Balance.SEEKER_RADIUS);
        info(reg, "arm_seeker", Balance.SEEKER_RADIUS);
        info(reg, "laser_tripod", Balance.BEAM_RANGE);
        info(reg, "laser_receiver", Balance.LWR_RADIUS);
        info(reg, "laser_tablet", Balance.TABLET_RANGE);

        info(reg, "motor_hypersonic", Proc.n((long) Balance.HYPER_SPEED), Balance.SEEKER_RADIUS, Balance.HYPER_WARN);
        info(reg, "tv_module", Proc.n((long) Balance.TV_CONE));
        tip(reg, "cam_pod");
        tip(reg, "laser_designator_pod");
        info(reg, "manpads_launcher", Balance.HAND_LOCK_TICKS / 20, Proc.n((long) Balance.MANPADS_RANGE));
        info(reg, "javelin_launcher", Balance.HAND_LOCK_TICKS / 20, Proc.n((long) Balance.JAV_RANGE));
        info(reg, "bomb_rack", Balance.SALVO_PULSE);
        info(reg, "bomb_bay", Balance.SALVO_PULSE);
        info(reg, "bomb_sight", Balance.SIGHT_LINK);
        for (BombType t : BombType.values()) {
            ItemStack s = Proc.stack(t.id, 1);
            if (!s.isEmpty()) reg.addItemStackInfo(s, Component.translatable("tooltip.wmd." + t.id), Component.translatable(t.heavy ? "tooltip.wmd.bomb.heavy" : "tooltip.wmd.bomb.light"),
                    Component.translatable("jei.wmd.info.bomb"));
        }
        info(reg, "drone_t1", Proc.n(Balance.DRONE_T1_RANGE), Balance.DRONE_T1_BATTERY / 20);
        info(reg, "drone_t2", Proc.n(Balance.DRONE_T2_RANGE), Balance.DRONE_T2_BATTERY / 20);
        info(reg, "drone_t3", DroneCatapultBlockEntity.BOMBS, Proc.n(Balance.DRONE_T3_RANGE));
        info(reg, "drone_t4", Balance.RUNWAY_W, Balance.RUNWAY_L, DroneModuleBlockEntity.PYLONS, Proc.n(Balance.DRONE_T4_RANGE));
        info(reg, "drone_hive", DroneHiveBlockEntity.BAYS);
        info(reg, "drone_catapult", DroneCatapultBlockEntity.BOMBS);
        info(reg, "drone_hangar", Balance.RUNWAY_W, Balance.RUNWAY_L);
        info(reg, "runway_tile", Balance.RUNWAY_W, Balance.RUNWAY_L);
        info(reg, "gas_mask", Balance.GAS_MASK_FILTER);

        info(reg, "fusion_reactor", Proc.n((long) Balance.FUSION_MAX_FE * 20), Proc.n((long) Balance.FUSION_IGNITION_FE), Proc.n(Balance.FUSION_RAMP_TICKS / 20));
        info(reg, "lithium_dust", Balance.MGCELL_LI_EVERY);
        info(reg, "breeder_rod", Balance.BREEDER_TICKS / 1200, Balance.BREEDER_SLOTS);
        info(reg, "irradiated_breeder_rod", Proc.n(Balance.BREEDER_TRITIUM_MB));
        info(reg, "superconducting_coil");
        info(reg, "laser_defence", Proc.n(Balance.LD_RANGE), Proc.n(Balance.LD_FIRE_FE));
        info(reg, "swarm_launcher", Proc.n(Balance.SW_CELLS), Proc.n(Balance.SW_RANGE), Proc.n(Balance.SW_SALVO_FE));
        info(reg, "swarm_drone_pack", Proc.n(Balance.SW_PACK));
        info(reg, "station_battery_rack");
        info(reg, "deadhand_bunker", Proc.n(Balance.DH_MAX_PADS), Proc.n(Balance.DH_COUNTDOWN / 20));
        info(reg, "warhead_nanite", Proc.n(Balance.NANITE_R), Proc.n(Balance.NANITE_TICKS / 20));
        info(reg, "warhead_gravity", Proc.n(Balance.GRAV_R), Proc.n(Balance.GRAV_TICKS / 20));
        info(reg, "warhead_cryo", Proc.n(Balance.CRYO_R), Proc.n(Balance.CRYO_TICKS / 20));
        info(reg, "forcefield_generator", Balance.FF_R_MIN, Balance.FF_R_MAX, Proc.n(Balance.FF_BUFFER_FE), Proc.n(Balance.FF_CHARGE_IN));

        info(reg, "antimatter_collider", Proc.n((long) Balance.AM_FE_PER_TICK * 20), Balance.AM_CYCLE_TICKS / 1200);
        info(reg, "antimatter_store", Balance.AM_STORE_CELLS, Proc.n(Balance.AM_STORE_FE * 20), Balance.AM_STORE_BACKUP_TICKS / 20);
        info(reg, "empty_trap_cell");
        info(reg, "antimatter_cell", Balance.AM_CYCLE_TICKS / 1200);

        info(reg, "railgun", Proc.n((long) Balance.RG_FE_PER_TICK * 20), Balance.RG_CHARGE_TICKS / 1200, (int) Balance.RG_ABORT_REACH);
        info(reg, "railgun_slug");
        info(reg, "tungsten_tile");
        info(reg, "leaflet", Balance.LEAFLET_DROPS);

        for (com.ashraf.wmd.WmdFluids.Entry e : com.ashraf.wmd.WmdFluids.ALL)
            reg.addIngredientInfo(new net.neoforged.neoforge.fluids.FluidStack(e.source().get(), 1000), mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK,
                    Component.translatable("jei.wmd.fluid." + e.id()), Component.translatable("jei.wmd.fluid.lookup"));
    }

    private static void tip(IRecipeRegistration reg, String id) {
        ItemStack s = Proc.stack(id, 1);
        if (!s.isEmpty()) reg.addItemStackInfo(s, Component.translatable("tooltip.wmd." + id));
    }
}
