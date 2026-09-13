package com.dmystery.client;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class PinnedChip {
    public final ResourceLocation id;
    public final AdvancementHolder holder;
    public Component title;
    public String displayTitle;
    public final ItemStack icon;
    public int x;
    public int y;
    public int w;
    public int h;
    public int closeX;
    public int closeY;
    public int closeW;
    public int closeH;

    public PinnedChip(ResourceLocation id, AdvancementHolder holder, Component title, ItemStack icon) {
        this.id = id;
        this.holder = holder;
        this.title = title;
        this.displayTitle = title.getString();
        this.icon = icon;
    }
}