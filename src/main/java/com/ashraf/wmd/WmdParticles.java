package com.ashraf.wmd;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WmdParticles {

    private WmdParticles() {}

    public static final DeferredRegister<ParticleType<?>> TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Wmd.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LAUNCH_SMOKE = TYPES.register("launch_smoke", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TRAIL_SMOKE = TYPES.register("trail_smoke", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ROCKET_FLAME = TYPES.register("rocket_flame", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VAPOUR = TYPES.register("vapour", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GAS_CLOUD = TYPES.register("gas_cloud", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VACUUM_SPARK = TYPES.register("vacuum_spark", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WATER_SPRAY = TYPES.register("water_spray", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> COLLAPSE_DUST = TYPES.register("collapse_dust", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TORPEDO_WAKE = TYPES.register("torpedo_wake", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CONTRAIL = TYPES.register("contrail", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEAT_HAZE = TYPES.register("heat_haze", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> NAV_LIGHT = TYPES.register("nav_light", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> UW_GAS = TYPES.register("uw_gas", () -> new SimpleParticleType(true));
}
