package com.ashraf.wmd.jei;

import static com.ashraf.wmd.jei.Proc.any;
import static com.ashraf.wmd.jei.Proc.anyOf;
import static com.ashraf.wmd.jei.Proc.flow;
import static com.ashraf.wmd.jei.Proc.fluid;
import static com.ashraf.wmd.jei.Proc.item;
import static com.ashraf.wmd.jei.Proc.makes;
import static com.ashraf.wmd.jei.Proc.n;
import static com.ashraf.wmd.jei.Proc.note;
import static com.ashraf.wmd.jei.Proc.sec;
import static com.ashraf.wmd.jei.Proc.stack;
import static com.ashraf.wmd.jei.Proc.time;
import static com.ashraf.wmd.jei.Proc.uses;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ashraf.wmd.Alkylate;
import com.ashraf.wmd.Balance;
import com.ashraf.wmd.BombLineRecipes;
import com.ashraf.wmd.BombType;
import com.ashraf.wmd.DroneRecipes;
import com.ashraf.wmd.DroneType;
import com.ashraf.wmd.GeneratorEngineBlockEntity;
import com.ashraf.wmd.LabModuleBlockEntity;
import com.ashraf.wmd.LabRecipes;
import com.ashraf.wmd.MissilePartItem;
import com.ashraf.wmd.PayloadUnitItem;
import com.ashraf.wmd.ResearchRecipes;
import com.ashraf.wmd.Satellites;
import com.ashraf.wmd.ShellPressBlockEntity;
import com.ashraf.wmd.WarheadType;
import com.ashraf.wmd.Wmd;
import com.ashraf.wmd.WmdFluids;

