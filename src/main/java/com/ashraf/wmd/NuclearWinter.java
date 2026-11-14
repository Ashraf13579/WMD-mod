package com.ashraf.wmd;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class NuclearWinter {

    private NuclearWinter() {}

    static final String NAME = "wmd_nuclear_winter";

    static final class Data extends SavedData {
        final List<long[]> blasts = new ArrayList<>();
        long start, end;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        static Data load(CompoundTag tag, HolderLookup.Provider r) {
            Data d = new Data();
            long[] b = tag.getLongArray("blasts");
            for (int i = 0; i + 1 < b.length; i += 2) d.blasts.add(new long[] { b[i], b[i + 1] });
            d.start = tag.getLong("start"); d.end = tag.getLong("end");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider r) {
            long[] b = new long[blasts.size() * 2];
            for (int i = 0; i < blasts.size(); i++) { b[2 * i] = blasts.get(i)[0]; b[2 * i + 1] = blasts.get(i)[1]; }
            tag.putLongArray("blasts", b);
            tag.putLong("start", start); tag.putLong("end", end);
            return tag;
        }
    }

    static int weight(WarheadType t) {
        return switch (t) { case NUKE -> Balance.WINTER_W_NUKE; case THERMO -> Balance.WINTER_W_THERMO; case TSAR -> Balance.WINTER_W_TSAR; default -> 0; };
    }

    static boolean active(MinecraftServer server) { return Data.get(server).end > server.overworld().getGameTime(); }

    static void blast(ServerLevel level, WarheadType t) {
        int w = weight(t);
        if (w <= 0 || level.dimension() != Level.OVERWORLD) return;
        MinecraftServer server = level.getServer();
        Data d = Data.get(server);
        long now = level.getGameTime();
        d.blasts.removeIf(b -> now - b[0] > Balance.WINTER_WINDOW);
        d.blasts.add(new long[] { now, w });
        d.setDirty();
        long sum = 0;
        for (long[] b : d.blasts) sum += b[1];
        Wmd.LOGGER.info("[winter] a {} blast: {} of {} in the last 2 days", t.id, sum, Balance.WINTER_THRESHOLD);
        if (sum >= Balance.WINTER_THRESHOLD) { d.blasts.clear(); begin(server, now); }
    }

    static void begin(MinecraftServer server, long now) {
        Data d = Data.get(server);
        boolean fresh = d.end <= now;
        if (fresh) d.start = now;
        d.end = now + Balance.WINTER_TICKS;
        d.setDirty();
        PacketDistributor.sendToAllPlayers(new Sync(d.start, d.end));
        Component title = Component.translatable(fresh ? "title.wmd.winter.start" : "title.wmd.winter.longer").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.wmd.winter.sub", Balance.WINTER_TICKS / 24_000)));
            p.sendSystemMessage(Component.translatable("message.wmd.winter.start", Balance.WINTER_TICKS / 24_000).withStyle(ChatFormatting.GRAY));
            p.playNotifySound(WmdSounds.WI_START.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1.0F, 1.0F);
        }
        Wmd.LOGGER.info("[winter] NUCLEAR WINTER {} until game time {}", fresh ? "begins" : "extended", d.end);
    }

    static void stop(MinecraftServer server) {
        Data d = Data.get(server);
        long now = server.overworld().getGameTime();
        boolean was = d.end > now;
        d.end = Math.min(d.end, now);
        d.blasts.clear();
        d.setDirty();
        PacketDistributor.sendToAllPlayers(new Sync(d.start, d.end));
        if (was) for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(Component.translatable("message.wmd.winter.end").withStyle(ChatFormatting.GREEN));
    }

    static Component status(MinecraftServer server) {
        Data d = Data.get(server);
        long now = server.overworld().getGameTime(), sum = 0;
        for (long[] b : d.blasts) if (now - b[0] <= Balance.WINTER_WINDOW) sum += b[1];
        if (d.end > now) return Component.literal("NUCLEAR WINTER: " + (d.end - now) / 1200 + " min left; count " + sum + " / " + Balance.WINTER_THRESHOLD);
        return Component.literal("No nuclear winter; count " + sum + " / " + Balance.WINTER_THRESHOLD + " in the last 2 days");
    }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || level.getGameTime() % 20 != 0) return;
        Data d = Data.get(level.getServer());
        long now = level.getGameTime();
        if (d.end > 0 && d.end <= now && d.end > now - 20) {
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) p.sendSystemMessage(Component.translatable("message.wmd.winter.end").withStyle(ChatFormatting.GREEN));
            PacketDistributor.sendToAllPlayers(new Sync(d.start, d.end));
        }
        boolean on = d.end > now, melting = !on && d.end > 0 && now - d.end < Balance.WINTER_MELT_TICKS;
        if (!on && !melting) return;
        for (ServerPlayer p : level.players()) {
            for (int i = 0; i < Balance.WINTER_SNOW_TRIES; i++) {
                int x = p.getBlockX() + level.random.nextInt(97) - 48, z = p.getBlockZ() + level.random.nextInt(97) - 48;
                if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
                BlockState below = level.getBlockState(top.below()), here = level.getBlockState(top);
                if (on) {
                    if (here.is(Blocks.SNOW)) {
                        int n = here.getValue(SnowLayerBlock.LAYERS);
                        if (n < Balance.WINTER_SNOW_MAX) level.setBlockAndUpdate(top, here.setValue(SnowLayerBlock.LAYERS, n + 1));
                    } else if (below.is(Blocks.SNOW)) {
                        int n = below.getValue(SnowLayerBlock.LAYERS);
                        if (n < Balance.WINTER_SNOW_MAX) level.setBlockAndUpdate(top.below(), below.setValue(SnowLayerBlock.LAYERS, n + 1));
                    } else if (here.isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, top)) level.setBlockAndUpdate(top, Blocks.SNOW.defaultBlockState());
                } else {
                    BlockPos s = here.is(Blocks.SNOW) ? top : below.is(Blocks.SNOW) ? top.below() : null;
                    if (s == null || !level.getBiome(s).value().warmEnoughToRain(s)) continue;
                    BlockState st = level.getBlockState(s);
                    int n = st.getValue(SnowLayerBlock.LAYERS);
                    if (n > 1) level.setBlockAndUpdate(s, st.setValue(SnowLayerBlock.LAYERS, n - 1)); else level.removeBlock(s, false);
                }
            }
        }
    }

    @SubscribeEvent
    static void onCrop(CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (Data.get(level.getServer()).end > level.getGameTime() && level.random.nextDouble() > Balance.WINTER_CROP_SPEED)
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) { Data d = Data.get(sp.server); PacketDistributor.sendToPlayer(sp, new Sync(d.start, d.end)); }
    }

    @SubscribeEvent
    static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("winter").requires(TestCmds::test)
                        .then(net.minecraft.commands.Commands.literal("start").executes(c -> { begin(c.getSource().getServer(), c.getSource().getServer().overworld().getGameTime()); return 1; }))
                        .then(net.minecraft.commands.Commands.literal("stop").executes(c -> { stop(c.getSource().getServer()); return 1; }))
                        .then(net.minecraft.commands.Commands.literal("status").executes(c -> { c.getSource().sendSuccess(() -> status(c.getSource().getServer()), false); return 1; }))));
    }

    public record Sync(long start, long end) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "nuclear_winter"));
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_LONG, Sync::start, ByteBufCodecs.VAR_LONG, Sync::end, Sync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static volatile long cStart, cEnd;

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Sync.TYPE, Sync.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> { cStart = p.start(); cEnd = p.end(); }));
        }
    }
}
