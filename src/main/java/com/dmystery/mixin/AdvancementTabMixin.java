package com.dmystery.mixin;

import com.dmystery.client.AdvancementScreenLayout;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.dmystery.client.AdvancementTabExtension;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(AdvancementTab.class)
public abstract class AdvancementTabMixin implements AdvancementTabExtension {
    @Shadow private double scrollX;
    @Shadow private double scrollY;
    @Shadow private int minX;
    @Shadow private int minY;
    @Shadow private int maxX;
    @Shadow private int maxY;
    @Shadow private float fade;
    @Shadow private boolean centered;
    @Shadow @Final private DisplayInfo display;
    @Shadow @Final private AdvancementWidget root;
    @Shadow @Final private Map<AdvancementHolder, AdvancementWidget> widgets;

    @Unique
    @Nullable
    private AdvancementWidget advancementProgress$hovered;

    @Override
    public AdvancementWidget advancementProgress$getHovered() {
        return this.advancementProgress$hovered;
    }

    @Inject(method = "canScrollHorizontally", at = @At("HEAD"), cancellable = true)
    private void onCanScrollHorizontally(CallbackInfoReturnable<Boolean> cir) {
        float scale = AdvancementScreenLayout.getZoom();
        double inW = AdvancementScreenLayout.getInsideWidth() / scale;
        int treeW = this.maxX - this.minX + 26;
        cir.setReturnValue(treeW > inW);
    }

    @Inject(method = "canScrollVertically", at = @At("HEAD"), cancellable = true)
    private void onCanScrollVertically(CallbackInfoReturnable<Boolean> cir) {
        float scale = AdvancementScreenLayout.getZoom();
        double inH = AdvancementScreenLayout.getInsideHeight() / scale;
        int treeH = this.maxY - this.minY + 26;
        cir.setReturnValue(treeH > inH);
    }

    @Inject(method = "scroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(double deltaX, double deltaY, CallbackInfo ci) {
        float scale = AdvancementScreenLayout.getZoom();
        double inW = AdvancementScreenLayout.getInsideWidth() / scale;
        double inH = AdvancementScreenLayout.getInsideHeight() / scale;
        int padding = 32;

        int treeW = this.maxX - this.minX + 26;
        if (treeW > inW) {
            double minScrollX = (inW - padding - this.maxX - 26);
            double maxScrollX = (padding - this.minX);
            this.scrollX = Mth.clamp(this.scrollX + deltaX / scale, minScrollX, maxScrollX);
        } else {
            this.scrollX = (inW - (this.maxX + this.minX + 26)) / 2.0;
        }

        int treeH = this.maxY - this.minY + 26;
        if (treeH > inH) {
            double minScrollY = (inH - padding - this.maxY - 26);
            double maxScrollY = (padding - this.minY);
            this.scrollY = Mth.clamp(this.scrollY + deltaY / scale, minScrollY, maxScrollY);
        } else {
            this.scrollY = (inH - (this.maxY + this.minY + 26)) / 2.0;
        }
        ci.cancel();
    }

    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void onExtractContents(GuiGraphicsExtractor graphics, int x, int y, CallbackInfo ci) {
        int inW = AdvancementScreenLayout.getInsideWidth();
        int inH = AdvancementScreenLayout.getInsideHeight();
        float scale = AdvancementScreenLayout.getZoom();
        double effectiveInW = inW / scale;
        double effectiveInH = inH / scale;

        if (!this.centered) {
            this.scrollX = (effectiveInW - (this.maxX + this.minX + 26)) / 2.0;
            this.scrollY = (effectiveInH - (this.maxY + this.minY + 26)) / 2.0;
            this.centered = true;
        }

        graphics.enableScissor(x, y, x + inW, y + inH);
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);

        Identifier bg = this.display.getBackground()
                .map(ClientAsset.ResourceTexture::texturePath)
                .orElse(TextureManager.INTENTIONAL_MISSING_TEXTURE);

        int sX = Mth.floor(this.scrollX);
        int sY = Mth.floor(this.scrollY);
        int tileOffsetX = (int) Math.round((sX * scale) % 16);
        int tileOffsetY = (int) Math.round((sY * scale) % 16);
        int cols = (inW / 16) + 1;
        int rows = (inH / 16) + 1;

        for (int col = -1; col <= cols; col++) {
            for (int row = -1; row <= rows; row++) {
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        bg,
                        tileOffsetX + col * 16,
                        tileOffsetY + row * 16,
                        0.0F, 0.0F,
                        16, 16,
                        16, 16
                );
            }
        }

        // Scale the tree content (connectivity and widgets)
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);

        this.root.extractConnectivity(graphics, sX, sY, true);
        this.root.extractConnectivity(graphics, sX, sY, false);
        this.root.extractRenderState(graphics, sX, sY);

        graphics.pose().popMatrix();

        graphics.pose().popMatrix();
        graphics.disableScissor();

        ci.cancel();
    }

    @Inject(method = "extractTooltips", at = @At("HEAD"), cancellable = true)
    private void onExtractTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int leftPos, int topPos, CallbackInfo ci) {
        int inW = AdvancementScreenLayout.getInsideWidth();
        int inH = AdvancementScreenLayout.getInsideHeight();
        float scale = AdvancementScreenLayout.getZoom();

        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0f, 0.0f);
        graphics.fill(0, 0, inW, inH, Mth.floor(this.fade * 255.0F) << 24);

        boolean hoveredAny = false;
        int sX = Mth.floor(this.scrollX);
        int sY = Mth.floor(this.scrollY);

        if (mouseX > 0 && mouseX < inW && mouseY > 0 && mouseY < inH) {
            int treeMouseX = (int) Math.round(mouseX / scale);
            int treeMouseY = (int) Math.round(mouseY / scale);
            for (AdvancementWidget widget : this.widgets.values()) {
                if (widget.isMouseOver(sX, sY, treeMouseX, treeMouseY)) {
                    hoveredAny = true;
                    this.advancementProgress$hovered = widget;
                    int adjustedSX = (int) Math.round((sX + widget.getX()) * scale) - widget.getX();
                    int adjustedSY = (int) Math.round((sY + widget.getY()) * scale) - widget.getY();
                    widget.extractHover(graphics, adjustedSX, adjustedSY, this.fade, leftPos, topPos);
                    break;
                }
            }
        }

        if (hoveredAny) {
            this.fade = Mth.clamp(this.fade + 0.02F, 0.0F, 0.3F);
        } else {
            this.fade = Mth.clamp(this.fade - 0.04F, 0.0F, 1.0F);
            if (this.fade <= 0.0F) {
                this.advancementProgress$hovered = null;
            }
        }

        graphics.pose().popMatrix();
        ci.cancel();
    }
}
