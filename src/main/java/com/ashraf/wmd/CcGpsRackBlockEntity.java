package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CcGpsRackBlockEntity extends CcModuleBlockEntity {

    private boolean navOk, joined;
    private int users;

    public CcGpsRackBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_GPS_RACK_BE.get(), pos, state, Balance.CC_GPS_FE); }

    @Override public CcModule kind() { return CcModule.GPS_CONSOLE; }

    public boolean navOk() { return navOk; }

    public boolean live() { return isOnline() && navOk; }
    public int users() { return users; }

    @Override
    protected void moduleTick(Level level, long now) {
        if (!joined) { CcGps.join(this); joined = true; }
        if ((now + 3) % 20 != 0 || level.getServer() == null) return;
        boolean n = isOnline() && CcGps.navControlled(level.getServer(), getOwner());
        int u = 1 + security().trusted().size();
        if (n != navOk || u != users) {
            navOk = n;
            users = u;
            setChanged();
            sync();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CcGps.leave(this);
        joined = false;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("navOk", navOk);
        tag.putInt("users", users);
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        navOk = tag.getBoolean("navOk");
        users = tag.getInt("users");
    }
}
