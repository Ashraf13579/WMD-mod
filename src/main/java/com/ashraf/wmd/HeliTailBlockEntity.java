package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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

public class HeliTailBlockEntity extends AirEngineBlockEntity implements CopycatState.Host {

    static final double SIDE = 1.0;

    private double lastHdg = Double.NaN;

    private final CopycatState copycat = new CopycatState();
    private float spool;

    private float tSpool, cSpool;
    private double ang, pAng;

    public HeliTailBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.HELI_TAIL_BE.get(), pos, state, 0, 1, 0);
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

    @Override protected double[] intakeLocal() { return new double[] { 0.7, 0.5, 0.0 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.7, 0.5, 0.0 }; }
    @Override protected double airflow() { return 0.0; }
    @Override protected double hitSize() { return 1.0; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        if ((sl.getGameTime() + (worldPosition.asLong() & 15)) % Balance.PROP_BEAT == 0) {
            airBeat(sl);
            report(sl, spool, spool > 0.2F, rudderOrd, 0.0);
        }
        double d = HeliTurboshaftBlockEntity.drive(sl, ship);
        float target = damageStage() < 3 ? (float) d : 0.0F;
        spool = approach(spool, target, 1.0F / (target > spool ? Balance.HELI_SPOOL_UP : Balance.HELI_SPOOL_DOWN));
        boolean cockpit = source == ShipControl.SRC_COCKPIT;

        double rate = yawRate(sl);

        if (Math.abs(rate) < Balance.HELI_YAW_DEAD) rate = 0.0;
        boolean turnKey = cockpit && Math.abs(rollOrd) > 0.01;
        boolean onGround = ship != null && LandingGearBlockEntity.onWheels(sl, ship);

        double cmd = Mth.clamp(Balance.HELI_YAW_K * ((cockpit ? rollOrd : 0.0) * Balance.HELI_YAW_RATE - rate), -1.0, 1.0);
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        double max = Math.min(Balance.HELI_TAIL_THRUST, (info == null ? 0.0 : Math.max(1.0, info.mass())) * Balance.HELI_TAIL_PER_MASS) * spool * pressure;

        double tq = yawTorquePerSide(sl, info);
        double side = spool > 0.05F && Math.abs(tq) > 1.0E-3 && (turnKey || !onGround) ? -cmd * max * Math.signum(tq) : 0.0;
        setForce(0.0, side, 0.0);
        if (sl.getGameTime() % 4 == 0) syncNow();
        return spool > 1.0E-3F;
    }

    private double yawTorquePerSide(ServerLevel sl, SableBridge.ShipInfo info) {
        if (info == null || info.worldCenter() == null) return 0.0;
        double[] t = thrustLocal();
        Vec3 p = plotPoint(t[0], t[1], t[2]), e = unit(localEast());
        Vec3 pw = SableBridge.toWorld(sl, p), ew = SableBridge.toWorld(sl, p.add(e));
        if (pw == null || ew == null) return 0.0;
        Vec3 r = pw.subtract(info.worldCenter()), d = ew.subtract(pw);
        return r.z * d.x - r.x * d.z;
    }

    private double yawRate(ServerLevel sl) {
        Vec3 c = net.minecraft.world.phys.Vec3.atCenterOf(worldPosition);
        Vec3 f = unit(front());
        Vec3 a = SableBridge.toWorld(sl, c), b = SableBridge.toWorld(sl, c.add(f));
        if (a == null || b == null) { lastHdg = Double.NaN; return 0.0; }
        double h = Math.toDegrees(Math.atan2(b.x - a.x, -(b.z - a.z)));
        double r = Double.isNaN(lastHdg) ? 0.0 : Mth.wrapDegrees(h - lastHdg) * 20.0;
        lastHdg = h;
        return r;
    }

    @Override
    public Component status() {
        return Component.translatable("block.wmd.heli_tail").append(": ").append(Component.translatable(spool > 0.05F ? "message.wmd.heli.turning" : "message.wmd.heli.no_drive", Math.round(spool * 100.0F)));
    }

    @Override
    protected void easeTick(Level lv) {
        pAng = ang;
        cSpool = approach(cSpool, tSpool, 1.0F / (tSpool > cSpool ? Balance.HELI_SPOOL_UP : Balance.HELI_SPOOL_DOWN) * 1.3F);
        ang += Math.PI * 2.0 * 7.0 / 20.0 * cSpool;
        if (ang > Math.PI * 2000.0) { ang -= Math.PI * 2000.0; pAng -= Math.PI * 2000.0; }
    }

    @Override protected boolean runningClient() { return cSpool > 0.05F; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float a = (float) Mth.lerp(pt, pAng, ang) * RX;
        model.getBone("trotor").ifPresent(b -> b.setRotX(a));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.TAIL_ROTOR_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL * (0.3F + 0.7F * cSpool); }
    @Override protected float soundPitch() { return 0.85F + 0.2F * cSpool; }
    @Override protected boolean soundAlive() { return cSpool > 0.05F; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected String detail() { return "spool " + Math.round(spool * 100) + "%, rudder " + rudderOrd; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("trSpool", spool);
        copycat.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        copycat.load(tag, registries, level != null && level.isClientSide);
        float sp = Mth.clamp(tag.getFloat("trSpool"), 0.0F, 1.0F);
        if (level != null && level.isClientSide) { tSpool = sp; return; }
        spool = sp;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putFloat("trSpool", spool);
        copycat.putSync(tag);
        return tag;
    }
}
