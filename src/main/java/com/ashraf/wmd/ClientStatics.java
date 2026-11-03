package com.ashraf.wmd;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientStatics {

    private ClientStatics() {}

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Statics.clearClient();
        ClientFlights.FLIGHTS.clear();
        ArtilleryView.clear();
        DroneView.clear();
        ReconView.clear();
        CcAlarmLogView.clear();
        LaunchTerminalBlockEntity.CLIENT_LAUNCHES.clear();
        SecurityClientState.pos = null;
        SecurityClientState.names = java.util.List.of();
        SecurityClientState.ownerName = "";
        SecurityClientState.version++;
        ClientCctv.clearStatics();
        ClientSeismic.clearStatics();
        PortableJammerBlockEntity.clearClientStatics();
        T4Launch.clearStatics();
    }
}
