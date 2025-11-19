package com.example.twilightbossaddon.core;

import com.example.twilightbossaddon.TwilightBossAddon;
import com.example.twilightbossaddon.entity.NarratorEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    /**
     * 创建一个实体类型的延迟注册器
     */
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TwilightBossAddon.MODID);

    /**
     * 注册 "讲述者" Boss 实体
     * "narrator" 是注册ID
     * .of(NarratorEntity::new, ...) 引用了实体的构造函数（我们下一步就创建它）
     * .sized(0.6F, 1.8F) 是实体的碰撞箱大小（宽0.6，高1.8，和玩家一样）
     * .clientTrackingRange(10) 是一个重要参数，让Boss在很远的地方（10个区块）就能被客户端渲染
     */
    public static final RegistryObject<EntityType<NarratorEntity>> NARRATOR =
            ENTITIES.register("narrator",
                    () -> EntityType.Builder.of(NarratorEntity::new, MobCategory.MONSTER) // MONSTER 类别（敌对生物）
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(10) // 暮色API示例中Naga的追踪范围也是10
                            .build("twilightbossaddon:narrator"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}