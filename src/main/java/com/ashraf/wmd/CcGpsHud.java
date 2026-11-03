package com.ashraf.wmd;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class CcGpsHud {

    private CcGpsHud() {}

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_gps_hud");
    private static final String[] POINTS = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ID, CcGpsHud::render);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { GpsView.clear(); CcAlertView.clear(); BdaView.clear(); CcBattleLogView.clear(); }

    static String point(double deg) {
        int i = (int) Math.floor(((deg % 360.0 + 360.0) % 360.0 + 22.5) / 45.0) % 8;
        return POINTS[i];
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null && !mc.options.hideGui) banner(g, mc);
        GpsPayload gps = GpsView.live();
        if (gps == null || p == null || mc.level == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        Font font = mc.font;
        int bx = (int) Math.floor(p.getX()), by = (int) Math.floor(p.getY()), bz = (int) Math.floor(p.getZ());
        double heading = ((p.getYRot() + 180.0) % 360.0 + 360.0) % 360.0;
        Component l1 = Component.translatable("hud.wmd.gps.pos", bx, by, bz);
        Component l2 = Component.translatable("hud.wmd.gps.heading", String.format("%03d", (int) Math.round(heading) % 360), point(heading));
        Component l3;
        if (mc.level.dimension().location().toString().equals(gps.dim())) {
            double dx = gps.x() + 0.5 - p.getX(), dz = gps.z() + 0.5 - p.getZ();
            int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            double bearing = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0;
            l3 = Component.translatable("hud.wmd.gps.base", String.format("%,d", dist), String.format("%03d", (int) Math.round(bearing) % 360), point(bearing));
        } else {
            l3 = Component.translatable("hud.wmd.gps.base_other", gps.dim());
        }
        int w = Math.max(font.width(l1), Math.max(font.width(l2), font.width(l3))) + 8;
        int x = 4, y = 4;
        g.fill(x, y, x + w, y + 34, 0x90101820);
        g.fill(x, y, x + 2, y + 34, 0xFF3FA9F5);
        g.drawString(font, l1, x + 5, y + 3, 0xFF9FE3FF, true);
        g.drawString(font, l2, x + 5, y + 13, 0xFFE0E0E0, true);
        g.drawString(font, l3, x + 5, y + 23, 0xFFE0E0E0, true);
        minimap(g, mc, p, gps, font);
    }

    private static void banner(GuiGraphics g, Minecraft mc) {
        CcAlertPayload a = CcAlertView.live();
        if (a == null) return;
        if (CcAlertView.fresh) {
            CcAlertView.fresh = false;
            float pitch = a.kind() == CcDefcon.B_INCOMING || a.kind() == CcDefcon.B_LOCKDOWN ? 0.6F : 1.4F;
            if (!LaunchSounds.banner(mc, a.kind(), a.args())) mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    a.kind() == CcDefcon.B_LOCKDOWN ? net.minecraft.sounds.SoundEvents.BELL_BLOCK : net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), pitch));
        }
        Font font = mc.font;
        Component text = CcBattleLog.text(a.key(), a.args());
        int sw = g.guiWidth(), maxW = Math.min(sw - 20, 320);
        int w = Math.min(maxW, font.width(text) + 16), x = (sw - w) / 2, y = 34;
        boolean blink = (CcAlertView.age() / 400L) % 2L == 0L;
        int col = switch (a.kind()) {
            case CcDefcon.B_INCOMING, CcDefcon.B_LOCKDOWN -> blink ? 0xFFFF3030 : 0xFFA01818;
            case CcDefcon.B_DEFCON -> a.args().isEmpty() ? 0xFF40D060 : CcDefcon.colour(parse(a.args().get(0)));
            case CcDefcon.B_COUNTER -> 0xFFF0A020;
            default -> 0xFF3FA9F5;
        };
        g.fill(x - 1, y - 1, x + w + 1, y + 15, col);
        g.fill(x, y, x + w, y + 14, 0xE0101018);
        g.fill(x, y, x + 3, y + 14, col);
        int tw = font.width(text), room = w - 12;
        if (tw <= room) g.drawString(font, text, x + 8 + (room - tw) / 2, y + 3, 0xFFFFFFFF, true);
        else {
            float s = room / (float) tw;
            g.pose().pushPose();
            g.pose().translate(x + 8, y + 3 + (1.0F - s) * 4.0F, 0.0F);
            g.pose().scale(s, s, 1.0F);
            g.drawString(font, text, 0, 0, 0xFFFFFFFF, true);
            g.pose().popPose();
        }
    }

    private static int parse(String s) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 5; } }

    static final int MAP = 80, BLOCKS_PER_PX = 16;

    private static void minimap(GuiGraphics g, Minecraft mc, LocalPlayer p, GpsPayload gps, Font font) {
        int sw = g.guiWidth();
        int x0 = sw - MAP - 4, y0 = p.getActiveEffects().isEmpty() ? 4 : 56;
        int cx = x0 + MAP / 2, cy = y0 + MAP / 2;
        g.fill(x0 - 1, y0 - 1, x0 + MAP + 1, y0 + MAP + 1, 0xFF3FA9F5);
        g.fill(x0, y0, x0 + MAP, y0 + MAP, 0xA0101820);
        for (int k = 1; k < 4; k++) {
            g.fill(x0 + k * 20, y0, x0 + k * 20 + 1, y0 + MAP, 0x30406080);
            g.fill(x0, y0 + k * 20, x0 + MAP, y0 + k * 20 + 1, 0x30406080);
        }
        g.drawString(font, "N", cx - 2, y0 + 2, 0xFFFF6060, true);
        g.drawString(font, "S", cx - 2, y0 + MAP - 10, 0xFFE0E0E0, true);
        g.drawString(font, "W", x0 + 2, cy - 4, 0xFFE0E0E0, true);
        g.drawString(font, "E", x0 + MAP - 7, cy - 4, 0xFFE0E0E0, true);
        boolean here = mc.level.dimension().location().toString().equals(gps.dim());
        if (here) {
            double px = p.getX(), pz = p.getZ();
            long now = mc.level.getGameTime();
            int[] w = gps.warns();
            long[] t = gps.times();
            for (int i = 0; i + 3 < w.length && i / 4 * 2 + 1 < t.length; i += 4) {
                long l0 = t[i / 4 * 2], l1 = t[i / 4 * 2 + 1];
                if (now > l1 + 200) continue;
                ring(g, x0, y0, cx, cy, w[i] - px, w[i + 1] - pz, Balance.RECON_IMPACT_BLUR, 0xFFFF3030);
                double k = l1 > l0 ? Math.max(0.0, Math.min(1.0, (now - l0) / (double) (l1 - l0))) : 1.0;
                if (k < 1.0) mark(g, x0, y0, cx, cy, w[i + 2] + (w[i] - w[i + 2]) * k - px, w[i + 3] + (w[i + 1] - w[i + 3]) * k - pz, 1,
                        0xFF000000, (now / 5) % 2 == 0 ? 0xFFFFFFFF : 0xFFFF4040);
            }

            for (Flight f : ClientFlights.FLIGHTS.values()) {
                if ((f.build() != null && f.build().isInterceptor()) || now < f.start() || now > f.endTime()) continue;
                if (!f.isOwnedBy(p.getUUID(), p.getGameProfile().getName())) continue;
                net.minecraft.world.phys.Vec3 v = f.position(now);
                int[] q = toMap(cx, cy, v.x - px, v.z - pz);
                if (inside(q, x0, y0)) g.fill(q[0], q[1], q[0] + 2, q[1] + 2, 0xFF50A0FF);
            }
            mark(g, x0, y0, cx, cy, gps.x() + 0.5 - px, gps.z() + 0.5 - pz, 2, 0xFF000000, 0xFF50A0FF);
            String self = p.getGameProfile().getName();
            int[] m = gps.mates();
            for (int i = 0; i < gps.mateNames().size() && i * 2 + 1 < m.length; i++)
                if (!gps.mateNames().get(i).equals(self)) mark(g, x0, y0, cx, cy, m[i * 2] + 0.5 - px, m[i * 2 + 1] + 0.5 - pz, 1, 0xFF000000, 0xFF40E060);
            if (gps.hasDes()) {
                double dx = gps.dx() + 0.5 - px, dz = gps.dz() + 0.5 - pz;
                int[] q = toMap(cx, cy, dx, dz);
                if (inside(q, x0, y0)) {
                    g.fill(q[0] - 3, q[1], q[0] + 4, q[1] + 1, 0xFFFF2020);
                    g.fill(q[0], q[1] - 3, q[0] + 1, q[1] + 4, 0xFFFF2020);
                } else edgeArrow(g, cx, cy, dx, dz, 0xFFFF2020);
            }
        } else {
            String s = Component.translatable("hud.wmd.gps.map_other").getString();
            g.drawString(font, s, cx - font.width(s) / 2, cy + 8, 0xFFB0B0B0, true);
        }

        double yaw = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        for (int k = 0; k <= 4; k++) {
            int ax = cx + (int) Math.round(fx * (k - 1)), az = cy + (int) Math.round(fz * (k - 1));
            int r = k < 2 ? 1 : 0;
            g.fill(ax - r, az - r, ax + r + 1, az + r + 1, 0xFFFFFFFF);
        }
    }

    private static int[] toMap(int cx, int cy, double dx, double dz) {
        return new int[] { cx + (int) Math.floor(dx / BLOCKS_PER_PX), cy + (int) Math.floor(dz / BLOCKS_PER_PX) };
    }

    private static boolean inside(int[] q, int x0, int y0) { return q[0] >= x0 + 1 && q[0] < x0 + MAP - 1 && q[1] >= y0 + 1 && q[1] < y0 + MAP - 1; }

    private static void mark(GuiGraphics g, int x0, int y0, int cx, int cy, double dx, double dz, int r, int outline, int col) {
        int[] q = toMap(cx, cy, dx, dz);
        if (!inside(q, x0, y0)) { edgeArrow(g, cx, cy, dx, dz, col); return; }
        g.fill(q[0] - r - 1, q[1] - r - 1, q[0] + r + 2, q[1] + r + 2, outline);
        g.fill(q[0] - r, q[1] - r, q[0] + r + 1, q[1] + r + 1, col);
    }

    private static void edgeArrow(GuiGraphics g, int cx, int cy, double dx, double dz, int col) {
        double len = Math.max(1.0E-6, Math.max(Math.abs(dx), Math.abs(dz)));
        double h = MAP / 2.0 - 3.0;
        int ex = cx + (int) Math.round(dx / len * h), ez = cy + (int) Math.round(dz / len * h);
        double n = Math.max(1.0E-6, Math.hypot(dx, dz)), vx = dx / n, vz = dz / n;
        for (int k = 0; k < 3; k++) {
            int bx = ex - (int) Math.round(vx * k), bz = ez - (int) Math.round(vz * k);
            int ox = (int) Math.round(-vz * k), oz = (int) Math.round(vx * k);
            g.fill(bx + ox, bz + oz, bx + ox + 1, bz + oz + 1, col);
            g.fill(bx - ox, bz - oz, bx - ox + 1, bz - oz + 1, col);
        }
    }

    private static void ring(GuiGraphics g, int x0, int y0, int cx, int cy, double dx, double dz, double radius, int col) {
        double rp = Math.max(2.0, radius / BLOCKS_PER_PX);
        int[] c = toMap(cx, cy, dx, dz);
        for (int i = 0; i < 20; i++) {
            double a = Math.PI * 2.0 * i / 20;
            int[] q = { c[0] + (int) Math.round(Math.cos(a) * rp), c[1] + (int) Math.round(Math.sin(a) * rp) };
            if (inside(q, x0, y0)) g.fill(q[0], q[1], q[0] + 1, q[1] + 1, col);
        }
        if (!inside(c, x0, y0)) edgeArrow(g, cx, cy, dx, dz, col);
    }
}
