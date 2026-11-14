package com.ashraf.wmd;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class AirGate {

    private AirGate() {}

    public static final String FLAG = "wmd.airBattles";

    public static final boolean AVAILABLE = !net.neoforged.fml.loading.FMLEnvironment.production || !"false".equalsIgnoreCase(System.getProperty(FLAG));

    private static volatile boolean session = true;

    public static boolean on() { return AVAILABLE && session; }

    static void setSession(boolean v) { session = v; }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { session = true; }
}
