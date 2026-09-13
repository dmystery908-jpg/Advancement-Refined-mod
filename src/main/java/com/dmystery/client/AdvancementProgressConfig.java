package com.dmystery.client;

import com.dmystery.AdvancementProgress;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class AdvancementProgressConfig {
    public enum HudPosition {
        TOP_RIGHT("advancements_refined.config.hud_position.top_right"),
        TOP_LEFT("advancements_refined.config.hud_position.top_left"),
        BOTTOM_RIGHT("advancements_refined.config.hud_position.bottom_right"),
        BOTTOM_LEFT("advancements_refined.config.hud_position.bottom_left");

        private final String key;

        HudPosition(String key) {
            this.key = key;
        }

        public Component getDisplayName() {
            return Component.translatable(this.key);
        }
    }

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("advancements-refined.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AdvancementProgressConfig INSTANCE = null;

    public boolean showGlobalBar = true;
    public boolean showTabBadges = true;
    public boolean showTooltipHints = true;
    public float treeZoom = 0.65f;
    public boolean hudEnabled = true;
    public HudPosition hudPosition = HudPosition.TOP_RIGHT;
    public int maxPins = 3;
    public boolean autoUnpinOnComplete = false;

    public static synchronized AdvancementProgressConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AdvancementProgressConfig();
            INSTANCE.load();
        }
        return INSTANCE;
    }

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            AdvancementProgressConfig loaded = GSON.fromJson(reader, AdvancementProgressConfig.class);
            if (loaded != null) {
                this.showGlobalBar = loaded.showGlobalBar;
                this.showTabBadges = loaded.showTabBadges;
                this.showTooltipHints = loaded.showTooltipHints;
                this.treeZoom = Mth.clamp(loaded.treeZoom, 0.5f, 1.0f);
                this.hudEnabled = loaded.hudEnabled;
                this.hudPosition = loaded.hudPosition != null ? loaded.hudPosition : HudPosition.TOP_RIGHT;
                this.maxPins = Mth.clamp(loaded.maxPins, 1, 5);
                this.autoUnpinOnComplete = loaded.autoUnpinOnComplete;
            }
        } catch (Exception e) {
            AdvancementProgress.LOGGER.error("Failed to load configuration from {}", CONFIG_PATH, e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            AdvancementProgress.LOGGER.error("Failed to save configuration to {}", CONFIG_PATH, e);
        }
    }

    public void resetDefaults() {
        this.showGlobalBar = true;
        this.showTabBadges = true;
        this.showTooltipHints = true;
        this.treeZoom = 0.65f;
        this.hudEnabled = true;
        this.hudPosition = HudPosition.TOP_RIGHT;
        this.maxPins = 3;
        this.autoUnpinOnComplete = false;
        save();
    }
}
