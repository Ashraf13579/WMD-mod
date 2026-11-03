package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ArtilleryGuns {

    private ArtilleryGuns() {}

    public static final int MAX_ROWS = 8;

    private static final Set<ArtilleryGunBlockEntity> GUNS = Collections.newSetFromMap(new WeakHashMap<>());

    static void seen(ArtilleryGunBlockEntity g) {
        if (!GUNS.contains(g)) GUNS.add(g);
    }

    public static boolean loaded(ArtilleryGunBlockEntity g) {
        Level l = g.getLevel();
        return l != null && !g.isRemoved() && l.isLoaded(g.getBlockPos()) && l.getBlockEntity(g.getBlockPos()) == g;
    }

    public static List<ArtilleryGunBlockEntity> gunsOf(UUID owner) {
        List<ArtilleryGunBlockEntity> l = new ArrayList<>();
        if (owner == null) return l;
        for (ArtilleryGunBlockEntity g : GUNS) if (owner.equals(g.getOwner()) && loaded(g)) l.add(g);
        l.sort((a, b) -> a.gun() != b.gun() ? Integer.compare(b.gun(), a.gun()) : Long.compare(a.getBlockPos().asLong(), b.getBlockPos().asLong()));
        return l.size() > MAX_ROWS ? new ArrayList<>(l.subList(0, MAX_ROWS)) : l;
    }

    public static ArtilleryGunBlockEntity gunAt(Level level, BlockPos pos, UUID owner) {
        if (level == null || owner == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof ArtilleryGunBlockEntity g && owner.equals(g.getOwner()) ? g : null;
    }

    static List<ArtilleryGunBlockEntity> all() {
        List<ArtilleryGunBlockEntity> l = new ArrayList<>();
        for (ArtilleryGunBlockEntity g : GUNS) if (loaded(g)) l.add(g);
        return l;
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        GUNS.clear();
    }
}
