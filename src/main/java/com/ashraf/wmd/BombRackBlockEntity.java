package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class BombRackBlockEntity extends ProcessUnitBlockEntity implements Owned, GuiItemSlots, GuiAction, CcAnimated {

    public static final int A_DROP = 0, A_SALVO = 1;

    public static final float RACK_X = 0.0F, BEAM_Y = 6.0F, RACK_TAIL_Z = 38.0F;

    public static final float[] BAY_ROW_X = { 0.0F, 16.0F, 32.0F }, BAY_TAIL_Z = { 38.0F, 86.0F, 134.0F };

    public static final float[] HEAVY_LANE_X = { 4.0F, 0.0F, 28.0F };
    public static final float BAY_MID_Z = 64.0F;

    private static final PortMap RACK_PORTS = new PortMap(0, 0, 4).power(0, 0, 3, Direction.EAST, false);
    private static final PortMap BAY_PORTS = new PortMap(0, 2, 9).power(2, 0, 8, Direction.SOUTH, false);

    public final boolean heavy;
    private final OwnerData ownerData = new OwnerData();
    private final ItemStack[] bombs;
    private final FluidTank[] gauges;

    private final int[] clientCount, clientKind;
    private boolean targetSet;
    private int targetX, targetZ;

    private int queued, highTicks;
    private boolean wasHigh, salvoFired;
    private long nextDropAt = -1L;

    private int pinSlot = -1;

    private long doorsOpenAt = -1L, doorsCloseAt = -1L;

    private Vec3 lastWorld, worldVel = Vec3.ZERO;

    public BombRackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean heavy) {
        super(type, pos, state, heavy ? Balance.BAY_IDLE : Balance.RACK_IDLE);
        this.heavy = heavy;
        int n = slots();
        bombs = new ItemStack[n];
        gauges = new FluidTank[n];
        clientCount = new int[n];
        clientKind = new int[n];
        for (int i = 0; i < n; i++) { bombs[i] = ItemStack.EMPTY; gauges[i] = new FluidTank(perSlot()); }
    }

    public int slots() { return heavy ? Balance.BAY_ROWS : 1; }

    public int perSlot() { return heavy ? Balance.BAY_LOAD : Balance.RACK_LOAD; }

    private int dropFe() { return heavy ? Balance.BAY_FE : Balance.RACK_FE; }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        sync();
    }

    private void sync() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public int count(int i) { return level != null && level.isClientSide ? clientCount[i] : bombs[i].getCount(); }

    public BombType kind(int i) {
        if (level != null && level.isClientSide) return clientCount[i] > 0 ? BombType.ofOrdinal(clientKind[i]) : null;
        return BombType.of(bombs[i]);
    }

    public int total() { int n = 0; for (int i = 0; i < slots(); i++) n += count(i); return n; }

    public boolean targetSet() { return targetSet; }
    public int targetX() { return targetX; }
    public int targetZ() { return targetZ; }

    void setTarget(boolean set, int x, int z) {
        targetSet = set; targetX = x; targetZ = z;
        setChanged();
        sync();
    }

    public float doorOpen(double now) {
        if (!heavy || doorsOpenAt < 0L) return 0.0F;
        double open = Math.min(1.0, (now - doorsOpenAt) / Balance.BAY_DOOR_TICKS);
        if (doorsCloseAt >= 0L && now >= doorsCloseAt) open = Math.max(0.0, 1.0 - (now - doorsCloseAt) / Balance.BAY_DOOR_TICKS);
        return (float) Math.max(0.0, open);
    }

    String dropBlock() {
        if (Machines.mount(getBlockState()) == Direction.UP) return "message.wmd.rack.upright";
        if (total() == 0) return "message.wmd.rack.empty";
        if (energy().getEnergyStored() < dropFe() + feCost()) return "message.wmd.rack.no_power";
        if (level instanceof ServerLevel sl && EmpField.jammed(sl, worldPosition)) return "message.wmd.rack.emp";
        return null;
    }

    private void request(ServerLevel sl, boolean all) {
        pinSlot = -1;
        request(sl, all ? total() : 1);
    }

    String stationDrop(ServerLevel sl, int slot, boolean salvo) {
        String why = dropBlock();
        if (why != null) return why;
        if (slot < 0 || slot >= slots() || bombs[slot].isEmpty()) return "message.wmd.rack.empty";
        if (queued > 0 && pinSlot != slot) return "message.wmd.sight.busy";
        pinSlot = slot;
        request(sl, salvo ? bombs[slot].getCount() : 1);
        Wmd.LOGGER.info("Bomb rack: station {} {} at {}", slot, salvo ? "salvo" : "drop", worldPosition.toShortString());
        return null;
    }

    private void request(ServerLevel sl, int want) {
        if (dropBlock() != null) return;
        if (queued >= want) return;
        queued = want;
        long now = sl.getGameTime();
        if (heavy) {
            boolean open = doorsOpenAt >= 0L && (doorsCloseAt < 0L || now < doorsCloseAt);
            if (!open) {
                doorsOpenAt = now;
                doorsCloseAt = -1L;
                Vec3 c = centre();
                sl.playSound(null, c.x, c.y, c.z, WmdSounds.CC_DOOR_OPEN.get(), SoundSource.BLOCKS, Balance.SND_MECH, 0.8F);
                sl.playSound(null, c.x, c.y, c.z, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.5F, 0.55F);
                nextDropAt = now + Balance.BAY_DOOR_TICKS;
                sync();
            } else {
                doorsCloseAt = -1L;
                if (nextDropAt < now) nextDropAt = now;
                sync();
            }
        } else if (nextDropAt < now) nextDropAt = now;
    }

    @Override
    public boolean guiAction(Player player, int id) {
        if ((id != A_DROP && id != A_SALVO) || !(level instanceof ServerLevel sl)) return false;
        if (!Access.mayUse(this, player)) { Access.deny(this, player); return false; }
        String why = dropBlock();
        if (why != null) { player.displayClientMessage(Component.translatable(why), true); return false; }
        request(sl, id == A_SALVO);
        Wmd.LOGGER.info("Bomb rack: {} ordered a {} at {}", player.getName().getString(), id == A_SALVO ? "salvo" : "drop", worldPosition.toShortString());
        return true;
    }

    public static void serverTickRack(Level level, BlockPos pos, BlockState state, BombRackBlockEntity be) {
        if (level instanceof ServerLevel sl) be.rackTick(sl);
        ProcessUnitBlockEntity.serverTick(level, pos, state, be);
    }

    private void rackTick(ServerLevel sl) {
        long now = sl.getGameTime();

        Vec3 w = centre();
        worldVel = lastWorld == null ? Vec3.ZERO : w.subtract(lastWorld);
        if (worldVel.lengthSqr() > 4.0) worldVel = Vec3.ZERO;
        lastWorld = w;

        boolean high = redstoneIn(sl);
        if (high && !wasHigh) { highTicks = 0; salvoFired = false; request(sl, false); }
        if (high) {
            highTicks++;
            if (highTicks >= Balance.SALVO_PULSE && !salvoFired) { salvoFired = true; request(sl, true); }
        }
        wasHigh = high;

        if (queued > 0 && nextDropAt >= 0L && now >= nextDropAt) {
            if (dropOne(sl)) {
                queued--;
                nextDropAt = now + Balance.SALVO_GAP;
            } else queued = 0;
            if (queued == 0) pinSlot = -1;
            if (queued == 0 && heavy) { doorsCloseAt = now + Balance.BAY_DOOR_HOLD; sync(); }
        }
        if (heavy && doorsCloseAt >= 0L && now == doorsCloseAt) {
            Vec3 c = centre();
            sl.playSound(null, c.x, c.y, c.z, WmdSounds.CC_DOOR_CLOSE.get(), SoundSource.BLOCKS, Balance.SND_MECH, 0.8F);
        }
        if (heavy && doorsCloseAt >= 0L && now > doorsCloseAt + Balance.BAY_DOOR_TICKS) { doorsOpenAt = -1L; doorsCloseAt = -1L; sync(); }
        if (now % 10 == 0) syncCounts();
    }

    private boolean redstoneIn(ServerLevel sl) {
        Direction f = facing(), m = Machines.mount(getBlockState());
        BlockPos cell = Machines.toWorld(worldPosition, f, m, heavy ? 1 : 0, 0, heavy ? 8 : 3);
        Direction out = Machines.dirToWorld(f, m, Direction.SOUTH);
        return sl.getSignal(cell.relative(out), out) > 0;
    }

    private int nextSlot() {
        if (pinSlot >= 0 && pinSlot < slots() && !bombs[pinSlot].isEmpty()) return pinSlot;
        int slot = 0;
        for (int i = 1; i < slots(); i++) if (bombs[i].getCount() > bombs[slot].getCount()) slot = i;
        return slot;
    }

    public Vec3 hangWorld() { return hangWorld(nextSlot()); }

    public Vec3 hangWorld(int slot) {
        if (slot < 0 || slot >= slots()) return null;
        BombType t = BombType.of(bombs[slot]);
        if (t == null) return null;
        int left = bombs[slot].getCount() - 1;
        float hx = heavy ? (t.heavy ? HEAVY_LANE_X[slot] : BAY_ROW_X[slot]) : RACK_X;
        float tail = heavy ? (t.heavy ? BAY_MID_Z + t.length / 2.0F : BAY_TAIL_Z[Math.max(0, Math.min(2, left))]) : RACK_TAIL_Z;
        double[] p = worldPoint(hx / 16.0, (BEAM_Y + t.radius) / 16.0, (tail - t.length / 2.0) / 16.0);
        return new Vec3(p[0], p[1], p[2]);
    }

    private boolean dropOne(ServerLevel sl) {
        if (dropBlock() != null) return false;
        int slot = nextSlot();
        if (pinSlot >= 0 && slot != pinSlot) return false;
        BombType t = BombType.of(bombs[slot]);
        if (t == null) return false;
        net.minecraft.network.chat.Component refused = Config.Server.refuseBomb(t);
        if (refused != null) {
            if (getOwner() != null && sl.getServer().getPlayerList().getPlayer(getOwner()) instanceof net.minecraft.server.level.ServerPlayer op) op.displayClientMessage(refused, true);
            return false;
        }
        if (!useExtraEnergy(dropFe())) return false;
        int left = bombs[slot].getCount() - 1;
        float r = t.radius;
        float hx = heavy ? (t.heavy ? HEAVY_LANE_X[slot] : BAY_ROW_X[slot]) : RACK_X;
        float tail = heavy ? (t.heavy ? BAY_MID_Z + t.length / 2.0F : BAY_TAIL_Z[Math.max(0, Math.min(2, left))]) : RACK_TAIL_Z;
        double[] p = worldPoint(hx / 16.0, (BEAM_Y + r) / 16.0, (tail - t.length / 2.0) / 16.0);
        Direction m = Machines.mount(getBlockState());
        Vec3 push = new Vec3(m.getStepX(), m.getStepY(), m.getStepZ()).scale(0.15);
        Set<BlockPos> cells = new HashSet<>();
        if (getBlockState().getBlock() instanceof MachineBlock mb)
            for (BlockPos l : mb.footprint()) cells.add(Machines.toWorld(worldPosition, facing(), m, l.getX(), l.getY(), l.getZ()));
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        BombEntity.release(sl, t, new Vec3(p[0], p[1], p[2]), worldVel.add(push), getOwner(), targetSet ? new int[] { targetX, targetZ } : null, cells, ship);
        bombs[slot].shrink(1);
        if (bombs[slot].isEmpty()) bombs[slot] = ItemStack.EMPTY;
        syncCounts();
        setChanged();
        return true;
    }

    private Vec3 centre() {
        double[] p = worldPoint(heavy ? 1.0 : 0.0, heavy ? 1.0 : 0.5, heavy ? 4.0 : 1.5);
        return new Vec3(p[0], p[1], p[2]);
    }

    private boolean fits(int i, ItemStack s) {
        BombType t = BombType.of(s);
        if (t == null) return false;
        if (!heavy) return !t.heavy;
        if (t.heavy && i == 1) return false;
        for (ItemStack b : bombs) if (!b.isEmpty() && BombType.of(b).heavy != t.heavy) return false;
        return true;
    }

    @Override public int slotCount() { return slots(); }
    @Override public int firstSlotTank() { return 0; }
    @Override public int slotMax(int i) { return perSlot(); }

    @Override
    public net.minecraft.world.Container slotContainer() {
        return new net.minecraft.world.Container() {
            @Override public int getContainerSize() { return slots(); }
            @Override public boolean isEmpty() { for (ItemStack s : bombs) if (!s.isEmpty()) return false; return true; }
            @Override public ItemStack getItem(int i) { return bombs[i]; }
            @Override public ItemStack removeItem(int i, int n) { if (bombs[i].isEmpty()) return ItemStack.EMPTY; ItemStack r = bombs[i].split(n); syncCounts(); BombRackBlockEntity.this.setChanged(); return r; }
            @Override public ItemStack removeItemNoUpdate(int i) { ItemStack r = bombs[i]; bombs[i] = ItemStack.EMPTY; syncCounts(); return r; }
            @Override public void setItem(int i, ItemStack st) { bombs[i] = st; syncCounts(); BombRackBlockEntity.this.setChanged(); }
            @Override public int getMaxStackSize() { return perSlot(); }
            @Override public void setChanged() { BombRackBlockEntity.this.setChanged(); }
            @Override public boolean stillValid(Player p) { return true; }
            @Override public boolean canPlaceItem(int i, ItemStack st) { return fits(i, st); }
            @Override public void clearContent() { for (int i = 0; i < bombs.length; i++) bombs[i] = ItemStack.EMPTY; syncCounts(); }
        };
    }

    public List<ItemStack> takeBombs() {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < bombs.length; i++) { if (!bombs[i].isEmpty()) l.add(bombs[i]); bombs[i] = ItemStack.EMPTY; }
        syncCounts();
        return l;
    }

    private void syncCounts() {
        boolean changed = false;
        for (int i = 0; i < slots(); i++) {
            int c = bombs[i].getCount(), k = bombs[i].isEmpty() ? 0 : BombType.of(bombs[i]).ordinal();
            if (clientCount[i] != c || clientKind[i] != k) { clientCount[i] = c; clientKind[i] = k; changed = true; }
            gauges[i].setFluid(c == 0 ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, c));
        }
        if (changed && level != null && !level.isClientSide) sync();
    }

    @Override protected boolean work() { reason = total() == 0 ? 4 : 0; return false; }
    @Override protected List<FluidTank> tanks() { return List.of(gauges); }
    @Override protected List<Fluid> tankFluids() { List<Fluid> l = new ArrayList<>(); for (int i = 0; i < slots(); i++) l.add(net.minecraft.world.level.material.Fluids.EMPTY); return l; }
    @Override protected List<String> labelKeys() { return heavy ? List.of("bay_row_1", "bay_row_2", "bay_row_3") : List.of("bombs"); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override public List<ItemStack> guiSlotIcons() { List<ItemStack> l = new ArrayList<>(); for (int i = 0; i < slots(); i++) l.add(new ItemStack(Wmd.BOMB_IRON.get())); return l; }
    @Override public PortMap guiPortMap() { return heavy ? BAY_PORTS : RACK_PORTS; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.rack.info_drop", String.format("%,d", dropFe())),
                Component.translatable("gui.wmd.rack.info_redstone", Balance.SALVO_PULSE));
    }

    @Override
    protected String detail() { return total() == 0 ? "empty" : queued > 0 ? "dropping " + queued : total() + " bombs"; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return heavy ? (at(part, side, 2, 0, 8, Direction.SOUTH) ? energy() : null) : (at(part, side, 0, 0, 3, Direction.EAST) ? energy() : null);
    }

    @Override protected SoundEvent runSound() { return SoundEvents.IRON_DOOR_OPEN; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, heavy ? 4 : 1); }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (heavy) {
            float a = (float) (Math.PI / 2.0 * doorOpen(tt + pt));
            model.getBone("door_l").ifPresent(b -> b.setRotZ(-a));
            model.getBone("door_r").ifPresent(b -> b.setRotZ(a));
        } else {
            int c = count(0);
            for (int k = 0; k < 4; k++) { final boolean on = k < c; model.getBone("pip" + k).ifPresent(b -> b.setHidden(!on)); }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        for (int i = 0; i < bombs.length; i++) if (!bombs[i].isEmpty()) tag.put("bombs" + i, bombs[i].save(registries));
        tag.putBoolean("tset", targetSet); tag.putInt("tx", targetX); tag.putInt("tz", targetZ);
        tag.putLong("dOpen", doorsOpenAt); tag.putLong("dClose", doorsCloseAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner") || tag.contains("ownerName")) ownerData.read(tag);
        if (tag.contains("energy")) {
            for (int i = 0; i < bombs.length; i++) bombs[i] = tag.contains("bombs" + i) ? ItemStack.parseOptional(registries, tag.getCompound("bombs" + i)) : ItemStack.EMPTY;
            syncCounts();
        }
        if (tag.contains("tset")) { targetSet = tag.getBoolean("tset"); targetX = tag.getInt("tx"); targetZ = tag.getInt("tz"); }
        if (tag.contains("dOpen")) { doorsOpenAt = tag.getLong("dOpen"); doorsCloseAt = tag.getLong("dClose"); }
        for (int i = 0; i < clientCount.length; i++) if (tag.contains("cnt" + i)) { clientCount[i] = tag.getInt("cnt" + i); clientKind[i] = tag.getInt("knd" + i); }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        for (int i = 0; i < bombs.length; i++) { tag.putInt("cnt" + i, bombs[i].getCount()); tag.putInt("knd" + i, bombs[i].isEmpty() ? 0 : BombType.of(bombs[i]).ordinal()); }
        tag.putBoolean("tset", targetSet); tag.putInt("tx", targetX); tag.putInt("tz", targetZ);
        tag.putLong("dOpen", doorsOpenAt); tag.putLong("dClose", doorsCloseAt);
        return tag;
    }
}
