package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class WmdFluids {

    private WmdFluids() {}

    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Wmd.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Wmd.MODID);

    public record Entry(String id, int color, DeferredHolder<FluidType, FluidType> type,
                        DeferredHolder<Fluid, BaseFlowingFluid.Source> source, DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing) {}

    public static final List<Entry> ALL = new ArrayList<>();

    private static Entry make(String id, int color, int density) {
        DeferredHolder<FluidType, FluidType> type = TYPES.register(id, () -> new FluidType(FluidType.Properties.create().density(density).viscosity(1500)));
        Entry[] self = new Entry[1];
        Supplier<BaseFlowingFluid.Properties> props = () -> new BaseFlowingFluid.Properties(type, self[0].source(), self[0].flowing());
        DeferredHolder<Fluid, BaseFlowingFluid.Source> source = FLUIDS.register(id, () -> new BaseFlowingFluid.Source(props.get()));
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing = FLUIDS.register(id + "_flowing", () -> new BaseFlowingFluid.Flowing(props.get()));
        self[0] = new Entry(id, color, type, source, flowing);
        ALL.add(self[0]);
        return self[0];
    }

    public static final Entry CRUDE_OIL = make("crude_oil", 0xFF241A10, 900);
    public static final Entry DESALTED_CRUDE = make("desalted_crude", 0xFF2E2012, 880);
    public static final Entry HOT_CRUDE = make("hot_crude", 0xFF4A2410, 850);
    public static final Entry BRINE = make("brine", 0xFF8090A0, 1100);

    public static final Entry LPG = make("lpg", 0xFFB8D8E8, 550);
    public static final Entry NAPHTHA = make("naphtha", 0xFFE8C860, 700);
    public static final Entry KEROSENE = make("kerosene", 0xFFD8B050, 800);
    public static final Entry DIESEL = make("diesel", 0xFFC89830, 830);
    public static final Entry FUEL_OIL = make("fuel_oil", 0xFF3A2A18, 950);

    public static final Entry LVGO = make("lvgo", 0xFF6A4A20, 900);
    public static final Entry HVGO = make("hvgo", 0xFF4A3418, 930);
    public static final Entry BITUMEN = make("bitumen", 0xFF0C0A08, 1020);

    public static final Entry REFORMATE = make("reformate", 0xFFE0A030, 760);
    public static final Entry GASOLINE = make("gasoline", 0xFFF0C040, 740);
    public static final Entry ALKYLATE = make("alkylate", 0xFFF0D868, 700);
    public static final Entry JET_FUEL = make("jet_fuel", 0xFFE8C078, 800);
    public static final Entry HYDROGEN = make("hydrogen", 0xFF9AB8FF, 100);

    public static final Entry CHLORINE = make("chlorine", 0xFFC8DC5A, 300);
    public static final Entry TITANIUM_TETRACHLORIDE = make("titanium_tetrachloride", 0xFFE6DCA8, 1730);
    public static final Entry MAGNESIUM_CHLORIDE = make("magnesium_chloride", 0xFFE8E4DA, 1680);

    public static final Entry STEAM = make("steam", 0xFFF2F5F8, 300);
    public static final Entry SPENT_STEAM = make("spent_steam", 0xFF8A9DB4, 600);

    public static final Entry HEAVY_WATER = make("heavy_water", 0xFF9FC8F0, 1107);

    public static final Entry DEUTERIUM = make("deuterium", 0xFFB4C8FF, 90);
    public static final Entry TRITIUM = make("tritium", 0xFF9CF0B4, 95);

    public static final Entry COAL_TAR = make("coal_tar", 0xFF3A2414, 1180);
}
