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

public class RfLinkBlockEntity extends ProcessUnitBlockEntity implements Owned, RadarLinked, CcAnimated, CopycatState.Host {

    public static final int ST_NO_LINK = 13, ST_JAMMED = 14;
    static final float YAW_SIGN = 1.0F, PITCH_SIGN = 1.0F;

    static final float PITCH_MIN = 0.0F;

    private static final Map<ResourceKey<Level>, Map<Integer, Set<Long>>> SENDERS = Statics.server(new HashMap<>());

    private final boolean sender;
    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();

    private final GenBuffer out = new GenBuffer(Balance.RF_BUFFER_FE, Balance.RF_OUT_FE, this::setChanged);
    private int channel, registeredChannel;

    private long budgetTick = Long.MIN_VALUE, lastLink = Long.MIN_VALUE / 2, jamCheck = Long.MIN_VALUE / 2;
    private int budgetLeft, partners, rateAcc, rateTicks, drawNow, drawLast;
    private double aimBest;
    private boolean enemyJam, dirty;
    private int state;
    private Vec3 aimAt;

    private boolean cLink;
    private int cPartners, cRate, cState;
    private float aimX, aimY, aimZ;

    private Vec3 shown, prevShown;

    public RfLinkBlockEntity(BlockPos pos, BlockState state) {
        super(isSender(state) ? RfPowerBlocks.SENDER_BE.get() : RfPowerBlocks.RECEIVER_BE.get(), pos, state, 0,
                isSender(state) ? Balance.RF_BUFFER_FE : 0, isSender(state) ? Balance.RF_SEND_FE : 0);
        this.sender = isSender(state);
    }

    private static boolean isSender(BlockState state) { return state.getBlock() instanceof RfLinkBlock b && b.sender; }

