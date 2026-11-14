package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

final class RocketPodsClient {

    private RocketPodsClient() {}

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        MountedRenderer.register(event, RocketPods.ROCKET_POD_BE.get(), context -> new RocketPodRenderer());
    }
}
