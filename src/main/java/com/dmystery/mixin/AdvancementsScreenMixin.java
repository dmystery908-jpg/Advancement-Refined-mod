package com.dmystery.mixin;

import com.dmystery.AdvancementProgressClient;
import com.dmystery.client.AdvancementCache;
import com.dmystery.client.AdvancementDataLoader;
import com.dmystery.client.AdvancementProgressConfigScreen;
import com.dmystery.client.AdvancementScreenLayout;
import com.dmystery.client.HudPinManager;
import com.dmystery.client.InspectorPanel;
import com.dmystery.client.PinnedChip;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementTabType;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsScreenMixin extends Screen {
    @Shadow @Final private static Identifier WINDOW_LOCATION;
    @Shadow @Final private static Component TITLE;
    @Shadow @Final private static Component NO_ADVANCEMENTS_LABEL;
    @Shadow @Final private static Component VERY_SAD_LABEL;
    @Shadow @Final private HeaderAndFooterLayout layout;
    @Shadow @Final private ClientAdvancements advancements;
    @Shadow @Final private Map<AdvancementHolder, AdvancementTab> tabs;
    @Shadow @Nullable private AdvancementTab selectedTab;
    @Unique
    private int leftPos;
    @Unique
    private int topPos;

    @Unique
    private final InspectorPanel advancementProgress$inspector = new InspectorPanel();

    @Unique
    private final List<PinnedChip> advancementProgress$activeChips = new ArrayList<>();
    @Unique
    private int advancementProgress$clearAllX, advancementProgress$clearAllY, advancementProgress$clearAllW, advancementProgress$clearAllH;
    @Unique
    private boolean advancementProgress$hasClearAll = false;
    @Unique
    private int advancementProgress$gearX, advancementProgress$gearY, advancementProgress$gearW = 18, advancementProgress$gearH = 14;

    @Unique
    @Nullable
    private AdvancementWidget advancementProgress$findWidgetAt(AdvancementTab tab, double mouseX, double mouseY) {
        if (!(tab instanceof AdvancementTabAccessor tabAccessor)) {
            return null;
        }
        int insideX = (int) Math.round(mouseX - (this.leftPos + 9));
        int insideY = (int) Math.round(mouseY - (this.topPos + 18));
        int inW = AdvancementScreenLayout.getInsideWidth();
        int inH = AdvancementScreenLayout.getInsideHeight();
        if (insideX < 0 || insideX >= inW || insideY < 0 || insideY >= inH) {
            return null;
        }
        float scale = AdvancementScreenLayout.getZoom();
        int treeMouseX = (int) Math.round(insideX / scale);
        int treeMouseY = (int) Math.round(insideY / scale);
        int sX = Mth.floor(tabAccessor.advancementProgress$getScrollX());
        int sY = Mth.floor(tabAccessor.advancementProgress$getScrollY());
        Map<AdvancementHolder, AdvancementWidget> widgets = tabAccessor.advancementProgress$getWidgets();
        if (widgets != null) {
            for (AdvancementWidget widget : widgets.values()) {
                if (widget.isMouseOver(sX, sY, treeMouseX, treeMouseY)) {
                    return widget;
                }
            }
        }
        return null;
    }

    @Unique
    @Nullable
    private AdvancementTab advancementProgress$findTabForAdvancement(AdvancementHolder holder) {
        if (holder == null) return null;
        for (AdvancementTab tab : this.tabs.values()) {
            if (tab instanceof AdvancementTabAccessor tabAccessor) {
                Map<AdvancementHolder, AdvancementWidget> widgets = tabAccessor.advancementProgress$getWidgets();
                if (widgets != null && widgets.containsKey(holder)) {
                    return tab;
                }
            }
        }
        return null;
    }

    protected AdvancementsScreenMixin(Component title) {
        super(title);
    }

    /**
     * Suppress the vanilla "Advancements" title from the header layout.
     * This prevents the vanilla title text from peeking out from underneath our top progress bar.
     */
    @Redirect(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addTitleHeader(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/Font;)V"
        )
    )
    private void removeVanillaTitleHeader(HeaderAndFooterLayout layout, Component title, Font font) {
        // Do not add vanilla title - our top progress bar serves as the clean header!
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void onInitHead(CallbackInfo ci) {
        AdvancementDataLoader.ensureAdvancementsLoaded(this.advancements);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        advancementProgress$inspector.close();
        if (this.tabs.isEmpty()) {
            AdvancementCache.clear();
        } else {
            AdvancementCache.markDirty();
            if (this.selectedTab == null) {
                AdvancementTab firstTab = this.tabs.values().iterator().next();
                this.selectedTab = firstTab;
                this.advancements.setSelectedTab(firstTab.getRootNode().holder(), true);
            }
        }
    }

    @Inject(method = "repositionElements", at = @At("HEAD"), cancellable = true)
    private void onRepositionElements(CallbackInfo ci) {
        boolean hasBottomTabs = false;
        for (AdvancementTab tab : this.tabs.values()) {
            if (tab.getType() == AdvancementTabType.BELOW) {
                hasBottomTabs = true;
                break;
            }
        }
        AdvancementScreenLayout.update(this.width, this.height, hasBottomTabs);
        int winW = AdvancementScreenLayout.getWindowWidth();
        this.leftPos = Math.max(8, (this.width - winW) / 2);
        this.topPos = 48;
        this.advancementProgress$gearW = 18;
        this.advancementProgress$gearH = 14;
        this.advancementProgress$gearX = this.leftPos + winW - this.advancementProgress$gearW;
        this.advancementProgress$gearY = 4;
        this.layout.arrangeElements();
        ci.cancel();
    }

    @Inject(method = "extractWindow", at = @At("HEAD"), cancellable = true)
    private void onExtractWindow(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY, CallbackInfo ci) {
        int winW = AdvancementScreenLayout.getWindowWidth();
        int winH = AdvancementScreenLayout.getWindowHeight();

        advancementProgress$renderWindowFrame(graphics, this.leftPos, this.topPos, winW, winH);

        if (this.tabs.size() > 1) {
            for (AdvancementTab tab : this.tabs.values()) {
                tab.extractTab(graphics, this.leftPos, this.topPos, mouseX, mouseY, tab == this.selectedTab);
            }
            for (AdvancementTab tab : this.tabs.values()) {
                tab.extractIcon(graphics, this.leftPos, this.topPos);
            }
        }

        if (this.selectedTab != null) {
            graphics.text(this.font, this.selectedTab.getTitle(), this.leftPos + 8, this.topPos + 6, 0xFF3F3F3F, false);
        } else {
            graphics.text(this.font, TITLE, this.leftPos + 8, this.topPos + 6, 0xFF3F3F3F, false);
        }

        ci.cancel();
    }

    @Unique
    private void advancementProgress$renderWindowFrame(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        // 9-slice rendering for the enlarged window frame
        // 1. Four corners
        graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, x, y, 0.0F, 0.0F, 9, 18, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, x + w - 9, y, 243.0F, 0.0F, 9, 18, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, x, y + h - 9, 0.0F, 131.0F, 9, 9, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, x + w - 9, y + h - 9, 243.0F, 131.0F, 9, 9, 256, 256);

        // 2. Top edge
        for (int currX = x + 9; currX < x + w - 9; ) {
            int segW = Math.min(200, x + w - 9 - currX);
            graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, currX, y, 9.0F, 0.0F, segW, 18, 256, 256);
            currX += segW;
        }

        // 3. Bottom edge
        for (int currX = x + 9; currX < x + w - 9; ) {
            int segW = Math.min(200, x + w - 9 - currX);
            graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, currX, y + h - 9, 9.0F, 131.0F, segW, 9, 256, 256);
            currX += segW;
        }

        // 4. Left edge
        for (int currY = y + 18; currY < y + h - 9; ) {
            int segH = Math.min(100, y + h - 9 - currY);
            graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, x, currY, 0.0F, 18.0F, 9, segH, 256, 256);
            currY += segH;
        }

        // 5. Right edge
        for (int currY = y + 18; currY < y + h - 9; ) {
            int segH = Math.min(100, y + h - 9 - currY);
            graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_LOCATION, x + w - 9, currY, 243.0F, 18.0F, 9, segH, 256, 256);
            currY += segH;
        }
    }

    @Unique
    private void advancementProgress$renderGearIcon(GuiGraphicsExtractor graphics, int x, int y, int color) {
        // Crisp 11x11 mechanical gear icon with 8 distinct protruding teeth and central hole
        // Row 0: Top tooth (cols 4..6)
        graphics.fill(x + 4, y + 0, x + 7, y + 1, color);
        // Row 1: Corner teeth tips (cols 1..2, 8..9) & top tooth base (cols 4..6)
        graphics.fill(x + 1, y + 1, x + 3, y + 2, color);
        graphics.fill(x + 4, y + 1, x + 7, y + 2, color);
        graphics.fill(x + 8, y + 1, x + 10, y + 2, color);
        // Row 2: Ring upper rim (cols 1..9)
        graphics.fill(x + 1, y + 2, x + 10, y + 3, color);
        // Row 3: Ring upper shoulder (cols 2..8)
        graphics.fill(x + 2, y + 3, x + 9, y + 4, color);
        // Rows 4..6: Left tooth + ring body (cols 0..3) and ring body + right tooth (cols 7..10)
        // Center hole at cols 4..6 (3x3 square hole)
        graphics.fill(x + 0, y + 4, x + 4, y + 7, color);
        graphics.fill(x + 7, y + 4, x + 11, y + 7, color);
        // Row 7: Ring lower shoulder (cols 2..8)
        graphics.fill(x + 2, y + 7, x + 9, y + 8, color);
        // Row 8: Ring lower rim (cols 1..9)
        graphics.fill(x + 1, y + 8, x + 10, y + 9, color);
        // Row 9: Corner teeth tips (cols 1..2, 8..9) & bottom tooth base (cols 4..6)
        graphics.fill(x + 1, y + 9, x + 3, y + 10, color);
        graphics.fill(x + 4, y + 9, x + 7, y + 10, color);
        graphics.fill(x + 8, y + 9, x + 10, y + 10, color);
        // Row 10: Bottom tooth (cols 4..6)
        graphics.fill(x + 4, y + 10, x + 7, y + 11, color);
    }

    @Inject(method = "extractInside", at = @At("HEAD"), cancellable = true)
    private void onExtractInside(GuiGraphicsExtractor graphics, int x, int y, CallbackInfo ci) {
        AdvancementTab tab = this.selectedTab;
        int inW = AdvancementScreenLayout.getInsideWidth();
        int inH = AdvancementScreenLayout.getInsideHeight();
        if (tab == null) {
            graphics.fill(this.leftPos + 9, this.topPos + 18, this.leftPos + 9 + inW, this.topPos + 18 + inH, 0xFF000000);
            int centerX = this.leftPos + 9 + inW / 2;
            int centerY = this.topPos + 18 + inH / 2;
            graphics.centeredText(this.font, NO_ADVANCEMENTS_LABEL, centerX, centerY - 9 / 2, 0xFFFFFFFF);
            graphics.centeredText(this.font, VERY_SAD_LABEL, centerX, this.topPos + 18 + inH - 9, 0xFFFFFFFF);
            ci.cancel();
        } else {
            tab.extractContents(graphics, this.leftPos + 9, this.topPos + 18);
            ci.cancel();
        }
    }

    @Inject(method = "onUpdateAdvancementProgress", at = @At("RETURN"))
    private void onProgressUpdated(AdvancementNode node, AdvancementProgress progress, CallbackInfo ci) {
        AdvancementCache.markDirty();
        advancementProgress$inspector.updateProgress(progress);
        if (progress != null && progress.isDone() && com.dmystery.client.AdvancementProgressConfig.getInstance().autoUnpinOnComplete) {
            HudPinManager.unpin(node.holder().id());
        }
    }

    @Inject(method = "onAddAdvancementRoot", at = @At("RETURN"))
    private void onRootAdded(AdvancementNode root, CallbackInfo ci) {
        AdvancementCache.markDirty();
        if (this.selectedTab == null && !this.tabs.isEmpty()) {
            AdvancementTab tab = this.tabs.get(root.holder());
            if (tab == null) {
                tab = this.tabs.values().iterator().next();
            }
            this.selectedTab = tab;
            this.advancements.setSelectedTab(tab.getRootNode().holder(), true);
        }
    }

    @Inject(method = "onRemoveAdvancementRoot", at = @At("RETURN"))
    private void onRootRemoved(AdvancementNode root, CallbackInfo ci) {
        AdvancementCache.markDirty();
        if (this.selectedTab != null && this.selectedTab.getRootNode().equals(root)) {
            this.selectedTab = this.tabs.isEmpty() ? null : this.tabs.values().iterator().next();
            if (this.selectedTab != null) {
                this.advancements.setSelectedTab(this.selectedTab.getRootNode().holder(), true);
            }
        }
    }

    @Inject(method = "onAddAdvancementTask", at = @At("RETURN"))
    private void onTaskAdded(AdvancementNode task, CallbackInfo ci) {
        AdvancementCache.markDirty();
    }

    @Inject(method = "onRemoveAdvancementTask", at = @At("RETURN"))
    private void onTaskRemoved(AdvancementNode task, CallbackInfo ci) {
        AdvancementCache.markDirty();
    }

    @Inject(method = "onAdvancementsCleared", at = @At("RETURN"))
    private void onCleared(CallbackInfo ci) {
        AdvancementCache.clear();
        advancementProgress$inspector.close();
    }

    @Inject(method = "onSelectedTabChanged", at = @At("RETURN"))
    private void onTabChanged(AdvancementHolder holder, CallbackInfo ci) {
        if (this.selectedTab == null && !this.tabs.isEmpty()) {
            AdvancementTab firstTab = this.tabs.values().iterator().next();
            this.selectedTab = firstTab;
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean flag, CallbackInfoReturnable<Boolean> cir) {
        // 0. Check click on settings gear button (20x20):
        if (event.button() == 0 && advancementProgress$gearW > 0) {
            if (event.x() >= advancementProgress$gearX && event.x() < advancementProgress$gearX + advancementProgress$gearW
                && event.y() >= advancementProgress$gearY && event.y() < advancementProgress$gearY + advancementProgress$gearH) {
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f)
                );
                net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
                    new AdvancementProgressConfigScreen(this)
                );
                cir.setReturnValue(true);
                return;
            }
        }

        // 1. Check clicks on top Pinned Chips Bar:
        if (event.y() >= 3 && event.y() <= 20) {
            // Check Clear All button
            if (advancementProgress$hasClearAll && event.button() == 0) {
                if (event.x() >= advancementProgress$clearAllX && event.x() <= advancementProgress$clearAllX + advancementProgress$clearAllW
                    && event.y() >= advancementProgress$clearAllY && event.y() <= advancementProgress$clearAllY + advancementProgress$clearAllH) {
                    HudPinManager.clearAll();
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.85f)
                    );
                    if (net.minecraft.client.Minecraft.getInstance().player != null) {
                        net.minecraft.client.Minecraft.getInstance().player.sendOverlayMessage(
                            Component.translatable("advancements_refined.feedback.cleared_all").withStyle(ChatFormatting.GOLD)
                        );
                    }
                    cir.setReturnValue(true);
                    return;
                }
            }

            // Check individual chips
            for (PinnedChip chip : advancementProgress$activeChips) {
                // Clicked close button [X]
                if (event.x() >= chip.closeX && event.x() <= chip.closeX + chip.closeW
                    && event.y() >= chip.closeY && event.y() <= chip.closeY + chip.closeH) {
                    HudPinManager.unpin(chip.id);
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.85f)
                    );
                    if (net.minecraft.client.Minecraft.getInstance().player != null) {
                        net.minecraft.client.Minecraft.getInstance().player.sendOverlayMessage(
                            Component.translatable("advancements_refined.feedback.unpinned", chip.title).withStyle(ChatFormatting.GOLD)
                        );
                    }
                    cir.setReturnValue(true);
                    return;
                }

                // Clicked chip body
                if (event.x() >= chip.x && event.x() <= chip.x + chip.w
                    && event.y() >= chip.y && event.y() <= chip.y + chip.h) {
                    if (event.button() == 1) {
                        // Right-click on chip body unpins
                        HudPinManager.unpin(chip.id);
                        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.85f)
                        );
                        if (net.minecraft.client.Minecraft.getInstance().player != null) {
                            net.minecraft.client.Minecraft.getInstance().player.sendOverlayMessage(
                                Component.translatable("advancements_refined.feedback.unpinned", chip.title).withStyle(ChatFormatting.GOLD)
                            );
                        }
                        cir.setReturnValue(true);
                        return;
                    } else if (event.button() == 0) {
                        // Left-click on chip jumps to tab
                        AdvancementTab tab = advancementProgress$findTabForAdvancement(chip.holder);
                        if (tab != null) {
                            this.selectedTab = tab;
                            this.advancements.setSelectedTab(tab.getRootNode().holder(), true);
                            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f)
                            );
                        }
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }

        // 2. Right-click (button 1) or Middle-click (button 2) on tree node toggles HUD Pin:
        if ((event.button() == 1 || event.button() == 2) && this.selectedTab != null) {
            AdvancementWidget widget = advancementProgress$findWidgetAt(this.selectedTab, event.x(), event.y());
            if (widget instanceof AdvancementWidgetAccessor widgetAccessor) {
                AdvancementNode node = widgetAccessor.advancementProgress$getNode();
                if (node != null) {
                    Identifier id = node.holder().id();
                    Component title = widgetAccessor.advancementProgress$getDisplay() != null 
                        ? widgetAccessor.advancementProgress$getDisplay().getTitle() 
                        : Component.literal(id.getPath());

                    if (HudPinManager.isPinned(id)) {
                        HudPinManager.unpin(id);
                        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.85f)
                        );
                        if (net.minecraft.client.Minecraft.getInstance().player != null) {
                            net.minecraft.client.Minecraft.getInstance().player.sendOverlayMessage(
                                Component.translatable("advancements_refined.feedback.unpinned", title).withStyle(ChatFormatting.GOLD)
                            );
                        }
                    } else {
                        int maxPinned = HudPinManager.getMaxPinned();
                        if (HudPinManager.getPinnedCount() >= maxPinned) {
                            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.5f)
                            );
                            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                                net.minecraft.client.Minecraft.getInstance().player.sendOverlayMessage(
                                    Component.translatable("advancements_refined.feedback.limit_reached", maxPinned, maxPinned).withStyle(ChatFormatting.RED)
                                );
                            }
                        } else {
                            HudPinManager.pin(id);
                            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.3f)
                            );
                            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                                net.minecraft.client.Minecraft.getInstance().player.sendOverlayMessage(
                                    Component.translatable("advancements_refined.feedback.pinned", title, HudPinManager.getPinnedCount(), maxPinned).withStyle(ChatFormatting.GREEN)
                                );
                            }
                        }
                    }
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        // 3. If inspector is open:
        if (advancementProgress$inspector.isVisible()) {
            // Clicked inside inspector panel
            if (advancementProgress$inspector.isMouseOver(event.x(), event.y())) {
                if (advancementProgress$inspector.mouseClicked(event.x(), event.y(), event.button())) {
                    cir.setReturnValue(true);
                    return;
                }
            } else if (event.button() == 0) {
                // Clicked outside inspector panel:
                AdvancementWidget clickedWidget = advancementProgress$findWidgetAt(this.selectedTab, event.x(), event.y());
                if (clickedWidget instanceof AdvancementWidgetAccessor widgetAccessor) {
                    AdvancementNode node = widgetAccessor.advancementProgress$getNode();
                    if (node != null && node.advancement().requirements().size() > 1) {
                        if (!advancementProgress$inspector.isInspecting(node)) {
                            advancementProgress$inspector.open(
                                node,
                                widgetAccessor.advancementProgress$getProgress(),
                                widgetAccessor.advancementProgress$getIcon(),
                                widgetAccessor.advancementProgress$getDisplay()
                            );
                            cir.setReturnValue(true);
                            return;
                        }
                    }
                }

                // If not clicking another composite node, close inspector panel!
                advancementProgress$inspector.close();
            }
        } else {
            // Inspector was closed: open if left-clicked on composite advancement
            if (event.button() == 0 && this.selectedTab != null) {
                AdvancementWidget clickedWidget = advancementProgress$findWidgetAt(this.selectedTab, event.x(), event.y());
                if (clickedWidget instanceof AdvancementWidgetAccessor widgetAccessor) {
                    AdvancementNode node = widgetAccessor.advancementProgress$getNode();
                    if (node != null && node.advancement().requirements().size() > 1) {
                        advancementProgress$inspector.open(
                            node,
                            widgetAccessor.advancementProgress$getProgress(),
                            widgetAccessor.advancementProgress$getIcon(),
                            widgetAccessor.advancementProgress$getDisplay()
                        );
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }

        // 4. Tab selection hit-test with responsive leftPos and topPos
        if (event.button() == 0) {
            for (AdvancementTab tab : this.tabs.values()) {
                if (tab.isMouseOver(this.leftPos, this.topPos, event.x(), event.y())) {
                    this.selectedTab = tab;
                    this.advancements.setSelectedTab(tab.getRootNode().holder(), true);
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f)
                    );
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        // 256 is GLFW_KEY_ESCAPE
        if (advancementProgress$inspector.isVisible() && event.key() == 256) {
            advancementProgress$inspector.close();
            cir.setReturnValue(true);
            return;
        }

        if (!AdvancementProgressClient.OPEN_SETTINGS_KEY.isUnbound()
            && AdvancementProgressClient.OPEN_SETTINGS_KEY.matches(event)) {
            net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
                new AdvancementProgressConfigScreen(this)
            );
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (advancementProgress$inspector.isVisible() && advancementProgress$inspector.mouseScrolled(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "extractTooltips", at = @At("HEAD"), cancellable = true)
    private void onExtractTooltipsHead(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int leftPos, int topPos, CallbackInfo ci) {
        if (advancementProgress$inspector.isMouseOver(mouseX, mouseY)) {
            ci.cancel();
            return;
        }

        if (this.selectedTab != null) {
            if (advancementProgress$inspector.isVisible() && this.selectedTab instanceof com.dmystery.client.AdvancementTabExtension tabExt) {
                AdvancementWidget hovered = tabExt.advancementProgress$getHovered();
                if (hovered instanceof AdvancementWidgetAccessor widgetAccessor) {
                    if (advancementProgress$inspector.isInspecting(widgetAccessor.advancementProgress$getNode())) {
                        ci.cancel();
                        return;
                    }
                }
            }

            graphics.pose().pushMatrix();
            graphics.pose().translate((float) (this.leftPos + 9), (float) (this.topPos + 18));
            graphics.nextStratum();
            this.selectedTab.extractTooltips(
                graphics,
                mouseX - this.leftPos - 9,
                mouseY - this.topPos - 18,
                this.leftPos,
                this.topPos
            );
            graphics.pose().popMatrix();
        }
        ci.cancel();
    }

    @Inject(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/advancements/AdvancementsScreen;extractTooltips(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"
        )
    )
    private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // Guard: Do not read AdvancementCache or render extra UI if tabs are empty or no tab is selected
        if (this.tabs.isEmpty() || this.selectedTab == null) {
            return;
        }

        AdvancementCache.updateIfDirty(this.tabs);

        int totalCount = AdvancementCache.getTotalCount();
        if (totalCount == 0) {
            return;
        }

        int winW = AdvancementScreenLayout.getWindowWidth();
        int winH = AdvancementScreenLayout.getWindowHeight();

        // --- 1. Top Bar: Overall Progress Bar + Pinned Advancements Chips ---
        int barHeight = 14;
        int barY = 4;

        // 1.1 Calculate Pinned Chips
        advancementProgress$activeChips.clear();
        advancementProgress$hasClearAll = false;
        List<Identifier> pinnedIds = HudPinManager.getPinned();
        int totalPinnedW = 0;

        for (Identifier id : pinnedIds) {
            AdvancementHolder holder = this.advancements.get(id);
            DisplayInfo display = (holder != null && holder.value().display().isPresent()) ? holder.value().display().get() : null;
            Component title = display != null ? display.getTitle() : Component.literal(id.getPath());
            ItemStack icon = display != null ? display.getIcon().create() : new ItemStack(Items.BOOK);
            PinnedChip chip = new PinnedChip(id, holder, title, icon);

            String rawTitle = title.getString();
            String shortTitle = this.font.plainSubstrByWidth(rawTitle, 110);
            if (!shortTitle.equals(rawTitle)) {
                shortTitle = this.font.plainSubstrByWidth(rawTitle, 102) + "…";
            }
            chip.displayTitle = shortTitle;
            int textW = this.font.width(shortTitle);
            chip.w = 16 + textW + 12;
            chip.h = barHeight;
            totalPinnedW += chip.w + 4;
            advancementProgress$activeChips.add(chip);
        }

        int clearAllW = 24;
        if (advancementProgress$activeChips.size() > 1) {
            advancementProgress$hasClearAll = true;
            totalPinnedW += clearAllW + 4;
        }

        int gearReserved = 22;
        advancementProgress$gearW = 18;
        advancementProgress$gearH = barHeight;
        advancementProgress$gearX = this.leftPos + winW - advancementProgress$gearW;
        advancementProgress$gearY = barY;

        int availableBarW = winW - gearReserved;

        int barWidth;
        int pinnedStartX;
        if (advancementProgress$activeChips.isEmpty()) {
            barWidth = availableBarW;
            pinnedStartX = this.leftPos + availableBarW;
        } else {
            barWidth = Math.max(120, availableBarW - totalPinnedW - 8);
            pinnedStartX = this.leftPos + availableBarW - totalPinnedW;
        }

        if (com.dmystery.client.AdvancementProgressConfig.getInstance().showGlobalBar) {
            int barX = this.leftPos;
            boolean barHovered = mouseX >= barX && mouseX <= barX + barWidth && mouseY >= barY && mouseY <= barY + barHeight;

            // Progress Background box
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xCC000000);

            // Progress fill
            int innerWidth = barWidth - 2;
            int innerHeight = barHeight - 2;
            int filledWidth = (int) Math.round(innerWidth * (double) AdvancementCache.getTotalPercent());
            if (filledWidth > 0) {
                int fillMaxX = Math.min(barX + 1 + filledWidth, barX + 1 + innerWidth);
                graphics.fillGradient(barX + 1, barY + 1, fillMaxX, barY + 1 + innerHeight, 0xFF2ECC71, 0xFF1B8A4C);
            }

            // Border outline
            int borderColor = barHovered ? 0xFFFFFFFF : 0xFF555555;
            graphics.outline(barX, barY, barWidth, barHeight, borderColor);

            // Centered text
            Component label = AdvancementCache.getCachedTotalText();
            int textY = barY + (barHeight - 9) / 2;
            graphics.centeredText(this.font, label, barX + barWidth / 2, textY, 0xFFFFFFFF);

            // Tooltip on hover
            if (barHovered) {
                Component tooltip = Component.translatable(
                    "advancements_refined.tooltip",
                    AdvancementCache.getTotalCompleted(),
                    AdvancementCache.getTotalCount()
                );
                graphics.setTooltipForNextFrame(tooltip, mouseX, mouseY);
            }
        }

        // 1.2 Render Pinned Chips
        int currX = pinnedStartX;
        for (PinnedChip chip : advancementProgress$activeChips) {
            chip.x = currX;
            chip.y = barY;
            chip.closeX = chip.x + chip.w - 11;
            chip.closeY = barY + 1;
            chip.closeW = 10;
            chip.closeH = barHeight - 2;

            boolean chipHovered = mouseX >= chip.x && mouseX < chip.x + chip.w && mouseY >= chip.y && mouseY <= chip.y + chip.h;
            boolean closeHovered = mouseX >= chip.closeX && mouseX <= chip.closeX + chip.closeW && mouseY >= chip.closeY && mouseY <= chip.closeY + chip.closeH;

            // Background & outline (clean dark slate card)
            int bgColor = chipHovered ? 0xEE2A3240 : 0xDD1F2530;
            graphics.fill(chip.x, chip.y, chip.x + chip.w, chip.y + chip.h, bgColor);
            int chipOutline = chipHovered ? 0xFFFFAA00 : 0x55778899;
            graphics.outline(chip.x, chip.y, chip.w, chip.h, chipOutline);

            // Icon scaled cleanly to fit 14px bar
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) (chip.x + 1), (float) chip.y);
            graphics.pose().scale(0.875f, 0.875f);
            graphics.fakeItem(chip.icon, 0, 0);
            graphics.pose().popMatrix();

            // Title
            graphics.text(this.font, Component.literal(chip.displayTitle != null ? chip.displayTitle : chip.title.getString()), chip.x + 15, chip.y + 3, 0xFFFFAA00, false);

            // Close button [✕]
            if (closeHovered) {
                graphics.fill(chip.closeX, chip.closeY, chip.closeX + chip.closeW, chip.closeY + chip.closeH, 0xEECC3333);
            }
            graphics.centeredText(this.font, Component.literal("✕"), chip.closeX + chip.closeW / 2 + 1, chip.closeY + 1, closeHovered ? 0xFFFFFFFF : 0xFFEE6666);

            // Tooltips
            if (closeHovered) {
                graphics.setTooltipForNextFrame(Component.translatable("advancements_refined.pinned_bar.unpin_tooltip", chip.title), mouseX, mouseY);
            } else if (chipHovered) {
                graphics.setTooltipForNextFrame(Component.translatable("advancements_refined.pinned_bar.jump_tooltip"), mouseX, mouseY);
            }

            currX += chip.w + 4;
        }

        // Render Clear All button if active
        if (advancementProgress$hasClearAll) {
            advancementProgress$clearAllX = currX;
            advancementProgress$clearAllY = barY;
            advancementProgress$clearAllW = clearAllW;
            advancementProgress$clearAllH = barHeight;

            boolean clearHovered = mouseX >= advancementProgress$clearAllX && mouseX <= advancementProgress$clearAllX + clearAllW
                && mouseY >= barY && mouseY <= barY + barHeight;

            graphics.fill(advancementProgress$clearAllX, barY, advancementProgress$clearAllX + clearAllW, barY + barHeight, clearHovered ? 0xEEAA2222 : 0xCC2A3240);
            graphics.outline(advancementProgress$clearAllX, barY, clearAllW, barHeight, clearHovered ? 0xFFFF4444 : 0x55778899);
            graphics.centeredText(this.font, Component.translatable("advancements_refined.pinned_bar.clear_all"), advancementProgress$clearAllX + clearAllW / 2, barY + 3, 0xFFFFFFFF);

            if (clearHovered) {
                graphics.setTooltipForNextFrame(Component.translatable("advancements_refined.pinned_bar.clear_all_tooltip"), mouseX, mouseY);
            }
        }

        // --- 1.3 Render Settings Gear Button ---
        boolean gearHovered = mouseX >= advancementProgress$gearX && mouseX < advancementProgress$gearX + advancementProgress$gearW
            && mouseY >= advancementProgress$gearY && mouseY < advancementProgress$gearY + advancementProgress$gearH;

        int gearBg = gearHovered ? 0xEE2A3240 : 0xDD1F2530;
        int gearBorder = gearHovered ? 0xFFFFAA00 : 0x55778899;
        graphics.fill(advancementProgress$gearX, advancementProgress$gearY, advancementProgress$gearX + advancementProgress$gearW, advancementProgress$gearY + advancementProgress$gearH, gearBg);
        graphics.outline(advancementProgress$gearX, advancementProgress$gearY, advancementProgress$gearW, advancementProgress$gearH, gearBorder);

        int iconX = advancementProgress$gearX + (advancementProgress$gearW - 11) / 2;
        int iconY = advancementProgress$gearY + (advancementProgress$gearH - 11) / 2;
        // Drop shadow (1px offset)
        advancementProgress$renderGearIcon(graphics, iconX + 1, iconY + 1, 0x60000000);
        // Main gear icon (gold on hover, metallic silver normally)
        int gearColor = gearHovered ? 0xFFFFAA00 : 0xFFD8E0E8;
        advancementProgress$renderGearIcon(graphics, iconX, iconY, gearColor);

        if (gearHovered) {
            graphics.setTooltipForNextFrame(
                Component.translatable("advancements_refined.config.button_tooltip"),
                mouseX,
                mouseY
            );
        }

        // --- 2. Mini-indicators on Tabs ---
        for (AdvancementTab tab : this.tabs.values()) {
            AdvancementCache.TabStats stats = AdvancementCache.getTabStats(tab);
            AdvancementTabType type = tab.getType();
            int tabIndex = tab.getIndex();
            int tabX = this.leftPos + type.getX(tabIndex);
            int tabY = this.topPos + type.getY(tabIndex);
            int tabW = type.getWidth();
            int tabH = type.getHeight();

            if (com.dmystery.client.AdvancementProgressConfig.getInstance().showTabBadges) {
                int indicatorColor = stats.percent() >= 1.0f ? 0xFFFFD700 : 0xFF2ECC71;

                if (type == AdvancementTabType.ABOVE) {
                    int miniBarX = tabX + 4;
                    int miniBarY = tabY + tabH - 4;
                    int miniBarW = tabW - 8;
                    graphics.fill(miniBarX, miniBarY, miniBarX + miniBarW, miniBarY + 2, 0x90000000);
                    int miniFillW = (int) Math.round(miniBarW * stats.percent());
                    if (miniFillW > 0) {
                        graphics.fill(miniBarX, miniBarY, miniBarX + miniFillW, miniBarY + 2, indicatorColor);
                    }
                } else if (type == AdvancementTabType.BELOW) {
                    int miniBarX = tabX + 4;
                    int miniBarY = tabY + 2;
                    int miniBarW = tabW - 8;
                    graphics.fill(miniBarX, miniBarY, miniBarX + miniBarW, miniBarY + 2, 0x90000000);
                    int miniFillW = (int) Math.round(miniBarW * stats.percent());
                    if (miniFillW > 0) {
                        graphics.fill(miniBarX, miniBarY, miniBarX + miniFillW, miniBarY + 2, indicatorColor);
                    }
                }
            }

            // Tab hover tooltip enhancement (only if mouse is not over inspector)
            if (!advancementProgress$inspector.isMouseOver(mouseX, mouseY) && tab.isMouseOver(this.leftPos, this.topPos, mouseX, mouseY)) {
                Component tabTip = Component.translatable(
                    "advancements_refined.tab_tooltip",
                    tab.getTitle(),
                    stats.completed(),
                    stats.total(),
                    String.format(java.util.Locale.ROOT, "%.0f", stats.percent() * 100.0f)
                );
                graphics.setTooltipForNextFrame(tabTip, mouseX, mouseY);
            }
        }

        // --- 3. Selected Tab Title Progress in Window Header ---
        if (this.selectedTab != null && com.dmystery.client.AdvancementProgressConfig.getInstance().showTabBadges) {
            AdvancementCache.TabStats selStats = AdvancementCache.getTabStats(this.selectedTab);
            int titleWidth = this.font.width(this.selectedTab.getTitle());
            String pctFormatted = String.format(java.util.Locale.ROOT, "%.0f%%", selStats.percent() * 100.0f);
            Component progressSub = Component.literal(" (" + selStats.completed() + "/" + selStats.total() + " — " + pctFormatted + ")");
            graphics.text(this.font, progressSub, this.leftPos + 8 + titleWidth + 4, this.topPos + 6, 0xFF666666, false);
        }

        // --- 5. Render Inspector Panel docked cleanly inside the right side of window ---
        int panelW = 160;
        int panelH = winH - 32;
        int panelX = this.leftPos + winW - panelW - 12;
        int panelY = this.topPos + 22;

        advancementProgress$inspector.setBounds(panelX, panelY, panelW, panelH);
        advancementProgress$inspector.render(graphics, this.font, mouseX, mouseY);
    }
}
