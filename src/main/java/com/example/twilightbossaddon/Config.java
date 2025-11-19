package com.example.twilightbossaddon;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = TwilightBossAddon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.DoubleValue NARRATOR_MAX_HEALTH = BUILDER
            .comment("讲述者 Boss 的最大生命值 / Max Health of Narrator Boss")
            .defineInRange("narratorMaxHealth", 300.0, 1.0, 10000.0);

    public static final ForgeConfigSpec.DoubleValue NARRATOR_ATTACK_DAMAGE = BUILDER
            .comment("讲述者 Boss 的基础攻击力 / Base Attack Damage of Narrator Boss")
            .defineInRange("narratorAttackDamage", 10.0, 0.0, 1000.0);

    public static final ForgeConfigSpec.DoubleValue MINION_HEALTH_MULTIPLIER = BUILDER
            .comment("召唤物血量倍率 (1.2 = 120%)")
            .defineInRange("minionHealthMultiplier", 1.2, 0.1, 10.0);

    public static final ForgeConfigSpec.DoubleValue MINION_DAMAGE_MULTIPLIER = BUILDER
            .comment("召唤物伤害倍率 (1.2 = 120%)")
            .defineInRange("minionDamageMultiplier", 1.2, 0.1, 10.0);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double narratorMaxHealth;
    public static double narratorAttackDamage;
    public static double minionHealthMultiplier;
    public static double minionDamageMultiplier;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        narratorMaxHealth = NARRATOR_MAX_HEALTH.get();
        narratorAttackDamage = NARRATOR_ATTACK_DAMAGE.get();
        minionHealthMultiplier = MINION_HEALTH_MULTIPLIER.get();
        minionDamageMultiplier = MINION_DAMAGE_MULTIPLIER.get();
    }
}