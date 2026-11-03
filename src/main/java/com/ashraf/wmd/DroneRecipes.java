package com.ashraf.wmd;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

public final class DroneRecipes {

    private DroneRecipes() {}

    public record Need(ResourceLocation id, int count) {
        public Item item() { return BuiltInRegistries.ITEM.get(id); }
        public boolean test(ItemStack s) { return !s.isEmpty() && s.is(item()); }
    }

    private static Need n(String id, int count) { return new Need(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id), count); }

    public static List<Need> needs(DroneType t) {
        return switch (t) {
            case T1 -> List.of(n("aluminium_plate", 4), n("copper_wire", 6), n("battery_t1", 1), n("basic_circuit", 2));
            case T2 -> List.of(n("aluminium_plate", 6), n("copper_wire", 8), n("battery_t1", 1), n("advanced_circuit", 1), n("cam_pod", 1), n("he_charge", 1));
            case T3 -> List.of(n("aluminium_plate", 8), n("titanium_plate", 4), n("jet_engine", 1), n("alkylate_canister", 2), n("advanced_circuit", 2), n("guidance_t2", 1));
            case T4 -> List.of(n("titanium_plate", 12), n("jet_engine", 2), n("alkylate_canister", 4), n("advanced_circuit", 4), n("guidance_t3", 1),
                    n("laser_designator_pod", 1), n("uplink_cell", 1));
        };
    }

    public static boolean accepts(ItemStack s) {
        if (s.isEmpty()) return false;
        for (DroneType t : DroneType.values()) for (Need n : needs(t)) if (n.test(s)) return true;
        return false;
    }

    public static int have(IItemHandler c, Need n) {
        int sum = 0;
        for (int i = 0; i < c.getSlots(); i++) if (n.test(c.getStackInSlot(i))) sum += c.getStackInSlot(i).getCount();
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
                if (!n.test(c.getStackInSlot(i))) continue;
                left -= c.extractItem(i, left, false).getCount();
            }
        }
    }
}
