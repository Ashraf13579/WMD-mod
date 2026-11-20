package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class HoloTableBlockEntity extends CcMapTableBlockEntity {

    public static final int K_OWN_AIR = 0, K_OWN_SHIP = 1, K_HOSTILE = 2, K_NEUTRAL = 3, K_FRIENDLY = 4;
    public static final int A_ZOOM_IN = 1, A_ZOOM_OUT = 2, A_PAN = 3, A_CENTRE = 4, A_CLEAR = 5;

    public static final float BED_X = 26.0F, BED_Z0 = 4.0F, BED_Z1 = 28.0F, BED_CZ = 16.0F, BED_Y = 11.0F;
    private static final double REACH = 8.0;

    private int zoom = 0;
    private boolean viewSet;
    private double vx, vz;
    private int[] tracks = new int[0];

    public HoloTableBlockEntity(BlockPos pos, BlockState state) {
        super(HoloBlocks.HOLO_TABLE_BE.get(), pos, state, Balance.HOLOT_FE);
    }

    public int zoom() { return zoom; }

    public int span() { return Balance.HOLOT_SPANS[Mth.clamp(zoom, 0, Balance.HOLOT_SPANS.length - 1)]; }
    public int level() { return Balance.HOLOT_LEVELS[Mth.clamp(zoom, 0, Balance.HOLOT_LEVELS.length - 1)]; }

    public double viewX() { return viewSet ? vx : worldPosition.getX() + 0.5; }
    public double viewZ() { return viewSet ? vz : worldPosition.getZ() + 0.5; }
    public int[] tracks() { return tracks; }

    public float scale() { return 2.0F * BED_X / span(); }

    public float[] toModel(double x, double z) {
        float s = scale();
        double[] l = toLocal(getBlockState().getValue(MachineBlock.FACING), (x - viewX()) * s, (z - viewZ()) * s);
        return new float[] { (float) l[0], BED_CZ + (float) l[1] };
    }

    public double[] toWorld(float mx, float mz) {
        float s = scale();
        double[] w = toWorldVec(getBlockState().getValue(MachineBlock.FACING), mx, mz - BED_CZ);
        return new double[] { viewX() + w[0] / s, viewZ() + w[1] / s };
    }

    static double[] toLocal(Direction f, double dx, double dz) {
        return switch (f) {
            case EAST -> new double[] { dz, -dx };
            case SOUTH -> new double[] { -dx, -dz };
            case WEST -> new double[] { -dz, dx };
            default -> new double[] { dx, dz };
        };
    }

    static double[] toWorldVec(Direction f, double lx, double lz) {
        return switch (f) {
            case EAST -> new double[] { -lz, lx };
            case SOUTH -> new double[] { -lx, -lz };
            case WEST -> new double[] { lz, -lx };
            default -> new double[] { lx, lz };
        };
    }

    static void action(ServerPlayer sp, BlockPos pos, int action, int a) {
        if (!sp.level().isLoaded(pos) || !(sp.level().getBlockEntity(pos) instanceof HoloTableBlockEntity t) || !Reach.within(sp, pos, REACH)) return;
        if (!Access.mayUse(t, sp)) { Access.deny(t, sp); return; }
        switch (action) {
            case A_ZOOM_IN -> t.zoom = Math.max(0, t.zoom - 1);
            case A_ZOOM_OUT -> t.zoom = Math.min(Balance.HOLOT_SPANS.length - 1, t.zoom + 1);
            case A_PAN -> {
                double step = t.span() * Balance.HOLOT_PAN;
                double x = t.viewX(), z = t.viewZ();
                switch (a) { case 0 -> z -= step; case 1 -> x += step; case 2 -> z += step; case 3 -> x -= step; default -> { return; } }
                t.vx = Mth.clamp(x, -29_000_000.0, 29_000_000.0); t.vz = Mth.clamp(z, -29_000_000.0, 29_000_000.0); t.viewSet = true;
            }
            case A_CENTRE -> t.viewSet = false;
            case A_CLEAR -> {
                CcBuildingBlockEntity b = t.building();
                if (b != null && sp.getUUID().equals(t.getOwner())) CcTarget.designate(sp, List.of(b), null);
                return;
            }
            default -> { return; }
        }
        t.setChanged();
        t.sync();
        sp.level().playSound(null, pos, WmdSounds.CC_PING.get(), SoundSource.BLOCKS, Balance.SND_CLICK * 0.5F, 1.6F);
    }

    boolean clickBed(ServerPlayer sp, Vec3 hit) {
        if (view() == VIEW_RECON) return false;
        float[] m = localPx(hit);
        if (Math.abs(m[0]) > BED_X || m[1] < BED_Z0 || m[1] > BED_Z1 || m[2] > 1.0) return false;
        if (!isOnline()) return true;
        CcBuildingBlockEntity b = building();
        if (b == null) return true;
        if (!sp.getUUID().equals(getOwner())) { sp.displayClientMessage(Component.translatable("message.wmd.cc.des.owner_only").withStyle(ChatFormatting.RED), true); return true; }
        double[] w = toWorld(m[0], m[1]);
        String label = "gui.wmd.cc.des.spot";
        float best = Balance.HOLOT_PICK_PX * Balance.HOLOT_PICK_PX;
        for (int i = 0; i + 4 < tracks.length; i += 5) {
            float[] q = toModel(tracks[i] + 0.5, tracks[i + 2] + 0.5);
            float dx = q[0] - m[0], dz = q[1] - m[1], d = dx * dx + dz * dz;
            if (d < best) { best = d; w = new double[] { tracks[i] + 0.5, tracks[i + 2] + 0.5 }; label = "gui.wmd.holot.des.track"; }
        }
        int x = Mth.clamp(Mth.floor(w[0]), -29_000_000, 29_000_000), z = Mth.clamp(Mth.floor(w[1]), -29_000_000, 29_000_000);
        CcTarget.designate(sp, List.of(b), new CcTarget(x, z, label, CcTarget.SRC_TABLE, sp.getGameProfile().getName(), System.currentTimeMillis()));
        return true;
    }

    float[] localPx(Vec3 hit) {
        double[] l = toLocal(getBlockState().getValue(MachineBlock.FACING), hit.x - worldPosition.getX() - 0.5, hit.z - worldPosition.getZ() - 0.5);
        return new float[] { (float) (l[0] * 16.0), (float) (l[1] * 16.0), (float) (hit.y - worldPosition.getY()) };
    }

    @Override
    protected void moduleTick(Level level, long now) {
        super.moduleTick(level, now);
        if ((now + 7) % Balance.HOLOT_TRACK_TICKS != 0 || !(level instanceof ServerLevel sl)) return;
        int[] t = isOnline() && getOwner() != null ? collectTracks(sl, getOwner(), now) : new int[0];
        if (!java.util.Arrays.equals(t, tracks)) { tracks = t; sync(); }
    }

    static int[] collectTracks(ServerLevel sl, UUID owner, long now) {
        if (!SableBridge.active()) return new int[0];
        List<int[]> out = new ArrayList<>();
        Set<UUID> locked = TrackNet.trackedBy(sl, owner, now);
        int seen = 0;
        for (SableBridge.ShipInfo s : SableBridge.allShips(sl)) {
            if (++seen > 256) break;
            ShipFaction.Occupants o = ShipFaction.occupants(sl, s.id());
            boolean own = o.machineOwners().containsKey(owner) || o.players().containsKey(owner);
            if (!own && !locked.contains(s.id())) continue;
            if (!own && Cloak.hidden(sl, s.id()) && !Cloak.revealedTo(sl, s.id(), owner)) continue;
            AirPicture.Aircraft a = AirGate.on() ? AirPicture.get(sl, s.id()) : null;
            Vec3 p = a != null ? a.pos() : s.worldCenter(), v = a != null ? a.vel() : s.linearVelocity();
            int kind;
            if (own) kind = a != null ? K_OWN_AIR : K_OWN_SHIP;
            else kind = switch (ShipFaction.relation(sl, s.id(), owner, null)) { case FRIENDLY -> K_FRIENDLY; case NEUTRAL -> K_NEUTRAL; default -> K_HOSTILE; };
            int h = v.horizontalDistanceSqr() < 1.0E-6 ? -1 : (int) Math.round(Mth.wrapDegrees(Math.toDegrees(Math.atan2(v.x, -v.z))) + 360.0) % 360;
            out.add(new int[] { Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z), kind, h });
        }
        out.sort((a, b) -> Integer.compare(a[3] <= K_OWN_SHIP ? 1 : 0, b[3] <= K_OWN_SHIP ? 1 : 0));
        int n = Math.min(Balance.HOLOT_MAX_TRACKS, out.size());
        int[] arr = new int[n * 5];
        for (int i = 0; i < n; i++) System.arraycopy(out.get(i), 0, arr, i * 5, 5);
        return arr;
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", guiFeUse()), Component.translatable("gui.wmd.info.holot"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.HOLO_HUM_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }
    @Override protected float soundPitch() { return 1.0F; }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) { HoloAnim.animate(this, model, tt, partialTick); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        putView(tag);
        tag.putIntArray("htTracks", tracks);
        return tag;
    }

    private void putView(CompoundTag tag) {
        tag.putInt("htZoom", zoom);
        tag.putBoolean("htSet", viewSet);
        tag.putDouble("htX", vx);
        tag.putDouble("htZ", vz);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        putView(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        zoom = tag.contains("htZoom") ? Mth.clamp(tag.getInt("htZoom"), 0, Balance.HOLOT_SPANS.length - 1) : 0;
        viewSet = tag.getBoolean("htSet");
        vx = tag.getDouble("htX");
        vz = tag.getDouble("htZ");
        if (tag.contains("htTracks")) { int[] t = tag.getIntArray("htTracks"); tracks = t.length % 5 == 0 ? t : new int[0]; }
    }

    double wakeAt = -1000.0, scanAt = -1000.0, alertAt = -1000.0, lastActivity = -1000.0;
    boolean awake;
    int lastWarnings, lastViewSig;
    float bridgeX = HoloAnim.BRIDGE_MIN;
}
