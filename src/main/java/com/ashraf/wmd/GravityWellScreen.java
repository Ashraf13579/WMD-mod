package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class GravityWellScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 160, PH = 146, WELL_Y = 28, LINE_Y = 64, HINT_Y = 108, BTN_Y = 124, BTN_H = 16;
    private static final int C_BUF = 0xFF4AA3FF, C_LOCK = 0xFFE0A030, C_TRACK = 0xFF1A2129;
    private static final String P = "gui.wmd.gwell.";
    private WmdUi.KitButton minus, plus, power;

    public GravityWellScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private GravityWellBlockEntity well() { return menu.machine() instanceof GravityWellBlockEntity s ? s : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return well() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        GravityWellBlockEntity s = well();
        if (s == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, s.getBlockPos())));
        minus = addRenderableWidget(new WmdUi.KitButton(px() + 6, py() + BTN_Y, 34, BTN_H, Component.literal("-"), b -> send(s, s.mode() - 2)));
        plus = addRenderableWidget(new WmdUi.KitButton(px() + 44, py() + BTN_Y, 34, BTN_H, Component.literal("+"), b -> send(s, s.mode() + 2)));
        power = addRenderableWidget(new WmdUi.KitButton(px() + 82, py() + BTN_Y, 72, BTN_H, powerLabel(s), b -> send(s, s.mode() ^ GravityWellBlockEntity.ON_BIT)));
    }

    private void send(GravityWellBlockEntity s, int next) {
        int steps = (Balance.GWG_R_MAX - Balance.GWG_R_MIN) / Balance.GWG_R_STEP;
        int step = Math.max(0, Math.min(steps, (next >> 1) & 7));
        if (next < 0) step = 0;
        int m = (next & GravityWellBlockEntity.ON_BIT) | (step << 1);
        s.setModeClient(m);
        PacketDistributor.sendToServer(new SetMachineModePayload(s.getBlockPos(), m));
        if (power != null) power.setMessage(powerLabel(s));
    }

    private static Component powerLabel(GravityWellBlockEntity s) { return Component.translatable(P + (s.switchedOn() ? "off" : "on")); }

    private static int kind(int st) {
        return switch (st) {
            case GravityWellBlockEntity.ST_UP, GravityWellBlockEntity.ST_BENDING, GravityWellBlockEntity.ST_DRAGGING -> WmdUi.K_GOOD;
            case GravityWellBlockEntity.ST_SPINNING, GravityWellBlockEntity.ST_CHARGING -> WmdUi.K_WARN;
            case GravityWellBlockEntity.ST_OFF -> WmdUi.K_INFO;
            default -> WmdUi.K_BAD;
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        GravityWellBlockEntity s = well();
        if (s == null) return;
        int steps = (Balance.GWG_R_MAX - Balance.GWG_R_MIN) / Balance.GWG_R_STEP;
        if (power != null) power.setMessage(powerLabel(s));
        if (minus != null) minus.active = s.radiusStep() > 0;
        if (plus != null) plus.active = s.radiusStep() < steps;
        int x = px(), y = py(), st = s.wellStatus();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        WmdUi.header(g, font, x, y, PW, new ItemStack(s.getBlockState().getBlock().asItem()), Component.translatable("block.wmd.gravity_well"),
                Component.translatable(P + "st." + st), kind(st), st == GravityWellBlockEntity.ST_SPINNING || st == GravityWellBlockEntity.ST_BENDING);

        WmdUi.well(g, x + 6, y + WELL_Y, PW - 12, 30);
        boolean lock = s.wellState() == GravityWellBlockEntity.S_LOCKOUT;

        int buf = menu.energyCap() > 0 ? (int) Math.min(1000L, (long) menu.energy() * 1000L / menu.energyCap()) : 0;
        double f = lock ? 1.0 - s.lockSecondsLeft() / (Balance.GWG_LOCKOUT / 20.0) : buf / 1000.0;
        int bx0 = x + 10, bx1 = x + PW - 10, by = y + WELL_Y + 16;
        g.fill(bx0, by, bx1, by + 10, C_TRACK);
        g.fill(bx0, by, bx0 + (int) Math.round((bx1 - bx0) * Math.max(0.0, Math.min(1.0, f))), by + 10, lock ? C_LOCK : C_BUF);
        if (!lock) {
            int mx = bx0 + (bx1 - bx0) * Balance.GWG_START_PCT / 100;
            g.fill(mx, by - 1, mx + 1, by + 11, 0xFFCFE4FF);
        }
        int left = s.lockSecondsLeft();
        Component bar = lock ? Component.translatable(P + "lockout", left / 60, String.format("%02d", left % 60)) : Component.translatable(P + "buffer", buf / 10);
        WmdUi.drawFit(g, font, bar, x + 10, y + WELL_Y + 5, PW - 20, WmdUi.TEXT);
        WmdUi.drawFit(g, font, Component.translatable(P + "radius", s.radius()), x + 6, y + LINE_Y, PW - 12, WmdUi.TEXT);
        WmdUi.drawFit(g, font, Component.translatable(P + "upkeep", String.format("%,d", s.upkeepFe())), x + 6, y + LINE_Y + 12, PW - 12, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "stats", s.bendCount(), s.dragCount()), x + 6, y + LINE_Y + 24, PW - 12,
                s.dragCount() > 0 ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
        WmdUi.divider(g, x + 6, y + HINT_Y - 4, PW - 12);
        WmdUi.drawFit(g, font, Component.translatable(P + "hint." + st, Balance.GWG_START_PCT), x + 6, y + HINT_Y, PW - 12, WmdUi.TEXT_MUTED);
    }
}
