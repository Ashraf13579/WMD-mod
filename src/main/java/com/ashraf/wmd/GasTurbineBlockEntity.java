package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class GasTurbineBlockEntity extends ItemProcessBlockEntity {

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    private final FluidTank fuel = new FluidTank(Balance.TURBINE_TANK_MB, fs -> fs.getFluid() == WmdFluids.LPG.source().get() || fs.getFluid() == WmdFluids.NAPHTHA.source().get());
    private final IFluidHandler fuelPort = fillOnly(fuel);
    private final Slot blades = new Slot(4, false, s -> s.is(wmd("turbine_blades")));
    private final Slot[] slots = { blades };
    private final List<FluidTank> tanks = List.of(fuel, blades.gauge);
    private final IItemHandler bladeHatch = inHandler(blades);
    private final GenBuffer out = new GenBuffer(Balance.TURBINE_BUFFER_FE, Balance.TURBINE_OUT_PER_TICK, this::setChanged);

    private int life, credit;

    protected static final int NO_BLADES = 11;

    private static final double[] MOUTH = { -2.5, 1.25, 1.0 };
    private static final double INTAKE_DEPTH = 3.0, INTAKE_KILL = 0.9;

    private static final double[] EXHAUST = { 0.125, 3.0, 1.0 };

    public GasTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.GAS_TURBINE_BE.get(), pos, state, 0);
    }

    @Override
    protected boolean process() {
        if (life <= 0) {
            if (blades.count() < 1) return fail(NO_BLADES);
            blades.take(1);
            life = Balance.TURBINE_BLADE_LIFE;
            setChanged();
        }
        if (!out.hasRoom(Balance.TURBINE_FE_PER_TICK)) return fail(OUT_FULL);
        if (credit < Balance.TURBINE_FE_PER_TICK) {
            if (fuel.getFluidAmount() < 1) return fail(NO_INPUT);
            boolean lpg = fuel.getFluid().getFluid() == WmdFluids.LPG.source().get();
            fuel.drain(1, IFluidHandler.FluidAction.EXECUTE);
            credit += lpg ? Balance.TURBINE_FE_PER_MB_LPG : Balance.TURBINE_FE_PER_MB_NAPHTHA;
            flowAcc += 1;
        }
        credit -= Balance.TURBINE_FE_PER_TICK;
        out.stored += Balance.TURBINE_FE_PER_TICK;
        life--;
        if (life % 200 == 0) setChanged();
        suck();
        return true;
    }

    private net.minecraft.world.phys.AABB intakeBox;
    private net.minecraft.world.phys.Vec3 mouth;

    private void suck() {
        if (level == null) return;
        if (intakeBox == null || SableBridge.isOnShip(level, worldPosition)) {
            double[] m = worldPoint(MOUTH[0], MOUTH[1], MOUTH[2]);
            double[] a = worldPoint(MOUTH[0] - INTAKE_DEPTH, MOUTH[1] - 1.5, MOUTH[2] - 1.5), b = worldPoint(MOUTH[0], MOUTH[1] + 1.5, MOUTH[2] + 1.5);
            double[] c = worldPoint(MOUTH[0] - INTAKE_DEPTH, MOUTH[1] + 1.5, MOUTH[2] + 1.5), d = worldPoint(MOUTH[0], MOUTH[1] - 1.5, MOUTH[2] - 1.5);
            mouth = new net.minecraft.world.phys.Vec3(m[0], m[1], m[2]);
            intakeBox = new net.minecraft.world.phys.AABB(a[0], a[1], a[2], b[0], b[1], b[2]).minmax(new net.minecraft.world.phys.AABB(c[0], c[1], c[2], d[0], d[1], d[2]));
        }
        for (net.minecraft.world.entity.LivingEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, intakeBox)) {
            if (e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) continue;
            net.minecraft.world.phys.Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            net.minecraft.world.phys.Vec3 to = mouth.subtract(c);
            double d = to.length();
            if (d <= INTAKE_KILL) {
                e.hurt(new net.minecraft.world.damagesource.DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(INTAKE_DAMAGE)), 1000.0F);
                continue;
            }
            double pull = 0.06 + 0.14 * Math.max(0.0, 1.0 - d / (INTAKE_DEPTH + 1.0));
            e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(to.normalize().scale(pull)));
            e.hurtMarked = true;
            e.fallDistance = 0.0F;
        }
    }

    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> INTAKE_DAMAGE =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "turbine_intake"));

    public int lifeFraction1000() { return life <= 0 ? 0 : Math.min(1000, (int) (life * 1000L / Balance.TURBINE_BLADE_LIFE)); }

    @Override protected String[] iconIds() { return new String[] { "turbine_blades" }; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return life <= 0 ? -1.0F : lifeFraction1000() / 1000.0F; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.LPG.source().get(), Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("fuel", "blades"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected int inputTanks() { return 2; }
    @Override public String guiTankUnit(int t) { return t == 0 ? "mB" : "items"; }
    @Override public IEnergyStorage guiEnergy() { return out; }
    @Override public void empDrain() { out.stored = 0; setChanged(); }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.turbine", Balance.TURBINE_FE_PER_TICK * 20, Balance.TURBINE_FE_PER_MB_LPG, Balance.TURBINE_FE_PER_MB_NAPHTHA),
                Component.translatable("gui.wmd.info.turbine_life", Balance.TURBINE_BLADE_LIFE / 1200),
                Component.translatable("gui.wmd.info.turbine_danger"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.GT_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(-2, 1, 1); }
    @Override protected float soundVolume() { return 0.7F; }
    @Override protected float levelFraction() { return fuel.getFluidAmount() / (float) Balance.TURBINE_TANK_MB; }

    @Override
    protected void clientEffects(Level level) {
        var r = level.random;

        long t = level.getGameTime(), key = getBlockPos().asLong();
        double[] comb = worldPoint(-0.56, 1.25, 1.0), stack = worldPoint(EXHAUST[0], EXHAUST[1], EXHAUST[2]);
        VeilBridge.machineGlow(key, comb[0], comb[1], comb[2], 1.0F, 0.45F, 0.15F, 3.0F, 1.2F, t);
        VeilBridge.machineGlow(key * 31 + 7, stack[0], stack[1], stack[2], 1.0F, 0.5F, 0.2F, 4.0F, 1.0F + 0.25F * (float) Math.sin(t * 0.15), t);
        double[] m = worldPoint(MOUTH[0], MOUTH[1], MOUTH[2]);
        for (int i = 0; i < 2; i++) {
            double[] p = worldPoint(MOUTH[0] - 0.6 - r.nextDouble() * INTAKE_DEPTH, MOUTH[1] + (r.nextDouble() - 0.5) * 2.4, MOUTH[2] + (r.nextDouble() - 0.5) * 2.4);
            double k = 0.12 + r.nextDouble() * 0.06;
            level.addParticle(ParticleTypes.CLOUD, p[0], p[1], p[2], (m[0] - p[0]) * k, (m[1] - p[1]) * k, (m[2] - p[2]) * k);
        }
        if (level.getGameTime() % 3 != 0) return;
        double[] w = worldPoint(EXHAUST[0], EXHAUST[1], EXHAUST[2]);
        level.addParticle(ParticleTypes.SMOKE, w[0] + (r.nextDouble() - 0.5) * 0.3, w[1], w[2] + (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.01, 0.08, (r.nextDouble() - 0.5) * 0.01);
    }

    @Override
    protected String detail() { return "fuel " + mb(fuel) + ", stored " + out.stored + " FE, blades " + (life <= 0 ? "none" : lifeFraction1000() / 10 + "%"); }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -1, 0, 2, Direction.SOUTH) ? fuelPort : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, 0, 1, 0, Direction.NORTH) ? bladeHatch : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 2, 0, 1, Direction.EAST) ? out : null; }

    @Override
    protected void saveExtra(CompoundTag tag) { tag.putInt("life", life); tag.putInt("credit", credit); tag.putInt("stored", out.stored); }

    @Override
    protected void loadExtra(CompoundTag tag) { life = tag.getInt("life"); credit = tag.getInt("credit"); out.stored = tag.getInt("stored"); }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 3).fluid(-1, 0, 2, Direction.SOUTH, "fuel", false).item(0, 1, 0, Direction.NORTH, false).power(2, 0, 1, Direction.EAST, true);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
