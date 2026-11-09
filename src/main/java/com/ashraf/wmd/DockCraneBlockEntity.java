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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class DockCraneBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated {

    public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 5, Z0 = -1, Z1 = 1;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 1, Direction.SOUTH, false);

    static final float JF_Y = 66.0F, JF_Z = -8.0F, JL = 88.0F, LUFF_REST = 16.0F, LUFF_MAX = 60.0F, HOOK_TOP = 17.0F, PADS = 30.2F, LOAD_AXIS = 36.2F;
    static final float SLEW_SIGN = TorpedoLauncherDeckBlockEntity.TRAIN_SIGN, LUFF_SIGN = 1.0F;

    public static final int P_IDLE = 0, P_SWING_PICK = 1, P_LOWER_PICK = 2, P_LATCH = 3, P_HOIST = 4, P_SWING_DROP = 5, P_LOWER_DROP = 6, P_RELEASE = 7, P_RETURN = 8;
    public static final int S_IDLE = 0, S_NO_LOAD = 1, S_NO_RECEIVER = 2, S_WORKING = 3, S_HOLDING = 4, S_NO_POWER = 5, S_OFF = 6;
    public static final int A_TOGGLE = 0;

    private final OwnerData ownerData = new OwnerData();
    private boolean enabled = true;

    private int phase = P_IDLE;
    private long phaseStart;
    private int phaseDur = 1;
    private float fs, fl, fd, ts, tl = LUFF_REST, td;
    private long pausedAt = -1L;
    private ItemStack carried = ItemStack.EMPTY;

    private BlockPos srcPos, dstPos;
    private int srcSlot = -1;
    private Vec3 pickAt, dropAt;

    private int status = S_IDLE, sources, receivers;
    private String jobText = "";

    Object moveLoop;

    public DockCraneBlockEntity(BlockPos pos, BlockState state) { super(Wmd.DOCK_CRANE_BE.get(), pos, state, Balance.CRANE_FE); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) { ownerData.claim(player); setChanged(); sync(); }

    public void act(int action, int[] data, Player by) {
        if (action == A_TOGGLE) { enabled = !enabled; status = enabled ? S_IDLE : S_OFF; setChanged(); sync(); }
    }

    public boolean enabled() { return enabled; }
    public int craneStatus() { return status; }
    public int phase() { return phase; }
    public int sourcesInReach() { return sources; }
    public int receiversInReach() { return receivers; }
    public String jobText() { return jobText; }
    public ItemStack carried() { return carried; }

    public static double reachMin() { return (8.0 + JL * Math.cos(Math.toRadians(LUFF_MAX))) / 16.0; }
    public static double reachMax() { return (8.0 + JL * Math.cos(Math.toRadians(LUFF_REST))) / 16.0; }

    static float headY(float a) { return JF_Y + JL * Mth.sin(a * Mth.DEG_TO_RAD); }
    static float headZ(float a) { return JF_Z - JL * Mth.cos(a * Mth.DEG_TO_RAD); }

    public float[] pose(double tt) {
        double t = pausedAt >= 0 ? pausedAt : tt;
        double k = Mth.clamp((t - phaseStart) / Math.max(1, phaseDur), 0.0, 1.0);
        k = k * k * (3.0 - 2.0 * k);
        float ds = Mth.wrapDegrees(ts - fs);
        return new float[] { fs + ds * (float) k, fl + (tl - fl) * (float) k, fd + (td - fd) * (float) k };
    }

    public boolean moving(double tt) { return phase != P_IDLE && pausedAt < 0 && tt - phaseStart < phaseDur; }

    private Vec3 wp(double lx, double ly, double lz) { double[] p = worldPoint(lx, ly, lz); return new Vec3(p[0], p[1], p[2]); }

    private Vec3 toLocal(Vec3 w) {
        Vec3 o = wp(0, 0, 0), r = wp(1, 0, 0).subtract(o), u = wp(0, 1, 0).subtract(o), f = wp(0, 0, -1).subtract(o);
        Vec3 d = w.subtract(o);
        return new Vec3(d.dot(r), d.dot(u), -d.dot(f));
    }

    float[] solve(Vec3 w) {
        Vec3 l = toLocal(w);
        double R = Math.hypot(l.x, l.z);
        if (R < reachMin() - 0.25 || R > reachMax() + 0.25) return null;
        float slew = (float) Math.toDegrees(Math.atan2(l.x, -l.z));
        double c = Mth.clamp((R * 16.0 - 8.0) / JL, Math.cos(Math.toRadians(LUFF_MAX)), Math.cos(Math.toRadians(LUFF_REST)));
        float luff = (float) Math.toDegrees(Math.acos(c));
        double padsRest = (headY(luff) - PADS) / 16.0;
        double drop = padsRest - l.y;
        if (drop < 0.0 || drop > Balance.CRANE_DROP) return null;
        return new float[] { slew, luff, (float) drop };
    }

    static boolean liftable(ItemStack s) { return !s.isEmpty() && s.getMaxStackSize() <= 4; }

    private boolean receiverKind(BlockEntity be, boolean onShip) {
        if (be instanceof TorpedoLauncherBlockEntity || be instanceof LaunchCellBlockEntity || be instanceof CoastalBatteryBlockEntity) return true;
        return be instanceof TorpedoRackBlockEntity && onShip;
    }

    private int freeSlot(BlockEntity be, ItemStack s) {
        if (!(be instanceof ItemProcessBlockEntity ip) || be.isRemoved()) return -1;
        if (be instanceof Owned o && !Access.trusts(o, getOwner())) return -1;
        Container c = ip.slotContainer();
        ItemStack one = s.copyWithCount(1);
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).isEmpty() && c.canPlaceItem(i, one)) return i;
        return -1;
    }

    private Vec3 topOver(ServerLevel sl, BlockPos p) {
        BlockPos top = p;
        for (int k = 12; k >= 1; k--) {
            BlockPos q = p.above(k);
            if (!sl.getBlockState(q).getCollisionShape(sl, q).isEmpty()) { top = q; break; }
        }
        Vec3 c = SableBridge.worldPos(sl, top), c2 = SableBridge.worldPos(sl, top.above());
        return c.add(c2.subtract(c).scale(0.5));
    }

    private record Cand(BlockEntity be, boolean onShip) {}

    private List<Cand> candidates(ServerLevel sl) {
        Vec3 o = wp(0, 0, 0);
        double r = reachMax() + 1.0;
        AABB box = new AABB(o.x - r, o.y - Balance.CRANE_DROP - 2.0, o.z - r, o.x + r, o.y + 8.0, o.z + r);
        List<Cand> out = new ArrayList<>();
        for (int cx = Mth.floor(box.minX) >> 4; cx <= Mth.floor(box.maxX) >> 4; cx++)
            for (int cz = Mth.floor(box.minZ) >> 4; cz <= Mth.floor(box.maxZ) >> 4; cz++) {
                LevelChunk ch = sl.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values())
                    if (be != this && box.contains(Vec3.atCenterOf(be.getBlockPos())) && !SableBridge.isOnShip(sl, be.getBlockPos())) out.add(new Cand(be, false));
            }
        if (SableBridge.active())
            for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, box))
                for (BlockPos p : SableBridge.blockEntitiesOn(sl, s.id())) {
                    BlockEntity be = sl.getBlockEntity(p);
                    if (be != null && be != this && box.contains(SableBridge.worldPos(sl, p))) out.add(new Cand(be, true));
                }
        return out;
    }

    private boolean findJob(ServerLevel sl) {
        List<Cand> all = candidates(sl);
        List<Cand> recv = new ArrayList<>();
        int nSrc = 0;
        for (Cand c : all) if (receiverKind(c.be(), c.onShip()) && solve(topOver(sl, c.be().getBlockPos())) != null) recv.add(c);
        Vec3 me = wp(0, 0, 0);
        recv.sort((a, b) -> Double.compare(SableBridge.worldPos(sl, a.be().getBlockPos()).distanceToSqr(me), SableBridge.worldPos(sl, b.be().getBlockPos()).distanceToSqr(me)));
        receivers = recv.size();
        if (!carried.isEmpty()) {
            sources = 0;
            for (Cand r : recv) if (freeSlot(r.be(), carried) >= 0) { setDrop(sl, r.be()); return true; }
            return false;
        }
        for (Cand c : all) {
            if (c.onShip() || receiverKind(c.be(), false) && !(c.be() instanceof TorpedoRackBlockEntity)) continue;
            Vec3 at = topOver(sl, c.be().getBlockPos());
            if (solve(at) == null) continue;
            List<ItemStack> items = sourceItems(sl, c.be());
            if (items == null) continue;
            nSrc++;
            for (int i = 0; i < items.size(); i++) {
                ItemStack s = items.get(i);
                if (!liftable(s)) continue;
                for (Cand r : recv) {
                    if (freeSlot(r.be(), s) < 0) continue;
                    sources = nSrc;
                    srcPos = c.be().getBlockPos(); srcSlot = i; pickAt = at;
                    setDrop(sl, r.be());
                    jobText = s.getHoverName().getString() + " -> " + r.be().getBlockState().getBlock().getName().getString();
                    return true;
                }
            }
        }
        sources = nSrc;
        return false;
    }

    private void setDrop(ServerLevel sl, BlockEntity r) {
        dstPos = r.getBlockPos();
        dropAt = topOver(sl, dstPos);
        if (!carried.isEmpty()) jobText = carried.getHoverName().getString() + " -> " + r.getBlockState().getBlock().getName().getString();
    }

    private List<ItemStack> sourceItems(ServerLevel sl, BlockEntity be) {
        List<ItemStack> l = new ArrayList<>();
        if (be instanceof TorpedoRackBlockEntity rack) {
            Container c = rack.slotContainer();
            for (int i = 0; i < c.getContainerSize(); i++) l.add(c.getItem(i).copy());
            return l;
        }
        if (be instanceof ProcessUnitBlockEntity) return null;
        IItemHandler h = sl.getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null);
        if (h == null) return null;
        for (int i = 0; i < h.getSlots(); i++) l.add(h.getStackInSlot(i).copy());
        return l;
    }

    private ItemStack takeFromSource(ServerLevel sl) {
        if (srcPos == null || srcSlot < 0) return ItemStack.EMPTY;
        BlockEntity be = sl.getBlockEntity(srcPos);
        if (be instanceof TorpedoRackBlockEntity rack) {
            Container c = rack.slotContainer();
            if (srcSlot >= c.getContainerSize() || !liftable(c.getItem(srcSlot))) return ItemStack.EMPTY;
            return c.removeItem(srcSlot, 1);
        }
        if (be == null || be instanceof ProcessUnitBlockEntity) return ItemStack.EMPTY;
        IItemHandler h = sl.getCapability(Capabilities.ItemHandler.BLOCK, srcPos, null);
        if (h == null || srcSlot >= h.getSlots() || !liftable(h.getStackInSlot(srcSlot))) return ItemStack.EMPTY;
        return h.extractItem(srcSlot, 1, false);
    }

    private boolean giveToReceiver(ServerLevel sl) {
        if (dstPos == null || carried.isEmpty()) return false;
        BlockEntity be = sl.getBlockEntity(dstPos);
        int slot = be == null ? -1 : freeSlot(be, carried);
        if (slot < 0) return false;
        ((ItemProcessBlockEntity) be).slotContainer().setItem(slot, carried.copyWithCount(1));
        carried.shrink(1);
        if (carried.isEmpty()) carried = ItemStack.EMPTY;
        return true;
    }

    private void go(int p, float s, float l, float d, int minDur) {
        float[] now = pose(level.getGameTime());
        fs = now[0]; fl = now[1]; fd = now[2];
        ts = s; tl = l; td = d;
        double dur = Math.max(Math.abs(Mth.wrapDegrees(s - fs)) / Balance.CRANE_SLEW_SPEED, Math.abs(l - fl) / Balance.CRANE_LUFF_SPEED);
        dur = Math.max(dur, Math.abs(d - fd) / Balance.CRANE_HOOK_SPEED);
        phase = p;
        phaseStart = level.getGameTime();
        phaseDur = Math.max(minDur, (int) Math.ceil(dur));
        setChanged();
        sync();
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        if (pausedAt >= 0) { phaseStart += now - pausedAt; pausedAt = -1L; sync(); }
        if (!enabled && phase == P_IDLE) return false;
        if (phase == P_IDLE) {
            if ((now + (worldPosition.asLong() & 15L)) % Balance.CRANE_SCAN_TICKS != 0) return false;
            int was = status;
            if (findJob(sl)) {
                status = S_WORKING;
                float[] p = carried.isEmpty() ? solve(pickAt) : solve(dropAt);
                if (p == null) return false;
                go(carried.isEmpty() ? P_SWING_PICK : P_SWING_DROP, p[0], p[1], 0.0F, 10);
                return true;
            }
            status = !carried.isEmpty() ? S_HOLDING : receivers == 0 ? S_NO_RECEIVER : S_NO_LOAD;
            if (status != was) { setChanged(); sync(); }
            return false;
        }
        if (now - phaseStart < phaseDur) return true;
        switch (phase) {
            case P_SWING_PICK -> { float[] p = solve(pickAt); if (p == null) go(P_RETURN, ts, tl, 0.0F, 10); else go(P_LOWER_PICK, p[0], p[1], p[2], 10); }
            case P_LOWER_PICK -> { sl.playSound(null, pickAt.x, pickAt.y, pickAt.z, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F); go(P_LATCH, ts, tl, td, Balance.CRANE_LATCH_TICKS); }
            case P_LATCH -> {
                carried = takeFromSource(sl);
                if (carried.isEmpty()) { jobText = ""; go(P_RETURN, ts, tl, 0.0F, 10); }
                else go(P_HOIST, ts, tl, 0.0F, 10);
            }
            case P_HOIST -> { float[] p = solve(dropAt); if (p == null) { status = S_HOLDING; go(P_RETURN, ts, tl, 0.0F, 1); } else go(P_SWING_DROP, p[0], p[1], 0.0F, 10); }
            case P_SWING_DROP -> { float[] p = solve(dropAt); if (p == null) go(P_RETURN, ts, tl, 0.0F, 1); else go(P_LOWER_DROP, p[0], p[1], p[2], 10); }
            case P_LOWER_DROP -> go(P_RELEASE, ts, tl, td, Balance.CRANE_LATCH_TICKS);
            case P_RELEASE -> {
                if (giveToReceiver(sl)) {
                    sl.playSound(null, dropAt.x, dropAt.y, dropAt.z, WmdSounds.TP_RELOAD.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
                    Wmd.LOGGER.info("Dock crane {} loaded {} into {}", worldPosition.toShortString(), jobText, dstPos.toShortString());
                    jobText = "";
                } else status = S_HOLDING;
                go(P_RETURN, ts, tl, 0.0F, 10);
            }
            default -> { phase = P_IDLE; phaseStart = now; phaseDur = 1; fs = ts; fl = tl; fd = td = 0.0F; setChanged(); sync(); }
        }
        return true;
    }

    @Override
    protected void idle() {
        super.idle();
        if (level == null) return;
        if (phase != P_IDLE && pausedAt < 0) { pausedAt = level.getGameTime(); setChanged(); sync(); }
        if (level.getGameTime() % 20 == 0 && status != S_NO_POWER) { status = S_NO_POWER; setChanged(); sync(); }
    }

    protected void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public List<ItemStack> takeAll() {
        List<ItemStack> l = new ArrayList<>();
        if (!carried.isEmpty()) l.add(carried);
        carried = ItemStack.EMPTY;
        return l;
    }

    public static void clientTickCrane(Level level, BlockPos pos, BlockState state, DockCraneBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        final Vec3 at = Vec3.atCenterOf(pos).add(0.0, 4.0, 0.0);
        final boolean hook = be.phase == P_LOWER_PICK || be.phase == P_HOIST || be.phase == P_LOWER_DROP || be.phase == P_RETURN;
        be.moveLoop = LoopSound.keep(be.moveLoop, level.getGameTime(), WmdSounds.DECK_TRAIN_LOOP.get(), Balance.SND_MACHINE, hook ? 0.7F : 0.9F, () -> at,
                () -> !be.isRemoved() && be.moving(level.getGameTime()));
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final float[] p = pose(tt);
        final boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        model.getBone("base").ifPresent(b -> { b.setPosX(0.0F); b.setPosZ(0.0F); b.setRotX(0.0F); b.setRotZ(0.0F); });
        model.getBone("slew").ifPresent(b -> b.setRotY(SLEW_SIGN * p[0] * Mth.DEG_TO_RAD));
        model.getBone("jib").ifPresent(b -> b.setRotX(LUFF_SIGN * (p[1] - LUFF_REST) * Mth.DEG_TO_RAD));
        model.getBone("hook").ifPresent(b -> b.setHidden(true));
        model.getBone("lamp").ifPresent(b -> b.setHidden(!enabled || status == S_NO_POWER || dark));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.CL_RUN_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 3, 0); }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.CRANE_FE * 20),
                Component.translatable("gui.wmd.info.dock_crane", String.format("%.1f", reachMin()), String.format("%.1f", reachMax()), (int) Balance.CRANE_DROP));
    }

    @Override
    protected String detail() {
        return (enabled ? "on" : "off") + ", phase " + phase + ", status " + status + ", sources " + sources + ", receivers " + receivers
                + (carried.isEmpty() ? "" : ", carrying " + carried.getHoverName().getString()) + (jobText.isEmpty() ? "" : ", job " + jobText);
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override public PortMap guiPortMap() { return PORT_MAP; }

    private void writeState(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("dcOn", enabled);
        tag.putInt("dcPhase", phase);
        tag.putLong("dcT0", phaseStart);
        tag.putInt("dcDur", phaseDur);
        tag.putFloat("dcFs", fs); tag.putFloat("dcFl", fl); tag.putFloat("dcFd", fd);
        tag.putFloat("dcTs", ts); tag.putFloat("dcTl", tl); tag.putFloat("dcTd", td);
        tag.putLong("dcPause", pausedAt);
        tag.putInt("dcStatus", status);
        tag.putInt("dcSrc", sources);
        tag.putInt("dcRecv", receivers);
        tag.putString("dcJob", jobText);
        if (!carried.isEmpty()) tag.put("dcLoad", carried.save(registries));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        writeState(tag, registries);
        if (srcPos != null) tag.putLong("dcSrcPos", srcPos.asLong());
        tag.putInt("dcSrcSlot", srcSlot);
        if (dstPos != null) tag.putLong("dcDstPos", dstPos.asLong());
        if (pickAt != null) { tag.putDouble("dcPx", pickAt.x); tag.putDouble("dcPy", pickAt.y); tag.putDouble("dcPz", pickAt.z); }
        if (dropAt != null) { tag.putDouble("dcDx", dropAt.x); tag.putDouble("dcDy", dropAt.y); tag.putDouble("dcDz", dropAt.z); }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        enabled = !tag.contains("dcOn") || tag.getBoolean("dcOn");
        phase = Mth.clamp(tag.getInt("dcPhase"), P_IDLE, P_RETURN);
        phaseStart = tag.getLong("dcT0");
        phaseDur = Math.max(1, tag.getInt("dcDur"));
        fs = tag.getFloat("dcFs"); fl = tag.contains("dcFl") ? tag.getFloat("dcFl") : LUFF_REST; fd = tag.getFloat("dcFd");
        ts = tag.getFloat("dcTs"); tl = tag.contains("dcTl") ? tag.getFloat("dcTl") : LUFF_REST; td = tag.getFloat("dcTd");
        pausedAt = tag.contains("dcPause") ? tag.getLong("dcPause") : -1L;
        status = tag.getInt("dcStatus");
        sources = tag.getInt("dcSrc");
        receivers = tag.getInt("dcRecv");
        jobText = tag.getString("dcJob");
        carried = tag.contains("dcLoad") ? ItemStack.parseOptional(registries, tag.getCompound("dcLoad")) : ItemStack.EMPTY;
        if (tag.contains("dcSrcPos")) srcPos = BlockPos.of(tag.getLong("dcSrcPos"));
        if (tag.contains("dcSrcSlot")) srcSlot = tag.getInt("dcSrcSlot");
        if (tag.contains("dcDstPos")) dstPos = BlockPos.of(tag.getLong("dcDstPos"));
        if (tag.contains("dcPx")) pickAt = new Vec3(tag.getDouble("dcPx"), tag.getDouble("dcPy"), tag.getDouble("dcPz"));
        if (tag.contains("dcDx")) dropAt = new Vec3(tag.getDouble("dcDx"), tag.getDouble("dcDy"), tag.getDouble("dcDz"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        writeState(tag, registries);
        return tag;
    }
}
