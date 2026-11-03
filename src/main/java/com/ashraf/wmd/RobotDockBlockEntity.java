package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class RobotDockBlockEntity extends BlockEntity implements Owned, MachinePorts {

    private static class Buffer extends EnergyStorage {
        Buffer(int cap, int receive) { super(cap, receive, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private final OwnerData ownerData = new OwnerData();
    private final Buffer store;
    private final int chargeRate;
    private UUID robot;
    private boolean charging;

    private float arm = 0.0F;

    public RobotDockBlockEntity(BlockPos pos, BlockState state) { this(Wmd.ROBOT_DOCK_BE.get(), pos, state, Balance.DOCK_BUFFER, Balance.DOCK_RECEIVE, Balance.DOCK_CHARGE); }

    protected RobotDockBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state, int buffer, int receive, int charge) {
        super(type, pos, state);
        store = new Buffer(buffer, receive);
        chargeRate = charge;
    }

    protected BlockPos socketCell() { return new BlockPos(1, 0, 8); }
    protected Direction socketFace() { return Direction.EAST; }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    public UUID robot() { return robot; }
    void setRobot(UUID id) { robot = id; setChanged(); }
    public boolean charging() { return charging; }

    static Vec3 parkSpot(Level level, BlockPos dock) {
        if (level == null || dock == null || !level.isLoaded(dock) || !(level.getBlockState(dock).getBlock() instanceof RobotDockBlock)) return null;
        Direction f = level.getBlockState(dock).getValue(MachineBlock.FACING);

        boolean charger = level.getBlockState(dock).getBlock() instanceof RunnerChargerBlock;
        double lx = charger ? 0.0 : -0.5, lz = charger ? -0.25 : level.getBlockState(dock).getBlock() instanceof XlDockBlock ? 8.0 : 4.0, wx, wz;
        switch (f) {
            case EAST -> { wx = -lz; wz = lx; }
            case SOUTH -> { wx = -lx; wz = -lz; }
            case WEST -> { wx = lz; wz = -lx; }
            default -> { wx = lx; wz = lz; }
        }
        return new Vec3(dock.getX() + 0.5 + wx, dock.getY() + (charger ? 1.5 : 3.0) / 16.0, dock.getZ() + 0.5 + wz);
    }

    static float parkYaw(Level level, BlockPos dock) {
        Direction f = level.getBlockState(dock).getValue(MachineBlock.FACING);
        return f.toYRot();
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        Direction f = getBlockState().getValue(MachineBlock.FACING);
        BlockPos l = Machines.toLocal(worldPosition, f, Machines.mount(getBlockState()), part);
        return l.equals(socketCell()) && side == Machines.dirToWorld(f, Machines.mount(getBlockState()), socketFace()) ? store : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RobotDockBlockEntity be) {
        if (!(level instanceof ServerLevel sl) || level.getGameTime() % 5 != 0) return;
        boolean was = be.charging;
        be.charging = false;
        if (be.robot != null && !EmpField.jammed(sl, pos)) {
            Entity e = sl.getEntity(be.robot);
            if (e instanceof MissileRobotEntity r && r.parked() && be.store.getEnergyStored() > 0) {
                int took = r.charge(Math.min(be.store.getEnergyStored(), be.chargeRate * 5));
                be.store.use(took);
                be.charging = took > 0;
            }
        }
        if (was != be.charging) level.sendBlockUpdated(pos, state, state, 3);
        if (level.getGameTime() % 100 == 0) be.setChanged();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, RobotDockBlockEntity be) {
        be.arm += ((be.charging ? 1.0F : 0.0F) - be.arm) * 0.08F;
    }

    public float armDown() { return arm; }

    public Component info() {
        Entity e = robot != null && level instanceof ServerLevel sl ? sl.getEntity(robot) : null;
        Component who = e instanceof MissileRobotEntity r ? Component.translatable("message.wmd.dock.robot", r.batteryPercent()) : Component.translatable("message.wmd.dock.no_robot");
        return Component.translatable("message.wmd.dock.info", String.format("%,d", store.getEnergyStored()), String.format("%,d", store.getMaxEnergyStored()), who);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putInt("energy", store.getEnergyStored());
        if (robot != null) tag.putUUID("robot", robot);
        tag.putBoolean("charging", charging);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        store.set(tag.getInt("energy"));
        robot = tag.hasUUID("robot") ? tag.getUUID("robot") : null;
        charging = tag.getBoolean("charging");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("charging", charging);
        return tag;
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
