package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class AirSearchRadarBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, ModeMachine, RadarLinked, CopycatState.Host {

    public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 3, Z0 = 0, Z1 = 2;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 2, Direction.SOUTH, false);

    private static final double ANTENNA_Y = 3.4;
    private static final int NAME_MAX = 16;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private int frequency = 1;
    private boolean off;

    private ListTag contacts = new ListTag();

    private float yaw, prevYaw, spin;
    private long paintAt = Long.MIN_VALUE / 2;

    public AirSearchRadarBlockEntity(BlockPos pos, BlockState state) { super(AirBlocks.AIR_SEARCH_RADAR_BE.get(), pos, state, Balance.ASR_FE); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        sync();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        RadarEmit.note(this);
    }

    @Override public int mode() { return off ? 1 : 0; }
    @Override public int modeCount() { return 2; }
    @Override public void setMode(int mode) { boolean was = off; off = mode == 1; if (off) contacts = new ListTag(); if (was != off && level != null) playOnce(level, WmdSounds.ASR_SWITCH.get(), Balance.SND_CLICK, 1.0F); sync(); }
    @Override public void setModeClient(int mode) { off = mode == 1; }
    public boolean switchedOn() { return !off; }

    @Override public int getFrequency() { return frequency; }
    @Override public void setFrequency(int f) { frequency = Mth.clamp(f, 1, 9999); sync(); }

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

    public List<CompoundTag> contacts() {
        List<CompoundTag> l = new ArrayList<>();
        for (Tag t : contacts) if (t instanceof CompoundTag c) l.add(c);
        return l;
    }

    @Override
    protected boolean work() {
        if (!AirGate.on() || off) return false;
        if (level instanceof ServerLevel sl) {
            long now = sl.getGameTime();
            if ((now + (worldPosition.asLong() & 7L)) % Balance.ASR_SWEEP_TICKS == 0) sweep(sl, now);
        }
        return true;
    }

    @Override
    protected void idle() {
        if (!contacts.isEmpty()) { contacts = new ListTag(); sync(); }
        if (level instanceof ServerLevel sl) setAlarm(sl, 0);
    }

    private void sweep(ServerLevel sl, long now) {
        if (!AirGate.on()) return;
        UUID owner = getOwner();
        TrackNet.airBeat(sl, owner, frequency, now);
        Vec3 me = SableBridge.worldPos(sl, worldPosition).add(0.0, ANTENNA_Y, 0.0);
        UUID own = SableBridge.shipIdAt(sl, worldPosition);
        FlightManager fm = FlightManager.get(sl);
        ListTag out = new ListTag();
        int hostiles = 0;
        for (AirPicture.Seen s : AirPicture.seenFrom(sl, me, Balance.ASR_RANGE, own)) {
            if (out.size() >= Balance.ASR_MAX_CONTACTS) break;
            AirPicture.Aircraft a = s.a();
            Vec3 p = a.pos();
            double dx = p.x - me.x, dz = p.z - me.z, hd = Math.sqrt(dx * dx + dz * dz);
            if (hd > Balance.ASR_CLUTTER_RANGE && !Double.isNaN(a.agl()) && a.agl() < Balance.ASR_CLUTTER_AGL) continue;
            if (masked(sl, me, p)) continue;
            if (fm.chaffHides(me.x, me.z, p.x, p.z)) continue;
            ShipFaction.Relation rel = ShipFaction.relation(sl, a.id(), owner, this);
            if (rel == ShipFaction.Relation.HOSTILE && owner != null) {
                SableBridge.ShipInfo si = SableBridge.shipInfo(sl, a.id());
                if (si != null && si.isLarge()) TrackNet.reportShip(sl, owner, frequency, a.id(), now);
                else { TrackNet.reportAircraft(sl, owner, frequency, a.id(), now); CcContactWatch.aircraft(sl, owner, a, now); hostiles++; }
            }
            if (rel != ShipFaction.Relation.FRIENDLY) Rwr.search(sl, a.id(), me, owner, "air_search_radar", worldPosition.asLong(), now);
            CompoundTag c = new CompoundTag();
            c.putInt("x", (int) Math.round(dx));
            c.putInt("z", (int) Math.round(dz));
            c.putInt("y", (int) Math.round(p.y));
            c.putInt("r", rel.code());
            c.putInt("b", a.blocks());
            c.putInt("s", (int) Math.round(a.speedBps()));
            Vec3 v = a.vel();
            c.putInt("h", v.horizontalDistanceSqr() < 1.0E-6 ? -1 : (int) Math.round(Mth.wrapDegrees(Math.toDegrees(Math.atan2(v.x, -v.z))) + 360.0) % 360);
            String n = a.name() == null ? "" : a.name();
            c.putString("n", n.length() > NAME_MAX ? n.substring(0, NAME_MAX) : n);
            out.add(c);
        }

        int ms = 0;
        for (AirMissiles.Shot m : AirMissiles.inFlight(sl)) {
            if (m.pos == null || ms >= 16) continue;
            double dx = m.pos.x - me.x, dz = m.pos.z - me.z;
            if (dx * dx + dz * dz > Balance.ASR_RANGE * Balance.ASR_RANGE) continue;
            CompoundTag c = new CompoundTag();
            c.putInt("x", (int) Math.round(dx)); c.putInt("z", (int) Math.round(dz)); c.putInt("y", (int) Math.round(m.pos.y));
            c.putInt("r", -1); c.putBoolean("m", true);
            out.add(c);
            ms++;
        }
        for (AirRockets.Shot m : AirRockets.inFlight(sl)) {
            if (m.pos() == null || ms >= 16) continue;
            double dx = m.pos().x - me.x, dz = m.pos().z - me.z;
            if (dx * dx + dz * dz > Balance.ASR_RANGE * Balance.ASR_RANGE) continue;
            CompoundTag c = new CompoundTag();
            c.putInt("x", (int) Math.round(dx)); c.putInt("z", (int) Math.round(dz)); c.putInt("y", (int) Math.round(m.pos().y));
            c.putInt("r", -1); c.putBoolean("m", true);
            out.add(c);
            ms++;
        }
        for (Pallets.Fly f : Pallets.inAir(sl)) {
            if (ms >= 16) break;
            double dx = f.pos().x - me.x, dz = f.pos().z - me.z;
            if (dx * dx + dz * dz > Balance.ASR_RANGE * Balance.ASR_RANGE) continue;
            CompoundTag c = new CompoundTag();
            c.putInt("x", (int) Math.round(dx)); c.putInt("z", (int) Math.round(dz)); c.putInt("y", (int) Math.round(f.pos().y));
            c.putInt("r", -1); c.putBoolean("m", true); c.putBoolean("p", true);
            out.add(c);
            ms++;
        }
        setAlarm(sl, hostiles > 0 ? 15 : 0);
        if (out.equals(contacts)) return;
        contacts = out;
        sync();
    }

    private int alarm;

    public int alarmSignal() { return alarm; }

    private void setAlarm(ServerLevel sl, int s) {
        if (s == alarm) return;
        alarm = s;
        sl.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    static boolean masked(ServerLevel sl, Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z, d = Math.sqrt(dx * dx + dz * dz);
        int n = (int) (d / Balance.ASR_MASK_STEP);
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            int x = Mth.floor(from.x + dx * t), z = Mth.floor(from.z + dz * t);
            LevelChunk ch = sl.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (ch == null) continue;
            double y = from.y + (to.y - from.y) * t;
            if (ch.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1.0 > y) return true;
        }
        return false;
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null; }

    static void clientTickRadar(Level level, BlockPos pos, BlockState state, AirSearchRadarBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        be.tickHead(level.getGameTime());
    }

    private void tickHead(long now) {
        boolean on = isRunning();
        prevYaw = yaw;
        spin = Mth.clamp(spin + (on ? 1.0F / Balance.ASR_SPIN_UP : -1.0F / Balance.ASR_SPIN_DOWN), 0.0F, 1.0F);
        float step = 360.0F / Balance.ASR_SWEEP_TICKS * (on ? ease(spin) : spin * spin);
        if (!on && spin <= 0.0F) {
            float d = Mth.wrapDegrees(-yaw);
            step = Math.abs(d) < 0.5F ? d : Mth.clamp(d * 0.08F, -3.0F, 3.0F);
        }
        float before = yaw;
        yaw += step;
        if (on && spin > 0.9F && paints(before, yaw)) paintAt = now;
        if (yaw >= 360.0F) { yaw -= 360.0F; prevYaw -= 360.0F; }
        if (yaw < 0.0F) { yaw += 360.0F; prevYaw += 360.0F; }
    }

    public float beamBearing(float pt) {
        float facingYaw = (getBlockState().getValue(MachineBlock.FACING).toYRot() + 180.0F) % 360.0F;
        return facingYaw + prevYaw + Mth.wrapDegrees(yaw - prevYaw) * pt;
    }

    private static float ease(float t) { return t < 0.5F ? 2.0F * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 2) / 2.0F; }

    private boolean paints(float a, float b) {
        if (contacts.isEmpty()) return false;
        float facingYaw = (getBlockState().getValue(MachineBlock.FACING).toYRot() + 180.0F) % 360.0F;
        for (Tag t : contacts) {
            if (!(t instanceof CompoundTag c)) continue;
            float brg = (float) Math.toDegrees(Math.atan2(c.getInt("x"), -c.getInt("z")));
            float rel = Mth.wrapDegrees(brg - facingYaw);
            float da = Mth.wrapDegrees(rel - a), db = Mth.wrapDegrees(rel - b);
            if (da >= 0.0F && db <= 0.0F) return true;
        }
        return false;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final float y = prevYaw + Mth.wrapDegrees(yaw - prevYaw) * pt;
        model.getBone("yaw").ifPresent(b -> b.setRotY(CcSpecS52.CBR_YAW_SIGN * (float) Math.toRadians(y)));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        boolean on = isRunning();
        long beat = (long) tt - paintAt;
        final boolean glow = !dark && on && !(beat >= 0 && beat < 6 && (beat < 2 || beat >= 4));
        model.getBone("glow").ifPresent(b -> b.setHidden(!glow));
        final boolean lamp = !dark && on;
        model.getBone("lamp").ifPresent(b -> b.setHidden(!lamp));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.ASR_FE * 20),
                Component.translatable("gui.wmd.info.asr", String.format("%,d", (int) Balance.ASR_RANGE), Balance.ASR_SWEEP_TICKS / 20.0F));
    }

    @Override
    protected String detail() {
        return (off ? "switched off" : isRunning() ? "sweeping" : "no power") + ", " + contacts.size() + " contact(s), frequency " + frequency;
    }

    @Override protected SoundEvent runSound() { return WmdSounds.ASR_RUN.get(); }
    @Override protected void runStarted(Level level) { playOnce(level, WmdSounds.ASR_SPIN_UP.get(), Balance.SND_MECH, 1.0F); }
    @Override protected void runStopped(Level level) { playOnce(level, WmdSounds.ASR_SPIN_DOWN.get(), Balance.SND_MECH, 1.0F); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 2, 1); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putInt("asrFreq", frequency);
        tag.putBoolean("asrOff", off);
        tag.put("asrContacts", contacts);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        if (tag.contains("asrFreq")) frequency = Mth.clamp(tag.getInt("asrFreq"), 1, 9999);
        off = tag.getBoolean("asrOff");
        contacts = tag.contains("asrContacts") ? tag.getList("asrContacts", Tag.TAG_COMPOUND) : new ListTag();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("asrFreq", frequency);
        tag.putBoolean("asrOff", off);
        tag.put("asrContacts", contacts);
        return tag;
    }
}
