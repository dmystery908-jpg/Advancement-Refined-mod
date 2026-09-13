package com.dmystery;

import com.dmystery.client.AdvancementProgressConfigScreen;
import com.dmystery.client.PinnedAdvancementsHud;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdvancementProgressClient implements ClientModInitializer {
    public static final String MOD_ID = AdvancementProgress.MOD_ID;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
        AdvancementProgress.id("key_category")
    );

    public static final KeyMapping OPEN_SETTINGS_KEY = KeyMappingHelper.registerKeyMapping(
        new KeyMapping(
            "key.advancements_refined.open_settings",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            KEY_CATEGORY
        )
    );

    @Override
    public void onInitializeClient() {
        HudElementRegistry.addLast(
            AdvancementProgress.id("pinned_advancements"),
            new PinnedAdvancementsHud()
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_SETTINGS_KEY.consumeClick()) {
                if (client != null) {
                    client.setScreenAndShow(new AdvancementProgressConfigScreen(client.screen));
                }
            }
        });

        LOGGER.info("[Advancements Refined] Client initialized with HUD Pinning and Settings support.");
    }
}
