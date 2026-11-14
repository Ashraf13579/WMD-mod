package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class GunnerPayloads {

    private GunnerPayloads() {}

    private static final Map<UUID, int[]> LAST_INPUT = Statics.server(new HashMap<>());

    private static ResourceLocation id(String n) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, n); }

    public record Input(BlockPos station, float ax, float ay, float az, boolean fire, int buttons) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(id("gunner_input"));
        public static final StreamCodec<FriendlyByteBuf, Input> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.station()); b.writeFloat(p.ax()); b.writeFloat(p.ay()); b.writeFloat(p.az()); b.writeBoolean(p.fire()); b.writeByte(p.buttons()); },
                b -> new Input(b.readBlockPos(), b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean(), b.readByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Hud(BlockPos station, int count, int selected, long focus, int rounds, int roundsMax, int flags, float yaw, float elev, long pod, byte[] arc)
            implements CustomPacketPayload {
        public static final Type<Hud> TYPE = new Type<>(id("gunner_hud"));
        public static final StreamCodec<FriendlyByteBuf, Hud> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.station()); b.writeVarInt(p.count()); b.writeVarInt(p.selected() + 1); b.writeLong(p.focus()); b.writeVarInt(p.rounds());
                    b.writeVarInt(p.roundsMax()); b.writeVarInt(p.flags()); b.writeFloat(p.yaw()); b.writeFloat(p.elev()); b.writeLong(p.pod()); b.writeByteArray(p.arc()); },
                b -> new Hud(b.readBlockPos(), b.readVarInt(), b.readVarInt() - 1, b.readLong(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat(),
                        b.readLong(), b.readByteArray(512)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        boolean has(int f) { return (flags & f) != 0; }
    }

    public record Aim(BlockPos pos, float yaw, float elev, float deploy, int flags) implements CustomPacketPayload {
        public static final Type<Aim> TYPE = new Type<>(id("turret_aim"));
        public static final StreamCodec<FriendlyByteBuf, Aim> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.pos()); b.writeFloat(p.yaw()); b.writeFloat(p.elev()); b.writeFloat(p.deploy()); b.writeByte(p.flags()); },
                b -> new Aim(b.readBlockPos(), b.readFloat(), b.readFloat(), b.readFloat(), b.readByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Fire(BlockPos pos, double fx, double fy, double fz, double tx, double ty, double tz, int kind) implements CustomPacketPayload {
        public static final Type<Fire> TYPE = new Type<>(id("turret_fire"));
        public static final StreamCodec<FriendlyByteBuf, Fire> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.pos()); b.writeDouble(p.fx()); b.writeDouble(p.fy()); b.writeDouble(p.fz()); b.writeDouble(p.tx()); b.writeDouble(p.ty());
                    b.writeDouble(p.tz()); b.writeByte(p.kind()); },
                b -> new Fire(b.readBlockPos(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void handleInput(ServerPlayer sp, Input p) {
        if (!AirGate.on() || sp == null) return;
        if (!(sp.getVehicle() instanceof GunnerSeatEntity seat) || !seat.station().equals(p.station())) return;

        int buttons = p.buttons() & 3;
        if (buttons != 0 && AirframeRecords.tooSoon(LAST_INPUT, sp, 1, Balance.SCREEN_ASK_TICKS)) buttons = 0;
        if (buttons == 0 && AirframeRecords.tooSoon(LAST_INPUT, sp, 0, Balance.GUNNER_INPUT_GAP)) return;
        if (!(sp.level().getBlockEntity(p.station()) instanceof GunnerStationBlockEntity be)) return;
        Vec3 aim = Float.isFinite(p.ax()) && Float.isFinite(p.ay()) && Float.isFinite(p.az()) ? new Vec3(p.ax(), p.ay(), p.az()) : null;
        be.input(sp, aim, p.fire(), buttons);
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToServer(Input.TYPE, Input.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) handleInput(sp, p); }));
            r.playToClient(Hud.TYPE, Hud.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientGunner.onHud(p)));
            r.playToClient(Aim.TYPE, Aim.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientGunner.onAim(p)));
            r.playToClient(Fire.TYPE, Fire.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientGunner.onFire(p)));
        }
    }
}
