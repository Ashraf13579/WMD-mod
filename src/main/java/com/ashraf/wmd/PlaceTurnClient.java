package com.ashraf.wmd;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class PlaceTurnClient {

    private PlaceTurnClient() {}

    static final IKeyConflictContext HOLDING = new IKeyConflictContext() {
        @Override public boolean isActive() { return Minecraft.getInstance().screen == null && held(Minecraft.getInstance().player) != null; }
        @Override public boolean conflicts(IKeyConflictContext other) { return other == this; }
    };

    static final KeyMapping TURN = new KeyMapping("key.wmd.place_turn", HOLDING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.wmd");

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Keys {
        @SubscribeEvent
        static void register(RegisterKeyMappingsEvent event) { event.register(TURN); }
    }

    private static Item lastItem;

    static InteractionHand held(LocalPlayer p) {
        if (p == null) return null;
        if (GhostPreview.machineOf(p.getMainHandItem()) != null) return InteractionHand.MAIN_HAND;
        if (GhostPreview.machineOf(p.getOffhandItem()) != null) return InteractionHand.OFF_HAND;
        return null;
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) { lastItem = null; PlaceTurn.clientTurn = 0; return; }
        InteractionHand hand = held(p);
        Item now = hand == null ? null : p.getItemInHand(hand).getItem();
        if (now != lastItem) {
            lastItem = now;
            if (PlaceTurn.clientTurn != 0) { PlaceTurn.clientTurn = 0; PacketDistributor.sendToServer(new PlaceTurnPayload(0)); }
        }
        while (TURN.consumeClick()) {
            if (hand == null) continue;
            PlaceTurn.clientTurn += p.isShiftKeyDown() ? -1 : 1;
            PacketDistributor.sendToServer(new PlaceTurnPayload(PlaceTurn.clientTurn));
            say(mc, p, hand);
        }
    }

    private static void say(Minecraft mc, LocalPlayer p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        MachineBlock mb = GhostPreview.machineOf(stack);
        if (mb == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !(stack.getItem() instanceof BlockItem bi)) return;
        BlockPlaceContext ctx = bi.updatePlacementContext(new BlockPlaceContext(p, hand, stack, hit));
        if (ctx == null) return;
        Direction[] plain = mb.placement(ctx);
        List<PlaceTurn.Way> ways = PlaceTurn.ways(mb, ctx, plain);
        if (ways.isEmpty()) { p.displayClientMessage(Component.translatable("message.wmd.place_turn.none"), true); return; }
        int i = Math.floorMod(PlaceTurn.clientTurn, ways.size());
        PlaceTurn.Way w = ways.get(i);
        String on = w.support() == Direction.DOWN ? "floor" : w.support() == Direction.UP ? "ceiling" : "wall";
        p.displayClientMessage(Component.translatable("message.wmd.place_turn.way", i + 1, ways.size(), Component.translatable("message.wmd.place_turn.on_" + on),
                Component.translatable("message.wmd.place_turn.dir_" + w.facing().getName())), true);
    }
}
