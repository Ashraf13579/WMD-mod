package com.ashraf.wmd;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class BulkLoad {

    private BulkLoad() {}

    public static boolean supports(Object machine) {
        return machine instanceof ArtilleryGunBlockEntity || machine instanceof CruiseLauncherBlockEntity || machine instanceof ShellPressBlockEntity
                || machine instanceof AlkylationUnitBlockEntity || machine instanceof CoalGeneratorBlockEntity;
    }

    public static void handle(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        AbstractContainerMenu open = player.containerMenu;
        if (open == null || !open.stillValid(player)) return;
        int first, end;
        BlockEntity be;
        if (open instanceof MachineMenu mm && mm.machine() instanceof BlockEntity b && supports(b)) {
            be = b;
            first = 0;
            end = mm.machineSlotCount();
        } else if (open instanceof LauncherMenu lm) {
            be = player.level().getBlockEntity(lm.getPos());
            if (!(be instanceof InterceptorSite)) return;
            first = 0;
            end = lm.slotCount();
        } else return;
        if (be == null || be.isRemoved() || !be.getBlockPos().equals(pos) || end <= first) return;
        if (be instanceof Owned o && !Access.mayUse(o, player)) { Access.deny(o, player); return; }

        if (be instanceof ShellPressBlockEntity press) { loadPress(player, open, press); return; }

        Map<Item, int[]> moved = new LinkedHashMap<>();
        Inventory inv = player.getInventory();
        for (int si = first; si < end; si++) {
            Slot slot = open.slots.get(si);
            for (int k : invOrder()) {
                ItemStack st = inv.getItem(k);
                if (st.isEmpty() || !slot.mayPlace(st)) continue;
                int before = st.getCount();
                Item item = st.getItem();
                slot.safeInsert(st);
                int n = before - st.getCount();
                if (n > 0) moved.computeIfAbsent(item, x -> new int[1])[0] += n;
                if (!slot.getItem().isEmpty() && slot.getItem().getCount() >= slot.getMaxStackSize(slot.getItem())) break;
            }
        }
        inv.setChanged();
        open.broadcastChanges();
        player.displayClientMessage(moved.isEmpty() ? Component.translatable("message.wmd.bulk.none") : Component.translatable("message.wmd.bulk.loaded", list(moved)), true);
    }

    private static int[] invOrder() {
        int[] o = new int[36];
        for (int i = 0; i < 27; i++) o[i] = 9 + i;
        for (int i = 0; i < 9; i++) o[27 + i] = i;
        return o;
    }

    private static Component list(Map<Item, int[]> moved) {
        MutableComponent out = Component.empty();
        int i = 0;
        for (Map.Entry<Item, int[]> e : moved.entrySet()) {
            if (i == 3) { out.append(Component.translatable("message.wmd.bulk.more", moved.size() - 3)); break; }
            if (i > 0) out.append(", ");
            out.append(Component.translatable("message.wmd.bulk.part", e.getValue()[0], new ItemStack(e.getKey()).getHoverName()));
            i++;
        }
        return out;
    }

    private static final int CASING = 0, FILLER = 1, FUZE = 2;

    private static void loadPress(ServerPlayer player, AbstractContainerMenu menu, ShellPressBlockEntity press) {
        int mode = press.mode();
        if (mode < 0 || mode >= ShellPressBlockEntity.RECIPES.size()) return;
        ShellPressBlockEntity.Recipe r = ShellPressBlockEntity.RECIPES.get(mode);
        Component product = r.outStack().getHoverName();
        String[] ids = { r.casing(), r.filler(), r.fuze() };
        int[] need = { r.casingN(), r.fillerN(), r.fuzeN() };
        int[] slotIdx = { CASING, FILLER, FUZE };
        Inventory inv = player.getInventory();
        ItemStack[] key = new ItemStack[3];
        int batches = Integer.MAX_VALUE;
        for (int j = 0; j < 3; j++) {
            if (need[j] <= 0) continue;
            Slot slot = menu.slots.get(slotIdx[j]);
            ItemStack cur = slot.getItem();
            if (!cur.isEmpty() && !ShellPressBlockEntity.holds(cur, ids[j], 1)) {
                player.displayClientMessage(Component.translatable("message.wmd.bulk.press_blocked", cur.getHoverName(), product), true);
                return;
            }

            ItemStack k = cur.isEmpty() ? ItemStack.EMPTY : cur;
            if (k.isEmpty()) for (int i : invOrder()) { ItemStack st = inv.getItem(i); if (!st.isEmpty() && ShellPressBlockEntity.holds(st, ids[j], 1) && slot.mayPlace(st)) { k = st; break; } }
            if (k.isEmpty()) { batches = 0; break; }
            key[j] = k.copyWithCount(1);
            int have = cur.getCount();
            for (int i : invOrder()) { ItemStack st = inv.getItem(i); if (!st.isEmpty() && ItemStack.isSameItemSameComponents(st, key[j])) have += st.getCount(); }
            int cap = slot.getMaxStackSize(key[j]);
            batches = Math.min(batches, Math.min(have, cap) / need[j]);
        }
        if (batches <= 0 || batches == Integer.MAX_VALUE) {
            player.displayClientMessage(Component.translatable("message.wmd.bulk.press_none", product), true);
            return;
        }
        Map<Item, int[]> moved = new LinkedHashMap<>();
        for (int j = 0; j < 3; j++) {
            if (need[j] <= 0 || key[j] == null) continue;
            Slot slot = menu.slots.get(slotIdx[j]);
            int want = batches * need[j] - slot.getItem().getCount();
            for (int i : invOrder()) {
                if (want <= 0) break;
                ItemStack st = inv.getItem(i);
                if (st.isEmpty() || !ItemStack.isSameItemSameComponents(st, key[j])) continue;
                int before = st.getCount();
                slot.safeInsert(st, want);
                int n = before - st.getCount();
                want -= n;
                if (n > 0) moved.computeIfAbsent(key[j].getItem(), x -> new int[1])[0] += n;
            }
        }
        inv.setChanged();
        menu.broadcastChanges();
        player.displayClientMessage(moved.isEmpty() ? Component.translatable("message.wmd.bulk.press_full", batches, product)
                : Component.translatable("message.wmd.bulk.press", batches, product, list(moved)), true);
    }
}
