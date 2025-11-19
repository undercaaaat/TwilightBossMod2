package com.example.twilightbossaddon.client;

import com.example.twilightbossaddon.TwilightBossAddon;
import com.example.twilightbossaddon.core.ModEntities;
import com.example.twilightbossaddon.core.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TwilightBossAddon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 注册实体的渲染器
        event.registerEntityRenderer(ModEntities.NARRATOR.get(), NarratorRenderer::new);
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // 修复刷怪蛋颜色
        event.register((stack, tintIndex) -> {
            if (stack.getItem() instanceof ForgeSpawnEggItem egg) {
                return egg.getColor(tintIndex);
            }
            return -1;
        }, ModItems.NARRATOR_SPAWN_EGG.get());
    }
}