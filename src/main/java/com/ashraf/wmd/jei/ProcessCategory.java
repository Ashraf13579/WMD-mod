package com.ashraf.wmd.jei;

import java.util.List;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ProcessCategory implements IRecipeCategory<Proc> {

    static final int W = 176, STEP = 19, PER_ROW = 4, IN_X = 2, OUT_X = 98, ARROW_X = 80, TOP = 2, LINE_H = 10;
    private static final int GREY = 0xFF555555, NOTE = 0xFF1F4E9A;

    private final RecipeType<Proc> type;
    private final Component title;
    private final IDrawable icon;
    private final int rows, height;

    public ProcessCategory(IGuiHelper gui, RecipeType<Proc> type, Component title, ItemStack icon, List<Proc> recipes) {
        this.type = type;
        this.title = title;
        this.icon = gui.createDrawableItemStack(icon.isEmpty() ? new ItemStack(Items.IRON_INGOT) : icon);
        int r = 1, lines = 0;
        Font font = Minecraft.getInstance().font;
        for (Proc p : recipes) {
            r = Math.max(r, rowsOf(p));
            int l = 0;
            for (Component c : p.lines()) l += font == null ? 1 : Math.max(1, font.split(c, W - 4).size());
            lines = Math.max(lines, l);
        }
        this.rows = r;
        this.height = TOP + rows * STEP + 3 + lines * LINE_H + 2;
    }

    private static int rowsOf(Proc p) {
        return Math.max(1, Math.max((p.in().size() + PER_ROW - 1) / PER_ROW, (p.out().size() + PER_ROW - 1) / PER_ROW));
    }

    @Override public RecipeType<Proc> getRecipeType() { return type; }
    @Override public Component getTitle() { return title; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return W; }
    @Override public int getHeight() { return height; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, Proc r, IFocusGroup focuses) {
        for (int i = 0; i < r.in().size(); i++)
            fill(b.addSlot(RecipeIngredientRole.INPUT, IN_X + (i % PER_ROW) * STEP, TOP + (i / PER_ROW) * STEP).setStandardSlotBackground(), r.in().get(i));
        for (int i = 0; i < r.out().size(); i++)
            fill(b.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + (i % PER_ROW) * STEP, TOP + (i / PER_ROW) * STEP).setOutputSlotBackground(), r.out().get(i));
    }

    private static void fill(IRecipeSlotBuilder s, Proc.Ing ing) {
        if (ing.isFluid()) s.setFluidRenderer(ing.mb(), false, 16, 16).addFluidStack(ing.fluid(), ing.mb());
        else s.addItemStacks(ing.items());
    }

    @Override
    public void draw(Proc r, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        g.drawString(font, "->", ARROW_X + 2, TOP + 5, GREY, false);
        int y = TOP + rows * STEP + 3;
        for (int i = 0; i < r.lines().size(); i++) {
            int colour = i < 2 ? GREY : NOTE;
            for (FormattedCharSequence s : font.split(r.lines().get(i), W - 4)) {
                g.drawString(font, s, 2, y, colour, false);
                y += LINE_H;
            }
        }
    }
}
