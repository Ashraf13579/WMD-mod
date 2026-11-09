package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class EscapePodBlockEntity extends ProcessUnitBlockEntity implements NavalPanel, CcAnimated {

    private static final PortMap PORT_MAP = new PortMap(0, 1, 2).power(1, 0, 1, Direction.SOUTH, false);

    private boolean armed = true;
    private long lastEject = -1000L;
    private boolean blocked;

    static final float DOOR_SIGN = 1.0F;

    public EscapePodBlockEntity(BlockPos pos, BlockState state) { super(Wmd.ESCAPE_POD_BE.get(), pos, state, Balance.POD_FE); }

    public boolean armed() { return armed; }

    public Vec3 centre() {
        double[] p = worldPoint(0.5, 0.2, 0.5);
        return new Vec3(p[0], p[1], p[2]);
    }

    @Override
    protected boolean work() {
        check();
        return armed;
    }

    @Override
    protected void idle() {
        super.idle();
        check();
    }

    private void check() {
        if (!armed || !(getLevel() instanceof ServerLevel sl) || sl.getGameTime() % Balance.POD_CHECK_TICKS != 0) return;
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        if (ship == null) return;
        boolean doom = false;
        DeepSeasBridge.SubState st = DeepSeasBridge.state(ship);
        if (st != null && st.imploding()) doom = true;
        if (!doom && flooded(sl)) doom = true;
        if (doom && !crew(sl).isEmpty()) eject(sl, true);
    }

    private boolean flooded(ServerLevel sl) {
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) for (int y = 0; y <= 1; y++) {
            BlockPos c = Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), x, y, z);
            if (sl.getFluidState(c).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    List<ServerPlayer> crew(ServerLevel sl) {
        Vec3 c = centre().add(0.0, 1.0, 0.0);
        List<ServerPlayer> l = new ArrayList<>();
        for (ServerPlayer p : sl.players()) if (p.isAlive() && !p.isSpectator() && p.position().add(0.0, 1.0, 0.0).distanceTo(c) <= Balance.POD_REACH) l.add(p);
        l.sort(Comparator.comparingDouble(p -> p.position().distanceToSqr(c)));
        return l.size() > Balance.POD_SEATS ? l.subList(0, Balance.POD_SEATS) : l;
    }

    public boolean hatchClear() { return HullPlug.open(this, facing(), Machines.mount(getBlockState()), new int[] { 0, 3, 0 }, new int[] { 1, 3, 0 }, new int[] { 0, 3, 1 }, new int[] { 1, 3, 1 }); }

    void eject(ServerLevel sl, boolean auto) {
        if (!armed) return;
        if (!hatchClear()) {
            if (!blocked) { blocked = true; sync(); }
            for (ServerPlayer p : crew(sl)) p.displayClientMessage(Component.translatable("message.wmd.escape_pod.blocked"), true);
            sl.playSound(null, worldPosition, WmdSounds.HATCH_OPEN.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 0.6F);
            return;
        }
        blocked = false;
        armed = false;
        lastEject = sl.getGameTime();
        List<ServerPlayer> crew = crew(sl);
        EscapePodEntity pod = EscapePodEntity.launch(sl, centre(), getOwnerId());
        for (ServerPlayer p : crew) {
            p.stopRiding();
            p.startRiding(pod, true);
            p.displayClientMessage(Component.translatable(auto ? "message.wmd.escape_pod.auto" : "message.wmd.escape_pod.ejected"), true);
        }
        sl.playSound(null, worldPosition, WmdSounds.LC_HATCH.get(), SoundSource.BLOCKS, Balance.SND_MECH, 0.9F);
        sl.playSound(null, worldPosition, WmdSounds.BAY_HATCH.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.2F);
        Wmd.LOGGER.info("Escape pod at {} ejected ({}) with {} aboard", worldPosition.toShortString(), auto ? "auto" : "manual", crew.size());
        sync();
    }

    private UUID getOwnerId() { return null; }

    public boolean tryRearm(Player player) {
        ItemStack held = player.getMainHandItem();
        if (armed || held.isEmpty() || !held.is(Wmd.ESCAPE_CAPSULE.get())) return false;
        armed = true;
        if (!player.getAbilities().instabuild) held.shrink(1);
        if (getLevel() != null) getLevel().playSound(null, worldPosition, WmdSounds.OR_CAPSULE_HATCH.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        sync();
        return true;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public String panelTitle() { return "gui.wmd.escape_pod.panel"; }

    @Override
    public List<Component> panelLines() {
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable(armed ? "gui.wmd.escape_pod.armed" : "gui.wmd.escape_pod.empty"));
        if (blocked) l.add(Component.translatable("gui.wmd.escape_pod.hatch_blocked"));
        l.add(Component.translatable("gui.wmd.escape_pod.seats", Balance.POD_SEATS, (int) Balance.POD_REACH));
        l.add(Component.translatable("gui.wmd.escape_pod.auto"));
        return l;
    }

    @Override public List<Component> panelRows() { return List.of(Component.translatable("gui.wmd.escape_pod.row_eject")); }
    @Override public boolean panelDanger(int row) { return true; }
    @Override public boolean panelEnabled(int row) { return armed; }
    @Override public int panelChipKind() { return armed ? WmdUi.K_GOOD : WmdUi.K_WARN; }
    @Override public Component panelChip() { return Component.translatable(armed ? "gui.wmd.escape_pod.chip_ready" : "gui.wmd.escape_pod.chip_empty"); }

    @Override
    public void setMode(int row) {
        if (row == 0 && getLevel() instanceof ServerLevel sl) eject(sl, false);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        for (String n : new String[] { "pod", "hatch", "beacon" }) model.getBone(n).ifPresent(b -> b.setHidden(!armed));
        model.getBone("latches").ifPresent(b -> b.setScaleX(armed ? 1.0F : 1.12F));

        double since = tt - lastEject;
        float open = since < 0.0 || since > 90.0 ? 0.0F : (float) Math.min(1.0, Math.min(since / 8.0, (90.0 - since) / 12.0));
        final float a = (float) Math.toRadians(105.0) * open * DOOR_SIGN;
        model.getBone("door0").ifPresent(b -> b.setRotZ(a));
        model.getBone("door1").ifPresent(b -> b.setRotZ(-a));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override public List<Component> guiInfo() { return List.of(Component.translatable("gui.wmd.info.use", Balance.POD_FE * 20), Component.translatable("gui.wmd.info.escape_pod", Balance.POD_SEATS)); }
    @Override protected SoundEvent runSound() { return WmdSounds.BAT_HUM_LOOP.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected String detail() { return armed ? "armed" : "empty (re-arm with an escape capsule)"; }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("epArmed", armed);
        tag.putLong("epLast", lastEject);
        tag.putBoolean("epBlk", blocked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        armed = !tag.contains("epArmed") || tag.getBoolean("epArmed");
        if (tag.contains("epLast")) lastEject = tag.getLong("epLast");
        blocked = tag.getBoolean("epBlk");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("epArmed", armed);
        tag.putLong("epLast", lastEject);
        tag.putBoolean("epBlk", blocked);
        return tag;
    }
}
