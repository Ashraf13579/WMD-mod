package com.ashraf.wmd;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;

public final class BombLineRecipes {

    private BombLineRecipes() {}

    public record Need(Item shown, int count, Predicate<ItemStack> test) {}

    private static Need item(Item it, int n) { return new Need(it, n, s -> s.is(it)); }

    private static Need wmd(String id, int n) { return item(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id)), n); }

    private static Need tag(String tag, String shownId, int n) {
        TagKey<Item> t = TagKey.create(Registries.ITEM, ResourceLocation.parse(tag));
        return new Need(BuiltInRegistries.ITEM.get(ResourceLocation.parse(shownId)), n, s -> s.is(t));
    }

    private static Need plates(int n) { return tag("c:plates/steel", "wmd:steel_plate", n); }
    private static Need he(int n) { return wmd("he_charge", n); }
    private static Need alu(int n) { return tag("c:ingots/aluminum", "wmd:aluminium_ingot", n); }
    private static Need tungsten(int n) { return tag("c:ingots/tungsten", "wmd:tungsten_ingot", n); }

    public static List<Need> needs(BombType t) {
        return switch (t) {
            case IRON -> List.of(plates(3), he(1));
            case LASER -> List.of(plates(3), he(3), wmd("laser_seeker", 1));
            case GLIDE -> List.of(plates(3), he(3), wmd("aluminium_plate", 4));
            case CLUSTER -> List.of(plates(3), he(2), item(Items.IRON_INGOT, 4));
            case DELAYED -> List.of(plates(3), he(1), item(Items.CLOCK, 1));
            case NAPALM -> List.of(plates(2), alu(2));
            case PENETRATOR -> List.of(plates(6), tungsten(3), he(2));
            case DAISY -> List.of(plates(8), he(4), alu(4));
            case TALLBOY -> List.of(plates(6), he(4), alu(2), tungsten(2));
            case BOUNCING -> List.of(plates(8), he(3));
            case TSAR -> List.of(plates(16), wmd("warhead_thermo", 3), wmd("plutonium", 4), tungsten(6));
        };
    }

    public static int naphtha(BombType t) { return t == BombType.NAPALM ? Balance.BOMB_NAPHTHA_MB : 0; }

    public static int runTicks(BombType t) { return Balance.BOMB_RUN_TICKS[t.ordinal()]; }
    public static int runFe(BombType t) { return Balance.BOMB_RUN_FE[t.ordinal()]; }

    public static ItemStack stack(BombType t) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, t.id))); }

    public static boolean accepts(ItemStack s) {
        if (s.isEmpty()) return false;
        for (BombType t : BombType.values()) for (Need n : needs(t)) if (n.test().test(s)) return true;
        return false;
    }

    public static int have(IItemHandler c, Need n) {
        int sum = 0;
        for (int i = 0; i < c.getSlots(); i++) if (n.test().test(c.getStackInSlot(i))) sum += c.getStackInSlot(i).getCount();
        return sum;
    }

    public static boolean satisfied(IItemHandler c, List<Need> needs) {
        for (Need n : needs) if (have(c, n) < n.count()) return false;
        return true;
    }

    public static void consume(IItemHandler c, List<Need> needs) {
        for (Need n : needs) {
            int left = n.count();
            for (int i = 0; i < c.getSlots() && left > 0; i++) {
                if (!n.test().test(c.getStackInSlot(i))) continue;
                left -= c.extractItem(i, left, false).getCount();
            }
        }
    }
}
