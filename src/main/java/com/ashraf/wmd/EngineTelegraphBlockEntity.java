package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class EngineTelegraphBlockEntity extends ProcessUnitBlockEntity implements CcAnimated {

    static final int STOP = Balance.PROP_STEP_STOP;
    static final float STEP_DEG = 26.0F, WHEEL_PER_RUDDER = 3.0F, NEEDLE_GAIN = 1.6F, RUDDER_CLICK = 10.0F;

    private int step = STOP, reply = STOP, rings;
    private float rudder;
    private long changed;
    private float realRudder;

    private UUID ship;

    private int cStep = STOP, cReply = STOP, cRings = -1;
    private float cRudder, cReal;
    private float leverFrom, leverTo, replyFrom, replyTo, wheelFrom, wheelTo, needle;
    private double leverT0 = -100, replyT0 = -100, wheelT0 = -100, ringT0 = -100, lastTt;

    public EngineTelegraphBlockEntity(BlockPos pos, BlockState state) { super(Wmd.ENGINE_TELEGRAPH_BE.get(), pos, state, 0); }

    void stepLever(Player player, int dir) {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        int n = ShipControl.clampStep(step + dir);
        if (n == step && dir > 0 && ship != null) {
            ShipControl.boost(sl, ship);
            sl.playSound(null, worldPosition.above(), WmdSounds.TELEGRAPH_BELL.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.2F);
            player.displayClientMessage(Component.translatable("message.wmd.telegraph.boost"), true);
            return;
        }
        if (n == step) { sl.playSound(null, worldPosition.above(), WmdSounds.TELEGRAPH_LEVER.get(), SoundSource.BLOCKS, Balance.SND_CLICK * 0.6F, 0.7F); return; }
        step = n;
        changed = sl.getGameTime();
        sl.playSound(null, worldPosition.above(), WmdSounds.TELEGRAPH_LEVER.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 0.95F + 0.1F * sl.random.nextFloat());
        publish(sl);
        player.displayClientMessage(Component.translatable(ship == null ? "message.wmd.telegraph.no_ship" : "message.wmd.telegraph.order",
                Component.translatable("gui.wmd.prop.step" + step)), true);
        sync();
    }

    void turnWheel(Player player, int dir) {
        if (!(getLevel() instanceof ServerLevel sl)) return;
        float click = (float) (RUDDER_CLICK / Balance.PROP_RUDDER_MAX);
        float n = dir == 0 ? 0.0F : Mth.clamp(Math.round((rudder + dir * click) / click) * click, -1.0F, 1.0F);
        if (Math.abs(n - rudder) < 1.0E-4) return;
        rudder = n;
        changed = sl.getGameTime();
        sl.playSound(null, worldPosition, WmdSounds.TELEGRAPH_WHEEL.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 0.9F + 0.2F * sl.random.nextFloat());
        publish(sl);
        int deg = Math.round(rudder * (float) Balance.PROP_RUDDER_MAX);
        player.displayClientMessage(Component.translatable(deg == 0 ? "message.wmd.telegraph.amidships" : deg > 0 ? "message.wmd.telegraph.starboard" : "message.wmd.telegraph.port",
                Math.abs(deg)), true);
        sync();
    }

    private void publish(ServerLevel sl) {
        ship = SableBridge.shipIdAt(sl, worldPosition);
        if (ship != null) ShipControl.publish(sl, ship, worldPosition, ShipControl.SRC_TELEGRAPH, step, rudder, 0.0F, 0.0F, changed);
    }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15)) % Balance.PROP_BEAT == 0) publish(sl);
        if (now % 5 == 0) {
            ShipControl.Answer a = ship == null ? null : ShipControl.answerOf(sl, ship);
            int target = a == null ? STOP : a.step();
            float real = a == null ? 0.0F : (float) a.rudder();
            boolean dirty = false;
            if (target != reply && now - changed >= Balance.PROP_REPLY_DELAY) {
                reply = target;
                dirty = true;
                if (reply == step) {
                    rings++;
                    sl.playSound(null, worldPosition.above(), WmdSounds.TELEGRAPH_BELL.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
                }
            }
            if (Math.abs(real - realRudder) > 1.0F) { realRudder = real; dirty = true; }
            if (dirty) sync();
        }
        return false;
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl && ship != null) ShipControl.withdraw(sl, ship, worldPosition);
        super.setRemoved();
    }

    static void clientTickTelegraph(Level lv, BlockPos pos, BlockState state, EngineTelegraphBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(lv, pos, state, be);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158F, c3 = c1 + 1.0F, u = x - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    private static float easeInOut(float x) { return x < 0.5F ? 2.0F * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 2) / 2.0F; }

    private static float easeOut(float x) { float u = 1.0F - x; return 1.0F - u * u * u; }

    private static float at(float from, float to, double t0, double tt, double len, int kind) {
        float x = (float) Mth.clamp((tt - t0) / len, 0.0, 1.0);
        float e = kind == 0 ? easeOutBack(x) : kind == 1 ? easeInOut(x) : easeOut(x);
        return from + (to - from) * e;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        lastTt = tt;
        float lever = at(leverFrom, leverTo, leverT0, tt, 5.0, 0), rep = at(replyFrom, replyTo, replyT0, tt, 8.0, 1), wheel = at(wheelFrom, wheelTo, wheelT0, tt, 6.0, 2);
        needle += (cReal * NEEDLE_GAIN - needle) * 0.2F;
        float rz = ScrewPropellerBlockEntity.RZ, rx = ScrewPropellerBlockEntity.RX;
        model.getBone("lever").ifPresent(b -> b.setRotZ((float) Math.toRadians(lever) * rz));
        model.getBone("reply").ifPresent(b -> b.setRotZ((float) Math.toRadians(rep) * rz));
        model.getBone("wheel").ifPresent(b -> b.setRotZ((float) Math.toRadians(wheel) * rz));
        model.getBone("rudder_needle").ifPresent(b -> b.setRotZ((float) Math.toRadians(needle) * rz));
        for (int i = 0; i < ShipControl.STEPS; i++) { final int k = i; model.getBone("seg" + i).ifPresent(b -> b.setHidden(k != cReply)); }
        double r = (tt - ringT0) / 20.0;
        float h = r < 0.0 || r > 0.4 ? 0.0F : (float) (r < 0.08 ? r / 0.08 : r < 0.2 ? 1.0 - (r - 0.08) / 0.12 : r < 0.28 ? (r - 0.2) / 0.08 : 1.0 - (r - 0.28) / 0.12);
        model.getBone("hammer").ifPresent(b -> b.setRotX((float) Math.toRadians(-18.0F * h) * rx));
    }

    private void clientUpdate(int st, int rp, float ru, float real, int rg) {
        double now = lastTt;
        if (cRings < 0) {
            cStep = st; cReply = rp; cRudder = ru; cReal = real; cRings = rg; needle = real * NEEDLE_GAIN;
            leverFrom = leverTo = st2deg(st); replyFrom = replyTo = st2deg(rp); wheelFrom = wheelTo = wheelDeg(ru);
            return;
        }
        if (st != cStep) { leverFrom = at(leverFrom, leverTo, leverT0, now, 5.0, 0); leverTo = st2deg(st); leverT0 = now; cStep = st; }
        if (rp != cReply) { replyFrom = at(replyFrom, replyTo, replyT0, now, 8.0, 1); replyTo = st2deg(rp); replyT0 = now; cReply = rp; }
        if (Math.abs(ru - cRudder) > 1.0E-4) { wheelFrom = at(wheelFrom, wheelTo, wheelT0, now, 6.0, 2); wheelTo = wheelDeg(ru); wheelT0 = now; cRudder = ru; }
        if (rg != cRings) { ringT0 = now; cRings = rg; }
        cReal = real;
    }

    static float st2deg(int step) { return (step - STOP) * STEP_DEG; }
    static float wheelDeg(float rudder) { return rudder * (float) Balance.PROP_RUDDER_MAX * WHEEL_PER_RUDDER; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override protected SoundEvent runSound() { return WmdSounds.TELEGRAPH_LEVER.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override public List<Component> guiInfo() { return List.of(); }

    @Override
    public Component status() {
        return Component.translatable("message.wmd.telegraph.status", Component.translatable("gui.wmd.prop.step" + step), Component.translatable("gui.wmd.prop.step" + reply),
                Math.round(rudder * (float) Balance.PROP_RUDDER_MAX), Math.round(realRudder));
    }

    @Override protected String detail() { return "order " + step + ", reply " + reply + ", rudder " + Math.round(rudder * Balance.PROP_RUDDER_MAX); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
        tag.putLong("etChg", changed);
    }

    private void put(CompoundTag tag) {
        tag.putInt("etStep", step);
        tag.putInt("etReply", reply);
        tag.putFloat("etRud", rudder);
        tag.putFloat("etReal", realRudder);
        tag.putInt("etRings", rings);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int st = ShipControl.clampStep(tag.contains("etStep") ? tag.getInt("etStep") : STOP), rp = ShipControl.clampStep(tag.contains("etReply") ? tag.getInt("etReply") : STOP);
        float ru = Mth.clamp(tag.getFloat("etRud"), -1.0F, 1.0F), real = tag.getFloat("etReal");
        int rg = tag.getInt("etRings");
        if (level != null && level.isClientSide) { clientUpdate(st, rp, ru, real, rg); return; }
        step = st; reply = rp; rudder = ru; realRudder = real; rings = rg; changed = tag.getLong("etChg");
        clientUpdate(st, rp, ru, real, rg);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        return tag;
    }
}
