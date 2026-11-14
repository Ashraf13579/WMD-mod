package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class SeaPilotPayloads {

    private SeaPilotPayloads() {}

    static final int OPEN = 0, SET = 1, ENGAGE = 2, OFF = 3, QUICK = 4, TILE = 5, LAST = TILE;
    private static final Map<UUID, int[]> LAST_ASK = Statics.server(new HashMap<>());

    private static ResourceLocation id(String n) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, n); }

    public record Ask(BlockPos pos, int op, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(id("sea_pilot_ask"));
        public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.pos()); b.writeByte(p.op()); b.writeNbt(p.data()); },
                b -> {
                    BlockPos pos = b.readBlockPos();
                    int op = b.readByte();
                    CompoundTag t = b.readNbt();
                    return new Ask(pos, op, t == null ? new CompoundTag() : t);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record State(BlockPos pos, boolean open, CompoundTag data) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(id("sea_pilot_state"));
        public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.pos()); b.writeBoolean(p.open()); b.writeNbt(p.data()); },
                b -> {
                    BlockPos pos = b.readBlockPos();
                    boolean o = b.readBoolean();
                    CompoundTag t = b.readNbt();
                    return new State(pos, o, t == null ? new CompoundTag() : t);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Tile(BlockPos pos, int x0, int z0, int cell, int w, int h, int[] cells) implements CustomPacketPayload {
        public static final Type<Tile> TYPE = new Type<>(id("sea_pilot_tile"));
        public static final StreamCodec<FriendlyByteBuf, Tile> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.pos()); b.writeInt(p.x0()); b.writeInt(p.z0()); b.writeVarInt(p.cell()); b.writeVarInt(p.w()); b.writeVarInt(p.h()); b.writeVarIntArray(p.cells()); },
                b -> {
                    BlockPos pos = b.readBlockPos();
                    int x0 = b.readInt(), z0 = b.readInt(), cell = b.readVarInt();
                    int w = Mth.clamp(b.readVarInt(), 1, TerrainAtlas.MAX_CELLS), h = Mth.clamp(b.readVarInt(), 1, TerrainAtlas.MAX_CELLS);
                    int[] c = b.readVarIntArray(TerrainAtlas.MAX_CELLS * TerrainAtlas.MAX_CELLS);
                    return new Tile(pos, x0, z0, cell, w, h, c.length == w * h ? c : new int[w * h]);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Hud(BlockPos helm, int mode, int leg, int legs, float dist, int speed, float depth, float clearance, int crushPct, int reason, boolean capped,
                      float course) implements CustomPacketPayload {
        public static final Type<Hud> TYPE = new Type<>(id("sea_pilot_hud"));
        public static final StreamCodec<FriendlyByteBuf, Hud> CODEC = StreamCodec.of(
                (b, p) -> {
                    b.writeBlockPos(p.helm()); b.writeByte(p.mode()); b.writeByte(p.leg()); b.writeByte(p.legs()); b.writeFloat(p.dist()); b.writeByte(p.speed());
                    b.writeFloat(p.depth()); b.writeFloat(p.clearance()); b.writeShort(p.crushPct()); b.writeByte(p.reason()); b.writeBoolean(p.capped()); b.writeFloat(p.course());
                },
                b -> new Hud(b.readBlockPos(), b.readByte(), b.readByte(), b.readByte(), b.readFloat(), b.readByte(), b.readFloat(), b.readFloat(), b.readShort(),
                        b.readByte(), b.readBoolean(), b.readFloat()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void handleAsk(ServerPlayer sp, Ask p) {
        if (!AirGate.on() || sp == null || p.op() < 0 || p.op() > LAST) return;
        int slot = p.op() == OPEN ? 0 : p.op() == TILE ? 2 : 1;
        if (AirframeRecords.tooSoon(LAST_ASK, sp, slot, slot == 0 ? Balance.SP_STATE_GAP : slot == 2 ? Balance.SP_TILE_GAP : Balance.SP_PRESS_GAP)) return;
        ServerLevel sl = sp.serverLevel();
        BlockPos src = p.pos();
        HelmStationBlockEntity helm;
        boolean seated = sp.getVehicle() instanceof HelmSeatEntity seat && seat.helm().equals(src);
        if (seated) {
            if (!(sl.getBlockEntity(src) instanceof HelmStationBlockEntity h) || h.isRemoved()) return;
            helm = h;
        } else {
            if (p.op() == QUICK) return;
            if (!Reach.usable(sp, src, Balance.CHART_REACH)) return;
            if (!(sl.getBlockEntity(src) instanceof ChartTableBlockEntity t) || t.isRemoved()) return;
            if (t.getOwner() == null || !Access.mayUse(t, sp)) return;
            helm = t.helm(sl);
        }
        if (p.op() == TILE) { tile(sp, src, p.data()); return; }
        UUID ship = SableBridge.shipIdAt(sl, src);
        SeaPilot.Ctx c = helm != null ? helm.apCtx(sl) : null;
        String msg = "";
        if (p.op() != OPEN) msg = helm == null ? "message.wmd.sp.no_helm" : helm.apCommand(p.op(), p.data(), c);
        if (helm != null && p.op() != OPEN) c = helm.apCtx(sl);
        PacketDistributor.sendToPlayer(sp, new State(src, p.op() == OPEN && p.data().getBoolean("open"), SeaChart.state(sl, src, ship, helm, c, msg)));
    }

    private static void tile(ServerPlayer sp, BlockPos src, CompoundTag d) {
        int cell = Mth.clamp(Integer.highestOneBit(Math.max(4, d.getInt("cell"))), 4, 64);
        int w = Mth.clamp(d.getInt("w"), 1, TerrainAtlas.MAX_CELLS), h = Mth.clamp(d.getInt("h"), 1, TerrainAtlas.MAX_CELLS);
        int x0 = d.getInt("x0"), z0 = d.getInt("z0");
        if (Math.abs(x0 - sp.getX()) > Balance.CHART_RANGE * 2 || Math.abs(z0 - sp.getZ()) > Balance.CHART_RANGE * 2) return;
        PacketDistributor.sendToPlayer(sp, new Tile(src, x0, z0, cell, w, h, SeaChart.tile(sp, x0, z0, cell, w, h)));
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToServer(Ask.TYPE, Ask.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) handleAsk(sp, p); }));
            r.playToClient(State.TYPE, State.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientSeaPilotBridge.onState(p)));
            r.playToClient(Tile.TYPE, Tile.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientSeaPilotBridge.onTile(p)));
            r.playToClient(Hud.TYPE, Hud.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientSeaPilotBridge.onHud(p)));
        }
    }
}
