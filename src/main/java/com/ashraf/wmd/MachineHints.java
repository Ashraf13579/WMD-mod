package com.ashraf.wmd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public final class MachineHints {

    private MachineHints() {}

    public record Hint(Component need, Component how) {}

    private static final int IDLE = 0, RUNNING = 1, NO_POWER = 2, OUT_FULL = 3, NO_INPUT = 4, NO_ELECTRODE = 5, NO_WATER = 6, NO_IMPELLER = 7,
            NO_DEPOSIT = 8, WRONG_BIT = 9, NO_BIT = 10, NO_BLADES = 11;

    private static final Map<String, String> HOW = new HashMap<>();

    private static final java.util.Set<String> SHORT_NEED = new java.util.HashSet<>();

    private static final Map<String, Integer> NEED = new HashMap<>();

    private static final Map<String, String> OVERRIDE = new HashMap<>();

    private static void how(String hintId, String... inputs) { for (String i : inputs) HOW.put(i, hintId); }

    static {

        how("crude_oil", "fluid:wmd:crude_oil");
        how("desalter", "fluid:wmd:desalted_crude", "fluid:wmd:brine");
        how("furnace", "fluid:wmd:hot_crude");
        how("atmospheric", "fluid:wmd:lpg", "fluid:wmd:naphtha", "fluid:wmd:kerosene", "fluid:wmd:diesel", "fluid:wmd:fuel_oil");
        how("vacuum", "fluid:wmd:lvgo", "fluid:wmd:hvgo", "fluid:wmd:bitumen");
        how("hydrogen", "fluid:wmd:hydrogen");

        how("chlorine", "fluid:wmd:chlorine");
        how("ticl4", "fluid:wmd:titanium_tetrachloride");
        how("mgcl2", "fluid:wmd:magnesium_chloride");
        how("water", "fluid:minecraft:water");
        how("deuterium", "fluid:wmd:deuterium");
        how("tritium", "fluid:wmd:tritium");
        how("breeder", "item:wmd:breeder_rod");

        how("ti_conc", "item:wmd:titanium_concentrate");
        how("w_conc", "item:wmd:tungsten_concentrate");
        how("coke", "item:wmd:petroleum_coke");
        how("magnesium", "item:wmd:magnesium_ingot");
        how("sponge", "item:wmd:titanium_sponge");
        how("bauxite", "item:wmd:raw_bauxite");
        how("nitrate", "item:wmd:nitrate_dust");
        how("raw", "item:wmd:raw_titanium", "item:wmd:raw_tungsten", "item:wmd:raw_uranium");
        how("ti_ingot", "item:wmd:titanium_ingot");
        how("chip_blank", "item:wmd:chip_blank");
        how("chip", "item:wmd:chip_metallurgy_2", "item:wmd:chip_mining_1", "item:wmd:chip_power_2", "item:wmd:chip_rocketry_2", "item:wmd:chip_defence_2", "item:wmd:chip_warheads_2");
        how("core_sample", "item:wmd:rutile_core_sample", "item:wmd:wolframite_core_sample", "item:wmd:uraninite_core_sample");
        how("chunk", "item:wmd:rutile_chunk", "item:wmd:wolframite_chunk", "item:wmd:uraninite_chunk");
        how("electrode", "item:wmd:carbon_electrode");
        how("acid", "item:wmd:acid_catalyst");
        how("canister", "item:wmd:fuel_canister", "item:wmd:alkylate_canister");
        how("cruise", "item:wmd:cruise_missile");

        how("plate", "item:wmd:steel_plate", "item:wmd:aluminium_plate", "item:wmd:titanium_plate");
        how("wire", "item:wmd:copper_wire", "item:wmd:gold_wire", "item:wmd:aluminium_cable");
        how("metal_block", "item:wmd:steel_block", "item:wmd:aluminium_block", "item:wmd:titanium_block");

        how("w_oxide", "item:wmd:tungsten_oxide");
        how("w_powder", "item:wmd:tungsten_powder");
        how("metal_block", "item:wmd:tungsten_block");
        how("plate", "item:wmd:tungsten_plate");

        how("u_conc", "item:wmd:uranium_concentrate");
        how("caustic", "item:wmd:caustic_soda");
        how("yellowcake", "item:wmd:yellowcake");
        how("fuel_rod", "item:wmd:fuel_rod");
        how("steam", "fluid:wmd:steam"); how("spent_steam", "fluid:wmd:spent_steam"); how("spent_fuel", "item:wmd:spent_fuel_rod");
        how("torpedo", "item:wmd:torpedo_t1");
        SHORT_NEED.add("u_conc");
        SHORT_NEED.add("torpedo");
        SHORT_NEED.addAll(List.of("ticl4", "mgcl2", "ti_conc", "w_conc", "arc_feed", "raw_ore", "fuel", "water", "converter", "power", "metal_block", "wire_feed"));

        NEED.put("coker|fluid:wmd:bitumen", Balance.COKER_BITUMEN_MB);
        NEED.put("chlor_alkali_cell|fluid:wmd:brine", Balance.CELL_BRINE_MB);
        NEED.put("chlorinator|fluid:wmd:chlorine", Balance.CHLOR_CL_MB);
        NEED.put("oxygen_station|fluid:minecraft:water", Balance.O2_STATION_WATER_MB);
        NEED.put("chlorinator|item:wmd:titanium_concentrate", Balance.CHLOR_CONC);
        NEED.put("chlorinator|item:wmd:petroleum_coke", Balance.CHLOR_COKE);
        NEED.put("kroll_reactor|fluid:wmd:titanium_tetrachloride", Balance.KROLL_TICL_MB);
        NEED.put("kroll_reactor|item:wmd:magnesium_ingot", Balance.KROLL_MG);
        NEED.put("magnesium_cell|fluid:wmd:magnesium_chloride", 0);
        NEED.put("magnesium_cell|fluid:wmd:brine", Balance.MGCELL_BRINE_MB);
        NEED.put("nitration_reactor|fluid:wmd:naphtha", Balance.NITRO_NAPHTHA_MB);
        NEED.put("nitration_reactor|item:wmd:nitrate_dust", Balance.NITRO_DUST_PER_CYCLE);
        NEED.put("electrolyser|item:wmd:raw_bauxite", Balance.ELECTRO_BAUXITE_PER_CYCLE);
        NEED.put("electrolyser|item:wmd:carbon_electrode", 0);
        NEED.put("arc_furnace|item:wmd:titanium_sponge", Balance.ARC_TI_CONC);
        NEED.put("arc_furnace|item:wmd:tungsten_concentrate", Balance.ARC_W_CONC);
        OVERRIDE.put("arc_furnace|item:wmd:tungsten_concentrate", "arc_feed");
        NEED.put("arc_furnace|item:wmd:petroleum_coke", 0);
        NEED.put("arc_furnace|item:wmd:carbon_electrode", 0);
        NEED.put("mining_rig|fluid:minecraft:water", Balance.RIG_WATER_MB);
        NEED.put("gas_turbine|item:wmd:turbine_blades", 0);
        NEED.put("roaster|item:wmd:tungsten_concentrate", Balance.ROAST_CONC);
        NEED.put("roaster|fluid:wmd:lpg", Balance.ROAST_LPG_MB);
        NEED.put("hydrogen_furnace|item:wmd:tungsten_oxide", Balance.H2F_OXIDE);
        NEED.put("hydrogen_furnace|fluid:wmd:hydrogen", Balance.H2F_H2_MB);
        NEED.put("sinter_press|item:wmd:tungsten_powder", Balance.SINTER_INGOT_POWDER);

        NEED.put("satellite_facility|item:wmd:titanium_plate", Balance.SATFAC_TITANIUM);
        NEED.put("satellite_facility|item:wmd:silicon", Balance.SATFAC_SILICON);
        NEED.put("satellite_facility|item:wmd:advanced_circuit", Balance.SATFAC_CIRCUITS);
        NEED.put("satellite_facility|item:wmd:aluminium_plate", Balance.SATFAC_ALUMINIUM);
        NEED.put("satellite_facility|item:wmd:gold_wire", Balance.SATFAC_GOLD_WIRE);
        NEED.put("leach_tank|item:wmd:uranium_concentrate", Balance.LEACH_CONC);
        NEED.put("leach_tank|item:wmd:caustic_soda", Balance.LEACH_CAUSTIC);
        NEED.put("leach_tank|fluid:minecraft:water", Balance.LEACH_WATER_MB);
        NEED.put("centrifuge|item:wmd:yellowcake", Balance.CENT_YELLOWCAKE);
        NEED.put("reprocessing_plant|item:wmd:spent_fuel_rod", Balance.REPRO_SPENT);
        NEED.put("reprocessing_plant|item:wmd:caustic_soda", Balance.REPRO_CAUSTIC);
        NEED.put("reprocessing_plant|fluid:minecraft:water", Balance.REPRO_WATER_MB);
        NEED.put("nuclear_reactor|item:wmd:fuel_rod", 1);
        NEED.put("nuclear_reactor|fluid:minecraft:water", 0);
        NEED.put("alkylation_unit|item:wmd:fuel_canister", 0);
        NEED.put("cruise_launcher|item:wmd:cruise_missile", 0);

        OVERRIDE.put("crusher|item:wmd:raw_tungsten", "raw_ore");
        OVERRIDE.put("gas_turbine|fluid:wmd:lpg", "fuel");
        for (String ingot : List.of("minecraft:copper_ingot", "minecraft:gold_ingot", "wmd:aluminium_ingot"))
            OVERRIDE.put("wire_drawer|item:" + ingot, "wire_feed");
    }

    private static Component how(String id) { return Component.translatable("gui.wmd.hint." + id + ".how"); }

    public static Hint of(MachineMenu menu) {
        GuiMachine m = menu.machine();
        if (m == null) return null;
        int st = menu.status();
        String block = m instanceof BlockEntity be ? BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath() : "";
        if (GuiMachine.lowPower(st) && m.guiFeUse() > 0)
            return new Hint(Component.translatable("gui.wmd.hint.low_power", GuiMachine.lowPowerPct(st), String.format("%,d", m.guiFeUse() * 20)), how("power"));
        if ((st == NO_INPUT || st == IDLE) && block.equals("magnesium_cell")) {
            int mg = menu.tankAmount(0);
            if (mg > 0 && mg < Balance.MGCELL_MGCL_MB)
                return new Hint(Component.translatable("gui.wmd.hint.mgcell_either", Balance.MGCELL_MGCL_MB - mg, String.format("%,d", Balance.MGCELL_BRINE_MB)), how("mgcl2"));
        }
        switch (st) {
            case RUNNING: return null;
            case NO_POWER:
                return m.guiFeUse() > 0 ? new Hint(Component.translatable("gui.wmd.hint.power", String.format("%,d", m.guiFeUse() * 20)), how("power")) : null;
            case OUT_FULL: return new Hint(null, how("output_full"));
            case NO_ELECTRODE: return new Hint(null, how("electrode"));
            case NO_IMPELLER: return new Hint(null, how("impeller"));
            case NO_DEPOSIT: return new Hint(null, how("deposit"));
            case WRONG_BIT: return new Hint(null, how("wrong_bit"));
            case NO_BIT: return new Hint(null, how("bit"));
            case NO_BLADES: return new Hint(null, how("blades"));
            case NO_WATER:
                return hasInput(menu, m, "fluid:minecraft:water") ? new Hint(Component.translatable("gui.wmd.hint.water"), how("water")) : null;
            case NO_INPUT:
                if (block.equals("grid_converter")) return new Hint(Component.translatable("gui.wmd.hint.converter"), how("converter"));
                return missing(menu, m, block, false);
            case IDLE:
                return missing(menu, m, block, true);
            default: return null;
        }
    }

    private static boolean hasInput(MachineMenu menu, GuiMachine m, String key) {
        List<Fluid> fl = m.guiTankFluids();
        for (int t = 0; t < Math.min(m.guiTanks().size(), MachineMenu.MAX_TANKS); t++)
            if (!isItem(m, t) && m.guiTankColoured() && !m.guiTankOutput(t) && t < fl.size() && key.equals(fluidKey(fl.get(t)))) return true;
        return false;
    }

    private static boolean isItem(GuiMachine m, int t) { List<ItemStack> icons = m.guiSlotIcons(); return t < icons.size() && !icons.get(t).isEmpty(); }

    private static String fluidKey(Fluid f) { return f == null || f == Fluids.EMPTY ? null : "fluid:" + BuiltInRegistries.FLUID.getKey(f); }

    private static Hint missing(MachineMenu menu, GuiMachine m, String block, boolean emptyOnly) {
        int n = Math.min(m.guiTanks().size(), MachineMenu.MAX_TANKS);
        List<Fluid> fl = m.guiTankFluids();
        List<ItemStack> icons = m.guiSlotIcons();
        for (int t = 0; t < n; t++) {
            String key;
            Component name;
            boolean empty = menu.tankAmount(t) <= 0;
            if (isItem(m, t)) {
                if (m.guiSlotOutput(t)) continue;
                int si = t - menu.itemTankOffset();
                ItemStack in = si >= 0 && si < menu.machineSlotCount() ? menu.slots.get(si).getItem() : ItemStack.EMPTY;
                ItemStack what = in.isEmpty() ? icons.get(t) : in;
                key = "item:" + BuiltInRegistries.ITEM.getKey(what.getItem());
                name = what.getHoverName();
                empty = in.isEmpty();
            } else {
                if (!m.guiTankColoured() || m.guiTankOutput(t) || t >= fl.size()) continue;
                key = fluidKey(fl.get(t));
                if (key == null) continue;
                name = fl.get(t).getFluidType().getDescription();
            }
            int need = NEED.getOrDefault(block + "|" + key, 1);
            if (m instanceof ResearchStationBlockEntity rs && rs.research() != null) {
                ResearchRecipes.Research r = rs.research();
                int si = t - menu.itemTankOffset();
                if (!isItem(m, t)) need = r.fluidMb();
                else if (si >= 0 && si < r.samples().size()) need = r.samples().get(si).count();
            }
            if (m instanceof SinterPressBlockEntity sp && isItem(m, t)) need = sp.powderPerRun();
            if (need <= 0) continue;
            if (emptyOnly ? !empty : menu.tankAmount(t) >= need) continue;
            String id = (key.startsWith("fluid:") || empty) ? OVERRIDE.get(block + "|" + key) : null;
            if (id == null) id = HOW.get(key);
            if (id == null) return new Hint(Component.translatable("gui.wmd.hint.needs", name), null);
            Component needLine = SHORT_NEED.contains(id) ? Component.translatable("gui.wmd.hint." + id) : Component.translatable("gui.wmd.hint.needs", name);
            return new Hint(needLine, how(id));
        }
        return null;
    }
}
