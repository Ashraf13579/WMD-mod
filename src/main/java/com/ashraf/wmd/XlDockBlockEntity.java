package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class XlDockBlockEntity extends RobotDockBlockEntity {

    private int seq = -1, leaveAge = -1, lastPhase = -1;
    private final float[] chock = new float[4], chockPrev = new float[4];
    private float swing, swingPrev, shoe, shoePrev;
    private boolean seen;

    public XlDockBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.XL_DOCK_BE.get(), pos, state, Balance.XL_DOCK_BUFFER, Balance.XL_DOCK_RECEIVE, Balance.XL_DOCK_CHARGE);
    }

    @Override protected BlockPos socketCell() { return new BlockPos(2, 0, 16); }

    @Override protected int contactTicks() { return XlDockAnim.CONTACT_TICKS; }

    @Override
    Vec3 contactPoint() {
        Direction f = getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        float a = switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; };
        Vec3 v = new Vec3(19.0 / 16.0, 28.0 / 16.0, 241.0 / 16.0).yRot(a * Mth.DEG_TO_RAD);
        return new Vec3(worldPosition.getX() + 0.5 + v.x, worldPosition.getY() + v.y, worldPosition.getZ() + 0.5 + v.z);
    }

    public static void xlClientTick(Level level, BlockPos pos, BlockState state, XlDockBlockEntity be) {
        RobotDockBlockEntity.clientTick(level, pos, state, be);
        be.animTick(level);
    }

    private void animTick(Level level) {
        int ph = phase();
        boolean parked = ph == CONNECTED;
        if (lastPhase == CONNECTED && !parked) leaveAge = 0;
        else if (leaveAge >= 0) leaveAge = leaveAge >= XlDockAnim.LEAVE_SHOW ? -1 : leaveAge + 1;
        if (parked) { seq = lastPhase == CONNECTED || seq >= 0 ? Math.min(seq + 1, 1000) : 0; leaveAge = -1; } else seq = -1;
        lastPhase = ph;
        System.arraycopy(chock, 0, chockPrev, 0, 4);
        swingPrev = swing;
        shoePrev = shoe;
        if (parked) {
            for (int c = 0; c < 4; c++) {
                float v = XlDockAnim.ramp(seq, XlDockAnim.CHOCK_AT + c * XlDockAnim.CHOCK_STEP, XlDockAnim.CHOCK_LEN);
                if (chock[c] < 1.0F && v >= 1.0F) play(level, WmdSounds.ST_DOCK_CLAMP.get(), Balance.SND_MECH, 0.9F + 0.06F * c);
                chock[c] = Math.max(chock[c], v);
            }
            if (swing <= 0.0F && seq == XlDockAnim.SWING_AT) play(level, WmdSounds.CAM_SERVO.get(), Balance.SND_CLICK, 0.7F);
            swing = Math.max(swing, XlDockAnim.ramp(seq, XlDockAnim.SWING_AT, XlDockAnim.SWING_LEN));
            shoe = Math.max(shoe, XlDockAnim.ramp(seq, XlDockAnim.SHOE_AT, XlDockAnim.SHOE_LEN));
        } else {
            boolean wasClosed = chock[0] > 0.5F;
            for (int c = 0; c < 4; c++) chock[c] = Math.max(0.0F, chock[c] - 1.0F / 6.0F);
            if (wasClosed && chock[0] <= 0.5F) play(level, WmdSounds.ST_DOCK_CLAMP.get(), Balance.SND_MECH, 0.8F);
            shoe = Math.max(0.0F, shoe - 0.25F);
            if (leaveAge < 0 || leaveAge >= 3) swing = Math.max(0.0F, swing - 1.0F / 14.0F);
        }
    }

    private void play(Level level, SoundEvent e, float volume, float pitch) {
        Vec3 c = contactPoint();
        level.playLocalSound(c.x, worldPosition.getY() + 0.3, c.z, e, SoundSource.BLOCKS, volume, pitch, false);
    }

    float chock(int corner, float pt) { return Mth.lerp(pt, chockPrev[corner], chock[corner]); }
    float swing(float pt) { return XlDockAnim.ease(Mth.lerp(pt, swingPrev, swing)); }
    float shoe(float pt) { return XlDockAnim.ease(Mth.lerp(pt, shoePrev, shoe)); }
    int seq() { return seq; }
    int leaveAge() { return leaveAge; }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (level != null && level.isClientSide && !seen) {
            seen = true;
            if (phase() == CONNECTED) {
                seq = 1000; lastPhase = CONNECTED;
                java.util.Arrays.fill(chock, 1.0F); java.util.Arrays.fill(chockPrev, 1.0F);
                swing = swingPrev = shoe = shoePrev = 1.0F;
            }
        }
    }
}
