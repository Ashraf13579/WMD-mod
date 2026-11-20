package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

public class NaniteForgeScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 140, PH = 112, BTN_Y = 72;
    private static final String P = "gui.wmd.nforge.";

    private WmdUi.KitButton autoBtn;

    public NaniteForgeScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private NaniteForgeBlockEntity forge() { return menu.machine() instanceof NaniteForgeBlockEntity f ? f : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return forge() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        if (menu.machine() instanceof BlockEntity be) {
            addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, be.getBlockPos())));
            NaniteForgeBlockEntity.viewing(be.getBlockPos());
        }
        if (forge() == null) return;
        autoBtn = addRenderableWidget(new WmdUi.KitButton(px() + 6, py() + BTN_Y, PW - 12, 18, Component.translatable(P + "auto_on"), b -> press(), false));
    }

    @Override
    public void removed() {
        NaniteForgeBlockEntity.viewing(null);
        super.removed();
    }

    private void press() {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, NaniteForgeBlockEntity.A_AUTO);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        NaniteForgeBlockEntity f = forge();
        if (autoBtn == null || f == null) return;
        autoBtn.setMessage(Component.translatable(P + (f.auto() ? "auto_on" : "auto_off")));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        NaniteForgeBlockEntity f = forge();
        if (f == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        fit(g, Component.translatable(P + "panel"), cx, y + 6, WmdUi.TEXT);
        int mode = f.modeNow();
        boolean charged = menu.energy() >= Balance.NFORGE_MIN_FE;
        Component state;
        int kind;
        boolean pulse = false;
        if (!f.linked()) { state = Component.translatable(P + "st.nolink"); kind = WmdUi.K_BAD; }
        else if (mode == NaniteForgeBlockEntity.SPRAY) { state = Component.translatable(P + "st.spray"); kind = WmdUi.K_INFO; pulse = true; }
        else if (mode == NaniteForgeBlockEntity.LOAD) { state = Component.translatable(P + "st.load"); kind = WmdUi.K_INFO; pulse = true; }
        else if (mode == NaniteForgeBlockEntity.RUN) { state = Component.translatable(P + "st.run"); kind = WmdUi.K_GOOD; pulse = true; }
        else if (!charged) { state = Component.translatable(P + "st.charging"); kind = WmdUi.K_WARN; }
        else { state = Component.translatable(P + "st.ready"); kind = WmdUi.K_GOOD; }
        int chipW = Math.min(font.width(state), PW - 10 - 14) + 15;
        WmdUi.chipRight(g, font, cx + (chipW + 1) / 2, y + 16, state, kind, pulse, PW - 10);
        fit(g, Component.translatable(P + (f.linked() ? "link_on" : "link_off")), cx, y + 32, f.linked() ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "charge", menu.energy() / 1000, Balance.NFORGE_MIN_FE / 1000), cx, y + 43, charged ? WmdUi.TEXT : WmdUi.WARN);
        fit(g, Component.translatable(P + "rebuilt", f.rebuiltCount()), cx, y + 54, WmdUi.TEXT_MUTED);
        WmdUi.divider(g, x + 5, y + BTN_Y - 6, PW - 10);
        fit(g, Component.translatable(P + (f.auto() ? "hint_auto" : "hint_manual")), cx, y + 98, WmdUi.TEXT_MUTED);
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) {
        int maxW = PW - 10;
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float sc = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - sc) * 4.0F, 0.0F);
        g.pose().scale(sc, sc, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
