package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientBoarding {

    private ClientBoarding() {}

    private static final class Puppet extends RemotePlayer {
        final UUID real;
        boolean walking;
        long seen;
        Puppet(ClientLevel level, PlayerInfo info) { super(level, info.getProfile()); real = info.getProfile().getId(); }
        @Override public boolean isSpectator() { return false; }
        @Override public PlayerSkin getSkin() {
            var c = Minecraft.getInstance().getConnection();
            PlayerInfo i = c == null ? null : c.getPlayerInfo(real);
            return i != null ? i.getSkin() : super.getSkin();
        }
    }

    private static final Map<UUID, Puppet> PUPPETS = new HashMap<>();

    static void puppet(LaunchTerminalBlockEntity be, double t, PoseStack ps, MultiBufferSource buffers, int light, float pt) {
        double[] p = PadCrew.puppet(t);
        UUID who = be.boardWho();
        Minecraft mc = Minecraft.getInstance();
        if (p == null || who == null || mc.level == null || mc.getConnection() == null) return;
        Puppet e = PUPPETS.get(who);
        if (e == null || e.level() != mc.level) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(who);
            if (info == null) return;
            e = new Puppet(mc.level, info);
            PUPPETS.put(who, e);
        }
        e.seen = mc.level.getGameTime();
        e.walking = p[4] > 0.5;
        Vec3 w = PadCrew.world(PadCrew.origin(be), PadCrew.rot(be), p[0], p[1], p[2]);
        e.setPos(w.x, w.y, w.z);
        e.xo = w.x; e.yo = w.y; e.zo = w.z;
        e.xOld = w.x; e.yOld = w.y; e.zOld = w.z;
        float yaw = (float) p[3];
        e.setYRot(yaw); e.yRotO = yaw; e.yBodyRot = yaw; e.yBodyRotO = yaw; e.yHeadRot = yaw; e.yHeadRotO = yaw;
        e.setXRot(0.0F); e.xRotO = 0.0F;
        boolean crouch = p[5] > 0.5;
        e.setPose(crouch ? Pose.CROUCHING : Pose.STANDING);
        e.setShiftKeyDown(crouch);
        mc.getEntityRenderDispatcher().render(e, p[0] * PadCrew.PX, p[1] * PadCrew.PX, p[2] * PadCrew.PX, yaw, pt, ps, buffers, light);
    }

    private static final int[][] CUES = { { 0, 0, 0 }, { 30, 0, 0 }, { 40, 1, 1 }, { 80, 1, 1 }, { 110, 2, 2 }, { 112, 0, 2 }, { 150, 0, 2 }, { 180, 3, 3 }, { 225, 4, 3 },
            { 240, 1, 1 }, { 280, 1, 1 } };

    private static final Map<Long, Double> HEARD = new HashMap<>();

    static void sounds(LaunchTerminalBlockEntity be, double t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long key = be.getBlockPos().asLong();
        Double last = HEARD.get(key);
        if (t < 0) { HEARD.remove(key); return; }
        HEARD.put(key, t);
        if (last == null || t < last) return;
        for (int[] c : CUES) {
            if (last >= c[0] || t < c[0] || t - c[0] > 20) continue;
            net.minecraft.sounds.SoundEvent ev = switch (c[1]) {
                case 0 -> WmdSounds.LIFT_DOORS.get(); case 1 -> WmdSounds.LIFT_MOTOR_LOOP.get(); case 2 -> WmdSounds.LIFT_CHIME.get();
                case 3 -> WmdSounds.HATCH_OPEN.get(); default -> WmdSounds.WR_SEAL.get();
            };
            Vec3 at = switch (c[2]) {
                case 0 -> new Vec3(PadCrew.SX, PadCrew.PT + 16.0, PadCrew.SZ);
                case 1 -> new Vec3(PadCrew.SX, PadCrew.PT + 20.0 + PadCrew.liftY(c[0]), PadCrew.SZ);
                case 2 -> PadCrew.arm(14.0, PadCrew.FLOOR + 16.0, 0.0);
                default -> { Vec3 h = PadCrew.hatchSpot(); yield new Vec3(h.x, PadCrew.FLOOR + 16.0, h.z); }
            };
            Vec3 w = PadCrew.world(PadCrew.origin(be), PadCrew.rot(be), at.x, at.y, at.z);
            mc.level.playLocalSound(w.x, w.y, w.z, ev, net.minecraft.sounds.SoundSource.BLOCKS, c[1] == 1 ? 1.6F : 1.4F, 1.0F, false);
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (PUPPETS.isEmpty()) return;
        if (mc.level == null) { PUPPETS.clear(); return; }
        long now = mc.level.getGameTime();
        for (Iterator<Map.Entry<UUID, Puppet>> it = PUPPETS.entrySet().iterator(); it.hasNext(); ) {
            Puppet e = it.next().getValue();
            if (now - e.seen > 40) { it.remove(); continue; }
            e.walkAnimation.update(e.walking ? 0.75F : 0.0F, 0.4F);
            e.tickCount++;
        }
    }
}
