package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

final class AirStores {

    private AirStores() {}

    public static final int SONOBUOY = 10, DEPTH_CHARGE = 11, TORPEDO_T1 = 12, TORPEDO_T2 = 13;

    public static final int ROCKET = 14;

    public static final int ASHM = 15;

    public static final int GUN = 16;

    static boolean isAshm(int kind) { return kind == ASHM; }

    public static final int BOMB_BASE = 20;

    static boolean isBomb(int kind) { return kind >= BOMB_BASE && kind < BOMB_BASE + BombType.values().length; }

    static BombType bomb(int kind) { return isBomb(kind) ? BombType.values()[kind - BOMB_BASE] : null; }

    static boolean pylonHeavy(BombType t) { return t == BombType.BOUNCING; }

    static boolean isDrop(int kind) { return kind == SONOBUOY || kind == DEPTH_CHARGE || isTorpedo(kind) || isBomb(kind) || kind == ASHM; }

    static boolean isTorpedo(int kind) { return kind == TORPEDO_T1 || kind == TORPEDO_T2; }

    static TorpedoKind torpedo(int kind) { return kind == TORPEDO_T1 ? TorpedoKind.T1 : kind == TORPEDO_T2 ? TorpedoKind.T2 : null; }

    static boolean isMissile(int kind) { return kind >= 0 && kind < AirMissiles.NAMES.length; }

    static int kindOf(ItemStack s) {
        if (s.isEmpty()) return -1;
        if (s.getItem() instanceof AamItem a) return a.kind();
        if (s.is(Wmd.SONOBUOY.get())) return SONOBUOY;
        if (s.is(Wmd.DEPTH_CHARGE.get())) return DEPTH_CHARGE;
        if (s.getItem() instanceof BombItem b) return BOMB_BASE + b.type().ordinal();
        if (s.getItem() instanceof AshmItem) return ASHM;
        TorpedoKind tk = TorpedoKind.of(s);
        if (tk == TorpedoKind.T1) return TORPEDO_T1;
        if (tk == TorpedoKind.T2) return TORPEDO_T2;
        return -1;
    }

    static int freqOf(ItemStack s) {
        if (s.getItem() instanceof AamItem) return AamItem.refined(s) ? REFINED_TAG : 1;
        if (s.getItem() instanceof AshmItem) return AshmItem.refined(s) ? REFINED_TAG : 1;
        return s.is(Wmd.SONOBUOY.get()) ? SonobuoyItem.freq(s) : 1;
    }

    static final int REFINED_TAG = 2;

    static boolean refined(int kind, int freq) { return (isMissile(kind) || kind == ASHM) && freq >= REFINED_TAG; }

