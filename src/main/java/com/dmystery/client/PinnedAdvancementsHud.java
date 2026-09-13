package com.dmystery.client;

import com.dmystery.mixin.ClientAdvancementsAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

public class PinnedAdvancementsHud implements HudElement {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // Only show during gameplay or chat
        net.minecraft.client.gui.screens.Screen screen = mc.screen;
        if (screen != null && !(screen instanceof ChatScreen)) {
            return;
        }

        AdvancementProgressConfig config = AdvancementProgressConfig.getInstance();
        if (!config.hudEnabled) {
            return;
        }

        List<Identifier> pinnedList = HudPinManager.getPinned();
        if (pinnedList.isEmpty()) {
            return;
        }

        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            return;
        }

        ClientAdvancements clientAdvancements = connection.getAdvancements();
        if (clientAdvancements == null) {
            return;
        }

        Map<AdvancementHolder, AdvancementProgress> progressMap =
            ((ClientAdvancementsAccessor) clientAdvancements).advancementProgress$getProgress();

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int cardWidth = 160;
        int textAvailableW = cardWidth - 25;

        boolean isBottom = config.hudPosition == AdvancementProgressConfig.HudPosition.BOTTOM_LEFT || config.hudPosition == AdvancementProgressConfig.HudPosition.BOTTOM_RIGHT;
        boolean isLeft = config.hudPosition == AdvancementProgressConfig.HudPosition.TOP_LEFT || config.hudPosition == AdvancementProgressConfig.HudPosition.BOTTOM_LEFT;

        int x = isLeft ? 4 : (screenWidth - cardWidth - 4);
        int y = 4;

        if (isBottom) {
            int totalCardsHeight = 0;
            for (Identifier id : pinnedList) {
                AdvancementHolder h = clientAdvancements.get(id);
                if (h == null) continue;
                DisplayInfo d = h.value().display().orElse(null);
                Component title = d != null ? d.getTitle() : Component.literal(id.getPath());
                Component desc = d != null ? d.getDescription() : Component.empty();
                AdvancementProgress prog = progressMap != null ? progressMap.get(h) : null;
                boolean done = prog != null && prog.isDone();
                Component titleToRender = done ? Component.literal("✔ ").append(title) : title;

                int titleLines = Math.max(1, font.split(titleToRender, textAvailableW).size());
                int descLines = !desc.getString().isEmpty() ? font.split(desc, textAvailableW).size() : 1;
                boolean isComposite = h.value().requirements().size() > 1;

                int contentH = (titleLines + descLines) * 9 + (isComposite ? 15 : 0);
                int cardH = Math.max(24, 3 + contentH + 3);
                totalCardsHeight += cardH + 4;
            }
            y = Math.max(4, screenHeight - totalCardsHeight - 4);
        }

        for (Identifier id : pinnedList) {
            AdvancementHolder holder = clientAdvancements.get(id);
            if (holder == null) {
                continue;
            }

            Advancement adv = holder.value();
            DisplayInfo display = adv.display().orElse(null);
            AdvancementProgress prog = progressMap != null ? progressMap.get(holder) : null;

            int totalCriteria = adv.requirements().size();
            boolean isComposite = totalCriteria > 1;
            boolean done = prog != null && prog.isDone();

            if (done && config.autoUnpinOnComplete) {
                HudPinManager.unpin(id);
                continue;
            }

            Component title = display != null ? display.getTitle() : Component.literal(id.getPath());
            Component desc = display != null ? display.getDescription() : Component.empty();

            Component titleToRender = done ? Component.literal("✔ ").append(title) : title;
            List<FormattedCharSequence> splitTitle = font.split(titleToRender, textAvailableW);
            List<FormattedCharSequence> splitDesc = font.split(desc, textAvailableW);

            // Dynamic card height fitting all lines cleanly
            int titleLines = Math.max(1, splitTitle.size());
            int descLines = !splitDesc.isEmpty() ? splitDesc.size() : 1;
            int contentH = (titleLines + descLines) * 9 + (isComposite ? 15 : 0);
            int cardHeight = Math.max(24, 3 + contentH + 3);

            // Card background & border
            graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xAA0F1318);
            graphics.outline(x, y, cardWidth, cardHeight, done ? 0x882ECC71 : 0x55FFAA00);

            // Icon: centered if short card, or placed near top if multi-line card
            ItemStack icon = display != null ? display.getIcon().create() : new ItemStack(Items.BOOK);
            int iconY = (cardHeight <= 28) ? y + (cardHeight - 16) / 2 : y + 4;
            graphics.item(icon, x + 3, iconY);

            int textY = y + 3;

            // Render ALL title lines without truncation
            int titleColor = done ? 0xFF2ECC71 : 0xFFFFAA00;
            for (FormattedCharSequence tLine : splitTitle) {
                graphics.text(font, tLine, x + 21, textY, titleColor, true);
                textY += 9;
            }

            // Render ALL description lines without truncation
            int descColor = done ? 0xFF88DDAA : 0xFFCCCCCC;
            if (!splitDesc.isEmpty()) {
                for (FormattedCharSequence dLine : splitDesc) {
                    graphics.text(font, dLine, x + 21, textY, descColor, true);
                    textY += 9;
                }
            } else {
                Component statusText = done
                    ? Component.translatable("advancements_refined.hud.done")
                    : Component.translatable("advancements_refined.hud.in_progress");
                graphics.text(font, statusText, x + 21, textY, descColor, true);
                textY += 9;
            }

            // Progress text and micro-bar (for composite)
            if (isComposite) {
                int doneCount;
                if (prog != null && prog.isDone()) {
                    doneCount = totalCriteria;
                } else if (prog != null) {
                    doneCount = Math.min(totalCriteria, adv.requirements().count(crit -> {
                        net.minecraft.advancements.CriterionProgress cp = prog.getCriterion(crit);
                        return cp != null && cp.isDone();
                    }));
                } else {
                    doneCount = 0;
                }
                float pct = totalCriteria > 0 ? (float) doneCount / totalCriteria : 0.0f;
                String pctStr = String.format(java.util.Locale.ROOT, "%.0f%%", pct * 100.0f);
                Component progLabel = Component.literal(doneCount + "/" + totalCriteria + " (" + pctStr + ")");
                graphics.text(font, progLabel, x + 21, textY, 0xFFAAAAAA, true);
                textY += 9;

                // Micro progress bar
                int barW = cardWidth - 25;
                int barH = 2;
                int barX = x + 21;
                int barY = textY + 1;
                graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
                int fillW = (int) Math.round(barW * pct);
                if (fillW > 0) {
                    int barColor = pct >= 1.0f ? 0xFFFFD700 : 0xFF2ECC71;
                    graphics.fill(barX, barY, barX + fillW, barY + barH, barColor);
                }
            }

            y += cardHeight + 4;
        }
    }
}
