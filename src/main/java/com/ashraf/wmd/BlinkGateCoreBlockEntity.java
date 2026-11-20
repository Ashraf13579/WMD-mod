package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;

public class BlinkGateCoreBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, ModeMachine, GuiItemSlots, RadarLinked {

    public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 1;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 - Z0 + 1).fromZ(Z0).power(0, 0, Z1, Direction.SOUTH, false);
    public static final int S_OFF = 0, S_DEPLOY = 1, S_POWER = 2, S_PORTAL = 3, S_OPEN = 4, S_DOWN = 5, S_COOL = 6;

    public static final int ST_NO_OWNER = 0, ST_UNBUILT = 1, ST_BUILDING = 2, ST_NO_PARTNER = 3, ST_READY = 4, ST_ARMING = 5, ST_OPEN = 6, ST_COOLING = 7,
            ST_NO_POWER = 8, ST_ON_SHIP = 9, ST_PARTNER_DOWN = 10, ST_REFUSED = 11;

    public static final int WHY_NONE = 0, WHY_TRUST = 1, WHY_FIT = 2, WHY_PARTNER = 3, WHY_EXIT = 4, WHY_POWER = 5;

    public static final int AUTO_BIT = 4;
    public static final int[] SIZES = { 13, 19, 25 };

    static final int FX_JUMP = 0, FX_ARRIVE = 1, FX_REFUSE = 2;

    static final TicketType<ChunkPos> TICKET = TicketType.create("wmd_blink_gate", java.util.Comparator.comparingLong(ChunkPos::toLong), 60);

    static final float X_SIGN = -1.0F;

    private final OwnerData ownerData = new OwnerData();
    private int mode = AUTO_BIT;
    private ItemStack corners = ItemStack.EMPTY, segments = ItemStack.EMPTY;
    private final FluidTank cornerGauge = new FluidTank(4), segmentGauge = new FluidTank(64);

    private BlockPos partner;
    private int state = S_OFF, status = ST_NO_OWNER, why = WHY_NONE, builtSize = 0, jumps;
    private long stateAt, coolUntil, lastUse, flashAt = Long.MIN_VALUE / 2, refuseAt = Long.MIN_VALUE / 2;
    private boolean formed, payer, redstone;
    private int rfChannel, rfRate, rfAcc, rfTicks;
    private boolean rfJam;
    private long rfJamCheck = Long.MIN_VALUE / 2;
    private float pairYaw;

    private final List<Slot> queue = new ArrayList<>();
    private long nextBuild;
    private final Map<UUID, Double> side = new HashMap<>();
    private final Map<UUID, Long> immune = new HashMap<>();
    private int checkTick;

    private double lastTT = Double.NaN;
    private int lastState = -1, clientTicks;

    public BlinkGateCoreBlockEntity(BlockPos pos, BlockState state) {
        super(BlinkGateBlocks.BLINK_CORE_BE.get(), pos, state, Balance.BG_IDLE_FE, Balance.BG_BUFFER_FE, Balance.BG_RECEIVE_FE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        sync();
    }

    @Override public int mode() { return mode; }
    @Override public int modeCount() { return 8; }
    @Override public void setMode(int m) { mode = clampMode(m); sync(); }
    @Override public void setModeClient(int m) { mode = clampMode(m); }

    private int clampMode(int m) {
        int size = Mth.clamp(m & 3, 0, SIZES.length - 1);
        if (formed || !queue.isEmpty()) size = sizeIndex();
        return size | (m & AUTO_BIT);
    }

    public int sizeIndex() { return Mth.clamp(mode & 3, 0, SIZES.length - 1); }
    public int size() { return SIZES[sizeIndex()]; }
    public boolean auto() { return (mode & AUTO_BIT) != 0; }

    public static double half(int n) { return (n - 4) / 2.0; }
    public double half() { return half(formed && builtSize > 0 ? builtSize : size()); }

    public static int[] needs(int n) { int s = (n - 10) / 3; return new int[] { 4, 4 * s - 1 }; }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public int gateState() { return state; }
    public int gateStatus() { return status; }
    public int lastWhy() { return why; }
    public boolean formed() { return formed; }
    public BlockPos partner() { return partner; }
    public int jumpCount() { return jumps; }
    public long stateAt() { return stateAt; }
    public long flashAt() { return flashAt; }
    public long refuseAt() { return refuseAt; }
    public int coolSecondsLeft() { return level == null ? 0 : (int) Math.max(0L, (coolUntil - level.getGameTime()) / 20L); }
    public int bufferPermille() { return (int) Math.min(1000L, energy().getEnergyStored() * 1000L / Balance.BG_BUFFER_FE); }
    public int cornerCount() { return corners.getCount(); }
    public int segmentCount() { return segments.getCount(); }

    public static long jumpFe(int blocks) { return (long) Balance.BG_JUMP_FE + (long) Balance.BG_BLOCK_FE * Math.max(0, blocks); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, Z1, Direction.SOUTH) ? energy() : null; }

    @Override public int getFrequency() { return rfChannel; }
    @Override public void setFrequency(int f) { rfChannel = Mth.clamp(f, 0, 9999); sync(); }
    public int rfChannel() { return rfChannel; }

    public int rfRate() { return rfRate; }

    private void rfCharge(ServerLevel sl, long now) {
        int got = 0;
        if (rfChannel > 0) {
            if (now - rfJamCheck >= Balance.RF_JAM_TICKS) { rfJamCheck = now; rfJam = PortableJammerBlockEntity.jams(sl, worldPosition, getOwner()); }
            int want = Math.min(Balance.RF_OUT_FE, energy().getMaxEnergyStored() - energy().getEnergyStored());
            if (!rfJam && want > 0) {
                double[] w = worldPoint(0.0, 1.5, 0.5);
                got = RfLinkBlockEntity.pullBuiltIn(sl, this, new Vec3(w[0], w[1], w[2]), rfChannel, want, now);
                chargeDirect(got);
            }
        }
        rfAcc += got;
        if (++rfTicks >= 20) { rfTicks = 0; int r = rfAcc / 20; rfAcc = 0; if (r != rfRate) { rfRate = r; sync(); } }
    }

    public Vec3 centre() {
        double[] w = worldPoint(0.0, 2.0 + half(), 0.5);
        return new Vec3(w[0], w[1], w[2]);
    }

    public Vec3 normal() {
        Direction d = Machines.dirToWorld(facing(), Direction.NORTH);
        return new Vec3(d.getStepX(), 0.0, d.getStepZ());
    }

    public Vec3 right() {
        Direction d = Machines.dirToWorld(facing(), Direction.EAST);
        return new Vec3(d.getStepX(), 0.0, d.getStepZ());
    }

    public static boolean inOpening(double x, double y, double a) {
        double ax = Math.abs(x), ay = Math.abs(y);
        return ax <= a && ay <= a && ax + ay <= 2.0 * a - 2.0;
    }

    record Slot(boolean corner, int x, int y, int k, float order) {}

    static int[] turn(int k, int x, int y) {
        return switch (k & 3) { case 1 -> new int[] { -y, x }; case 2 -> new int[] { -x, -y }; case 3 -> new int[] { y, -x }; default -> new int[] { x, y }; };
    }

    static List<Slot> slots(int n) {
        List<Slot> out = new ArrayList<>();
        int a = (n - 4) * 8, cy = 32 + a, s = (n - 10) / 3;
        for (int i = 0; i < s; i++) {
            int c = (i * 2 - (s - 1)) * 24;
            add(out, false, c, cy + a, 0, n);
            add(out, false, -a, cy + c, 1, n);
            add(out, false, a, cy + c, 3, n);
            if (c != 0) add(out, false, c, cy - a, 2, n);
        }
        int[][] cs = { { a, cy + a, 0 }, { -a, cy + a, 1 }, { -a, cy - a, 2 }, { a, cy - a, 3 } };
        for (int[] q : cs) add(out, true, q[0], q[1], q[2], n);
        out.sort(java.util.Comparator.comparingDouble(Slot::order));
        return out;
    }

    private static void add(List<Slot> out, boolean corner, int ox, int oy, int k, int n) {
        int[] d = corner ? turn(k, 8, -40) : turn(k, 0, 8);
        int px = ox + d[0], py = oy + d[1];
        int x = Math.round(px / 16.0F), y = Math.round((py - 8) / 16.0F);
        double cy = 32.0 + (n - 4) * 8.0;
        float order = (float) (Math.abs(Math.atan2(px, -(py - cy))) / Math.PI);
        out.add(new Slot(corner, x, y, k, order));
    }

    static List<BlockPos> footprint(boolean corner) {
        List<BlockPos> f = new ArrayList<>();
        for (int z = 0; z <= 1; z++) {
            if (!corner) { for (int x = -1; x <= 1; x++) for (int y = 0; y <= 1; y++) f.add(new BlockPos(x, y, z)); continue; }
            for (int x = -3; x <= 1; x++) for (int y = 3; y <= 4; y++) f.add(new BlockPos(x, y, z));
            for (int x = 0; x <= 1; x++) for (int y = 0; y <= 2; y++) f.add(new BlockPos(x, y, z));
            f.add(new BlockPos(-1, 2, z)); f.add(new BlockPos(-2, 2, z)); f.add(new BlockPos(-1, 1, z));
        }
        return f;
    }

    private BlockPos cw(int x, int y, int z) { return Machines.toWorld(worldPosition, facing(), Direction.UP, x, y, z); }

    private BlockPos cdir(int x, int y, int z) { return cw(x, y, z).subtract(cw(0, 0, 0)); }

    record Place(Direction facing, Direction mount, BlockPos origin, List<BlockPos> cells) {}

    Place place(Slot s) {
        int[] ex = turn(s.k(), 1, 0), ey = turn(s.k(), 0, 1);
        BlockPos Ex = cdir(ex[0], ex[1], 0), Ey = cdir(ey[0], ey[1], 0), Ez = cdir(0, 0, 1);
        for (Direction m : Direction.values()) for (Direction f : Direction.Plane.HORIZONTAL) {
            BlockPos o = Machines.toWorld(BlockPos.ZERO, f, m, 0, 0, 0);
            BlockPos Lx = Machines.toWorld(BlockPos.ZERO, f, m, 1, 0, 0).subtract(o), Ly = Machines.toWorld(BlockPos.ZERO, f, m, 0, 1, 0).subtract(o),
                    Lz = Machines.toWorld(BlockPos.ZERO, f, m, 0, 0, 1).subtract(o);
            if (!Lx.equals(Ex) || !Ly.equals(Ey)) continue;
            boolean flip = Lz.equals(new BlockPos(-Ez.getX(), -Ez.getY(), -Ez.getZ()));
            if (!flip && !Lz.equals(Ez)) continue;
            BlockPos origin = cw(s.x(), s.y(), flip ? 1 : 0);
            List<BlockPos> cells = new ArrayList<>();
            for (BlockPos l : footprint(s.corner())) cells.add(Machines.toWorld(origin, f, m, l.getX(), l.getY(), l.getZ()));
            return new Place(f, m, origin, cells);
        }
        return null;
    }

    private boolean pieceOk(Slot s) {
        Place p = place(s);
        if (p == null || level == null) return false;
        BlockState st = level.getBlockState(p.origin());
        if (!st.is(s.corner() ? BlinkGateBlocks.BLINK_CORNER.get() : BlinkGateBlocks.BLINK_SEGMENT.get())) return false;
        if (st.getValue(MachineBlock.FACING) != p.facing() || Machines.mount(st) != p.mount()) return false;
        return level.getBlockEntity(p.origin()) instanceof BlinkGatePieceBlockEntity pe && worldPosition.equals(pe.core());
    }

    private boolean checkFormed() {
        if (builtSize <= 0) return false;
        for (Slot s : slots(builtSize)) if (!pieceOk(s)) return false;
        return true;
    }

    void build(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl) || formed || !queue.isEmpty()) return;
        if (SableBridge.isOnShip(sl, worldPosition)) { player.displayClientMessage(Component.translatable("message.wmd.blink.on_ship"), true); return; }
        int n = size();
        List<Slot> todo = new ArrayList<>();
        int needC = 0, needS = 0;
        List<BlockPos> cells = new ArrayList<>();
        for (Slot s : slots(n)) {
            if (builtSize == n && pieceOk(s)) continue;
            Place p = place(s);
            if (p == null) return;
            for (BlockPos c : p.cells()) {
                if (!sl.isInWorldBounds(c) || !sl.getBlockState(c).canBeReplaced()) {
                    player.displayClientMessage(Component.translatable("message.wmd.blink.blocked", c.getX(), c.getY(), c.getZ()), true);
                    return;
                }
                cells.add(c);
            }
            todo.add(s);
            if (s.corner()) needC++; else needS++;
        }
        if (todo.isEmpty()) { formed = checkFormed(); sync(); return; }
        if (corners.getCount() < needC || segments.getCount() < needS) {
            player.displayClientMessage(Component.translatable("message.wmd.blink.pieces", needC, needS), true);
            return;
        }
        Entity in = PlaceGuard.blocker(sl, cells);
        if (in != null) { PlaceGuard.tell(sl, player, in); return; }
        if (!ClaimGuard.mayPlace(sl, player, cells, worldPosition)) return;

        syncGauges();
        if (builtSize > 0 && builtSize != n) {
            for (Slot s : slots(builtSize)) {
                Place p = place(s);
                if (p != null && sl.getBlockEntity(p.origin()) instanceof BlinkGatePieceBlockEntity pe && worldPosition.equals(pe.core()))
                    sl.destroyBlock(p.origin(), false);
            }
        }
        builtSize = n;
        queue.clear();
        queue.addAll(todo);
        nextBuild = sl.getGameTime() + 5;
        sync();
    }

    private void buildStep(ServerLevel sl) {
        if (queue.isEmpty() || sl.getGameTime() < nextBuild) return;
        Slot s = queue.remove(0);
        nextBuild = sl.getGameTime() + Balance.BG_BUILD_GAP;
        Place p = place(s);
        boolean ok = p != null;
        if (ok) for (BlockPos c : p.cells()) if (!sl.getBlockState(c).canBeReplaced()) { ok = false; break; }
        ItemStack stock = s.corner() ? corners : segments;
        if (ok && stock.isEmpty()) ok = false;
        if (ok) {
            stock.shrink(1);
            syncGauges();
            MachineBlock b = s.corner() ? BlinkGateBlocks.BLINK_CORNER.get() : BlinkGateBlocks.BLINK_SEGMENT.get();
            BlockState st = Waterlog.placed(b.defaultBlockState().setValue(MachineBlock.FACING, p.facing()).setValue(MachineBlock.MOUNT, p.mount()), sl, p.origin());
            sl.setBlock(p.origin(), st, 3);
            b.setPlacedBy(sl, p.origin(), st, null, ItemStack.EMPTY);
            if (sl.getBlockEntity(p.origin()) instanceof BlinkGatePieceBlockEntity pe) pe.bind(worldPosition, s.order(), getOwner(), getOwnerName());
            playAt(sl, p.origin(), WmdSounds.BAY_CLAMP.get(), Balance.SND_MECH, 0.8F + 0.4F * s.order());
        }
        if (queue.isEmpty()) {
            formed = checkFormed();
            if (formed) playAt(sl, worldPosition, WmdSounds.MB_FORMED.get(), Balance.SND_MECH, 1.0F);
            sync();
        }
    }

    private void refund(boolean corner) {
        if (corner) { if (corners.isEmpty()) corners = new ItemStack(BlinkGateBlocks.CORNER_ITEM.get()); else corners.grow(1); }
        else { if (segments.isEmpty()) segments = new ItemStack(BlinkGateBlocks.SEGMENT_ITEM.get()); else segments.grow(1); }
        syncGauges();
    }

    void pieceGone() {
        if (!formed) return;
        formed = false;
        if (level instanceof ServerLevel sl && state >= S_DEPLOY && state <= S_OPEN) powerDown(sl, sl.getGameTime(), false);
        sync();
    }

    List<ItemStack> dismantle() {
        List<ItemStack> out = new ArrayList<>();
        if (!corners.isEmpty()) out.add(corners);
        if (!segments.isEmpty()) out.add(segments);
        corners = ItemStack.EMPTY; segments = ItemStack.EMPTY;
        if (level instanceof ServerLevel sl && builtSize > 0) {
            for (Slot s : slots(builtSize)) {
                Place p = place(s);
                if (p != null && level.getBlockEntity(p.origin()) instanceof BlinkGatePieceBlockEntity pe && worldPosition.equals(pe.core()))
                    sl.destroyBlock(p.origin(), false);
            }
        }
        unpair();
        return out;
    }

    int pair(BlinkGateCoreBlockEntity other) {
        if (other == this || other.worldPosition.equals(worldPosition)) return 1;
        unpair();
        other.unpair();
        partner = other.worldPosition.immutable();
        other.partner = worldPosition.immutable();
        sync();
        other.sync();
        return 0;
    }

    void unpair() {
        if (partner != null && level != null && level.isLoaded(partner) && level.getBlockEntity(partner) instanceof BlinkGateCoreBlockEntity o && worldPosition.equals(o.partner)) {
            o.partner = null;
            o.sync();
        }
        partner = null;
        sync();
    }

    private BlinkGateCoreBlockEntity partnerBe() {
        if (partner == null || level == null || !level.isLoaded(partner)) return null;
        return level.getBlockEntity(partner) instanceof BlinkGateCoreBlockEntity o && worldPosition.equals(o.partner) ? o : null;
    }

    public boolean arm() {
        if (!(level instanceof ServerLevel sl) || state != S_OFF || !formed || partner == null || getOwner() == null) return false;
        if (SableBridge.isOnShip(sl, worldPosition)) return false;
        if (energy().getEnergyStored() < Balance.BG_OPEN_FE * 20) return false;
        long now = sl.getGameTime();
        BlinkGateCoreBlockEntity o = partnerBe();
        if (o != null && !o.canFollow(now)) return false;
        payer = true;
        go(S_DEPLOY, now);
        lastUse = now;
        ticket(sl);
        playAt(sl, worldPosition, WmdSounds.RG_CLAWS.get(), Balance.SND_MECH, 0.9F);
        if (o != null) o.mirror(sl, state, stateAt, now);
        return true;
    }

    boolean canFollow(long now) {
        return formed && (state == S_OFF || state == S_COOL && coolUntil - now <= Balance.BG_STOW || state >= S_DEPLOY && state <= S_OPEN && !payer);
    }

    void mirror(ServerLevel sl, int s, long at, long now) {
        if (!canFollow(now)) return;
        lastUse = now;
        if (state == s && stateAt == at && !payer) return;
        if (state == S_OFF || state == S_COOL) playAt(sl, worldPosition, WmdSounds.RG_CLAWS.get(), Balance.SND_MECH, 0.9F);
        payer = false;
        state = s;
        stateAt = at;
        sync();
    }

    public void close() {
        if (!(level instanceof ServerLevel sl) || state < S_DEPLOY || state > S_OPEN) return;
        powerDown(sl, sl.getGameTime(), false);
    }

    void redstone(boolean on) {
        if (on && !redstone) arm();
        redstone = on;
    }

    private void ticket(ServerLevel sl) {
        sl.getChunkSource().addRegionTicket(TICKET, new ChunkPos(worldPosition), 2, new ChunkPos(worldPosition));
        if (partner != null) sl.getChunkSource().addRegionTicket(TICKET, new ChunkPos(partner), 2, new ChunkPos(partner));
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        rfCharge(sl, now);
        buildStep(sl);
        if (++checkTick % 40 == 0 && builtSize > 0 && queue.isEmpty()) {
            boolean f = checkFormed();
            if (f != formed) { formed = f; if (!f && state >= S_DEPLOY && state <= S_OPEN) powerDown(sl, now, false); sync(); }
        }
        switch (state) {
            case S_OFF -> { if (auto() && formed && partner != null && now % 10 == 0) autoArm(sl, now); }
            case S_DEPLOY, S_POWER, S_PORTAL, S_OPEN -> {
                if (!payer) {
                    if (now - lastUse > 40) closeSelf(sl, now, false);
                    else if (state == S_OPEN) crossings(sl, now);
                    break;
                }
                if (!useExtraEnergy(Balance.BG_OPEN_FE)) { powerDown(sl, now, false); break; }
                if (now % 20 == 0) ticket(sl);
                BlinkGateCoreBlockEntity fo = partnerBe();
                if (fo != null) fo.mirror(sl, state, stateAt, now);
                if (state == S_DEPLOY && now - stateAt >= Balance.BG_DEPLOY) go(S_POWER, now);
                else if (state == S_POWER && now - stateAt >= Balance.BG_POWER) go(S_PORTAL, now);
                else if (state == S_PORTAL && now - stateAt >= Balance.BG_PORTAL) go(S_OPEN, now);
                else if (state == S_OPEN) {
                    crossings(sl, now);
                    if (state == S_OPEN && payer && now - lastUse >= Balance.BG_IDLE_CLOSE) powerDown(sl, now, false);
                }
            }
            case S_DOWN -> { if (now - stateAt >= Balance.BG_DOWN) go(S_COOL, now); }
            case S_COOL -> { if (now >= coolUntil) { go(S_OFF, now); side.clear(); } }
            default -> go(S_OFF, now);
        }
        immune.values().removeIf(t -> t < now);
        updateStatus(now);
        return state >= S_POWER && state <= S_OPEN;
    }

    @Override
    protected void idle() {
        super.idle();
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        rfCharge(sl, now);
        buildStep(sl);
        if (state >= S_DEPLOY && state <= S_OPEN && !payer && now - lastUse > 40) closeSelf(sl, now, false);
        else if (state >= S_DEPLOY && state <= S_OPEN && payer && energy().getEnergyStored() < Balance.BG_OPEN_FE) powerDown(sl, now, false);
        else if (state == S_DOWN && now - stateAt >= Balance.BG_DOWN) go(S_COOL, now);
        else if (state == S_COOL && now >= coolUntil) { go(S_OFF, now); side.clear(); }
        updateStatus(now);
    }

    @Override
    protected void jammed(ServerLevel sl) {
        if (state >= S_DEPLOY && state <= S_OPEN) powerDown(sl, sl.getGameTime(), false);
    }

    private void go(int s, long now) {
        state = s;
        stateAt = now;
        sync();
    }

    private void powerDown(ServerLevel sl, long now, boolean jumped) {
        closeSelf(sl, now, jumped);
        BlinkGateCoreBlockEntity o = partnerBe();
        if (o != null) o.closeSelf(sl, now, jumped);
    }

    void closeSelf(ServerLevel sl, long now, boolean jumped) {
        if (state < S_DEPLOY || state > S_OPEN) {
            if (jumped && state != S_OFF) coolUntil = Math.max(coolUntil, now + Balance.BG_DOWN + Balance.BG_COOL);
            return;
        }
        coolUntil = now + Balance.BG_DOWN + (jumped ? Balance.BG_COOL : Balance.BG_STOW);
        payer = false;
        go(S_DOWN, now);
        playAt(sl, worldPosition, WmdSounds.RG_WHINE_DOWN.get(), Balance.SND_HEAVY, 1.0F);
    }

    private void updateStatus(long now) {
        int st;
        if (getOwner() == null) st = ST_NO_OWNER;
        else if (level != null && SableBridge.isOnShip(level, worldPosition)) st = ST_ON_SHIP;
        else if (!queue.isEmpty()) st = ST_BUILDING;
        else if (!formed) st = ST_UNBUILT;
        else if (partner == null) st = ST_NO_PARTNER;
        else if (state == S_OFF && level != null && level.isLoaded(partner) && level.getBlockEntity(partner) instanceof BlinkGateCoreBlockEntity o && !o.formed) st = ST_PARTNER_DOWN;
        else if (state == S_OPEN) st = now - refuseAt < 60 ? ST_REFUSED : ST_OPEN;
        else if (state >= S_DEPLOY && state <= S_PORTAL) st = ST_ARMING;
        else if (state == S_DOWN || state == S_COOL) st = ST_COOLING;
        else if (energy().getEnergyStored() < Balance.BG_OPEN_FE * 20) st = ST_NO_POWER;
        else st = ST_READY;
        if (st != status) { status = st; sync(); }
    }

    private void autoArm(ServerLevel sl, long now) {
        if (energy().getEnergyStored() < Balance.BG_OPEN_FE * 20) return;
        Vec3 c = centre();
        double R = Balance.BG_AUTO_R;
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, new AABB(c, c).inflate(R))) {
            Vec3 p = s.worldCenter(), v = s.linearVelocity();
            Vec3 d = c.subtract(p);
            double dist = d.length(), sp = v.length();
            if (dist > R || sp < Balance.BG_AUTO_MIN_SPEED) continue;
            if (v.dot(d) / (sp * Math.max(1.0E-6, dist)) < Math.cos(Math.toRadians(Balance.BG_AUTO_CONE))) continue;
            if (!trusted(sl, s)) continue;
            arm();
            return;
        }
    }

    private void crossings(ServerLevel sl, long now) {
        Vec3 c = centre(), n = normal(), r = right();
        double a = half();
        AABB box = new AABB(c, c).inflate(Math.abs(r.x) * (a + 1) + Math.abs(n.x) * 12.0, a + 1, Math.abs(r.z) * (a + 1) + Math.abs(n.z) * 12.0);
        Set<UUID> seen = new HashSet<>();
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, box)) {
            seen.add(s.id());
            Vec3 p = SableBridge.shipPosition(sl, s.id());
            if (p == null) continue;
            double now_ = p.subtract(c).dot(n);
            Double before = side.put(s.id(), now_);
            if (before == null || immune.containsKey(s.id())) continue;
            if (Math.signum(before) == Math.signum(now_) || now_ == 0.0) continue;
            Vec3 rel = p.subtract(c);
            if (!inOpening(rel.dot(r), rel.y, a)) continue;
            lastUse = now;
            int w = tryJump(sl, s, p, now_ < before, now);
            if (w != WHY_NONE) { refuse(sl, now, w); tellRefusal(sl, s, w); }
        }
        side.keySet().retainAll(seen);
    }

    private int tryJump(ServerLevel sl, SableBridge.ShipInfo s, Vec3 p, boolean front, long now) {
        BlinkGateCoreBlockEntity o = partnerBe();
        if (o == null || o.state != S_OPEN || !o.formed || o.energy().getEnergyStored() < Balance.BG_EXIT_MIN_FE) return WHY_PARTNER;
        if (!trusted(sl, s)) return WHY_TRUST;

        double a = Math.min(half(), o.half());
        Vec3 c = centre(), r = right();
        AABB b = s.worldBounds();
        Vec3 bc = b.getCenter().subtract(c);
        double hw = Math.abs(r.x) * b.getXsize() / 2.0 + Math.abs(r.z) * b.getZsize() / 2.0, hh = b.getYsize() / 2.0;
        double bx = bc.dot(r), by = bc.y;
        for (int i = 0; i < 4; i++) if (!inOpening(bx + ((i & 1) == 0 ? -hw : hw), by + ((i & 2) == 0 ? -hh : hh), a)) return WHY_FIT;
        long fe = jumpFe(s.blockCount());
        if (energy().getEnergyStored() < fe + feCost()) return WHY_POWER;

        Vec3 nA = normal(), nB = o.normal();
        double theta = Math.atan2(nA.z * -nB.x - nA.x * -nB.z, nA.x * -nB.x + nA.z * -nB.z);
        Vec3 cB = o.centre();
        AABB exit = turnBox(b, c, cB, theta);
        if (exitBlocked(sl, exit, s.id(), o)) return WHY_EXIT;

        List<Entity> riders = sl.getEntities((Entity) null, b.inflate(1.5), e -> !e.isPassenger() && e.isAlive() && !(e instanceof net.minecraft.world.entity.decoration.HangingEntity));
        if (!SableBridge.gateJump(sl, s.id(), c, cB, theta)) return WHY_PARTNER;
        useExtraEnergy((int) Math.min(Integer.MAX_VALUE, fe));
        for (Entity e : riders) {
            Vec3 np = cB.add(turn(e.position().subtract(c), theta));
            float yaw = e.getYRot() - (float) Math.toDegrees(theta);
            Vec3 nv = turn(e.getDeltaMovement(), theta);
            e.teleportTo(sl, np.x, np.y, np.z, Set.of(), yaw, e.getXRot());
            e.setDeltaMovement(nv);
            e.hurtMarked = true;
        }
        jumps++;
        o.immune.put(s.id(), now + Balance.BG_IMMUNE);
        immune.put(s.id(), now + Balance.BG_IMMUNE);
        flashAt = now;
        o.flashAt = now;
        sl.playSound(null, c.x, c.y, c.z, WmdSounds.BG_JUMP.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 1.0F);
        sl.playSound(null, cB.x, cB.y, cB.z, WmdSounds.BG_ARRIVE.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 1.0F);
        fx(sl, FX_JUMP, c, nA, half());
        fx(sl, FX_ARRIVE, cB, nB, o.half());
        Wmd.LOGGER.info("[blink gate] {} -> {}: craft {} ({} blocks) jumped, {} FE, {} rider(s)", worldPosition.toShortString(), o.worldPosition.toShortString(),
                s.id(), s.blockCount(), fe, riders.size());
        powerDown(sl, now, true);
        return WHY_NONE;
    }

    static Vec3 turn(Vec3 v, double theta) {
        double co = Math.cos(theta), si = Math.sin(theta);
        return new Vec3(v.x * co + v.z * si, v.y, -v.x * si + v.z * co);
    }

    static AABB turnBox(AABB b, Vec3 cA, Vec3 cB, double theta) {
        double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            Vec3 q = turn(new Vec3((i & 1) == 0 ? b.minX : b.maxX, 0.0, (i & 2) == 0 ? b.minZ : b.maxZ).subtract(cA.x, 0.0, cA.z), theta);
            x0 = Math.min(x0, q.x); x1 = Math.max(x1, q.x); z0 = Math.min(z0, q.z); z1 = Math.max(z1, q.z);
        }
        double dy = cB.y - cA.y;
        return new AABB(cB.x + x0, b.minY + dy, cB.z + z0, cB.x + x1, b.maxY + dy, cB.z + z1);
    }

    private static boolean exitBlocked(ServerLevel sl, AABB box, UUID self, BlinkGateCoreBlockEntity exitGate) {
        for (SableBridge.ShipInfo s : SableBridge.shipsIntersecting(sl, box)) if (!s.id().equals(self)) return true;
        BlockPos a = BlockPos.containing(box.minX, box.minY, box.minZ), b = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        long cells = (long) (b.getX() - a.getX() + 1) * (b.getY() - a.getY() + 1) * (b.getZ() - a.getZ() + 1);
        int step = cells > Balance.BG_EXIT_SCAN ? 2 : 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = a.getX(); x <= b.getX(); x += step) for (int y = a.getY(); y <= b.getY(); y += step) for (int z = a.getZ(); z <= b.getZ(); z += step) {
            m.set(x, y, z);
            if (!sl.isLoaded(m)) return true;
            BlockState st = sl.getBlockState(m);
            if (st.canBeReplaced() || st.getCollisionShape(sl, m).isEmpty()) continue;
            if (st.is(Wmd.MACHINE_PART.get()) && sl.getBlockEntity(m) instanceof MachinePartBlockEntity pe && pe.getController() != null
                    && sl.getBlockEntity(pe.getController()) instanceof BlinkGatePieceBlockEntity) continue;
            if (sl.getBlockEntity(m) instanceof BlinkGatePieceBlockEntity || m.equals(exitGate.worldPosition)) continue;
            return true;
        }
        return false;
    }

    private boolean trusted(ServerLevel sl, SableBridge.ShipInfo s) {
        AABB b = s.worldBounds().inflate(2.0);
        for (ServerPlayer p : sl.players()) {
            boolean aboard = b.contains(p.position()) || s.id().equals(SableBridge.shipUnder(p))
                    || p.getVehicle() != null && s.id().equals(SableBridge.shipIdAt(sl, p.getVehicle().blockPosition()));
            if (aboard && Access.trusts(this, p.getUUID())) return true;
        }
        for (BlockPos q : SableBridge.blockEntitiesOn(sl, s.id())) {
            if (sl.getBlockEntity(q) instanceof Owned ow && ow.getOwner() != null && Access.trusts(this, ow.getOwner())) return true;
        }
        return false;
    }

    private void tellRefusal(ServerLevel sl, SableBridge.ShipInfo s, int w) {
        Component msg = Component.translatable("gui.wmd.blink.why." + w).withStyle(net.minecraft.ChatFormatting.RED);
        AABB near = s.worldBounds().inflate(8.0);
        List<String> who = new ArrayList<>();
        for (ServerPlayer p : sl.players()) if (near.contains(p.position())) { p.displayClientMessage(msg, true); who.add(p.getGameProfile().getName()); }
        Set<UUID> owners = new HashSet<>();
        int owned = 0;
        for (BlockPos q : SableBridge.blockEntitiesOn(sl, s.id())) if (sl.getBlockEntity(q) instanceof Owned ow) { owned++; if (ow.getOwner() != null) owners.add(ow.getOwner()); }
        Wmd.LOGGER.info("[blink gate] {} refused craft {} ({} blocks, box {}): reason {} | owned blocks aboard {}, owners {} (gate owner {}) | players near {}",
                worldPosition.toShortString(), s.id(), s.blockCount(), s.worldBounds(), w, owned, owners, getOwner(), who);
    }

    private void refuse(ServerLevel sl, long now, int w) {
        why = w;
        refuseAt = now;
        Vec3 c = centre();
        sl.playSound(null, c.x, c.y, c.z, WmdSounds.TP_ABORT.get(), SoundSource.BLOCKS, Balance.SND_HEAVY, 0.8F);
        fx(sl, FX_REFUSE, c, normal(), half());
        sync();
    }

    private void fx(ServerLevel sl, int kind, Vec3 c, Vec3 n, double a) {
        BlinkGateFxPayload p = new BlinkGateFxPayload(kind, c, n, (float) a);
        double range = Balance.BG_FX_RANGE;
        for (ServerPlayer pl : sl.players()) if (pl.position().distanceToSqr(c) <= range * range) PacketDistributor.sendToPlayer(pl, p);
    }

    private static void playAt(ServerLevel sl, BlockPos p, SoundEvent e, float vol, float pitch) {
        sl.playSound(null, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, e, SoundSource.BLOCKS, vol, pitch);
    }

    public double deploy(float o, double tt) {
        double since = tt - stateAt;
        return switch (state) {
            case S_DEPLOY -> Mth.clamp((since - 45.0 * o) / 15.0, 0.0, 1.0);
            case S_POWER, S_PORTAL, S_OPEN, S_DOWN -> 1.0;
            case S_COOL -> 1.0 - Mth.clamp((since - 45.0 * (1.0 - o)) / 15.0, 0.0, 1.0);
            default -> 0.0;
        };
    }

    public boolean locked(float o, double tt) {
        double since = tt - stateAt;
        return switch (state) {
            case S_DEPLOY -> since >= 45.0 * o + 19.0;
            case S_POWER, S_PORTAL, S_OPEN, S_DOWN -> true;
            case S_COOL -> since < 45.0 * (1.0 - o) - 2.0;
            default -> false;
        };
    }

    public boolean lit(float o, double tt) {
        double since = tt - stateAt;
        return switch (state) {
            case S_POWER -> since >= 30.0 * o;
            case S_PORTAL, S_OPEN -> true;
            case S_DOWN -> since < 10.0 + 40.0 * (1.0 - o);
            default -> false;
        };
    }

    public double spin(double tt) {
        double since = tt - stateAt, v = switch (state) {
            case S_POWER -> Mth.clamp(since / 40.0, 0.0, 1.0);
            case S_PORTAL, S_OPEN -> 1.0;
            case S_DOWN -> 1.0 - 0.3 * Mth.clamp(since / 60.0, 0.0, 1.0);
            case S_COOL -> 0.7 * Math.max(0.0, 1.0 - since / 200.0);
            default -> 0.0;
        };
        double sf = tt - flashAt;
        if (sf >= 0.0 && sf < 30.0) v += 0.6 * Math.sin(Math.PI * sf / 30.0);
        return v;
    }

    public double portal(double tt) {
        double since = tt - stateAt;
        return switch (state) {
            case S_PORTAL -> Mth.clamp(since / Balance.BG_PORTAL, 0.0, 1.0);
            case S_OPEN -> 1.0;
            case S_DOWN -> 1.0 - Mth.clamp(since / 20.0, 0.0, 1.0);
            default -> 0.0;
        };
    }

    static void clientTickGate(Level level, BlockPos pos, BlockState st, BlinkGateCoreBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, st, be);
        be.tickClient(level);
    }

    private void tickClient(Level level) {
        long now = level.getGameTime();
        clientTicks++;
        lastState = state;
        if (!formed) BlinkGateGhost.unbuilt(worldPosition, now);
        double p = portal(now);
        if (formed && (p > 0.0 || state == S_COOL && now < coolUntil - Balance.BG_STOW || now - refuseAt < 30))
            ClientBlinkGate.portal(worldPosition, centre(), normal(), right(), half(), (float) p, state, now - refuseAt < 30, now);
        if (state == S_DOWN && now - stateAt > 20 && now - stateAt < 80 && now % 3 == 0) {
            for (int s = -1; s <= 1; s += 2) {
                double[] w = worldPoint(s * 1.1, 1.4, -0.45);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, w[0], w[1], w[2], (level.random.nextDouble() - 0.5) * 0.02, 0.06, (level.random.nextDouble() - 0.5) * 0.02);
            }
        }
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        lastTT = tt;
        double since = tt - stateAt;
        double doors = switch (state) {
            case S_POWER -> Mth.clamp(since / 20.0, 0.0, 1.0);
            case S_PORTAL, S_OPEN -> 1.0;
            case S_DOWN -> 1.0 - Mth.clamp((since - 40.0) / 20.0, 0.0, 1.0);
            default -> 0.0;
        };
        double key = switch (state) {
            case S_PORTAL -> Mth.clamp(since / 15.0, 0.0, 1.0);
            case S_OPEN -> 1.0;
            case S_DOWN -> 1.0 - Mth.clamp(since / 20.0, 0.0, 1.0);
            default -> 0.0;
        };
        final float dx = (float) (Balance.BG_DOOR_OPEN * ease(doors)), ky = (float) (Balance.BG_KEY_UP * ease(key));
        model.getBone("door_l").ifPresent(b -> b.setPosX(-X_SIGN * dx));
        model.getBone("door_r").ifPresent(b -> b.setPosX(X_SIGN * dx));
        model.getBone("key").ifPresent(b -> b.setPosY(ky));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean on = !dark && lit(0.0F, tt), keyOn = !dark && key > 0.5;
        model.getBone("lit").ifPresent(b -> b.setHidden(!on));
        model.getBone("keylit").ifPresent(b -> b.setHidden(!keyOn));
    }

    static double ease(double t) { t = Mth.clamp(t, 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }

    @Override public int slotCount() { return 2; }
    @Override public int firstSlotTank() { return 0; }
    @Override public int slotMax(int i) { return i == 0 ? 4 : 64; }

    private void syncGauges() {
        cornerGauge.setFluid(corners.isEmpty() ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, corners.getCount()));
        segmentGauge.setFluid(segments.isEmpty() ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, segments.getCount()));
        setChanged();
    }

    private ItemStack get(int i) { return i == 0 ? corners : segments; }

    private void put(int i, ItemStack st) { if (i == 0) corners = st; else segments = st; syncGauges(); }

    @Override
    public Container slotContainer() {
        return new Container() {
            @Override public int getContainerSize() { return 2; }
            @Override public boolean isEmpty() { return corners.isEmpty() && segments.isEmpty(); }
            @Override public ItemStack getItem(int i) { return get(i); }
            @Override public ItemStack removeItem(int i, int n) { ItemStack s = get(i); if (s.isEmpty()) return ItemStack.EMPTY; ItemStack r = s.split(n); syncGauges(); return r; }
            @Override public ItemStack removeItemNoUpdate(int i) { ItemStack r = get(i); put(i, ItemStack.EMPTY); return r; }
            @Override public void setItem(int i, ItemStack st) { put(i, st); }
            @Override public int getMaxStackSize() { return 64; }
            @Override public void setChanged() { BlinkGateCoreBlockEntity.this.setChanged(); }
            @Override public boolean stillValid(Player p) { return true; }
            @Override public boolean canPlaceItem(int i, ItemStack st) {
                return i == 0 ? st.is(BlinkGateBlocks.CORNER_ITEM.get()) : st.is(BlinkGateBlocks.SEGMENT_ITEM.get());
            }
            @Override public void clearContent() { corners = ItemStack.EMPTY; segments = ItemStack.EMPTY; syncGauges(); }
        };
    }

    @Override public List<ItemStack> guiSlotIcons() { return List.of(new ItemStack(BlinkGateBlocks.CORNER_ITEM.get()), new ItemStack(BlinkGateBlocks.SEGMENT_ITEM.get())); }
    @Override public String guiTankUnit(int t) { return "items"; }

    @Override protected List<FluidTank> tanks() { return List.of(cornerGauge, segmentGauge); }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY, Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("blink_corners", "blink_segments"); }
    @Override protected SoundEvent runSound() { return WmdSounds.BG_HUM_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_SHOWPIECE; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected float progress01() { return state >= S_DEPLOY && state <= S_PORTAL && level != null
            ? (float) Mth.clamp((level.getGameTime() - stateAt + (state == S_POWER ? Balance.BG_DEPLOY : state == S_PORTAL ? Balance.BG_DEPLOY + Balance.BG_POWER : 0))
                    / (double) (Balance.BG_DEPLOY + Balance.BG_POWER + Balance.BG_PORTAL), 0.0, 1.0) : -1.0F; }

    @Override
    public List<Component> guiInfo() {
        int[] nd = needs(size());
        return List.of(Component.translatable("gui.wmd.info.blink_gate", size(), size() - 4, nd[0], nd[1]),
                Component.translatable("gui.wmd.info.blink_gate_fe", Balance.BG_JUMP_FE / 1_000_000, String.format("%,d", Balance.BG_BLOCK_FE),
                        String.format("%,d", Balance.BG_OPEN_FE), Balance.BG_BUFFER_FE / 1_000_000));
    }

    @Override
    protected String detail() {
        return "state " + state + ", size " + size() + ", formed " + formed + ", partner " + (partner == null ? "-" : partner.toShortString()) + ", jumps " + jumps
                + ", buffer " + bufferPermille() / 10 + " %";
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        put(tag);
        if (!corners.isEmpty()) tag.put("bgCorners", corners.save(registries));
        if (!segments.isEmpty()) tag.put("bgSegments", segments.save(registries));
    }

    private void put(CompoundTag tag) {
        tag.putInt("bgMode", mode);
        tag.putInt("bgState", state);
        tag.putLong("bgStateAt", stateAt);
        tag.putLong("bgCool", coolUntil);
        tag.putLong("bgUse", lastUse);
        tag.putLong("bgFlash", flashAt);
        tag.putLong("bgRefuse", refuseAt);
        tag.putInt("bgSt", status);
        tag.putInt("bgWhy", why);
        tag.putInt("bgBuilt", builtSize);
        tag.putInt("bgJumps", jumps);
        tag.putBoolean("bgFormed", formed);
        tag.putBoolean("bgPayer", payer);
        tag.putInt("bgRf", rfChannel);
        tag.putInt("bgRfRate", rfRate);
        if (partner != null) tag.putLong("bgPartner", partner.asLong());
        tag.putInt("bgCornerN", corners.getCount());
        tag.putInt("bgSegmentN", segments.getCount());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        mode = tag.contains("bgMode") ? tag.getInt("bgMode") & 7 : AUTO_BIT;
        state = Mth.clamp(tag.getInt("bgState"), S_OFF, S_COOL);
        stateAt = tag.getLong("bgStateAt");
        coolUntil = tag.getLong("bgCool");
        lastUse = tag.getLong("bgUse");
        if (tag.contains("bgFlash")) flashAt = tag.getLong("bgFlash");
        if (tag.contains("bgRefuse")) refuseAt = tag.getLong("bgRefuse");
        status = tag.getInt("bgSt");
        why = tag.getInt("bgWhy");
        builtSize = tag.getInt("bgBuilt");
        jumps = tag.getInt("bgJumps");
        formed = tag.getBoolean("bgFormed");
        payer = tag.getBoolean("bgPayer");
        rfChannel = Mth.clamp(tag.getInt("bgRf"), 0, 9999);
        rfRate = tag.getInt("bgRfRate");
        partner = tag.contains("bgPartner") ? BlockPos.of(tag.getLong("bgPartner")) : null;
        if (tag.contains("bgCorners")) corners = ItemStack.parseOptional(registries, tag.getCompound("bgCorners"));
        if (tag.contains("bgSegments")) segments = ItemStack.parseOptional(registries, tag.getCompound("bgSegments"));
        if (level != null && level.isClientSide) {
            int cn = tag.getInt("bgCornerN"), sn = tag.getInt("bgSegmentN");
            cornerGauge.setFluid(cn > 0 ? new FluidStack(Fluids.WATER, cn) : FluidStack.EMPTY);
            segmentGauge.setFluid(sn > 0 ? new FluidStack(Fluids.WATER, sn) : FluidStack.EMPTY);
        } else syncGauges();
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        put(tag);
        return tag;
    }
}
