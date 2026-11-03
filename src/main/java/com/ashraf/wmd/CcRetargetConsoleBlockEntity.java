package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CcRetargetConsoleBlockEntity extends CcConsoleBlockEntity {

    public static final int MAX_ROWS = 6;

    public CcRetargetConsoleBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_RETARGET_CONSOLE_BE.get(), pos, state, Balance.CC_RETARGET_FE); }

    @Override public CcModule kind() { return CcModule.RETARGET_CONSOLE; }

    static List<Flight> uplinked(ServerLevel level, UUID owner, String ownerName) {
        List<Flight> out = new ArrayList<>();
        long now = level.getGameTime();
        for (Flight f : FlightManager.get(level).flights()) {
            if (!f.build().uplink() || Satellites.toOrbit(f.build()) || now < f.start() || now >= f.endTime()) continue;
            if (f.isOwnedBy(owner, ownerName)) out.add(f);
        }
        out.sort((a, b) -> Long.compare(a.id(), b.id()));
        return out;
    }

    @Override
    protected ListTag buildRows(ServerLevel level, UUID owner) {
        ListTag out = new ListTag();
        long now = level.getGameTime();
        boolean cov = Uplink.active(level.getServer(), owner);
        List<Flight> l = uplinked(level, owner, getOwnerName());
        for (int i = 0; i < Math.min(MAX_ROWS, l.size()); i++) {
            Flight f = l.get(i);
            Vec3 at = f.basePosition(now);
            CompoundTag c = new CompoundTag();
            c.putLong("id", f.id());
            c.putInt("t", f.tier());
            c.putString("w", f.build().warheadType());
            c.putInt("tx", f.tx());
            c.putInt("tz", f.tz());
            c.putLong("end", f.endTime());
            c.putInt("uf", f.uflags());
            if (!f.build().seeker().isEmpty()) c.putString("sk", f.build().hypersonic() ? "hyper" : f.build().seeker());
            c.putBoolean("in", CcReach.reach(level, owner, at.x, at.z));
            c.putBoolean("cov", cov);
            out.add(c);
        }
        return out;
    }

    @Override
    protected Component act(ServerPlayer player, CcBPayload p) {
        if (p.action() != CcBPayload.RETARGET) return null;
        ServerLevel sl = (ServerLevel) level;
        String P = "message.wmd.uplink.";
        Flight f = FlightManager.get(sl).find(p.arg());
        long now = sl.getGameTime();
        if (f == null || !f.isOwnedBy(getOwner(), getOwnerName()) || Satellites.toOrbit(f.build()) || now < f.start() || now >= f.endTime())
            return Component.translatable(P + "no_flight", p.arg());
        Vec3 at = f.basePosition(now);
        CcReach.Result r = CcReach.check(sl, getOwner(), at.x, at.z);
        if (!r.ok()) return Component.translatable("message.wmd.cc.retarget.out_of_reach", f.id(), r.text());
        Component answer = Uplink.retarget(player, f.id(), p.x(), p.z(), p.name());
        arm();
        return answer;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        double end = armedUntil(), start = end - CcSpecB.ARMED_TICKS;
        float flick = tt > start && tt < end ? (float) Math.sin(Math.min(1.0, (tt - start) / 20.0) * Math.PI) : 0.0F;
        float sway = isOnline() ? 0.25F : 0.0F;
        float max = (float) Math.toRadians(CcSpecB.STICK_TILT_DEG);
        float ax = max * (sway * (float) Math.sin(tt * 0.05) + flick);
        float az = max * (sway * (float) Math.sin(tt * 0.037 + 1.3) - 0.6F * flick);
        model.getBone("stick").ifPresent(b -> { b.setRotX(ax); b.setRotZ(az); });
    }
}
