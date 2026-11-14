package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

final class NavLightScreen extends Screen {

    static final int PW = 236, PH = 166, WELL_X = 8, WELL_Y = 26, WELL_S = 76, SPOT_B = 14, SPOT_D = 22, TURN_Y = 108, TURN_H = 14,
            COL_X = 92, COL_W = 136, TOG_W = 44, TOG_H = 14, ROW_F = 26, ROW_P = 76, ROW_B = 108, FOOT_Y = 148;
    private static final String P = "gui.wmd.navlight.";

    private final BlockPos pos;
    private int func, spot, nose, power, bright;
    private int heldUntil;
    private int ticks;
    private final List<Toggle> toggles = new ArrayList<>();

    NavLightScreen(BlockPos pos) {
        super(Component.translatable("block.wmd.nav_light"));
        this.pos = pos;
    }

    static void open(BlockPos pos) { Minecraft.getInstance().setScreen(new NavLightScreen(pos)); }

    private int px() { return (width - PW) / 2; }
    private int py() { return (height - PH) / 2; }

    private static Component t(String k, Object... a) { return Component.translatable(P + k, a); }

    private BlockState state() { return minecraft != null && minecraft.level != null ? minecraft.level.getBlockState(pos) : null; }
    private NavLightBlockEntity be() { return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof NavLightBlockEntity b ? b : null; }

    private boolean pull() {
        BlockState s = state();
        NavLightBlockEntity b = be();
        if (s == null || b == null || !(s.getBlock() instanceof NavLightBlock)) return false;
        if (heldUntil > 0) return true;
        func = s.getValue(NavLightBlock.FUNC).ordinal();
        spot = s.getValue(NavLightBlock.SPOT).ordinal();
        nose = s.getValue(NavLightBlock.NOSE).get3DDataValue();
        power = b.power();
        bright = b.bright();
        return true;
    }

    private void send() {
        heldUntil = 10;
        PacketDistributor.sendToServer(new NavLightPayloads.Apply(pos, func, spot, nose, power, bright));
    }

    private final class Toggle extends WmdUi.KitButton {
        private final java.util.function.IntSupplier value;
        private final int mine;

