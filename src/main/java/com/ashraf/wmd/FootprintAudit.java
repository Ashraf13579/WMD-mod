package com.ashraf.wmd;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

@EventBusSubscriber(modid = Wmd.MODID, bus = EventBusSubscriber.Bus.MOD)
final class FootprintAudit {

    private FootprintAudit() {}

    @SubscribeEvent
    static void onSetup(FMLCommonSetupEvent event) {
        if (FMLEnvironment.production) return;
        event.enqueueWork(FootprintAudit::write);
    }

    private static void write() {
        try {
            JsonObject out = new JsonObject();
            for (Block b : BuiltInRegistries.BLOCK) {
                if (!(b instanceof MachineBlock mb)) continue;
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
                if (!Wmd.MODID.equals(id.getNamespace())) continue;
                List<BlockPos> fp;
                try { fp = mb.footprint(); } catch (RuntimeException e) { continue; }
                JsonObject o = new JsonObject();
                o.addProperty("mesh", MachineShapes.meshOf(id.getPath()));
                JsonArray cells = new JsonArray();
                for (BlockPos p : fp) cells.add(p.getX() + "," + p.getY() + "," + p.getZ());
                o.add("footprint", cells);
                out.add(id.getPath(), o);
            }
            Path f = FMLPaths.GAMEDIR.get().resolve("wmd_footprint_audit.json");
            Files.writeString(f, out.toString(), StandardCharsets.UTF_8);
            Wmd.LOGGER.info("[footprint] wrote {} machine footprints to {}", out.size(), f);
        } catch (Exception e) {
            Wmd.LOGGER.warn("[footprint] audit failed: {}", e.toString());
        }
    }
}
