package com.ashraf.wmd;

import java.util.Iterator;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class DeckLiftWalk {

    private DeckLiftWalk() {}

    private static final Set<BlockPos> HEADS = Statics.client(new java.util.HashSet<>());

    static void seen(BlockPos head) { if (HEADS.size() < 64) HEADS.add(head.immutable()); }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (HEADS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Level level = mc.level;
        if (p == null || level == null) { HEADS.clear(); return; }
        if (!AirGate.on() || p.isPassenger() || p.isSpectator() || p.getAbilities().flying) return;
        for (Iterator<BlockPos> it = HEADS.iterator(); it.hasNext(); ) {
            BlockPos hp = it.next();
            if (!(level.getBlockEntity(hp) instanceof DeckLiftBlockEntity d) || d.liftState() == DeckLiftBlockEntity.UP || d.sx() <= 0) { it.remove(); continue; }
            Vec3 l = AnchorClient.toPlot(level, hp, p.position(), 1.0F);
            if (l.x < hp.getX() || l.x > hp.getX() + d.sx() || l.z < hp.getZ() || l.z > hp.getZ() + d.sz()) continue;
            double top = d.topY(0.0F);
            if (l.y > top + 0.02 || l.y < top - Balance.LIFT_WALK_SNAP) continue;
            if (p.getDeltaMovement().y > 0.0 && l.y > top - 0.3) continue;
            Vec3 nw = ShipFrame.drawnWorld(level, hp, new Vec3(l.x, top, l.z), Float.NaN);
            p.setPos(p.getX(), nw.y, p.getZ());
            p.setDeltaMovement(p.getDeltaMovement().x, 0.0, p.getDeltaMovement().z);
            p.setOnGround(true);
            p.resetFallDistance();
            return;
        }
    }
}
