package com.ashraf.wmd;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class GeneratorTooltips {

    private GeneratorTooltips() {}

    private static final Map<String, Supplier<Component>> LINES = new LinkedHashMap<>();

    private static void add(String id, String key, Supplier<Object[]> args) {
        LINES.put(id, () -> Component.translatable("tooltip.wmd.gen." + key, args.get()));
    }

    private static String n(long v) { return String.format(java.util.Locale.ROOT, "%,d", v); }

    static {
        add("coal_generator", "fuel", () -> new Object[] { n(Balance.COAL_GEN_FE_PER_TICK), Component.translatable("tooltip.wmd.gen.on.coal") });
        add("starter_generator", "fuel", () -> new Object[] { n(Balance.SG_FE_PER_TICK), Component.translatable("tooltip.wmd.gen.on.coal") });
        add("steam_engine", "fuel", () -> new Object[] { n(Balance.SE_FE_PER_TICK), Component.translatable("tooltip.wmd.gen.on.coal_water") });
        add("lava_generator", "lava", () -> new Object[] { n(Balance.LG_FE_PIPED), n(Balance.LG_FE_PASSIVE_MAX) });
        add("solar_panel", "solar", () -> new Object[] { n(Balance.SOLAR_ARRAY_FE[1]), n(Balance.SOLAR_ARRAY_FE[Balance.SOLAR_ARRAY_FE.length - 1]),
                Balance.SOLAR_ARRAY_FE.length - 1, Balance.SOLAR_ARRAY_FE.length - 1 });
        add("wind_turbine", "wind", () -> new Object[] { n(Balance.WIND_FE_LOW), n(Balance.WIND_FE_HIGH) });
        add("diesel_generator", "fuel", () -> new Object[] { n(GeneratorEngineBlockEntity.FE_PER_SECOND / 20), Component.translatable("tooltip.wmd.gen.on.diesel") });
        add("gasoline_engine", "fuel", () -> new Object[] { n(Balance.GE_FE_PER_TICK), Component.translatable("tooltip.wmd.gen.on.gasoline") });
        add("free_piston_generator", "fuel", () -> new Object[] { n(Balance.FP_FE_PER_TICK), Component.translatable("tooltip.wmd.gen.on.reformate") });
        add("gas_turbine", "fuel", () -> new Object[] { n(Balance.TURBINE_FE_PER_TICK), Component.translatable("tooltip.wmd.gen.on.turbine") });
        add("flare_stack", "fuel", () -> new Object[] { n((long) Balance.FLARE_FE_PER_MB * 10 / 20), Component.translatable("tooltip.wmd.gen.on.lpg") });
        add("steam_turbine", "steam", () -> new Object[] { n((long) Balance.STEAM_TURBINE_MB * Balance.STEAM_TURBINE_FE_PER_MB), n(Balance.STEAM_TURBINE_MB) });
        add("nuclear_reactor", "reactor", () -> new Object[] { n(Balance.REACTOR_STEAM_MB), n((long) Balance.REACTOR_STEAM_MB * Balance.STEAM_TURBINE_FE_PER_MB) });
        add("fusion_reactor", "fusion", () -> new Object[] { n(Balance.FUSION_MAX_FE), n(Balance.FUSION_SOCKET_OUT) });
        add("naval_reactor", "fuel", () -> new Object[] { n(Balance.NR_FE_PER_TICK), Component.translatable("tooltip.wmd.gen.on.rods") });
        add("sub_diesel", "fuel", () -> new Object[] { n(Balance.SD_FE_PER_TICK), Component.translatable("tooltip.wmd.gen.on.diesel") });
        add("jet_power_core", "fuel", () -> new Object[] { n(Balance.CORE_APU_FE), Component.translatable("tooltip.wmd.gen.on.apu") });

        for (String ore : GroundScannerItem.ORES) LINES.put(ore + "_core_sample", () -> Component.translatable("tooltip.wmd.core_sample.how"));
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        ItemStack s = event.getItemStack();
        if (s.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (id == null || !Wmd.MODID.equals(id.getNamespace())) return;
        Supplier<Component> line = LINES.get(id.getPath());
        if (line == null) return;
        Component c = line.get().copy().withStyle(ChatFormatting.AQUA);
        event.getToolTip().add(Math.min(1, event.getToolTip().size()), c);
    }

    static java.util.Set<String> ids() { return LINES.keySet(); }
}
