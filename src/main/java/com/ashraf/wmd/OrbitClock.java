package com.ashraf.wmd;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class OrbitClock {

    private OrbitClock() {}

    public static final int AUTO = 0, DAY = 1, NIGHT = 2;

    static volatile int mode = AUTO;

    static void clearStatics() { mode = AUTO; }

    public static int phase(long gameTime) {
        return switch (mode) {
            case DAY -> Balance.STATION_SUN_TICKS / 2;
            case NIGHT -> (Balance.STATION_SUN_TICKS + Balance.STATION_ORBIT_TICKS) / 2;
            default -> (int) Math.floorMod(gameTime, (long) Balance.STATION_ORBIT_TICKS);
        };
    }

    static void set(net.minecraft.server.MinecraftServer server, int m) {
        mode = m;
        PacketDistributor.sendToAllPlayers(new Sync(m));
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, new Sync(mode));
    }

    public record Sync(int mode) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "orbit_clock"));
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Sync::new, Sync::mode);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Sync.TYPE, Sync.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> mode = p.mode()));
        }
    }
}
