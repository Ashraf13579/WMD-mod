package com.ashraf.wmd;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class NavalPanelScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 150, LINE_H = 10, ROW_H = 16, TOP = 20;

    public NavalPanelScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private NavalPanel panel() { return menu.machine() instanceof NavalPanel p ? p : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return panel() != null ? PW : 0; }

    private int linesY() { return topPos + TOP; }

    private int rowsY(NavalPanel p) { return linesY() + p.panelLines().size() * LINE_H + 4; }

    private int ph(NavalPanel p) {
        GuiMachine m = menu.machine();
        int n = 1 + (m != null && m.guiPortMap() != null ? 1 : 0) + (m != null && !m.guiInfo().isEmpty() ? 1 : 0);
        int need = TOP + p.panelLines().size() * LINE_H + 4 + p.panelRows().size() * (ROW_H + 2) + 4;
        return Math.max(need, 22 + n * 24 + 2);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        NavalPanel p = panel();
        if (p == null) return;
        int x = px(), y = topPos;
        WmdUi.panel(g, x, y, PW, ph(p));
        drawSelectedTab(g);
        g.drawString(font, Component.translatable(p.panelTitle()), x + 7, y + 6, WmdUi.TEXT, false);
        Component chip = p.panelChip();
        if (!chip.getString().isEmpty()) WmdUi.chip(g, font, x + PW - 6 - Math.min(44, font.width(chip) + 10), y + 5, chip, p.panelChipKind(), p.panelChipKind() == WmdUi.K_BAD);
        WmdUi.divider(g, x + 6, y + 15, PW - 12);
        List<Component> lines = p.panelLines();
        for (int i = 0; i < lines.size(); i++) WmdUi.drawFit(g, font, lines.get(i), x + 7, linesY() + i * LINE_H, PW - 14, i == 0 ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
        List<Component> rows = p.panelRows();
        int ry0 = rowsY(p);
        for (int i = 0; i < rows.size(); i++) {
            int ry = ry0 + i * (ROW_H + 2);
            boolean hover = mouseX >= x + 5 && mouseX < x + PW - 5 && mouseY >= ry && mouseY < ry + ROW_H;
            int style = !p.panelEnabled(i) ? WmdUi.B_DISABLED : p.panelDanger(i) ? WmdUi.B_DANGER : WmdUi.B_NORMAL;
            if (i == p.panelPicked()) { g.fill(x + 4, ry - 1, x + PW - 4, ry + ROW_H + 1, WmdUi.BLUE); }
            WmdUi.button(g, font, x + 5, ry, PW - 10, ROW_H, rows.get(i), style, hover && style != WmdUi.B_DISABLED);
        }
    }

    private boolean inPanel(double mx, double my) {
        NavalPanel p = panel();
        return p != null && mx >= px() && mx < px() + PW && my >= topPos && my < topPos + ph(p);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        NavalPanel p = panel();
        if (p != null && inPanel(mx, my)) {
            int x = px(), ry0 = rowsY(p);
            for (int i = 0; i < p.panelRows().size(); i++) {
                int ry = ry0 + i * (ROW_H + 2);
                if (mx >= x + 5 && mx < x + PW - 5 && my >= ry && my < ry + ROW_H) {
                    if (!p.panelEnabled(i)) { WmdUi.deny(); break; }
                    if (p.panelDanger(i)) WmdUi.confirm(); else WmdUi.click();
                    if (p.panelClient(i)) break;
                    PacketDistributor.sendToServer(new SetMachineModePayload(((net.minecraft.world.level.block.entity.BlockEntity) p).getBlockPos(), i));
                    break;
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPanel(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }
}
