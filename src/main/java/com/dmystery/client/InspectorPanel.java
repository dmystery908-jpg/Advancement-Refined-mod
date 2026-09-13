package com.dmystery.client;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class InspectorPanel {
    public record CriterionItem(String id, boolean done, ItemStack icon, Component name) {}

    private DisplayInfo display;
    private AdvancementNode node;
    private AdvancementProgress progress;
    private ItemStack advancementIcon = ItemStack.EMPTY;
    private Component advancementTitle = Component.empty();
    private int completedCount = 0;
    private int totalCount = 0;

    private boolean visible = false;
    private boolean hideCompleted = false;
    private double scrollOffset = 0.0;

    private int x = 0;
    private int y = 0;
    private int width = 160;
    private int height = 150;

    private final List<CriterionItem> allEntries = new ArrayList<>();
    private final List<CriterionItem> visibleEntries = new ArrayList<>();

    public void open(AdvancementNode node, AdvancementProgress progress, ItemStack icon, DisplayInfo display) {
        this.display = display;
        this.node = node;
        this.progress = progress;
        this.advancementIcon = icon != null ? icon : new ItemStack(Items.BOOK);
        this.advancementTitle = display != null ? display.getTitle() : Component.literal("Advancement");
        this.visible = true;
        this.scrollOffset = 0.0;
        rebuildEntries();
    }

    public void close() {
        this.visible = false;
        this.display = null;
        this.node = null;
        this.progress = null;
        this.allEntries.clear();
        this.visibleEntries.clear();
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isInspecting(AdvancementNode checkNode) {
        return visible && node != null && node.equals(checkNode);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public AdvancementNode getNode() {
        return node;
    }

    public void updateProgress(AdvancementProgress newProgress) {
        if (!visible || node == null) return;
        this.progress = newProgress;
        rebuildEntries();
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void rebuildEntries() {
        allEntries.clear();
        visibleEntries.clear();
        completedCount = 0;
        totalCount = 0;

        if (node == null) return;

        Advancement adv = node.advancement();
        int reqSize = adv.requirements().size();
        this.totalCount = reqSize;

        if (progress != null && progress.isDone()) {
            this.completedCount = reqSize;
        } else if (progress != null) {
            this.completedCount = Math.min(reqSize, adv.requirements().count(critName -> {
                net.minecraft.advancements.CriterionProgress cp = progress.getCriterion(critName);
                return cp != null && cp.isDone();
            }));
        } else {
            this.completedCount = 0;
        }

        for (String critName : adv.requirements().names()) {
            boolean done = progress != null && progress.getCriterion(critName) != null && progress.getCriterion(critName).isDone();

            CriterionResolver.CriterionDisplay display = CriterionResolver.resolve(critName);
            CriterionItem item = new CriterionItem(critName, done, display.icon(), display.name());
            allEntries.add(item);
        }

        // Sort: remaining items first so player sees what they still need to do
        allEntries.sort((a, b) -> {
            if (a.done() != b.done()) {
                return Boolean.compare(a.done(), b.done());
            }
            return a.name().getString().compareToIgnoreCase(b.name().getString());
        });

        for (CriterionItem item : allEntries) {
            if (hideCompleted && item.done()) {
                continue;
            }
            visibleEntries.add(item);
        }

        clampScroll();
    }

    private void clampScroll() {
        int listHeight = height - 56;
        int contentHeight = visibleEntries.size() * 18;
        int maxScroll = Math.max(0, contentHeight - listHeight);
        scrollOffset = Mth.clamp(scrollOffset, 0.0, maxScroll);
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        // 1. Panel Background and border
        graphics.fill(x, y, x + width, y + height, 0xF0121212);
        graphics.renderOutline(x, y, width, height, 0xFF444444);

        // 2. Header
        // Icon
        if (!advancementIcon.isEmpty()) {
            graphics.renderItem(advancementIcon, x + 6, y + 6);
        }

        // Title
        int textX = x + 26;
        int titleMaxW = width - 42;
        java.util.List<net.minecraft.util.FormattedCharSequence> titleLines = font.split(advancementTitle, titleMaxW);
        net.minecraft.util.FormattedCharSequence shortTitle = titleLines.isEmpty() ? net.minecraft.util.FormattedCharSequence.EMPTY : titleLines.get(0);
        graphics.drawString(font, shortTitle, textX, y + 6, 0xFFFFAA00, true);

        // Progress counter
        float pct = totalCount > 0 ? (float) completedCount / totalCount : 0.0f;
        String pctStr = String.format(java.util.Locale.ROOT, "%.0f%%", pct * 100.0f);
        Component progressLabel = Component.literal(completedCount + " / " + totalCount + " (" + pctStr + ")");
        if (display != null && display.isHidden()) {
            progressLabel = progressLabel.copy().append(Component.literal(" ")).append(Component.translatable("advancements_refined.hidden_advancement"));
        }
        graphics.drawString(font, progressLabel, textX, y + 16, 0xFFAAAAAA, true);

        // Close button (X)
        int closeX = x + width - 15;
        int closeY = y + 5;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + 10 && mouseY >= closeY && mouseY <= closeY + 10;
        graphics.drawString(font, "✕", closeX + 1, closeY + 1, closeHovered ? 0xFFFF5555 : 0xFF888888, true);

        // Pin button (★)
        int pinX = closeX - 14;
        int pinY = y + 5;
        boolean isPinned = node != null && HudPinManager.isPinned(node.holder().id());
        boolean pinHovered = mouseX >= pinX && mouseX <= pinX + 12 && mouseY >= pinY && mouseY <= pinY + 10;
        int pinColor = isPinned ? 0xFF55FFFF : (pinHovered ? 0xFFFFFFFF : 0xFF888888);
        graphics.drawString(font, "★", pinX + 1, pinY + 1, pinColor, true);

        if (pinHovered) {
            Component pinTip = isPinned
                ? Component.translatable("advancements_refined.pin.unpin_tooltip")
                : (HudPinManager.getPinnedCount() >= HudPinManager.getMaxPinned()
                    ? Component.translatable("advancements_refined.pin.max_reached", HudPinManager.getMaxPinned())
                    : Component.translatable("advancements_refined.pin.pin_tooltip"));
            graphics.renderTooltip(font, pinTip, mouseX, mouseY);
        }

        // Mini progress bar in header
        int barW = width - 12;
        int barH = 3;
        int barX = x + 6;
        int barY = y + 27;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
        int fillW = (int) Math.round(barW * pct);
        if (fillW > 0) {
            graphics.fill(barX, barY, barX + fillW, barY + barH, 0xFF2ECC71);
        }

        // 3. Filter toggle button
        int filterBtnX = x + 6;
        int filterBtnY = y + 34;
        int filterBtnW = width - 12;
        int filterBtnH = 14;
        boolean filterHovered = mouseX >= filterBtnX && mouseX <= filterBtnX + filterBtnW &&
                               mouseY >= filterBtnY && mouseY <= filterBtnY + filterBtnH;

        int filterBg = filterHovered ? 0xFF2E2E2E : 0xFF1F1F1F;
        graphics.fill(filterBtnX, filterBtnY, filterBtnX + filterBtnW, filterBtnY + filterBtnH, filterBg);
        graphics.renderOutline(filterBtnX, filterBtnY, filterBtnW, filterBtnH, filterHovered ? 0xFF666666 : 0xFF3A3A3A);

        String checkMark = hideCompleted ? "✔ " : "   ";
        Component filterText = Component.literal(checkMark).append(
            Component.translatable("advancements_refined.inspector.hide_completed")
        );
        graphics.drawCenteredString(font, filterText, filterBtnX + filterBtnW / 2, filterBtnY + 3, hideCompleted ? 0xFF55FF55 : 0xFFCCCCCC);

        // 4. Criteria List
        int listX = x + 4;
        int listY = y + 52;
        int listW = width - 8;
        int listH = height - 56;
        int listBottom = listY + listH;

        graphics.enableScissor(listX, listY, listX + listW, listBottom);

        int rowH = 18;
        for (int i = 0; i < visibleEntries.size(); i++) {
            CriterionItem item = visibleEntries.get(i);
            int rowY = listY + i * rowH - (int) scrollOffset;

            // Culling
            if (rowY + rowH < listY || rowY > listBottom) {
                continue;
            }

            boolean rowHovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + rowH;
            if (rowHovered) {
                graphics.fill(listX, rowY, listX + listW, rowY + rowH, 0x25FFFFFF);
            }

            // Status icon: checkmark vs bullet
            if (item.done()) {
                graphics.drawString(font, "✔", listX + 2, rowY + 5, 0xFF2ECC71, true);
            } else {
                graphics.drawString(font, "○", listX + 2, rowY + 5, 0xFF777777, true);
            }

            // Item/entity icon
            if (!item.icon().isEmpty()) {
                graphics.renderItem(item.icon(), listX + 12, rowY + 1);
            }

            // Name
            int nameX = listX + 31;
            int nameMaxW = listW - 35;
            java.util.List<net.minecraft.util.FormattedCharSequence> nameLines = font.split(item.name(), nameMaxW);
            net.minecraft.util.FormattedCharSequence shortName = nameLines.isEmpty() ? net.minecraft.util.FormattedCharSequence.EMPTY : nameLines.get(0);
            int textColor = item.done() ? 0xFF88D49E : 0xFFE0E0E0;
            graphics.drawString(font, shortName, nameX, rowY + 5, textColor, true);

            // Row tooltip if hovered and mouse is inside list view
            if (rowHovered && mouseY >= listY && mouseY <= listBottom) {
                graphics.renderTooltip(font, item.name(), mouseX, mouseY);
            }
        }

        graphics.disableScissor();

        // 5. Scrollbar
        int contentH = visibleEntries.size() * rowH;
        if (contentH > listH) {
            int scrollbarX = x + width - 4;
            int scrollbarW = 2;
            graphics.fill(scrollbarX, listY, scrollbarX + scrollbarW, listBottom, 0x40FFFFFF);

            float scrollRatio = (float) scrollOffset / (contentH - listH);
            int thumbH = Math.max(12, (int) ((float) listH / contentH * listH));
            int thumbY = listY + (int) (scrollRatio * (listH - thumbH));
            graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        // Check if inside panel
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }

        // Close button
        int closeX = x + width - 15;
        int closeY = y + 5;
        if (mouseX >= closeX && mouseX <= closeX + 10 && mouseY >= closeY && mouseY <= closeY + 10) {
            close();
            return true;
        }

        // Pin button
        int pinX = closeX - 14;
        int pinY = y + 5;
        if (mouseX >= pinX && mouseX <= pinX + 12 && mouseY >= pinY && mouseY <= pinY + 10) {
            if (node != null) {
                HudPinManager.togglePin(node.holder().id());
            }
            return true;
        }

        // Filter button
        int filterBtnX = x + 6;
        int filterBtnY = y + 34;
        int filterBtnW = width - 12;
        int filterBtnH = 14;
        if (mouseX >= filterBtnX && mouseX <= filterBtnX + filterBtnW &&
            mouseY >= filterBtnY && mouseY <= filterBtnY + filterBtnH) {
            hideCompleted = !hideCompleted;
            scrollOffset = 0.0;
            rebuildEntries();
            return true;
        }

        return true; // Consume clicks inside panel to prevent clicking elements behind
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (!visible) return false;

        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            scrollOffset -= verticalAmount * 18.0;
            clampScroll();
            return true;
        }
        return false;
    }
}
