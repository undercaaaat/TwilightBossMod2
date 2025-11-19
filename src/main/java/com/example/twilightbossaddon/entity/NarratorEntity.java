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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class NarratorEntity extends Monster {

    // 同步数据定义
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(NarratorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE_TICK = SynchedEntityData.defineId(NarratorEntity.class, EntityDataSerializers.INT);
    // 二阶段子状态: 0=无, 1=章节变迁, 2=炼狱史诗, 3=极寒寓言, 4=凋零神话
    private static final EntityDataAccessor<Integer> SUB_PHASE = SynchedEntityData.defineId(NarratorEntity.class, EntityDataSerializers.INT);

    private boolean isInvulnerablePhase = false;

    // --- 技能调度系统 ---
    // 一阶段：技能计数器 (0=命运书写, 1=命运书写, 2=风暴墨雨) -> 循环
    private int skillCycleCounter = 0;

    // 二阶段：诅咒队列 (保证不重复)
    private final Queue<Integer> sagaQueue = new LinkedList<>();
    private int subPhaseTimer = 0;

    private InkStormGoal inkStormGoal;
    private WrittenFateGoal writtenFateGoal;
    private TalesOfTheFallenGoal talesGoal;

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
        this.entityData.define(SUB_PHASE, 0);
    }

    // --- API 方法 ---
    public int getPhase() { return this.entityData.get(PHASE); }
    public void setPhase(int newPhase) { this.entityData.set(PHASE, newPhase); }
    public int getPhasingTick() { return this.entityData.get(PHASE_TICK); }
    public void setPhasingTick(int ticks) { this.entityData.set(PHASE_TICK, ticks); }
    public int getSubPhase() { return this.entityData.get(SUB_PHASE); }
    public void setSubPhase(int phase) { this.entityData.set(SUB_PHASE, phase); }
    public void setInvulnerablePhase(boolean isInvulnerable) { this.isInvulnerablePhase = isInvulnerable; }
    public boolean isInvulnerablePhase() { return this.isInvulnerablePhase; }

    // --- 调度逻辑 ---
    public void advanceSkillCycle() {
        this.skillCycleCounter++;
        if (this.skillCycleCounter > 2) {
            this.skillCycleCounter = 0;
        }
    }

    public boolean shouldCastInkStorm() {
        if (this.talesGoal != null && this.talesGoal.isRunning()) return false;
        return this.skillCycleCounter == 2;
    }

    public boolean shouldCastWrittenFate() {
        if (this.talesGoal != null && this.talesGoal.isRunning()) return false;
        return this.skillCycleCounter < 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 15.0D) // 提高基础伤害
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        this.talesGoal = new TalesOfTheFallenGoal(this);
        this.goalSelector.addGoal(1, this.talesGoal);

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
    public boolean canBeAffected(MobEffectInstance pPotioneffect) {
        MobEffectCategory category = pPotioneffect.getEffect().getCategory();
        // 全局免疫有害效果
        if (category == MobEffectCategory.HARMFUL) return false;
        // 二阶段变迁期双重保险
        if (this.getPhase() == 2 && this.getSubPhase() == 1 && category == MobEffectCategory.HARMFUL) return false;

        return category == MobEffectCategory.BENEFICIAL && super.canBeAffected(pPotioneffect);
    }

    // --- 伤害判定逻辑 ---
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        boolean isCreative = source.getEntity() instanceof Player player && player.getAbilities().instabuild;

        // 技能强制无敌
        if (this.isInvulnerablePhase && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !isCreative) {
            return true;
        }
        // 虚化无敌
        if (this.getPhasingTick() > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            // 1. 位置判定 (蔑视/僭越)
            double bossY = this.getY();
            double attackerY = attacker.getY();

            if (attackerY < bossY - 0.1) {
                amount *= 0.8f; // 蔑视
            } else if (attackerY > bossY + 2.1) {
                amount *= 1.2f; // 僭越
            }

            // 2. 二阶段特性
            if (this.getPhase() == 2) {
                if (this.getSubPhase() == 3 && source.is(DamageTypeTags.IS_FIRE)) {
                    amount *= 1.2f; // 极寒寓言弱火
                }
                if (this.getSubPhase() == 1) {
                    amount *= 0.5f; // 章节变迁减伤
                    if (!this.level().isClientSide) this.playSound(SoundEvents.BEACON_DEACTIVATE, 1.0f, 2.0f);
                }
            }

            // 3. 阈值与封顶
            float attackerMaxHealth = attacker.getMaxHealth();
            float minThreshold = attackerMaxHealth * 0.40f;
            float maxCap = attackerMaxHealth * 0.80f;

            if (amount < minThreshold) {
                if (!this.level().isClientSide) this.playSound(SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
                return false; // 免疫低伤
            }

            if (amount > maxCap) {
                amount = maxCap; // 伤害封顶
            }
        }

        boolean damaged = super.hurt(source, amount);

        // 4. 触发虚化
        if (damaged && !this.level().isClientSide) {
            this.setPhasingTick(15);
            clearHarmfulEffects();
            this.playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 2.0f);
        }

        return damaged;
    }

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

        // 更新技能
        // 注意: Goal 的 tick 只在 Goal 激活时运行，但我们需要在这里更新一些状态

        // 虚化更新
        int currentPhasing = this.getPhasingTick();
        if (currentPhasing > 0) {
            this.setPhasingTick(currentPhasing - 1);
            if (this.level().isClientSide) {
                for(int i = 0; i < 2; ++i) {
                    this.level().addParticle(ParticleTypes.PORTAL, this.getRandomX(0.5D), this.getRandomY() - 0.25D, this.getRandomZ(0.5D), (this.random.nextDouble() - 0.5D) * 2.0D, -this.random.nextDouble(), (this.random.nextDouble() - 0.5D) * 2.0D);
                }
            }
        }

        // 二阶段逻辑
        if (this.getPhase() == 2) {
            if (!this.level().isClientSide) {
                tickPhaseTwoLogic();
            } else {
                spawnPhaseTwoParticles();
            }
        }
    }

    // --- 二阶段状态机 (洗牌算法) ---
    private void tickPhaseTwoLogic() {
        if (this.getSubPhase() == 0) {
            startChapterTransition();
            return;
        }

        this.subPhaseTimer--;

        if (this.subPhaseTimer <= 0) {
            if (this.getSubPhase() == 1) {
                startNextSagaFromQueue(); // 变迁结束 -> 下个史诗
            } else {
                startChapterTransition(); // 史诗结束 -> 变迁
            }
        }
    }

    private void startNextSagaFromQueue() {
        if (this.sagaQueue.isEmpty()) {
            List<Integer> sagas = new ArrayList<>();
            sagas.add(2); // 炼狱
            sagas.add(3); // 极寒
            sagas.add(4); // 凋零
            Collections.shuffle(sagas, this.random); // 洗牌
            this.sagaQueue.addAll(sagas);
        }

        int nextSaga = this.sagaQueue.poll();
        this.setSubPhase(nextSaga);
        this.subPhaseTimer = 200; // 10秒
        this.setNoGravity(false);

        switch (nextSaga) {
            case 2 -> this.setCustomName(Component.literal("讲述者-巨匠: 炼狱史诗"));
            case 3 -> this.setCustomName(Component.literal("讲述者-巨匠: 极寒寓言"));
            case 4 -> this.setCustomName(Component.literal("讲述者-巨匠: 凋零神话"));
        }
    }

    private void startChapterTransition() {
        this.setSubPhase(1);
        this.subPhaseTimer = 40; // 2秒
        this.setCustomName(Component.literal("讲述者-巨匠: 章节变迁"));
        this.setNoGravity(true);
        this.setDeltaMovement(0, 0, 0);
        clearHarmfulEffects();
    }

    private void spawnPhaseTwoParticles() {
        int sub = this.getSubPhase();
        double x = this.getX();
        double y = this.getY() + 1.0;
        double z = this.getZ();

        this.level().addParticle(ParticleTypes.SMOKE, x, y + 0.5, z, 0, 0, 0);

        switch (sub) {
            case 1 -> this.level().addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.05, 0);
            case 2 -> this.level().addParticle(ParticleTypes.FLAME, x + (random.nextDouble()-0.5), y, z + (random.nextDouble()-0.5), 0, 0.1, 0);
            case 3 -> this.level().addParticle(ParticleTypes.SNOWFLAKE, x + (random.nextDouble()-0.5), y, z + (random.nextDouble()-0.5), 0, 0, 0);
            case 4 -> this.level().addParticle(ParticleTypes.SQUID_INK, x + (random.nextDouble()-0.5), y, z + (random.nextDouble()-0.5), 0, 0, 0);
        }
    }

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
        this.setCustomName(Component.literal("讲述者-巨匠: 章节变迁"));
        this.removeAllEffects();
        this.bossEvent.setColor(BossEvent.BossBarColor.RED);
        this.bossEvent.setDarkenScreen(true);

        // 重置调度器
        this.setSubPhase(0);
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