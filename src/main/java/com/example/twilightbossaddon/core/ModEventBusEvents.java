package com.example.twilightbossaddon.core;

import com.example.twilightbossaddon.TwilightBossAddon;
import com.example.twilightbossaddon.entity.NarratorEntity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 这个类专门用于监听 Mod 事件总线上的事件
 */
@Mod.EventBusSubscriber(modid = TwilightBossAddon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEventBusEvents {
    /**
     * 当游戏注册实体属性时，此方法被调用
     */
    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        // 将 NarratorEntity.createAttributes() 中定义的属性，注册给 NARRATOR 实体
        event.put(ModEntities.NARRATOR.get(), NarratorEntity.createAttributes().build());
    }
}