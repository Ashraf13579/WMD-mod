package com.ashraf.wmd;

import com.seibel.distanthorizons.api.DhApi;

final class DhRead {

    private DhRead() {}

    static double farRadius() {
        if (DhFar.hidden || DhApi.Delayed.configs == null) return 0.0;
        var g = DhApi.Delayed.configs.graphics();
        if (!Boolean.TRUE.equals(g.renderingEnabled().getValue())) return 0.0;
        Integer d = g.chunkRenderDistance().getValue();
        return d == null ? 0.0 : d * 16.0;
    }
}
