package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class RoasterBlockEntity extends ItemProcessBlockEntity implements CcAnimated {

    static final int DRUM_TURN_TICKS = 200;
    static final float DRUM_FULL = Mth.TWO_PI / DRUM_TURN_TICKS, DRUM_UP = DRUM_FULL / 100.0F, DRUM_DOWN = DRUM_FULL / 180.0F, DRUM_EASE = 0.06F;

    static final float ROLL_RATIO = 8.2F / 2.3F, PIN_RATIO = 30.0F / 8.0F, COUPL_RATIO = 5.0F;

    static final float LUMP_FALL = 3.6F, HEAP_DROP = 2.6F;
    static final float HEAT_UP = 1.0F / 160.0F, HEAT_DOWN = 1.0F / 240.0F;
    static final int PILOT_TICKS = 10;
    static final float FLAME_GROW = 1.0F / 30.0F;
    private float drum, drumSpeed, heat, flame;
    private int runTicks;

    private final FluidTank lpg = new FluidTank(Balance.ROAST_TANK_MB, fs -> fs.getFluid() == WmdFluids.LPG.source().get());
    private final Slot conc = new Slot(64, false, s -> s.is(wmd("tungsten_concentrate")));
    private final Slot oxide = new Slot(64, true, s -> false).chute(2, 0, 0, Direction.EAST);
    private final Slot[] slots = { conc, oxide };
    private final List<FluidTank> tanks = List.of(lpg, conc.gauge, oxide.gauge);
    private final IFluidHandler lpgIn = fillOnly(lpg);
    private final IItemHandler in = inHandler(conc), outH = outHandler(oxide);
    private int progress;

    public RoasterBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.ROASTER_BE.get(), pos, state, Balance.ROAST_FE_PER_TICK);
    }

    @Override
    protected boolean process() {
        if (progress == 0) {
            if (conc.count() < Balance.ROAST_CONC || lpg.getFluidAmount() < Balance.ROAST_LPG_MB) return fail(NO_INPUT);
            if (!oxide.fits(wmd("tungsten_oxide"), Balance.ROAST_OXIDE)) return fail(OUT_FULL);
            conc.take(Balance.ROAST_CONC);
            lpg.drain(Balance.ROAST_LPG_MB, IFluidHandler.FluidAction.EXECUTE);
            progress = 1;
            setChanged();
            return true;
        }
        flowAcc += 1;
        if (++progress > Balance.ROAST_CYCLE_TICKS) {
            progress = 0;
            oxide.add(wmd("tungsten_oxide"), Balance.ROAST_OXIDE);
            setChanged();
        }
        return true;
    }

    @Override protected String[] iconIds() { return new String[] { "tungsten_concentrate", "tungsten_oxide" }; }
    @Override
    public ItemStack guiPreview(List<ItemStack> in) {
        return !in.isEmpty() && in.get(0).is(wmd("tungsten_concentrate")) ? new ItemStack(wmd("tungsten_oxide"), Balance.ROAST_OXIDE) : ItemStack.EMPTY;
    }

    @Override protected float progress01() { return progress == 0 ? 0.0F : progress / (float) Balance.ROAST_CYCLE_TICKS; }
    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(WmdFluids.LPG.source().get(), Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("lpg", "concentrate", "oxide"); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected int inputTanks() { return 2; }
    @Override public String guiTankUnit(int t) { return t == 0 ? "mB" : "items"; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.ROAST_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.roaster", Balance.ROAST_CONC, Balance.ROAST_LPG_MB, Balance.ROAST_OXIDE, Balance.ROAST_CYCLE_TICKS / 20),
                Component.translatable("gui.wmd.info.items_hint"));
    }

    public static void clientTickRoaster(Level level, BlockPos pos, BlockState state, RoasterBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        boolean run = be.isRunning();
        be.runTicks = run ? be.runTicks + 1 : 0;
        float target = run ? DRUM_FULL : 0.0F;
        be.drumSpeed += Mth.clamp((target - be.drumSpeed) * DRUM_EASE, -DRUM_DOWN, DRUM_UP);
        if (Math.abs(target - be.drumSpeed) < 0.0002F) be.drumSpeed = target;
        be.drum = (be.drum + be.drumSpeed) % (Mth.TWO_PI * 64);
        be.heat = Mth.clamp(be.heat + (run ? HEAT_UP : -HEAT_DOWN), 0.0F, 1.0F);
        be.flame = run ? (be.runTicks < PILOT_TICKS ? 0.15F : Math.min(1.0F, be.flame + FLAME_GROW)) : Math.max(0.0F, be.flame - 0.25F);
    }

    @Override
    public float spinAngle(float partialTick) { return drum + drumSpeed * partialTick; }

    float drum01() { return Mth.clamp(drumSpeed / DRUM_FULL, 0.0F, 1.0F); }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float a = spinAngle(partialTick);
        for (int i = 0; i < 4; i++) model.getBone("kroller" + i).ifPresent(b -> b.setRotX(-a * ROLL_RATIO));
        model.getBone("kpinion").ifPresent(b -> b.setRotX(-a * PIN_RATIO));
        model.getBone("kcoupl").ifPresent(b -> b.setRotX(-a * PIN_RATIO * COUPL_RATIO));
        boolean run = isRunning();
        boolean dark = ClientHemp.darkAt(getBlockPos().getX() + 0.5, getBlockPos().getZ() + 0.5);

        float f = flame;
        float flick = 0.86F + 0.09F * (float) Math.sin(tt * 0.9) + 0.05F * (float) Math.sin(tt * 2.3 + 1.0);
        float len = f <= 0.16F ? f : f * (0.7F + 0.3F * flowFraction()) * flick;
        model.getBone("kflame").ifPresent(b -> {
            b.setHidden(f < 0.05F || dark);
            b.setScaleX(Math.max(0.05F, len));
            b.setScaleY(0.85F + 0.15F * flick);
        });
        model.getBone("kbed").ifPresent(b -> b.setHidden(heat < 0.25F));

        float d = drum01();
        for (int i = 0; i < 3; i++) {
            final float ph = (float) ((tt * 0.05 + i / 3.0) % 1.0);
            model.getBone("klump" + i).ifPresent(b -> {
                b.setHidden(!run || d < 0.3F);
                b.setPosY(-LUMP_FALL * ph * ph);
            });
        }

        float lv = levelShown();
        model.getBone("kheap").ifPresent(b -> { b.setHidden(lv < 0.02F); b.setPosY(-HEAP_DROP * (1.0F - lv)); });
        model.getBone("kvib").ifPresent(b -> {
            float buzz = run ? 1.0F : 0.0F;
            b.setPosY(buzz * 0.12F * (float) Math.sin(tt * 3.1));
            b.setPosZ(buzz * 0.08F * (float) Math.sin(tt * 2.3 + 0.7));
        });
    }

    @Override protected float levelFraction() { return conc.count() / 64.0F; }

    @Override
    protected void runStarted(Level level) {
        playOnce(level, WmdSounds.BAY_CLAMP.get(), Balance.SND_MECH, 0.8F);
        double[] sight = worldPoint(2.5, 1.125, 0.5), stack = worldPoint(-1.95, 2.0, 0.825);
        for (int i = 0; i < 6; i++) {
            level.addParticle(ParticleTypes.FLAME, sight[0], sight[1] + (level.random.nextDouble() - 0.5) * 0.08, sight[2] + (level.random.nextDouble() - 0.5) * 0.08, 0.02, 0.01, 0);
            level.addParticle(ParticleTypes.LARGE_SMOKE, stack[0], stack[1], stack[2], 0, 0.06, 0);
        }
    }

    @Override
    protected void runStopped(Level level) { playOnce(level, WmdSounds.BAY_CLAMP.get(), Balance.SND_MECH, 0.6F); }

    @Override protected SoundEvent runSound() { return WmdSounds.RO_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(2, 0, 0); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE * (isRunning() ? 1.0F : Math.max(0.05F, drum01())); }
    @Override protected float soundPitch() { return 0.85F + 0.15F * drum01(); }
    @Override protected boolean soundAlive() { return isRunning() || drum01() > 0.12F; }

    @Override
    protected void clientEffects(Level level) {
        long t = level.getGameTime(), key = getBlockPos().asLong() ^ 0x524F_0000_0000_0000L;
        double[] peep = worldPoint(1.98, 0.84, -0.47), sight = worldPoint(2.5, 1.125, 0.5), lamp = worldPoint(1.6, 0.89, -0.28);
        VeilBridge.machineGlow(key, peep[0], peep[1], peep[2], 1.0F, 0.5F, 0.15F, 3.0F, 0.6F + 0.6F * flame + 0.2F * (float) Math.sin(t * 0.37), t);
        VeilBridge.machineGlow(key + 1, sight[0], sight[1], sight[2], 1.0F, 0.7F, 0.25F, 1.5F, 0.8F, t);
        VeilBridge.machineGlow(key + 2, lamp[0], lamp[1], lamp[2], 0.3F, 1.0F, 0.4F, 0.8F, 0.5F, t);
        if (t % 7 != 0) return;
        var r = level.random;
        double[] stack = worldPoint(-1.95, 2.0, 0.825);
        level.addParticle(ParticleTypes.SMOKE, stack[0] + (r.nextDouble() - 0.5) * 0.1, stack[1], stack[2] + (r.nextDouble() - 0.5) * 0.1, 0, 0.04, 0);
    }

    @Override
    protected String detail() { return "LPG " + mb(lpg) + ", concentrate " + conc.count() + ", oxide " + oxide.count() + ", cycle " + progress * 100 / Balance.ROAST_CYCLE_TICKS + "%"; }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, -2, 0, 1, Direction.SOUTH) ? lpgIn : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (at(part, side, -2, 0, 0, Direction.WEST)) return in;
        if (at(part, side, 2, 0, 0, Direction.EAST)) return outH;
        return null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override protected void saveExtra(CompoundTag tag) { tag.putInt("progress", progress); }
    @Override protected void loadExtra(CompoundTag tag) { progress = tag.getInt("progress"); }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 2).item(-2, 0, 0, Direction.WEST, false).fluid(-2, 0, 1, Direction.SOUTH, "lpg", false)
            .item(2, 0, 0, Direction.EAST, true).power(1, 0, 1, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
