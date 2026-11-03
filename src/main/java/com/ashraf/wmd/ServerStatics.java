package com.ashraf.wmd;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ServerStatics {

    private ServerStatics() {}

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) { clear(); }

    @SubscribeEvent
    static void onAboutToStart(ServerAboutToStartEvent event) { clear(); }

    static void clear() {
        Statics.clearServer();
        LoadGuard.clear();
        OfflineGuard.clear();
        CcActionPayload.clearStatics();
        Evade.clearAll();
        AlarmNet.clearStatics();
        TrackNet.clearStatics();
        Uplink.FORCED.clear();
        EmpField.clearStatics();
        SecurityActionPayload.clearStatics();
        CounterBatteryRadarBlockEntity.clearStatic();
        NuclearReactorBlockEntity.clearStatics();
        GroundStationBlockEntity.clearStatics();
        EarlyWarningArrayBlockEntity.clearStatics();
        DecoyBlockEntity.LOADED.clear();
        Debris.clearStatics();
        MapFeed.clearStatics();
        ClaimGuard.clearStatics();
        BigDrones.clearStatics();
        SpecialShells.clear();
        DroneWarnings.clear();
        BombEntity.clearStatics();
        ForcefieldBlockEntity.COOL_UNTIL.clear();

        Planet.clearStatics();
        Station.clearStatics();
        Rendezvous.clearStatics();
        Turret.clearStatics();
        HeatLog.clearStatics();
        RailgunStrikes.clearStatics();
        AsteroidDrop.clearStatics();
        RodOfDeath.clearStatics();
        RailgunWreck.clearStatics();
        TsarEvent.clearStatics();
        OrbitClock.clearStatics();
        SensorArray.clearStatics();
        MirrorBurn.clearStatics();
        StationLife.clearStatics();
        OrbitSurvival.clearStatics();
        Hypersonic.clearStatics();
        Boarding.clearStatics();
        Crew.clearStatics();
        Logistics.clearStatics();
        StationPayloads.clearStatics();
        AreaView.clearStatics();
        CamTerrain.clearStatics();
        Hacks.clearStatics();
        HackingDeviceItem.clearStatics();
        CctvNet.clearStatics();
        CctvPayloads.clearStatics();
        ElectricFenceBlock.clearStatics();
        MirrorFocus.clearStatics(); StationLaser.clearStatics(); WarheadFx.clearBites();
        StationWreck.clearStatics();
        Grid.pushing = false;
        MachineBlock.removing = false;
    }
}
