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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

public final class TungstenCategory implements IRecipeCategory<TungstenCategory.Recipe> {

    public record Recipe(List<ItemStack> inItems, Fluid inFluid, int inFluidMb, ItemStack out, int ticks, int fePerTick, String note) {}

    public static final RecipeType<Recipe> ROASTER = RecipeType.create(Wmd.MODID, "roaster", Recipe.class);
    public static final RecipeType<Recipe> HYDROGEN_FURNACE = RecipeType.create(Wmd.MODID, "hydrogen_furnace", Recipe.class);
    public static final RecipeType<Recipe> SINTER_PRESS = RecipeType.create(Wmd.MODID, "sinter_press", Recipe.class);

    private static final int W = 130, H = 52, GREY = 0xFF555555;

    private final RecipeType<Recipe> type;
    private final String name;
    private final IDrawable icon;

    private TungstenCategory(IGuiHelper gui, RecipeType<Recipe> type, String name) {
        this.type = type;
        this.name = name;
        ItemStack machine = stack(name, 1);
        this.icon = gui.createDrawableItemStack(machine.isEmpty() ? new ItemStack(Items.IRON_INGOT) : machine);
    }

    private static ItemStack stack(String id, int n) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id));
        return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it, n);
    }

    public static IRecipeCategory<?>[] categories(IGuiHelper gui) {
        return new IRecipeCategory<?>[] { new TungstenCategory(gui, ROASTER, "roaster"), new TungstenCategory(gui, HYDROGEN_FURNACE, "hydrogen_furnace"),
                new TungstenCategory(gui, SINTER_PRESS, "sinter_press") };
    }

    public static void registerRecipes(IRecipeRegistration reg) {
        reg.addRecipes(ROASTER, List.of(new Recipe(List.of(stack("tungsten_concentrate", Balance.ROAST_CONC)), WmdFluids.LPG.source().get(), Balance.ROAST_LPG_MB,
                stack("tungsten_oxide", Balance.ROAST_OXIDE), Balance.ROAST_CYCLE_TICKS, Balance.ROAST_FE_PER_TICK, "")));
        reg.addRecipes(HYDROGEN_FURNACE, List.of(new Recipe(List.of(stack("tungsten_oxide", Balance.H2F_OXIDE)), WmdFluids.HYDROGEN.source().get(), Balance.H2F_H2_MB,
                stack("tungsten_powder", Balance.H2F_POWDER), Balance.H2F_CYCLE_TICKS, Balance.H2F_FE_PER_TICK, "")));
        reg.addRecipes(SINTER_PRESS, List.of(
                new Recipe(List.of(stack("tungsten_powder", Balance.SINTER_INGOT_POWDER)), null, 0, stack("tungsten_ingot", 1), Balance.SINTER_CYCLE_TICKS, Balance.SINTER_FE_PER_TICK, "jei.wmd.sinter.ingot"),
                new Recipe(List.of(stack("tungsten_powder", Balance.SINTER_ROD_POWDER)), null, 0, stack("tungsten_rod", 1), Balance.SINTER_CYCLE_TICKS, Balance.SINTER_FE_PER_TICK, "jei.wmd.sinter.rod")));
    }

    public static void registerCatalysts(IRecipeCatalystRegistration reg) {
        String[] ids = { "roaster", "hydrogen_furnace", "sinter_press" };
        List<RecipeType<Recipe>> types = List.of(ROASTER, HYDROGEN_FURNACE, SINTER_PRESS);
        for (int i = 0; i < ids.length; i++) {
            ItemStack m = stack(ids[i], 1);
            if (!m.isEmpty()) reg.addRecipeCatalyst(m, types.get(i));
        }
    }

    @Override public RecipeType<Recipe> getRecipeType() { return type; }
    @Override public Component getTitle() { return Component.translatable("block.wmd." + name); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return W; }
    @Override public int getHeight() { return H; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, Recipe r, IFocusGroup focuses) {
        int x = 4;
        for (ItemStack in : r.inItems()) {
            if (!in.isEmpty()) b.addSlot(RecipeIngredientRole.INPUT, x, 6).setStandardSlotBackground().addItemStack(in);
            x += 20;
        }
        if (r.inFluid() != null) b.addSlot(RecipeIngredientRole.INPUT, x, 6).setStandardSlotBackground().addFluidStack(r.inFluid(), r.inFluidMb());
        if (!r.out().isEmpty()) b.addSlot(RecipeIngredientRole.OUTPUT, 104, 6).setOutputSlotBackground().addItemStack(r.out());
        ItemStack machine = stack(name, 1);
        if (!machine.isEmpty()) b.addSlot(RecipeIngredientRole.CATALYST, 56, 6).addItemStack(machine);
    }

    @Override
    public void draw(Recipe r, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, "->", 82, 10, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.time", String.format("%.1f", r.ticks() / 20.0)), 4, 28, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.power", r.fePerTick() * 20), 4, 40, GREY, false);
        if (!r.note().isEmpty()) {
            Component note = Component.translatable(r.note());
            g.drawString(font, note, W - 4 - font.width(note), 28, 0xFF1F4E9A, false);
        }
    }
}
