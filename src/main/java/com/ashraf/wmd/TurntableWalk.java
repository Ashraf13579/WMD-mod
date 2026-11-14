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
public final class TurntableWalk {

    private TurntableWalk() {}

    private static final Set<BlockPos> HEADS = Statics.client(new java.util.HashSet<>());

    private static final java.util.Map<BlockPos, Double> LAST = Statics.client(new java.util.HashMap<>());

    static void seen(BlockPos head) { if (HEADS.size() < 64) HEADS.add(head.immutable()); }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (HEADS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Level level = mc.level;
        if (p == null || level == null) { HEADS.clear(); LAST.clear(); return; }
        for (Iterator<BlockPos> it = HEADS.iterator(); it.hasNext(); ) {
            BlockPos hp = it.next();
            if (!(level.getBlockEntity(hp) instanceof TurntableBlockEntity t) || !t.turning() || t.size() <= 0) { it.remove(); LAST.remove(hp); continue; }
            double ang = t.angle(level.getGameTime()), was = LAST.getOrDefault(hp, ang);
            LAST.put(hp, ang);
            if (!AirGate.on() || p.isPassenger() || p.isSpectator() || p.getAbilities().flying) continue;
            Vec3 l = AnchorClient.toPlot(level, hp, p.position(), 1.0F);
            double dx = l.x - t.cx(), dz = l.z - t.cz(), r = t.radius(), top = t.top();
            if (dx * dx + dz * dz > r * r) continue;
            if (l.y > top + 0.02 || l.y < top - Balance.LIFT_WALK_SNAP) continue;
            if (p.getDeltaMovement().y > 0.0 && l.y > top - 0.3) continue;
            double dd = ang - was, phi = Math.toRadians(dd), c = Math.cos(phi), s = Math.sin(phi);
            Vec3 nl = new Vec3(t.cx() + dx * c - dz * s, top, t.cz() + dx * s + dz * c);
            Vec3 nw = ShipFrame.drawnWorld(level, hp, nl, Float.NaN);
            p.setPos(nw.x, nw.y, nw.z);
            if (Math.abs(dd) > 1.0E-6) { p.setYRot(p.getYRot() + (float) dd); p.yRotO += (float) dd; }
            p.setDeltaMovement(p.getDeltaMovement().x, 0.0, p.getDeltaMovement().z);
            p.setOnGround(true);
            p.resetFallDistance();
            return;
        }
    }
}
