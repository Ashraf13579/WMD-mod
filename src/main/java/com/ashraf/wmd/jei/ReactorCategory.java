package com.ashraf.wmd.jei;

import java.util.List;

import com.ashraf.wmd.Balance;
import com.ashraf.wmd.Wmd;
import com.ashraf.wmd.WmdFluids;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

public final class ReactorCategory implements IRecipeCategory<WmdJeiPlugin.MachineRecipe> {

    public static final RecipeType<WmdJeiPlugin.MachineRecipe> TYPE = RecipeType.create(Wmd.MODID, "nuclear_reactor", WmdJeiPlugin.MachineRecipe.class);

    private static final int W = 160, H = 78, GREY = 0xFF555555;
    private final IDrawable icon;

    public ReactorCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(UraniumCategory.machine("nuclear_reactor"));
    }

    public static void registerRecipes(IRecipeRegistration reg) {
        reg.addRecipes(TYPE, List.of(new WmdJeiPlugin.MachineRecipe(List.of(UraniumCategory.machine("fuel_rod")), Fluids.WATER, Balance.REACTOR_WATER_MB,
                List.of(UraniumCategory.machine("spent_fuel_rod")), Balance.REACTOR_FUEL_TICKS, Balance.REACTOR_PUMP_FE_PER_TICK)));
    }

    public static void registerCatalysts(IRecipeCatalystRegistration reg) {
        for (String id : new String[] { "nuclear_reactor" }) {
            ItemStack s = UraniumCategory.machine(id);
            if (!s.isEmpty()) reg.addRecipeCatalyst(s, TYPE);
        }
    }

    @Override public RecipeType<WmdJeiPlugin.MachineRecipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("block.wmd.nuclear_reactor"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return W; }
    @Override public int getHeight() { return H; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, WmdJeiPlugin.MachineRecipe r, IFocusGroup focuses) {
        b.addSlot(RecipeIngredientRole.INPUT, 4, 6).setStandardSlotBackground().addItemStacks(r.inItems());
        b.addSlot(RecipeIngredientRole.INPUT, 24, 6).setStandardSlotBackground().addFluidStack(r.inFluid(), r.inFluidMb());
        b.addSlot(RecipeIngredientRole.OUTPUT, 70, 6).setOutputSlotBackground().addFluidStack(WmdFluids.STEAM.source().get(), Balance.REACTOR_STEAM_MB);
        b.addSlot(RecipeIngredientRole.OUTPUT, 90, 6).setOutputSlotBackground().addItemStacks(r.outItems());
    }

    @Override
    public void draw(WmdJeiPlugin.MachineRecipe r, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, "->", 48, 10, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.reactor.rod", r.ticks() / 1200), 4, 30, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.reactor.steam", Balance.REACTOR_STEAM_MB), 4, 42, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.reactor.pumps", String.format("%,d", r.fePerTick() * 20)), 4, 54, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.reactor.heat_steam"), 4, 66, GREY, false);
    }
}
