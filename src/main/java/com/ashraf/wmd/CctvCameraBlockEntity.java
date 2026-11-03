package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CctvCameraBlockEntity extends CctvDeviceEntity implements RadarLinked {

    static final float LENS_Y = 9.0F, LENS_Z = -6.4F;

    private float pan;
    private long viewedUntil;
    private long servoAt;
    private boolean viewed;

    public CctvCameraBlockEntity(BlockPos pos, BlockState state) {
        super(state.getBlock() instanceof CctvBlocks.Covert ? Wmd.COVERT_CAMERA_BE.get() : Wmd.CCTV_CAMERA_BE.get(), pos, state, 0);
    }

    public boolean covert() { return getBlockState().getBlock() instanceof CctvBlocks.Covert; }

    public Vec3 lens() { return covert() ? modelPoint(0.0, 2.0, -2.6) : modelPoint(0.0, LENS_Y, LENS_Z); }

    public float baseYaw() {
        Direction d = Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.NORTH);
        return d.getAxis().isHorizontal() ? d.toYRot() : facing().toYRot();
    }

    public float lookYaw() { return Mth.wrapDegrees(baseYaw() - pan); }

    public float pan() { return pan; }

    void setPan(float p, long now) {
        float np = Mth.clamp(p, -Balance.CCTV_PAN, Balance.CCTV_PAN);
        if (Math.abs(np - pan) > 3.0F && now - servoAt >= 12 && level != null) {
            servoAt = now;
            level.playSound(null, worldPosition, WmdSounds.CAM_SERVO.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        pan = np;
        viewedUntil = now + 40;
        setChanged();
    }

    @Override
    protected void changed() {
        if (level instanceof ServerLevel sl) CctvNet.register(sl, worldPosition, new CctvNet.Node(covert() ? CctvNet.COVERT : CctvNet.CAM, getOwner(), frequency, baseYaw(), true));
    }

    @Override
    public void deviceTick(ServerLevel sl, long now) {
        if ((now + (worldPosition.asLong() & 31L)) % 40 == 0) changed();
        boolean v = now < viewedUntil;
        if (v != viewed || (v && now % 4 == 0)) { viewed = v; sync(); }
    }

    private float shownPan, prevPan;

    @Override
    public void deviceClient(Level level) {
        prevPan = shownPan;
        float want = viewed ? pan : (covert() ? 0.0F : 35.0F * Mth.sin(level.getGameTime() * 0.012F + (worldPosition.asLong() & 63L)));
        shownPan += Mth.clamp(want - shownPan, -3.0F, 3.0F);
    }

    public float shownPan(float pt) { return Mth.lerp(pt, prevPan, shownPan); }
    public boolean viewedShown() { return viewed; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.cctv.freq", frequency),
                Component.translatable("gui.wmd.info.cctv.reach", Balance.CCTV_DIRECT),
                Component.translatable(covert() ? "gui.wmd.info.cctv.covert" : "gui.wmd.info.cctv.camera"));
    }

    @Override protected String detail() { return "frequency " + frequency; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("pan", pan);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pan = tag.getFloat("pan");
        if (tag.contains("viewed")) viewed = tag.getBoolean("viewed");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putFloat("pan", pan);
        tag.putBoolean("viewed", viewed);
        return tag;
    }
}
