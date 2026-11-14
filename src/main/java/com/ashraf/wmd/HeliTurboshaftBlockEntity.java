package com.ashraf.wmd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class HeliTurboshaftBlockEntity extends AirEngineBlockEntity implements CopycatState.Host {

    private static final Map<UUID, double[]> DRIVE = Statics.server(new HashMap<>());

    private final CopycatState copycat = new CopycatState();
    private float spool;

    private float tSpool, cSpool;
    private double fan, pFan;

    public HeliTurboshaftBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.HELI_TURBOSHAFT_BE.get(), pos, state, 0, 1, 0);
    }

    static double drive(ServerLevel sl, UUID ship) {
        double[] d = ship == null ? null : DRIVE.get(ship);
        return d == null || sl.getGameTime() - (long) d[1] > 20 ? 0.0 : d[0];
    }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { syncNow(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    @Override protected double[] intakeLocal() { return new double[] { 0.0, 0.5, -0.45 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, 0.5, 0.5 }; }
    @Override protected double airflow() { return 0.0; }
    @Override protected double hitSize() { return 1.0; }
    @Override protected boolean hotExhaust() { return true; }
    @Override protected double[] exhaustLocal() { return new double[] { 0.0, 1.0, 1.0 }; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        if ((sl.getGameTime() + (worldPosition.asLong() & 15)) % Balance.PROP_BEAT == 0) {
            airBeat(sl);
            report(sl, spool, spool > 0.2F, 0.0, 0.0);
        }
        boolean want = Math.abs(throttle) > 0.02 && damageStage() < 3 && dry && ship != null && ShipPower.draw(sl, ship, Balance.TURBO_FE);
        spool = approach(spool, want ? 1.0F : 0.0F, 1.0F / (want ? Balance.TURBO_SPOOL_UP : Balance.TURBO_SPOOL_DOWN));
        if (ship != null) {
            double[] d = DRIVE.get(ship);
            long now = sl.getGameTime();
            if (d == null || now - (long) d[1] > 20 || spool >= d[0]) DRIVE.put(ship, new double[] { spool, now });
        }
        force = null;
        if (sl.getGameTime() % 4 == 0) syncNow();
        return spool > 1.0E-3F;
    }

    @Override
    public Component status() {
        return Component.translatable("block.wmd.heli_turboshaft").append(": ").append(Component.translatable(spool > 0.05F ? "message.wmd.heli.turbo_on" : "message.wmd.heli.turbo_off", Math.round(spool * 100.0F)));
    }

    @Override
    protected void easeTick(Level lv) {
        pFan = fan;
        cSpool = approach(cSpool, tSpool, 1.0F / (tSpool > cSpool ? Balance.TURBO_SPOOL_UP : Balance.TURBO_SPOOL_DOWN) * 1.3F);
        fan += Math.PI * 2.0 * 6.0 / 20.0 * cSpool;
        if (fan > Math.PI * 2000.0) { fan -= Math.PI * 2000.0; pFan -= Math.PI * 2000.0; }
    }

    @Override protected boolean runningClient() { return cSpool > 0.05F; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float a = (float) Mth.lerp(pt, pFan, fan) * RZ;
        model.getBone("fan").ifPresent(b -> b.setRotZ(a));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (cSpool < 0.3F || lv.random.nextFloat() > cSpool * 0.5F) return;
        double[] w = worldPoint(0.0, 1.0, 1.0);
        AirFx.haze(lv, w);
    }

    @Override protected SoundEvent runSound() { return WmdSounds.TURBOSHAFT_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY * (0.3F + 0.7F * cSpool); }
    @Override protected float soundPitch() { return 0.8F + 0.2F * cSpool; }
    @Override protected boolean soundAlive() { return cSpool > 0.05F; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected String detail() { return "spool " + Math.round(spool * 100) + "%"; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("tsSpool", spool);
        copycat.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        copycat.load(tag, registries, level != null && level.isClientSide);
        float sp = Mth.clamp(tag.getFloat("tsSpool"), 0.0F, 1.0F);
        if (level != null && level.isClientSide) { tSpool = sp; return; }
        spool = sp;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putFloat("tsSpool", spool);
        copycat.putSync(tag);
        return tag;
    }
}
