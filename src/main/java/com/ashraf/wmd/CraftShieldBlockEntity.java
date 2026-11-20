package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class CraftShieldBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, ModeMachine, CopycatState.Host {

    public static final int ST_OFF = 0, ST_RISING = 1, ST_UP = 2, ST_COOLDOWN = 3, ST_NO_POWER = 4, ST_TOO_BIG = 5, ST_NO_CRAFT = 6, ST_JAMMED = 7;

    static final float FIN_SIGN = 1.0F, RING_SIGN = 1.0F;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();

    private boolean on, sig;
    private long riseAt = -1L, coolUntil = Long.MIN_VALUE / 2;

    private int state = ST_OFF, pct;
    private long cRise = -1L, cCool = Long.MIN_VALUE / 2;
    private UUID ship;

    private float stage, prevStage, spin, prevSpin, spinF;

    public CraftShieldBlockEntity(BlockPos pos, BlockState state) {
        super(IonBlocks.SHIELD_BE.get(), pos, state, 0, Balance.CSH_BUFFER_FE, Balance.CSH_RECEIVE_FE);
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
    public int shieldState() { return state; }

    public int corePct() { return pct; }

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

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        boolean s = sl.hasNeighborSignal(worldPosition) || sl.hasNeighborSignal(worldPosition.above());
        if (s && !sig) setOn(!on);
        sig = s;
        UUID sh = SableBridge.shipIdAt(sl, worldPosition);
        if (!java.util.Objects.equals(sh, ship)) { if (ship != null) { CraftShield.dropped(sl, ship, worldPosition); CraftShield.left(sl, ship, worldPosition); } ship = sh; }
        if (ship != null) CraftShield.aboard(sl, ship, worldPosition);
        if (now % Balance.CSH_GAUGE_TICKS == (worldPosition.asLong() & 7)) gauge(sl);
        if (!AirGate.on() || !on) { stop(sl, false, ST_OFF); return false; }
        if (ship == null) { stop(sl, false, ST_NO_CRAFT); return false; }
        if (EmpBlackout.out(sl, ship)) { jammed(sl); return false; }
        long cool = Math.max(coolUntil, CraftShield.coolUntil(ship));
        if (now < cool) { stop(sl, false, ST_COOLDOWN); if (cCool != cool) { cCool = cool; coolUntil = cool; sync(); } return false; }
        int[] ch = ShipPower.coreCharge(sl, ship);
        boolean floorOk = ch[1] > 0 && ch[0] >= ch[1] * Balance.CSH_FLOOR;
        boolean paid = floorOk && ShipPower.draw(this, sl, ship, Balance.CSH_FE);
        if (!paid && floorOk && useExtraEnergy(Balance.CSH_FE)) { paid = true; PowerMeter.noteIn(this, Balance.CSH_FE); }
        if (!paid) { stop(sl, false, ST_NO_POWER); return false; }
        if (riseAt < 0) {
            riseAt = now;
            playOnce(sl, WmdSounds.BAY_HATCH.get(), Balance.SND_MECH, 1.15F);
            setState(ST_RISING);
        }
        if (now - riseAt < Balance.CSH_RISE_TICKS) { setState(ST_RISING); return true; }
        if (state != ST_UP && state != ST_TOO_BIG) playOnce(sl, WmdSounds.FF_DOME_UP.get(), Balance.SND_MECH, 1.4F);
        setState(CraftShield.report(sl, ship, worldPosition, now) == CraftShield.ST_TOO_BIG ? ST_TOO_BIG : ST_UP);
        return true;
    }

    private void gauge(ServerLevel sl) {
        int[] ch = ship == null ? new int[] { 0, 0 } : ShipPower.coreCharge(sl, ship);
        int p = ch[1] <= 0 ? 0 : (int) Math.round(100.0 * ch[0] / ch[1]);
        if (Math.abs(p - pct) >= 2 || (p == 0) != (pct == 0)) { pct = p; sync(); }
    }

    private void stop(ServerLevel sl, boolean cool, int st) {
        boolean was = riseAt >= 0;
        if (was) {
            if (ship != null) CraftShield.dropped(sl, ship, worldPosition);
            playOnce(sl, WmdSounds.HATCH_OPEN.get(), Balance.SND_MECH, 0.8F);
        }
        riseAt = -1L;
        if (cool && was) { coolUntil = sl.getGameTime() + Balance.CSH_COOLDOWN; cCool = coolUntil; }
        setState(st);
    }

    @Override
    protected void jammed(ServerLevel sl) { if (riseAt >= 0) stop(sl, true, ST_JAMMED); else setState(ST_JAMMED); }

    private void setState(int s) {
        if (s == state && cRise == riseAt) return;
        state = s;
        cRise = riseAt;
        sync();
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl && ship != null) { CraftShield.dropped(sl, ship, worldPosition); CraftShield.left(sl, ship, worldPosition); }
        super.setRemoved();
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).fromZ(-1).power(0, 0, 1, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public int guiStatus() { return state == ST_UP ? 1 : state == ST_NO_POWER ? 2 : 0; }

    @Override
    public List<Component> guiInfo() {
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable("gui.wmd.info.csh_use", String.format("%,d", Balance.CSH_FE)));
        l.add(Component.translatable("gui.wmd.info.csh_rule", Math.round(Balance.CSH_FLOOR * 100.0)));
        return l;
    }

    public float riseProgress() {
        if (level == null || cRise < 0) return state == ST_UP || state == ST_TOO_BIG ? 1.0F : 0.0F;
        return Mth.clamp((level.getGameTime() - cRise) / (float) Balance.CSH_RISE_TICKS, 0.0F, 1.0F);
    }

    public float coolLeft() {
        if (level == null) return 0.0F;
        return Mth.clamp((cCool - level.getGameTime()) / (float) Balance.CSH_COOLDOWN, 0.0F, 1.0F);
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return Balance.CSH_FE; }

    @Override
    protected String detail() {
        String[] n = { "off", "rising", "UP", "cooling down", "no power (core under the floor)", "craft too big for a bubble", "not on a craft", "EMP jammed" };
        return (on ? "on" : "off") + ", " + n[Mth.clamp(state, 0, n.length - 1)] + ", core " + pct + " %"
                + (level instanceof ServerLevel sl ? ", " + CraftShield.count(sl) + " bubbles up in this dimension" : "");
    }

    @Override protected SoundEvent runSound() { return WmdSounds.SHIELD_HUM_LOOP.get(); }
    @Override protected boolean soundAlive() { return state == ST_UP; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }
    @Override protected float soundPitch() { return 1.0F; }

    static void clientTickShield(Level level, BlockPos pos, BlockState st, CraftShieldBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, st, be);
        be.prevStage = be.stage;
        be.prevSpin = be.spin;
        boolean up = be.cRise >= 0;
        float target = up ? Math.min(Balance.CSH_RISE_TICKS, level.getGameTime() - be.cRise) : 0.0F;
        be.stage = up ? Math.max(be.stage, Math.min(target, be.stage + 1.0F)) : Math.max(0.0F, be.stage - 1.5F);
        float s = be.stage / Balance.CSH_RISE_TICKS;
        be.spinF = up ? Mth.clamp((s - 0.4F) / 0.6F, 0.0F, 1.0F) : Math.max(0.0F, be.spinF - 0.02F);
        be.spin += Balance.CSH_RING_SPIN * be.spinF;
        if (be.spin > 3600.0F) { be.spin -= 3600.0F; be.prevSpin -= 3600.0F; }
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float s = (prevStage + (stage - prevStage) * pt) / Balance.CSH_RISE_TICKS;
        float fold = ease(Mth.clamp(s / 0.45F, 0.0F, 1.0F));
        final float fin = FIN_SIGN * (float) Math.toRadians(Balance.CSH_FIN_DEG * fold);
        for (int k = 0; k < 6; k++) model.getBone("fin" + k).ifPresent(b -> b.setRotX(fin));
        final float lift = Balance.CSH_RING_LIFT_PX * ease(Mth.clamp((s - 0.3F) / 0.3F, 0.0F, 1.0F));
        final float spinNow = RING_SIGN * (float) Math.toRadians(prevSpin + (spin - prevSpin) * pt);
        model.getBone("ring").ifPresent(b -> { b.setPosY(lift); b.setRotY(spinNow); });
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        long t = (long) tt;
        for (int k = 0; k < 3; k++) {
            final boolean lit = !dark && (state == ST_UP || state == ST_TOO_BIG || (state == ST_RISING && s >= 0.2F + 0.2F * k));
            model.getBone("coil" + k).ifPresent(b -> b.setHidden(!lit));
        }
        final boolean crystal = !dark && state == ST_UP && t % 30 < 26;
        model.getBone("crystal").ifPresent(b -> b.setHidden(!crystal));
        boolean blink = state == ST_COOLDOWN && t % 20 < 10;
        for (int k = 0; k < 5; k++) {
            final boolean lit = !dark && !blink && state != ST_OFF && pct > 10 + 18 * k;
            model.getBone("bar" + k).ifPresent(b -> b.setHidden(!lit));
        }
        final boolean lamp = !dark && (state == ST_UP || ((state == ST_RISING || state == ST_COOLDOWN) && t % 20 < 10));
        model.getBone("lamp").ifPresent(b -> b.setHidden(!lamp));
    }

    private static float ease(float t) { return t < 0.5F ? 2.0F * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 2) / 2.0F; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putBoolean("cshOn", on);
        tag.putLong("cshRise", riseAt);
        tag.putLong("cshCool", coolUntil);
        tag.putInt("cshState", state);
        tag.putInt("cshPct", pct);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        on = tag.getBoolean("cshOn");
        if (tag.contains("cshRise")) riseAt = tag.getLong("cshRise");
        if (tag.contains("cshCool")) coolUntil = tag.getLong("cshCool");
        state = tag.getInt("cshState");
        pct = tag.getInt("cshPct");
        if (tag.contains("cshCRise")) cRise = tag.getLong("cshCRise");
        if (tag.contains("cshCCool")) cCool = tag.getLong("cshCCool");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putBoolean("cshOn", on);
        tag.putInt("cshState", state);
        tag.putInt("cshPct", pct);
        tag.putLong("cshCRise", cRise);
        tag.putLong("cshCCool", cCool);
        return tag;
    }
}
