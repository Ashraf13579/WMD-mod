package com.ashraf.wmd;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SecurityActionPayload(BlockPos pos, int action, String text) implements CustomPacketPayload {

    public static final Type<SecurityActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "security_action"));

    public static final StreamCodec<FriendlyByteBuf, SecurityActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SecurityActionPayload::pos,
            ByteBufCodecs.VAR_INT, SecurityActionPayload::action,
            ByteBufCodecs.stringUtf8(32), SecurityActionPayload::text,
            SecurityActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static final Map<UUID, Integer> LAST_EDIT = new java.util.HashMap<>();
    private static final int COOLDOWN_TICKS = 20;

    static final int MAX_TRUSTED = 32;

    public static void handle(SecurityActionPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!Reach.usable(sp, p.pos(), 30.0)) return;
            BlockEntity be = sp.level().getBlockEntity(p.pos());
            if (!(be instanceof Owned o)) return;
            boolean edit = Access.mayConfigure(o, sp);
            if (edit && p.action() != 0) {
                int now = sp.server.getTickCount();
                Integer last = LAST_EDIT.get(sp.getUUID());
                if (last != null && now - last >= 0 && now - last < COOLDOWN_TICKS) { reply(sp, p.pos(), o); return; }
                LAST_EDIT.put(sp.getUUID(), now);
                if (LAST_EDIT.size() > 512) LAST_EDIT.entrySet().removeIf(e -> now - e.getValue() > COOLDOWN_TICKS || now < e.getValue());
                SecurityData sec = o.security();
                switch (p.action()) {
                    case 1 -> sec.setPublic(!sec.isPublic());
                    case 2 -> {
                        String name = p.text().trim();
                        if (name.isEmpty() || name.length() > 16 || !name.matches("[A-Za-z0-9_]+")) {
                            sp.displayClientMessage(Component.translatable("message.wmd.security.unknown_player", name), true);
                            break;
                        }
                        ServerPlayer online = sp.server.getPlayerList().getPlayerByName(name);
                        if (online != null) { trust(sp, p.pos(), online.getUUID(), online.getGameProfile().getName()); return; }

                        var cache = sp.server.getProfileCache();
                        if (cache == null) { sp.displayClientMessage(Component.translatable("message.wmd.security.unknown_player", name), true); break; }
                        java.util.concurrent.CompletableFuture<Optional<GameProfile>> f;
                        try { f = cache.getAsync(name); }
                        catch (IllegalStateException noExecutor) { sp.displayClientMessage(Component.translatable("message.wmd.security.unknown_player", name), true); break; }
                        final UUID asker = sp.getUUID();
                        final BlockPos pos = p.pos();
                        final net.minecraft.server.MinecraftServer server = sp.server;
                        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = sp.level().dimension();
                        f.whenComplete((prof, err) -> server.execute(() -> {
                            ServerPlayer back = server.getPlayerList().getPlayer(asker);
                            if (back == null || !back.level().dimension().equals(dim)) return;
                            if (err != null || prof == null || prof.isEmpty()) {
                                back.displayClientMessage(Component.translatable("message.wmd.security.unknown_player", name), true);
                                return;
                            }
                            trust(back, pos, prof.get().getId(), prof.get().getName());
                        }));
                        return;
                    }
                    case 3 -> sec.trusted().values().removeIf(n -> n.equalsIgnoreCase(p.text().trim()));
                    default -> { }
                }
                be.setChanged();
            }
            reply(sp, p.pos(), o);
        });
    }

    private static void trust(ServerPlayer sp, BlockPos pos, UUID id, String shown) {
        if (!Reach.usable(sp, pos, 30.0)) return;
        BlockEntity be = sp.level().getBlockEntity(pos);
        if (!(be instanceof Owned o) || !Access.mayConfigure(o, sp)) return;
        if (id != null && !id.equals(o.getOwner()) && (o.security().trusted().size() < MAX_TRUSTED || o.security().trusted().containsKey(id))) { o.security().trusted().put(id, shown); be.setChanged(); }
        reply(sp, pos, o);
    }

    private static void reply(ServerPlayer sp, BlockPos pos, Owned o) {
        boolean edit = Access.mayConfigure(o, sp);
        java.util.List<String> names = Access.mayUse(o, sp) ? java.util.List.copyOf(o.security().trusted().values()) : java.util.List.of();
        if (names.size() > SecurityStatePayload.MAX_NAMES) names = names.subList(0, SecurityStatePayload.MAX_NAMES);
        PacketDistributor.sendToPlayer(sp, new SecurityStatePayload(pos, o.security().isPublic(), edit, o.getOwnerName(), names));
    }

    static void clearStatics() { LAST_EDIT.clear(); }
}
