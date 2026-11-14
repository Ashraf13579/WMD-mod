package com.ashraf.wmd;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class ServiceArmScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 160, PH = 170, LIST_Y = 88, ROW_H = 10;
    static final String P = "gui.wmd.sarm.";

    public ServiceArmScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private ServiceArmBlockEntity arm() { return menu.machine() instanceof ServiceArmBlockEntity a ? a : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return arm() != null ? PW : 0; }

    private WmdUi.KitButton modeB, goB;

    private void send(int action, int... data) {
        ServiceArmBlockEntity a = arm();
        if (a != null) PacketDistributor.sendToServer(new ShorePayload(a.getBlockPos(), action, data));
    }

    @Override
    protected void init() {
        super.init();
        ServiceArmBlockEntity a = arm();
        if (a == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, a.getBlockPos())));
        int half = (PW - 14) / 2;
        modeB = addRenderableWidget(new WmdUi.KitButton(px() + 6, topPos + 150, half, 14, modeLabel(a), w -> send(ServiceArmBlockEntity.A_MODE)));
        goB = addRenderableWidget(new WmdUi.KitButton(px() + 8 + half, topPos + 150, half, 14, goLabel(a),
                w -> { ServiceArmBlockEntity k = arm(); if (k != null) send(k.phase() == ServiceArmBlockEntity.P_IDLE ? ServiceArmBlockEntity.A_GO : ServiceArmBlockEntity.A_STOP); }));
    }

    private static Component modeLabel(ServiceArmBlockEntity a) { return Component.translatable(P + "mode." + a.mode()); }

    private static Component goLabel(ServiceArmBlockEntity a) { return Component.translatable(a.phase() == ServiceArmBlockEntity.P_IDLE ? P + "start" : P + "stop"); }

    private static int kind(int st) {
        return switch (st) {
            case ServiceArmBlockEntity.S_WORKING, ServiceArmBlockEntity.S_PUMPING -> WmdUi.K_INFO;
            case ServiceArmBlockEntity.S_IDLE, ServiceArmBlockEntity.S_DONE -> WmdUi.K_GOOD;
            case ServiceArmBlockEntity.S_WAIT_PARK, ServiceArmBlockEntity.S_NO_FUEL -> WmdUi.K_WARN;
            case ServiceArmBlockEntity.S_STANDBY -> WmdUi.K_IDLE;
            default -> WmdUi.K_BAD;
        };
    }

    private int rowAt(double mx, double my) {
        ServiceArmBlockEntity a = arm();
        if (a == null) return -1;
        int x = px() + 5, y = topPos + LIST_Y + 2;
        if (mx < x || mx >= x + PW - 10 || my < y) return -1;
        int r = (int) ((my - y) / ROW_H);
        return r < a.foundList().size() && r < ServiceArmBlockEntity.LIST ? r : -1;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        ServiceArmBlockEntity a = arm();
        if (a == null) return;
        if (modeB != null) modeB.setMessage(modeLabel(a));
        if (goB != null) goB.setMessage(goLabel(a));
        int x = px(), y = topPos, tw = PW - 14;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        int st = a.armStatus();
        WmdUi.header(g, font, x, y, PW, new ItemStack(a.getBlockState().getBlock().asItem()), Component.translatable(a.getBlockState().getBlock().getDescriptionId()),
                Component.translatable(P + "chip." + st), kind(st), st == ServiceArmBlockEntity.S_WORKING || st == ServiceArmBlockEntity.S_PUMPING);
        WmdUi.drawFit(g, font, Component.translatable(P + "mode_line", Component.translatable(P + "mode." + a.mode())), x + 7, y + 26, tw, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "reach", (int) Balance.SA_REACH, Balance.SA_FUEL_RATE * 20, Balance.SA_GROUND_FE * 20), x + 7, y + 37, tw, WmdUi.TEXT_MUTED);
        WmdUi.well(g, x + 5, y + 50, PW - 10, 22);
        String t = a.targetText();
        WmdUi.drawFit(g, font, t.isEmpty() ? Component.translatable(P + "no_target") : Component.translatable(P + "target", t), x + 7, y + 52, tw, t.isEmpty() ? WmdUi.TEXT_MUTED : WmdUi.TEXT);
        WmdUi.drawFit(g, font, Component.translatable(P + "pumped", String.format("%,d", a.jobMb()), String.format("%,d", a.jobFe())), x + 7, y + 62, tw,
                a.jobMb() > 0 || a.jobFe() > 0 ? WmdUi.ORANGE : WmdUi.TEXT_MUTED);

        WmdUi.drawFit(g, font, Component.translatable(P + "points", a.pointsFound()), x + 7, y + 78, tw, WmdUi.TEXT_MUTED);
        WmdUi.well(g, x + 5, y + LIST_Y, PW - 10, ROW_H * ServiceArmBlockEntity.LIST + 2);
        List<String> l = a.foundList();
        int hover = rowAt(mouseX, mouseY);
        if (l.isEmpty()) WmdUi.drawFit(g, font, Component.translatable(P + "no_target"), x + 7, y + LIST_Y + 3, tw, WmdUi.TEXT_DIM);
        for (int i = 0; i < Math.min(l.size(), ServiceArmBlockEntity.LIST); i++) {
            int ry = y + LIST_Y + 2 + i * ROW_H;
            boolean pin = i == a.pinnedRow();
            if (pin) g.fill(x + 6, ry, x + PW - 6, ry + ROW_H, WmdUi.BLUE);
            else if (i == hover) g.fill(x + 6, ry, x + PW - 6, ry + ROW_H, WmdUi.CARD);
            WmdUi.drawFit(g, font, Component.literal((pin ? "> " : "  ") + l.get(i)), x + 7, ry + 1, tw, pin ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
        }
        WmdUi.drawFit(g, font, Component.translatable(a.pinned() ? P + "pinned" : P + "auto_pick"), x + 7, y + 136, tw, WmdUi.TEXT_DIM);
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= topPos && my < topPos + PH; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (arm() != null && inPanel(mx, my)) {
            int r = rowAt(mx, my);
            if (r >= 0 && button == 0) { send(ServiceArmBlockEntity.A_PIN_AT, r); return true; }
            if (super.mouseClicked(mx, my, button)) return true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPanel(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }
}
