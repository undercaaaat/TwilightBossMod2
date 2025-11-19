package com.example.twilightbossaddon.events;

import com.example.twilightbossaddon.TwilightBossAddon;
import com.example.twilightbossaddon.core.ModEntities;
import com.example.twilightbossaddon.entity.NarratorEntity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TwilightBossAddon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEventBusEvents {

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.NARRATOR.get(), NarratorEntity.createAttributes().build());
    }
}