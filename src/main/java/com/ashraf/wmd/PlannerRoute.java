package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

final class PlannerRoute {

    static final int MAX_PRIO = 9, MAX_KEEP = Balance.DEPOT_CELLS;

    final int id;
    long src;
    final long dst;
    Item item;
    int keep = 1, prio = 5;
    UUID vehicle;
    boolean on = true;
    int cond;
    CargoFilter filter = CargoFilter.ANY;
    static final int MODE_KEEP = 0, MODE_ALL = 1;
    final java.util.List<Item> also = new java.util.ArrayList<>();
    boolean anyItem;
    int mode = MODE_KEEP;

    boolean bulk(boolean chestTarget) { return chestTarget || anyItem || !also.isEmpty(); }

    java.util.List<Item> items() {
        java.util.List<Item> l = new java.util.ArrayList<>();
        l.add(item);
        for (Item it : also) if (!l.contains(it)) l.add(it);
        return l;
    }

    PlannerRoute(int id, long src, long dst, Item item) {
        this.id = id;
        this.src = src;
        this.dst = dst;
        this.item = item;
    }

    CompoundTag write() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putLong("src", src);
        t.putLong("dst", dst);
        t.putString("item", BuiltInRegistries.ITEM.getKey(item).toString());
        t.putInt("keep", keep);
        t.putInt("prio", prio);
        if (vehicle != null) t.putUUID("veh", vehicle);
        t.putBoolean("on", on);
        t.putInt("cond", cond);
        filter.write(t);
        if (!also.isEmpty()) {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (Item it : also) l.add(net.minecraft.nbt.StringTag.valueOf(BuiltInRegistries.ITEM.getKey(it).toString()));
            t.put("also", l);
        }
        if (anyItem) t.putBoolean("any", true);
        if (mode != MODE_KEEP) t.putInt("mode", mode);
        return t;
    }

    static PlannerRoute read(CompoundTag t) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.getString("item")));
        if (it == Items.AIR) return null;
        PlannerRoute r = new PlannerRoute(t.getInt("id"), t.getLong("src"), t.getLong("dst"), it);
        r.keep = Math.max(1, Math.min(Balance.RUNNER_UP_KEEP, t.contains("keep") ? t.getInt("keep") : 1));
        r.prio = Math.max(0, Math.min(MAX_PRIO, t.contains("prio") ? t.getInt("prio") : 5));
        r.vehicle = t.hasUUID("veh") ? t.getUUID("veh") : null;
        r.on = !t.contains("on") || t.getBoolean("on");
        r.cond = Math.floorMod(t.getInt("cond"), Logistics.COND_N);
        r.filter = CargoFilter.read(t);
        net.minecraft.nbt.ListTag al = t.getList("also", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < al.size() && r.also.size() < Balance.RUNNER_UP_ITEMS - 1; i++) {
            Item a = BuiltInRegistries.ITEM.get(ResourceLocation.parse(al.getString(i)));
            if (a != Items.AIR && a != it && !r.also.contains(a)) r.also.add(a);
        }
        r.anyItem = t.getBoolean("any");
        r.mode = t.getInt("mode") == MODE_ALL ? MODE_ALL : MODE_KEEP;
        return r;
    }
}
