package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Access {
    private Access() {}

    public static boolean mayUse(Owned o, Player p) {
        return o == null || o.getOwner() == null || o.getOwner().equals(p.getUUID()) || p.hasPermissions(2) || o.security().allows(p.getUUID())
                || Hacks.allows(o, p);
    }

    public static boolean trusts(Owned o, java.util.UUID who) {
        return o == null || o.getOwner() == null || o.getOwner().equals(who) || (who != null && o.security().allows(who));
    }

    public static boolean joins(Owned upstream, Owned module) {
        if (upstream == null || module == null) return true;
        java.util.UUID a = upstream.getOwner(), b = module.getOwner();
        return a == null || b == null || a.equals(b) || upstream.security().trusted().containsKey(b);
    }

    public static boolean mayMine(Owned o, Player p) {
        return o == null || o.getOwner() == null || o.getOwner().equals(p.getUUID()) || p.hasPermissions(2) || raidDevice(o);
    }

    public static boolean raidDevice(Owned o) {
        return o instanceof PortableJammerBlockEntity || o instanceof C4ChargeBlockEntity || o instanceof MineBlockEntity
                || (o instanceof CctvCameraBlockEntity c && c.covert());
    }

    public static boolean mayConfigure(Owned o, Player p) {
        return o == null || o.getOwner() == null || o.getOwner().equals(p.getUUID()) || p.hasPermissions(2);
    }

    public static void deny(Owned o, Player p) { deny(o, p, CcAlarms.USE); }

    public static void deny(Owned o, Player p, int alarmKind) {
        if (alarmKind == CcAlarms.USE && Hacks.intel(o, p)) return;
        p.displayClientMessage(Component.translatable("message.wmd.not_yours", o.getOwnerName().isEmpty() ? "?" : o.getOwnerName()), true);
        CcAlarms.refused(o, p, alarmKind);
    }

    public static Owned ownerAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Owned o) return o;
        if (be instanceof MachinePartBlockEntity part && part.getController() != null && level.getBlockEntity(part.getController()) instanceof Owned o) return o;
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof RadarPartBlock) {
            BlockPos c = RadarStructure.findController(level, pos);
            if (c != null && level.getBlockEntity(c) instanceof Owned o) return o;
        }
        if (st.getBlock() instanceof ReactorPartBlock && st.getValue(ReactorPartBlock.FORMED)) {
            BlockPos c = ReactorStructure.findController(level, pos, true);
            if (c != null && level.getBlockEntity(c) instanceof Owned o) return o;
        }
        if (st.getBlock() instanceof PadPlateBlock) {
            BlockPos t = PadStructure.findOwner(level, pos);
            if (t != null && level.getBlockEntity(t) instanceof Owned o) return o;
        }
        return null;
    }

    @SubscribeEvent
    public static void breakSpeed(PlayerEvent.BreakSpeed event) {
        Player p = event.getEntity();
        if (event.getPosition().isEmpty()) return;
        Owned o = ownerAt(p.level(), event.getPosition().get());
        if (o != null && !mayMine(o, p)) event.setNewSpeed(0.0F);
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        Player p = event.getPlayer();
        if (p == null || !(event.getLevel() instanceof Level level)) return;

        if (event.getState().getBlock() instanceof DepositOreBlock && p instanceof net.neoforged.neoforge.common.util.FakePlayer) { event.setCanceled(true); return; }
        Owned o = ownerAt(level, event.getPos());
        if (o != null && !mayMine(o, p)) {
            event.setCanceled(true);
            deny(o, p, CcAlarms.BREAK);
        } else if (o != null) CcAlarms.minedByOther(o, p);
    }
}
