package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;

public final class NoseRadars {

    private NoseRadars() {}

    public static NoseRadarBlockEntity of(ServerLevel level, UUID ship) { return NoseRadarBlockEntity.of(level, ship); }
}
