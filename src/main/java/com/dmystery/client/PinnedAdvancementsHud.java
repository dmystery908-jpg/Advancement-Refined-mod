package com.dmystery.client;

import com.dmystery.mixin.ClientAdvancementsAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

public class PinnedAdvancementsHud implements HudRenderCallback {
    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
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

        List<ResourceLocation> pinnedList = HudPinManager.getPinned();
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
        int cardWidth = 130;

        boolean isBottom = config.hudPosition == AdvancementProgressConfig.HudPosition.BOTTOM_LEFT || config.hudPosition == AdvancementProgressConfig.HudPosition.BOTTOM_RIGHT;
        boolean isLeft = config.hudPosition == AdvancementProgressConfig.HudPosition.TOP_LEFT || config.hudPosition == AdvancementProgressConfig.HudPosition.BOTTOM_LEFT;

        int x = isLeft ? 4 : (screenWidth - cardWidth - 4);
        int y = 4;

        if (isBottom) {
            int totalCardsHeight = 0;
            for (ResourceLocation id : pinnedList) {
                AdvancementHolder h = clientAdvancements.get(id);
                if (h == null) continue;
                DisplayInfo d = h.value().display().orElse(null);
                Component desc = d != null ? d.getDescription() : Component.empty();
                List<FormattedCharSequence> splitDesc = font.split(desc, cardWidth - 25);
                int cardH = (h.value().requirements().size() > 1) ? 22 : (splitDesc.size() >= 2 ? 31 : 22);
                totalCardsHeight += cardH + 4;
            }
            y = Math.max(4, screenHeight - totalCardsHeight - 4);
        }

        for (ResourceLocation id : pinnedList) {
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

            int textAvailableW = cardWidth - 25;
            Component titleToRender = done ? Component.literal("✔ ").append(title) : title;
            List<FormattedCharSequence> splitTitle = font.split(titleToRender, textAvailableW);
            FormattedCharSequence shortTitle = splitTitle.isEmpty() ? FormattedCharSequence.EMPTY : splitTitle.get(0);

            List<FormattedCharSequence> splitDesc = font.split(desc, textAvailableW);

            int cardHeight;
            if (isComposite) {
                cardHeight = 22;
            } else if (splitDesc.size() >= 2) {
                cardHeight = 31;
            } else {
                cardHeight = 22;
            }

            // Card background & border
            graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xAA0F1318);
            graphics.renderOutline(x, y, cardWidth, cardHeight, done ? 0x882ECC71 : 0x55FFAA00);

            // Icon vertically centered
            ItemStack icon = display != null ? display.getIcon() : new ItemStack(Items.BOOK);
            graphics.renderItem(icon, x + 3, y + (cardHeight - 16) / 2);

            // Title
            int titleColor = done ? 0xFF2ECC71 : 0xFFFFAA00;
            graphics.drawString(font, shortTitle, x + 21, y + 3, titleColor, true);

            // Progress text and micro-bar (for composite) OR description (for simple)
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
                graphics.drawString(font, progLabel, x + 21, y + 11, 0xFFAAAAAA, true);

                // Micro progress bar
                int barW = cardWidth - 25;
                int barH = 1;
                int barX = x + 21;
                int barY = y + 20;
                graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
                int fillW = (int) Math.round(barW * pct);
                if (fillW > 0) {
                    int barColor = pct >= 1.0f ? 0xFFFFD700 : 0xFF2ECC71;
                    graphics.fill(barX, barY, barX + fillW, barY + barH, barColor);
                }
            } else {
                int descColor = done ? 0xFF88DDAA : 0xFFCCCCCC;
                if (!splitDesc.isEmpty()) {
                    graphics.drawString(font, splitDesc.get(0), x + 21, y + 12, descColor, true);
                    if (splitDesc.size() >= 2) {
                        graphics.drawString(font, splitDesc.get(1), x + 21, y + 21, descColor, true);
                    }
                } else {
                    Component statusText = done
                        ? Component.translatable("advancements_refined.hud.done")
                        : Component.translatable("advancements_refined.hud.in_progress");
                    graphics.drawString(font, statusText, x + 21, y + 12, descColor, true);
                }
            }

            y += cardHeight + 4;
        }
    }
}