import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public final class WmdRecipes {

    private WmdRecipes() {}

    record Def(RecipeType<Proc> type, Component title, ItemStack icon, List<ItemStack> catalysts, List<Proc> recipes) {}

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    public static RecipeType<Proc> type(String id) { Def d = DEFS.get(id); return d == null ? null : d.type(); }

    private static Fluid f(WmdFluids.Entry e) { return e.source().get(); }

    private static Proc p(List<Proc.Ing> in, List<Proc.Ing> out, Component... lines) { return new Proc(in, out, List.of(lines)); }

    private static void machine(String id, List<Proc> recipes) {
        ItemStack m = stack(id, 1);
        def(id, Component.translatable("block.wmd." + id), m, m.isEmpty() ? List.of() : List.of(m), recipes);
    }

    private static void def(String id, Component title, ItemStack icon, List<ItemStack> catalysts, List<Proc> recipes) {
        if (recipes.isEmpty()) return;
        DEFS.put(id, new Def(RecipeType.create(Wmd.MODID, "proc_" + id, Proc.class), title, icon, catalysts, recipes));
    }

    public static void registerCategories(IRecipeCategoryRegistration reg) {
        build();
        IGuiHelper gui = reg.getJeiHelpers().getGuiHelper();
        for (Def d : DEFS.values()) reg.addRecipeCategories(new ProcessCategory(gui, d.type(), d.title(), d.icon(), d.recipes()));
    }

    public static void registerRecipes(IRecipeRegistration reg) {
        for (Def d : DEFS.values()) reg.addRecipes(d.type(), d.recipes());
    }

    public static void registerCatalysts(IRecipeCatalystRegistration reg) {
        for (Def d : DEFS.values()) for (ItemStack c : d.catalysts()) if (!c.isEmpty()) reg.addRecipeCatalyst(c, d.type());
    }

    static void build() {
        DEFS.clear();
        oil();
        chemistry();
        power();
        shellPress();
        lab();
        bay();
        droneLine();
        bombLine();
        research();
    }

    private static void oil() {
        machine("pumpjack", List.of(p(List.of(item("crude_oil_deposit", 1)), List.of(fluid(f(WmdFluids.CRUDE_OIL), Balance.PUMPJACK_MB_PER_30S)),
                time(600), uses(Balance.PUMPJACK_FE_PER_TICK), note("jei.wmd.note.pumpjack"))));
        machine("feed_pump", List.of(p(List.of(fluid(f(WmdFluids.CRUDE_OIL), 1000)), List.of(fluid(f(WmdFluids.CRUDE_OIL), 1000)),
                flow(Balance.FEED_PUMP_MB_PER_TICK), uses(Balance.FEED_PUMP_FE_PER_TICK), note("jei.wmd.note.feed_pump"))));
        int brine = (int) Math.round(1000 * (1.0 - Balance.DESALTER_YIELD));
        machine("desalter", List.of(p(List.of(fluid(f(WmdFluids.CRUDE_OIL), 1000)),
                List.of(fluid(f(WmdFluids.DESALTED_CRUDE), 1000 - brine), fluid(f(WmdFluids.BRINE), brine)),
                flow(Balance.DESALTER_MB_PER_TICK), uses(Balance.DESALTER_FE_PER_TICK), note("jei.wmd.note.desalter"))));
        machine("furnace", List.of(p(List.of(fluid(f(WmdFluids.DESALTED_CRUDE), 1000)), List.of(fluid(f(WmdFluids.HOT_CRUDE), 1000)),
                flow(Balance.FURNACE_MB_PER_TICK), uses(Balance.FURNACE_FE_PER_TICK))));
        machine("atmospheric_column", List.of(p(List.of(fluid(f(WmdFluids.HOT_CRUDE), 1000)),
                split(Balance.ADU_YIELD, WmdFluids.LPG, WmdFluids.NAPHTHA, WmdFluids.KEROSENE, WmdFluids.DIESEL, WmdFluids.FUEL_OIL),
                flow(Balance.ADU_MB_PER_TICK), uses(Balance.ADU_FE_PER_TICK), note("jei.wmd.note.column"))));
        machine("vacuum_column", List.of(p(List.of(fluid(f(WmdFluids.FUEL_OIL), 1000)),
                split(Balance.VDU_YIELD, WmdFluids.LVGO, WmdFluids.HVGO, WmdFluids.BITUMEN),
                flow(Balance.VDU_MB_PER_TICK), uses(Balance.VDU_FE_PER_TICK), note("jei.wmd.note.column"))));
        machine("reformer", List.of(p(List.of(fluid(f(WmdFluids.NAPHTHA), 1000)), split(Balance.REFORMER_YIELD, WmdFluids.REFORMATE, WmdFluids.HYDROGEN),
                flow(Balance.REFORMER_MB_PER_TICK), uses(Balance.REFORMER_FE_PER_TICK), note("jei.wmd.note.reformer"))));
        List<Proc> fcc = new ArrayList<>();
        for (WmdFluids.Entry gasOil : List.of(WmdFluids.LVGO, WmdFluids.HVGO))
            fcc.add(p(List.of(fluid(f(gasOil), 1000)), split(Balance.FCC_YIELD, WmdFluids.GASOLINE, WmdFluids.LPG),
                    flow(Balance.FCC_MB_PER_TICK), uses(Balance.FCC_FE_PER_TICK), note("jei.wmd.note.fcc")));
        machine("fcc", fcc);
        machine("hydrotreater", List.of(p(List.of(fluid(f(WmdFluids.KEROSENE), 1000), fluid(f(WmdFluids.HYDROGEN), (int) Math.round(1000 * Balance.HDT_H2_PER_MB))),
                List.of(fluid(f(WmdFluids.JET_FUEL), (int) Math.round(1000 * Balance.HDT_JET_YIELD))),
                flow(Balance.HYDROTREATER_MB_PER_TICK), uses(Balance.HYDROTREATER_FE_PER_TICK), note("jei.wmd.note.hydrotreater"))));
        int lpg = Balance.ALKY_ACID_LPG_MB;
        machine("alkylation_unit", List.of(
                p(List.of(fluid(f(WmdFluids.LPG), lpg), item(new ItemStack(Wmd.ACID_CATALYST.get()))), List.of(fluid(Alkylate.fluid(), (int) Math.round(lpg * Balance.ALKYLATE_YIELD))),
                        time(lpg / Math.max(1, Balance.ALKYLATION_MB_PER_TICK)), uses(Balance.ALKYLATION_FE_PER_TICK), note("jei.wmd.note.alkylation", n(Balance.ALKYLATION_MB_PER_TICK * 20L))),
                p(List.of(item(new ItemStack(Wmd.FUEL_CANISTER.get())), fluid(Alkylate.fluid(), Balance.ALKYLATE_CANISTER_MB)), List.of(item(new ItemStack(Wmd.ALKYLATE_CANISTER.get()))),
                        time(Balance.ALKY_FILL_TICKS), uses(Balance.ALKYLATION_FE_PER_TICK), note("jei.wmd.note.alky_canister"))));
    }

    private static List<Proc.Ing> split(double[] yield, WmdFluids.Entry... products) {
        List<Proc.Ing> l = new ArrayList<>();
        for (int i = 0; i < products.length && i < yield.length; i++) l.add(fluid(f(products[i]), (int) Math.round(1000 * yield[i])));
        return l;
    }

    private static void chemistry() {
        machine("chlor_alkali_cell", List.of(p(List.of(fluid(f(WmdFluids.BRINE), Balance.CELL_BRINE_MB)),
                List.of(fluid(f(WmdFluids.CHLORINE), Balance.CELL_CL_FROM_BRINE), fluid(f(WmdFluids.HYDROGEN), Balance.CELL_H2_FROM_BRINE), item("caustic_soda", Balance.CELL_CAUSTIC)),
                time(Balance.CELL_CYCLE_TICKS), uses(Balance.CELL_FE_PER_TICK)),
                p(List.of(fluid(f(WmdFluids.HEAVY_WATER), Balance.CELL_HW_MB)), List.of(fluid(f(WmdFluids.DEUTERIUM), Balance.CELL_D2_FROM_HW)),
                        time(Balance.CELL_CYCLE_TICKS), uses(Balance.CELL_FE_PER_TICK), note("jei.wmd.note.cell_heavy"))));
        machine("magnesium_cell", List.of(
                p(List.of(fluid(f(WmdFluids.MAGNESIUM_CHLORIDE), Balance.MGCELL_MGCL_MB)),
                        List.of(item("magnesium_ingot", Balance.MGCELL_MG_FROM_MGCL), fluid(f(WmdFluids.CHLORINE), Balance.MGCELL_CL_FROM_MGCL)),
                        time(Balance.MGCELL_CYCLE_TICKS), uses(Balance.MGCELL_FE_PER_TICK), note("jei.wmd.note.mgcell_mgcl")),
                p(List.of(fluid(f(WmdFluids.BRINE), Balance.MGCELL_BRINE_MB)), List.of(item("magnesium_ingot", Balance.MGCELL_MG_FROM_BRINE), item("lithium_dust", 1)),
                        time(Balance.MGCELL_BRINE_TICKS), uses(Balance.MGCELL_FE_PER_TICK), note("jei.wmd.note.mgcell_brine"),
                        Component.translatable("jei.wmd.note.mgcell_lithium", Balance.MGCELL_LI_EVERY))));
        machine("chlorinator", List.of(p(List.of(item("titanium_concentrate", Balance.CHLOR_CONC), item("petroleum_coke", Balance.CHLOR_COKE), fluid(f(WmdFluids.CHLORINE), Balance.CHLOR_CL_MB)),
                List.of(fluid(f(WmdFluids.TITANIUM_TETRACHLORIDE), Balance.CHLOR_TICL_MB)), time(Balance.CHLOR_CYCLE_TICKS), uses(Balance.CHLOR_FE_PER_TICK))));
        machine("kroll_reactor", List.of(p(List.of(fluid(f(WmdFluids.TITANIUM_TETRACHLORIDE), Balance.KROLL_TICL_MB), item("magnesium_ingot", Balance.KROLL_MG)),
                List.of(item("titanium_sponge", Balance.KROLL_SPONGE), fluid(f(WmdFluids.MAGNESIUM_CHLORIDE), Balance.KROLL_MGCL_MB)),
                time(Balance.KROLL_CYCLE_TICKS), uses(Balance.KROLL_FE_PER_TICK), note("jei.wmd.note.kroll"))));
        machine("water_intake", List.of(
                p(List.of(item("bronze_impeller", 1)), List.of(fluid(Fluids.WATER, 1000)), flow(Balance.INTAKE_MB_BRONZE), uses(Balance.INTAKE_FE_PER_TICK),
                        note("jei.wmd.note.intake", n(Balance.INTAKE_LIFE_BRONZE / 1200), Balance.INTAKE_MIN_SOURCES)),
                p(List.of(item("titanium_impeller", 1)), List.of(fluid(Fluids.WATER, 1000)), flow(Balance.INTAKE_MB_TITANIUM), uses(Balance.INTAKE_FE_PER_TICK),
                        note("jei.wmd.note.intake", n(Balance.INTAKE_LIFE_TITANIUM / 1200), Balance.INTAKE_MIN_SOURCES)),

                p(List.of(item("upgrade_heavy_water", 1), fluid(Fluids.WATER, Balance.HEAVY_WATER_FEED_PER_LEVEL * 20)),
                        List.of(fluid(f(WmdFluids.HEAVY_WATER), Balance.HEAVY_WATER_FEED_PER_LEVEL * 20 / Balance.HEAVY_WATER_RATIO)),
                        time(20), uses(Balance.HEAVY_WATER_FE_PER_LEVEL), note("jei.wmd.note.heavy_water", Balance.HEAVY_WATER_RATIO))));

        machine("nuclear_reactor", List.of(p(List.of(item("breeder_rod", 1)), List.of(item("irradiated_breeder_rod", 1)), time(Balance.BREEDER_TICKS),
                note("jei.wmd.note.breeder"))));
        machine("reprocessing_plant", List.of(p(List.of(item("irradiated_breeder_rod", 1)), List.of(fluid(f(WmdFluids.TRITIUM), Balance.BREEDER_TRITIUM_MB)),
                time(Balance.REPRO_CYCLE_TICKS), uses(Balance.REPRO_FE_PER_TICK), note("jei.wmd.note.tritium"))));
        Component bits = note("jei.wmd.note.rig_bits", sec(Balance.RIG_TICKS_STEEL), sec(Balance.RIG_TICKS_CARBIDE), sec(Balance.RIG_TICKS_DIAMOND),
                Balance.RIG_BIT_BLOCKS_STEEL, Balance.RIG_BIT_BLOCKS_CARBIDE, Balance.RIG_BIT_BLOCKS_DIAMOND);
        Component reach = note("jei.wmd.note.rig_reach", Balance.RIG_RADIUS);
        machine("mining_rig", List.of(
                p(List.of(item("rutile_deposit", 1), anyOf(1, "drill_bit", "carbide_drill_bit", "diamond_drill_bit"), fluid(Fluids.WATER, Balance.RIG_WATER_MB)),
                        List.of(item("raw_titanium", Balance.RIG_RAW_PER_BLOCK)), time(Balance.RIG_TICKS_STEEL), uses(Balance.RIG_FE_PER_TICK), bits, reach),
                p(List.of(item("wolframite_deposit", 1), anyOf(1, "drill_bit", "carbide_drill_bit", "diamond_drill_bit"), fluid(Fluids.WATER, Balance.RIG_WATER_MB)),
                        List.of(item("raw_tungsten", Balance.RIG_RAW_PER_BLOCK)), time(Balance.RIG_TICKS_STEEL), uses(Balance.RIG_FE_PER_TICK), bits, reach),
                p(List.of(item("uraninite_deposit", 1), item("diamond_drill_bit", 1), fluid(Fluids.WATER, Balance.RIG_WATER_MB)),
                        List.of(item("raw_uranium", Balance.RIG_RAW_PER_BLOCK)), time(Balance.RIG_TICKS_DIAMOND), uses(Balance.RIG_FE_PER_TICK), note("jei.wmd.note.rig_diamond"), reach)));
    }

    private static void power() {
        int dieselFe = GeneratorEngineBlockEntity.FE_PER_TICK;
        machine("diesel_generator", List.of(p(List.of(fluid(f(WmdFluids.DIESEL), 1000)), List.of(),
                makes(dieselFe), note("jei.wmd.note.gen_fluid", n(Balance.GENERATOR_FE_PER_MB), n(1000L * Balance.GENERATOR_FE_PER_MB), sec(1000 * Balance.GENERATOR_FE_PER_MB / Math.max(1, dieselFe))))));
        List<Proc> coal = new ArrayList<>();
        for (Item fuel : List.of(Items.COAL, Items.CHARCOAL, Items.COAL_BLOCK, stackItem("petroleum_coke"), Items.BLAZE_ROD, Items.OAK_LOG)) {
            if (fuel == null || fuel == Items.AIR) continue;
            int burn;
            try { burn = new ItemStack(fuel).getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING); } catch (RuntimeException e) { burn = 0; }
            if (burn <= 0) continue;
            coal.add(p(List.of(item(new ItemStack(fuel))), List.of(), makes(Balance.COAL_GEN_FE_PER_TICK),
                    note("jei.wmd.note.gen_item", n((long) burn * Balance.COAL_GEN_FE_PER_TICK), sec(burn)), note("jei.wmd.note.coal_gen")));
        }
        machine("coal_generator", coal);

        machine("blast_furnace", List.of(p(List.of(item("minecraft:iron_ingot", Balance.BF_STEEL_IRON), anyOf(Balance.BF_STEEL_COAL, "minecraft:coal", "minecraft:charcoal")),
                List.of(item("steel_ingot", 1)), time(Balance.BF_STEEL_TICKS), note("jei.wmd.note.blast_furnace_fuel", Balance.BF_SMELT_PER_COAL), note("jei.wmd.note.blast_furnace", Balance.BF_BLOWER_SPEED, n(Balance.BF_BLOWER_FE * 20L))),
                p(List.of(item("minecraft:iron_ingot", Balance.BF_STEEL_IRON), item("coke", Balance.BF_STEEL_COKE)), List.of(item("steel_ingot", 1)), time(Balance.BF_STEEL_TICKS)),
                p(List.of(item("minecraft:copper_ingot", Balance.BF_BRONZE_COPPER), item("tin_ingot", Balance.BF_BRONZE_TIN), anyOf(1, "minecraft:coal", "minecraft:charcoal")),
                        List.of(item("bronze_ingot", Balance.BF_BRONZE_OUT)), time(Balance.BF_BRONZE_TICKS))));

        machine("coke_oven", List.of(p(List.of(item("minecraft:coal", Balance.CO_COAL)), List.of(item("coke", 1), fluid(f(WmdFluids.COAL_TAR), Balance.CO_TAR_MB)),
                time(Balance.CO_CYCLE_TICKS), note("jei.wmd.note.coke_oven"))));
        machine("starter_generator", List.of(
                p(List.of(anyOf(1, "minecraft:coal", "minecraft:charcoal", "coke")), List.of(), makes(Balance.SG_FE_PER_TICK), note("jei.wmd.note.coal_gen")),
                p(List.of(fluid(f(WmdFluids.COAL_TAR), 1000)), List.of(), makes(Balance.SG_FE_PER_TICK), note("jei.wmd.note.starter_tar", n((long) Balance.SG_TAR_TICKS_PER_MB * Balance.SG_FE_PER_TICK)))));
        List<Proc> stamps = new ArrayList<>();
        for (String[] m : new String[][] { { "minecraft:raw_iron", "crushed_iron" }, { "minecraft:raw_copper", "crushed_copper" }, { "minecraft:raw_gold", "crushed_gold" },
                { "raw_tin", "crushed_tin" }, { "raw_lead", "crushed_lead" } })
            stamps.add(p(List.of(item(m[0], 1)), List.of(item(m[1], Balance.SM_YIELD)), time(Balance.SM_CYCLE_TICKS), uses(Balance.SM_FE_PER_TICK)));
        machine("stamp_mill", stamps);
        machine("powder_mill", List.of(p(List.of(item("nitrate_dust", Balance.PM_NITRATE), item("minecraft:charcoal", Balance.PM_CHARCOAL)),
                List.of(item("minecraft:gunpowder", Balance.PM_GUNPOWDER)), time(Balance.PM_CYCLE_TICKS), uses(Balance.PM_FE_PER_TICK))));
        List<Proc> turbine = new ArrayList<>();
        Proc.Ing blades = item("turbine_blades", 1);
        for (Object[] fuel : new Object[][] { { WmdFluids.LPG, Balance.TURBINE_FE_PER_MB_LPG }, { WmdFluids.NAPHTHA, Balance.TURBINE_FE_PER_MB_NAPHTHA } }) {
            int perMb = (Integer) fuel[1];
            turbine.add(p(List.of(fluid(f((WmdFluids.Entry) fuel[0]), 1000), blades), List.of(), makes(Balance.TURBINE_FE_PER_TICK),
                    note("jei.wmd.note.gen_fluid", n(perMb), n(1000L * perMb), sec(1000 * perMb / Math.max(1, Balance.TURBINE_FE_PER_TICK))),
                    note("jei.wmd.note.turbine_blades", n(Balance.TURBINE_BLADE_LIFE / 1200))));
        }
        machine("gas_turbine", turbine);
        int flareFeT = Balance.FLARE_FE_PER_MB * Balance.FLARE_MB_PER_SEC / 20;
        machine("flare_stack", List.of(p(List.of(fluid(f(WmdFluids.LPG), 1000)), List.of(), makes(flareFeT),
                note("jei.wmd.note.gen_fluid", n(Balance.FLARE_FE_PER_MB), n(1000L * Balance.FLARE_FE_PER_MB), sec(1000 * 20 / Math.max(1, Balance.FLARE_MB_PER_SEC))),
                note("jei.wmd.note.flare"))));
    }

    private static Item stackItem(String id) { ItemStack s = stack(id, 1); return s.isEmpty() ? null : s.getItem(); }

    private static void shellPress() {
        List<Proc> l = new ArrayList<>();
        for (ShellPressBlockEntity.Recipe r : ShellPressBlockEntity.RECIPES) {
            List<Proc.Ing> in = new ArrayList<>(List.of(item(r.casing(), r.casingN()), item(r.filler(), r.fillerN())));
            if (r.fuzeN() > 0) in.add(item(r.fuze(), r.fuzeN()));
            List<Component> lines = new ArrayList<>(List.of(time(Balance.SHELL_PRESS_TICKS), uses(Balance.SHELL_PRESS_FE_PER_TICK)));
            String path = r.out().substring(r.out().indexOf(':') + 1);
            if (path.contains("guided")) lines.add(note("jei.wmd.note.guided"));
            else if (path.equals("super_charge")) lines.add(note("jei.wmd.note.super_charge", n(Balance.SUPER_CHARGE_ALKYLATE_MB)));
            else if (path.equals("propellant_charge")) lines.add(note("jei.wmd.note.propellant"));
            else if (path.equals("shell_super_he")) lines.add(note("jei.wmd.note.super_shell"));
            else if (path.equals("mlrs_pod")) lines.add(note("jei.wmd.note.mlrs_pod"));
            lines.add(note("jei.wmd.note.press_pick"));
            l.add(new Proc(in, List.of(item(r.out(), r.outN())), lines));
        }
        machine("shell_press", l);
    }

    private static final Map<String, String> LAB_TAGS = Map.of("steel", "c:ingots/steel", "aluminium", "c:ingots/aluminum", "copper", "c:ingots/copper",
            "bronze", "c:ingots/bronze", "tungsten", "c:ingots/tungsten");

    private static void lab() {
        int ticks = Balance.LAB_RUN_TICKS;
        for (LabModuleBlockEntity.Stage s : LabModuleBlockEntity.Stage.values())
            if (s != LabModuleBlockEntity.Stage.FAIL_HOME) ticks += s.ticks;
        List<Proc> l = new ArrayList<>();
        for (WarheadType t : WarheadType.values()) {
            ItemStack out = LabRecipes.warheadStack(t);
            if (out.isEmpty()) continue;
            List<Proc.Ing> in = new ArrayList<>();
            for (LabRecipes.Need need : LabRecipes.needs(t)) in.add(labIng(need));
            l.add(p(in, List.of(item(out)), Component.translatable("jei.wmd.lab.time", sec(ticks), n(Balance.LAB_RUN_FE)),
                    note("jei.wmd.lab.line"), note("jei.wmd.lab.tier", t.tier)));
        }
        ItemStack core = stack("lab_core", 1);
        List<ItemStack> cat = new ArrayList<>();
        for (String id : new String[] { "lab_core", "lab_press", "lab_filler", "warhead_reactor", "lab_oven", "lab_storage" }) { ItemStack s = stack(id, 1); if (!s.isEmpty()) cat.add(s); }
        def("warhead_lab", Component.translatable("jei.wmd.cat.lab"), core, cat, l);
    }

    private static Proc.Ing labIng(LabRecipes.Need need) {
        String tag = LAB_TAGS.get(need.key());
        if (tag != null) {
            Proc.Ing i = Proc.tag(tag, need.count());
            if (!i.empty()) return i;
        }
        ItemStack s = stack(need.key(), need.count());
        if (!s.isEmpty() && need.test().test(s)) return item(s);
        List<ItemStack> l = new ArrayList<>();
        for (Item it : BuiltInRegistries.ITEM) {
            ItemStack st = new ItemStack(it, need.count());
            if (need.test().test(st)) l.add(st);
            if (l.size() >= 16) break;
        }
        return any(l);
    }

    private static List<ItemStack> parts(int slot, int minTier, int maxTier, java.util.function.Predicate<String> variant) {
        List<ItemStack> l = new ArrayList<>();
        for (var h : Wmd.partItems()) {
            MissilePartItem p = h.get();
            if (p.slot() == slot && p.tier() >= minTier && p.tier() <= maxTier && variant.test(p.variant() == null ? "" : p.variant())) l.add(new ItemStack(p));
        }
        return l;
    }

    private static boolean plain(String v) { return v.isEmpty(); }

    private static boolean ground(String v) {
        return !v.equals("interceptor") && !v.startsWith(Satellites.PREFIX) && !v.equals("asat") && !v.equals("cubesat");
    }

    private static Component bayTime(int stations) {
        return Component.translatable("jei.wmd.bay.time", sec(Balance.bayRunTicks(stations)), n(Balance.bayRunFe(stations) / 1000L), n(Balance.BAY_FE_PER_TICK * 20L));
    }

    private static void bay() {
        List<Proc> l = new ArrayList<>();
        ItemStack[] missiles = { new ItemStack(Wmd.MISSILE_T1.get()), new ItemStack(Wmd.MISSILE_T2.get()), new ItemStack(Wmd.MISSILE_T3.get()) };
        ItemStack[] interceptors = { new ItemStack(Wmd.INTERCEPTOR_T1.get()), new ItemStack(Wmd.INTERCEPTOR_T2.get()), new ItemStack(Wmd.INTERCEPTOR_T3.get()) };
        for (int t = 1; t <= 3; t++) {
            final int tier = t;
            l.add(p(List.of(any(parts(0, t, t, WmdRecipes::plain)), any(parts(1, t, t, v -> !v.equals(MissilePartItem.JET))), any(parts(2, t, t, WmdRecipes::plain)),
                            any(parts(3, 1, t, v -> ground(v) || (tier >= 3 && v.equals("cubesat"))))),
                    List.of(item(missiles[t - 1])), bayTime(t - 1), note("jei.wmd.bay.stations", t - 1), note("jei.wmd.bay.missile", t)));
        }
        for (int t = 1; t <= 3; t++)
            l.add(p(List.of(any(parts(0, t, t, WmdRecipes::plain)), any(parts(1, t, t, v -> !v.equals(MissilePartItem.JET))), any(parts(2, t, t, WmdRecipes::plain)),
                            item(new ItemStack(Wmd.WARHEAD_INTERCEPTOR.get()))),
                    List.of(item(interceptors[t - 1])), bayTime(t - 1), note("jei.wmd.bay.stations", t - 1), note("jei.wmd.bay.interceptor")));
        l.add(p(List.of(any(parts(0, 4, 4, WmdRecipes::plain)), any(parts(1, 4, 4, WmdRecipes::plain))), List.of(item(new ItemStack(Wmd.STAGE_CORE.get()))),
                bayTime(3), note("jei.wmd.bay.stations", 3), note("jei.wmd.bay.core")));
        l.add(p(List.of(any(parts(0, 4, 4, WmdRecipes::plain)), any(parts(1, 4, 4, WmdRecipes::plain)), any(parts(2, 4, 4, WmdRecipes::plain)),
                        any(parts(3, 1, 4, v -> !v.equals("interceptor") && !v.equals("cubesat") && !v.startsWith(Satellites.PREFIX)))),
                List.of(item(new ItemStack(Wmd.STAGE_UPPER.get()))), bayTime(3), note("jei.wmd.bay.stations", 3), note("jei.wmd.bay.upper")));
        l.add(p(List.of(any(parts(0, 2, 3, WmdRecipes::plain)), any(parts(1, 2, 3, WmdRecipes::plain))), List.of(item(new ItemStack(Wmd.STAGE_BOOSTER.get()))),
                bayTime(1), note("jei.wmd.bay.stations_range", 1, 2), note("jei.wmd.bay.booster")));
        l.add(p(List.of(any(parts(0, 2, 3, WmdRecipes::plain)), item(new ItemStack(Wmd.JET_ENGINE.get())), any(parts(2, 2, 3, WmdRecipes::plain)),
                        any(parts(3, 1, 3, WmdRecipes::ground))),
                List.of(item(new ItemStack(Wmd.CRUISE_MISSILE.get()))), bayTime(1), note("jei.wmd.bay.stations_range", 1, 2),
                note("jei.wmd.bay.cruise", Balance.CRUISE_FUEL_CANISTERS)));
        List<ItemStack> cat = new ArrayList<>();
        for (String id : new String[] { "bay_core", "bay_weld", "bay_crane", "bay_warhead", "bay_clean", "bay_paint", "bay_cryo" }) { ItemStack s = stack(id, 1); if (!s.isEmpty()) cat.add(s); }
        def("assembly_bay", Component.translatable("jei.wmd.cat.bay"), stack("bay_core", 1), cat, l);

        List<ItemStack> top = new ArrayList<>(List.of(new ItemStack(Wmd.STAGE_UPPER.get())));
        for (String type : Satellites.TYPES) top.add(PayloadUnitItem.of(type));
        ItemStack term = new ItemStack(Wmd.LAUNCH_TERMINAL_4_ITEM.get());
        def("rocket_stack", Component.translatable("jei.wmd.cat.rocket"), term, List.of(term), List.of(p(
                List.of(item(new ItemStack(Wmd.STAGE_CORE.get())), item(new ItemStack(Wmd.STAGE_BOOSTER.get(), Balance.BOOSTER_STD)), any(top)), List.of(),
                note("jei.wmd.rocket.boosters", Balance.BOOSTER_MIN, n(Balance.BOOSTER_FUEL_MB)), note("jei.wmd.rocket.fuel", String.format("%.2f", Balance.ALKYLATE_T4_RANGE_MULT)),
                note("jei.wmd.rocket.top"))));
    }

    private static List<ItemStack> stacks(String... ids) {
        List<ItemStack> l = new ArrayList<>();
        for (String id : ids) { ItemStack s = stack(id, 1); if (!s.isEmpty()) l.add(s); }
        return l;
    }

    private static void droneLine() {
        List<Proc> l = new ArrayList<>();
        for (DroneType t : DroneType.values()) {
            List<Proc.Ing> in = new ArrayList<>();
            for (DroneRecipes.Need need : DroneRecipes.needs(t)) in.add(item(new ItemStack(need.item(), need.count())));
            l.add(p(in, List.of(item(t.stack())), Component.translatable("jei.wmd.lab.time", sec(t.runTicks()), n(t.runFe())),
                    note("jei.wmd.drone_line.line"), note(t.big ? "jei.wmd.drone_line.big" : "jei.wmd.drone_line.small", Balance.DRONE_SHELVES)));
        }
        List<ItemStack> cat = stacks("drone_core", "drone_frame", "drone_avionics", "drone_propulsion", "drone_test", "drone_storage", "drone_hangar");
        def("drone_line", Component.translatable("jei.wmd.cat.drone_line"), stack("drone_core", 1), cat, l);
    }

    private static void bombLine() {
        List<Proc> l = new ArrayList<>();
        for (BombType t : BombType.values()) {
            List<Proc.Ing> in = new ArrayList<>();
            for (BombLineRecipes.Need need : BombLineRecipes.needs(t)) in.add(bombIng(need));
            if (BombLineRecipes.naphtha(t) > 0) in.add(fluid(f(WmdFluids.NAPHTHA), BombLineRecipes.naphtha(t)));
            l.add(p(in, List.of(item(BombLineRecipes.stack(t))), Component.translatable("jei.wmd.lab.time", sec(BombLineRecipes.runTicks(t)), n(BombLineRecipes.runFe(t))),
                    note("jei.wmd.bomb_line.line"), note(t.heavy ? "jei.wmd.bomb_line.heavy" : "jei.wmd.bomb_line.light")));
        }
        List<ItemStack> cat = stacks("bomb_line_core", "bomb_line_press", "bomb_line_filling", "bomb_line_fuze", "bomb_line_storage", "bomb_line_cradle");
        def("bomb_line", Component.translatable("jei.wmd.cat.bomb_line"), stack("bomb_line_core", 1), cat, l);
    }

    private static Proc.Ing bombIng(BombLineRecipes.Need need) {
        List<ItemStack> l = new ArrayList<>();
        ItemStack shown = new ItemStack(need.shown(), need.count());
        if (!shown.isEmpty() && need.test().test(shown)) l.add(shown);
        for (Item it : BuiltInRegistries.ITEM) {
            if (l.size() >= 8) break;
            ItemStack st = new ItemStack(it, need.count());
            if (it != need.shown() && need.test().test(st)) l.add(st);
        }
        return any(l);
    }

    private static void research() {
        List<Proc> l = new ArrayList<>();
        for (ResearchRecipes.Research r : ResearchRecipes.all()) {
            ItemStack chip = ResearchRecipes.chip(r);
            if (chip.isEmpty() || chip.getItem() == Items.AIR) continue;
            List<Proc.Ing> out = new ArrayList<>();
            for (String u : r.unlocks()) out.add(item(u, 1));
            l.add(p(List.of(item(chip)), out, note("jei.wmd.unlocks.line")));
        }
        ItemStack station = stack(ResearchCategory.STATION_ID, 1);
        def("research_unlocks", Component.translatable("jei.wmd.cat.unlocks"), station, station.isEmpty() ? List.of() : List.of(station), l);
    }
}
