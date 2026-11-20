package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class TeslaTowerScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 160, PH = 136, WELL_Y = 28, LINE_Y = 64, HINT_Y = 96, BTN_Y = 114, BTN_H = 16;
    private static final int C_CHARGE = 0xFF4AA3FF, C_TRACK = 0xFF1A2129;
    private static final String P = "gui.wmd.tesla.";
    private WmdUi.KitButton onOff;

    public TeslaTowerScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private TeslaTowerBlockEntity tt() { return menu.machine() instanceof TeslaTowerBlockEntity s ? s : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return tt() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        TeslaTowerBlockEntity s = tt();
        if (s == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, s.getBlockPos())));
        onOff = addRenderableWidget(new WmdUi.KitButton(px() + 6, py() + BTN_Y, PW - 12, BTN_H, label(s), b -> {
            int next = s.off() ? 0 : 1;
            s.setModeClient(next);
            PacketDistributor.sendToServer(new SetMachineModePayload(s.getBlockPos(), next));
            if (onOff != null) onOff.setMessage(label(s));
        }));
    }

    private static Component label(TeslaTowerBlockEntity s) { return Component.translatable(s.off() ? P + "turn_on" : P + "turn_off"); }

    private static int kind(int st) {
        return switch (st) {
            case TeslaTowerBlockEntity.ST_WATCHING -> WmdUi.K_GOOD;
            case TeslaTowerBlockEntity.ST_CHARGING, TeslaTowerBlockEntity.ST_STRIKE -> WmdUi.K_WARN;
            case TeslaTowerBlockEntity.ST_OFF -> WmdUi.K_INFO;
            default -> WmdUi.K_BAD;
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        TeslaTowerBlockEntity s = tt();
        if (s == null) return;
        if (onOff != null) onOff.setMessage(label(s));
        int x = px(), y = py(), st = s.teslaStatus();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        WmdUi.header(g, font, x, y, PW, new ItemStack(s.getBlockState().getBlock().asItem()), Component.translatable("block.wmd.tesla_tower"),
                Component.translatable(P + "st." + st), kind(st), st == TeslaTowerBlockEntity.ST_CHARGING || st == TeslaTowerBlockEntity.ST_STRIKE);
        WmdUi.well(g, x + 6, y + WELL_Y, PW - 12, 30);
        int bx0 = x + 10, bx1 = x + PW - 10, by = y + WELL_Y + 16;
        g.fill(bx0, by, bx1, by + 10, C_TRACK);
        g.fill(bx0, by, bx0 + Math.round((bx1 - bx0) * s.chargePercent() / 100.0F), by + 10, C_CHARGE);
        WmdUi.drawFit(g, font, Component.translatable(P + "charge", s.chargePercent()), x + 10, y + WELL_Y + 5, PW - 20, WmdUi.TEXT);
        WmdUi.drawFit(g, font, Component.translatable(P + "targets", s.targetsInRange(), (int) s.range()), x + 6, y + LINE_Y, PW - 12, WmdUi.TEXT);
        int ago = s.secondsSinceStrike();
        WmdUi.drawFit(g, font, ago < 0 || s.lastHits() == 0 ? Component.translatable(P + "no_strike") : Component.translatable(P + "last", s.lastHits(), ago),
                x + 6, y + LINE_Y + 12, PW - 12, WmdUi.TEXT_MUTED);
        WmdUi.divider(g, x + 6, y + HINT_Y - 4, PW - 12);
        WmdUi.drawFit(g, font, Component.translatable(P + "hint." + st), x + 6, y + HINT_Y, PW - 12, WmdUi.TEXT_MUTED);
    }
}
