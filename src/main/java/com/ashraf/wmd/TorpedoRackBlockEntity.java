package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class TorpedoRackBlockEntity extends ItemProcessBlockEntity {

    public static final int X0 = 0, X1 = 1, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 3;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 3, Direction.SOUTH, false);
    static final int FEED_TICKS = 60;

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    private final Slot[] slots = new Slot[4];
    private final List<FluidTank> gauges = new ArrayList<>();
    private int feedLeft;
    private long lastFeed = -1000L;

    private int[] shownKinds = { -1, -1, -1, -1 };

    public TorpedoRackBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.TORPEDO_RACK_BE.get(), pos, state, Balance.TORP_RACK_FE);
        for (int i = 0; i < slots.length; i++) { slots[i] = new Slot(1, false, s -> s.getItem() instanceof TorpedoItem); gauges.add(slots[i].gauge); }
    }

    public boolean hasTorpedo() { for (Slot s : slots) if (!s.stack.isEmpty()) return true; return false; }

    void feeding() {
        feedLeft = FEED_TICKS; lastFeed = level != null ? level.getGameTime() : 0L; setChanged(); sync();
        if (level != null) level.playSound(null, worldPosition, WmdSounds.TP_RACK_HOIST.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
    }

    ItemStack take() {
        for (int i = slots.length - 1; i >= 0; i--) {
            if (slots[i].stack.isEmpty()) continue;
            ItemStack t = slots[i].stack.copyWithCount(1);
            slots[i].take(1);
            setChanged();
            return t;
        }
        return ItemStack.EMPTY;
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected boolean process() {
        if (feedLeft > 0) { feedLeft--; return true; }
        return false;
    }

    public int[] kinds() { return shownKinds; }
    public long lastFeed() { return lastFeed; }

    public boolean hasTorpedoShown() { for (int k : shownKinds) if (k >= 0) return true; return false; }

    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return gauges; }
    @Override protected List<Fluid> tankFluids() { List<Fluid> l = new ArrayList<>(); for (int i = 0; i < slots.length; i++) l.add(Fluids.EMPTY); return l; }
    @Override protected int inputTanks() { return slots.length; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override protected float progress01() { return -1.0F; }
    @Override protected List<String> labelKeys() { return List.of("torpedo", "torpedo", "torpedo", "torpedo"); }
    @Override protected String[] iconIds() { return new String[] { "torpedo_t1", "torpedo_t1", "torpedo_t1", "torpedo_t1" }; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected SoundEvent runSound() { return WmdSounds.TP_RACK_HOIST.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 3, Direction.SOUTH) ? energy() : null; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", guiFeUse() * 20), Component.translatable("gui.wmd.info.torpedo_rack"));
    }

    @Override
    protected String detail() {
        int n = 0;
        for (Slot s : slots) if (!s.stack.isEmpty()) n++;
        return n + " of 4 torpedoes" + (feedLeft > 0 ? ", handing one over" : "");
    }

    @Override
    protected void saveExtra(CompoundTag tag) { tag.putLong("trFeed", lastFeed); }

    @Override
    protected void loadExtra(CompoundTag tag) {
        lastFeed = tag.contains("trFeed") ? tag.getLong("trFeed") : -1000L;
        if (tag.contains("trKinds")) {
            int[] k = tag.getIntArray("trKinds");
            for (int i = 0; i < 4; i++) shownKinds[i] = i < k.length ? k[i] : -1;
        }
    }

    private int[] kindsNow() {
        int[] k = new int[4];
        for (int i = 0; i < 4; i++) { TorpedoKind t = TorpedoKind.of(slots[i].stack); k[i] = t == null ? -1 : t.ordinal(); }
        return k;
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveExtra(tag);
        tag.putIntArray("trKinds", kindsNow());
        return tag;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            int[] k = kindsNow();
            if (!java.util.Arrays.equals(k, shownKinds)) { shownKinds = k; sync(); }
        }
    }
}
