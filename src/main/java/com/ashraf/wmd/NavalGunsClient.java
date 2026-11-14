package com.ashraf.wmd;

import java.util.Set;

final class NavalGunsClient {

    private NavalGunsClient() {}

    private static final Set<String> SKIN = Set.of("housing", "housing_deck");

    static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        MountedRenderer.register(event, NavalGuns.DECK_GUN_BE.get(), context -> new GunnerRenderers.Skinned<NavalGunBlockEntity>("deck_gun", 3, 2, SKIN, 3.0));
        MountedRenderer.register(event, NavalGuns.BATTLESHIP_TURRET_BE.get(), context -> new GunnerRenderers.Skinned<NavalGunBlockEntity>("battleship_turret", 5, 3, SKIN, 5.0));
    }
}
