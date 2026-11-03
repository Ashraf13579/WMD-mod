package com.ashraf.wmd;

import net.minecraft.server.level.ServerPlayer;

public interface ScreenCommandTarget {
    void command(ServerPlayer sp, int cmd, int x, int z);
}
