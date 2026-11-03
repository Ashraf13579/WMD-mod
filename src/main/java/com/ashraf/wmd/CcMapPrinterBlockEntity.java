package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class CcMapPrinterBlockEntity extends CcModuleBlockEntity implements GuiItemSlots {

    public static final int MAX = 16;
    private static final int NO_SNAPSHOT = 4, OUT_FULL = 3;
    public static final int MODE_RECON = 0, MODE_DAMAGE = 1, MODE_LOG = 2;
    private int mode = MODE_RECON, outMode;
    private static final PortMap PORT_MAP = new PortMap(0, 0, 1).item(0, 0, 0, Direction.WEST, false);

    private ItemStack in = ItemStack.EMPTY, out = ItemStack.EMPTY;
    private final FluidTank gaugeIn = new FluidTank(MAX), gaugeOut = new FluidTank(MAX);
    private int progress;

    private long outSnapMs;

    private long printedAt = -1000L;

    public CcMapPrinterBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_MAP_PRINTER_BE.get(), pos, state, Balance.CC_PRINTER_FE); }

    @Override public CcModule kind() { return CcModule.MAP_PRINTER; }

    public int mode() { return mode; }

    void setMode(int m) {
        m = m == MODE_DAMAGE || m == MODE_LOG ? m : MODE_RECON;
        if (m == mode) return;
        mode = m;
        progress = 0;
        setChanged();
        sync();
    }

    private long[] printKey(net.minecraft.server.MinecraftServer server) {
        if (mode == MODE_DAMAGE) {
            CcBda.Report r = CcBda.latest(server, getOwner());
            return r != null && r.done() ? new long[] { r.id() } : null;
        }
        if (mode == MODE_LOG) {
            List<CcBattleLog.Event> ev = CcBattleLog.events(server, getOwner());
            return ev.isEmpty() ? null : new long[] { ev.get(ev.size() - 1).timeMs() };
        }
        ReconData.Snapshot s = CcMapPrint.snapshot(server, getOwner());
        return s == null ? null : new long[] { s.timeMs() };
    }

    private ItemStack printNew(net.minecraft.server.MinecraftServer server) {
        if (mode == MODE_DAMAGE) {
            CcBda.Report r = CcBda.latest(server, getOwner());
            return r != null && r.done() ? CcMapPrint.printBda(server, r) : null;
        }
        if (mode == MODE_LOG) return CcMapPrint.printLog(server, getOwnerName(), CcBattleLog.events(server, getOwner()));
        return CcMapPrint.print(server, CcMapPrint.snapshot(server, getOwner()));
    }

    private static boolean isEmptyMap(ItemStack s) { return s.is(Items.MAP); }

    private void gauges() {
        gaugeIn.setFluid(in.isEmpty() ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, in.getCount()));
        gaugeOut.setFluid(out.isEmpty() ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, out.getCount()));
    }

    @Override
    protected void moduleTick(Level level, long now) {
        if (level.getServer() == null) return;
        if (!isOnline() || in.isEmpty()) { if (progress != 0) { progress = 0; setChanged(); } reason = in.isEmpty() ? NO_SNAPSHOT : 0; return; }
        long[] key = printKey(level.getServer());
        if (key == null) { reason = NO_SNAPSHOT; progress = 0; return; }
        boolean copy = !out.isEmpty() && outSnapMs == key[0] && outMode == mode && out.getCount() < Math.min(MAX, out.getMaxStackSize());
        if (!out.isEmpty() && !copy) { reason = OUT_FULL; return; }
        reason = 0;
        if (++progress < Balance.CC_PRINTER_TICKS) { if (progress % 20 == 0) setChanged(); return; }
        progress = 0;
        if (copy) out.grow(1);
        else {
            ItemStack map = printNew(level.getServer());
            if (map == null) { reason = NO_SNAPSHOT; return; }
            out = map;
            outSnapMs = key[0];
            outMode = mode;
        }
        in.shrink(1);
        if (in.isEmpty()) in = ItemStack.EMPTY;
        gauges();
        printedAt = now;
        setChanged();
        sync();
    }

    @Override protected float progress01() { return progress / (float) Math.max(1, Balance.CC_PRINTER_TICKS); }

    @Override
    public int guiStatus() {
        if (!isOnline()) return 2;
        return reason != 0 ? reason : 1;
    }

    public boolean tryInsert(Player player) {
        ItemStack held = player.getMainHandItem();
        if (!isEmptyMap(held)) return false;
        int n = Math.min(MAX - in.getCount(), held.getCount());
        if (n <= 0) return false;
        if (in.isEmpty()) in = held.copyWithCount(n); else in.grow(n);
        if (!player.getAbilities().instabuild) held.shrink(n);
        gauges();
        setChanged();
        return true;
    }

    public List<ItemStack> takeAll() {
        List<ItemStack> l = new ArrayList<>();
        if (!in.isEmpty()) l.add(in);
        if (!out.isEmpty()) l.add(out);
        in = ItemStack.EMPTY;
        out = ItemStack.EMPTY;
        gauges();
        return l;
    }

    private final IItemHandler hatch = new IItemHandler() {
        @Override public int getSlots() { return 2; }
        @Override public ItemStack getStackInSlot(int i) { return i == 0 ? in : out; }
        @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) {
            if (i != 0 || !isEmptyMap(s)) return s;
            int n = Math.min(MAX - in.getCount(), s.getCount());
            if (n <= 0) return s;
            if (!sim) { if (in.isEmpty()) in = s.copyWithCount(n); else in.grow(n); gauges(); setChanged(); }
            return n == s.getCount() ? ItemStack.EMPTY : s.copyWithCount(s.getCount() - n);
        }
        @Override public ItemStack extractItem(int i, int n, boolean sim) {
            if (i != 1 || out.isEmpty() || n <= 0) return ItemStack.EMPTY;
            int t = Math.min(n, out.getCount());
            ItemStack r = out.copyWithCount(t);
            if (!sim) { out.shrink(t); if (out.isEmpty()) out = ItemStack.EMPTY; gauges(); setChanged(); }
            return r;
        }
        @Override public int getSlotLimit(int i) { return MAX; }
        @Override public boolean isItemValid(int i, ItemStack s) { return i == 0 && isEmptyMap(s); }
    };

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.WEST) ? hatch : null; }

    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override protected List<FluidTank> tanks() { return List.of(gaugeIn, gaugeOut); }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.WATER, Fluids.WATER); }
    @Override protected List<String> labelKeys() { return List.of("empty_map", "recon_map"); }
    @Override protected int inputTanks() { return 1; }
    @Override public String guiTankUnit(int tank) { return "items"; }
    @Override public List<ItemStack> guiSlotIcons() { return List.of(new ItemStack(Items.MAP), new ItemStack(Items.FILLED_MAP)); }
    @Override public boolean guiSlotOutput(int i) { return i == 1; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", kind().fe() * 20), Component.translatable("gui.wmd.info.cc_printer"));
    }

    @Override
    protected String detail() {
        return (isOnline() ? "online" : "offline: needs an active command center room") + ", " + in.getCount() + " empty maps"
                + (out.isEmpty() ? "" : ", " + out.getCount() + " printed");
    }

    @Override
    public Container slotContainer() {
        return new Container() {
            @Override public int getContainerSize() { return 2; }
            @Override public boolean isEmpty() { return in.isEmpty() && out.isEmpty(); }
            @Override public ItemStack getItem(int i) { return i == 0 ? in : out; }
            @Override public ItemStack removeItem(int i, int n) {
                ItemStack s = i == 0 ? in : out;
                if (s.isEmpty()) return ItemStack.EMPTY;
                ItemStack r = s.split(n);
                if (in.isEmpty()) in = ItemStack.EMPTY;
                if (out.isEmpty()) out = ItemStack.EMPTY;
                gauges();
                setChanged();
                return r;
            }
            @Override public ItemStack removeItemNoUpdate(int i) {
                ItemStack r = i == 0 ? in : out;
                if (i == 0) in = ItemStack.EMPTY; else out = ItemStack.EMPTY;
                gauges();
                return r;
            }
            @Override public void setItem(int i, ItemStack st) {
                if (i == 0) in = st; else out = st;
                gauges();
                setChanged();
            }
            @Override public int getMaxStackSize() { return MAX; }
            @Override public void setChanged() { CcMapPrinterBlockEntity.this.setChanged(); }
            @Override public boolean stillValid(Player p) { return true; }
            @Override public boolean canPlaceItem(int i, ItemStack st) { return i == 0 && isEmptyMap(st); }
            @Override public void clearContent() { in = ItemStack.EMPTY; out = ItemStack.EMPTY; gauges(); }
        };
    }

    @Override public int slotCount() { return 2; }
    @Override public int firstSlotTank() { return 0; }
    @Override public int slotMax(int i) { return MAX; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        double k = (tt - printedAt) / Math.max(1, CcSpecC.PAPER_TICKS);
        final float out01 = k < 0 || k > 1 ? 0.0F : (float) Math.sin(Math.PI * k);
        model.getBone("paper").ifPresent(b -> b.setPosZ(CcSpecC.ZS * CcSpecC.PAPER_TRAVEL_PX * (1.0F - out01)));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("in", in.saveOptional(registries));
        tag.put("out", out.saveOptional(registries));
        tag.putInt("progress", progress);
        tag.putLong("outSnap", outSnapMs);
        tag.putInt("mode", mode);
        tag.putInt("outMode", outMode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("in")) in = ItemStack.parseOptional(registries, tag.getCompound("in"));
        if (tag.contains("out")) out = ItemStack.parseOptional(registries, tag.getCompound("out"));
        progress = tag.getInt("progress");
        outSnapMs = tag.getLong("outSnap");
        mode = tag.getInt("mode");
        outMode = tag.getInt("outMode");
        if (tag.contains("printedAt")) printedAt = tag.getLong("printedAt");
        gauges();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putLong("printedAt", printedAt);
        tag.putInt("mode", mode);
        return tag;
    }
}
