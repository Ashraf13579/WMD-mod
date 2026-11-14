package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class StorageTankBlockEntity extends BlockEntity implements GeoBlockEntity, MachinePorts, GuiMachine, TankDamage.Holder {

    private final FluidTank tank = new FluidTank(Balance.STORAGE_TANK_MB);
    private int syncedMb = -1;

    private final TankDamage.Health health = new TankDamage.Health(Balance.TANK_HP_STORAGE);
    private int syncedHp = -1;
    private boolean blown;

    Object soundHandle;
    private long fillingUntil, drainingUntil;
    private boolean loadedOnce;

    private float shownLevel = -1;

    private float swirl, swirlPrev, swirlSpeed = 0.003F, pour, pourPrev, drain, drainPrev;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private final IFluidHandler input = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int t) { return tank.getFluid(); }
        @Override public int getTankCapacity(int t) { return tank.getCapacity(); }
        @Override public boolean isFluidValid(int t, FluidStack s) { return tank.isFluidValid(s); }
        @Override public int fill(FluidStack s, FluidAction a) { return tank.fill(s, a); }
        @Override public FluidStack drain(FluidStack s, FluidAction a) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int max, FluidAction a) { return FluidStack.EMPTY; }
    };

    private final IFluidHandler output = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int t) { return tank.getFluid(); }
        @Override public int getTankCapacity(int t) { return tank.getCapacity(); }
        @Override public boolean isFluidValid(int t, FluidStack s) { return false; }
        @Override public int fill(FluidStack s, FluidAction a) { return 0; }
        @Override public FluidStack drain(FluidStack s, FluidAction a) { return tank.drain(s, a); }
        @Override public FluidStack drain(int max, FluidAction a) { return tank.drain(max, a); }
    };

    public StorageTankBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.STORAGE_TANK_BE.get(), pos, state);
    }

    public int getStoredMb() { return tank.getFluidAmount(); }

    public Component fluidName() {
        return tank.isEmpty() ? Component.translatable("gui.wmd.label.empty") : tank.getFluid().getHoverName();
    }

    public FluidStack fluidStack() { return tank.getFluid(); }

    public int fluidIndex() {
        if (tank.isEmpty()) return -1;
        Fluid f = tank.getFluid().getFluid();
        for (int i = 0; i < WmdFluids.ALL.size(); i++) if (WmdFluids.ALL.get(i).source().get() == f) return i;
        return -1;
    }

    @Override public Component guiTitle() { return Component.translatable("block.wmd.storage_tank"); }
    @Override public List<FluidTank> guiTanks() { return List.of(tank); }
    @Override public List<Fluid> guiTankFluids() { return List.of(tank.getFluid().getFluid()); }
    @Override public List<Component> guiTankLabels() { return List.of(fluidName()); }
    @Override public boolean guiRunning() { return false; }
    @Override public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.capacity", Balance.STORAGE_TANK_MB / 1000),
                Component.translatable("gui.wmd.info.tank_sides"));
    }

    public float fill() { return (float) tank.getFluidAmount() / tank.getCapacity(); }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        BlockPos l = Machines.toLocal(worldPosition, facing, Machines.mount(getBlockState()), part);
        if (l.getY() != 0 || l.getZ() != 2) return null;
        if (l.getX() == -2 && side == Machines.dirToWorld(facing, Machines.mount(getBlockState()), Direction.WEST)) return input;
        if (l.getX() == 2 && side == Machines.dirToWorld(facing, Machines.mount(getBlockState()), Direction.EAST)) return output;
        return null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StorageTankBlockEntity be) {
        if (level instanceof ServerLevel sl) be.damageTick(sl);
        if (be.isRemoved()) return;
        int now = be.tank.getFluidAmount();
        if ((now != be.syncedMb || be.health.hp != be.syncedHp) && level.getGameTime() % 10 == 0) {
            be.syncedMb = now;
            be.syncedHp = be.health.hp;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void damageTick(ServerLevel sl) {
        if (!Config.Server.tankDamage()) return;
        long t = sl.getGameTime();
        if (t % 20 == (worldPosition.asLong() & 7)) {
            TankDamage.track(this);
            if (TankDamage.second(health, tank, Balance.TANK_BURN_HP_STORAGE, Balance.TANK_LEAK_MB_STORAGE)) setChanged();
        }
        if (TankDamage.burning(health.stage(), tank.getFluid()) && t % 5 == 0) {
            AABB b = tankBox();
            TankDamage.burnFx(sl, TankDamage.fireId(sl, worldPosition), new Vec3(b.getCenter().x, b.minY + 5.0, b.getCenter().z), 2.0F, 3.0F, 2.0F, Vec3.ZERO, TankDamage.of(tank.getFluid()));
        }
        if (health.hp <= 0 && !blown) {
            blown = true;
            FluidStack in = tank.getFluid().copy();
            float f = fill();
            tank.setFluid(FluidStack.EMPTY);
            AABB b = tankBox();
            TankDamage.untrack(this);
            shatter(sl, b);
            sl.removeBlock(worldPosition, false);
            TankDamage.explode(sl, new Vec3(b.getCenter().x, b.minY + 1.5, b.getCenter().z), in, f, TankDamage.Kind.STORAGE, false, null);
        }
    }

    public int damageStage() { return health.stage(); }

    public float damageFraction() { return 1.0F - health.hp / (float) health.max; }

    private void shatter(ServerLevel sl, AABB b) {
        net.minecraft.core.particles.BlockParticleOption glass = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
        Vec3 c = b.getCenter();
        for (int i = 0; i < 6; i++) sl.sendParticles(glass, c.x, b.minY + 1.0 + i * 1.0, c.z, 40, 1.4, 0.4, 1.4, 0.35);
        sl.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_RELEASE, 0.7F);
    }

    @Override public Level tankLevel() { return level; }
    @Override public boolean tankGone() { return isRemoved() || blown; }

    @Override
    public AABB tankBox() {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        Direction mount = Machines.mount(getBlockState());
        BlockPos a = Machines.toWorld(worldPosition, facing, mount, -2, 0, 0), c = Machines.toWorld(worldPosition, facing, mount, 2, 7, 4);
        AABB box = new AABB(a).minmax(new AABB(c));
        if (level == null || level.isClientSide) return box;
        Vec3 lo = SableBridge.toWorld(level, new Vec3(box.minX, box.minY, box.minZ)), hi = SableBridge.toWorld(level, new Vec3(box.maxX, box.maxY, box.maxZ));
        return new AABB(lo, hi);
    }

    @Override
    public void tankHit(ServerLevel sl, double hp) {
        if (blown) return;
        boolean stage = health.hit(hp);
        setChanged();
        if (stage) {
            Wmd.LOGGER.info("[tank] storage tank at {}: hit, {} HP, stage {}", worldPosition.toShortString(), health.hp, health.stage());
            AABB b = tankBox();
            sl.playSound(null, b.getCenter().x, b.getCenter().y, b.getCenter().z, health.stage() == 2 ? net.minecraft.sounds.SoundEvents.FIRECHARGE_USE
                    : net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_MECH, 0.8F);
        }
    }

    @Override
    public boolean tankRepair(ServerLevel sl) {
        if (blown || !health.repair()) return false;
        setChanged();
        return true;
    }

    @Override public boolean tankAtRest(ServerLevel sl) { return true; }

    @Override
    public void setRemoved() {
        TankDamage.untrack(this);
        super.setRemoved();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, StorageTankBlockEntity be) {
        float target = be.fill();
        be.cTicks++;
        if (be.shownLevel < 0) be.shownLevel = target;
        be.shownLevel += (target - be.shownLevel) * 0.1F;
        boolean filling = be.isFilling(), draining = be.isDraining();
        float want = draining ? 0.030F : filling ? 0.020F : 0.003F;
        be.swirlSpeed += (want - be.swirlSpeed) * 0.05F;
        be.swirlPrev = be.swirl;
        be.swirl += be.swirlSpeed;
        if (be.swirl > 1000F) { be.swirl -= 2 * Math.PI * 100; be.swirlPrev -= 2 * Math.PI * 100; }
        be.pourPrev = be.pour;
        be.pour += ((filling && target < 0.995F ? 1F : 0F) - be.pour) * 0.15F;
        be.drainPrev = be.drain;
        be.drain += ((draining && target > 0.01F ? 1F : 0F) - be.drain) * 0.06F;
        if (be.isFilling()) StorageTankClient.ensureSound(be);
    }

    public float swirl(float partial) { return swirlPrev + (swirl - swirlPrev) * partial; }
    public float pour(float partial) { return pourPrev + (pour - pourPrev) * partial; }
    public float drain(float partial) { return drainPrev + (drain - drainPrev) * partial; }

    public boolean isDraining() { return level != null && level.getGameTime() < drainingUntil; }

    public BlockPos centre() {
        return Machines.toWorld(worldPosition, getBlockState().getValue(MachineBlock.FACING), Machines.mount(getBlockState()), 0, 3, 2);
    }

    public boolean isFilling() { return level != null && level.getGameTime() < fillingUntil; }

    public float shownLevel() { return shownLevel < 0 ? fill() : shownLevel; }

    private int cTicks;
    private long cLogAt = Long.MIN_VALUE;

    void logLiquid(FluidStack fs, float lvl) {
        long now = level == null ? 0L : level.getGameTime();
        if (now - cLogAt < 200L) return;
        cLogAt = now;
        Wmd.LOGGER.info("[tank-fx] {}: {} {} mB, fill {}, drawn level {}, client ticks {}, swirl {}, pour {}, filling {}, stage {}", worldPosition.toShortString(),
                fs.isEmpty() ? "empty" : fs.getHoverName().getString(), fs.getAmount(), String.format("%.3f", fill()), String.format("%.3f", lvl), cTicks,
                String.format("%.2f", swirl), String.format("%.2f", pour), isFilling(), damageStage());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.writeToNBT(registries, tag);
        health.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int before = tank.getFluidAmount();
        tank.readFromNBT(registries, tag);
        health.load(tag);

        if (level != null && level.isClientSide) {
            if (loadedOnce && tank.getFluidAmount() > before) fillingUntil = level.getGameTime() + 40;
            if (loadedOnce && tank.getFluidAmount() < before) drainingUntil = level.getGameTime() + 40;
            loadedOnce = true;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tank.writeToNBT(registries, tag);
        health.save(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
