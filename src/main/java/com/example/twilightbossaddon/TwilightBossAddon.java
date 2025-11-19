package com.example.twilightbossaddon;

import com.example.twilightbossaddon.core.ModEntities;
import com.example.twilightbossaddon.core.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.example.twilightbossaddon.core.ModCreativeModeTabs;

// @Mod 注解告诉Forge这是一个Mod主类
// "twilightbossaddon" 必须和 mods.toml 里的 modId 一致！
@Mod(TwilightBossAddon.MODID)
public class TwilightBossAddon {

    public static final String MODID = "twilightbossaddon";
    // 日志记录器
    private static final Logger LOGGER = LogUtils.getLogger();

    public TwilightBossAddon() {
        // 获取Mod事件总线
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modEventBus);

        ModEntities.register(modEventBus);

        // 注册创造模式标签页
        ModCreativeModeTabs.register(modEventBus);
        // 注册 FMLCommonSetupEvent 事件
        modEventBus.addListener(this::commonSetup);

        // 将Mod注册到Minecraft的事件总线（用于监听玩家事件等）
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // 这是Mod加载的早期阶段，适合注册网络包等
        LOGGER.info("Twilight Boss Addon: Common Setup Finished.");
    }
}