package com.dmystery.mixin;

import com.dmystery.client.HudPinManager;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetMixin {
    @Shadow @Final private DisplayInfo display;
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Mutable @Final private List<FormattedCharSequence> description;
    @Shadow @Mutable @Final private int width;
    @Shadow @Final private AdvancementNode advancementNode;
    @Shadow private int x;
    @Shadow private int y;

    @Redirect(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/advancements/DisplayInfo;isHidden()Z"
        )
    )
    private boolean redirectIsHiddenInRender(DisplayInfo display) {
        // Allow unearned hidden advancements to be rendered
        return false;
    }

    @Redirect(
        method = "isMouseOver",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/advancements/DisplayInfo;isHidden()Z"
        )
    )
    private boolean redirectIsHiddenInMouseOver(DisplayInfo display) {
        // Allow unearned hidden advancements to be hovered
        return false;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(AdvancementTab tab, Minecraft minecraft, AdvancementNode node, DisplayInfo display, CallbackInfo ci) {
        boolean isComposite = node != null && node.advancement().requirements().size() > 1;
        if (com.dmystery.client.AdvancementProgressConfig.getInstance().showTooltipHints) {
            int minHintWidth = isComposite ? 150 : 80;
            this.width = Math.max(this.width, minHintWidth);
        }

        if (display != null && display.isHidden()) {
            Component hiddenLabel = Component.translatable("advancement_progress.hidden_advancement")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
            List<FormattedCharSequence> extraLines = this.minecraft.font.split(hiddenLabel, Math.max(160, this.width));
            List<FormattedCharSequence> combined = new ArrayList<>(this.description);
            combined.addAll(extraLines);
            this.description = combined;

            for (FormattedCharSequence line : extraLines) {
                this.width = Math.max(this.width, this.minecraft.font.width(line) + 8);
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void renderPinBadge(GuiGraphicsExtractor graphics, int scrollX, int scrollY, CallbackInfo ci) {
        if (HudPinManager.isPinned(this.advancementNode.holder().id())) {
            // Elegant gold border around the frame
            graphics.outline(scrollX + this.x + 2, scrollY + this.y - 1, 28, 28, 0xFFFFD700);
            // Bright gold star in the upper corner without ugly black box
            int starX = scrollX + this.x + 20;
            int starY = scrollY + this.y - 2;
            graphics.text(this.minecraft.font, Component.literal("★"), starX, starY, 0xFFFFD700, false);
        }
    }

    @Redirect(
        method = "extractHover",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/gui/screens/advancements/AdvancementWidget;description:Ljava/util/List;"
        )
    )
    private List<FormattedCharSequence> redirectDescriptionInHover(AdvancementWidget widget) {
        return advancementProgress$getHoverDescription();
    }

    @org.spongepowered.asm.mixin.Unique
    private List<FormattedCharSequence> advancementProgress$getHoverDescription() {
        List<FormattedCharSequence> result = new ArrayList<>(this.description);

        if (!com.dmystery.client.AdvancementProgressConfig.getInstance().showTooltipHints) {
            return result;
        }

        boolean isComposite = this.advancementNode != null && this.advancementNode.advancement().requirements().size() > 1;
        boolean isPinned = this.advancementNode != null && HudPinManager.isPinned(this.advancementNode.holder().id());
        boolean isFull = !isPinned && HudPinManager.getPinnedCount() >= HudPinManager.getMaxPinned();

        net.minecraft.network.chat.MutableComponent hintComp = Component.empty();
        if (isComposite) {
            hintComp.append(Component.translatable("advancement_progress.hint.key_lmb").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" "))
                .append(Component.translatable("advancement_progress.hint.action_inspect").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  •  ").withStyle(ChatFormatting.DARK_GRAY));
        }

        hintComp.append(Component.translatable("advancement_progress.hint.key_rmb").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" "));

        if (isPinned) {
            hintComp.append(Component.translatable("advancement_progress.hint.action_unpin").withStyle(ChatFormatting.GRAY));
        } else if (isFull) {
            hintComp.append(Component.translatable("advancement_progress.hint.action_full").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            hintComp.append(Component.translatable("advancement_progress.hint.action_pin").withStyle(ChatFormatting.GRAY));
        }

        int hintWidth = this.minecraft.font.width(hintComp);
        this.width = Math.max(this.width, hintWidth + 10);

        List<FormattedCharSequence> hintLines = this.minecraft.font.split(hintComp, this.width);

        if (!result.isEmpty()) {
            result.add(FormattedCharSequence.EMPTY);
        }
        result.addAll(hintLines);

        return result;
    }
}
