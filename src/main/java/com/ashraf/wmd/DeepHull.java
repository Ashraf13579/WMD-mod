package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class DeepHull {

    private DeepHull() {}

    static final String[] FITTINGS = { "torpedo_tube", "launch_cell", "periscope", "escape_pod", "comms_buoy", "towed_array_winch", "sonar_dome",
            "hydrophone_array", "machine_part", "noisemaker_launcher",
            "hull_penetrator" };

    static int apply() {
        if (!Config.Server.deepHullRatings() || !DeepSeasBridge.active()) return 0;
        int n = 0;
        n += put("manganese_hull_plate", Balance.DEEP_HULL_RATING[0], Balance.DEEP_HULL_CRACK[0]);
        n += put("superalloy_hull", Balance.DEEP_HULL_RATING[1], Balance.DEEP_HULL_CRACK[1]);
        n += put("deep_viewport", Balance.DEEP_VIEWPORT_RATING, Balance.DEEP_VIEWPORT_CRACK);
        n += put("anechoic_tile", Balance.ANECHOIC_RATING, Balance.ANECHOIC_CRACK);
        for (String f : FITTINGS) n += put(f, Balance.DEEP_FITTING_RATING, Balance.DEEP_FITTING_CRACK);
        return n;
    }

    private static int put(String path, int depth, float crack) {
        String id = Wmd.MODID + ":" + path;
        if (DeepSeasBridge.hullRating(id) == depth) return 0;
        return DeepSeasBridge.setHullRating(id, depth, crack) ? 1 : 0;
    }

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        int n = apply();
        if (n > 0) Wmd.LOGGER.info("Deep-rated hulls: {} Deep Seas hull ratings set", n);
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer s = event.getServer();
        if (s.getTickCount() % Balance.DEEP_HULL_RECHECK_TICKS == 0) apply();
    }

    static int tier(Level level, UUID id) {
        String weakest = DeepSeasBridge.weakestHullBlock(level, id);
        if (weakest == null) return -1;
        int r = DeepSeasBridge.hullRating(weakest);
        for (int t = Balance.DEEP_HULL_RATING.length - 1; t >= 0; t--) if (r >= Balance.DEEP_HULL_RATING[t]) return t;
        return -1;
    }

    static double implodeBonus(Level level, UUID id) {
        int t = tier(level, id);
        return t < 0 ? 0.0 : Balance.DEEP_HULL_IMPLODE_BONUS[t];
    }

    static void tip(List<Component> tooltip, int rating, int tier) {
        tooltip.add(Component.translatable("tooltip.wmd.deep_hull.rating", rating).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.deep_hull.implode", (int) Balance.DEEP_HULL_IMPLODE_BONUS[tier]).withStyle(ChatFormatting.GRAY));
        if (!DeepSeasBridge.loaded()) tooltip.add(Component.translatable("tooltip.wmd.deep_hull.no_ds").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static class Plate extends Block {
        final int tier;
        private final MapCodec<Plate> codec;
        public Plate(Properties p, int tier) { super(p); this.tier = tier; this.codec = simpleCodec(q -> new Plate(q, tier)); }
        @Override protected MapCodec<? extends Block> codec() { return codec; }
        @Override public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
            tip(tooltip, Balance.DEEP_HULL_RATING[tier], tier);
        }
    }

    public static class Viewport extends TransparentBlock {
        public static final MapCodec<Viewport> CODEC = simpleCodec(Viewport::new);
        public Viewport(Properties p) { super(p); }
        @Override public MapCodec<? extends TransparentBlock> codec() { return CODEC; }
        @Override public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
            tip(tooltip, Balance.DEEP_VIEWPORT_RATING, 0);
        }
    }
}
