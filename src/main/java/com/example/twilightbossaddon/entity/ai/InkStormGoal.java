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
    private int cooldown = 0;
    private int attackTimer = 0; // 技能执行计时器

    public InkStormGoal(NarratorEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.boss.getPhase() == 1
                && this.boss.getTarget() != null
                && !this.boss.isInvulnerable()
                && cooldown <= 0
                && this.boss.getRandom().nextFloat() < 0.05f;
    }

    @Override
    public void start() {
        this.attackTimer = 0;
        this.cooldown = 200; // 10秒冷却
        this.boss.setCustomName(Component.literal("讲述者-守门人: 风暴墨雨"));

        // 播放施法动画（如果已实现）
    }

    @Override
    public void tick() {
        this.attackTimer++;
        LivingEntity target = this.boss.getTarget();

        // 在技能开始后的第 10 tick (0.5秒) 生成第一波
        // 你可以复制这个 if 块，在 attackTimer == 40 时生成第二波，制造连续攻击感
        if (this.attackTimer == 10 && target != null) {
            performInkAttack(target);
        }

        if (this.attackTimer == 40 && target != null) {
            performInkAttack(target);
        }

        if (this.attackTimer == 70 && target != null) {
            performInkAttack(target);
        }

    }

    private void performInkAttack(LivingEntity target) {
        // 1. 在玩家当前位置生成一个
        createCloudWithWarning(target.blockPosition());

        // 2. 在 Boss 周围 32 格范围内随机生成 3-5 个
        int randomCount = 3 + this.boss.getRandom().nextInt(3);
        for (int i = 0; i < randomCount; i++) {
            BlockPos randomPos = findRandomPosNearBoss(32);
            createCloudWithWarning(randomPos);
        }
    }

    private void createCloudWithWarning(BlockPos pos) {
        if (!(this.boss.level() instanceof ServerLevel serverLevel)) return;

        // --- 视觉预警：红圈粒子 ---
        // 我们画一个半径为 3 的圆圈
        double radius = 3.0;
        int particleCount = 30; // 粒子数量，越多越连贯
        // 红色粒子 (RGB: 1.0, 0.0, 0.0)，尺寸 1.0
        DustParticleOptions redDust = new DustParticleOptions(new Vector3f(1.0f, 0.0f, 0.0f), 1.0f);

        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            double x = pos.getX() + 0.5 + radius * Math.cos(angle);
            double z = pos.getZ() + 0.5 + radius * Math.sin(angle);
            // 发送粒子包给客户端
            serverLevel.sendParticles(redDust, x, pos.getY() + 0.2, z, 1, 0, 0, 0, 0);
        }

        // --- 生成药水云 ---
        AreaEffectCloud cloud = new AreaEffectCloud(this.boss.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        cloud.setRadius(3.0F); // 半径3格 = 直径6格
        cloud.setRadiusOnUse(-0.1F); // 玩家触碰后稍微缩小一点点，避免瞬间消失
        cloud.setWaitTime(20); // 核心修改：等待 20 ticks (1秒) 后才生效！这就给了玩家反应时间
        cloud.setDuration(140); // 存活时间：等待的1秒 + 滞留的6秒
        cloud.setRadiusPerTick(-0.01F); // 随时间缓慢缩小

        // 负面效果
        cloud.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1)); // 中毒 II
        cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2)); // 缓慢 III
        cloud.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));

        // 视觉调整：深黑色
        cloud.setFixedColor(0x1A1A1A);
        // 粒子类型：使用鱿鱼墨水粒子，看起来更像“墨雨”
        cloud.setParticle(net.minecraft.core.particles.ParticleTypes.SQUID_INK);

        this.boss.level().addFreshEntity(cloud);
    }

    private BlockPos findRandomPosNearBoss(int radius) {
        double angle = this.boss.getRandom().nextDouble() * 2 * Math.PI;
        double distance = this.boss.getRandom().nextDouble() * radius;
        double x = this.boss.getX() + distance * Math.cos(angle);
        double z = this.boss.getZ() + distance * Math.sin(angle);

        // 获取地面高度 (避免生成在半空中或地底下)
        BlockPos targetPos = new BlockPos((int)x, (int)this.boss.getY(), (int)z);
        // 这里可以做一个简单的地面判定，寻找 y 轴最近的固体方块，简单起见先用 boss 的 Y 轴
        return targetPos;
    }

    @Override
    public boolean canContinueToUse() {
        // 技能持续时间结束后停止
        return attackTimer < 30; // 稍微留点时间让动画播完
    }

    @Override
    public void stop() {
        this.boss.setCustomName(null);
    }

    public void decreaseCooldown() {
        if (this.cooldown > 0) this.cooldown--;
    }
}