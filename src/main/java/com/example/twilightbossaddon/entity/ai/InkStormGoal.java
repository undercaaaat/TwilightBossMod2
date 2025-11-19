package com.example.twilightbossaddon.entity.ai;

import com.example.twilightbossaddon.entity.NarratorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.joml.Vector3f;

import java.util.EnumSet;

public class InkStormGoal extends Goal {
    private final NarratorEntity boss;
    private int skillTimer = 0;

    public InkStormGoal(NarratorEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 仅在一阶段 + 有目标 + 非无敌 + 轮到此技能
        return this.boss.getPhase() == 1
                && this.boss.getTarget() != null
                && !this.boss.isInvulnerablePhase()
                && this.boss.shouldCastInkStorm();
    }

    @Override
    public void start() {
        this.skillTimer = 0;
        this.boss.setCustomName(Component.literal("讲述者-守门人: 风暴墨雨"));
    }

    @Override
    public void tick() {
        this.skillTimer++;
        LivingEntity target = this.boss.getTarget();
        if (target == null) return;

        // 三轮波次：第 10, 25, 40 tick
        if (this.skillTimer == 10 || this.skillTimer == 25 || this.skillTimer == 40) {
            performInkWave(target);
        }
    }

    private void performInkWave(LivingEntity target) {
        // 1. 必中点：玩家当前位置
        createCloudWithWarning(target.blockPosition());

        // 2. 随机点：在半径 32 格内生成 16 个
        for (int i = 0; i < 16; i++) {
            BlockPos randomPos = findRandomPos(target.blockPosition(), 32);
            createCloudWithWarning(randomPos);
        }
    }

    private void createCloudWithWarning(BlockPos pos) {
        if (!(this.boss.level() instanceof ServerLevel serverLevel)) return;

        // --- 视觉预警：红圈 ---
        // 红色 (R=1.0, G=0.0, B=0.0), 尺寸 2.0 (更明显)
        DustParticleOptions redDust = new DustParticleOptions(new Vector3f(1.0f, 0.0f, 0.0f), 2.0f);

        double radius = 3.0; // 直径 6 格
        int particleCount = 30;

        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            double x = pos.getX() + 0.5 + radius * Math.cos(angle);
            double z = pos.getZ() + 0.5 + radius * Math.sin(angle);
            // 确保在地面上方显示
            serverLevel.sendParticles(redDust, x, pos.getY() + 0.2, z, 1, 0, 0, 0, 0);
        }

        // --- 生成药水云 ---
        AreaEffectCloud cloud = new AreaEffectCloud(this.boss.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        cloud.setRadius(3.0F);
        cloud.setRadiusOnUse(-0.05F);
        cloud.setWaitTime(20); // 1秒延迟，配合红圈
        cloud.setDuration(140); // 1秒等待 + 6秒滞留
        cloud.setRadiusPerTick(-0.01F);

        cloud.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
        cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
        cloud.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));

        cloud.setFixedColor(0x1A1A1A); // 墨水色
        cloud.setParticle(net.minecraft.core.particles.ParticleTypes.SQUID_INK);

        this.boss.level().addFreshEntity(cloud);
    }

    private BlockPos findRandomPos(BlockPos center, int radius) {
        double angle = this.boss.getRandom().nextDouble() * 2 * Math.PI;
        double distance = this.boss.getRandom().nextDouble() * radius;
        double x = center.getX() + distance * Math.cos(angle);
        double z = center.getZ() + distance * Math.sin(angle);
        // 简单沿用目标高度，避免生成在地底下
        return new BlockPos((int)x, center.getY(), (int)z);
    }

    @Override
    public boolean canContinueToUse() {
        return skillTimer < 60;
    }

    @Override
    public void stop() {
        this.boss.setCustomName(null);
        // 关键：推进技能循环
        this.boss.advanceSkillCycle();
    }
}