package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public abstract class SonarSensorBlockEntity extends ProcessUnitBlockEntity implements Owned {

    private final OwnerData ownerData = new OwnerData();
    protected int frequency = 1;

    protected ListTag heard = new ListTag();

    protected boolean wet;
    private int lastFreq = -1;

    protected SonarSensorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int fe) { super(type, pos, state, fe); }

    protected abstract double range();

    protected abstract int kind();

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        sync();
    }

    public int frequency() { return frequency; }

    public void setFrequency(int f) {
        f = Math.max(1, Math.min(9999, f));
        if (f == frequency) return;
        if (level instanceof ServerLevel sl) SonarNet.forget(sl, getOwner(), frequency, worldPosition.asLong());
        frequency = f;
        setChanged();
        sync();
    }

    protected void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public Vec3 ear() { return SableBridge.worldPos(level, worldPosition); }

    protected boolean inWater(ServerLevel sl) {
        Vec3 e = ear();
        if (NavalLayer.underwater(sl, e)) return true;
        BlockPos b = BlockPos.containing(e);
        for (Direction d : Direction.Plane.HORIZONTAL) if (sl.getFluidState(b.relative(d)).is(FluidTags.WATER) && NavalLayer.depthBelowSurface(sl, Vec3.atCenterOf(b.relative(d))) >= 1.0) return true;
        return false;
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return true;
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15L)) % Balance.SONAR_SCAN_TICKS != 0) return true;
        boolean w = inWater(sl);
        Vec3 ear = ear();
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        SonarNet.beat(sl, getOwner(), frequency, worldPosition.asLong(), ear, kind(), range(), ship, deep());
        List<NoiseNet.Heard> h = new ArrayList<>();
        if (w && !deaf(sl)) {
            for (NoiseNet.Heard x : NoiseNet.heard(sl, ear, range(), Balance.NOISE_FLOOR_DB, ship, null)) {
                double lv = x.level() - SonarNet.layerLoss(sl, ear, x.src().pos());
                if (lv >= Balance.NOISE_FLOOR_DB) h.add(new NoiseNet.Heard(x.src(), lv, x.dist(), x.bearing()));
            }
        }
        SonarNet.report(sl, getOwner(), frequency, worldPosition.asLong(), ear, h);
        ListTag l = new ListTag();
        for (NoiseNet.Heard x : h) {
            if (l.size() >= Balance.SONAR_KEEP) break;
            CompoundTag c = new CompoundTag();
            c.putInt("b", (int) Math.round(x.bearing() * 10.0));
            c.putInt("l", (int) Math.round(x.level()));
            c.putInt("k", x.src().kind().ordinal());
            l.add(c);
        }
        boolean changed = w != wet || !l.equals(heard) || lastFreq != frequency;
        wet = w;
        heard = l;
        lastFreq = frequency;
        if (changed) { setChanged(); sync(); }
        return true;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) SonarNet.forget(sl, getOwner(), frequency, worldPosition.asLong());
        super.setRemoved();
    }

    public boolean wet() { return wet; }

    public List<CompoundTag> heardRows() {
        List<CompoundTag> l = new ArrayList<>();
        for (Tag t : heard) if (t instanceof CompoundTag c) l.add(c);
        return l;
    }

    protected boolean deaf(ServerLevel sl) { return false; }

    public String deafKey() { return null; }

    public double listenRange() { return range(); }

    public String modeKey() { return (kind() == SonarNet.DOME ? "gui.wmd.sonar.dome_mode" : "gui.wmd.sonar.array_mode") + (deep() ? "_deep" : ""); }

    public Component extraLine() { return null; }

    private boolean deepShown;

    protected boolean takesDeepUpgrade() { return false; }

    @Override
    protected boolean acceptsUpgrade(UpgradeItem.Type t) { return t == UpgradeItem.Type.DEEP_SONAR && takesDeepUpgrade(); }

    public boolean deep() { return level != null && level.isClientSide ? deepShown : takesDeepUpgrade() && upgradeLevel(UpgradeItem.Type.DEEP_SONAR) > 0; }

    @Override
    protected void upgradesChanged() { setChanged(); sync(); }

    public int ping(Player by) { return 0; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.SON_ROOM_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", feCostShown() * 20),
                Component.translatable("gui.wmd.info.sonar", String.format("%,d", (int) range())));
    }

    protected int feCostShown() { return guiFeUse(); }

    @Override
    protected String detail() {
        return (isRunning() ? (wet ? "listening" : "window dry") : "no power") + ", " + heard.size() + " contact(s), frequency " + frequency;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putInt("sonFreq", frequency);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        if (tag.contains("sonFreq")) frequency = Math.max(1, Math.min(9999, tag.getInt("sonFreq")));
        if (tag.contains("sonHeard")) heard = tag.getList("sonHeard", Tag.TAG_COMPOUND);
        wet = tag.getBoolean("sonWet");
        deepShown = tag.getBoolean("sonDeep");
        readExtra(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        tag.putInt("sonFreq", frequency);
        tag.put("sonHeard", heard);
        tag.putBoolean("sonWet", wet);
        tag.putBoolean("sonDeep", deep());
        writeExtra(tag);
        return tag;
    }

    protected void readExtra(CompoundTag tag) {}
    protected void writeExtra(CompoundTag tag) {}
}
