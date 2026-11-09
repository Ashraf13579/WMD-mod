package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.items.IItemHandler;

public final class TorpedoLineRecipes {

    private TorpedoLineRecipes() {}

    private static BombLineRecipes.Need item(net.minecraft.world.item.Item it, int n) { return new BombLineRecipes.Need(it, n, s -> s.is(it)); }

    private static BombLineRecipes.Need wmd(String id, int n) { return item(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id)), n); }

    private static BombLineRecipes.Need tag(String tag, String shownId, int n) {
        net.minecraft.tags.TagKey<net.minecraft.world.item.Item> t = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse(tag));
        return new BombLineRecipes.Need(BuiltInRegistries.ITEM.get(ResourceLocation.parse(shownId)), n, s -> s.is(t));
    }

    public static List<BombLineRecipes.Need> needs(TorpedoKind k) {
        if (k == TorpedoKind.T4)

            return new ArrayList<>(List.of(tag("c:plates/steel", "wmd:steel_plate", 6), wmd("he_charge", 3), wmd("motor_t2", 1),
                    wmd("nickel_cobalt_ingot", 2), wmd("advanced_circuit", 1)));
        if (k == TorpedoKind.ATT)
            return new ArrayList<>(List.of(tag("c:plates/steel", "wmd:steel_plate", 2), wmd("he_charge", 1), wmd("battery_t1", 1), tag("c:ingots/copper", "minecraft:copper_ingot", 1),
                    wmd("advanced_circuit", 1), item(Items.PRISMARINE_SHARD, 1)));
        if (k == TorpedoKind.UUV)
            return new ArrayList<>(List.of(tag("c:plates/steel", "wmd:steel_plate", 4), wmd("battery_t1", 2), tag("c:ingots/copper", "minecraft:copper_ingot", 2),
                    wmd("advanced_circuit", 1), item(Items.PRISMARINE_SHARD, 2)));
        List<BombLineRecipes.Need> l = new ArrayList<>(List.of(tag("c:plates/steel", "wmd:steel_plate", 4), wmd("he_charge", 2), wmd("battery_t1", 1),
                tag("c:ingots/copper", "minecraft:copper_ingot", 2), wmd("basic_circuit", 1)));
        if (k == TorpedoKind.T2 || k == TorpedoKind.T3) { l.add(wmd("advanced_circuit", 1)); l.add(item(Items.PRISMARINE_SHARD, 2)); }
        if (k == TorpedoKind.T3) l.add(item(Items.STRING, 4));
        return l;
    }

    public static int runTicks(TorpedoKind k) { return Balance.TLINE_RUN_TICKS[k.ordinal()]; }
    public static int runFe(TorpedoKind k) { return runTicks(k) * Balance.TLINE_FE_PER_TICK; }

    public static ItemStack stack(TorpedoKind k) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, k.id))); }

    public static boolean accepts(ItemStack s) {
        if (s.isEmpty()) return false;
        for (TorpedoKind k : TorpedoKind.values()) for (BombLineRecipes.Need n : needs(k)) if (n.test().test(s)) return true;
        return false;
    }

    public static boolean satisfied(IItemHandler c, List<BombLineRecipes.Need> needs) { return BombLineRecipes.satisfied(c, needs); }

    public static void consume(IItemHandler c, List<BombLineRecipes.Need> needs) { BombLineRecipes.consume(c, needs); }
}
