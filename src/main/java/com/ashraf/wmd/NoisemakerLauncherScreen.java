package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class NoisemakerLauncherScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 172, PH = 130;
    private static final String P = "gui.wmd.noisemaker_launcher.";
    private WmdUi.KitButton autoBtn;

    public NoisemakerLauncherScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private NoisemakerLauncherBlockEntity be() { return menu.machine() instanceof NoisemakerLauncherBlockEntity d ? d : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return be() != null ? PW : 0; }

    private void send(int action, int value) {
        NoisemakerLauncherBlockEntity d = be();
        if (d != null) PacketDistributor.sendToServer(new NoisemakerLauncherPayload(d.getBlockPos(), action, value));
    }

    private Component autoLabel(NoisemakerLauncherBlockEntity d) { return Component.translatable(P + (d.auto() ? "auto_on" : "auto_off")); }

    @Override
    protected void init() {
        super.init();
        NoisemakerLauncherBlockEntity d = be();
        if (d == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, d.getBlockPos())));
        int x = px(), y = py();
        autoBtn = addRenderableWidget(new WmdUi.KitButton(x + 6, y + 51, PW - 12, 14, autoLabel(d), b -> send(NoisemakerLauncherBlockEntity.A_AUTO, d.auto() ? 0 : 1)));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 70, PW - 12, 16, Component.translatable(P + "fire"), b -> send(NoisemakerLauncherBlockEntity.A_FIRE, 0), true));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        NoisemakerLauncherBlockEntity d = be();
        if (d == null) return;
        if (autoBtn != null) autoBtn.setMessage(autoLabel(d));
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        int st = d.fireStatus();
        int kind = st == NoisemakerLauncherBlockEntity.S_READY ? WmdUi.K_GOOD : st == NoisemakerLauncherBlockEntity.S_CHARGING ? WmdUi.K_INFO
                : st == NoisemakerLauncherBlockEntity.S_EMPTY ? WmdUi.K_WARN : WmdUi.K_BAD;
        WmdUi.header(g, font, x, y, PW, new ItemStack(d.getBlockState().getBlock().asItem()), Component.translatable(d.getBlockState().getBlock().getDescriptionId()),
                Component.translatable(P + "chip." + st), kind, st == NoisemakerLauncherBlockEntity.S_CHARGING);
        fit(g, Component.translatable(P + "loaded", d.loaded(), Balance.DECOY_MAG), cx, y + 26, WmdUi.TEXT);
        fit(g, Component.translatable(P + "charge", d.charge01x100()), cx, y + 38, WmdUi.TEXT_MUTED);
        int hy = y + 92;
        WmdUi.divider(g, x + 5, hy - 3, PW - 10);
        fit(g, Component.translatable(P + "hint_place"), cx, hy + 2, WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "hint_auto", (int) Balance.DECOY_AUTO_RANGE), cx, hy + 13, WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "hint_redstone"), cx, hy + 24, WmdUi.TEXT_MUTED);
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
