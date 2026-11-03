package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public class MissileTeleporterBlockEntity extends BlockEntity implements Owned, MachinePorts, EmpVulnerable {

    public static final int EMPTY = 0, LOADING = 1, READY = 2, CHARGING = 3, FADING = 4;

    public static final int OK = 0, NOT_LOADED = 1, GONE = 2, NOT_FORMED = 3, OCCUPIED = 4, TOO_SMALL = 5, NOT_YOURS = 6, NO_MISSILE = 8, COOLDOWN = 9,
            NO_POWER = 10, JAMMED = 11;

    private static class Buffer extends EnergyStorage {
        Buffer() { super(Balance.TP_BUFFER, Balance.TP_RECEIVE, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private final OwnerData ownerData = new OwnerData();
    private final Buffer store = new Buffer();
    private final ItemStackHandler chamber = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return s.getItem() instanceof LaunchMissileItem m && m.getTier() <= 3; }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { contentsChanged(); }
    };

    private final IItemHandler hatch = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return chamber.getStackInSlot(0); }
        @Override public ItemStack insertItem(int slot, ItemStack s, boolean sim) { return phase == EMPTY ? chamber.insertItem(0, s, sim) : s; }
        @Override public ItemStack extractItem(int slot, int amount, boolean sim) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack s) { return phase == EMPTY && chamber.isItemValid(0, s); }
    };
    private final List<BlockPos> links = new ArrayList<>();
    private int phase = EMPTY, target = -1;
    private long phaseStart, cooldownUntil;

    public MissileTeleporterBlockEntity(BlockPos pos, BlockState state) { super(Wmd.MISSILE_TELEPORTER_BE.get(), pos, state); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }
    @Override public void empDrain() { store.set(0); setChanged(); }

    public IItemHandler chamber() { return chamber; }
    public ItemStack missile() { return chamber.getStackInSlot(0); }
    public int phase() { return phase; }
    public long phaseStart() { return phaseStart; }
    public int energy() { return store.getEnergyStored(); }
    public List<BlockPos> links() { return links; }
    public int target() { return target; }

    public int cooldownSeconds() { return level == null ? 0 : (int) Math.max(0, (cooldownUntil - level.getGameTime() + 19) / 20); }

    public float progress(float pt) {
        int len = switch (phase) { case LOADING -> Balance.TP_LOAD_TICKS; case CHARGING -> Balance.TP_CHARGE_TICKS; case FADING -> Balance.TP_FADE_TICKS; default -> 0; };
        if (len <= 0 || level == null) return 0.0F;
        return Mth.clamp((level.getGameTime() - phaseStart + pt) / len, 0.0F, 1.0F);
    }

    private void contentsChanged() {
        setChanged();
        if (level == null || level.isClientSide) return;
        if (phase == EMPTY && !missile().isEmpty()) enter(LOADING);
        else if (missile().isEmpty() && phase != EMPTY) enter(EMPTY);
        else sync();
    }

    private void enter(int p) {
        phase = p;
        phaseStart = level == null ? 0 : level.getGameTime();
        setChanged();
        sync();
    }

    private void sync() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    int addLink(BlockPos pad) {
        if (links.contains(pad)) return 1;
        if (links.size() >= Balance.TP_MAX_LINKS) return 2;
        links.add(pad.immutable());
        setChanged();
        sync();
        return 0;
    }

    void removeLink(int i) {
        if (i < 0 || i >= links.size() || phase == CHARGING || phase == FADING) return;
        links.remove(i);
        if (target == i) target = -1; else if (target > i) target--;
        setChanged();
        sync();
    }

    int linkState(int i) {
        if (!(level instanceof ServerLevel sl) || i < 0 || i >= links.size()) return GONE;
        BlockPos p = links.get(i);
        if (!sl.isLoaded(p)) return NOT_LOADED;
        if (!(sl.getBlockEntity(p) instanceof LaunchTerminalBlockEntity t)) return GONE;
        Owned o = Access.ownerAt(sl, p);
        if (o != null && getOwner() != null && !Access.trusts(o, getOwner())) return NOT_YOURS;
        if (!t.isFormed()) return NOT_FORMED;
        if (!t.automationPort().getStackInSlot(LaunchTerminalBlockEntity.SLOT_MISSILE).isEmpty()) return OCCUPIED;
        if (missile().getItem() instanceof LaunchMissileItem m && m.getTier() > t.getTier()) return TOO_SMALL;
        return OK;
    }

    int jump(ServerPlayer player, int link) {
        if (phase != READY) return NO_MISSILE;
        if (level.getGameTime() < cooldownUntil) return COOLDOWN;
        if (EmpField.jammed((ServerLevel) level, worldPosition)) return JAMMED;
        int st = linkState(link);
        if (st != OK) return st;
        if (store.getEnergyStored() < Balance.TP_FE_PER_JUMP) return NO_POWER;
        store.use(Balance.TP_FE_PER_JUMP);
        target = link;
        level.playSound(null, worldPosition, WmdSounds.TP_CHARGE_LOOP.get(), SoundSource.BLOCKS, 3.0F, 0.8F);
        enter(CHARGING);
        return OK;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MissileTeleporterBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        Logistics.noteTeleporter(be);
        long now = level.getGameTime();
        if ((be.phase == CHARGING || be.phase == FADING) && EmpField.jammed(sl, pos)) {
            be.store.set(0);
            be.target = -1;
            level.playSound(null, pos, WmdSounds.TP_ABORT.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
            be.enter(READY);
            return;
        }
        switch (be.phase) {
            case LOADING -> { if (now - be.phaseStart >= Balance.TP_LOAD_TICKS) be.enter(READY); }
            case CHARGING -> {
                long t = now - be.phaseStart;
                if (t % 100 == 0) level.playSound(null, pos.above(4 + (int) (t * 18 / Balance.TP_CHARGE_TICKS)), WmdSounds.TP_CHARGE_LOOP.get(), SoundSource.BLOCKS, 3.0F, 0.8F + 0.4F * t / (Balance.TP_CHARGE_TICKS / 6));
                if (t >= Balance.TP_CHARGE_TICKS) {
                    level.playSound(null, pos, WmdSounds.TP_FIRE.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
                    be.enter(FADING);
                }
            }
            case FADING -> { if (now - be.phaseStart >= Balance.TP_FADE_TICKS) be.arrive(sl); }
            default -> {}
        }
        if (now % 100 == 0) be.setChanged();
    }

    private void arrive(ServerLevel sl) {
        int st = linkState(target);
        BlockPos pad = target >= 0 && target < links.size() ? links.get(target) : null;
        if (st != OK || pad == null || !(sl.getBlockEntity(pad) instanceof LaunchTerminalBlockEntity t)) {

            sl.playSound(null, worldPosition, WmdSounds.TP_ABORT.get(), SoundSource.BLOCKS, 3.0F, 0.9F);
            tellOwner(sl, Component.translatable("message.wmd.tp.failed", Component.translatable("gui.wmd.tp.st." + st)));
            target = -1;
            enter(READY);
            return;
        }
        ItemStack m = missile().copy();
        ItemStack rest = ItemPorts.asWmd(() -> ItemHandlerHelper.insertItem(t.automationPort(), m, false));
        if (!rest.isEmpty()) { target = -1; enter(READY); return; }
        chamber.setStackInSlot(0, ItemStack.EMPTY);
        cooldownUntil = sl.getGameTime() + Balance.TP_COOLDOWN_TICKS;
        target = -1;
        flash(sl, Vec3.atCenterOf(worldPosition).add(0.0, 10.0, 0.0), true);
        flash(sl, Vec3.atCenterOf(pad).add(0.0, 3.0, 0.0), false);
        tellOwner(sl, Component.translatable("message.wmd.tp.arrived", m.getHoverName(), pad.getX(), pad.getY(), pad.getZ()));
        setChanged();
        sync();
    }

    private void flash(ServerLevel sl, Vec3 at, boolean sender) {
        sl.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 3, 0.5, 2.0, 0.5, 0.0);
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 120, 1.5, 4.0, 1.5, 0.25);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 60, 2.0, 4.0, 2.0, 0.3);
        sl.playSound(null, BlockPos.containing(at), sender ? WmdSounds.TP_FIRE.get() : WmdSounds.TP_ARRIVE.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
        MinecraftServer server = sl.getServer();
        for (UUID o : TrackNet.radarOwnersNear(sl, at.x, at.z, Balance.RADAR_DETECT_RANGE, sl.getGameTime())) {
            if (o.equals(getOwner())) continue;
            String x = Integer.toString((int) Math.round(at.x)), z = Integer.toString((int) Math.round(at.z));
            CcBattleLog.add(server, o, CcBattleLog.ALARM, "log.wmd.teleport_flash", x, z);
            ServerPlayer p = server.getPlayerList().getPlayer(o);
            if (p != null) p.displayClientMessage(Component.translatable("message.wmd.tp.radar_flash", x, z), true);
        }
    }

    private void tellOwner(ServerLevel sl, Component c) {
        ServerPlayer p = getOwner() == null ? null : sl.getServer().getPlayerList().getPlayer(getOwner());
        if (p != null) p.displayClientMessage(c, true);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, MissileTeleporterBlockEntity be) {
        if (be.phase != CHARGING && be.phase != FADING) return;
        Direction f = state.getValue(MachineBlock.FACING);
        Vec3 axis = Vec3.atBottomCenterOf(Machines.toWorld(pos, f, Machines.mount(state), 0, 0, 3));
        var r = level.random;
        if (be.phase == CHARGING) {
            float p = be.progress(0.0F);
            int lit = Math.max(1, (int) Math.ceil(p * 6));
            for (int k = 0; k < 2; k++) {
                double y = 3.75 + r.nextInt(lit) * 3.125 + r.nextDouble() * 3.0, a = r.nextDouble() * Math.PI * 2;
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, axis.x + Math.cos(a) * 1.8, pos.getY() + y, axis.z + Math.sin(a) * 1.8, 0.0, 0.05, 0.0);
            }
        } else if (be.missile().getItem() instanceof LaunchMissileItem m) {
            double len = 10.0 * BayCalibre.len(m.getTier());
            double cut = 1.5 + len * (1.0 - be.progress(0.0F));
            for (int k = 0; k < 6; k++) {
                double a = r.nextDouble() * Math.PI * 2, rr = 0.9 + r.nextDouble() * 0.6;
                level.addParticle(ParticleTypes.END_ROD, axis.x + Math.cos(a) * rr, pos.getY() + cut, axis.z + Math.sin(a) * rr, 0.0, 0.08 + r.nextDouble() * 0.08, 0.0);
            }
        }
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        Direction f = getBlockState().getValue(MachineBlock.FACING);
        return Machines.atPart(worldPosition, f, Machines.mount(getBlockState()), part, side, 3, 1, 3, Direction.EAST) ? store : null;
    }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        Direction f = getBlockState().getValue(MachineBlock.FACING);
        return Machines.atPart(worldPosition, f, Machines.mount(getBlockState()), part, side, 0, 0, 0, Direction.NORTH) ? hatch : null;
    }

    IItemHandler hatch() { return hatch; }

    void dropContents() {
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, chamber.getStackInSlot(0));
        chamber.setStackInSlot(0, ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        tag.putInt("energy", store.getEnergyStored());
        tag.putLong("cooldown", cooldownUntil);
        writeSync(tag, registries);
    }

    private void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("chamber", chamber.serializeNBT(registries));
        tag.putInt("phase", phase);
        tag.putLong("phaseStart", phaseStart);
        tag.putInt("target", target);
        ListTag l = new ListTag();
        for (BlockPos p : links) l.add(LongTag.valueOf(p.asLong()));
        tag.put("links", l);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner") || tag.contains("ownerName")) ownerData.read(tag);
        if (tag.contains("energy")) store.set(tag.getInt("energy"));
        if (tag.contains("cooldown")) cooldownUntil = tag.getLong("cooldown");
        if (tag.contains("chamber")) {
            int keep = phase;
            phase = -1;
            chamber.deserializeNBT(registries, tag.getCompound("chamber"));
            phase = keep;
        }
        phase = tag.getInt("phase");
        phaseStart = tag.getLong("phaseStart");
        target = tag.getInt("target");
        links.clear();
        ListTag l = tag.getList("links", Tag.TAG_LONG);
        for (int i = 0; i < l.size(); i++) links.add(BlockPos.of(((LongTag) l.get(i)).getAsLong()));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeSync(tag, registries);
        return tag;
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
