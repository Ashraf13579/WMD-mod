package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CcBuildingScreen extends AbstractContainerScreen<CcBuildingMenu> {

    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD;
    private static final int PM_W = 74, PM_H = 98;

    public CcBuildingScreen(CcBuildingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 244;
        this.inventoryLabelY = CcBuildingMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, topPos + 22, "icon_lock", Component.translatable("gui.wmd.kit.tab.lock"),
                b -> minecraft.setScreen(new SecurityScreen(this, menu.getPos()))));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        WmdUi.divider(g, x + 8, y + 16, imageWidth - 16);
        WmdHelp.button(g, x + imageWidth - 8 - WmdHelp.SIZE, y + 4);

        int bx = x + 8, by = y + 30, bw = imageWidth - 16;
        WmdUi.well(g, bx, by, bw, 6);
        long cap = Math.max(1, menu.energyCap());
        int fill = (int) (bw * Math.min(1.0, menu.energy() / (double) cap));
        if (fill > 0) {
            g.fill(bx, by, bx + fill, by + 6, WmdUi.CYAN);
            g.fill(bx, by, bx + fill, by + 1, 0x60FFFFFF);
        }

        for (int i = 0; i < CcBuildingBlockEntity.N; i++) {
            Slot sl = menu.slots.get(i);
            int ax = x + sl.x, ay = y + sl.y;
            CcModule k = CcModule.values()[i];
            boolean none = !k.built || CcBuildingSpec.block(k) == null;
            WmdUi.slot(g, ax, ay, none ? 0 : WmdUi.BLUE);
            if (none) {
                g.fill(ax + 3, ay + 7, ax + 13, ay + 9, WmdUi.TEXT_DIM);
            } else if (!sl.hasItem()) {
                g.renderItem(new ItemStack(CcBuildingSpec.item(k)), ax, ay);
                g.fill(ax, ay, ax + 16, ay + 16, 0xB0141A21);
            }
            int bar = menu.online(i) ? WmdUi.GOOD : menu.blocked(i) ? WmdUi.BAD : sl.hasItem() ? WmdUi.BLUE : 0;
            if (bar != 0) g.fill(ax, ay + 20, ax + 16, ay + 22, bar);
        }

        for (int i = CcBuildingBlockEntity.N; i < menu.slots.size(); i++) {
            Slot sl = menu.slots.get(i);
            WmdUi.slot(g, x + sl.x, y + sl.y, 0);
        }
        portMap(g, x + imageWidth + 2, y + 22);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        centred(g, title, 6, TEXT);
        Component st = Component.translatable(menu.powered() ? "gui.wmd.ccb.state.online" : "gui.wmd.ccb.state.no_power");
        centred(g, st, 18, menu.powered() ? GOOD : BAD);
        int pct = menu.energyCap() <= 0 ? 0 : (int) Math.round(100.0 * menu.energy() / menu.energyCap());
        centred(g, Component.translatable("gui.wmd.ccb.power", String.format("%,d", menu.draw()), pct), 40, TEXT);
        centred(g, Component.translatable(menu.sats() ? "gui.wmd.cc.sats_on" : "gui.wmd.cc.sats_off"), 51, menu.sats() ? GOOD : GREY);
        centred(g, Component.translatable("gui.wmd.ccb.hint"), 140, GREY);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, GREY, false);
    }

    private void centred(GuiGraphics g, Component text, int y, int colour) {
        int maxW = imageWidth - 16, w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, (imageWidth - w) / 2, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(8.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {

        int bx = leftPos + 8, by = topPos + 30;
        if (menu.getCarried().isEmpty() && mouseX >= bx && mouseX < bx + imageWidth - 16 && mouseY >= by - 1 && mouseY < by + 7) {
            g.renderTooltip(font, Component.translatable("gui.wmd.ccb.buffer", String.format("%,d", menu.energy()), String.format("%,d", menu.energyCap())), mouseX, mouseY);
            return;
        }

        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.index < CcBuildingBlockEntity.N) {
            int i = hoveredSlot.index;
            CcModule k = CcModule.values()[i];
            List<Component> lines = new ArrayList<>();
            lines.add(hoveredSlot.hasItem() ? hoveredSlot.getItem().getHoverName()
                    : CcBuildingSpec.block(k) != null ? Component.translatable(CcBuildingSpec.block(k).getDescriptionId()) : Component.translatable(k.langKey()));
            String key = !k.built || CcBuildingSpec.block(k) == null ? "later" : menu.online(i) ? "online" : menu.blocked(i) ? (k == CcModule.COMMS_ARRAY ? "blocked_roof" : "blocked")
                    : hoveredSlot.hasItem() ? (menu.placed(i) ? "offline" : "installing") : "empty";
            lines.add(Component.translatable("gui.wmd.ccb.slot." + key));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        super.renderTooltip(g, mouseX, mouseY);
    }

    private void portMap(GuiGraphics g, int x, int y) {
        Direction facing = Direction.NORTH;
        if (minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.getPos()) instanceof CcBuildingBlockEntity be)
            facing = be.getBlockState().getValue(MachineBlock.FACING);
        WmdUi.panel(g, x, y, PM_W, PM_H);
        g.drawString(font, Component.translatable("gui.wmd.ports"), x + 6, y + 5, TEXT, false);
        g.drawString(font, "N", x + PM_W - 12, y + 5, BAD, false);
        int c = 3, n = 19, mx = x + (PM_W - n * c) / 2, my = y + 17;
        int wx0 = Integer.MAX_VALUE, wz0 = Integer.MAX_VALUE;
        for (int lx = CcBuildingSpec.X0; lx <= CcBuildingSpec.X1; lx++) for (int lz = CcBuildingSpec.Z0; lz <= CcBuildingSpec.Z1; lz++) {
            BlockPos w = Machines.toWorld(BlockPos.ZERO, facing, lx, 0, lz);
            wx0 = Math.min(wx0, w.getX()); wz0 = Math.min(wz0, w.getZ());
        }
        WmdUi.well(g, mx, my, n * c, n * c);
        g.fill(mx, my, mx + n * c, my + n * c, 0xFF3A4552);
        for (int lx = CcBuildingSpec.HALL_X0; lx <= CcBuildingSpec.HALL_X1; lx++) for (int lz = CcBuildingSpec.HALL_Z0; lz <= CcBuildingSpec.HALL_Z1; lz++) {
            BlockPos w = Machines.toWorld(BlockPos.ZERO, facing, lx, 0, lz);
            int cx = mx + (w.getX() - wx0) * c, cy = my + (w.getZ() - wz0) * c;
            g.fill(cx, cy, cx + c, cy + c, 0xFF5A6878);
        }
        switch (facing) {
            case NORTH -> g.fill(mx - 1, my - 2, mx + n * c + 1, my, WmdUi.BLUE);
            case SOUTH -> g.fill(mx - 1, my + n * c, mx + n * c + 1, my + n * c + 2, WmdUi.BLUE);
            case WEST -> g.fill(mx - 2, my - 1, mx, my + n * c + 1, WmdUi.BLUE);
            default -> g.fill(mx + n * c, my - 1, mx + n * c + 2, my + n * c + 1, WmdUi.BLUE);
        }
        BlockPos s = Machines.toWorld(BlockPos.ZERO, facing, CcBuildingSpec.SOCKET_X, 0, CcBuildingSpec.SOCKET_Z);
        Direction ws = Machines.dirToWorld(facing, Direction.EAST);
        int dx = mx + (s.getX() - wx0) * c + 1, dy = my + (s.getZ() - wz0) * c + 1;
        switch (ws) {
            case WEST -> dx -= 2;
            case EAST -> dx += 2;
            case NORTH -> dy -= 2;
            default -> dy += 2;
        }
        g.fill(dx - 3, dy - 3, dx + 4, dy + 4, 0xFF000000);
        g.fill(dx - 2, dy - 2, dx + 3, dy + 3, WmdUi.BLUE);
        g.fill(dx - 1, dy - 1, dx + 2, dy + 2, WmdUi.CYAN);
        int ly = y + PM_H - 13;
        g.fill(x + 6, ly + 1, x + 12, ly + 7, WmdUi.BLUE);
        g.drawString(font, Component.translatable("gui.wmd.ccb.power_in"), x + 15, ly, TEXT, false);
    }
}
