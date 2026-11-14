package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NavLightPayloads {

    private NavLightPayloads() {}

    private static final Map<UUID, int[]> LAST = Statics.server(new HashMap<>());

    private static ResourceLocation id(String n) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, n); }

    public record Apply(BlockPos light, int func, int spot, int nose, int power, int bright) implements CustomPacketPayload {
        public static final Type<Apply> TYPE = new Type<>(id("navlight_set"));
        public static final StreamCodec<FriendlyByteBuf, Apply> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.light()); b.writeByte(p.func()); b.writeByte(p.spot()); b.writeByte(p.nose()); b.writeByte(p.power()); b.writeByte(p.bright()); },
                b -> new Apply(b.readBlockPos(), b.readByte(), b.readByte(), b.readByte(), b.readByte(), b.readByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Open(BlockPos light) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(id("navlight_open"));
        public static final StreamCodec<FriendlyByteBuf, Open> CODEC = StreamCodec.of((b, p) -> b.writeBlockPos(p.light()), b -> new Open(b.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static boolean mayChange(ServerPlayer sp, BlockPos pos) {
        if (!AirGate.on() || !Reach.usable(sp, pos, Balance.NL_REACH) || !(sp.level() instanceof ServerLevel sl)) return false;
        UUID ship = SableBridge.shipIdAt(sl, pos);
        boolean ok;
        if (ship != null) {
            UUID owner = Crafts.owner(sl, ship);
            ok = owner == null || owner.equals(sp.getUUID());
        } else ok = ClaimGuard.mayPlaceQuiet(sl, sp, pos);
        if (!ok) sp.displayClientMessage(Component.translatable("message.wmd.navlight.denied"), true);
        return ok;
    }

    static void handleSet(ServerPlayer sp, Apply p) {
        if (sp == null || AirframeRecords.tooSoon(LAST, sp, 1, Balance.NL_PRESS_GAP)) return;
        NavLightBlock.Func[] fs = NavLightBlock.Func.values();
        NavLightBlock.Spot[] ss = NavLightBlock.Spot.values();
        if (p.func() < 0 || p.func() >= fs.length || p.spot() < 0 || p.spot() >= ss.length || p.nose() < 0 || p.nose() > 5
                || p.power() < 0 || p.power() > 2 || p.bright() < 0 || p.bright() > 2) return;
        if (!mayChange(sp, p.light())) return;
        ServerLevel sl = (ServerLevel) sp.level();
        BlockState st = sl.getBlockState(p.light());
        if (!(st.getBlock() instanceof NavLightBlock) || !(sl.getBlockEntity(p.light()) instanceof NavLightBlockEntity be) || be.isRemoved()) return;
        Direction nose = Direction.from3DDataValue(p.nose());
        if (nose.getAxis() == st.getValue(NavLightBlock.ATTACH).getAxis()) return;
        BlockState n = st.setValue(NavLightBlock.FUNC, fs[p.func()]).setValue(NavLightBlock.SPOT, ss[p.spot()]).setValue(NavLightBlock.NOSE, nose);
        if (n != st) sl.setBlock(p.light(), n, 3);
        be.set(p.power(), p.bright());
        sl.playSound(null, p.light(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, Balance.SND_CLICK, 1.4F);
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToServer(Apply.TYPE, Apply.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) handleSet(sp, p); }));
            r.playToClient(Open.TYPE, Open.CODEC, (p, ctx) -> ctx.enqueueWork(() -> NavLightScreen.open(p.light())));
        }
    }
}
