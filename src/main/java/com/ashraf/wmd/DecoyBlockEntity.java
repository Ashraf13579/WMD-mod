package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public abstract class DecoyBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated {

    static final int INFLATE_TICKS = 100, DEFLATE_TICKS = 40, REMOVE_TICKS = 70;
    static final float FLAT = 0.06F;

    static final Set<DecoyBlockEntity> LOADED = Statics.server(Collections.newSetFromMap(new WeakHashMap<>()));

    private final OwnerData ownerData = new OwnerData();
    private long placedAt = -1L, poppedAt = -1L;
    private boolean wasHit;

    protected DecoyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state, 0); }

    @Override public boolean hasPower() { return true; }
    @Override protected int feCost() { return 0; }

    public abstract int reconKind();

    abstract String id();

    public boolean popped() { return poppedAt >= 0L; }

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

    private long lifeTicks() { return Balance.DECOY_LIFE_MINUTES * 60L * 20L; }

    @Override
    protected boolean work() {
        if (level == null) return false;
        LOADED.add(this);
        if (this instanceof Radar) RadarEmit.note(this);
        long now = level.getGameTime();
        if (placedAt < 0L) { placedAt = now; setChanged(); sync(); }
        if (poppedAt < 0L && now - placedAt > lifeTicks()) deflate(false);
        if (poppedAt >= 0L && now - poppedAt > REMOVE_TICKS) { level.removeBlock(worldPosition, false); return false; }
        return poppedAt < 0L && now - placedAt >= 0L;
    }

    void deflate(boolean hit) {
        if (poppedAt >= 0L || level == null) return;
        poppedAt = level.getGameTime();
        wasHit = hit;
        Vec3 c = centre();
        if (level instanceof ServerLevel sl) {
            if (hit) {
                sl.playSound(null, c.x, c.y, c.z, WmdSounds.DECOY_POP.get(), SoundSource.BLOCKS, 4.0F, 0.6F);
                sl.playSound(null, c.x, c.y, c.z, WmdSounds.CHAFF_HISS.get(), SoundSource.BLOCKS, 2.0F, 0.7F);
                sl.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 30, 1.2, 0.8, 1.2, 0.05);
                if (getOwner() != null)
                    CcBattleLog.add(sl.getServer(), getOwner(), CcBattleLog.HIT, "log.wmd.decoy_hit", "#block.wmd." + id(),
                            Integer.toString(worldPosition.getX()), Integer.toString(worldPosition.getZ()));
            } else sl.playSound(null, c.x, c.y, c.z, WmdSounds.CHAFF_HISS.get(), SoundSource.BLOCKS, 1.0F, 0.5F);
        }
        setChanged();
        sync();
    }

    AABB box() {
        BlockState s = getBlockState();
        Direction f = s.hasProperty(MachineBlock.FACING) ? s.getValue(MachineBlock.FACING) : Direction.NORTH;
        AABB b = new AABB(worldPosition);
        if (s.getBlock() instanceof MachineBlock mb)
            for (BlockPos l : mb.footprint()) b = b.minmax(new AABB(Machines.toWorld(worldPosition, f, Machines.mount(getBlockState()), l.getX(), l.getY(), l.getZ())));
        return b;
    }

    Vec3 centre() { return box().getCenter(); }

    static void blast(ServerLevel level, Vec3 pos, double reach) {
        if (LOADED.isEmpty()) return;
        List<DecoyBlockEntity> hit = new ArrayList<>();
        for (DecoyBlockEntity d : LOADED) {
            if (d.isRemoved() || d.getLevel() != level || d.popped()) continue;
            AABB b = d.box();
            Vec3 c0 = b.getCenter(), cw = SableBridge.toWorld(level, c0);
            if (cw != c0) b = b.move(cw.subtract(c0));
            double cx = Math.max(b.minX, Math.min(b.maxX, pos.x)), cy = Math.max(b.minY, Math.min(b.maxY, pos.y)), cz = Math.max(b.minZ, Math.min(b.maxZ, pos.z));
            if (pos.distanceToSqr(cx, cy, cz) <= reach * reach) hit.add(d);
        }
        for (DecoyBlockEntity d : hit) d.deflate(true);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) LOADED.remove(this);
    }

    float inflation(double tt) {
        if (poppedAt >= 0L) return Math.max(FLAT, 1.0F - (float) ((tt - poppedAt) / DEFLATE_TICKS) * (1.0F - FLAT));
        if (placedAt < 0L) return FLAT;
        float k = (float) Math.max(0.0, Math.min(1.0, (tt - placedAt) / INFLATE_TICKS));
        return FLAT + (1.0F - FLAT) * k * k * (3.0F - 2.0F * k);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final float s = inflation(tt);
        boolean moving = s < 0.999F && s > FLAT + 0.001F;
        final float wob = moving ? 1.0F + 0.03F * (float) Math.sin(tt * 1.3) : 1.0F;
        model.getBone("inflate").ifPresent(b -> { b.setScaleY(s); b.setScaleX(wob); b.setScaleZ(2.0F - wob); });
        boolean on = poppedAt < 0L && placedAt >= 0L && !ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        model.getBone("lamp").ifPresent(b -> b.setHidden(!on));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public IEnergyStorage guiEnergy() { return null; }
    @Override protected SoundEvent runSound() { return WmdSounds.DC_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected float soundVolume() { return 0.05F; }

    @Override
    public List<Component> guiInfo() {
        long left = level == null || placedAt < 0L ? lifeTicks() : Math.max(0L, lifeTicks() - (level.getGameTime() - placedAt));
        return List.of(Component.translatable(popped() ? "gui.wmd.info.decoy_popped" : "gui.wmd.info.decoy", (int) Math.ceil(left / 1200.0)));
    }

    @Override
    protected String detail() {
        return popped() ? (wasHit ? "popped (hit)" : "deflated") : placedAt < 0L ? "inflating" : "inflated";
    }

    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return null; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putLong("placed", placedAt);
        tag.putLong("popped", poppedAt);
        tag.putBoolean("hit", wasHit);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        placedAt = tag.contains("placed") ? tag.getLong("placed") : -1L;
        poppedAt = tag.contains("popped") ? tag.getLong("popped") : -1L;
        wasHit = tag.getBoolean("hit");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        tag.putLong("placed", placedAt);
        tag.putLong("popped", poppedAt);
        tag.putBoolean("hit", wasHit);
        return tag;
    }

    public static class Launcher extends DecoyBlockEntity {
        public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 3;
        public Launcher(BlockPos pos, BlockState state) { super(Wmd.DECOY_LAUNCHER_BE.get(), pos, state); }
        @Override public int reconKind() { return Recon.ICON_LAUNCH; }
        @Override String id() { return "decoy_launcher"; }
    }

    public static class Radar extends DecoyBlockEntity {
        public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 3, Z0 = 0, Z1 = 2;
        public Radar(BlockPos pos, BlockState state) { super(Wmd.DECOY_RADAR_BE.get(), pos, state); }
        @Override public int reconKind() { return Recon.ICON_RADAR; }
        @Override String id() { return "decoy_radar"; }
    }
}
