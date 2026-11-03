package com.ashraf.wmd;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class VacuumBurst {

    private VacuumBurst() {}

    public record Burst(double x, double y, double z, float size) implements CustomPacketPayload {
        public static final Type<Burst> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "vacuum_burst"));
        public static final StreamCodec<FriendlyByteBuf, Burst> CODEC = StreamCodec.of(
                (b, p) -> { b.writeDouble(p.x()); b.writeDouble(p.y()); b.writeDouble(p.z()); b.writeFloat(p.size()); },
                b -> new Burst(b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final java.util.concurrent.ConcurrentLinkedQueue<Burst> PENDING = Statics.client(new java.util.concurrent.ConcurrentLinkedQueue<>());

    public static void send(ServerLevel level, Vec3 at, double size) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 600.0, new Burst(at.x, at.y, at.z, (float) size));
    }

    private static boolean redoing;

    @SubscribeEvent
    static void onExplosion(ExplosionEvent.Start event) {
        if (redoing || !(event.getLevel() instanceof ServerLevel level) || !Orbit.isOrbit(level)) return;
        Explosion e = event.getExplosion();
        event.setCanceled(true);
        Level.ExplosionInteraction how = switch (e.getBlockInteraction()) {
            case KEEP -> Level.ExplosionInteraction.NONE;
            case TRIGGER_BLOCK -> Level.ExplosionInteraction.TRIGGER;
            default -> Level.ExplosionInteraction.TNT;
        };
        Vec3 c = e.center();
        Holder<SoundEvent> silent = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY);
        redoing = true;
        try {
            level.explode(e.getDirectSourceEntity(), null, null, c.x, c.y, c.z, e.radius(), false, how, ParticleTypes.FLASH, ParticleTypes.FLASH, silent);
        } finally {
            redoing = false;
        }
        send(level, c, e.radius());
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Burst.TYPE, Burst.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
        }
    }
}
