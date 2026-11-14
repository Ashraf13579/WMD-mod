package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FlightCheck {

    private FlightCheck() {}

    static UUID lookedAt(ServerPlayer sp) {
        if (!AirGate.on() || sp == null || !(sp.level() instanceof ServerLevel sl) || !SableBridge.active()) return null;
        HitResult hr = sp.pick(Balance.FC_REACH, 0.0F, false);
        if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
            UUID id = SableBridge.shipIdAt(sl, bh.getBlockPos());
            if (id != null) return id;
        }
        Vec3 eye = sp.getEyePosition(), end = eye.add(sp.getLookAngle().scale(Balance.FC_REACH));
        UUID best = null;
        double bd = Double.MAX_VALUE;
        for (SableBridge.ShipInfo s : SableBridge.shipsAlongSegment(sl, eye, end)) {
            double d = eye.distanceToSqr(s.worldCenter());
            if (d < bd) { bd = d; best = s.id(); }
        }
        return best != null ? best : SableBridge.shipUnder(sp);
    }

    static boolean inReach(ServerPlayer sp, UUID ship) {
        if (!AirGate.on() || sp == null || ship == null || !(sp.level() instanceof ServerLevel sl)) return false;
        SableBridge.ShipInfo s = SableBridge.shipInfo(sl, ship);
        return s != null && s.worldBounds().inflate(Balance.FC_REACH).contains(sp.getEyePosition());
    }

    public static void sendTo(ServerPlayer sp, UUID ship) {
        if (!AirGate.on() || sp == null || ship == null || !(sp.level() instanceof ServerLevel sl)) return;
        CompoundTag t = compute(sl, ship);
        if (t != null) PacketDistributor.sendToPlayer(sp, new FlightCheckPayload.Show(t));
    }

    static CompoundTag compute(ServerLevel sl, UUID ship) {
        if (!AirGate.on() || sl == null || ship == null) return null;
        SableBridge.ShipInfo s = SableBridge.shipInfo(sl, ship);
        if (s == null) return null;
        int fans = 0, turbofans = 0, jatos = 0, cores = 0, guns = 0, props = 0, vtols = 0;
        double thrust = 0.0, top = 0.0, demand = 0.0, burn = 0.0, vtol = 0.0;
        int fuel = 0;
        for (BlockPos p : SableBridge.blockEntitiesOn(sl, ship)) {
            if (!sl.isLoaded(p)) continue;
            BlockEntity be = sl.getBlockEntity(p);
            if (be instanceof DuctedFanBlockEntity) { fans++; thrust += Balance.EDF_THRUST; top = Math.max(top, Balance.EDF_AIRFLOW); demand += Balance.EDF_FE + Balance.EDF_FE_LOAD; }
            else if (be instanceof TurbofanBlockEntity tf) { turbofans++; thrust += Balance.TF_THRUST; top = Math.max(top, Balance.TF_AIRFLOW); fuel += tf.fuelMb(); burn += Balance.TF_FUEL_FULL; }
            else if (be instanceof JetEngineBlockEntity j) { turbofans++; thrust += j.flightThrust(); top = Math.max(top, Balance.JET_AIRFLOW); burn += j.flightBurn(); }
            else if (be instanceof JatoRackBlockEntity) jatos++;
            else if (be instanceof LiftFanBlockEntity) { vtols++; vtol += Balance.LIFT_THRUST; }
            else if (be instanceof HeliRotorBlockEntity r) { vtols++; vtol += Balance.HELI_LIFT[rotorIndex(r.bladeLength())]; }
            else if (be instanceof HeliTurboshaftBlockEntity) demand += Balance.TURBO_FE;
            else if (be instanceof TiltrotorBlockEntity) { vtols++; props++; vtol += Balance.TR_THRUST; thrust += Balance.TR_THRUST; top = Math.max(top, Balance.TR_AIRFLOW); demand += Balance.TR_FE; }
            else if (be instanceof TurbopropBlockEntity) { props++; thrust += Balance.TP_THRUST; top = Math.max(top, Balance.TP_AIRFLOW); demand += Balance.TP_FE; }
            else if (be instanceof PowerCoreBlockEntity c) { cores++; fuel += c.fuelMb(); }
            else if (be instanceof FuelTankBlockEntity ft) fuel += ft.fuelMb();
            else if (be instanceof JetCockpitBlockEntity) demand += Balance.COCKPIT_FE;
            else if (be instanceof NoseRadarBlockEntity) demand += Balance.NOSE_FE;
            else if (be instanceof JammerPodBlockEntity) demand += Balance.JAM_POD_FE;
            else if (be instanceof GunPodBlockEntity) { guns++; demand += Balance.GPOD_FE; }
        }
        top = Math.min(top, Balance.AIRCRAFT_MAX_SPEED);

        double[] ph = SableBridge.physicsAt(sl, s.worldCenter());
        double grav = ph == null ? Balance.FC_GRAVITY : Math.abs(ph[2]);
        double weight = Math.max(1.0, s.mass()) * grav;
        double cruise = top * Balance.FC_CRUISE_SHARE;
        int wings = SableBridge.countLiftBlocks(sl, ship);
        double pressure = ph == null ? AirEngineBlockEntity.airPressure(sl, s.worldCenter().y) : ph[0];
        double aoa = Math.toRadians(Balance.FC_CRUISE_AOA);
        double perSpeed = wings * Balance.FC_LIFT_K * pressure * Math.sin(aoa) * Math.cos(aoa);
        double lift = perSpeed * cruise;
        double stall = perSpeed > 1.0E-6 ? weight / perSpeed : -1.0;
        int[] charge = PowerCoreBlockEntity.chargeOf(sl, ship);
        double apu = cores > 0 && fuel > 0 ? cores * (double) Balance.CORE_APU_FE : 0.0;
        if (cores > 0 && demand > 0.0) burn += cores / (double) Balance.CORE_APU_TICKS_PER_MB;
        double v = ShipDefence.velocity(sl, s).length() * 20.0;
        AirPicture.Aircraft a = AirPicture.get(sl, ship);
        CompoundTag t = new CompoundTag();
        t.putString("n", s.name() == null ? "" : s.name());
        t.putUUID("id", ship);
        t.putDouble("m", s.mass());
        t.putInt("b", s.blockCount());
        t.putDouble("w", weight);
        t.putDouble("th", thrust);
        t.putInt("fan", fans);
        t.putInt("tf", turbofans);
        t.putInt("jato", jatos);
        t.putInt("prop", props);
        t.putInt("vtol", vtols);
        t.putDouble("vt", vtol);
        t.putInt("gun", guns);
        t.putDouble("top", top);
        t.putDouble("cr", cruise);
        t.putInt("wing", wings);
        t.putDouble("lift", lift);
        t.putDouble("stall", stall);
        t.putInt("core", cores);
        t.putInt("fs", charge[0]);
        t.putInt("fc", charge[1]);
        t.putDouble("dem", demand);
        t.putDouble("apu", apu);
        t.putInt("fuel", fuel);
        t.putDouble("burn", burn);
        t.putDouble("v", v);
        t.putInt("alt", (int) Math.round(s.worldCenter().y));
        t.putInt("agl", a == null || Double.isNaN(a.agl()) ? -1 : (int) Math.round(a.agl()));
        return t;
    }

    private static int rotorIndex(int len) {
        for (int i = 0; i < HeliRotorBlockEntity.LENGTHS.length; i++) if (HeliRotorBlockEntity.LENGTHS[i] == len) return i;
        return 0;
    }

    static void ask(ServerPlayer sp, UUID ship) {
        if (!AirGate.on() || !inReach(sp, ship)) return;
        sendTo(sp, ship);
    }
}
