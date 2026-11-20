package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class IonThrusterBlockEntity extends AirEngineBlockEntity {

    private static final PortMap PORT_MAP = new PortMap(0, 1, 3).power(0, 1, 1, Direction.UP, false);

    static final double AX = 0.5, EXIT_Z = 39.6 / 16.0, GRID_Z = 33.8 / 16.0;

    static final float IRIS_SIGN = 1.0F;

    private float spool, bYaw, bPitch;
    private boolean active;
    private double share;

    private float tSpool, tYaw, tPitch, cSpool, cYaw, cPitch, pSpool, pYaw, pPitch;
    private float phase, pPhase;
    private boolean cActive;

    public IonThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(IonBlocks.ION_BE.get(), pos, state, Balance.ION_FE_IDLE, (Balance.ION_FE_IDLE + Balance.ION_FE_LOAD) * 200, (Balance.ION_FE_IDLE + Balance.ION_FE_LOAD) * 4);
    }

    double axisY() { return (nacelle() ? 16.0 : 13.0) / 16.0; }

    @Override protected double[] intakeLocal() { return new double[] { AX, axisY(), EXIT_Z + 0.2 }; }
    @Override protected double[] thrustLocal() { return new double[] { AX, axisY(), GRID_Z }; }
    @Override protected double airflow() { return Balance.ION_AIRFLOW; }
    @Override protected double noiseDb() { return Balance.ION_NOISE_DB; }
    @Override protected double heatSignature() { return 0.0; }
    @Override protected double hitSize() { return 1.2; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        tickAir(sl);
        double want = Math.max(0.0, throttle);
        boolean centred = Math.abs(bYaw) < 0.5F && Math.abs(bPitch) < 0.5F;
        if (want < 1.0E-3 && spool < 1.0E-3 && centred) {
            spool = 0.0F;
            active = false;
            after(sl);
            return false;
        }
        active = true;
        float target = (float) (want > 1.0E-3 && useLoad(want) ? want : 0.0);
        spool = approach(spool, target, 1.0F / (target > spool ? Balance.ION_SPOOL_UP : Balance.ION_SPOOL_DOWN));
        steer(0.05F);
        after(sl);
        return true;
    }

    private boolean useLoad(double want) {
        int fe = (int) Math.round(Balance.ION_FE_LOAD * want);
        return fe <= 0 || useExtraEnergy(fe) || (getLevel() instanceof ServerLevel sl && ShipPower.draw(this, sl, ship, fe));
    }

    @Override
    protected void idle() {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        orders(sl);
        tickAir(sl);
        double want = Math.max(0.0, throttle);
        if ((spool > 1.0E-3 || want > 1.0E-3) && ShipPower.draw(this, sl, ship, Balance.ION_FE_IDLE)) {
            float target = (float) (want > 1.0E-3 && ShipPower.draw(this, sl, ship, (int) Math.round(Balance.ION_FE_LOAD * want)) ? want : 0.0);
            spool = approach(spool, target, 1.0F / (target > spool ? Balance.ION_SPOOL_UP : Balance.ION_SPOOL_DOWN));
            steer(0.05F);
            active = true;
        } else {
            spool = approach(spool, 0.0F, 1.0F / Balance.ION_COAST);
            active = spool > 1.0E-3;
        }
        after(sl);
    }

    private void steer(float dt) {
        double[] s = steerLocal(true);
        float r = (float) Balance.ION_STEER_RATE * dt;
        bYaw = approach(bYaw, (float) (s[0] * Balance.ION_STEER_MAX), r);
        bPitch = approach(bPitch, (float) (s[1] * Balance.ION_STEER_MAX), r);
    }

    private void tickAir(ServerLevel sl) {
        if ((sl.getGameTime() + (worldPosition.asLong() & 15)) % Balance.PROP_BEAT == 0) {
            airBeat(sl);
            pressure = 1.0;
            report(sl, share, active && spool > 0.2F, bYaw, bPitch);
        }
    }

    private void after(ServerLevel sl) {
        double thrust = Balance.ION_THRUST * spool * airShare(spool);
        double y = Math.toRadians(bYaw), p = Math.toRadians(bPitch);
        share = thrust / Balance.ION_THRUST;
        setForce(thrust * Math.cos(y) * Math.cos(p), thrust * Math.sin(y), thrust * Math.sin(p));
        syncMaybe(sl.getGameTime());
    }

    private float sentSpool = -9, sentYaw = 999, sentPitch = 999;
    private boolean sentActive;

    private void syncMaybe(long now) {
        boolean big = active != sentActive || (spool < 0.01F) != (sentSpool < 0.01F) || (spool > 0.99F) != (sentSpool > 0.99F);
        boolean small = Math.abs(spool - sentSpool) > 0.06F || Math.abs(bYaw - sentYaw) > 1.5F || Math.abs(bPitch - sentPitch) > 1.5F;
        if (!big && !(small && now % 4 == 0)) return;
        sentActive = active; sentSpool = spool; sentYaw = bYaw; sentPitch = bPitch;
        syncNow();
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 1, 1, Direction.UP) ? energy() : null; }

    @Override
    protected void easeTick(Level lv) {
        ClientIon.track(this);
        pYaw = cYaw; pPitch = cPitch; pSpool = cSpool; pPhase = phase;
        float k = (float) Balance.ION_STEER_RATE / 20.0F * 1.3F;
        cYaw = approach(cYaw, tYaw, k);
        cPitch = approach(cPitch, tPitch, k);
        cSpool = approach(cSpool, tSpool, 1.0F / (tSpool > cSpool ? Balance.ION_SPOOL_UP : Balance.ION_SPOOL_DOWN) * 1.3F);
        phase += 0.08F + 0.32F * cSpool;
        if (phase > 4000.0F) { phase -= 4000.0F; pPhase -= 4000.0F; }
    }

    float beamNow(float pt) { return Mth.lerp(pt, pSpool, cSpool); }
    float yawNow(float pt) { return Mth.lerp(pt, pYaw, cYaw); }
    float pitchNow(float pt) { return Mth.lerp(pt, pPitch, cPitch); }
    boolean beamOn() { return cActive && damageStage() < 3 && cSpool > 0.02F; }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float s = Mth.lerp(partialTick, pSpool, cSpool);
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5) || damageStage() >= 3;
        float open = ease(Mth.clamp(s, 0.0F, 1.0F));
        float iris = IRIS_SIGN * (float) Math.toRadians(Balance.ION_IRIS_CLOSED) * (1.0F - open);
        for (int k = 0; k < 8; k++) model.getBone("iris" + k).ifPresent(b -> b.setRotZ(iris));
        for (int k = 0; k < 3; k++) {
            final boolean lit = !dark && s >= 0.04F + 0.12F * k;
            model.getBone("grid" + k).ifPresent(b -> b.setHidden(!lit));
        }
        float ph = Mth.lerp(partialTick, pPhase, phase);
        for (int k = 0; k < 4; k++) {
            float d = ((ph - k) % 4.0F + 4.0F) % 4.0F;
            final boolean lit = !dark && cActive && s > 0.02F && d < 1.6F;
            model.getBone("coil" + k).ifPresent(b -> b.setHidden(!lit));
        }
        final boolean neut = !dark && s > 0.08F;
        model.getBone("neut").ifPresent(b -> b.setHidden(!neut));
        final boolean lamp = !dark && cActive;
        model.getBone("lamp").ifPresent(b -> b.setHidden(!lamp));
    }

    private static float ease(float s) { return s * s * (3.0F - 2.0F * s); }

    @Override protected void runStarted(Level lv) { playOnce(lv, WmdSounds.LASER_ON.get(), Balance.SND_SMALL, 0.7F); }
    @Override protected SoundEvent runSound() { return WmdSounds.ION_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL * (0.35F + 0.65F * cSpool); }
    @Override protected float soundPitch() { return 0.9F + 0.25F * cSpool; }
    @Override protected boolean soundAlive() { return cActive || cSpool > 0.05F; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 2); }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.ion_use", Balance.ION_FE_IDLE, String.format("%,d", Balance.ION_FE_IDLE + Balance.ION_FE_LOAD)),
                Component.translatable("gui.wmd.screw.order", Component.translatable("gui.wmd.prop.step" + cStep), sourceName(cSource)),
                Component.translatable("gui.wmd.ion.state", Math.round(cSpool * 100.0F), Math.round(cYaw), Math.round(cPitch)),
                Component.translatable(!SableBridge.loaded() ? "gui.wmd.screw.no_sable" : "gui.wmd.ion.rule"));
    }

    @Override protected String detail() { return "step " + step + ", beam " + Math.round(spool * 100) + "%, bend " + Math.round(bYaw) + " / " + Math.round(bPitch) + (dry ? "" : " (exit wet)"); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
    }

    private void put(CompoundTag tag) {
        tag.putFloat("ionSpool", spool);
        tag.putFloat("ionYaw", bYaw);
        tag.putFloat("ionPitch", bPitch);
        tag.putBoolean("ionAct", active);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        float sp = Mth.clamp(tag.getFloat("ionSpool"), 0.0F, 1.0F);
        float lim = (float) Balance.ION_STEER_MAX;
        float ya = Mth.clamp(tag.getFloat("ionYaw"), -lim, lim), pi = Mth.clamp(tag.getFloat("ionPitch"), -lim, lim);
        boolean act = tag.getBoolean("ionAct");
        if (level != null && level.isClientSide) { tSpool = sp; tYaw = ya; tPitch = pi; cActive = act; return; }
        spool = sp; bYaw = ya; bPitch = pi; active = act;
        tSpool = cSpool = pSpool = sp; tYaw = cYaw = pYaw = ya; tPitch = cPitch = pPitch = pi; cActive = act;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        return tag;
    }
}
