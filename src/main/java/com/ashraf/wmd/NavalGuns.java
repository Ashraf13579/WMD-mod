package com.ashraf.wmd;

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

public final class NavalGuns {

    private NavalGuns() {}

    record Spec(boolean xl, String id, int half, int height, int barrels, int rack, int charges, int reload, int heavyReload, int hoist, int feLay, int feShot,
                double eyeSpeed, double eyeMin, double eyeRange, double conRange, double trainMax, double elevUp, double elevDown, double trainRate,
                double elevRate, double align, int craneStack,

                double trunY, double trunZ, double[] barrelX, double muzzleZ, double recoil, double lensX, double lensY, double lensZ, double gunhouseR) {}

    static final Spec DECK = new Spec(false, "deck_gun", 1, 3, 1, Balance.NGUN_RACK, 0, Balance.NGUN_RELOAD_TICKS, Balance.NGUN_RELOAD_TICKS, Balance.NGUN_HOIST_TICKS,
            Balance.NGUN_FE_LAY, Balance.NGUN_FE_SHOT, Balance.NGUN_EYE_SPEED, Balance.NGUN_EYE_MIN, Balance.NGUN_EYE_RANGE, Balance.NGUN_CON_RANGE,
            Balance.NGUN_TRAIN_MAX, Balance.NGUN_ELEV_UP, Balance.NGUN_ELEV_DOWN, Balance.NGUN_TRAIN_RATE, Balance.NGUN_ELEV_RATE, Balance.NGUN_ALIGN_DEG,
            Balance.NGUN_CRANE_STACK,
            34.0, -8.0, new double[] { 0.0 }, -67.0, 7.0, 7.5, 47.0, -6.6, 21.7);
    static final Spec TURRET = new Spec(true, "battleship_turret", 2, 5, 3, Balance.XGUN_RACK, Balance.XGUN_CHARGES, Balance.XGUN_RELOAD_TICKS, Balance.XGUN_HEAVY_RELOAD_TICKS,
            Balance.XGUN_HOIST_TICKS, Balance.XGUN_FE_LAY, Balance.XGUN_FE_SALVO, Balance.XGUN_EYE_SPEED, Balance.XGUN_EYE_MIN, Balance.XGUN_EYE_RANGE, Balance.XGUN_CON_RANGE,
            Balance.XGUN_TRAIN_MAX, Balance.XGUN_ELEV_UP, Balance.XGUN_ELEV_DOWN, Balance.XGUN_TRAIN_RATE, Balance.XGUN_ELEV_RATE, Balance.XGUN_ALIGN_DEG,
            Balance.XGUN_CRANE_STACK,
            56.0, -20.0, new double[] { -13.0, 0.0, 13.0 }, -116.0, 13.0, 30.5, 61.0, 3.6, 37.2);

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion()
                .lightLevel(UnitBlock.light(2));
    }

    public static final DeferredBlock<NavalGunBlock.Deck> DECK_GUN = Wmd.BLOCKS.registerBlock("deck_gun", NavalGunBlock.Deck::new, props());
    public static final DeferredItem<BlockItem> DECK_GUN_ITEM = Wmd.ITEMS.registerSimpleBlockItem("deck_gun", DECK_GUN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NavalGunBlockEntity>> DECK_GUN_BE = Wmd.BLOCK_ENTITY_TYPES.register("deck_gun",
            () -> BlockEntityType.Builder.of((p, s) -> new NavalGunBlockEntity(NavalGuns.DECK_GUN_BE.get(), p, s, DECK), DECK_GUN.get()).build(null));

    public static final DeferredBlock<NavalGunBlock.Turret> BATTLESHIP_TURRET = Wmd.BLOCKS.registerBlock("battleship_turret", NavalGunBlock.Turret::new, props());
    public static final DeferredItem<BlockItem> BATTLESHIP_TURRET_ITEM = Wmd.ITEMS.registerSimpleBlockItem("battleship_turret", BATTLESHIP_TURRET);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NavalGunBlockEntity>> BATTLESHIP_TURRET_BE = Wmd.BLOCK_ENTITY_TYPES.register("battleship_turret",
            () -> BlockEntityType.Builder.of((p, s) -> new NavalGunBlockEntity(NavalGuns.BATTLESHIP_TURRET_BE.get(), p, s, TURRET), BATTLESHIP_TURRET.get()).build(null));

    static void init() {}

    static void navyTab(CreativeModeTab.Output output) {
        output.accept(DECK_GUN_ITEM.get());
        output.accept(BATTLESHIP_TURRET_ITEM.get());
    }
}
