package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public interface DriveEngine {

    boolean driveFlange(BlockPos part, Direction side);

    int driveDemand();

    int driveIn(int fe, boolean simulate);
}
