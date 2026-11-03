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

public class ForcefieldScreen extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 128, PH = 154;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD;
    private static final int BAR_Y = 40, BAR_H = 8;
    private static final int RAD_Y = 86, RAD_W = 20, RAD_H = 14;
    private static final int GO_Y = 106, GO_H = 18;

    private final List<Object[]> tips = new ArrayList<>();

    public ForcefieldScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override
    protected void init() {
        super.init();
        ForcefieldBlockEntity f = field();
        if (f != null) addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, f.getBlockPos())));
    }

    private ForcefieldBlockEntity field() { return menu.machine() instanceof ForcefieldBlockEntity f ? f : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return field() != null ? PW : 0; }

    private static boolean radiusFree(ForcefieldBlockEntity f) {
        int st = f.state();
        return st != ForcefieldBlockEntity.BOOT && st != ForcefieldBlockEntity.UP && st != ForcefieldBlockEntity.CRACK;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        tips.clear();
        ForcefieldBlockEntity f = field();
        if (f == null) return;
        int x = px(), y = topPos, cx = x + PW / 2, st = f.state();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        centre(g, Component.translatable("gui.wmd.ff.panel"), cx, y + 6, TEXT);

        int kind = switch (st) {
            case ForcefieldBlockEntity.UP -> f.pct() < Balance.FF_LOW_PCT ? WmdUi.K_WARN : WmdUi.K_GOOD;
            case ForcefieldBlockEntity.CRACK, ForcefieldBlockEntity.DOWN -> WmdUi.K_BAD;
            case ForcefieldBlockEntity.OFF -> WmdUi.K_IDLE;
            default -> WmdUi.K_INFO;
        };
        Component status = Component.translatable("gui.wmd.ff.st." + st);
        int chipW = Math.min(font.width(status), PW - 10 - 14) + 15;
        WmdUi.chipRight(g, font, cx + (chipW + 1) / 2, y + 17, status, kind, st != ForcefieldBlockEntity.OFF, PW - 10);

        centre(g, Component.translatable("gui.wmd.ff.buffer", f.pct()), cx, y + 30, f.pct() < Balance.FF_LOW_PCT ? BAD : WmdUi.BLUE_LIGHT);
        int bx = x + 6, bw = PW - 12, by = y + BAR_Y;
        WmdUi.well(g, bx, by, bw, BAR_H);
        int fill = Math.round(bw * f.pct() / 100.0F);
        if (fill > 0) g.fill(bx, by, bx + fill, by + BAR_H, f.pct() < Balance.FF_LOW_PCT ? 0xFFD05050 : WmdUi.BLUE);
        int tick = bx + Math.round(bw * Balance.FF_BOOT_PCT / 100.0F);
        g.fill(tick, by - 1, tick + 1, by + BAR_H + 1, 0xFFFFFFFF);
        if (mouseX >= bx && mouseX < bx + bw && mouseY >= by - 2 && mouseY < by + BAR_H + 2)
            tips.add(new Object[] { bx, by - 2, bx + bw, by + BAR_H + 2, List.of(Component.translatable("gui.wmd.ff.buffer_tip",
                    String.format("%,d", f.storedShown()), String.format("%,d", Balance.FF_BUFFER_FE)),
                    Component.translatable("gui.wmd.ff.buffer_how", Balance.FF_BOOT_PCT, String.format("%,d", Balance.FF_CHARGE_IN)).withStyle(ChatFormatting.GRAY)) });

        boolean holding = st == ForcefieldBlockEntity.UP || st == ForcefieldBlockEntity.BOOT;
        centre(g, Component.translatable("gui.wmd.ff.hold", String.format("%,d", f.holdFe())), cx, y + 54, holding ? GOOD : GREY);
        centre(g, Component.translatable("gui.wmd.ff.across", f.radius() * 2), cx, y + 64, GREY);

        centre(g, Component.translatable("gui.wmd.ff.radius"), cx, y + 76, TEXT);
        int my0 = y + RAD_Y, mx0 = x + 6, px0 = x + PW - 6 - RAD_W;
        boolean free = radiusFree(f);
        boolean hm = in(mouseX, mouseY, mx0, my0, RAD_W, RAD_H), hp = in(mouseX, mouseY, px0, my0, RAD_W, RAD_H);
        WmdUi.button(g, font, mx0, my0, RAD_W, RAD_H, Component.literal("-"), !free || f.radius() <= Balance.FF_R_MIN ? WmdUi.B_DISABLED : WmdUi.B_NORMAL, hm);
        WmdUi.button(g, font, px0, my0, RAD_W, RAD_H, Component.literal("+"), !free || f.radius() >= Balance.FF_R_MAX ? WmdUi.B_DISABLED : WmdUi.B_NORMAL, hp);
        WmdUi.well(g, mx0 + RAD_W + 4, my0 + 1, px0 - mx0 - RAD_W - 8, RAD_H - 2);
        fit(g, Component.literal(String.valueOf(f.radius())), cx, my0 + 3, px0 - mx0 - RAD_W - 12, WmdUi.BLUE_LIGHT);
        if (hm || hp) tips.add(new Object[] { hm ? mx0 : px0, my0, (hm ? mx0 : px0) + RAD_W, my0 + RAD_H, List.of(Component.translatable("gui.wmd.ff.radius_tip",
                Balance.FF_R_MIN, Balance.FF_R_MAX), Component.translatable(free ? "gui.wmd.ff.radius_use" : "gui.wmd.ff.radius_locked").withStyle(ChatFormatting.GRAY)) });

        boolean wantsOn = !f.isOn();
        int gx0 = x + 6, gw = PW - 12, gy0 = y + GO_Y;
        boolean gh = in(mouseX, mouseY, gx0, gy0, gw, GO_H);
        WmdUi.button(g, font, gx0, gy0, gw, GO_H, Component.empty(), wantsOn ? WmdUi.B_NORMAL : WmdUi.B_DANGER, gh);
        fit(g, Component.translatable(wantsOn ? "gui.wmd.ff.on" : "gui.wmd.ff.off").withStyle(ChatFormatting.BOLD), cx, gy0 + 5, gw - 4, wantsOn ? GOOD : TEXT);
        if (gh) tips.add(new Object[] { gx0, gy0, gx0 + gw, gy0 + GO_H, List.of(Component.translatable(wantsOn ? "gui.wmd.ff.on" : "gui.wmd.ff.off"),
                Component.translatable(wantsOn ? "gui.wmd.ff.on_tip" : "gui.wmd.ff.off_tip", Balance.FF_BOOT_PCT).withStyle(ChatFormatting.GRAY)) });

        Component why = null;
        if (st == ForcefieldBlockEntity.CRACK || st == ForcefieldBlockEntity.DOWN) {
            int left = f.coolLeft();
            why = Component.translatable("gui.wmd.ff.why." + f.broke()).append(" ").append(left > 0
                    ? Component.translatable("gui.wmd.ff.cool", left / 1200, String.format("%02d", left / 20 % 60))
                    : Component.translatable("gui.wmd.ff.cool_buffer", Balance.FF_REBOOT_PCT));
        }
        else if (st == ForcefieldBlockEntity.WAIT) why = Component.translatable("gui.wmd.ff.wait", Balance.FF_BOOT_PCT);
        if (why != null) {
            int lineY = y + 130;
            for (var seq : font.split(why, PW - 10)) {
                int w = font.width(seq);
                g.drawString(font, seq, cx - w / 2, lineY, st == ForcefieldBlockEntity.WAIT ? GREY : BAD, false);
                lineY += 10;
                if (lineY > y + PH - 10) break;
            }
        }
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

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
        ForcefieldBlockEntity f = field();
        if (f != null && inPanel(mx, my)) {
            int x = px(), y = topPos, pick = -1;
            int my0 = y + RAD_Y, mx0 = x + 6, px0 = x + PW - 6 - RAD_W;
            int k = (f.radius() - Balance.FF_R_MIN) / Balance.FF_R_STEP;
            if (radiusFree(f) && in((int) mx, (int) my, mx0, my0, RAD_W, RAD_H) && k > 0) pick = k - 1;
            if (radiusFree(f) && in((int) mx, (int) my, px0, my0, RAD_W, RAD_H) && k < ForcefieldBlockEntity.RADII - 1) pick = k + 1;
            if (in((int) mx, (int) my, x + 6, y + GO_Y, PW - 12, GO_H)) pick = f.isOn() ? ForcefieldBlockEntity.MODE_OFF : ForcefieldBlockEntity.MODE_ON;
            if (pick >= 0) {
                f.setModeClient(pick);
                PacketDistributor.sendToServer(new SetMachineModePayload(f.getBlockPos(), pick));
                if (minecraft != null) {
                    if (pick == ForcefieldBlockEntity.MODE_OFF) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.STONE_BUTTON_CLICK_ON, 1.0F));
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
