package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class CollectorRigScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 172, PH = 149;
    private static final String P = "gui.wmd.rig.";

    public CollectorRigScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private CollectorRigBlockEntity be() { return menu.machine() instanceof CollectorRigBlockEntity r ? r : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return be() != null ? PW : 0; }

    private void send(int action) {
        CollectorRigBlockEntity r = be();
        if (r != null) PacketDistributor.sendToServer(new NodulePayload(r.getBlockPos(), action, 0));
    }

    @Override
    protected void init() {
        super.init();
        CollectorRigBlockEntity r = be();
        if (r == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, r.getBlockPos())));
        int x = px(), y = py();
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 64, 78, 16, Component.translatable(P + "launch"), b -> send(CollectorRigBlockEntity.A_LAUNCH), true));
        addRenderableWidget(new WmdUi.KitButton(x + 88, y + 64, 78, 16, Component.translatable(P + "recall"), b -> send(CollectorRigBlockEntity.A_RECALL)));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        CollectorRigBlockEntity r = be();
        if (r == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        int st = r.rigStatus(), cs = r.crawlerState();
        boolean good = st == CollectorRigBlockEntity.S_READY || st == CollectorRigBlockEntity.S_WORKING || st == CollectorRigBlockEntity.S_RETURNING;
        boolean out = cs != CollectorRigBlockEntity.C_DOCKED;
        WmdUi.header(g, font, x, y, PW, new ItemStack(r.getBlockState().getBlock().asItem()), Component.translatable(r.getBlockState().getBlock().getDescriptionId()),
                Component.translatable(P + (out && good ? "chip_working" : good ? "chip_ready" : "chip_stop")), good ? WmdUi.K_GOOD : WmdUi.K_WARN, out);
        fit(g, Component.translatable(P + "st." + st), cx, y + 26, good ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "cs." + cs), cx, y + 38, WmdUi.TEXT);
        fit(g, Component.translatable(P + "count", r.targetsLeft(), r.collected()), cx, y + 50, WmdUi.TEXT_MUTED);
        int hy = y + 96;
        WmdUi.divider(g, x + 5, hy - 3, PW - 10);
        fit(g, Component.translatable(P + "hint_place"), cx, hy + 2, WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "hint_split", Balance.RIG_SEP_IN, Balance.RIG_MN_OUT, Balance.RIG_NICO_OUT), cx, hy + 13, WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "hint_reach", Balance.CRAWLER_RADIUS), cx, hy + 24, WmdUi.TEXT_MUTED);
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
