package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record VnMapPayload(int x0, int z0, int chunks, List<String> palette, byte[] grid) implements CustomPacketPayload {

    public static final Type<VnMapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "vn_map"));
    static final int MAX_CHUNKS = 96;

    public static final StreamCodec<FriendlyByteBuf, VnMapPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeInt(p.x0()); b.writeInt(p.z0()); b.writeVarInt(p.chunks()); b.writeVarInt(p.palette().size()); for (String s : p.palette()) b.writeUtf(s, 64); b.writeByteArray(p.grid()); },
            b -> {
                int x0 = b.readInt(), z0 = b.readInt(), c = b.readVarInt(), n = b.readVarInt();
                if (c < 0 || c > MAX_CHUNKS || n < 0 || n > 120) throw new IllegalArgumentException("vn_map: bad size");
                List<String> pal = new ArrayList<>(n);
                for (int i = 0; i < n; i++) pal.add(b.readUtf(64));
                return new VnMapPayload(x0, z0, c, pal, b.readByteArray(MAX_CHUNKS * MAX_CHUNKS));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    static void send(ServerPlayer sp, ServerLevel level, int x0, int z0, int size) {
        if (!VnBridge.active() || size <= 0) return;
        int c = Math.min(MAX_CHUNKS, (size + 15) / 16);
        int cx0 = x0 >> 4, cz0 = z0 >> 4;
        List<String> pal = new ArrayList<>();
        Map<String, Integer> idx = new HashMap<>();
        byte[] grid = new byte[c * c];
        boolean any = false;
        for (int j = 0; j < c; j++)
            for (int i = 0; i < c; i++) {
                String land = VnBridge.countryAt(level, new BlockPos(((cx0 + i) << 4) + 8, 64, ((cz0 + j) << 4) + 8));
                if (land == null) continue;
                Integer k = idx.get(land);
                if (k == null) { if (pal.size() >= 120) continue; pal.add(land); k = pal.size(); idx.put(land, k); }
                grid[j * c + i] = (byte) (int) k;
                any = true;
            }
        PacketDistributor.sendToPlayer(sp, new VnMapPayload(cx0 << 4, cz0 << 4, any ? c : 0, pal, any ? grid : new byte[0]));
    }

    static volatile VnMapPayload last;

    static void onReceive(VnMapPayload p) { last = p.chunks() > 0 ? p : null; }

    static int colour(String id) {
        float h = (id.hashCode() & 0xFFFF) / 65535.0F;
        return java.awt.Color.HSBtoRGB(h, 0.65F, 0.95F) & 0xFFFFFF;
    }
}
