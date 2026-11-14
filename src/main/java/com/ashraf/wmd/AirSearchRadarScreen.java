package com.ashraf.wmd;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class AirSearchRadarScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 172, PH = 214, SC_Y = 86, SC_R = 58, ROW_Y = 150, ROW_H = 10, BTN_Y = 194, BTN_H = 16;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, BAD = WmdUi.BAD;
    private static final int C_HOSTILE = 0xFFE5534B, C_NEUTRAL = 0xFFB4BEC8, C_FRIEND = 0xFF57C46A, C_RING = 0xFF2D4256, C_SWEEP = 0xFF4AA3FF;
    private static final String P = "gui.wmd.asr.";
    private WmdUi.KitButton power;

    public AirSearchRadarScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private AirSearchRadarBlockEntity radar() { return menu.machine() instanceof AirSearchRadarBlockEntity r ? r : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return radar() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        AirSearchRadarBlockEntity r = radar();
        if (r == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, r.getBlockPos())));
        power = addRenderableWidget(new WmdUi.KitButton(px() + 10, py() + BTN_Y, PW - 20, BTN_H, label(r), b -> {
            int next = r.switchedOn() ? 1 : 0;
            r.setModeClient(next);
            PacketDistributor.sendToServer(new SetMachineModePayload(r.getBlockPos(), next));
            b.setMessage(label(r));
        }));
    }

    private static Component label(AirSearchRadarBlockEntity r) { return Component.translatable(r.switchedOn() ? P + "switch_off" : P + "switch_on"); }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        AirSearchRadarBlockEntity r = radar();
        if (r == null) return;
        if (power != null) power.setMessage(label(r));
        int x = px(), y = py(), cx = x + PW / 2, cy = y + SC_Y;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        List<CompoundTag> l = r.contacts();
        int hostile = 0;
        java.util.List<CompoundTag> rows = new java.util.ArrayList<>();
        for (CompoundTag c : l) if (!c.getBoolean("m")) rows.add(c);
        for (CompoundTag c : rows) if (c.getInt("r") == ShipFaction.Relation.HOSTILE.code()) hostile++;
        boolean on = r.switchedOn(), run = r.isRunning();
        Component chip = !on ? Component.translatable(P + "off") : !run ? Component.translatable(P + "no_power")
                : hostile > 0 ? Component.translatable(P + "hostile", hostile) : Component.translatable(P + "sweeping");
        WmdUi.header(g, font, x, y, PW, new ItemStack(r.getBlockState().getBlock().asItem()), Component.translatable("block.wmd.air_search_radar"),
                chip, !on || !run ? WmdUi.K_BAD : hostile > 0 ? WmdUi.K_WARN : WmdUi.K_GOOD, hostile > 0);

        WmdUi.well(g, cx - SC_R - 2, cy - SC_R - 2, SC_R * 2 + 4, SC_R * 2 + 4);
        for (int k = 1; k <= 3; k++) circle(g, cx, cy, SC_R * k / 3, C_RING);
        g.fill(cx, cy - SC_R, cx + 1, cy - SC_R + 4, TEXT);
        if (run) {
            float brg = (float) Math.toRadians(r.beamBearing(partialTick));
            for (int i = 2; i < SC_R; i++) dot(g, cx + Mth.sin(brg) * i, cy - Mth.cos(brg) * i, C_SWEEP);
        }
        double scale = SC_R / Balance.ASR_RANGE;
        for (CompoundTag c : l) {
            int col = colour(c.getInt("r"));
            float sx = cx + (float) (c.getInt("x") * scale), sy = cy + (float) (c.getInt("z") * scale);
            if (Math.hypot(sx - cx, sy - cy) > SC_R) continue;
            if (c.getBoolean("p")) {
                int ix = Math.round(sx), iy = Math.round(sy);
                g.fill(ix - 1, iy - 1, ix + 2, iy + 2, WmdUi.BLUE);
                continue;
            }
            if (c.getBoolean("m")) {
                int ix = Math.round(sx), iy = Math.round(sy);
                g.fill(ix - 1, iy, ix + 2, iy + 1, WmdUi.ORANGE_LIGHT); g.fill(ix, iy - 1, ix + 1, iy + 2, WmdUi.ORANGE_LIGHT);
                continue;
            }
            g.fill(Math.round(sx) - 1, Math.round(sy) - 1, Math.round(sx) + 2, Math.round(sy) + 2, col);
            int h = c.getInt("h");
            if (h >= 0) {
                float a = (float) Math.toRadians(h);
                for (int i = 2; i < 6; i++) dot(g, sx + Mth.sin(a) * i, sy - Mth.cos(a) * i, col);
            }
        }
        fit(g, Component.translatable(P + "range", String.format("%,d", (int) Balance.ASR_RANGE)), cx, cy + SC_R + 3 - 0, GREY);

        if (rows.isEmpty()) { fit(g, Component.translatable(on && run ? P + "clear" : P + "blank"), cx, y + ROW_Y + 12, GREY); return; }
        for (int i = 0; i < rows.size() && i < 4; i++) {
            CompoundTag c = rows.get(i);
            int ry = y + ROW_Y + 4 + i * ROW_H;
            int d = (int) Math.round(Math.hypot(c.getInt("x"), c.getInt("z")));
            String n = c.getString("n");
            Component rel = Component.translatable(P + "rel" + c.getInt("r"));
            WmdUi.led(g, x + 7, ry, c.getInt("r") == ShipFaction.Relation.HOSTILE.code() ? WmdUi.K_BAD : c.getInt("r") == ShipFaction.Relation.FRIENDLY.code() ? WmdUi.K_GOOD : WmdUi.K_WARN, false);
            WmdUi.drawFit(g, font, Component.translatable(P + "row", rel, n.isEmpty() ? "?" : n, String.format("%,d", d), c.getInt("y"), c.getInt("s")), x + 15, ry, PW - 22,
                    i == 0 ? TEXT : GREY);
        }
    }

    private static int colour(int code) {
        return code == ShipFaction.Relation.HOSTILE.code() ? C_HOSTILE : code == ShipFaction.Relation.FRIENDLY.code() ? C_FRIEND : C_NEUTRAL;
    }

    private static void dot(GuiGraphics g, float x, float y, int col) {
        int ix = Math.round(x), iy = Math.round(y);
        g.fill(ix, iy, ix + 1, iy + 1, col);
    }

    private static void circle(GuiGraphics g, int cx, int cy, int r, int col) {
        int n = Math.max(24, r * 6);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2.0 / n;
            dot(g, (float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r), col);
        }
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) {
        int maxW = PW - 10, w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        WmdUi.drawFit(g, font, text, cx - maxW / 2, y, maxW, colour);
    }
}
