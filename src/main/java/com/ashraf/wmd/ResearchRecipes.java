package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public final class ResearchRecipes {
    private ResearchRecipes() {}

    public record Sample(String itemOrTag, int count) {}

    public record Research(String id, String chipId, List<Sample> samples, String fluidId, int fluidMb, int ticks, int fePerTick, List<String> unlocks) {}

    private static final Map<String, Research> ALL = new LinkedHashMap<>();

    private static void add(String id, List<Sample> samples, String fluid, int ticks, int fe, String... unlocks) {
        ALL.put(id, new Research(id, "wmd:chip_" + id, List.copyOf(samples), fluid, fluid == null ? 0 : Balance.RES_FLUID_MB, ticks, fe, List.of(unlocks)));
    }

    static {
        add("metallurgy_2", List.of(new Sample("wmd:rutile_core_sample", 2), new Sample("wmd:rutile_chunk", 2)), "wmd:brine",
                Balance.RES_METALLURGY_TICKS, Balance.RES_METALLURGY_FE,
                "wmd:chlorinator", "wmd:kroll_reactor", "wmd:magnesium_cell", "wmd:carbide_drill_bit", "wmd:titanium_impeller", "wmd:turbine_blades");
        add("mining_1", List.of(new Sample("#wmd:core_samples", 2), new Sample("#wmd:raw_ores", 4)), null,
                Balance.RES_MINING_TICKS, Balance.RES_MINING_FE,
                "wmd:mining_rig");
        add("power_2", List.of(new Sample("wmd:titanium_ingot", 2)), "wmd:naphtha",
                Balance.RES_POWER_TICKS, Balance.RES_POWER_FE,
                "wmd:gas_turbine", "wmd:grid_converter");
        add("rocketry_2", List.of(new Sample("wmd:titanium_ingot", 2), new Sample("wmd:motor_t1", 1), new Sample("wmd:guidance_t1", 1)), null,
                Balance.RES_ROCKETRY_TICKS, Balance.RES_ROCKETRY_FE,
                "wmd:bay_size_kit", "wmd:interceptor_silo_t3", "wmd:launch_terminal_3", "wmd:mlrs", "wmd:cruise_launcher");
        add("defence_2", List.of(new Sample("wmd:titanium_ingot", 2), new Sample("wmd:interceptor_t1", 1), new Sample("wmd:advanced_circuit", 1)), null,
                Balance.RES_DEFENCE_TICKS, Balance.RES_DEFENCE_FE,
                "wmd:ciws", "wmd:interceptor_battery_t2", "wmd:counter_battery_radar", "wmd:howitzer", "wmd:cc_artillery_console", "wmd:cc_iron_dome");
        add("warheads_2", List.of(new Sample("wmd:warhead_he_t1", 1), new Sample("wmd:magnesium_ingot", 4), new Sample("wmd:nitrate_dust", 2)), null,
                Balance.RES_WARHEADS_TICKS, Balance.RES_WARHEADS_FE,
                "wmd:warhead_bunker", "wmd:warhead_emp", "wmd:warhead_nuke", "wmd:warhead_rod", "wmd:warhead_hemp", "wmd:warhead_seismic");

        add("metallurgy_3", List.of(new Sample("wmd:wolframite_core_sample", 2), new Sample("wmd:tungsten_ingot", 2)), "wmd:hydrogen",
                Balance.RES_METALLURGY3_TICKS, Balance.RES_METALLURGY3_FE,
                "wmd:roaster", "wmd:hydrogen_furnace", "wmd:sinter_press", "wmd:super_gun");
        add("nuclear_1", List.of(new Sample("wmd:uraninite_core_sample", 2), new Sample("wmd:uraninite_chunk", 2), new Sample("wmd:caustic_soda", 1)), null,
                Balance.RES_NUCLEAR_TICKS, Balance.RES_NUCLEAR_FE,
                "wmd:leach_tank", "wmd:centrifuge", "wmd:nuclear_reactor", "wmd:cooling_tower", "wmd:steam_turbine", "wmd:reactor_steam_port", "wmd:reprocessing_plant");

        add("fusion_1", List.of(new Sample("wmd:superconducting_coil", 1), new Sample("wmd:irradiated_breeder_rod", 1), new Sample("wmd:plutonium", 1)), "wmd:heavy_water",
                Balance.RES_FUSION_TICKS, Balance.RES_FUSION_FE,
                "wmd:fusion_reactor");

        add("antimatter_1", List.of(new Sample("wmd:superconducting_coil", 2), new Sample("wmd:tungsten_tile", 2), new Sample("wmd:chip_fusion_1", 1)), "wmd:tritium",
                Balance.RES_ANTIMATTER_TICKS, Balance.RES_ANTIMATTER_FE,
                "wmd:antimatter_collider", "wmd:antimatter_store", "wmd:warhead_antimatter", "wmd:missile_teleporter", "wmd:power_core");

        add("railgun_1", List.of(new Sample("wmd:chip_antimatter_1", 1), new Sample("wmd:tungsten_block", 2), new Sample("wmd:superconducting_coil", 2)), "wmd:tritium",
                Balance.RES_RAILGUN_TICKS, Balance.RES_RAILGUN_FE,
                "wmd:railgun", "wmd:railgun_slug");

        add("space_1", List.of(new Sample("wmd:satellite_part", 1), new Sample("wmd:advanced_circuit", 2), new Sample("wmd:plutonium", 1)), null,
                Balance.RES_SPACE_TICKS, Balance.RES_SPACE_FE,
                "wmd:ground_station", "wmd:rtg_cell", "wmd:satellite_facility",
                "wmd:cc_building", "wmd:cc_map_table", "wmd:cc_warning_board", "wmd:cc_recon_wall", "wmd:cc_air_defence", "wmd:cc_launch_console", "wmd:cc_retarget_console", "wmd:cc_strike_planner", "wmd:cc_missile_cam",
                "wmd:cc_sat_globe", "wmd:cc_ew_rack", "wmd:cc_gps_rack", "wmd:cc_alarm_board", "wmd:cc_map_printer", "wmd:cc_comms_array", "wmd:cc_interceptor_console");

        add("space_2", List.of(new Sample("wmd:chip_space_1", 1), new Sample("wmd:chip_fusion_1", 1), new Sample("wmd:titanium_casing", 2)), "wmd:hydrogen",
                Balance.RES_SPACE2_TICKS, Balance.RES_SPACE2_FE,
                "wmd:eva_helmet", "wmd:eva_torso", "wmd:eva_legs", "wmd:eva_boots", "wmd:oxygen_tank", "wmd:oxygen_station", "wmd:crew_capsule",
                "wmd:orbital_frame", "wmd:life_support_unit");

        add("space_3", List.of(new Sample("wmd:chip_space_2", 1), new Sample("wmd:chip_antimatter_1", 1), new Sample("wmd:titanium_casing", 4)), "wmd:hydrogen",
                Balance.RES_SPACE3_TICKS, Balance.RES_SPACE3_FE,
                "wmd:station_kit_core", "wmd:station_kit_habitat", "wmd:station_kit_wing",
                "wmd:station_kit_sensor", "wmd:station_kit_laser", "wmd:station_kit_bay", "wmd:station_kit_turret", "wmd:weapon_core", "wmd:orbital_rod", "wmd:tug_core");
    }

    public static List<Research> all() { return List.copyOf(ALL.values()); }

    public static Research byId(String id) { return id == null ? null : ALL.get(id); }

    public static Research byChip(Item chip) {
        String key = BuiltInRegistries.ITEM.getKey(chip).toString();
        for (Research r : ALL.values()) if (r.chipId().equals(key)) return r;
        return null;
    }

    public static boolean matches(Sample s, ItemStack stack) {
        if (stack.isEmpty()) return false;
        String k = s.itemOrTag();
        if (k.startsWith("#")) return stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(k.substring(1))));
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(k);
    }

    public static List<ItemStack> stacks(Sample s) {
        List<ItemStack> l = new ArrayList<>();
        String k = s.itemOrTag();
        if (k.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(k.substring(1)));
            BuiltInRegistries.ITEM.getTag(tag).ifPresent(set -> { for (Holder<Item> h : set) l.add(new ItemStack(h.value(), s.count())); });
        } else {
            Item it = item(k);
            if (it != Items.AIR) l.add(new ItemStack(it, s.count()));
        }
        return l;
    }

    public static ItemStack icon(Sample s, long gameTime) {
        List<ItemStack> l = stacks(s);
        if (l.isEmpty()) return new ItemStack(Items.BARRIER);
        return l.get((int) ((gameTime / 20) % l.size()));
    }

    public static net.minecraft.network.chat.Component name(Sample s) {
        String k = s.itemOrTag();
        if (k.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.parse(k.substring(1));
            return net.minecraft.network.chat.Component.translatable("tag.item." + rl.getNamespace() + "." + rl.getPath().replace('/', '.'));
        }
        return item(k).getDescription();
    }

    public static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }

    public static ItemStack chip(Research r) { return new ItemStack(item(r.chipId())); }

    public static Fluid fluid(Research r) { return r == null || r.fluidId() == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(ResourceLocation.parse(r.fluidId())); }
}
