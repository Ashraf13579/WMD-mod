package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class AntimatterStoreBlockEntity extends ItemProcessBlockEntity implements Owned, CcAnimated {

    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    private final Slot[] slots = new Slot[Balance.AM_STORE_CELLS];
    private final List<FluidTank> tanks;
    private int backup = Balance.AM_STORE_BACKUP_TICKS;

    public AntimatterStoreBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.ANTIMATTER_STORE_BE.get(), pos, state, Balance.AM_STORE_FE);
        FluidTank[] g = new FluidTank[slots.length];
        for (int i = 0; i < slots.length; i++) { slots[i] = new Slot(1, false, s -> s.is(Wmd.ANTIMATTER_CELL.get())); g[i] = slots[i].gauge; }
        tanks = List.of(g);
    }

    public int cells() { int n = 0; for (Slot s : slots) n += s.count(); return n; }

    @Override
    protected boolean process() {
        if (backup < Balance.AM_STORE_BACKUP_TICKS) { backup = Balance.AM_STORE_BACKUP_TICKS; setChanged(); sync(); }
        return cells() > 0 || fail(NO_INPUT);
    }

    @Override
    protected void idle() {
        super.idle();
        if (level == null || cells() == 0) return;
        backup--;
        if (backup % 100 == 0 || backup <= 0) alarm();
        if (backup % 20 == 0) sync();
        if (backup <= 0) containmentLost("power lost");
    }

    private void alarm() {
        if (!(level instanceof ServerLevel sl)) return;
        Component msg = Component.translatable("msg.wmd.antimatter_alarm", cells(), Math.max(0, backup / 20));
        for (net.minecraft.server.level.ServerPlayer p : sl.players())
            if (p.blockPosition().distSqr(worldPosition) < 48 * 48) p.displayClientMessage(msg, true);
        sl.playSound(null, worldPosition, WmdSounds.AM_ALARM.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_ALARM, 1.0F);
    }

    private void containmentLost(String why) {
        int n = cells();
        if (n == 0 || !(level instanceof ServerLevel sl)) return;
        for (Slot s : slots) { s.stack = ItemStack.EMPTY; s.sync(); }
        backup = Balance.AM_STORE_BACKUP_TICKS;
        setChanged();
        sync();
        annihilate(sl, worldPoint(0.0, 0.0, 2.0), n, "store at " + worldPosition.toShortString() + " (" + why + ")");
    }

    static void annihilate(ServerLevel sl, double[] w, int n, String what) {
        int tier = n <= 2 ? 2 : n <= 5 ? 3 : 4;
        int gx = (int) Math.floor(w[0]), gy = (int) Math.floor(w[1]), gz = (int) Math.floor(w[2]);
        Wmd.LOGGER.info("[antimatter] {}: {} cell(s) ANNIHILATE, tier {} at {} {} {}", what, n, tier, gx, gy, gz);
        sl.getServer().execute(() -> {
            FlightManager mgr = FlightManager.get(sl);
            mgr.holdForTest(sl, gx, gz, WarheadFx.holdRadius(tier, WarheadType.ANTIMATTER));
            Forcefield.inside(() -> WarheadFx.detonate(sl, mgr, gx, gy, gz, tier, WarheadType.ANTIMATTER, 0.0, 1.0));
        });
    }

    @Override
    public void empDrain() {
        super.empDrain();
        if (cells() > 0) containmentLost("EMP");
    }

    static final java.util.Set<AntimatterStoreBlockEntity> LOADED = Statics.server(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) LOADED.remove(this);
    }

    public static void blastAt(ServerLevel sl, net.minecraft.world.phys.Vec3 pos, double r) {
        for (AntimatterStoreBlockEntity be : new java.util.ArrayList<>(LOADED)) {
            if (be.level != sl || be.isRemoved() || be.cells() == 0) continue;
            double[] c = be.worldPoint(0.0, 1.5, 2.0);
            if (Math.sqrt((c[0] - pos.x) * (c[0] - pos.x) + (c[1] - pos.y) * (c[1] - pos.y) + (c[2] - pos.z) * (c[2] - pos.z)) > r + 3.0) continue;
            be.containmentLost("caught in a blast r " + Math.round(r));
        }
        AntimatterColliderBlockEntity.blastAt(sl, pos, r);
    }

    private long minedTick = Long.MIN_VALUE;
    void markMined(Level lv) { minedTick = lv.getGameTime(); }

    void removed(Level lv, BlockPos pos) {
        if (lv.getGameTime() - minedTick <= 1) {
            for (Slot s : slots) if (!s.stack.isEmpty()) Containers.dropItemStack(lv, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s.stack.copy());
            for (Slot s : slots) { s.stack = ItemStack.EMPTY; s.sync(); }
        } else if (lv instanceof ServerLevel sl && cells() > 0) {
            sl.getServer().execute(() -> containmentLost("destroyed"));
        }
    }

    private int shownMask;
    private int shownBackup = Balance.AM_STORE_BACKUP_TICKS;

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        boolean countdown = shownBackup < Balance.AM_STORE_BACKUP_TICKS;
        boolean blinkOff = countdown && ((long) tt) % (shownBackup < 400 ? 6 : 14) < 3;
        for (int k = 0; k < Balance.AM_STORE_CELLS; k++) {
            final boolean on = !dark && (shownMask & (1 << k)) != 0 && !blinkOff;
            model.getBone("cell" + k).ifPresent(b -> b.setHidden(!on));
        }
        final boolean desk = !dark && (shownMask != 0 || running);
        model.getBone("desk").ifPresent(b -> b.setHidden(!desk));
    }

    private int lastSent = -1;

    private void sync() {
        if (level == null || level.isClientSide) return;
        int mask = 0;
        for (int i = 0; i < slots.length; i++) if (slots[i].count() > 0) mask |= 1 << i;
        int h = mask * 4096 + backup / 20;
        if (h == lastSent) return;
        lastSent = h;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        sync();
    }

    @Override protected Slot[] slots() { return slots; }
    @Override protected String[] iconIds() { String[] a = new String[slots.length]; java.util.Arrays.fill(a, "antimatter_cell"); return a; }
    @Override protected float progress01() { return backup < Balance.AM_STORE_BACKUP_TICKS ? backup / (float) Balance.AM_STORE_BACKUP_TICKS : -1.0F; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { Fluid[] f = new Fluid[slots.length]; java.util.Arrays.fill(f, Fluids.EMPTY); return List.of(f); }
    @Override protected List<String> labelKeys() { String[] a = new String[slots.length]; java.util.Arrays.fill(a, "antimatter_cell"); return List.of(a); }
    @Override protected int inputTanks() { return slots.length; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", String.format("%,d", Balance.AM_STORE_FE * 20)),
                Component.translatable("gui.wmd.info.antimatter_store", Balance.AM_STORE_CELLS, Balance.AM_STORE_BACKUP_TICKS / 20),
                Component.translatable("gui.wmd.info.antimatter_backup", Math.max(0, (level != null && level.isClientSide ? shownBackup : backup) / 20)));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.AM_STORE_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 2); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }

    @Override
    protected String detail() { return cells() + " / " + Balance.AM_STORE_CELLS + " cells, backup " + backup / 20 + " s"; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 1, 4, Direction.SOUTH) ? energy() : null; }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 5).power(0, 1, 4, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("am_backup", backup);
        ownerData.write(tag);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        if (tag.contains("am_backup")) {
            backup = Math.max(0, Math.min(Balance.AM_STORE_BACKUP_TICKS, tag.getInt("am_backup")));
            ownerData.read(tag);
        }
        if (tag.contains("am_mask")) { shownMask = tag.getInt("am_mask"); shownBackup = tag.getInt("am_shown_backup"); }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        int mask = 0;
        for (int i = 0; i < slots.length; i++) if (slots[i].count() > 0) mask |= 1 << i;
        tag.putInt("am_mask", mask);
        tag.putInt("am_shown_backup", backup);
        return tag;
    }
}
