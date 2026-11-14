package com.ashraf.wmd;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class DeckLiftBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    public static final int UP = 0, LOWERING = 1, DOWN = 2, RAISING = 3;

    private static final Map<ResourceKey<Level>, Set<Long>> ACTIVE = Statics.server(new HashMap<>());

    private static final Map<ResourceKey<Level>, Floor[]> FLOORS = Statics.server(new java.util.concurrent.ConcurrentHashMap<>());

    public record Floor(BlockPos head, UUID ship, int sx, int sz, int drop, int state, long stateAt, int tripTicks) {

        public double top(double now) { return head.getY() + 1.0 - drop * eased(state, now, stateAt, tripTicks); }

        public double rate(double now) { return (top(now + 0.05) - top(now - 0.05)) / 0.1; }

        public Vec3 velocity(ServerLevel sl, double now) {
            double r = rate(now) * 20.0;
            if (ship == null) return new Vec3(0.0, r, 0.0);
            Vec3 p = new Vec3(head.getX() + sx * 0.5, top(now), head.getZ() + sz * 0.5);
            Vec3 a = SableBridge.toWorld(sl, p), b = SableBridge.toWorld(sl, p.add(0.0, 1.0, 0.0));
            Vec3 up = a == null || b == null ? new Vec3(0.0, 1.0, 0.0) : b.subtract(a);
            SableBridge.ShipInfo si = SableBridge.shipInfo(sl, ship);
            Vec3 sv = si == null ? Vec3.ZERO : si.linearVelocity();
            return sv.add(up.scale(r));
        }
    }

    public static double physicsNow(ServerLevel sl, double partial) { return sl.getGameTime() - 1.0 + partial; }

    private volatile Floor floor;

    private Object platform;
    private long platformRetry;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private int sx, sz, drop;
    private int state, tripTicks;
    private long stateAt, nextScan;
    private boolean sig;

    public DeckLiftBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.DECK_LIFT_BE.get(), pos, state, 0, Balance.LIFT_BUFFER, Balance.LIFT_RECEIVE);
    }

    long coverAt = Long.MIN_VALUE;
    boolean covered;

    public int sx() { return sx; }
    public int sz() { return sz; }
    public int drop() { return drop; }
    public int liftState() { return state; }
    long getStateAtClient() { return stateAt; }
    int getTripTicksClient() { return tripTicks; }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }
    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    boolean head() { return getBlockState().getValue(DeckLiftBlock.HEAD); }

    public double topY(float pt) {
        double deck = worldPosition.getY() + 1.0;
        if (level == null) return deck;
        double k = progress(level.getGameTime() + pt);
        return deck - drop * k;
    }

    double progress(double now) { return eased(state, now, stateAt, tripTicks); }

    static double eased(int state, double now, long stateAt, int tripTicks) {
        if (state == UP) return 0.0;
        if (state == DOWN) return 1.0;
        double t = Math.max(0.0, Math.min(1.0, (now - stateAt) / Math.max(1, tripTicks)));
        t = t * t * (3.0 - 2.0 * t);
        return state == LOWERING ? t : 1.0 - t;
    }

    public long tripFe() { return (long) sx * sz * Math.max(1, drop) * Balance.LIFT_FE; }

    int tripTicksFor() {
        return Balance.LIFT_BASE_TICKS + (int) Math.round(drop * Balance.LIFT_TICKS_PER_BLOCK * (1.0 + sx * sz / (double) Balance.LIFT_AREA_REF));
    }

    private boolean tile(BlockPos p) { return level != null && level.getBlockState(p).getBlock() instanceof DeckLiftBlock; }

    private boolean measure() {
        int nx = 0, nz = 0;
        while (nx < Balance.LIFT_MAX && tile(worldPosition.east(nx))) nx++;
        while (nz < Balance.LIFT_MAX && tile(worldPosition.south(nz))) nz++;
        boolean whole = nx >= Balance.LIFT_MIN && nz >= Balance.LIFT_MIN;
        for (int x = 0; whole && x < nx; x++) for (int z = 0; whole && z < nz; z++) if (!tile(worldPosition.offset(x, 0, z))) whole = false;
        int d = 0;
        while (d < Balance.LIFT_MAX_DROP - 1 && level.getBlockState(worldPosition.below(d + 1)).getCollisionShape(level, worldPosition.below(d + 1)).isEmpty()) d++;
        int nd = whole && d > 0 ? d + 1 : 0;
        if (!whole) { nx = 0; nz = 0; }
        if (nx != sx || nz != sz || nd != drop) { sx = nx; sz = nz; drop = nd; sync(); }
        return whole && drop > 0;
    }

    private boolean shaftClear() {
        for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) for (int y = 1; y < drop; y++) {
            BlockPos p = worldPosition.offset(x, -y, z);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
        }
        return true;
    }

    private AABB column(ServerLevel sl) {
        AABB plot = new AABB(worldPosition.getX(), worldPosition.getY() + 1 - drop, worldPosition.getZ(), worldPosition.getX() + sx, worldPosition.getY() + 25, worldPosition.getZ() + sz);
        Vec3 a = SableBridge.toWorld(sl, new Vec3(plot.minX, plot.minY, plot.minZ)), b = SableBridge.toWorld(sl, new Vec3(plot.maxX, plot.maxY, plot.maxZ));
        return a == null || b == null ? plot : new AABB(a, b);
    }

    private boolean halfOn(ServerLevel sl) {
        UUID own = SableBridge.shipIdAt(sl, worldPosition);
        AABB c = column(sl);
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, c)) {
            if (s.id().equals(own)) continue;
            AABB b = s.worldBounds();
            if (b.minX < c.minX - 0.25 || b.maxX > c.maxX + 0.25 || b.minZ < c.minZ - 0.25 || b.maxZ > c.maxZ + 0.25) return true;
        }
        return false;
    }

    String toggle(Player player) {
        if (!(level instanceof ServerLevel sl)) return "message.wmd.lift.busy";
        return start(sl);
    }

    private String start(ServerLevel sl) {
        if (!AirGate.on()) return "message.wmd.lift.off";
        if (state == LOWERING || state == RAISING) return "message.wmd.lift.busy";
        if (state == UP && !measure()) return "message.wmd.lift.not_rect";
        if (halfOn(sl)) return "message.wmd.lift.half_on";
        if (state == UP && !shaftClear()) return "message.wmd.lift.blocked";
        if (energy().getEnergyStored() < tripFe()) return "message.wmd.lift.no_power";
        useExtraEnergy((int) Math.min(Integer.MAX_VALUE, tripFe()));
        long now = sl.getGameTime();
        tripTicks = tripTicksFor();
        if (state == UP) { setLowered(sl, true); state = LOWERING; } else state = RAISING;
        stateAt = now;
        ACTIVE.computeIfAbsent(sl.dimension(), k -> new LinkedHashSet<>()).add(worldPosition.asLong());
        refloor(sl, this);
        keepPlatform(sl);
        playOnce(sl, WmdSounds.LIFT_CLUNK.get(), Balance.SND_MECH, 1.0F);
        playOnce(sl, WmdSounds.LIFT_RAIL.get(), Balance.SND_CLICK, 1.0F);
        Wmd.LOGGER.info("Deck lift at {}: {} ({} x {}, drop {}, {} ticks, {} FE)", worldPosition, state == LOWERING ? "down" : "up", sx, sz, drop, tripTicks, tripFe());
        sync();
        return state == LOWERING ? "message.wmd.lift.down" : "message.wmd.lift.up";
    }

    private void setLowered(ServerLevel sl, boolean lowered) {
        for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) {
            BlockPos p = worldPosition.offset(x, 0, z);
            BlockState s = sl.getBlockState(p);
            if (s.getBlock() instanceof DeckLiftBlock && s.getValue(DeckLiftBlock.LOWERED) != lowered) sl.setBlock(p, s.setValue(DeckLiftBlock.LOWERED, lowered), 2);
        }
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;

        BlockState ws = sl.getBlockState(worldPosition);
        if (ws != getBlockState() && ws.getBlock() == getBlockState().getBlock()) setBlockState(ws);
        if (!head()) return false;
        long now = sl.getGameTime();
        if (!AirGate.on()) return false;
        boolean s = sl.hasNeighborSignal(worldPosition);
        if (s && !sig) start(sl);
        sig = s;
        if (state == UP) {
            if (now >= nextScan) { nextScan = now + Balance.LIFT_SCAN_TICKS; measure(); }
            return false;
        }
        if (floor == null || now % 40 == 0) refloor(sl, this);
        keepPlatform(sl);
        if ((state == LOWERING || state == RAISING) && now % 10 == 0) wakeRiders(sl);
        carry(sl, now);
        if ((state == LOWERING || state == RAISING) && now - stateAt >= tripTicks) {
            if (state == RAISING) {
                state = UP;
                setLowered(sl, false);
                Set<Long> a = ACTIVE.get(sl.dimension());
                if (a != null) a.remove(worldPosition.asLong());
                dropPlatform(sl);
            } else state = DOWN;
            stateAt = now;
            refloor(sl, this);
            playOnce(sl, WmdSounds.LIFT_CLUNK.get(), Balance.SND_MECH, 0.9F);
            playOnce(sl, WmdSounds.LIFT_RAIL.get(), Balance.SND_CLICK, 0.9F);
            sync();
        }
        return state == LOWERING || state == RAISING;
    }

    private void carry(ServerLevel sl, long now) {
        double top = topY(0.0F);
        AABB c = column(sl);
        for (Entity e : sl.getEntities((Entity) null, c, en -> !en.isSpectator() && !en.isPassenger())) {

            if (e instanceof net.minecraft.world.entity.player.Player) continue;
            Vec3 w = e.position();
            UUID ship = SableBridge.shipIdAt(sl, worldPosition);
            Vec3 l = ship == null ? w : SableBridge.toShip(sl, ship, w);
            if (l == null) continue;
            if (l.x < worldPosition.getX() || l.x > worldPosition.getX() + sx || l.z < worldPosition.getZ() || l.z > worldPosition.getZ() + sz) continue;
            if (l.y < top - 1.2 || l.y > top + 1.0) continue;
            Vec3 nl = new Vec3(l.x, top, l.z), nw = ship == null ? nl : SableBridge.toWorld(sl, nl);
            if (nw == null) continue;
            e.teleportTo(nw.x, nw.y, nw.z);
            e.setDeltaMovement(e.getDeltaMovement().multiply(1.0, 0.0, 1.0));
            e.fallDistance = 0.0F;
            e.setOnGround(true);
        }
    }

    private void keepPlatform(ServerLevel sl) {
        if (state == UP || sx <= 0 || sz <= 0 || floor == null) { dropPlatform(sl); return; }
        if (platform != null || sl.getGameTime() < platformRetry) return;
        platform = SableBridge.liftPlatform(sl, this, sx, sz);
        if (platform == null) platformRetry = sl.getGameTime() + 20;
    }

    private void dropPlatform(ServerLevel sl) {
        if (platform == null) return;
        SableBridge.liftPlatformRemove(sl, platform);
        platform = null;
    }

    private void wakeRiders(ServerLevel sl) {
        UUID own = SableBridge.shipIdAt(sl, worldPosition);
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, column(sl))) if (!s.id().equals(own)) SableBridge.wake(sl, s.id());
    }

    public Floor floor() { return floor; }

    private static void refloor(ServerLevel sl, DeckLiftBlockEntity me) {
        Set<Long> a = ACTIVE.get(sl.dimension());
        java.util.List<Floor> l = new java.util.ArrayList<>();
        if (me != null && me.state == UP) me.floor = null;
        if (a != null) for (Long p : a) {
            BlockPos bp = BlockPos.of(p);
            DeckLiftBlockEntity d = me != null && me.worldPosition.equals(bp) ? me : sl.isLoaded(bp) && sl.getBlockEntity(bp) instanceof DeckLiftBlockEntity x ? x : null;
            if (d == null || d.isRemoved()) continue;
            d.floor = d.state == UP || d.sx <= 0 ? null : new Floor(bp.immutable(), SableBridge.shipIdAt(sl, bp), d.sx, d.sz, d.drop, d.state, d.stateAt, d.tripTicks);
            if (d.floor != null) l.add(d.floor);
        }
        if (l.isEmpty()) FLOORS.remove(sl.dimension());
        else FLOORS.put(sl.dimension(), l.toArray(new Floor[0]));
    }

    public static Floor floorAt(ServerLevel level, Vec3 q, double now) {
        Floor[] a = FLOORS.get(level.dimension());
        if (a == null) return null;
        for (Floor f : a) {
            Vec3 l = f.ship() == null ? q : SableBridge.toShip(level, f.ship(), q);
            if (l == null) continue;
            BlockPos bp = f.head();
            if (l.x < bp.getX() || l.x > bp.getX() + f.sx() || l.z < bp.getZ() || l.z > bp.getZ() + f.sz()) continue;
            double top = f.top(now);
            if (l.y <= top && l.y >= top - 1.0) return f;
        }
        return null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) {
            Set<Long> a = ACTIVE.get(sl.dimension());
            if (a != null) a.remove(worldPosition.asLong());
            dropPlatform(sl);
            refloor(sl, null);
        }
        super.setRemoved();
    }

    @Override public void ccAnimate(CcBones model, double tt, float pt) {}

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.LIFT_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }

    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return head() && side == Direction.WEST ? energy() : null; }

    @Override protected String detail() { return head() ? sx + " x " + sz + ", drop " + drop + ", " + new String[] { "UP", "LOWERING", "DOWN", "RAISING" }[state] + ", trip " + tripFe() + " FE" : "tile"; }

    @Override
    public Component status() {
        String k = sx == 0 ? "message.wmd.lift.status_not_rect" : new String[] { "message.wmd.lift.status_up", "message.wmd.lift.status_moving", "message.wmd.lift.status_down", "message.wmd.lift.status_moving" }[state];
        return Component.translatable("block.wmd.deck_lift").append(": ").append(Component.translatable(k, sx, sz, drop, tripFe(), energy().getEnergyStored(), Balance.LIFT_MIN, Balance.LIFT_MAX));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putInt("dlSx", sx); tag.putInt("dlSz", sz); tag.putInt("dlDrop", drop);
        tag.putInt("dlState", state == LOWERING || state == RAISING ? DOWN : state);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        sx = tag.getInt("dlSx"); sz = tag.getInt("dlSz"); drop = tag.getInt("dlDrop");
        state = Math.max(0, Math.min(3, tag.getInt("dlState")));
        if (tag.contains("dlAt")) { stateAt = tag.getLong("dlAt"); tripTicks = tag.getInt("dlTrip"); }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl && state != UP) {
            ACTIVE.computeIfAbsent(sl.dimension(), k -> new LinkedHashSet<>()).add(worldPosition.asLong());
            refloor(sl, this);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("dlSx", sx); tag.putInt("dlSz", sz); tag.putInt("dlDrop", drop);
        tag.putInt("dlState", state);
        tag.putLong("dlAt", stateAt);
        tag.putInt("dlTrip", tripTicks);
        return tag;
    }
}
