package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class ShellPressScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 112, ROW_Y0 = 18, ROW_H = 21;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD;

    private final List<Object[]> tips = new ArrayList<>();

    public ShellPressScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private ShellPressBlockEntity press() { return menu.machine() instanceof ShellPressBlockEntity p ? p : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return press() != null ? PW : 0; }

    static int rows() { return ShellPressBlockEntity.RECIPES.size(); }

    static int ph() { if (ShellPressPager.paged()) return ROW_Y0 + ShellPressPager.PAGE * ROW_H + 16 + ShellPressPager.BAR + LEAF_H; return ROW_Y0 + rows() * ROW_H + 16 + LEAF_H; }

    static final int LEAF_H = 30;
    private net.minecraft.client.gui.components.EditBox leafBox;

    private static final int GO_W = 38, GO_H = 12;
    private int goX = -1000, goY = -1000;

    @Override
    protected void init() {
        super.init();
        ShellPressBlockEntity p = press();
        if (p != null) addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, p.getBlockPos())));
        if (p != null) ShellPressPager.show(p.mode());
        if (p != null) {
            int by = topPos + ph() - LEAF_H + 12;
            leafBox = new net.minecraft.client.gui.components.EditBox(font, px() + 6, by, PW - 12, 13, Component.translatable("gui.wmd.shell_press.leaflet"));
            leafBox.setMaxLength(Balance.LEAFLET_CHARS);
            leafBox.setValue(p.leafletText());
            leafBox.setHint(Component.translatable("gui.wmd.shell_press.leaflet_hint"));
            leafBox.setResponder(t -> PacketDistributor.sendToServer(new ShellPressTextPayload(p.getBlockPos(), t)));
            addRenderableWidget(leafBox);
            leafBox.visible = ShellPressBlockEntity.isLeaflet(p.picked());
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        tips.clear();
        ShellPressBlockEntity p = press();
        if (p == null) return;
        int x = px(), y = topPos;
        WmdUi.panel(g, x, y, PW, ph());
        drawSelectedTab(g);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.shell_press.recipe"), x + 7, y + 6, PW - 14, TEXT);
        WmdUi.divider(g, x + 6, y + 15, PW - 12);
        for (int m = 0; m < rows(); m++) {
            ShellPressBlockEntity.Recipe r = ShellPressBlockEntity.RECIPES.get(m);
            int ry = y + ROW_Y0 + m * ROW_H;
            if (ShellPressPager.hidden(m)) continue;
            ry -= ShellPressPager.shift(m) * ROW_H;
            boolean picked = p.mode() == m;
            boolean hover = mouseX >= x + 4 && mouseX < x + PW - 4 && mouseY >= ry && mouseY < ry + ROW_H - 1;
            if (picked) { g.fill(x + 4, ry, x + PW - 4, ry + ROW_H - 1, WmdUi.BLUE); g.fill(x + 5, ry + 1, x + PW - 5, ry + ROW_H - 2, 0xFF2D3A4A); }
            else g.fill(x + 4, ry, x + PW - 4, ry + ROW_H - 1, hover ? 0xFF263040 : WmdUi.CARD);
            ItemStack out = r.outStack();
            g.renderItem(out, x + 6, ry + 2);
            fitLeft(g, out.getHoverName(), x + 25, ry + 1, PW - 25 - 6, TEXT);
            int ix = x + 25;
            for (ItemStack in : new ItemStack[] { r.casingStack(), r.fillerStack(), r.fuzeStack() }) {
                if (in.isEmpty()) continue;
                mini(g, in, ix, ry + 11);
                ix += 26;
            }
            if (hover) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("gui.wmd.shell_press.makes", r.outN(), out.getHoverName()));
                lines.add(Component.translatable("gui.wmd.shell_press.casing", r.casingN(), r.casingStack().getHoverName()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.wmd.shell_press.filler", r.fillerN(), r.fillerStack().getHoverName()).withStyle(ChatFormatting.GRAY));
                if (r.fuzeN() > 0) lines.add(Component.translatable("gui.wmd.shell_press.fuze", r.fuzeN(), r.fuzeStack().getHoverName()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(picked ? "gui.wmd.shell_press.picked" : "gui.wmd.shell_press.click").withStyle(ChatFormatting.GRAY));
                tips.add(new Object[] { x + 4, ry, x + PW - 4, ry + ROW_H - 1, lines });
            }
        }

        ShellPressBlockEntity.Recipe r = p.picked();
        ItemStack c = menu.slots.size() > 2 ? menu.slots.get(0).getItem() : ItemStack.EMPTY, f = menu.slots.size() > 2 ? menu.slots.get(1).getItem() : ItemStack.EMPTY,
                z = menu.slots.size() > 2 ? menu.slots.get(2).getItem() : ItemStack.EMPTY;
        boolean ok = ShellPressBlockEntity.ready(r, c, f, z);
        int sy = y + ROW_Y0 + rows() * ROW_H + 3;
        if (ShellPressPager.paged()) sy = ShellPressPager.drawBar(g, font, x, y + ROW_Y0 + ShellPressPager.PAGE * ROW_H, PW, mouseX, mouseY) + 3;
        WmdUi.led(g, x + 7, sy + 1, ok ? WmdUi.K_GOOD : WmdUi.K_WARN, false);
        fitLeft(g, Component.translatable(ok ? "gui.wmd.shell_press.ready" : "gui.wmd.shell_press.missing"), x + 15, sy, PW - 22 - GO_W - 4, ok ? GOOD : WmdUi.WARN);

        goX = x + PW - 6 - GO_W; goY = sy - 2;
        boolean gh = mouseX >= goX && mouseX < goX + GO_W && mouseY >= goY && mouseY < goY + GO_H;
        WmdUi.button(g, font, goX, goY, GO_W, GO_H, Component.translatable(p.isOn() ? "gui.wmd.shell_press.stop" : "gui.wmd.shell_press.start"),
                p.isOn() ? WmdUi.B_DANGER : WmdUi.B_NORMAL, gh);
        if (gh) tips.add(new Object[] { goX, goY, goX + GO_W, goY + GO_H, java.util.List.of(Component.translatable(p.isOn() ? "gui.wmd.shell_press.stop_tip" : "gui.wmd.shell_press.start_tip")) });
        if (leafBox != null) {
            leafBox.visible = ShellPressBlockEntity.isLeaflet(r);
            if (leafBox.visible) fitLeft(g, Component.translatable("gui.wmd.shell_press.leaflet", leafBox.getValue().length(), Balance.LEAFLET_CHARS), x + 7, y + ph() - LEAF_H + 2, PW - 14, GREY);
        }
    }

    private void mini(GuiGraphics g, ItemStack st, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(0.5F, 0.5F, 1.0F);
        g.renderItem(st, 0, 0);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 200.0F);
        g.drawString(font, Integer.toString(st.getCount()), x + 9, y, GREY, false);
        g.pose().popPose();
    }

    private void fitLeft(GuiGraphics g, Component text, int x, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, x, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - s) * 4.0F, 0.0F);
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

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= topPos && my < topPos + ph(); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        ShellPressBlockEntity p = press();
        if (p != null && inPanel(mx, my)) {
            int x = px();
            if (ShellPressPager.click(mx, my, x, topPos + ROW_Y0 + ShellPressPager.PAGE * ROW_H, PW)) return true;
            if (mx >= goX && mx < goX + GO_W && my >= goY && my < goY + GO_H) {
                int m = p.isOn() ? ShellPressBlockEntity.stopMode() : ShellPressBlockEntity.startMode();
                p.setModeClient(m);
                PacketDistributor.sendToServer(new SetMachineModePayload(p.getBlockPos(), m));
                WmdUi.click();
                return true;
            }
            if (leafBox != null && leafBox.visible && leafBox.mouseClicked(mx, my, button)) { setFocused(leafBox); leafBox.setFocused(true); return true; }
            for (int m = 0; m < rows(); m++) {
                int ry = topPos + ROW_Y0 + m * ROW_H;
                if (ShellPressPager.hidden(m)) continue;
                ry -= ShellPressPager.shift(m) * ROW_H;
                if (mx >= x + 4 && mx < x + PW - 4 && my >= ry && my < ry + ROW_H - 1) {
                    if (p.mode() != m) {
                        p.setModeClient(m);
                        PacketDistributor.sendToServer(new SetMachineModePayload(p.getBlockPos(), m));
                    }
                    WmdUi.click();
                    break;
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (leafBox != null && leafBox.visible && leafBox.isFocused() && leafBox.charTyped(c, modifiers)) return true;
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != 256 && leafBox != null && leafBox.isFocused() && (leafBox.keyPressed(keyCode, scanCode, modifiers) || leafBox.canConsumeInput())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPanel(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }
}
