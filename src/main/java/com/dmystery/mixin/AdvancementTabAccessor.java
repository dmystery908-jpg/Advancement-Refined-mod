package com.dmystery.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(AdvancementTab.class)
public interface AdvancementTabAccessor {
    @Accessor("widgets")
    Map<AdvancementHolder, AdvancementWidget> advancementProgress$getWidgets();

    @Accessor("scrollX")
    double advancementProgress$getScrollX();

    @Accessor("scrollX")
    void advancementProgress$setScrollX(double x);

    @Accessor("scrollY")
    double advancementProgress$getScrollY();

    @Accessor("scrollY")
    void advancementProgress$setScrollY(double y);
}
