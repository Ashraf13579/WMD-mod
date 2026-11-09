package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class SeaMineScreen extends Screen {

    static final int PW = 196, PH = 150;
    private static final String P = "gui.wmd.seamine.";
    private final BlockPos pos;

    public SeaMineScreen(BlockPos pos) {
        super(Component.translatable(P + "title"));
        this.pos = pos;
    }

    static void open(BlockPos pos) { Minecraft.getInstance().setScreen(new SeaMineScreen(pos)); }

    private SeaMineBlockEntity be() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof SeaMineBlockEntity m ? m : null;
    }

    String page() {
        SeaMineBlockEntity m = be();
        return m == null ? "sea_mines" : m.kind() == SeaMineBlockEntity.K_BOTTOM ? "mine_bottom" : m.kind() == SeaMineBlockEntity.K_CAPTOR ? "mine_captor" : "mine_influence";
    }

    private int px() { return (width - PW) / 2; }

    private int py() { return (height - PH) / 2; }

    private void send(int action, int value) { PacketDistributor.sendToServer(new SeaMinePayloads.Act(pos, action, value)); }

    @Override
    protected void init() {
        SeaMineBlockEntity m = be();
        if (m == null) return;
        int x = px(), y = py();
        addRenderableWidget(new WmdUi.TabButton(x - 23, y + 4, "icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), b -> minecraft.setScreen(new SecurityScreen(this, pos))));
        if (m.moored()) {
            int[] dd = { -10, -1, 1, 10 };
            String[] dl = { "-10", "-1", "+1", "+10" };
            for (int i = 0; i < 4; i++) {
                final int d = dd[i];
                addRenderableWidget(new WmdUi.KitButton(x + 8 + i * 46, y + 64, 42, 12, Component.literal(dl[i]), b -> { SeaMineBlockEntity n = be(); if (n != null) send(SeaMineBlockEntity.A_DEPTH, n.depth() + d); }));
            }
        }
        addRenderableWidget(new WmdUi.KitButton(x + 8, y + PH - 22, PW - 16, 14, Component.translatable("gui.wmd.security.back"), b -> onClose()));
    }

    @Override
    public void tick() {
        super.tick();
        if (be() == null) onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        SeaMineBlockEntity m = be();
        if (m == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        boolean armed = m.ready() && m.armed();
        Component chip = Component.translatable(P + (!m.ready() ? "chip_empty" : armed ? "chip_armed" : "chip_arming"));
        WmdUi.header(g, font, x, y, PW, new ItemStack(m.getBlockState().getBlock().asItem()), Component.translatable(m.getBlockState().getBlock().getDescriptionId()),
                chip, !m.ready() ? WmdUi.K_WARN : armed ? WmdUi.K_BAD : WmdUi.K_INFO, armed);
        String k = m.kind() == SeaMineBlockEntity.K_BOTTOM ? "bottom" : m.kind() == SeaMineBlockEntity.K_CAPTOR ? "captor" : "influence";
        fit(g, Component.translatable(P + "what_" + k, (int) Balance.MINE_INF_TRIGGER, (int) Balance.MINE_INF_REACH, (int) Balance.MINE_BOT_LISTEN, (int) Balance.MINE_CAP_RANGE), cx, y + 26, WmdUi.TEXT);
        if (m.moored()) {
            double eye = m.eyeY();
            int surf = NavalLayer.waterTop(m.getLevel(), pos.getX(), pos.getZ());
            int really = Double.isNaN(eye) || surf == NavalLayer.NO_WATER ? m.depth() : (int) Math.round(surf + 1.0 - m.caseAt().y);
            int cable = Double.isNaN(eye) ? 0 : (int) Math.round(eye - pos.getY() - SeaMineBlockEntity.FAIRLEAD_PX / 16.0);
            fit(g, Component.translatable(P + "depth", m.depth()), cx, y + 38, WmdUi.TEXT);
            fit(g, Component.translatable(P + "floats", really, cable), cx, y + 50, really != m.depth() ? WmdUi.TEXT_MUTED : WmdUi.TEXT_DIM);
        } else fit(g, Component.translatable(P + "seabed"), cx, y + 38, WmdUi.TEXT_MUTED);
        if (m.warheadItem() != null)
            fit(g, Component.translatable(m.loaded() ? P + "loaded" : P + "needs", Component.translatable(m.warheadItem().getDescriptionId())), cx, y + (m.moored() ? 80 : 54), m.loaded() ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
        int hy = y + 94;
        WmdUi.divider(g, x + 5, hy - 3, PW - 10);
        fit(g, Component.translatable(P + "hint_arm", Balance.SEAMINE_ARM_TICKS / 20), cx, hy + 2, WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "hint_friends"), cx, hy + 13, WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "hint_take"), cx, hy + 24, WmdUi.TEXT_MUTED);
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) {
        int maxW = PW - 12, w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
