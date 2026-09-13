package com.dmystery.mixin;

import com.dmystery.client.AdvancementScreenLayout;
import net.minecraft.client.gui.screens.advancements.AdvancementTabType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AdvancementTabType.class)
public class AdvancementTabTypeMixin {
    @Inject(method = "getX", at = @At("HEAD"), cancellable = true)
    private void onGetX(int index, CallbackInfoReturnable<Integer> cir) {
        AdvancementTabType type = (AdvancementTabType) (Object) this;
        if (type == AdvancementTabType.RIGHT) {
            cir.setReturnValue(AdvancementScreenLayout.getWindowWidth() - 4);
        }
    }

    @Inject(method = "getY", at = @At("HEAD"), cancellable = true)
    private void onGetY(int index, CallbackInfoReturnable<Integer> cir) {
        AdvancementTabType type = (AdvancementTabType) (Object) this;
        if (type == AdvancementTabType.BELOW) {
            cir.setReturnValue(AdvancementScreenLayout.getWindowHeight() - 4);
        }
    }
}
