package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class LabScreen extends AbstractContainerScreen<LabMenu> {

    private static final int TEXT = WmdUi.TEXT, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, DIM = WmdUi.TEXT_MUTED;

    private static final int SOCK_BUILT = 0xFF1F4D2E, SOCK_BUILT_EDGE = 0xFF35D25A, SOCK_OPEN = 0xFF2D3A4A, SOCK_LOCKED = 0xFF161B21, SOCK_EDGE = WmdUi.BLUE,
            SOCK_LOCKED_EDGE = WmdUi.DIVIDER, SEL = 0xFF2D3A4A;

    private static final int ROW_ON = 0xFF35D25A, ROW_OFF = 0xFFE0372D;

    private static final int[][] GRID = { { -1, 0, 0 }, { 0, 1, 0 }, { 1, 2, 0 }, { 2, 0, 1 }, { 3, 1, 1 }, { 4, 2, 1 } };
    private static final int GX = 12, GY = 26, GW = 46, GH = 22, GSTEP_X = 50, GSTEP_Y = 28;
    private int tab;
    private Button prev, next, start, autoBtn;

    private static final int AUTO_X = 116, AUTO_Y = 94, AUTO_W = 52, HW_X = 116, HW_Y = 114, HW_W = 52;
    private final WmdUi.Smooth progSmooth = new WmdUi.Smooth(8.0F), feSmooth = new WmdUi.Smooth(6.0F);

    public LabScreen(LabMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 234;
        this.inventoryLabelY = LabMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, topPos + 22, "icon_lock", Component.translatable("gui.wmd.kit.tab.lock"),
                b -> minecraft.setScreen(new SecurityScreen(this, menu.getPos()))));
        int x = leftPos, y = topPos;
        prev = addRenderableWidget(new WmdUi.KitButton(x + 8, y + 16, 14, 14, Component.literal("<"), b -> press(0)));
        next = addRenderableWidget(new WmdUi.KitButton(x + 114, y + 16, 14, 14, Component.literal(">"), b -> press(1)));
        start = addRenderableWidget(new WmdUi.KitButton(x + 134, y + 16, 34, 14, Component.translatable("gui.wmd.lab.start"), b -> press(2)));
        autoBtn = addRenderableWidget(new WmdUi.KitButton(x + AUTO_X, y + AUTO_Y, AUTO_W, 14, Component.translatable("gui.wmd.lab.auto.off"), b -> press(3)));
        showTab();
    }

    private void showTab() {
        boolean build = tab == 0;
        if (prev != null) { prev.visible = build; next.visible = build; start.visible = build; autoBtn.visible = build; }
        menu.tab = tab;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void tabButton(GuiGraphics g, int x, int y, String key, boolean on, int mouseX, int mouseY) {
        if (on) {
            g.fill(x, y, x + 30, y + 12, WmdUi.BLUE);
            g.fill(x + 1, y + 1, x + 29, y + 11, SEL);
        } else {
            WmdUi.button(g, font, x, y, 30, 12, Component.empty(), WmdUi.B_NORMAL, WmdUi.over(mouseX, mouseY, x, y, 30, 12));
        }
        Component t = Component.translatable("gui.wmd.lab.tab." + key);
        float sc = Math.min(1.0F, 26.0F / Math.max(1, font.width(t)));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + 15 - font.width(t) * sc / 2, y + 2.5F + (1.0F - sc) * 3.0F, 0);
        pose.scale(sc, sc, 1.0F);
        g.drawString(font, t, 0, 0, on ? TEXT : WmdUi.TEXT_MUTED, false);
        pose.popPose();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        if (autoBtn != null) autoBtn.setMessage(Component.translatable(menu.auto() ? "gui.wmd.lab.auto.on" : "gui.wmd.lab.auto.off"));
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        if (tab != 0) WmdUi.divider(g, x + 8, y + 17, imageWidth - 16);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) WmdUi.slot(g, x + 8 + col * 18, y + LabMenu.INV_Y + row * 18, 0);
        for (int col = 0; col < 9; col++) WmdUi.slot(g, x + 8 + col * 18, y + LabMenu.HOTBAR_Y, 0);
        WmdHelp.button(g, x + 67, y + 4);
        tabButton(g, x + 80, y + 4, "build", tab == 0, mouseX, mouseY);
        tabButton(g, x + 112, y + 4, "line", tab == 1, mouseX, mouseY);
        tabButton(g, x + 144, y + 4, "store", tab == 2, mouseX, mouseY);
        if (tab == 1) { renderLine(g, x, y); return; }
        if (tab == 2) {
            for (int i = 0; i < LabModuleBlockEntity.SLOTS; i++) WmdUi.slot(g, x + LabMenu.STORE_X + (i % 5) * 30, y + LabMenu.STORE_Y + (i / 5) * 30, WmdUi.ORANGE);
            return;
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) WmdUi.slot(g, x + LabMenu.GRID_X + col * 18, y + LabMenu.GRID_Y + row * 18, WmdUi.BLUE);
        int w = 100;
        BayScreen.hEnergy(g, x + 8, y + 100, w, 5, feSmooth.to((float) Math.min(1.0, menu.energy() / (double) Balance.LAB_BUFFER_FE)));
        WmdUi.progressBar(g, x + 8, y + 114, w, 5, progSmooth.cycle(menu.progress()), menu.running());

        WmdUi.well(g, x + HW_X, y + HW_Y, HW_W, 5);
        int fill = (int) Math.round(HW_W * Math.min(1.0, menu.heavyWater() / (double) Balance.LAB_HEAVY_WATER_TANK));
        if (fill > 0) g.fill(x + HW_X, y + HW_Y, x + HW_X + fill, y + HW_Y + 5, 0xFF9FC8F0);
    }

    private int cellAt(double mx, double my) {
        for (int i = 0; i < GRID.length; i++) {
            int cx = leftPos + GX + GRID[i][1] * GSTEP_X, cy = topPos + GY + GRID[i][2] * GSTEP_Y;
            if (mx >= cx && mx < cx + GW && my >= cy && my < cy + GH) return i;
        }
        return -1;
    }

    private static String sockName(int s) { return s < 0 ? "core" : LabBuilder.Socket.values()[s].name().toLowerCase(); }

    private void renderLine(GuiGraphics g, int x, int y) {
        int mask = menu.lineMask(), open = menu.placeableMask();
        for (int[] c : GRID) {
            int cx = x + GX + c[1] * GSTEP_X, cy = y + GY + c[2] * GSTEP_Y, s = c[0];
            boolean isBuilt = s < 0 || (mask >> LabBuilder.Socket.values()[s].kind.ordinal() & 1) != 0, isOpen = s >= 0 && (open >> s & 1) != 0;
            g.fill(cx - 1, cy - 1, cx + GW + 1, cy + GH + 1, isOpen ? SOCK_EDGE : isBuilt ? SOCK_BUILT_EDGE : SOCK_LOCKED_EDGE);
            g.fill(cx, cy, cx + GW, cy + GH, isBuilt ? SOCK_BUILT : isOpen ? SOCK_OPEN : SOCK_LOCKED);
            Component name = Component.translatable("gui.wmd.lab.sock." + sockName(s));
            float sc = Math.min(0.9F, (GW - 4.0F) / Math.max(1, font.width(name)));
            var pose = g.pose();
            pose.pushPose();
            pose.translate(cx + GW / 2.0F - font.width(name) * sc / 2, cy + GH / 2.0F - 4.0F * sc, 0);
            pose.scale(sc, sc, 1.0F);
            g.drawString(font, name, 0, 0, isBuilt ? GOOD : isOpen ? TEXT : WmdUi.TEXT_DIM, false);
            pose.popPose();
        }
    }

    private void lineLabels(GuiGraphics g) {
        var r = menu.result();
        if (r != LabBuilder.Result.NONE) {
            boolean good = r == LabBuilder.Result.BUILT || r == LabBuilder.Result.REMOVED;
            fitted(g, Component.translatable("gui.wmd.lab.line.result." + r.name().toLowerCase(), menu.resultArg()), GX, 86, good ? GOOD : BAD, 152);
        }
        fitted(g, Component.translatable("gui.wmd.lab.line.order"), GX, 100, TEXT, 152);
        fitted(g, Component.translatable("gui.wmd.lab.line.how"), GX, 110, TEXT, 152);
        fitted(g, Component.translatable("gui.wmd.lab.line.remove"), GX, 120, TEXT, 152);
        fitted(g, Component.translatable("gui.wmd.lab.line.where"), GX, 130, TEXT, 152);
    }

    private List<Component> socketTip(int cell) {
        int s = GRID[cell][0];
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("gui.wmd.lab.sock.full." + sockName(s)));
        if (s < 0) { tip.add(Component.translatable("gui.wmd.lab.sock.tip.core")); return tip; }
        boolean isBuilt = (menu.lineMask() >> LabBuilder.Socket.values()[s].kind.ordinal() & 1) != 0;
        if (isBuilt) tip.add(Component.translatable((menu.removableMask() >> s & 1) != 0 ? "gui.wmd.lab.sock.tip.remove" : menu.running() ? "gui.wmd.lab.sock.tip.busy" : "gui.wmd.lab.sock.tip.keep"));
        else if (menu.running()) tip.add(Component.translatable("gui.wmd.lab.sock.tip.busy"));
        else tip.add(Component.translatable((menu.placeableMask() >> s & 1) != 0 ? "gui.wmd.lab.sock.tip.build" : "gui.wmd.lab.sock.tip.locked"));
        return tip;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double lx = mx - leftPos, ly = my - topPos;
        if (button == 0 && ly >= 4 && ly < 16) {
            int t = lx >= 80 && lx < 110 ? 0 : lx >= 112 && lx < 142 ? 1 : lx >= 144 && lx < 174 ? 2 : -1;
            if (t >= 0) { tab = t; showTab(); return true; }
        }
        if (tab == 1 && button == 0) {
            int c = cellAt(mx, my);
            if (c >= 0 && GRID[c][0] >= 0) {
                int s = GRID[c][0];
                boolean isBuilt = (menu.lineMask() >> LabBuilder.Socket.values()[s].kind.ordinal() & 1) != 0;
                if (isBuilt) { if (hasShiftDown()) press(16 + s); }
                else press(8 + s);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        WmdUi.drawFit(g, font, title, 8, 5, 57, TEXT);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, WmdUi.TEXT_MUTED, false);
        if (tab == 1) { lineLabels(g); return; }
        if (tab == 2) {
            for (int i = 0; i < LabModuleBlockEntity.SLOTS; i++) g.drawString(font, Integer.toString(i + 1), LabMenu.STORE_X + (i % 5) * 30 + 6, LabMenu.STORE_Y + (i / 5) * 30 + 20, WmdUi.TEXT_DIM, false);
            g.drawString(font, Component.translatable("gui.wmd.lab.stored", menu.stored(), LabModuleBlockEntity.SLOTS), 8, 22, menu.stored() > 0 ? GOOD : DIM, false);
            fitted(g, Component.translatable("gui.wmd.lab.store.hint"), 8, 108, TEXT, 160);
            return;
        }
        g.drawCenteredString(font, Component.translatable("warhead.wmd." + menu.type().id), 71, 19, TEXT);
        int ty = 36;
        for (LabRecipes.Need n : LabRecipes.needs(menu.type())) {
            int have = menu.have(n);
            var pose = g.pose();
            pose.pushPose();
            pose.translate(66, ty, 0);
            pose.scale(0.75F, 0.75F, 1.0F);
            g.drawString(font, Math.min(have, n.count()) + "/" + n.count() + " " + Component.translatable("lab.wmd.need." + n.key()).getString(), 0, 0, have >= n.count() ? GOOD : BAD, false);
            pose.popPose();
            ty += 9;
        }
        var pose = g.pose();
        pose.pushPose();
        pose.translate(8, 92, 0);
        pose.scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, Component.translatable("gui.wmd.lab.power", menu.energy() / 1000, Balance.LAB_RUN_FE / 1000), 0, 0, DIM, false);
        pose.popPose();
        pose.pushPose();
        pose.translate(8, 107, 0);
        pose.scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, Component.translatable("gui.wmd.lab.stage." + menu.stage().name().toLowerCase()), 0, 0, DIM, false);
        pose.popPose();
        int mask = menu.lineMask();
        for (int i = 0; i < LabKind.values().length; i++) g.fill(8 + i * 20, 124, 8 + i * 20 + 17, 129, (mask >> i & 1) != 0 ? ROW_ON : ROW_OFF);
        pose.pushPose();
        pose.translate(HW_X, HW_Y - 7, 0);
        pose.scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, Component.translatable("gui.wmd.lab.heavy_water"), 0, 0, DIM, false);
        pose.popPose();
        int b = menu.blocker();

        if (menu.auto()) {
            int ab = menu.autoBlocker();
            boolean go = menu.autoRun() || ab == 0;
            WmdUi.led(g, 8, 134, go ? WmdUi.K_GOOD : WmdUi.K_WARN, menu.running());
            fitted(g, Component.translatable(menu.autoRun() ? "gui.wmd.lab.auto.running" : ab == 0 ? "gui.wmd.lab.auto.ready" : ab == 1 ? "gui.wmd.lab.auto.busy" : "message.wmd.lab.cannot." + ab),
                    16, 133, go ? GOOD : BAD, 152);
        } else {
            WmdUi.led(g, 8, 134, b == 0 ? WmdUi.K_GOOD : WmdUi.K_BAD, menu.running());
            fitted(g, Component.translatable(b == 0 ? "gui.wmd.lab.ready" : "message.wmd.lab.cannot." + b), 16, 133, b == 0 ? GOOD : BAD, 152);
        }
    }

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
        if (tab == 0 && menu.getCarried().isEmpty()) {
            if (WmdUi.over(mouseX, mouseY, leftPos + HW_X, topPos + HW_Y - 1, HW_W, 7))
                g.renderTooltip(font, Component.translatable("gui.wmd.lab.heavy_water.tip", menu.heavyWater(), Balance.LAB_HEAVY_WATER_TANK, Balance.LAB_AUTO_HEAVY_WATER), mouseX, mouseY);
            else if (autoBtn != null && autoBtn.isHovered())
                g.renderComponentTooltip(font, List.of(Component.translatable("gui.wmd.lab.auto.tip1"), Component.translatable("gui.wmd.lab.auto.tip2", Balance.LAB_AUTO_HEAVY_WATER)), mouseX, mouseY);
        }
        if (tab == 1 && menu.getCarried().isEmpty()) {
            int c = cellAt(mouseX, mouseY);
            if (c >= 0) g.renderComponentTooltip(font, socketTip(c), mouseX, mouseY);
        }
    }
}
