package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class BombSightScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 140, PH = 166;
    private static final String P = "gui.wmd.sight.";

    private WmdUi.KitButton off, ccip, ccrp, src, set, clear;
    private EditBox fx, fz;

    public BombSightScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private BombSightBlockEntity sight() { return menu.machine() instanceof BombSightBlockEntity s ? s : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return sight() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        if (menu.machine() instanceof BlockEntity be)
            addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, be.getBlockPos())));
        BombSightBlockEntity s = sight();
        if (s == null) return;
        int x = px(), y = py();
        off = addRenderableWidget(new WmdUi.KitButton(x + 6, y + 32, 38, 16, Component.translatable(P + "off"), b -> click(BombSightBlockEntity.A_OFF), false));
        ccip = addRenderableWidget(new WmdUi.KitButton(x + 48, y + 32, 42, 16, Component.translatable(P + "ccip"), b -> click(BombSightBlockEntity.A_CCIP), false));
        ccrp = addRenderableWidget(new WmdUi.KitButton(x + 94, y + 32, 40, 16, Component.translatable(P + "ccrp"), b -> click(BombSightBlockEntity.A_CCRP), false));
        src = addRenderableWidget(new WmdUi.KitButton(x + 6, y + 52, PW - 12, 16, Component.empty(), b -> click(BombSightBlockEntity.A_SRC), false));
        fx = addRenderableWidget(new EditBox(font, x + 6, y + 72, 58, 14, Component.translatable("gui.wmd.rack.x")));
        fz = addRenderableWidget(new EditBox(font, x + 70, y + 72, 58, 14, Component.translatable("gui.wmd.rack.z")));
        for (EditBox b : new EditBox[] { fx, fz }) { b.setMaxLength(9); b.setFilter(t -> t.matches("-?\\d{0,8}")); }
        fx.setHint(Component.translatable("gui.wmd.rack.x"));
        fz.setHint(Component.translatable("gui.wmd.rack.z"));
        if (s.targetSet()) { fx.setValue(Integer.toString(s.targetX())); fz.setValue(Integer.toString(s.targetZ())); }
        set = addRenderableWidget(new WmdUi.KitButton(x + 6, y + 90, 58, 16, Component.translatable("gui.wmd.rack.set"), b -> sendTarget(true), false));
        clear = addRenderableWidget(new WmdUi.KitButton(x + 70, y + 90, 64, 16, Component.translatable("gui.wmd.rack.clear"), b -> sendTarget(false), false));
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void sendTarget(boolean on) {
        BombSightBlockEntity s = sight();
        if (s == null) return;
        int x = 0, z = 0;
        if (on) {
            try { x = Integer.parseInt(fx.getValue()); z = Integer.parseInt(fz.getValue()); }
            catch (NumberFormatException e) { return; }
        } else { fx.setValue(""); fz.setValue(""); }
        PacketDistributor.sendToServer(new BombSightPayload(s.getBlockPos(), on, x, z));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        BombSightBlockEntity s = sight();
        if (s == null || off == null) return;
        off.active = s.mode() != BombSightBlockEntity.OFF;
        ccip.active = s.mode() != BombSightBlockEntity.CCIP;
        ccrp.active = s.mode() != BombSightBlockEntity.CCRP;
        src.setMessage(Component.translatable(P + (s.laserTarget() ? "src_laser" : "src_coords")));
        boolean coords = !s.laserTarget();
        fx.setEditable(coords); fz.setEditable(coords);
        set.active = coords && !fx.getValue().isEmpty() && !fz.getValue().isEmpty() && !"-".equals(fx.getValue()) && !"-".equals(fz.getValue());
        clear.active = coords && s.targetSet();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((fx != null && fx.isFocused()) || (fz != null && fz.isFocused())) {
            if (key == 256) return super.keyPressed(key, scan, mods);
            if (fx.isFocused()) return fx.keyPressed(key, scan, mods) || true;
            return fz.keyPressed(key, scan, mods) || true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        BombSightBlockEntity s = sight();
        if (s == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        fit(g, Component.translatable(P + "panel"), cx, y + 6, WmdUi.TEXT);
        Component state;
        int kind;
        boolean pulse = false;
        if (s.mode() == BombSightBlockEntity.OFF) { state = Component.translatable(P + "state_off"); kind = WmdUi.K_INFO; }
        else if (s.pulsing()) { state = Component.translatable(P + "state_release"); kind = WmdUi.K_WARN; pulse = true; }
        else if (!s.solved()) { state = Component.translatable(P + "state_nosol"); kind = WmdUi.K_BAD; }
        else if (s.mode() == BombSightBlockEntity.CCIP) { state = Component.translatable(P + "state_ccip"); kind = WmdUi.K_GOOD; }
        else if (!s.aimed()) { state = Component.translatable(P + "state_notarget"); kind = WmdUi.K_BAD; }
        else { state = Component.translatable(P + "state_ccrp"); kind = WmdUi.K_GOOD; }
        int chipW = Math.min(font.width(state), PW - 10 - 14) + 15;
        WmdUi.chipRight(g, font, cx + (chipW + 1) / 2, y + 16, state, kind, pulse, PW - 10);
        WmdUi.divider(g, x + 5, y + 110, PW - 10);
        if (s.mode() != BombSightBlockEntity.OFF) {
            fit(g, Component.translatable(P + "alt_spd", (int) Math.round(s.height()), (int) Math.round(s.speed())), cx, y + 116, WmdUi.TEXT);
            fit(g, Component.translatable(P + "track", String.format("%03d", (int) Math.round(s.track()) % 360)), cx, y + 127, WmdUi.TEXT);
            double rel = s.releaseSeconds();
            Component third = s.mode() == BombSightBlockEntity.CCRP && s.aimed() && !Double.isNaN(rel)
                    ? (rel >= 0 ? Component.translatable(P + "release", String.format("%.1f", rel), String.format("%.0f", s.cross())) : Component.translatable(P + "passed"))
                    : Component.translatable(P + "impact", (int) Math.round(Math.hypot(s.impact().x - s.dropPoint().x, s.impact().z - s.dropPoint().z)));
            fit(g, third, cx, y + 138, WmdUi.TEXT_MUTED);
        }
        fit(g, Component.translatable(P + "hint"), cx, y + 152, WmdUi.TEXT_MUTED);
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) {
        int maxW = PW - 10;
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float sc = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - sc) * 4.0F, 0.0F);
        g.pose().scale(sc, sc, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
