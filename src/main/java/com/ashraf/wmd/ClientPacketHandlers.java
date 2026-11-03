package com.ashraf.wmd;

public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void flights(FlightsPayload payload) {
        if (payload.full()) ClientFlights.set(payload.flights());
        else ClientFlights.update(payload.flights(), payload.removed());
    }

    public static void impact(ImpactPayload p) {
        WarheadType t = WarheadType.ofOrdinal(p.warhead());
        boolean shell = (p.flags() & ImpactPayload.SHELL) != 0;
        if (t == WarheadType.HEAVY_BLAST) {
            t = WarheadType.HE;
            if (p.stage() == 0 && !shell) heavyPound(p.x(), p.y(), p.z());
        } else if (t == WarheadType.CARPET) t = WarheadType.CLUSTER;
        else if (t == WarheadType.UPHEAVAL || t == WarheadType.DAM_BREAKER) t = WarheadType.HE;
        else if (t == WarheadType.NAPALM) t = WarheadType.INCENDIARY;
        else if (t == WarheadType.PENETRATOR || t == WarheadType.EARTHQUAKE) t = WarheadType.BUNKER;
        else if (t == WarheadType.DAISY_CUTTER) {
            t = WarheadType.THERMOBARIC;
            if (p.stage() == 0 && !shell) heavyPound(p.x(), p.y(), p.z());
        }
        ImpactFx.start(p.x(), p.y(), p.z(), p.tier(), t, p.stage(), p.air(), (p.flags() & ImpactPayload.WET) != 0, shell);
        ClientReplay.impact(p.x(), p.y(), p.z(), p.tier(), t, p.stage(), p.air(), (p.flags() & ImpactPayload.WET) != 0);
        if (p.stage() == 0 && !shell) DhBridge.impact(p.x(), p.y(), p.z(), p.tier(), t);
    }

    private static void heavyPound(double x, double y, double z) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        T4Fx.shakeNear(new net.minecraft.world.phys.Vec3(x, y, z), 150.0, 1.3F);
        mc.level.playLocalSound(x, y, z, WmdSounds.HE_FAR.get(), net.minecraft.sounds.SoundSource.BLOCKS, 9.0F, 0.55F, true);
    }
}
