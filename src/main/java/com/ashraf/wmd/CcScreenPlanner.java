package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class CcScreenPlanner extends MachineScreen implements WmdUi.Kit {

    static final int PW = 184, PH = 236, BTN_Y = 64, BTN_H = 24, HINT_Y = 100;
    private static final String P = "gui.wmd.planner.";

    public CcScreenPlanner(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private CcPlannerBlockEntity be() { return menu.machine() instanceof CcPlannerBlockEntity c ? c : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return be() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        CcPlannerBlockEntity b = be();
        if (b != null) addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, b.getBlockPos())));
    }

    private List<CompoundTag> rows(int t) {
        List<CompoundTag> l = new ArrayList<>();
        CcPlannerBlockEntity b = be();
        if (b != null) for (Tag x : b.rows()) if (x instanceof CompoundTag c && c.getInt("t") == t) l.add(c);
        return l;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        CcPlannerBlockEntity b = be();
        if (b != null && button == 0 && WmdUi.over(mx, my, px() + 7, py() + BTN_Y, PW - 14, BTN_H)) {
            WmdUi.click();
            minecraft.setScreen(new PlannerMapScreen(this, b.getBlockPos()));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        if (WmdUi.over(mx, my, px(), py(), PW, PH)) return false;
        return super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        CcPlannerBlockEntity b = be();
        if (b == null) return;
        int x = px(), y = py();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        boolean on = b.isOnline();
        WmdUi.header(g, font, x, y, PW, new ItemStack(b.getBlockState().getBlock().asItem()), Component.translatable(b.kind().langKey()),
                Component.translatable(on ? "gui.wmd.cc.online" : "gui.wmd.cc.offline"), on ? WmdUi.K_GOOD : WmdUi.K_BAD, false);
        List<CompoundTag> vs = rows(1), rs = rows(2);
        int docked = 0, stuck = 0, low = 100;
        for (CompoundTag c : vs) { if (c.getInt("s") == MissileRobotEntity.IDLE) docked++; if (c.getInt("k") != 0) stuck++; low = Math.min(low, c.getInt("b")); }
        drawLeft(g, vs.isEmpty() ? Component.translatable(P + "no_robots") : Component.translatable(P + "robots", vs.size(), docked, vs.size() - docked, stuck, low),
                x + 7, y + 30, PW - 14, vs.isEmpty() || stuck > 0 ? WmdUi.WARN : WmdUi.TEXT);
        int done = 0, way = 0;
        for (CompoundTag c : rs) { int st = c.getInt("st"); if (st == Logistics.ST_DONE) done++; else if (st == Logistics.ST_ON_WAY) way++; }
        drawLeft(g, Component.translatable(P + "routes", rs.size(), done, way, rs.size() - done - way), x + 7, y + 44, PW - 14, WmdUi.TEXT);
        boolean hov = WmdUi.over(mouseX, mouseY, x + 7, y + BTN_Y, PW - 14, BTN_H);
        WmdUi.button(g, font, x + 7, y + BTN_Y, PW - 14, BTN_H, Component.translatable(P + "open_map"), WmdUi.B_NORMAL, hov);
        for (int i = 0; i < 4; i++) drawLeft(g, Component.translatable(P + "hint" + (i + 1), Balance.ROBOT_ZONE), x + 7, y + HINT_Y + i * 12, PW - 14, i == 0 ? WmdUi.BLUE_LIGHT : WmdUi.TEXT_MUTED);
    }

    private void drawLeft(GuiGraphics g, Component text, int x, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, x, y, colour, false); return; }
        float sc = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - sc) * 4.0F, 0.0F);
        g.pose().scale(sc, sc, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
