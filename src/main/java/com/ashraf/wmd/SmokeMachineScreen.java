package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SmokeMachineScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 140, PH = 100, BTN_Y = 62, ARM_TICKS = 60;
    private static final String P = "gui.wmd.smoke.";

    private WmdUi.KitButton launch;
    private long armedAt = -1L;

    public SmokeMachineScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private SmokeMachineBlockEntity smoke() { return menu.machine() instanceof SmokeMachineBlockEntity s ? s : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    private long now() { return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L; }

    private boolean armed() { return armedAt >= 0L && now() - armedAt < ARM_TICKS; }

    @Override
    protected int sidePanelWidth() { return smoke() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        if (menu.machine() instanceof BlockEntity be)
            addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, be.getBlockPos())));
        if (smoke() == null) return;
        launch = addRenderableWidget(new WmdUi.KitButton(px() + 6, py() + BTN_Y, PW - 12, 18, Component.translatable(P + "launch"), b -> press(), true));
    }

    private void press() {
        if (!armed()) { armedAt = now(); return; }
        armedAt = -1L;
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SmokeMachineBlockEntity.A_LAUNCH);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        SmokeMachineBlockEntity s = smoke();
        if (launch == null || s == null) return;
        launch.active = s.cooldownLeft(now()) == 0 && s.canisterCount() > 0 && menu.energy() >= Balance.SMOKE_FE_PER_LAUNCH;
        launch.setMessage(Component.translatable(P + (armed() ? "confirm" : "launch")));
        if (!launch.active) armedAt = -1L;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        SmokeMachineBlockEntity s = smoke();
        if (s == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        long now = now();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        fit(g, Component.translatable(P + "panel"), cx, y + 6, WmdUi.TEXT);
        Component state;
        int kind;
        boolean pulse = false;
        long left = Balance.SMOKE_SECONDS - s.sinceLaunch(now) / 20L;
        if (s.smoking(now)) { state = Component.translatable(P + "state_smoke", left); kind = WmdUi.K_INFO; pulse = true; }
        else if (s.cooldownLeft(now) > 0) { state = Component.translatable(P + "state_cooldown", s.cooldownLeft(now)); kind = WmdUi.K_WARN; }
        else if (s.canisterCount() == 0) { state = Component.translatable(P + "state_empty"); kind = WmdUi.K_BAD; }
        else if (menu.energy() < Balance.SMOKE_FE_PER_LAUNCH) { state = Component.translatable(P + "state_power"); kind = WmdUi.K_BAD; }
        else { state = Component.translatable(P + "state_ready"); kind = WmdUi.K_GOOD; }
        int chipW = Math.min(font.width(state), PW - 10 - 14) + 15;
        WmdUi.chipRight(g, font, cx + (chipW + 1) / 2, y + 16, state, kind, pulse, PW - 10);
        fit(g, Component.translatable(P + "canisters", s.canisterCount(), Balance.SMOKE_CANISTERS_MAX), cx, y + 32, WmdUi.TEXT);
        fit(g, Component.translatable(P + "cloud", Balance.SMOKE_RADIUS, Balance.SMOKE_SECONDS), cx, y + 43, WmdUi.TEXT_MUTED);
        WmdUi.divider(g, x + 5, y + BTN_Y - 6, PW - 10);
        fit(g, Component.translatable(P + (armed() ? "hint_armed" : "hint")), cx, y + 86, armed() ? WmdUi.WARN : WmdUi.TEXT_MUTED);
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
