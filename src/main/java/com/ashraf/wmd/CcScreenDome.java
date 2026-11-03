package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class CcScreenDome extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 184, PH = 220;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, BLUE = WmdUi.BLUE_LIGHT, AMBER = WmdUi.WARN;
    private static final String P = "gui.wmd.dome.";
    private static final int ROW_Y = 36, ROW_H = 10, BOX_Y = 120, DES_Y = 138, RAD_Y = 156, SET_Y = 174, HINT_Y = 196;
    private static final String[] DIR = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };

    private EditBox xBox, zBox;
    private String xText = "", zText = "";
    private Button setBtn, clearBtn, desBtn;
    private int radius = -1;

    public CcScreenDome(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private CcIronDomeBlockEntity be() { return menu.machine() instanceof CcIronDomeBlockEntity c ? c : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return be() != null ? PW : 0; }

    private boolean owner() {
        CcIronDomeBlockEntity b = be();
        return b != null && minecraft != null && minecraft.player != null && minecraft.player.getUUID().equals(b.getOwner());
    }

    @Override
    protected void init() {
        super.init();
        CcIronDomeBlockEntity b = be();
        if (b == null) return;
        if (radius < 0) {
            radius = b.radius();
            if (b.hasCentre() && xText.isEmpty() && zText.isEmpty()) { xText = Integer.toString(b.centreX()); zText = Integer.toString(b.centreZ()); }
        }
        int x = px(), y = py();
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, b.getBlockPos())));
        xBox = box(x + 16, y + BOX_Y, 64, xText);
        zBox = box(x + 98, y + BOX_Y, 64, zText);
        desBtn = addRenderableWidget(new WmdUi.KitButton(x + 6, y + DES_Y, PW - 12, 15, Component.empty(), w -> useDesignated()));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + RAD_Y, 14, 14, Component.literal("<"), w -> radius = IronDome.clampRadius(radius - 16)));
        addRenderableWidget(new WmdUi.KitButton(x + PW - 20, y + RAD_Y, 14, 14, Component.literal(">"), w -> radius = IronDome.clampRadius(radius + 16)));
        setBtn = addRenderableWidget(new WmdUi.KitButton(x + 6, y + SET_Y, 112, 16, Component.translatable(P + "set"), w -> set()));
        clearBtn = addRenderableWidget(new WmdUi.KitButton(x + 122, y + SET_Y, PW - 128, 16, Component.translatable(P + "clear"), w -> clear()));
        updateButtons();
    }

    private EditBox box(int x, int y, int w, String value) {
        EditBox e = new EditBox(font, x, y, w, 14, Component.empty());
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

    private static int parse(EditBox b) {
        try { return b == null ? 0 : Integer.parseInt(b.getValue()); } catch (NumberFormatException e) { return 0; }
    }

    private boolean hasTarget() {
        return xBox != null && !xBox.getValue().isEmpty() && !xBox.getValue().equals("-") && !zBox.getValue().isEmpty() && !zBox.getValue().equals("-");
    }

    private CcTarget designated() {
        CcIronDomeBlockEntity b = be();
        CcBuildingBlockEntity bb = b != null ? b.building() : null;
        return bb != null ? bb.designated() : null;
    }

    private void useDesignated() {
        CcTarget t = designated();
        if (t == null || xBox == null) return;
        xBox.setValue(Integer.toString(t.x()));
        zBox.setValue(Integer.toString(t.z()));
        updateButtons();
    }

    private void set() {
        CcIronDomeBlockEntity b = be();
        if (b == null || !hasTarget()) return;
        PacketDistributor.sendToServer(CcBPayload.of(b.getBlockPos(), CcBPayload.DOME_SET, radius, parse(xBox), parse(zBox)));
    }

    private void clear() {
        CcIronDomeBlockEntity b = be();
        if (b != null) PacketDistributor.sendToServer(CcBPayload.of(b.getBlockPos(), CcBPayload.DOME_CLEAR, 0L, 0, 0));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != 256) for (EditBox box : new EditBox[] { xBox, zBox })
            if (box != null && box.isFocused() && (box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    private void updateButtons() {
        CcIronDomeBlockEntity b = be();
        if (b == null || setBtn == null) return;
        boolean ok = b.isOnline() && owner();
        setBtn.active = ok && hasTarget();
        clearBtn.active = ok && b.hasCentre();
        CcTarget t = designated();
        desBtn.active = t != null;
        desBtn.setMessage(t == null ? Component.translatable("gui.wmd.cc.b.use_des_none") : Component.translatable("gui.wmd.cc.b.use_des_at", t.x(), t.z()));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        CcIronDomeBlockEntity b = be();
        if (b == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        boolean on = b.isOnline(), set = b.hasCentre(), up = on && set && b.domeActive();
        Component chip = Component.translatable(!on ? "gui.wmd.cc.offline" : !set ? P + "chip_unset" : up ? P + "chip_on" : P + "chip_down");
        WmdUi.header(g, font, x, y, PW, new ItemStack(b.getBlockState().getBlock().asItem()), Component.translatable(b.kind().langKey()), chip,
                !on ? WmdUi.K_BAD : !set ? WmdUi.K_IDLE : up ? WmdUi.K_GOOD : WmdUi.K_BAD, on && set && !up);
        fit(g, set ? Component.translatable(P + "centre", b.centreX(), b.centreZ(), b.radius()) : Component.translatable(P + "no_centre"), cx, y + 24, set ? TEXT : GREY);
        List<CompoundTag> rows = new ArrayList<>();
        for (Tag t : b.rows()) if (t instanceof CompoundTag c) rows.add(c);
        int ly = y + ROW_Y;
        if (rows.isEmpty()) {
            fit(g, Component.translatable(set ? "gui.wmd.cc.offline" : P + "set_hint"), cx, ly + 30, GREY);
        }
        for (CompoundTag c : rows) {
            int i = Math.max(0, Math.min(7, c.getInt("i"))), st = c.getInt("st");
            WmdUi.led(g, x + 7, ly + 1, st == IronDome.M_OK ? WmdUi.K_GOOD : st == IronDome.M_UNLOADED ? WmdUi.K_IDLE : WmdUi.K_BAD, false);
            Component need = Component.translatable(P + "m." + Math.max(0, Math.min(IronDome.M_UNSET, st)), Balance.DOME_MARKER_TOL);
            drawLeft(g, Component.translatable(P + "row", i + 1, DIR[i], c.getInt("x"), c.getInt("z"), need), x + 15, ly, PW - 20,
                    st == IronDome.M_OK ? GOOD : st == IronDome.M_UNLOADED ? GREY : AMBER);
            ly += ROW_H;
        }
        g.drawString(font, "X", x + 7, y + BOX_Y + 3, TEXT, false);
        g.drawString(font, "Z", x + 89, y + BOX_Y + 3, TEXT, false);
        boolean changed = set && radius != b.radius();
        fit(g, Component.translatable(P + "radius", radius, Balance.DOME_RADIUS_MIN, Balance.DOME_RADIUS_MAX), cx, y + RAD_Y + 3, changed ? AMBER : TEXT);
        fit(g, Component.translatable(P + "hint1", Balance.DOME_MARKER_TOL), cx, y + HINT_Y, BLUE);
        fit(g, Component.translatable(P + "hint2", Math.round(Balance.DOME_HIT * 100), Math.round(Balance.CRAM_HIT * 100)), cx, y + HINT_Y + 11, GREY);
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

    private void drawLeft(GuiGraphics g, Component text, int x, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, x, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
