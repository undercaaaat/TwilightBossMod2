package com.example.twilightbossaddon.core;

import com.example.twilightbossaddon.TwilightBossAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModeTabs {
    // 创建一个用于注册标签页的 DeferredRegister
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TwilightBossAddon.MODID);

    // 注册一个名为 "twilight_boss_tab" 的标签页
    public static final RegistryObject<CreativeModeTab> TWILIGHT_BOSS_TAB = CREATIVE_MODE_TABS.register("twilight_boss_tab",
            () -> CreativeModeTab.builder()
                    // 设置图标为 "灰烬之灯"
                    .icon(() -> new ItemStack(ModItems.ASH_LIGHT.get()))
                    // 设置显示的标题 (需要去语言文件 en_us.json 添加翻译)
                    .title(Component.translatable("creativetab.twilightboss_tab"))
                    // 将物品加入到这个标签页中
                    .displayItems((pParameters, pOutput) -> {
                        // 加入灰烬之灯
                        pOutput.accept(ModItems.ASH_LIGHT.get());
                        // 加入刷怪蛋
                        pOutput.accept(ModItems.NARRATOR_SPAWN_EGG.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
