package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class StormGeneratorScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 160, PH = 166, WELL_Y = 28, BOX_Y = 64, SET_Y = 80, LINE_Y = 98, HINT_Y = 128, BTN_Y = 144, BTN_H = 16;
    private static final int C_CHARGE = 0xFF4AA3FF, C_STORM = 0xFF5FE3FF, C_COOL = 0xFFE0A030, C_TRACK = 0xFF1A2129;
    private static final String P = "gui.wmd.storm.";
    private EditBox xBox, zBox;

    public StormGeneratorScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private StormGeneratorBlockEntity sg() { return menu.machine() instanceof StormGeneratorBlockEntity s ? s : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return sg() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        StormGeneratorBlockEntity s = sg();
        if (s == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, s.getBlockPos())));
        xBox = new EditBox(font, px() + 6, py() + BOX_Y, 71, 13, Component.literal("X"));
        zBox = new EditBox(font, px() + 83, py() + BOX_Y, 71, 13, Component.literal("Z"));
        for (EditBox b : new EditBox[] { xBox, zBox }) {
            b.setMaxLength(9);
            b.setFilter(v -> v.isEmpty() || v.matches("-?\\d{0,8}"));
            addRenderableWidget(b);
        }
        xBox.setHint(Component.literal("X")); zBox.setHint(Component.literal("Z"));
        if (s.hasTarget()) { xBox.setValue(Integer.toString(s.targetX())); zBox.setValue(Integer.toString(s.targetZ())); }
    }

    private static int kind(int st) {
        return switch (st) {
            case StormGeneratorBlockEntity.ST_READY -> WmdUi.K_GOOD;
            case StormGeneratorBlockEntity.ST_BUILD, StormGeneratorBlockEntity.ST_STORM, StormGeneratorBlockEntity.ST_CHARGING -> WmdUi.K_WARN;
            case StormGeneratorBlockEntity.ST_END, StormGeneratorBlockEntity.ST_COOLING -> WmdUi.K_INFO;
            default -> WmdUi.K_BAD;
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        StormGeneratorBlockEntity s = sg();
        if (s == null) return;
        int x = px(), y = py(), st = s.stormStatus(), ph = s.phase();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        WmdUi.header(g, font, x, y, PW, new ItemStack(s.getBlockState().getBlock().asItem()), Component.translatable("block.wmd.storm_generator"),
                Component.translatable(P + "st." + st), kind(st), st == StormGeneratorBlockEntity.ST_BUILD || st == StormGeneratorBlockEntity.ST_STORM);

        WmdUi.well(g, x + 6, y + WELL_Y, PW - 12, 30);
        int left = s.secondsLeft();
        double f;
        int col;
        Component bar;
        if (ph == StormGeneratorBlockEntity.P_BUILD || ph == StormGeneratorBlockEntity.P_STORM) {
            double total = (ph == StormGeneratorBlockEntity.P_BUILD ? StormField.BUILD : Balance.STGEN_STORM) / 20.0;
            f = 1.0 - left / total; col = C_STORM;
            bar = Component.translatable(P + (ph == StormGeneratorBlockEntity.P_BUILD ? "building" : "raining"), left);
        } else if (ph == StormGeneratorBlockEntity.P_END || ph == StormGeneratorBlockEntity.P_COOL) {
            f = 1.0 - left / (Balance.STGEN_COOLDOWN / 20.0); col = C_COOL;
            bar = Component.translatable(P + "cooling", left / 60, String.format("%02d", left % 60));
        } else {
            f = s.chargePermille() / 1000.0; col = C_CHARGE;
            bar = Component.translatable(P + "charge", s.chargePermille() / 10);
        }
        int bx0 = x + 10, bx1 = x + PW - 10, by = y + WELL_Y + 16;
        g.fill(bx0, by, bx1, by + 10, C_TRACK);
        g.fill(bx0, by, bx0 + (int) Math.round((bx1 - bx0) * Math.max(0.0, Math.min(1.0, f))), by + 10, col);
        WmdUi.drawFit(g, font, bar, x + 10, y + WELL_Y + 5, PW - 20, WmdUi.TEXT);

        WmdUi.button(g, font, x + 6, y + SET_Y, 72, 13, Component.translatable(P + "set"), WmdUi.B_NORMAL, WmdUi.over(mouseX, mouseY, x + 6, y + SET_Y, 72, 13));
        WmdUi.button(g, font, x + 82, y + SET_Y, 72, 13, Component.translatable(P + "desig"), WmdUi.B_NORMAL, WmdUi.over(mouseX, mouseY, x + 82, y + SET_Y, 72, 13));
        targetLine(g, s, x + 6, y + LINE_Y);
        WmdUi.drawFit(g, font, Component.translatable(P + "radius_line", s.radius()), x + 6, y + LINE_Y + 12, PW - 12, WmdUi.TEXT_MUTED);
        WmdUi.divider(g, x + 6, y + HINT_Y - 4, PW - 12);
        WmdUi.drawFit(g, font, Component.translatable(P + "hint." + st), x + 6, y + HINT_Y, PW - 12, WmdUi.TEXT_MUTED);

        boolean running = ph == StormGeneratorBlockEntity.P_BUILD || ph == StormGeneratorBlockEntity.P_STORM;
        WmdUi.button(g, font, x + 6, y + BTN_Y, 72, BTN_H, Component.translatable(P + "radius", s.radius()), running ? WmdUi.B_DISABLED : WmdUi.B_NORMAL,
                WmdUi.over(mouseX, mouseY, x + 6, y + BTN_Y, 72, BTN_H));
        boolean can = running || st == StormGeneratorBlockEntity.ST_READY;
        WmdUi.button(g, font, x + 82, y + BTN_Y, 72, BTN_H, Component.translatable(P + (running ? "stop" : "fire")), can ? WmdUi.B_DANGER : WmdUi.B_DISABLED,
                can && WmdUi.over(mouseX, mouseY, x + 82, y + BTN_Y, 72, BTN_H));
    }

    private void targetLine(GuiGraphics g, StormGeneratorBlockEntity s, int lx, int ly) {
        Integer tx = num(xBox), tz = num(zBox);
        if (tx != null && tz != null && !(s.hasTarget() && tx == s.targetX() && tz == s.targetZ())) {
            long d = Math.round(Math.hypot(tx + 0.5 - (s.getBlockPos().getX() + 0.5), tz + 0.5 - (s.getBlockPos().getZ() + 0.5)));
            boolean ok = d >= Balance.STGEN_MIN_RANGE && d <= Balance.STGEN_MAX_RANGE;
            WmdUi.drawFit(g, font, Component.translatable(ok ? P + "dist" : P + "out", String.format("%,d", d), Balance.STGEN_MIN_RANGE, String.format("%,d", Balance.STGEN_MAX_RANGE)),
                    lx, ly, PW - 12, ok ? WmdUi.BLUE_LIGHT : WmdUi.BAD);
            return;
        }
        if (s.hasTarget()) {
            WmdUi.drawFit(g, font, Component.translatable(P + "target", s.targetX(), s.targetZ(), String.format("%,d", s.distance()), s.bearing()), lx, ly, PW - 12, WmdUi.BLUE_LIGHT);
            return;
        }
        WmdUi.drawFit(g, font, Component.translatable(P + "reach", Balance.STGEN_MIN_RANGE, String.format("%,d", Balance.STGEN_MAX_RANGE)), lx, ly, PW - 12, WmdUi.TEXT_MUTED);
    }

    private static Integer num(EditBox b) {
        if (b == null) return null;
        try { return Integer.parseInt(b.getValue().trim()); } catch (NumberFormatException e) { return null; }
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= py() && my < py() + PH; }

    private void send(int cmd, int x, int z) {
        StormGeneratorBlockEntity s = sg();
        if (s == null) return;
        PacketDistributor.sendToServer(new RailgunAimPayload(s.getBlockPos(), cmd, x, z));
        WmdUi.click();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        StormGeneratorBlockEntity s = sg();
        if (s != null && inPanel(mx, my)) {
            for (EditBox b : new EditBox[] { xBox, zBox })
                if (b != null && b.mouseClicked(mx, my, button)) { setFocused(b); b.setFocused(true); return true; }
            int x = px(), y = py(), ph = s.phase();
            boolean running = ph == StormGeneratorBlockEntity.P_BUILD || ph == StormGeneratorBlockEntity.P_STORM;
            if (WmdUi.over((int) mx, (int) my, x + 6, y + SET_Y, 72, 13)) {
                Integer tx = num(xBox), tz = num(zBox);
                if (tx != null && tz != null) send(StormGeneratorBlockEntity.CMD_TARGET, tx, tz);
            } else if (WmdUi.over((int) mx, (int) my, x + 82, y + SET_Y, 72, 13)) {
                send(StormGeneratorBlockEntity.CMD_DESIG, 0, 0);
            } else if (!running && WmdUi.over((int) mx, (int) my, x + 6, y + BTN_Y, 72, BTN_H)) {
                send(StormGeneratorBlockEntity.CMD_RADIUS, (s.radiusStep() + 1) % Balance.STGEN_RADII.length, 0);
            } else if (WmdUi.over((int) mx, (int) my, x + 82, y + BTN_Y, 72, BTN_H)) {
                send(running ? StormGeneratorBlockEntity.CMD_STOP : StormGeneratorBlockEntity.CMD_FIRE, 0, 0);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        for (EditBox b : new EditBox[] { xBox, zBox }) if (b != null && b.isFocused() && b.charTyped(c, modifiers)) return true;
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox b : new EditBox[] { xBox, zBox })
            if (keyCode != 256 && b != null && b.isFocused() && (b.keyPressed(keyCode, scanCode, modifiers) || b.canConsumeInput())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPanel(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }
}
