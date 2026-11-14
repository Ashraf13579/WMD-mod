package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class JatoRackBlockEntity extends AirEngineBlockEntity {

    @Override protected boolean damageable() { return false; }

    public static final int EMPTY = 0, LOADED = 1, BURNING = 2, SPENT = 3;

    static final double BOTTLE_Y = 7.0 / 16.0, BOTTLE_Z = 1.0, NOZZLE_Z = 40.0 / 16.0;
    static final float HOOK_OPEN = -65.0F;

    private int state = EMPTY, burn, spentTicks;
    private float gYaw, gPitch;
    private long lastBoost = -1;
    private boolean lastRs;

    private int cState = EMPTY;
    private float tYaw, tPitch, cYaw, cPitch, pYaw, pPitch, cHook, pHook, cRam, pRam;

    public JatoRackBlockEntity(BlockPos pos, BlockState state) { super(Wmd.JATO_RACK_BE.get(), pos, state, 0, 0, 0); }

    @Override protected double[] intakeLocal() { return new double[] { 0.0, BOTTLE_Y, NOZZLE_Z }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, BOTTLE_Y, BOTTLE_Z }; }
    @Override protected double airflow() { return 0.0; }
    @Override protected boolean rocket() { return true; }

    @Override protected double heatSignature() { return Balance.HEAT_SIG_HOT; }
    @Override protected double noiseDb() { return Balance.AIR_NOISE_DB_JATO; }

    public boolean loaded() { return state == LOADED; }

    boolean empty() { return state == EMPTY; }

    @Override
    boolean interactExtra(Player player, ItemStack held) {
        if (!held.is(Wmd.JATO_BOOSTER.get())) return false;
        if (state != EMPTY) {
            player.displayClientMessage(Component.translatable("message.wmd.jato.busy"), true);
            return true;
        }
        if (!player.getAbilities().instabuild) held.shrink(1);
        loadBooster();
        player.displayClientMessage(Component.translatable("message.wmd.jato.loaded"), true);
        return true;
    }

    boolean loadBooster() {
        if (state != EMPTY) return false;
        state = LOADED;
        if (level instanceof net.minecraft.server.level.ServerLevel sl) playOnce(sl, WmdSounds.JATO_LATCH.get(), Balance.SND_MECH, 1.0F);
        syncNow();
        return true;
    }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15)) % Balance.PROP_BEAT == 0) {
            airBeat(sl);
            if (state == BURNING) report(sl, burnShare(), true, gYaw, gPitch);
        }

        boolean rs = redstone > 0;
        boolean pulse = rs && !lastRs;
        lastRs = rs;
        long b = ship == null ? -1 : ShipControl.boostAt(sl, ship);
        boolean boost = b > lastBoost && now - b <= Balance.PROP_BEAT;
        if (b > lastBoost) lastBoost = b;
        if (state == LOADED && (boost || pulse)) { state = BURNING; burn = Balance.JATO_BURN; airBeat(sl); }
        switch (state) {
            case BURNING -> {
                double[] s = steerLocal(true);
                float r = (float) Balance.JATO_GIMBAL_RATE * 0.05F;
                gYaw = approach(gYaw, (float) (s[0] * Balance.JATO_GIMBAL), r);
                gPitch = approach(gPitch, (float) (s[1] * Balance.JATO_GIMBAL), r);
                double thrust = Balance.JATO_THRUST * airShare(1.0);
                double ty = Math.toRadians(gYaw), tp = Math.toRadians(gPitch);
                setForce(thrust * Math.cos(ty) * Math.cos(tp), thrust * Math.sin(ty), thrust * Math.sin(tp));
                if (--burn <= 0) { state = SPENT; spentTicks = 0; force = null; }
            }
            case SPENT -> {
                force = null;
                gYaw = approach(gYaw, 0.0F, 2.0F);
                gPitch = approach(gPitch, 0.0F, 2.0F);
                if (++spentTicks >= Balance.JATO_RELEASE_DELAY) { release(sl); state = EMPTY; }
            }
            default -> force = null;
        }
        syncMaybe();
        return state == BURNING;
    }

    private double burnShare() { return state == BURNING ? Balance.JATO_THRUST / Balance.TF_THRUST : 0.0; }

    private void release(ServerLevel sl) {
        double[] p = worldPoint(0.0, BOTTLE_Y, BOTTLE_Z), n = worldPoint(0.0, BOTTLE_Y, BOTTLE_Z - 1.0), d = worldPoint(0.0, BOTTLE_Y - 1.0, BOTTLE_Z);
        Vec3 pos = new Vec3(p[0], p[1], p[2]), nose = new Vec3(n[0] - p[0], n[1] - p[1], n[2] - p[2]), away = new Vec3(d[0] - p[0], d[1] - p[1], d[2] - p[2]);
        Vec3 vel = away.scale(Balance.JATO_KICK);
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        if (info != null && info.linearVelocity() != null) vel = vel.add(info.linearVelocity().scale(1.0 / 20.0));
        JatoBottleEntity e = new JatoBottleEntity(sl, pos, nose, vel);
        sl.addFreshEntity(e);
        sl.playSound(null, BlockPos.containing(pos), WmdSounds.JATO_RELEASE.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
    }

    private int sentState = -1;
    private float sentYaw = 999, sentPitch = 999;

    private void syncMaybe() {
        boolean big = state != sentState;
        boolean small = Math.abs(gYaw - sentYaw) > 1.0F || Math.abs(gPitch - sentPitch) > 1.0F;
        if (!big && !(small && level != null && level.getGameTime() % 4 == 0)) return;
        sentState = state; sentYaw = gYaw; sentPitch = gPitch;
        syncNow();
    }

    @Override
    protected void easeTick(Level lv) {
        pYaw = cYaw; pPitch = cPitch; pHook = cHook; pRam = cRam;
        float k = (float) Balance.JATO_GIMBAL_RATE / 20.0F * 1.3F;
        cYaw = approach(cYaw, tYaw, k);
        cPitch = approach(cPitch, tPitch, k);
        cHook = approach(cHook, cState == EMPTY ? HOOK_OPEN : 0.0F, cState == EMPTY ? 65.0F / 4.0F : 65.0F / 10.0F);
        cRam = approach(cRam, 0.0F, 1.2F / 10.0F);
        if (cState == BURNING) {
            double[] c = worldPoint(0.0, BOTTLE_Y, NOZZLE_Z + 0.4);
            VeilBridge.glow(0x4A54_0000_0000_0000L ^ worldPosition.asLong(), c[0], c[1], c[2], 1.0F, 0.6F, 0.25F, 6.0F, 1.6F, lv.getGameTime());
        }
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float hook = (float) Math.toRadians(Mth.lerp(partialTick, pHook, cHook)) * RX;
        model.getBone("hook_f").ifPresent(b -> b.setRotX(hook));
        model.getBone("hook_r").ifPresent(b -> b.setRotX(hook));
        float ram = Mth.lerp(partialTick, pRam, cRam);
        model.getBone("ram").ifPresent(b -> b.setPosY(ram));
        boolean bottle = cState == LOADED || cState == BURNING || cState == SPENT;
        model.getBone("bottle").ifPresent(b -> b.setHidden(!bottle));
        model.getBone("nozzle").ifPresent(b -> b.setHidden(!bottle));
        float yaw = (float) Math.toRadians(Mth.lerp(partialTick, pYaw, cYaw)), pitch = (float) Math.toRadians(Mth.lerp(partialTick, pPitch, cPitch));
        model.getBone("nozzle").ifPresent(b -> { b.setRotX(pitch * RX); b.setRotY(yaw * RY); });
        model.getBone("flame").ifPresent(b -> b.setHidden(cState != BURNING));
        model.getBone("lamp").ifPresent(b -> b.setHidden(cState != LOADED));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (cState != BURNING) return;
        var r = lv.random;
        double[] c = worldPoint(0.0, BOTTLE_Y, NOZZLE_Z + 0.3), a = worldPoint(0.0, BOTTLE_Y, NOZZLE_Z + 1.3);
        for (int i = 0; i < 3; i++) {
            double sp = 0.4 + r.nextDouble() * 0.4;
            lv.addParticle(i == 0 ? WmdParticles.ROCKET_FLAME.get() : WmdParticles.LAUNCH_SMOKE.get(),
                    c[0] + (r.nextDouble() - 0.5) * 0.3, c[1] + (r.nextDouble() - 0.5) * 0.3, c[2] + (r.nextDouble() - 0.5) * 0.3,
                    (a[0] - c[0]) * sp, (a[1] - c[1]) * sp, (a[2] - c[2]) * sp);
        }
    }

    @Override protected void runStarted(Level lv) { playOnce(lv, WmdSounds.JATO_IGNITE.get(), Balance.SND_RELEASE, 1.0F); }
    @Override protected void runStopped(Level lv) { playOnce(lv, WmdSounds.JATO_BURNOUT.get(), Balance.SND_MECH, 1.0F); }
    @Override protected SoundEvent runSound() { return WmdSounds.JATO_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_GIANT; }
    @Override protected boolean soundAlive() { return cState == BURNING; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 2); }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public PortMap guiPortMap() { return new PortMap(0, 0, 3); }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.jato.state" + cState),
                Component.translatable("gui.wmd.jato.fire"),
                Component.translatable(!SableBridge.loaded() ? "gui.wmd.screw.no_sable" : "gui.wmd.jato.rule"));
    }

    @Override protected String detail() { return "state " + state + ", burn " + burn + ", gimbal " + Math.round(gYaw) + " / " + Math.round(gPitch); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
        tag.putInt("jtBurn", burn);
        tag.putInt("jtSpent", spentTicks);
        tag.putBoolean("jtRs", lastRs);
    }

    private void put(CompoundTag tag) {
        tag.putInt("jtState", state);
        tag.putFloat("jtYaw", gYaw);
        tag.putFloat("jtPitch", gPitch);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int st = Mth.clamp(tag.getInt("jtState"), EMPTY, SPENT);
        float lim = (float) Balance.JATO_GIMBAL;
        float ya = Mth.clamp(tag.getFloat("jtYaw"), -lim, lim), pi = Mth.clamp(tag.getFloat("jtPitch"), -lim, lim);
        if (level != null && level.isClientSide) {
            if (st == EMPTY && (cState == SPENT || cState == BURNING)) cRam = -1.2F;
            cState = st; tYaw = ya; tPitch = pi;
            return;
        }
        state = st; gYaw = ya; gPitch = pi; burn = tag.getInt("jtBurn"); spentTicks = tag.getInt("jtSpent"); lastRs = tag.getBoolean("jtRs");
        cState = st; tYaw = cYaw = pYaw = ya; tPitch = cPitch = pPitch = pi; cHook = pHook = st == EMPTY ? HOOK_OPEN : 0.0F;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        return tag;
    }
}
