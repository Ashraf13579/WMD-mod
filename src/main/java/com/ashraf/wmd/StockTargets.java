package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

final class StockTargets {

    private StockTargets() {}

    interface Target {
        BlockPos pos();

        String group();

        ItemStack current();

        String fill();

        boolean accepts(ItemStack one);

        boolean full();

        ItemStack pick(Container store);

        boolean load(ServerLevel sl, ItemStack one);

        default ItemStack unload(ServerLevel sl) { return ItemStack.EMPTY; }
        default boolean unloadable() { return false; }

        default int roomFor(ItemStack one) { return room(this, one) ? 1 : 0; }
    }

    static List<Target> of(ServerLevel sl, UUID ship, UUID owner) {
        List<Target> out = new ArrayList<>();
        if (!Crafts.ownedBy(sl, ship, owner)) return out;
        for (BlockPos p : SableBridge.blockEntitiesOn(sl, ship)) {
            BlockEntity be = sl.getBlockEntity(p);
            if (be == null || (be instanceof Owned o && (o.getOwner() == null || !o.getOwner().equals(owner)))) continue;
            Target t = target(be);
            if (t != null) out.add(t);
        }
        return out;
    }

    static String blockId(BlockEntity be) { return BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath(); }

    static Target target(BlockEntity be) {
        if (be instanceof AirStationBlockEntity st) return new Target() {
            public BlockPos pos() { return st.getBlockPos(); }
            public String group() { return blockId(st); }
            public ItemStack current() { return st.loadedShown() < 0 ? ItemStack.EMPTY : AirStores.stack(st.loadedShown(), 1); }
            public String fill() { return ""; }
            public boolean accepts(ItemStack one) { int k = AirStores.kindOf(one); return k >= 0 && st.accepts(k); }
            public boolean full() { return st.loadedShown() >= 0; }
            public ItemStack pick(Container s) { return full() ? ItemStack.EMPTY : store(s, k -> st.accepts(k)); }
            public boolean load(ServerLevel sl, ItemStack one) { int k = AirStores.kindOf(one); return k >= 0 && st.load(k, AirStores.freqOf(one)); }
            public ItemStack unload(ServerLevel sl) { return st.unload(); }
            public boolean unloadable() { return true; }
        };
        if (be instanceof WeaponsBayBlockEntity wb) return new Target() {
            public BlockPos pos() { return wb.getBlockPos(); }
            public String group() { return blockId(wb); }
            public ItemStack current() { return wb.loaded() < 0 ? ItemStack.EMPTY : AirStores.stack(wb.loaded(), 1); }
            public String fill() { return ""; }
            public boolean accepts(ItemStack one) { int k = AirStores.kindOf(one); return k >= 0 && wb.accepts(k); }
            public boolean full() { return wb.loaded() >= 0; }
            public ItemStack pick(Container s) { return full() ? ItemStack.EMPTY : store(s, k -> wb.accepts(k)); }
            public boolean load(ServerLevel sl, ItemStack one) { int k = AirStores.kindOf(one); return k >= 0 && wb.load(k, AirStores.freqOf(one)); }
            public ItemStack unload(ServerLevel sl) { return wb.unload(); }
            public boolean unloadable() { return true; }
        };
        if (be instanceof GunPodBlockEntity gp) return new Target() {
            public BlockPos pos() { return gp.getBlockPos(); }
            public String group() { return blockId(gp); }
            public ItemStack current() { return gp.rounds() > 0 ? item("ciws_ammo") : ItemStack.EMPTY; }
            public String fill() { return gp.rounds() + " / " + Balance.GPOD_ROUNDS; }
            public boolean accepts(ItemStack one) { return "ciws_ammo".equals(id(one)); }
            public boolean full() { return gp.rounds() + Balance.GPOD_BOX > Balance.GPOD_ROUNDS; }
            public ItemStack pick(Container s) { return full() ? ItemStack.EMPTY : first(s, "ciws_ammo"); }
            public boolean load(ServerLevel sl, ItemStack one) { return "message.wmd.gun_pod.loaded".equals(gp.loadBox()); }
            public int roomFor(ItemStack one) { return accepts(one) ? Math.max(0, (Balance.GPOD_ROUNDS - gp.rounds()) / Balance.GPOD_BOX) : 0; }
        };
        if (be instanceof RocketPodBlockEntity rp) return new Target() {
            public BlockPos pos() { return rp.getBlockPos(); }
            public String group() { return blockId(rp); }
            public ItemStack current() { return rp.rockets() > 0 ? item("air_rocket") : ItemStack.EMPTY; }
            public String fill() { return rp.rockets() + " / " + Balance.RPOD_TUBES; }
            public boolean accepts(ItemStack one) { return "air_rocket".equals(id(one)); }
            public boolean full() { return rp.rockets() >= Balance.RPOD_TUBES; }
            public ItemStack pick(Container s) { return full() ? ItemStack.EMPTY : first(s, "air_rocket"); }
            public boolean load(ServerLevel sl, ItemStack one) { return rp.loadOne(); }
            public int roomFor(ItemStack one) { return accepts(one) ? Math.max(0, Balance.RPOD_TUBES - rp.rockets()) : 0; }
        };
        if (be instanceof GunTurretBlockEntity gt) return new Target() {
            public BlockPos pos() { return gt.getBlockPos(); }
            public String group() { return blockId(gt); }
            public ItemStack current() { return gt.rounds() > 0 ? item("ciws_ammo") : ItemStack.EMPTY; }
            public String fill() { return gt.rounds() + " / " + Balance.GTUR_ROUNDS; }
            public boolean accepts(ItemStack one) { return "ciws_ammo".equals(id(one)); }
            public boolean full() { return gt.rounds() + Balance.GTUR_BOX > Balance.GTUR_ROUNDS; }
            public ItemStack pick(Container s) { return full() ? ItemStack.EMPTY : first(s, "ciws_ammo"); }
            public boolean load(ServerLevel sl, ItemStack one) { return "message.wmd.gun_turret.loaded".equals(gt.loadBox()); }
            public int roomFor(ItemStack one) { return accepts(one) ? Math.max(0, (Balance.GTUR_ROUNDS - gt.rounds()) / Balance.GTUR_BOX) : 0; }
        };
        if (be instanceof NavalGunBlockEntity ng) return new Target() {
            public BlockPos pos() { return ng.getBlockPos(); }
            public String group() { return blockId(ng); }
            public ItemStack current() { int r = ng.nextRound(false); return r < 0 ? ItemStack.EMPTY : NavalGunBlockEntity.roundItem(r); }
            public String fill() { return ng.rounds() + " / " + ng.roundsMax() + (ng.spec().xl() ? "  " + ng.charges() + " C" : ""); }
            public boolean accepts(ItemStack one) { return ng.accepts(one); }
            public boolean full() { return ng.rackCount() >= ng.spec().rack() && (!ng.spec().xl() || ng.charges() >= ng.spec().charges()); }
            public ItemStack pick(Container s) {
                for (int i = 0; i < s.getContainerSize(); i++) { ItemStack it = s.getItem(i); if (!it.isEmpty() && ng.accepts(it) && ng.room(it)) return it.copyWithCount(1); }
                return ItemStack.EMPTY;
            }
            public boolean load(ServerLevel sl, ItemStack one) { return ng.loadOne(one); }
            public int roomFor(ItemStack one) { return ng.accepts(one) ? Math.max(0, ng.roomCount(one)) : 0; }
        };
        if (be instanceof HeavyGunBlockEntity hg) return new Target() {
            public BlockPos pos() { return hg.getBlockPos(); }
            public String group() { return blockId(hg); }
            public ItemStack current() { return hg.rounds() > 0 ? item("shell_howitzer_he") : ItemStack.EMPTY; }
            public String fill() { return hg.rackCount() + " / " + Balance.HGUN_RACK; }
            public boolean accepts(ItemStack one) { return HeavyGunBlockEntity.accepts(one); }
            public boolean full() { return hg.rackFull(); }
            public ItemStack pick(Container s) {
                if (full()) return ItemStack.EMPTY;
                for (int i = 0; i < s.getContainerSize(); i++) { ItemStack it = s.getItem(i); if (HeavyGunBlockEntity.accepts(it)) return it.copyWithCount(1); }
                return ItemStack.EMPTY;
            }
            public boolean load(ServerLevel sl, ItemStack one) { return hg.loadRack(one); }
            public int roomFor(ItemStack one) { return HeavyGunBlockEntity.accepts(one) ? Math.max(0, Balance.HGUN_RACK - hg.rackCount()) : 0; }
        };
        if (be instanceof CountermeasureDispenserBlockEntity d) return new Target() {
            public BlockPos pos() { return d.getBlockPos(); }
            public String group() { return blockId(d); }
            public ItemStack current() { return d.flares() > 0 ? item("flare_pack") : d.chaff() > 0 ? item("chaff_pod") : ItemStack.EMPTY; }
            public String fill() { return d.flares() + " F  " + d.chaff() + " C"; }
            public boolean accepts(ItemStack one) { String i = id(one); return "flare_pack".equals(i) || "chaff_pod".equals(i); }
            public boolean full() { return d.flares() >= Balance.AAM_DISPENSER_FLARES && d.chaff() >= Balance.AAM_DISPENSER_CHAFF; }
            public ItemStack pick(Container s) {
                if (d.flares() < Balance.AAM_DISPENSER_FLARES) { ItemStack f = first(s, "flare_pack"); if (!f.isEmpty()) return f; }
                return d.chaff() < Balance.AAM_DISPENSER_CHAFF ? first(s, "chaff_pod") : ItemStack.EMPTY;
            }
            public boolean load(ServerLevel sl, ItemStack one) { return d.reload(sl, "flare_pack".equals(id(one)) ? AirMissiles.FLARE : AirMissiles.CHAFF) == null; }
            public int roomFor(ItemStack one) {
                boolean f = "flare_pack".equals(id(one));
                if (!f && !"chaff_pod".equals(id(one))) return 0;
                int gap = f ? Balance.AAM_DISPENSER_FLARES - d.flares() : Balance.AAM_DISPENSER_CHAFF - d.chaff();
                return gap <= 0 ? 0 : (gap + Balance.DISP_PER_ITEM - 1) / Balance.DISP_PER_ITEM;
            }
        };
        if (be instanceof JatoRackBlockEntity j) return new Target() {
            public BlockPos pos() { return j.getBlockPos(); }
            public String group() { return blockId(j); }
            public ItemStack current() { return j.empty() ? ItemStack.EMPTY : item("jato_booster"); }
            public String fill() { return ""; }
            public boolean accepts(ItemStack one) { return "jato_booster".equals(id(one)); }
            public boolean full() { return !j.empty(); }
            public ItemStack pick(Container s) { return j.empty() ? first(s, "jato_booster") : ItemStack.EMPTY; }
            public boolean load(ServerLevel sl, ItemStack one) { return j.loadBooster(); }
        };
        if (be instanceof CiwsBlockEntity c) {
            IItemHandler h = c.itemPort(c.getBlockPos(), Direction.UP);
            if (h != null) return new Target() {
                public BlockPos pos() { return c.getBlockPos(); }
                public String group() { return blockId(c); }
                public ItemStack current() { for (int i = 0; i < h.getSlots(); i++) if (!h.getStackInSlot(i).isEmpty()) return h.getStackInSlot(i).copy(); return ItemStack.EMPTY; }
                public String fill() { int n = 0; for (int i = 0; i < h.getSlots(); i++) n += h.getStackInSlot(i).getCount(); return n > 0 ? n + " box" : ""; }
                public boolean accepts(ItemStack one) { return h.getSlots() > 0 && h.isItemValid(0, one); }
                public boolean full() { return !fits(h, item("ciws_ammo")); }
                public ItemStack pick(Container s) {
                    for (int i = 0; i < s.getContainerSize(); i++) {
                        ItemStack one = s.getItem(i).copyWithCount(1);
                        if (!one.isEmpty() && fits(h, one)) return one;
                    }
                    return ItemStack.EMPTY;
                }
                public boolean load(ServerLevel sl, ItemStack one) {
                    for (int i = 0; i < h.getSlots(); i++) if (h.insertItem(i, one.copy(), false).isEmpty()) return true;
                    return false;
                }
                public int roomFor(ItemStack one) {
                    if (one.isEmpty()) return 0;
                    int n = 0, max = one.getMaxStackSize();
                    for (int i = 0; i < h.getSlots(); i++) n += max - h.insertItem(i, one.copyWithCount(max), true).getCount();
                    return n;
                }
            };
        }
        if (isSlotWeapon(be) && be instanceof GuiItemSlots g) return new SlotTarget(be, g);
        return null;
    }

