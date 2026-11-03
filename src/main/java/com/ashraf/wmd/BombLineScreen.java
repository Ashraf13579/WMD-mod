package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BombLineScreen extends AbstractContainerScreen<BombLineMenu> {

    private static final int TEXT = WmdUi.TEXT, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, DIM = WmdUi.TEXT_MUTED;
    private static final int SOCK_BUILT = 0xFF1F4D2E, SOCK_BUILT_EDGE = 0xFF35D25A, SOCK_OPEN = 0xFF2D3A4A, SOCK_LOCKED = 0xFF161B21, SOCK_EDGE = WmdUi.BLUE,
            SOCK_LOCKED_EDGE = WmdUi.DIVIDER, SEL = 0xFF2D3A4A, ROW_ON = 0xFF35D25A, ROW_OFF = 0xFFE0372D;

    private static final int[][] GRID = { { -1, 0, 0 }, { 0, 1, 0 }, { 1, 2, 0 }, { 2, 0, 1 }, { 3, 1, 1 }, { 4, 2, 1 } };
    private static final int GX = 12, GY = 26, GW = 46, GH = 22, GSTEP_X = 50, GSTEP_Y = 28;
    private static final int AUTO_X = 116, AUTO_Y = 108, AUTO_W = 52;
    private int tab;
    private Button prev, next, start, autoBtn;
    private final WmdUi.Smooth progSmooth = new WmdUi.Smooth(8.0F), feSmooth = new WmdUi.Smooth(6.0F);

    public BombLineScreen(BombLineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 234;
        this.inventoryLabelY = BombLineMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, topPos + 22, "icon_lock", Component.translatable("gui.wmd.kit.tab.lock"),
                b -> minecraft.setScreen(new SecurityScreen(this, menu.getPos()))));
        int x = leftPos, y = topPos;
        prev = addRenderableWidget(new WmdUi.KitButton(x + 8, y + 16, 14, 14, Component.literal("<"), b -> press(0)));
        next = addRenderableWidget(new WmdUi.KitButton(x + 114, y + 16, 14, 14, Component.literal(">"), b -> press(1)));
        start = addRenderableWidget(new WmdUi.KitButton(x + 134, y + 16, 34, 14, Component.translatable("gui.wmd.bomb_line.start"), b -> press(2)));
        autoBtn = addRenderableWidget(new WmdUi.KitButton(x + AUTO_X, y + AUTO_Y, AUTO_W, 14, Component.translatable("gui.wmd.bomb_line.auto.off"), b -> press(3)));
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
        Component t = Component.translatable("gui.wmd.bomb_line.tab." + key);
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
        if (autoBtn != null) autoBtn.setMessage(Component.translatable(menu.auto() ? "gui.wmd.bomb_line.auto.on" : "gui.wmd.bomb_line.auto.off"));
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        if (tab != 0) WmdUi.divider(g, x + 8, y + 17, imageWidth - 16);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) WmdUi.slot(g, x + 8 + col * 18, y + BombLineMenu.INV_Y + row * 18, 0);
        for (int col = 0; col < 9; col++) WmdUi.slot(g, x + 8 + col * 18, y + BombLineMenu.HOTBAR_Y, 0);
        WmdHelp.button(g, x + 67, y + 4);
        tabButton(g, x + 80, y + 4, "build", tab == 0, mouseX, mouseY);
        tabButton(g, x + 112, y + 4, "line", tab == 1, mouseX, mouseY);
        tabButton(g, x + 144, y + 4, "store", tab == 2, mouseX, mouseY);
        if (tab == 1) { renderLine(g, x, y); return; }
        if (tab == 2) {
            for (int i = 0; i < BombLineBlockEntity.PALLETS; i++) WmdUi.slot(g, x + BombLineMenu.palX(i), y + BombLineMenu.palY(i), WmdUi.ORANGE);
            WmdUi.slot(g, x + BombLineMenu.CRADLE_X, y + BombLineMenu.CRADLE_Y, WmdUi.ORANGE);
            return;
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) WmdUi.slot(g, x + BombLineMenu.GRID_X + col * 18, y + BombLineMenu.GRID_Y + row * 18, WmdUi.BLUE);
        int w = 100;
        BayScreen.hEnergy(g, x + 8, y + 104, w, 5, feSmooth.to((float) Math.min(1.0, menu.energy() / (double) Balance.BOMB_BUFFER_FE)));
        WmdUi.progressBar(g, x + 8, y + 118, w, 5, progSmooth.cycle(menu.progress()), menu.running());
    }

    private int cellAt(double mx, double my) {
        for (int i = 0; i < GRID.length; i++) {
            int cx = leftPos + GX + GRID[i][1] * GSTEP_X, cy = topPos + GY + GRID[i][2] * GSTEP_Y;
            if (mx >= cx && mx < cx + GW && my >= cy && my < cy + GH) return i;
        }
        return -1;
    }

    private static String sockName(int s) { return s < 0 ? "core" : BombLineBuilder.Socket.values()[s].name().toLowerCase(); }

    private boolean built(int s) { return s < 0 || (menu.lineMask() >> BombLineBuilder.Socket.values()[s].kind.ordinal() & 1) != 0; }

    private void renderLine(GuiGraphics g, int x, int y) {
        int open = menu.placeableMask();
        for (int[] c : GRID) {
            int cx = x + GX + c[1] * GSTEP_X, cy = y + GY + c[2] * GSTEP_Y, s = c[0];
            boolean isBuilt = built(s), isOpen = s >= 0 && (open >> s & 1) != 0;
            g.fill(cx - 1, cy - 1, cx + GW + 1, cy + GH + 1, isOpen ? SOCK_EDGE : isBuilt ? SOCK_BUILT_EDGE : SOCK_LOCKED_EDGE);
            g.fill(cx, cy, cx + GW, cy + GH, isBuilt ? SOCK_BUILT : isOpen ? SOCK_OPEN : SOCK_LOCKED);
            Component name = Component.translatable("gui.wmd.bomb_line.sock." + sockName(s));
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
            fitted(g, Component.translatable("gui.wmd.lab.line.result." + r.name().toLowerCase(), menu.resultArg()), GX, 86, good ? GOOD : BAD, 156);
        }
        fitted(g, Component.translatable("gui.wmd.bomb_line.line.order"), GX, 100, TEXT, 156);
        fitted(g, Component.translatable("gui.wmd.lab.line.how"), GX, 110, TEXT, 156);
        fitted(g, Component.translatable("gui.wmd.lab.line.remove"), GX, 120, TEXT, 156);
        fitted(g, Component.translatable("gui.wmd.bomb_line.line.hangar"), GX, 130, TEXT, 156);
    }

    private List<Component> socketTip(int cell) {
        int s = GRID[cell][0];
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("block.wmd.bomb_line_" + (s < 0 ? "core" : BombLineBuilder.Socket.values()[s].kind.id())));
        if (s < 0) { tip.add(Component.translatable("gui.wmd.bomb_line.sock.tip.core")); return tip; }
        if (built(s)) tip.add(Component.translatable((menu.removableMask() >> s & 1) != 0 ? "gui.wmd.lab.sock.tip.remove" : menu.running() ? "gui.wmd.lab.sock.tip.busy" : "gui.wmd.lab.sock.tip.keep"));
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
                if (built(s)) { if (hasShiftDown()) press(16 + s); }
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
            fitted(g, Component.translatable("gui.wmd.bomb_line.pallets", menu.onPallets()), 8, 24, menu.onPallets() > 0 ? GOOD : DIM, 96);
            fitted(g, Component.translatable("gui.wmd.bomb_line.cradle", menu.onCradle()), BombLineMenu.CRADLE_X - 16, 24, menu.onCradle() > 0 ? GOOD : DIM, 56);
            fitted(g, Component.translatable("gui.wmd.bomb_line.store.hint"), 8, 122, TEXT, 160);
            return;
        }
        BombType t = menu.type();
        Component name = Component.translatable("item.wmd." + t.id);
        float sc = Math.min(1.0F, 86.0F / Math.max(1, font.width(name)));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(71 - font.width(name) * sc / 2, 19 + (1.0F - sc) * 4.0F, 0);
        pose.scale(sc, sc, 1.0F);
        g.drawString(font, name, 0, 0, TEXT, false);
        pose.popPose();
        int ty = 36;
        fitted(g, Component.translatable("gui.wmd.bomb_line.blast", Component.translatable("warhead.wmd." + t.blast.id)), 66, ty, DIM, 102, 0.75F);
        ty += 9;
        for (BombLineRecipes.Need n : BombLineRecipes.needs(t)) {
            int have = menu.have(n);
            fitted(g, Component.literal(Math.min(have, n.count()) + "/" + n.count() + " ").append(n.shown().getDescription()), 66, ty, have >= n.count() ? GOOD : BAD, 102, 0.75F);
            ty += 9;
        }
        int nap = BombLineRecipes.naphtha(t);
        if (nap > 0) {
            int in = Math.max(0, menu.naphtha());
            fitted(g, Component.translatable("gui.wmd.bomb_line.naphtha", Math.min(in, nap), nap), 66, ty, in >= nap ? GOOD : BAD, 102, 0.75F);
        }
        small(g, Component.translatable("gui.wmd.bomb_line.power", menu.energy() / 1000, BombLineRecipes.runFe(t) / 1000), 8, 96, DIM);
        small(g, Component.translatable("gui.wmd.bomb_line.stage." + menu.stage().name().toLowerCase()), 8, 111, DIM);
        int mask = menu.lineMask();
        for (int i = 0; i < BombLineKind.values().length; i++) {
            boolean need = BombLineKind.values()[i] != BombLineKind.CRADLE || t.heavy;
            g.fill(8 + i * 17, 127, 8 + i * 17 + 15, 131, (mask >> i & 1) != 0 ? ROW_ON : need ? ROW_OFF : WmdUi.DIVIDER);
        }
        int b = menu.blocker();
        WmdUi.led(g, 8, 136, b == 0 || menu.running() ? WmdUi.K_GOOD : menu.auto() ? WmdUi.K_WARN : WmdUi.K_BAD, menu.running());
        Component status = menu.running() ? Component.translatable("gui.wmd.bomb_line.running") : b == 0 ? Component.translatable(menu.auto() ? "gui.wmd.bomb_line.auto.ready" : "gui.wmd.bomb_line.ready")
                : Component.translatable("message.wmd.bomb_line.cannot." + b);
        fitted(g, status, 16, 135, b == 0 || menu.running() ? GOOD : BAD, 152);
    }

    private void small(GuiGraphics g, Component c, int x, int y, int colour) { fitted(g, c, x, y, colour, 104, 0.75F); }

    private void fitted(GuiGraphics g, Component text, int x, int y, int colour, int width) { fitted(g, text, x, y, colour, width, 1.0F); }

    private void fitted(GuiGraphics g, Component text, int x, int y, int colour, int width, float max) {
        float sc = Math.min(max, (float) width / Math.max(1, font.width(text)));
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
        if (tab == 0 && menu.getCarried().isEmpty() && autoBtn != null && autoBtn.isHovered())
            g.renderComponentTooltip(font, List.of(Component.translatable("gui.wmd.bomb_line.auto.tip1"), Component.translatable("gui.wmd.bomb_line.auto.tip2")), mouseX, mouseY);
        if (tab == 1 && menu.getCarried().isEmpty()) {
            int c = cellAt(mouseX, mouseY);
            if (c >= 0) g.renderComponentTooltip(font, socketTip(c), mouseX, mouseY);
        }
    }
}
