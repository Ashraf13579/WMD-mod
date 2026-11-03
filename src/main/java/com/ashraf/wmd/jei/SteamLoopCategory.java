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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public final class SteamLoopCategory implements IRecipeCategory<SteamLoopCategory.Step> {

    public static final RecipeType<Step> TYPE = RecipeType.create(Wmd.MODID, "steam_loop", Step.class);

    public record Step(String machine, Fluid in, int inMb, Fluid out, int outMb, int fe, String note) {}

    private static final int W = 160, H = 54, GREY = 0xFF555555;
    private final IDrawable icon;

    public SteamLoopCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(UraniumCategory.machine("steam_turbine"));
    }

    public static void registerRecipes(IRecipeRegistration reg) {
        reg.addRecipes(TYPE, List.of(
                new Step("steam_turbine", WmdFluids.STEAM.source().get(), Balance.STEAM_TURBINE_MB, WmdFluids.SPENT_STEAM.source().get(), Balance.STEAM_TURBINE_MB,
                        Balance.STEAM_TURBINE_MB * Balance.STEAM_TURBINE_FE_PER_MB, "jei.wmd.steam_turbine.note"),
                new Step("cooling_tower", WmdFluids.SPENT_STEAM.source().get(), Balance.TOWER_CONDENSE_MB, Fluids.WATER, Balance.TOWER_CONDENSE_MB * Balance.TOWER_WATER_PCT / 100,
                        -Balance.TOWER_FE_PER_TICK, "jei.wmd.cooling_tower.note")));
    }

    public static void registerCatalysts(IRecipeCatalystRegistration reg) {
        for (String id : new String[] { "steam_turbine", "cooling_tower" }) {
            ItemStack s = UraniumCategory.machine(id);
            if (!s.isEmpty()) reg.addRecipeCatalyst(s, TYPE);
        }
    }

    @Override public RecipeType<Step> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.wmd.steam_loop"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return W; }
    @Override public int getHeight() { return H; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, Step r, IFocusGroup focuses) {
        b.addSlot(RecipeIngredientRole.CATALYST, 4, 6).addItemStack(UraniumCategory.machine(r.machine()));
        b.addSlot(RecipeIngredientRole.INPUT, 28, 6).setStandardSlotBackground().addFluidStack(r.in(), r.inMb());
        b.addSlot(RecipeIngredientRole.OUTPUT, 70, 6).setOutputSlotBackground().addFluidStack(r.out(), r.outMb());
    }

    @Override
    public void draw(Step r, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, "->", 52, 10, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.per_tick"), 92, 10, GREY, false);
        g.drawString(font, Component.translatable(r.fe() >= 0 ? "jei.wmd.steam_loop.makes" : "jei.wmd.steam_loop.uses", String.format("%,d", Math.abs(r.fe()) * 20)), 4, 28, GREY, false);
        g.drawString(font, Component.translatable(r.note()), 4, 40, GREY, false);
    }
}
