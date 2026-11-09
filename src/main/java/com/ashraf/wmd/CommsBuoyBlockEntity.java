package com.ashraf.wmd;

import java.util.ArrayList;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class CommsBuoyBlockEntity extends ProcessUnitBlockEntity implements NavalPanel, CcAnimated {

    public static final int S_STOWED = 0, S_RISING = 1, S_FLOATING = 2, S_DEEP = 3, S_REELING = 4, S_DRY = 5;
    private static final PortMap PORT_MAP = new PortMap(0, 1, 2).power(1, 0, 1, Direction.SOUTH, false);

    private boolean out;
    private UUID buoy;
    private int state = S_STOWED;
    private boolean linkUp;
    private float drumSpin;

    public CommsBuoyBlockEntity(BlockPos pos, BlockState state) { super(Wmd.COMMS_BUOY_BE.get(), pos, state, Balance.BUOY_FE); }

    public boolean isOut() { return out; }
    public int buoyState() { return state; }

    public Vec3 fairlead() {
        double[] p = worldPoint(0.5, 0.95, 0.45);
        return new Vec3(p[0], p[1], p[2]);
    }

    private CommsBuoyEntity buoyEntity(ServerLevel sl) {
        return buoy != null && sl.getEntity(buoy) instanceof CommsBuoyEntity b && b.isAlive() ? b : null;
    }

    public boolean trunkClear() { return HullPlug.open(this, facing(), Machines.mount(getBlockState()), new int[] { 0, 2, 0 }, new int[] { 1, 2, 0 }, new int[] { 0, 2, 1 }, new int[] { 1, 2, 1 }); }

    static final float DOOR_SIGN = 1.0F;
    private float doorOpen;

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        CommsBuoyEntity b = buoyEntity(sl);
        if (out && b == null && !trunkClear()) {
            out = false;
            tell(sl, Component.translatable("message.wmd.comms_buoy.blocked"));
            sync();
        }
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);

        if (out && ship != null && sl.getGameTime() % 10 == 0) {
            SableBridge.ShipInfo si = SableBridge.shipInfo(sl, ship);
            if (si != null && si.linearVelocity().horizontalDistance() > Balance.BUOY_MAX_SPEED && b != null) {
                out = false;
                tell(sl, Component.translatable("message.wmd.comms_buoy.too_fast"));
            }
        }
        int st;
        if (out) {
            if (b == null) {
                b = CommsBuoyEntity.release(sl, this);
                buoy = b.getUUID();
                sl.playSound(null, worldPosition, WmdSounds.T4_WINCH.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.1F);
            }
            st = b.floating() ? S_FLOATING : b.hanging() ? S_DEEP : b.dry() ? S_DRY : S_RISING;
        } else if (b != null) {
            b.reel();
            st = S_REELING;
        } else st = S_STOWED;
        if (st == S_FLOATING && ship != null && sl.getGameTime() % Balance.MAST_BEAT == 0) {
            Masts.beat(sl, ship, Masts.BUOY);
            NoiseNet.emit(sl, this, b.position(), Balance.BUOY_DB, NoiseNet.Kind.MACHINE, null, null);
        }
        if (st != S_FLOATING && state == S_FLOATING && ship != null) { Masts.drop(sl, ship, Masts.BUOY); NoiseNet.silence(sl, this); }
        boolean link = !CommsLink.cutOff(sl, worldPosition);
        if (st != state || link != linkUp) {
            if (st == S_FLOATING && state != S_FLOATING) sl.playSound(null, b.getX(), b.getY(), b.getZ(), WmdSounds.BUOY_SPLASH.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
            state = st;
            linkUp = link;
            sync();
        }
        return out || b != null;
    }

    @Override
    public void setRemoved() {
        if (getLevel() instanceof ServerLevel sl) {
            CommsBuoyEntity b = buoyEntity(sl);
            if (b != null) b.discard();
            UUID ship = SableBridge.shipIdAt(sl, worldPosition);
            if (ship != null) Masts.drop(sl, ship, Masts.BUOY);
            NoiseNet.silence(sl, this);
        }
        super.setRemoved();
    }

    void stowed() {
        buoy = null;
        state = S_STOWED;
        sync();
    }

    private void tell(ServerLevel sl, Component c) {
        Vec3 w = fairlead();
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(w) < 32 * 32) p.displayClientMessage(c, true);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public String panelTitle() { return "gui.wmd.comms_buoy.panel"; }

    @Override
    public List<Component> panelLines() {
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable("gui.wmd.comms_buoy.st." + state));
        l.add(Component.translatable(linkUp ? "gui.wmd.comms_buoy.link_up" : "gui.wmd.comms_buoy.link_cut"));
        l.add(Component.translatable("gui.wmd.comms_buoy.cable", (int) Balance.BUOY_CABLE));
        return l;
    }

    @Override public List<Component> panelRows() { return List.of(Component.translatable(out ? "gui.wmd.comms_buoy.row_reel" : "gui.wmd.comms_buoy.row_release")); }
    @Override public int panelChipKind() { return linkUp ? WmdUi.K_GOOD : WmdUi.K_BAD; }
    @Override public Component panelChip() { return Component.translatable(linkUp ? "gui.wmd.comms_buoy.chip_link" : "gui.wmd.comms_buoy.chip_cut"); }

    @Override
    public void setMode(int row) {
        if (row != 0 || !(getLevel() instanceof ServerLevel sl)) return;
        if (!out && buoyEntity(sl) == null && !trunkClear()) { tell(sl, Component.translatable("message.wmd.comms_buoy.blocked")); return; }
        out = !out;
        sl.playSound(null, worldPosition, WmdSounds.T4_WINCH.get(), SoundSource.BLOCKS, Balance.SND_CLICK, out ? 1.1F : 0.9F);
        sync();
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        boolean moving = state == S_RISING || state == S_REELING;
        if (moving) drumSpin += state == S_RISING ? 0.15F : -0.15F;
        final float a = drumSpin;
        model.getBone("drum").ifPresent(b -> b.setRotX(a));
        final boolean gone = state != S_STOWED;
        for (String n : new String[] { "buoy", "antenna", "blink" }) model.getBone(n).ifPresent(b -> b.setHidden(gone));
        doorOpen += ((gone ? 1.0F : 0.0F) - doorOpen) * 0.08F;
        final float d = (float) Math.toRadians(105.0) * doorOpen * DOOR_SIGN;
        model.getBone("door0").ifPresent(b -> b.setRotZ(d));
        model.getBone("door1").ifPresent(b -> b.setRotZ(-d));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override public List<Component> guiInfo() { return List.of(Component.translatable("gui.wmd.info.use", Balance.BUOY_FE * 20), Component.translatable("gui.wmd.info.comms_buoy", (int) Balance.COMMS_DEPTH)); }
    @Override protected SoundEvent runSound() { return WmdSounds.T4_WINCH.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected String detail() { return (out ? "out" : "in") + ", state " + state + ", link " + (linkUp ? "up" : "CUT"); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 1, Direction.SOUTH) ? energy() : null; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
    }

    private void put(CompoundTag tag) {
        tag.putBoolean("cbOut", out);
        tag.putInt("cbState", state);
        tag.putBoolean("cbLink", linkUp);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        out = tag.getBoolean("cbOut");
        state = tag.getInt("cbState");
        linkUp = tag.getBoolean("cbLink");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        put(tag);
        return tag;
    }
}
