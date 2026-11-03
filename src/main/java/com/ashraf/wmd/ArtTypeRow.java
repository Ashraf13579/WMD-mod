package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class ArtTypeRow {

    private ArtTypeRow() {}

    static final int X0 = 6, Y = 64, CELL_W = 23, CELL_H = 16, CELL_STEP = 25;
    private static final int[] COLOUR = { 0xFFE0372D, 0xFF35D25A, 0xFFE0A030, 0xFF3A9BF5, 0xFFB8C2CC };
    private static final String P = "gui.wmd.art.";

    static void draw(GuiGraphics g, Font font, ArtilleryGunBlockEntity gun, MachineMenu menu, int x, int y, int mouseX, int mouseY, List<Object[]> tips) {
        for (int t = 0; t < Artillery.TYPES.length; t++) {
            int bx = x + X0 + t * CELL_STEP, by = y + Y;
            boolean picked = gun.picked() == t, hover = mouseX >= bx && mouseX < bx + CELL_W && mouseY >= by && mouseY < by + CELL_H;
            WmdUi.well(g, bx, by, CELL_W, CELL_H);
            if (picked) { g.fill(bx - 1, by - 1, bx + CELL_W + 1, by + CELL_H + 1, WmdUi.BLUE); g.fill(bx, by, bx + CELL_W, by + CELL_H, 0xFF2D3A4A); }
            else if (hover) g.fill(bx, by, bx + CELL_W, by + CELL_H, 0xFF263040);
            g.fill(bx + 1, by, bx + CELL_W - 1, by + 1, COLOUR[t % COLOUR.length]);
            ItemStack st = new ItemStack(Artillery.shellItem(gun.gun(), t));
            g.renderItem(st, bx + 1, by);
            int n = menu.slots.size() > t ? menu.slots.get(t).getItem().getCount() : 0;
            String s = Integer.toString(n);
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 200.0F);
            g.drawString(font, s, bx + CELL_W - 1 - font.width(s), by + CELL_H - 8, n > 0 ? WmdUi.TEXT : WmdUi.BAD, true);
            g.pose().popPose();
            if (hover) tips.add(new Object[] { bx, by, bx + CELL_W, by + CELL_H, List.of(st.getHoverName(),
                    Component.translatable("tooltip.wmd.art_shell." + Artillery.TYPES[t]).withStyle(ChatFormatting.GRAY),
                    Component.translatable(picked ? P + "picked" : P + "click").withStyle(ChatFormatting.GRAY)) });
        }
    }

    static final int RACK_Y = 83, RACK_X0 = 50, RACK_STEP = 28, RACK_H = 20;

    static void drawRack(GuiGraphics g, Font font, ArtilleryGunBlockEntity gun, MachineMenu menu, int x, int y, int mouseX, int mouseY, List<Object[]> tips) {
        g.drawString(font, Component.translatable(P + "special"), x + 7, y + RACK_Y + 4, WmdUi.TEXT_MUTED, false);
        for (int k = 0; k < Balance.RACK_SLOTS; k++) {
            int bx = x + RACK_X0 + k * RACK_STEP, by = y + RACK_Y;
            boolean picked = gun.picked() == Artillery.PICK_RACK + k, hover = mouseX >= bx && mouseX < bx + CELL_W && mouseY >= by && mouseY < by + CELL_H;
            WmdUi.well(g, bx, by, CELL_W, CELL_H);
            if (picked) { g.fill(bx - 1, by - 1, bx + CELL_W + 1, by + CELL_H + 1, WmdUi.BLUE); g.fill(bx, by, bx + CELL_W, by + CELL_H, 0xFF2D3A4A); }
            else if (hover) g.fill(bx, by, bx + CELL_W, by + CELL_H, 0xFF263040);
            g.fill(bx + 1, by, bx + CELL_W - 1, by + 1, 0xFFB070E0);
            int slot = Artillery.TYPES.length + k;
            ItemStack st = menu.slots.size() > slot ? menu.slots.get(slot).getItem() : ItemStack.EMPTY;
            if (!st.isEmpty()) {
                g.renderItem(st, bx + 1, by);
                String n = Integer.toString(st.getCount());
                g.pose().pushPose();
                g.pose().translate(0.0F, 0.0F, 200.0F);
                g.drawString(font, n, bx + CELL_W - 1 - font.width(n), by + CELL_H - 8, WmdUi.TEXT, true);
                g.pose().popPose();
            }
            if (hover) {
                List<Component> lines = st.isEmpty() ? List.of(Component.translatable(P + "special_empty", k + 1).withStyle(ChatFormatting.GRAY))
                        : List.of(st.getHoverName(), Component.translatable("tooltip.wmd.art_shell." + (st.getItem() instanceof ArtShellItem a ? Artillery.typeName(a.type()) : "he")).withStyle(ChatFormatting.GRAY),
                                Component.translatable(picked ? P + "picked" : P + "click").withStyle(ChatFormatting.GRAY));
                tips.add(new Object[] { bx, by, bx + CELL_W, by + CELL_H, lines });
            }
        }
    }

    static int clickedRack(double mx, double my, int x, int y) {
        for (int k = 0; k < Balance.RACK_SLOTS; k++) {
            int bx = x + RACK_X0 + k * RACK_STEP, by = y + RACK_Y;
            if (mx >= bx && mx < bx + CELL_W && my >= by && my < by + CELL_H) return k;
        }
        return -1;
    }

    static int clicked(double mx, double my, int x, int y) {
        for (int t = 0; t < Artillery.TYPES.length; t++) {
            int bx = x + X0 + t * CELL_STEP, by = y + Y;
            if (mx >= bx && mx < bx + CELL_W && my >= by && my < by + CELL_H) return t;
        }
        return -1;
    }
}
