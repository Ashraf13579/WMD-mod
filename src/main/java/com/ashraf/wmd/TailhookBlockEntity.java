package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class TailhookBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    static final float ARM_SIGN = 1.0F;

    static final float EXT_SLIDE_PX = 8.0F;

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> HOOKS = Statics.server(new HashMap<>());

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private boolean sig;

    private boolean ordered;
    private int ext;
    private UUID ship;

    private Vec3 tip, tipPrev;
    long caughtUntil;

    private double lastT = Double.NaN;
    private float extShown;
    private boolean caughtShown;

    public TailhookBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.TAILHOOK_BE.get(), pos, state, 0, 1, 0);
    }

    public boolean down() { return ext >= Balance.HOOK_TICKS; }
    public UUID ship() { return ship; }
    Vec3 tip() { return tip; }
    Vec3 tipPrev() { return tipPrev; }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }
    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

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

    static void setAll(ServerLevel sl, UUID ship, boolean down) {
        Map<UUID, Set<Long>> m = HOOKS.get(sl.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s != null) for (Long q : s) { BlockPos bp = BlockPos.of(q); if (sl.isLoaded(bp) && sl.getBlockEntity(bp) instanceof TailhookBlockEntity h && !h.isRemoved()) { h.ordered = down; h.setChanged(); } }
    }

    static String toggleAll(ServerLevel sl, UUID ship) {
        Map<UUID, Set<Long>> m = HOOKS.get(sl.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        List<TailhookBlockEntity> l = new ArrayList<>();
        if (s != null) for (Long q : s) { BlockPos bp = BlockPos.of(q); if (sl.isLoaded(bp) && sl.getBlockEntity(bp) instanceof TailhookBlockEntity h && !h.isRemoved()) l.add(h); }
        if (l.isEmpty()) return "message.wmd.tailhook.none";
        boolean down = true;
        for (TailhookBlockEntity h : l) if (h.ordered) { down = false; break; }
        for (TailhookBlockEntity h : l) { h.ordered = down; h.setChanged(); }
        return down ? "message.wmd.tailhook.down" : "message.wmd.tailhook.up";
    }

    static List<TailhookBlockEntity> downHooks(ServerLevel level) {
        List<TailhookBlockEntity> out = new ArrayList<>();
        Map<UUID, Set<Long>> m = HOOKS.get(level.dimension());
        if (m == null) return out;
        for (Set<Long> s : m.values())
            for (Long p : s) {
                BlockPos bp = BlockPos.of(p);
                if (level.isLoaded(bp) && level.getBlockEntity(bp) instanceof TailhookBlockEntity h && !h.isRemoved() && h.down() && h.tip != null && h.tipPrev != null) out.add(h);
            }
        return out;
    }

    void caught(ServerLevel sl, int ticks) {
        boolean was = sl.getGameTime() < caughtUntil;
        caughtUntil = sl.getGameTime() + ticks;
        AirCrash.excuse(ship, sl.getGameTime(), ticks + 10);
        if (!was) sync();
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { leave(sl); return false; }
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0) {
            UUID s = SableBridge.shipIdAt(sl, worldPosition);
            if (s == null ? ship != null : !s.equals(ship)) { leave(sl); ship = s; }
            if (ship != null) HOOKS.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
        }
        sig = sl.hasNeighborSignal(worldPosition);
        boolean want = sig || ordered;
        int was = ext;
        if (want && ext < Balance.HOOK_TICKS) ext++;
        else if (!want && ext > 0) ext--;
        if (was != ext) {
            if (was == 0 || was == Balance.HOOK_TICKS) playOnce(sl, WmdSounds.HOOK_MOVE.get(), Balance.SND_MECH, 1.0F);
            if (ext == 0 || ext == Balance.HOOK_TICKS || ext % 5 == 0) sync();
        }
        tipPrev = tip;
        tip = down() ? tipWorld(sl) : null;
        if (caughtShown != now < caughtUntil) { caughtShown = now < caughtUntil; sync(); }
        return false;
    }

    private Vec3 tipWorld(ServerLevel sl) {
        BlockState st = getBlockState();
        Direction m = st.hasProperty(MachineBlock.MOUNT) ? st.getValue(MachineBlock.MOUNT) : Direction.UP, f = facing();
        Vec3 c = Vec3.atCenterOf(worldPosition);
        Vec3 p = c.add(new Vec3(-m.getStepX(), -m.getStepY(), -m.getStepZ()).scale(0.5 + Balance.HOOK_DROP)).add(new Vec3(-f.getStepX(), -f.getStepY(), -f.getStepZ()).scale(Balance.HOOK_AFT));
        return SableBridge.toWorld(sl, p);
    }

    private void leave(ServerLevel sl) {
        if (ship == null) return;
        Map<UUID, Set<Long>> m = HOOKS.get(sl.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s != null) { s.remove(worldPosition.asLong()); if (s.isEmpty()) m.remove(ship); }
        ship = null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) leave(sl);
        super.setRemoved();
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (!Double.isNaN(lastT) && tt > lastT) extShown += (float) ((ext / (float) Balance.HOOK_TICKS - extShown) * Math.min(1.0, 0.5 * Math.min(5.0, tt - lastT)));
        if (Double.isNaN(lastT) || tt > lastT) lastT = tt;

        final float a = (float) Math.toRadians(caughtShown ? 80.0 : 75.0) * extShown * ARM_SIGN;
        final float slide = EXT_SLIDE_PX * extShown;
        model.getBone("arm").ifPresent(b -> b.setRotX(a));
        model.getBone("ext").ifPresent(b -> b.setPosZ(slide));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.GPOD_SPIN.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected String detail() { return (down() ? "DOWN" : ext == 0 ? "up" : "moving") + (ship == null ? ", not on a ship" : ", on " + ship); }

    @Override
    public Component status() {
        return Component.translatable("block.wmd.tailhook").append(": ").append(Component.translatable(down() ? "message.wmd.hook.down" : "message.wmd.hook.up"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putInt("thExt", ext);
        tag.putBoolean("thOrd", ordered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        ext = Math.max(0, Math.min(Balance.HOOK_TICKS, tag.getInt("thExt")));
        ordered = tag.getBoolean("thOrd");
        if (tag.contains("thCaught")) caughtShown = tag.getBoolean("thCaught");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("thExt", ext);
        tag.putBoolean("thCaught", caughtShown);
        return tag;
    }
}
