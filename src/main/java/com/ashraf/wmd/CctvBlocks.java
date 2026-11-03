package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class CctvBlocks {

    private CctvBlocks() {}

    abstract static class Device extends UnitBlock {
        Device(Properties p, int y1) { super(p, 0, 0, 0, y1, 0, 0); }

        abstract BlockEntityType<? extends ProcessUnitBlockEntity> type();

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
            if (!level.isClientSide && !newState.is(this) && !ShipRelink.shipMove(level, movedByPiston) && level instanceof ServerLevel sl) CctvNet.forget(sl, pos);
            super.onRemove(state, level, pos, newState, movedByPiston);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
            return level.isClientSide ? createTickerHelper(t, type(), CctvBlocks::clientTick) : createTickerHelper(t, type(), CctvBlocks::serverTick);
        }
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, ProcessUnitBlockEntity be) {
        ProcessUnitBlockEntity.serverTick(level, pos, state, be);
        if (be instanceof CctvDevice d && level instanceof ServerLevel sl) d.deviceTick(sl, level.getGameTime());
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, ProcessUnitBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        if (be instanceof CctvDevice d) d.deviceClient(level);
    }

    public static class Camera extends Device {
        public static final MapCodec<Camera> CODEC = simpleCodec(Camera::new);
        public Camera(Properties p) { super(p, 0); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CctvCameraBlockEntity(pos, state); }
        @Override BlockEntityType<? extends ProcessUnitBlockEntity> type() { return Wmd.CCTV_CAMERA_BE.get(); }
    }

    public static class Covert extends Device {
        public static final MapCodec<Covert> CODEC = simpleCodec(Covert::new);
        public Covert(Properties p) { super(p, 0); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public boolean anyFace() { return true; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CctvCameraBlockEntity(pos, state); }
        @Override BlockEntityType<? extends ProcessUnitBlockEntity> type() { return Wmd.COVERT_CAMERA_BE.get(); }
    }

    public static class Relay extends Device {
        public static final MapCodec<Relay> CODEC = simpleCodec(Relay::new);
        public Relay(Properties p) { super(p, 2); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CameraRelayBlockEntity(pos, state); }
        @Override BlockEntityType<? extends ProcessUnitBlockEntity> type() { return Wmd.CAMERA_RELAY_BE.get(); }
    }

    public static class Sensor extends Device {
        public static final MapCodec<Sensor> CODEC = simpleCodec(Sensor::new);
        public Sensor(Properties p) { super(p, 0); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PerimeterSensorBlockEntity(pos, state); }
        @Override BlockEntityType<? extends ProcessUnitBlockEntity> type() { return Wmd.PERIMETER_SENSOR_BE.get(); }
    }

    public static class Floodlight extends Device {
        public static final MapCodec<Floodlight> CODEC = simpleCodec(Floodlight::new);
        public Floodlight(Properties p) { super(p, 0); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FloodlightBlockEntity(pos, state); }
        @Override BlockEntityType<? extends ProcessUnitBlockEntity> type() { return Wmd.FLOODLIGHT_BE.get(); }
    }

    public static class Jammer extends Device {
        public static final MapCodec<Jammer> CODEC = simpleCodec(Jammer::new);
        public Jammer(Properties p) { super(p, 0); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PortableJammerBlockEntity(pos, state); }
        @Override BlockEntityType<? extends ProcessUnitBlockEntity> type() { return Wmd.PORTABLE_JAMMER_BE.get(); }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            var cd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (!level.isClientSide && cd != null && cd.copyTag().contains("charge") && level.getBlockEntity(pos) instanceof PortableJammerBlockEntity j)
                j.setCharge(cd.copyTag().getInt("charge"));
        }

        @Override
        public void interact(Level level, BlockPos controller, net.minecraft.world.entity.player.Player player) {
            if (!(level.getBlockEntity(controller) instanceof PortableJammerBlockEntity j)) return;
            var held = player.getMainHandItem();
            int add = held.is(net.minecraft.world.item.Items.REDSTONE) ? Balance.JAM_DUST : held.is(net.minecraft.world.item.Items.REDSTONE_BLOCK) ? Balance.JAM_BLOCK : 0;
            if (add > 0 && j.charge() < Balance.JAM_CHARGE) {
                j.setCharge(j.charge() + add);
                if (!player.getAbilities().instabuild) held.shrink(1);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.jammer.charge", j.charge() * 100 / Balance.JAM_CHARGE), true);
                return;
            }
            if (player.isShiftKeyDown() && held.isEmpty()) {
                net.minecraft.world.item.ItemStack s = new net.minecraft.world.item.ItemStack(asItem());
                net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
                t.putInt("charge", j.charge());
                s.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(t));
                level.removeBlock(controller, false);
                if (!player.addItem(s)) player.drop(s, false);
                return;
            }
            super.interact(level, controller, player);
        }

        @Override
        protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
            var be = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
            net.minecraft.world.item.ItemStack s = new net.minecraft.world.item.ItemStack(asItem());
            if (be instanceof PortableJammerBlockEntity j) {
                net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
                t.putInt("charge", j.charge());
                s.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(t));
            }
            return java.util.List.of(s);
        }
    }

    public static class Scanner extends Device {
        public static final MapCodec<Scanner> CODEC = simpleCodec(Scanner::new);
        public Scanner(Properties p) { super(p, 1); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SeismicScannerBlockEntity(pos, state); }
        @Override BlockEntityType<? extends ProcessUnitBlockEntity> type() { return Wmd.SEISMIC_SCANNER_BE.get(); }

        @Override
        public void interact(Level level, BlockPos controller, net.minecraft.world.entity.player.Player player) {
            if (!player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && player instanceof net.minecraft.server.level.ServerPlayer sp
                    && level.getBlockEntity(controller) instanceof SeismicScannerBlockEntity sc) { sc.start(sp); return; }
            super.interact(level, controller, player);
        }
    }

    interface CctvDevice {
        void deviceTick(ServerLevel sl, long now);
        default void deviceClient(Level level) {}
    }
}
