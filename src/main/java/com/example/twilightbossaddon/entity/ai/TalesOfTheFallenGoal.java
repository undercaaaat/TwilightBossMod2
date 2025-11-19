package com.example.twilightbossaddon.entity.ai;

import com.example.twilightbossaddon.entity.NarratorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 技能：消逝传说 (Tales of the Fallen)
 * 逻辑：
 * 1. 战斗开始时触发。
 * 2. Boss 飞向空中，变为无敌。
 * 3. 召唤娜迦和巫妖。
 * 4. 持续监测，直到娜迦和巫妖死亡，才解除无敌并落地。
 */
public class TalesOfTheFallenGoal extends Goal {
    private final NarratorEntity boss;
    private boolean hasSummoned = false; // 是否已经召唤过了
    private boolean isFinished = false;  // 技能是否彻底完成（本场战斗不再触发）

    // 用来识别召唤物的 Tag
    private static final String MINION_TAG = "NarratorSummon";

    public TalesOfTheFallenGoal(NarratorEntity boss) {
        this.boss = boss;
        // 互斥锁：这个技能运行时，Boss 不能移动、跳跃或看向别处（完全由本 Goal 接管）
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 触发条件：
        // 1. 必须在一阶段
        // 2. 必须有攻击目标（被玩家唤醒）
        // 3. 技能没有完成过
        return this.boss.getPhase() == 1
                && this.boss.getTarget() != null
                && !isFinished;
    }

    @Override
    public boolean canContinueToUse() {
        // 只要技能没完成，就一直运行（保持无敌状态）
        return !isFinished;
    }

    @Override
    public void start() {
        // 开启无敌模式
        this.boss.setInvulnerablePhase(true);

        // 显示技能头衔 (可选)
        this.boss.setCustomName(Component.literal("讲述者-守门人: 消逝传说"));
        this.boss.setCustomNameVisible(true);
    }

    @Override
    public void tick() {
        // 1. 悬浮逻辑：让 Boss 慢慢升空并保持在目标上方一定高度
        if (this.boss.getY() < this.boss.getTarget().getY() + 5.0D) {
            this.boss.setDeltaMovement(0, 0.1, 0); // 向上飘
        } else {
            this.boss.setDeltaMovement(0, 0, 0); // 悬停
        }
        this.boss.getNavigation().stop(); // 禁止寻路移动

        // 2. 召唤逻辑 (只执行一次)
        if (!hasSummoned && this.boss.level() instanceof ServerLevel serverLevel) {
            summonBosses(serverLevel);
            hasSummoned = true;
        }

        // 3. 监测召唤物逻辑
        if (hasSummoned) {
            checkMinionsStatus();
        }
    }

    private void summonBosses(ServerLevel level) {
        // 尝试通过注册表查找暮色森林的实体
        // 注意：暮色森林的实体ID通常是 twilightforest:naga 和 twilightforest:lich
        EntityType<?> nagaType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("twilightforest", "naga"));
        EntityType<?> lichType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("twilightforest", "lich"));

        BlockPos bossPos = this.boss.blockPosition();

        // 召唤娜迦
        if (nagaType != null) {
            Entity naga = nagaType.create(level);
            if (naga instanceof LivingEntity livingNaga) {
                livingNaga.moveTo(bossPos.getX() + 5, bossPos.getY(), bossPos.getZ() + 5, 0, 0);
                // 削弱：20% 血量
                double maxHealth = livingNaga.getMaxHealth();
                livingNaga.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth * 0.2);
                livingNaga.setHealth((float) (maxHealth * 0.2));
                // 标记为召唤物
                livingNaga.addTag(MINION_TAG);
                // 设置仇恨
                livingNaga.setLastHurtByMob(this.boss.getTarget());

                level.addFreshEntity(livingNaga);
            }
        }

        // 召唤巫妖
        if (lichType != null) {
            Entity lich = lichType.create(level);
            if (lich instanceof LivingEntity livingLich) {
                livingLich.moveTo(bossPos.getX() - 5, bossPos.getY(), bossPos.getZ() - 5, 0, 0);
                // 削弱：20% 血量
                double maxHealth = livingLich.getMaxHealth();
                livingLich.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth * 0.2);
                livingLich.setHealth((float) (maxHealth * 0.2));
                // 标记为召唤物
                livingLich.addTag(MINION_TAG);

                level.addFreshEntity(livingLich);
            }
        }

        // 播放音效提示
        // this.boss.playSound(SoundEvents.WITHER_SPAWN, 1.0f, 1.0f);
    }

    private void checkMinionsStatus() {
        Level level = this.boss.level();
        // 搜索周围 100 格内带有 "NarratorSummon" 标签的实体
        List<LivingEntity> minions = level.getEntitiesOfClass(LivingEntity.class,
                this.boss.getBoundingBox().inflate(100.0D),
                entity -> entity.getTags().contains(MINION_TAG));

        // 如果没有活着的召唤物了
        if (minions.isEmpty()) {
            finishSkill();
        }
    }

    private void finishSkill() {
        isFinished = true;
        this.boss.setInvulnerablePhase(false); // 解除无敌

        // 恢复重力 (让它自然掉下来)
        this.boss.setNoGravity(false);

        // 重置名字
        this.boss.setCustomName(null);
    }

    @Override
    public void stop() {
        // 强制清理状态（防止意外中断）
        this.boss.setInvulnerablePhase(false);
    }
}