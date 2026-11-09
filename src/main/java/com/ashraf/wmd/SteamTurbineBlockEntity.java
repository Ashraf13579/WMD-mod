package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class SteamTurbineBlockEntity extends ProcessUnitBlockEntity implements CcAnimated {

    public static final int X0 = -4, X1 = 4, Y0 = 0, Y1 = 3, Z0 = 0, Z1 = 3;

    private final FluidTank steam = new FluidTank(Balance.STEAM_TURBINE_TANK_MB, fs -> fs.getFluid() == WmdFluids.STEAM.source().get());
    private final FluidTank spent = new FluidTank(Balance.STEAM_TURBINE_TANK_MB, fs -> fs.getFluid() == WmdFluids.SPENT_STEAM.source().get());
    private final List<FluidTank> tanks = List.of(steam, spent);
    private final IFluidHandler steamIn = fillOnly(steam), spentOut = drainOnly(spent);
    private final GenBuffer out = new GenBuffer(Balance.STEAM_TURBINE_BUFFER_FE, Balance.STEAM_TURBINE_OUT_PER_TICK, this::setChanged);
    private int feNow;

    public SteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.STEAM_TURBINE_BE.get(), pos, state, 0);
    }

    @Override
    protected boolean work() {
        reason = 0;
        feNow = 0;
        int use = Math.min(Balance.STEAM_TURBINE_MB, steam.getFluidAmount());
        if (use <= 0) { reason = 4; return false; }
        use = Math.min(use, spent.getCapacity() - spent.getFluidAmount());
        use = Math.min(use, (out.getMaxEnergyStored() - out.stored) / Balance.STEAM_TURBINE_FE_PER_MB);
        if (use <= 0) { reason = 3; return false; }
        steam.drain(use, IFluidHandler.FluidAction.EXECUTE);
        spent.fill(new FluidStack(WmdFluids.SPENT_STEAM.source().get(), use), IFluidHandler.FluidAction.EXECUTE);
        feNow = use * Balance.STEAM_TURBINE_FE_PER_MB;
        out.stored += feNow;
        flowAcc += use;
        if (level.getGameTime() % 100 == 0) setChanged();
        return true;
    }

    static final float SPD_FULL = 1.15F, SPD_TURN = Mth.TWO_PI / 160.0F;

    static final float SPD_EASE = 0.04F, SPD_UP = 0.009F, SPD_DOWN = 0.0048F;

    static final double G_PA = 0.9, G_LA = 5.0, G_LK = 5.0, G_PS = 1.85, G_PIV = 48.0, TH0 = 20.0, TH_RUN = 55.0;
    static final float STEM_LIFT = 4.0F;
    static final float TG_RATIO = 2.5F;

    static final float GOV_SIGN = 1.0F;

    private float shaft, shaftSpeed, gov, tgear, valve;
    private int runTicks;

    static double[] govPose(double th) {
        double t = Math.toRadians(th);
        double ax = G_PA + G_LA * Math.sin(t), ay = G_PIV - G_LA * Math.cos(t);
        double dx = ax - G_PS;
        double ys = ay - Math.sqrt(G_LK * G_LK - dx * dx);
        return new double[] { ys, Math.toDegrees(Math.atan2(dx, ay - ys)) };
    }
    private static final double[] GOV_REST = govPose(TH0);

    boolean hasSteamShown() { return levelShown() > 0.01F; }

    boolean readyShown() { return !isRunning() && hasSteamShown(); }

    public static void clientTickTurbine(Level level, BlockPos pos, BlockState state, SteamTurbineBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        boolean run = be.isRunning(), ready = be.readyShown();
        be.runTicks = run ? be.runTicks + 1 : 0;
        float target = run ? 0.25F + 0.9F * be.flowFraction() : ready ? SPD_TURN : 0.0F;
        be.shaftSpeed += Mth.clamp((target - be.shaftSpeed) * SPD_EASE, -SPD_DOWN, SPD_UP);
        if (Math.abs(target - be.shaftSpeed) < 0.0005F) be.shaftSpeed = target;
        be.shaft = (be.shaft + be.shaftSpeed) % (Mth.TWO_PI * 64);
        be.gov = (be.gov + be.shaftSpeed * 0.3F) % (Mth.TWO_PI * 64);
        if (ready && be.shaftSpeed <= SPD_TURN * 1.5F) be.tgear = (be.tgear - be.shaftSpeed * TG_RATIO) % (Mth.TWO_PI * 64);

        float vt = run ? (be.runTicks < 12 ? 1.0F : 0.35F + 0.65F * be.flowFraction()) : ready ? 0.08F : 0.0F;
        be.valve += (vt - be.valve) * (vt < be.valve && !run ? 0.35F : 0.12F);
    }

    @Override
    public float spinAngle(float partialTick) { return shaft + shaftSpeed * partialTick; }

    float rotor01() { return Mth.clamp(shaftSpeed / SPD_FULL, 0.0F, 1.0F); }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float g = rotor01();
        float th = (float) (TH0 + (TH_RUN - TH0) * g * g * (3.0F - 2.0F * g));
        double[] gp = govPose(th);
        float arm = GOV_SIGN * (float) Math.toRadians(th - TH0), link = GOV_SIGN * (float) Math.toRadians(gp[1] - GOV_REST[1]);
        model.getBone("gov").ifPresent(b -> b.setRotY(gov + shaftSpeed * 0.3F * partialTick));
        model.getBone("govarm0").ifPresent(b -> b.setRotZ(arm));
        model.getBone("govarm1").ifPresent(b -> b.setRotZ(-arm));
        model.getBone("govsl").ifPresent(b -> b.setPosY((float) (gp[0] - GOV_REST[0])));
        model.getBone("govlk0").ifPresent(b -> b.setRotZ(-link));
        model.getBone("govlk1").ifPresent(b -> b.setRotZ(link));
        float lift = valve * STEM_LIFT;
        model.getBone("stem0").ifPresent(b -> b.setPosY(lift));
        model.getBone("stem1").ifPresent(b -> b.setPosY(lift));
        model.getBone("tgear").ifPresent(b -> b.setRotX(tgear));
        boolean dark = ClientHemp.darkAt(getBlockPos().getX() + 0.5, getBlockPos().getZ() + 0.5);
        boolean run = isRunning(), ready = readyShown(), blink = ((long) tt / 20) % 2 == 0;
        model.getBone("lamp_run").ifPresent(b -> b.setHidden(dark || !run));
        model.getBone("lamp_ready").ifPresent(b -> b.setHidden(dark || !ready));
        model.getBone("lamp_fault").ifPresent(b -> b.setHidden(dark || run || ready || !blink));
    }

    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.STEAM.source().get(), WmdFluids.SPENT_STEAM.source().get()); }
    @Override protected List<String> labelKeys() { return List.of("steam", "spent_steam"); }
    @Override protected int rateMbPerTick() { return Balance.STEAM_TURBINE_MB; }
    @Override public IEnergyStorage guiEnergy() { return out; }
    @Override public void empDrain() { out.stored = 0; setChanged(); }

    @Override
    public int guiStatus() {
        if (running) return 1;
        return reason;
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.steam_turbine", Balance.STEAM_TURBINE_MB, String.format("%,d", Balance.STEAM_TURBINE_MB * Balance.STEAM_TURBINE_FE_PER_MB * 20)),
                Component.translatable("gui.wmd.info.steam_turbine_out"));
    }

    @Override
    protected String detail() { return "steam " + mb(steam) + ", spent steam " + mb(spent) + ", making " + feNow + " FE/t, stored " + out.stored + " FE"; }

    @Override protected SoundEvent runSound() { return WmdSounds.STT_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 2); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY * (isRunning() ? 1.0F : Math.max(0.05F, rotor01())); }
    @Override protected float soundPitch() { return 0.7F + 0.3F * rotor01(); }
    @Override protected boolean soundAlive() { return isRunning() || rotor01() > 0.12F; }

    @Override protected float levelFraction() {
        int mb = steam.getFluidAmount();
        return mb <= 0 ? 0.0F : Math.max(1.0F / 32.0F, mb / (float) Balance.STEAM_TURBINE_TANK_MB);
    }

    @Override protected List<double[]> vents() { return List.of(new double[] { -2.22, 1.5, 1.5 }, new double[] { 1.9, 1.5, 1.5 }); }

    @Override
    protected void runStarted(Level level) {
        playOnce(level, WmdSounds.BAY_CLAMP.get(), Balance.SND_MECH, 0.75F);
        for (double z : new double[] { 1.0, 2.0 }) puff(level, -3.625, 2.3, z, 6);
    }

    @Override
    protected void runStopped(Level level) {
        playOnce(level, WmdSounds.NR_STEAM.get(), Balance.SND_RELEASE, 1.1F);
        puff(level, -2.22, 1.5, 1.5, 5);
        puff(level, -3.3, 1.5, 1.5, 5);
    }

    private void puff(Level level, double lx, double ly, double lz, int n) {
        var r = level.random;
        double[] w = worldPoint(lx, ly, lz);
        for (int i = 0; i < n; i++)
            level.addParticle(ParticleTypes.CLOUD, w[0] + (r.nextDouble() - 0.5) * 0.25, w[1], w[2] + (r.nextDouble() - 0.5) * 0.25,
                    (r.nextDouble() - 0.5) * 0.04, 0.06 + r.nextDouble() * 0.05, (r.nextDouble() - 0.5) * 0.04);
    }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime();
        long key = getBlockPos().asLong() ^ 0x5354_0000_0000_0000L;
        double[] lamp = worldPoint(0.22, 0.68, -0.52), pit = worldPoint(-0.25, 1.4, 1.0);
        VeilBridge.machineGlow(key, lamp[0], lamp[1], lamp[2], 0.3F, 1.0F, 0.45F, 0.8F, 0.5F, t);
        VeilBridge.machineGlow(key + 1, pit[0], pit[1], pit[2], 1.0F, 0.75F, 0.45F, 2.5F, 0.7F, t);
        if (t % 40 == 0) {
            var r = level.random;
            double[] w = worldPoint(-4.6, 0.5, 1.0 + r.nextDouble());
            level.addParticle(ParticleTypes.CLOUD, w[0], w[1], w[2], 0, 0.02, 0);
        }
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (at(part, side, -4, 0, 1, Direction.WEST) || at(part, side, -4, 0, 2, Direction.WEST)) return steamIn;
        if (at(part, side, 4, 0, 1, Direction.EAST) || at(part, side, 4, 0, 2, Direction.EAST)) return spentOut;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 3, Direction.SOUTH) ? out : null; }

    private static final PortMap PORT_MAP = new PortMap(-4, 4, 4)
            .fluid(-4, 0, 1, Direction.WEST, "steam", false).fluid(-4, 0, 2, Direction.WEST, "steam", false)
            .fluid(4, 0, 1, Direction.EAST, "spent_steam", true).fluid(4, 0, 2, Direction.EAST, "spent_steam", true)
            .power(0, 0, 3, Direction.SOUTH, true);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stored", out.stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        out.stored = Mth.clamp(tag.getInt("stored"), 0, out.getMaxEnergyStored());
    }
}
