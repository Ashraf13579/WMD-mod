package com.ashraf.wmd;

import net.minecraft.nbt.CompoundTag;

public interface LoadoutHolder {
    CompoundTag loadoutTag();
    void setLoadoutTag(CompoundTag tag);
}
