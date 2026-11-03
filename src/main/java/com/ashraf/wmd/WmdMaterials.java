package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

public final class WmdMaterials {
    private WmdMaterials() {}

    public static final List<DeferredItem<?>> TAB_ITEMS = new ArrayList<>();

    private static final String[] STONE_ORES = {"tin", "lead", "tungsten", "silver", "platinum", "titanium"};

    private static final String[] PLAIN_ITEMS = {
            "tin_ingot", "lead_ingot", "tungsten_ingot", "silver_ingot", "platinum_ingot",
            "aluminium_ingot", "bronze_ingot", "steel_ingot",
            "raw_tin", "raw_lead", "raw_tungsten", "raw_silver", "raw_platinum", "raw_bauxite", "raw_titanium",
            "titanium_ingot", "tungsten_concentrate", "titanium_concentrate", "tin_dust", "lead_dust", "silver_dust", "platinum_dust",
            "nitrate_dust", "silicon", "refractory_brick", "carbon_electrode",
            "basic_circuit", "advanced_circuit", "drill_bit", "rocket_engine", "aluminium_airframe",
            "he_charge", "petroleum_coke", "heat_shield_tile", "flare_pack", "chaff_pod",

            "rutile_chunk", "wolframite_chunk", "uraninite_chunk", "rutile_core_sample", "wolframite_core_sample", "uraninite_core_sample",
            "bronze_impeller", "titanium_impeller", "turbine_blades", "caustic_soda", "carbide_drill_bit", "diamond_drill_bit", "raw_uranium",
            "magnesium_ingot", "titanium_sponge",

            "aluminium_casing", "titanium_casing",

            "steel_plate", "aluminium_plate", "titanium_plate", "copper_wire", "gold_wire", "aluminium_cable",

            "uranium_concentrate", "yellowcake", "enriched_uranium", "depleted_uranium", "fuel_rod",

            "spent_fuel_rod", "plutonium",

            "tungsten_oxide", "tungsten_powder", "tungsten_rod", "tungsten_plate", "tungsten_alloy_casing",

            "lithium_dust", "breeder_rod", "irradiated_breeder_rod",

            "superconducting_coil", "tungsten_tile",

            "coke", "crushed_iron", "crushed_copper", "crushed_gold", "crushed_tin", "crushed_lead"};

    public static final String[] STORAGE_BLOCKS = {"steel_block", "aluminium_block", "titanium_block", "tungsten_block",
            "tin_block", "lead_block", "bronze_block", "silver_block", "platinum_block", "magnesium_block"};

    public static final String[] DEPOSITS = {"rutile", "wolframite", "uraninite"};

    public static void init() {
        for (String o : STONE_ORES) {
            ore(o + "_ore", false);
            ore("deepslate_" + o + "_ore", true);
        }
        ore("bauxite_ore", false);
        oreWith("nitrate_ore", 0.8F, SoundType.SAND, MapColor.SAND);
        for (String o : DEPOSITS) {
            DeferredBlock<DepositOreBlock> b = Wmd.BLOCKS.registerBlock(o + "_deposit", p -> new DepositOreBlock(o), BlockBehaviour.Properties.of());
            TAB_ITEMS.add(Wmd.ITEMS.registerSimpleBlockItem(o + "_deposit", b));
        }
        for (String id : STORAGE_BLOCKS) {
            DeferredBlock<Block> b = Wmd.BLOCKS.registerBlock(id, Block::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.METAL));
            TAB_ITEMS.add(Wmd.ITEMS.registerSimpleBlockItem(id, b));
        }
        for (String id : PLAIN_ITEMS) {
            TAB_ITEMS.add(Wmd.ITEMS.registerSimpleItem(id, new Item.Properties()));
        }
    }

    private static void ore(String id, boolean deep) {
        oreWith(id, deep ? 4.5F : 3.0F, deep ? SoundType.DEEPSLATE : SoundType.STONE, deep ? MapColor.DEEPSLATE : MapColor.STONE);
    }

    private static void oreWith(String id, float hardness, SoundType sound, MapColor map) {
        DeferredBlock<Block> b = Wmd.BLOCKS.registerBlock(id, Block::new,
                BlockBehaviour.Properties.of().mapColor(map).requiresCorrectToolForDrops()
                        .strength(hardness, 3.0F).sound(sound));
        DeferredItem<BlockItem> bi = Wmd.ITEMS.registerSimpleBlockItem(id, b);
        TAB_ITEMS.add(bi);
    }

    public static void addToTab(CreativeModeTab.Output output) {
        for (DeferredItem<?> i : TAB_ITEMS) output.accept(i.get());
    }
}
