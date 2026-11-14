package com.ashraf.wmd;

import java.util.BitSet;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public interface GunnerGun {

    BlockPos gunPos();

    UUID getOwner();

    boolean stationCommand(long now, BlockPos station, Vec3 point, boolean trigger);

    boolean deployed();

    float deploy01();

    boolean blockedNow();

    boolean firingNow();

    int rounds();

    int roundsMax();

    boolean arcReady();

    BitSet arcMap();

    float yawDeg();

    float elevDeg();

    long lastHitAt();

    boolean heavy();

    float[] shownPose();

    Vec3 lensPlot(double yawDeg, double elevDeg, double deploy01);

    void clientAim(float yaw, float elev, float deploy, int flags);

    default double eyeRange() { return Balance.GTUR_RANGE; }

    default Vec3 clampAimPlot(Vec3 dirPlot, double marginDeg) { return dirPlot; }

    default double eyeSpeed() { return Balance.HGUN_SPEED; }
}
