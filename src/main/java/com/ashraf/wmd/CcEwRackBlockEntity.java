package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CcEwRackBlockEntity extends CcModuleBlockEntity {

    private boolean enabled = true, jamming;
    private boolean joined;

    public CcEwRackBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_EW_RACK_BE.get(), pos, state, Balance.CC_EW_FE); }

    @Override public CcModule kind() { return CcModule.EW_CONSOLE; }

    public boolean isEnabled() { return enabled; }
    public boolean isJamming() { return jamming; }

    void toggle() {
        enabled = !enabled;
        setChanged();
        sync();
    }

    @Override
    protected void moduleTick(Level level, long now) {
        if (!joined) { CcJammer.join(this); joined = true; }
        if ((now + 11) % 20 != 0) return;
        boolean j = isOnline() && enabled && GroundStationBlockEntity.anyJamming(getOwner());
        if (j != jamming) {
            jamming = j;
            setChanged();
            sync();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CcJammer.leave(this);
        joined = false;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        boolean on = isOnline() && !ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        for (int i = 0; i < 3; i++) {
            final double ph = i * 1.7;
            final float deg = CcSpecC.METER_REST_DEG + (jamming && on ? CcSpecC.METER_SWING_DEG * (0.55F + 0.3F * (float) Math.sin(tt * (0.21 + 0.07 * i) + ph)
                    + 0.15F * (float) Math.sin(tt * 1.9 + ph * 3.0)) : on ? 8.0F : 0.0F);
            final float a = (float) Math.toRadians(deg);
            model.getBone("meter" + i).ifPresent(b -> CcSatGlobeBlockEntity.turn(b, CcSpecC.METER_AXIS, a));
        }
        final boolean blink = on && ((long) tt) % 20 < 10;
        model.getBone("blink").ifPresent(b -> b.setHidden(!blink));
        final boolean jam = on && jamming;
        model.getBone("jam").ifPresent(b -> b.setHidden(!jam));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("ewOff", !enabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        enabled = !tag.getBoolean("ewOff");
        jamming = tag.getBoolean("jamming");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("ewOff", !enabled);
        tag.putBoolean("jamming", jamming);
        return tag;
    }
}
