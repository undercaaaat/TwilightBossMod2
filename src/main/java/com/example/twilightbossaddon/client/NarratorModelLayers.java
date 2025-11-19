package com.example.twilightbossaddon.client;

import com.example.twilightbossaddon.TwilightBossAddon;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * 这个类用来注册我们模型的“图层”，让游戏知道去哪里加载它
 */
public class NarratorModelLayers {
    public static final ModelLayerLocation NARRATOR_LAYER =
            new ModelLayerLocation(new ResourceLocation(TwilightBossAddon.MODID, "narrator"), "main");
}