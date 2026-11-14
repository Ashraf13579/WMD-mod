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
public final class CargoRampWalk {

    private CargoRampWalk() {}

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
            if (!(level.getBlockEntity(hp) instanceof CargoRampBlockEntity r) || !r.head() || !r.opened() || r.width() == 0) { it.remove(); continue; }
            Vec3 c = ShipFrame.drawnWorld(level, hp, Vec3.atCenterOf(hp), Float.NaN);
            if (c.distanceToSqr(p.position()) > 24.0 * 24.0) continue;
            float th = r.poseAfter(level.getGameTime() - r.syncAtClient())[0];
            if (th < 30.0F) continue;
            double[] l = r.local(AnchorClient.toPlot(level, hp, p.position(), 1.0F));
            if (l[1] < -0.3 || l[1] > r.width() + 0.3 || l[0] < 0.0) continue;
            double y = r.leafHeight(l[0], th);
            if (Double.isNaN(y) || l[2] > y + 0.02 || l[2] < y - Balance.RAMP_WALK_SNAP) continue;
            if (p.getDeltaMovement().y > 0.0) continue;
            Vec3 nw = ShipFrame.drawnWorld(level, hp, r.gridPoint(l[0], l[1], y), Float.NaN);
            p.setPos(p.getX(), nw.y, p.getZ());
            p.setDeltaMovement(p.getDeltaMovement().x, 0.0, p.getDeltaMovement().z);
            p.setOnGround(true);
            p.resetFallDistance();
            return;
        }
    }
}
