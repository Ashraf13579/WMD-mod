package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class LaunchCellScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 172, PH = 148;
    static final int ROW_Y = 72, LAUNCH_Y = 100, HINT_Y = 124;
    private static final String P = "gui.wmd.lc.";

    private EditBox xBox, zBox;
    private String xText = "", zText = "";
    private boolean filled;

    public LaunchCellScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private LaunchCellBlockEntity be() { return menu.machine() instanceof LaunchCellBlockEntity c ? c : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return be() != null ? PW : 0; }

    private void send(int action, int value) {
        LaunchCellBlockEntity c = be();
        if (c != null) PacketDistributor.sendToServer(new LaunchCellPayload(c.getBlockPos(), action, value));
    }

    @Override
    protected void init() {
        super.init();
        LaunchCellBlockEntity c = be();
        if (c == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, c.getBlockPos())));
        addActionTab("icon_freq", Component.translatable("gui.wmd.kit.tab.freq"), () -> minecraft.setScreen(new FrequencyScreen(this, c::frequency, f -> send(LaunchCellBlockEntity.A_FREQ, f))));
        int x = px(), y = py();
        if (!filled && c.strikeSet()) { xText = Integer.toString(c.strikeX()); zText = Integer.toString(c.strikeZ()); filled = true; }
        xBox = box(x + 6, y + ROW_Y, 50, xText);
        zBox = box(x + 60, y + ROW_Y, 50, zText);
        addRenderableWidget(new WmdUi.KitButton(x + 114, y + ROW_Y, 26, 14, Component.translatable(P + "set"), b -> setStrike()));
        addRenderableWidget(new WmdUi.KitButton(x + 142, y + ROW_Y, 24, 14, Component.translatable(P + "clear"), b -> { send(LaunchCellBlockEntity.A_CLEAR, 0); xBox.setValue(""); zBox.setValue(""); }));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + LAUNCH_Y, PW - 12, 16, Component.translatable(P + "launch"), b -> send(LaunchCellBlockEntity.A_LAUNCH, 0), true));
    }

    private EditBox box(int x, int y, int w, String value) {
        EditBox b = new EditBox(font, x, y, w, 14, Component.empty());
        b.setMaxLength(9);
        b.setFilter(s -> s.matches("-?\\d*"));
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void rebuildWidgets() {
        if (xBox != null) { xText = xBox.getValue(); zText = zBox.getValue(); }
        super.rebuildWidgets();
    }

    private static Integer parse(EditBox b) {
        try { return b == null || b.getValue().isEmpty() || b.getValue().equals("-") ? null : Integer.parseInt(b.getValue()); } catch (NumberFormatException e) { return null; }
    }

    private void setStrike() {
        Integer x = parse(xBox), z = parse(zBox);
        if (x == null || z == null) { if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(P + "need_xz"), true); return; }
        send(LaunchCellBlockEntity.A_TX, x);
        send(LaunchCellBlockEntity.A_TZ, z);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        LaunchCellBlockEntity c = be();
        if (c == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        int st = c.cellStatus();
        WmdUi.header(g, font, x, y, PW, new ItemStack(c.getBlockState().getBlock().asItem()), Component.translatable(c.getBlockState().getBlock().getDescriptionId()),
                Component.translatable(P + "chip." + st), st == LaunchCellBlockEntity.S_READY ? WmdUi.K_GOOD : st == LaunchCellBlockEntity.S_LAUNCHING ? WmdUi.K_INFO : WmdUi.K_WARN,
                st == LaunchCellBlockEntity.S_READY);
        fit(g, Component.translatable(P + "net", c.frequency()), cx, y + 26, WmdUi.TEXT_MUTED);
        int rk = c.roundKind();
        Component round = switch (rk) {
            case LaunchCellBlockEntity.R_SLBM -> Component.translatable(P + "round.slbm", String.format("%,d", c.shownRange()));
            case LaunchCellBlockEntity.R_CRUISE -> Component.translatable(P + "round.cruise", String.format("%,d", c.shownRange()), c.shownCapsules());
            case LaunchCellBlockEntity.R_ASROC -> Component.translatable(P + "round.asroc");
            default -> Component.translatable(P + "round.none", c.shownCapsules());
        };
        fit(g, round, cx, y + 37, WmdUi.TEXT);
        fit(g, c.targetDistance() > 0 ? Component.translatable(P + "target", c.targetDistance()) : Component.translatable(P + "no_target"), cx, y + 48, WmdUi.TEXT);
        fit(g, Component.translatable(P + "st." + st), cx, y + 59, st == LaunchCellBlockEntity.S_READY ? WmdUi.GOOD : WmdUi.TEXT_MUTED);

        boolean viaConsole = !c.strikeSet() && c.aimSource() == LaunchCellBlockEntity.SRC_CONSOLE && (rk == LaunchCellBlockEntity.R_SLBM || rk == LaunchCellBlockEntity.R_CRUISE);
        fit(g, c.strikeSet() ? Component.translatable(P + "strike", c.strikeX(), c.strikeZ())
                : viaConsole ? Component.translatable(P + "strike_console", c.consoleStrikeX(), c.consoleStrikeZ()) : Component.translatable(P + "no_strike"),
                cx, y + 89, c.strikeSet() || viaConsole ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
        WmdUi.divider(g, x + 5, y + HINT_Y - 3, PW - 10);
        fit(g, Component.translatable(P + "hint_target"), cx, y + HINT_Y, WmdUi.TEXT_MUTED);
        fit(g, Component.translatable(P + "hint_redstone", (int) Balance.LC_MAX_DEPTH), cx, y + HINT_Y + 11, WmdUi.TEXT_MUTED);
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= py() && my < py() + PH; }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPanel(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != 256) {
            for (EditBox box : new EditBox[] { xBox, zBox })
                if (box != null && box.isFocused() && (box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput())) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
