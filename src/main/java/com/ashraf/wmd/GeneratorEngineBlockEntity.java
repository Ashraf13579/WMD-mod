package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class GeneratorEngineBlockEntity extends BlockEntity implements GeoBlockEntity, EmpVulnerable, MachinePorts {

    public static final int CAPACITY = 10_000_000;
    public static final int FE_PER_SECOND = 15_000;
    public static final int FE_PER_TICK = FE_PER_SECOND / 20;
    public static final int PUSH_PER_TICK = 10_000;

    private final GeneratorEnergy energy = new GeneratorEnergy(CAPACITY, PUSH_PER_TICK);

    @Override
    public void empDrain() { energy.empty(); setChanged(); }
    private final FluidTank fuel = new FluidTank(Balance.GENERATOR_TANK_MB, fs -> fs.getFluid() == WmdFluids.DIESEL.source().get());
    private int feCredit;
    private boolean running;

    private static final RawAnimation ANIM_RUNNING = RawAnimation.begin().thenLoop("running");
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final double STACK_LX = 21.8 / 16.0, STACK_LY = 47.4 / 16.0, STACK_LZ = 15.5 / 16.0;

    private static Vec3 stackTop(BlockPos pos, Direction f) {
        double lx = STACK_LX, lz = STACK_LZ, dx, dz;
        switch (f) {
            case EAST -> { dx = -lz; dz = lx; }
            case SOUTH -> { dx = -lx; dz = -lz; }
            case WEST -> { dx = lz; dz = -lx; }
            default -> { dx = lx; dz = lz; }
        }
        return new Vec3(pos.getX() + 0.5 + dx, pos.getY() + STACK_LY, pos.getZ() + 0.5 + dz);
    }

    private final IFluidHandler fuelInput = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int t) { return fuel.getFluid(); }
        @Override public int getTankCapacity(int t) { return fuel.getCapacity(); }
        @Override public boolean isFluidValid(int t, FluidStack s) { return fuel.isFluidValid(s); }
        @Override public int fill(FluidStack s, FluidAction a) { int n = fuel.fill(s, a); if (n > 0 && a.execute()) setChanged(); return n; }
        @Override public FluidStack drain(FluidStack s, FluidAction a) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int max, FluidAction a) { return FluidStack.EMPTY; }
    };

    public static final int DATA_ENERGY_LOW = 0, DATA_ENERGY_HIGH = 1, DATA_FUEL = 2, DATA_FORMED = 3, DATA_RUN = 4, DATA_COUNT = 5;
    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> energy.getEnergyStored() & 0xFFFF;
                case DATA_ENERGY_HIGH -> (energy.getEnergyStored() >> 16) & 0xFFFF;
                case DATA_FUEL -> fuel.getFluidAmount();
                case DATA_FORMED -> 1;
                case DATA_RUN -> running ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {

        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public GeneratorEngineBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.DIESEL_GENERATOR_BE.get(), pos, state);
    }

    public IEnergyStorage getEnergy() { return energy; }
    public IFluidHandler getFuelInput() { return fuelInput; }
    public FluidTank getFuelTank() { return fuel; }
    public ContainerData getContainerData() { return containerData; }

    public boolean isFormed() { return true; }
    public boolean isRunning() { return running; }

    public Direction getFacing() {
        BlockState s = getBlockState();
        return s.hasProperty(MachineBlock.FACING) ? s.getValue(MachineBlock.FACING) : Direction.NORTH;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0,
                state -> state.setAndContinue(isRunning() ? ANIM_RUNNING : ANIM_IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, GeneratorEngineBlockEntity be) {
        if (!be.isRunning()) return;
        MachineLoop.ensure(be, WmdSounds.DIESEL_RUN.get(), be::isRunning, 1.0, 0.9F, true);
        RandomSource random = level.random;
        for (int k = 0; k < 2; k++) {
            if (random.nextInt(3) != 0) continue;
            Vec3 p = stackTop(pos, be.getFacing());
            level.addParticle(ParticleTypes.LARGE_SMOKE,
                    p.x + (random.nextDouble() - 0.5) * 0.08, p.y, p.z + (random.nextDouble() - 0.5) * 0.08,
                    (random.nextDouble() - 0.5) * 0.01, 0.05 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.01);
        }
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        Direction f = getFacing();
        return part.equals(GeneratorStructure.fuelPos(worldPosition, f, Machines.mount(getBlockState()))) && side == Machines.dirToWorld(f, Machines.mount(getBlockState()), Direction.NORTH) ? fuelInput : null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        Direction f = getFacing();
        if (!part.equals(GeneratorStructure.powerPos(worldPosition, f, Machines.mount(getBlockState())))) return null;
        return side == Machines.dirToWorld(f, Machines.mount(getBlockState()), Direction.NORTH) || side == Machines.dirToWorld(f, Machines.mount(getBlockState()), Direction.WEST) ? energy : null;
    }

    @Deprecated
    public static GeneratorEngineBlockEntity findOwner(Level level, BlockPos partPos) { return null; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorEngineBlockEntity be) {
        if (level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, pos)) {
            if (be.running) { be.running = false; be.setChanged(); be.syncToClients(); }
            return;
        }
        be.tick(level);
    }

    private void tick(Level level) {
        boolean changed = false;
        boolean hasRoom = energy.getEnergyStored() + FE_PER_TICK <= CAPACITY;

        boolean made = false;
        if (hasRoom) {
            if (feCredit < FE_PER_TICK && fuel.getFluidAmount() >= 1) {
                fuel.drain(1, IFluidHandler.FluidAction.EXECUTE);
                feCredit += Balance.GENERATOR_FE_PER_MB;
                changed = true;
            }
            if (feCredit >= FE_PER_TICK) {
                energy.generate(FE_PER_TICK);
                feCredit -= FE_PER_TICK;
                made = true;
                changed = true;
            }
        }

        if (energy.getEnergyStored() > 0) {
            Direction facing = getFacing();
            BlockPos port = GeneratorStructure.powerPos(worldPosition, facing, Machines.mount(getBlockState()));
            for (Direction dir : Direction.values()) {
                BlockPos target = port.relative(dir);
                if (GeneratorStructure.inside(Machines.toLocal(worldPosition, facing, Machines.mount(getBlockState()), target))) continue;
                IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, dir.getOpposite());
                if (storage == null || !storage.canReceive()) continue;
                int offer = energy.extractEnergy(PUSH_PER_TICK, true);
                int accepted = storage.receiveEnergy(offer, false);
                if (accepted > 0) {
                    energy.extractEnergy(accepted, false);
                    changed = true;
                }
            }
        }

        boolean nowRunning = made;
        if (nowRunning != running) {
            running = nowRunning;
            changed = true;
            syncToClients();
        }

        if (changed) setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energy.serializeNBT(registries));
        tag.put("fuelTank", fuel.writeToNBT(registries, new CompoundTag()));
        tag.putInt("feCredit", feCredit);
        tag.putBoolean("running", running);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) energy.deserializeNBT(registries, tag.get("energy"));
        if (tag.contains("fuelTank")) fuel.readFromNBT(registries, tag.getCompound("fuelTank"));
        feCredit = tag.getInt("feCredit");
        running = tag.getBoolean("running");
    }

    private static class GeneratorEnergy extends EnergyStorage {
        GeneratorEnergy(int capacity, int maxExtract) {
            super(capacity, 0, maxExtract);
        }

        void empty() { energy = 0; }

        void generate(int amount) {
            energy = Math.min(capacity, energy + amount);
        }
    }
}
