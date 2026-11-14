package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class JetEngineBlock extends UnitBlock implements WrenchUse {

    public enum Kind {
        SINGLE("single_jet", 0, new String[] { "single_jet", "single_jet_bare" }),
        TWIN("twin_jet", 1, new String[] { "twin_jet_round", "twin_jet_round_bare", "twin_jet_vector", "twin_jet_vector_bare", "twin_jet_v", "twin_jet_v_bare" }),
        STEALTH("stealth_jet", 2, new String[] { "stealth_jet", "stealth_jet_bare" });

        public final String id;

        public final int half;

        public final String[] meshes;

        Kind(String id, int half, String[] meshes) { this.id = id; this.half = half; this.meshes = meshes; }

        public String mesh(int variant) { return meshes[Math.floorMod(variant, meshes.length)]; }

        public boolean afterburner() { return this != STEALTH; }
    }

    public static final MapCodec<JetEngineBlock> CODEC_SINGLE = simpleCodec(p -> new JetEngineBlock(p, Kind.SINGLE));
    public static final MapCodec<JetEngineBlock> CODEC_TWIN = simpleCodec(p -> new JetEngineBlock(p, Kind.TWIN));
    public static final MapCodec<JetEngineBlock> CODEC_STEALTH = simpleCodec(p -> new JetEngineBlock(p, Kind.STEALTH));

    private final Kind kind;

    public JetEngineBlock(Properties properties, Kind kind) {
        super(properties, -kind.half, kind.half, 0, 0, 0, 3);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return switch (kind) { case SINGLE -> CODEC_SINGLE; case TWIN -> CODEC_TWIN; default -> CODEC_STEALTH; };
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return SableBridge.newJetEngine(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != AirBlocks.JET_ENGINE_BE.get()) return null;
        return level.isClientSide ? (lv, pos, st, be) -> AirEngineBlockEntity.clientTickAir(lv, pos, st, (AirEngineBlockEntity) be)
                : (lv, pos, st, be) -> ProcessUnitBlockEntity.serverTick(lv, pos, st, (ProcessUnitBlockEntity) be);
    }

    @Override
    public VoxelShape machineShape(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) {
        String mesh = level != null && controller != null && level.getBlockEntity(controller) instanceof JetEngineBlockEntity be ? be.meshName() : kind.mesh(0);
        VoxelShape g = MachineShapes.get(mesh, local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get(kind.mesh(0), local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof JetEngineBlockEntity be)) return;
        ItemStack held = player.getMainHandItem();
        boolean wrench = held.is(Wmd.WRENCH.get()) && !player.isShiftKeyDown();
        if ((held.isEmpty() && player.isShiftKeyDown()) || (wrench && kind != Kind.TWIN)) {
            be.toggleStyle();
            say(level, player, hit, Component.translatable(be.bare() ? "message.wmd.p6.bare" : "message.wmd.p6.boxed"));
            return;
        }
        if (wrench) {
            be.nextLayout();
            say(level, player, hit, Component.translatable("message.wmd.jet.layout", Component.translatable("variant.wmd." + be.meshName())));
            return;
        }
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        player.displayClientMessage(be.status(), true);
    }

    private static void say(Level level, Player player, BlockHitResult hit, Component msg) {
        player.displayClientMessage(msg, true);
        level.playSound(null, hit.getBlockPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4F, 1.2F);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof ProcessUnitBlockEntity p) player.displayClientMessage(p.status(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd." + kind.id).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.jet.use").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable(kind == Kind.TWIN ? "tooltip.wmd.jet.style_twin" : "tooltip.wmd.jet.style").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
