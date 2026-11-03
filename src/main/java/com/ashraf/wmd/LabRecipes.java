package com.ashraf.wmd;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

public final class LabRecipes {

    private LabRecipes() {}

    public record Need(String key, int count, Predicate<ItemStack> test) {}

    private static Need item(String key, int n, String id) {
        ResourceLocation rl = ResourceLocation.parse(id);
        return new Need(key, n, s -> s.is(BuiltInRegistries.ITEM.get(rl)));
    }

    private static Need tag(String key, int n, String id) {
        TagKey<Item> t = TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
        return new Need(key, n, s -> s.is(t));
    }

    private static Need he(int n) { return item("he_charge", n, "wmd:he_charge"); }
    private static Need steel(int n) { return tag("steel", n, "c:ingots/steel"); }
    private static Need alu(int n) { return tag("aluminium", n, "c:ingots/aluminum"); }
    private static Need copper(int n) { return tag("copper", n, "c:ingots/copper"); }

    private static Need copperWire(int n) { return item("copper_wire", n, "wmd:copper_wire"); }
    private static Need bronze(int n) { return tag("bronze", n, "c:ingots/bronze"); }
    private static Need tungsten(int n) { return tag("tungsten", n, "c:ingots/tungsten"); }
    private static Need coke(int n) { return item("petroleum_coke", n, "wmd:petroleum_coke"); }
    private static Need basic(int n) { return item("basic_circuit", n, "wmd:basic_circuit"); }
    private static Need adv(int n) { return item("advanced_circuit", n, "wmd:advanced_circuit"); }

    private static Need chipW2() { return item("chip_warheads_2", 1, "wmd:chip_warheads_2"); }

    public static List<Need> needs(WarheadType t) {
        return switch (t) {
            case HE -> List.of(he(2), steel(2));
            case INCENDIARY -> List.of(he(1), coke(2), alu(2));
            case FRAG -> List.of(he(1), steel(4));
            case DECOY -> List.of(alu(2), adv(1));
            case THERMOBARIC -> List.of(he(1), coke(4), steel(2), item("heat_shield_tile", 1, "wmd:heat_shield_tile"));
            case CLUSTER -> List.of(he(3), steel(2), bronze(2));
            case BREACHER -> List.of(he(1), copper(2), steel(2), tungsten(1));
            case CHAFF -> List.of(alu(6), steel(1), basic(1));
            case BUNKER -> List.of(tungsten(3), he(2), steel(2), chipW2());
            case EMP -> List.of(copperWire(18), item("battery_t1", 1, "wmd:battery_t1"), adv(2), steel(1), chipW2());
            case NUKE -> List.of(item("plutonium", 1, "wmd:plutonium"), tungsten(2), he(3), adv(2), chipW2());
            case INTERCEPTOR -> List.of(he(1), alu(2), basic(1));
            case ROD -> List.of(tungsten(6), he(1), adv(2), steel(3), chipW2());
            case HEMP -> List.of(copperWire(36), item("battery_t1", 4, "wmd:battery_t1"), adv(6), steel(2), chipW2());
            case SEISMIC -> List.of(he(8), steel(6), adv(3), tungsten(2), chipW2());
            case THERMO -> List.of(item("warhead_nuke", 1, "wmd:warhead_nuke"), item("plutonium", 1, "wmd:plutonium"), tungsten(6), he(6), adv(4), item("heat_shield_tile", 4, "wmd:heat_shield_tile"), steel(4));
            case ASAT -> List.of(tungsten(4), item("guidance_core_t4", 1, "wmd:guidance_core_t4"), adv(4), he(1), steel(2), chipW2());
            case CUBESAT -> List.of(item("cubesat", 1, "wmd:cubesat"), alu(2), adv(1));

            case NEUTRON -> List.of(item("plutonium", 1, "wmd:plutonium"), tungsten(2), he(2), adv(3), chipW2());
            case FLASH -> List.of(item("magnesium_ingot", 6, "wmd:magnesium_ingot"), alu(2), he(1), basic(1));
            case CONCUSSION -> List.of(he(6), steel(4), adv(1));
            case CHEMICAL -> List.of(item("fermented_spider_eye", 6, "minecraft:fermented_spider_eye"), he(1), steel(2), basic(1));

            case NANITE -> List.of(adv(6), item("silicon", 8, "wmd:silicon"), item("platinum_ingot", 4, "wmd:platinum_ingot"), copperWire(12), chipW2());
            case GRAVITY -> List.of(item("superconducting_coil", 2, "wmd:superconducting_coil"), tungsten(4), he(2), adv(3), chipW2());
            case CRYO -> List.of(item("blue_ice", 8, "minecraft:blue_ice"), item("titanium_ingot", 4, "wmd:titanium_ingot"), he(1), adv(2), chipW2());
            case PLASMA -> List.of();
            case RAILGUN -> List.of();
            case ORBITAL_ROD -> List.of();
            case ASTEROID -> List.of();
            case TSAR -> List.of();
            case ANTIMATTER -> List.of(item("antimatter_cell", 2, "wmd:antimatter_cell"), item("heat_shield_tile", 4, "wmd:heat_shield_tile"), tungsten(6), adv(6), steel(4));
            case HEAVY_BLAST, CARPET, UPHEAVAL, NAPALM, PENETRATOR, DAISY_CUTTER, EARTHQUAKE, DAM_BREAKER -> List.of();
        };
    }

    public static boolean accepts(ItemStack s) {
        if (s.isEmpty()) return false;
        for (WarheadType t : WarheadType.values()) for (Need n : needs(t)) if (n.test().test(s)) return true;
        return false;
    }

    public static ItemStack warheadStack(WarheadType t) {
        for (var it : Wmd.partItems()) {
            MissilePartItem p = it.get();
            if (p.slot() == 3 && p.variant().equals(t.id)) return new ItemStack(p);
        }
        return ItemStack.EMPTY;
    }

    public static int have(SimpleContainer c, Need n) {
        int sum = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (n.test().test(c.getItem(i))) sum += c.getItem(i).getCount();
        return sum;
    }

    public static boolean satisfied(SimpleContainer c, List<Need> needs) {
        for (Need n : needs) if (have(c, n) < n.count()) return false;
        return true;
    }

    public static void consume(SimpleContainer c, List<Need> needs) {
        for (Need n : needs) {
            int left = n.count();
            for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
                ItemStack s = c.getItem(i);
                if (!n.test().test(s)) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
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
