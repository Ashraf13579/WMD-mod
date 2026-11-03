package com.ashraf.wmd;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class SwarmLauncherScreen extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 128, PH = 166;
    private static final int BOX_Y = 32, SET_Y = 50, GRID_Y = 80, CELL = 6, GO_Y = 144, GO_H = 16;
    private EditBox xBox, zBox;

    public SwarmLauncherScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private SwarmLauncherBlockEntity sw() { return menu.machine() instanceof SwarmLauncherBlockEntity s ? s : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return sw() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        SwarmLauncherBlockEntity s = sw();
        if (s == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, s.getBlockPos())));
        int bw = (PW - 18) / 2;
        xBox = new EditBox(font, px() + 6, topPos + BOX_Y, bw, 13, Component.literal("X"));
        zBox = new EditBox(font, px() + 12 + bw, topPos + BOX_Y, bw, 13, Component.literal("Z"));
        for (EditBox b : new EditBox[] { xBox, zBox }) {
            b.setMaxLength(9);
            b.setFilter(v -> v.isEmpty() || v.matches("-?\\d{0,8}"));
            addRenderableWidget(b);
        }
        xBox.setHint(Component.literal("X")); zBox.setHint(Component.literal("Z"));
        if (s.hasTarget()) { xBox.setValue(Integer.toString(s.targetX())); zBox.setValue(Integer.toString(s.targetZ())); }
    }

    private static String state(SwarmLauncherBlockEntity s) {
        return s.firing() ? "firing" : s.loaded() <= 0 ? "empty" : s.storedShown() < Balance.SW_SALVO_FE ? "power" : !s.hasTarget() ? "no_target" : "ready";
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        SwarmLauncherBlockEntity s = sw();
        if (s == null) return;
        int x = px(), y = topPos, cx = x + PW / 2;
        String st = state(s);
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        fit(g, Component.translatable("gui.wmd.swarm.panel"), cx, y + 6, WmdUi.TEXT);
        int kind = switch (st) { case "ready" -> WmdUi.K_GOOD; case "firing" -> WmdUi.K_WARN; case "power" -> WmdUi.K_BAD; default -> WmdUi.K_IDLE; };
        Component status = Component.translatable("gui.wmd.swarm.st." + st);
        int chipW = Math.min(font.width(status), PW - 24) + 15;
        WmdUi.chipRight(g, font, cx + (chipW + 1) / 2, y + 17, status, kind, st.equals("firing"), PW - 10);

        boolean hs = in(mouseX, mouseY, x + 6, y + SET_Y, PW - 12, 13);
        WmdUi.button(g, font, x + 6, y + SET_Y, PW - 12, 13, Component.translatable("gui.wmd.railgun.set"), WmdUi.B_NORMAL, hs);
        fit(g, s.hasTarget() ? Component.translatable("gui.wmd.railgun.target", s.targetX(), s.targetZ()) : Component.translatable("gui.wmd.railgun.no_target"),
                cx, y + SET_Y + 17, s.hasTarget() ? WmdUi.BLUE_LIGHT : WmdUi.TEXT_MUTED);

        int gw = 7 * CELL + 6, gx = cx - gw / 2, gy = y + GRID_Y;
        WmdUi.well(g, gx - 2, gy - 2, gw + 4, gw + 4);
        boolean[] full = new boolean[Balance.SW_CELLS];
        for (int k = 0; k < s.loaded() && k < SwarmLauncherBlockEntity.ORDER.length; k++) full[SwarmLauncherBlockEntity.ORDER[k]] = true;
        for (int c = 0; c < Balance.SW_CELLS; c++) {
            int i = c / 7, j = c % 7, qx = gx + i * (CELL + 1), qy = gy + j * (CELL + 1);
            g.fill(qx, qy, qx + CELL, qy + CELL, full[c] ? WmdUi.BLUE_LIGHT : 0xFF2A323C);
        }
        fit(g, Component.translatable("gui.wmd.swarm.loaded", s.loaded(), Balance.SW_CELLS), cx, gy + gw + 6, WmdUi.TEXT_MUTED);

        boolean can = st.equals("ready");
        int gx0 = x + 6, bw = PW - 12, gy0 = y + GO_Y;
        boolean gh = in(mouseX, mouseY, gx0, gy0, bw, GO_H);
        WmdUi.button(g, font, gx0, gy0, bw, GO_H, Component.empty(), can ? WmdUi.B_DANGER : WmdUi.B_DISABLED, gh && can);
        fit(g, Component.translatable("gui.wmd.swarm.fire").withStyle(ChatFormatting.BOLD), cx, gy0 + 4, bw - 4, can ? WmdUi.TEXT : WmdUi.TEXT_DIM);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) { fit(g, text, cx, y, PW - 10, colour); }

    private void fit(GuiGraphics g, Component text, int cx, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float sc = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - sc) * 4.0F, 0.0F);
        g.pose().scale(sc, sc, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= topPos && my < topPos + PH; }

    private void send(int cmd, int x, int z) {
        SwarmLauncherBlockEntity s = sw();
        if (s == null) return;
        PacketDistributor.sendToServer(new RailgunAimPayload(s.getBlockPos(), cmd, x, z));
        WmdUi.click();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        SwarmLauncherBlockEntity s = sw();
        if (s != null && inPanel(mx, my)) {
            for (EditBox b : new EditBox[] { xBox, zBox })
                if (b != null && b.mouseClicked(mx, my, button)) { setFocused(b); b.setFocused(true); return true; }
            int x = px(), y = topPos;
            if (in((int) mx, (int) my, x + 6, y + SET_Y, PW - 12, 13)) {
                try { send(SwarmLauncherBlockEntity.CMD_TARGET, Integer.parseInt(xBox.getValue().trim()), Integer.parseInt(zBox.getValue().trim())); }
                catch (NumberFormatException ignored) {}
            } else if (in((int) mx, (int) my, x + 6, y + GO_Y, PW - 12, GO_H)) {
                send(SwarmLauncherBlockEntity.CMD_FIRE, 0, 0);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        for (EditBox b : new EditBox[] { xBox, zBox }) if (b != null && b.isFocused() && b.charTyped(c, modifiers)) return true;
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox b : new EditBox[] { xBox, zBox })
            if (keyCode != 256 && b != null && b.isFocused() && (b.keyPressed(keyCode, scanCode, modifiers) || b.canConsumeInput())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPanel(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }
}