        Toggle(int x, int y, int w, Component text, java.util.function.IntSupplier value, int mine, java.util.function.IntConsumer set) {
            super(x, y, w, TOG_H, text, b -> { set.accept(mine); send(); });
            this.value = value;
            this.mine = mine;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            super.renderWidget(g, mx, my, pt);
            if (value.getAsInt() == mine) {
                g.fill(getX(), getY() + height - 2, getX() + width, getY() + height, WmdUi.BLUE_LIGHT);
                g.fill(getX(), getY(), getX() + width, getY() + 1, WmdUi.BLUE);
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        toggles.clear();
        if (!pull()) return;
        int x = px(), y = py();
        NavLightBlock.Func[] fs = NavLightBlock.Func.values();
        for (int i = 0; i < fs.length; i++) {
            int bx = x + COL_X + (i % 3) * (TOG_W + 2), by = y + ROW_F + 10 + (i / 3) * (TOG_H + 2);
            toggles.add(addRenderableWidget(new Toggle(bx, by, TOG_W, t("f_" + fs[i].getSerializedName()), () -> func, i, v -> func = v)));
        }
        String[] pw = { "p_on", "p_off", "p_auto" };
        for (int i = 0; i < 3; i++)
            toggles.add(addRenderableWidget(new Toggle(x + COL_X + i * (TOG_W + 2), y + ROW_P + 10, TOG_W, t(pw[i]), () -> power, i, v -> power = v)));
        for (int i = 0; i < 3; i++)
            toggles.add(addRenderableWidget(new Toggle(x + COL_X + i * (TOG_W + 2), y + ROW_B + 10, TOG_W, t("b_" + i), () -> bright, i, v -> bright = v)));

        int cx = x + WELL_X + WELL_S / 2 - SPOT_B / 2, cy = y + WELL_Y + WELL_S / 2 - SPOT_B / 2;
        NavLightBlock.Spot[] ss = NavLightBlock.Spot.values();
        for (int i = 0; i < ss.length; i++) {
            Toggle tg = new Toggle(cx + ss[i].du * SPOT_D, cy - ss[i].dv * SPOT_D, SPOT_B, Component.empty(), () -> spot, i, v -> spot = v);
            tg.setTooltip(net.minecraft.client.gui.components.Tooltip.create(t("s_" + ss[i].getSerializedName())));
            toggles.add(addRenderableWidget(tg));
        }
        addRenderableWidget(new WmdUi.KitButton(x + WELL_X, y + TURN_Y, WELL_S, TURN_H, t("turn"), b -> {
            BlockState s = state();
            if (s == null || !(s.getBlock() instanceof NavLightBlock)) return;
            nose = NavLightBlock.nextNose(s.getValue(NavLightBlock.ATTACH), Direction.from3DDataValue(nose)).get3DDataValue();
            send();
        }));
    }

    @Override
    public void tick() {
        ticks++;
        if (heldUntil > 0) heldUntil--;
        if (!pull() || minecraft.player == null || !Reach.within(minecraft.player, pos, Balance.NL_REACH + 2.0)) onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        BlockState s = state();
        NavLightBlockEntity b = be();
        if (s == null || b == null || !(s.getBlock() instanceof NavLightBlock)) return;
        int x = px(), y = py();
        boolean lit = b.lit();
        WmdUi.panel(g, x, y, PW, PH);
        WmdUi.header(g, font, x, y, PW, new ItemStack(s.getBlock()), title, t(lit ? "st_lit" : "st_off"), lit ? WmdUi.K_GOOD : WmdUi.K_IDLE, false);
        face(g, s, x + WELL_X, y + WELL_Y, lit);
        g.drawString(font, t("l_func"), x + COL_X, y + ROW_F, WmdUi.TEXT_MUTED, false);
        g.drawString(font, t("l_power"), x + COL_X, y + ROW_P, WmdUi.TEXT_MUTED, false);
        g.drawString(font, t("l_bright"), x + COL_X, y + ROW_B, WmdUi.TEXT_MUTED, false);
        WmdUi.divider(g, x + 8, y + FOOT_Y - 4, PW - 16);
        NavLightBlock.Func f = NavLightBlock.Func.values()[func];
        String h = power == NavLightBlockEntity.ON ? "hint_on" : power == NavLightBlockEntity.OFF ? "hint_off"
                : f == NavLightBlock.Func.STROBE ? "hint_auto_strobe" : f == NavLightBlock.Func.LANDING ? "hint_auto_landing" : "hint_auto";
        WmdUi.drawFit(g, font, t(h), x + 8, y + FOOT_Y, PW - 16, WmdUi.TEXT_MUTED);
    }

    private void face(GuiGraphics g, BlockState s, int x, int y, boolean lit) {
        WmdUi.well(g, x, y, WELL_S, WELL_S);
        g.fill(x, y, x + WELL_S, y + WELL_S, WmdUi.WELL);
        for (int i = 1; i < 4; i++) {
            int k = i * WELL_S / 4;
            g.fill(x + k, y + 1, x + k + 1, y + WELL_S - 1, WmdUi.DIVIDER);
            g.fill(x + 1, y + k, x + WELL_S - 1, y + k + 1, WmdUi.DIVIDER);
        }

        Direction attach = s.getValue(NavLightBlock.ATTACH), n = Direction.from3DDataValue(nose);
        Direction[] uv = NavLightBlock.faceAxes(attach, n);
        int au = dot(n, uv[0]), av = dot(n, uv[1]);
        int cx = x + WELL_S / 2, cy = y + WELL_S / 2;
        for (int i = 4; i < 30; i++) g.fill(cx + au * i, cy - av * i, cx + au * i + 1, cy - av * i + 1, WmdUi.TEXT_DIM);
        if (au == 0 && av == 0) g.fill(cx - 1, y + 3, cx + 2, y + 6, WmdUi.TEXT_DIM);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        BlockState s = state();
        NavLightBlockEntity b = be();
        if (s == null || b == null || !(s.getBlock() instanceof NavLightBlock)) return;
        NavLightBlock.Spot sp = NavLightBlock.Spot.values()[spot];
        NavLightBlock.Func f = NavLightBlock.Func.values()[func];
        float[] c = NavLightRenderer.RGB[f.ordinal()];
        float k = b.lit() && minecraft != null && minecraft.level != null
                ? NavLightRenderer.flash(f, minecraft.level.getGameTime() + pt, s.getValue(NavLightBlock.ATTACH) == Direction.UP) : 0.0F;
        int gk = Math.round(60 + 195 * k);
        int col = 0xFF000000 | Math.round(c[0] * gk) << 16 | Math.round(c[1] * gk) << 8 | Math.round(c[2] * gk);
        int lx = px() + WELL_X + WELL_S / 2 + sp.du * SPOT_D, ly = py() + WELL_Y + WELL_S / 2 - sp.dv * SPOT_D;
        g.fill(lx - 3, ly - 3, lx + 3, ly + 3, col);
    }

    private static int dot(Direction a, Direction b) { return a.getStepX() * b.getStepX() + a.getStepY() * b.getStepY() + a.getStepZ() * b.getStepZ(); }
}
