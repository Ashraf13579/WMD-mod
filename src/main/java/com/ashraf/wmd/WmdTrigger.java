package com.ashraf.wmd;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WmdTrigger extends SimpleCriterionTrigger<WmdTrigger.Instance> {

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Wmd.MODID);
    public static final DeferredHolder<CriterionTrigger<?>, WmdTrigger> EVENT = TRIGGERS.register("event", WmdTrigger::new);

    @Override
    public Codec<Instance> codec() { return Instance.CODEC; }

    public record Instance(Optional<ContextAwarePredicate> player, String id) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.fieldOf("id").forGetter(Instance::id)).apply(i, Instance::new));
    }

    static void fire(ServerPlayer p, String id) {
        if (p != null) EVENT.get().trigger(p, inst -> inst.id().equals(id));
    }

    static void fire(MinecraftServer server, java.util.UUID player, String id) {
        if (server != null && player != null) fire(server.getPlayerList().getPlayer(player), id);
    }
}
