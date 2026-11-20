package com.ashraf.wmd;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class QuantumRadarScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 172, PH = 214, SC_Y = 86, SC_R = 58, ROW_Y = 150, ROW_H = 10, BTN_Y = 194, BTN_H = 16;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED;
    private static final int C_HOSTILE = 0xFFE5534B, C_NEUTRAL = 0xFFB4BEC8, C_FRIEND = 0xFF57C46A, C_RING = 0xFF2D4256, C_SWEEP = 0xFF7FE3FF;
    private static final String P = "gui.wmd.qradar.";
    private WmdUi.KitButton power;

    public QuantumRadarScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private QuantumRadarBlockEntity radar() { return menu.machine() instanceof QuantumRadarBlockEntity r ? r : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return radar() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        QuantumRadarBlockEntity r = radar();
        if (r == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, r.getBlockPos())));
        power = addRenderableWidget(new WmdUi.KitButton(px() + 10, py() + BTN_Y, PW - 20, BTN_H, label(r), b -> {
            int next = r.switchedOn() ? 1 : 0;
            r.setModeClient(next);
            PacketDistributor.sendToServer(new SetMachineModePayload(r.getBlockPos(), next));
            b.setMessage(label(r));
        }));
    }

    private static Component label(QuantumRadarBlockEntity r) { return Component.translatable(r.switchedOn() ? P + "switch_off" : P + "switch_on"); }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        QuantumRadarBlockEntity r = radar();
        if (r == null) return;
        if (power != null) power.setMessage(label(r));
        int x = px(), y = py(), cx = x + PW / 2, cy = y + SC_Y;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        List<CompoundTag> l = r.contacts();
        boolean on = r.switchedOn(), run = r.isRunning();
        Component chip = !on ? Component.translatable(P + "off") : !run ? Component.translatable(P + "no_power")
                : !l.isEmpty() ? Component.translatable(P + "ghosts", l.size()) : Component.translatable(P + "sweeping");
        WmdUi.header(g, font, x, y, PW, new ItemStack(r.getBlockState().getBlock().asItem()), Component.translatable("block.wmd.quantum_radar"),
                chip, !on || !run ? WmdUi.K_BAD : !l.isEmpty() ? WmdUi.K_WARN : WmdUi.K_GOOD, !l.isEmpty());
        WmdUi.well(g, cx - SC_R - 2, cy - SC_R - 2, SC_R * 2 + 4, SC_R * 2 + 4);
        for (int k = 1; k <= 3; k++) circle(g, cx, cy, SC_R * k / 3, C_RING, 1);
        g.fill(cx, cy - SC_R, cx + 1, cy - SC_R + 4, TEXT);
        if (run) {
            float brg = (float) Math.toRadians(r.beamBearing(partialTick));
            for (int i = 2; i < SC_R; i++) dot(g, cx + Mth.sin(brg) * i, cy - Mth.cos(brg) * i, C_SWEEP);
        }
        double scale = SC_R / Balance.QR_RANGE;
        float shimmer = WmdUi.pulse(0.8F);
        for (CompoundTag c : l) {
            int col = WmdUi.lerp(colour(c.getInt("r")), 0xFF0F1318, 0.45F * (1.0F - shimmer));
            float sx = cx + (float) (c.getInt("x") * scale), sy = cy + (float) (c.getInt("z") * scale);
            if (Math.hypot(sx - cx, sy - cy) > SC_R) { sx = cx + (sx - cx) * SC_R / (float) Math.hypot(sx - cx, sy - cy); sy = cy + (sy - cy) * SC_R / (float) Math.hypot(sx - cx, sy - cy); }
            if (c.getBoolean("pl")) circle(g, Math.round(sx), Math.round(sy), 3, col, 1);
            else ghostDiamond(g, sx, sy, 4 + Math.min(3, c.getInt("b") / 300), col);
            int h = c.getInt("h");
            if (h >= 0) {
                float a = (float) Math.toRadians(h);
                for (int i = 5; i < 9; i += 2) dot(g, sx + Mth.sin(a) * i, sy - Mth.cos(a) * i, col);
            }
        }
        fit(g, Component.translatable(P + "range", String.format("%,d", (int) Balance.QR_RANGE), (int) Balance.QR_PLAYER_RANGE), cx, cy + SC_R + 3, GREY);
        if (l.isEmpty()) { fit(g, Component.translatable(on && run ? P + "clear" : P + "blank"), cx, y + ROW_Y + 12, GREY); return; }
        for (int i = 0; i < l.size() && i < 4; i++) {
            CompoundTag c = l.get(i);
            int ry = y + ROW_Y + 4 + i * ROW_H;
            int d = (int) Math.round(Math.hypot(c.getInt("x"), c.getInt("z")));
            String n = c.getString("n");
            int rc = c.getInt("r");
            WmdUi.led(g, x + 7, ry, rc == ShipFaction.Relation.HOSTILE.code() ? WmdUi.K_BAD : rc == ShipFaction.Relation.FRIENDLY.code() ? WmdUi.K_GOOD : WmdUi.K_WARN, true);
            Component kind = Component.translatable(c.getBoolean("pl") ? P + "player" : P + "craft");
            WmdUi.drawFit(g, font, Component.translatable(P + "row", kind, n.isEmpty() ? "?" : n, String.format("%,d", d), c.getInt("y")), x + 15, ry, PW - 22,
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

    private static void circle(GuiGraphics g, int cx, int cy, int r, int col, int step) {
        int n = Math.max(12, r * 6);
        for (int i = 0; i < n; i += step) {
            double a = i * Math.PI * 2.0 / n;
            dot(g, (float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r), col);
        }
    }

    private static void ghostDiamond(GuiGraphics g, float x, float y, int s, int col) {
        for (int i = 0; i <= s; i += 1) {
            if ((i & 1) == 1) continue;
            dot(g, x - s + i, y - i, col); dot(g, x + s - i, y - i, col);
            dot(g, x - s + i, y + i, col); dot(g, x + s - i, y + i, col);
        }
        dot(g, x, y, col);
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) {
        int maxW = PW - 10, w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        WmdUi.drawFit(g, font, text, cx - maxW / 2, y, maxW, colour);
    }
}
