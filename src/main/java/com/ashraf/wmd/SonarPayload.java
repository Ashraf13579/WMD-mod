package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SonarPayload(BlockPos pos, int action, int value) implements CustomPacketPayload {

    public static final int FREQ = 0, PING = 1, HUNT = 2;
    public static final int REEL = 4;
    public static final int SURVEY = 3;

    public static final Type<SonarPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sonar"));

    public static final StreamCodec<FriendlyByteBuf, SonarPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SonarPayload::pos,
            ByteBufCodecs.VAR_INT, SonarPayload::action,
            ByteBufCodecs.VAR_INT, SonarPayload::value,
            SonarPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SonarPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof net.minecraft.world.level.block.entity.BlockEntity be)
                    || !(be instanceof Owned o) || !be.getBlockPos().equals(p.pos()) || be.isRemoved() || !Access.mayUse(o, context.player())) return;
            if (be instanceof SonarSensorBlockEntity s) {
                if (p.action() == FREQ) s.setFrequency(p.value());
                else if (p.action() == REEL && s instanceof TowedArrayWinchBlockEntity w) w.setStowed(p.value() != 0);
                else if (p.action() == PING) {
                    int r = s.ping(context.player());
                    if (r > 1) context.player().displayClientMessage(Component.translatable("gui.wmd.sonar.ping_fail." + r), true);
                }
            } else if (be instanceof SonarConsoleBlockEntity c) {
                if (p.action() == FREQ) c.setFrequency(p.value());
                else if (p.action() == PING) context.player().displayClientMessage(Component.translatable("gui.wmd.sonar.pinged", c.pingAll(context.player())), true);
                else if (p.action() == SURVEY) {
                    int n = c.surveyAll(context.player());
                    context.player().displayClientMessage(n > 0 ? Component.translatable("gui.wmd.sonar.surveying", n, Balance.SURVEY_RANGE)
                            : c.surveyFail() != null ? c.surveyFail() : Component.translatable("gui.wmd.sonar.survey_none"), true);
                }
                else if (p.action() == HUNT) {
                    c.setHunting(p.value() != 0);
                    context.player().displayClientMessage(Component.translatable(c.hunting() ? "gui.wmd.sonar.hunt_on" : "gui.wmd.sonar.hunt_off"), true);
                }
            }
        });
    }
}
