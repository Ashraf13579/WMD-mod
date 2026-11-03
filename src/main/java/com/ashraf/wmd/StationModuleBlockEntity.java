package com.ashraf.wmd;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StationModuleBlockEntity extends BlockEntity {

    private String type = Station.CORE;
    private Direction facing = Direction.EAST;
    private UUID owner;
    private int id = -1;
    private int[] ports = new int[8];
    private int breach;
    private long arrive, deploy, dock;
    private boolean wrecked;
    private int mode;
    private int lzState;
    private long lzT0;
    private float aimRoll, aimPitch;
    private int rods;
    private int rdState, rdFrom;
    private long rdT0, rdFromEl, rdCool;
    private int bayState, bayPm;
    private boolean bayPaused;
    private long bayT0;

    public StationModuleBlockEntity(BlockPos pos, BlockState state) { super(Wmd.STATION_MODULE_BE.get(), pos, state); }

    void setUp(String type, Direction facing, UUID owner, int id) {
        this.type = type; this.facing = facing; this.owner = owner; this.id = id;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public String type() { return type; }
    public Direction facing() { return facing; }
    public UUID owner() { return owner; }
    public int id() { return id; }
    public int port(int k) { return k >= 0 && k < ports.length ? ports[k] : 0; }
    public int breach() { return breach; }
    public long arrive() { return arrive; }
    public long deploy() { return deploy; }
    public long dock() { return dock; }
    public boolean wrecked() { return wrecked; }
    public int mode() { return mode; }
    public int lzState() { return lzState; }
    public long lzT0() { return lzT0; }
    public float aimRoll() { return aimRoll; }
    public float aimPitch() { return aimPitch; }
    public int rods() { return rods; }
    public int rdState() { return rdState; }
    public int rdFrom() { return rdFrom; }
    public long rdT0() { return rdT0; }
    public int bayState() { return bayState; }
    public int bayPm() { return bayPm; }
    public boolean bayPaused() { return bayPaused; }
    public long bayT0() { return bayT0; }

    void setBay(int state, int pm, boolean paused, long t0) {
        if (state == bayState && pm == bayPm && paused == bayPaused && t0 == bayT0) return;
        bayState = state; bayPm = pm; bayPaused = paused; bayT0 = t0;
        sync();
    }
    public long rdFromEl() { return rdFromEl; }
    public long rdCool() { return rdCool; }

    void setAim(float roll, float pitch) {
        if (Math.abs(roll - aimRoll) < 0.05F && Math.abs(pitch - aimPitch) < 0.05F) return;
        aimRoll = roll; aimPitch = pitch;
        sync();
    }

    void setRod(int state, long t0, int from, long fromEl, long cool) {
        if (state == rdState && t0 == rdT0 && from == rdFrom && fromEl == rdFromEl && cool == rdCool) return;
        rdState = state; rdT0 = t0; rdFrom = from; rdFromEl = fromEl; rdCool = cool;
        sync();
    }

    void setRods(int n) { if (n != rods) { rods = n; sync(); } }

    void setLaser(int state, long t0, float roll, float pitch) {
        if (state == lzState && t0 == lzT0 && roll == aimRoll && pitch == aimPitch) return;
        lzState = state; lzT0 = t0; aimRoll = roll; aimPitch = pitch;
        sync();
    }

    void setMode(int m) { if (m != mode) { mode = m; sync(); } }

    void setWrecked(boolean w) { if (w != wrecked) { wrecked = w; sync(); } }

    void setTimes(long arrive, long deploy) { this.arrive = arrive; this.deploy = deploy; sync(); }

    void setDock(long dock) { this.dock = dock; sync(); }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    void setState(int[] ports, int breach) {
        if (java.util.Arrays.equals(ports, this.ports) && breach == this.breach) return;
        this.ports = ports.clone(); this.breach = breach;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("type", type);
        tag.putString("facing", facing.getSerializedName());
        if (owner != null) tag.putUUID("owner", owner);
        tag.putInt("id", id);
        tag.putIntArray("ports", ports);
        tag.putInt("breach", breach);
        tag.putLong("arrive", arrive); tag.putLong("deploy", deploy); tag.putLong("dock", dock);
        tag.putBoolean("wrecked", wrecked);
        tag.putInt("mode", mode);
        tag.putInt("lz", lzState); tag.putLong("lzt", lzT0); tag.putFloat("lzr", aimRoll); tag.putFloat("lzp", aimPitch);
        tag.putInt("rods", rods);
        tag.putInt("rds", rdState); tag.putLong("rdt", rdT0); tag.putInt("rdf", rdFrom); tag.putLong("rdfe", rdFromEl); tag.putLong("rdc", rdCool);
        tag.putInt("bys", bayState); tag.putInt("bypm", bayPm); tag.putBoolean("byp", bayPaused); tag.putLong("byt", bayT0);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("type")) type = tag.getString("type");
        Direction d = Direction.byName(tag.getString("facing"));
        facing = d == null || d.getAxis().isVertical() ? Direction.EAST : d;
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        id = tag.contains("id") ? tag.getInt("id") : -1;
        int[] pt = tag.getIntArray("ports");
        ports = java.util.Arrays.copyOf(pt, 8);
        breach = tag.getInt("breach");
        arrive = tag.getLong("arrive"); deploy = tag.getLong("deploy"); dock = tag.getLong("dock");
        wrecked = tag.getBoolean("wrecked");
        mode = tag.getInt("mode");
        lzState = tag.getInt("lz"); lzT0 = tag.getLong("lzt"); aimRoll = tag.getFloat("lzr"); aimPitch = tag.getFloat("lzp");
        rods = tag.getInt("rods");
        rdState = tag.getInt("rds"); rdT0 = tag.getLong("rdt"); rdFrom = tag.getInt("rdf"); rdFromEl = tag.getLong("rdfe"); rdCool = tag.getLong("rdc");
        bayState = tag.getInt("bys"); bayPm = tag.getInt("bypm"); bayPaused = tag.getBoolean("byp"); bayT0 = tag.getLong("byt");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putString("type", type);
        t.putString("facing", facing.getSerializedName());
        t.putInt("id", id);
        t.putIntArray("ports", ports);
        t.putInt("breach", breach);
        t.putLong("arrive", arrive); t.putLong("deploy", deploy); t.putLong("dock", dock);
        t.putBoolean("wrecked", wrecked);
        t.putInt("mode", mode);
        t.putInt("lz", lzState); t.putLong("lzt", lzT0); t.putFloat("lzr", aimRoll); t.putFloat("lzp", aimPitch);
        t.putInt("rods", rods);
        t.putInt("rds", rdState); t.putLong("rdt", rdT0); t.putInt("rdf", rdFrom); t.putLong("rdfe", rdFromEl); t.putLong("rdc", rdCool);
        t.putInt("bys", bayState); t.putInt("bypm", bayPm); t.putBoolean("byp", bayPaused); t.putLong("byt", bayT0);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
