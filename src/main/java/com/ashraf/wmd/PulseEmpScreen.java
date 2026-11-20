package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class PulseEmpScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 160, PH = 146, WELL_Y = 28, LINE_Y = 64, HINT_Y = 108, BTN_Y = 124, BTN_H = 16;
    private static final int C_CHARGE = 0xFF4AA3FF, C_COOL = 0xFFE0A030, C_TRACK = 0xFF1A2129;
    private static final String P = "gui.wmd.pemp.";
    private WmdUi.KitButton rule, fire;

    public PulseEmpScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private PulseEmpBlockEntity emp() { return menu.machine() instanceof PulseEmpBlockEntity s ? s : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return emp() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        PulseEmpBlockEntity s = emp();
        if (s == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, s.getBlockPos())));
        rule = addRenderableWidget(new WmdUi.KitButton(px() + 6, py() + BTN_Y, 72, BTN_H, ruleLabel(s), b -> send(s, (s.rule() + 1) & 3)));
        fire = addRenderableWidget(new WmdUi.KitButton(px() + 82, py() + BTN_Y, 72, BTN_H, Component.translatable(P + "fire"), b -> send(s, s.rule() | PulseEmpBlockEntity.FIRE_BIT)));
    }

    private void send(PulseEmpBlockEntity s, int next) {
        s.setModeClient(next);
        PacketDistributor.sendToServer(new SetMachineModePayload(s.getBlockPos(), next));
        if (rule != null) rule.setMessage(ruleLabel(s));
    }

    private static Component ruleLabel(PulseEmpBlockEntity s) { return Component.translatable(P + "rule." + s.rule()); }

    private static int kind(int st) {
        return switch (st) {
            case PulseEmpBlockEntity.ST_READY, PulseEmpBlockEntity.ST_TARGET -> WmdUi.K_GOOD;
            case PulseEmpBlockEntity.ST_CHARGING, PulseEmpBlockEntity.ST_FIRING -> WmdUi.K_WARN;
            case PulseEmpBlockEntity.ST_COOLING -> WmdUi.K_INFO;
            default -> WmdUi.K_BAD;
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        PulseEmpBlockEntity s = emp();
        if (s == null) return;
        if (rule != null) rule.setMessage(ruleLabel(s));
        if (fire != null) fire.active = s.empState() == PulseEmpBlockEntity.S_READY;
        int x = px(), y = py(), st = s.empStatus();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        WmdUi.header(g, font, x, y, PW, new ItemStack(s.getBlockState().getBlock().asItem()), Component.translatable("block.wmd.pulse_emp"),
                Component.translatable(P + "st." + st), kind(st), st == PulseEmpBlockEntity.ST_FIRING || st == PulseEmpBlockEntity.ST_CHARGING);

        WmdUi.well(g, x + 6, y + WELL_Y, PW - 12, 30);
        boolean cooling = s.empState() == PulseEmpBlockEntity.S_COOL;
        double f = cooling ? 1.0 - s.coolSecondsLeft() / (Balance.PEMP_COOLDOWN / 20.0) : s.chargePermille() / 1000.0;
        int bx0 = x + 10, bx1 = x + PW - 10, by = y + WELL_Y + 16;
        g.fill(bx0, by, bx1, by + 10, C_TRACK);
        g.fill(bx0, by, bx0 + (int) Math.round((bx1 - bx0) * Math.max(0.0, Math.min(1.0, f))), by + 10, cooling ? C_COOL : C_CHARGE);
        int left = s.coolSecondsLeft();
        Component bar = cooling ? Component.translatable(P + "cooling", left / 60, String.format("%02d", left % 60))
                : Component.translatable(P + "charge", s.chargePermille() / 10);
        WmdUi.drawFit(g, font, bar, x + 10, y + WELL_Y + 5, PW - 20, WmdUi.TEXT);

        WmdUi.drawFit(g, font, Component.translatable(P + "contacts", s.contacts(), s.getFrequency()), x + 6, y + LINE_Y, PW - 12, WmdUi.TEXT);
        WmdUi.drawFit(g, font, Component.translatable(P + "cone", s.inCone()), x + 6, y + LINE_Y + 12, PW - 12, s.inCone() > 0 ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "rule_line", Component.translatable(P + "rule." + s.rule())), x + 6, y + LINE_Y + 24, PW - 12, WmdUi.TEXT_MUTED);
        WmdUi.divider(g, x + 6, y + HINT_Y - 4, PW - 12);
        WmdUi.drawFit(g, font, Component.translatable(P + "hint." + st, s.getFrequency()), x + 6, y + HINT_Y, PW - 12, WmdUi.TEXT_MUTED);
    }
}
