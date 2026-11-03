package com.ashraf.wmd;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class RecipeLookup {

    private RecipeLookup() {}

    public static boolean available() { return ModList.get().isLoaded("emi") || ModList.get().isLoaded("jei"); }

    public static boolean show(ItemStack machine) {
        if (machine.isEmpty()) return false;
        if (ModList.get().isLoaded("emi") && emi(machine)) return true;
        return ModList.get().isLoaded("jei") && com.ashraf.wmd.jei.WmdJeiPlugin.showMachineRecipes(machine);
    }

    private static boolean emi(ItemStack machine) {
        try {
            Class<?> api = Class.forName("dev.emi.emi.api.EmiApi"), stack = Class.forName("dev.emi.emi.api.stack.EmiStack"),
                    ingredient = Class.forName("dev.emi.emi.api.stack.EmiIngredient"), category = Class.forName("dev.emi.emi.api.recipe.EmiRecipeCategory"),
                    manager = Class.forName("dev.emi.emi.api.recipe.EmiRecipeManager");
            Object es = stack.getMethod("of", ItemStack.class).invoke(null, machine);
            Object rm = api.getMethod("getRecipeManager").invoke(null);
            java.lang.reflect.Method workstations = manager.getMethod("getWorkstations", category), recipes = manager.getMethod("getRecipes", category),
                    stacks = ingredient.getMethod("getEmiStacks"), equal = stack.getMethod("isEqual", stack);
            java.util.Map<Object, java.util.List<?>> pages = new java.util.LinkedHashMap<>();
            for (Object cat : (java.util.List<?>) manager.getMethod("getCategories").invoke(rm)) {
                boolean mine = false;
                for (Object w : (java.util.List<?>) workstations.invoke(rm, cat)) {
                    for (Object s : (java.util.List<?>) stacks.invoke(w)) if ((Boolean) equal.invoke(es, s)) { mine = true; break; }
                    if (mine) break;
                }
                if (mine) pages.put(cat, (java.util.List<?>) recipes.invoke(rm, cat));
            }
            if (pages.isEmpty()) return false;
            try {
                java.lang.reflect.Method set = api.getDeclaredMethod("setPages", java.util.Map.class, ingredient);
                set.setAccessible(true);
                set.invoke(null, pages, es);
            } catch (ReflectiveOperationException | RuntimeException e) {
                api.getMethod("displayRecipeCategory", category).invoke(null, pages.keySet().iterator().next());
            }
            return true;
        } catch (Throwable t) {
            Wmd.LOGGER.debug("[recipes] EMI lookup failed, trying JEI: {}", t.toString());
            return false;
        }
    }
}
