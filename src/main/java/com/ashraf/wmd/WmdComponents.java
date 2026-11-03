package com.ashraf.wmd;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WmdComponents {

    private WmdComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Wmd.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WARHEAD_GRADE =
            COMPONENTS.registerComponentType("warhead_grade", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SCANNER_ORE =
            COMPONENTS.registerComponentType("scanner_ore", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MissileBuild>> MISSILE_BUILD =
            COMPONENTS.registerComponentType("missile_build", b -> b.persistent(MissileBuild.CODEC).networkSynchronized(MissileBuild.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> CANISTER_FLUID =
            COMPONENTS.registerComponentType("canister_fluid", b -> b.persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC).networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> OXYGEN =
            COMPONENTS.registerComponentType("oxygen", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SAT_TYPE =
            COMPONENTS.registerComponentType("sat_type", b -> b.persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));
}
