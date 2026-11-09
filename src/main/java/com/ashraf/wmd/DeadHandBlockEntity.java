package com.ashraf.wmd;

import java.util.ArrayList;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class DeadHandBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, ScreenCommandTarget, HardenedSite {

    @Override public int blastRating() { return 3; }

    static boolean isBunker(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DeadHandBlock) return true;
        return state.getBlock() instanceof MachinePartBlock && level.getBlockEntity(pos) instanceof MachinePartBlockEntity part && part.getController() != null
                && level.getBlockState(part.getController()).getBlock() instanceof DeadHandBlock;
    }

    public static final int X0 = -2, X1 = 2, Y0 = 0, Y1 = 3, Z0 = -2, Z1 = 2;
    public static final int CMD_KEY0 = 0, CMD_KEY1 = 1, CMD_CANCEL = 2, CMD_LINK = 3, CMD_UNLINK = 4;

    public static final int P_READY = 0, P_NO_MISSILE = 1, P_NO_TARGET = 2, P_UNLOADED = 3, P_NOT_READY = 4;

    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    private boolean key0, key1, doorOpen;
    private long doorAt = Long.MIN_VALUE, keyAt0 = Long.MIN_VALUE, keyAt1 = Long.MIN_VALUE, alarmAt = -1;
    private final List<Long> pads = new ArrayList<>();

    private int[] board = new int[0];
    private long[] candidates = new long[0];

    public DeadHandBlockEntity(BlockPos pos, BlockState state) { super(Wmd.DEADHAND_BE.get(), pos, state, 0); }

    public boolean armed() { return key0 && key1; }

    boolean inside(Player p) {
        BlockPos l = Machines.toLocal(worldPosition, facing(), Machines.mount(getBlockState()), BlockPos.containing(p.getX(), p.getY() + 0.2, p.getZ()));
        return l.getX() >= X0 && l.getX() <= X1 && l.getZ() >= Z0 && l.getZ() <= Z1 && l.getY() >= 0 && l.getY() <= 2;
    }

    private boolean may(Player p) { return Access.mayUse(this, p); }

    @Override
    public void command(ServerPlayer sp, int cmd, int x, int z) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!may(sp)) { Access.deny(this, sp); return; }
        if (!inside(sp)) { sp.displayClientMessage(Component.translatable("message.wmd.deadhand.inside").withStyle(ChatFormatting.RED), true); return; }
        long now = sl.getGameTime();
        switch (cmd) {
            case CMD_KEY0, CMD_KEY1 -> {
                boolean was = armed();
                if (cmd == CMD_KEY0) { key0 = !key0; keyAt0 = now; } else { key1 = !key1; keyAt1 = now; }
                sound(WmdSounds.DH_KEY.get(), 1.0F);
                if (armed() && !was) { sound(WmdSounds.DH_ARMED.get(), 2.0F); Wmd.LOGGER.info("[deadhand] {} ARMED by {} ({} pads)", worldPosition.toShortString(), sp.getGameProfile().getName(), pads.size()); }
                if (!armed() && was && alarmAt >= 0) sound(WmdSounds.DH_CANCEL.get(), 2.0F);
            }
            case CMD_CANCEL -> { if (DeadHand.cancel(sl, worldPosition)) sound(WmdSounds.DH_CANCEL.get(), 2.0F); }
            case CMD_LINK -> {
                long pick = Long.MIN_VALUE;
                for (long c : candidates) { BlockPos b = BlockPos.of(c); if (b.getX() == x && b.getZ() == z) pick = c; }
                if (pick == Long.MIN_VALUE || pads.size() >= Balance.DH_MAX_PADS || pads.contains(pick)) return;
                pads.add(pick);
            }
            case CMD_UNLINK -> pads.removeIf(p -> BlockPos.of(p).getX() == x && BlockPos.of(p).getZ() == z);
            default -> { return; }
        }
        push(sl);
        refreshBoard(sl);
        setChanged();
        sync();
    }

    private void push(ServerLevel sl) { alarmAt = DeadHand.update(sl, worldPosition, getOwner(), getOwnerName(), armed(), pads); }

    void fired() { key0 = false; key1 = false; alarmAt = -1; long t = level == null ? 0 : level.getGameTime(); keyAt0 = t; keyAt1 = t; setChanged(); sync(); }

    private void refreshBoard(ServerLevel sl) {
        int[] b = new int[pads.size() * 3];
        for (int i = 0; i < pads.size(); i++) {
            BlockPos p = BlockPos.of(pads.get(i));
            int st = P_UNLOADED, tx = 0, tz = 0;
            if (sl.isLoaded(p) && sl.getBlockEntity(p) instanceof LaunchTerminalBlockEntity t) {
                tx = t.getTargetX(); tz = t.getTargetZ();
                st = t.shownBuild() == null ? P_NO_MISSILE : !t.isTargetSet() ? P_NO_TARGET : t.launchStatus() == LaunchTerminalBlockEntity.OK ? P_READY : P_NOT_READY;
            }
            b[i * 3] = st; b[i * 3 + 1] = tx; b[i * 3 + 2] = tz;
        }
        List<Long> c = new ArrayList<>();
        double r2 = (double) Balance.DH_LINK_RANGE * Balance.DH_LINK_RANGE;
        for (LaunchTerminalBlockEntity t : CcLaunch.terminalsIn(sl, getOwner(), 64)) {
            long k = t.getBlockPos().asLong();
            if (pads.contains(k) || t.getBlockPos().distSqr(worldPosition) > r2 || c.size() >= 16) continue;
            c.add(k);
        }
        long[] cand = c.stream().mapToLong(Long::longValue).toArray();
        if (!java.util.Arrays.equals(b, board) || !java.util.Arrays.equals(cand, candidates)) { board = b; candidates = cand; sync(); }
    }

    private void sound(SoundEvent e, float vol) {
        if (level == null) return;
        double[] w = worldPoint(0.0, 1.5, 0.0);
        level.playSound(null, w[0], w[1], w[2], e, SoundSource.BLOCKS, vol, 1.0F);
    }

    void doorClick(Player player) {
        if (!may(player) && !inside(player)) { player.displayClientMessage(Component.translatable("message.wmd.deadhand.door_locked").withStyle(ChatFormatting.RED), true); return; }
        if (level == null) return;
        doorOpen = !doorOpen;
        doorAt = level.getGameTime();
        double[] w = worldPoint(0.0, 26.0 / 16.0, 36.0 / 16.0);
        level.playSound(null, w[0], w[1], w[2], WmdSounds.DH_DOOR.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        setChanged();
        sync();
    }

    public boolean doorOpen() { return doorOpen; }

    private void sync() { if (level != null && !level.isClientSide) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); sendIntel(); } }

    public static void serverTickDh(Level level, BlockPos pos, BlockState bs, DeadHandBlockEntity be) {
        ProcessUnitBlockEntity.serverTick(level, pos, bs, be);
        if (!(level instanceof ServerLevel sl) || be.getOwner() == null) return;
        long now = sl.getGameTime();
        if (now % 20 == 0) {
            if (be.armed() && DeadHand.spent(sl, pos)) { be.fired(); be.push(sl); }
            long a = DeadHand.alarmAt(sl, pos);
            if (a != be.alarmAt) { be.alarmAt = a; be.sync(); }
            if (now % 100 == 0) { be.push(sl); be.alarmAt = DeadHand.alarmAt(sl, pos); }
            be.refreshBoard(sl);
            be.sendIntel();
        }
    }

    @Override protected boolean work() { return alarmAt >= 0; }

    @Override public void empDrain() { }

    public boolean key(int i) { return i == 0 ? key0 : key1; }
    public long alarmAt() { return alarmAt; }
    public int[] board() { return board; }
    public long[] candidates() { return candidates; }
    public List<Long> pads() { return pads; }

    public int secondsLeft() {
        if (alarmAt < 0 || level == null) return -1;
        return (int) Math.max(0, (alarmAt + Balance.DH_COUNTDOWN - level.getGameTime() + 19) / 20);
    }

    private static float ease(double tt, long at, boolean on, double ticks) {
        double u = at == Long.MIN_VALUE ? 1.0 : Mth.clamp((tt - at) / ticks, 0.0, 1.0);
        u = u * u * (3.0 - 2.0 * u);
        return (float) (on ? u : 1.0 - u);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {

        float k0 = ease(tt, keyAt0, key0, 6.0), k1 = ease(tt, keyAt1, key1, 6.0);
        model.getBone("key0").ifPresent(b -> b.setRotZ((float) -Math.toRadians(CcSpecB.KEY_TURN_DEG) * k0));
        model.getBone("key1").ifPresent(b -> b.setRotZ((float) Math.toRadians(CcSpecB.KEY_TURN_DEG) * k1));
        float cv = Math.min(k0, k1);
        model.getBone("cover").ifPresent(b -> b.setRotX((float) -Math.toRadians(CcSpecB.COVER_OPEN_DEG) * cv));

        float d = ease(tt, doorAt, doorOpen, Balance.DH_DOOR_TICKS);
        model.getBone("door").ifPresent(b -> b.setRotY((float) -Math.toRadians(100.0) * d));
        boolean alarm = alarmAt >= 0;
        boolean flash = alarm && ((long) tt / 8) % 2 == 0;
        model.getBone("dh_alarm").ifPresent(b -> b.setHidden(!flash));
        model.getBone("dh_clock").ifPresent(b -> b.setHidden(!(alarm ? ((long) tt / 10) % 2 == 0 : armed())));
    }

    public static void clientTickDh(Level level, BlockPos pos, BlockState bs, DeadHandBlockEntity be) { ProcessUnitBlockEntity.clientTick(level, pos, bs, be); }

    @Override protected SoundEvent runSound() { return WmdSounds.DH_TICK.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return 0.0F; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int inputTanks() { return 0; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public IEnergyStorage guiEnergy() { return null; }
    @Override public int guiFeUse() { return 0; }
    @Override public int guiStatus() { return alarmAt >= 0 ? 1 : armed() ? 0 : 4; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.dh_pads", pads.size(), Balance.DH_MAX_PADS),
                Component.translatable("gui.wmd.info.dh_rule", Balance.DH_COUNTDOWN / 20));
    }

    @Override protected String detail() { return "keys " + key0 + "/" + key1 + ", pads " + pads.size() + ", alarm " + alarmAt + ", door " + (doorOpen ? "open" : "shut"); }

    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return null; }

    private static final PortMap PORT_MAP = new PortMap(-2, 2, 5);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        putState(tag);
        putIntel(tag);
        tag.putBoolean("dhsave", true);
        ownerData.write(tag);
    }

    private void putState(CompoundTag tag) {
        tag.putBoolean("dhk0", key0);
        tag.putBoolean("dhk1", key1);
        tag.putBoolean("dhdoor", doorOpen);
        tag.putLong("dhdoorat", doorAt);
        tag.putLong("dhkat0", keyAt0);
        tag.putLong("dhkat1", keyAt1);
        tag.putLong("dhalarm", alarmAt);
    }

    private void putIntel(CompoundTag tag) {
        tag.putLongArray("dhpads", pads.stream().mapToLong(Long::longValue).toArray());
        tag.putIntArray("dhboard", board);
        tag.putLongArray("dhcand", candidates);
    }

    private void sendIntel() {
        if (!(level instanceof ServerLevel sl) || getOwner() == null) return;
        DeadHandIntelPayload p = null;
        for (net.minecraft.server.level.ServerPlayer pl : sl.players()) {
            if (pl.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) > 48.0 * 48.0 || !Access.trusts(this, pl.getUUID())) continue;
            if (p == null) p = new DeadHandIntelPayload(worldPosition, pads.stream().mapToLong(Long::longValue).toArray(), board, candidates);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(pl, p);
        }
    }

    static void onIntel(net.minecraft.world.entity.player.Player me, DeadHandIntelPayload p) {
        if (me == null || !(me.level().getBlockEntity(p.pos()) instanceof DeadHandBlockEntity be)) return;
        be.pads.clear();
        for (long k : p.pads()) if (be.pads.size() < Balance.DH_MAX_PADS) be.pads.add(k);
        be.board = p.board();
        be.candidates = p.candidates();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("dhk0")) {
            key0 = tag.getBoolean("dhk0");
            key1 = tag.getBoolean("dhk1");
            doorOpen = tag.getBoolean("dhdoor");
            doorAt = tag.getLong("dhdoorat");
            keyAt0 = tag.getLong("dhkat0");
            keyAt1 = tag.getLong("dhkat1");
            alarmAt = tag.getLong("dhalarm");
        }
        if (tag.contains("dhpads")) {
            pads.clear();
            for (long p : tag.getLongArray("dhpads")) if (pads.size() < Balance.DH_MAX_PADS) pads.add(p);
            board = tag.getIntArray("dhboard");
            candidates = tag.getLongArray("dhcand");
        }
        if (tag.contains("dhsave")) ownerData.read(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        putState(tag);
        return tag;
    }
}
