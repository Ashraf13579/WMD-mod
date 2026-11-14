package com.ashraf.wmd;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

public final class GunnerBlocks {

    private GunnerBlocks() {}

    public static final DeferredBlock<GunTurretBlock> GUN_TURRET = Wmd.BLOCKS.registerBlock("gun_turret", GunTurretBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> GUN_TURRET_ITEM = Wmd.ITEMS.registerSimpleBlockItem("gun_turret", GUN_TURRET);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GunTurretBlockEntity>> GUN_TURRET_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("gun_turret", () -> BlockEntityType.Builder.of(GunTurretBlockEntity::new, GUN_TURRET.get()).build(null));

    public static final DeferredBlock<GunnerStationBlock> GUNNER_STATION = Wmd.BLOCKS.registerBlock("gunner_station", GunnerStationBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> GUNNER_STATION_ITEM = Wmd.ITEMS.registerSimpleBlockItem("gunner_station", GUNNER_STATION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GunnerStationBlockEntity>> GUNNER_STATION_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("gunner_station", () -> BlockEntityType.Builder.of(GunnerStationBlockEntity::new, GUNNER_STATION.get()).build(null));

    public static final DeferredBlock<HeavyGunBlock> HEAVY_GUN = Wmd.BLOCKS.registerBlock("heavy_gun", HeavyGunBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 14.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> HEAVY_GUN_ITEM = Wmd.ITEMS.registerSimpleBlockItem("heavy_gun", HEAVY_GUN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeavyGunBlockEntity>> HEAVY_GUN_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("heavy_gun", () -> BlockEntityType.Builder.of(HeavyGunBlockEntity::new, HEAVY_GUN.get()).build(null));

    public static final DeferredItem<ArtShellItem> SHELL_GUNSHIP = Wmd.ITEMS.registerItem("shell_howitzer_gunship", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.GUNSHIP_105),
            new net.minecraft.world.item.Item.Properties().stacksTo(16));

    public static final DeferredHolder<EntityType<?>, EntityType<GunnerSeatEntity>> GUNNER_SEAT = Wmd.ENTITY_TYPES.register("gunner_seat",
            () -> EntityType.Builder.<GunnerSeatEntity>of(GunnerSeatEntity::new, MobCategory.MISC).sized(0.5F, 0.3F).clientTrackingRange(10).build("gunner_seat"));

    static void init() {}

    static void tab(CreativeModeTab.Output output) {
        output.accept(GUNNER_STATION_ITEM.get());
        output.accept(GUN_TURRET_ITEM.get());
        output.accept(HEAVY_GUN_ITEM.get());
        output.accept(SHELL_GUNSHIP.get());
    }
}
