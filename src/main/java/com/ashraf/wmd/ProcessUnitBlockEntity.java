package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public abstract class ProcessUnitBlockEntity extends BlockEntity implements GeoBlockEntity, MachinePorts, GuiMachine, EmpVulnerable {

    private static class Buffer extends EnergyStorage {
        Buffer(int capacity, int maxReceive) { super(capacity, maxReceive, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private final Buffer store;

    @Override
    public void empDrain() { store.set(0); setChanged(); }
    private final int fePerTick;
    protected boolean running;

    protected int flowAcc;
    private float flow;
    private int syncedFlowQ = -1;
    private float flowTarget, shownFlow;
    private int syncedLevelQ = -1;
    private float levelTarget, levelShown;
    Object soundHandle;
    private int clientAge;
    private boolean wasRunning;

    private float spin, spinSpeed;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    protected ProcessUnitBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int fePerTick) {
        super(type, pos, state);
        this.fePerTick = fePerTick;
        this.store = new Buffer(fePerTick * 400, fePerTick * 10);
    }

    protected abstract boolean work();

    protected void idle() {}

    protected abstract List<FluidTank> tanks();

    protected abstract SoundEvent runSound();
    protected abstract BlockPos soundLocal();
    protected float soundVolume() { return 0.7F; }

    protected float soundPitch() { return 1.0F; }

    protected void runStarted(Level level) {}
    protected void runStopped(Level level) {}

    protected final void playOnce(Level level, SoundEvent e, float volume, float pitch) {
        BlockPos p = soundPos();
        net.minecraft.world.phys.Vec3 w = ShipFrame.drawnWorld(level, worldPosition, new net.minecraft.world.phys.Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), Float.NaN);
        if (level instanceof net.minecraft.server.level.ServerLevel sl) {
            int mf = upgradeLevel(UpgradeItem.Type.MUFFLER);
            float f = mf >= Balance.UPGRADE_MUFFLER_SILENT ? 0.0F : 1.0F - 0.2F * mf;
            if (f > 0) sl.playSound(null, w.x, w.y, w.z, e, net.minecraft.sounds.SoundSource.BLOCKS, volume * f, pitch);
        } else if (mufflerFactor() > 0) level.playLocalSound(w.x, w.y, w.z, e, net.minecraft.sounds.SoundSource.BLOCKS, volume * mufflerFactor(), pitch, false);
    }
    protected float levelFraction() { return 0.0F; }
    protected void clientEffects(Level level) {}

    protected List<double[]> vents() { return List.of(); }

    protected double[] worldPoint(double lx, double ly, double lz) {
        double dx, dz;
        switch (facing()) {
            case EAST -> { dx = -lz; dz = lx; }
            case SOUTH -> { dx = -lx; dz = -lz; }
            case WEST -> { dx = lz; dz = -lx; }
            default -> { dx = lx; dz = lz; }
        }
        Direction m = Machines.mount(getBlockState());
        net.minecraft.world.phys.Vec3 p = m == Direction.UP ? new net.minecraft.world.phys.Vec3(worldPosition.getX() + 0.5 + dx, worldPosition.getY() + ly, worldPosition.getZ() + 0.5 + dz)
                : Machines.mountPoint(worldPosition, m, dx, ly, dz);
        p = level != null && level.isClientSide ? ShipFrame.drawnWorld(level, worldPosition, p, Float.NaN) : SableBridge.toWorld(level, p);
        return new double[] { p.x, p.y, p.z };
    }

    private void ventEffects(Level level) {
        List<double[]> vs = vents();
        if (vs.isEmpty() || level.getGameTime() % 9 != 0) return;
        var r = level.random;
        double[] v = vs.get(r.nextInt(vs.size()));
        double[] w = worldPoint(v[0], v[1], v[2]);
        float strength = 0.4F + 0.6F * flowFraction();
        if (r.nextFloat() > strength) return;
        level.addParticle(ParticleTypes.CLOUD, w[0] + (r.nextDouble() - 0.5) * 0.2, w[1] + 0.35, w[2] + (r.nextDouble() - 0.5) * 0.2,
                (r.nextDouble() - 0.5) * 0.01, 0.025 + r.nextDouble() * 0.015, (r.nextDouble() - 0.5) * 0.01);
    }

    public BlockPos soundPos() {
        BlockPos l = soundLocal();
        return Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), l.getX(), l.getY(), l.getZ());
    }

    protected abstract List<Fluid> tankFluids();
    protected abstract List<String> labelKeys();
    protected abstract int rateMbPerTick();

    protected abstract String detail();

    public boolean isRunning() { return running; }

    private long lastWorkTick = Long.MIN_VALUE / 2;
    private float workShare = 1.0F;

    @Override public Component guiTitle() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }

    protected int inputTanks() { return 1; }
    @Override public boolean guiTankColoured() { return true; }
    @Override public boolean guiTankOutput(int tank) { return tank >= inputTanks(); }
    @Override public List<FluidTank> guiTanks() { return tanks(); }
    @Override public List<Fluid> guiTankFluids() { return tankFluids(); }
    @Override public List<Component> guiTankLabels() { return labelKeys().stream().<Component>map(k -> Component.translatable("gui.wmd.label." + k)).toList(); }
    @Override public IEnergyStorage guiEnergy() { return store; }
    @Override public int guiFeUse() { return feCost(); }

    protected int reason;

    protected float progress01() { return -1.0F; }

    @Override
    public int guiProgress() { float p = progress01(); return p < 0 ? -1 : Math.round(Math.min(1.0F, p) * 1000.0F); }

    @Override
    public int guiStatus() {
        if (running && workShare < 0.95F) return GuiMachine.LOW_POWER + Math.max(1, Math.min(99, Math.round(workShare * 100.0F)));
        if (running) return 1;
        if (!hasPower()) return 2;
        return reason;
    }
    @Override public boolean guiRunning() { return running; }
    @Override public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", fePerTick * 20),
                Component.translatable("gui.wmd.info.rate", rateMbPerTick() * 20));
    }
    public boolean hasPower() { return store.getEnergyStored() >= feCost(); }
    protected IEnergyStorage energy() { return store; }

    protected boolean useExtraEnergy(int n) {
        if (store.getEnergyStored() < feCost() + n) return false;
        store.use(n);
        return true;
    }

    public static final int UPGRADE_SLOTS = 4;
    private final net.minecraft.world.item.ItemStack[] upgrades = { net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.item.ItemStack.EMPTY,
            net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.item.ItemStack.EMPTY };

    protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    protected void upgradesChanged() {}

    protected static final java.util.Set<UpgradeItem.Type> ITEM_MACHINE = java.util.EnumSet.of(UpgradeItem.Type.SPEED, UpgradeItem.Type.EFFICIENCY, UpgradeItem.Type.BUFFER, UpgradeItem.Type.MUFFLER),
            FLUID_UNIT = java.util.EnumSet.of(UpgradeItem.Type.THROUGHPUT, UpgradeItem.Type.EFFICIENCY, UpgradeItem.Type.BUFFER, UpgradeItem.Type.MUFFLER);

    private int mufflerShown;

    private double extraRuns;

    private int[] baseCap;

    protected int gaugeTanks() { return 0; }

    private void changedUpgrades() {
        applyBuffer();
        upgradesChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void applyBuffer() {
        List<FluidTank> ts = tanks();
        int n = ts.size() - gaugeTanks();
        if (n <= 0) return;
        if (baseCap == null) { baseCap = new int[n]; for (int i = 0; i < n; i++) baseCap[i] = ts.get(i).getCapacity(); }
        int lvl = upgradeLevel(UpgradeItem.Type.BUFFER);
        for (int i = 0; i < n && i < baseCap.length; i++) ts.get(i).setCapacity((int) Math.round(baseCap[i] * (1.0 + Balance.UPGRADE_BUFFER_PER_LEVEL * lvl)));
    }

    protected int feCost() {

        if (fePerTick <= 0) return 0;
        double m = 1.0;
        int sp = upgradeLevel(UpgradeItem.Type.SPEED);
        if (sp > 0) m *= (1.0 + Balance.UPGRADE_SPEED_FE_PER_LEVEL * sp) / (1.0 + Balance.UPGRADE_SPEED_PER_LEVEL * sp);
        m *= Math.max(0.0, 1.0 - Balance.UPGRADE_EFFICIENCY_PER_LEVEL * upgradeLevel(UpgradeItem.Type.EFFICIENCY));
        return Math.max(1, (int) Math.round(fePerTick * m));
    }

    private double extraPerTick() {
        return Balance.UPGRADE_SPEED_PER_LEVEL * upgradeLevel(UpgradeItem.Type.SPEED) + Balance.UPGRADE_THROUGHPUT_PER_LEVEL * upgradeLevel(UpgradeItem.Type.THROUGHPUT);
    }

    float mufflerFactor() { return mufflerShown >= Balance.UPGRADE_MUFFLER_SILENT ? 0.0F : 1.0F - 0.2F * mufflerShown; }

    public boolean takesUpgrades() {
        for (UpgradeItem.Type t : UpgradeItem.Type.values()) if (acceptsUpgrade(t)) return true;
        return false;
    }

    public int upgradeLevel(UpgradeItem.Type t) {
        for (net.minecraft.world.item.ItemStack s : upgrades) if (s.getItem() instanceof UpgradeItem u && u.type == t) return s.getCount();
        return 0;
    }

    public boolean installUpgrade(net.minecraft.world.entity.player.Player player) {
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof UpgradeItem u)) return false;
        if (this instanceof Owned o && !Access.mayUse(o, player)) { Access.deny(o, player); return true; }
        if (!acceptsUpgrade(u.type)) { player.displayClientMessage(Component.translatable("message.wmd.upgrade.wrong_machine"), true); return true; }
        int slot = -1;
        for (int i = 0; i < UPGRADE_SLOTS; i++) if (upgrades[i].getItem() instanceof UpgradeItem f && f.type == u.type) slot = i;
        if (slot < 0) for (int i = 0; i < UPGRADE_SLOTS && slot < 0; i++) if (upgrades[i].isEmpty()) slot = i;
        if (slot < 0) { player.displayClientMessage(Component.translatable("message.wmd.upgrade.full"), true); return true; }
        int n = Math.min(held.getCount(), u.type.maxLevel - upgrades[slot].getCount());
        if (n <= 0) { player.displayClientMessage(Component.translatable("message.wmd.upgrade.max", u.type.maxLevel), true); return true; }
        if (upgrades[slot].isEmpty()) upgrades[slot] = held.copyWithCount(n); else upgrades[slot].grow(n);
        if (!player.getAbilities().instabuild) held.shrink(n);
        player.displayClientMessage(Component.translatable("message.wmd.upgrade.fitted", upgrades[slot].getHoverName(), upgrades[slot].getCount()), true);
        changedUpgrades();
        setChanged();
        return true;
    }

    public net.minecraft.world.item.ItemStack removeUpgrade(int i) {
        if (i < 0 || i >= UPGRADE_SLOTS || upgrades[i].isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        net.minecraft.world.item.ItemStack s = upgrades[i];
        upgrades[i] = net.minecraft.world.item.ItemStack.EMPTY;
        changedUpgrades();
        setChanged();
        return s;
    }

    public List<net.minecraft.world.item.ItemStack> takeUpgrades() {
        List<net.minecraft.world.item.ItemStack> l = new java.util.ArrayList<>();
        for (int i = 0; i < UPGRADE_SLOTS; i++) { if (!upgrades[i].isEmpty()) l.add(upgrades[i]); upgrades[i] = net.minecraft.world.item.ItemStack.EMPTY; }
        return l;
    }

    @Override public boolean guiUpgrades() { return takesUpgrades(); }

    @Override
    public int guiUpgradeCode(int i) {
        if (i < 0 || i >= UPGRADE_SLOTS || !(upgrades[i].getItem() instanceof UpgradeItem u)) return 0;
        return (u.type.ordinal() + 1) * 16 + upgrades[i].getCount();
    }

    public Component status() {
        Component state = Component.translatable(running ? "message.wmd.unit.running"
                : hasPower() ? "message.wmd.unit.idle" : "message.wmd.unit.no_power");
        return Component.translatable("message.wmd.unit.status",
                Component.translatable(getBlockState().getBlock().getDescriptionId()), state, detail());
    }

    protected Direction facing() { return getBlockState().getValue(MachineBlock.FACING); }

    protected boolean at(BlockPos part, Direction side, int x, int y, int z, Direction localSide) {
        Direction f = facing(), m = Machines.mount(getBlockState());
        BlockPos l = Machines.toLocal(worldPosition, f, m, part);
        return l.getX() == x && l.getY() == y && l.getZ() == z && side == Machines.dirToWorld(f, m, localSide);
    }

    protected static IFluidHandler fillOnly(FluidTank t) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int i) { return t.getFluid(); }
            @Override public int getTankCapacity(int i) { return t.getCapacity(); }
            @Override public boolean isFluidValid(int i, FluidStack s) { return t.isFluidValid(s); }
            @Override public int fill(FluidStack s, FluidAction a) { return t.fill(s, a); }
            @Override public FluidStack drain(FluidStack s, FluidAction a) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(int max, FluidAction a) { return FluidStack.EMPTY; }
        };
    }

    protected static IFluidHandler drainOnly(FluidTank t) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int i) { return t.getFluid(); }
            @Override public int getTankCapacity(int i) { return t.getCapacity(); }
            @Override public boolean isFluidValid(int i, FluidStack s) { return false; }
            @Override public int fill(FluidStack s, FluidAction a) { return 0; }
            @Override public FluidStack drain(FluidStack s, FluidAction a) { return t.drain(s, a); }
            @Override public FluidStack drain(int max, FluidAction a) { return t.drain(max, a); }
        };
    }

    protected static String mb(FluidTank t) { return t.getFluidAmount() + " / " + t.getCapacity() + " mB"; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ProcessUnitBlockEntity be) {
        if (level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, pos)) {
            if (be.running) {
                be.running = false;
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
                if (be.litState(level, pos)) be.setLit(level, pos, false);
            }
            return;
        }
        boolean did = false, starved = !be.hasPower();
        if (!starved) {
            int cost = be.feCost();
            did = be.work();
            if (did) be.store.use(cost);

            be.extraRuns += did ? be.extraPerTick() : 0.0;
            while (did && be.extraRuns >= 1.0 && be.store.getEnergyStored() >= cost) {
                be.extraRuns -= 1.0;
                if (!be.work()) break;
                be.store.use(cost);
            }
            if (be.extraRuns > 4.0) be.extraRuns = 0.0;
        } else {
            be.idle();
        }

        long now = level.getGameTime();
        if (did) be.lastWorkTick = now;

        boolean shown = did || now - be.lastWorkTick <= (starved ? Balance.LOW_POWER_GRACE : Balance.FLOW_GRACE);
        if (!shown) be.workShare = 1.0F;
        else if (did || starved) be.workShare += ((did ? 1.0F : 0.0F) - be.workShare) * 0.02F;
        be.flow += (Math.min(1.0F, be.flowAcc / (float) Math.max(1, be.rateMbPerTick())) - be.flow) * 0.25F;
        be.flowAcc = 0;
        int q = Math.round(be.flow * 20), lq = Math.round(be.levelFraction() * 32);
        if (shown != be.running || ((q != be.syncedFlowQ || lq != be.syncedLevelQ) && level.getGameTime() % 4 == 0)) {
            be.syncedLevelQ = lq;
            be.running = shown;
            be.syncedFlowQ = q;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
        if (level.getGameTime() % 40 == 0) be.setChanged();
        if (shown != be.litState(level, pos)) be.setLit(level, pos, shown);
    }

    private boolean litState(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
    }

    private void setLit(Level level, BlockPos pos, boolean lit) {
        BlockState s = level.getBlockState(pos);
        if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) != lit) level.setBlock(pos, s.setValue(BlockStateProperties.LIT, lit), 3);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ProcessUnitBlockEntity be) {
        be.clientAge++;
        if (be.running != be.wasRunning) {
            be.wasRunning = be.running;
            if (be.clientAge > 40) { if (be.running) be.runStarted(level); else be.runStopped(level); }
        }
        be.spin += be.spinSpeed;
        be.shownFlow += (be.flowTarget - be.shownFlow) * 0.15F;
        be.levelShown += (be.levelTarget - be.levelShown) * 0.1F;
        if (be.running) {
            UnitClient.ensureSound(be);
            be.clientEffects(level);
            be.ventEffects(level);
        }
        float target = be.running ? 0.9F : 0.0F;
        be.spinSpeed += (target - be.spinSpeed) * 0.05F;
        if (!be.running && be.spinSpeed < 0.002F) be.spinSpeed = 0.0F;
        if (be.spin > Mth.TWO_PI * 100) be.spin -= Mth.TWO_PI * 100;
    }

    public float levelShown() { return Mth.clamp(levelShown, 0.0F, 1.0F); }

    public float flowFraction() { return Mth.clamp(shownFlow, 0.0F, 1.0F); }

    public float spinAngle(float partialTick) { return spin + spinSpeed * partialTick; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        List<FluidTank> ts = tanks();
        for (int i = 0; i < ts.size(); i++) tag.put("tank" + i, ts.get(i).writeToNBT(registries, new CompoundTag()));
        tag.putInt("energy", store.getEnergyStored());
        tag.putBoolean("running", running);
        for (int i = 0; i < UPGRADE_SLOTS; i++) if (!upgrades[i].isEmpty()) tag.put("upg" + i, upgrades[i].save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        List<FluidTank> ts = tanks();
        for (int i = 0; i < ts.size(); i++) ts.get(i).readFromNBT(registries, tag.getCompound("tank" + i));
        store.set(tag.getInt("energy"));
        running = tag.getBoolean("running");
        if (tag.contains("energy")) {
            for (int i = 0; i < UPGRADE_SLOTS; i++) upgrades[i] = net.minecraft.world.item.ItemStack.parseOptional(registries, tag.getCompound("upg" + i));
            applyBuffer();
        }
        if (tag.contains("muf")) mufflerShown = tag.getInt("muf");
        flowTarget = tag.getInt("flowq") / 20.0F;
        levelTarget = tag.getInt("levelq") / 32.0F;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("running", running);
        tag.putInt("flowq", syncedFlowQ < 0 ? 0 : syncedFlowQ);
        tag.putInt("levelq", syncedLevelQ < 0 ? 0 : syncedLevelQ);
        tag.putInt("muf", upgradeLevel(UpgradeItem.Type.MUFFLER));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
