package com.ashraf.wmd;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class RailgunScreen extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 128, PH = 160;
    private static final int BOX_Y = 32, SET_Y = 50, BAR_Y = 88, GO_Y = 102, GO_H = 18;
    private EditBox xBox, zBox;

    public RailgunScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private RailgunBlockEntity gun() { return menu.machine() instanceof RailgunBlockEntity r ? r : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return gun() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        RailgunBlockEntity r = gun();
        if (r == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, r.getBlockPos())));
        int bw = (PW - 18) / 2;
        xBox = new EditBox(font, px() + 6, topPos + BOX_Y, bw, 13, Component.literal("X"));
        zBox = new EditBox(font, px() + 12 + bw, topPos + BOX_Y, bw, 13, Component.literal("Z"));
        for (EditBox b : new EditBox[] { xBox, zBox }) {
            b.setMaxLength(9);
            b.setFilter(s -> s.isEmpty() || s.matches("-?\\d{0,8}"));
            addRenderableWidget(b);
        }
        xBox.setHint(Component.literal("X")); zBox.setHint(Component.literal("Z"));
        if (r.hasTarget()) { xBox.setValue(Integer.toString(r.targetX())); zBox.setValue(Integer.toString(r.targetZ())); }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        RailgunBlockEntity r = gun();
        if (r == null) return;
        int x = px(), y = topPos, cx = x + PW / 2, st = r.state();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        fit(g, Component.translatable("gui.wmd.railgun.panel"), cx, y + 6, WmdUi.TEXT);
        int kind = switch (st) { case RailgunBlockEntity.CHARGE -> WmdUi.K_WARN; case RailgunBlockEntity.ABORT -> WmdUi.K_BAD; case RailgunBlockEntity.IDLE -> WmdUi.K_IDLE; default -> WmdUi.K_INFO; };
        Component status = Component.translatable("gui.wmd.railgun.st." + st);
        int chipW = Math.min(font.width(status), PW - 24) + 15;
        WmdUi.chipRight(g, font, cx + (chipW + 1) / 2, y + 17, status, kind, st != RailgunBlockEntity.IDLE, PW - 10);

        boolean idle = st == RailgunBlockEntity.IDLE;
        boolean hs = in(mouseX, mouseY, x + 6, y + SET_Y, PW - 12, 13);
        WmdUi.button(g, font, x + 6, y + SET_Y, PW - 12, 13, Component.translatable("gui.wmd.railgun.set"), idle ? WmdUi.B_NORMAL : WmdUi.B_DISABLED, hs && idle);
        fit(g, r.hasTarget() ? Component.translatable("gui.wmd.railgun.target", r.targetX(), r.targetZ()) : Component.translatable("gui.wmd.railgun.no_target"),
                cx, y + SET_Y + 17, r.hasTarget() ? WmdUi.BLUE_LIGHT : WmdUi.TEXT_MUTED);

        int bx = x + 6, bw = PW - 12, by = y + BAR_Y;
        WmdUi.well(g, bx, by, bw, 8);
        int fill = Math.round(bw * r.charge01());
        if (fill > 0) g.fill(bx, by, bx + fill, by + 8, WmdUi.BLUE);
        fit(g, st == RailgunBlockEntity.CHARGE ? Component.translatable("gui.wmd.railgun.charge", Math.round(r.charge01() * 100), OrbitMath.mmss(r.secondsToShot() * 1000L))
                : Component.translatable(r.loaded() ? "gui.wmd.railgun.loaded" : "gui.wmd.railgun.slugs", r.slugs()), cx, by - 10, WmdUi.TEXT_MUTED);

        boolean busy = st == RailgunBlockEntity.SLEW || st == RailgunBlockEntity.LOAD || st == RailgunBlockEntity.CHARGE;
        boolean cool = st == RailgunBlockEntity.AFTER || st == RailgunBlockEntity.ABORT;
        int gx0 = x + 6, gw = PW - 12, gy0 = y + GO_Y;
        boolean gh = in(mouseX, mouseY, gx0, gy0, gw, GO_H);
        WmdUi.button(g, font, gx0, gy0, gw, GO_H, Component.empty(), cool ? WmdUi.B_DISABLED : busy ? WmdUi.B_DANGER : WmdUi.B_NORMAL, gh && !cool);
        fit(g, Component.translatable(busy ? "gui.wmd.railgun.stop" : "gui.wmd.railgun.fire").withStyle(ChatFormatting.BOLD), cx, gy0 + 5, gw - 4,
                cool ? WmdUi.TEXT_DIM : busy ? WmdUi.TEXT : WmdUi.BAD);

        if (r.why() != RailgunBlockEntity.R_NONE) {
            int lineY = y + 124;
            for (var seq : font.split(Component.translatable("gui.wmd.railgun.why." + r.why()), PW - 10)) {
                g.drawString(font, seq, cx - font.width(seq) / 2, lineY, WmdUi.BAD, false);
                lineY += 10;
                if (lineY > y + PH - 10) break;
            }
        }
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) { fit(g, text, cx, y, PW - 10, colour); }

    private void fit(GuiGraphics g, Component text, int cx, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= topPos && my < topPos + PH; }

    private void send(int cmd, int x, int z) {
        RailgunBlockEntity r = gun();
        if (r == null) return;
        PacketDistributor.sendToServer(new RailgunAimPayload(r.getBlockPos(), cmd, x, z));
        WmdUi.click();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        RailgunBlockEntity r = gun();
        if (r != null && inPanel(mx, my)) {
            for (EditBox b : new EditBox[] { xBox, zBox })
                if (b != null && b.mouseClicked(mx, my, button)) { setFocused(b); b.setFocused(true); return true; }
            int x = px(), y = topPos, st = r.state();
            if (in((int) mx, (int) my, x + 6, y + SET_Y, PW - 12, 13) && st == RailgunBlockEntity.IDLE) {
                try { send(RailgunBlockEntity.CMD_TARGET, Integer.parseInt(xBox.getValue().trim()), Integer.parseInt(zBox.getValue().trim())); }
                catch (NumberFormatException ignored) {}
            } else if (in((int) mx, (int) my, x + 6, y + GO_Y, PW - 12, GO_H)) {
                boolean busy = st == RailgunBlockEntity.SLEW || st == RailgunBlockEntity.LOAD || st == RailgunBlockEntity.CHARGE;
                if (busy) send(RailgunBlockEntity.CMD_STOP, 0, 0);
                else if (st == RailgunBlockEntity.IDLE) send(RailgunBlockEntity.CMD_FIRE, 0, 0);
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
