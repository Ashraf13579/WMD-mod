package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class LaserTabletItem extends Item {

    public LaserTabletItem(Properties p) { super(p); }

    private static CompoundTag data(ItemStack s) {
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    private static void put(ItemStack s, String key, long pos, String dim) {
        CompoundTag t = data(s);
        t.putLong(key, pos);
        t.putString(key + "Dim", dim);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    static BlockPos bound(ItemStack s, String key, Level level) {
        CompoundTag t = data(s);
        if (!t.contains(key) || !t.getString(key + "Dim").equals(level.dimension().location().toString())) return null;
        return BlockPos.of(t.getLong(key));
    }

    private static BlockEntity machineAt(Level level, BlockPos p) {
        BlockEntity be = level.getBlockEntity(p);
        if (be instanceof MachinePartBlockEntity part && part.getController() != null) be = level.getBlockEntity(part.getController());
        return be;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = machineAt(level, ctx.getClickedPos());
        LaunchTerminalBlockEntity term = be instanceof LaunchTerminalBlockEntity t ? t : null;
        String dim = level.dimension().location().toString();
        if (term != null) {
            if (term.getOwner() != null && !Access.trusts(term, player.getUUID())) { player.displayClientMessage(Component.translatable("message.wmd.tablet.not_yours"), true); return InteractionResult.FAIL; }
            if (term.getTier() > 3) { player.displayClientMessage(Component.translatable("message.wmd.tablet.no_t4"), true); return InteractionResult.FAIL; }
            put(ctx.getItemInHand(), "launcher", term.getBlockPos().asLong(), dim);
            player.displayClientMessage(Component.translatable("message.wmd.tablet.bound_launcher", term.getBlockPos().toShortString()), true);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
            return InteractionResult.CONSUME;
        }
        if (be instanceof DroneHiveBlockEntity hive) {
            if (hive.getOwner() != null && !Access.trusts(hive, player.getUUID())) { player.displayClientMessage(Component.translatable("message.wmd.tablet.not_yours"), true); return InteractionResult.FAIL; }
            put(ctx.getItemInHand(), "hive", hive.getBlockPos().asLong(), dim);
            player.displayClientMessage(Component.translatable("message.wmd.tablet.bound_hive", hive.getBlockPos().toShortString()), true);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
            return InteractionResult.CONSUME;
        }
        if (be instanceof BigDroneSite site && (site instanceof DroneCatapultBlockEntity || site.isHangarModule())) {
            if (site.getOwner() != null && !Access.trusts(site, player.getUUID())) { player.displayClientMessage(Component.translatable("message.wmd.tablet.not_yours"), true); return InteractionResult.FAIL; }
            put(ctx.getItemInHand(), "hive", site.sitePos().asLong(), dim);
            player.displayClientMessage(Component.translatable("message.wmd.tablet.bound_site", site.sitePos().toShortString()), true);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
            return InteractionResult.CONSUME;
        }
        if (be instanceof LaserTripodBlockEntity tri) {
            if (tri.getOwner() != null && !Access.trusts(tri, player.getUUID())) { player.displayClientMessage(Component.translatable("message.wmd.tablet.not_yours"), true); return InteractionResult.FAIL; }
            put(ctx.getItemInHand(), "tripod", tri.getBlockPos().asLong(), dim);
            player.displayClientMessage(Component.translatable("message.wmd.tablet.bound_tripod", tri.getBlockPos().toShortString()), true);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, status(sp, s, hand));
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    static TabletInfoPayload status(ServerPlayer p, ItemStack s, InteractionHand hand) {
        List<String> lines = new ArrayList<>();
        String why = check(p, s, lines);
        return new TabletInfoPayload(hand == InteractionHand.MAIN_HAND, lines, why == null, why == null ? "" : why);
    }

    private static String check(ServerPlayer p, ItemStack s, List<String> lines) {
        ServerLevel level = p.serverLevel();
        BlockPos lp = bound(s, "launcher", level), tp = bound(s, "tripod", level);
        LaunchTerminalBlockEntity term = lp != null && level.isLoaded(lp) && level.getBlockEntity(lp) instanceof LaunchTerminalBlockEntity t ? t : null;
        LaserTripodBlockEntity tri = tp != null && level.isLoaded(tp) && level.getBlockEntity(tp) instanceof LaserTripodBlockEntity x ? x : null;
        if (term != null && term.getOwner() != null && !Access.trusts(term, p.getUUID())) term = null;
        if (tri != null && tri.getOwner() != null && !Access.trusts(tri, p.getUUID())) tri = null;
        if (term == null) lines.add("gui.wmd.tablet.no_launcher");
        else {
            MissileBuild b = term.getLoadedBuild();
            int dist = (int) Math.round(Math.sqrt(SableBridge.distSqWithShips(p.level(), p.position(), term.getBlockPos().getCenter())));
            lines.add("gui.wmd.tablet.launcher|" + term.getTier() + "|" + term.getBlockPos().getX() + "|" + term.getBlockPos().getZ() + "|" + dist);
            lines.add(b == null ? "gui.wmd.tablet.empty" : b.seeker().equals(MissileBuild.SEEK_LASER) ? "gui.wmd.tablet.missile_laser|" + b.calibre() + "|" + b.warheadType()
                    : "gui.wmd.tablet.missile_no_seeker|" + b.calibre() + "|" + b.warheadType());
        }
        LaserSpots.Spot spot = tri == null ? null : LaserSpots.get(level, tri.spotId());
        if (tri == null) lines.add("gui.wmd.tablet.no_tripod");
        else if (spot == null) lines.add("gui.wmd.tablet.tripod_dark|" + tri.getBlockPos().getX() + "|" + tri.getBlockPos().getZ());
        else lines.add("gui.wmd.tablet.spot|" + (int) Math.floor(spot.pos().x) + "|" + (int) Math.floor(spot.pos().y) + "|" + (int) Math.floor(spot.pos().z));
        if (term == null) return "gui.wmd.tablet.why.launcher";
        if (term.getTier() > 3) return "gui.wmd.tablet.why.t4";
        if (!Reach.within(p, term.getBlockPos(), Balance.TABLET_RANGE)) return "gui.wmd.tablet.why.range";
        if (term.getLoadedBuild() == null) return "gui.wmd.tablet.why.empty";
        if (!term.getLoadedBuild().seeker().equals(MissileBuild.SEEK_LASER)) return "gui.wmd.tablet.why.seeker";
        if (tri == null) return "gui.wmd.tablet.why.tripod";
        if (spot == null) return "gui.wmd.tablet.why.dark";
        return null;
    }

    static void fire(ServerPlayer p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!(s.getItem() instanceof LaserTabletItem)) return;
        String why = check(p, s, new ArrayList<>());
        if (why != null) { p.displayClientMessage(Component.translatable(why), true); return; }
        ServerLevel level = p.serverLevel();
        LaunchTerminalBlockEntity term = (LaunchTerminalBlockEntity) level.getBlockEntity(bound(s, "launcher", level));
        LaserTripodBlockEntity tri = (LaserTripodBlockEntity) level.getBlockEntity(bound(s, "tripod", level));
        LaserSpots.Spot spot = LaserSpots.get(level, tri.spotId());
        Component no = term.remoteLaunch(p.getUUID(), p.getGameProfile().getName(), (int) Math.floor(spot.pos().x), (int) Math.floor(spot.pos().z));
        p.displayClientMessage(no != null ? no : Component.translatable("message.wmd.tablet.fired", (int) Math.floor(spot.pos().x), (int) Math.floor(spot.pos().z)), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = data(stack);
        tooltip.add(Component.translatable("tooltip.wmd.tablet", Balance.TABLET_RANGE).withStyle(ChatFormatting.GRAY));
        if (t.contains("launcher")) tooltip.add(Component.translatable("tooltip.wmd.tablet.launcher", BlockPos.of(t.getLong("launcher")).toShortString()).withStyle(ChatFormatting.DARK_GRAY));
        if (t.contains("tripod")) tooltip.add(Component.translatable("tooltip.wmd.tablet.tripod", BlockPos.of(t.getLong("tripod")).toShortString()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
