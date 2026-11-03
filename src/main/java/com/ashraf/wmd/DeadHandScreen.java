package com.ashraf.wmd;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class DeadHandScreen extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 128, PH = 172;
    private static final int KEY_Y = 30, BOARD_Y = 50, ROW_Y = 60, ROW_H = 9, ADD_Y = 134, CANCEL_Y = 150, CANCEL_H = 16, LINK_W = 30;
    private int pick;

    public DeadHandScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private DeadHandBlockEntity dh() { return menu.machine() instanceof DeadHandBlockEntity d ? d : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return dh() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        DeadHandBlockEntity d = dh();
        if (d != null) addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, d.getBlockPos())));
    }

    private static String xz(long p) { BlockPos b = BlockPos.of(p); return b.getX() + "," + b.getZ(); }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        DeadHandBlockEntity d = dh();
        if (d == null) return;
        int x = px(), y = topPos, cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        fit(g, Component.translatable("gui.wmd.deadhand.panel"), cx, y + 6, PW - 10, WmdUi.TEXT);
        int left = d.secondsLeft();
        Component status = left >= 0 ? Component.translatable("gui.wmd.deadhand.st.count", OrbitMath.mmss(left * 1000L))
                : Component.translatable(d.armed() ? "gui.wmd.deadhand.st.armed" : "gui.wmd.deadhand.st.safe");
        int kind = left >= 0 ? WmdUi.K_BAD : d.armed() ? WmdUi.K_WARN : WmdUi.K_IDLE;
        int chipW = Math.min(font.width(status), PW - 24) + 15;
        WmdUi.chipRight(g, font, cx + (chipW + 1) / 2, y + 17, status, kind, left >= 0, PW - 10);

        int kw = (PW - 18) / 2;
        for (int i = 0; i < 2; i++) {
            int bx = x + 6 + i * (kw + 6);
            boolean on = d.key(i), h = in(mouseX, mouseY, bx, y + KEY_Y, kw, 14);
            WmdUi.button(g, font, bx, y + KEY_Y, kw, 14, Component.empty(), on ? WmdUi.B_DANGER : WmdUi.B_NORMAL, h);
            fit(g, Component.translatable(on ? "gui.wmd.deadhand.key_on" : "gui.wmd.deadhand.key_off", i == 0 ? "A" : "B"), bx + kw / 2, y + KEY_Y + 3, kw - 4,
                    on ? WmdUi.BAD : WmdUi.TEXT);
        }

        java.util.List<Long> pads = d.pads();
        int[] b = d.board();
        fit(g, Component.translatable("gui.wmd.deadhand.board", pads.size(), Balance.DH_MAX_PADS), cx, y + BOARD_Y, PW - 10, WmdUi.TEXT_MUTED);
        WmdUi.well(g, x + 5, y + ROW_Y - 1, PW - 10, Balance.DH_MAX_PADS * ROW_H + 1);
        for (int i = 0; i < pads.size() && i < Balance.DH_MAX_PADS; i++) {
            int ry = y + ROW_Y + i * ROW_H, st = i * 3 + 2 < b.length ? b[i * 3] : DeadHandBlockEntity.P_UNLOADED;
            Component row = st == DeadHandBlockEntity.P_UNLOADED ? Component.translatable("gui.wmd.deadhand.row_far", xz(pads.get(i)))
                    : st == DeadHandBlockEntity.P_NO_MISSILE || st == DeadHandBlockEntity.P_NO_TARGET ? Component.translatable("gui.wmd.deadhand.row_bad." + st, xz(pads.get(i)))
                    : Component.translatable("gui.wmd.deadhand.row", xz(pads.get(i)), b[i * 3 + 1], b[i * 3 + 2]);
            int col = st == DeadHandBlockEntity.P_READY ? WmdUi.GOOD : st == DeadHandBlockEntity.P_UNLOADED || st == DeadHandBlockEntity.P_NOT_READY ? WmdUi.TEXT_MUTED : WmdUi.BAD;
            fitLeft(g, row, x + 8, ry + 1, PW - 26, col);
            boolean hx = in(mouseX, mouseY, x + PW - 15, ry, 9, ROW_H);
            g.drawString(font, "x", x + PW - 13, ry + 1, hx ? WmdUi.BAD : WmdUi.TEXT_DIM, false);
        }
        if (pads.isEmpty()) fit(g, Component.translatable("gui.wmd.deadhand.board_empty"), cx, y + ROW_Y + 3 * ROW_H, PW - 14, WmdUi.TEXT_DIM);

        long[] c = d.candidates();
        if (pick >= c.length) pick = 0;
        int aw = PW - 12 - LINK_W - 4;
        boolean ha = in(mouseX, mouseY, x + 6, y + ADD_Y, aw, 12);
        WmdUi.well(g, x + 6, y + ADD_Y, aw, 12);
        fit(g, c.length == 0 ? Component.translatable("gui.wmd.deadhand.no_pads") : Component.translatable("gui.wmd.deadhand.pick", xz(c[pick]), pick + 1, c.length),
                x + 6 + aw / 2, y + ADD_Y + 2, aw - 4, c.length == 0 ? WmdUi.TEXT_DIM : ha ? WmdUi.BLUE_LIGHT : WmdUi.TEXT);
        boolean canLink = c.length > 0 && pads.size() < Balance.DH_MAX_PADS;
        int lx = x + PW - 6 - LINK_W;
        WmdUi.button(g, font, lx, y + ADD_Y, LINK_W, 12, Component.translatable("gui.wmd.deadhand.link"), canLink ? WmdUi.B_NORMAL : WmdUi.B_DISABLED,
                canLink && in(mouseX, mouseY, lx, y + ADD_Y, LINK_W, 12));

        boolean can = left >= 0;
        boolean hc = in(mouseX, mouseY, x + 6, y + CANCEL_Y, PW - 12, CANCEL_H);
        WmdUi.button(g, font, x + 6, y + CANCEL_Y, PW - 12, CANCEL_H, Component.empty(), can ? WmdUi.B_DANGER : WmdUi.B_DISABLED, can && hc);
        fit(g, Component.translatable("gui.wmd.deadhand.cancel").withStyle(ChatFormatting.BOLD), cx, y + CANCEL_Y + 4, PW - 16, can ? WmdUi.TEXT : WmdUi.TEXT_DIM);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private void fit(GuiGraphics g, Component text, int cx, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        fitLeft(g, text, cx - maxW / 2, y, maxW, colour);
    }

    private void fitLeft(GuiGraphics g, Component text, int x, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, x, y, colour, false); return; }
        float sc = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - sc) * 4.0F, 0.0F);
        g.pose().scale(sc, sc, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= topPos && my < topPos + PH; }

    private void send(int cmd, int a) { send(cmd, a, 0); }

    private void send(int cmd, int a, int b) {
        DeadHandBlockEntity d = dh();
        if (d == null) return;
        PacketDistributor.sendToServer(new RailgunAimPayload(d.getBlockPos(), cmd, a, b));
        WmdUi.click();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        DeadHandBlockEntity d = dh();
        if (d != null && inPanel(mx, my)) {
            int x = px(), y = topPos, kw = (PW - 18) / 2, ix = (int) mx, iy = (int) my;
            for (int i = 0; i < 2; i++) if (in(ix, iy, x + 6 + i * (kw + 6), y + KEY_Y, kw, 14)) send(i == 0 ? DeadHandBlockEntity.CMD_KEY0 : DeadHandBlockEntity.CMD_KEY1, 0);
            for (int i = 0; i < d.pads().size(); i++)
                if (in(ix, iy, x + PW - 15, y + ROW_Y + i * ROW_H, 9, ROW_H)) { BlockPos p = BlockPos.of(d.pads().get(i)); send(DeadHandBlockEntity.CMD_UNLINK, p.getX(), p.getZ()); }
            long[] c = d.candidates();
            int aw = PW - 12 - LINK_W - 4;
            if (c.length > 0 && in(ix, iy, x + 6, y + ADD_Y, aw, 12)) { pick = (pick + (button == 1 ? c.length - 1 : 1)) % c.length; WmdUi.click(); }
            if (c.length > 0 && in(ix, iy, x + PW - 6 - LINK_W, y + ADD_Y, LINK_W, 12)) { BlockPos p = BlockPos.of(c[Math.min(pick, c.length - 1)]); send(DeadHandBlockEntity.CMD_LINK, p.getX(), p.getZ()); }
            if (d.secondsLeft() >= 0 && in(ix, iy, x + 6, y + CANCEL_Y, PW - 12, CANCEL_H)) send(DeadHandBlockEntity.CMD_CANCEL, 0);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPanel(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }
}
