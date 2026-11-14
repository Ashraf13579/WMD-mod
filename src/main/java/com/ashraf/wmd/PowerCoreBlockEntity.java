package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class PowerCoreBlockEntity extends ProcessUnitBlockEntity implements CcAnimated, DriveEngine, CopycatState.Host, TankDamage.Holder {

    public static final int X0 = 0, X1 = 0, Y0 = 0, Y1 = 0, Z0 = 0, Z1 = 0;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 0, Direction.SOUTH, false).fluid(0, 0, 0, Direction.WEST, "jet_fuel", false);

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> CORES = Statics.server(new HashMap<>());

    private final FluidTank fuel = new FluidTank(Balance.CORE_TANK_MB, fs -> fs.getFluid() == WmdFluids.JET_FUEL.source().get());
    private final CopycatState copycat = new CopycatState();

    private final TankDamage.Health health = new TankDamage.Health(Balance.TANK_HP_CORE);
    private boolean blown;
    private UUID ship;
    private boolean apu;
    private int apuTicks;
    private long driveAt = Long.MIN_VALUE / 2;

    private boolean cApu, cDrive;

    private float shaftAng, prevShaftAng, shaftSpeed;

    public PowerCoreBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.POWER_CORE_BE.get(), pos, state, 0, Balance.CORE_BUFFER_FE, Balance.CORE_RECEIVE_FE);
    }

    static boolean drawFrom(ServerLevel level, UUID ship, int fe) {
        if (!AirGate.on() || level == null || ship == null || fe <= 0) return false;
        Map<UUID, Set<Long>> m = CORES.get(level.dimension());
        Set<Long> at = m == null ? null : m.get(ship);
        if (at == null || at.isEmpty()) return false;
        for (Long p : new ArrayList<>(at)) {
            if (!level.isLoaded(BlockPos.of(p))) continue;
            if (level.getBlockEntity(BlockPos.of(p)) instanceof PowerCoreBlockEntity c && !c.isRemoved() && c.take(fe)) return true;
        }
        return false;
    }

    static int[] chargeOf(ServerLevel level, UUID ship) {
        int[] out = { 0, 0 };
        if (!AirGate.on() || level == null || ship == null) return out;
        Map<UUID, Set<Long>> m = CORES.get(level.dimension());
        Set<Long> at = m == null ? null : m.get(ship);
        if (at == null) return out;
        for (Long p : at)
            if (level.isLoaded(BlockPos.of(p)) && level.getBlockEntity(BlockPos.of(p)) instanceof PowerCoreBlockEntity c) {
                out[0] += c.energy().getEnergyStored();
                out[1] += c.energy().getMaxEnergyStored();
            }
        return out;
    }

    static int[] fuelOf(ServerLevel level, UUID ship) {
        int[] out = { 0, 0 };
        if (!AirGate.on() || level == null || ship == null) return out;
        Map<UUID, Set<Long>> m = CORES.get(level.dimension());
        Set<Long> at = m == null ? null : m.get(ship);
        if (at == null) return out;
        for (Long p : at)
            if (level.isLoaded(BlockPos.of(p)) && level.getBlockEntity(BlockPos.of(p)) instanceof PowerCoreBlockEntity c) {
                out[0] += c.fuel.getFluidAmount();
                out[1] += c.fuel.getCapacity();
            }
        int[] t = FuelTankBlockEntity.poolOf(level, ship);
        out[0] += t[0];
        out[1] += t[1];
        return out;
    }

    static int drainFuelFrom(ServerLevel level, UUID ship, int mb) {
        if (!AirGate.on() || level == null || ship == null || mb <= 0) return 0;
        Map<UUID, Set<Long>> m = CORES.get(level.dimension());
        Set<Long> at = m == null ? null : m.get(ship);
        int got = 0;
        if (at != null) for (Long p : new ArrayList<>(at)) {
            if (got >= mb) break;
            if (!level.isLoaded(BlockPos.of(p))) continue;
            if (level.getBlockEntity(BlockPos.of(p)) instanceof PowerCoreBlockEntity c && !c.isRemoved() && !EmpField.jammed(level, c.worldPosition)) {
                int d = c.fuel.drain(mb - got, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).getAmount();
                if (d > 0) { got += d; c.setChanged(); }
            }
        }
        if (got < mb) got += FuelTankBlockEntity.drain(level, ship, mb - got);
        return got;
    }

    static int moveFuel(ServerLevel level, UUID ship, int mb, boolean drain) {
        if (!AirGate.on() || level == null || ship == null || mb <= 0) return 0;
        Map<UUID, Set<Long>> m = CORES.get(level.dimension());
        Set<Long> at = m == null ? null : m.get(ship);
        int left = mb;

        if (drain) left -= FuelTankBlockEntity.drain(level, ship, left);
        if (at != null) for (Long p : new ArrayList<>(at)) {
            if (left <= 0) break;
            if (!level.isLoaded(BlockPos.of(p)) || !(level.getBlockEntity(BlockPos.of(p)) instanceof PowerCoreBlockEntity c)) continue;
            int n = drain ? c.fuel.drain(left, IFluidHandler.FluidAction.EXECUTE).getAmount()
                    : c.fuel.fill(new net.neoforged.neoforge.fluids.FluidStack(WmdFluids.JET_FUEL.source().get(), left), IFluidHandler.FluidAction.EXECUTE);
            if (n > 0) { left -= n; c.setChanged(); }
        }
        if (!drain && left > 0) left -= FuelTankBlockEntity.fill(level, ship, left);
        return mb - left;
    }

    static int chargeFrom(ServerLevel level, UUID ship, int fe) {
        if (!AirGate.on() || level == null || ship == null || fe <= 0) return 0;
        Map<UUID, Set<Long>> m = CORES.get(level.dimension());
        Set<Long> at = m == null ? null : m.get(ship);
        if (at == null) return 0;
        int left = fe;
        for (Long p : new ArrayList<>(at)) {
            if (left <= 0) break;
            if (!level.isLoaded(BlockPos.of(p)) || !(level.getBlockEntity(BlockPos.of(p)) instanceof PowerCoreBlockEntity c) || c.isRemoved()) continue;
            int n = c.energy().receiveEnergy(left, false);
            if (n > 0) { left -= n; c.setChanged(); }
        }
        return fe - left;
    }

    private boolean take(int fe) {
        if (EmpField.jammed((ServerLevel) level, worldPosition)) return false;
        if (!useExtraEnergy(fe)) return false;
        setChanged();
        return true;
    }

    private void register(ServerLevel sl) {
        UUID now = SableBridge.shipIdAt(sl, worldPosition);
        if (now == null ? ship == null : now.equals(ship)) {
            if (ship != null) CORES.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
            return;
        }
        unregister(sl);
        ship = now;
        if (ship != null) CORES.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
    }

    private void unregister(ServerLevel sl) {
        if (ship == null) return;
        Map<UUID, Set<Long>> m = CORES.get(sl.dimension());
        if (m != null) {
            Set<Long> s = m.get(ship);
            if (s != null) { s.remove(worldPosition.asLong()); if (s.isEmpty()) m.remove(ship); }
            if (m.isEmpty()) CORES.remove(sl.dimension());
        }
        ship = null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) unregister(sl);
        TankDamage.untrack(this);
        super.setRemoved();
    }

    private net.minecraft.world.phys.Vec3 centre() { double[] c = worldPoint(0.0, 0.0, 0.0); return new net.minecraft.world.phys.Vec3(c[0], c[1], c[2]); }

    private void damageTick(ServerLevel sl, long now) {
        if (!Config.Server.tankDamage()) return;
        if (now % 20 == (worldPosition.asLong() & 7)) {
            TankDamage.track(this);
            if (TankDamage.second(health, fuel, Balance.TANK_BURN_HP_CORE, Balance.TANK_LEAK_MB_CORE)) {
                setChanged();
                if (health.stage() == 1 || health.stage() == 2) TankDamage.leakFx(sl, centre().add(0.0, -0.4, 0.0), fuel.getFluid());
            }
        }
        if (TankDamage.burning(health.stage(), fuel.getFluid()) && now % 4 == 0) {
            SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
            net.minecraft.world.phys.Vec3 v = info == null ? net.minecraft.world.phys.Vec3.ZERO : info.linearVelocity().scale(1.0 / 20.0);
            TankDamage.burnFx(sl, TankDamage.fireId(sl, worldPosition), centre(), 0.6F, 0.6F, 0.6F, v, TankDamage.Burn.VOLATILE);
        }
        if (health.hp <= 0 && !blown) {
            blown = true;
            net.neoforged.neoforge.fluids.FluidStack in = fuel.getFluid().copy();
            float f = fuel.getCapacity() <= 0 ? 0.0F : in.getAmount() / (float) fuel.getCapacity();
            net.minecraft.world.phys.Vec3 at = centre();
            boolean onShip = ship != null;
            fuel.setFluid(net.neoforged.neoforge.fluids.FluidStack.EMPTY);
            TankDamage.untrack(this);
            unregister(sl);
            sl.removeBlock(worldPosition, false);
            TankDamage.explode(sl, at, in, f, TankDamage.Kind.CORE, onShip, null);
        }
    }

    @Override public Level tankLevel() { return level; }
    @Override public boolean tankGone() { return isRemoved() || blown; }

    @Override
    public net.minecraft.world.phys.AABB tankBox() { return net.minecraft.world.phys.AABB.ofSize(centre(), 1.0, 1.0, 1.0); }

    @Override
    public void tankHit(ServerLevel sl, double hp) {
        if (blown || !AirGate.on()) return;
        if (health.hit(hp)) {
            Wmd.LOGGER.info("[tank] power core at {}: hit, {} HP, stage {}", worldPosition.toShortString(), health.hp, health.stage());
            net.minecraft.world.phys.Vec3 c = centre();
            if (health.stage() == 2) sl.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_MECH, 0.8F);
        }
        setChanged();
    }

    @Override
    public boolean tankRepair(ServerLevel sl) {
        if (blown || !health.repair()) return false;
        setChanged();
        return true;
    }

    @Override
    public boolean tankAtRest(ServerLevel sl) {
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        return info == null || info.linearVelocity().length() < Balance.ENGINE_REPAIR_SPEED;
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { unregister(sl); apu = false; syncFlags(); return false; }
        long now = sl.getGameTime();
        damageTick(sl, now);
        if (blown) return false;
        if ((now + (worldPosition.asLong() & 15L)) % Balance.CORE_SHIP_TICKS == 0) register(sl);
        if (ship != null && (now + (worldPosition.asLong() & 15L)) % 20 == 0 && fuel.getSpace() > 0) {
            int got = FuelTankBlockEntity.drain(sl, ship, Math.min(fuel.getSpace(), Balance.TANK_FEED_MB));
            if (got > 0) { fuel.fill(new net.neoforged.neoforge.fluids.FluidStack(WmdFluids.JET_FUEL.source().get(), got), IFluidHandler.FluidAction.EXECUTE); setChanged(); }
        }
        IEnergyStorage e = energy();
        int stored = e.getEnergyStored(), cap = e.getMaxEnergyStored();
        if (!apu && stored < cap * Balance.CORE_APU_START && fuel.getFluidAmount() > 0) {
            apu = true;
            playOnce(sl, WmdSounds.CORE_APU_START.get(), Balance.SND_MECH, 1.0F);
        }
        if (apu) {
            if (stored >= cap || fuel.getFluidAmount() <= 0) apu = false;
            else {
                if (++apuTicks >= Balance.CORE_APU_TICKS_PER_MB) { apuTicks = 0; fuel.drain(1, IFluidHandler.FluidAction.EXECUTE); }
                e.receiveEnergy(Balance.CORE_APU_FE, false);
            }
        }
        syncFlags();
        return stored > 0 || apu;
    }

    private void syncFlags() {
        boolean d = level != null && level.getGameTime() - driveAt < 20;
        if (d != cDrive || apu != cApu) {
            cDrive = d;
            cApu = apu;
            copycatChanged();
        }
    }

    @Override protected float levelFraction() {
        IEnergyStorage e = energy();
        return e.getMaxEnergyStored() <= 0 ? 0.0F : e.getEnergyStored() / (float) e.getMaxEnergyStored();
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.SOUTH) ? energy() : null; }

    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.WEST) ? fillOnly(fuel) : null; }

    @Override public boolean driveFlange(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.EAST); }

    @Override public int driveDemand() {
        if (!AirGate.on()) return 0;
        IEnergyStorage e = energy();
        return e.getEnergyStored() < e.getMaxEnergyStored() ? Balance.CORE_DRIVE_FE : 0;
    }

    @Override public int driveIn(int fe, boolean simulate) {
        if (fe <= 0 || !AirGate.on()) return 0;
        int t = energy().receiveEnergy(fe, simulate);
        if (t > 0 && !simulate && level != null) driveAt = level.getGameTime();
        return t;
    }

    @Override public CopycatState copycat() { return copycat; }

    @Override public void copycatChanged() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    static void clientTickCore(Level level, BlockPos pos, BlockState state, PowerCoreBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        be.prevShaftAng = be.shaftAng;
        be.shaftSpeed += ((be.cDrive ? 0.6F : 0.0F) - be.shaftSpeed) * (be.cDrive ? 0.08F : 0.04F);
        if (be.shaftSpeed < 0.002F) be.shaftSpeed = 0.0F;
        be.shaftAng += be.shaftSpeed;
        if (be.shaftAng > 6.2831855F * 100) { be.shaftAng -= 6.2831855F * 100; be.prevShaftAng -= 6.2831855F * 100; }
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float charge = levelShown();
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        for (int k = 0; k < 3; k++) {
            final boolean lit = !dark && charge >= (k + 0.5F) / 3.0F;
            model.getBone("ring" + k).ifPresent(b -> b.setHidden(!lit));
        }
        final boolean low = !dark && charge < Balance.CORE_LOW && ((long) tt) % 20 < 10;
        model.getBone("low").ifPresent(b -> b.setHidden(!low));
        final float a = prevShaftAng + (shaftAng - prevShaftAng) * pt;
        model.getBone("shaft").ifPresent(b -> b.setRotX(a));
    }

    @Override protected List<FluidTank> tanks() { return List.of(fuel); }

    public int fuelMb() { return fuel.getFluidAmount(); }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.JET_FUEL.source().get()); }
    @Override protected List<String> labelKeys() { return List.of("fuel"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.core", String.format("%,d", Balance.CORE_BUFFER_FE)),
                Component.translatable(cApu ? "gui.wmd.info.core_apu_on" : "gui.wmd.info.core_apu", Balance.CORE_APU_FE * 20));
    }

    @Override
    protected String detail() {
        IEnergyStorage e = energy();
        return e.getEnergyStored() + " / " + e.getMaxEnergyStored() + " FE" + (apu ? ", APU running" : "") + (ship != null ? ", feeding ship " + ship : ", not on a ship");
    }

    @Override protected SoundEvent runSound() { return cApu ? WmdSounds.CORE_APU.get() : WmdSounds.CORE_HUM.get(); }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected float soundVolume() { return cApu ? Balance.SND_SMALL : Balance.SND_AMBIENT; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        copycat.save(tag, registries);
        tag.putBoolean("pcApu", apu);
        tag.putInt("pcApuT", apuTicks);
        health.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        cApu = tag.getBoolean("pcApu");
        cDrive = tag.getBoolean("pcDrive");
        if (client) return;
        apu = cApu;
        apuTicks = tag.getInt("pcApuT");
        health.load(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        copycat.putSync(tag);
        tag.putBoolean("pcApu", apu);
        tag.putBoolean("pcDrive", cDrive);
        return tag;
    }
}
