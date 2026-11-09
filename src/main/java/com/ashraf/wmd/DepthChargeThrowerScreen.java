package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class DepthChargeThrowerScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 172, PH = 142;
    private static final String P = "gui.wmd.kgun.";

    public DepthChargeThrowerScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private DepthChargeThrowerBlockEntity be() { return menu.machine() instanceof DepthChargeThrowerBlockEntity k ? k : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return be() != null ? PW : 0; }

    private void send(int action, int value) {
        DepthChargeThrowerBlockEntity k = be();
        if (k != null) PacketDistributor.sendToServer(new ThrowerPayload(k.getBlockPos(), action, value));
    }

    @Override
    protected void init() {
        super.init();
        DepthChargeThrowerBlockEntity k = be();
        if (k == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, k.getBlockPos())));
        addActionTab("icon_freq", Component.translatable("gui.wmd.kit.tab.freq"), () -> minecraft.setScreen(new FrequencyScreen(this, k::frequency, f -> send(DepthChargeThrowerBlockEntity.A_FREQ, f))));
        int x = px(), y = py();
        int[] dd = { -10, -1, 1, 10 };
        String[] dl = { "-10", "-1", "+1", "+10" };
        for (int i = 0; i < 4; i++) {
            final int d = dd[i];
            addRenderableWidget(new WmdUi.KitButton(x + 6 + i * 40, y + 51, 38, 12, Component.literal(dl[i]), b -> send(DepthChargeThrowerBlockEntity.A_DEPTH, k.depth() + d)));
        }
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 70, PW - 12, 16, Component.translatable(P + "throw"), b -> send(DepthChargeThrowerBlockEntity.A_FIRE, 0), true));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        DepthChargeThrowerBlockEntity k = be();
        if (k == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        boolean reloading = k.getLevel() != null && KGunAnim.reload01(k, k.getLevel().getGameTime()) >= 0;
        String chip = k.arborShown() ? "chip_ready" : reloading ? "chip_reload" : "chip_empty";
        WmdUi.header(g, font, x, y, PW, new ItemStack(k.getBlockState().getBlock().asItem()), Component.translatable(k.getBlockState().getBlock().getDescriptionId()),
                Component.translatable(P + chip), k.arborShown() ? WmdUi.K_GOOD : reloading ? WmdUi.K_INFO : WmdUi.K_WARN, reloading);
        Component spare = Component.translatable(P + (k.spareMine() >= 0 ? "spare_mine" : k.spareShown() ? "spare" : "no_spare"));
        int mine = k.arborMine();
        if (mine >= 0) {
            net.minecraft.world.item.Item want = LaidMineEntity.torpedoFor(mine);
            Component torp = want == null ? Component.empty() : Component.translatable(P + (k.arborMineLoaded() ? "mine_t" : "mine_not"), Component.translatable(want.getDescriptionId()));
            fit(g, Component.translatable(P + "loaded_mine", Component.translatable(mineKey(mine)), torp, spare), cx, y + 26, k.arborMineLoaded() || want == null ? WmdUi.TEXT : WmdUi.WARN);
            fit(g, mine == SeaMineBlockEntity.K_BOTTOM ? Component.translatable(P + "mine_bottom") : Component.translatable(P + "mine_depth", k.mineDepth()), cx, y + 38, WmdUi.TEXT);
        } else {
            fit(g, Component.translatable(P + (k.arborShown() ? "loaded" : "unloaded"), spare), cx, y + 26, WmdUi.TEXT);
            fit(g, Component.translatable(P + "depth", k.depth()), cx, y + 38, WmdUi.TEXT);
        }
        fit(g, k.frequency() <= 0 ? Component.translatable(P + "no_freq") : Component.translatable(P + (mine >= 0 ? "freq_mine" : "freq"), String.format("%04d", k.frequency())), cx, y + 90, WmdUi.TEXT_MUTED);
        int hy = y + 104;
        WmdUi.divider(g, x + 5, hy - 3, PW - 10);
        fit(g, Component.translatable(P + "hint_place"), cx, hy + 2, WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "hint_rack"), cx, hy + 13, WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "hint_redstone"), cx, hy + 24, WmdUi.TEXT_MUTED);
    }

    static String mineKey(int kind) {
        return kind == SeaMineBlockEntity.K_BOTTOM ? "block.wmd.mine_bottom" : kind == SeaMineBlockEntity.K_CAPTOR ? "block.wmd.mine_captor" : "block.wmd.mine_influence";
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
}
