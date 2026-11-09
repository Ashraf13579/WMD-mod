package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class DepthChargeThrowerBlockEntity extends ItemProcessBlockEntity implements Owned {

    public static final int X0 = 0, X1 = 0, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 1;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 1, Direction.SOUTH, false);
    public static final int A_FIRE = 0, A_DEPTH = 1, A_FREQ = 2;

    static final double DRUM_X = -3.1, DRUM_Y = 21.5, DRUM_Z = 4.0, AXIS_X = -0.5, AXIS_Y = 0.8660254;

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    private final Slot arbor = new Slot(1, false, DepthChargeThrowerBlockEntity::throwable), spare = new Slot(1, false, DepthChargeThrowerBlockEntity::throwable);
    private final Slot[] slots = { arbor, spare };

    private final ItemStack[] torp = { ItemStack.EMPTY, ItemStack.EMPTY };

    static final double MINE_X = -0.45, MINE_Y = 16.86, MINE_Z = 4.0;
    static final float MINE_SCALE = 1.0F / 3.0F;
    private final List<FluidTank> gauges = List.of(arbor.gauge, spare.gauge);
    private int depth = (int) Balance.DC_DEPTH_DEFAULT, frequency, fireLeft, reloadLeft;
    private boolean queued, lastSignal;
    private long lastFire = -1000L, reloadStart = -1000L;
    private UUID firer;
    private final OwnerData ownerData = new OwnerData();

    private boolean shownArbor, shownSpare, shownArborTorp;

    private int shownArborMine = -1, shownSpareMine = -1;

    public DepthChargeThrowerBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.DEPTH_CHARGE_THROWER_BE.get(), pos, state, Balance.KG_FE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) { ownerData.claim(player); setChanged(); sync(); }

    public int depth() { return depth; }
    public int frequency() { return frequency; }
    public long lastFire() { return lastFire; }
    public long reloadStart() { return reloadStart; }

    public boolean arborShown() { return shownArbor; }
    public boolean spareShown() { return shownSpare; }
    public boolean loaded() { return !arbor.stack.isEmpty(); }

    public int arborMine() { return shownArborMine; }
    public int spareMine() { return shownSpareMine; }
    public boolean arborMineLoaded() { return shownArborTorp; }

    boolean mineOnArbor() { return LaidMineEntity.kindOf(arbor.stack) >= 0; }

    static boolean throwable(ItemStack s) { return s.is(Wmd.DEPTH_CHARGE.get()) || LaidMineEntity.kindOf(s) >= 0; }

    public int mineDepth() { return Math.max(Balance.MINE_LAY_DEPTHS[0], Math.min(Balance.MINE_LAY_DEPTHS[Balance.MINE_LAY_DEPTHS.length - 1], depth)); }
    public boolean reloading() { return reloadLeft > 0; }

    public void act(Player by, int action, int value) {
        switch (action) {
            case A_FIRE -> fire(by == null ? null : by.getUUID());
            case A_DEPTH -> depth = (int) Math.max(Balance.DC_DEPTH_MIN, Math.min(Balance.DC_DEPTH_MAX, value));
            case A_FREQ -> frequency = Math.max(0, Math.min(9999, value));
            default -> {}
        }
        setChanged();
        sync();
    }

    boolean fire(UUID by) {
        if (!loaded() || queued || fireLeft > 0) return false;
        queued = true;
        firer = by;
        return true;
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private Vec3 model(double lx, double ly, double lz) {
        Direction f = facing(), m = Machines.mount(getBlockState());
        Direction e = Machines.dirToWorld(f, Direction.EAST), s = Machines.dirToWorld(f, Direction.SOUTH);
        double x = (e.getStepX() * lx + s.getStepX() * lz) / 16.0, z = (e.getStepZ() * lx + s.getStepZ() * lz) / 16.0, y = ly / 16.0 - 0.5;
        double[] t = Machines.mountTurn(m, x, y, z);
        return SableBridge.worldPos(level, worldPosition.getX() + 0.5 + t[0], worldPosition.getY() + 0.5 + t[1], worldPosition.getZ() + 0.5 + t[2]);
    }

    private void throwDrum() {
        if (!(level instanceof ServerLevel sl) || arbor.stack.isEmpty()) return;
        Vec3 at = model(DRUM_X, DRUM_Y, DRUM_Z), ahead = model(DRUM_X + AXIS_X * 16.0, DRUM_Y + AXIS_Y * 16.0, DRUM_Z);
        Vec3 dir = ahead.subtract(at).normalize();
        if (mineOnArbor()) {
            ItemStack one = arbor.stack.copyWithCount(1);
            final int d = mineDepth();
            if (LaidMineEntity.kindOf(one) != SeaMineBlockEntity.K_BOTTOM)
                net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, one, t -> t.putInt(SeaMineItem.DEPTH_TAG, d));
            Vec3 from = model(MINE_X, MINE_Y, MINE_Z).add(dir.scale(0.4));
            if (LaidMineEntity.launch(sl, from, dir.scale(Balance.KG_SPEED), one, torp[0], getOwner(), MINE_SCALE, worldPosition) == null) {
                queued = false;
                firer = null;
                return;
            }
            Wmd.LOGGER.info("K-gun at {} threw a sea mine ({}, depth {}, torpedo {})", worldPosition.toShortString(), one.getItem(), d, !torp[0].isEmpty());
            torp[0] = ItemStack.EMPTY;
        } else {
            UUID ship = SableBridge.active() ? SableBridge.shipIdAt(sl, worldPosition) : null;
            DepthChargeEntity.drop(sl, at.add(dir.scale(0.4)), dir.scale(Balance.KG_SPEED), depth, firer != null ? firer : getOwner(), ship, worldPosition);
        }
        arbor.take(1);
        tossArbor(sl, dir);
        Vec3 smoke = at.subtract(dir.scale(0.3));
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, smoke.x, smoke.y, smoke.z, 14, 0.2, 0.2, 0.2, 0.04);
        sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, smoke.x, smoke.y, smoke.z, 6, 0.15, 0.1, 0.15, 0.02);
        sl.sendParticles(ParticleTypes.POOF, smoke.x, smoke.y, smoke.z, 10, 0.1, 0.1, 0.1, 0.08);
        sl.playSound(null, at.x, at.y, at.z, WmdSounds.DC_THROW.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 0.85F + sl.random.nextFloat() * 0.1F);
        lastFire = sl.getGameTime();
        queued = false;
        firer = null;
        setChanged();
        sync();
    }

    static final double ARB_X = -0.3, ARB_Y = 15.55, ARB_Z = 4.0;

    static final int ARBOR_SEAT_TICKS = Math.max(1, Math.round(Balance.KG_RELOAD_TICKS * KGunAnim.ARBOR_SEAT));

    private void tossArbor(ServerLevel sl, Vec3 dir) {
        Vec3 at = model(ARB_X, ARB_Y, ARB_Z), west = model(ARB_X - 16.0, ARB_Y, ARB_Z).subtract(at);
        double hx = west.x, hz = west.z, hl = Math.sqrt(hx * hx + hz * hz);
        if (hl < 1e-6) { hx = dir.x; hz = dir.z; hl = Math.max(1e-6, Math.sqrt(hx * hx + hz * hz)); }
        float heading = (float) Math.toDegrees(Math.atan2(hz / hl, -hx / hl));
        float tilt = (float) (Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dir.y)))) - 30.0);
        KGunArborEntity.toss(sl, worldPosition, at.add(dir.scale(0.4)), dir.scale(Balance.KG_SPEED), heading, tilt);
    }

    private void startReload() {
        reloadLeft = Balance.KG_RELOAD_TICKS;
        reloadStart = level == null ? 0L : level.getGameTime();
        if (level != null) level.playSound(null, worldPosition, WmdSounds.TP_RACK_HOIST.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.2F);
        sync();
    }

    private void finishReload() {
        if (spare.stack.isEmpty() || !arbor.stack.isEmpty()) return;
        arbor.stack = spare.stack;
        spare.stack = ItemStack.EMPTY;
        torp[0] = torp[1];
        torp[1] = ItemStack.EMPTY;
        arbor.sync();
        spare.sync();
        if (level != null) level.playSound(null, worldPosition, WmdSounds.TP_RELOAD.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        setChanged();
        sync();
    }

    @Override
    protected boolean process() {
        boolean busy = false;
        if (fireLeft > 0) {
            busy = true;
            if (--fireLeft == 0) throwDrum();
        } else if (queued && loaded() && reloadLeft == 0) {
            fireLeft = Balance.KG_FIRE_TICKS;
            busy = true;
        } else if (queued && !loaded() && reloadLeft == 0) queued = false;
        if (reloadLeft > 0) {
            busy = true;
            if (--reloadLeft == Balance.KG_RELOAD_TICKS - ARBOR_SEAT_TICKS && level != null)
                level.playSound(null, worldPosition, WmdSounds.CATAPULT_LATCH.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.3F);
            if (reloadLeft == 0) finishReload();
        } else if (fireLeft == 0 && !loaded() && !spare.stack.isEmpty() && level != null && level.getGameTime() - lastFire > 10) {
            startReload();
            busy = true;
        }
        if (level != null && level.getGameTime() % 20 == Math.floorMod(worldPosition.asLong(), 20L) && spare.stack.isEmpty()) feedFromRack();
        releaseOrphans(null);
        pulse();
        beat();
        return busy;
    }

    @Override
    protected void idle() {
        super.idle();
        releaseOrphans(null);
        lastSignal = signal();
    }

    private boolean armFrom(Player p, int i) {
        if (p == null || !torp[i].isEmpty()) return false;
        net.minecraft.world.item.Item want = LaidMineEntity.torpedoFor(LaidMineEntity.kindOf(slots[i].stack));
        if (want == null) return false;
        ItemStack src = p.getOffhandItem().is(want) ? p.getOffhandItem() : ItemStack.EMPTY;
        if (src.isEmpty()) for (int k = 0; k < p.getInventory().getContainerSize(); k++) if (p.getInventory().getItem(k).is(want)) { src = p.getInventory().getItem(k); break; }
        if (src.isEmpty()) return false;
        torp[i] = src.copyWithCount(1);
        if (!p.getAbilities().instabuild) src.shrink(1);
        setChanged();
        return true;
    }

    private void releaseOrphans(Player to) {
        for (int i = 0; i < torp.length; i++) {
            if (torp[i].isEmpty()) continue;
            net.minecraft.world.item.Item want = LaidMineEntity.torpedoFor(LaidMineEntity.kindOf(slots[i].stack));
            if (want != null && torp[i].is(want)) continue;
            ItemStack back = torp[i];
            torp[i] = ItemStack.EMPTY;
            if (to != null) { if (!to.addItem(back)) to.drop(back, false); }
            else if (level != null && !level.isClientSide)
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, back);
            setChanged();
        }
    }

    private Player screenUser() {
        if (!(level instanceof ServerLevel sl)) return null;
        for (net.minecraft.server.level.ServerPlayer p : sl.players())
            if (p.containerMenu instanceof MachineMenu m && m.machine() == this) return p;
        return null;
    }

    @Override
    public Container slotContainer() {
        Container base = super.slotContainer();
        return new Container() {
            private void after() {
                Player p = screenUser();
                releaseOrphans(p);
                for (int i = 0; i < slots.length; i++) armFrom(p, i);
            }
            @Override public int getContainerSize() { return base.getContainerSize(); }
            @Override public boolean isEmpty() { return base.isEmpty(); }
            @Override public ItemStack getItem(int i) { return base.getItem(i); }
            @Override public ItemStack removeItem(int i, int n) { ItemStack r = base.removeItem(i, n); after(); return r; }
            @Override public ItemStack removeItemNoUpdate(int i) { ItemStack r = base.removeItemNoUpdate(i); after(); return r; }
            @Override public void setItem(int i, ItemStack st) { base.setItem(i, st); after(); }
            @Override public int getMaxStackSize() { return base.getMaxStackSize(); }
            @Override public void setChanged() { base.setChanged(); after(); }
            @Override public boolean stillValid(Player p) { return base.stillValid(p); }
            @Override public boolean canPlaceItem(int i, ItemStack st) { return base.canPlaceItem(i, st); }
            @Override public void clearContent() { base.clearContent(); after(); }
        };
    }

    @Override
    public boolean tryInsert(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return false;
        for (int i = 0; i < slots.length; i++) {
            net.minecraft.world.item.Item want = LaidMineEntity.torpedoFor(LaidMineEntity.kindOf(slots[i].stack));
            if (want != null && torp[i].isEmpty() && held.is(want)) {
                torp[i] = held.copyWithCount(1);
                if (!player.getAbilities().instabuild) held.shrink(1);
                player.displayClientMessage(Component.translatable("message.wmd.kgun.mine_armed", Component.translatable(want.getDescriptionId())), true);
                setChanged();
                return true;
            }
        }
        for (int i = 0; i < slots.length; i++) {
            ItemStack rest = slots[i].insert(held, false);
            if (rest.getCount() == held.getCount()) continue;
            ItemStack in = slots[i].stack;
            if (!player.getAbilities().instabuild) held.setCount(rest.getCount());
            int kind = LaidMineEntity.kindOf(in);
            if (kind >= 0) {
                net.minecraft.world.item.Item want = LaidMineEntity.torpedoFor(kind);
                boolean armed = armFrom(player, i);
                player.displayClientMessage(want == null ? Component.translatable("message.wmd.kgun.mine_in", Component.translatable(in.getDescriptionId()), mineDepth())
                        : Component.translatable(armed ? "message.wmd.kgun.mine_in_loaded" : "message.wmd.kgun.mine_in_empty", Component.translatable(in.getDescriptionId()),
                                Component.translatable(want.getDescriptionId())), true);
            }
            setChanged();
            return true;
        }
        return false;
    }

    @Override
    public List<ItemStack> takeAll() {
        List<ItemStack> l = new ArrayList<>(super.takeAll());
        for (int i = 0; i < torp.length; i++) { if (!torp[i].isEmpty()) l.add(torp[i]); torp[i] = ItemStack.EMPTY; }
        return l;
    }

    private void feedFromRack() {
        if (level == null || !(getBlockState().getBlock() instanceof MachineBlock mb)) return;
        Direction f = facing(), m = Machines.mount(getBlockState());
        for (BlockPos l : mb.footprint()) {
            BlockPos c = Machines.toWorld(worldPosition, f, m, l.getX(), l.getY(), l.getZ());
            for (Direction d : Direction.values()) {
                BlockEntity b = level.getBlockEntity(c.relative(d));
                if (b instanceof MachinePartBlockEntity part && part.getController() != null) b = level.getBlockEntity(part.getController());
                if (!(b instanceof DepthChargeRackBlockEntity r) || !java.util.Objects.equals(r.getOwner(), getOwner())) continue;
                Container ct = r.slotContainer();
                for (int i = ct.getContainerSize() - 1; i >= 0; i--) {
                    if (ct.getItem(i).isEmpty()) continue;
                    ItemStack got = ct.removeItem(i, 1);
                    if (got.isEmpty()) continue;
                    spare.stack = got;
                    spare.sync();
                    setChanged();
                    sync();
                    return;
                }
            }
        }
    }

    private void pulse() {
        boolean sig = signal();
        if (sig && !lastSignal) fire(null);
        lastSignal = sig;
    }

    private boolean signal() {
        if (level == null || !(getBlockState().getBlock() instanceof MachineBlock mb)) return false;
        for (BlockPos l : mb.footprint())
            if (level.hasNeighborSignal(Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), l.getX(), l.getY(), l.getZ()))) return true;
        return false;
    }

    private static final Map<String, Map<Long, Long>> NET = Statics.server(new HashMap<>());

    private void beat() {
        if (!(level instanceof ServerLevel sl) || frequency <= 0 || sl.getGameTime() % 20 != 0) return;
        NET.computeIfAbsent(FireControlNet.key(sl, getOwner(), frequency), k -> new HashMap<>()).put(worldPosition.asLong(), sl.getGameTime());
    }

    static int salvo(ServerLevel sl, UUID owner, int freq, Player by) {
        Map<Long, Long> m = NET.get(FireControlNet.key(sl, owner, freq));
        if (m == null) return 0;
        long now = sl.getGameTime();
        m.entrySet().removeIf(e -> now - e.getValue() > FireControlNet.NET_TTL);
        int n = 0;
        for (long p : new ArrayList<>(m.keySet())) {
            BlockPos bp = BlockPos.of(p);
            if (!sl.isLoaded(bp) || !(sl.getBlockEntity(bp) instanceof DepthChargeThrowerBlockEntity k) || k.isRemoved() || k.frequency != freq
                    || !java.util.Objects.equals(k.getOwner(), owner)) continue;
            if (k.mineOnArbor()) continue;
            if (k.fire(by == null ? null : by.getUUID())) { n++; k.setChanged(); }
        }
        return n;
    }

    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return gauges; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected int inputTanks() { return slots.length; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override protected float progress01() {
        if (fireLeft > 0) return 1.0F - fireLeft / (float) Balance.KG_FIRE_TICKS;
        return reloadLeft > 0 ? 1.0F - reloadLeft / (float) Balance.KG_RELOAD_TICKS : -1.0F;
    }
    @Override protected List<String> labelKeys() { return List.of("depth_charge", "depth_charge"); }
    @Override protected String[] iconIds() { return new String[] { "depth_charge", "depth_charge" }; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected SoundEvent runSound() { return WmdSounds.DC_THROW.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", guiFeUse() * 20), Component.translatable("gui.wmd.info.depth_charge_thrower"));
    }

    @Override
    protected String detail() {
        return (mineOnArbor() ? "sea mine on the arbor" + (torp[0].isEmpty() ? "" : " (torpedo loaded)") + ", lays " + mineDepth() + " deep" : loaded() ? "drum on the arbor" : "no drum on the arbor")
                + (spare.stack.isEmpty() ? "" : LaidMineEntity.kindOf(spare.stack) >= 0 ? " + a spare mine" : " + a spare") + ", fuse " + depth + " deep"
                + (frequency > 0 ? ", fire control " + String.format("%04d", frequency) : "") + (reloadLeft > 0 ? ", reloading" : fireLeft > 0 || queued ? ", firing" : "");
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("kgDepth", depth);
        tag.putInt("kgFreq", frequency);
        tag.putLong("kgFire", lastFire);
        tag.putLong("kgReload", reloadStart);
        tag.putInt("kgReloadLeft", reloadLeft);
        for (int i = 0; i < torp.length; i++) if (!torp[i].isEmpty()) tag.putString("kgTorp" + i, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(torp[i].getItem()).toString());
        ownerData.write(tag);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        if (tag.contains("kgDepth")) depth = tag.getInt("kgDepth");
        frequency = tag.getInt("kgFreq");
        ownerData.read(tag);
        lastFire = tag.contains("kgFire") ? tag.getLong("kgFire") : -1000L;
        reloadStart = tag.contains("kgReload") ? tag.getLong("kgReload") : -1000L;
        reloadLeft = tag.getInt("kgReloadLeft");
        for (int i = 0; i < torp.length; i++) {
            net.minecraft.world.item.Item it = tag.contains("kgTorp" + i)
                    ? net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(tag.getString("kgTorp" + i))) : null;
            torp[i] = it == null || it == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
        }
        if (tag.contains("kgArbM")) shownArborMine = tag.getInt("kgArbM");
        if (tag.contains("kgSpM")) shownSpareMine = tag.getInt("kgSpM");
        if (tag.contains("kgArbT")) shownArborTorp = tag.getBoolean("kgArbT");
        if (tag.contains("kgArbor")) shownArbor = tag.getBoolean("kgArbor");
        if (tag.contains("kgSpare")) shownSpare = tag.getBoolean("kgSpare");
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveExtra(tag);
        tag.putBoolean("kgArbor", loaded());
        tag.putBoolean("kgSpare", !spare.stack.isEmpty());
        tag.putInt("kgArbM", LaidMineEntity.kindOf(arbor.stack));
        tag.putInt("kgSpM", LaidMineEntity.kindOf(spare.stack));
        tag.putBoolean("kgArbT", !torp[0].isEmpty());
        return tag;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide && (loaded() != shownArbor || spare.stack.isEmpty() == shownSpare || LaidMineEntity.kindOf(arbor.stack) != shownArborMine
                || LaidMineEntity.kindOf(spare.stack) != shownSpareMine || torp[0].isEmpty() == shownArborTorp)) {
            shownArbor = loaded();
            shownSpare = !spare.stack.isEmpty();
            shownArborMine = LaidMineEntity.kindOf(arbor.stack);
            shownSpareMine = LaidMineEntity.kindOf(spare.stack);
            shownArborTorp = !torp[0].isEmpty();
            sync();
        }
    }
}
