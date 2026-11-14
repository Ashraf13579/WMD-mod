package com.ashraf.wmd;

import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class Ammo {

    private Ammo() {}

    private static final Set<String> IDS = Set.of("sonobuoy", "depth_charge", "noisemaker", "asroc", "slbm", "cruise_missile", "cruise_capsule",
            "ciws_ammo", "flare_pack", "chaff_pod", "jato_booster", "air_rocket",
            "ashm",
            "shell_super_he", "super_charge");
    private static final Set<String> HEAVY_IDS = Set.of("slbm", "cruise_missile", "shell_super_he", "super_charge");

    private static String id(ItemStack s) {
        ResourceLocation k = BuiltInRegistries.ITEM.getKey(s.getItem());
        return k != null && Wmd.MODID.equals(k.getNamespace()) ? k.getPath() : "";
    }

    static boolean isAmmo(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (s.getItem() instanceof AamItem || s.getItem() instanceof BombItem || s.getItem() instanceof TorpedoItem || s.getItem() instanceof SeaMineItem) return true;

        if (s.getItem() instanceof ArtShellItem a && a.gun() == Artillery.HOWITZER && a.type() != Artillery.CANISTER) return true;
        return IDS.contains(id(s));
    }

    static boolean heavy(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (s.getItem() instanceof BombItem b) return b.type().heavy;
        TorpedoKind k = TorpedoKind.of(s);
        if (k != null) return k == TorpedoKind.T3 || k == TorpedoKind.T4 || k == TorpedoKind.UUV;
        return HEAVY_IDS.contains(id(s));
    }

    static boolean carries(boolean heavyLoader, ItemStack s) { return isAmmo(s) && (heavyLoader || !heavy(s)); }
}