    public boolean isSender() { return sender; }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }

    @Override public int getFrequency() { return channel; }

    @Override
    public void setFrequency(int f) {
        channel = Mth.clamp(f, 0, 9999);
        if (level instanceof ServerLevel sl && sender) register(sl);
        sync();
    }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    private void register(ServerLevel sl) {
        if (registeredChannel != channel) unregister(sl);
        if (!AirGate.on() || channel <= 0 || isRemoved()) return;
        SENDERS.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(channel, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
        registeredChannel = channel;
    }

    private void unregister(ServerLevel sl) {
        if (registeredChannel <= 0) return;
        Map<Integer, Set<Long>> m = SENDERS.get(sl.dimension());
        if (m != null) {
            Set<Long> s = m.get(registeredChannel);
            if (s != null) { s.remove(worldPosition.asLong()); if (s.isEmpty()) m.remove(registeredChannel); }
            if (m.isEmpty()) SENDERS.remove(sl.dimension());
        }
        registeredChannel = 0;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) unregister(sl);
        super.setRemoved();
    }

    Vec3 headWorld() {
        return SableBridge.toWorld(level, plotPoint(0.0, 1.5, 0.0));
    }

    private boolean jammed(ServerLevel sl, long now) {
        if (now - jamCheck >= Balance.RF_JAM_TICKS) { jamCheck = now; enemyJam = PortableJammerBlockEntity.jams(sl, worldPosition, getOwner()); }
        return enemyJam || EmpField.jammed(sl, worldPosition);
    }

    int beam(int raw, long now, Vec3 to) {
        if (budgetTick != now) { budgetTick = now; budgetLeft = Balance.RF_SEND_FE; drawLast = drawNow; drawNow = 0; aimBest = Double.MAX_VALUE; }
        int n = Math.min(raw, Math.min(budgetLeft, energy().getEnergyStored()));
        if (n <= 0 || !useExtraEnergy(n)) return 0;
        budgetLeft -= n;
        rateAcc += n;
        flowAcc += n;
        lastLink = now;
        drawNow++;
        double d2 = to.distanceToSqr(headWorld());
        if (d2 < aimBest) { aimBest = d2; aimAt = to; }
        return n;
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        if (!AirGate.on()) { unregister(sl); state = ST_NO_LINK; finishTick(now); return false; }
        boolean did = sender ? senderTick(sl, now) : receiverTick(sl, now);
        finishTick(now);
        return did;
    }

    private boolean senderTick(ServerLevel sl, long now) {
        if ((now + (worldPosition.asLong() & 15L)) % Balance.RF_REG_TICKS == 0 || registeredChannel != channel) register(sl);
        partners = now - budgetTick <= 1 ? Math.max(drawNow, drawLast) : 0;
        if (jammed(sl, now)) { state = ST_JAMMED; return false; }
        state = now - lastLink <= Balance.RF_LINK_HOLD ? 1 : ST_NO_LINK;
        return now - lastLink <= 1;
    }

    private boolean receiverTick(ServerLevel sl, long now) {
        if (channel <= 0) { state = ST_NO_LINK; aimAt = null; partners = 0; return false; }
        if (jammed(sl, now)) { state = ST_JAMMED; partners = 0; return false; }
        Map<Integer, Set<Long>> m = SENDERS.get(sl.dimension());
        Set<Long> at = m == null ? null : m.get(channel);
        Vec3 me = headWorld();
        double range2 = (double) Balance.RF_RANGE * Balance.RF_RANGE;
        int want = Math.min(Balance.RF_OUT_FE, Balance.RF_BUFFER_FE - out.stored), got = 0, found = 0;
        Vec3 nearest = null;
        double best = Double.MAX_VALUE;
        if (at != null) {
            for (Long p : new ArrayList<>(at)) {
                BlockPos sp = BlockPos.of(p);
                if (!sl.isLoaded(sp) || !(sl.getBlockEntity(sp) instanceof RfLinkBlockEntity s) || s.isRemoved() || !s.sender || s.channel != channel) continue;
                if (!Access.joins(s, this) || s.jammed(sl, now)) continue;
                Vec3 sw = s.headWorld();
                double d2 = sw.distanceToSqr(me);
                if (d2 > range2) continue;
                found++;
                if (d2 < best) { best = d2; nearest = sw; }
                if (want - got <= 0) continue;
                int raw = (int) Math.min(Integer.MAX_VALUE, ((long) (want - got) * 100 + Balance.RF_EFFICIENCY - 1) / Balance.RF_EFFICIENCY);
                int sent = s.beam(raw, now, me);
                if (sent > 0) got += (int) ((long) sent * Balance.RF_EFFICIENCY / 100);
            }
        }
        partners = found;
        aimAt = nearest;
        if (got > 0) {
            out.stored = Math.min(Balance.RF_BUFFER_FE, out.stored + got);
            rateAcc += got;
            flowAcc += got;
            lastLink = now;
            setChanged();
        }
        if (found == 0) { state = ST_NO_LINK; return false; }
        state = want <= 0 ? 3 : 1;
        return got > 0;
    }

    private void finishTick(long now) {
        boolean link = state == 1 && now - lastLink <= Balance.RF_LINK_HOLD;
        if (++rateTicks >= 20) { rateTicks = 0; int r = rateAcc / 20; rateAcc = 0; if (r != cRate) { cRate = r; dirty = true; } }
        float ax = 0, ay = 0, az = 0;
        if (aimAt != null) {
            Vec3 d = aimAt.subtract(headWorld());
            double l = d.length();
            if (l > 1.0E-3) { ax = (float) (d.x / l); ay = (float) (d.y / l); az = (float) (d.z / l); }
        }
        boolean aimMoved = Math.abs(ax - aimX) + Math.abs(ay - aimY) + Math.abs(az - aimZ) > 0.02F;
        if (link != cLink && level != null) playOnce(level, WmdSounds.ASR_SWITCH.get(), Balance.SND_CLICK, link ? 1.2F : 0.8F);
        if (dirty || link != cLink || state != cState || partners != cPartners || (aimMoved && now % 5 == 0)) {
            dirty = false;
            cLink = link;
            cState = state;
            cPartners = partners;
            aimX = ax; aimY = ay; aimZ = az;
            sync();
        }
    }

    @Override
    public void empDrain() {
        super.empDrain();
        out.stored = 0;
        setChanged();
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        if (!at(part, side, 0, 0, 0, Direction.SOUTH)) return null;
        return sender ? energy() : out;
    }

    private static final PortMap SENDER_MAP = new PortMap(0, 0, 1).power(0, 0, 0, Direction.SOUTH, false);
    private static final PortMap RECEIVER_MAP = new PortMap(0, 0, 1).power(0, 0, 0, Direction.SOUTH, true);
    @Override public PortMap guiPortMap() { return sender ? SENDER_MAP : RECEIVER_MAP; }

    @Override public IEnergyStorage guiEnergy() { return sender ? energy() : out; }

    @Override
    public int guiStatus() {
        if (level instanceof ServerLevel sl && EmpField.jammed(sl, worldPosition)) return ST_JAMMED;
        if (cState == ST_JAMMED || cState == ST_NO_LINK || cState == 3) return cState;
        if (sender && energy().getEnergyStored() <= 0) return 2;
        return cLink ? 1 : ST_NO_LINK;
    }

    @Override
    public List<Component> guiInfo() {
        String ch = channel > 0 ? String.format("%04d", channel) : "----";
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable(sender ? "gui.wmd.info.rf_sender" : "gui.wmd.info.rf_receiver", ch,
                String.format("%,d", (sender ? Balance.RF_SEND_FE : Balance.RF_OUT_FE) * 20)));
        l.add(Component.translatable(sender ? "gui.wmd.info.rf_now_sender" : "gui.wmd.info.rf_now_receiver", cPartners, String.format("%,d", cRate * 20)));
        l.add(Component.translatable("gui.wmd.info.rf_rule", Balance.RF_EFFICIENCY, String.format("%,d", Balance.RF_RANGE)));
        return l;
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return sender ? Balance.RF_SEND_FE : Balance.RF_OUT_FE; }

    @Override
    protected String detail() {
        return (sender ? "sender" : "receiver") + ", channel " + channel + ", " + (sender ? energy().getEnergyStored() : out.stored) + " FE stored, "
                + partners + (sender ? " receivers" : " senders in range") + (state == ST_JAMMED ? ", JAMMED" : "");
    }

    @Override protected SoundEvent runSound() { return WmdSounds.CORE_HUM.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }

    public boolean linked() { return cLink; }

    static void clientTickRf(Level level, BlockPos pos, BlockState state, RfLinkBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        Vec3 target = new Vec3(be.aimX, be.aimY, be.aimZ);
        if (target.lengthSqr() < 0.25) { be.prevShown = be.shown; return; }
        if (be.shown == null) be.shown = target;
        be.prevShown = be.shown;
        Vec3 n = be.shown.add(target.subtract(be.shown).scale(Balance.RF_AIM_EASE));
        be.shown = n.lengthSqr() < 1.0E-6 ? target : n.normalize();
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean glow = cLink && isRunning() && !dark;
        model.getBone("glow").ifPresent(b -> b.setHidden(!glow));
        float[] yp = headAngles(pt);
        final float yaw = (float) Math.toRadians(YAW_SIGN * yp[0]), pitch = (float) Math.toRadians(PITCH_SIGN * yp[1]);
        model.getBone("yaw").ifPresent(b -> b.setRotY(yaw));
        model.getBone("pitch").ifPresent(b -> b.setRotX(pitch));
    }

    float[] headAngles(float pt) {
        if (shown == null) return new float[] { 0.0F, 20.0F };
        Vec3 w = prevShown == null ? shown : prevShown.add(shown.subtract(prevShown).scale(pt));
        Vec3 f = ShipFrame.dirInFrame(level, worldPosition, w, pt);
        double[] u = Machines.mountUnturn(Machines.mount(getBlockState()), f.x, f.y, f.z);
        double lx, lz;
        switch (facing()) {
            case EAST -> { lx = u[2]; lz = -u[0]; }
            case SOUTH -> { lx = -u[0]; lz = -u[2]; }
            case WEST -> { lx = -u[2]; lz = u[0]; }
            default -> { lx = u[0]; lz = u[2]; }
        }
        double h = Math.hypot(lx, lz);
        float yaw = h < 1.0E-6 ? 0.0F : (float) Math.toDegrees(Math.atan2(-lx, -lz));
        float pitch = (float) Math.toDegrees(Math.atan2(u[1], h));
        return new float[] { yaw, Math.max(PITCH_MIN, pitch) };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putInt("rfCh", channel);
        tag.putInt("rfOut", out.stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boolean client = level != null && level.isClientSide;
        ownerData.read(tag);
        copycat.load(tag, registries, client);
        channel = Mth.clamp(tag.getInt("rfCh"), 0, 9999);
        if (tag.contains("rfOut")) out.stored = Mth.clamp(tag.getInt("rfOut"), 0, Balance.RF_BUFFER_FE);
        if (tag.contains("rfLink")) {
            cLink = tag.getBoolean("rfLink");
            cPartners = tag.getInt("rfN");
            cRate = tag.getInt("rfRate");
            cState = tag.getInt("rfSt");
            aimX = tag.getFloat("rfAx");
            aimY = tag.getFloat("rfAy");
            aimZ = tag.getFloat("rfAz");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("rfCh", channel);
        tag.putInt("rfOut", out.stored);
        tag.putBoolean("rfLink", cLink);
        tag.putInt("rfN", cPartners);
        tag.putInt("rfRate", cRate);
        tag.putInt("rfSt", cState);
        tag.putFloat("rfAx", aimX);
        tag.putFloat("rfAy", aimY);
        tag.putFloat("rfAz", aimZ);
        return tag;
    }
}
