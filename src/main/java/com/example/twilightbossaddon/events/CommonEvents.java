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

        // --- 1. 玩家易伤效果处理 (一阶段寒冰符文) ---
        if (target.getTags().contains(VULNERABLE_TAG)) {
            long expiry = target.getPersistentData().getLong(VULNERABLE_EXPIRY_TAG);
            if (target.level().getGameTime() < expiry) {
                event.setAmount(event.getAmount() * 1.15f);
            } else {
                target.removeTag(VULNERABLE_TAG);
            }
        }

        // --- 2. 二阶段 BOSS 被动: 吸血与真实伤害 ---
        if (source.getEntity() instanceof NarratorEntity boss && boss.getPhase() == 2) {
            // 避免魔法伤害再次触发此逻辑 (防止递归)
            if (source.is(DamageTypes.MAGIC)) {
                // 只有吸血，没有额外伤害
                float healAmount = event.getAmount() * 0.10f;
                if (healAmount > 0) boss.heal(healAmount);
                return;
            }

            // 伤害转化：50% 物理 + 50% 维度意志(Magic/True)
            float originalDamage = event.getAmount();
            float physicalPart = originalDamage * 0.5f;
            float dimensionWillPart = originalDamage * 0.5f;

            // 设置本次物理伤害为 50%
            event.setAmount(physicalPart);

            // 额外造成 50% 真实伤害
            target.invulnerableTime = 0;
            target.hurt(boss.damageSources().magic(), dimensionWillPart);

            return;
        }

        // --- 3. 一阶段符文与召唤物伤害 ---
        if (source.getDirectEntity() instanceof Snowball rune && rune.getTags().contains("NarratorRune")) {
            if (source.is(DamageTypes.MAGIC)) return;

            float maxHealth = target.getMaxHealth();
            float baseRuneDamage = maxHealth * 0.07f;
            float extraDamage = 0.0f;

            if (rune.getTags().contains("RuneType:Fire")) {
                extraDamage += target.getHealth() * 0.10f;
                target.setSecondsOnFire(3);
            } else if (rune.getTags().contains("RuneType:Ice")) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 1));
                target.addTag(VULNERABLE_TAG);
                target.getPersistentData().putLong(VULNERABLE_EXPIRY_TAG, target.level().getGameTime() + 100);
            } else if (rune.getTags().contains("RuneType:Dark")) {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0));
            }

            event.setCanceled(true);
            target.invulnerableTime = 0;
            target.hurt(target.damageSources().magic(), baseRuneDamage + extraDamage);
        }
        // 召唤物伤害增强逻辑 (TalesOfTheFallenGoal.MINION_TAG)
        else if (source.getEntity() instanceof LivingEntity attacker && attacker.getTags().contains(TalesOfTheFallenGoal.MINION_TAG)) {
            if (!source.is(DamageTypes.MAGIC)) {
                float dimensionWillDamage = event.getAmount() * 0.1f;
                target.invulnerableTime = 0;
                target.hurt(target.damageSources().magic(), dimensionWillDamage);
            }
        }
    }
}