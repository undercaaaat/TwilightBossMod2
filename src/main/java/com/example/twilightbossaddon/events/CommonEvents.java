package com.example.twilightbossaddon.events;

import com.example.twilightbossaddon.TwilightBossAddon;
import com.example.twilightbossaddon.entity.ai.TalesOfTheFallenGoal;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "twilightbossaddon", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonEvents {

    // 易伤标记的 Tag 前缀
    public static final String VULNERABLE_TAG = "NarratorVulnerability";
    public static final String VULNERABLE_EXPIRY_TAG = "NarratorVulnerabilityExpiry";

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();

        // --- 逻辑1: 处理易伤效果 (Priority High) ---
        // 检查目标是否有易伤 Tag 且未过期
        if (target.getTags().contains(VULNERABLE_TAG)) {
            long expiry = target.getPersistentData().getLong(VULNERABLE_EXPIRY_TAG);
            if (target.level().getGameTime() < expiry) {
                // 增加 15% 伤害
                float original = event.getAmount();
                event.setAmount(original * 1.15f);
            } else {
                // 过期了，清理 Tag
                target.removeTag(VULNERABLE_TAG);
            }
        }

        // --- 逻辑2: 处理符文攻击 ---
        // 检查攻击源是否为我们的符文 (Snowball + Tag)
        if (event.getSource().getDirectEntity() instanceof Snowball rune
                && rune.getTags().contains("NarratorRune")) {

            // 防止死循环 (因为我们下面会再次造成伤害)
            if (event.getSource().is(DamageTypes.MAGIC)) return;

            // 1. 计算基础“维度意志伤害” (7% 最大生命值)
            float maxHealth = target.getMaxHealth();
            float baseRuneDamage = maxHealth * 0.07f;

            // 2. 检查特殊符文效果
            float extraDamage = 0.0f;

            if (rune.getTags().contains("RuneType:Fire")) {
                // 火焰符文: 额外 10% 当前生命值
                extraDamage += target.getHealth() * 0.10f;
                target.setSecondsOnFire(3); // 顺便点燃一下
            }
            else if (rune.getTags().contains("RuneType:Ice")) {
                // 寒冰符文: 缓慢 II (7s) + 易伤 (5s)
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 1)); // 140 ticks = 7s, Level 1 = 缓慢2

                // 施加易伤标记 (存入 PersistentData)
                target.addTag(VULNERABLE_TAG);
                target.getPersistentData().putLong(VULNERABLE_EXPIRY_TAG, target.level().getGameTime() + 100); // 100 ticks = 5s
            }
            else if (rune.getTags().contains("RuneType:Dark")) {
                // 黑暗符文: 凋零 (5s) + 黑暗 (2s)
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1)); // 凋零 II
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0));
            }

            // 3. 结算总伤害
            // 取消原本雪球的伤害 (通常是0)
            event.setCanceled(true);

            // 施加真实伤害
            // 注意：这里我们手动施加伤害，可能会再次触发 onLivingDamage，
            // 但我们在前面加了 event.getSource().is(DamageTypes.MAGIC) 检查来防止死循环
            target.invulnerableTime = 0; // 无视无敌帧确保连击生效
            target.hurt(target.damageSources().magic(), baseRuneDamage + extraDamage);
        }
    }
}