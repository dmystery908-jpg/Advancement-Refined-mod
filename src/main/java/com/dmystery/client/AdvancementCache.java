package com.dmystery.client;

import com.dmystery.mixin.AdvancementTabAccessor;
import com.dmystery.mixin.AdvancementWidgetAccessor;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AdvancementCache {
    public record TabStats(int completed, int total, float percent) {}

    private static boolean dirty = true;
    private static int totalCompleted = 0;
    private static int totalCount = 0;
    private static float totalPercent = 0.0f;
    private static Component cachedTotalText = Component.empty();
    private static Map<AdvancementTab, TabStats> tabStats = Collections.emptyMap();

    public static void markDirty() {
        dirty = true;
    }

    public static boolean isDirty() {
        return dirty;
    }

    public static void clear() {
        dirty = true;
        totalCompleted = 0;
        totalCount = 0;
        totalPercent = 0.0f;
        cachedTotalText = Component.empty();
        tabStats = Collections.emptyMap();
    }

    public static void updateIfDirty(Map<AdvancementHolder, AdvancementTab> tabs) {
        if (!dirty) {
            return;
        }
        if (tabs == null || tabs.isEmpty()) {
            clear();
            dirty = false;
            return;
        }
        recalculate(tabs);
    }

    public static void recalculate(Map<AdvancementHolder, AdvancementTab> tabs) {
        dirty = false;
        int sumCompleted = 0;
        int sumTotal = 0;

        if (tabs == null || tabs.isEmpty()) {
            clear();
            dirty = false;
            return;
        }

        Map<AdvancementTab, TabStats> newTabStats = new HashMap<>();

        for (AdvancementTab tab : tabs.values()) {
            if (!(tab instanceof AdvancementTabAccessor tabAccessor)) {
                continue;
            }

            Map<AdvancementHolder, AdvancementWidget> widgets = tabAccessor.advancementProgress$getWidgets();
            int tabCompleted = 0;
            int tabTotal = 0;

            if (widgets != null) {
                for (AdvancementWidget widget : widgets.values()) {
                    tabTotal++;
                    if (widget instanceof AdvancementWidgetAccessor widgetAccessor) {
                        AdvancementProgress progress = widgetAccessor.advancementProgress$getProgress();
                        if (progress != null && progress.isDone()) {
                            tabCompleted++;
                        }
                    }
                }
            }

            float tabPct = tabTotal > 0 ? (float) tabCompleted / tabTotal : 0.0f;
            newTabStats.put(tab, new TabStats(tabCompleted, tabTotal, tabPct));
            sumCompleted += tabCompleted;
            sumTotal += tabTotal;
        }

        totalCompleted = sumCompleted;
        totalCount = sumTotal;
        totalPercent = sumTotal > 0 ? (float) sumCompleted / sumTotal : 0.0f;
        tabStats = newTabStats;

        String percentFormatted = String.format(java.util.Locale.ROOT, "%.1f", totalPercent * 100.0f);
        cachedTotalText = Component.translatable("advancement_progress.total", totalCompleted, totalCount, percentFormatted);
    }

    public static int getTotalCompleted() {
        return totalCompleted;
    }

    public static int getTotalCount() {
        return totalCount;
    }

    public static float getTotalPercent() {
        return totalPercent;
    }

    public static Component getCachedTotalText() {
        return cachedTotalText;
    }

    public static TabStats getTabStats(AdvancementTab tab) {
        return tabStats.getOrDefault(tab, new TabStats(0, 0, 0.0f));
    }
}
