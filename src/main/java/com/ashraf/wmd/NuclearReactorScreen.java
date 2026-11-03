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

public class NuclearReactorScreen extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 128, PH = 154;

    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, AMBER = WmdUi.WARN;
    private static final int BAR_Y = 29, BAR_H = 8;
    private static final int BTN_Y = 84, BTN_W = 19, BTN_H = 14, BTN_GAP = 1;
    private static final int SCRAM_Y = 103, SCRAM_H = 18;

    private final List<Object[]> tips = new ArrayList<>();

    public NuclearReactorScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override
    protected void init() {
        super.init();
        NuclearReactorBlockEntity r = reactor();
        if (r != null) addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, r.getBlockPos())));
    }

    private NuclearReactorBlockEntity reactor() { return menu.machine() instanceof NuclearReactorBlockEntity r ? r : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return reactor() != null ? PW : 0; }

    private int btnX(int i) { return px() + (PW - (5 * BTN_W + 4 * BTN_GAP)) / 2 + i * (BTN_W + BTN_GAP); }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        tips.clear();
        NuclearReactorBlockEntity r = reactor();
        if (r == null) return;
        int x = px(), y = topPos, cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        centre(g, Component.translatable("gui.wmd.reactor.panel"), cx, y + 6, TEXT);

        int heat = r.heat(), st = r.stage();
        int heatCol = st >= NuclearReactorBlockEntity.STEAM ? BAD : st >= NuclearReactorBlockEntity.ALARM ? AMBER : TEXT;
        centre(g, Component.translatable("gui.wmd.reactor.heat", heat, Balance.REACTOR_HEAT_MAX), cx, y + 18, heatCol);
        int bx = x + 6, bw = PW - 12, by = y + BAR_Y;
        WmdUi.well(g, bx, by, bw, BAR_H);
        int fill = Math.round(bw * Math.min(1.0F, heat / (float) Balance.REACTOR_HEAT_MAX));
        int barCol = st >= NuclearReactorBlockEntity.IMMINENT ? 0xFFE02020 : st >= NuclearReactorBlockEntity.STEAM ? 0xFFE0602A : st >= NuclearReactorBlockEntity.ALARM ? 0xFFE0A030 : 0xFF35C24A;
        if (fill > 0) g.fill(bx, by, bx + fill, by + BAR_H, barCol);
        int[] marks = { Balance.REACTOR_HEAT_ALARM, Balance.REACTOR_HEAT_STEAM, Balance.REACTOR_HEAT_IMMINENT };
        int[] markCols = { 0xFFE0A030, 0xFFE0602A, 0xFFE02020 };
        for (int k = 0; k < marks.length; k++) {
            int mx = bx + Math.round(bw * marks[k] / (float) Balance.REACTOR_HEAT_MAX);
            g.fill(mx, by - 2, mx + 1, by + BAR_H + 2, markCols[k]);
        }
        if (mouseX >= bx && mouseX < bx + bw && mouseY >= by - 2 && mouseY < by + BAR_H + 2)
            tips.add(new Object[] { bx, by - 2, bx + bw, by + BAR_H + 2, List.of(Component.translatable("gui.wmd.reactor.heat", heat, Balance.REACTOR_HEAT_MAX),
                    Component.translatable("gui.wmd.reactor.marks", Balance.REACTOR_HEAT_ALARM, Balance.REACTOR_HEAT_STEAM, Balance.REACTOR_HEAT_IMMINENT).withStyle(ChatFormatting.GRAY)) });

        centre(g, Component.translatable("gui.wmd.reactor.loop", r.steamOutRate(), r.waterInRate()), cx, y + 43, r.steamOutRate() > 0 ? GOOD : TEXT);
        boolean coolBad = !r.pumpsOn() || (menu.running() && r.coolPct() < 100);
        centre(g, Component.translatable(r.pumpsOn() ? "gui.wmd.reactor.cooling_pumps" : "gui.wmd.reactor.cooling_nopower", r.coolPct()), cx, y + 53, coolBad ? BAD : TEXT);
        centre(g, Component.translatable("gui.wmd.reactor.safe_max", r.safeMaxPct()), cx, y + 63, r.levelPct() > r.safeMaxPct() ? BAD : GOOD);

        centre(g, Component.translatable("gui.wmd.reactor.rods"), cx, y + 74, TEXT);
        for (int i = 0; i <= 4; i++) {
            int x0 = btnX(i), y0 = y + BTN_Y;
            boolean picked = r.rodLevel() == i && !r.isDamaged();
            boolean hover = mouseX >= x0 && mouseX < x0 + BTN_W && mouseY >= y0 && mouseY < y0 + BTN_H;
            boolean unsafe = i * 25 > r.safeMaxPct();

            WmdUi.button(g, font, x0, y0, BTN_W, BTN_H, Component.empty(), r.isDamaged() ? WmdUi.B_DISABLED : WmdUi.B_NORMAL, hover && !r.isDamaged());
            if (picked) { g.fill(x0, y0, x0 + BTN_W, y0 + BTN_H, WmdUi.BLUE); g.fill(x0 + 1, y0 + 1, x0 + BTN_W - 1, y0 + BTN_H - 1, 0xFF2D3A4A); }
            String label = Integer.toString(i * 25);
            int col = r.isDamaged() ? WmdUi.TEXT_DIM : unsafe ? BAD : picked ? WmdUi.BLUE_LIGHT : TEXT;
            fit(g, Component.literal(label), x0 + BTN_W / 2, y0 + 3, BTN_W - 2, col);
            if (hover) tips.add(new Object[] { x0, y0, x0 + BTN_W, y0 + BTN_H, List.of(Component.translatable("gui.wmd.reactor.set", i * 25),
                    Component.translatable(unsafe ? "gui.wmd.reactor.unsafe" : "gui.wmd.reactor.safe").withStyle(unsafe ? ChatFormatting.RED : ChatFormatting.GRAY)) });
        }

        int sx0 = x + 6, sx1 = x + PW - 6, sy0 = y + SCRAM_Y, sy1 = sy0 + SCRAM_H;
        boolean sh = mouseX >= sx0 && mouseX < sx1 && mouseY >= sy0 && mouseY < sy1;
        WmdUi.button(g, font, sx0, sy0, sx1 - sx0, SCRAM_H, Component.empty(), WmdUi.B_DANGER, sh);
        if (sh) g.fill(sx0 + 1, sy0 + 1, sx1 - 1, sy1 - 1, 0x22FFFFFF);
        fit(g, Component.translatable("gui.wmd.reactor.scram").withStyle(ChatFormatting.BOLD), cx, sy0 + 5, sx1 - sx0 - 4, TEXT);
        if (sh) tips.add(new Object[] { sx0, sy0, sx1, sy1, List.of(Component.translatable("gui.wmd.reactor.scram"),
                Component.translatable("gui.wmd.reactor.scram_tip").withStyle(ChatFormatting.GRAY)) });

        Component status;
        int kind;
        boolean pulse = false;
        if (!r.isFormed()) { status = Component.translatable("gui.wmd.reactor.st.unformed"); kind = WmdUi.K_BAD; }
        else if (r.isDamaged()) { status = Component.translatable("gui.wmd.reactor.st.damaged"); kind = WmdUi.K_BAD; }
        else if (st >= NuclearReactorBlockEntity.IMMINENT) { status = Component.translatable("gui.wmd.reactor.st.imminent"); kind = WmdUi.K_BAD; pulse = true; }
        else if (st >= NuclearReactorBlockEntity.STEAM) { status = Component.translatable("gui.wmd.reactor.st.steam"); kind = WmdUi.K_BAD; }
        else if (st >= NuclearReactorBlockEntity.ALARM) { status = Component.translatable("gui.wmd.reactor.st.alarm"); kind = WmdUi.K_BAD; }
        else if (r.isScrammed()) { status = Component.translatable("gui.wmd.reactor.st.scram"); kind = WmdUi.K_BAD; }
        else if (menu.running()) { status = Component.translatable("gui.wmd.reactor.st.running", r.levelPct()); kind = WmdUi.K_GOOD; pulse = true; }
        else if (r.spentFull()) { status = Component.translatable("gui.wmd.reactor.st.spent_full"); kind = WmdUi.K_WARN; }
        else if (r.rodLevel() > 0) { status = Component.translatable("gui.wmd.reactor.st.no_fuel"); kind = WmdUi.K_WARN; }
        else { status = Component.translatable("gui.wmd.reactor.st.idle"); kind = WmdUi.K_IDLE; }
        int chipW = Math.min(font.width(status), PW - 10 - 14) + 15;
        WmdUi.chipRight(g, font, cx + (chipW + 1) / 2, y + 126, status, kind, pulse, PW - 10);
        fit(g, Component.translatable("gui.wmd.reactor.core_fuel", r.coreFuelPct()), cx, y + 140, PW - 10, GREY);
    }

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
        NuclearReactorBlockEntity r = reactor();
        if (r != null && inPanel(mx, my)) {
            int pick = -1;
            for (int i = 0; i <= 4; i++) {
                int x0 = btnX(i), y0 = topPos + BTN_Y;
                if (mx >= x0 && mx < x0 + BTN_W && my >= y0 && my < y0 + BTN_H) pick = i;
            }
            if (mx >= px() + 6 && mx < px() + PW - 6 && my >= topPos + SCRAM_Y && my < topPos + SCRAM_Y + SCRAM_H) pick = NuclearReactorBlockEntity.SCRAM_MODE;
            if (pick >= 0 && (pick == NuclearReactorBlockEntity.SCRAM_MODE || !r.isDamaged())) {
                r.setModeClient(pick);
                PacketDistributor.sendToServer(new SetMachineModePayload(r.getBlockPos(), pick));
                if (minecraft != null) { if (pick == NuclearReactorBlockEntity.SCRAM_MODE) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.STONE_BUTTON_CLICK_ON, 1.0F)); else WmdUi.click(); }
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
