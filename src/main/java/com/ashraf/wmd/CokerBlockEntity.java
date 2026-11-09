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

public class CokerBlockEntity extends ItemProcessBlockEntity {

    private final FluidTank bitumen = new FluidTank(Balance.COKER_TANK_MB, fs -> fs.getFluid() == WmdFluids.BITUMEN.source().get());
    private final Slot coke = new Slot(64, true, s -> false).chute(1, 0, 1, Direction.EAST);
    private final Slot[] slots = { coke };
    private final List<FluidTank> tanks = List.of(bitumen, coke.gauge);
    private final IFluidHandler bitumenPort = fillOnly(bitumen);
    private final IItemHandler outH = outHandler(coke);
    private int progress;

    public CokerBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.COKER_BE.get(), pos, state, Balance.COKER_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (bitumen.getFluidAmount() < Balance.COKER_BITUMEN_MB) return fail(NO_INPUT);
            if (!coke.fits(wmd("petroleum_coke"), Balance.COKER_COKE_PER_CYCLE)) return fail(OUT_FULL);
            bitumen.drain(Balance.COKER_BITUMEN_MB, IFluidHandler.FluidAction.EXECUTE);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += Balance.COKER_BITUMEN_MB / Balance.COKER_CYCLE_TICKS;
        if (++progress > Balance.COKER_CYCLE_TICKS) {
            progress = 0;
            coke.add(wmd("petroleum_coke"), Balance.COKER_COKE_PER_CYCLE);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "petroleum_coke" }; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.COKER_CYCLE_TICKS; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.BITUMEN.source().get(), Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("bitumen", "coke"); }
    @Override protected int rateMbPerTick() { return Balance.COKER_BITUMEN_MB / Balance.COKER_CYCLE_TICKS; }
    @Override public String guiTankUnit(int t) { return t == 0 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.COKER_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.coker", Balance.COKER_BITUMEN_MB, Balance.COKER_COKE_PER_CYCLE, Balance.COKER_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.chute_hint"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.CK_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 1); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }

    @Override protected float levelFraction() { return progress / (float) Balance.COKER_CYCLE_TICKS; }

    static final double CYCLE_RAD = Math.PI * 200.0;

    public double cycle(float pt) {
        double c = spinAngle(pt) % CYCLE_RAD;
        if (c < 0) c += CYCLE_RAD;
        return c / CYCLE_RAD * 2.0;
    }

    public float run01(float pt) { return net.minecraft.util.Mth.clamp((spinAngle(1.0F) - spinAngle(0.0F)) / 0.9F, 0.0F, 1.0F); }

    private int shownHalf = -1;

    @Override
    protected void clientEffects(Level level) {
        var r = level.random;
        final double cyc = cycle(0.0F);
        final int half = cyc < 1.0 ? 0 : 1;
        final double p = cyc - half;
        if (shownHalf >= 0 && half != shownHalf) {
            double[] v = worldPoint(-0.375, 1.625, -0.1875);
            level.playLocalSound(v[0], v[1], v[2], WmdSounds.ST_DOCK_CLAMP.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_MECH, 1.15F, false);
        }
        shownHalf = half;
        final double dx = half == 0 ? -9.0 / 16.0 : 9.0 / 16.0;
        if (p >= CokerAnim.SWITCH_END && p < CokerAnim.STEM_DOWN && level.getGameTime() % 3 == 0) {
            double[] w = worldPoint(dx, 8.1, 1.125);
            level.addParticle(ParticleTypes.CLOUD, w[0] + (r.nextDouble() - 0.5) * 0.15, w[1], w[2] + (r.nextDouble() - 0.5) * 0.15, 0.0, 0.06 + r.nextDouble() * 0.03, 0.0);
        }
        if (CokerAnim.cutting(p) && level.getGameTime() % 2 == 0) {
            double[] w = worldPoint(dx + (r.nextDouble() - 0.5) * 0.3, 0.85, 1.125 + (r.nextDouble() - 0.5) * 0.3);
            level.addParticle(ParticleTypes.SMOKE, w[0], w[1], w[2], 0.0, -0.01, 0.0);
            if (r.nextInt(3) == 0) {
                double[] o = worldPoint(1.45, 0.6, 1.0);
                level.addParticle(ParticleTypes.SMOKE, o[0], o[1], o[2], 0.0, 0.01, 0.0);
            }
        }
        if (level.getGameTime() % 3 != 0) return;
        double[] w = worldPoint(-1.0, 3.3, -0.125);
        level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, w[0] + (r.nextDouble() - 0.5) * 0.2, w[1], w[2] + (r.nextDouble() - 0.5) * 0.2,
                (r.nextDouble() - 0.5) * 0.01, 0.05 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.01);
    }

    @Override
    protected List<double[]> vents() { return List.of(new double[] { 1.44, 8.75, 1.25 }); }

    @Override
    protected String detail() { return "bitumen " + mb(bitumen) + ", coke " + coke.count() + ", cycle " + progress * 100 / Balance.COKER_CYCLE_TICKS + "%"; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -1, 1, 1, Direction.WEST) ? bitumenPort : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 1, Direction.EAST) ? outH : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).fluid(-1, 1, 1, Direction.WEST, "bitumen", false).item(1, 0, 1, Direction.EAST, true).power(0, 0, 2, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
