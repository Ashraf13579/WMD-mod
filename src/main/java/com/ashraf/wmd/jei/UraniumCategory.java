package com.ashraf.wmd.jei;

import com.ashraf.wmd.Balance;
import com.ashraf.wmd.Wmd;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class UraniumCategory implements IRecipeCategory<WmdJeiPlugin.MachineRecipe> {

    public static final RecipeType<WmdJeiPlugin.MachineRecipe> LEACH_TANK = RecipeType.create(Wmd.MODID, "leach_tank", WmdJeiPlugin.MachineRecipe.class);
    public static final RecipeType<WmdJeiPlugin.MachineRecipe> CENTRIFUGE = RecipeType.create(Wmd.MODID, "centrifuge", WmdJeiPlugin.MachineRecipe.class);

    public static final RecipeType<WmdJeiPlugin.MachineRecipe> REPROCESSING = RecipeType.create(Wmd.MODID, "reprocessing_plant", WmdJeiPlugin.MachineRecipe.class);

    private static final int W = 160, GREY = 0xFF555555;
    private final RecipeType<WmdJeiPlugin.MachineRecipe> type;
    private final String name;
    private final boolean cascade;
    private final IDrawable icon;

    public UraniumCategory(IGuiHelper gui, RecipeType<WmdJeiPlugin.MachineRecipe> type, String name, boolean cascade) {
        this.type = type;
        this.name = name;
        this.cascade = cascade;
        this.icon = gui.createDrawableItemStack(machine(name));
    }

    static ItemStack machine(String name) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, name))); }

    private int height() { return cascade ? 64 : 52; }

    @Override public RecipeType<WmdJeiPlugin.MachineRecipe> getRecipeType() { return type; }
    @Override public Component getTitle() { return Component.translatable("block.wmd." + name); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return W; }
    @Override public int getHeight() { return height(); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, WmdJeiPlugin.MachineRecipe r, IFocusGroup focuses) {
        int x = 4;
        for (ItemStack in : r.inItems()) {
            b.addSlot(RecipeIngredientRole.INPUT, x, 6).setStandardSlotBackground().addItemStack(in);
            x += 20;
        }
        if (r.inFluid() != null) b.addSlot(RecipeIngredientRole.INPUT, x, 6).setStandardSlotBackground().addFluidStack(r.inFluid(), r.inFluidMb());
        int ox = 106;
        for (ItemStack out : r.outItems()) {
            b.addSlot(RecipeIngredientRole.OUTPUT, ox, 6).setOutputSlotBackground().addItemStack(out);
            ox += 22;
        }
    }

    @Override
    public void draw(WmdJeiPlugin.MachineRecipe r, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, "->", 88, 10, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.time", String.format("%.1f", r.ticks() / 20.0)), 4, 28, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.power", r.fePerTick() * 20), 4, 40, GREY, false);
        if (cascade) g.drawString(font, Component.translatable("jei.wmd.cascade", Balance.CENT_NEIGHBOUR_BONUS_PCT), 4, 52, GREY, false);
    }
}
