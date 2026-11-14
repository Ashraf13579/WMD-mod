package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

final class ServiceBlocksClient {

    private ServiceBlocksClient() {}

    static final float[][] RECEPTACLE_HOUSING = {
            { -8F, 14.6F, -8F, 8F, 16F, 8F }, { -8F, 0F, -8F, 8F, 1.4F, 8F }, { -8F, 1.4F, -8F, -6.6F, 14.6F, 8F }, { 6.6F, 1.4F, -8F, 8F, 14.6F, 8F },
            { -6.6F, 1.4F, 6.6F, 6.6F, 14.6F, 8F },
            { -6.6F, 1.4F, -8F, -5.6F, 14.6F, -6.6F }, { 5.6F, 1.4F, -8F, 6.6F, 14.6F, -6.6F }, { -5.6F, 1.4F, -8F, 5.6F, 2.4F, -6.6F }, { -5.6F, 13.6F, -8F, 5.6F, 14.6F, -6.6F } };

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        MountedRenderer.register(event, ServiceBlocks.SERVICE_ARM_BE.get(), context -> new ServiceArmRenderer());
        MountedRenderer.register(event, ServiceBlocks.SERVICE_RECEPTACLE_BE.get(), context -> new CopycatUnitRenderer<ServiceReceptacleBlockEntity>("service_receptacle", 1, 1, RECEPTACLE_HOUSING));
    }
}
