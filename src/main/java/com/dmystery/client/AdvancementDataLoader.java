package com.dmystery.client;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdvancementDataLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static List<AdvancementHolder> cachedVanillaAdvancements = null;

    public static void ensureAdvancementsLoaded(ClientAdvancements clientAdvancements) {
        if (clientAdvancements == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer singleplayerServer = minecraft.getSingleplayerServer();

        Collection<AdvancementHolder> candidates = null;

        if (singleplayerServer != null && singleplayerServer.getAdvancements() != null) {
            candidates = singleplayerServer.getAdvancements().getAllAdvancements();
        } else {
            candidates = getOrCreateVanillaAdvancements();
        }

        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        List<AdvancementHolder> missing = new ArrayList<>();
        for (AdvancementHolder candidate : candidates) {
            if (candidate.value().display().isPresent() && clientAdvancements.getTree().get(candidate.id()) == null) {
                missing.add(candidate);
            }
        }

        if (!missing.isEmpty()) {
            clientAdvancements.getTree().addAll(missing);
        }
    }

    private static synchronized List<AdvancementHolder> getOrCreateVanillaAdvancements() {
        if (cachedVanillaAdvancements != null) {
            return cachedVanillaAdvancements;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return List.of();
        }

        RegistryAccess registryAccess = connection.registryAccess();

        try (MultiPackResourceManager resourceManager = new MultiPackResourceManager(
                PackType.SERVER_DATA,
                List.of(minecraft.getVanillaPackResources())
        )) {
            Map<Identifier, Advancement> rawAdvancements = new HashMap<>();
            SimpleJsonResourceReloadListener.scanDirectory(
                    resourceManager,
                    FileToIdConverter.registry(Registries.ADVANCEMENT),
                    registryAccess.createSerializationContext(JsonOps.INSTANCE),
                    Advancement.CODEC,
                    rawAdvancements
            );

            List<AdvancementHolder> holders = new ArrayList<>();
            for (Map.Entry<Identifier, Advancement> entry : rawAdvancements.entrySet()) {
                if (entry.getValue().display().isPresent()) {
                    holders.add(new AdvancementHolder(entry.getKey(), entry.getValue()));
                }
            }

            cachedVanillaAdvancements = List.copyOf(holders);
            LOGGER.info("Advancements Refined: Loaded {} vanilla display advancements", cachedVanillaAdvancements.size());
            return cachedVanillaAdvancements;
        } catch (Exception e) {
            LOGGER.error("Advancements Refined: Failed to load vanilla advancements", e);
            return List.of();
        }
    }

    public static void clearCache() {
        cachedVanillaAdvancements = null;
    }
}
