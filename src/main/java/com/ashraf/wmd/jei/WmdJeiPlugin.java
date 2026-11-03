package com.ashraf.wmd.jei;

import java.util.List;

import com.ashraf.wmd.Balance;
import com.ashraf.wmd.MissileConfig;
import com.ashraf.wmd.MissilePartItem;
import com.ashraf.wmd.Config;
import com.ashraf.wmd.WmdFluids;
import com.ashraf.wmd.Wmd;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

@JeiPlugin
public class WmdJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jei");

    public record MachineRecipe(List<ItemStack> inItems, Fluid inFluid, int inFluidMb, List<ItemStack> outItems, int ticks, int fePerTick) {}

    public static final RecipeType<MachineRecipe> ELECTROLYSER = RecipeType.create(Wmd.MODID, "electrolyser", MachineRecipe.class);
    public static final RecipeType<MachineRecipe> NITRATION = RecipeType.create(Wmd.MODID, "nitration_reactor", MachineRecipe.class);
    public static final RecipeType<MachineRecipe> CRUSHER = RecipeType.create(Wmd.MODID, "crusher", MachineRecipe.class);
    public static final RecipeType<MachineRecipe> ARC_FURNACE = RecipeType.create(Wmd.MODID, "arc_furnace", MachineRecipe.class);
    public static final RecipeType<MachineRecipe> COKER = RecipeType.create(Wmd.MODID, "coker", MachineRecipe.class);

    public static final RecipeType<MachineRecipe> ROLLING_MILL = RecipeType.create(Wmd.MODID, "rolling_mill", MachineRecipe.class);
    public static final RecipeType<MachineRecipe> WIRE_DRAWER = RecipeType.create(Wmd.MODID, "wire_drawer", MachineRecipe.class);

    private static Item item(String name) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, name));
    }

    private static ItemStack stack(String name, int n) { return new ItemStack(item(name), n); }

    @Override
    public ResourceLocation getPluginUid() { return UID; }

    private static volatile mezz.jei.api.runtime.IJeiRuntime runtime;

    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime rt) { runtime = rt; }

    @Override
    public void onRuntimeUnavailable() { runtime = null; }

    public static boolean showMachineRecipes(ItemStack machine) {
        mezz.jei.api.runtime.IJeiRuntime rt = runtime;
        if (rt == null || machine.isEmpty()) return false;
        mezz.jei.api.recipe.IRecipeManager rm = rt.getRecipeManager();
        List<RecipeType<?>> types = rm.createRecipeCategoryLookup().get().<RecipeType<?>>map(c -> c.getRecipeType())
                .filter(t -> rm.createRecipeCatalystLookup(t).getItemStack().anyMatch(c -> ItemStack.isSameItem(c, machine))).toList();
        if (types.isEmpty()) return false;
        rt.getRecipesGui().showTypes(types);
        return true;
    }

    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration reg) {
        reg.addGuiContainerHandler(com.ashraf.wmd.MachineScreen.class, new mezz.jei.api.gui.handlers.IGuiContainerHandler<com.ashraf.wmd.MachineScreen>() {
            @Override
            public List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(com.ashraf.wmd.MachineScreen screen) { return screen.kitExtraAreas(); }

            @Override
            public java.util.Collection<mezz.jei.api.gui.handlers.IGuiClickableArea> getGuiClickableAreas(com.ashraf.wmd.MachineScreen screen, double guiMouseX, double guiMouseY) {
                return progressClickArea(screen);
            }

            @Override
            public java.util.Optional<? extends mezz.jei.api.runtime.IClickableIngredient<?>> getClickableIngredientUnderMouse(mezz.jei.api.gui.builder.IClickableIngredientFactory builder,
                    com.ashraf.wmd.MachineScreen screen, double mouseX, double mouseY) {
                Object[] g = screen.fluidGaugeAt(mouseX, mouseY);
                if (g == null) return java.util.Optional.empty();
                return builder.createBuilder(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK, new net.neoforged.neoforge.fluids.FluidStack((Fluid) g[0], 1000))
                        .buildWithArea((int) g[1], (int) g[2], (int) g[3], (int) g[4]);
            }
        });
    }

    private static java.util.Collection<mezz.jei.api.gui.handlers.IGuiClickableArea> progressClickArea(com.ashraf.wmd.MachineScreen screen) {
        if (screen.getClass() != com.ashraf.wmd.MachineScreen.class) return List.of();
        com.ashraf.wmd.MachineMenu menu = screen.getMenu();
        if (!(menu.machine() instanceof net.minecraft.world.level.block.entity.BlockEntity be) || menu.machineSlotCount() <= 0 || menu.progress() < 0) return List.of();
        List<RecipeType<?>> types = clickTypes(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath());
        if (types.isEmpty()) return List.of();
        int x0 = Integer.MAX_VALUE, x1 = 0;
        for (int i = 0; i < menu.machineSlotCount(); i++) { x0 = Math.min(x0, menu.slots.get(i).x); x1 = Math.max(x1, menu.slots.get(i).x + 16); }
        int w = 60;
        return List.of(mezz.jei.api.gui.handlers.IGuiClickableArea.createBasic((x0 + x1) / 2 - w / 2 - 3, com.ashraf.wmd.MachineLayout.SLOT_Y + 25, w, 9, types.toArray(new RecipeType<?>[0])));
    }

    private static List<RecipeType<?>> clickTypes(String block) {
        List<RecipeType<?>> l = new java.util.ArrayList<>();
        RecipeType<?> old = switch (block) {
            case "electrolyser" -> ELECTROLYSER; case "nitration_reactor" -> NITRATION; case "crusher" -> CRUSHER; case "arc_furnace" -> ARC_FURNACE;
            case "coker" -> COKER; case "rolling_mill" -> ROLLING_MILL; case "wire_drawer" -> WIRE_DRAWER; case "leach_tank" -> UraniumCategory.LEACH_TANK;
            case "centrifuge" -> UraniumCategory.CENTRIFUGE; case "reprocessing_plant" -> UraniumCategory.REPROCESSING; case "roaster" -> TungstenCategory.ROASTER;
            case "hydrogen_furnace" -> TungstenCategory.HYDROGEN_FURNACE; case "sinter_press" -> TungstenCategory.SINTER_PRESS; case "nuclear_reactor" -> ReactorCategory.TYPE;
            case "steam_turbine", "cooling_tower" -> SteamLoopCategory.TYPE; case "research_station" -> ResearchCategory.TYPE;
            case "satellite_facility" -> SatelliteFacilityCategory.TYPE;
            default -> null;
        };
        if (old != null) l.add(old);
        RecipeType<?> proc = WmdRecipes.type(block);
        if (proc != null) l.add(proc);
        if (block.equals("research_station") && WmdRecipes.type("research_unlocks") != null) l.add(WmdRecipes.type("research_unlocks"));
        return l;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration reg) {
        IGuiHelper gui = reg.getJeiHelpers().getGuiHelper();
        reg.addRecipeCategories(
                new Category(gui, ELECTROLYSER, "electrolyser"),
                new Category(gui, NITRATION, "nitration_reactor"),
                new Category(gui, COKER, "coker"),
                new Category(gui, CRUSHER, "crusher"),
                new Category(gui, ARC_FURNACE, "arc_furnace"),
                new Category(gui, ROLLING_MILL, "rolling_mill"),
                new Category(gui, WIRE_DRAWER, "wire_drawer"),
                new UraniumCategory(gui, UraniumCategory.LEACH_TANK, "leach_tank", false),
                new UraniumCategory(gui, UraniumCategory.CENTRIFUGE, "centrifuge", true),
                new ReactorCategory(gui),
                new SteamLoopCategory(gui),
                new UraniumCategory(gui, UraniumCategory.REPROCESSING, "reprocessing_plant", false),
                new ResearchCategory(gui),
                new SatelliteFacilityCategory(gui));
        reg.addRecipeCategories(TungstenCategory.categories(gui));
        WmdRecipes.registerCategories(reg);
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        registerItemInfo(reg);
        WmdRecipes.registerRecipes(reg);
        WmdInfo.register(reg);
        registerOreProcessing(reg);
        registerMetalworking(reg);
        TungstenCategory.registerRecipes(reg);
        SatelliteFacilityCategory.registerRecipes(reg);
        registerUranium(reg);
        ReactorCategory.registerRecipes(reg);
        SteamLoopCategory.registerRecipes(reg);
        reg.addRecipes(UraniumCategory.REPROCESSING, List.of(new MachineRecipe(
                List.of(stack("spent_fuel_rod", Balance.REPRO_SPENT), stack("caustic_soda", Balance.REPRO_CAUSTIC)), net.minecraft.world.level.material.Fluids.WATER, Balance.REPRO_WATER_MB,
                List.of(stack("plutonium", Balance.REPRO_PLUTONIUM), stack("depleted_uranium", Balance.REPRO_DEPLETED)), Balance.REPRO_CYCLE_TICKS, Balance.REPRO_FE_PER_TICK)));
        reg.addRecipes(ResearchCategory.TYPE, com.ashraf.wmd.ResearchRecipes.all());
        registerChipInfo(reg);
        reg.addRecipes(ELECTROLYSER, List.of(new MachineRecipe(
                List.of(stack("raw_bauxite", Balance.ELECTRO_BAUXITE_PER_CYCLE), stack("carbon_electrode", 1)), null, 0,
                List.of(stack("aluminium_ingot", Balance.ELECTRO_ALU_PER_CYCLE)), Balance.ELECTRO_CYCLE_TICKS, Balance.ELECTRO_FE_PER_TICK)));
        reg.addRecipes(NITRATION, List.of(new MachineRecipe(
                List.of(stack("nitrate_dust", Balance.NITRO_DUST_PER_CYCLE)), WmdFluids.NAPHTHA.source().get(), Balance.NITRO_NAPHTHA_MB,
                List.of(stack("he_charge", Balance.NITRO_HE_PER_CYCLE)), Balance.NITRO_CYCLE_TICKS, Balance.NITRO_FE_PER_TICK)));
        reg.addRecipes(COKER, List.of(new MachineRecipe(
                List.of(), WmdFluids.BITUMEN.source().get(), Balance.COKER_BITUMEN_MB,
                List.of(stack("petroleum_coke", Balance.COKER_COKE_PER_CYCLE)), Balance.COKER_CYCLE_TICKS, Balance.COKER_FE_PER_TICK)));
    }

    private static void registerOreProcessing(IRecipeRegistration reg) {
        reg.addRecipes(CRUSHER, List.of(
                new MachineRecipe(List.of(stack("raw_tungsten", 1)), null, 0, List.of(stack("tungsten_concentrate", Balance.CRUSH_YIELD)), Balance.CRUSH_CYCLE_TICKS, Balance.CRUSH_FE_PER_TICK),
                new MachineRecipe(List.of(stack("raw_titanium", 1)), null, 0, List.of(stack("titanium_concentrate", Balance.CRUSH_YIELD)), Balance.CRUSH_CYCLE_TICKS, Balance.CRUSH_FE_PER_TICK),
                new MachineRecipe(List.of(stack("raw_tin", 1)), null, 0, List.of(stack("tin_dust", Balance.CRUSH_YIELD)), Balance.CRUSH_CYCLE_TICKS, Balance.CRUSH_FE_PER_TICK),
                new MachineRecipe(List.of(stack("raw_lead", 1)), null, 0, List.of(stack("lead_dust", Balance.CRUSH_YIELD)), Balance.CRUSH_CYCLE_TICKS, Balance.CRUSH_FE_PER_TICK),
                new MachineRecipe(List.of(stack("raw_silver", 1)), null, 0, List.of(stack("silver_dust", Balance.CRUSH_YIELD)), Balance.CRUSH_CYCLE_TICKS, Balance.CRUSH_FE_PER_TICK),
                new MachineRecipe(List.of(stack("raw_platinum", 1)), null, 0, List.of(stack("platinum_dust", Balance.CRUSH_YIELD)), Balance.CRUSH_CYCLE_TICKS, Balance.CRUSH_FE_PER_TICK),
                new MachineRecipe(List.of(stack("rutile_chunk", 1)), null, 0, List.of(stack("titanium_concentrate", Balance.CRUSH_CHUNK_YIELD)), Balance.CRUSH_CYCLE_TICKS, Balance.CRUSH_FE_PER_TICK),
                new MachineRecipe(List.of(stack("wolframite_chunk", 1)), null, 0, List.of(stack("tungsten_concentrate", Balance.CRUSH_CHUNK_YIELD)), Balance.CRUSH_CYCLE_TICKS, Balance.CRUSH_FE_PER_TICK),
                new MachineRecipe(List.of(stack("raw_uranium", 1)), null, 0, List.of(stack("uranium_concentrate", Balance.CRUSH_YIELD)), Balance.CRUSH_CYCLE_TICKS, Balance.CRUSH_FE_PER_TICK),
                new MachineRecipe(List.of(stack("uraninite_chunk", 1)), null, 0, List.of(stack("uranium_concentrate", Balance.CRUSH_CHUNK_YIELD)), Balance.CRUSH_CYCLE_TICKS, Balance.CRUSH_FE_PER_TICK)));
        reg.addRecipes(ARC_FURNACE, List.of(
                new MachineRecipe(List.of(stack("tungsten_concentrate", Balance.ARC_W_CONC), stack("carbon_electrode", 1)), null, 0, List.of(stack("tungsten_ingot", Balance.ARC_INGOTS_PER_CYCLE)), Balance.ARC_W_CYCLE_TICKS, Balance.ARC_FE_PER_TICK),
                new MachineRecipe(List.of(stack("titanium_sponge", Balance.ARC_TI_CONC), stack("carbon_electrode", 1)), null, 0, List.of(stack("titanium_ingot", Balance.ARC_INGOTS_PER_CYCLE)), Balance.ARC_TI_CYCLE_TICKS, Balance.ARC_FE_PER_TICK)));
    }

    private static void registerMetalworking(IRecipeRegistration reg) {
        String[][] mill = { { "steel_block", "steel_plate" }, { "aluminium_block", "aluminium_plate" }, { "titanium_block", "titanium_plate" }, { "tungsten_block", "tungsten_plate" } };
        List<MachineRecipe> m = new java.util.ArrayList<>();
        for (String[] r : mill) m.add(new MachineRecipe(List.of(stack(r[0], 1)), null, 0, List.of(stack(r[1], Balance.MILL_PLATES)), Balance.MILL_CYCLE_TICKS, Balance.MILL_FE_PER_TICK));
        reg.addRecipes(ROLLING_MILL, m);
        ItemStack[][] wire = { { new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT), stack("copper_wire", Balance.WIRE_PER_INGOT) },
                { new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT), stack("gold_wire", Balance.WIRE_PER_INGOT) },
                { stack("aluminium_ingot", 1), stack("aluminium_cable", Balance.WIRE_PER_INGOT) } };
        List<MachineRecipe> w = new java.util.ArrayList<>();
        for (ItemStack[] r : wire) w.add(new MachineRecipe(List.of(r[0]), null, 0, List.of(r[1]), Balance.WIRE_CYCLE_TICKS, Balance.WIRE_FE_PER_TICK));
        reg.addRecipes(WIRE_DRAWER, w);
    }

    private static void registerUranium(IRecipeRegistration reg) {
        reg.addRecipes(UraniumCategory.LEACH_TANK, List.of(new MachineRecipe(
                List.of(stack("uranium_concentrate", Balance.LEACH_CONC), stack("caustic_soda", Balance.LEACH_CAUSTIC)), net.minecraft.world.level.material.Fluids.WATER, Balance.LEACH_WATER_MB,
                List.of(stack("yellowcake", Balance.LEACH_YELLOWCAKE)), Balance.LEACH_CYCLE_TICKS, Balance.LEACH_FE_PER_TICK)));
        reg.addRecipes(UraniumCategory.CENTRIFUGE, List.of(new MachineRecipe(
                List.of(stack("yellowcake", Balance.CENT_YELLOWCAKE)), null, 0,
                List.of(stack("enriched_uranium", Balance.CENT_ENRICHED), stack("depleted_uranium", Balance.CENT_DEPLETED)), Balance.CENT_CYCLE_TICKS, Balance.CENT_FE_PER_TICK)));
    }

    private static void registerItemInfo(IRecipeRegistration reg) {
        for (var holder : Wmd.partItems()) {
            MissilePartItem part = holder.get();
            ItemStack st = new ItemStack(part);
            int tier = Math.min(3, part.tier());
            Component text = switch (part.slot()) {
                case 0 -> Component.translatable("jei.wmd.info.airframe", part.tier(), String.format("%.0f", MissileConfig.health(tier)), String.format("%.1f", MissileConfig.weightAirframe(tier)));
                case 1 -> part.variant().equals("divert")
                        ? Component.translatable("jei.wmd.info.divert", String.format("%.1f", MissileConfig.speed(3)), String.format("%.1f", MissileConfig.weightDivert()), Math.round(MissileConfig.divertRange() * 100))
                        : Component.translatable("jei.wmd.info.motor", part.tier(), String.format("%,.0f", MissileConfig.baseRange(tier)), String.format("%.1f", MissileConfig.speed(tier)), String.format("%.1f", MissileConfig.weightMotor(tier)));
                case 2 -> Component.translatable("jei.wmd.info.guidance", part.tier(), (int) Config.missRadius(part.tier()), String.format("%.1f", MissileConfig.weightGuidance(tier)));
                default -> Component.translatable("jei.wmd.info.warhead", Component.translatable("warhead.wmd." + part.variant()), part.tier(),
                        Component.translatable("jei.wmd.info.wh." + part.variant()), String.format("%.1f", MissileConfig.weightWarhead(part.variant())));
            };
            reg.addItemStackInfo(st, text);
        }
        reg.addItemStackInfo(new ItemStack(item("flare_pack")), Component.translatable("jei.wmd.info.flare_pack"));
        reg.addItemStackInfo(new ItemStack(item("chaff_pod")), Component.translatable("jei.wmd.info.chaff_pod"));
        Component missile = Component.translatable("jei.wmd.info.missile", String.format("%.0f", MissileConfig.fuelBase()), String.format("%.3f", MissileConfig.fuelPerBlock()));
        reg.addItemStackInfo(new ItemStack(Wmd.MISSILE_T1.get()), missile);
        reg.addItemStackInfo(new ItemStack(Wmd.MISSILE_T2.get()), missile);
        reg.addItemStackInfo(new ItemStack(Wmd.MISSILE_T3.get()), missile);
    }

    private static final String[] CHIPS = {"chip_blank", "chip_metallurgy_2", "chip_mining_1", "chip_power_2", "chip_rocketry_2", "chip_defence_2", "chip_warheads_2"};

    private static void registerChipInfo(IRecipeRegistration reg) {
        for (String chip : CHIPS) {
            ItemStack st = ResearchCategory.stackOf(chip, 1);
            if (!st.isEmpty()) reg.addItemStackInfo(st, Component.translatable("jei.wmd.info." + chip));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration reg) {
        reg.addRecipeCatalyst(new ItemStack(Wmd.ELECTROLYSER_ITEM.get()), ELECTROLYSER);
        reg.addRecipeCatalyst(new ItemStack(Wmd.NITRATION_REACTOR_ITEM.get()), NITRATION);
        reg.addRecipeCatalyst(new ItemStack(Wmd.COKER_ITEM.get()), COKER);
        reg.addRecipeCatalyst(new ItemStack(Wmd.CRUSHER_ITEM.get()), CRUSHER);
        reg.addRecipeCatalyst(new ItemStack(Wmd.ARC_FURNACE_ITEM.get()), ARC_FURNACE);
        reg.addRecipeCatalyst(new ItemStack(Wmd.BLAST_FURNACE_ITEM.get()), mezz.jei.api.constants.RecipeTypes.BLASTING, mezz.jei.api.constants.RecipeTypes.SMELTING);
        reg.addRecipeCatalyst(new ItemStack(Wmd.ROLLING_MILL_ITEM.get()), ROLLING_MILL);
        reg.addRecipeCatalyst(new ItemStack(Wmd.WIRE_DRAWER_ITEM.get()), WIRE_DRAWER);
        TungstenCategory.registerCatalysts(reg);
        SatelliteFacilityCategory.registerCatalysts(reg);
        reg.addRecipeCatalyst(new ItemStack(Wmd.LEACH_TANK_ITEM.get()), UraniumCategory.LEACH_TANK);
        reg.addRecipeCatalyst(new ItemStack(Wmd.CENTRIFUGE_ITEM.get()), UraniumCategory.CENTRIFUGE);
        ReactorCategory.registerCatalysts(reg);
        SteamLoopCategory.registerCatalysts(reg);
        reg.addRecipeCatalyst(new ItemStack(Wmd.REPROCESSING_PLANT_ITEM.get()), UraniumCategory.REPROCESSING);
        ItemStack station = ResearchCategory.stackOf(ResearchCategory.STATION_ID, 1);
        if (!station.isEmpty()) reg.addRecipeCatalyst(station, ResearchCategory.TYPE);
        WmdRecipes.registerCatalysts(reg);
    }

    private static final class Category implements IRecipeCategory<MachineRecipe> {
        private static final int W = 130, H = 52;
        private final RecipeType<MachineRecipe> type;
        private final String name;
        private final IDrawable icon;

        Category(IGuiHelper gui, RecipeType<MachineRecipe> type, String name) {
            this.type = type;
            this.name = name;
            this.icon = gui.createDrawableItemStack(new ItemStack(item(name)));
        }

        @Override public RecipeType<MachineRecipe> getRecipeType() { return type; }
        @Override public Component getTitle() { return Component.translatable("block.wmd." + name); }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return W; }
        @Override public int getHeight() { return H; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, MachineRecipe r, IFocusGroup focuses) {
            int x = 4;
            for (ItemStack in : r.inItems()) {
                b.addSlot(RecipeIngredientRole.INPUT, x, 6).addItemStack(in);
                x += 20;
            }
            if (r.inFluid() != null) {
                b.addSlot(RecipeIngredientRole.INPUT, x, 6).addFluidStack(r.inFluid(), r.inFluidMb());
            }
            int ox = 100;
            for (ItemStack out : r.outItems()) {
                b.addSlot(RecipeIngredientRole.OUTPUT, ox, 6).addItemStack(out);
                ox += 20;
            }
            b.addSlot(RecipeIngredientRole.CATALYST, 56, 6).addItemStack(new ItemStack(item(name)));
        }

        @Override
        public void draw(MachineRecipe r, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            g.drawString(font, "->", 80, 10, 0xFF555555, false);
            g.drawString(font, Component.translatable("jei.wmd.time", String.format("%.1f", r.ticks() / 20.0)), 4, 28, 0xFF555555, false);
            g.drawString(font, Component.translatable("jei.wmd.power", r.fePerTick() * 20), 4, 40, 0xFF555555, false);
        }
    }
}
