package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class NaniteForgeBlockEntity extends ProcessUnitBlockEntity implements CcAnimated, CopycatState.Host, Owned, GuiAction {

    public static final int STANDBY = 0, RUN = 1, LOAD = 2, SPRAY = 3;
    public static final int A_AUTO = 1;

    static volatile BlockPos viewing;

    private final CopycatState copycat = new CopycatState();
    private final OwnerData ownerData = new OwnerData();
    private boolean auto = true, linked;
    private int mode = STANDBY;
    private long busyAt = -100L, loadAt = -100L;
    private int rebuilt, runs;

    private int cMode;
    private boolean cLinked, cAuto;
    private long lastEase = -1;
    private float swirl, pSwirl, imp, pImp, drum, pDrum, spin = 0.0F, hatch, pHatch, car, pCar, carGoal;
    private int cRebuilt;

    public NaniteForgeBlockEntity(BlockPos pos, BlockState state) {
        super(NaniteBlocks.NANITE_FORGE_BE.get(), pos, state, Balance.NFORGE_FE, Balance.NFORGE_BUFFER, Balance.NFORGE_RECEIVE);
    }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }
    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }

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

    private Direction front() { return Machines.dirToWorld(facing(), Direction.UP, Direction.NORTH); }
    Direction east() { return Machines.dirToWorld(facing(), Direction.UP, Direction.EAST); }
    Direction facingDir() { return facing(); }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return part.equals(worldPosition) && side == front().getOpposite() ? energy() : null; }

    void link(boolean on) { if (on != linked) { linked = on; if (!on) mode = STANDBY; sync(); } }

    boolean linked() { return level != null && level.isClientSide ? cLinked : linked; }
    boolean auto() { return level != null && level.isClientSide ? cAuto : auto; }

    void busy(int m) {
        if (level == null) return;
        busyAt = level.getGameTime();
        if (m == LOAD && mode != LOAD) { loadAt = busyAt; playOnce(level, WmdSounds.ARC_STRIKE.get(), Balance.SND_MECH, 0.7F); }
        if (m != mode) { mode = m; sync(); }
    }

    boolean ready() { return energy().getEnergyStored() >= Balance.NFORGE_MIN_FE; }

    boolean canPay(int n) { return energy().getEnergyStored() >= n; }

    boolean pay(int n) { return useExtraEnergy(n); }

    void rebuilt() { rebuilt++; setChanged(); }

    void runDone() { runs++; mode = STANDBY; sync(); }

    int energyFe() { return energy().getEnergyStored(); }
    int rebuiltCount() { return level != null && level.isClientSide ? cRebuilt : rebuilt; }
    int modeNow() { return level != null && level.isClientSide ? cMode : mode; }

    @Override
    protected boolean work() {
        if (level == null) return false;
        tickCharge();
        long now = level.getGameTime();
        boolean busy = now - busyAt <= 2;
        if (!busy && mode != STANDBY) { mode = STANDBY; sync(); }
        return busy;
    }

    @Override
    public boolean guiAction(Player player, int id) {
        if (id != A_AUTO) return false;
        if (!Access.mayUse(this, player)) { Access.deny(this, player); return true; }
        auto = !auto;
        sync();
        return true;
    }

    @Override
    public Component status() {
        String k = !linked ? "nolink" : mode == SPRAY ? "spray" : mode == LOAD ? "load" : mode == RUN ? "run" : ready() ? "ready" : "charging";
        return Component.translatable("block.wmd.nanite_forge").append(": ").append(Component.translatable("gui.wmd.nforge.st." + k));
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.NFORGE_FE),
                Component.translatable("gui.wmd.nforge.info_block", Balance.NFORGE_BLOCK_FE),
                Component.translatable("gui.wmd.nforge.info_batch", Balance.NFORGE_BATCH, Balance.NFORGE_BATCH_TICKS));
    }

    static void viewing(BlockPos p) { viewing = p; }

    private static float approach(float x, float goal, float step) { return x < goal ? Math.min(goal, x + step) : Math.max(goal, x - step); }

    private void ease() {
        if (level == null || level.getGameTime() == lastEase) return;
        lastEase = level.getGameTime();
        pSwirl = swirl; pImp = imp; pDrum = drum; pHatch = hatch; pCar = car;
        boolean on = isRunning() || cMode != STANDBY;
        float want = cMode == STANDBY ? (cLinked ? 1.0F : 0.4F) : 4.0F;
        spin = approach(spin, want, want > spin ? 0.15F : 4.0F / Balance.NFORGE_SPINDOWN);
        swirl += Balance.NFORGE_SWIRL * spin;
        imp += cMode == STANDBY ? 0.0F : 30.0F;
        drum += on && cMode != STANDBY ? 6.0F : 0.0F;
        hatch = approach(hatch, cMode == LOAD ? 1.0F : 0.0F, 1.0F / 10.0F);
        if (cMode == LOAD && hatch >= 0.99F && car == carGoal) carGoal += 60.0F;
        car = approach(car, carGoal, 4.0F);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        ease();
        float sw = (float) Math.toRadians(pSwirl + (swirl - pSwirl) * pt), im = (float) Math.toRadians(pImp + (imp - pImp) * pt);
        float dr = (float) Math.toRadians(pDrum + (drum - pDrum) * pt), h = pHatch + (hatch - pHatch) * pt;
        float c = (float) Math.toRadians(pCar + (car - pCar) * pt);
        float door = worldPosition.equals(viewing) ? 1.0F : 0.0F;
        float d = (float) Math.toRadians(100.0) * door;
        model.getBone("housing_door_l").ifPresent(b -> b.setRotY(d));
        model.getBone("housing_door_r").ifPresent(b -> b.setRotY(-d));
        float hr = (float) Math.toRadians(Balance.NFORGE_HATCH_OPEN) * h;
        model.getBone("housing_hatch_f").ifPresent(b -> b.setRotX(-hr));
        model.getBone("housing_hatch_b").ifPresent(b -> b.setRotX(hr));
        model.getBone("lift").ifPresent(b -> b.setPosY(Balance.NFORGE_LIFT * Math.min(1.0F, h * 1.25F)));
        model.getBone("carousel").ifPresent(b -> b.setRotY(c));
        model.getBone("vat0").ifPresent(b -> b.setRotY(sw));
        model.getBone("vat1").ifPresent(b -> b.setRotY(-sw));
        model.getBone("imp0").ifPresent(b -> b.setRotY(im));
        model.getBone("imp1").ifPresent(b -> b.setRotY(-im));
        model.getBone("drum").ifPresent(b -> b.setRotX(dr));
        boolean powered = energyShown();
        model.getBone("laser").ifPresent(b -> b.setHidden(!(cMode != STANDBY && ((long) tt / 3) % 2 == 0)));

        boolean flicker = !powered && ((long) tt / 2) % 3 == 0;
        model.getBone("vat0").ifPresent(b -> b.setHidden(flicker));
        model.getBone("vat1").ifPresent(b -> b.setHidden(flicker));
        model.getBone("lamp").ifPresent(b -> b.setHidden(!powered || ((long) tt / 10) % 2 == 0 && !cLinked));
    }

    private boolean energyShown() { return cCharged; }

    private boolean cCharged;

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.NF_SWARM_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }
    @Override protected float soundPitch() { return 1.0F; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected String detail() { return rebuilt + " / " + runs; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putBoolean("nfAuto", auto);
        tag.putInt("nfRebuilt", rebuilt);
        tag.putInt("nfRuns", runs);
        putSync(tag);
    }

    private void putSync(CompoundTag tag) {
        tag.putInt("nfM", mode);
        tag.putBoolean("nfL", linked);
        tag.putBoolean("nfA", auto);
        tag.putBoolean("nfC", level != null && !level.isClientSide ? ready() : cCharged);
        tag.putInt("nfR", rebuilt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        cMode = tag.getInt("nfM");
        cLinked = tag.getBoolean("nfL");
        cAuto = tag.getBoolean("nfA");
        cCharged = tag.getBoolean("nfC");
        cRebuilt = tag.getInt("nfR");
        if (level != null && level.isClientSide) return;
        auto = !tag.contains("nfAuto") || tag.getBoolean("nfAuto");
        rebuilt = tag.getInt("nfRebuilt");
        runs = tag.getInt("nfRuns");
        mode = STANDBY;
        linked = false;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        putSync(tag);
        return tag;
    }

    private boolean lastCharged;

    @Override
    protected void idle() { tickCharge(); }

    private void tickCharge() {
        if (level == null || level.isClientSide || level.getGameTime() % 20 != 0) return;
        boolean c = ready();
        if (c != lastCharged) { lastCharged = c; sync(); }
    }
}
