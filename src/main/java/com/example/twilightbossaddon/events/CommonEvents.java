package com.example.twilightbossaddon.events;

import com.example.twilightbossaddon.TwilightBossAddon;
import com.example.twilightbossaddon.entity.NarratorEntity;
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

    public static final String VULNERABLE_TAG = "NarratorVulnerability";
    public static final String VULNERABLE_EXPIRY_TAG = "NarratorVulnerabilityExpiry";

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        net.minecraft.world.damagesource.DamageSource source = event.getSource();

        // --- 1. 易伤检测 (优先) ---
        if (target.getTags().contains(VULNERABLE_TAG)) {
            long expiry = target.getPersistentData().getLong(VULNERABLE_EXPIRY_TAG);
            if (target.level().getGameTime() < expiry) {
                event.setAmount(event.getAmount() * 1.15f);
            } else {
                target.removeTag(VULNERABLE_TAG);
            }
        }

        // --- 2. 符文伤害逻辑 (一阶段) ---
        if (source.getDirectEntity() instanceof Snowball rune && rune.getTags().contains("NarratorRune")) {
            // 防止无限递归
            if (source.is(DamageTypes.MAGIC)) return;

            float maxHealth = target.getMaxHealth();
            // 基础伤害：7% 最大生命值，保底 4 点
            float baseRuneDamage = Math.max(4.0f, maxHealth * 0.07f);
            float extraDamage = 0.0f;

            if (rune.getTags().contains("RuneType:Fire")) {
                extraDamage += target.getHealth() * 0.10f; // 火焰：10% 当前生命
                target.setSecondsOnFire(3);
            } else if (rune.getTags().contains("RuneType:Ice")) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 1));
                target.addTag(VULNERABLE_TAG);
                target.getPersistentData().putLong(VULNERABLE_EXPIRY_TAG, target.level().getGameTime() + 100);
            } else if (rune.getTags().contains("RuneType:Dark")) {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0));
            }

            float totalDamage = baseRuneDamage + extraDamage;

            // 核心修复：取消原投掷物伤害(0)，手动施加魔法伤害
            event.setCanceled(true);
            target.invulnerableTime = 0; // 穿透无敌帧，确保连发命中
            target.hurt(target.damageSources().magic(), totalDamage);
            return; // 结束处理
        }

        // --- 3. 二阶段 Boss 被动 (吸血 + 真伤) ---
        if (source.getEntity() instanceof NarratorEntity boss && boss.getPhase() == 2) {
            if (source.is(DamageTypes.MAGIC)) {
                // 仅处理吸血
                float healAmount = event.getAmount() * 0.10f;
                if (healAmount > 0) boss.heal(healAmount);
                return;
            }

            float originalDamage = event.getAmount();
            if (originalDamage < 1.0f) return;

            float physicalPart = originalDamage * 0.5f;
            float dimensionWillPart = originalDamage * 0.5f;

            // 修改本次伤害为一半
            event.setAmount(physicalPart);

            // 额外施加一半真实伤害
            target.invulnerableTime = 0;
            target.hurt(boss.damageSources().magic(), dimensionWillPart);
            return;
        }

        // --- 4. 召唤物增强 (维度意志) ---
        else if (source.getEntity() instanceof LivingEntity attacker && attacker.getTags().contains(TalesOfTheFallenGoal.MINION_TAG)) {
            if (!source.is(DamageTypes.MAGIC)) {
                float dimensionWillDamage = event.getAmount() * 0.1f;
                target.hurt(target.damageSources().magic(), dimensionWillDamage);
            }
        }
    }
}