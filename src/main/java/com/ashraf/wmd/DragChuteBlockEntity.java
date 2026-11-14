package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class DragChuteBlockEntity extends ProcessUnitBlockEntity implements CcAnimated, CopycatState.Host {

    public static final int PACKED = 0, OUT = 1, SPENT = 2;

    private final CopycatState copycat = new CopycatState();
    private int state;

    private static final java.util.Map<UUID, long[]> SEEN = Statics.server(new java.util.HashMap<>());

    static int stateAboard(ServerLevel sl, UUID ship) {
        long[] s = ship == null ? null : SEEN.get(ship);
        return s == null || sl.getGameTime() - s[1] > 40 ? -1 : (int) s[0];
    }
    private long stateAt;

    private int cState;
    private long cStateAt;
    private float cLid, pLid;

    public DragChuteBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.DRAG_CHUTE_BE.get(), pos, state, 0, 1, 0);
    }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    public int chuteState() { return level != null && level.isClientSide ? cState : state; }
    public long chuteStateAt() { return level != null && level.isClientSide ? cStateAt : stateAt; }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    Direction aft() { return Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.SOUTH); }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return false;
        long now = sl.getGameTime();
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        if (ship == null) return false;
        SableBridge.ShipInfo info = SableBridge.shipInfo(sl, ship);
        if (info == null || info.linearVelocity() == null) return false;
        Vec3 v = ShipDefence.velocity(sl, info).scale(20.0);
        Vec3 horiz = new Vec3(v.x, 0.0, v.z);
        double speed = horiz.length();
        boolean asked = ShipModes.takeChuteRequest(ship, now);
        SEEN.put(ship, new long[] { state, now });
        if (state == PACKED) {
            if (asked && speed > Balance.CHUTE_RIP_SPEED) {
                state = SPENT; stateAt = now;
                playOnce(sl, WmdSounds.CHUTE_OPEN.get(), Balance.SND_MECH, 0.8F);
                playOnce(sl, WmdSounds.CHUTE_OPEN.get(), Balance.SND_CLICK, 1.6F);
                Wmd.LOGGER.info("Drag chute at {}: ripped off at {} b/s", worldPosition.toShortString(), Math.round(speed));
                sync();
                return false;
            }
            if (asked && speed > Balance.CHUTE_MIN_SPEED) {
                state = OUT; stateAt = now;
                playOnce(sl, WmdSounds.CHUTE_OPEN.get(), Balance.SND_MECH, 0.8F);
                Wmd.LOGGER.info("Drag chute at {}: out at {} b/s", worldPosition.toShortString(), Math.round(speed));
                sync();
            }
            return false;
        }
        if (state == OUT) {
            if (speed < Balance.CHUTE_RELEASE_SPEED || now - stateAt > Balance.CHUTE_MAX_TICKS) {
                state = SPENT; stateAt = now;
                playOnce(sl, WmdSounds.CHUTE_OPEN.get(), Balance.SND_CLICK, 1.4F);
                sync();
                return false;
            }
            double a = Math.min(Balance.CHUTE_MAX_DECEL, Balance.CHUTE_DRAG_K * speed * speed);

            double dv = Math.min(speed, a / 20.0);
            SableBridge.shipPush(sl, ship, horiz.normalize().scale(-dv), Vec3.ZERO);
        }
        return false;
    }

    boolean repack(Player p) {
        if (state != SPENT || !(level instanceof ServerLevel sl)) return false;
        state = PACKED; stateAt = sl.getGameTime();
        sl.playSound(null, worldPosition, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        p.displayClientMessage(Component.translatable("message.wmd.chute.repacked"), true);
        sync();
        return true;
    }

    @Override
    public Component status() {
        String k = state == PACKED ? "message.wmd.chute.packed" : state == OUT ? "message.wmd.chute.out" : "message.wmd.chute.spent";
        return Component.translatable("block.wmd.drag_chute").append(": ").append(Component.translatable(k));
    }

    @Override
    protected void clientEffects(Level lv) {}

    private void easeLid() {
        pLid = cLid;
        float want = cState == PACKED ? 0.0F : 1.0F;
        cLid = want > cLid ? Math.min(want, cLid + 0.35F) : Math.max(want, cLid - 0.08F);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (level != null && level.getGameTime() != lastEase) { lastEase = level.getGameTime(); easeLid(); }
        float k = pLid + (cLid - pLid) * pt;
        model.getBone("housing_lid").ifPresent(b -> b.setRotX((float) Math.toRadians(Balance.CHUTE_LID_OPEN * k)));
        model.getBone("bag").ifPresent(b -> b.setHidden(cState != PACKED));
    }

    private long lastEase = -1;

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.GPOD_SPIN.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected String detail() { return new String[] { "packed", "OUT", "spent" }[Math.max(0, Math.min(2, state))]; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("dcState", state);
        tag.putLong("dcAt", stateAt);
        copycat.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        copycat.load(tag, registries, level != null && level.isClientSide);
        int s = Math.max(0, Math.min(2, tag.getInt("dcState")));
        long at = tag.getLong("dcAt");
        cState = s; cStateAt = at;
        if (level != null && level.isClientSide) return;
        state = s; stateAt = at;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("dcState", state);
        tag.putLong("dcAt", stateAt);
        copycat.putSync(tag);
        return tag;
    }
}
