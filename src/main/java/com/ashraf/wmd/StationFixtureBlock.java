package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public abstract class StationFixtureBlock extends UnitBlock {

    protected StationFixtureBlock(Properties p, int z1) { super(p, 0, 0, 0, 1, 0, z1); }

    protected StationFixtureBlock(Properties p, int x1, int y1, int z1) { super(p, 0, x1, 0, y1, 0, z1); }

    public boolean meshDrawn() { return false; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StationFixtureBlockEntity(pos, state); }

    public static class Console extends StationFixtureBlock {
        public static final MapCodec<Console> CODEC = simpleCodec(Console::new);
        public Console(Properties p) { super(p, 0); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

        @Override
        public void interact(Level level, BlockPos controller, Player player) {
            if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) StationPayloads.sendInfo(sp, Orbit.slotAt(controller.getX(), controller.getZ()));
        }
    }

    public static class Bunk extends StationFixtureBlock {
        public static final MapCodec<Bunk> CODEC = simpleCodec(Bunk::new);
        public Bunk(Properties p) { super(p, 1); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

        @Override
        public void interact(Level level, BlockPos controller, Player player) {
            if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel)) return;
            BlockPos front = controller.relative(level.getBlockState(controller).getValue(FACING)).below();
            sp.setRespawnPosition(level.dimension(), front, sp.getYRot(), true, false);
            sp.getPersistentData().putLong(StationLife.BUNK, controller.asLong());
            sp.displayClientMessage(Component.translatable("message.wmd.station.bunk").withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, controller, WmdSounds.OR_CAPSULE_HATCH.get(), SoundSource.BLOCKS, 0.6F, 1.3F);
        }
    }

    public static class Rack extends StationFixtureBlock {
        public static final MapCodec<Rack> CODEC = simpleCodec(Rack::new);
        public Rack(Properties p) { super(p, 0); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

        @Override
        public void interact(Level level, BlockPos controller, Player player) {
            if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return;
            ItemStack held = sp.getMainHandItem();
            ItemStack target = held.getItem() instanceof OxygenTankItem ? held : EvaSuitItem.torso(sp);
            if (target.isEmpty()) { sp.displayClientMessage(Component.translatable("message.wmd.station.rack_nothing").withStyle(ChatFormatting.YELLOW), true); return; }
            int have = EvaSuitItem.oxygen(target), room = Balance.O2_FULL - have;
            if (room <= 0) { sp.displayClientMessage(Component.translatable("message.wmd.station.rack_full").withStyle(ChatFormatting.GREEN), true); return; }
            int slot = Orbit.slotAt(controller.getX(), controller.getZ());
            int got = StationLife.drawOxygen(sl, slot, room);
            if (got <= 0) { sp.displayClientMessage(Component.translatable("message.wmd.station.rack_no_power").withStyle(ChatFormatting.RED), true); return; }
            EvaSuitItem.setOxygen(target, have + got);
            sp.displayClientMessage(Component.translatable("message.wmd.oxygen.refilled", EvaSuitItem.clock(have + got)).withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, controller, WmdSounds.OR_O2_POUR.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
    }

    public static class Battery extends StationFixtureBlock {
        public static final MapCodec<Battery> CODEC = simpleCodec(Battery::new);
        public Battery(Properties p) { super(p, 0); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

        @Override
        public void interact(Level level, BlockPos controller, Player player) {
            if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return;
            int slot = Orbit.slotAt(controller.getX(), controller.getZ());
            if (sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty()) {
                if (!sp.getUUID().equals(Orbit.OrbitSlots.get(sl.getServer()).ownerOf(slot)) && !sp.hasPermissions(2)) {
                    sp.displayClientMessage(Component.translatable("message.wmd.not_yours").withStyle(ChatFormatting.RED), true);
                    return;
                }
                if (Station.takeBattery(sl, slot, controller)) {
                    sp.getInventory().placeItemBackInInventory(new ItemStack(Wmd.STATION_BATTERY_ITEM.get()));
                    sp.displayClientMessage(Component.translatable("message.wmd.station.battery_out", Station.capacity(sl.getServer(), slot) / 1_000_000L).withStyle(ChatFormatting.YELLOW), true);
                    level.playSound(null, controller, WmdSounds.ST_DOCK_CLAMP.get(), SoundSource.BLOCKS, 0.6F, 1.4F);
                }
                return;
            }
            long buf = Station.Data.get(sl.getServer()).energy.getOrDefault(slot, Balance.STATION_BUFFER / 2), cap = Station.capacity(sl.getServer(), slot);
            sp.displayClientMessage(Component.translatable("message.wmd.station.battery_info", buf / 1_000_000L, cap / 1_000_000L).withStyle(ChatFormatting.AQUA), true);
        }
    }

    public static class RodRack extends StationFixtureBlock {
        public static final MapCodec<RodRack> CODEC = simpleCodec(RodRack::new);
        public RodRack(Properties p) { super(p, 7, 1, 6); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public boolean meshDrawn() { return true; }

        @Override
        public void interact(Level level, BlockPos controller, Player player) {
            if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return;
            int slot = Orbit.slotAt(controller.getX(), controller.getZ());
            Station.Module m = Station.rodModuleAt(sl.getServer(), slot, controller);
            if (m == null) return;
            Station.Data d = Station.Data.get(sl.getServer());
            int n = d.rods.getOrDefault(m.id(), 0);
            ItemStack held = sp.getMainHandItem();
            if (!held.is(Wmd.ORBITAL_ROD.get())) {
                sp.displayClientMessage(Component.translatable("message.wmd.rod.rack", n, Balance.ROD_RACK_MAX).withStyle(ChatFormatting.AQUA), true);
                return;
            }
            if (n >= Balance.ROD_RACK_MAX) { sp.displayClientMessage(Component.translatable("message.wmd.rod.rack_full", Balance.ROD_RACK_MAX).withStyle(ChatFormatting.YELLOW), true); return; }
            if (!sp.getAbilities().instabuild) held.shrink(1);
            d.rods.put(m.id(), n + 1);
            d.setDirty();
            if (sl.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be) be.setRods(n + 1);
            sp.displayClientMessage(Component.translatable("message.wmd.rod.loaded", n + 1, Balance.ROD_RACK_MAX).withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, controller, WmdSounds.ST_DOCK_CLAMP.get(), SoundSource.BLOCKS, 0.8F, 1.3F);
        }
    }

    public static class RodConsole extends StationFixtureBlock {
        public static final MapCodec<RodConsole> CODEC = simpleCodec(RodConsole::new);
        public RodConsole(Properties p) { super(p, 1, 0, 0); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public boolean meshDrawn() { return true; }

        @Override
        public void interact(Level level, BlockPos controller, Player player) {
            if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return;
            int slot = Orbit.slotAt(controller.getX(), controller.getZ());
            Station.Module m = Station.rodModuleAt(sl.getServer(), slot, controller);
            if (m == null) return;
            RodPayloads.sendInfo(sp, slot);
        }
    }

    public static class MirrorDesk extends StationFixtureBlock {
        public static final MapCodec<MirrorDesk> CODEC = simpleCodec(MirrorDesk::new);
        public MirrorDesk(Properties p) { super(p, 1, 1, 2); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public boolean meshDrawn() { return true; }

        @Override
        public void interact(Level level, BlockPos controller, Player player) {
            if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return;
            int slot = Orbit.slotAt(controller.getX(), controller.getZ());
            if (Station.fixtureModule(sl.getServer(), slot, controller, Station.MIRROR) == null) return;
            MirrorPayloads.sendInfo(sp, slot);
        }
    }

    public static class BayConsole extends StationFixtureBlock {
        public static final MapCodec<BayConsole> CODEC = simpleCodec(BayConsole::new);
        public BayConsole(Properties p) { super(p, 2, 0, 0); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public boolean meshDrawn() { return true; }

        @Override
        public void interact(Level level, BlockPos controller, Player player) {
            if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return;
            int slot = Orbit.slotAt(controller.getX(), controller.getZ());
            Station.Module m = Station.fixtureModule(sl.getServer(), slot, controller, Station.BAY);
            if (m == null) return;
            AsteroidBay.console(sp, sl, slot, m);
        }
    }
}
