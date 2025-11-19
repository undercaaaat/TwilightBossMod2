package com.example.twilightbossaddon.entity;

import com.example.twilightbossaddon.entity.ai.InkStormGoal;
import com.example.twilightbossaddon.entity.ai.TalesOfTheFallenGoal;
import com.example.twilightbossaddon.entity.ai.WrittenFateGoal;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class NarratorEntity extends Monster {

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(NarratorEntity.class, EntityDataSerializers.INT);

    // 用于同步虚化倒计时的变量
    private static final EntityDataAccessor<Integer> PHASE_TICK = SynchedEntityData.defineId(NarratorEntity.class, EntityDataSerializers.INT);

    // 用于控制是否处于无敌技能释放期间的变量 (无需同步到客户端，仅服务端逻辑使用)
    private boolean isInvulnerablePhase = false;


    //保存技能的引用，以便在 tick 中更新冷却
    private InkStormGoal inkStormGoal;
    private WrittenFateGoal writtenFateGoal;




    private final ServerBossEvent bossEvent =
            new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    public NarratorEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(PHASE, 1);
        this.entityData.define(PHASE_TICK, 0);
    }

    // --- API 方法 ---

    // --- 虚化机制 API ---
    public int getPhasingTick() {
        return this.entityData.get(PHASE_TICK);
    }

    public void setPhasingTick(int ticks) {
        this.entityData.set(PHASE_TICK, ticks);
    }

    // 设置无敌状态（由 Goal 调用）
    public void setInvulnerablePhase(boolean isInvulnerable) {
        this.isInvulnerablePhase = isInvulnerable;
    }

    // 重写受击逻辑：如果处于无敌阶段，且伤害来源不是创造模式玩家或虚空伤害，则免疫
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        boolean isCreative = source.getEntity() instanceof Player player && player.getAbilities().instabuild;
        if (this.isInvulnerablePhase && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !isCreative) {
            return true;
        }
        // 如果处于虚化时间 (Phasing)，免疫所有非穿透性伤害
        if (this.getPhasingTick() > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    public int getPhase() {
        return this.entityData.get(PHASE);
    }

    public void setPhase(int newPhase) {
        this.entityData.set(PHASE, newPhase);
        // 刷新属性等逻辑后续添加
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // --- 注册技能 Goal ---
        this.goalSelector.addGoal(1, new TalesOfTheFallenGoal(this));

        this.inkStormGoal = new InkStormGoal(this);
        this.goalSelector.addGoal(2, this.inkStormGoal);

        this.writtenFateGoal = new WrittenFateGoal(this);
        this.goalSelector.addGoal(2, this.writtenFateGoal);

        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // --- 免疫系统 ---

    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance pPotioneffect) {
        // 如果效果类型是“有害的”(HARMFUL)，则直接免疫
        if (pPotioneffect.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
            return false;
        }
        // 同时也免疫“中性”里的负面效果（如挖掘疲劳），或者直接只允许有益效果
        // 这里我们可以简单粗暴一点：只要不是有益的，都不要
        return pPotioneffect.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL
                && super.canBeAffected(pPotioneffect);
    }


    // --- 核心伤害逻辑 ---
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 只有当攻击者是生物时才触发这些机制（忽略摔落、窒息等环境伤害）
        if (source.getEntity() instanceof LivingEntity attacker) {

            // --- 机制 0: 蔑视与僭越 (Contempt & Overstep) ---
            // 比较 Y 轴高度
            double bossY = this.getY();
            double attackerY = attacker.getY();

            // 允许 0.1 的误差，避免在同一平面时因为浮点数精度问题导致判定错误
            if (attackerY < bossY - 0.1) {
                // 蔑视：目标低于 Boss，伤害减少 20%
                amount *= 0.8f;
                // [视觉建议] 如果是客户端，可以在这里播放一个沉闷的音效，但 hurt 方法主要在服务端运行
            } else if (attackerY > bossY + 2.1) {
                // 僭越：目标高于 Boss，伤害增加 20%
                amount *= 1.2f;
            }

            // --- 机制 1: 伤害修正逻辑 (阈值检查 & 封顶) ---
            float attackerMaxHealth = attacker.getMaxHealth();

            // 机制 A: 免疫低伤 (< 40%)
            // 注意：这里使用的是经过“蔑视/僭越”修正后的 amount
            float minThreshold = attackerMaxHealth * 0.40f;
            if (amount < minThreshold) {
                // 伤害未达标
                if (!this.level().isClientSide) {
                    // 播放铁砧落地声提示无效
                    this.playSound(SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
                }
                return false; // 彻底免疫此次伤害
            }

            // 机制 B: 伤害封顶 (> 80%)
            float maxCap = attackerMaxHealth * 0.80f;
            if (amount > maxCap) {
                amount = maxCap; // 超过上限的伤害被截断
            }
        }

        // 2. 执行原版扣血逻辑 (传入修正后的 amount)
        boolean damaged = super.hurt(source, amount);

        // 3. 触发虚化 (Trigger Phasing)
        if (damaged && !this.level().isClientSide) {
            this.setPhasingTick(15); // 0.75秒 = 15 ticks
            clearHarmfulEffects();   // 清空负面效果

            // 播放虚化音效
            this.playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 2.0f);
        }

        return damaged;
    }


    // 辅助方法：只清除有害效果
    private void clearHarmfulEffects() {
        List<MobEffect> effectsToRemove = new ArrayList<>();
        for (MobEffectInstance instance : this.getActiveEffects()) {
            if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                effectsToRemove.add(instance.getEffect());
            }
        }
        for (MobEffect effect : effectsToRemove) {
            this.removeEffect(effect);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // 必须在 tick 中更新技能冷却
        if (this.inkStormGoal != null) this.inkStormGoal.decreaseCooldown();
        if (this.writtenFateGoal != null) this.writtenFateGoal.decreaseCooldown();

        // --- 虚化逻辑更新 ---
        int currentPhasing = this.getPhasingTick();
        if (currentPhasing > 0) {
            this.setPhasingTick(currentPhasing - 1);

            // [视觉] 虚化期间生成特殊粒子 (末影人粒子)
            if (this.level().isClientSide) {
                for(int i = 0; i < 2; ++i) {
                    this.level().addParticle(ParticleTypes.PORTAL,
                            this.getRandomX(0.5D), this.getRandomY() - 0.25D, this.getRandomZ(0.5D),
                            (this.random.nextDouble() - 0.5D) * 2.0D, -this.random.nextDouble(), (this.random.nextDouble() - 0.5D) * 2.0D);
                }
            }
        }

        // [视觉实现] 阶段转换时的持续特效
        if (this.getPhase() == 2 && this.level().isClientSide) {
            // 比如二阶段身体会有扭曲的黑色粒子
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 1.5, this.getZ(), 0, 0, 0);
        }
    }

    @Override
    public void die(DamageSource pDamageSource) {
        // 核心：拦截死亡事件
        // 如果在一阶段被击杀，且不是被创造模式玩家/虚空秒杀
        if (this.getPhase() == 1 && !pDamageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            transitionToPhaseTwo();
        } else {
            super.die(pDamageSource); // 真的死了
        }
    }

    private void transitionToPhaseTwo() {
        // 1. 切换阶段
        this.setPhase(2);

        // 2. 数值进化：血量上限提升至 500
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
        this.setHealth(500.0F); // 满血复活

        // 3. 播放效果
        if (this.level() instanceof ServerLevel serverLevel) {
            // [视觉实现] 这里需要一个巨大的爆炸或黑洞特效
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
        }

        // 4. 重置状态
        this.setCustomName(Component.literal("讲述者-巨匠: 章节变迁"));
        this.removeAllEffects(); // 清除所有药水效果

        // 更新 Boss条样式（二阶段通常更压抑）
        this.bossEvent.setColor(BossEvent.BossBarColor.RED);
        this.bossEvent.setDarkenScreen(true);
    }



    @Override
    public void startSeenByPlayer(ServerPlayer pPlayer) {
        super.startSeenByPlayer(pPlayer);
        this.bossEvent.addPlayer(pPlayer);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer pPlayer) {
        super.stopSeenByPlayer(pPlayer);
        this.bossEvent.removePlayer(pPlayer);
    }

    @Override
    public void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }
}