package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class BayPick {

    private BayPick() {}

    static final double SLACK = 3.0;

    public record Ask(BlockPos controller, BlockPos cell) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_pick"));
        public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = StreamCodec.of((b, p) -> { b.writeBlockPos(p.controller()); b.writeBlockPos(p.cell()); },
                b -> new Ask(b.readBlockPos(), b.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static VoxelShape cellShape(BlockGetter level, BlockPos controller, BlockState state, BayModuleBlock block, int lx, int ly, int lz) {
        Direction f = state.getValue(MachineBlock.FACING), mount = Machines.mount(state);
        VoxelShape s = block.machineShape(level, controller, new BlockPos(lx, ly, lz), f);
        return s == null || s.isEmpty() ? null : Machines.mountShape(s, mount);
    }

    static boolean hasModel(BayModuleBlock block) {
        return !MachineShapes.cells(MachineShapes.meshOf(BuiltInRegistries.BLOCK.getKey(block).getPath())).isEmpty();
    }

    static final int X0 = -8, X1 = 8, Y0 = 0, Y1 = 12;

    private static void handle(ServerPlayer sp, Ask a) {
        BlockPos c = a.controller(), cell = a.cell();
        if (!Reach.usable(sp, cell, sp.blockInteractionRange() + SLACK) || !sp.level().isLoaded(c)) return;
        BlockState st = sp.level().getBlockState(c);
        if (!(st.getBlock() instanceof BayModuleBlock block) || !(sp.level().getBlockEntity(c) instanceof BayModuleBlockEntity)) return;
        Direction f = st.getValue(MachineBlock.FACING), mount = Machines.mount(st);
        for (int x = X0; x <= X1; x++)
            for (int y = Y0; y <= Y1; y++)
                for (int z = 0; z < block.kind.length; z++) {
                    if (!Machines.toWorld(c, f, mount, x, y, z).equals(cell)) continue;
                    if (cellShape(sp.level(), c, st, block, x, y, z) == null) return;
                    block.interact(sp.level(), c, sp, new BlockHitResult(Vec3.atCenterOf(cell), Direction.UP, cell, false));
                    return;
                }
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(Ask.TYPE, Ask.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) handle(sp, p); }));
        }
    }
}
