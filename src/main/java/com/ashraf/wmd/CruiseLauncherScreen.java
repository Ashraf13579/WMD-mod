package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class CruiseLauncherScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 150, PH = 196;
    private static final int ROWS = Balance.CRUISE_WAYPOINTS + 1, ROW_Y = 27, ROW_STEP = 15, BX = 30, BZ = 90, BW = 52;
    static final String P = "gui.wmd.cruise.";

    private final EditBox[] xBox = new EditBox[ROWS], zBox = new EditBox[ROWS];
    private final String[] xText = new String[ROWS], zText = new String[ROWS];
    private boolean filled;

    public CruiseLauncherScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        java.util.Arrays.fill(xText, "");
        java.util.Arrays.fill(zText, "");
    }

    private CruiseLauncherBlockEntity launcher() { return menu.machine() instanceof CruiseLauncherBlockEntity l ? l : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return launcher() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        CruiseLauncherBlockEntity l = launcher();
        if (l == null) return;
        int x = px(), y = topPos;
        if (!filled) {
            for (int i = 0; i < l.waypoints(); i++) { xText[i] = Integer.toString(l.wpX(i)); zText[i] = Integer.toString(l.wpZ(i)); }
            if (l.hasTarget()) { xText[ROWS - 1] = Integer.toString(l.targetX()); zText[ROWS - 1] = Integer.toString(l.targetZ()); }
            filled = true;
        }
        for (int i = 0; i < ROWS; i++) {
            xBox[i] = box(x + BX, y + ROW_Y + i * ROW_STEP, xText[i]);
            zBox[i] = box(x + BZ, y + ROW_Y + i * ROW_STEP, zText[i]);
        }
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, l.getBlockPos())));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 105, 46, 14, Component.translatable(P + "set"), b -> setRoute()));
        addRenderableWidget(new WmdUi.KitButton(x + 55, y + 105, 50, 14, Component.translatable(P + "designated"), b -> send(CruiseLauncherBlockEntity.A_DESIGNATED, new int[0])));
        addRenderableWidget(new WmdUi.KitButton(x + 108, y + 105, 36, 14, Component.translatable(P + "clear"), b -> clearWaypoints()));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 178, 44, 14, Component.translatable(P + "fire1"), b -> send(CruiseLauncherBlockEntity.A_FIRE, new int[0]), true));
        addRenderableWidget(new WmdUi.KitButton(x + 54, y + 178, 46, 14, Component.translatable(P + "salvo"), b -> send(CruiseLauncherBlockEntity.A_SALVO, new int[0]), true));
        addRenderableWidget(new WmdUi.KitButton(x + 104, y + 178, 40, 14, Component.translatable(P + "abort"), b -> send(CruiseLauncherBlockEntity.A_ABORT, new int[0])));
    }

    private EditBox box(int x, int y, String value) {
        EditBox b = new EditBox(font, x, y, BW, 13, Component.empty());
        b.setMaxLength(9);
        b.setFilter(s -> s.matches("-?\\d*"));
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void rebuildWidgets() {
        for (int i = 0; i < ROWS; i++) if (xBox[i] != null) { xText[i] = xBox[i].getValue(); zText[i] = zBox[i].getValue(); }
        super.rebuildWidgets();
    }

    private static Integer parse(EditBox b) {
        try { return b == null || b.getValue().isEmpty() || b.getValue().equals("-") ? null : Integer.parseInt(b.getValue()); } catch (NumberFormatException e) { return null; }
    }

    private void setRoute() {
        Integer tx = parse(xBox[ROWS - 1]), tz = parse(zBox[ROWS - 1]);
        if (tx == null || tz == null) { if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(P + "need_target"), true); return; }
        int[] d = new int[3 + 2 * Balance.CRUISE_WAYPOINTS];
        int n = 0;
        for (int i = 0; i < ROWS - 1; i++) {
            Integer wx = parse(xBox[i]), wz = parse(zBox[i]);
            if (wx == null || wz == null) continue;
            d[1 + 2 * n] = wx; d[2 + 2 * n] = wz; n++;
        }
        d[0] = n;
        d[1 + 2 * n] = tx; d[2 + 2 * n] = tz;
        send(CruiseLauncherBlockEntity.A_ROUTE, java.util.Arrays.copyOf(d, 3 + 2 * n));
    }

    private void clearWaypoints() {
        for (int i = 0; i < ROWS - 1; i++) { xBox[i].setValue(""); zBox[i].setValue(""); }
        send(CruiseLauncherBlockEntity.A_CLEAR_WP, new int[0]);
    }

    private void send(int action, int[] data) {
        CruiseLauncherBlockEntity l = launcher();
        if (l == null) return;
        PacketDistributor.sendToServer(new CruisePayload(l.getBlockPos(), action, data));
    }

    private int count(int i) { return menu.slots.size() > i ? menu.slots.get(i).getItem().getCount() : 0; }

    private static int statusKind(int st) {
        return switch (st) {
            case CruiseLauncherBlockEntity.S_READY, CruiseLauncherBlockEntity.S_FIRING -> WmdUi.K_GOOD;
            case CruiseLauncherBlockEntity.S_RAISING, CruiseLauncherBlockEntity.S_LOWERING -> WmdUi.K_WARN;
            case CruiseLauncherBlockEntity.S_NO_TARGET -> WmdUi.K_IDLE;
            default -> WmdUi.K_BAD;
        };
    }

    private static int statusColour(int st) {
        return switch (statusKind(st)) { case WmdUi.K_GOOD -> WmdUi.GOOD; case WmdUi.K_WARN -> WmdUi.WARN; case WmdUi.K_IDLE -> WmdUi.TEXT_MUTED; default -> WmdUi.BAD; };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        CruiseLauncherBlockEntity l = launcher();
        if (l == null) return;
        int x = px(), y = topPos;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        WmdUi.drawFit(g, font, Component.translatable(P + "panel"), x + 7, y + 6, PW - 14, WmdUi.TEXT);
        WmdUi.divider(g, x + 6, y + 15, PW - 12);
        g.drawCenteredString(font, "X", x + BX + BW / 2, y + 18, WmdUi.TEXT_MUTED);
        g.drawCenteredString(font, "Z", x + BZ + BW / 2, y + 18, WmdUi.TEXT_MUTED);
        for (int i = 0; i < ROWS; i++) {
            Component lab = i < ROWS - 1 ? Component.translatable(P + "wp", i + 1) : Component.translatable(P + "tgt");
            WmdUi.drawFit(g, font, lab, x + 6, y + ROW_Y + i * ROW_STEP + 3, BX - 8, i < ROWS - 1 ? WmdUi.TEXT_MUTED : WmdUi.ORANGE);
        }
        WmdUi.divider(g, x + 6, y + ROW_Y + ROWS * ROW_STEP - 1, PW - 12);

        WmdUi.well(g, x + 5, y + 124, PW - 10, 33);
        int ly = y + 126;
        if (l.hasTarget()) {
            boolean far = l.nextRange() > 0 && l.routeLength() > l.nextRange();
            WmdUi.drawFit(g, font, Component.translatable(P + "route", String.format("%,d", l.routeLength()), l.nextRange() > 0 ? String.format("%,d", l.nextRange()) : "-"), x + 7, ly, PW - 14, far ? WmdUi.BAD : WmdUi.TEXT);
            int secs = (int) Math.round(l.routeLength() / (Balance.CRUISE_SPEED * 20.0));
            WmdUi.drawFit(g, font, Component.translatable(P + "eta", secs / 60, String.format("%02d", secs % 60), l.waypoints()), x + 7, ly + 10, PW - 14, WmdUi.TEXT);
        } else WmdUi.drawFit(g, font, Component.translatable(P + "no_route"), x + 7, ly, PW - 14, WmdUi.TEXT_MUTED);
        int missiles = 0;
        for (int i = 0; i < CruiseLauncherBlockEntity.CANISTERS; i++) if (count(i) > 0) missiles++;
        WmdUi.drawFit(g, font, Component.translatable(P + "load", missiles, CruiseLauncherBlockEntity.CANISTERS, count(CruiseLauncherBlockEntity.CANISTERS)), x + 7, ly + 20, PW - 14,
                missiles > 0 && count(CruiseLauncherBlockEntity.CANISTERS) >= Balance.CRUISE_FUEL_CANISTERS ? WmdUi.TEXT : WmdUi.WARN);

        int st = l.launcherStatus();
        boolean busy = st == CruiseLauncherBlockEntity.S_RAISING || st == CruiseLauncherBlockEntity.S_FIRING || st == CruiseLauncherBlockEntity.S_LOWERING;
        WmdUi.led(g, x + 8, y + 163, statusKind(st), busy);
        Component sl = l.salvoLeft() > 0 && busy ? Component.translatable(P + "st." + st).append(Component.translatable(P + "st_left", l.salvoLeft())) : Component.translatable(P + "st." + st);
        WmdUi.drawFit(g, font, sl, x + 17, y + 162, PW - 24, statusColour(st));
        WmdUi.divider(g, x + 6, y + 174, PW - 12);
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= topPos && my < topPos + PH; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (launcher() != null && inPanel(mx, my)) {
            if (super.mouseClicked(mx, my, button)) return true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPanel(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != 256) {
            for (int i = 0; i < ROWS; i++)
                for (EditBox box : new EditBox[] { xBox[i], zBox[i] })
                    if (box != null && box.isFocused() && (box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput())) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
