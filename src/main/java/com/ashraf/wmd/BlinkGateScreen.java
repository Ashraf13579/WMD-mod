package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class BlinkGateScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 168, PH = 188, WELL_Y = 28, LINE_Y = 64, HINT_Y = 128, BTN_Y = 146, BTN2_Y = 166, BTN_H = 16;
    private static final int C_BUF = 0xFF4AA3FF, C_COOL = 0xFFE0A030, C_TRACK = 0xFF1A2129;
    private static final String P = "gui.wmd.blink.";
    private WmdUi.KitButton minus, plus, build, arm, auto, unpair;

    public BlinkGateScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private BlinkGateCoreBlockEntity gate() { return menu.machine() instanceof BlinkGateCoreBlockEntity s ? s : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return gate() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        BlinkGateCoreBlockEntity s = gate();
        if (s == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, s.getBlockPos())));
        int x = px(), y = py();
        minus = addRenderableWidget(new WmdUi.KitButton(x + 6, y + BTN_Y, 28, BTN_H, Component.literal("-"), b -> size(s, -1)));
        plus = addRenderableWidget(new WmdUi.KitButton(x + 38, y + BTN_Y, 28, BTN_H, Component.literal("+"), b -> size(s, 1)));
        build = addRenderableWidget(new WmdUi.KitButton(x + 70, y + BTN_Y, 92, BTN_H, Component.translatable(P + "build"), b -> send(s, BlinkGatePayload.A_BUILD, 0)));
        arm = addRenderableWidget(new WmdUi.KitButton(x + 6, y + BTN2_Y, 56, BTN_H, armLabel(s), b -> send(s, open(s) ? BlinkGatePayload.A_CLOSE : BlinkGatePayload.A_ARM, 0)));
        auto = addRenderableWidget(new WmdUi.KitButton(x + 66, y + BTN2_Y, 52, BTN_H, autoLabel(s), b -> {
            int m = s.mode() ^ BlinkGateCoreBlockEntity.AUTO_BIT;
            s.setModeClient(m);
            send(s, BlinkGatePayload.A_AUTO, (m & BlinkGateCoreBlockEntity.AUTO_BIT) != 0 ? 1 : 0);
        }));
        unpair = addRenderableWidget(new WmdUi.KitButton(x + 122, y + BTN2_Y, 40, BTN_H, Component.translatable(P + "unpair"), b -> send(s, BlinkGatePayload.A_UNPAIR, 0)));
    }

    private static boolean open(BlinkGateCoreBlockEntity s) { return s.gateState() >= BlinkGateCoreBlockEntity.S_DEPLOY && s.gateState() <= BlinkGateCoreBlockEntity.S_OPEN; }

    private void size(BlinkGateCoreBlockEntity s, int d) {
        int i = Math.max(0, Math.min(BlinkGateCoreBlockEntity.SIZES.length - 1, s.sizeIndex() + d));
        s.setModeClient((s.mode() & ~3) | i);
        send(s, BlinkGatePayload.A_SIZE, i);
        BlinkGateGhost.show(s.getBlockPos());
    }

    private static void send(BlinkGateCoreBlockEntity s, int action, int value) { PacketDistributor.sendToServer(new BlinkGatePayload(s.getBlockPos(), action, value)); }

    private static Component armLabel(BlinkGateCoreBlockEntity s) { return Component.translatable(P + (open(s) ? "close" : "arm")); }

    private static Component autoLabel(BlinkGateCoreBlockEntity s) { return Component.translatable(P + (s.auto() ? "auto_on" : "auto_off")); }

    private static int kind(int st) {
        return switch (st) {
            case BlinkGateCoreBlockEntity.ST_OPEN, BlinkGateCoreBlockEntity.ST_READY -> WmdUi.K_GOOD;
            case BlinkGateCoreBlockEntity.ST_ARMING, BlinkGateCoreBlockEntity.ST_BUILDING, BlinkGateCoreBlockEntity.ST_COOLING -> WmdUi.K_WARN;
            case BlinkGateCoreBlockEntity.ST_UNBUILT, BlinkGateCoreBlockEntity.ST_NO_PARTNER -> WmdUi.K_INFO;
            default -> WmdUi.K_BAD;
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        BlinkGateCoreBlockEntity s = gate();
        if (s == null) return;
        boolean built = s.formed();
        if (arm != null) { arm.setMessage(armLabel(s)); arm.active = built && s.partner() != null; }
        if (auto != null) auto.setMessage(autoLabel(s));
        if (minus != null) minus.active = !built && s.sizeIndex() > 0;
        if (plus != null) plus.active = !built && s.sizeIndex() < BlinkGateCoreBlockEntity.SIZES.length - 1;
        if (build != null) build.active = !built && s.gateStatus() != BlinkGateCoreBlockEntity.ST_BUILDING;
        if (unpair != null) unpair.active = s.partner() != null;
        int x = px(), y = py(), st = s.gateStatus();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        WmdUi.header(g, font, x, y, PW, new ItemStack(s.getBlockState().getBlock().asItem()), Component.translatable("block.wmd.blink_gate_core"),
                Component.translatable(P + "st." + st), kind(st), st == BlinkGateCoreBlockEntity.ST_ARMING || st == BlinkGateCoreBlockEntity.ST_OPEN);

        WmdUi.well(g, x + 6, y + WELL_Y, PW - 12, 30);
        boolean cool = s.gateState() == BlinkGateCoreBlockEntity.S_COOL && s.coolSecondsLeft() > Balance.BG_STOW / 20;

        int buf = menu.energyCap() > 0 ? (int) Math.min(1000L, (long) menu.energy() * 1000L / menu.energyCap()) : 0;
        double f = cool ? 1.0 - s.coolSecondsLeft() / ((Balance.BG_DOWN + Balance.BG_COOL) / 20.0) : buf / 1000.0;
        int bx0 = x + 10, bx1 = x + PW - 10, by = y + WELL_Y + 16;
        g.fill(bx0, by, bx1, by + 10, C_TRACK);
        g.fill(bx0, by, bx0 + (int) Math.round((bx1 - bx0) * Math.max(0.0, Math.min(1.0, f))), by + 10, cool ? C_COOL : C_BUF);
        int left = s.coolSecondsLeft();
        Component bar = cool ? Component.translatable(P + "cooldown", left) : Component.translatable(P + "buffer", buf / 10);
        WmdUi.drawFit(g, font, bar, x + 10, y + WELL_Y + 5, PW - 20, WmdUi.TEXT);
        int n = s.size();
        int[] need = BlinkGateCoreBlockEntity.needs(n);
        WmdUi.drawFit(g, font, Component.translatable(P + "ring", n, n - 4), x + 6, y + LINE_Y, PW - 12, WmdUi.TEXT);
        int haveC = menu.tankAmount(0), haveS = menu.tankAmount(1);
        WmdUi.drawFit(g, font, built ? Component.translatable(P + "built") : Component.translatable(P + "pieces", haveC, need[0], haveS, need[1]),
                x + 6, y + LINE_Y + 12, PW - 12, built || haveC >= need[0] && haveS >= need[1] ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, s.partner() == null ? Component.translatable(P + "no_partner")
                : Component.translatable(P + "partner", s.partner().getX(), s.partner().getY(), s.partner().getZ()), x + 6, y + LINE_Y + 24, PW - 12,
                s.partner() == null ? WmdUi.TEXT_MUTED : WmdUi.TEXT);
        WmdUi.drawFit(g, font, s.lastWhy() != BlinkGateCoreBlockEntity.WHY_NONE && st == BlinkGateCoreBlockEntity.ST_REFUSED
                ? Component.translatable(P + "why." + s.lastWhy()) : Component.translatable(P + "jumps", s.jumpCount()), x + 6, y + LINE_Y + 36, PW - 12, WmdUi.TEXT_MUTED);
        int ch = s.rfChannel(), rate = s.rfRate();
        WmdUi.drawFit(g, font, ch <= 0 ? Component.translatable(P + "rf_none") : rate > 0 ? Component.translatable(P + "rf", ch, String.format("%,d", rate))
                : Component.translatable(P + "rf_wait", ch), x + 6, y + LINE_Y + 48, PW - 12, rate > 0 ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
        WmdUi.divider(g, x + 6, y + HINT_Y - 4, PW - 12);
        WmdUi.drawFit(g, font, Component.translatable(P + "hint." + st), x + 6, y + HINT_Y, PW - 12, WmdUi.TEXT_MUTED);
    }
}
