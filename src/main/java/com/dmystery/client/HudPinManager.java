package com.dmystery.client;

import com.dmystery.AdvancementProgress;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HudPinManager {
    public static final int MAX_PINNED = 3;
    private static final Map<String, List<Identifier>> WORLD_PINS = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("advancements_refined_pins.json");
    private static boolean loaded = false;

    public static String getCurrentWorldKey() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            try {
                net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
                java.nio.file.Path worldPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                if (worldPath != null && worldPath.getFileName() != null) {
                    return "local_" + worldPath.getFileName().toString();
                }
                return "local_" + server.getWorldData().getLevelName();
            } catch (Exception ignored) {}
        }
        if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) {
            return "server_" + mc.getCurrentServer().ip.replace(':', '_');
        }
        if (mc.level != null) {
            return "world_" + mc.level.dimension().identifier().toString().replace(':', '_');
        }
        return "default";
    }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        load();
    }

    public static synchronized List<Identifier> getPinned() {
        ensureLoaded();
        String key = getCurrentWorldKey();
        List<Identifier> list = WORLD_PINS.get(key);
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    public static synchronized boolean isPinned(Identifier id) {
        if (id == null) return false;
        ensureLoaded();
        String key = getCurrentWorldKey();
        List<Identifier> list = WORLD_PINS.get(key);
        return list != null && list.contains(id);
    }

    public static int getMaxPinned() {
        return AdvancementProgressConfig.getInstance().maxPins;
    }

    public static synchronized boolean pin(Identifier id) {
        if (id == null) return false;
        ensureLoaded();
        String key = getCurrentWorldKey();
        List<Identifier> list = WORLD_PINS.computeIfAbsent(key, k -> new ArrayList<>());
        if (list.contains(id)) return true;
        if (list.size() >= getMaxPinned()) return false;
        list.add(id);
        save();
        return true;
    }

    public static synchronized boolean unpin(Identifier id) {
        if (id == null) return false;
        ensureLoaded();
        String key = getCurrentWorldKey();
        List<Identifier> list = WORLD_PINS.get(key);
        if (list != null && list.remove(id)) {
            save();
            return true;
        }
        return false;
    }

    public static synchronized boolean togglePin(Identifier id) {
        if (isPinned(id)) {
            unpin(id);
            return false;
        } else {
            return pin(id);
        }
    }

    public static synchronized int getPinnedCount() {
        ensureLoaded();
        String key = getCurrentWorldKey();
        List<Identifier> list = WORLD_PINS.get(key);
        return list != null ? list.size() : 0;
    }

    public static synchronized void clearAll() {
        ensureLoaded();
        String key = getCurrentWorldKey();
        List<Identifier> list = WORLD_PINS.get(key);
        if (list != null && !list.isEmpty()) {
            list.clear();
            save();
        }
    }

    private static void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            com.google.gson.JsonElement root = com.google.gson.JsonParser.parseReader(reader);
            WORLD_PINS.clear();
            if (root != null && root.isJsonObject()) {
                com.google.gson.JsonObject obj = root.getAsJsonObject();
                for (String key : obj.keySet()) {
                    com.google.gson.JsonElement val = obj.get(key);
                    if (val != null && val.isJsonArray()) {
                        List<Identifier> list = new ArrayList<>();
                        for (com.google.gson.JsonElement item : val.getAsJsonArray()) {
                            if (item.isJsonPrimitive()) {
                                Identifier id = Identifier.tryParse(item.getAsString());
                                if (id != null && !list.contains(id) && list.size() < getMaxPinned()) {
                                    list.add(id);
                                }
                            }
                        }
                        WORLD_PINS.put(key, list);
                    }
                }
            }
        } catch (Exception e) {
            AdvancementProgress.LOGGER.error("Failed to load pinned advancements config", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                Map<String, List<String>> saveMap = new HashMap<>();
                for (Map.Entry<String, List<Identifier>> entry : WORLD_PINS.entrySet()) {
                    List<String> strList = new ArrayList<>();
                    for (Identifier id : entry.getValue()) {
                        strList.add(id.toString());
                    }
                    saveMap.put(entry.getKey(), strList);
                }
                GSON.toJson(saveMap, writer);
            }
        } catch (Exception e) {
            AdvancementProgress.LOGGER.error("Failed to save pinned advancements config", e);
        }
    }
}
