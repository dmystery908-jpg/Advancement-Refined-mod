package com.dmystery.client;

import com.dmystery.mixin.AdvancementsScreenAccessor;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

public class AdvancementProgressConfigScreen extends Screen {
    private final Screen parent;
    private final AdvancementProgressConfig config;

    public AdvancementProgressConfigScreen(Screen parent) {
        super(Component.translatable("advancements_refined.config.title"));
        this.parent = parent;
        this.config = AdvancementProgressConfig.getInstance();
    }

    @Override
    protected void init() {
        clearWidgets();

        int colW = 150;
        int gap = 10;
        int totalW = colW * 2 + gap;
        int leftX = (this.width - totalW) / 2;
        int rightX = leftX + colW + gap;
        int startY = 42;
        int rowSpacing = 24;

        // Row 1: Global Bar | Tab Badges
        addRenderableWidget(
            CycleButton.onOffBuilder(config.showGlobalBar)
                .create(leftX, startY, colW, 20, Component.translatable("advancements_refined.config.show_global_bar"), (btn, val) -> config.showGlobalBar = val)
        );
        addRenderableWidget(
            CycleButton.onOffBuilder(config.showTabBadges)
                .create(rightX, startY, colW, 20, Component.translatable("advancements_refined.config.show_tab_badges"), (btn, val) -> config.showTabBadges = val)
        );

        // Row 2: Tooltip Hints | Auto Unpin
        addRenderableWidget(
            CycleButton.onOffBuilder(config.showTooltipHints)
                .create(leftX, startY + rowSpacing, colW, 20, Component.translatable("advancements_refined.config.show_tooltip_hints"), (btn, val) -> config.showTooltipHints = val)
        );
        addRenderableWidget(
            CycleButton.onOffBuilder(config.autoUnpinOnComplete)
                .create(rightX, startY + rowSpacing, colW, 20, Component.translatable("advancements_refined.config.auto_unpin"), (btn, val) -> config.autoUnpinOnComplete = val)
        );

        // Row 3: HUD Enabled | HUD Position
        addRenderableWidget(
            CycleButton.onOffBuilder(config.hudEnabled)
                .create(leftX, startY + rowSpacing * 2, colW, 20, Component.translatable("advancements_refined.config.hud_enabled"), (btn, val) -> config.hudEnabled = val)
        );
        addRenderableWidget(
            CycleButton.builder(AdvancementProgressConfig.HudPosition::getDisplayName, config.hudPosition)
                .withValues(AdvancementProgressConfig.HudPosition.values())
                .create(rightX, startY + rowSpacing * 2, colW, 20, Component.translatable("advancements_refined.config.hud_position"), (btn, val) -> config.hudPosition = val)
        );

        // Row 4: Max Pins | Tree Zoom
        addRenderableWidget(
            CycleButton.builder(v -> Component.literal(String.valueOf(v)), config.maxPins)
                .withValues(1, 2, 3, 4, 5)
                .create(leftX, startY + rowSpacing * 3, colW, 20, Component.translatable("advancements_refined.config.max_pins"), (btn, val) -> config.maxPins = val)
        );
        addRenderableWidget(
            new AbstractSliderButton(rightX, startY + rowSpacing * 3, colW, 20, Component.empty(), (config.treeZoom - 0.5f) / 0.5f) {
                {
                    updateMessage();
                }

                @Override
                protected void updateMessage() {
                    setMessage(Component.translatable("advancements_refined.config.tree_zoom", String.format(Locale.ROOT, "%.0f%%", config.treeZoom * 100.0f)));
                }

                @Override
                protected void applyValue() {
                    config.treeZoom = Mth.clamp(0.5f + (float) this.value * 0.5f, 0.5f, 1.0f);
                }
            }
        );

        // Footer: Reset | Done
        int bottomY = Math.max(startY + rowSpacing * 4 + 16, this.height - 30);
        addRenderableWidget(
            Button.builder(Component.translatable("advancements_refined.config.reset"), btn -> {
                config.resetDefaults();
                rebuildWidgets();
            }).bounds(leftX, bottomY, colW, 20).build()
        );
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE, btn -> onClose())
                .bounds(rightX, bottomY, colW, 20)
                .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        config.save();
        if (this.minecraft != null) {
            if (this.parent instanceof AdvancementsScreen advScreen) {
                if (this.minecraft.player != null && this.minecraft.player.connection != null) {
                    AdvancementHolder currentTabHolder = null;
                    Screen lastScreen = null;
                    if (advScreen instanceof AdvancementsScreenAccessor accessor) {
                        AdvancementTab tab = accessor.advancementProgress$getSelectedTab();
                        if (tab != null) {
                            currentTabHolder = tab.getRootNode().holder();
                        }
                        lastScreen = accessor.advancementProgress$getLastScreen();
                    }
                    ClientAdvancements adv = this.minecraft.player.connection.getAdvancements();
                    AdvancementsScreen freshScreen = new AdvancementsScreen(adv, lastScreen);
                    if (currentTabHolder != null) {
                        adv.setSelectedTab(currentTabHolder, true);
                    }
                    this.minecraft.setScreenAndShow(freshScreen);
                    return;
                }
            }
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
