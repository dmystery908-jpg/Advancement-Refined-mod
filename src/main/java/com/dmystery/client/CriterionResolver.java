package com.dmystery.client;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CriterionResolver {
    public record CriterionDisplay(ItemStack icon, Component name) {}

    private static final Map<String, CriterionDisplay> CACHE = new HashMap<>();

    public static CriterionDisplay resolve(String rawName) {
        return resolve(null, rawName);
    }

    public static CriterionDisplay resolve(@Nullable Identifier advId, String rawName) {
        String cacheKey = (advId != null ? advId.toString() : "") + "#" + rawName;
        return CACHE.computeIfAbsent(cacheKey, k -> computeDisplay(advId, rawName));
    }

    private static boolean isEntityContext(@Nullable Identifier advId) {
        if (advId == null) return false;
        String path = advId.getPath();
        return path.contains("bred_all_animals") || path.contains("breed")
            || path.contains("kill_all_mobs") || path.contains("kill_a_mob")
            || path.contains("monsters_hunted") || path.contains("mob")
            || path.contains("animal") || path.contains("entity");
    }

    private static CriterionDisplay computeDisplay(@Nullable Identifier advId, String raw) {
        String cleanName = raw.trim();
        Identifier id = Identifier.tryParse(cleanName.contains(":") ? cleanName : "minecraft:" + cleanName);

        if (id != null) {
            boolean preferEntity = isEntityContext(advId);

            if (preferEntity) {
                // Check EntityType first for entity-focused advancements (e.g. bred_all_animals, kill_all_mobs)
                Optional<Holder.Reference<EntityType<?>>> entityHolder = BuiltInRegistries.ENTITY_TYPE.get(id);
                if (entityHolder.isPresent()) {
                    EntityType<?> entityType = entityHolder.get().value();
                    Component entityName = entityType.getDescription();
                    Optional<Holder<Item>> eggHolder = SpawnEggItem.byId(entityType);
                    ItemStack icon = eggHolder.map(ItemStack::new).orElse(new ItemStack(Items.ZOMBIE_HEAD));
                    return new CriterionDisplay(icon, entityName);
                }
            }

            // 1. Check if it matches an Item
            Optional<Holder.Reference<Item>> itemHolder = BuiltInRegistries.ITEM.get(id);
            if (itemHolder.isPresent() && itemHolder.get().value() != Items.AIR) {
                ItemStack stack = new ItemStack(itemHolder.get().value());
                return new CriterionDisplay(stack, stack.getHoverName());
            }

            // 2. Check if it matches an EntityType (if not already checked)
            if (!preferEntity) {
                Optional<Holder.Reference<EntityType<?>>> entityHolder = BuiltInRegistries.ENTITY_TYPE.get(id);
                if (entityHolder.isPresent()) {
                    EntityType<?> entityType = entityHolder.get().value();
                    Component entityName = entityType.getDescription();
                    Optional<Holder<Item>> eggHolder = SpawnEggItem.byId(entityType);
                    ItemStack icon = eggHolder.map(ItemStack::new).orElse(new ItemStack(Items.ZOMBIE_HEAD));
                    return new CriterionDisplay(icon, entityName);
                }
            }

            // 3. Check for biome (e.g. adventuring time)
            if (cleanName.startsWith("minecraft:") || !cleanName.contains("/")) {
                String biomeKey = "biome." + id.getNamespace() + "." + id.getPath();
                if (Language.getInstance().has(biomeKey)) {
                    Component biomeComp = Component.translatable(biomeKey);
                    return new CriterionDisplay(new ItemStack(Items.COMPASS), biomeComp);
                }
            }
        }

        // 4. Special cases (textures like cats, frogs, etc.)
        if (cleanName.contains("/") && cleanName.endsWith(".png")) {
            String fileName = cleanName.substring(cleanName.lastIndexOf('/') + 1, cleanName.length() - 4);
            String formatted = formatWord(fileName);
            return new CriterionDisplay(new ItemStack(Items.LEAD), Component.literal(formatted));
        }

        // 5. Fallback formatting
        String formatted = formatWord(cleanName.contains(":") ? cleanName.substring(cleanName.indexOf(':') + 1) : cleanName);
        return new CriterionDisplay(new ItemStack(Items.BOOK), Component.literal(formatted));
    }

    private static String formatWord(String text) {
        String[] parts = text.replace('_', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
