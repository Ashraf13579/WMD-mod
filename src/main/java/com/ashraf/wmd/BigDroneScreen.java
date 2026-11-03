package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class BigDroneScreen extends AbstractContainerScreen<BigDroneMenu> {

    private static final int ROWS = DroneMission.WAYPOINTS + 1, ROW_Y = 60, ROW_STEP = 12, BX = 28, BZ = 74, BW = 42, SIDE_X = 120, SIDE_W = 48;

    private static final int WELL_Y = ROW_Y + ROWS * ROW_STEP + 3, BTN_Y = WELL_Y + 23;
    static final String P = "gui.wmd.big_drone.";

    private final EditBox[] xBox = new EditBox[ROWS], zBox = new EditBox[ROWS];
    private final String[] xText = new String[ROWS], zText = new String[ROWS];
    private boolean filled;

    public BigDroneScreen(BigDroneMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = BigDroneMenu.HOTBAR_Y + 24;
        this.inventoryLabelY = BigDroneMenu.INV_Y - 11;
        java.util.Arrays.fill(xText, "");
        java.util.Arrays.fill(zText, "");
    }

    private BigDroneSite site() { return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.getPos()) instanceof BigDroneSite s ? s : null; }

    @Override
    protected void init() {
        super.init();
        BigDroneSite s = site();
        if (!filled && s != null) {
            DroneMission m = s.mission();
            for (int i = 0; i < m.xs().length && i < ROWS - 1; i++) { xText[i] = Integer.toString(m.xs()[i]); zText[i] = Integer.toString(m.zs()[i]); }
            if (m.hasTarget()) { xText[ROWS - 1] = Integer.toString(m.tx()); zText[ROWS - 1] = Integer.toString(m.tz()); }
            filled = true;
        }
        int x = leftPos, y = topPos;
        for (int i = 0; i < ROWS; i++) {
            xBox[i] = box(x + BX, y + ROW_Y + i * ROW_STEP, xText[i]);
            zBox[i] = box(x + BZ, y + ROW_Y + i * ROW_STEP, zText[i]);
        }
        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, topPos + 22, "icon_lock", Component.translatable("gui.wmd.kit.tab.lock"),
                b -> minecraft.setScreen(new SecurityScreen(this, menu.getPos()))));
        addRenderableWidget(new WmdUi.KitButton(x + SIDE_X, y + ROW_Y, SIDE_W, 12, Component.translatable(P + "set"), b -> setRoute()));
        addRenderableWidget(new WmdUi.KitButton(x + SIDE_X, y + ROW_Y + 15, SIDE_W, 12, Component.translatable(P + "designated"), b -> press(BigDroneSite.A_DESIGNATED)));
        addRenderableWidget(new WmdUi.KitButton(x + SIDE_X, y + ROW_Y + 30, SIDE_W, 12, Component.translatable(P + "clear"), b -> clearWaypoints()));
        addRenderableWidget(new WmdUi.KitButton(x + 8, y + BTN_Y, 52, 13, Component.translatable(P + "launch"), b -> press(BigDroneSite.A_LAUNCH), true));
        addRenderableWidget(new WmdUi.KitButton(x + 64, y + BTN_Y, 50, 13, Component.translatable(P + "recall"), b -> press(BigDroneSite.A_RECALL)));
        addRenderableWidget(new WmdUi.KitButton(x + 118, y + BTN_Y, 50, 13, Component.translatable(P + "watch"), b -> press(BigDroneSite.A_WATCH)));
    }

    private EditBox box(int x, int y, String value) {
        EditBox b = new EditBox(font, x, y, BW, 11, Component.empty());
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
        int[] d = new int[3 + 2 * DroneMission.WAYPOINTS];
        int n = 0;
        for (int i = 0; i < ROWS - 1; i++) {
            Integer wx = parse(xBox[i]), wz = parse(zBox[i]);
            if (wx == null || wz == null) continue;
            d[1 + 2 * n] = wx; d[2 + 2 * n] = wz; n++;
        }
        d[0] = n;
        d[1 + 2 * n] = tx; d[2 + 2 * n] = tz;
        PacketDistributor.sendToServer(new BigDronePayload(menu.getPos(), java.util.Arrays.copyOf(d, 3 + 2 * n)));
    }

    private void clearWaypoints() {
        for (int i = 0; i < ROWS - 1; i++) { xBox[i].setValue(""); zBox[i].setValue(""); }
        Integer tx = parse(xBox[ROWS - 1]), tz = parse(zBox[ROWS - 1]);
        if (tx != null && tz != null) PacketDistributor.sendToServer(new BigDronePayload(menu.getPos(), new int[] { 0, tx, tz }));
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private static int statusKind(int st) {
        return switch (st) {
            case BigDroneSite.S_READY, BigDroneSite.S_OUT -> WmdUi.K_GOOD;
            case BigDroneSite.S_NO_TARGET, BigDroneSite.S_EMPTY -> WmdUi.K_IDLE;
            case BigDroneSite.S_FLIGHT_T3 -> WmdUi.K_WARN;
            default -> WmdUi.K_BAD;
        };
    }

    private static int statusColour(int st) {
        return switch (statusKind(st)) { case WmdUi.K_GOOD -> WmdUi.GOOD; case WmdUi.K_WARN -> WmdUi.WARN; case WmdUi.K_IDLE -> WmdUi.TEXT_MUTED; default -> WmdUi.BAD; };
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        WmdUi.divider(g, x + 8, y + 15, imageWidth - 16);
        WmdHelp.button(g, x + imageWidth - 18, y + 4);
        for (int i = 0; i < menu.droneSlotCount(); i++) WmdUi.slot(g, x + BigDroneMenu.droneX(i), y + BigDroneMenu.SLOT_Y, WmdUi.BLUE);
        for (int i = 0; i < menu.bombSlotCount(); i++) WmdUi.slot(g, x + BigDroneMenu.bombX(i), y + BigDroneMenu.SLOT_Y, WmdUi.BLUE);
        WmdUi.divider(g, x + 8, y + 49, imageWidth - 16);
        WmdUi.divider(g, x + 8, y + WELL_Y - 2, imageWidth - 16);
        WmdUi.well(g, x + 8, y + WELL_Y, imageWidth - 16, 20);
        WmdUi.led(g, x + 10, y + WELL_Y + 12, statusKind(menu.status()), menu.status() == BigDroneSite.S_OUT);
        WmdUi.divider(g, x + 8, y + BTN_Y + 16, imageWidth - 16);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) WmdUi.slot(g, x + 8 + col * 18, y + BigDroneMenu.INV_Y + row * 18, 0);
        for (int col = 0; col < 9; col++) WmdUi.slot(g, x + 8 + col * 18, y + BigDroneMenu.HOTBAR_Y, 0);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        WmdUi.drawFit(g, font, title, 8, 5, 140, WmdUi.TEXT);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, WmdUi.TEXT_MUTED, false);
        small(g, Component.translatable(P + "drone"), 8, 19, WmdUi.TEXT_MUTED, 54);
        small(g, Component.translatable(menu.hangar() ? P + "pylons" : P + "bombs"), 66, 19, WmdUi.TEXT_MUTED, 102);
        small(g, Component.translatable(P + "route_head"), 8, 52, WmdUi.TEXT, 20);
        g.drawCenteredString(font, "X", BX + BW / 2, 51, WmdUi.TEXT_MUTED);
        g.drawCenteredString(font, "Z", BZ + BW / 2, 51, WmdUi.TEXT_MUTED);
        for (int i = 0; i < ROWS; i++) {
            Component lab = i < ROWS - 1 ? Component.translatable(P + "wp", i + 1) : Component.translatable(P + "tgt");
            small(g, lab, 8, ROW_Y + i * ROW_STEP + 2, i < ROWS - 1 ? WmdUi.TEXT_MUTED : WmdUi.ORANGE, 20);
        }

        BigDroneSite s = site();
        DroneMission m = s == null ? DroneMission.NONE : s.mission();
        DroneType t = s == null ? null : DroneType.of(s.droneSlots().getStackInSlot(0));
        int range = t == DroneType.T4 ? Balance.DRONE_T4_RANGE : Balance.DRONE_T3_RANGE;
        Component route;
        int rc = WmdUi.TEXT;
        if (!m.hasTarget()) { route = Component.translatable(P + "no_route"); rc = WmdUi.TEXT_MUTED; }
        else {

            net.minecraft.world.phys.Vec3 at = ShipFrame.drawnWorld(minecraft.level, menu.getPos(), net.minecraft.world.phys.Vec3.atCenterOf(menu.getPos()), Float.NaN);
            int len = m.length(at.x, at.z);
            int direct = (int) Math.round(Math.hypot(m.tx() + 0.5 - at.x, m.tz() + 0.5 - at.z));
            route = Component.translatable(P + "route", String.format("%,d", len), String.format("%,d", direct), String.format("%,d", range));
            if (direct > range) rc = WmdUi.BAD;
        }
        small(g, route, 10, WELL_Y + 2, rc, imageWidth - 22);
        int st = menu.status();
        Component sl = st == BigDroneSite.S_OUT ? Component.translatable(P + "st." + st, menu.outId()) : Component.translatable(P + "st." + st);
        if (!menu.hangar() && st != BigDroneSite.S_OUT) sl = sl.copy().append(Component.translatable(P + "power", menu.energy() / 1000, Balance.CATAPULT_BUFFER_FE / 1000));
        if (menu.hangar() && st != BigDroneSite.S_OUT) sl = sl.copy().append(Component.translatable(menu.runway() ? P + "runway_ok" : P + "runway_no"));
        small(g, sl, 18, WELL_Y + 11, statusColour(st), imageWidth - 30);
    }

    private void small(GuiGraphics g, Component c, int x, int y, int colour, int width) {
        float sc = Math.min(0.75F, (float) width / Math.max(1, font.width(c)));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y + (1.0F - sc) * 4.0F, 0);
        pose.scale(sc, sc, 1.0F);
        g.drawString(font, c, 0, 0, colour, false);
        pose.popPose();
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
