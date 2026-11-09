package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

final class SableParked {

    private SableParked() {}

    private static final int LOOK_TICKS = 100;

    private static final Map<Level, Map<UUID, ParkedShips.Parked>> LOOKS = Statics.server(new WeakHashMap<>());

    static void tick(ServerLevel level) {
        Map<UUID, ParkedShips.Parked> looks = LOOKS.get(level);
        if (looks == null) {
            SubLevelContainer c = SubLevelContainer.getContainer((Level) level);
            if (c == null) return;
            looks = new HashMap<>();
            LOOKS.put(level, looks);
            c.addObserver(new Observer(level));
        }
        if (level.getGameTime() % LOOK_TICKS != 0) return;
        long now = level.getGameTime();
        Map<UUID, ParkedShips.Parked> next = new HashMap<>();
        for (SableBridge.ShipInfo s : SableShips.all(level)) {
            AABB b = s.worldBounds();
            boolean wet = NavalLayer.inWater(level, s), down = NavalLayer.submerged(level, s);
            next.put(s.id(), new ParkedShips.Parked(s.id(), s.worldCenter().x, s.worldCenter().y, s.worldCenter().z, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ,
                    DeepSeasBridge.isSub(s.id()), down, NavalLayer.shipDepth(level, s), wet, s.blockCount(), now));
        }
        looks.clear();
        looks.putAll(next);
    }

    private static final class Observer implements SubLevelObserver {
        private final ServerLevel level;
        Observer(ServerLevel level) { this.level = level; }

        @Override
        public void onSubLevelAdded(SubLevel s) {
            try { ParkedShips.unpark(level, s.getUniqueId()); }
            catch (Throwable t) { SableBridge.fail(t); }
        }

        @Override
        public void onSubLevelRemoved(SubLevel s, SubLevelRemovalReason reason) {
            try {
                UUID id = s.getUniqueId();
                Map<UUID, ParkedShips.Parked> looks = LOOKS.get(level);
                ParkedShips.Parked last = looks == null ? null : looks.remove(id);
                if (reason == SubLevelRemovalReason.UNLOADED && last != null) ParkedShips.park(level, last);
                else ParkedShips.unpark(level, id);
            } catch (Throwable t) { SableBridge.fail(t); }
        }
    }
}
