package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

final class NavalFire {

    private NavalFire() {}

    static List<NavalGunBlockEntity> guns(ServerLevel sl, BlockPos console, UUID owner) {
        List<NavalGunBlockEntity> out = new ArrayList<>();
        if (!AirGate.on() || owner == null) return out;
        UUID ship = SableBridge.shipIdAt(sl, console);
        if (ship == null) return out;
        for (NavalGunBlockEntity g : NavalGunBlockEntity.gunsOf(sl, ship)) if (Objects.equals(g.getOwner(), owner)) out.add(g);
        return out.size() > Balance.FCC_ROWS ? out.subList(0, Balance.FCC_ROWS) : out;
    }

    static Vec3[] gunTarget(ServerLevel sl, CompoundTag tgt, int[] strike, Vec3 fallbackY) {
        if (tgt != null) {
            if (tgt.hasUUID("sh")) {
                SableBridge.ShipInfo s = SableBridge.shipInfo(sl, tgt.getUUID("sh"));
                if (s != null) return new Vec3[] { s.worldCenter(), ShipDefence.velocity(sl, s) };
            }
            return new Vec3[] { new Vec3(tgt.getInt("x") + 0.5, tgt.getInt("y") + 0.5, tgt.getInt("z") + 0.5), Vec3.ZERO };
        }
        if (strike == null) return null;
        int x = strike[0], z = strike[1];
        BlockPos col = new BlockPos(x, 0, z);
        double y = sl.isLoaded(col) ? sl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : fallbackY.y;
        return new Vec3[] { new Vec3(x + 0.5, y, z + 0.5), Vec3.ZERO };
    }

    static ListTag rows(ServerLevel sl, BlockPos console, UUID owner, Vec3[] target) {
        ListTag out = new ListTag();
        long now = sl.getGameTime();
        for (NavalGunBlockEntity g : guns(sl, console, owner)) {
            CompoundTag r = new CompoundTag();
            var sp = g.spec();
            r.putLong("p", g.getBlockPos().asLong());
            r.putBoolean("xl", sp.xl());
            r.putInt("st", g.consoleStatus(now));
            r.putInt("n", g.rounds()); r.putInt("max", g.roundsMax());
            r.putInt("c", g.charges()); r.putInt("cmax", sp.charges());
            r.putBoolean("hv", g.heavyMode());
            r.putInt("nx", g.nextRound(false));
            int d = -1, rc = FireControlConsoleBlockEntity.RC_NONE;
            if (target != null) {
                Vec3 gw = SableBridge.worldPos(sl, g.getBlockPos());
                double h = Math.hypot(target[0].x - gw.x, target[0].z - gw.z);
                d = (int) Math.round(h);
                boolean heavy = sp.xl() && g.heavyMode() && g.nextRound(false) == NavalGunBlockEntity.SUPER;
                double min = heavy ? Balance.XGUN_HEAVY_MIN : sp.eyeMin();
                double max = heavy ? SuperGun.chargeRange(Balance.XGUN_HEAVY_CHARGES_MAX) : sp.conRange();
                rc = h < min ? FireControlConsoleBlockEntity.RC_CLOSE : h > max ? FireControlConsoleBlockEntity.RC_FAR : FireControlConsoleBlockEntity.RC_OK;
            }
            r.putInt("d", d); r.putInt("rc", rc);
            out.add(r);
        }
        return out;
    }

    static boolean fire(ServerLevel sl, BlockPos console, UUID owner, int i, Vec3[] target, Player by) {
        List<NavalGunBlockEntity> l = guns(sl, console, owner);
        if (i < 0 || i >= l.size()) return false;
        if (target == null) { say(by, Component.translatable("gui.wmd.fcc.gun.no_target")); return false; }
        String k = l.get(i).consoleOrder(sl, target[0], target[1]);
        say(by, Component.translatable(k));
        return "gui.wmd.fcc.gun.ordered".equals(k);
    }

    static int salvo(ServerLevel sl, BlockPos console, UUID owner, Vec3[] target) {
        if (target == null) return 0;
        int n = 0;
        for (NavalGunBlockEntity g : guns(sl, console, owner)) if ("gui.wmd.fcc.gun.ordered".equals(g.consoleOrder(sl, target[0], target[1]))) n++;
        return n;
    }

    static void toggleHeavy(ServerLevel sl, BlockPos console, UUID owner, int i, Player by) {
        List<NavalGunBlockEntity> l = guns(sl, console, owner);
        if (i < 0 || i >= l.size() || !l.get(i).spec().xl()) return;
        NavalGunBlockEntity g = l.get(i);
        g.setHeavyMode(!g.heavyMode());
        say(by, Component.translatable(g.heavyMode() ? "gui.wmd.fcc.gun.heavy_on" : "gui.wmd.fcc.gun.heavy_off", i + 1));
    }

    static int clampRow(int sel, int n) { return n <= 0 ? 0 : Mth.clamp(sel, 0, n - 1); }

    private static void say(Player by, Component c) { if (by != null) by.displayClientMessage(c, true); }
}
