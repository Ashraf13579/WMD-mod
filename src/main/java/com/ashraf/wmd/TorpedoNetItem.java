package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class TorpedoNetItem extends Item {

    static final String A = "wmdNetA", DIM = "wmdNetDim";

    public TorpedoNetItem(Properties properties) { super(properties); }

    record Plan(BlockPos lo, BlockPos hi, Direction.Axis axis, int len, int depth, int cost, String err, Object[] args) {
        boolean ok() { return err == null; }
        Component message() {
            if (ok()) return Component.translatable("message.wmd.torpedo_net.ghost", len, depth, cost).withStyle(ChatFormatting.AQUA);
            return Component.translatable("message.wmd.torpedo_net.err." + err, args).withStyle(ChatFormatting.RED);
        }
    }

    static BlockPos corner(ItemStack st, Level level) {
        if (st.isEmpty() || !(st.getItem() instanceof TorpedoNetItem)) return null;
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        if (d == null || !d.contains(A)) return null;
        CompoundTag t = d.copyTag();
        if (!level.dimension().location().toString().equals(t.getString(DIM))) return null;
        return BlockPos.of(t.getLong(A));
    }

    private static void setCorner(ItemStack st, BlockPos p, Level level) {
        CustomData.update(DataComponents.CUSTOM_DATA, st, t -> { t.putLong(A, p.asLong()); t.putString(DIM, level.dimension().location().toString()); });
    }

    private static void clear(ItemStack st) {
        if (st.has(DataComponents.CUSTOM_DATA)) CustomData.update(DataComponents.CUSTOM_DATA, st, t -> { t.remove(A); t.remove(DIM); });
    }

    static BlockPos aim(Level level, Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1.0F).scale(Balance.NET_AIM_RANGE));
        boolean under = level.getFluidState(BlockPos.containing(eye)).is(FluidTags.WATER);
        BlockHitResult h = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, under ? ClipContext.Fluid.NONE : ClipContext.Fluid.SOURCE_ONLY, p));
        if (h.getType() != HitResult.Type.BLOCK) return null;
        BlockPos hp = h.getBlockPos();
        if (level.getBlockState(hp).is(Blocks.WATER)) return hp.immutable();
        return hp.relative(h.getDirection()).immutable();
    }

    static boolean openWater(Level level, BlockPos q) {
        BlockState s = level.getBlockState(q);
        return s.is(Blocks.WATER) && level.getFluidState(q).isSource();
    }

    static BlockPos cell(BlockPos lo, Direction.Axis axis, int i, int j) {
        return axis == Direction.Axis.X ? lo.offset(i, j, 0) : lo.offset(0, j, i);
    }

    static Plan check(Level level, Player p, BlockPos a, BlockPos b, boolean server) {
        BlockPos lo = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos hi = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        Direction.Axis axis;
        if (a.getX() == b.getX() && a.getZ() == b.getZ()) axis = p.getDirection().getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
        else if (a.getZ() == b.getZ()) axis = Direction.Axis.X;
        else if (a.getX() == b.getX()) axis = Direction.Axis.Z;
        else return new Plan(lo, hi, null, 0, 0, 0, "plane", new Object[0]);
        int len = (axis == Direction.Axis.X ? hi.getX() - lo.getX() : hi.getZ() - lo.getZ()) + 1, depth = hi.getY() - lo.getY() + 1;
        int cost = (len * depth + Balance.NET_AREA_PER_ITEM - 1) / Balance.NET_AREA_PER_ITEM;
        if (len > Balance.NET_MAX_LEN || depth > Balance.NET_MAX_DEPTH)
            return new Plan(lo, hi, axis, len, depth, cost, "size", new Object[] { len, depth, Balance.NET_MAX_LEN, Balance.NET_MAX_DEPTH });
        Direction normal = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        List<BlockPos> cells = new ArrayList<>();
        for (int j = 0; j < depth; j++) {
            for (int i = 0; i < len; i++) {
                BlockPos q = cell(lo, axis, i, j);
                if (!level.isInWorldBounds(q) || !level.getWorldBorder().isWithinBounds(q)) return new Plan(lo, hi, axis, len, depth, cost, "border", new Object[0]);
                if (level.getBlockState(q).getBlock() instanceof TorpedoNetBlock) return new Plan(lo, hi, axis, len, depth, cost, "overlap", xyz(q));
                if (!openWater(level, q)) return new Plan(lo, hi, axis, len, depth, cost, "dry", xyz(q));
                for (int d = 1; d <= Balance.NET_LAYER_GAP; d++) {
                    for (int s = -1; s <= 1; s += 2) {
                        BlockPos r = q.relative(normal, d * s);
                        BlockState rs = level.getBlockState(r);
                        if (TorpedoNetBlock.placed(rs) && rs.getValue(TorpedoNetBlock.AXIS) == axis)
                            return new Plan(lo, hi, axis, len, depth, cost, "layer", new Object[] { Balance.NET_LAYER_GAP, r.getX(), r.getY(), r.getZ() });
                    }
                }
                cells.add(q);
            }
        }
        if (SableBridge.isOnShip(level, lo) || SableBridge.isOnShip(level, hi)) return new Plan(lo, hi, axis, len, depth, cost, "ship", new Object[0]);
        if (server && level instanceof ServerLevel sl) {
            for (BlockPos q : cells) if (sl.getServer().isUnderSpawnProtection(sl, q, p)) return new Plan(lo, hi, axis, len, depth, cost, "claim", new Object[0]);
            if (!ClaimGuard.mayPlace(sl, p, cells, null)) return new Plan(lo, hi, axis, len, depth, cost, "claim", new Object[0]);
            if (PlaceGuard.blocker(sl, lo, hi) != null) return new Plan(lo, hi, axis, len, depth, cost, "room", new Object[0]);
        }
        int have = count(p);
        if (!p.getAbilities().instabuild && have < cost) return new Plan(lo, hi, axis, len, depth, cost, "items", new Object[] { cost, have });
        return new Plan(lo, hi, axis, len, depth, cost, null, new Object[0]);
    }

    private static Object[] xyz(BlockPos q) { return new Object[] { q.getX(), q.getY(), q.getZ() }; }

    static int count(Player p) {
        int n = 0;
        for (ItemStack st : p.getInventory().items) if (st.getItem() instanceof TorpedoNetItem) n += st.getCount();
        for (ItemStack st : p.getInventory().offhand) if (st.getItem() instanceof TorpedoNetItem) n += st.getCount();
        return n;
    }

    private static void take(Player p, ItemStack held, int n) {
        int t = Math.min(n, held.getCount());
        held.shrink(t); n -= t;
        for (List<ItemStack> list : List.of(p.getInventory().items, p.getInventory().offhand)) {
            for (ItemStack st : list) {
                if (n <= 0) return;
                if (st == held || !(st.getItem() instanceof TorpedoNetItem)) continue;
                t = Math.min(n, st.getCount());
                st.shrink(t); n -= t;
            }
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) { return InteractionResult.PASS; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (level.isClientSide || !(level instanceof ServerLevel sl)) return InteractionResultHolder.success(st);
        BlockPos a = corner(st, level);
        if (p.isSecondaryUseActive()) {
            if (a != null) { clear(st); p.displayClientMessage(Component.translatable("message.wmd.torpedo_net.cancel").withStyle(ChatFormatting.GRAY), true); }
            else p.displayClientMessage(Component.translatable("message.wmd.torpedo_net.how").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.success(st);
        }
        BlockPos c = aim(level, p);
        if (c == null) {
            p.displayClientMessage(Component.translatable("message.wmd.torpedo_net.aim", (int) Balance.NET_AIM_RANGE).withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(st);
        }
        if (a == null) {
            if (!openWater(level, c)) {
                p.displayClientMessage(Component.translatable("message.wmd.torpedo_net.corner_dry").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(st);
            }
            setCorner(st, c, level);
            p.displayClientMessage(Component.translatable("message.wmd.torpedo_net.corner_a", c.getX(), c.getY(), c.getZ()).withStyle(ChatFormatting.AQUA), true);
            return InteractionResultHolder.success(st);
        }
        Plan plan = check(level, p, a, c, true);
        if (!plan.ok()) {
            p.displayClientMessage(plan.message(), true);
            return InteractionResultHolder.fail(st);
        }
        clear(st);
        if (!p.getAbilities().instabuild) take(p, st, plan.cost());
        place(sl, p, plan);
        p.displayClientMessage(Component.translatable("message.wmd.torpedo_net.placed", plan.len(), plan.depth(), p.getAbilities().instabuild ? 0 : plan.cost())
                .withStyle(ChatFormatting.AQUA), true);
        return InteractionResultHolder.success(st);
    }

    static void place(ServerLevel sl, Player p, Plan plan) {
        BlockState base = Wmd.TORPEDO_NET.get().defaultBlockState().setValue(TorpedoNetBlock.AXIS, plan.axis()).setValue(TorpedoNetBlock.PLACED, true)
                .setValue(Waterlog.W, true);
        sl.setBlock(plan.lo(), base.setValue(TorpedoNetBlock.CTRL, true), 3);
        for (int j = 0; j < plan.depth(); j++) {
            for (int i = 0; i < plan.len(); i++) {
                if (i == 0 && j == 0) continue;
                sl.setBlock(cell(plan.lo(), plan.axis(), i, j), base, 3);
            }
        }
        if (sl.getBlockEntity(plan.lo()) instanceof TorpedoNetBlockEntity be) be.setup(p.getUUID(), plan.len(), plan.depth());
        Vec3 mid = AABB.encapsulatingFullBlocks(plan.lo(), plan.hi()).getCenter();
        sl.playSound(null, mid.x, mid.y, mid.z, WmdSounds.NET_DEPLOY.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        BlockPos top = cell(plan.lo(), plan.axis(), plan.len() / 2, plan.depth());
        if (sl.getFluidState(top).isEmpty()) sl.playSound(null, mid.x, top.getY(), mid.z, WmdSounds.BUOY_SPLASH.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
        Wmd.LOGGER.info("Torpedo net {} x {} placed at {} (axis {}) by {}, {} item(s)", plan.len(), plan.depth(), plan.lo().toShortString(), plan.axis(),
                p.getName().getString(), p.getAbilities().instabuild ? 0 : plan.cost());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wmd.torpedo_net", Balance.NET_MAX_LEN, Balance.NET_MAX_DEPTH, Balance.NET_AREA_PER_ITEM).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.torpedo_net.t4").withStyle(ChatFormatting.GRAY));
    }
}
