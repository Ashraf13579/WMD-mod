package com.ashraf.wmd;

import java.util.UUID;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CrewCapsuleBlock extends UnitBlock {

    public static final MapCodec<CrewCapsuleBlock> CODEC = simpleCodec(CrewCapsuleBlock::new);

    public CrewCapsuleBlock(Properties properties) { super(properties, -1, 1, 0, 2, -1, 1); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CrewCapsuleBlockEntity(pos, state); }

    private static final java.util.Map<net.minecraft.core.Direction, java.util.List<net.minecraft.world.phys.AABB>> SIDE = new java.util.concurrent.ConcurrentHashMap<>();

    private static net.minecraft.core.Direction sidePort(net.minecraft.world.level.BlockGetter level, BlockPos controller) {
        if (!(level.getBlockEntity(controller) instanceof CrewCapsuleBlockEntity be)) return null;
        int port = be.boardPort();
        return Boarding.side(port) ? Station.spotDir(port) : null;
    }

    private static java.util.List<net.minecraft.world.phys.AABB> sideBoxes(net.minecraft.core.Direction dir) {
        return SIDE.computeIfAbsent(dir, d -> {
            java.util.List<net.minecraft.world.phys.AABB> out = new java.util.ArrayList<>();
            for (net.minecraft.world.phys.AABB b : MachineShapes.meshBoxes(MachineShapes.meshOf("crew_capsule_hull"))) {

                double px0 = 0.75 - b.maxY, px1 = 0.75 - b.minY, py0 = 1.5 + b.minX, py1 = 1.5 + b.maxX;
                double ax, az, bx, bz;
                switch (d) {
                    case SOUTH -> { ax = -b.maxZ; az = px0; bx = -b.minZ; bz = px1; }
                    case WEST -> { ax = -px1; az = -b.maxZ; bx = -px0; bz = -b.minZ; }
                    case NORTH -> { ax = b.minZ; az = -px1; bx = b.maxZ; bz = -px0; }
                    default -> { ax = px0; az = b.minZ; bx = px1; bz = b.maxZ; }
                }
                out.add(new net.minecraft.world.phys.AABB(ax, py0, az, bx, py1, bz));
            }
            return out;
        });
    }

    private static net.minecraft.world.phys.shapes.VoxelShape sideShape(net.minecraft.core.Direction dir, int ox, int oy, int oz) {
        net.minecraft.world.phys.shapes.VoxelShape out = net.minecraft.world.phys.shapes.Shapes.empty();
        double x0 = ox - 0.5, y0 = oy, z0 = oz - 0.5;
        for (net.minecraft.world.phys.AABB b : sideBoxes(dir)) {
            double ax = Math.max(b.minX, x0), ay = Math.max(b.minY, y0), az = Math.max(b.minZ, z0), bx = Math.min(b.maxX, x0 + 1), by = Math.min(b.maxY, y0 + 1), bz = Math.min(b.maxZ, z0 + 1);
            if (bx - ax < 1e-3 || by - ay < 1e-3 || bz - az < 1e-3) continue;
            out = net.minecraft.world.phys.shapes.Shapes.or(out, net.minecraft.world.phys.shapes.Shapes.box(ax - x0, ay - y0, az - z0, bx - x0, by - y0, bz - z0));
        }
        return out.optimize();
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        net.minecraft.core.Direction side = sidePort(level, pos);
        return side != null ? sideShape(side, 0, 0, 0) : super.getShape(state, level, pos, context);
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape machineShape(net.minecraft.world.level.BlockGetter level, BlockPos controller, BlockPos local, net.minecraft.core.Direction facing) {
        net.minecraft.core.Direction side = sidePort(level, controller);
        if (side == null) return super.machineShape(level, controller, local, facing);
        BlockPos o = Machines.toWorld(BlockPos.ZERO, facing, local.getX(), local.getY(), local.getZ());
        return sideShape(side, o.getX(), o.getY(), o.getZ());
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.isClientSide || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return;
        Crew.openMenu(sp, sl, controller);
    }

    static BlockPos park(ServerLevel orbit, int n, UUID owner, ResourceKey<Level> padDim, BlockPos pad) {
        BlockPos c = Orbit.slotCentre(n);
        Station.Module core = Station.core(orbit.getServer(), n);
        for (int k = core != null && core.built() ? -1 : 0; k < 8; k++) {
            BlockPos at = k < 0 ? Station.world(core, 0, 9, 0) : c.offset(k * 8, 0, 0);
            boolean free = true;
            for (int x = -1; x <= 1 && free; x++) for (int y = 0; y <= 2 && free; y++) for (int z = -1; z <= 1 && free; z++)
                if (!orbit.getBlockState(at.offset(x, y, z)).isAir()) free = false;
            if (!free) continue;
            BlockState st = Wmd.CREW_CAPSULE_BLOCK.get().defaultBlockState();
            orbit.setBlockAndUpdate(at, st);
            st.getBlock().setPlacedBy(orbit, at, st, null, ItemStack.EMPTY);
            if (orbit.getBlockEntity(at) instanceof CrewCapsuleBlockEntity be) be.setUp(owner, padDim, pad);
            if (k < 0 && orbit.getBlockEntity(Station.controller(core)) instanceof StationModuleBlockEntity sb) {
                sb.setDock(orbit.getGameTime());
                orbit.playSound(null, at, WmdSounds.ST_DOCK_CLAMP.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_MECH, 1.1F);
            }
            if (!Orbit.hasGround(orbit.getServer(), n)) Orbit.setGround(orbit.getServer(), n, padDim, pad.getX(), pad.getZ());
            Wmd.LOGGER.info("[crew] capsule parked in orbit at {} (slot {})", at.toShortString(), n);
            return at;
        }
        return null;
    }
}
