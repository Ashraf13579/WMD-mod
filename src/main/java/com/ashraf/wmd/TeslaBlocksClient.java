package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class TeslaBlocksClient {

    private TeslaBlocksClient() {}

    static final float[][] HOUSING = {
            { -24F, 0F, -21F, 24F, 16F, 21F },
            { -24F, 0F, -24F, -9F, 16F, -21F }, { 9F, 0F, -24F, 24F, 16F, -21F }, { -9F, 0F, -24F, 9F, 2F, -21F }, { -9F, 14F, -24F, 9F, 16F, -21F },
            { -24F, 0F, 21F, -6.6F, 16F, 24F }, { 6.6F, 0F, 21F, 24F, 16F, 24F }, { -6.6F, 0F, 21F, 6.6F, 1.8F, 24F }, { -6.6F, 14F, 21F, 6.6F, 16F, 24F } };

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {

        MountedRenderer.register(event, TeslaBlocks.TESLA_TOWER_BE.get(), context -> new CopycatUnitRenderer<TeslaTowerBlockEntity>("tesla_tower", 10, 2, HOUSING));
    }
}
