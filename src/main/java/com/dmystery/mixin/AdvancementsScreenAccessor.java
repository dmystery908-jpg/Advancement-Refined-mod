package com.dmystery.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementsScreen.class)
public interface AdvancementsScreenAccessor {
    @Accessor("selectedTab")
    @Nullable
    AdvancementTab advancementProgress$getSelectedTab();

    @Accessor("lastScreen")
    @Nullable
    Screen advancementProgress$getLastScreen();
}