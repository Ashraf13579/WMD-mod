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

public class HeliRotorBlockEntity extends AirEngineBlockEntity implements CopycatState.Host {

    static final int[] LENGTHS = { 7, 11, 15 };

    private final CopycatState copycat = new CopycatState();

    private int lenIdx = 1, blades = 4;
    private boolean folded;

    private float spool, cycP, cycR, coll;
    private boolean tandem, ccw;

    private int heads = 1;
    private Vec3 upPrev;

    private Vec3 levI = Vec3.ZERO;

    private float liftTrim;

    private float tSpool, cSpool, tCycP, tCycR, tColl, cCycP, cCycR, cColl;
    private boolean cTandemCcw;
    private double ang, pAng;

    public HeliRotorBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.HELI_ROTOR_BE.get(), pos, state, 0, 1, 0);
    }

    public int bladeLength() { return LENGTHS[Mth.clamp(lenIdx, 0, 2)]; }
    public int bladeCount() { return blades; }

    float spoolNow() { return spool; }
    public boolean folded() { return folded; }
    float rotorAngle(float pt) { return (float) Mth.lerp(pt, pAng, ang); }
    float pitchShown() { return cColl * (float) Balance.HELI_PITCH_SHOW; }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { syncNow(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    @Override protected double[] intakeLocal() { return new double[] { 0.0, 1.6, 0.0 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, 1.5, 0.0 }; }
    @Override protected double airflow() { return 0.0; }
    @Override protected double hitSize() { return 1.0; }

    boolean stopped() { return spool < 0.05F; }

    String nextLength() {
        if (!stopped()) return "message.wmd.heli.stop_first";
        lenIdx = (lenIdx + 1) % 3;
        syncNow();
        return "message.wmd.heli.length";
    }

    String nextCount() {
        if (!stopped()) return "message.wmd.heli.stop_first";
        blades = blades >= 4 ? 2 : blades + 1;
        syncNow();
        return "message.wmd.heli.count";
    }

    String toggleFold() {
        if (!stopped()) return "message.wmd.heli.stop_first";
        folded = !folded;
        syncNow();
        return folded ? "message.wmd.heli.folded" : "message.wmd.heli.spread";
    }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        if ((sl.getGameTime() + (worldPosition.asLong() & 15)) % Balance.PROP_BEAT == 0) {
            airBeat(sl);
            tandemBeat(sl);
            report(sl, spool, spool > 0.2F, cycR, cycP);
        }
        double d = HeliTurboshaftBlockEntity.drive(sl, ship);
        float target = !folded && damageStage() < 3 ? (float) d : 0.0F;
        spool = approach(spool, target, 1.0F / (target > spool ? Balance.HELI_SPOOL_UP : Balance.HELI_SPOOL_DOWN));
        boolean cockpit = source == ShipControl.SRC_COCKPIT;
        float rate = 30.0F / 20.0F;
        cycP = approach(cycP, cockpit ? (float) (planesOrd * (planesOrd > 0 ? Balance.HELI_TILT_FWD : Balance.HELI_TILT)) : 0.0F, rate);
        cycR = approach(cycR, cockpit ? (float) (rudderOrd * Balance.HELI_TILT) : 0.0F, rate);
        coll = approach(coll, cockpit ? (float) Mth.clamp(throttle, 0.0, 1.0) : 0.0F, 1.0F / 20.0F);
        push(sl, cockpit);
        if (sl.getGameTime() % 4 == 0) syncNow();
        return spool > 1.0E-3F;
    }

    private void tandemBeat(ServerLevel sl) {
        tandem = false; ccw = false;
        if (ship == null) return;
        Vec3 me = Vec3.atCenterOf(worldPosition), f = unit(front());
        int ahead = 0, heads = 0;
        for (AirEngineBlockEntity e : live(sl)) {
            if (!(e instanceof HeliRotorBlockEntity h) || !ship.equals(h.ship)) continue;
            heads++;
            if (h != this && Vec3.atCenterOf(h.getBlockPos()).subtract(me).dot(f) > 0.5) ahead++;
        }
        tandem = heads >= 2;
        this.heads = Math.max(1, heads);
        ccw = tandem && ahead > 0;
    }

    private void push(ServerLevel sl, boolean cockpit) {

        double cap = Balance.HELI_LIFT[Mth.clamp(lenIdx, 0, 2)] * spool * pressure * damageShare() * devThrustScale;
        if (ship == null || cap < 1.0E-3 || !dry) { force = null; return; }
        SableBridge.ShipInfo info = SableBridge.shipInfo(sl, ship);
        if (info == null) { force = null; return; }
        double c = coll - 0.5;
        double vyT = Math.abs(c) <= Balance.HELI_COLL_DEAD ? 0.0 : Mth.clamp((c - Math.signum(c) * Balance.HELI_COLL_DEAD) / (0.5 - Balance.HELI_COLL_DEAD), -1.0, 1.0) * Balance.HELI_CLIMB;
        double vy = info.linearVelocity() == null ? 0.0 : ShipDefence.velocity(sl, info).y * 20.0;
        double share = Math.max(1.0, info.mass()) * WingTrails.GRAV / heads;

        double need = share * (1.0 + liftTrim + Balance.HELI_VY_K * (vyT - vy));
        if (need < cap) liftTrim = (float) Mth.clamp(liftTrim + Balance.HELI_VY_I * (vyT - vy), -Balance.HELI_TRIM_DOWN, Balance.HELI_TRIM_UP);
        Vec3 com = SableBridge.toShip(sl, info, info.worldCenter());
        Vec3 a = SableBridge.toShip(sl, info, info.worldCenter().add(0.0, 1.0, 0.0));
        if (com == null || a == null) { force = null; return; }
        Vec3 up = a.subtract(com);
        up = up.lengthSqr() < 1.0E-9 ? new Vec3(0.0, 1.0, 0.0) : up.normalize();
        Vec3[] ax = cockpitAxes(sl, ship);
        Vec3 nose = level(ax != null ? ax[0] : unit(front())), right = level(ax != null ? ax[1] : unit(localEast()));

        double th = Math.toRadians(cycP);
        Vec3 want = new Vec3(0.0, Math.cos(th), 0.0).subtract(nose.scale(Math.sin(th)));
        Vec3 e = up.subtract(want);
        Vec3 du = upPrev == null ? Vec3.ZERO : up.subtract(upPrev).scale(20.0);
        upPrev = up;
        levI = levI.add(e.x * Balance.HELI_LEVEL_I, 0.0, e.z * Balance.HELI_LEVEL_I);
        if (levI.length() > Balance.HELI_LEVEL_IMAX) levI = levI.normalize().scale(Balance.HELI_LEVEL_IMAX);
        double ox = -Balance.HELI_LEVEL_K * e.x - Balance.HELI_LEVEL_D * du.x - levI.x, oz = -Balance.HELI_LEVEL_K * e.z - Balance.HELI_LEVEL_D * du.z - levI.z;
        double len = Math.sqrt(ox * ox + oz * oz);
        if (len > Balance.HELI_LEVEL_MAX) { ox *= Balance.HELI_LEVEL_MAX / len; oz *= Balance.HELI_LEVEL_MAX / len; }
        double cosT = up.y;
        boolean upset = cosT < Math.cos(Math.toRadians(Balance.HELI_UPSET_DEG));
        double lift = Mth.clamp(need / Math.max(0.5, cosT), 0.0, cap);
        if (lift < 1.0E-3) { force = null; levI = Vec3.ZERO; liftTrim = 0.0F; return; }
        Vec3 f = (upset ? up : new Vec3(0.0, 1.0, 0.0)).scale(lift).add(right.scale(lift * Math.sin(Math.toRadians(cycR))));
        if (tandem && cockpit) f = f.add(right.scale(lift * Balance.HELI_TANDEM_YAW * rollOrd * (ccw ? -1.0 : 1.0)));
        if (Math.abs(cycP) < 0.5F && Math.abs(cycR) < 0.5F && info.linearVelocity() != null && !LandingGearBlockEntity.onWheels(sl, ship)) {
            Vec3 vw = ShipDefence.velocity(sl, info).scale(20.0);
            Vec3 b = SableBridge.toShip(sl, info, info.worldCenter().add(vw));
            if (b != null) {
                Vec3 vl = b.subtract(com);
                Vec3 h = new Vec3(vl.x, 0.0, vl.z).scale(-Balance.HELI_HOLD);
                double hcap = lift * Balance.HELI_HOLD_MAX;
                if (h.length() > hcap) h = h.normalize().scale(hcap);
                f = f.add(h);
            }
        }
        force = new double[] { com.x + ox, com.y, com.z + oz, f.x, f.y, f.z };
    }

    static boolean aboard(ServerLevel sl, java.util.UUID ship) {
        if (ship == null) return false;
        for (AirEngineBlockEntity e : live(sl)) if (e instanceof HeliRotorBlockEntity && ship.equals(e.ship)) return true;
        return false;
    }

    static Vec3[] cockpitAxes(ServerLevel sl, java.util.UUID ship) {
        ShipControl.Order o = ship == null ? null : ShipControl.newest(sl, ship);
        if (o == null || o.from() == null || !sl.isLoaded(o.from())) return null;
        return sl.getBlockEntity(o.from()) instanceof JetCockpitBlockEntity jc ? jc.axesPlot() : null;
    }

    private static Vec3 level(Vec3 v) {
        Vec3 h = new Vec3(v.x, 0.0, v.z);
        return h.lengthSqr() < 1.0E-6 ? Vec3.ZERO : h.normalize();
    }

    private static Vec3 flat(Vec3 v, Vec3 wu) {
        Vec3 h = v.subtract(wu.scale(v.dot(wu)));
        return h.lengthSqr() < 1.0E-6 ? Vec3.ZERO : h.normalize();
    }

    @Override
    public Component status() {
        String k = folded ? "message.wmd.heli.folded" : spool > 0.05F ? "message.wmd.heli.turning" : "message.wmd.heli.no_drive";
        return Component.translatable("block.wmd.heli_rotor").append(": ").append(Component.translatable("message.wmd.heli.setup", blades, bladeLength()))
                .append(", ").append(Component.translatable(k, Math.round(spool * 100.0F))).append(tandem ? Component.translatable("message.wmd.heli.tandem") : Component.empty());
    }

    @Override
    protected void easeTick(Level lv) {
        pAng = ang;
        cSpool = approach(cSpool, tSpool, 1.0F / (tSpool > cSpool ? Balance.HELI_SPOOL_UP : Balance.HELI_SPOOL_DOWN) * 1.3F);
        float k = 30.0F / 20.0F * 1.3F;
        cCycP = approach(cCycP, tCycP, k); cCycR = approach(cCycR, tCycR, k); cColl = approach(cColl, tColl, 0.07F);
        ang += (cTandemCcw ? -1.0 : 1.0) * Math.PI * 2.0 * 4.5 / 20.0 * cSpool;
        if (Math.abs(ang) > Math.PI * 2000.0) { double d = Math.signum(ang) * Math.PI * 2000.0; ang -= d; pAng -= d; }
        if (folded && cSpool < 0.01F) { ang = 0.0; pAng = 0.0; }
    }

    @Override protected boolean runningClient() { return cSpool > 0.05F; }

    @Override
    protected void clientEffects(Level lv) {
        if (cSpool < 0.3F) return;
        double[] c = worldPoint(0.0, 0.0, 0.0);
        AirFx.downwash(lv, c[0], c[1], c[2], cSpool * Math.max(0.35F, Mth.clamp(cColl, 0.0F, 1.0F)), bladeLength() * 0.5);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float a = (float) Mth.lerp(pt, pAng, ang) * RY;
        model.getBone("mast").ifPresent(b -> b.setRotY(a));
        float p = (float) Math.toRadians(cCycP) * RX, r = (float) Math.toRadians(cCycR) * RZ;
        model.getBone("swash").ifPresent(b -> { b.setRotX(p); b.setRotZ(r); b.setPosY(cColl * 1.2F); });
    }

    @Override protected SoundEvent runSound() { return WmdSounds.ROTOR_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_GIANT * (0.3F + 0.7F * cSpool); }
    @Override protected float soundPitch() { return 0.75F + 0.25F * cSpool; }
    @Override protected boolean soundAlive() { return cSpool > 0.05F; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected String detail() { return blades + " x " + bladeLength() + (folded ? " folded" : "") + ", spool " + Math.round(spool * 100) + "%, cyclic " + Math.round(cycP) + " / " + Math.round(cycR) + (tandem ? ", tandem" : ""); }

    private void put(CompoundTag tag) {
        tag.putInt("hrLen", lenIdx);
        tag.putInt("hrBlades", blades);
        tag.putBoolean("hrFold", folded);
        tag.putFloat("hrSpool", spool);
        tag.putFloat("hrCycP", cycP);
        tag.putFloat("hrCycR", cycR);
        tag.putFloat("hrColl", coll);
        tag.putBoolean("hrCcw", ccw);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
        copycat.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        copycat.load(tag, registries, level != null && level.isClientSide);
        lenIdx = tag.contains("hrLen") ? Mth.clamp(tag.getInt("hrLen"), 0, 2) : 1;
        blades = tag.contains("hrBlades") ? Mth.clamp(tag.getInt("hrBlades"), 2, 4) : 4;
        folded = tag.getBoolean("hrFold");
        float sp = Mth.clamp(tag.getFloat("hrSpool"), 0.0F, 1.0F);
        float cp = tag.getFloat("hrCycP"), cr = tag.getFloat("hrCycR"), co = Mth.clamp(tag.getFloat("hrColl"), 0.0F, 1.0F);
        if (level != null && level.isClientSide) { tSpool = sp; tCycP = cp; tCycR = cr; tColl = co; cTandemCcw = tag.getBoolean("hrCcw"); return; }
        spool = sp; cycP = cp; cycR = cr; coll = co;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        copycat.putSync(tag);
        return tag;
    }
}
