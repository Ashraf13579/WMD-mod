package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class DredgeScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 172, PH = 149;
    private static final String P = "gui.wmd.dredge.";

    public DredgeScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private DredgeWinchBlockEntity be() { return menu.machine() instanceof DredgeWinchBlockEntity r ? r : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return be() != null ? PW : 0; }

    private void send(int action, int value) {
        DredgeWinchBlockEntity r = be();
        if (r != null) PacketDistributor.sendToServer(new NodulePayload(r.getBlockPos(), action, value));
    }

    @Override
    protected void init() {
        super.init();
        DredgeWinchBlockEntity r = be();
        if (r == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, r.getBlockPos())));
        int x = px(), y = py();
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 56, 78, 16, Component.translatable(P + "drop"), b -> send(DredgeWinchBlockEntity.A_DROP, 0), true));
        addRenderableWidget(new WmdUi.KitButton(x + 88, y + 56, 78, 16, Component.translatable(P + "auto"), b -> send(DredgeWinchBlockEntity.A_AUTO, r.auto() ? 0 : 1)));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        DredgeWinchBlockEntity r = be();
        if (r == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        int st = r.dredgeStatus();
        boolean good = st == DredgeWinchBlockEntity.S_READY || st == DredgeWinchBlockEntity.S_WORKING;
        WmdUi.header(g, font, x, y, PW, new ItemStack(r.getBlockState().getBlock().asItem()), Component.translatable(r.getBlockState().getBlock().getDescriptionId()),
                Component.translatable(P + (st == DredgeWinchBlockEntity.S_WORKING ? "chip_working" : good ? "chip_ready" : "chip_stop")), good ? WmdUi.K_GOOD : WmdUi.K_WARN,
                st == DredgeWinchBlockEntity.S_WORKING);
        fit(g, Component.translatable(P + "st." + st), cx, y + 26, good ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
        fit(g, r.lastHaul() < 0 ? Component.translatable(P + "no_haul") : Component.translatable(P + "last", r.lastHaul()), cx, y + 40, WmdUi.TEXT);
        fit(g, Component.translatable(r.auto() ? P + "auto_on" : P + "auto_off"), x + 88 + 39, y + 76, WmdUi.TEXT_MUTED);
        int hy = y + 96;
        WmdUi.divider(g, x + 5, hy - 3, PW - 10);
        fit(g, Component.translatable(P + "hint_place"), cx, hy + 2, WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "hint_reach", Balance.DREDGE_REACH), cx, hy + 13, WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "hint_power"), cx, hy + 24, WmdUi.TEXT_MUTED);
        fit(g, r.surveyHint().line(), cx, hy + 35, r.surveyHint().found() ? WmdUi.CYAN : WmdUi.TEXT_MUTED);
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) {
        int maxW = PW - 10, w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
