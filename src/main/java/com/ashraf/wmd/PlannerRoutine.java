package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

final class PlannerRoutine {

    static final int MAX_STEPS = 12;

    static final int AMT_ONE = 0, AMT_LOAD = 1, AMT_FILL = 2, AMT_N = 3;

    static final int RS_STOPPED = 0, RS_ON = 1, RS_COND = 2, RS_STEP_WAIT = 3, RS_BUSY = 4, RS_GONE = 5, RS_DONE = 6;

    record Step(long src, long dst, Item item, int amt) {
        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putLong("src", src);
            t.putLong("dst", dst);
            t.putString("item", BuiltInRegistries.ITEM.getKey(item).toString());
            t.putInt("amt", amt);
            return t;
        }

        static Step read(CompoundTag t) {
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.getString("item")));
            return it == Items.AIR ? null : new Step(t.getLong("src"), t.getLong("dst"), it, Math.floorMod(t.getInt("amt"), AMT_N));
        }
    }

    final int id;
    final UUID vehicle;
    final List<Step> steps = new ArrayList<>();
    boolean loop = true, running;
    int cond, at, waits;
    int state = RS_STOPPED;

    PlannerRoutine(int id, UUID vehicle) {
        this.id = id;
        this.vehicle = vehicle;
    }

    void advance() {
        waits = 0;
        at++;
        if (at >= steps.size()) {
            at = 0;
            if (!loop) running = false;
        }
    }

    CompoundTag write() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putUUID("veh", vehicle);
        ListTag l = new ListTag();
        for (Step s : steps) l.add(s.write());
        t.put("steps", l);
        t.putBoolean("loop", loop);
        t.putBoolean("run", running);
        t.putInt("cond", cond);
        t.putInt("at", at);
        return t;
    }

    static PlannerRoutine read(CompoundTag t) {
        if (!t.hasUUID("veh")) return null;
        PlannerRoutine r = new PlannerRoutine(t.getInt("id"), t.getUUID("veh"));
        ListTag l = t.getList("steps", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size() && r.steps.size() < MAX_STEPS; i++) { Step s = Step.read(l.getCompound(i)); if (s != null) r.steps.add(s); }
        r.loop = t.getBoolean("loop");
        r.running = t.getBoolean("run") && !r.steps.isEmpty();
        r.cond = Math.floorMod(t.getInt("cond"), Logistics.COND_N);
        r.at = r.steps.isEmpty() ? 0 : Math.floorMod(t.getInt("at"), r.steps.size());
        return r;
    }
}
