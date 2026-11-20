package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Comparator;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class QuantumRadarBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, ModeMachine, RadarLinked {

    public static final int X0 = -2, X1 = 2, Y0 = 0, Y1 = 8, Z0 = 0, Z1 = 4;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 4, Direction.SOUTH, false);

    static final double HEAD_Y = 7.4, HEAD_Z = 2.0;
    static final float PANEL_SIGN = 1.0F, YAW_SIGN = CcSpecS52.CBR_YAW_SIGN;
    private static final int NAME_MAX = 16;

    private final OwnerData ownerData = new OwnerData();
    private int frequency = 1;
    private boolean off;
    private ListTag contacts = new ListTag();

    private float yaw, prevYaw, spin, open, prevOpen;
    private long hitAt = Long.MIN_VALUE / 2;
    private int lastCount;

    public QuantumRadarBlockEntity(BlockPos pos, BlockState state) { super(CloakBlocks.QRADAR_BE.get(), pos, state, Balance.QR_FE); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }

    @Override public int mode() { return off ? 1 : 0; }
    @Override public int modeCount() { return 2; }
    @Override
    public void setMode(int mode) {
        boolean was = off;
        off = mode == 1;
        if (off) contacts = new ListTag();
        if (was != off && level != null) playOnce(level, WmdSounds.ASR_SWITCH.get(), Balance.SND_CLICK, 1.0F);
        sync();
    }
    @Override public void setModeClient(int mode) { off = mode == 1; }
    public boolean switchedOn() { return !off; }

    @Override public int getFrequency() { return frequency; }
    @Override public void setFrequency(int f) { frequency = Mth.clamp(f, 1, 9999); sync(); }

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
    public void onLoad() {
        super.onLoad();
        RadarEmit.note(this);
    }

    @Override
    protected boolean work() {
        if (!AirGate.on() || off) return false;
        if (level instanceof ServerLevel sl) {
            long now = sl.getGameTime();
            if ((now + (worldPosition.asLong() & 7L)) % Balance.QR_SWEEP_TICKS == 0) sweep(sl, now);
        }
        return true;
    }

    @Override
    protected void idle() { if (!contacts.isEmpty()) { contacts = new ListTag(); sync(); } }

    private void sweep(ServerLevel sl, long now) {
        if (!AirGate.on()) return;
        UUID owner = getOwner();
        TrackNet.airBeat(sl, owner, frequency, now);
        Vec3 me = SableBridge.toWorld(sl, plotPoint(0.0, HEAD_Y, HEAD_Z));
        UUID own = SableBridge.shipIdAt(sl, worldPosition);
        List<Cloak.Ghost> seen = new ArrayList<>();
        for (Cloak.Ghost g : Cloak.ghosts(sl)) {
            if (!g.player() && g.id().equals(own)) continue;
            double r = g.player() ? Balance.QR_PLAYER_RANGE : Balance.QR_RANGE;
            if (g.pos().distanceToSqr(me) > r * r) continue;
            seen.add(g);
        }
        seen.sort(Comparator.comparingDouble(g -> g.pos().distanceToSqr(me)));
        ListTag out = new ListTag();
        for (Cloak.Ghost g : seen) {
            if (out.size() >= Balance.QR_MAX_CONTACTS) break;
            ShipFaction.Relation rel;
            if (g.player()) {
                Player p = sl.getPlayerByUUID(g.id());
                rel = p != null && DefenceTargets.trusted(sl, this, p) ? ShipFaction.Relation.FRIENDLY : ShipFaction.Relation.HOSTILE;
            } else rel = ShipFaction.relation(sl, g.id(), owner, this);
            if (rel != ShipFaction.Relation.FRIENDLY && owner != null) {
                Cloak.reveal(sl, g.id(), owner, now);
                if (rel == ShipFaction.Relation.HOSTILE && !g.player()) {
                    SableBridge.ShipInfo si = SableBridge.shipInfo(sl, g.id());
                    if (si != null && si.isLarge()) TrackNet.reportShip(sl, owner, frequency, g.id(), now);
                    else TrackNet.reportAircraft(sl, owner, frequency, g.id(), now);
                }
            }
            Vec3 p = g.pos();
            double dx = p.x - me.x, dz = p.z - me.z;
            CompoundTag c = new CompoundTag();
            c.putInt("x", (int) Math.round(dx));
            c.putInt("z", (int) Math.round(dz));
            c.putInt("y", (int) Math.round(p.y));
            c.putInt("r", rel.code());
            c.putInt("b", g.blocks());
            Vec3 v = g.vel();
            c.putInt("s", (int) Math.round(v.length() * 20.0));
            c.putInt("h", v.horizontalDistanceSqr() < 1.0E-6 ? -1 : (int) Math.round(Mth.wrapDegrees(Math.toDegrees(Math.atan2(v.x, -v.z))) + 360.0) % 360);
            String n = g.name() == null ? "" : g.name();
            c.putString("n", n.length() > NAME_MAX ? n.substring(0, NAME_MAX) : n);
            if (g.player()) c.putBoolean("pl", true);
            out.add(c);
        }
        if (out.equals(contacts)) return;
        contacts = out;
        sync();
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 4, Direction.SOUTH) ? energy() : null; }

    static void clientTickQr(Level level, BlockPos pos, BlockState state, QuantumRadarBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        be.tickHead(level, level.getGameTime());
    }

    private void tickHead(Level level, long now) {
        boolean on = isRunning();
        prevOpen = open;
        float total = Balance.QR_PANEL_TICKS + 3 * Balance.QR_PANEL_STAGGER;
        open = Mth.clamp(open + (on ? 1.0F : -1.0F), 0.0F, total);
        prevYaw = yaw;
        boolean panelsOut = open >= total;
        spin = Mth.clamp(spin + (on && panelsOut ? 1.0F / Balance.QR_SPIN_UP : -1.0F / Balance.QR_SPIN_DOWN), 0.0F, 1.0F);
        float step = 360.0F / Balance.QR_SWEEP_TICKS * spin * spin;
        if (!on && spin <= 0.0F) {
            float d = Mth.wrapDegrees(-yaw);
            step = Math.abs(d) < 0.5F ? d : Mth.clamp(d * 0.08F, -3.0F, 3.0F);
        }
        float before = yaw;
        yaw += step;
        if (on && spin > 0.9F) {
            Vec3 head = ShipFrame.drawnWorld(level, worldPosition, plotPoint(0.0, 8.9, HEAD_Z), Float.NaN);
            float facingYaw = (getBlockState().getValue(MachineBlock.FACING).toYRot() + 180.0F) % 360.0F;
            for (Tag t : contacts) {
                if (!(t instanceof CompoundTag c)) continue;
                float brg = (float) Math.toDegrees(Math.atan2(c.getInt("x"), -c.getInt("z")));
                float rel = Mth.wrapDegrees(brg - facingYaw);
                if (Mth.wrapDegrees(rel - before) >= 0.0F && Mth.wrapDegrees(rel - yaw) <= 0.0F) {
                    hitAt = now;
                    Vec3 to = new Vec3(head.x + c.getInt("x"), c.getInt("y"), head.z + c.getInt("z"));
                    QuantumRadarFx.pair(head, to, now, true);
                    level.playLocalSound(head.x, head.y, head.z, WmdSounds.SCANNER_PING.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_CLICK, 1.4F, false);
                }
            }
            float sweepRel = Mth.wrapDegrees(-before);
            if (sweepRel >= 0.0F && Mth.wrapDegrees(-yaw) <= 0.0F) {
                double a = Math.toRadians(facingYaw);
                QuantumRadarFx.pair(head, head.add(Math.sin(a) * 160.0, 20.0, -Math.cos(a) * 160.0), now, false);
            }
        }
        if (contacts.size() > lastCount && isRunning()) {
            Vec3 w = ShipFrame.drawnWorld(level, worldPosition, plotPoint(0.0, 7.0, HEAD_Z), Float.NaN);
            level.playLocalSound(w.x, w.y, w.z, WmdSounds.QR_CONTACT.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F, false);
        }
        lastCount = contacts.size();
        if (yaw >= 360.0F) { yaw -= 360.0F; prevYaw -= 360.0F; }
        if (yaw < 0.0F) { yaw += 360.0F; prevYaw += 360.0F; }
    }

    public float beamBearing(float pt) {
        float facingYaw = (getBlockState().getValue(MachineBlock.FACING).toYRot() + 180.0F) % 360.0F;
        return facingYaw + prevYaw + Mth.wrapDegrees(yaw - prevYaw) * pt;
    }

    private static float ease(float t) { t = Mth.clamp(t, 0.0F, 1.0F); return t < 0.5F ? 2.0F * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 2) / 2.0F; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final float y = prevYaw + Mth.wrapDegrees(yaw - prevYaw) * pt;
        model.getBone("yaw").ifPresent(b -> b.setRotY(YAW_SIGN * (float) Math.toRadians(y)));
        float o = prevOpen + (open - prevOpen) * pt;
        String[] order = { "n", "e", "s", "w" };
        for (int k = 0; k < 4; k++) {
            final float a = PANEL_SIGN * (float) Math.toRadians(Balance.QR_PANEL_OPEN * ease((o - k * Balance.QR_PANEL_STAGGER) / Balance.QR_PANEL_TICKS));
            switch (order[k]) {
                case "n" -> model.getBone("panel_n").ifPresent(b -> b.setRotX(-a));
                case "s" -> model.getBone("panel_s").ifPresent(b -> b.setRotX(a));
                case "e" -> model.getBone("panel_e").ifPresent(b -> b.setRotZ(-a));
                default -> model.getBone("panel_w").ifPresent(b -> b.setRotZ(a));
            }
        }
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        boolean on = isRunning();
        long beat = (long) tt - hitAt;
        boolean flash = beat >= 0 && beat < 18 && (beat % 6) < 3;
        final boolean mast = !dark && on && (spin > 0.2F || flash) && !(beat >= 0 && beat < 18 && !flash);
        model.getBone("glow_mast").ifPresent(b -> b.setHidden(!mast));
        final boolean core = !dark && on && (spin > 0.5F || flash);
        model.getBone("glow_core").ifPresent(b -> b.setHidden(!core));
        final boolean tiles = !dark && on && o > Balance.QR_PANEL_TICKS * 0.5F && (spin > 0.3F || flash);
        for (String s : order) model.getBone("glow_" + s).ifPresent(b -> b.setHidden(!tiles));
        final boolean tip = !dark && on && (flash || ((long) tt % Balance.QR_SWEEP_TICKS) < 4);
        model.getBone("tip").ifPresent(b -> b.setHidden(!tip));
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
        return List.of(Component.translatable("gui.wmd.info.use", String.format("%,d", Balance.QR_FE)),
                Component.translatable("gui.wmd.info.qradar", String.format("%,d", (int) Balance.QR_RANGE), (int) Balance.QR_PLAYER_RANGE, Balance.QR_SWEEP_TICKS / 20.0F));
    }

    @Override
    protected String detail() {
        return (off ? "switched off" : isRunning() ? "sweeping" : "no power") + ", " + contacts.size() + " ghost(s), frequency " + frequency;
    }

    @Override protected SoundEvent runSound() { return WmdSounds.QR_RUN_LOOP.get(); }
    @Override protected void runStarted(Level level) { playOnce(level, WmdSounds.BAY_HATCH.get(), Balance.SND_MECH, 0.9F); }
    @Override protected void runStopped(Level level) { playOnce(level, WmdSounds.ASR_SPIN_DOWN.get(), Balance.SND_MECH, 0.8F); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 7, 2); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putInt("qrFreq", frequency);
        tag.putBoolean("qrOff", off);
        tag.put("qrContacts", contacts);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        if (tag.contains("qrFreq")) frequency = Mth.clamp(tag.getInt("qrFreq"), 1, 9999);
        off = tag.getBoolean("qrOff");
        contacts = tag.contains("qrContacts") ? tag.getList("qrContacts", Tag.TAG_COMPOUND) : new ListTag();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        tag.putInt("qrFreq", frequency);
        tag.putBoolean("qrOff", off);
        tag.put("qrContacts", contacts);
        return tag;
    }
}
