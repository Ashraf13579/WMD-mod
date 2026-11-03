package com.ashraf.wmd.jei;

import java.util.ArrayList;
import java.util.List;

import com.ashraf.wmd.Balance;
import com.ashraf.wmd.PayloadUnitItem;
import com.ashraf.wmd.SatelliteFacilityBlockEntity;
import com.ashraf.wmd.Satellites;
import com.ashraf.wmd.Wmd;

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

public final class SatelliteFacilityCategory implements IRecipeCategory<SatelliteFacilityCategory.Recipe> {

    public record Recipe(List<ItemStack> inputs, ItemStack out, String type) {}

    public static final RecipeType<Recipe> TYPE = RecipeType.create(Wmd.MODID, "satellite_facility", Recipe.class);

    private static final int W = 170, H = 52, GREY = 0xFF555555;
    private final IDrawable icon;

    public SatelliteFacilityCategory(IGuiHelper gui) {
        ItemStack machine = stack("satellite_facility", 1);
        this.icon = gui.createDrawableItemStack(machine.isEmpty() ? new ItemStack(Items.IRON_INGOT) : machine);
    }

    private static ItemStack stack(String id, int n) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id));
        return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it, n);
    }

    public static void registerRecipes(IRecipeRegistration reg) {
        List<Recipe> l = new ArrayList<>();
        for (String t : Satellites.TYPES) {
            Item m = SatelliteFacilityBlockEntity.moduleFor(t);
            if (m == null) continue;
            l.add(new Recipe(List.of(stack("rtg_cell", 1), stack("titanium_plate", Balance.SATFAC_TITANIUM), stack("silicon", Balance.SATFAC_SILICON),
                    stack("advanced_circuit", Balance.SATFAC_CIRCUITS), stack("aluminium_plate", Balance.SATFAC_ALUMINIUM), new ItemStack(m, SatelliteFacilityBlockEntity.moduleCount(t))),
                    PayloadUnitItem.of(t), t));
        }
        reg.addRecipes(TYPE, l);
    }

    public static void registerCatalysts(IRecipeCatalystRegistration reg) {
        ItemStack m = stack("satellite_facility", 1);
        if (!m.isEmpty()) reg.addRecipeCatalyst(m, TYPE);
    }

    @Override public RecipeType<Recipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("block.wmd.satellite_facility"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return W; }
    @Override public int getHeight() { return H; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, Recipe r, IFocusGroup focuses) {
        int x = 2;
        for (ItemStack in : r.inputs()) {
            if (!in.isEmpty()) b.addSlot(RecipeIngredientRole.INPUT, x, 6).setStandardSlotBackground().addItemStack(in);
            x += 19;
        }
        b.addSlot(RecipeIngredientRole.OUTPUT, 150, 6).setOutputSlotBackground().addItemStack(r.out());
    }

    @Override
    public void draw(Recipe r, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, "->", madeArrowX(), 10, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.time", String.format("%.1f", Balance.SATFAC_TICKS / 20.0)), 4, 28, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.power", Balance.SATFAC_FE_PER_TICK * 20), 4, 40, GREY, false);
        Component note = Component.translatable("jei.wmd.satfac.type", Component.translatable("gui.wmd.satfac.type." + r.type()));
        g.drawString(font, note, W - 4 - font.width(note), 28, 0xFF1F4E9A, false);
    }

    private static int madeArrowX() { return 2 + 6 * 19 + 10; }
}
