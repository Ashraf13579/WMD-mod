package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class BombRackScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 140, PH = 160, ARM_TICKS = 60;
    private static final String P = "gui.wmd.rack.";

    private WmdUi.KitButton drop, salvo, set, clear;
    private EditBox fx, fz;
    private long armedAt = -1L;

    public BombRackScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private BombRackBlockEntity rack() { return menu.machine() instanceof BombRackBlockEntity r ? r : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    private long now() { return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L; }

    private boolean armed() { return armedAt >= 0L && now() - armedAt < ARM_TICKS; }

    @Override
    protected int sidePanelWidth() { return rack() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        if (menu.machine() instanceof BlockEntity be)
            addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, be.getBlockPos())));
        BombRackBlockEntity r = rack();
        if (r == null) return;
        int x = px(), y = py();
        fx = addRenderableWidget(new EditBox(font, x + 6, y + 60, 58, 14, Component.translatable(P + "x")));
        fz = addRenderableWidget(new EditBox(font, x + 70, y + 60, 58, 14, Component.translatable(P + "z")));
        for (EditBox b : new EditBox[] { fx, fz }) { b.setMaxLength(9); b.setFilter(s -> s.matches("-?\\d{0,8}")); }
        fx.setHint(Component.translatable(P + "x"));
        fz.setHint(Component.translatable(P + "z"));
        if (r.targetSet()) { fx.setValue(Integer.toString(r.targetX())); fz.setValue(Integer.toString(r.targetZ())); }
        set = addRenderableWidget(new WmdUi.KitButton(x + 6, y + 78, 58, 16, Component.translatable(P + "set"), b -> sendTarget(true), false));
        clear = addRenderableWidget(new WmdUi.KitButton(x + 70, y + 78, 64, 16, Component.translatable(P + "clear"), b -> sendTarget(false), false));
        drop = addRenderableWidget(new WmdUi.KitButton(x + 6, y + 104, PW - 12, 18, Component.translatable(P + "drop"), b -> click(BombRackBlockEntity.A_DROP), false));
        salvo = addRenderableWidget(new WmdUi.KitButton(x + 6, y + 124, PW - 12, 18, Component.translatable(P + "salvo"), b -> pressSalvo(), true));
    }

    private void sendTarget(boolean on) {
        BombRackBlockEntity r = rack();
        if (r == null) return;
        int x = 0, z = 0;
        if (on) {
            try { x = Integer.parseInt(fx.getValue()); z = Integer.parseInt(fz.getValue()); }
            catch (NumberFormatException e) { return; }
        } else { fx.setValue(""); fz.setValue(""); }
        PacketDistributor.sendToServer(new BombTargetPayload(r.getBlockPos(), on, x, z));
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void pressSalvo() {
        if (!armed()) { armedAt = now(); return; }
        armedAt = -1L;
        click(BombRackBlockEntity.A_SALVO);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        BombRackBlockEntity r = rack();
        if (r == null || drop == null) return;
        boolean can = r.total() > 0 && menu.energy() >= (r.heavy ? Balance.BAY_FE : Balance.RACK_FE) && !upright(r);
        drop.active = can;
        salvo.active = can && r.total() > 1;
        salvo.setMessage(Component.translatable(P + (armed() ? "confirm" : "salvo")));
        if (!salvo.active) armedAt = -1L;
        set.active = !fx.getValue().isEmpty() && !fz.getValue().isEmpty() && !"-".equals(fx.getValue()) && !"-".equals(fz.getValue());
        clear.active = r.targetSet();
    }

    private static boolean upright(BombRackBlockEntity r) { return Machines.mount(r.getBlockState()) == net.minecraft.core.Direction.UP; }

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
        BombRackBlockEntity r = rack();
        if (r == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        fit(g, Component.translatable(P + (r.heavy ? "panel_bay" : "panel")), cx, y + 6, WmdUi.TEXT);
        Component state;
        int kind;
        int fe = r.heavy ? Balance.BAY_FE : Balance.RACK_FE;
        if (upright(r)) { state = Component.translatable(P + "state_upright"); kind = WmdUi.K_BAD; }
        else if (r.total() == 0) { state = Component.translatable(P + "state_empty"); kind = WmdUi.K_BAD; }
        else if (menu.energy() < fe) { state = Component.translatable(P + "state_power"); kind = WmdUi.K_BAD; }
        else if (r.heavy && r.doorOpen(now()) > 0.0F) { state = Component.translatable(P + "state_doors"); kind = WmdUi.K_WARN; }
        else { state = Component.translatable(P + "state_ready", r.total()); kind = WmdUi.K_GOOD; }
        int chipW = Math.min(font.width(state), PW - 10 - 14) + 15;
        WmdUi.chipRight(g, font, cx + (chipW + 1) / 2, y + 16, state, kind, false, PW - 10);
        fit(g, Component.translatable(P + "load", r.total(), r.slots() * r.perSlot()), cx, y + 32, WmdUi.TEXT);
        fit(g, r.targetSet() ? Component.translatable(P + "target", r.targetX(), r.targetZ()) : Component.translatable(P + "no_target"), cx, y + 43, WmdUi.TEXT_MUTED);
        WmdUi.divider(g, x + 5, y + 54, PW - 10);
        WmdUi.divider(g, x + 5, y + 99, PW - 10);
        fit(g, Component.translatable(P + (armed() ? "hint_armed" : "hint")), cx, y + 146, armed() ? WmdUi.WARN : WmdUi.TEXT_MUTED);
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
