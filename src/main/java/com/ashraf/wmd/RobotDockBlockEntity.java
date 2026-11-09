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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
    public static final int OFF = 0, READY = 1, INBOUND = 2, CONNECTED = 3;

    public static final int ARM_TICKS = 30, RELEASE_TICKS = 16;
    static final double INBOUND_RANGE = 12.0;
    private int phase = OFF, level10;
    private boolean powered, contact;
    private int contactTicks;

    private float arm = 0.0F, armPrev = 0.0F;
    private int beatAge = -1;
    private boolean synced;

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
    public int phase() { return phase; }
    public boolean powered() { return powered; }

    public int level10() { return level10; }

    protected int contactTicks() { return ARM_TICKS; }

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
        boolean was = be.charging, wasContact = be.contact, wasPowered = be.powered;
        int wasPhase = be.phase, wasLevel = be.level10;
        be.charging = false;
        be.powered = be.store.getEnergyStored() > 0;
        MissileRobotEntity r = null;
        if (be.robot != null && !EmpField.jammed(sl, pos) && sl.getEntity(be.robot) instanceof MissileRobotEntity m) r = m;
        Vec3 park = r == null ? null : parkSpot(sl, pos);
        if (r != null && r.parked()) be.phase = CONNECTED;
        else if (r != null && park != null && r.state() == MissileRobotEntity.HOME && r.position().distanceToSqr(park) < INBOUND_RANGE * INBOUND_RANGE) be.phase = INBOUND;
        else be.phase = be.powered ? READY : OFF;
        int need = be.contactTicks();
        if (be.phase == CONNECTED) {
            be.contactTicks = Math.min(be.contactTicks + 5, need + 5);
            be.contact = be.contactTicks >= need;
            if (be.contact && !wasContact && need > 0) be.contactSound(sl, 1.35F);
            if (be.contact && be.powered) {
                int took = r.charge(Math.min(be.store.getEnergyStored(), be.chargeRate * 5));
                be.store.use(took);
                be.charging = took > 0;
            }
            be.level10 = Mth.clamp(r.batteryPercent() / 10, 0, 10);
        } else {
            if (wasContact && need > 0) be.contactSound(sl, 1.7F);
            be.contactTicks = 0;
            be.contact = false;
            be.level10 = 0;
        }
        if (was != be.charging || wasContact != be.contact || wasPowered != be.powered || wasPhase != be.phase || wasLevel != be.level10) {
            level.sendBlockUpdated(pos, state, state, 3);
            be.setChanged();
        }
        if (level.getGameTime() % 100 == 0) be.setChanged();
    }

    private void contactSound(ServerLevel sl, float pitch) {
        Vec3 c = contactPoint();
        sl.playSound(null, c.x, c.y, c.z, WmdSounds.ST_DOCK_CLAMP.get(), SoundSource.BLOCKS, Balance.SND_MECH, pitch);
    }

    Vec3 contactPoint() {
        Direction f = getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        float a = switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; };
        Vec3 v = new Vec3(-8.0 / 16.0, 25.0 / 16.0, 119.5 / 16.0).yRot(a * Mth.DEG_TO_RAD);
        return new Vec3(worldPosition.getX() + 0.5 + v.x, worldPosition.getY() + v.y, worldPosition.getZ() + 0.5 + v.z);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, RobotDockBlockEntity be) {
        be.armPrev = be.arm;
        int need = Math.max(1, be.contactTicks());
        float before = be.arm;
        be.arm = Mth.clamp(be.arm + (be.phase == CONNECTED ? 1.0F / need : -1.0F / RELEASE_TICKS), 0.0F, 1.0F);
        if (be.beatAge >= 0) be.beatAge = be.beatAge > 40 ? -1 : be.beatAge + 1;
        if (before < 1.0F && be.arm >= 1.0F && be.contactTicks() > 0) {
            be.beatAge = 0;
            Vec3 c = be.contactPoint();
            for (int i = 0; i < 8; i++)
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x, c.y - 0.02, c.z,
                        (level.random.nextDouble() - 0.5) * 0.25, level.random.nextDouble() * 0.12, (level.random.nextDouble() - 0.5) * 0.25);
        }
        if (be.charging && be.beatAge < 0 && level.random.nextInt(60) == 0) {
            Vec3 c = be.contactPoint();
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x, c.y - 0.02, c.z, 0.0, 0.03, 0.0);
        }
    }

    public float armDown(float partialTick) {
        float k = Mth.lerp(partialTick, armPrev, arm);
        return k * k * (3.0F - 2.0F * k);
    }

    public float armDown() { return armDown(1.0F); }

    public int beatAge() { return beatAge; }

    public boolean contactClosed() { return contact && arm >= 1.0F; }

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
        tag.putInt("phase", phase);
        tag.putBoolean("powered", powered);
        tag.putBoolean("contact", contact);
        tag.putInt("contactTicks", contactTicks);
        tag.putInt("level10", level10);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        store.set(tag.getInt("energy"));
        robot = tag.hasUUID("robot") ? tag.getUUID("robot") : null;
        charging = tag.getBoolean("charging");
        phase = tag.getInt("phase");
        powered = tag.getBoolean("powered");
        contact = tag.getBoolean("contact");
        contactTicks = tag.getInt("contactTicks");
        level10 = tag.getInt("level10");
        if (level != null && level.isClientSide && !synced) {
            synced = true;
            arm = armPrev = contact ? 1.0F : 0.0F;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("charging", charging);
        tag.putInt("phase", phase);
        tag.putBoolean("powered", powered);
        tag.putBoolean("contact", contact);
        tag.putInt("level10", level10);
        return tag;
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
