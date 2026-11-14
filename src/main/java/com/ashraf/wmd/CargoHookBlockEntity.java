package com.ashraf.wmd;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CargoHookBlockEntity extends P6DriveBlockEntity implements P7Parts {

    private static final Set<String> MOVED = Set.of("hook", "latch");

    private final boolean heavy;
    private float len;
    private int loadKind;
    private UUID loadId;
    private Vec3 loadPlot;
    private boolean lastRedstone;
    private float[] sentPos = { 999, 999, 999 };
    private float sentLen = -9;

    private float[] hkPos = new float[3];
    private boolean latchOpen;

    private float tLen, cLen, pLen, cLatch, pLatch;
    private float[] cPos = new float[3], pPos = new float[3], tPos = new float[3];
    private boolean cLoaded;

    public CargoHookBlockEntity(BlockPos pos, BlockState state) {
        super(state.getBlock() instanceof CargoHookBlock b && b.heavy() ? AirBlocks.CARGO_HOOK_HEAVY_BE.get() : AirBlocks.CARGO_HOOK_LIGHT_BE.get(), pos, state, 0, 1, 0);
        heavy = state.getBlock() instanceof CargoHookBlock b && b.heavy();
        float[] r = stubRest();
        hkPos = r.clone(); cPos = r.clone(); pPos = r.clone(); tPos = r.clone();
    }

    private int size() { return heavy ? 1 : 0; }

    private float[] stubRest() { return heavy ? new float[] { 0.5F, 9.5F / 16.0F, 0.5F } : new float[] { 0.0F, 7.4F / 16.0F, 0.0F }; }

    private float[] fairlead() { return heavy ? new float[] { 0.5F, 9.2F / 16.0F, 0.5F } : new float[] { 0.0F, 7.1F / 16.0F, 0.0F }; }

    @Override protected double[] intakeLocal() { float[] s = stubRest(); return new double[] { s[0], s[1], s[2] }; }
    @Override protected double[] thrustLocal() { float[] s = stubRest(); return new double[] { s[0], s[1], s[2] }; }
    @Override protected double airflow() { return 0.0; }
    @Override protected boolean damageable() { return false; }
    @Override protected int spoolUp() { return 6; }
    @Override protected int spoolDown() { return 6; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        if (now % 10 == (worldPosition.asLong() & 7)) ship = SableBridge.shipIdAt(sl, worldPosition);
        boolean down = ship != null && ShipModes.hookDown(ship);

        boolean rs = (now % 4 == 0) ? redstoneIn(sl) > 0 : lastRedstone;
        boolean release = (ship != null && ShipModes.releaseAsked(ship, now)) || (rs && !lastRedstone);
        lastRedstone = rs;
        if (release && loadKind != 0) drop(sl, true);

        Entity ent = loadKind == 1 ? sl.getEntity(loadId) : null;
        SableBridge.ShipInfo body = loadKind == 2 ? SableBridge.shipInfo(sl, loadId) : null;
        if ((loadKind == 1 && (ent == null || !ent.isAlive())) || (loadKind == 2 && body == null)) drop(sl, false);

        float[] s = stubRest();
        Vec3 top = worldVec(s[0], s[1], s[2]);
        float max = (float) Balance.HOOK_LEN[size()];
        float want = down ? max : loadKind != 0 ? (float) Balance.HOOK_MIN_LOADED : 0.0F;
        float step = (float) Balance.HOOK_REEL / 20.0F;
        float before = len;
        if (want > len && loadKind == 0 && len > 0.5F && !sl.getBlockState(BlockPos.containing(top.x, top.y - len - 0.6, top.z)).isAir()) want = len;
        len = approach(len, want, step);
        spoolTo(Math.abs(len - before) > 1.0E-4F ? 1.0F : 0.0F);

        Vec3 hook = loadKind == 1 && ent != null ? ent.position().add(0.0, ent.getBbHeight(), 0.0)
                : loadKind == 2 && body != null ? SableBridge.toWorld(sl, loadPlot) : top.add(0.0, -len, 0.0);
        force = null;
        if (loadKind == 0 && down && len > 1.0F && now % Balance.HOOK_SCAN == 0) latch(sl, hook);
        if (loadKind != 0 && hook != null && hook.distanceTo(top) > len + Balance.HOOK_PART) {
            Wmd.LOGGER.info("Cargo hook at {}: the rope parted ({} blocks for a {}-block cable)", worldPosition.toShortString(), Math.round(hook.distanceTo(top)), Math.round(len));
            drop(sl, false);
            hook = top.add(0.0, -len, 0.0);
        }
        if (loadKind != 0 && hook != null) rope(sl, top, hook, ent, body);
        latchOpen = loadKind == 0 && down;
        float[] lp = hook == null ? null : localOf(sl, hook);
        if (lp != null) hkPos = lp;
        if (now % 2 == 0 && (dist(hkPos, sentPos) > 0.04F || Math.abs(len - sentLen) > 0.1F)) { sentPos = hkPos.clone(); sentLen = len; syncNow(); }
        else syncSpool(now);
        return true;
    }

    private static float dist(float[] a, float[] b) { return (float) Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])); }

    private void latch(ServerLevel sl, Vec3 h) {
        List<Entity> es = sl.getEntities((Entity) null, new AABB(h, h).inflate(Balance.HOOK_CATCH),
                e -> e.isAlive() && !e.isSpectator() && !e.isPassenger() && !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb) && !(e instanceof Projectile)
                        && !(ship != null && ship.equals(SableBridge.shipUnder(e)))

                        && e.getBbWidth() <= Balance.HOOK_ENTITY_MAX && e.getBbHeight() <= Balance.HOOK_ENTITY_MAX
                        && e.position().add(0.0, e.getBbHeight(), 0.0).distanceTo(h) <= Balance.HOOK_CATCH + 1.0);
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : es) { double d = e.position().add(0.0, e.getBbHeight(), 0.0).distanceToSqr(h); if (d < bd) { bd = d; best = e; } }
        if (best != null) { loadKind = 1; loadId = best.getUUID(); latched(sl, h); return; }
        for (SableBridge.ShipInfo s : SableBridge.shipsAlongSegment(sl, h.add(0.0, 0.3, 0.0), h.add(0.0, -1.2, 0.0))) {
            if (s.id().equals(ship)) continue;
            Vec3 below = SableBridge.toShip(sl, s.id(), h.add(0.0, -0.6, 0.0)), at = SableBridge.toShip(sl, s.id(), h);
            if (below == null || at == null || sl.getBlockState(BlockPos.containing(below)).isAir()) continue;

            Vec3 com = s.worldCenter();
            Vec3 top = com == null || s.worldBounds() == null ? null : SableBridge.toShip(sl, s.id(), new Vec3(com.x, s.worldBounds().maxY, com.z));
            loadKind = 2; loadId = s.id(); loadPlot = top != null ? top : at;
            latched(sl, h);
            return;
        }
    }

    private void latched(ServerLevel sl, Vec3 h) {
        sl.playSound(null, h.x, h.y, h.z, WmdSounds.HOOK_LATCH.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        Entity le = loadKind == 1 ? sl.getEntity(loadId) : null;
        SableBridge.ShipInfo lb = loadKind == 2 ? SableBridge.shipInfo(sl, loadId) : null;
        Wmd.LOGGER.info("Cargo hook at {}: latched {} {} ({}) at {}", worldPosition.toShortString(), loadKind == 1 ? "entity" : "body", loadId,
                le != null ? le.getType().toShortString() : lb != null ? lb.name() + ", " + lb.blockCount() + " blocks" : "?", BlockPos.containing(h).toShortString());
        syncNow();
    }

    private void drop(ServerLevel sl, boolean thunk) {
        if (thunk) { double[] w = worldPoint(stubRest()[0], stubRest()[1] - len, stubRest()[2]); sl.playSound(null, w[0], w[1], w[2], WmdSounds.HOOK_RELEASE.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_MECH, 0.7F); }
        if (loadKind == 2 && loadId != null) SableBridge.debrisWake(sl, loadId);
        loadKind = 0; loadId = null; loadPlot = null;
        syncNow();
    }

    private void rope(ServerLevel sl, Vec3 top, Vec3 hook, Entity ent, SableBridge.ShipInfo body) {
        Vec3 d = hook.subtract(top);
        double dist = d.length();
        if (dist <= len || dist < 1.0E-3) return;
        Vec3 n = d.scale(1.0 / dist);
        SableBridge.ShipInfo me = ship == null ? null : SableBridge.shipInfo(sl, ship);
        Vec3 v1 = me != null && me.linearVelocity() != null ? me.linearVelocity() : Vec3.ZERO;
        double inv1 = me != null && me.mass() > 0.0 ? 1.0 / me.mass() : 0.0;
        Vec3 v2;
        double inv2;
        if (ent != null) { v2 = ent.getDeltaMovement().scale(20.0); inv2 = 1.0 / (ent instanceof PalletEntity pe ? pe.mass() : Balance.HOOK_MOB_MASS); }
        else { v2 = body.linearVelocity() != null ? body.linearVelocity() : Vec3.ZERO; inv2 = body.mass() > 0.0 ? 1.0 / body.mass() : 0.0; }
        if (inv1 + inv2 <= 0.0) return;
        double vrel = v2.subtract(v1).dot(n), err = dist - len;

        double mEff = 1.0 / (inv1 + inv2), w = Balance.HOOK_SPRING_W;
        double tension = mEff * (w * w * err + 2.0 * Balance.HOOK_DAMP * w * vrel);
        if (tension <= 0.0) return;
        tension = Math.min(tension, Balance.HOOK_LIFT[size()] * Balance.HOOK_G);
        double j = -tension * 0.05;
        if (inv2 > 0.0) j = Math.max(j, -Balance.HOOK_MAX_DV / inv2);
        Vec3 dv2 = n.scale(j * inv2);
        if (ent != null) {
            ent.setDeltaMovement(ent.getDeltaMovement().add(dv2.scale(1.0 / 20.0)));
            ent.fallDistance = 0.0F;
            ent.hurtMarked = true;
        } else SableBridge.shipPush(sl, loadId, dv2, hook.subtract(body.worldCenter()));
        if (me == null || inv1 <= 0.0) return;
        Vec3 f = n.scale(-j / 0.05);
        Vec3 a = SableBridge.toShip(sl, ship, top), b = SableBridge.toShip(sl, ship, top.add(f.normalize()));
        if (a == null || b == null) return;
        Vec3 fl = b.subtract(a).scale(f.length());
        float[] s = stubRest();
        Vec3 p = plotPoint(s[0], s[1], s[2]);
        force = new double[] { p.x, p.y, p.z, fl.x, fl.y, fl.z };
    }

    @Override
    public Component status() {
        String k = loadKind == 1 ? "message.wmd.hook.load_entity" : loadKind == 2 ? "message.wmd.hook.load_body" : len > 0.5F ? "message.wmd.hook.down" : "message.wmd.hook.up";
        return Component.translatable("block.wmd." + (heavy ? "cargo_hook_heavy" : "cargo_hook_light")).append(": ").append(Component.translatable(k, Math.round(len), Math.round(Balance.HOOK_LEN[size()])));
    }

    @Override protected String detail() { return "len " + Math.round(len * 10) / 10.0 + ", load " + loadKind + " " + loadId; }

    int hudState() { return loadKind != 0 ? 2 : len > 0.5F ? 1 : 0; }

    @Override
    protected void clientExtra(Level lv) {
        pLen = cLen; pLatch = cLatch;
        pPos = cPos.clone();
        cLen = approach(cLen, tLen, (float) Balance.HOOK_REEL / 20.0F * 1.5F);
        for (int i = 0; i < 3; i++) cPos[i] += (tPos[i] - cPos[i]) * 0.5F;
        cLatch = approach(cLatch, latchOpen ? 50.0F : 0.0F, 8.0F);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float r = heavy ? 3.25F / 16.0F : 2.25F / 16.0F;
        float a = Mth.lerp(pt, pLen, cLen) / r * RX;
        model.getBone("drum").ifPresent(b -> b.setRotX(a));
        float l = (float) Math.toRadians(Mth.lerp(pt, pLatch, cLatch)) * RZ;
        model.getBone("latch").ifPresent(b -> b.setRotZ(l));
    }

    @Override public Set<String> movedBones() { return MOVED; }

    @Override
    public float[] boneShift(String bone, float pt) {
        float[] s = stubRest();
        return new float[] { Mth.lerp(pt, pPos[0], cPos[0]) - s[0], Mth.lerp(pt, pPos[1], cPos[1]) - s[1], Mth.lerp(pt, pPos[2], cPos[2]) - s[2] };
    }

    @Override
    public float[][] cables(float pt) {
        float[] f = fairlead();
        float x = Mth.lerp(pt, pPos[0], cPos[0]), y = Mth.lerp(pt, pPos[1], cPos[1]), z = Mth.lerp(pt, pPos[2], cPos[2]);
        if (f[1] - y < 0.05F) return new float[0][];
        return new float[][] { { f[0], f[1], f[2], x, y, z, heavy ? 0.075F : 0.06F, 0 } };
    }

    @Override protected SoundEvent runSound() { return WmdSounds.WINCH_LOOP.get(); }
    @Override protected float soundVolume() { return (heavy ? Balance.SND_MACHINE : Balance.SND_SMALL) * cSpool; }
    @Override protected float soundPitch() { return heavy ? 0.85F : 1.05F; }

    @Override
    protected void putExtra(CompoundTag tag) {
        tag.putFloat("hkLen", len);
        tag.putInt("hkKind", loadKind);
        tag.putBoolean("hkOpen", latchOpen);
        tag.putFloat("hkX", hkPos[0]); tag.putFloat("hkY", hkPos[1]); tag.putFloat("hkZ", hkPos[2]);
        if (loadId != null) tag.putUUID("hkId", loadId);
        if (loadPlot != null) { tag.putDouble("hkPx", loadPlot.x); tag.putDouble("hkPy", loadPlot.y); tag.putDouble("hkPz", loadPlot.z); }
    }

    @Override
    protected void readExtra(CompoundTag tag, boolean client) {
        float l = Mth.clamp(tag.getFloat("hkLen"), 0.0F, 64.0F);
        float[] p = { tag.getFloat("hkX"), tag.getFloat("hkY"), tag.getFloat("hkZ") };
        boolean has = tag.contains("hkY");
        if (client) {
            tLen = l; latchOpen = tag.getBoolean("hkOpen"); cLoaded = tag.getInt("hkKind") != 0;
            if (has) tPos = p;
            return;
        }
        len = l;
        loadKind = Mth.clamp(tag.getInt("hkKind"), 0, 2);
        loadId = tag.hasUUID("hkId") ? tag.getUUID("hkId") : null;
        loadPlot = tag.contains("hkPx") ? new Vec3(tag.getDouble("hkPx"), tag.getDouble("hkPy"), tag.getDouble("hkPz")) : null;
        if (loadKind != 0 && (loadId == null || (loadKind == 2 && loadPlot == null))) loadKind = 0;
        if (has) hkPos = p;
    }
}
