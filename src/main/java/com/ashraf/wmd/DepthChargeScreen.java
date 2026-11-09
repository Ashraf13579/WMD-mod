package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class DepthChargeScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 172, PH = 136;
    private static final String P = "gui.wmd.dc.";

    public DepthChargeScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private DepthChargeRackBlockEntity be() { return menu.machine() instanceof DepthChargeRackBlockEntity r ? r : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return be() != null ? PW : 0; }

    private void send(int action, int value) {
        DepthChargeRackBlockEntity r = be();
        if (r != null) PacketDistributor.sendToServer(new DepthChargePayload(r.getBlockPos(), action, value));
    }

    @Override
    protected void init() {
        super.init();
        DepthChargeRackBlockEntity r = be();
        if (r == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, r.getBlockPos())));
        int x = px(), y = py();
        int[] dd = { -10, -1, 1, 10 };
        String[] dl = { "-10", "-1", "+1", "+10" };
        for (int i = 0; i < 4; i++) {
            final int d = dd[i];
            addRenderableWidget(new WmdUi.KitButton(x + 6 + i * 40, y + 51, 38, 12, Component.literal(dl[i]), b -> send(DepthChargeRackBlockEntity.A_DEPTH, r.depth() + d)));
        }
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 72, 78, 16, Component.translatable(P + "drop"), b -> send(DepthChargeRackBlockEntity.A_DROP, 0), true));
        addRenderableWidget(new WmdUi.KitButton(x + 88, y + 72, 78, 16, Component.translatable(P + "pattern"), b -> send(DepthChargeRackBlockEntity.A_PATTERN, 0), true));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        DepthChargeRackBlockEntity r = be();
        if (r == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        int n = r.shownCount();
        WmdUi.header(g, font, x, y, PW, new ItemStack(r.getBlockState().getBlock().asItem()), Component.translatable(r.getBlockState().getBlock().getDescriptionId()),
                Component.translatable(P + (n > 0 ? "chip_ready" : "chip_empty")), n > 0 ? WmdUi.K_GOOD : WmdUi.K_WARN, n > 0);
        fit(g, Component.translatable(P + "loaded", n), cx, y + 26, WmdUi.TEXT);
        fit(g, Component.translatable(P + "depth", r.depth()), cx, y + 40, WmdUi.TEXT);
        int hy = y + 96;
        WmdUi.divider(g, x + 5, hy - 3, PW - 10);
        fit(g, Component.translatable(P + "hint_redstone"), cx, hy + 2, WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "hint_place"), cx, hy + 13, WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "hint_power"), cx, hy + 24, WmdUi.TEXT_MUTED);
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
