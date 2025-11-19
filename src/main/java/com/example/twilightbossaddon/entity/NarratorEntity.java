package com.example.twilightbossaddon.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class NarratorEntity extends Monster {

    // --- 数据同步 ---
    // 我们需要一个在服务器和客户端之间同步的变量来存储当前的“阶段”
    // 这样客户端才能根据阶段改变模型或贴图 (Visual Implementation)
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(NarratorEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent =
            new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    public NarratorEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        // 初始化阶段为 1
        this.entityData.define(PHASE, 1);
    }

    // --- 阶段管理 API ---
    public int getPhase() {
        return this.entityData.get(PHASE);
    }

    public void setPhase(int newPhase) {
        this.entityData.set(PHASE, newPhase);
        // 这里可以添加切换阶段时的逻辑，比如播放音效、生成粒子、无敌时间等
        refreshAttributesForPhase(newPhase);
    }

    private void refreshAttributesForPhase(int phase) {
        // TODO: 根据阶段调整属性（例如设计文档中提到的：三阶段免疫击退，攻击频率上升）
        // 这部分我们后续实现
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D) // 记得添加基础攻击力
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D) // Boss通常需要一些击退抗性
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // --- 技能系统预留位 ---
        // 我们后续会在这里添加根据 getPhase() 返回值而变化的自定义 Goal
        // 例如：if (phase == 2) addGoal(new TantrumAttackGoal(this));

        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true));
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

        // TODO: 这里是编写“阶段转换逻辑”的好地方
        // 例如：if (this.getHealth() < 200 && this.getPhase() == 1) setPhase(2);
    }
}