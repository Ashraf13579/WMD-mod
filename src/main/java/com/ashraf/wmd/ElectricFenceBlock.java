package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ElectricFenceBlock extends CrossCollisionBlock {

    public static final MapCodec<ElectricFenceBlock> CODEC = simpleCodec(ElectricFenceBlock::new);
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "electric_fence"));

    public ElectricFenceBlock(Properties properties) {
        super(3.2F, 2.0F, 16.0F, 16.0F, 24.0F, properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends CrossCollisionBlock> codec() { return CODEC; }

    private boolean attachsTo(BlockState s, boolean solidSide) {
        return s.getBlock() instanceof ElectricFenceBlock || s.getBlock() instanceof FenceEnergizerBlock || (!isExceptionForConnection(s) && solidSide);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockGetter level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        FluidState fluid = ctx.getLevel().getFluidState(pos);
        BlockState s = defaultBlockState().setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos np = pos.relative(d);
            BlockState n = level.getBlockState(np);
            s = s.setValue(PROPERTY_BY_DIRECTION.get(d), attachsTo(n, n.isFaceSturdy(level, np, d.getOpposite())));
        }
        return s;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos npos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return dir.getAxis().isHorizontal()
                ? state.setValue(PROPERTY_BY_DIRECTION.get(dir), attachsTo(neighbor, neighbor.isFaceSturdy(level, npos, dir.getOpposite())))
                : super.updateShape(state, dir, neighbor, level, pos, npos);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext ctx) { return Shapes.empty(); }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adj, Direction dir) {
        if (adj.is(this)) {
            if (!dir.getAxis().isHorizontal()) return true;
            if (state.getValue(PROPERTY_BY_DIRECTION.get(dir)) && adj.getValue(PROPERTY_BY_DIRECTION.get(dir.getOpposite()))) return true;
        }
        return super.skipRendering(state, adj, dir);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, WEST, SOUTH, WATERLOGGED);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return !state.getValue(WATERLOGGED); }

    private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> LIVE = new HashMap<>();

    static synchronized void markLive(ServerLevel sl, BlockPos pos, long until) {
        LIVE.computeIfAbsent(sl.dimension(), k -> new Long2LongOpenHashMap()).put(pos.asLong(), until);
    }

    public static synchronized boolean live(Level level, BlockPos pos) {
        Long2LongOpenHashMap m = LIVE.get(level.dimension());
        return m != null && m.get(pos.asLong()) > level.getGameTime();
    }

    static synchronized void prune(ServerLevel sl) {
        Long2LongOpenHashMap m = LIVE.get(sl.dimension());
        if (m == null) return;
        long now = sl.getGameTime();
        m.long2LongEntrySet().removeIf(e -> e.getLongValue() <= now);
    }

    public static synchronized void clearStatics() { LIVE.clear(); }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof LivingEntity le) || !le.isAlive()) return;
        if (le instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        if (le.invulnerableTime > 0 || !live(sl, pos)) return;
        Holder<DamageType> type = sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE);
        Vec3 c = Vec3.atBottomCenterOf(pos);
        if (!le.hurt(new DamageSource(type, c), Balance.EF_DAMAGE)) return;
        Vec3 away = le.position().subtract(c).multiply(1.0, 0.0, 1.0);
        away = away.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0.0F, le.getYRot() + 180.0F) : away.normalize();
        le.push(away.x * Balance.EF_PUSH, 0.3, away.z * Balance.EF_PUSH);
        le.hurtMarked = true;
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 14, 0.25, 0.4, 0.25, 0.25);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.5, c.z, 8, 0.15, 0.3, 0.15, 0.1);
        sl.playSound(null, c.x, c.y + 0.5, c.z, WmdSounds.EF_ZAP.get(), SoundSource.BLOCKS, 0.9F, 0.9F + sl.random.nextFloat() * 0.2F);
    }

    static java.util.List<BlockPos> walk(LevelReader level, java.util.Collection<BlockPos> start, int max) {
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.LinkedHashSet<BlockPos> seen = new java.util.LinkedHashSet<>();
        for (BlockPos s : start) if (level.getBlockState(s).getBlock() instanceof ElectricFenceBlock && seen.add(s)) q.add(s);
        while (!q.isEmpty() && seen.size() < max) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.size() >= max) break;
                if (!seen.contains(n) && level.hasChunkAt(n) && level.getBlockState(n).getBlock() instanceof ElectricFenceBlock) { seen.add(n); q.add(n); }
            }
        }
        return new java.util.ArrayList<>(seen);
    }
}
