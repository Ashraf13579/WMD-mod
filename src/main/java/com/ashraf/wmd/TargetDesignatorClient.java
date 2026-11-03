package com.ashraf.wmd;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class TargetDesignatorClient {

    private TargetDesignatorClient() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "target_designator");
    private static final ResourceLocation SCOPE = ResourceLocation.withDefaultNamespace("textures/misc/spyglass_scope.png");

    private static boolean hit;
    private static BlockPos hitPos = BlockPos.ZERO;
    private static int hitDist;

    private static boolean zoomed(LocalPlayer p) { return p != null && p.isUsingItem() && p.getUseItem().getItem() instanceof TargetDesignatorItem; }

    private static BlockHitResult ray(LocalPlayer p, float pt) {
        HitResult r = p.pick(TargetDesignatorItem.RANGE, pt, true);
        return r instanceof BlockHitResult b && r.getType() == HitResult.Type.BLOCK ? b : null;
    }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(LAYER, TargetDesignatorClient::render); }

    @SubscribeEvent
    static void fov(ComputeFovModifierEvent event) {
        if (zoomed(Minecraft.getInstance().player)) event.setNewFovModifier(0.1F);
    }

    @SubscribeEvent
    static void hands(RenderHandEvent event) {
        if (zoomed(Minecraft.getInstance().player)) event.setCanceled(true);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!zoomed(p) || p.tickCount % 4 != 0) return;
        BlockHitResult b = ray(p, 1.0F);
        hit = b != null;
        if (hit) {
            hitPos = b.getBlockPos();
            hitDist = (int) Math.round(Math.sqrt(b.getLocation().distanceToSqr(p.getEyePosition())));
        }
    }

    @SubscribeEvent
    static void click(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!event.isUseItem() || p == null || !p.isShiftKeyDown()) return;
        InteractionHand hand = event.getHand();
        ItemStack s = p.getItemInHand(hand);
        if (!(s.getItem() instanceof TargetDesignatorItem)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (p.getCooldowns().isOnCooldown(s.getItem())) return;
        BlockHitResult b = ray(p, 1.0F);
        if (b == null) { p.displayClientMessage(Component.translatable("message.wmd.cc.des.no_hit", TargetDesignatorItem.RANGE), true); return; }
        BlockPos at = b.getBlockPos();
        PacketDistributor.sendToServer(new CcDesignatePayload(at.getX(), at.getY(), at.getZ()));
        p.swing(hand);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!zoomed(p) || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float f = Math.min(w, h);
        int size = Mth.floor(f * Math.min(w / f, h / f));
        int x0 = (w - size) / 2, y0 = (h - size) / 2, x1 = x0 + size, y1 = y0 + size;
        RenderSystem.enableBlend();
        g.blit(SCOPE, x0, y0, 0.0F, 0.0F, size, size, size, size);
        RenderSystem.disableBlend();
        g.fill(0, y1, w, h, 0xFF000000);
        g.fill(0, 0, w, y0, 0xFF000000);
        g.fill(0, y0, x0, y1, 0xFF000000);
        g.fill(x1, y0, w, y1, 0xFF000000);
        int cx = w / 2, cy = h / 2;
        g.fill(cx - 6, cy, cx - 1, cy + 1, 0xFFFF3030);
        g.fill(cx + 2, cy, cx + 7, cy + 1, 0xFFFF3030);
        g.fill(cx, cy - 6, cx + 1, cy - 1, 0xFFFF3030);
        g.fill(cx, cy + 2, cx + 1, cy + 7, 0xFFFF3030);
        Font font = mc.font;
        Component line = hit ? Component.translatable("hud.wmd.designator.hit", hitPos.getX(), hitPos.getY(), hitPos.getZ(), String.format("%,d", hitDist))
                : Component.translatable("hud.wmd.designator.no_hit", TargetDesignatorItem.RANGE);
        Component hint = Component.translatable("hud.wmd.designator.hint");
        g.drawString(font, line, cx - font.width(line) / 2, cy + 14, hit ? 0xFFFFE0A0 : 0xFFB0B0B0, true);
        g.drawString(font, hint, cx - font.width(hint) / 2, Math.min(h - 12, y1 - 24), 0xFFE0E0E0, true);
    }
}
