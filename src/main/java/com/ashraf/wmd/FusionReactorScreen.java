package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class FusionReactorScreen extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 128, PH = 154;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD;
    private static final int BAR_Y = 40, BAR_H = 8;
    private static final int THR_Y = 86, THR_W = 20, THR_H = 14;
    private static final int GO_Y = 106, GO_H = 18;

    private final List<Object[]> tips = new ArrayList<>();

    public FusionReactorScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override
    protected void init() {
        super.init();
        FusionReactorBlockEntity r = reactor();
        if (r != null) addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, r.getBlockPos())));
    }

    private FusionReactorBlockEntity reactor() { return menu.machine() instanceof FusionReactorBlockEntity r ? r : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return reactor() != null ? PW : 0; }

    private boolean cold(FusionReactorBlockEntity r) { return r.state() == FusionReactorBlockEntity.COLD; }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        tips.clear();
        FusionReactorBlockEntity r = reactor();
        if (r == null) return;
        int x = px(), y = topPos, cx = x + PW / 2, st = r.state();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        centre(g, Component.translatable("gui.wmd.fusion.panel"), cx, y + 6, TEXT);

        int kind = switch (st) {
            case FusionReactorBlockEntity.RUNNING, FusionReactorBlockEntity.RAMP -> WmdUi.K_GOOD;
            case FusionReactorBlockEntity.QUENCH -> WmdUi.K_BAD;
            case FusionReactorBlockEntity.COLD -> r.reason() >= FusionReactorBlockEntity.R_WATER && r.reason() <= FusionReactorBlockEntity.R_EMP ? WmdUi.K_WARN : WmdUi.K_IDLE;
            default -> WmdUi.K_INFO;
        };
        float sp = r.stateProgress();
        Component status = sp >= 0.0F && st != FusionReactorBlockEntity.QUENCH
                ? Component.translatable("gui.wmd.fusion.st." + st + ".pct", Math.round(sp * 100.0F))
                : Component.translatable("gui.wmd.fusion.st." + st);
        int chipW = Math.min(font.width(status), PW - 10 - 14) + 15;
        WmdUi.chipRight(g, font, cx + (chipW + 1) / 2, y + 17, status, kind, st != FusionReactorBlockEntity.COLD, PW - 10);

        centre(g, Component.translatable("gui.wmd.fusion.charge", r.chargePermille() / 10), cx, y + 30, st == FusionReactorBlockEntity.ENERGISING ? WmdUi.BLUE_LIGHT : GREY);
        int bx = x + 6, bw = PW - 12, by = y + BAR_Y;
        WmdUi.well(g, bx, by, bw, BAR_H);
        int fill = Math.round(bw * r.chargePermille() / 1000.0F);
        if (st == FusionReactorBlockEntity.IGNITION || st == FusionReactorBlockEntity.RAMP || st == FusionReactorBlockEntity.RUNNING) fill = bw;
        if (fill > 0) g.fill(bx, by, bx + fill, by + BAR_H, st == FusionReactorBlockEntity.ENERGISING ? WmdUi.BLUE : 0xFF7A4BD8);
        if (mouseX >= bx && mouseX < bx + bw && mouseY >= by - 2 && mouseY < by + BAR_H + 2)
            tips.add(new Object[] { bx, by - 2, bx + bw, by + BAR_H + 2, List.of(Component.translatable("gui.wmd.fusion.charge_tip",
                    String.format("%,d", (long) r.chargePermille() * Balance.FUSION_IGNITION_FE / 1000), String.format("%,d", Balance.FUSION_IGNITION_FE)),
                    Component.translatable("gui.wmd.fusion.charge_how").withStyle(ChatFormatting.GRAY)) });

        boolean making = st == FusionReactorBlockEntity.RAMP || st == FusionReactorBlockEntity.RUNNING || st == FusionReactorBlockEntity.SHUTDOWN;
        centre(g, Component.translatable("gui.wmd.fusion.output", String.format("%,d", making ? r.outputFe() : 0)), cx, y + 54, making ? GOOD : TEXT);
        centre(g, Component.translatable("gui.wmd.fusion.plasma", making ? r.plasmaPct() : 0), cx, y + 64, making ? 0xFFB48CFF : GREY);

        centre(g, Component.translatable("gui.wmd.fusion.throttle"), cx, y + 76, TEXT);
        int my0 = y + THR_Y, mx0 = x + 6, px0 = x + PW - 6 - THR_W;
        boolean hm = in(mouseX, mouseY, mx0, my0, THR_W, THR_H), hp = in(mouseX, mouseY, px0, my0, THR_W, THR_H);
        WmdUi.button(g, font, mx0, my0, THR_W, THR_H, Component.literal("-"), r.throttle() <= 10 ? WmdUi.B_DISABLED : WmdUi.B_NORMAL, hm);
        WmdUi.button(g, font, px0, my0, THR_W, THR_H, Component.literal("+"), r.throttle() >= 100 ? WmdUi.B_DISABLED : WmdUi.B_NORMAL, hp);
        WmdUi.well(g, mx0 + THR_W + 4, my0 + 1, px0 - mx0 - THR_W - 8, THR_H - 2);
        fit(g, Component.literal(r.throttle() + " %"), cx, my0 + 3, px0 - mx0 - THR_W - 12, WmdUi.BLUE_LIGHT);
        if (hm || hp) tips.add(new Object[] { hm ? mx0 : px0, my0, (hm ? mx0 : px0) + THR_W, my0 + THR_H, List.of(Component.translatable("gui.wmd.fusion.throttle_tip"),
                Component.translatable("gui.wmd.fusion.throttle_use", fmt(Balance.FUSION_D_MB * 20 * r.throttle() / 100.0), fmt(Balance.FUSION_T_MB * 20 * r.throttle() / 100.0),
                        fmt(Balance.FUSION_WATER_MB * 20 * r.throttle() / 100.0)).withStyle(ChatFormatting.GRAY)) });

        boolean start = cold(r), busy = st == FusionReactorBlockEntity.SHUTDOWN || st == FusionReactorBlockEntity.QUENCH;
        int gx0 = x + 6, gw = PW - 12, gy0 = y + GO_Y;
        boolean gh = in(mouseX, mouseY, gx0, gy0, gw, GO_H);
        WmdUi.button(g, font, gx0, gy0, gw, GO_H, Component.empty(), busy ? WmdUi.B_DISABLED : start ? WmdUi.B_NORMAL : WmdUi.B_DANGER, gh && !busy);
        if (gh && !start && !busy) g.fill(gx0 + 1, gy0 + 1, gx0 + gw - 1, gy0 + GO_H - 1, 0x22FFFFFF);
        fit(g, Component.translatable(start ? "gui.wmd.fusion.start" : "gui.wmd.fusion.stop").withStyle(ChatFormatting.BOLD), cx, gy0 + 5, gw - 4,
                busy ? WmdUi.TEXT_DIM : start ? GOOD : TEXT);
        if (gh) tips.add(new Object[] { gx0, gy0, gx0 + gw, gy0 + GO_H, List.of(Component.translatable(start ? "gui.wmd.fusion.start" : "gui.wmd.fusion.stop"),
                Component.translatable(start ? "gui.wmd.fusion.start_tip" : "gui.wmd.fusion.stop_tip", String.format("%,d", Balance.FUSION_START_WATER_MB),
                        String.format("%,d", Balance.FUSION_START_FUEL_MB)).withStyle(ChatFormatting.GRAY)) });

        Component why = null;
        int rs = r.reason();
        if (st == FusionReactorBlockEntity.QUENCH || st == FusionReactorBlockEntity.COLD && rs >= FusionReactorBlockEntity.R_WATER && rs <= FusionReactorBlockEntity.R_EMP)
            why = Component.translatable("gui.wmd.fusion.why." + rs);
        else if (st == FusionReactorBlockEntity.ENERGISING) why = Component.translatable("gui.wmd.fusion.wait_charge");
        if (why != null) {
            g.pose().pushPose();
            int lineY = y + 130;
            for (var seq : font.split(why, PW - 10)) {
                int w = font.width(seq);
                g.drawString(font, seq, cx - w / 2, lineY, st == FusionReactorBlockEntity.ENERGISING ? GREY : BAD, false);
                lineY += 10;
                if (lineY > y + PH - 10) break;
            }
            g.pose().popPose();
        }
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private static String fmt(double v) { return v == Math.rint(v) ? String.format("%,d", (long) v) : String.format("%.1f", v); }

    private void centre(GuiGraphics g, Component c, int cx, int y, int colour) { fit(g, c, cx, y, PW - 10, colour); }

    private void fit(GuiGraphics g, Component text, int cx, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        for (Object[] tp : tips) {
            if (mouseX >= (int) tp[0] && mouseX < (int) tp[2] && mouseY >= (int) tp[1] && mouseY < (int) tp[3]) {
                @SuppressWarnings("unchecked") List<Component> lines = (List<Component>) tp[4];
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= topPos && my < topPos + PH; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        FusionReactorBlockEntity r = reactor();
        if (r != null && inPanel(mx, my)) {
            int x = px(), y = topPos, pick = -1;
            int my0 = y + THR_Y, mx0 = x + 6, px0 = x + PW - 6 - THR_W;
            if (in((int) mx, (int) my, mx0, my0, THR_W, THR_H) && r.throttle() > 10) pick = r.throttle() / 10 - 2;
            if (in((int) mx, (int) my, px0, my0, THR_W, THR_H) && r.throttle() < 100) pick = r.throttle() / 10;
            boolean busy = r.state() == FusionReactorBlockEntity.SHUTDOWN || r.state() == FusionReactorBlockEntity.QUENCH;
            if (in((int) mx, (int) my, x + 6, y + GO_Y, PW - 12, GO_H) && !busy) pick = cold(r) ? FusionReactorBlockEntity.MODE_START : FusionReactorBlockEntity.MODE_STOP;
            if (pick >= 0) {
                r.setModeClient(pick);
                PacketDistributor.sendToServer(new SetMachineModePayload(r.getBlockPos(), pick));
                if (minecraft != null) {
                    if (pick == FusionReactorBlockEntity.MODE_STOP) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.STONE_BUTTON_CLICK_ON, 1.0F));
                    else WmdUi.click();
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPanel(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }
}
