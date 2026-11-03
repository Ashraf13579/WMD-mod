package com.ashraf.wmd;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class SwarmLauncherBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, ScreenCommandTarget {

    public static final int X0 = -2, X1 = 2, Y0 = 0, Y1 = 2, Z0 = -2, Z1 = 2;
    public static final int CMD_TARGET = 0, CMD_FIRE = 1;

    static final int[] ORDER = new int[Balance.SW_CELLS];
    static {
        java.util.List<Integer> l = new java.util.ArrayList<>();
        for (int c = 0; c < Balance.SW_CELLS; c++) l.add(c);
        l.sort((a, b) -> { int da = a / 7 + a % 7, db = b / 7 + b % 7; return da != db ? Integer.compare(da, db) : Integer.compare(a / 7, b / 7); });
        for (int i = 0; i < l.size(); i++) ORDER[i] = l.get(i);
    }

    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    private int loaded, stored, tx, tz;
    private boolean hasTarget;
    private long salvoT0 = Long.MIN_VALUE;
    private int[] fired = new int[0];
    private int chargedThisTick;
    private long chargeTick = -1;

    private final IEnergyStorage socket = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int n, boolean sim) {
            if (level == null) return 0;
            long t = level.getGameTime();
            if (t != chargeTick) { chargeTick = t; chargedThisTick = 0; }
            int take = Math.max(0, Math.min(n, Math.min(Balance.SW_BUFFER_FE - stored, Balance.SW_CHARGE_IN - chargedThisTick)));
            if (!sim && take > 0) { stored += take; chargedThisTick += take; setChanged(); }
            return take;
        }
        @Override public int extractEnergy(int n, boolean sim) { return 0; }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return Balance.SW_BUFFER_FE; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return stored < Balance.SW_BUFFER_FE; }
    };

    public SwarmLauncherBlockEntity(BlockPos pos, BlockState state) { super(Wmd.SWARM_LAUNCHER_BE.get(), pos, state, 0); }

    int load(int packs) {
        int room = (Balance.SW_CELLS - loaded) / Balance.SW_PACK, take = Math.max(0, Math.min(room, packs));
        if (take > 0) { loaded += take * Balance.SW_PACK; setChanged(); sync(); }
        return take;
    }

    private boolean may(ServerPlayer sp) { return Access.mayUse(this, sp); }

    @Override
    public void command(ServerPlayer sp, int cmd, int x, int z) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!may(sp)) { Access.deny(this, sp); return; }
        Vec3 deck = deck();
        if (cmd == CMD_TARGET) {
            if (Math.hypot(x + 0.5 - deck.x, z + 0.5 - deck.z) > Balance.SW_RANGE) { sp.displayClientMessage(Component.translatable("message.wmd.swarm.range", Balance.SW_RANGE).withStyle(ChatFormatting.RED), true); return; }
            tx = x; tz = z; hasTarget = true; setChanged(); sync();
        } else if (cmd == CMD_FIRE) {
            String why = !hasTarget ? "no_target" : loaded <= 0 ? "empty" : stored < Balance.SW_SALVO_FE ? "power" : firing() ? "busy" : null;
            if (why != null) { sp.displayClientMessage(Component.translatable("message.wmd.swarm." + why).withStyle(ChatFormatting.RED), true); return; }
            int ty = sl.hasChunk(tx >> 4, tz >> 4) ? sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz)
                    : sl.getChunkSource().getGenerator().getBaseHeight(tx, tz, Heightmap.Types.WORLD_SURFACE_WG, sl, sl.getChunkSource().randomState());
            fired = java.util.Arrays.copyOf(ORDER, loaded);
            stored -= Balance.SW_SALVO_FE;
            loaded = 0;
            salvoT0 = sl.getGameTime();
            Swarm.launch(sl, deck, facing().get3DDataValue(), new Vec3(tx + 0.5, ty, tz + 0.5), fired, getOwner() != null ? getOwner() : sp.getUUID());
            HeatLog.fired(level, worldPosition, getOwner());
            setChanged(); sync();
        }
    }

    Vec3 deck() { double[] w = worldPoint(0.0, 38.0 / 16.0, 0.0); return new Vec3(w[0], w[1], w[2]); }

    public boolean firing() { return level != null && salvoT0 != Long.MIN_VALUE && level.getGameTime() < salvoT0 + (long) fired.length * Balance.SW_RIPPLE + 90; }

    private void sync() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public static void serverTickSwarm(Level level, BlockPos pos, BlockState bs, SwarmLauncherBlockEntity be) { ProcessUnitBlockEntity.serverTick(level, pos, bs, be); }

    @Override protected boolean work() { return firing(); }

    @Override public void empDrain() { stored = 0; setChanged(); }

    public int loaded() { return loaded; }
    public int storedShown() { return stored; }
    public boolean hasTarget() { return hasTarget; }
    public int targetX() { return tx; }
    public int targetZ() { return tz; }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        int n = fired.length;
        boolean[] open = new boolean[Balance.SW_CELLS];
        for (int c : fired) if (c >= 0 && c < open.length) open[c] = true;
        double end = salvoT0 + (double) n * Balance.SW_RIPPLE + 80.0;
        for (int k = 0; k < n; k++) {
            int c = fired[k];
            double t0 = salvoT0 + (double) k * Balance.SW_RIPPLE;
            double f = salvoT0 == Long.MIN_VALUE ? 0.0 : tt < end ? Mth.clamp((tt - t0) / Swarm.HATCH_TICKS, 0.0, 1.0) : Mth.clamp(1.0 - (tt - end) / 10.0, 0.0, 1.0);
            final float a = (float) Math.toRadians(110.0 * f);
            model.getBone("h" + c).ifPresent(b -> b.setRotX(a));
        }
        for (int c = 0; c < Balance.SW_CELLS; c++) if (!open[c]) model.getBone("h" + c).ifPresent(b -> b.setRotX(0.0F));
    }

    public static void clientTickSwarm(Level level, BlockPos pos, BlockState bs, SwarmLauncherBlockEntity be) { ProcessUnitBlockEntity.clientTick(level, pos, bs, be); }

    @Override protected SoundEvent runSound() { return WmdSounds.SW_DRONE_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 2, 0); }
    @Override protected float soundVolume() { return 0.0F; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int inputTanks() { return 0; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public IEnergyStorage guiEnergy() { return socket; }
    @Override public int guiFeUse() { return 0; }
    @Override public int guiStatus() { return firing() ? 1 : loaded <= 0 ? 4 : stored < Balance.SW_SALVO_FE ? 4 : 0; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.sw_load", loaded, Balance.SW_CELLS, Balance.SW_PACK),
                Component.translatable("gui.wmd.info.sw_salvo", String.format("%,d", Balance.SW_SALVO_FE), Balance.SW_RANGE));
    }

    @Override protected String detail() { return "loaded " + loaded + ", buffer " + stored + " FE, target " + (hasTarget ? tx + " " + tz : "none") + ", firing " + firing(); }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 2, 0, 0, Direction.EAST) ? socket : null; }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 5).power(2, 0, 0, Direction.EAST, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        putState(tag);
        tag.putBoolean("swsave", true);
        ownerData.write(tag);
    }

    private void putState(CompoundTag tag) {
        tag.putInt("swloaded", loaded);
        tag.putInt("swstored", stored);
        tag.putBoolean("swhas", hasTarget);
        tag.putInt("swtx", tx); tag.putInt("swtz", tz);
        tag.putLong("swt0", salvoT0);
        tag.putIntArray("swfired", fired);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("swloaded")) {
            loaded = Mth.clamp(tag.getInt("swloaded"), 0, Balance.SW_CELLS);
            stored = Mth.clamp(tag.getInt("swstored"), 0, Balance.SW_BUFFER_FE);
            hasTarget = tag.getBoolean("swhas");
            tx = tag.getInt("swtx"); tz = tag.getInt("swtz");
            salvoT0 = tag.getLong("swt0");
            fired = tag.getIntArray("swfired");
        }
        if (tag.contains("swsave")) ownerData.read(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        putState(tag);
        return tag;
    }
}
