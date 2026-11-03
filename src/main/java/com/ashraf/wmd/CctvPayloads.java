package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class CctvPayloads {

    private CctvPayloads() {}

    private static ResourceLocation rl(String p) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, p); }

    public record CamInfo(long pos, float yaw, boolean covert, int state) {}

    public record Mark(int kind, int id, float x, float y, float z) {}

    public record FeedData(long pos, int state, boolean dark, List<Mark> marks) {}

    static final int MAX_MARKS = 48;
    static final TicketType<ChunkPos> TICKET = TicketType.create("wmd_cctv", Comparator.comparingLong(ChunkPos::toLong), 100);

    public record Open(BlockPos monitor, int frequency, boolean online, List<CamInfo> cams, int alert) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(rl("cctv_open"));
        public static final StreamCodec<FriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.monitor()); buf.writeVarInt(p.frequency()); buf.writeBoolean(p.online());
                    int n = Math.min(Balance.CCTV_MAX, p.cams().size());
                    buf.writeVarInt(n);
                    for (int i = 0; i < n; i++) { CamInfo c = p.cams().get(i); buf.writeLong(c.pos()); buf.writeFloat(c.yaw()); buf.writeBoolean(c.covert()); buf.writeByte(c.state()); }
                    buf.writeVarInt(p.alert() + 1);
                },
                buf -> {
                    BlockPos m = buf.readBlockPos();
                    int f = buf.readVarInt();
                    boolean on = buf.readBoolean();
                    int n = buf.readVarInt();
                    if (n < 0 || n > Balance.CCTV_MAX) throw new IllegalArgumentException("cctv: bad camera count " + n);
                    List<CamInfo> l = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) l.add(new CamInfo(buf.readLong(), buf.readFloat(), buf.readBoolean(), buf.readByte()));
                    return new Open(m, f, on, List.copyOf(l), buf.readVarInt() - 1);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record FeedRequest(BlockPos monitor, List<Long> cams) implements CustomPacketPayload {
        public static final Type<FeedRequest> TYPE = new Type<>(rl("cctv_feed_request"));
        public static final StreamCodec<FriendlyByteBuf, FeedRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBlockPos(p.monitor()); int n = Math.min(4, p.cams().size()); buf.writeVarInt(n); for (int i = 0; i < n; i++) buf.writeLong(p.cams().get(i)); },
                buf -> {
                    BlockPos m = buf.readBlockPos();
                    int n = buf.readVarInt();
                    if (n < 0 || n > 4) throw new IllegalArgumentException("cctv: bad feed count " + n);
                    List<Long> l = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) l.add(buf.readLong());
                    return new FeedRequest(m, List.copyOf(l));
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Feed(BlockPos monitor, List<FeedData> feeds) implements CustomPacketPayload {
        public static final Type<Feed> TYPE = new Type<>(rl("cctv_feed"));
        public static final StreamCodec<FriendlyByteBuf, Feed> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.monitor());
                    int n = Math.min(4, p.feeds().size());
                    buf.writeVarInt(n);
                    for (int i = 0; i < n; i++) {
                        FeedData f = p.feeds().get(i);
                        buf.writeLong(f.pos()); buf.writeByte(f.state()); buf.writeBoolean(f.dark());
                        int k = Math.min(MAX_MARKS, f.marks().size());
                        buf.writeVarInt(k);
                        for (int j = 0; j < k; j++) { Mark m = f.marks().get(j); buf.writeByte(m.kind()); buf.writeVarInt(m.id()); buf.writeFloat(m.x()); buf.writeFloat(m.y()); buf.writeFloat(m.z()); }
                    }
                },
                buf -> {
                    BlockPos mon = buf.readBlockPos();
                    int n = buf.readVarInt();
                    if (n < 0 || n > 4) throw new IllegalArgumentException("cctv: bad feed count " + n);
                    List<FeedData> l = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        long pos = buf.readLong();
                        int st = buf.readByte();
                        boolean dark = buf.readBoolean();
                        int k = buf.readVarInt();
                        if (k < 0 || k > MAX_MARKS) throw new IllegalArgumentException("cctv: bad mark count " + k);
                        List<Mark> ms = new ArrayList<>(k);
                        for (int j = 0; j < k; j++) ms.add(new Mark(buf.readByte(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
                        l.add(new FeedData(pos, st, dark, List.copyOf(ms)));
                    }
                    return new Feed(mon, List.copyOf(l));
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static final Map<UUID, Integer> LAST_FEED = new HashMap<>(), LAST_MARK = new HashMap<>();

    public static synchronized void clearStatics() { LAST_FEED.clear(); LAST_MARK.clear(); }

    private static synchronized boolean cooled(Map<UUID, Integer> last, ServerPlayer sp, int gap) {
        int tick = sp.server.getTickCount();
        Integer t = last.get(sp.getUUID());
        if (t != null && tick >= t && tick - t < gap) return false;
        last.put(sp.getUUID(), tick);
        if (last.size() > 512) last.entrySet().removeIf(e -> tick < e.getValue() || tick - e.getValue() > gap);
        return true;
    }

    static void handleFeed(FeedRequest p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!cooled(LAST_FEED, sp, 5)) return;
            CcSecurityMonitorBlockEntity m = CcSecurityMonitorBlockEntity.at(sp, p.monitor());
            if (m == null) return;
            ServerLevel sl = sp.serverLevel();
            List<FeedData> out = new ArrayList<>();
            for (long pos : p.cams()) {
                CctvNet.Cam c = m.cam(pos);
                if (c == null) { out.add(new FeedData(pos, CcSecurityMonitorBlockEntity.LOST, false, List.of())); continue; }
                int st = m.state(sl, c.pos());
                if (st != CcSecurityMonitorBlockEntity.LIVE) { out.add(new FeedData(pos, st, false, List.of())); continue; }
                ChunkPos cp = new ChunkPos(c.pos());
                sl.getChunkSource().addRegionTicket(TICKET, cp, 3, cp);
                Vec3 lens = lens(c);
                out.add(new FeedData(pos, st, dark(sl, c.pos()), marks(sl, c, lens)));
            }
            PacketDistributor.sendToPlayer(sp, new Feed(p.monitor(), List.copyOf(out)));
        });
    }

    static Vec3 lens(CctvNet.Cam c) {
        Vec3 f = Vec3.directionFromRotation(0.0F, c.yaw());
        Vec3 b = Vec3.atBottomCenterOf(c.pos());
        return c.covert() ? b.add(0.0, 2.0 / 16.0, 0.0).add(f.scale(0.2)) : b.add(0.0, 9.0 / 16.0, 0.0).add(f.scale(0.4));
    }

    static boolean dark(ServerLevel sl, BlockPos pos) {
        return !sl.hasChunkAt(pos) || !sl.isDay() || sl.getRawBrightness(pos, sl.getSkyDarken()) < 7;
    }

    static List<Mark> marks(ServerLevel sl, CctvNet.Cam c, Vec3 lens) {
        if (!sl.hasChunkAt(c.pos())) return List.of();

        float yaw = sl.getBlockEntity(c.pos()) instanceof CctvCameraBlockEntity cam ? cam.lookYaw() : c.yaw();
        Vec3 f = Vec3.directionFromRotation(0.0F, yaw);
        List<LivingEntity> l = sl.getEntitiesOfClass(LivingEntity.class, new AABB(lens, lens).inflate(Balance.CCTV_SEE),
                e -> e.isAlive() && !e.isInvisible() && !e.isSpectator());
        l.sort(Comparator.comparingDouble(e -> e.distanceToSqr(lens)));
        List<Mark> out = new ArrayList<>();
        for (LivingEntity e : l) {
            if (out.size() >= MAX_MARKS) break;
            Vec3 mid = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0), d = mid.subtract(lens);
            double len = d.length();
            if (len > Balance.CCTV_SEE || len < 0.3 || d.dot(f) / len < -0.35) continue;
            if (sl.clip(new ClipContext(lens.add(d.scale(0.5 / len)), mid, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null)).getType() != HitResult.Type.MISS) continue;
            int kind = e instanceof Player ? AreaMarkersPayload.PLAYER : e instanceof Enemy ? AreaMarkersPayload.MOB : AreaMarkersPayload.CREATURE;
            out.add(new Mark(kind, e.getId(), (float) e.getX(), (float) e.getY(), (float) e.getZ()));
        }
        return out;
    }

    public record View(BlockPos monitor, long cam, float pan) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(rl("cctv_view"));
        public static final StreamCodec<FriendlyByteBuf, View> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBlockPos(p.monitor()); buf.writeLong(p.cam()); buf.writeFloat(p.pan()); },
                buf -> new View(buf.readBlockPos(), buf.readLong(), buf.readFloat()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void handleView(View p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            CcSecurityMonitorBlockEntity m = p.cam() == 0L ? null : CcSecurityMonitorBlockEntity.at(sp, p.monitor());
            CctvNet.Cam c = m == null ? null : m.cam(p.cam());
            if (c == null) { CctvNet.viewing(sp.getUUID(), null); return; }
            CctvNet.viewing(sp.getUUID(), c.pos());
            Level lv = sp.level();
            if (Float.isFinite(p.pan()) && lv.isLoaded(c.pos()) && lv.getBlockEntity(c.pos()) instanceof CctvCameraBlockEntity cam) cam.setPan(p.pan(), lv.getGameTime());
        });
    }

    public record MarkTarget(BlockPos monitor, long cam, int entity) implements CustomPacketPayload {
        public static final Type<MarkTarget> TYPE = new Type<>(rl("cctv_mark"));
        public static final StreamCodec<FriendlyByteBuf, MarkTarget> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBlockPos(p.monitor()); buf.writeLong(p.cam()); buf.writeVarInt(p.entity()); },
                buf -> new MarkTarget(buf.readBlockPos(), buf.readLong(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void handleMark(MarkTarget p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!cooled(LAST_MARK, sp, 10)) return;
            CcSecurityMonitorBlockEntity m = CcSecurityMonitorBlockEntity.at(sp, p.monitor());
            CctvNet.Cam c = m == null ? null : m.cam(p.cam());
            ServerLevel sl = sp.serverLevel();
            if (c == null || m.state(sl, c.pos()) != CcSecurityMonitorBlockEntity.LIVE || !(sl.getEntity(p.entity()) instanceof LivingEntity e)) return;
            boolean seen = false;
            for (Mark k : marks(sl, c, lens(c))) if (k.id() == e.getId()) { seen = true; break; }
            if (!seen || !DefenceTargets.hostile(sl, m, e)) { sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("hud.wmd.cctv.mark_no"), true); return; }
            long now = sl.getGameTime();
            int n = 0;
            Vec3 at = e.position();
            for (FloodlightBlockEntity f : CctvNet.near(sl, FloodlightBlockEntity.class, at, Balance.CCTV_CUE))
                if (m.getOwner() != null && m.getOwner().equals(f.getOwner())) { f.cue(e, now); n++; }
            for (SentryTurretBlockEntity t : CctvNet.near(sl, SentryTurretBlockEntity.class, at, Balance.CCTV_CUE))
                if (m.getOwner() != null && m.getOwner().equals(t.getOwner())) { t.cue(e); n++; }
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("hud.wmd.cctv.marked", e.getDisplayName(), n), true);
        });
    }
}
