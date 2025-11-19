暮色森林附属模组：讲述者 Boss (Twilight Boss Addon) 技术开发文档
版本: 0.0.1-Dev 游戏版本: Minecraft 1.20.1 (Java Edition) 加载器: Forge (推荐 47.2.0+) 核心依赖: Twilight Forest (暮色森林)

1. 项目概述与架构
本项目旨在为暮色森林模组添加一个名为“讲述者 (Narrator)”的最终 Boss。项目采用标准的 Forge 模组架构，实现了前后端分离（逻辑与渲染分离）。

1.1 包结构 (com.example.twilightbossaddon)
TwilightBossAddon.java (Main): 模组主入口。负责构建事件总线（Event Bus），加载注册表，并初始化通用设置（CommonSetup）。

Config.java: 配置文件管理。使用 ForgeConfigSpec 定义了 Boss 的基础数值（血量、攻击力），允许玩家在 .toml 文件中修改。

core/ (Registry Layer): 负责所有游戏元素的注册。

ModItems: 物品注册（灰烬之灯、刷怪蛋）。

ModEntities: 实体注册（讲述者 Boss）。

ModCreativeModeTabs: 创造模式物品栏注册。

ModEventBusEvents: 处理实体属性注册（Attribute Creation）。

entity/ (Logic Layer): 实体行为逻辑。

NarratorEntity: Boss 的核心类，继承自 Monster。包含状态机、AI 目标（Goals）、网络同步数据。

client/ (Visual Layer): 客户端渲染相关。

ModClientEvents: 客户端事件监听，负责绑定渲染器。

NarratorRenderer: 渲染器逻辑（目前使用 HumanoidModel 占位）。

2. 核心代码特征与接口说明
2.1 实体类 (NarratorEntity)
这是开发的核心区域，所有的 Boss 行为都在此定义。

继承关系: Monster (未来可视情况改为 TFPart 或其他暮色基类，目前标准怪物类足矣)。

关键变量:

EntityDataAccessor<Integer> PHASE: [核心机制] 用于同步 Boss 阶段（1=讲述者, 2=狂怒, 3=真身）。通过 SynchedEntityData 在服务端和客户端间自动同步。

ServerBossEvent bossEvent: 管理屏幕上方的紫色 Boss 血条。

核心方法:

createAttributes(): 定义最大生命值（从 Config 读取）、移动速度、攻击力、击退抗性。

registerGoals(): [AI 入口] 注册 AI 优先级。目前包含：

FloatGoal (防溺水)

MeleeAttackGoal (近战攻击 - 待替换为技能 Goal)

LookAtPlayerGoal (注视玩家)

NearestAttackableTargetGoal (索敌逻辑)

setPhase(int phase) / getPhase(): 切换和获取当前战斗阶段的 API。

customServerAiStep(): 每 tick 运行的逻辑，用于更新 Boss 血条进度和检测阶段转换条件。

2.2 注册系统 (DeferredRegister)
项目完全使用 Forge 的 DeferredRegister 系统，确保线程安全和模组兼容性。

物品 (ModItems):

ASH_LIGHT: Item 类型，设定为不可堆叠。

NARRATOR_SPAWN_EGG: ForgeSpawnEggItem 类型。注意：颜色参数 0x4A004A (主色) 和 0xFF0000 (副色) 目前在游戏中未渲染，需要客户端颜色注册（见下文）。

创造模式页 (ModCreativeModeTabs):

TWILIGHT_BOSS_TAB: 独立的分页，自动填充上述物品。

2.3 渲染系统 (NarratorRenderer)
当前状态: 临时使用 HumanoidMobRenderer 和 HumanoidModel（类人生物模型），贴图借用原版僵尸。

扩展性: 构造函数中已预留 EntityRendererProvider.Context，未来可轻松替换为 GeoEntityRenderer (如果使用 Geckolib) 或自定义的 NarratorModel。

3. 待完成开发任务 (Roadmap)
任务一：修复刷怪蛋颜色 (Visual Fix) - 高优先级
问题描述: 刷怪蛋目前显示为白色/灰色，未应用定义的颜色。 解决方案: 在 client/ 包中新建或在 ModClientEvents 中添加 RegisterColorHandlersEvent.Item 监听。

代码片段 (需添加到 ModClientEvents.java):

Java

@SubscribeEvent
public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
    event.register((stack, tintIndex) -> {
        // 这是一个通用逻辑，适用于所有 ForgeSpawnEggItem
        if (stack.getItem() instanceof ForgeSpawnEggItem egg) {
            return egg.getColor(tintIndex);
        }
        return -1;
    }, ModItems.NARRATOR_SPAWN_EGG.get());
}
任务二：视觉资源实装 (Assets Implementation)
目标: 替换目前的“僵尸”占位符。

模型制作: 使用 Blockbench 制作三个阶段的 Boss 模型（建议导出为 Java Class 或 Geckolib 格式）。

贴图绘制: 绘制对应的纹理 (.png)。

渲染器重写:

创建 NarratorModel.java (粘贴 Blockbench 导出的代码)。

修改 NarratorRenderer.java 以加载新的模型和贴图。

阶段切换视觉: 在 NarratorRenderer 中读取 entity.getPhase()，根据阶段更换贴图或模型层（Layer）。

任务三：Boss 技能与阶段逻辑 (AI & Logic)
根据设计文档，需要实现以下逻辑（建议为每个技能创建独立的 Goal 类）：

阶段转换器 (State Machine):

在 NarratorEntity.tick() 中检测血量。

当血量 < 60% -> setPhase(2) -> 触发“狂怒”特效。

当血量 < 30% -> setPhase(3) -> 触发“真身”变换。

一阶段技能 (书本仆从):

编写 SummonMinionGoal: 在 Boss 周围生成 TwilightForest 模组中的 Minion Ghast 或自定义实体。

二阶段技能 (眼泪/幽灵):

编写 ProjectileAttackGoal: 类似于恶魂，但发射自定义投掷物（Tears）。

三阶段技能 (维度意志):

实现“禁疗”和“虚弱”光环（通过 AreaEffectCloud 或直接操作玩家 Potion Effects）。

任务四：物品功能实现 (Item Functionality)
灰烬之灯 (Ash Light):

目前只是一个贴图。需要创建一个自定义 AshLightItem 类，重写 use 或 inventoryTick 方法，实现“解除麻痹”或“致盲 Boss”的功能。

4. 开发者注意事项
Mixin 警告: 如果需要修改暮色森林原有的逻辑（如修改传送门生成），可能需要使用 Mixin，目前暂不需要。

依赖库: 确保开发环境 libs 文件夹或 Gradle 缓存中始终包含 twilightforest 的 jar 包，否则无法引用暮色特有的实体或方块。

备份: 在进行大规模重构（如引入 Geckolib 动画库）前，务必备份当前可运行的代码。