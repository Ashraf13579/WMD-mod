package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DronesPayload(boolean full, List<DroneView.Contact> drones, List<Integer> removed) implements CustomPacketPayload {

    public static final Type<DronesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "drones"));
    static final int MAX = 256, MAX_REMOVED = 1024;

    public static final StreamCodec<FriendlyByteBuf, DronesPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.full());
                int n = Math.min(MAX, p.drones().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    DroneView.Contact c = p.drones().get(i);
                    buf.writeVarInt(c.id()); buf.writeUUID(c.owner()); buf.writeDouble(c.x()); buf.writeDouble(c.y()); buf.writeDouble(c.z()); buf.writeVarInt(c.phase());
                }
                int m = Math.min(MAX_REMOVED, p.removed().size());
                buf.writeVarInt(m);
                for (int i = 0; i < m; i++) buf.writeVarInt(p.removed().get(i));
            },
            buf -> {
                boolean full = buf.readBoolean();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX) throw new IllegalArgumentException("drones: bad count " + n);
                List<DroneView.Contact> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(new DroneView.Contact(buf.readVarInt(), buf.readUUID(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt()));
                int m = buf.readVarInt();
                if (m < 0 || m > MAX_REMOVED) throw new IllegalArgumentException("drones: bad removed count " + m);
                List<Integer> gone = new ArrayList<>(m);
                for (int i = 0; i < m; i++) gone.add(buf.readVarInt());
                return new DronesPayload(full, l, gone);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
