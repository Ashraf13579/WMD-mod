package com.ashraf.wmd;

import org.joml.Matrix4f;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientShaderTest {
    private ClientShaderTest() {}

    private static final RenderLevelStageEvent.Stage[] ROWS = {
            RenderLevelStageEvent.Stage.AFTER_SKY, RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS, RenderLevelStageEvent.Stage.AFTER_ENTITIES,
            RenderLevelStageEvent.Stage.AFTER_PARTICLES, RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, RenderLevelStageEvent.Stage.AFTER_LEVEL };
    private static final String[] ROW_NAMES = { "after sky", "after solid blocks", "after entities", "after particles", "after translucent", "after level" };
    private static final String COLS = "ABCDEFGHIJK";
    private static final String[] COL_NAMES = { "A immediate colour (smoke, mushroom)", "B immediate additive (fireball glow)", "C debugQuads",
            "D lightning (glow type)", "E entityTranslucent", "F entityTranslucentEmissive", "G lines", "H particles (top row)", "I block buffer (cutscene land)",
            "J blocks via the solid render type", "K blocks via the entity path" };
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    private static final float R = 1.0F, G = 0.55F, B = 0.15F, A = 0.7F, HALF = 0.9F, STEP = 2.4F;

    private static Vec3 anchor, right;
    private static VertexBuffer cube;

    @SubscribeEvent
    static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmdshadertest")
                .executes(c -> { place(); return 1; })
                .then(Commands.literal("off").executes(c -> { remove(); return 1; })));
    }

    private static void place() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 look = mc.player.getLookAngle(), flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0.0, 0.0, 1.0);
        flat = flat.normalize();
        anchor = mc.player.position().add(flat.scale(8.0));
        right = new Vec3(-flat.z, 0.0, flat.x);
        say("Shader test wall placed: columns A-I = the ways WMD draws, rows = the render stage (top: after sky). Press K (Iris) to switch shaders on / off and compare. /wmdshadertest off removes it.");
        for (String n : COL_NAMES) say("  " + n);
    }

    private static void remove() {
        anchor = null;
        if (cube != null) { cube.close(); cube = null; }
        say("Shader test wall removed.");
    }

    private static void say(String s) { Minecraft.getInstance().gui.getChat().addMessage(Component.literal(s)); }

    private static Vec3 at(int c, int r) {
        return anchor.add(right.scale((c - (COLS.length() - 1) / 2.0) * STEP)).add(0.0, 1.2 + (ROWS.length - 1 - r) * STEP, 0.0);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (anchor == null || level == null || mc.isPaused() || level.getGameTime() % 3 != 0) return;
        Vec3 p = at(COLS.indexOf('H'), 0);
        var rnd = level.random;
        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x + (rnd.nextDouble() - 0.5) * 0.6, p.y - 0.8, p.z + (rnd.nextDouble() - 0.5) * 0.6, 0.0, 0.03, 0.0);
        level.addParticle(ParticleTypes.FLAME, p.x + (rnd.nextDouble() - 0.5) * 0.8, p.y + (rnd.nextDouble() - 0.5) * 0.8, p.z, 0.0, 0.01, 0.0);
        level.addParticle(WmdParticles.TRAIL_SMOKE.get(), p.x + (rnd.nextDouble() - 0.5) * 0.8, p.y, p.z + (rnd.nextDouble() - 0.5) * 0.8, 0.0, 0.0, 0.0);
        level.addParticle(WmdParticles.ROCKET_FLAME.get(), p.x, p.y + 0.4, p.z, 0.0, 0.02, 0.0);
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (anchor == null) return;
        int r = -1;
        for (int i = 0; i < ROWS.length; i++) if (ROWS[i] == event.getStage()) r = i;
        if (r < 0) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();

        ClientBlastGeo.beginFrame();
        for (int k = 0; k < 2; k++) {
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Vec3 c = at(k, r);
            Vec3[] q = corners(c);
            for (int s = 0; s < 2; s++) for (int j = 0; j < 4; j++) {
                Vec3 v = q[s == 0 ? j : 3 - j];
                ClientBlastGeo.v(bb, cam, v.x, v.y, v.z, R, G, B, A);
            }
            ClientBlastGeo.draw(event, bb, true, k == 1);
        }

        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack.Pose pose = ps.last();
        colourPanel(buffers.getBuffer(RenderType.debugQuads()), pose, cam, at(2, r));
        colourPanel(buffers.getBuffer(RenderType.lightning()), pose, cam, at(3, r));
        texPanel(buffers.getBuffer(RenderType.entityTranslucent(WHITE)), pose, cam, at(4, r));
        texPanel(buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)), pose, cam, at(5, r));
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Vec3[] q = corners(at(6, r));
        for (int j = 0; j < 4; j++) line(lines, pose, cam, q[j], q[(j + 1) % 4]);
        line(lines, pose, cam, q[0], q[2]);

        TextureAtlasSprite sp = mc.getBlockRenderer().getBlockModel(Blocks.STONE.defaultBlockState()).getParticleIcon();
        cubeQuads(buffers.getBuffer(RenderType.solid()), pose, cam, at(9, r), sp, false);
        cubeQuads(buffers.getBuffer(RenderType.entitySolid(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)), pose, cam, at(10, r), sp, true);

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            for (int c = 0; c < COLS.length(); c++) label(mc, ps, buffers, cam, at(c, ROWS.length - 1).add(0.0, -1.3, 0.0), String.valueOf(COLS.charAt(c)), 0xFFFFFF);
            for (int i = 0; i < ROWS.length; i++) label(mc, ps, buffers, cam, at(0, i).subtract(right.scale(2.6)), ROW_NAMES[i], 0xFFE070);
        }
        buffers.endBatch();

        drawCube(event, cam, at(8, r));
    }

    private static Vec3[] corners(Vec3 c) {
        Vec3 h = right.scale(HALF);
        return new Vec3[] { c.subtract(h).add(0.0, -HALF, 0.0), c.add(h).add(0.0, -HALF, 0.0), c.add(h).add(0.0, HALF, 0.0), c.subtract(h).add(0.0, HALF, 0.0) };
    }

    private static void cubeQuads(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Vec3 c, TextureAtlasSprite sp, boolean overlay) {
        float s = 0.8F, x = (float) (c.x - cam.x), y = (float) (c.y - cam.y), z = (float) (c.z - cam.z);
        float[][][] faces = {
                { { -s, -s, -s }, { -s, s, -s }, { s, s, -s }, { s, -s, -s }, { 0, 0, -1 } }, { { s, -s, s }, { s, s, s }, { -s, s, s }, { -s, -s, s }, { 0, 0, 1 } },
                { { -s, -s, s }, { -s, s, s }, { -s, s, -s }, { -s, -s, -s }, { -1, 0, 0 } }, { { s, -s, -s }, { s, s, -s }, { s, s, s }, { s, -s, s }, { 1, 0, 0 } },
                { { -s, s, -s }, { -s, s, s }, { s, s, s }, { s, s, -s }, { 0, 1, 0 } }, { { -s, -s, s }, { -s, -s, -s }, { s, -s, -s }, { s, -s, s }, { 0, -1, 0 } } };
        float[][] uv = { { sp.getU0(), sp.getV1() }, { sp.getU0(), sp.getV0() }, { sp.getU1(), sp.getV0() }, { sp.getU1(), sp.getV1() } };
        for (float[][] f : faces) for (int j = 0; j < 4; j++) {
            VertexConsumer v = vc.addVertex(pose, x + f[j][0], y + f[j][1], z + f[j][2]).setColor(-1).setUv(uv[j][0], uv[j][1]);
            if (overlay) v.setOverlay(OverlayTexture.NO_OVERLAY);
            v.setLight(0xF000F0).setNormal(pose, f[4][0], f[4][1], f[4][2]);
        }
    }

    private static void colourPanel(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Vec3 c) {
        Vec3[] q = corners(c);
        for (int s = 0; s < 2; s++) for (int j = 0; j < 4; j++) {
            Vec3 v = q[s == 0 ? j : 3 - j];
            vc.addVertex(pose, (float) (v.x - cam.x), (float) (v.y - cam.y), (float) (v.z - cam.z)).setColor(R, G, B, A);
        }
    }

    private static void texPanel(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Vec3 c) {
        Vec3[] q = corners(c);
        float[][] uv = { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 0, 0 } };
        for (int s = 0; s < 2; s++) for (int j = 0; j < 4; j++) {
            int i = s == 0 ? j : 3 - j;
            Vec3 v = q[i];
            vc.addVertex(pose, (float) (v.x - cam.x), (float) (v.y - cam.y), (float) (v.z - cam.z)).setColor(R, G, B, A).setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0.0F, 1.0F, 0.0F);
        }
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a).normalize();
        vc.addVertex(pose, (float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z)).setColor(R, G, B, 1.0F).setNormal(pose, (float) d.x, (float) d.y, (float) d.z);
        vc.addVertex(pose, (float) (b.x - cam.x), (float) (b.y - cam.y), (float) (b.z - cam.z)).setColor(R, G, B, 1.0F).setNormal(pose, (float) d.x, (float) d.y, (float) d.z);
    }

    private static void label(Minecraft mc, PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vec3 p, String text, int colour) {
        Font font = mc.font;
        ps.pushPose();
        ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
        ps.mulPose(mc.gameRenderer.getMainCamera().rotation());
        ps.scale(-0.04F, -0.04F, 0.04F);
        Matrix4f m = ps.last().pose();
        font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, colour, false, m, buffers, Font.DisplayMode.NORMAL, 0x80000000, 0xF000F0);
        ps.popPose();
    }

    private static void drawCube(RenderLevelStageEvent event, Vec3 cam, Vec3 c) {
        if (cube == null) {
            TextureAtlasSprite sp = Minecraft.getInstance().getBlockRenderer().getBlockModel(Blocks.STONE.defaultBlockState()).getParticleIcon();
            try (ByteBufferBuilder bytes = new ByteBufferBuilder(4096)) {
                BufferBuilder bb = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                float s = 1.6F;
                float[][][] faces = {
                        { { 0, 0, 0 }, { 0, s, 0 }, { s, s, 0 }, { s, 0, 0 }, { 0, 0, -1 } }, { { s, 0, s }, { s, s, s }, { 0, s, s }, { 0, 0, s }, { 0, 0, 1 } },
                        { { 0, 0, s }, { 0, s, s }, { 0, s, 0 }, { 0, 0, 0 }, { -1, 0, 0 } }, { { s, 0, 0 }, { s, s, 0 }, { s, s, s }, { s, 0, s }, { 1, 0, 0 } },
                        { { 0, s, 0 }, { 0, s, s }, { s, s, s }, { s, s, 0 }, { 0, 1, 0 } }, { { 0, 0, s }, { 0, 0, 0 }, { s, 0, 0 }, { s, 0, s }, { 0, -1, 0 } } };
                float[][] uv = { { sp.getU0(), sp.getV1() }, { sp.getU0(), sp.getV0() }, { sp.getU1(), sp.getV0() }, { sp.getU1(), sp.getV1() } };
                for (float[][] f : faces) for (int j = 0; j < 4; j++)
                    bb.addVertex(f[j][0], f[j][1], f[j][2]).setColor(0xFFFFFFFF).setUv(uv[j][0], uv[j][1]).setLight(0xF000F0).setNormal(f[4][0], f[4][1], f[4][2]);
                MeshData md = bb.build();
                if (md == null) return;
                cube = new VertexBuffer(VertexBuffer.Usage.STATIC);
                cube.bind();
                cube.upload(md);
                VertexBuffer.unbind();
            }
        }
        RenderType rt = RenderType.solid();
        Matrix4f mv = new Matrix4f(event.getModelViewMatrix()), proj = new Matrix4f(event.getProjectionMatrix());
        rt.setupRenderState();
        ShaderInstance sh = RenderSystem.getShader();
        if (sh == null) { rt.clearRenderState(); return; }
        sh.setDefaultUniforms(VertexFormat.Mode.QUADS, mv, proj, Minecraft.getInstance().getWindow());
        sh.apply();
        Uniform off = sh.CHUNK_OFFSET;
        if (off != null) { off.set((float) (c.x - 0.8 - cam.x), (float) (c.y - 0.8 - cam.y), (float) (c.z - 0.8 - cam.z)); off.upload(); }
        cube.bind();
        cube.draw();
        if (off != null) off.set(0.0F, 0.0F, 0.0F);
        sh.clear();
        VertexBuffer.unbind();
        rt.clearRenderState();
    }
}
