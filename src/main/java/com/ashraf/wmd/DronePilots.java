package com.ashraf.wmd;

import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = Wmd.MODID)
final class DronePilots {

    private DronePilots() {}

    private static final String TAG = "wmd_drone_return";

    static boolean piloting(ServerPlayer sp) { return sp.getPersistentData().contains(TAG); }

    static Vec3 home(ServerPlayer sp) {
        if (!piloting(sp)) return null;
        CompoundTag t = sp.getPersistentData().getCompound(TAG);
        return new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
    }

    static void begin(ServerPlayer sp) {
        if (piloting(sp)) end(sp);
        CompoundTag t = new CompoundTag();
        t.putDouble("x", sp.getX()); t.putDouble("y", sp.getY()); t.putDouble("z", sp.getZ());
        t.putFloat("yaw", sp.getYRot()); t.putFloat("pitch", sp.getXRot());
        t.putString("dim", sp.level().dimension().location().toString());
        t.putBoolean("mayfly", sp.getAbilities().mayfly); t.putBoolean("flying", sp.getAbilities().flying);
        t.putBoolean("invisible", sp.isInvisible()); t.putBoolean("invulnerable", sp.isInvulnerable());
        sp.getPersistentData().put(TAG, t);
        PilotBodyEntity body = new PilotBodyEntity(Wmd.PILOT_BODY.get(), sp.level());
        body.setup(sp);
        body.moveTo(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), 0.0F);
        sp.level().addFreshEntity(body);
        sp.getAbilities().mayfly = true;
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
        sp.setInvisible(true);
        sp.setInvulnerable(true);
        sp.stopRiding();
    }

    static void follow(ServerPlayer sp, double x, double y, double z) {
        if (!piloting(sp)) return;

        sp.connection.teleport(x, y - 1.0, z, sp.getYRot(), sp.getXRot(), EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
        sp.fallDistance = 0.0F;
        sp.serverLevel().getChunkSource().move(sp);
    }

    static void end(ServerPlayer sp) {
        if (!piloting(sp)) return;
        CompoundTag t = sp.getPersistentData().getCompound(TAG);
        sp.getPersistentData().remove(TAG);
        ServerLevel level = sp.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim"))));
        if (level == null) level = sp.serverLevel();
        Vec3 at = new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
        sp.getAbilities().mayfly = t.getBoolean("mayfly");
        sp.getAbilities().flying = t.getBoolean("flying");
        sp.onUpdateAbilities();
        sp.setInvisible(t.getBoolean("invisible"));
        sp.setInvulnerable(t.getBoolean("invulnerable"));
        sp.fallDistance = 0.0F;
        sp.teleportTo(level, at.x, at.y, at.z, t.getFloat("yaw"), t.getFloat("pitch"));
        removeBody(level, sp, at);
    }

    private static void removeBody(Level level, ServerPlayer sp, Vec3 at) {
        for (PilotBodyEntity b : level.getEntitiesOfClass(PilotBodyEntity.class, new AABB(at, at).inflate(3.0), b -> sp.getUUID().equals(b.owner()))) b.discard();
    }

    static void bodyHit(ServerPlayer sp) { StrikeDrones.releasePilot(sp); end(sp); }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer sp && piloting(sp)) end(sp); }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && piloting(sp)) { StrikeDrones.releasePilot(sp); end(sp); }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && piloting(sp)) {
            CompoundTag t = sp.getPersistentData().getCompound(TAG);
            sp.getPersistentData().remove(TAG);
            sp.getAbilities().mayfly = t.getBoolean("mayfly");
            sp.getAbilities().flying = t.getBoolean("flying");
            sp.onUpdateAbilities();
            sp.setInvisible(t.getBoolean("invisible"));
            sp.setInvulnerable(t.getBoolean("invulnerable"));
        }
    }
}
