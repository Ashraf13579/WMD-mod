package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CockpitInputPayload(BlockPos cockpit, int seat, int pitch, int roll, int yaw, float throttle, boolean afterburner, int buttons, int station,
                                  boolean gun, boolean brake, float ax, float ay, float az, float dx, float dy, float dz)
        implements CustomPacketPayload {

    public static final Type<CockpitInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cockpit_input"));

    public static final StreamCodec<FriendlyByteBuf, CockpitInputPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.cockpit()); buf.writeByte(p.seat()); buf.writeByte(p.pitch()); buf.writeByte(p.roll()); buf.writeByte(p.yaw());
                buf.writeFloat(p.throttle()); buf.writeBoolean(p.afterburner()); buf.writeVarInt(p.buttons()); buf.writeByte(p.station()); buf.writeBoolean(p.gun());
                buf.writeBoolean(p.brake()); buf.writeFloat(p.ax()); buf.writeFloat(p.ay()); buf.writeFloat(p.az());
                buf.writeFloat(p.dx()); buf.writeFloat(p.dy()); buf.writeFloat(p.dz()); },
            buf -> new CockpitInputPayload(buf.readBlockPos(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readFloat(), buf.readBoolean(),
                    buf.readVarInt(), buf.readByte(), buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static net.minecraft.world.phys.Vec3 aimDir(CockpitInputPayload p) {
        if (!Float.isFinite(p.dx()) || !Float.isFinite(p.dy()) || !Float.isFinite(p.dz())) return null;
        net.minecraft.world.phys.Vec3 d = new net.minecraft.world.phys.Vec3(p.dx(), p.dy(), p.dz());
        double l = d.length();
        return l > 0.5 && l < 1.5 ? d.scale(1.0 / l) : null;
    }

    public static void handle(CockpitInputPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!AirGate.on() || !(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.getVehicle() instanceof CockpitSeatEntity seat) || !seat.cockpit().equals(p.cockpit()) || seat.seatIndex() != p.seat()) return;
            float thr = Float.isFinite(p.throttle()) ? p.throttle() : 0.0F;
            if (sp.level().getBlockEntity(p.cockpit()) instanceof JetCockpitBlockEntity be)
                be.input(sp, p.seat(), Integer.signum(p.pitch()), Integer.signum(p.roll()), Integer.signum(p.yaw()), thr, p.afterburner(), p.buttons() & 0x7FFFFF,                   p.station(), p.gun(),
                        p.brake(), Float.isFinite(p.ax()) && Float.isFinite(p.ay()) && Float.isFinite(p.az()) ? new net.minecraft.world.phys.Vec3(p.ax(), p.ay(), p.az()) : null,
                        aimDir(p));
        });
    }
}
