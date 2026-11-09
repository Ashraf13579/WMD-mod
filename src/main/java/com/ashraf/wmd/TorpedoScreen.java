package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class TorpedoScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 172;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED;
    private static final String P = "gui.wmd.torp.";

    public TorpedoScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private TorpedoLauncherBlockEntity be() { return menu.machine() instanceof TorpedoLauncherBlockEntity t ? t : null; }

    private boolean deck() { return be() instanceof TorpedoLauncherDeckBlockEntity; }

    private int ph() { return deck() ? 210 : 181; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - ph() - 4)); }

    private int gyroY() { return deck() ? 108 : 79; }

    private int fireY() { return deck() ? 137 : 109; }

    @Override
    protected int sidePanelWidth() { return be() != null ? PW : 0; }

    private void send(int action, int value) {
        TorpedoLauncherBlockEntity t = be();
        if (t != null) PacketDistributor.sendToServer(new TorpedoPayload(t.getBlockPos(), action, value));
    }

    @Override
    protected void init() {
        super.init();
        TorpedoLauncherBlockEntity t = be();
        if (t == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, t.getBlockPos())));
        addActionTab("icon_freq", Component.translatable("gui.wmd.kit.tab.freq"), () -> minecraft.setScreen(new FrequencyScreen(this, t::frequency, f -> send(TorpedoLauncherBlockEntity.A_FREQ, f))));
        int x = px(), y = py();
        int[] dd = { -5, -1, 1, 5 };
        String[] dl = { "-5", "-1", "+1", "+5" };
        for (int i = 0; i < 4; i++) {
            final int d = dd[i];
            addRenderableWidget(new WmdUi.KitButton(x + 6 + i * 40, y + 61, 38, 12, Component.literal(dl[i]), b -> send(TorpedoLauncherBlockEntity.A_DEPTH, Math.max(0, t.depth() + d))));
        }
        if (t instanceof TorpedoLauncherDeckBlockEntity dk) {
            int[] td = { -15, -5, 5, 15 };
            String[] tl = { "<< 15", "< 5", "5 >", "15 >>" };
            for (int i = 0; i < 4; i++) {
                final int d = td[i];
                addRenderableWidget(new WmdUi.KitButton(x + 6 + i * 40, y + 90, 38, 12, Component.literal(tl[i]), b -> send(TorpedoLauncherBlockEntity.A_TRAIN, dk.train() + d)));
            }
        }
        int[] gd = { -15, -5, 5, 15 };
        String[] gl = { "<< 15", "< 5", "5 >", "15 >>" };
        for (int i = 0; i < 4; i++) {
            final int d = gd[i];
            addRenderableWidget(new WmdUi.KitButton(x + 6 + i * 40, y + gyroY() + 11, 38, 12, Component.literal(gl[i]), b -> send(TorpedoLauncherBlockEntity.A_GYRO, t.gyro() + d)));
        }
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + fireY(), PW - 12, 16, Component.translatable(P + "fire"), b -> send(TorpedoLauncherBlockEntity.A_FIRE, 0), true));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        TorpedoLauncherBlockEntity t = be();
        if (t == null) return;
        int x = px(), y = py(), cx = x + PW / 2, ph = ph();
        WmdUi.panel(g, x, y, PW, ph);
        drawSelectedTab(g);
        int st = t.fireStatus();
        int kind = st == TorpedoLauncherBlockEntity.S_READY ? WmdUi.K_GOOD : st == TorpedoLauncherBlockEntity.S_NO_POWER || st == TorpedoLauncherBlockEntity.S_BLOCKED ? WmdUi.K_BAD
                : st == TorpedoLauncherBlockEntity.S_EMPTY ? WmdUi.K_WARN : WmdUi.K_INFO;
        WmdUi.header(g, font, x, y, PW, new ItemStack(t.getBlockState().getBlock().asItem()), Component.translatable(t.getBlockState().getBlock().getDescriptionId()),
                Component.translatable(P + "chip." + st), kind, st == TorpedoLauncherBlockEntity.S_READY);

        int[] k = t.kinds();
        Component load;
        if (k.length == 1) load = k[0] >= 0 ? Component.translatable(P + "loaded_one", Component.translatable("item.wmd." + TorpedoKind.of(k[0]).id)) : Component.translatable(P + "loaded_none");
        else load = Component.translatable(P + "loaded_n", t.loadedCount(), k.length);
        fit(g, load, cx, y + 26, TEXT);
        fit(g, Component.translatable(P + "charge", t.charge01x100()), cx, y + 37, GREY);

        fit(g, t.depth() == 0 ? Component.translatable(P + "depth_launch") : Component.translatable(P + "depth", t.depth()), cx, y + 50, TEXT);
        if (t instanceof TorpedoLauncherDeckBlockEntity dk) {
            int a = dk.train();
            fit(g, a == 0 ? Component.translatable(P + "bearing_ahead") : Component.translatable(a > 0 ? P + "bearing_right" : P + "bearing_left", Math.abs(a)), cx, y + 79, TEXT);
        }

        int gy = t.gyro();
        fit(g, gy == 0 ? Component.translatable(P + "gyro0") : Component.translatable(P + "gyro", (gy > 0 ? "+" : "") + gy), cx, y + gyroY(), TEXT);

        int hy = y + fireY() + 22;
        WmdUi.divider(g, x + 5, hy - 3, PW - 10);
        fit(g, Component.translatable(P + "status." + st), cx, hy + 2, st == TorpedoLauncherBlockEntity.S_READY ? WmdUi.GOOD : GREY);
        fit(g, Component.translatable(P + "hint_redstone"), cx, hy + 14, GREY);
        fit(g, Component.translatable(P + "hint_rack"), cx, hy + 25, GREY);
        fit(g, Component.translatable(P + "freq", t.frequency()), cx, hy + 36, GREY);
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
