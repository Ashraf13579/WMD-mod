package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class CoastalBatteryScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 150, PH = 170, BX = 30, BZ = 90, BW = 52;
    static final String P = "gui.wmd.coastal.";

    private EditBox xBox, zBox;
    private String xText = "", zText = "";
    private boolean filled;

    public CoastalBatteryScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private CoastalBatteryBlockEntity battery() { return menu.machine() instanceof CoastalBatteryBlockEntity b ? b : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return battery() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        CoastalBatteryBlockEntity b = battery();
        if (b == null) return;
        int x = px(), y = topPos;
        if (!filled) {
            if (b.typed()) { xText = Integer.toString(b.typedX()); zText = Integer.toString(b.typedZ()); }
            filled = true;
        }
        xBox = box(x + BX, y + 49, xText);
        zBox = box(x + BZ, y + 49, zText);
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, b.getBlockPos())));
        addActionTab("icon_freq", Component.translatable("gui.wmd.kit.tab.freq"), () -> minecraft.setScreen(new FrequencyScreen(this,
                () -> battery() != null ? battery().frequency() : 1, f -> send(CoastalBatteryBlockEntity.A_FREQ, new int[] { f }))));
        modeButton = addRenderableWidget(new WmdUi.KitButton(x + 6, y + 19, PW - 12, 14, modeLabel(b), w -> {
            CoastalBatteryBlockEntity c = battery();
            if (c == null) return;
            send(CoastalBatteryBlockEntity.A_MODE, new int[] { c.mode() == CoastalBatteryBlockEntity.M_NET ? CoastalBatteryBlockEntity.M_TYPED : CoastalBatteryBlockEntity.M_NET });
        }));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 66, 68, 14, Component.translatable(P + "set"), w -> setTarget()));
        addRenderableWidget(new WmdUi.KitButton(x + 78, y + 66, 66, 14, Component.translatable(P + "clear"), w -> {
            xBox.setValue(""); zBox.setValue("");
            send(CoastalBatteryBlockEntity.A_CLEAR, new int[0]);
        }));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 148, 44, 14, Component.translatable(P + "fire1"), w -> send(CoastalBatteryBlockEntity.A_FIRE, new int[0]), true));
        addRenderableWidget(new WmdUi.KitButton(x + 54, y + 148, 46, 14, Component.translatable(P + "salvo"), w -> send(CoastalBatteryBlockEntity.A_SALVO, new int[0]), true));
        addRenderableWidget(new WmdUi.KitButton(x + 104, y + 148, 40, 14, Component.translatable(P + "abort"), w -> send(CoastalBatteryBlockEntity.A_ABORT, new int[0])));
    }

    private static Component modeLabel(CoastalBatteryBlockEntity b) {
        return Component.translatable(b.mode() == CoastalBatteryBlockEntity.M_NET ? P + "mode_net" : P + "mode_typed");
    }

    private EditBox box(int x, int y, String value) {
        EditBox e = new EditBox(font, x, y, BW, 13, Component.empty());
        e.setMaxLength(9);
        e.setFilter(s -> s.matches("-?\\d*"));
        e.setValue(value);
        addRenderableWidget(e);
        return e;
    }

    @Override
    protected void rebuildWidgets() {
        if (xBox != null) { xText = xBox.getValue(); zText = zBox.getValue(); }
        super.rebuildWidgets();
    }

    private static Integer parse(EditBox b) {
        try { return b == null || b.getValue().isEmpty() || b.getValue().equals("-") ? null : Integer.parseInt(b.getValue()); } catch (NumberFormatException e) { return null; }
    }

    private void setTarget() {
        Integer tx = parse(xBox), tz = parse(zBox);
        if (tx == null || tz == null) { if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(P + "need_target"), true); return; }
        send(CoastalBatteryBlockEntity.A_TARGET, new int[] { tx, tz });
    }

    private void send(int action, int[] data) {
        CoastalBatteryBlockEntity b = battery();
        if (b != null) PacketDistributor.sendToServer(new ShorePayload(b.getBlockPos(), action, data));
    }

    private int count(int i) { return menu.slots.size() > i ? menu.slots.get(i).getItem().getCount() : 0; }

    private static int statusKind(int st) {
        return switch (st) {
            case CoastalBatteryBlockEntity.S_READY, CoastalBatteryBlockEntity.S_FIRING -> WmdUi.K_GOOD;
            case CoastalBatteryBlockEntity.S_RAISING, CoastalBatteryBlockEntity.S_LOWERING, CoastalBatteryBlockEntity.S_TRAINING -> WmdUi.K_WARN;
            case CoastalBatteryBlockEntity.S_NO_TARGET -> WmdUi.K_IDLE;
            default -> WmdUi.K_BAD;
        };
    }

    private static int colour(int kind) {
        return switch (kind) { case WmdUi.K_GOOD -> WmdUi.GOOD; case WmdUi.K_WARN -> WmdUi.WARN; case WmdUi.K_IDLE -> WmdUi.TEXT_MUTED; default -> WmdUi.BAD; };
    }

    private WmdUi.KitButton modeButton;

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        CoastalBatteryBlockEntity b = battery();
        if (b == null) return;
        if (modeButton != null) modeButton.setMessage(modeLabel(b));
        int x = px(), y = topPos, tw = PW - 14;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        WmdUi.drawFit(g, font, Component.translatable(P + "panel"), x + 7, y + 6, tw, WmdUi.TEXT);
        WmdUi.divider(g, x + 6, y + 15, PW - 12);
        boolean net = b.mode() == CoastalBatteryBlockEntity.M_NET;
        WmdUi.drawFit(g, font, net ? Component.translatable(P + "net_line", b.frequency()) : Component.translatable(P + "typed_line"), x + 7, y + 37, tw, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "tgt"), x + 6, y + 52, BX - 8, net ? WmdUi.TEXT_DIM : WmdUi.ORANGE);

        WmdUi.well(g, x + 5, y + 84, PW - 10, 41);
        int ly = y + 86;
        if (b.hasAim()) {
            String[] lab = b.lockLabel().split(":");
            Component what = lab.length >= 2 ? Component.translatable(P + "lock_" + lab[0], lab[1]) : Component.translatable(P + "point", b.aimX(), b.aimZ());
            WmdUi.drawFit(g, font, what, x + 7, ly, tw, lab.length >= 2 && lab[0].equals("sub_down") ? WmdUi.BAD : WmdUi.TEXT);
            WmdUi.drawFit(g, font, Component.translatable(P + "bearing", Math.round(b.trainTarget()), Math.round(b.shownTrain(partialTick))), x + 7, ly + 10, tw, WmdUi.TEXT);
            boolean far = b.nextRange() > 0 && b.routeLength() > b.nextRange();
            WmdUi.drawFit(g, font, Component.translatable(P + "route", String.format("%,d", b.routeLength()), b.nextRange() > 0 ? String.format("%,d", b.nextRange()) : "-"), x + 7, ly + 20, tw,
                    far ? WmdUi.BAD : WmdUi.TEXT);
        } else WmdUi.drawFit(g, font, Component.translatable(net ? P + "no_pick" : P + "no_typed"), x + 7, ly, tw, WmdUi.TEXT_MUTED);
        int missiles = 0;
        for (int i = 0; i < CoastalBatteryBlockEntity.CANISTERS; i++) if (count(i) > 0) missiles++;
        int fuel = count(CoastalBatteryBlockEntity.CANISTERS);
        WmdUi.drawFit(g, font, Component.translatable(P + "load", missiles, CoastalBatteryBlockEntity.CANISTERS, fuel), x + 7, ly + 30, tw,
                missiles > 0 && fuel >= Balance.CRUISE_FUEL_CANISTERS ? WmdUi.TEXT : WmdUi.WARN);
        int st = b.batteryStatus(), k = statusKind(st);
        boolean busy = st == CoastalBatteryBlockEntity.S_RAISING || st == CoastalBatteryBlockEntity.S_FIRING || st == CoastalBatteryBlockEntity.S_LOWERING || st == CoastalBatteryBlockEntity.S_TRAINING;
        WmdUi.led(g, x + 8, y + 131, k, busy);
        Component sl = b.salvoLeft() > 0 && busy ? Component.translatable(P + "st." + st).append(Component.translatable(P + "st_left", b.salvoLeft())) : Component.translatable(P + "st." + st);
        WmdUi.drawFit(g, font, sl, x + 17, y + 130, PW - 24, colour(k));
        WmdUi.divider(g, x + 6, y + 142, PW - 12);
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= topPos && my < topPos + PH; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (battery() != null && inPanel(mx, my)) {
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
        if (keyCode != 256)
            for (EditBox box : new EditBox[] { xBox, zBox })
                if (box != null && box.isFocused() && (box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
