package com.ashraf.wmd;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

public final class AntiShipItems {

    private AntiShipItems() {}

    public static final DeferredItem<AshmItem> ASHM = Wmd.ITEMS.register("ashm", () -> new AshmItem(new Item.Properties().stacksTo(2)));

    static void init() {}

    static void tab(CreativeModeTab.Output output) { output.accept(ASHM.get()); }
}
