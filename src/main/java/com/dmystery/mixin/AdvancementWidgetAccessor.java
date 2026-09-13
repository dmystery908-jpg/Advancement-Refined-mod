package com.dmystery.mixin;

import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementWidget.class)
public interface AdvancementWidgetAccessor {
    @Accessor("progress")
    AdvancementProgress advancementProgress$getProgress();

    @Accessor("advancementNode")
    AdvancementNode advancementProgress$getNode();

    @Accessor("display")
    DisplayInfo advancementProgress$getDisplay();
}
