package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BayScreen extends AbstractContainerScreen<BayMenu> {

    private static final int TEXT = WmdUi.TEXT, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD;
    private static final String[] LABEL = { "airframe", "motor", "guidance", "warhead" };

    private static final int SOCK_BUILT = 0xFF1F4D2E, SOCK_BUILT_EDGE = 0xFF35D25A, SOCK_OPEN = 0xFF2D3A4A, SOCK_LOCKED = 0xFF161B21, SOCK_EDGE = WmdUi.BLUE,
            SOCK_LOCKED_EDGE = WmdUi.DIVIDER, NEEDED = WmdUi.WARN, SEL = 0xFF2D3A4A;

    private static final int[][] GRID = {
            { -1, 0, 0 }, { 0, 1, 0 }, { 1, 2, 0 }, { 2, 3, 0 },
            { 3, 0, 1 }, { 4, 1, 1 }, { 5, 2, 1 }, { -2, 3, 1 },
            { 6, 0, 2 }, { 7, 1, 2 }, { 8, 2, 2 },
            { 9, 0, 3 }, { 10, 1, 3 },
            { 11, 0, 4 }, { 12, 1, 4 }, { 13, 2, 4 },
            { 14, 0, 5 }, { 15, 1, 5 } };
    private static final int GX = 12, GY = 20, GW = 36, GH = 11, GSTEP_X = 38, GSTEP_Y = 14;
    private int tab;
    private final WmdUi.Smooth progSmooth = new WmdUi.Smooth(8.0F), feSmooth = new WmdUi.Smooth(6.0F);

    public BayScreen(BayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 232 + BayMenu.ROW2;
        this.inventoryLabelY = BayMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, topPos + 22, "icon_lock", Component.translatable("gui.wmd.kit.tab.lock"),
                b -> minecraft.setScreen(new SecurityScreen(this, menu.getPos()))));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        WmdUi.divider(g, x + 8, y + 17, imageWidth - 16);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) WmdUi.slot(g, x + 8 + col * 18, y + BayMenu.INV_Y + row * 18, 0);
        for (int col = 0; col < 9; col++) WmdUi.slot(g, x + 8 + col * 18, y + BayMenu.HOTBAR_Y, 0);
        WmdHelp.button(g, x + 99, y + 4);
        tabButton(g, x + 112, y + 4, "build", tab == 0, mouseX, mouseY);
        tabButton(g, x + 144, y + 4, "line", tab == 1, mouseX, mouseY);
        if (tab == 1) { renderLine(g, x, y); return; }
        for (int i = 0; i < 4; i++) WmdUi.slot(g, x + BayMenu.PART_X + i * BayMenu.PART_STEP, y + BayMenu.SLOT_Y, WmdUi.BLUE);
        WmdUi.slot(g, x + BayMenu.OUT_X, y + BayMenu.SLOT_Y, WmdUi.ORANGE);
        WmdUi.slot(g, x + BayMenu.UPLINK_X, y + BayMenu.MOD_Y, WmdUi.BLUE);
        if (menu.getSlot(BayMenu.CAM_INDEX).hasItem()) WmdUi.slot(g, x + BayMenu.CAM_X, y + BayMenu.MOD_Y, WmdUi.ORANGE);
        WmdUi.slot(g, x + BayMenu.SEEKER_X, y + BayMenu.MOD_Y, WmdUi.BLUE);
        g.drawString(font, "▶", x + BayMenu.ARROW_X, y + BayMenu.SLOT_Y + 4, menu.running() ? WmdUi.ORANGE : WmdUi.TEXT_DIM, false);

        WmdUi.button(g, font, x + 12, y + 126 + BayMenu.ROW2, 152, 12, Component.empty(), WmdUi.B_NORMAL, WmdUi.over(mouseX, mouseY, x + 12, y + 126 + BayMenu.ROW2, 152, 12));

        int bx = x + 12, by = y + 62 + BayMenu.ROW2, bw = 152, bh = 8;
        WmdUi.progressBar(g, bx, by, bw - 6, bh, progSmooth.cycle(menu.progress()), menu.running());

        hEnergy(g, bx, y + 74 + BayMenu.ROW2, bw, 6, feSmooth.to(Math.min(1.0F, menu.energy() / (float) Balance.BAY_BUFFER_FE)));
    }

    static void hEnergy(GuiGraphics g, int x, int y, int w, int h, float frac) {
        WmdUi.well(g, x, y, w, h);
        int fill = Math.round(w * Math.max(0.0F, Math.min(1.0F, frac)));
        if (frac > 0 && fill == 0) fill = 1;
        for (int px = 0; px < fill; px++) {
            if (px % 4 == 3) continue;
            g.fill(x + px, y + 1, x + px + 1, y + h - 1, WmdUi.lerp(0xFF1E7F8E, WmdUi.CYAN, px / (float) w));
        }
        if (fill > 1) g.fill(x + fill - 1, y + 1, x + fill, y + h - 1, 0xFFBFF4FA);
    }

    private void tabButton(GuiGraphics g, int x, int y, String key, boolean on, int mouseX, int mouseY) {
        Component t = Component.translatable("gui.wmd.bay.tab." + key);
        if (on) {
            g.fill(x, y, x + 30, y + 12, WmdUi.BLUE);
            g.fill(x + 1, y + 1, x + 29, y + 11, SEL);
        } else {
            WmdUi.button(g, font, x, y, 30, 12, Component.empty(), WmdUi.B_NORMAL, WmdUi.over(mouseX, mouseY, x, y, 30, 12));
        }
        float sc = Math.min(1.0F, 26.0F / Math.max(1, font.width(t)));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + 15 - font.width(t) * sc / 2, y + 2.5F + (1.0F - sc) * 3.0F, 0);
        pose.scale(sc, sc, 1.0F);
        g.drawString(font, t, 0, 0, on ? TEXT : WmdUi.TEXT_MUTED, false);
        pose.popPose();
    }

    private int cellAt(double mx, double my) {
        for (int i = 0; i < GRID.length; i++) {
            int cx = leftPos + GX + GRID[i][1] * GSTEP_X, cy = topPos + GY + GRID[i][2] * GSTEP_Y;
            if (mx >= cx && mx < cx + GW && my >= cy && my < cy + GH) return i;
        }
        return -1;
    }

    private void renderLine(GuiGraphics g, int x, int y) {
        int built = menu.builtMask(), open = menu.placeableMask(), need = menu.neededStations();
        for (int[] c : GRID) {
            int cx = x + GX + c[1] * GSTEP_X, cy = y + GY + c[2] * GSTEP_Y, s = c[0];
            if (s == -2) continue;
            boolean isBuilt = s == -1 || (built >> s & 1) != 0, isOpen = s >= 0 && (open >> s & 1) != 0;
            int fill = isBuilt ? SOCK_BUILT : isOpen ? SOCK_OPEN : SOCK_LOCKED;
            g.fill(cx - 1, cy - 1, cx + GW + 1, cy + GH + 1, isOpen ? SOCK_EDGE : isBuilt ? SOCK_BUILT_EDGE : SOCK_LOCKED_EDGE);
            g.fill(cx, cy, cx + GW, cy + GH, fill);
            if (s >= 0 && !isBuilt && ((s == 3 && need >= 1) || (s == 4 && need >= 2) || (s == 5 && need >= 3))) g.fill(cx + GW - 5, cy + 1, cx + GW - 1, cy + 5, NEEDED);
            Component name = Component.translatable("gui.wmd.bay.sock." + (s < 0 ? "core" : BayBuilder.Socket.values()[s].name().toLowerCase()));
            float sc = Math.min(0.85F, (GW - 4.0F) / Math.max(1, font.width(name)));
            var pose = g.pose();
            pose.pushPose();
            pose.translate(cx + GW / 2.0F - font.width(name) * sc / 2, cy + GH / 2.0F - 4.0F * sc, 0);
            pose.scale(sc, sc, 1.0F);
            g.drawString(font, name, 0, 0, isBuilt ? GOOD : isOpen ? TEXT : WmdUi.TEXT_DIM, false);
            pose.popPose();
        }
    }

    private void lineLabels(GuiGraphics g) {
        int need = menu.neededStations();
        int lvl = menu.sizeLevel(), want = Math.max(1, Math.min(4, menu.airframeGrade()));
        fitted(g, Component.translatable("gui.wmd.bay.line.size", lvl, want), GX + 3 * GSTEP_X - 1, GY + GSTEP_Y + 4, lvl >= want ? GOOD : BAD, GW + 2);
        var r = menu.result();
        if (r != BayBuilder.Result.NONE) {
            boolean good = r == BayBuilder.Result.BUILT || r == BayBuilder.Result.REMOVED;
            fitted(g, Component.translatable("gui.wmd.bay.line.result." + r.name().toLowerCase(), menu.resultArg()), GX, 103, good ? GOOD : BAD, 152);
        }
        fitted(g, Component.translatable("gui.wmd.bay.line.table"), GX, 113, TEXT, 152);
        fitted(g, Component.translatable("gui.wmd.bay.line.needs", Component.translatable("gui.wmd.bay.line.needs." + need)), GX, 122, TEXT, 152);
        fitted(g, Component.translatable("gui.wmd.bay.line.note"), GX, 131, WmdUi.TEXT_MUTED, 152);
    }

    private List<Component> socketTip(int cell) {
        int s = GRID[cell][0];
        List<Component> tip = new ArrayList<>();
        if (s == -2) return tip;
        tip.add(Component.translatable("gui.wmd.bay.sock.full." + (s < 0 ? "core" : BayBuilder.Socket.values()[s].name().toLowerCase())));
        if (s < 0) { tip.add(Component.translatable("gui.wmd.bay.sock.tip.core")); return tip; }
        boolean isBuilt = (menu.builtMask() >> s & 1) != 0;
        if (isBuilt) tip.add(Component.translatable((menu.removableMask() >> s & 1) != 0 ? "gui.wmd.bay.sock.tip.remove" : menu.running() ? "gui.wmd.bay.sock.tip.busy" : "gui.wmd.bay.sock.tip.keep"));
        else if (menu.running()) tip.add(Component.translatable("gui.wmd.bay.sock.tip.busy"));
        else tip.add(Component.translatable((menu.placeableMask() >> s & 1) != 0 ? "gui.wmd.bay.sock.tip.build" : "gui.wmd.bay.sock.tip.locked"));
        return tip;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double lx = mx - leftPos, ly = my - topPos;
        if (tab == 0 && button == 0 && lx >= 12 && lx < 164 && ly >= 126 + BayMenu.ROW2 && ly < 138 + BayMenu.ROW2) { press(32); return true; }
        if (button == 0 && ly >= 4 && ly < 16) {
            int t = lx >= 112 && lx < 142 ? 0 : lx >= 144 && lx < 174 ? 1 : -1;
            if (t >= 0) { tab = t; menu.lineTab = t == 1; return true; }
        }
        if (tab == 1 && button == 0) {
            int c = cellAt(mx, my);
            if (c >= 0 && GRID[c][0] >= 0) {
                int s = GRID[c][0];
                boolean isBuilt = (menu.builtMask() >> s & 1) != 0;
                if (isBuilt) { if (hasShiftDown()) press(16 + s); }
                else press(s);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        float ts = Math.min(1.0F, 89.0F / Math.max(1, font.width(title)));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(8, 6 + (1.0F - ts) * 4.0F, 0);
        pose.scale(ts, ts, 1.0F);
        g.drawString(font, title, 0, 0, TEXT, false);
        pose.popPose();
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, WmdUi.TEXT_MUTED, false);
        if (tab == 1) { lineLabels(g); return; }
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.translate(BayMenu.PART_X + i * BayMenu.PART_STEP - 1, BayMenu.SLOT_Y + 21, 0);
            pose.scale(0.5F, 0.5F, 1.0F);
            g.drawString(font, Component.translatable("gui.wmd.bay.part." + LABEL[i]), 0, 0, WmdUi.TEXT_MUTED, false);
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(BayMenu.UPLINK_X - 1, BayMenu.MOD_Y + 21, 0);
        pose.scale(0.5F, 0.5F, 1.0F);
        g.drawString(font, Component.translatable("gui.wmd.bay.part.uplink"), 0, 0, WmdUi.TEXT_MUTED, false);
        pose.popPose();
        if (menu.getSlot(BayMenu.CAM_INDEX).hasItem()) {
            pose.pushPose();
            pose.translate(BayMenu.CAM_X - 1, BayMenu.MOD_Y + 21, 0);
            pose.scale(0.5F, 0.5F, 1.0F);
            g.drawString(font, Component.translatable("gui.wmd.bay.part.campod"), 0, 0, WmdUi.TEXT_MUTED, false);
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(BayMenu.SEEKER_X - 1, BayMenu.MOD_Y + 21, 0);
        pose.scale(0.5F, 0.5F, 1.0F);
        g.drawString(font, Component.translatable("gui.wmd.bay.part.seeker"), 0, 0, WmdUi.TEXT_MUTED, false);
        pose.popPose();
        fitted(g, Component.translatable("gui.wmd.bay.modules"), BayMenu.CAM_X + 24, BayMenu.MOD_Y + 5, WmdUi.TEXT_MUTED, 164 - BayMenu.CAM_X - 24);
        pose.pushPose();
        pose.translate(BayMenu.OUT_X - 2, BayMenu.SLOT_Y + 21, 0);
        pose.scale(0.5F, 0.5F, 1.0F);
        g.drawString(font, Component.translatable("gui.wmd.bay.part.missile"), 0, 0, WmdUi.TEXT_MUTED, false);
        pose.popPose();
        fitted(g, Component.translatable("gui.wmd.bay.mode", Component.translatable("gui.wmd.bay.mode." + menu.mode())), 16, 128 + BayMenu.ROW2, TEXT, 144);
        boolean running = menu.stage() != BayModuleBlockEntity.Stage.IDLE;
        Component status = running ? Component.translatable("gui.wmd.bay.stage." + menu.stage().name().toLowerCase())
                : Component.translatable((menu.flags() & BayModuleBlockEntity.FLAG_LINKED) == 0 ? "gui.wmd.bay.not_linked"
                        : (menu.flags() & BayModuleBlockEntity.FLAG_MODE) != 0 ? (menu.mode() != BayModuleBlockEntity.MODE_CRUISE && jetIn() ? "gui.wmd.bay.jet_needs_cruise"
                                : menu.mode() == 0 ? "gui.wmd.bay.t4_is_stages" : "gui.wmd.bay.mode_airframe." + menu.mode())
                        : (menu.flags() & BayModuleBlockEntity.FLAG_GRADE) != 0 ? "gui.wmd.bay.grade_too_high"
                        : (menu.flags() & BayModuleBlockEntity.FLAG_SIZE) != 0 ? "gui.wmd.bay.size_short"
                        : (menu.flags() & BayModuleBlockEntity.FLAG_NEEDS) != 0 ? "gui.wmd.bay.need_station_short"
                        : (menu.flags() & BayModuleBlockEntity.FLAG_RUNWAY) != 0 ? "gui.wmd.bay.runway_short"
                        : (menu.flags() & BayModuleBlockEntity.FLAG_OUT) != 0 ? "gui.wmd.bay.output_full"
                        : (menu.flags() & BayModuleBlockEntity.FLAG_READY) != 0 ? "gui.wmd.bay.stage.idle" : "gui.wmd.bay.waiting");
        boolean bad = !running && (menu.flags() & BayModuleBlockEntity.FLAG_READY) == 0;

        WmdUi.led(g, 12, 87 + BayMenu.ROW2, bad ? WmdUi.K_BAD : WmdUi.K_GOOD, running);
        fitted(g, status, 20, 86 + BayMenu.ROW2, bad ? BAD : GOOD, 144);
        fitted(g, Component.translatable("gui.wmd.bay.energy", menu.energy() / 1000, menu.needKfe()), 12, 97 + BayMenu.ROW2, WmdUi.TEXT_MUTED);

        if (menu.partsIn() >= 4) {
            fitted(g, Component.translatable("gui.wmd.bay.stats1", String.format("%.1f", menu.statWeight()), String.format("%,d", menu.statRange())), 12, 110 + BayMenu.ROW2, TEXT);
            fitted(g, Component.translatable(menu.mode() == BayModuleBlockEntity.MODE_CRUISE ? "gui.wmd.bay.stats2_cruise" : menu.mode() == BayModuleBlockEntity.MODE_SLBM ? "gui.wmd.bay.stats2_slbm" : "gui.wmd.bay.stats2",
                    String.format("%.1f", menu.statSpeed()), menu.statHealth(), String.format("%,d", menu.statFuel())), 12, 120 + BayMenu.ROW2, TEXT);
        } else {
            fitted(g, Component.translatable("gui.wmd.bay.stats_none"), 12, 110 + BayMenu.ROW2, WmdUi.TEXT_MUTED);
        }
    }

    private boolean jetIn() { return menu.slots.size() > 1 && menu.slots.get(1).getItem().getItem() instanceof MissilePartItem p && p.isJet(); }

    private void fitted(GuiGraphics g, Component text, int x, int y, int colour) { fitted(g, text, x, y, colour, 152); }

    private void fitted(GuiGraphics g, Component text, int x, int y, int colour, int width) {
        float sc = Math.min(1.0F, (float) width / Math.max(1, font.width(text)));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y + (1.0F - sc) * 4.0F, 0);
        pose.scale(sc, sc, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        pose.popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (tab == 1 && menu.getCarried().isEmpty()) {
            int c = cellAt(mouseX, mouseY);
            if (c >= 0) { List<Component> tip = socketTip(c); if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY); }
        }
    }
}
