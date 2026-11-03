package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class CcLaunchConsoleBlockEntity extends CcConsoleBlockEntity {

    public CcLaunchConsoleBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_LAUNCH_CONSOLE_BE.get(), pos, state, Balance.CC_LAUNCH_FE); }

    @Override public CcModule kind() { return CcModule.LAUNCH_CONSOLE; }

    @Override protected boolean wantsIntel() { return true; }

    @Override
    protected ListTag buildRows(ServerLevel level, UUID owner) {
        ListTag out = CcLaunch.rows(level, owner);
        for (CruiseLauncherBlockEntity l : CruiseLaunchers.launchersOf(owner)) out.add(CruiseLaunchers.row(level, owner, l));
        return out;
    }

    @Override
    protected Component act(ServerPlayer player, CcBPayload p) {
        if (p.action() != CcBPayload.AIM && p.action() != CcBPayload.LAUNCH && p.action() != CcBPayload.ASAT_PICK) return null;
        String P = "message.wmd.cc.launch.";
        BlockPos tp = BlockPos.of(p.arg());
        CruiseLauncherBlockEntity cl = CruiseLaunchers.launcherAt(level, tp, getOwner());
        if (cl != null) return cruise(player, p, cl, tp);
        LaunchTerminalBlockEntity t = CcLaunch.terminalAt(level, tp, getOwner());
        if (t == null) return Component.translatable(P + "no_terminal", tp.toShortString());
        CcReach.Result r = CcReach.check(level, getOwner(), tp);
        if (!r.ok()) return Component.translatable(P + "out_of_reach", tp.toShortString(), r.text());
        if (p.action() == CcBPayload.ASAT_PICK) return t.pickAsat(getOwner(), p.x());
        int x = Mth.clamp(p.x(), -29_999_000, 29_999_000), z = Mth.clamp(p.z(), -29_999_000, 29_999_000);
        arm();
        if (p.action() == CcBPayload.AIM) {
            t.remoteAim(x, z);
            return Component.translatable(P + "aimed", tp.toShortString(), x, z);
        }
        Component why = t.remoteLaunch(getOwner(), player.getGameProfile().getName(), x, z);
        if (why != null) return Component.translatable(P + "refused", tp.toShortString(), why);
        if (t.asatLoaded()) return Component.translatable(P + "asat", tp.toShortString(), t.asatSat());
        Wmd.LOGGER.info("Command center: {} launched the terminal at {} at {} {}", player.getGameProfile().getName(), tp.toShortString(), x, z);
        return Component.translatable(P + "launched", tp.toShortString(), x, z);
    }

    private Component cruise(ServerPlayer player, CcBPayload p, CruiseLauncherBlockEntity cl, BlockPos tp) {
        String P = "message.wmd.cc.launch.";
        if (p.action() == CcBPayload.ASAT_PICK) return null;
        CcReach.Result r = CcReach.check(level, getOwner(), tp);
        if (!r.ok()) return Component.translatable(P + "out_of_reach", tp.toShortString(), r.text());
        int x = Mth.clamp(p.x(), -29_999_000, 29_999_000), z = Mth.clamp(p.z(), -29_999_000, 29_999_000);
        arm();
        if (p.action() == CcBPayload.AIM) {
            cl.aimAt(x, z);
            return Component.translatable(P + "aimed", tp.toShortString(), x, z);
        }
        int st = cl.order(x, z, 1, player.getGameProfile().getName());
        if (st != CruiseLauncherBlockEntity.S_RAISING && st != CruiseLauncherBlockEntity.S_FIRING)
            return Component.translatable(P + "refused", tp.toShortString(), Component.translatable("gui.wmd.cruise.st." + st));
        Wmd.LOGGER.info("Command center: {} fired the cruise launcher at {} at {} {}", player.getGameProfile().getName(), tp.toShortString(), x, z);
        return Component.translatable(P + "cruise_fired", tp.toShortString(), x, z);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float u = armedShare(tt);
        model.getBone("cover").ifPresent(b -> b.setRotX((float) Math.toRadians(CcSpecB.COVER_OPEN_DEG) * u));
        model.getBone("key0").ifPresent(b -> b.setRotZ((float) Math.toRadians(CcSpecB.KEY_TURN_DEG) * u));
        model.getBone("key1").ifPresent(b -> b.setRotZ((float) -Math.toRadians(CcSpecB.KEY_TURN_DEG) * u));
    }

    float armedShare(double tt) {
        double end = armedUntil(), start = end - CcSpecB.ARMED_TICKS;
        if (tt <= start || tt >= end) return 0.0F;
        return (float) Math.min(1.0, Math.min((tt - start) / 10.0, (end - tt) / 10.0));
    }
}