    static boolean isSlotWeapon(BlockEntity be) {
        return be instanceof SamSiteBlockEntity || be instanceof TorpedoLauncherBlockEntity || be instanceof TorpedoRackBlockEntity
                || be instanceof DepthChargeRackBlockEntity || be instanceof DepthChargeThrowerBlockEntity || be instanceof NoisemakerLauncherBlockEntity
                || be instanceof LaunchCellBlockEntity || be instanceof BombRackBlockEntity;
    }

    private static final class SlotTarget implements Target {
        private final BlockEntity be;
        private final GuiItemSlots g;
        SlotTarget(BlockEntity be, GuiItemSlots g) { this.be = be; this.g = g; }
        public BlockPos pos() { return be.getBlockPos(); }
        public String group() { return blockId(be); }

        private int limit(Container c, int i, ItemStack s) {
            int m = Math.min(c.getMaxStackSize(), s.getMaxStackSize());
            if (be instanceof ItemProcessBlockEntity ip && i < ip.slotCount()) m = Math.min(m, ip.slotMax(i));
            return m;
        }

        private int slotFor(Container c, ItemStack one) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack cur = c.getItem(i);
                if (!c.canPlaceItem(i, one)) continue;
                if (cur.isEmpty() || (ItemStack.isSameItemSameComponents(cur, one) && cur.getCount() < limit(c, i, one))) return i;
            }
            return -1;
        }

        public ItemStack current() {
            Container c = g.slotContainer();
            for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) return c.getItem(i).copyWithCount(1);
            return ItemStack.EMPTY;
        }

        public String fill() {
            Container c = g.slotContainer();
            int n = 0, cap = 0;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack cur = c.getItem(i);
                n += cur.getCount();
                cap += be instanceof ItemProcessBlockEntity ip && i < ip.slotCount() ? ip.slotMax(i) : Math.max(1, cur.getMaxStackSize());
            }
            return n + " / " + cap;
        }

        public boolean accepts(ItemStack one) {
            Container c = g.slotContainer();
            for (int i = 0; i < c.getContainerSize(); i++) if (c.canPlaceItem(i, one)) return true;
            return false;
        }

        public boolean full() {
            Container c = g.slotContainer();
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack cur = c.getItem(i);
                if (cur.isEmpty() || cur.getCount() < limit(c, i, cur)) return false;
            }
            return true;
        }

        public ItemStack pick(Container s) {
            Container c = g.slotContainer();
            for (int k = 0; k < s.getContainerSize(); k++) {
                ItemStack one = s.getItem(k).copyWithCount(1);
                if (!one.isEmpty() && slotFor(c, one) >= 0) return one;
            }
            return ItemStack.EMPTY;
        }

        boolean room(ItemStack one) { return slotFor(g.slotContainer(), one) >= 0; }

        public int roomFor(ItemStack one) {
            Container c = g.slotContainer();
            int n = 0;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack cur = c.getItem(i);
                if (!c.canPlaceItem(i, one)) continue;
                if (cur.isEmpty()) n += limit(c, i, one);
                else if (ItemStack.isSameItemSameComponents(cur, one)) n += Math.max(0, limit(c, i, one) - cur.getCount());
            }
            return n;
        }

        public boolean load(ServerLevel sl, ItemStack one) {
            Container c = g.slotContainer();
            int i = slotFor(c, one);
            if (i < 0) return false;
            ItemStack cur = c.getItem(i);
            c.setItem(i, cur.isEmpty() ? one.copyWithCount(1) : cur.copyWithCount(cur.getCount() + 1));
            c.setChanged();
            be.setChanged();
            return true;
        }

        public ItemStack unload(ServerLevel sl) {
            Container c = g.slotContainer();
            for (int i = c.getContainerSize() - 1; i >= 0; i--) {
                ItemStack cur = c.getItem(i);
                if (cur.isEmpty() || !c.canPlaceItem(i, cur.copyWithCount(1))) continue;
                ItemStack one = cur.copyWithCount(1);
                c.setItem(i, cur.getCount() > 1 ? cur.copyWithCount(cur.getCount() - 1) : ItemStack.EMPTY);
                c.setChanged();
                be.setChanged();
                return one;
            }
            return ItemStack.EMPTY;
        }

        public boolean unloadable() { return true; }
    }

    static boolean room(Target t, ItemStack one) {
        if (t instanceof SlotTarget s) return s.room(one);
        return t.accepts(one) && !t.full();
    }

    private interface KindOk { boolean ok(int kind); }

    private static ItemStack store(Container s, KindOk ok) {
        ItemStack drop = ItemStack.EMPTY;
        for (int i = 0; i < s.getContainerSize(); i++) {
            ItemStack it = s.getItem(i);
            int k = AirStores.kindOf(it);
            if (k < 0 || !ok.ok(k)) continue;
            if (AirStores.isMissile(k)) return it.copyWithCount(1);
            if (drop.isEmpty()) drop = it.copyWithCount(1);
        }
        return drop;
    }

    private static ItemStack first(Container s, String id) {
        for (int i = 0; i < s.getContainerSize(); i++) if (id.equals(id(s.getItem(i)))) return s.getItem(i).copyWithCount(1);
        return ItemStack.EMPTY;
    }

    static ItemStack item(String id) {
        var it = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id));
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    static String id(ItemStack s) {
        if (s.isEmpty()) return "";
        var k = BuiltInRegistries.ITEM.getKey(s.getItem());
        return k != null && Wmd.MODID.equals(k.getNamespace()) ? k.getPath() : "";
    }

    private static boolean fits(IItemHandler h, ItemStack one) {
        if (one.isEmpty()) return false;
        for (int i = 0; i < h.getSlots(); i++) if (h.insertItem(i, one.copy(), true).isEmpty()) return true;
        return false;
    }
}
