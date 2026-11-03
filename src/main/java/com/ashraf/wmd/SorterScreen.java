package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class SorterScreen extends AbstractContainerScreen<SorterMenu> {

    private static final String[] FACE_KEYS = { "down", "up", "north", "south", "west", "east" };
    static final int FACE_Y = 24, MODE_Y = 44, LIST_Y = 62, FILTER_Y = 94, BUFFER_Y = 118;
    private int face = Direction.NORTH.ordinal();
    private WmdUi.KitButton modeB, rrB, listB;

    public SorterScreen(SorterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = SorterMenu.INV_Y - 11;
    }

    private SorterBlockEntity sorter() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos()) instanceof SorterBlockEntity s ? s : null;
    }

    private void send(int op, int value, ItemStack item) { PacketDistributor.sendToServer(new SorterConfigPayload(menu.pos(), op, face, value, item)); }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < 6; i++) {
            final int f = i;
            addRenderableWidget(new WmdUi.KitButton(leftPos + 8 + i * 27, topPos + FACE_Y, 24, 14, Component.translatable("gui.wmd.sorter.face_short." + FACE_KEYS[i]), b -> face = f));
        }
        modeB = addRenderableWidget(new WmdUi.KitButton(leftPos + 8, topPos + MODE_Y, 78, 14, Component.empty(), b -> {
            SorterBlockEntity s = sorter();
            if (s != null) send(SorterConfigPayload.MODE, s.mode(Direction.from3DDataValue(face)).ordinal() + 1, ItemStack.EMPTY);
        }));
        rrB = addRenderableWidget(new WmdUi.KitButton(leftPos + 90, topPos + MODE_Y, 78, 14, Component.empty(), b -> send(SorterConfigPayload.ROUND_ROBIN, 0, ItemStack.EMPTY)));
        listB = addRenderableWidget(new WmdUi.KitButton(leftPos + 8, topPos + LIST_Y, 78, 14, Component.empty(), b -> send(SorterConfigPayload.ALLOW, 0, ItemStack.EMPTY)));
        addRenderableWidget(new WmdUi.KitButton(leftPos + 90, topPos + LIST_Y, 14, 14, Component.literal("-"), b -> send(SorterConfigPayload.PRIORITY, -1, ItemStack.EMPTY)));
        addRenderableWidget(new WmdUi.KitButton(leftPos + 154, topPos + LIST_Y, 14, 14, Component.literal("+"), b -> send(SorterConfigPayload.PRIORITY, 1, ItemStack.EMPTY)));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        SorterBlockEntity s = sorter();
        if (s == null) return;
        Direction f = Direction.from3DDataValue(face);
        modeB.setMessage(Component.translatable("gui.wmd.sorter.mode." + s.mode(f).getSerializedName()));
        rrB.setMessage(Component.translatable(s.roundRobin() ? "gui.wmd.sorter.rr_on" : "gui.wmd.sorter.rr_off"));
        listB.setMessage(Component.translatable(s.allowList(f) ? "gui.wmd.sorter.allow" : "gui.wmd.sorter.block"));
    }

    private int ghostX(int i) { return leftPos + 8 + i * 18; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < SorterBlockEntity.FILTER_SLOTS; i++) {
            if (!WmdUi.over(mx, my, ghostX(i), topPos + FILTER_Y, 16, 16)) continue;
            ItemStack carried = menu.getCarried();
            WmdUi.click();
            send(SorterConfigPayload.FILTER, i, button == 0 && !carried.isEmpty() ? carried.copyWithCount(1) : ItemStack.EMPTY);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        SorterBlockEntity s = sorter();
        boolean busy = s != null && !s.buffer().isEmpty();
        ItemStack icon = minecraft != null && minecraft.level != null ? new ItemStack(minecraft.level.getBlockState(menu.pos()).getBlock()) : ItemStack.EMPTY;
        WmdUi.header(g, font, x, y, imageWidth, icon, title, Component.translatable(busy ? "gui.wmd.sorter.chip.sorting" : "gui.wmd.sorter.chip.idle"), busy ? WmdUi.K_GOOD : WmdUi.K_IDLE, false);
        g.fill(x + 8 + face * 27, y + FACE_Y + 15, x + 32 + face * 27, y + FACE_Y + 17, WmdUi.BLUE_LIGHT);
        if (s == null) return;
        Direction f = Direction.from3DDataValue(face);
        SorterBlock.Face mode = s.mode(f);
        int rim = mode == SorterBlock.Face.INPUT ? WmdUi.BLUE : mode == SorterBlock.Face.OUTPUT ? WmdUi.ORANGE : 0;
        for (int i = 0; i < SorterBlockEntity.FILTER_SLOTS; i++) {
            WmdUi.slot(g, ghostX(i), y + FILTER_Y, rim);
            ItemStack gst = s.filter(f, i);
            if (!gst.isEmpty()) { g.renderItem(gst, ghostX(i), y + FILTER_Y); g.fill(ghostX(i), y + FILTER_Y, ghostX(i) + 16, y + FILTER_Y + 16, 0x50202830); }
        }
        WmdUi.slot(g, x + 150, y + BUFFER_Y - 4, 0);
        if (!s.buffer().isEmpty()) { g.renderItem(s.buffer(), x + 150, y + BUFFER_Y - 4); g.renderItemDecorations(font, s.buffer(), x + 150, y + BUFFER_Y - 4); }
        for (net.minecraft.world.inventory.Slot sl : menu.slots) WmdUi.slot(g, x + sl.x, y + sl.y, 0);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        SorterBlockEntity s = sorter();
        if (s != null) {
            Direction f = Direction.from3DDataValue(face);
            g.drawString(font, Component.translatable("gui.wmd.sorter.priority", s.priority(f)), 108, LIST_Y + 3, WmdUi.TEXT, false);
            g.drawString(font, Component.translatable("gui.wmd.sorter.filter", Component.translatable("gui.wmd.sorter.face." + FACE_KEYS[face])), 8, FILTER_Y - 10, WmdUi.TEXT_MUTED, false);
            g.drawString(font, Component.translatable(s.buffer().isEmpty() ? "gui.wmd.sorter.buffer_empty" : "gui.wmd.sorter.buffer"), 8, BUFFER_Y, WmdUi.TEXT_MUTED, false);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, WmdUi.TEXT_MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        for (int i = 0; i < SorterBlockEntity.FILTER_SLOTS; i++)
            if (WmdUi.over(mouseX, mouseY, ghostX(i), topPos + FILTER_Y, 16, 16)) { g.renderTooltip(font, Component.translatable("gui.wmd.sorter.ghost_tip"), mouseX, mouseY); return; }
        renderTooltip(g, mouseX, mouseY);
    }
}
