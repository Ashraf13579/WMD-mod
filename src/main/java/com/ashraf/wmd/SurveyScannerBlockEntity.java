package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class SurveyScannerBlockEntity extends ProcessUnitBlockEntity implements CcAnimated, CopycatState.Host, Owned {

    static final float RX = 1.0F, RY = 1.0F, RZ = 1.0F;

    private final CopycatState copycat = new CopycatState();
    private final OwnerData ownerData = new OwnerData();

    private UUID target;
    private long cycleAt = -1, lastScan = -1;
    private int faults = -1;

    private long cCycleAt = -1;
    private int cFaults = -1;

    public SurveyScannerBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.SURVEY_SCANNER_BE.get(), pos, state, Balance.SCANNER_FE);
    }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }
    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, net.minecraft.core.Direction side) {
        return side == Machines.dirToWorld(facing(), Machines.mount(getBlockState()), net.minecraft.core.Direction.UP) ? energy() : null;
    }

    UUID target() { return target; }

    int comparator() { return faults <= 0 ? 0 : Math.min(15, faults); }

    long cycleStart() { return level != null && level.isClientSide ? cCycleAt : cycleAt; }

    private UUID findCraft(ServerLevel sl) {
        UUID owner = getOwner();
        if (owner == null || !SableBridge.active()) return null;
        Vec3 c = SableBridge.worldPos(sl, worldPosition);
        UUID own = SableBridge.shipIdAt(sl, worldPosition);
        AirframeRecords d = AirframeRecords.get(sl.getServer());
        UUID best = null;
        double bd = Double.MAX_VALUE;
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, new AABB(c, c).inflate(Balance.SCANNER_RANGE))) {
            if (s.id().equals(own)) continue;
            AirframeRecords.Rec r = d.now(s.id());
            if (r == null || !ShipStrike.sameSide(owner, r.owner)) continue;
            if (!Crafts.parked(sl, s.id(), null, Balance.SCANNER_PARKED)) continue;
            double dd = s.worldCenter().distanceToSqr(c);
            if (dd < bd) { bd = dd; best = s.id(); }
        }
        return best;
    }

    boolean startScan(ServerLevel sl, UUID c) {
        if (cycleAt >= 0 || c == null) return false;
        target = c;
        cycleAt = sl.getGameTime();
        sync();
        return true;
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return false;
        long now = sl.getGameTime();
        if (cycleAt >= 0) {
            long t = now - cycleAt;
            if (t == Balance.SCANNER_RESULT_AT) result(sl);
            if (t >= Balance.SCANNER_CYCLE) { cycleAt = -1; sync(); return false; }
            return true;
        }
        if ((now + worldPosition.hashCode()) % 20 != 0) return false;
        UUID c = findCraft(sl);
        if (c == null) {
            if (target != null || faults >= 0) { target = null; faults = -1; sync(); comparatorChanged(); }
            return false;
        }
        if (!c.equals(target) || lastScan < 0 || now - lastScan >= Balance.SCANNER_RESCAN_TICKS) startScan(sl, c);
        return false;
    }

    private void result(ServerLevel sl) {
        lastScan = sl.getGameTime();
        int f = target == null ? -1 : AirframeRecords.faults(sl, target);
        if (f != faults) { faults = f; comparatorChanged(); }
        playOnce(sl, WmdSounds.SCANNER_PING.get(), Balance.SND_CLICK, f > 0 ? 0.8F : 1.2F);
        sync();
    }

    private void comparatorChanged() {
        if (level != null) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    void use(net.minecraft.server.level.ServerPlayer sp) {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return;
        UUID c = target != null && SableBridge.shipInfo(sl, target) != null ? target : findCraft(sl);
        if (c == null) { sp.displayClientMessage(status(), true); return; }
        if (cycleAt < 0 && hasPower()) startScan(sl, c);
        AirframeRecords.openFor(sp, c);
    }

    @Override
    public Component status() {
        Component head = Component.translatable("block.wmd.survey_scanner").append(": ");
        if (target == null) return head.copy().append(Component.translatable("message.wmd.survey_scanner.none", (int) Balance.SCANNER_RANGE));
        if (faults < 0) return head.copy().append(Component.translatable("message.wmd.survey_scanner.waiting"));
        return head.copy().append(faults == 0 ? Component.translatable("message.wmd.survey_scanner.ok") : Component.translatable("message.wmd.survey_scanner.faults", faults));
    }

    @Override
    protected void clientEffects(Level lv) {}

    private static float ramp(float t, float a, float b) { return ease(Math.max(0.0F, Math.min(1.0F, (t - a) / (b - a)))); }

    private static float ease(float x) { return x * x * (3.0F - 2.0F * x); }

    static float[] phases(float t) {
        if (t < 0 || t > Balance.SCANNER_CYCLE) return new float[] { 0.0F, 0.0F, 0.0F };
        float c = Balance.SCANNER_CYCLE, doorsIn = Balance.SCANNER_DOORS, armIn = Balance.SCANNER_ARM, res = Balance.SCANNER_RESULT_AT;
        float doors = t < c / 2 ? ramp(t, 0, doorsIn) : 1.0F - ramp(t, c - doorsIn, c);
        float arm = t < c / 2 ? ramp(t, doorsIn, armIn) : 1.0F - ramp(t, res, c - doorsIn);
        float scan = t >= armIn && t <= res ? 1.0F : 0.0F;
        return new float[] { doors, arm, scan };
    }

    float cycleTicks(float pt) {
        return level == null || cCycleAt < 0 ? -1.0F : (float) (level.getGameTime() - cCycleAt) + pt;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float t = cycleTicks(pt);
        float[] ph = phases(t);
        float door = (float) Math.toRadians(Balance.SCANNER_DOOR_OPEN * ph[0]);
        float arm = (float) Math.toRadians(Balance.SCANNER_ARM_DOWN * ph[1]);
        model.getBone("housing_door_w").ifPresent(b -> b.setRotZ(-RZ * door));
        model.getBone("housing_door_e").ifPresent(b -> b.setRotZ(RZ * door));
        model.getBone("scan_arm").ifPresent(b -> b.setRotX(RX * arm));
        model.getBone("scan_head").ifPresent(b -> b.setRotX(-RX * arm));
        float active = Math.max(0.0F, t - Balance.SCANNER_ARM);
        float yaw = t < 0 ? 0.0F : (float) Math.toRadians(Balance.SCANNER_YAW_DEG_PER_TICK * Math.min(active, Balance.SCANNER_RESULT_AT - Balance.SCANNER_ARM));
        model.getBone("scan_turret").ifPresent(b -> b.setRotY(RY * yaw));
        model.getBone("scan_drum").ifPresent(b -> b.setRotX(RX * (float) Math.toRadians(ph[2] > 0 ? (tt * 360.0 * 2.0) % 360.0 : 0.0)));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.RADAR_RUN.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected String detail() { return faults < 0 ? "-" : Integer.toString(faults); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        if (target != null) tag.putUUID("ssT", target);
        tag.putLong("ssAt", cycleAt);
        tag.putLong("ssLast", lastScan);
        tag.putInt("ssF", faults);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        cCycleAt = tag.contains("ssAt") ? tag.getLong("ssAt") : -1;
        cFaults = tag.contains("ssF") ? tag.getInt("ssF") : -1;
        if (level != null && level.isClientSide) return;
        target = tag.hasUUID("ssT") ? tag.getUUID("ssT") : null;
        cycleAt = cCycleAt;
        lastScan = tag.getLong("ssLast");
        faults = cFaults;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putLong("ssAt", cycleAt);
        tag.putInt("ssF", faults);
        return tag;
    }
}
