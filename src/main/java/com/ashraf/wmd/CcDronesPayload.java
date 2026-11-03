package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

public record CcDronesPayload(BlockPos table, List<Row> drones, List<Site> sites) implements CustomPacketPayload {

    public record Row(int id, int kind, int phase, int battery, int dist) {}
    public record Site(BlockPos pos, BlockPos at, boolean hangar, int tier, int bombs, int status, int out, int[] mission, int range) {}

    static final int MAX_DRONES = 24, MAX_SITES = 16;

    public static final Type<CcDronesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_drones"));

    public static final StreamCodec<FriendlyByteBuf, CcDronesPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.table());
                buf.writeVarInt(Math.min(MAX_DRONES, p.drones().size()));
                for (int i = 0; i < Math.min(MAX_DRONES, p.drones().size()); i++) {
                    Row r = p.drones().get(i);
                    buf.writeVarInt(r.id()); buf.writeVarInt(r.kind()); buf.writeVarInt(r.phase()); buf.writeInt(r.battery()); buf.writeVarInt(r.dist());
                }
                buf.writeVarInt(Math.min(MAX_SITES, p.sites().size()));
                for (int i = 0; i < Math.min(MAX_SITES, p.sites().size()); i++) {
                    Site s = p.sites().get(i);
                    buf.writeBlockPos(s.pos()); buf.writeBlockPos(s.at()); buf.writeBoolean(s.hangar()); buf.writeVarInt(s.tier()); buf.writeVarInt(s.bombs()); buf.writeVarInt(s.status());
                    buf.writeVarInt(s.out()); buf.writeVarIntArray(s.mission()); buf.writeVarInt(s.range());
                }
            },
            buf -> {
                BlockPos table = buf.readBlockPos();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_DRONES) throw new IllegalArgumentException("cc drones: " + n);
                List<Row> rows = new ArrayList<>();
                for (int i = 0; i < n; i++) rows.add(new Row(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readVarInt()));
                int m = buf.readVarInt();
                if (m < 0 || m > MAX_SITES) throw new IllegalArgumentException("cc drone sites: " + m);
                List<Site> sites = new ArrayList<>();
                for (int i = 0; i < m; i++) {
                    BlockPos pos = buf.readBlockPos(), at = buf.readBlockPos();
                    boolean hangar = buf.readBoolean();
                    int tier = buf.readVarInt(), bombs = buf.readVarInt(), status = buf.readVarInt(), out = buf.readVarInt();
                    int[] mission = buf.readVarIntArray(3 + 2 * DroneMission.WAYPOINTS);
                    sites.add(new Site(pos, at, hangar, tier, bombs, status, out, mission, buf.readVarInt()));
                }
                return new CcDronesPayload(table, rows, sites);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    static CcDronesPayload of(ServerLevel level, BlockPos table, UUID owner) {
        List<Row> rows = new ArrayList<>();
        net.minecraft.world.phys.Vec3 tw = SableBridge.worldPos(level, table);
        for (Drones.Drone d : Drones.get(level.getServer()).drones()) {
            if (!owner.equals(d.owner) || !d.airborne() || rows.size() >= MAX_DRONES) continue;
            int full = switch (d.kind) { case 1 -> Balance.DRONE_T1_BATTERY; case 2 -> Balance.DRONE_T2_BATTERY; case 3 -> Balance.DRONE_T3_BATTERY; case 4 -> Balance.DRONE_T4_BATTERY; default -> 0; };
            int bat = full > 0 ? Mth.clamp(d.battery * 100 / full, 0, 100) : -1;
            rows.add(new Row(d.id, d.kind, d.phase, bat, (int) Math.round(Math.hypot(d.x - tw.x, d.z - tw.z))));
        }
        List<Site> sites = new ArrayList<>();
        for (BigDroneSite s : BigDrones.sitesOf(owner, level)) {
            if (sites.size() >= MAX_SITES) break;
            DroneType t = DroneType.of(s.droneSlots().getStackInSlot(0));
            if (s.isHangar() && t != DroneType.T4) t = null;
            int bombs = 0;
            for (int i = 0; i < s.bombSlots().getSlots(); i++) if (!s.bombSlots().getStackInSlot(i).isEmpty()) bombs++;
            sites.add(new Site(s.sitePos(), BlockPos.containing(SableBridge.worldPos(level, s.sitePos())), s.isHangar(), t == null ? 0 : t.tier, bombs, s.siteStatus(), s.outId(), wire(s.mission()),
                    t == DroneType.T4 || s.isHangar() ? Balance.DRONE_T4_RANGE : Balance.DRONE_T3_RANGE));
        }
        return new CcDronesPayload(table, rows, sites);
    }

    static int[] wire(DroneMission m) {
        if (m == null || !m.hasTarget()) return new int[0];
        int n = m.xs().length;
        int[] d = new int[3 + 2 * n];
        d[0] = n;
        for (int i = 0; i < n; i++) { d[1 + 2 * i] = m.xs()[i]; d[2 + 2 * i] = m.zs()[i]; }
        d[1 + 2 * n] = m.tx(); d[2 + 2 * n] = m.tz();
        return d;
    }
}
