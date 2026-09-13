package com.dmystery;

import com.dmystery.client.AdvancementProgressConfigScreen;
import com.dmystery.client.PinnedAdvancementsHud;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdvancementProgressClient implements ClientModInitializer {
    public static final String MOD_ID = AdvancementProgress.MOD_ID;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyMapping OPEN_SETTINGS_KEY = KeyBindingHelper.registerKeyBinding(
        new KeyMapping(
            "key.advancements_refined.open_settings",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.advancements_refined"
        )
    );

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(new PinnedAdvancementsHud());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_SETTINGS_KEY.consumeClick()) {
                if (client != null) {
                    client.setScreen(new AdvancementProgressConfigScreen(client.screen));
                }
            }
        });

        LOGGER.info("[Advancements Refined] Client initialized with HUD Pinning and Settings support.");
    }
}
