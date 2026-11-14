package com.ashraf.wmd;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class LiftFanBlockEntity extends AirEngineBlockEntity implements CopycatState.Host {

    private static final Set<UUID> LIFTING = Statics.server(new HashSet<>());

    private final CopycatState copycat = new CopycatState();

    private float spool, louvre;
    private boolean hoverOn, driven;
    private Vec3 upPrev;

    private Vec3 levI = Vec3.ZERO;
    private double share;

    private float tSpool, cSpool, tLouvre, cLouvre, pLouvre;
    private double fanAngle, pFan;

    public LiftFanBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.LIFT_FAN_BE.get(), pos, state, 0, 1, 0);
    }

    static boolean lifting(UUID ship) { return ship != null && LIFTING.contains(ship); }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { syncNow(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    @Override protected double[] intakeLocal() { return new double[] { 0.0, 1.0, 0.0 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, 0.5, 0.0 }; }
    @Override protected double airflow() { return 0.0; }
    @Override protected double hitSize() { return 1.5; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        if ((sl.getGameTime() + (worldPosition.asLong() & 15)) % Balance.PROP_BEAT == 0) {
            airBeat(sl);
            hoverOn = ShipModes.hover(ship);
            driven = hoverOn && jetRunning(sl, ship);
            report(sl, share, spool > 0.2F, 0.0, louvre);
        }
        float target = driven && damageStage() < 3 ? 1.0F : 0.0F;
        spool = approach(spool, target, 1.0F / (target > spool ? Balance.LIFT_SPOOL_UP : Balance.LIFT_SPOOL_DOWN));
        louvre = approach(louvre, (float) (planesOrd * Balance.LIFT_LOUVRE_TILT), (float) Balance.LIFT_LOUVRE_RATE / 20.0F);
        if (ship != null) { if (spool > 0.05F) LIFTING.add(ship); else LIFTING.remove(ship); }
        push(sl);
        syncMaybe(sl.getGameTime());
        return spool > 1.0E-3F || hoverOn;
    }

    private void push(ServerLevel sl) {
        double lift = Balance.LIFT_THRUST * spool * Mth.clamp(throttle, 0.0, 1.0) * pressure * damageShare() * devThrustScale;
        share = lift / Balance.LIFT_THRUST;
        if (ship == null || lift < 1.0E-3 || !dry) { force = null; levI = Vec3.ZERO; return; }
        SableBridge.ShipInfo info = SableBridge.shipInfo(sl, ship);
        Vec3 com = info == null ? null : SableBridge.toShip(sl, info, info.worldCenter());
        Vec3 a = info == null ? null : SableBridge.toShip(sl, info, info.worldCenter().add(0.0, 1.0, 0.0));
        if (com == null || a == null) { force = null; return; }
        Vec3 up = a.subtract(com);
        Vec3 du = upPrev == null ? Vec3.ZERO : up.subtract(upPrev).scale(20.0);
        upPrev = up;

        if (LandingGearBlockEntity.onWheels(sl, ship)) levI = Vec3.ZERO;
        else {
            levI = levI.add(up.x * Balance.LIFT_LEVEL_I, 0.0, up.z * Balance.LIFT_LEVEL_I);
            if (levI.length() > Balance.LIFT_LEVEL_IMAX) levI = levI.normalize().scale(Balance.LIFT_LEVEL_IMAX);
        }
        double ox = -Balance.LIFT_LEVEL_K * up.x - Balance.LIFT_LEVEL_D * du.x - levI.x, oz = -Balance.LIFT_LEVEL_K * up.z - Balance.LIFT_LEVEL_D * du.z - levI.z;
        double len = Math.sqrt(ox * ox + oz * oz);
        if (len > Balance.LIFT_LEVEL_MAX) { ox *= Balance.LIFT_LEVEL_MAX / len; oz *= Balance.LIFT_LEVEL_MAX / len; }
        Vec3 f = unit(front()).scale(lift * Math.sin(Math.toRadians(louvre)) * Balance.LIFT_LOUVRE_SIDE).add(0.0, lift, 0.0);
        force = new double[] { com.x + ox, com.y, com.z + oz, f.x, f.y, f.z };
    }

    private float sentSpool = -9, sentLouvre = 999;

    private void syncMaybe(long now) {
        boolean big = (spool < 0.01F) != (sentSpool < 0.01F) || (spool > 0.99F) != (sentSpool > 0.99F);
        boolean small = Math.abs(spool - sentSpool) > 0.08F || Math.abs(louvre - sentLouvre) > 2.0F;
        if (!big && !(small && now % 4 == 0)) return;
        sentSpool = spool; sentLouvre = louvre;
        syncNow();
    }

    @Override
    public void setRemoved() {
        if (ship != null) LIFTING.remove(ship);
        super.setRemoved();
    }

    @Override
    public Component status() {
        String k = !hoverOn ? "message.wmd.lift.off" : !driven ? "message.wmd.lift.no_jet" : damageStage() >= 3 ? "message.wmd.lift.flameout" : "message.wmd.lift.hover";
        return Component.translatable("block.wmd.lift_fan").append(": ").append(Component.translatable(k, Math.round(spool * 100.0F)));
    }

    @Override
    protected void easeTick(Level lv) {
        pFan = fanAngle; pLouvre = cLouvre;
        float up = 1.0F / Balance.LIFT_SPOOL_UP * 1.3F, down = 1.0F / Balance.LIFT_SPOOL_DOWN * 1.3F;
        cSpool = tSpool > cSpool ? approach(cSpool, tSpool, up) : approach(cSpool, tSpool, down);
        cLouvre = approach(cLouvre, tLouvre, (float) Balance.LIFT_LOUVRE_RATE / 20.0F * 1.3F);
        fanAngle += Math.PI * 2.0 * 5.0 / 20.0 * cSpool * cSpool;
        if (fanAngle > Math.PI * 2000.0) { fanAngle -= Math.PI * 2000.0; pFan -= Math.PI * 2000.0; }
    }

    @Override protected boolean runningClient() { return cSpool > 0.05F; }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float a = (float) Mth.lerp(partialTick, pFan, fanAngle) * RZ;
        model.getBone("fan").ifPresent(b -> b.setRotZ(a));
        model.getBone("fan2").ifPresent(b -> b.setRotZ(-a * 0.9F));
        float open = Mth.clamp(cSpool * 3.0F, 0.0F, 1.0F) * (float) Balance.LIFT_LOUVRE_OPEN;
        float tilt = (float) Mth.lerp(partialTick, pLouvre, cLouvre) * Mth.clamp(cSpool * 3.0F, 0.0F, 1.0F);
        float r = (float) Math.toRadians(open - tilt) * RX;
        for (int k = 0; k < 6; k++) model.getBone("louv" + k).ifPresent(b -> b.setRotX(r));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (cSpool < 0.4F || lv.random.nextFloat() > cSpool * 0.6F) return;
        var rnd = lv.random;
        double[] c = worldPoint((rnd.nextDouble() - 0.5) * 1.6, -0.2, (rnd.nextDouble() - 0.5) * 1.6), d = worldPoint(0.0, -1.4, 0.0), o = worldPoint(0.0, -0.2, 0.0);
        lv.addParticle(ParticleTypes.CLOUD, c[0], c[1], c[2], (d[0] - o[0]) * 0.35, (d[1] - o[1]) * 0.35, (d[2] - o[2]) * 0.35);
        AirFx.downwash(lv, o[0], o[1], o[2], cSpool, 2.5);
    }

    @Override protected SoundEvent runSound() { return WmdSounds.LIFT_FAN_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY * (0.3F + 0.7F * cSpool); }
    @Override protected float soundPitch() { return 0.8F + 0.2F * cSpool; }
    @Override protected boolean soundAlive() { return cSpool > 0.05F; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }

    @Override protected String detail() { return "hover " + hoverOn + ", jet " + driven + ", fan " + Math.round(spool * 100) + "%, louvres " + Math.round(louvre); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
        copycat.save(tag, registries);
    }

    private void put(CompoundTag tag) {
        tag.putFloat("lfSpool", spool);
        tag.putFloat("lfLouvre", louvre);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        copycat.load(tag, registries, level != null && level.isClientSide);
        float sp = Mth.clamp(tag.getFloat("lfSpool"), 0.0F, 1.0F), lo = Mth.clamp(tag.getFloat("lfLouvre"), -45.0F, 45.0F);
        if (level != null && level.isClientSide) { tSpool = sp; tLouvre = lo; return; }
        spool = sp; louvre = lo;
        tSpool = cSpool = sp; tLouvre = cLouvre = pLouvre = lo;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        copycat.putSync(tag);
        return tag;
    }
}
