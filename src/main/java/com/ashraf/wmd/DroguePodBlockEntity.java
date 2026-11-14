package com.ashraf.wmd;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class DroguePodBlockEntity extends P6DriveBlockEntity implements P7Parts {

    private static final Set<String> MOVED = Set.of("drogue", "lamp_a", "lamp_g", "lamp_r");
    static final float AY = 6.4F / 16.0F, TAIL_Z = 21.4F / 16.0F, BASKET_Z = 22.0F / 16.0F;

    private float len;
    private RefuelProbeBlockEntity contact;
    private long redUntil, lastFlow = -100;
    private int moved;
    private float[] basket = { 0.0F, AY, BASKET_Z }, sentBasket = { 999, 999, 999 };
    private int lamp;

    private float[] cB = { 0.0F, AY, BASKET_Z }, pB = { 0.0F, AY, BASKET_Z }, tB = { 0.0F, AY, BASKET_Z };
    private int cLamp;
    private float tLen;

    public DroguePodBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.DROGUE_POD_BE.get(), pos, state, 0, 1, 0);
    }

    @Override protected double[] intakeLocal() { return new double[] { 0.0, AY, 0.0 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, AY, 0.5 }; }
    @Override protected double airflow() { return 0.0; }
    @Override protected boolean damageable() { return false; }
    @Override protected int spoolUp() { return 20; }
    @Override protected int spoolDown() { return 40; }
    @Override protected double spinRps() { return 8.0; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        if (now % 10 == (worldPosition.asLong() & 7)) ship = SableBridge.shipIdAt(sl, worldPosition);
        boolean stream = ship != null && ShipModes.refuel(ship);
        float before = len;
        len = approach(len, stream ? (float) Balance.HOSE_LEN : 0.0F, (float) Balance.HOSE_REEL / 20.0F);
        boolean reeling = Math.abs(len - before) > 1.0E-4F;

        Vec3 free = worldVec(0.0, AY, BASKET_Z + len).add(0.0, -len * Balance.HOSE_SAG, 0.0);
        Vec3 at = free;
        if (contact != null) {
            Vec3 tip = contact.isRemoved() ? null : contact.tipWorld();
            if (tip == null || tip.distanceTo(free) > Balance.DROGUE_BREAK || len < Balance.HOSE_LEN * 0.8 || contact.hudState(now) == 0) {
                breakAway(sl, now);
            } else {
                at = tip;
                pump(sl, now);
            }
        } else if (len > Balance.HOSE_LEN * 0.8 && now % 2 == 0) {
            for (RefuelProbeBlockEntity p : RefuelProbeBlockEntity.live()) {
                if (p.getLevel() != sl || p.shipId() == null || p.shipId().equals(ship)) continue;
                if (p.tipWorld().distanceTo(free) <= Balance.DROGUE_CATCH) {
                    contact = p;
                    moved = 0;
                    sl.playSound(null, free.x, free.y, free.z, WmdSounds.REFUEL_CONTACT.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
                    Wmd.LOGGER.info("Drogue pod at {}: contact with the probe at {}", worldPosition.toShortString(), p.getBlockPos().toShortString());
                    break;
                }
            }
        }
        int[] fuel = PowerCoreBlockEntity.fuelOf(sl, ship);
        lamp = len < 0.5F ? 0 : now < redUntil || fuel[0] <= 0 ? 3 : now - lastFlow < 10 ? 2 : 1;
        spoolTo(len > 0.5F ? 1.0F : 0.0F);
        float[] b = localOf(sl, at);
        if (b != null) basket = b;
        force = null;
        if (now % 2 == 0 && (dist(basket, sentBasket) > 0.05F || lamp != cLampSent)) { sentBasket = basket.clone(); cLampSent = lamp; syncNow(); }
        else syncSpool(now);
        reelingNow = reeling;
        return true;
    }

    private int cLampSent = -1;
    private boolean reelingNow;

    private static float dist(float[] a, float[] b) { return (float) Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])); }

    private void pump(ServerLevel sl, long now) {
        int took = PowerCoreBlockEntity.moveFuel(sl, ship, Balance.REFUEL_RATE, true);
        if (took <= 0) return;
        int put = PowerCoreBlockEntity.moveFuel(sl, contact.shipId(), took, false);
        if (put < took) PowerCoreBlockEntity.moveFuel(sl, ship, took - put, false);
        if (put > 0) { lastFlow = now; moved += put; contact.flowing(now); }
    }

    private void breakAway(ServerLevel sl, long now) {
        if (contact != null && now - lastFlow < 40) {
            redUntil = now + Balance.DROGUE_RED_TICKS;
            Wmd.LOGGER.info("Drogue pod at {}: break-away after {} mB", worldPosition.toShortString(), moved);
        }
        contact = null;
    }

    @Override
    public Component status() {
        String k = contact != null ? "message.wmd.drogue.contact" : len > 0.5F ? "message.wmd.drogue.streamed" : "message.wmd.drogue.stowed";
        return Component.translatable("block.wmd.drogue_pod").append(": ").append(Component.translatable(k, moved));
    }

    @Override protected String detail() { return "hose " + Math.round(len * 10) / 10.0 + ", lamp " + lamp + ", contact " + (contact != null) + ", moved " + moved; }

    int hudState(long now) { return now - lastFlow < 10 ? 2 : len > 0.5F ? 1 : 0; }

    @Override
    protected void clientExtra(Level lv) {
        pB = cB.clone();
        for (int i = 0; i < 3; i++) cB[i] += (tB[i] - cB[i]) * 0.5F;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float a = spinAt(pt) * RZ;
        model.getBone("rat").ifPresent(b -> b.setRotZ(a));
    }

    @Override public Set<String> movedBones() { return MOVED; }

    @Override
    public float[] boneShift(String bone, float pt) {
        if (bone.startsWith("lamp_")) {
            int want = bone.equals("lamp_a") ? 1 : bone.equals("lamp_g") ? 2 : 3;
            return cLamp == want ? new float[3] : null;
        }
        return new float[] { Mth.lerp(pt, pB[0], cB[0]), Mth.lerp(pt, pB[1], cB[1]) - AY, Mth.lerp(pt, pB[2], cB[2]) - BASKET_Z };
    }

    @Override public boolean boneLit(String bone) { return bone.startsWith("lamp_"); }

    @Override
    public float[][] cables(float pt) {
        float x = Mth.lerp(pt, pB[0], cB[0]), y = Mth.lerp(pt, pB[1], cB[1]), z = Mth.lerp(pt, pB[2], cB[2]);
        if (z - TAIL_Z < 0.2F && Math.abs(x) < 0.1F && Math.abs(y - AY) < 0.1F) return new float[0][];
        return new float[][] { { 0.0F, AY, TAIL_Z, x, y, z - 0.05F, 0.05F, 1 } };
    }

    @Override protected SoundEvent runSound() { return cLamp == 2 ? WmdSounds.FUEL_FLOW_LOOP.get() : WmdSounds.HOSE_REEL_LOOP.get(); }
    @Override protected float soundVolume() { return cLamp == 2 ? Balance.SND_SMALL : Balance.SND_SMALL * 0.8F; }
    @Override protected float soundPitch() { return 1.0F; }
    @Override protected boolean soundAlive() { return cLamp == 2 || Math.abs(cB[2] - pB[2]) > 0.01F; }

    @Override
    protected void putExtra(CompoundTag tag) {
        tag.putFloat("dpLen", len);
        tag.putInt("dpLamp", lamp);
        tag.putFloat("dpX", basket[0]); tag.putFloat("dpY", basket[1]); tag.putFloat("dpZ", basket[2]);
    }

    @Override
    protected void readExtra(CompoundTag tag, boolean client) {
        float l = Mth.clamp(tag.getFloat("dpLen"), 0.0F, 64.0F);
        float[] b = tag.contains("dpY") ? new float[] { tag.getFloat("dpX"), tag.getFloat("dpY"), tag.getFloat("dpZ") } : null;
        if (client) { tLen = l; cLamp = tag.getInt("dpLamp"); if (b != null) tB = b; return; }
        len = l;
        if (b != null) basket = b;
    }
}
