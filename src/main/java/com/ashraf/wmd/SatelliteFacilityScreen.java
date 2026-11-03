package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class SatelliteFacilityScreen extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 100, ROW_Y0 = 18, ROW_H = 22;

    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD;
    private static final int ROW_SEL = 0xFF2D3A4A, ROW_HOVER = 0xFF263040;
    private static final int MODULE_SLOT = 5;

    private final List<Object[]> tips = new ArrayList<>();

    public SatelliteFacilityScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private SatelliteFacilityBlockEntity facility() { return menu.machine() instanceof SatelliteFacilityBlockEntity f ? f : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return facility() != null ? PW : 0; }

    private static int rows() { return Satellites.TYPES.size(); }

    private static int ph() { return ROW_Y0 + rows() * ROW_H + 16; }

    @Override
    protected void init() {
        super.init();
        SatelliteFacilityBlockEntity f = facility();

        if (f != null) addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, f.getBlockPos())));
    }

    private static Component typeName(String t) { return Component.translatable("gui.wmd.satfac.type." + t); }

    private static ItemStack moduleStack(String t) {
        Item m = SatelliteFacilityBlockEntity.moduleFor(t);
        return m == null ? ItemStack.EMPTY : new ItemStack(m, SatelliteFacilityBlockEntity.moduleCount(t));
    }

    private static Component needs(String t) {
        ItemStack m = moduleStack(t);
        return m.isEmpty() ? Component.literal("-") : Component.translatable("gui.wmd.satfac.needs", m.getCount(), m.getHoverName());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        tips.clear();
        SatelliteFacilityBlockEntity f = facility();
        if (f == null) return;
        int x = px(), y = topPos;
        WmdUi.panel(g, x, y, PW, ph());
        drawSelectedTab(g);
        g.drawString(font, Component.translatable("gui.wmd.satfac.type"), x + 7, y + 6, TEXT, false);
        WmdUi.divider(g, x + 6, y + 15, PW - 12);
        for (int m = 0; m < rows(); m++) {
            String t = Satellites.TYPES.get(m);
            int ry = y + ROW_Y0 + m * ROW_H;
            boolean picked = f.mode() == m;
            boolean hover = mouseX >= x + 4 && mouseX < x + PW - 4 && mouseY >= ry && mouseY < ry + ROW_H - 1;
            if (picked) { g.fill(x + 4, ry, x + PW - 4, ry + ROW_H - 1, WmdUi.BLUE); g.fill(x + 5, ry + 1, x + PW - 5, ry + ROW_H - 2, ROW_SEL); }
            else if (hover) g.fill(x + 4, ry, x + PW - 4, ry + ROW_H - 1, ROW_HOVER);
            ItemStack mod = moduleStack(t);
            if (!mod.isEmpty()) g.renderItem(mod, x + 6, ry + 2);
            fitLeft(g, typeName(t), x + 25, ry + 2, PW - 25 - 6, TEXT);
            fitLeft(g, needs(t), x + 25, ry + 11, PW - 25 - 6, GREY);
            if (hover) tips.add(new Object[] { x + 4, ry, x + PW - 4, ry + ROW_H - 1, List.of(Component.translatable("warhead.wmd." + Satellites.PREFIX + t),
                    Component.translatable("tooltip.wmd.satellite." + t).withStyle(ChatFormatting.GRAY), needs(t).copy().withStyle(ChatFormatting.GRAY),
                    Component.translatable(picked ? "gui.wmd.satfac.picked" : "gui.wmd.satfac.click").withStyle(ChatFormatting.GRAY)) });
        }

        String t = f.pickedType();
        ItemStack in = menu.slots.size() > MODULE_SLOT ? menu.slots.get(MODULE_SLOT).getItem() : ItemStack.EMPTY;
        Component line;
        int colour;
        if (SatelliteFacilityBlockEntity.moduleMatches(t, in)) { line = Component.translatable("gui.wmd.satfac.module_ok"); colour = GOOD; }
        else if (SatelliteFacilityBlockEntity.wrongModule(t, in)) { line = Component.translatable("gui.wmd.satfac.module_wrong"); colour = BAD; }
        else { line = Component.translatable("gui.wmd.satfac.module_missing"); colour = GREY; }
        fitLeft(g, line, x + 7, y + ROW_Y0 + rows() * ROW_H + 3, PW - 14, colour);
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
        SatelliteFacilityBlockEntity f = facility();
        if (f != null && inPanel(mx, my)) {
            int x = px();
            for (int m = 0; m < rows(); m++) {
                int ry = topPos + ROW_Y0 + m * ROW_H;
                if (mx >= x + 4 && mx < x + PW - 4 && my >= ry && my < ry + ROW_H - 1) {
                    if (f.mode() != m) {
                        f.setModeClient(m);
                        PacketDistributor.sendToServer(new SetMachineModePayload(f.getBlockPos(), m));
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
