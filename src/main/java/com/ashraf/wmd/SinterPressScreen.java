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

public class SinterPressScreen extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 96, ROW_Y0 = 18, ROW_H = 22, PH = ROW_Y0 + 2 * ROW_H + 4;

    private static final int ROW_SEL = 0xFF2D3A4A, ROW_HOVER = 0xFF263040;

    private final List<Object[]> tips = new ArrayList<>();

    public SinterPressScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private SinterPressBlockEntity press() { return menu.machine() instanceof SinterPressBlockEntity p ? p : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return press() != null ? PW : 0; }

    private int ph() {
        GuiMachine m = menu.machine();
        int n = 1 + (m != null && m.guiPortMap() != null ? 1 : 0) + (m != null && !m.guiInfo().isEmpty() ? 1 : 0);
        return Math.max(PH, 22 + n * 24 + 2);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        tips.clear();
        SinterPressBlockEntity p = press();
        if (p == null) return;
        int x = px(), y = topPos;
        WmdUi.panel(g, x, y, PW, ph());
        drawSelectedTab(g);
        g.drawString(font, Component.translatable("gui.wmd.press.mode"), x + 7, y + 6, WmdUi.TEXT, false);
        WmdUi.divider(g, x + 6, y + 15, PW - 12);
        for (int m = 0; m < 2; m++) {
            int ry = y + ROW_Y0 + m * ROW_H;
            boolean picked = p.mode() == m;
            boolean hover = mouseX >= x + 4 && mouseX < x + PW - 4 && mouseY >= ry && mouseY < ry + ROW_H - 1;
            if (picked) { g.fill(x + 4, ry, x + PW - 4, ry + ROW_H - 1, WmdUi.BLUE); g.fill(x + 5, ry + 1, x + PW - 5, ry + ROW_H - 2, ROW_SEL); }
            else if (hover) g.fill(x + 4, ry, x + PW - 4, ry + ROW_H - 1, ROW_HOVER);
            ItemStack product = new ItemStack(ItemProcessBlockEntity.wmd(SinterPressBlockEntity.productFor(m)));
            g.renderItem(product, x + 6, ry + 2);
            g.drawString(font, Component.translatable(m == SinterPressBlockEntity.ROD ? "gui.wmd.press.rod" : "gui.wmd.press.ingot"), x + 25, ry + 2, WmdUi.TEXT, false);
            g.drawString(font, Component.translatable("gui.wmd.press.cost", SinterPressBlockEntity.powderFor(m)), x + 25, ry + 11, WmdUi.TEXT_MUTED, false);
            if (hover) tips.add(new Object[] { x + 4, ry, x + PW - 4, ry + ROW_H - 1, List.of(product.getHoverName(),
                    Component.translatable("gui.wmd.press.cost", SinterPressBlockEntity.powderFor(m)).withStyle(ChatFormatting.GRAY),
                    Component.translatable(picked ? "gui.wmd.press.picked" : "gui.wmd.press.click").withStyle(ChatFormatting.GRAY)) });
        }
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
        SinterPressBlockEntity p = press();
        if (p != null && inPanel(mx, my)) {
            int x = px();
            for (int m = 0; m < 2; m++) {
                int ry = topPos + ROW_Y0 + m * ROW_H;
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
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPanel(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }
}
