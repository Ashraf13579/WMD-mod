package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class SamSiteScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 160, PH = 146, WELL_Y = 28, LINE_Y = 82, HINT_Y = 108, BTN_Y = 124, BTN_H = 16;
    private static final int C_LOADED = 0xFF4AA3FF, C_EMPTY = 0xFF1A2129, C_RIM = 0xFF6F7C8A;
    private static final String P = "gui.wmd.sam.";
    private WmdUi.KitButton salvo, hold;

    public SamSiteScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private SamSiteBlockEntity sam() { return menu.machine() instanceof SamSiteBlockEntity s ? s : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return sam() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        SamSiteBlockEntity s = sam();
        if (s == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, s.getBlockPos())));
        salvo = addRenderableWidget(new WmdUi.KitButton(px() + 6, py() + BTN_Y, 72, BTN_H, salvoLabel(s), b -> send(s, s.mode() ^ 1)));
        hold = addRenderableWidget(new WmdUi.KitButton(px() + 82, py() + BTN_Y, 72, BTN_H, holdLabel(s), b -> send(s, s.mode() ^ 2)));
    }

    private void send(SamSiteBlockEntity s, int next) {
        s.setModeClient(next);
        PacketDistributor.sendToServer(new SetMachineModePayload(s.getBlockPos(), next));
        if (salvo != null) salvo.setMessage(salvoLabel(s));
        if (hold != null) hold.setMessage(holdLabel(s));
    }

    private static Component salvoLabel(SamSiteBlockEntity s) { return Component.translatable(s.salvoSize() == 2 ? P + "salvo2" : P + "salvo1"); }

    private static Component holdLabel(SamSiteBlockEntity s) { return Component.translatable(s.hold() ? P + "hold" : P + "free"); }

    private static int kind(int st) {
        return switch (st) {
            case SamSiteBlockEntity.ST_READY -> WmdUi.K_GOOD;
            case SamSiteBlockEntity.ST_TRACKING, SamSiteBlockEntity.ST_FIRING, SamSiteBlockEntity.ST_GUIDING -> WmdUi.K_WARN;
            case SamSiteBlockEntity.ST_HOLD -> WmdUi.K_INFO;
            default -> WmdUi.K_BAD;
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        SamSiteBlockEntity s = sam();
        if (s == null) return;
        if (salvo != null) salvo.setMessage(salvoLabel(s));
        if (hold != null) hold.setMessage(holdLabel(s));
        int x = px(), y = py(), st = s.samStatus();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        boolean busy = st == SamSiteBlockEntity.ST_FIRING || st == SamSiteBlockEntity.ST_GUIDING;
        WmdUi.header(g, font, x, y, PW, new ItemStack(s.getBlockState().getBlock().asItem()), Component.translatable("block.wmd.sam_site"),
                Component.translatable(P + "st." + st), kind(st), busy);

        WmdUi.well(g, x + 6, y + WELL_Y, PW - 12, 48);
        int mask = s.loadedMask();
        for (int i = 0; i < 4; i++) {
            int cx = x + 20 + (i % 2) * 22, cy = y + WELL_Y + (i < 2 ? 35 : 13);
            disc(g, cx, cy, 9, C_RIM);
            disc(g, cx, cy, 7, (mask >> i & 1) != 0 ? C_LOADED : C_EMPTY);
        }
        int tx = x + 62, tw = PW - 12 - 56 - 4;
        WmdUi.drawFit(g, font, Component.translatable(P + "rounds", s.roundsLoaded()), tx, y + WELL_Y + 6, tw, WmdUi.TEXT);
        WmdUi.drawFit(g, font, Component.translatable(P + "salvo_line", s.salvoSize()), tx, y + WELL_Y + 18, tw, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "freq_line", s.getFrequency()), tx, y + WELL_Y + 30, tw, WmdUi.TEXT_MUTED);

        WmdUi.drawFit(g, font, Component.translatable(P + "tracks", s.trackCount()), x + 6, y + LINE_Y, PW - 12, WmdUi.TEXT);
        int d = s.targetDistance();
        WmdUi.drawFit(g, font, d < 0 ? Component.translatable(P + "no_target") : Component.translatable(P + "target", String.format("%,d", d)), x + 6, y + LINE_Y + 12, PW - 12,
                d < 0 ? WmdUi.TEXT_MUTED : WmdUi.TEXT);
        WmdUi.divider(g, x + 6, y + HINT_Y - 4, PW - 12);
        WmdUi.drawFit(g, font, Component.translatable(P + "hint." + st, s.getFrequency()), x + 6, y + HINT_Y, PW - 12, WmdUi.TEXT_MUTED);
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, col);
        }
    }
}
