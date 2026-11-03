package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class CcScreenS5 extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 184, PH = 222;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, BLUE = WmdUi.BLUE_LIGHT;
    private static final String P = "gui.wmd.cc.intsat.";
    private static final int ROW_Y = 40, ROW_H = 21, ENGAGE_Y = 106, BTN_Y = 128, LOG_Y = 150;

    private Button engageBtn;
    private int headChipX = Integer.MAX_VALUE, headY;
    private Component headState = Component.empty();

    public CcScreenS5(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private CcInterceptorConsoleBlockEntity be() { return menu.machine() instanceof CcInterceptorConsoleBlockEntity c ? c : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return be() != null ? PW : 0; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    private boolean owner() {
        CcInterceptorConsoleBlockEntity b = be();
        return b != null && minecraft != null && minecraft.player != null && minecraft.player.getUUID().equals(b.getOwner());
    }

    @Override
    protected void init() {
        super.init();
        CcInterceptorConsoleBlockEntity b = be();
        if (b == null) return;
        int x = px(), y = py();
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, b.getBlockPos())));
        engageBtn = addRenderableWidget(new WmdUi.KitButton(x + 6, y + BTN_Y, PW - 12, 16, Component.empty(), w -> toggle()));
        updateButtons();
    }

    private void toggle() {
        CcInterceptorConsoleBlockEntity b = be();
        if (b == null || !owner()) return;
        int next = b.mode() == SatDefence.MODE_ANY ? SatDefence.MODE_ZONE : SatDefence.MODE_ANY;
        PacketDistributor.sendToServer(CcBPayload.of(b.getBlockPos(), CcBPayload.ENGAGE, next, 0, 0));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    private void updateButtons() {
        CcInterceptorConsoleBlockEntity b = be();
        if (b == null || engageBtn == null) return;
        engageBtn.active = owner() && b.isOnline();
        engageBtn.setMessage(Component.translatable(b.mode() == SatDefence.MODE_ANY ? P + "set_zone" : P + "set_any"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        if (be() != null && headChipX == px() + PW - 17 && WmdUi.over(mouseX, mouseY, headChipX, headY + 6, 11, 11)) g.renderTooltip(font, headState, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        CcInterceptorConsoleBlockEntity b = be();
        if (b == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        headY = y;
        headState = Component.translatable(b.isOnline() ? "gui.wmd.cc.online" : "gui.wmd.cc.offline");
        headChipX = WmdUi.header(g, font, x, y, PW, new ItemStack(b.getBlockState().getBlock().asItem()), Component.translatable(b.kind().langKey()),
                headState, b.isOnline() ? WmdUi.K_GOOD : WmdUi.K_BAD, !b.isOnline());
        fit(g, Component.translatable(P + "sats"), cx, y + 28, TEXT);
        sats(g, b, x, y, cx);
        rule(g, x, y + ENGAGE_Y - 3);
        boolean any = b.effectiveMode() == SatDefence.MODE_ANY;
        Component eng = !b.isOnline() ? Component.translatable(P + "engage_offline")
                : any ? Component.translatable(P + "engage_any") : Component.translatable(P + "engage_zone", String.format("%,d", Balance.INT_SAT_ZONE));
        fit(g, eng, cx, y + ENGAGE_Y, any ? BAD : BLUE);
        fit(g, Component.translatable(P + "engage_hint"), cx, y + ENGAGE_Y + 11, GREY);
        rule(g, x, y + LOG_Y - 3);
        fit(g, Component.translatable(P + "log"), cx, y + LOG_Y, TEXT);
        log(g, b, x, y, cx);
    }

    private void rule(GuiGraphics g, int x, int y) { WmdUi.divider(g, x + 5, y - 1, PW - 10); }

    private void sats(GuiGraphics g, CcInterceptorConsoleBlockEntity b, int x, int y, int cx) {
        List<CompoundTag> rows = new ArrayList<>();
        for (Tag t : b.rows()) if (t instanceof CompoundTag c) rows.add(c);
        if (rows.isEmpty()) { fit(g, Component.translatable(P + "none"), cx, y + ROW_Y + 8, GREY); return; }
        long now = OrbitView.now();
        double bx = b.getBlockPos().getX() + 0.5, bz = b.getBlockPos().getZ() + 0.5;
        for (int i = 0; i < Math.min(3, rows.size()); i++) {
            CompoundTag c = rows.get(i);
            int ry = y + ROW_Y + i * ROW_H;
            OrbitMath.Sat s = null;
            for (OrbitMath.Sat o : OrbitView.sats()) if (o.id() == c.getInt("id")) s = o;
            long shot = c.getLong("shot"), ready = shot <= 0L ? 0L : Math.max(0L, shot + OrbitMath.periodMs() - OrbitMath.windowMs() - now);
            boolean ctl = c.getBoolean("ctl");
            WmdUi.led(g, x + 8, ry + 1, !ctl ? WmdUi.K_IDLE : ready <= 0 ? WmdUi.K_GOOD : WmdUi.K_BAD, false);
            Component charge = !ctl ? Component.translatable(P + "no_control") : ready <= 0 ? Component.translatable(P + "charged")
                    : Component.translatable(P + "spent", OrbitMath.mmss(ready));
            drawLeft(g, Component.translatable(P + "row", c.getInt("id"), charge, c.getInt("hits"), c.getInt("miss")), x + 16, ry, PW - 22,
                    !ctl ? GREY : ready <= 0 ? GOOD : BAD);
            if (s == null) continue;
            long over = OrbitMath.overheadLeft(s, bx, bz, now);
            Component pass = over > 0 ? Component.translatable(P + "over", OrbitMath.mmss(over)) : Component.translatable(P + "next", OrbitMath.mmss(OrbitMath.nextPass(s, bx, bz, now)));
            drawLeft(g, pass, x + 16, ry + 10, PW - 22, over > 0 ? GOOD : GREY);
        }
    }

    private void log(GuiGraphics g, CcInterceptorConsoleBlockEntity b, int x, int y, int cx) {
        List<CompoundTag> l = new ArrayList<>();
        for (Tag t : b.log()) if (t instanceof CompoundTag c) l.add(c);
        if (l.isEmpty()) { fit(g, Component.translatable(P + "log_none"), cx, y + LOG_Y + 13, GREY); return; }
        java.time.format.DateTimeFormatter hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        int ly = y + LOG_Y + 12;
        for (int i = l.size() - 1; i >= 0 && ly <= y + PH - 12; i--) {
            CompoundTag e = l.get(i);
            String time = hm.format(java.time.Instant.ofEpochMilli(e.getLong("ms")).atZone(java.time.ZoneId.systemDefault()));
            boolean hit = e.getBoolean("hit");
            drawLeft(g, Component.translatable(hit ? P + "log_kill" : P + "log_miss", time, e.getInt("sat"), e.getString("by"), e.getInt("x"), e.getInt("z")),
                    x + 8, ly, PW - 16, hit ? GOOD : BAD);
            ly += 10;
        }
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
}
