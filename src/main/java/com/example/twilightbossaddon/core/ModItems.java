package com.example.twilightbossaddon.core;

import com.example.twilightbossaddon.TwilightBossAddon;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TwilightBossAddon.MODID);

    // 1. 注册 "灰烬之灯" (Ash Light)
    public static final RegistryObject<Item> ASH_LIGHT = ITEMS.register("ash_light",
            () -> new Item(new Item.Properties().stacksTo(1))); // 只能堆叠1个

    // 2. 注册 "讲述者" 刷怪蛋
    // 注意：使用 ForgeSpawnEggItem 而不是原版 SpawnEggItem
    // 0x4A004A (深紫) 和 0xFF0000 (红) 是蛋的配色
    public static final RegistryObject<Item> NARRATOR_SPAWN_EGG = ITEMS.register("narrator_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.NARRATOR, 0x4A004A, 0xFF0000,
                    new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}