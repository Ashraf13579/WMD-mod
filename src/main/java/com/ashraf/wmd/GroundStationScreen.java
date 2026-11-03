package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class GroundStationScreen extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 132, PH = 244;

    private static final int ENEMY_Y = 140, EROW_Y = 152, EROW_H = 26, HACK_W = 34;
    private final Button[] hackButtons = new Button[3];

    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, BLUE = WmdUi.BLUE_LIGHT;
    private static final int ROW_Y = 46, ROW_H = 30;

    public GroundStationScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private GroundStationBlockEntity station() { return menu.machine() instanceof GroundStationBlockEntity s ? s : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return station() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        if (menu.machine() instanceof BlockEntity be) {
            addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, be.getBlockPos())));
        }
        for (int i = 0; i < hackButtons.length; i++) {
            final int row = i;
            hackButtons[i] = addRenderableWidget(new WmdUi.KitButton(px() + PW - 6 - HACK_W, py() + EROW_Y + i * EROW_H - 2, HACK_W, 12,
                    Component.translatable("gui.wmd.ground_station.hack"), b -> hack(row), true));
            hackButtons[i].visible = false;
        }
    }

    private List<OrbitMath.Sat> enemies(GroundStationBlockEntity st) {
        List<OrbitMath.Sat> l = new ArrayList<>();
        if (st.getOwner() != null) for (OrbitMath.Sat s : OrbitView.sats()) if (!st.getOwner().equals(s.owner())) l.add(s);
        return l.size() > hackButtons.length ? l.subList(0, hackButtons.length) : l;
    }

    private void hack(int row) {
        GroundStationBlockEntity st = station();
        if (st == null) return;
        List<OrbitMath.Sat> l = enemies(st);
        if (row < l.size()) PacketDistributor.sendToServer(new S5Payload(st.getBlockPos(), S5Payload.HACK, l.get(row).id()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        GroundStationBlockEntity st = station();
        if (st == null) return;
        List<OrbitMath.Sat> l = enemies(st);
        boolean owner = minecraft.player != null && minecraft.player.getUUID().equals(st.getOwner());
        long now = OrbitView.now();
        double sx = st.getBlockPos().getX() + 0.5, sz = st.getBlockPos().getZ() + 0.5;
        for (int i = 0; i < hackButtons.length; i++) {
            Button b = hackButtons[i];
            OrbitMath.Sat s = i < l.size() ? l.get(i) : null;
            b.visible = s != null && owner;
            b.active = s != null && owner && st.isRunning() && st.hackSat() == 0 && !st.triedSat(s.id()) && OrbitMath.isOverhead(s, sx, sz, now);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        GroundStationBlockEntity st = station();
        if (st == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        fit(g, Component.translatable("gui.wmd.ground_station.panel"), cx, y + 6, TEXT);
        fit(g, Component.translatable("gui.wmd.ground_station.orbit", OrbitView.count(), Balance.SAT_MAX), cx, y + 18, GREY);
        boolean online = st.isRunning();
        if (online && st.isJamming()) fit(g, Component.translatable("gui.wmd.ground_station.jamming"), cx, y + 30, BLUE);
        else fit(g, Component.translatable(online ? "gui.wmd.ground_station.online" : "gui.wmd.ground_station.offline"), cx, y + 30, online ? GOOD : BAD);

        List<OrbitMath.Sat> mine = new ArrayList<>();
        if (st.getOwner() != null) for (OrbitMath.Sat s : OrbitView.sats()) if (st.getOwner().equals(s.owner())) mine.add(s);
        mine.sort(java.util.Comparator.comparing(OrbitMath.Sat::isCubesat));
        enemyPanel(g, st, x, y, cx);
        if (mine.isEmpty()) {
            fit(g, Component.translatable("gui.wmd.ground_station.none"), cx, y + ROW_Y + 10, GREY);
            return;
        }
        long now = OrbitView.now();
        double sx = st.getBlockPos().getX() + 0.5, sz = st.getBlockPos().getZ() + 0.5;
        for (int i = 0; i < Math.min(mine.size(), Balance.SAT_MAX) && i < 3; i++) {
            OrbitMath.Sat s = mine.get(i);
            int ry = y + ROW_Y + i * ROW_H;
            WmdUi.divider(g, x + 5, ry - 4, PW - 10);
            long left = Math.max(0L, s.endMs() - now);
            long h = left / 3_600_000L;
            Component life = s.isCubesat() ? Component.literal(OrbitMath.mmss(left))
                    : Component.translatable("gui.wmd.ground_station.life", h / 24, h % 24);
            fit(g, Component.translatable("gui.wmd.ground_station.row", OrbitData.typeName(s.type()), s.id(), life), cx, ry, TEXT);
            long over = OrbitMath.overheadLeft(s, sx, sz, now);
            if (over > 0) fit(g, Component.translatable("gui.wmd.ground_station.overhead", OrbitMath.mmss(over)), cx, ry + 11, online ? GOOD : BLUE);
            else fit(g, Component.translatable("gui.wmd.ground_station.next", OrbitMath.mmss(OrbitMath.nextPass(s, sx, sz, now))), cx, ry + 11, GREY);
        }
    }

    private void enemyPanel(GuiGraphics g, GroundStationBlockEntity st, int x, int y, int cx) {
        WmdUi.divider(g, x + 5, y + ENEMY_Y - 4, PW - 10);
        fit(g, Component.translatable("gui.wmd.ground_station.enemies"), cx, y + ENEMY_Y, TEXT);
        List<OrbitMath.Sat> l = enemies(st);
        if (l.isEmpty()) { fit(g, Component.translatable("gui.wmd.ground_station.no_enemies"), cx, y + EROW_Y + 4, GREY); return; }
        long now = OrbitView.now();
        double sx = st.getBlockPos().getX() + 0.5, sz = st.getBlockPos().getZ() + 0.5;
        int textW = PW - 14 - HACK_W - 4;
        for (int i = 0; i < l.size(); i++) {
            OrbitMath.Sat s = l.get(i);
            int ry = y + EROW_Y + i * EROW_H;
            drawLeft(g, Component.translatable("gui.wmd.ground_station.enemy_row", OrbitData.typeName(s.type()), s.id(), s.ownerName()), x + 7, ry, textW, TEXT);
            if (st.hackSat() == s.id()) {
                float u = Math.min(1.0F, st.hackTicks() / (float) Math.max(1, Balance.SAT_HACK_SECONDS * 20));
                int bx0 = x + 7, bx1 = x + PW - 7, by = ry + 11;
                WmdUi.well(g, bx0, by, bx1 - bx0, 7);
                g.fill(bx0 + 1, by + 1, bx0 + 1 + Math.round((bx1 - bx0 - 2) * u), by + 6, 0xFFD04030);
                fit(g, Component.translatable("gui.wmd.ground_station.hacking", Math.round(u * 100)), cx, by, TEXT);
                continue;
            }
            long over = OrbitMath.overheadLeft(s, sx, sz, now);
            Component second = st.triedSat(s.id()) ? Component.translatable("gui.wmd.ground_station.tried")
                    : over > 0 ? Component.translatable("gui.wmd.ground_station.overhead", OrbitMath.mmss(over))
                    : Component.translatable("gui.wmd.ground_station.next", OrbitMath.mmss(OrbitMath.nextPass(s, sx, sz, now)));
            drawLeft(g, second, x + 7, ry + 11, PW - 14, over > 0 && !st.triedSat(s.id()) ? BAD : GREY);
        }
        fit(g, Component.translatable("gui.wmd.ground_station.hack_needs", Balance.SAT_HACK_SECONDS), cx, y + PH - 12, GREY);
    }

    private void drawLeft(GuiGraphics g, Component text, int x, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, x, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) {
        int maxW = PW - 10;
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
