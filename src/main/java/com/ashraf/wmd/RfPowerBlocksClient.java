package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class RfPowerBlocksClient {

    private RfPowerBlocksClient() {}

    static final float[][] HOUSING = {
            { -8F, 14.6F, -8F, 8F, 16F, 8F }, { -8F, 0F, -8F, 8F, 1.4F, 8F },
            { -8F, 1.4F, -8F, -6.6F, 14.6F, 8F }, { 6.6F, 1.4F, -8F, 8F, 14.6F, 8F },
            { -6.6F, 1.4F, -8F, -5F, 14.6F, -6.6F }, { 5F, 1.4F, -8F, 6.6F, 14.6F, -6.6F }, { -5F, 1.4F, -8F, 5F, 3F, -6.6F }, { -5F, 12.2F, -8F, 5F, 14.6F, -6.6F },
            { -6.6F, 1.4F, 6.6F, -6F, 14.6F, 8F }, { 6F, 1.4F, 6.6F, 6.6F, 14.6F, 8F } };

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {

        MountedRenderer.register(event, RfPowerBlocks.SENDER_BE.get(), context -> new CopycatUnitRenderer<RfLinkBlockEntity>("rf_sender", 2, -1, HOUSING));

        MountedRenderer.register(event, RfPowerBlocks.RECEIVER_BE.get(), context -> new CopycatUnitRenderer<RfLinkBlockEntity>("rf_receiver", 2, -1, HOUSING));
    }
}
