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
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class NarratorEntity extends Monster {

    // --- 同步数据定义 ---
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(NarratorEntity.class, EntityDataSerializers.INT);
    // 用于同步虚化倒计时的变量
    private static final EntityDataAccessor<Integer> PHASE_TICK = SynchedEntityData.defineId(NarratorEntity.class, EntityDataSerializers.INT);
    // [新增] 二阶段子状态: 0=无, 1=章节变迁, 2=炼狱史诗, 3=极寒寓言, 4=凋零神话
    private static final EntityDataAccessor<Integer> SUB_PHASE = SynchedEntityData.defineId(NarratorEntity.class, EntityDataSerializers.INT);

    // --- 内部状态变量 ---
    private boolean isInvulnerablePhase = false;
    private int subPhaseTimer = 0; // 二阶段计时器 (服务端专用)

    // 技能引用
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
        this.entityData.define(SUB_PHASE, 0); // 初始化子阶段为 0
    }

    // --- API 方法 ---

    public int getPhase() { return this.entityData.get(PHASE); }
    public void setPhase(int newPhase) { this.entityData.set(PHASE, newPhase); }

    public int getPhasingTick() { return this.entityData.get(PHASE_TICK); }
    public void setPhasingTick(int ticks) { this.entityData.set(PHASE_TICK, ticks); }

    public int getSubPhase() { return this.entityData.get(SUB_PHASE); }
    public void setSubPhase(int phase) { this.entityData.set(SUB_PHASE, phase); }

    public void setInvulnerablePhase(boolean isInvulnerable) {
        this.isInvulnerablePhase = isInvulnerable;
    }

    // --- 属性定义 ---
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    // --- AI 目标注册 ---
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // 技能 Goal
        this.goalSelector.addGoal(1, new TalesOfTheFallenGoal(this));

        this.inkStormGoal = new InkStormGoal(this);
        this.goalSelector.addGoal(2, this.inkStormGoal); // 内部已判断 Phase 1

        this.writtenFateGoal = new WrittenFateGoal(this);
        this.goalSelector.addGoal(2, this.writtenFateGoal); // 内部已判断 Phase 1

        // 常规 AI
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // --- 免疫系统 (整合二阶段逻辑) ---
    @Override
    public boolean canBeAffected(MobEffectInstance pPotioneffect) {
        MobEffectCategory category = pPotioneffect.getEffect().getCategory();

        // 1. 全局免疫有害效果 (如设计文档所述)
        if (category == MobEffectCategory.HARMFUL) {
            return false;
        }

        // 2. 特殊状态下的绝对免疫 (二阶段: 章节变迁)
        // 虽然上面已经免疫了所有 HARMFUL，但这里可以作为双重保险，或者如果未来要放宽全局免疫，这里可以保留
        if (this.getPhase() == 2 && this.getSubPhase() == 1 && category == MobEffectCategory.HARMFUL) {
            return false;
        }

        return category == MobEffectCategory.BENEFICIAL && super.canBeAffected(pPotioneffect);
    }

    // --- 核心受击与无敌逻辑 ---
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        boolean isCreative = source.getEntity() instanceof Player player && player.getAbilities().instabuild;

        // 1. 技能强制无敌 (如一阶段召唤时)
        if (this.isInvulnerablePhase && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !isCreative) {
            return true;
        }
        // 2. 虚化机制 (Phasing) - 受击后短暂无敌
        if (this.getPhasingTick() > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }

        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 只有当攻击者是生物时才触发复杂判定 (忽略摔落、窒息等环境伤害)
        if (source.getEntity() instanceof LivingEntity attacker) {

            // === 第一步：位置判定 (蔑视/僭越) ===
            double bossY = this.getY();
            double attackerY = attacker.getY();

            if (attackerY < bossY - 0.1) {
                // 蔑视：目标低于 Boss，伤害减少 20%
                amount *= 0.8f;
            } else if (attackerY > bossY + 2.1) { // 修正判定为高 2 格
                // 僭越：目标高于 Boss，伤害增加 20%
                amount *= 1.2f;
            }

            // === 第二步：二阶段特殊判定 ===
            if (this.getPhase() == 2) {
                int subPhase = this.getSubPhase();
                // 极寒寓言 (3): 受到火焰伤害增加 20%
                if (subPhase == 3 && source.is(DamageTypeTags.IS_FIRE)) {
                    amount *= 1.2f;
                }
                // 章节变迁 (1): 获得 50% 免伤
                if (subPhase == 1) {
                    amount *= 0.5f;
                    if (!this.level().isClientSide) {
                        this.playSound(SoundEvents.BEACON_DEACTIVATE, 1.0f, 2.0f);
                    }
                }
            }

            // === 第三步：阈值与封顶判定 ===
            float attackerMaxHealth = attacker.getMaxHealth();
            float minThreshold = attackerMaxHealth * 0.40f;
            float maxCap = attackerMaxHealth * 0.80f;

            // A. 免疫低伤
            if (amount < minThreshold) {
                if (!this.level().isClientSide) {
                    // 播放铁砧落地声提示无效
                    this.playSound(SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
                }
                return false;
            }

            // B. 伤害封顶
            if (amount > maxCap) {
                amount = maxCap;
            }
        }

        // === 第四步：执行原版扣血 ===
        boolean damaged = super.hurt(source, amount);

        // === 第五步：触发虚化 (Phasing) ===
        if (damaged && !this.level().isClientSide) {
            this.setPhasingTick(15); // 0.75秒 = 15 ticks
            clearHarmfulEffects();   // 受击清空负面效果

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

    // --- Tick 更新逻辑 ---
    @Override
    public void aiStep() {
        super.aiStep();

        // 1. 更新一阶段技能冷却
        if (this.inkStormGoal != null) this.inkStormGoal.decreaseCooldown();
        if (this.writtenFateGoal != null) this.writtenFateGoal.decreaseCooldown();

        // 2. 虚化倒计时更新
        int currentPhasing = this.getPhasingTick();
        if (currentPhasing > 0) {
            this.setPhasingTick(currentPhasing - 1);

            // 虚化粒子 (末影人粒子)
            if (this.level().isClientSide) {
                for(int i = 0; i < 2; ++i) {
                    this.level().addParticle(ParticleTypes.PORTAL,
                            this.getRandomX(0.5D), this.getRandomY() - 0.25D, this.getRandomZ(0.5D),
                            (this.random.nextDouble() - 0.5D) * 2.0D, -this.random.nextDouble(), (this.random.nextDouble() - 0.5D) * 2.0D);
                }
            }
        }

        // 3. 二阶段逻辑核心
        if (this.getPhase() == 2) {
            if (!this.level().isClientSide) {
                tickPhaseTwoLogic(); // 服务端：处理状态切换
            } else {
                spawnPhaseTwoParticles(); // 客户端：处理粒子特效
            }
        }
    }

    // --- 二阶段状态机逻辑 ---
    private void tickPhaseTwoLogic() {
        // 初始化
        if (this.getSubPhase() == 0) {
            startChapterTransition();
            return;
        }

        this.subPhaseTimer--;

        // 倒计时结束，切换状态
        if (this.subPhaseTimer <= 0) {
            if (this.getSubPhase() == 1) {
                // 章节变迁结束 -> 随机进入史诗
                startRandomSaga();
            } else {
                // 史诗结束 -> 进入章节变迁
                startChapterTransition();
            }
        }
    }

    private void startChapterTransition() {
        this.setSubPhase(1);
        this.subPhaseTimer = 40; // 2秒
        this.setCustomName(Component.literal("讲述者-巨匠: 章节变迁"));

        // 悬浮不动
        this.setNoGravity(true);
        this.setDeltaMovement(0, 0, 0);
        clearHarmfulEffects();
    }

    private void startRandomSaga() {
        int nextSaga = this.random.nextInt(3) + 2; // 随机 2, 3, 4
        this.setSubPhase(nextSaga);
        this.subPhaseTimer = 200; // 10秒
        this.setNoGravity(false); // 恢复移动

        switch (nextSaga) {
            case 2 -> this.setCustomName(Component.literal("讲述者-巨匠: 炼狱史诗"));
            case 3 -> this.setCustomName(Component.literal("讲述者-巨匠: 极寒寓言"));
            case 4 -> this.setCustomName(Component.literal("讲述者-巨匠: 凋零神话"));
        }
    }

    private void spawnPhaseTwoParticles() {
        int sub = this.getSubPhase();
        double x = this.getX();
        double y = this.getY() + 1.0;
        double z = this.getZ();

        // 二阶段常驻：扭曲黑色烟雾
        this.level().addParticle(ParticleTypes.SMOKE, x, y + 0.5, z, 0, 0, 0);

        // 子状态特效
        switch (sub) {
            case 1: // 变迁：白色光辉
                this.level().addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.05, 0);
                break;
            case 2: // 火
                this.level().addParticle(ParticleTypes.FLAME, x + (random.nextDouble()-0.5), y, z + (random.nextDouble()-0.5), 0, 0.1, 0);
                break;
            case 3: // 冰
                this.level().addParticle(ParticleTypes.SNOWFLAKE, x + (random.nextDouble()-0.5), y, z + (random.nextDouble()-0.5), 0, 0, 0);
                break;
            case 4: // 凋零
                this.level().addParticle(ParticleTypes.SQUID_INK, x + (random.nextDouble()-0.5), y, z + (random.nextDouble()-0.5), 0, 0, 0);
                break;
        }
    }

    // --- 死亡与阶段转换 ---
    @Override
    public void die(DamageSource pDamageSource) {
        if (this.getPhase() == 1 && !pDamageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            transitionToPhaseTwo();
        } else {
            super.die(pDamageSource);
        }
    }

    private void transitionToPhaseTwo() {
        this.setPhase(2);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
        this.setHealth(500.0F);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
        }

        // 初始进入二阶段逻辑由 tickPhaseTwoLogic 接管 (SubPhase 0 -> 1)

        this.bossEvent.setColor(BossEvent.BossBarColor.RED);
        this.bossEvent.setDarkenScreen(true);
    }

    // --- Boss 条管理 ---
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