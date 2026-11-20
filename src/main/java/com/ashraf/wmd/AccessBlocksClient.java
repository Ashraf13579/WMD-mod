package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class AccessBlocksClient {

    private AccessBlocksClient() {}

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {

        MountedRenderer.register(event, AccessBlocks.TERMINAL_BE.get(), context -> new CopycatUnitRenderer<AccessTerminalBlockEntity>("access_terminal", 2, 1, null));
    }
}
