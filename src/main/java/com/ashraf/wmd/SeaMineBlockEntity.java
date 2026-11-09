package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SeaMineBlockEntity extends BlockEntity implements Owned {

    public static final int K_INFLUENCE = 0, K_BOTTOM = 1, K_CAPTOR = 2;
    public static final int A_DEPTH = 0, A_OPEN = 9;

    static final float INF_CENTRE_PX = 18.0F, CAP_CENTRE_PX = 7.0F, FAIRLEAD_PX = 15.8F, ROCKET_TOP_PX = 31.2F;

    private final OwnerData ownerData = new OwnerData();
    private int depth = -1;
    private long armedFrom = -1, lastFire = -1000L;
    private boolean loaded, fired;

    private double eyeY = Double.NaN;
    private long nextCase;

    private long laidAt = -1;

    public SeaMineBlockEntity(BlockPos pos, BlockState state) { super(Wmd.SEA_MINE_BE.get(), pos, state); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) { ownerData.claim(player); setChanged(); sync(); }

    private void sync() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public int kind() { return getBlockState().getBlock() instanceof SeaMineBlock m ? m.kind : K_INFLUENCE; }
    public boolean moored() { return kind() != K_BOTTOM; }
    public int depth() { return depth < 0 ? defaultDepth(kind()) : depth; }
    static int defaultDepth(int kind) { return (int) (kind == K_CAPTOR ? Balance.SEAMINE_DEPTH_CAPTOR : Balance.SEAMINE_DEPTH_DEFAULT); }

    public boolean ready() { return kind() == K_INFLUENCE || loaded; }
    public boolean loaded() { return loaded; }
    public boolean armed() { return level != null && armedFrom >= 0 && level.getGameTime() >= armedFrom + Balance.SEAMINE_ARM_TICKS; }

    public long armAge(Level l) { return armedFrom < 0 ? -1 : l.getGameTime() - armedFrom; }
    public long lastFire() { return lastFire; }
    public double eyeY() { return eyeY; }

    public Item warheadItem() { return kind() == K_BOTTOM ? Wmd.TORPEDO_T1.get() : kind() == K_CAPTOR ? Wmd.TORPEDO_T2.get() : null; }

    public Vec3 caseAt() {
        double x = worldPosition.getX() + 0.5, z = worldPosition.getZ() + 0.5;
        if (kind() == K_BOTTOM) return new Vec3(x, worldPosition.getY() + 1.0, z);
        double e = Double.isNaN(eyeY) ? worldPosition.getY() + FAIRLEAD_PX / 16.0 + Balance.SEAMINE_CABLE_MIN : eyeY;
        return new Vec3(x, e + (kind() == K_CAPTOR ? CAP_CENTRE_PX : INF_CENTRE_PX) / 16.0, z);
    }

    static double eyeFor(Level level, BlockPos anchor, int kind, int depth) {
        double top = anchor.getY() + FAIRLEAD_PX / 16.0, centre = (kind == K_CAPTOR ? CAP_CENTRE_PX : INF_CENTRE_PX) / 16.0;
        int w = NavalLayer.waterTop(level, anchor.getX(), anchor.getZ());
        double want = w == NavalLayer.NO_WATER ? top + Balance.SEAMINE_CABLE_MIN : w + 1.0 - depth - centre;
        return Mth.clamp(want, top + Balance.SEAMINE_CABLE_MIN, top + Balance.SEAMINE_CABLE_MAX);
    }

    public void act(Player by, int action, int value) {
        if (action == A_DEPTH && moored()) {
            depth = (int) Mth.clamp(value, Balance.SEAMINE_DEPTH_MIN, Balance.SEAMINE_DEPTH_MAX);
            nextCase = 0;
        }
        setChanged();
        sync();
    }

    public boolean tryLoad(Player p) {
        ItemStack h = p.getMainHandItem();
        Item want = warheadItem();
        if (want == null || loaded || h.isEmpty() || !h.is(want)) return false;
        if (!p.getAbilities().instabuild) h.shrink(1);
        loaded = true;
        fired = false;
        armedFrom = level == null ? -1 : level.getGameTime();
        if (level != null) level.playSound(null, worldPosition, WmdSounds.TP_RELOAD.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.1F);
        setChanged();
        sync();
        return true;
    }

    void depthFromItem(int d) {
        if (d < 0 || !moored()) return;
        depth = (int) Mth.clamp(d, Balance.SEAMINE_DEPTH_MIN, Balance.SEAMINE_DEPTH_MAX);
        nextCase = 0;
        setChanged();
        sync();
    }

    void laid(boolean withTorpedo) {
        if (level == null) return;
        if (withTorpedo && warheadItem() != null) loaded = true;
        fired = false;
        armedFrom = laidAt = level.getGameTime();
        setChanged();
        sync();
    }

    public long laidAt() { return laidAt; }

    ItemStack takeWarhead() {
        if (!loaded || warheadItem() == null) return ItemStack.EMPTY;
        loaded = false;
        return new ItemStack(warheadItem());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SeaMineBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (be.armedFrom < 0) { be.armedFrom = now; be.setChanged(); be.sync(); }
        if (be.moored() && now >= be.nextCase) {
            be.nextCase = now + 40;
            double e = eyeFor(sl, pos, be.kind(), be.depth());
            if (Double.isNaN(be.eyeY) || Math.abs(e - be.eyeY) > 0.05) { be.eyeY = e; be.setChanged(); be.sync(); }
        }
        Vec3 c = be.caseAt();
        SeaMines.put(sl, pos, be.kind(), c, be.getOwner());
        if (be.ready() && now == be.armedFrom + Balance.SEAMINE_ARM_TICKS) sl.playSound(null, c.x, c.y, c.z, WmdSounds.MINE_ARM.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 0.7F);
        if (!be.ready() || !be.armed() || Math.floorMod(now + pos.asLong(), (long) Balance.MINE_SCAN_TICKS) != 0 || !SableBridge.active()) return;
        switch (be.kind()) {
            case K_INFLUENCE -> {
                if (!SeaMines.strangersNear(sl, c, Balance.MINE_INF_TRIGGER, be.getOwner()).isEmpty()) be.detonate(sl, "influence");
            }
            case K_BOTTOM -> {
                for (SableBridge.ShipInfo s : SeaMines.strangersNear(sl, c, Balance.MINE_BOT_LISTEN + Balance.MINE_BOT_SIDE, be.getOwner())) {
                    AABB b = s.worldBounds();
                    double side = Math.hypot(Math.max(0.0, Math.max(b.minX - c.x, c.x - b.maxX)), Math.max(0.0, Math.max(b.minZ - c.z, c.z - b.maxZ)));
                    if (side <= Balance.MINE_BOT_SIDE && b.minY > c.y && b.minY - c.y <= Balance.MINE_BOT_LISTEN) { be.rise(sl, s); break; }
                }
            }
            case K_CAPTOR -> {
                for (SableBridge.ShipInfo s : SeaMines.strangersNear(sl, c, Balance.MINE_CAP_RANGE, be.getOwner()))
                    if (NavalLayer.submerged(sl, s)) { be.release(sl, s); break; }
            }
            default -> {}
        }
    }

    private void detonate(ServerLevel sl, String why) {
        if (fired) return;
        fired = true;
        Vec3 c = caseAt();
        SeaMines.remove(sl, worldPosition);
        sl.removeBlock(worldPosition, false);
        blast(sl, c, Balance.MINE_INF_POWER, Balance.MINE_INF_REACH, UnderwaterBlast.MINE, getOwner(), why);
    }

    private void rise(ServerLevel sl, SableBridge.ShipInfo s) {
        loaded = false;
        lastFire = sl.getGameTime();
        Vec3 at = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + ROCKET_TOP_PX / 16.0 + 0.2, worldPosition.getZ() + 0.5);
        RisingMineEntity.launch(sl, at, s.id(), getOwner());
        sl.playSound(null, at.x, at.y, at.z, WmdSounds.LC_HATCH.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.2F);
        tell(sl, Component.translatable("message.wmd.seamine.rise", Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z)));
        Wmd.LOGGER.info("Bottom mine at {} rises at ship {}", worldPosition.toShortString(), s.id());
        setChanged();
        sync();
    }

    private void release(ServerLevel sl, SableBridge.ShipInfo s) {
        loaded = false;
        lastFire = sl.getGameTime();
        Vec3 c = caseAt(), t = s.worldBounds().getCenter();
        Vec3 dir = t.subtract(c);
        dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : dir.normalize();
        Vec3 at = c.add(dir.scale(1.6));
        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, c.x, c.y, c.z, 30, 0.3, 0.3, 0.3, 0.2);
        TorpedoEntity.launch(sl, TorpedoKind.T2, at, dir, 0.3, NavalLayer.depthBelowSurface(sl, t), getOwner(), null);
        tell(sl, Component.translatable("message.wmd.seamine.captor", Mth.floor(c.x), Mth.floor(c.y), Mth.floor(c.z)));
        Wmd.LOGGER.info("Captor mine at {} released its T2 at ship {}", worldPosition.toShortString(), s.id());
        setChanged();
        sync();
    }

    void sympathetic(ServerLevel sl) {
        if (fired || isRemoved()) return;
        if (kind() == K_INFLUENCE) { detonate(sl, "sympathetic"); return; }
        if (!loaded) return;
        loaded = false;
        fired = true;
        Vec3 c = caseAt();
        SeaMines.remove(sl, worldPosition);
        sl.removeBlock(worldPosition, false);
        blast(sl, c, Balance.TORP_POWER[kind() == K_BOTTOM ? 0 : 1], Balance.TORP_REACH, UnderwaterBlast.TORPEDO, getOwner(), "sympathetic");
    }

    static void blast(ServerLevel sl, Vec3 at, float power, double reach, int type, UUID owner, String why) {
        int hull = ShipStrike.blast(sl, at, reach, power, owner);
        UnderwaterBlast.go(sl, at, type, power, owner, null);
        if (owner != null && sl.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer p)
            p.displayClientMessage(Component.translatable("message.wmd.seamine.blast", Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z),
                    (int) Math.round(NavalLayer.depthBelowSurface(sl, at)), hull), false);
        Wmd.LOGGER.info("Sea mine blast ({}) at {} {} {} power {} reach {}, {} hull blast(s)", why, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z), power, reach, hull);
    }

    private void tell(ServerLevel sl, Component c) {
        if (getOwner() != null && sl.getServer().getPlayerList().getPlayer(getOwner()) instanceof ServerPlayer p) p.displayClientMessage(c, false);
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) SeaMines.remove(level, worldPosition);
        super.setRemoved();
    }

    public Component status() {
        if (!ready()) return Component.translatable("message.wmd.seamine.empty", Component.translatable(warheadItem() == null ? "item.wmd.torpedo_t1" : warheadItem().getDescriptionId()));
        return Component.translatable(armed() ? "message.wmd.seamine.armed" : "message.wmd.seamine.arming");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag);
        tag.putBoolean("smFired", fired);
    }

    private void put(CompoundTag tag) {
        ownerData.write(tag);
        tag.putInt("smDepth", depth);
        tag.putLong("smArmed", armedFrom);
        tag.putLong("smFire", lastFire);
        tag.putBoolean("smLoaded", loaded);
        tag.putLong("smLaid", laidAt);
        if (!Double.isNaN(eyeY)) tag.putDouble("smEye", eyeY);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("owner") || tag.contains("ownerName")) ownerData.read(tag);
        if (tag.contains("smDepth")) depth = tag.getInt("smDepth");
        if (tag.contains("smArmed")) armedFrom = tag.getLong("smArmed");
        if (tag.contains("smFire")) lastFire = tag.getLong("smFire");
        loaded = tag.getBoolean("smLoaded");
        laidAt = tag.contains("smLaid") ? tag.getLong("smLaid") : -1;
        fired = tag.getBoolean("smFired");
        eyeY = tag.contains("smEye") ? tag.getDouble("smEye") : Double.NaN;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        put(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
