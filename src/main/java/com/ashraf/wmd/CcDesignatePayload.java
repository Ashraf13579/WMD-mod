package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CcDesignatePayload(int x, int y, int z) implements CustomPacketPayload {

    public static final Type<CcDesignatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_designate"));

    public static final StreamCodec<FriendlyByteBuf, CcDesignatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeInt(p.x()); buf.writeInt(p.y()); buf.writeInt(p.z()); },
            buf -> new CcDesignatePayload(buf.readInt(), buf.readInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CcDesignatePayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            ItemStack held = sp.getMainHandItem().getItem() instanceof TargetDesignatorItem ? sp.getMainHandItem()
                    : sp.getOffhandItem().getItem() instanceof TargetDesignatorItem ? sp.getOffhandItem() : ItemStack.EMPTY;
            if (held.isEmpty() || sp.getCooldowns().isOnCooldown(held.getItem())) return;
            Vec3 at = SableBridge.toWorld(sp.level(), new Vec3(p.x() + 0.5, p.y() + 1.0, p.z() + 0.5));
            if (sp.getEyePosition().distanceTo(at) > TargetDesignatorItem.RANGE + 4.0) return;
            sp.getCooldowns().addCooldown(held.getItem(), 20);
            List<CcBuildingBlockEntity> all = CommandCenter.activeBuildings(sp.getUUID());
            boolean gps = false;
            for (CcBuildingBlockEntity b : all) if (b.online(CcModule.GPS_CONSOLE)) gps = true;
            if (all.isEmpty() || !gps) { sp.displayClientMessage(Component.translatable("message.wmd.cc.des.need_gps").withStyle(ChatFormatting.RED), true); return; }
            if (!CcGps.navControlled(sp.server, sp.getUUID())) { sp.displayClientMessage(Component.translatable("message.wmd.cc.des.need_nav").withStyle(ChatFormatting.RED), true); return; }
            CcTarget t = new CcTarget((int) Math.floor(at.x), (int) Math.floor(at.z), "gui.wmd.cc.des.observer", CcTarget.SRC_DESIGNATOR, sp.getGameProfile().getName(), System.currentTimeMillis());
            if (CcTarget.designate(sp, new ArrayList<>(all), t) > 0) CcTarget.beam(sp, at.x, at.y, at.z);
        });
    }
}
