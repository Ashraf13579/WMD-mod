package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class CameraRelayBlockEntity extends CctvDeviceEntity implements RadarLinked {

    public CameraRelayBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.CAMERA_RELAY_BE.get(), pos, state, Balance.CCTV_RELAY_FE);
    }

    @Override
    protected void changed() {
        if (level instanceof ServerLevel sl) CctvNet.register(sl, worldPosition, new CctvNet.Node(CctvNet.RELAY, getOwner(), frequency, 0.0F, isRunning()));
    }

    @Override
    public void deviceTick(ServerLevel sl, long now) {
        if ((now + (worldPosition.asLong() & 15L)) % 20 == 0) changed();
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.cctv.freq", frequency),
                Component.translatable("gui.wmd.info.cctv.relay", Balance.CCTV_HOP, Balance.CCTV_HOPS, Balance.CCTV_DIRECT));
    }

    @Override protected String detail() { return (isRunning() ? "UP" : "DOWN") + ", frequency " + frequency; }
    @Override protected float soundVolume() { return 0.05F; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.SOUTH) ? energy() : null; }

    private static final PortMap PORT_MAP = new PortMap(0, 0, 1).power(0, 0, 0, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
