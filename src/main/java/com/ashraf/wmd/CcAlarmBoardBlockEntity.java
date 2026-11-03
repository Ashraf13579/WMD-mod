package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CcAlarmBoardBlockEntity extends CcModuleBlockEntity {

    private long alertUntil;
    private int count;
    private boolean joined;

    public CcAlarmBoardBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_ALARM_BOARD_BE.get(), pos, state, Balance.CC_ALARM_FE); }

    @Override public CcModule kind() { return CcModule.ALARM_BOARD; }

    public int count() { return count; }

    public boolean alert() { return isOnline() && level != null && level.getGameTime() < alertUntil; }

    void alarm() {
        if (level == null) return;
        alertUntil = level.getGameTime() + Balance.CC_ALARM_TICKS;
        count++;
        setChanged();
        sync();
        updateAlert();
    }

    private void updateAlert() {
        if (level == null || level.isClientSide) return;
        BlockState st = level.getBlockState(worldPosition);
        if (st.hasProperty(CcModuleBlocksC.AlarmBoard.ALERT) && st.getValue(CcModuleBlocksC.AlarmBoard.ALERT) != alert())
            level.setBlock(worldPosition, st.setValue(CcModuleBlocksC.AlarmBoard.ALERT, alert()), 3);
    }

    @Override
    protected void moduleTick(Level level, long now) {
        if (!joined) { CcAlarms.join(this); joined = true; }
        if (now % 10 == 4) updateAlert();
    }

    @Override protected void onlineChanged() { updateAlert(); }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CcAlarms.leave(this);
        joined = false;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        boolean on = isOnline() && !ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean al = on && alert();
        final float a = al ? (float) Math.toRadians((tt * CcSpecC.BEACON_DEG_PER_TICK) % 360.0) : 0.0F;
        model.getBone("beacon").ifPresent(b -> { b.setHidden(!al); b.setRotY(a); });
        final boolean lampOn = on && (!al || ((long) tt) % 10 < 5);
        model.getBone("lamp").ifPresent(b -> b.setHidden(!lampOn));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("alertUntil", alertUntil);
        tag.putInt("alarms", count);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        alertUntil = tag.getLong("alertUntil");
        count = tag.getInt("alarms");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putLong("alertUntil", alertUntil);
        tag.putInt("alarms", count);
        return tag;
    }
}