    static ItemStack stack(int kind, int freq) {
        if (isMissile(kind)) {
            Item m = AamItem.of(kind);
            if (m == null) return ItemStack.EMPTY;
            ItemStack s = new ItemStack(m);
            if (refined(kind, freq)) s.set(WmdComponents.WARHEAD_GRADE.get(), 1);
            return s;
        }
        if (kind == DEPTH_CHARGE) return new ItemStack(Wmd.DEPTH_CHARGE.get());
        if (kind == ASHM) {
            if (!AirGate.AVAILABLE) return ItemStack.EMPTY;
            ItemStack s = new ItemStack(AntiShipItems.ASHM.get());
            if (freq >= REFINED_TAG) s.set(WmdComponents.WARHEAD_GRADE.get(), 1);
            return s;
        }
        if (kind == ROCKET) return AirGate.AVAILABLE ? new ItemStack(RocketPods.AIR_ROCKET.get()) : ItemStack.EMPTY;
        if (isBomb(kind)) return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, bomb(kind).id)));
        if (kind == TORPEDO_T1) return new ItemStack(Wmd.TORPEDO_T1.get());
        if (kind == TORPEDO_T2) return new ItemStack(Wmd.TORPEDO_T2.get());
        if (kind == SONOBUOY) {
            ItemStack s = new ItemStack(Wmd.SONOBUOY.get());
            if (freq > 1) { CompoundTag t = new CompoundTag(); t.putInt("sonFreq", freq); s.set(DataComponents.CUSTOM_DATA, CustomData.of(t)); }
            return s;
        }
        return ItemStack.EMPTY;
    }

    static String name(int kind) {
        return isMissile(kind) ? AirMissiles.NAMES[kind] + " missile" : kind == SONOBUOY ? "sonobuoy" : kind == DEPTH_CHARGE ? "depth charge" : kind == ROCKET ? "70 mm rocket" : kind == ASHM ? "anti-ship missile"
                : isTorpedo(kind) ? torpedo(kind).label() + " torpedo" : isBomb(kind) ? bomb(kind).id : "empty";
    }

    static Component label(int kind) {
        return Component.translatable(isMissile(kind) ? "item.wmd.aam_" + AirMissiles.NAMES[kind] : kind == SONOBUOY ? "item.wmd.sonobuoy" : kind == ROCKET ? "item.wmd.air_rocket" : kind == ASHM ? "item.wmd.ashm"
                : isTorpedo(kind) ? "item.wmd." + torpedo(kind).id : isBomb(kind) ? "item.wmd." + bomb(kind).id : "item.wmd.depth_charge");
    }

    static Vec3 bombImpact(ServerLevel sl, int kind, Vec3 from, Vec3 v, UUID owner, UUID ship) {
        BombType t = bomb(kind);
        if (t == null) return null;
        if (t == BombType.GLIDE) return AirStations.gpsPoint(sl, owner, ship, from, new Vec3(v.x, 0.0, v.z), new String[] { "" });
        double vy = v.y - Balance.STORE_DROP_KICK, g = Balance.BOMB_G;
        double x = from.x, z = from.z, ground = 0.0;
        for (int pass = 0; pass < 2; pass++) {
            int cx = net.minecraft.util.Mth.floor(x), cz = net.minecraft.util.Mth.floor(z);

            ground = sl.getChunkSource().getChunkNow(cx >> 4, cz >> 4) != null
                    ? sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz)
                    : sl.getChunkSource().getGenerator().getBaseHeight(cx, cz, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, sl, sl.getChunkSource().randomState());
            double h = Math.max(0.0, from.y - ground), d = -vy;
            double tt = Math.max(1.0, (-d + Math.sqrt(d * d + 2.0 * g * h)) / g);
            x = from.x + v.x * tt; z = from.z + v.z * tt;
        }
        return new Vec3(x, ground, z);
    }

    static String bombCheck(ServerLevel sl, int kind, UUID owner, UUID ship, Vec3 from, Vec3 dir, int[][] glide) {
        BombType t = bomb(kind);
        if (t == null) return null;
        if (!Config.Server.bombAllowed(t)) return "that bomb is switched off on this server";
        if (t != BombType.GLIDE) return null;
        Vec3 gp = AirStations.gpsPoint(sl, owner, ship, from, dir, new String[] { "" });
        if (gp == null) return "no GPS target for the glide bomb (pod spot, laser spot or command center target)";
        if (Math.hypot(gp.x - from.x, gp.z - from.z) > Balance.GLIDE_RANGE) return "the glide target is out of range (" + (int) Balance.GLIDE_RANGE + " blocks)";
        glide[0] = new int[] { net.minecraft.util.Mth.floor(gp.x), net.minecraft.util.Mth.floor(gp.z) };
        return null;
    }

    static String release(ServerLevel sl, int kind, int freq, Vec3 at, Vec3 carrier, UUID owner, UUID ownShip, BlockPos source) {
        return release(sl, kind, freq, at, carrier, owner, ownShip, source, null);
    }

    static String release(ServerLevel sl, int kind, int freq, Vec3 at, Vec3 carrier, UUID owner, UUID ownShip, BlockPos source, Vec3 fwd) {
        return release(sl, kind, freq, at, carrier, owner, ownShip, source, fwd, null);
    }

    static String release(ServerLevel sl, int kind, int freq, Vec3 at, Vec3 carrier, UUID owner, UUID ownShip, BlockPos source, Vec3 fwd, int[] glide) {
        Vec3 v = carrier.add(0.0, -Balance.STORE_DROP_KICK, 0.0);
        if (isBomb(kind)) {
            if (!Config.Server.bombAllowed(bomb(kind))) return "that bomb is switched off on this server";
            BombEntity.release(sl, bomb(kind), at, v, owner, glide, null, ownShip);
            return null;
        }
        if (isTorpedo(kind)) TorpedoEntity.airDrop(sl, torpedo(kind), at, v, fwd, owner, ownShip);
        else if (kind == SONOBUOY) SonobuoyEntity.toss(sl, at, v, owner, freq);
        else if (kind == DEPTH_CHARGE) DepthChargeEntity.drop(sl, at, v, Balance.DC_DEPTH_DEFAULT, owner, ownShip, source);
        else return "not a drop store";
        return null;
    }
}
