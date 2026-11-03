package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class LauncherScreen extends AbstractContainerScreen<LauncherMenu> {

    private static final int TEXT = WmdUi.TEXT, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, WARN = WmdUi.WARN, DIM = WmdUi.TEXT_MUTED;
    private static final int TAB_X = -23, TAB_Y = 22, TAB_STEP = 24, TABS = 2;

    private final WmdUi.Smooth feSmooth = new WmdUi.Smooth(6.0F);

    public LauncherScreen(LauncherMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 240;
        this.inventoryLabelY = LauncherMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new WmdUi.TabButton(leftPos + TAB_X, topPos + TAB_Y, "icon_lock", Component.translatable("gui.wmd.kit.tab.lock"),
                b -> minecraft.setScreen(new SecurityScreen(this, menu.getPos()))));
        addRenderableWidget(new WmdUi.TabButton(leftPos + TAB_X, topPos + TAB_Y + TAB_STEP, "icon_freq", Component.translatable("gui.wmd.kit.tab.freq"),
                b -> minecraft.setScreen(new FrequencyScreen(this, menu::frequency, f -> PacketDistributor.sendToServer(new SetLauncherFrequencyPayload(menu.getPos(), f))))));

        WmdUi.KitButton lb = ciws() ? new WmdUi.KitButton(leftPos + 66, topPos + 65, 48, 13, Component.translatable("gui.wmd.bulk.load"), b -> bulk())
                : new WmdUi.KitButton(leftPos + 120, topPos + 48, 48, 13, Component.translatable("gui.wmd.bulk.load"), b -> bulk());
        lb.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.wmd.bulk.tip")));
        addRenderableWidget(lb);
    }

    private void bulk() { PacketDistributor.sendToServer(new BulkLoadPayload(menu.getPos())); }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        if (WmdUi.over(mx, my, leftPos + TAB_X, topPos + TAB_Y, 23, TAB_STEP * (TABS - 1) + 22)) return false;
        return super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }

    private boolean ciws() { return menu.slotCount() == 1; }

    private ItemStack icon() {
        if (minecraft == null || minecraft.level == null) return ItemStack.EMPTY;
        return new ItemStack(minecraft.level.getBlockState(menu.getPos()).getBlock());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);

        Component chip;
        int kind;
        if (!menu.claimed()) { chip = Component.translatable("gui.wmd.kit.chip.unclaimed"); kind = WmdUi.K_BAD; }
        else if (menu.loaded() == 0) { chip = Component.translatable("gui.wmd.kit.chip.empty"); kind = WmdUi.K_BAD; }
        else if (menu.energy() < Balance.INT_SHOT_FE) { chip = Component.translatable("gui.wmd.kit.chip.no_power"); kind = WmdUi.K_BAD; }
        else if (menu.cooldown() > 0) { chip = Component.translatable("gui.wmd.kit.chip.reloading"); kind = WmdUi.K_WARN; }
        else { chip = Component.translatable("gui.wmd.kit.chip.armed"); kind = WmdUi.K_GOOD; }
        WmdUi.header(g, font, x, y, imageWidth, icon(), title, chip, kind, kind == WmdUi.K_GOOD && menu.link() == TrackNet.LOCKED);

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            WmdUi.slot(g, x + s.x, y + s.y, i < menu.slotCount() ? WmdUi.BLUE : 0);
        }

        int w = 100, bx = x + 8, by = y + 126;
        WmdUi.well(g, bx, by, w, 5);
        float f = feSmooth.to((float) Math.min(1.0, menu.energy() / (double) Balance.INT_BUFFER_FE));
        int fill = Math.round(w * f);
        if (f > 0 && fill == 0) fill = 1;
        if (fill > 0) {
            g.fill(bx, by, bx + fill, by + 5, WmdUi.CYAN);
            g.fill(bx, by, bx + fill, by + 1, 0xFFBFF4FA);
        }
    }

    private static int pct(double d) {
        double q = Math.pow(Math.max(0.0, 1.0 - d / Balance.CIWS_RANGE), 1.5);
        return (int) Math.round(100.0 * (Balance.CIWS_HIT_FAR + (Balance.CIWS_HIT_CLOSE - Balance.CIWS_HIT_FAR) * q));
    }

    private void fitted(GuiGraphics g, Component text, int x, int y, int colour, int width) {
        float sc = Math.min(1.0F, (float) width / Math.max(1, font.width(text)));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y + (1.0F - sc) * 4.0F, 0);
        pose.scale(sc, sc, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        pose.popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {

        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, DIM, false);

        int link = menu.link();
        Component linkText = link == TrackNet.LOCKED ? Component.translatable("gui.wmd.launcher.link.3", menu.tracked()) : Component.translatable("gui.wmd.launcher.link." + link);
        fitted(g, linkText, 8, 40, !menu.claimed() ? BAD : link == TrackNet.LOCKED || link == TrackNet.RADAR_SEEKING ? GOOD : link == TrackNet.RADAR_IDLE ? WARN : BAD, 160);
        boolean ciws = ciws();
        fitted(g, Component.translatable(ciws ? "gui.wmd.ciws.box" : "gui.wmd.launcher.magazine"), LauncherMenu.MAG_X, LauncherMenu.MAG_Y - 11, DIM, ciws ? 120 : 72);
        if (ciws) {
            int drum = minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.getPos()) instanceof CiwsBlockEntity cb ? cb.drumSize() : Balance.CIWS_DRUM;

            int boxes = menu.getSlot(0).getItem().getCount(), inDrum = Math.max(0, menu.loaded() - boxes * Balance.CIWS_BOX);
            fitted(g, Component.translatable("gui.wmd.ciws.rounds", inDrum, drum, boxes * Balance.CIWS_BOX), 8, 88, menu.loaded() > 0 ? TEXT : BAD, 160);
            fitted(g, Component.translatable("gui.wmd.ciws.hit", pct(1800), pct(900), pct(450), pct(100)), 8, 98, DIM, 160);
            fitted(g, Component.translatable("gui.wmd.ciws.burst", (int) Balance.CIWS_RANGE, (int) (menu.loaded() / 100)), 8, 107, DIM, 160);
        }
        fitted(g, Component.translatable(ciws ? "gui.wmd.ciws.power" : "gui.wmd.launcher.power", menu.energy() / 1000, ciws ? Balance.CIWS_FIRE_FE : Balance.INT_SHOT_FE / 1000), 8, 116, DIM, 160);
        Component status;
        int colour = GOOD;
        if (!menu.claimed()) { status = Component.translatable("gui.wmd.launcher.unclaimed"); colour = BAD; }
        else if (menu.loaded() == 0) { status = Component.translatable(ciws ? "gui.wmd.ciws.empty" : "gui.wmd.launcher.empty"); colour = BAD; }
        else if (menu.energy() < Balance.INT_SHOT_FE) { status = Component.translatable("gui.wmd.launcher.no_power"); colour = BAD; }
        else if (menu.cooldown() > 0) { status = Component.translatable("gui.wmd.launcher.reloading", menu.cooldown() / 20); colour = WARN; }
        else status = Component.translatable("gui.wmd.launcher.ready");
        fitted(g, status, 8, 136, colour, 160);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
