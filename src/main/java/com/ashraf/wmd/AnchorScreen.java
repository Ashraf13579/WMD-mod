package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

final class AnchorScreen extends Screen {

    static final int PW = 200, PH = 164, VIEW_X = 8, VIEW_Y = 26, VIEW_W = 84, VIEW_H = 92, COL_X = 100, COL_W = 92, ROW_Y = 28, ROW_H = 18,
            BTN_Y = 124, BTN_W = 88, BTN_H = 16, FOOT_Y = 146;
    private static final String P = "gui.wmd.anchor.";

    private final BlockPos pos;
    private WmdUi.KitButton drop, weigh;

    AnchorScreen(BlockPos pos) {
        super(Component.translatable("block.wmd.anchor_windlass"));
        this.pos = pos;
    }

    BlockPos pos() { return pos; }

    private int px() { return (width - PW) / 2; }
    private int py() { return (height - PH) / 2; }

    private AnchorWindlassBlockEntity be() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof AnchorWindlassBlockEntity b ? b : null;
    }

    private static Component t(String k, Object... a) { return Component.translatable(P + k, a); }

    @Override
    protected void init() {
        super.init();
        int x = px(), y = py();
        drop = addRenderableWidget(new WmdUi.KitButton(x + 8, y + BTN_Y, BTN_W, BTN_H, t("b_drop"), b -> press(AnchorPayloads.A_DROP), true));
        weigh = addRenderableWidget(new WmdUi.KitButton(x + PW - 8 - BTN_W, y + BTN_Y, BTN_W, BTN_H, t("b_weigh"), b -> press(AnchorPayloads.A_WEIGH)));
        buttons();
    }

    private void press(int action) { PacketDistributor.sendToServer(new AnchorPayloads.Press(pos, action)); }

    private void buttons() {
        AnchorWindlassBlockEntity b = be();
        int s = b == null ? -1 : b.state();
        if (drop != null) drop.active = s == AnchorWindlassBlockEntity.S_STOWED || s == AnchorWindlassBlockEntity.S_WEIGHING;
        if (weigh != null) weigh.active = s != -1 && s != AnchorWindlassBlockEntity.S_STOWED && s != AnchorWindlassBlockEntity.S_WEIGHING;
    }

    @Override
    public void tick() {
        AnchorWindlassBlockEntity b = be();
        if (b == null || minecraft.player == null || !Reach.within(minecraft.player, pos, Balance.ANCHOR_REACH + 2.0)) { onClose(); return; }
        buttons();
    }

    @Override public boolean isPauseScreen() { return false; }

    static int chipKind(int s) {
        return switch (s) {
            case AnchorWindlassBlockEntity.S_HOLDING -> WmdUi.K_GOOD;
            case AnchorWindlassBlockEntity.S_DRAGGING, AnchorWindlassBlockEntity.S_NO_BOTTOM -> WmdUi.K_WARN;
            case AnchorWindlassBlockEntity.S_DROPPING, AnchorWindlassBlockEntity.S_WEIGHING -> WmdUi.K_INFO;
            default -> WmdUi.K_IDLE;
        };
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        AnchorWindlassBlockEntity b = be();
        if (b == null) return;
        int x = px(), y = py(), s = b.state(), pr = b.problems();
        WmdUi.panel(g, x, y, PW, PH);
        WmdUi.header(g, font, x, y, PW, new ItemStack(AnchorBlocks.ANCHOR_WINDLASS_ITEM.get()), title, t("st_" + AnchorWindlassBlockEntity.stateKey(s)), chipKind(s),
                s == AnchorWindlassBlockEntity.S_DRAGGING);
        view(g, b, x + VIEW_X, y + VIEW_Y);
        rows(g, b, x + COL_X, y + ROW_Y);
        WmdUi.divider(g, x + 8, y + FOOT_Y - 4, PW - 16);
        String p = (pr & AnchorWindlassBlockEntity.P_BLOCKED) != 0 ? "p_blocked" : (pr & AnchorWindlassBlockEntity.P_NO_WATER) != 0 ? "p_no_water"
                : (pr & AnchorWindlassBlockEntity.P_NO_POWER) != 0 ? "p_no_power" : null;
        Component foot = p != null ? t(p) : t("hint_" + AnchorWindlassBlockEntity.stateKey(s));
        WmdUi.drawFit(g, font, foot, x + 8, y + FOOT_Y, PW - 16, p != null ? WmdUi.WARN : WmdUi.TEXT_MUTED);
    }

    private void view(GuiGraphics g, AnchorWindlassBlockEntity b, int x, int y) {
        WmdUi.well(g, x, y, VIEW_W, VIEW_H);
        g.fill(x, y, x + VIEW_W, y + VIEW_H, WmdUi.WELL);
        float sea = b.seaDist(), chain = b.chainOut(), depth = Math.max(0.0F, b.waterDepth());
        float span = Math.max(8.0F, Math.max(sea > 0 ? sea : 0.0F, chain) + 2.0F);
        int top = y + 12, h = VIEW_H - 16;
        float k = h / span;
        int hx = x + VIEW_W / 2;

        g.fill(x + 6, y + 4, hx + 10, top, 0xFF3A4450);
        g.fill(hx - 2, y + 4, hx + 2, top, 0xFF11161C);

        int wl = sea > 0 && depth > 0 ? top + Math.round((sea - depth) * k) : top + 2;
        int bed = sea > 0 ? top + Math.round(sea * k) : y + VIEW_H - 2;
        g.fill(x + 1, Math.max(top, wl), x + VIEW_W - 1, Math.min(bed, y + VIEW_H - 1), 0xFF12324D);
        g.fill(x + 1, Math.max(top, wl), x + VIEW_W - 1, Math.max(top, wl) + 1, 0xFF5E8FB8);
        if (sea > 0) g.fill(x + 1, bed, x + VIEW_W - 1, y + VIEW_H - 1, 0xFF3A3426);

        int ay = top + Math.round(Mth.clamp(b.onBottom() ? sea : chain, 0.0F, span) * k);
        int col = b.state() == AnchorWindlassBlockEntity.S_DRAGGING ? WmdUi.WARN : 0xFFB6C0CA;
        if (ay > top) g.fill(hx, top, hx + 1, ay, col);
        g.fill(hx - 3, ay - 1, hx + 4, ay + 1, col);
        g.fill(hx, ay - 4, hx + 1, ay, col);
        if (b.state() == AnchorWindlassBlockEntity.S_DRAGGING) g.fill(hx - 12, ay, hx - 4, ay + 1, WmdUi.WARN);
    }

    private void rows(GuiGraphics g, AnchorWindlassBlockEntity b, int x, int y) {
        float depth = b.waterDepth();
        row(g, x, y, t("r_water"), depth > 0 ? t("v_m", Math.round(depth)) : t("v_none"), WmdUi.TEXT);
        row(g, x, y + ROW_H, t("r_chain"), t("v_chain", Math.round(b.chainOut()), Balance.ANCHOR_CHAIN_MAX), WmdUi.TEXT);
        row(g, x, y + 2 * ROW_H, t("r_pipes"), Component.literal(String.valueOf(b.pipes())), WmdUi.TEXT);
        row(g, x, y + 3 * ROW_H, t("r_tension"), null, 0);
        float tn = b.tension();
        bar(g, x, y + 3 * ROW_H + 10, tn, tn < 0.6F ? WmdUi.GOOD : tn < 0.9F ? WmdUi.WARN : WmdUi.BAD);
        row(g, x, y + 4 * ROW_H, t("r_power"), null, 0);
        WmdUi.energyGauge(g, x, y + 4 * ROW_H + 10, COL_W, 4, b.storedFraction());
    }

    private void row(GuiGraphics g, int x, int y, Component label, Component value, int col) {
        g.drawString(font, label, x, y, WmdUi.TEXT_MUTED, false);
        if (value != null) g.drawString(font, value, x + COL_W - font.width(value), y, col, false);
    }

    private static void bar(GuiGraphics g, int x, int y, float f, int col) {
        g.fill(x, y, x + COL_W, y + 4, WmdUi.WELL);
        g.fill(x, y, x + Math.round(COL_W * Mth.clamp(f, 0.0F, 1.0F)), y + 4, col);
    }
}
