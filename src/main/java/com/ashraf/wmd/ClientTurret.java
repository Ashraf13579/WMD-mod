package com.ashraf.wmd;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientTurret {

    private ClientTurret() {}

    private static final ResourceLocation LENS = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "textures/gui/lens_edge.png");
    private record Bolt(Vec3 from, Vec3 dir, double t0) {}
    private static final Map<Long, Bolt> BOLTS = new HashMap<>();
    private static boolean sent;

    static boolean seated() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() instanceof TurretSeatEntity;
    }

    static boolean gunnerOf(net.minecraft.core.BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (!seated()) return false;
        Vec3 e = mc.player.getVehicle().position();
        return Math.abs(e.x - (pos.getX() + 0.5)) < 1.0 && Math.abs(e.z - (pos.getZ() + 0.5)) < 1.0 && Math.abs(e.y - pos.getY()) < 6.0;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean down = false;
        if (seated() && mc.screen == null) {
            down = mc.options.keyAttack.isDown();
            while (mc.options.keyAttack.consumeClick()) { }
        }
        if (down != sent) { sent = down; PacketDistributor.sendToServer(new Turret.Trigger(down)); }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (CustomPacketPayload p; (p = Turret.PENDING.poll()) != null; ) {
            if (p instanceof Turret.Shot s) BOLTS.put(s.id(), new Bolt(new Vec3(s.x(), s.y(), s.z()), new Vec3(s.dx(), s.dy(), s.dz()), mc.level.getGameTime()));
            else if (p instanceof Turret.Hit h) BOLTS.remove(h.id());
        }
        if (BOLTS.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        ClientBlastGeo.beginFrame();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Iterator<Bolt> it = BOLTS.values().iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            double el = now - b.t0();
            if (el * Balance.TURRET_BOLT_SPEED > Balance.TURRET_RANGE || el < -1.0) { it.remove(); continue; }
            Vec3 head = b.from().add(b.dir().scale(Math.max(0.0, el) * Balance.TURRET_BOLT_SPEED)), tail = head.subtract(b.dir().scale(Math.min(5.0, Math.max(0.5, el * 4.0))));
            ClientBlastGeo.streak(bb, cam, tail.x, tail.y, tail.z, head.x, head.y, head.z, 0.22, 0.55F, 0.85F, 1.0F, 0.1F, 1.0F);
            ClientBlastGeo.streak(bb, cam, tail.x, tail.y, tail.z, head.x, head.y, head.z, 0.08, 0.95F, 0.98F, 1.0F, 0.3F, 1.0F);
        }
        ClientBlastGeo.draw(event, bb, true, true);
    }

    @SubscribeEvent
    static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!seated() || !mc.options.getCameraType().isFirstPerson()) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2, cy = h / 2;
        RenderSystem.enableBlend();
        g.blit(LENS, 0, 0, w, h, 0.0F, 0.0F, 256, 256, 256, 256);
        RenderSystem.disableBlend();
        int c = 0xC09FD0FF;
        g.fill(cx - 12, cy - 1, cx - 5, cy, c); g.fill(cx + 5, cy - 1, cx + 12, cy, c);
        g.fill(cx - 1, cy - 12, cx, cy - 5, c); g.fill(cx - 1, cy + 5, cx, cy + 12, c);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        Component hint = Component.translatable("gui.wmd.turret.hint");
        g.drawString(mc.font, hint, cx - mc.font.width(hint) / 2, h - 52, 0xB0FFFFFF, false);
    }
}
