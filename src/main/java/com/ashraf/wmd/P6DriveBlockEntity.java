package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public abstract class P6DriveBlockEntity extends AirEngineBlockEntity implements CopycatState.Host {

    protected final CopycatState copycat = new CopycatState();
    protected float spool;
    private float sentSpool = -9.0F;

    protected float tSpool, cSpool;
    protected double spin, pSpin;

    protected P6DriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int fePerTick, int bufferFe, int receivePerTick) {
        super(type, pos, state, fePerTick, bufferFe, receivePerTick);
    }

    protected abstract int spoolUp();
    protected abstract int spoolDown();

    protected double spinRps() { return 5.0; }

    public boolean bare() { return nacelle(); }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { syncNow(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    protected void spoolTo(float target) {
        spool = approach(spool, target, 1.0F / (target > spool ? spoolUp() : spoolDown()));
    }

    protected void syncSpool(long now) {
        boolean edge = (spool < 0.01F) != (sentSpool < 0.01F) || (spool > 0.99F) != (sentSpool > 0.99F);
        if (!edge && !(Math.abs(spool - sentSpool) > 0.05F && now % 4 == 0)) return;
        sentSpool = spool;
        syncNow();
    }

    protected boolean beat(ServerLevel sl) { return (sl.getGameTime() + (worldPosition.asLong() & 15)) % Balance.PROP_BEAT == 0; }

    protected Vec3 shipUp(ServerLevel sl) {
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        Vec3 com = info == null ? null : SableBridge.toShip(sl, info, info.worldCenter());
        Vec3 a = info == null ? null : SableBridge.toShip(sl, info, info.worldCenter().add(0.0, 1.0, 0.0));
        return com == null || a == null ? null : a.subtract(com);
    }

    protected float[] localOf(ServerLevel sl, Vec3 world) {
        Vec3 p = ship != null ? SableBridge.toShip(sl, ship, world) : world;
        if (p == null) return null;
        double[] l = AirLights.local(p.x - (worldPosition.getX() + 0.5), p.z - (worldPosition.getZ() + 0.5), facing());
        return new float[] { (float) l[0], (float) (p.y - worldPosition.getY()), (float) l[1] };
    }

    protected Vec3 worldVec(double lx, double ly, double lz) {
        double[] w = worldPoint(lx, ly, lz);
        return new Vec3(w[0], w[1], w[2]);
    }

    @Override
    protected void idle() {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        force = null;
        spoolTo(0.0F);
        syncSpool(sl.getGameTime());
    }

    @Override
    protected void easeTick(Level lv) {
        pSpin = spin;
        cSpool = approach(cSpool, tSpool, 1.3F / (tSpool > cSpool ? spoolUp() : spoolDown()));
        spin += Math.PI * 2.0 * spinRps() / 20.0 * cSpool;
        if (spin > Math.PI * 2000.0) { spin -= Math.PI * 2000.0; pSpin -= Math.PI * 2000.0; }
        clientExtra(lv);
    }

    protected void clientExtra(Level lv) {}

    protected float spinAt(float pt) { return (float) Mth.lerp(pt, pSpin, spin); }

    @Override protected boolean runningClient() { return cSpool > 0.05F; }
    @Override protected boolean soundAlive() { return cSpool > 0.05F; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }

    protected void putExtra(CompoundTag tag) {}
    protected void readExtra(CompoundTag tag, boolean client) {}

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("p6Spool", spool);
        copycat.save(tag, registries);
        putExtra(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        float sp = Mth.clamp(tag.getFloat("p6Spool"), 0.0F, 1.0F);
        if (client) tSpool = sp; else spool = sp;
        readExtra(tag, client);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putFloat("p6Spool", spool);
        copycat.putSync(tag);
        putExtra(tag);
        return tag;
    }
}
