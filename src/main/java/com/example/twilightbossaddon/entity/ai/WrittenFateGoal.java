package com.example.twilightbossaddon.entity.ai;

import com.example.twilightbossaddon.entity.NarratorEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Snowball;

import java.util.EnumSet;

public class WrittenFateGoal extends Goal {
    private final NarratorEntity boss;
    private int cooldown = 0;

    // 状态变量
    private int currentRound = 0;      // 当前轮次 (0-2)
    private int shotsInRound = 0;      // 当前轮次已发射数量 (0-3)
    private int tickCounter = 0;       // 用于控制射击节奏的计时器
    private boolean isActive = false;

    public WrittenFateGoal(NarratorEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.boss.getPhase() == 1
                && this.boss.getTarget() != null
                && !this.boss.isInvulnerable()
                && cooldown <= 0
                && this.boss.getRandom().nextFloat() < 0.1f;
    }

    @Override
    public void start() {
        this.isActive = true;
        this.currentRound = 0;
        this.shotsInRound = 0;
        this.tickCounter = 0;
        this.cooldown = 200; // 技能冷却 10秒
        this.boss.setCustomName(Component.literal("讲述者-守门人: 命运书写"));
    }

    @Override
    public void tick() {
        LivingEntity target = this.boss.getTarget();
        if (target == null) {
            stop();
            return;
        }

        this.boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.tickCounter++;

        // 逻辑：每一轮发射4次 (3普通+1特殊)
        // 频率：每秒2发 -> 间隔 10 ticks
        // 轮间间隔：1秒 -> 20 ticks

        // 射击检查
        if (this.tickCounter >= getDelayForNextShot()) {
            shootRune(target);
            this.tickCounter = 0; // 重置计时器
            this.shotsInRound++;

            // 检查一轮是否结束
            if (this.shotsInRound >= 4) {
                this.currentRound++;
                this.shotsInRound = 0;
            }
        }
    }

    private int getDelayForNextShot() {
        // 如果刚打完一轮 (shotsInRound被重置为0且currentRound增加了)，则需要等待 20 ticks (1秒)
        // 否则，普通射击间隔 10 ticks (0.5秒)
        if (this.shotsInRound == 0 && this.currentRound > 0) {
            return 20;
        }
        return 10;
    }

    private void shootRune(LivingEntity target) {
        Snowball rune = new Snowball(this.boss.level(), this.boss);

        // 计算预判轨迹 (保持原有的精准度)
        double d0 = target.getX() - this.boss.getX();
        double d1 = target.getEyeY() - 1.1F - rune.getY();
        double d2 = target.getZ() - this.boss.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2) * 0.2F;
        rune.shoot(d0, d1 + d3, d2, 1.6F, 1.0F); // 速度1.6，散布1.0(稍微精准点)

        // --- 标签系统 ---
        rune.addTag(TalesOfTheFallenGoal.MINION_TAG); // 确保会被视为Boss攻击，触发基础逻辑
        rune.addTag("NarratorRune"); // 标记为符文

        // 判断符文类型
        if (this.shotsInRound < 3) {
            // 前3发：普通符文
            rune.addTag("RuneType:Normal");
        } else {
            // 第4发：根据轮次决定特殊符文
            switch (this.currentRound) {
                case 0 -> rune.addTag("RuneType:Fire");
                case 1 -> rune.addTag("RuneType:Ice");
                case 2 -> rune.addTag("RuneType:Dark");
            }
            // [视觉实现] 可以在这里根据Tag修改Snowball的粒子轨迹颜色
            // 比如 Fire -> 火焰粒子, Ice -> 雪花粒子
        }

        this.boss.level().addFreshEntity(rune);
        // [视觉实现] 播放发射音效
    }

    @Override
    public boolean canContinueToUse() {
        // 3轮打完 (0,1,2)，当 round 变成 3 时结束
        return this.isActive && this.currentRound < 3;
    }

    @Override
    public void stop() {
        this.isActive = false;
        this.boss.setCustomName(null);
    }

    public void decreaseCooldown() {
        if (this.cooldown > 0) this.cooldown--;
    }
}