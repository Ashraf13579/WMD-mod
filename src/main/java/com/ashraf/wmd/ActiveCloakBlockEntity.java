package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class ActiveCloakBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, ModeMachine, CopycatState.Host {

    public static final int ST_OFF = 0, ST_STARTING = 1, ST_UP = 2, ST_RECHARGE = 3, ST_NO_POWER = 4, ST_TOO_BIG = 5, ST_JAMMED = 6;
    static final float PETAL_SIGN = 1.0F, RING_A_SIGN = 1.0F, RING_B_SIGN = 1.0F;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();

    private boolean on, sig;
    private long startAt = -1L, rechargeUntil = Long.MIN_VALUE / 2;

    private int state = ST_OFF;
    private long cStart = -1L;

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> ABOARD = Statics.server(new HashMap<>());

    private float stage, prevStage, spinA, prevSpinA, spinB, prevSpinB, spinF;

    public ActiveCloakBlockEntity(BlockPos pos, BlockState state) {
        super(CloakBlocks.CLOAK_BE.get(), pos, state, 0, Balance.CLOAK_BUFFER_FE, Balance.CLOAK_RECEIVE_FE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }

    @Override public int mode() { return on ? 1 : 0; }
    @Override public int modeCount() { return 2; }
    @Override public void setMode(int mode) { setOn(mode == 1); }
    @Override public void setModeClient(int mode) { on = mode == 1; }
    public boolean switchedOn() { return on; }
    public int cloakState() { return state; }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

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

    void setOn(boolean v) {
        if (v == on) return;
        on = v;
        if (level != null && !level.isClientSide) playOnce(level, WmdSounds.ASR_SWITCH.get(), Balance.SND_CLICK, v ? 1.1F : 0.9F);
        sync();
    }

    Vec3 centre() { return SableBridge.toWorld(level, plotPoint(0.0, 1.5, 0.0)); }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0) {
            UUID at = SableBridge.shipIdAt(sl, worldPosition);
            if (at != null) ABOARD.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(at, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
        }
        boolean s = sl.hasNeighborSignal(worldPosition) || sl.hasNeighborSignal(worldPosition.above());
        if (s && !sig) setOn(!on);
        sig = s;
        if (!AirGate.on() || !on) { stop(sl, false, ST_OFF); return false; }
        if (now < rechargeUntil) { setState(ST_RECHARGE); return false; }
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        if (ship != null && EmpBlackout.out(sl, ship)) { jammed(sl); return false; }
        boolean paid = ship != null && ShipPower.draw(this, sl, ship, Balance.CLOAK_FE);
        if (!paid && useExtraEnergy(Balance.CLOAK_FE)) { paid = true; PowerMeter.noteIn(this, Balance.CLOAK_FE); }
        if (!paid) { stop(sl, startAt >= 0, ST_NO_POWER); return false; }
        if (startAt < 0) {
            startAt = now;
            playOnce(sl, WmdSounds.BAY_HATCH.get(), Balance.SND_MECH, 1.0F);
            setState(ST_STARTING);
        }
        if (now - startAt < Balance.CLOAK_ENGAGE_TICKS) { setState(ST_STARTING); return true; }
        if (state != ST_UP && state != ST_TOO_BIG) playOnce(sl, WmdSounds.CLOAK_ENGAGE.get(), Balance.SND_MECH, 1.0F);
        if (ship != null) {
            SableBridge.ShipInfo si = SableBridge.shipInfo(sl, ship);
            Cloak.engaged(sl, worldPosition, ship, this, centre(), now);
            setState(si != null && !Cloak.hidden(sl, ship) && now - startAt > Balance.CLOAK_ENGAGE_TICKS + 2 ? ST_TOO_BIG : ST_UP);
        } else {
            Cloak.engaged(sl, worldPosition, null, this, centre(), now);
            setState(ST_UP);
        }
        return true;
    }

    public static int toggleAll(ServerLevel sl, UUID ship) {
        if (!AirGate.on() || sl == null || ship == null) return -1;
        Map<UUID, Set<Long>> m = ABOARD.get(sl.dimension());
        Set<Long> set = m == null ? null : m.get(ship);
        if (set == null) return -1;
        List<ActiveCloakBlockEntity> l = new ArrayList<>();
        for (java.util.Iterator<Long> it = set.iterator(); it.hasNext(); ) {
            BlockPos bp = BlockPos.of(it.next());
            if (sl.isLoaded(bp) && sl.getBlockEntity(bp) instanceof ActiveCloakBlockEntity c && !c.isRemoved() && ship.equals(SableBridge.shipIdAt(sl, bp))) l.add(c);
            else it.remove();
        }
        if (set.isEmpty()) m.remove(ship);
        if (l.isEmpty()) return -1;
        boolean any = false;
        for (ActiveCloakBlockEntity c : l) any |= c.on;
        for (ActiveCloakBlockEntity c : l) c.setOn(!any);
        return any ? 0 : 1;
    }

    private void stop(ServerLevel sl, boolean recharge, int st) {
        boolean was = startAt >= 0;
        if (was) {
            Cloak.dropped(sl, worldPosition);
            playOnce(sl, WmdSounds.CLOAK_DROP.get(), Balance.SND_MECH, 1.0F);
        }
        startAt = -1L;
        if (recharge && was) rechargeUntil = sl.getGameTime() + Balance.CLOAK_RECHARGE_TICKS;
        setState(st);
    }

    @Override
    protected void jammed(ServerLevel sl) { if (startAt >= 0) stop(sl, true, ST_JAMMED); else setState(ST_JAMMED); }

    private void setState(int s) {
        if (s == state && cStart == startAt) return;
        state = s;
        cStart = startAt;
        sync();
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) Cloak.dropped(sl, worldPosition);
        super.setRemoved();
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.SOUTH) ? energy() : null; }

    private static final PortMap PORT_MAP = new PortMap(0, 0, 1).power(0, 0, 0, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public int guiStatus() { return state == ST_UP ? 1 : state == ST_NO_POWER ? 2 : 0; }

    @Override
    public List<Component> guiInfo() {
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable("gui.wmd.info.cloak_use", String.format("%,d", Balance.CLOAK_FE)));
        l.add(Component.translatable("gui.wmd.info.cloak_rule", String.format("%,d", Balance.CLOAK_COVER_BLOCKS), (int) Balance.CLOAK_FIELD_R));
        return l;
    }

    public float startProgress() {
        if (level == null || cStart < 0) return state == ST_UP ? 1.0F : 0.0F;
        return Mth.clamp((level.getGameTime() - cStart) / (float) Balance.CLOAK_ENGAGE_TICKS, 0.0F, 1.0F);
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return Balance.CLOAK_FE; }

    @Override
    protected String detail() {
        String[] n = { "off", "starting", "CLOAKED", "recharging", "no power", "craft too big for its cloaks", "EMP jammed" };
        return (on ? "on" : "off") + ", " + n[Mth.clamp(state, 0, n.length - 1)] + (level instanceof ServerLevel sl ? ", " + Cloak.count(sl) + " hidden in this dimension" : "");
    }

    @Override protected SoundEvent runSound() { return WmdSounds.CLOAK_HUM_LOOP.get(); }
    @Override protected boolean soundAlive() { return state == ST_UP || state == ST_TOO_BIG; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }

    static void clientTickCloak(Level level, BlockPos pos, BlockState st, ActiveCloakBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, st, be);
        be.prevStage = be.stage;
        be.prevSpinA = be.spinA;
        be.prevSpinB = be.spinB;
        boolean up = be.cStart >= 0;
        float target = up ? Math.min(Balance.CLOAK_ENGAGE_TICKS, level.getGameTime() - be.cStart) : 0.0F;
        be.stage = up ? Math.max(be.stage, Math.min(target, be.stage + 1.0F)) : Math.max(0.0F, be.stage - 1.5F);
        float spinStart = Balance.CLOAK_PETAL_TICKS + Balance.CLOAK_LIFT_TICKS + Balance.CLOAK_MAST_TICKS;
        be.spinF = Mth.clamp((be.stage - spinStart) / Balance.CLOAK_SPIN_TICKS, 0.0F, 1.0F);
        be.spinA += Balance.CLOAK_RING_A * be.spinF;
        be.spinB += Balance.CLOAK_RING_B * be.spinF;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float s = prevStage + (stage - prevStage) * pt;
        float pet = ease(Mth.clamp(s / Balance.CLOAK_PETAL_TICKS, 0.0F, 1.0F));
        float lift = ease(Mth.clamp((s - Balance.CLOAK_PETAL_TICKS) / Balance.CLOAK_LIFT_TICKS, 0.0F, 1.0F));
        float mast = ease(Mth.clamp((s - Balance.CLOAK_PETAL_TICKS - Balance.CLOAK_LIFT_TICKS) / Balance.CLOAK_MAST_TICKS, 0.0F, 1.0F));
        float spinStart = Balance.CLOAK_PETAL_TICKS + Balance.CLOAK_LIFT_TICKS + Balance.CLOAK_MAST_TICKS;
        float sf = Mth.clamp((s - spinStart) / Balance.CLOAK_SPIN_TICKS, 0.0F, 1.0F);
        float a = prevSpinA + (spinA - prevSpinA) * pt, b = prevSpinB + (spinB - prevSpinB) * pt;
        float amp = (float) Math.toRadians(Balance.CLOAK_RING_AMP) * sf;
        final float petal = PETAL_SIGN * (float) Math.toRadians(90.0F * pet);
        model.getBone("housing_pe").ifPresent(bn -> bn.setRotZ(petal));
        model.getBone("housing_pw").ifPresent(bn -> bn.setRotZ(-petal));
        final float ly = Balance.CLOAK_LIFT_PX * lift, my = Balance.CLOAK_MAST_PX * mast;
        model.getBone("lift").ifPresent(bn -> bn.setPosY(ly));
        model.getBone("mast").ifPresent(bn -> bn.setPosY(my));
        final float ra = RING_A_SIGN * amp * Mth.sin(a), rb = RING_B_SIGN * amp * Mth.sin(b + 1.0F);
        model.getBone("gim_a").ifPresent(bn -> bn.setRotZ(ra));
        model.getBone("gim_b").ifPresent(bn -> bn.setRotX(rb));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean lens = !dark && sf > 0.05F;
        model.getBone("glow_a").ifPresent(bn -> bn.setHidden(!lens));
        model.getBone("glow_b").ifPresent(bn -> bn.setHidden(!lens));
        long t = (long) tt;
        final boolean tip = !dark && mast > 0.5F && (state == ST_UP || t % 10 < 6);
        model.getBone("tip").ifPresent(bn -> bn.setHidden(!tip));
        final boolean lamp = !dark && (state == ST_UP || ((state == ST_STARTING || state == ST_RECHARGE) && t % 20 < 10));
        model.getBone("lamp").ifPresent(bn -> bn.setHidden(!lamp));
    }

    private static float ease(float t) { return t < 0.5F ? 2.0F * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 2) / 2.0F; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putBoolean("clkOn", on);
        tag.putLong("clkStart", startAt);
        tag.putLong("clkRecharge", rechargeUntil);
        tag.putInt("clkState", state);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        on = tag.getBoolean("clkOn");
        if (tag.contains("clkStart")) startAt = tag.getLong("clkStart");
        if (tag.contains("clkRecharge")) rechargeUntil = tag.getLong("clkRecharge");
        state = tag.getInt("clkState");
        if (tag.contains("clkCStart")) cStart = tag.getLong("clkCStart");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putBoolean("clkOn", on);
        tag.putInt("clkState", state);
        tag.putLong("clkCStart", cStart);
        return tag;
    }
}
