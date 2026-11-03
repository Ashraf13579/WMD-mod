package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientBombSight {

    private ClientBombSight() {}

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bomb_sight_hud");

    public static final net.minecraft.client.KeyMapping KEY_DROP = new net.minecraft.client.KeyMapping("key.wmd.bomb_drop",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_B, "key.categories.wmd");
    public static final net.minecraft.client.KeyMapping KEY_NEXT = new net.minecraft.client.KeyMapping("key.wmd.bomb_next",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_N, "key.categories.wmd");

    @EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
    static final class Keys {
        @SubscribeEvent
        static void register(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) { event.register(KEY_DROP); event.register(KEY_NEXT); }
    }

    private static BombSightBlockEntity nearest(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        BombSightBlockEntity best = null;
        double bd = Balance.SIGHT_HUD * Balance.SIGHT_HUD;
        for (BombSightBlockEntity s : BombSightBlockEntity.CLIENT.values()) {
            if (s.isRemoved() || s.getLevel() != mc.level || s.mode() == BombSightBlockEntity.OFF) continue;
            Vec3 c = ShipFrame.drawnWorld(mc.level, s.getBlockPos(), net.minecraft.world.phys.Vec3.atCenterOf(s.getBlockPos()), Float.NaN);
            double d = c.distanceToSqr(mc.player.position());
            if (d <= bd) { bd = d; best = s; }
        }
        return best;
    }

    @SubscribeEvent
    static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean drop = false, salvo = false, next = false;
        while (KEY_DROP.consumeClick()) { if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) salvo = true; else drop = true; }
        while (KEY_NEXT.consumeClick()) next = true;
        if (!(drop || salvo || next) || mc.screen != null) return;
        BombSightBlockEntity s = nearest(mc);
        if (s == null) return;
        if (next) net.neoforged.neoforge.network.PacketDistributor.sendToServer(new BombSightKeyPayload(s.getBlockPos(), BombSightBlockEntity.K_NEXT));
        if (salvo) net.neoforged.neoforge.network.PacketDistributor.sendToServer(new BombSightKeyPayload(s.getBlockPos(), BombSightBlockEntity.K_SALVO));
        else if (drop) net.neoforged.neoforge.network.PacketDistributor.sendToServer(new BombSightKeyPayload(s.getBlockPos(), BombSightBlockEntity.K_DROP));
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { BombSightBlockEntity.CLIENT.clear(); }

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) { event.registerAboveAll(ID, ClientBombSight::hud); }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BombSightBlockEntity.CLIENT.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        double tt = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        BufferBuilder bb = null;
        for (BombSightBlockEntity s : BombSightBlockEntity.CLIENT.values()) {
            if (s.isRemoved() || s.getLevel() != mc.level || s.mode() == BombSightBlockEntity.OFF || !s.solved()) continue;
            Vec3 i = s.impact();
            if (i.distanceToSqr(cam) > Balance.SIGHT_DRAW * Balance.SIGHT_DRAW && s.dropPoint().distanceToSqr(cam) > Balance.SIGHT_DRAW * Balance.SIGHT_DRAW) continue;
            if (bb == null) bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            double rel = s.releaseSeconds();
            boolean window = s.mode() == BombSightBlockEntity.CCRP && s.aimed() && !Double.isNaN(rel) && rel >= 0.0 && rel < 3.0 && s.cross() <= Balance.SIGHT_CROSS;
            float r = window ? 0.3F : 0.35F, g = window ? 1.0F : 0.8F, b = window ? 0.4F : 1.0F;
            float x = (float) (i.x - cam.x), y = (float) (i.y + 0.08 - cam.y), z = (float) (i.z - cam.z);
            ring(bb, x, y, z, 3.0F, 0.15F, 32, r, g, b, 0.75F);
            ring(bb, x, y, z, 0.6F, 0.12F, 12, r, g, b, 0.9F);
            for (int k = 0; k < 4; k++) {
                double a = Math.PI / 2 * k + tt * 0.01;
                float c = (float) Math.cos(a), sn = (float) Math.sin(a);
                quad(bb, x + c * 3.4F, y, z + sn * 3.4F, x + c * 5.0F, y, z + sn * 5.0F, 0.12F, r, g, b, 0.8F);
            }
            Vec3 d = s.dropPoint();
            dashed(bb, (float) (d.x - cam.x), (float) (d.y - cam.y), (float) (d.z - cam.z), x, y, z, r, g, b, 0.45F);
            if (s.mode() == BombSightBlockEntity.CCRP && s.aimed()) {
                Vec3 t = s.target();
                float tx = (float) (t.x - cam.x), tz = (float) (t.z - cam.z);
                ring(bb, tx, y, tz, 4.0F, 0.2F, 32, 1.0F, 0.55F, 0.15F, 0.8F);
                dashed(bb, x, y, z, tx, y, tz, 1.0F, 0.55F, 0.15F, 0.5F);
            }
        }
        if (bb != null) ClientBlastGeo.draw(event, bb, true, true);
    }

    private static void ring(BufferBuilder bb, float x, float y, float z, float rad, float th, int n, float r, float g, float b, float a) {
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2.0 * i / n, a1 = Math.PI * 2.0 * (i + 1) / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            bb.addVertex(x + c0 * (rad - th), y, z + s0 * (rad - th)).setColor(r, g, b, a);
            bb.addVertex(x + c1 * (rad - th), y, z + s1 * (rad - th)).setColor(r, g, b, a);
            bb.addVertex(x + c1 * (rad + th), y, z + s1 * (rad + th)).setColor(r, g, b, a);
            bb.addVertex(x + c0 * (rad + th), y, z + s0 * (rad + th)).setColor(r, g, b, a);
        }
    }

    private static void quad(BufferBuilder bb, float x0, float y, float z0, float x1, float y1, float z1, float w, float r, float g, float b, float a) {
        float dx = x1 - x0, dz = z1 - z0, l = (float) Math.sqrt(dx * dx + dz * dz);
        if (l < 1.0E-4F) return;
        float nx = -dz / l * w, nz = dx / l * w;
        bb.addVertex(x0 + nx, y, z0 + nz).setColor(r, g, b, a);
        bb.addVertex(x1 + nx, y1, z1 + nz).setColor(r, g, b, a);
        bb.addVertex(x1 - nx, y1, z1 - nz).setColor(r, g, b, a);
        bb.addVertex(x0 - nx, y, z0 - nz).setColor(r, g, b, a);
    }

    private static void dashed(BufferBuilder bb, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, float a) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = Math.max(2, (int) (l / 2.5F));
        for (int i = 0; i < n; i += 2) {
            float t0 = i / (float) n, t1 = Math.min(1.0F, (i + 1) / (float) n);
            float ax = x0 + dx * t0, ay = y0 + dy * t0, az = z0 + dz * t0, cx = x0 + dx * t1, cy = y0 + dy * t1, cz = z0 + dz * t1;
            bb.addVertex(ax - 0.06F, ay, az).setColor(r, g, b, a);
            bb.addVertex(cx - 0.06F, cy, cz).setColor(r, g, b, a);
            bb.addVertex(cx + 0.06F, cy, cz).setColor(r, g, b, a);
            bb.addVertex(ax + 0.06F, ay, az).setColor(r, g, b, a);
            bb.addVertex(ax, ay, az - 0.06F).setColor(r, g, b, a);
            bb.addVertex(cx, cy, cz - 0.06F).setColor(r, g, b, a);
            bb.addVertex(cx, cy, cz + 0.06F).setColor(r, g, b, a);
            bb.addVertex(ax, ay, az + 0.06F).setColor(r, g, b, a);
        }
    }

    private static void hud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || BombSightBlockEntity.CLIENT.isEmpty()) return;
        BombSightBlockEntity best = nearest(mc);
        if (best == null) return;
        Component line;
        String mode = best.mode() == BombSightBlockEntity.CCIP ? "CCIP" : "CCRP";
        String head = String.format("%s  ALT %d  SPD %d  TRK %03d", mode, (int) Math.round(best.height()), (int) Math.round(best.speed()), (int) Math.round(best.track()) % 360);
        if (best.pulsing()) line = Component.translatable("hud.wmd.sight.release", head);
        else if (!best.solved()) line = Component.translatable("hud.wmd.sight.nosol", head);
        else if (best.mode() == BombSightBlockEntity.CCRP) {
            double rel = best.releaseSeconds();
            line = !best.aimed() ? Component.translatable("hud.wmd.sight.notarget", head)
                    : Double.isNaN(rel) || rel < 0 ? Component.translatable("hud.wmd.sight.passed", head)
                    : Component.translatable("hud.wmd.sight.ccrp", head, String.format("%.1f", rel), String.format("%.0f", best.cross()));
        } else line = Component.literal(head);
        int w = mc.font.width(line), x = (g.guiWidth() - w) / 2, y = 26;
        g.fill(x - 6, y - 4, x + w + 6, y + 12, 0xB0101820);
        g.fill(x - 6, y + 12, x + w + 6, y + 13, best.pulsing() ? 0xFFFF9030 : 0xFF4DA3FF);
        g.drawString(mc.font, line, x, y, best.pulsing() ? 0xFFFFB060 : 0xFFE6EDF4, false);
        stores(g, mc, best, y + 20);
    }

    private static void stores(GuiGraphics g, Minecraft mc, BombSightBlockEntity s, int y) {
        int n = s.stationCount();
        java.util.List<Component> rows = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            Component where = s.stationBay(i) ? Component.translatable("hud.wmd.sight.st_bay", s.stationRack(i), s.stationSlot(i) + 1)
                    : Component.translatable("hud.wmd.sight.st_rack", s.stationRack(i));
            rows.add(Component.translatable("hud.wmd.sight.st_row", i + 1, where, s.stationName(i)));
        }
        if (rows.isEmpty()) rows.add(Component.translatable("hud.wmd.sight.st_none", Balance.SIGHT_LINK));
        Component keys = Component.translatable("hud.wmd.sight.st_keys", KEY_NEXT.getTranslatedKeyMessage(), KEY_DROP.getTranslatedKeyMessage(), KEY_DROP.getTranslatedKeyMessage());
        int w = mc.font.width(keys);
        for (Component c : rows) w = Math.max(w, mc.font.width(c) + 10);
        int x = (g.guiWidth() - w) / 2, h = rows.size() * 10 + 14;
        g.fill(x - 6, y - 4, x + w + 6, y + h, 0xB0101820);
        for (int i = 0; i < rows.size(); i++) {
            boolean sel = n > 0 && i == s.station();
            if (sel) g.fill(x - 4, y + i * 10 - 1, x + w + 4, y + i * 10 + 9, 0x604DA3FF);
            g.drawString(mc.font, sel ? ">" : " ", x, y + i * 10, 0xFF4DA3FF, false);
            g.drawString(mc.font, rows.get(i), x + 10, y + i * 10, sel ? 0xFFFFFFFF : 0xFFB8C4D0, false);
        }
        g.drawString(mc.font, keys, x, y + rows.size() * 10 + 3, 0xFF8A97A5, false);
    }
}
