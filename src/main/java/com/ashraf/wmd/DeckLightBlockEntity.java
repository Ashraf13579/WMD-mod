package com.ashraf.wmd;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class DeckLightBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    public static final int BLUE = 0, WHITE = 1, RED = 2, GREEN = 3, BEACON = 4;

    static final float[][] RGB = { { 0.25F, 0.5F, 1.0F }, { 1.0F, 0.97F, 0.88F }, { 1.0F, 0.15F, 0.1F }, { 0.2F, 1.0F, 0.35F }, { 1.0F, 0.6F, 0.15F } };

    private static final Map<ResourceKey<Level>, Set<Long>> BEACONS = Statics.server(new HashMap<>());

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private int mode = BLUE;
    private boolean off;

    private boolean consoleOff;

    public DeckLightBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.DECK_LIGHT_BE.get(), pos, state, 0, 1, 0);
    }

    public int mode() { return mode; }
    public boolean lit() { return !off; }

    @Override public java.util.UUID getOwner() { return ownerData.id(); }
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

    void cycleMode() { mode = (mode + 1) % 5; sync(); }

    void setMode(int m) { int n = Math.floorMod(m, 5); if (n != mode) { mode = n; setChanged(); sync(); } }

    boolean consoleOff() { return consoleOff; }
    void setConsoleOff(boolean v) { if (v != consoleOff) { consoleOff = v; setChanged(); } }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        boolean o = sl.hasNeighborSignal(worldPosition) || consoleOff || !AirGate.on();
        if (o != off) { off = o; sync(); }
        Set<Long> b = BEACONS.computeIfAbsent(sl.dimension(), k -> new LinkedHashSet<>());
        if (mode == BEACON && !off && AirGate.on()) b.add(worldPosition.asLong()); else b.remove(worldPosition.asLong());
        return false;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) { Set<Long> b = BEACONS.get(sl.dimension()); if (b != null) b.remove(worldPosition.asLong()); }
        super.setRemoved();
    }

    public static double[] beaconFor(ServerLevel level, Vec3 at) {
        Set<Long> b = BEACONS.get(level.dimension());
        if (b == null || b.isEmpty() || at == null) return null;
        double[] best = null;
        for (Long p : b) {
            BlockPos bp = BlockPos.of(p);
            if (!level.isLoaded(bp) || !(level.getBlockEntity(bp) instanceof DeckLightBlockEntity d) || d.off || d.mode != BEACON) continue;
            Vec3 c = SableBridge.toWorld(level, Vec3.atCenterOf(bp).add(0.0, 0.5, 0.0));
            Direction f = d.facing();
            Vec3 ax0 = SableBridge.toWorld(level, Vec3.atCenterOf(bp).add(-f.getStepX(), 0.5, -f.getStepZ()));
            if (c == null || ax0 == null) continue;
            Vec3 ax = ax0.subtract(c), r = at.subtract(c);
            Vec3 axH = new Vec3(ax.x, 0.0, ax.z), rH = new Vec3(r.x, 0.0, r.z);
            double dist = rH.length();
            if (dist < 4.0 || dist > Balance.BEACON_RANGE || axH.lengthSqr() < 1.0E-9) continue;
            axH = axH.normalize();
            double side = Math.toDegrees(Math.atan2(axH.x * rH.z - axH.z * rH.x, axH.dot(rH)));
            if (Math.abs(side) > Balance.BEACON_CONE) continue;
            double elev = Math.toDegrees(Math.atan2(r.y, dist));
            if (best == null || dist < best[1]) best = new double[] { elev - Balance.BEACON_SLOPE, dist, side };
        }
        return best;
    }

    static Vec3[] beaconFrame(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof DeckLightBlockEntity d) || d.off || d.mode != BEACON) return null;
        Direction f = d.facing();
        Vec3 c = SableBridge.toWorld(level, Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0));
        Vec3 a = SableBridge.toWorld(level, Vec3.atCenterOf(pos).add(f.getStepX(), 0.5, f.getStepZ()));
        if (c == null || a == null) return null;
        Vec3 u = new Vec3(a.x - c.x, 0.0, a.z - c.z);
        if (u.lengthSqr() < 1.0E-9) return null;
        return new Vec3[] { c, u.normalize() };
    }

    static BlockPos beaconNear(ServerLevel level, Vec3 at, double r) {
        Set<Long> b = BEACONS.get(level.dimension());
        if (b == null || at == null) return null;
        BlockPos best = null;
        double bd = r * r;
        for (Long p : b) {
            BlockPos bp = BlockPos.of(p);
            Vec3[] fr = beaconFrame(level, bp);
            if (fr == null) continue;
            double d = fr[0].distanceToSqr(at);
            if (d < bd) { bd = d; best = bp; }
        }
        return best;
    }

    static boolean foulDeck(ServerLevel level, java.util.UUID deck) {
        if (deck == null) return false;
        for (BlockPos q : SableBridge.blockEntitiesOn(level, deck))
            if (level.isLoaded(q) && level.getBlockEntity(q) instanceof DeckLightBlockEntity d && !d.off && d.mode == RED) return true;
        return false;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final boolean show = !off && !ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);

    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.GPOD_SPIN.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected String detail() { return new String[] { "blue edge", "white centreline", "red", "green", "LANDING BEACON" }[mode] + (off ? ", OFF" : ""); }

    @Override
    public Component status() {
        return Component.translatable("block.wmd.deck_light").append(": ").append(Component.translatable("message.wmd.deck_light.mode" + mode, (int) Balance.BEACON_SLOPE))
                .append(off ? Component.translatable("message.wmd.deck_light.off") : Component.empty());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putInt("dlMode", mode);
        tag.putBoolean("dlConsoleOff", consoleOff);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        mode = Math.max(0, Math.min(4, tag.getInt("dlMode")));
        consoleOff = tag.getBoolean("dlConsoleOff");
        if (tag.contains("dlOff")) off = tag.getBoolean("dlOff");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("dlMode", mode);
        tag.putBoolean("dlOff", off);
        return tag;
    }
}
