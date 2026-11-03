package com.ashraf.wmd.jei;

import java.util.ArrayList;
import java.util.List;

import com.ashraf.wmd.Wmd;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

public record Proc(List<Ing> in, List<Ing> out, List<Component> lines) {

    public record Ing(List<ItemStack> items, Fluid fluid, int mb) {
        public boolean isFluid() { return fluid != null && mb > 0; }
        public boolean empty() { return !isFluid() && (items == null || items.isEmpty()); }
    }

    public Proc {
        in = in.stream().filter(i -> !i.empty()).toList();
        out = out.stream().filter(i -> !i.empty()).toList();
        lines = List.copyOf(lines);
    }

    public static ItemStack stack(String id, int n) {
        ResourceLocation rl = id.contains(":") ? ResourceLocation.parse(id) : ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id);
        Item it = BuiltInRegistries.ITEM.get(rl);
        return it == Items.AIR || n <= 0 ? ItemStack.EMPTY : new ItemStack(it, n);
    }

    public static Ing item(String id, int n) { ItemStack s = stack(id, n); return new Ing(s.isEmpty() ? List.of() : List.of(s), null, 0); }

    public static Ing item(ItemStack s) { return new Ing(s.isEmpty() ? List.of() : List.of(s), null, 0); }

    public static Ing any(List<ItemStack> l) { return new Ing(l.stream().filter(s -> !s.isEmpty()).toList(), null, 0); }

    public static Ing anyOf(int n, String... ids) {
        List<ItemStack> l = new ArrayList<>();
        for (String id : ids) { ItemStack s = stack(id, n); if (!s.isEmpty()) l.add(s); }
        return new Ing(l, null, 0);
    }

    public static Ing tag(String tagId, int n) {
        TagKey<Item> t = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId));
        List<ItemStack> l = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(t).ifPresent(set -> set.forEach(h -> l.add(new ItemStack(h.value(), n))));
        return new Ing(l, null, 0);
    }

    public static Ing fluid(Fluid f, int mb) { return new Ing(List.of(), f, mb); }

    static String n(long v) { return String.format("%,d", v); }

    static String sec(int ticks) {
        double s = ticks / 20.0;
        return s == Math.rint(s) ? n((long) s) : String.format("%.1f", s);
    }

    public static Component time(int ticks) { return Component.translatable("jei.wmd.time", sec(ticks)); }

    public static Component uses(int fePerTick) { return Component.translatable("jei.wmd.power", n(fePerTick * 20L)); }

    public static Component flow(int mbPerTick) {
        return Component.translatable("jei.wmd.flow", sec(1000 / Math.max(1, mbPerTick)), n(mbPerTick * 20L));
    }

    public static Component makes(int fePerTick) { return Component.translatable("jei.wmd.makes", n(fePerTick * 20L)); }

    public static Component note(String key, Object... args) { return Component.translatable(key, args); }
}
