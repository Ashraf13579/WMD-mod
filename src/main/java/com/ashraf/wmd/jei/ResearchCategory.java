package com.ashraf.wmd.jei;

import java.util.ArrayList;
import java.util.List;

import com.ashraf.wmd.ResearchRecipes;
import com.ashraf.wmd.Wmd;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public final class ResearchCategory implements IRecipeCategory<ResearchRecipes.Research> {

    public static final RecipeType<ResearchRecipes.Research> TYPE = RecipeType.create(Wmd.MODID, "research", ResearchRecipes.Research.class);

    static final String STATION_ID = "wmd:research_station";
    static final String BLANK_CHIP_ID = "wmd:chip_blank";

    private static final int W = 160, H = 64;
    private static final int GREY = 0xFF555555;
    private final IDrawable icon;

    ResearchCategory(IGuiHelper gui) {
        ItemStack station = stackOf(STATION_ID, 1);
        this.icon = gui.createDrawableItemStack(station.isEmpty() ? new ItemStack(Items.BOOK) : station);
    }

    static ResourceLocation id(String s) {
        return s.indexOf(':') >= 0 ? ResourceLocation.parse(s) : ResourceLocation.fromNamespaceAndPath(Wmd.MODID, s);
    }

    static Item itemOf(String s) {
        return BuiltInRegistries.ITEM.get(id(s));
    }

    static ItemStack stackOf(String s, int n) {
        Item it = itemOf(s);
        return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it, n);
    }

    static List<ItemStack> sampleStacks(ResearchRecipes.Sample s) {
        String key = s.itemOrTag();
        if (!key.startsWith("#")) {
            ItemStack st = stackOf(key, s.count());
            return st.isEmpty() ? List.of() : List.of(st);
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, id(key.substring(1)));
        List<ItemStack> out = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(tag).ifPresent(set -> set.forEach(h -> out.add(new ItemStack(h.value(), s.count()))));
        return out;
    }

    static Component unlockName(String unlock) {
        try {
            Item it = itemOf(unlock);
            if (it != Items.AIR) return it.getDescription();
        } catch (RuntimeException ignored) {

        }
        return Component.literal(unlock);
    }

    @Override public RecipeType<ResearchRecipes.Research> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.wmd.research"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return W; }
    @Override public int getHeight() { return H; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, ResearchRecipes.Research r, IFocusGroup focuses) {
        int x = 4;
        for (ResearchRecipes.Sample s : r.samples()) {
            IRecipeSlotBuilder slot = b.addSlot(RecipeIngredientRole.INPUT, x, 4).setStandardSlotBackground().addItemStacks(sampleStacks(s));
            if (s.itemOrTag().startsWith("#")) {
                String tag = s.itemOrTag().substring(1);
                slot.addRichTooltipCallback((view, tip) -> tip.add(Component.translatable("jei.wmd.research.any", tag).withStyle(ChatFormatting.GRAY)));
            }
            x += 18;
        }
        Fluid fluid = r.fluidId() == null || r.fluidId().isEmpty() ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(id(r.fluidId()));
        if (fluid != Fluids.EMPTY && r.fluidMb() > 0) {
            b.addSlot(RecipeIngredientRole.INPUT, x, 4).setStandardSlotBackground().addFluidStack(fluid, r.fluidMb());
        }
        ItemStack blank = stackOf(BLANK_CHIP_ID, 1);
        IRecipeSlotBuilder blankSlot = b.addSlot(RecipeIngredientRole.INPUT, 4, 24).setStandardSlotBackground();
        if (!blank.isEmpty()) blankSlot.addItemStack(blank);
        blankSlot.addRichTooltipCallback((view, tip) -> tip.add(Component.translatable("jei.wmd.research.blank").withStyle(ChatFormatting.GRAY)));

        ItemStack chip = stackOf(r.chipId(), 1);
        IRecipeSlotBuilder out = b.addSlot(RecipeIngredientRole.OUTPUT, W - 22, 14).setOutputSlotBackground();
        if (!chip.isEmpty()) out.addItemStack(chip);
        if (r.unlocks() != null && !r.unlocks().isEmpty()) {
            out.addRichTooltipCallback((view, tip) -> {
                tip.add(Component.translatable("jei.wmd.research.unlocks").withStyle(ChatFormatting.GOLD));
                for (String u : r.unlocks()) tip.add(Component.literal(" - ").append(unlockName(u)).withStyle(ChatFormatting.GRAY));
            });
        }
    }

    @Override
    public void draw(ResearchRecipes.Research r, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, "->", W - 40, 18, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.research.blank_short"), 24, 29, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.research.time", String.format("%.0f", r.ticks() / 20.0)), 4, 44, GREY, false);
        g.drawString(font, Component.translatable("jei.wmd.research.power", r.fePerTick(), String.format("%,d", (long) r.fePerTick() * r.ticks())), 4, 54, GREY, false);
    }
}
