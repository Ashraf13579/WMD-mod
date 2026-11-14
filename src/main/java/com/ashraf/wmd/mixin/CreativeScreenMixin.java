package com.ashraf.wmd.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ashraf.wmd.ClientCreativeSections;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenMixin extends EffectRenderingInventoryScreen<CreativeModeInventoryScreen.ItemPickerMenu> {

    @Shadow private static CreativeModeTab selectedTab;
    @Shadow private float scrollOffs;

    private CreativeScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu, Inventory inv, Component title) { super(menu, inv, title); }

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void wmd$sectionsOnSelect(CreativeModeTab tab, CallbackInfo ci) { wmd$layout(); }

    @Inject(method = "refreshCurrentTabContents", at = @At("TAIL"))
    private void wmd$sectionsOnRefresh(Collection<ItemStack> items, CallbackInfo ci) { wmd$layout(); }

    private void wmd$layout() {
        if (ClientCreativeSections.layout(selectedTab, this.menu.items)) this.menu.scrollTo(this.scrollOffs);
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen;renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void wmd$banners(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        ClientCreativeSections.draw(g, this.leftPos, this.topPos, ((ItemPickerMenuAccessor) this.menu).wmd$rowIndex(this.scrollOffs));
    }
}
