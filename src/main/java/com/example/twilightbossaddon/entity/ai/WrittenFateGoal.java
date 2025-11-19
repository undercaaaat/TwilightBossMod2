package com.example.twilightbossaddon.entity.ai;

import com.example.twilightbossaddon.entity.NarratorEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Snowball;

import java.util.EnumSet;

public class WrittenFateGoal extends Goal {
    private final NarratorEntity boss;

    // 状态
    private int currentRound = 0;
    private int shotsInRound = 0;
    private int tickCounter = 0;
    private boolean isActive = false;

    public WrittenFateGoal(NarratorEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.boss.getPhase() == 1
                && this.boss.getTarget() != null
                && !this.boss.isInvulnerablePhase()
                && this.boss.shouldCastWrittenFate();
    }

    @Override
    public void start() {
        this.isActive = true;
        this.currentRound = 0;
        this.shotsInRound = 0;
        this.tickCounter = 0;
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

        // 射击间隔控制：
        // 轮间休息：20 ticks (1秒)
        if (this.shotsInRound == 0 && this.tickCounter < 20 && this.currentRound > 0) {
            this.tickCounter++;
            return;
        }

        this.tickCounter++;
        // 射速：每 7 ticks 一发 (约 3 发/秒)
        if (this.tickCounter >= 7) {
            shootRune(target);
            this.tickCounter = 0;
            this.shotsInRound++;

            // 一轮 4 发 (3普通 + 1特殊)
            if (this.shotsInRound >= 4) {
                this.currentRound++;
                this.shotsInRound = 0;
            }
        }
    }

    private void shootRune(LivingEntity target) {
        Snowball rune = new Snowball(this.boss.level(), this.boss);

        double d0 = target.getX() - this.boss.getX();
        double d1 = target.getEyeY() - 1.1F - rune.getY();
        double d2 = target.getZ() - this.boss.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2) * 0.2F;

        rune.shoot(d0, d1 + d3, d2, 1.6F, 1.0F);

        // 标签设置 (关键逻辑)
        rune.addTag("NarratorRune");

        if (this.shotsInRound < 3) {
            rune.addTag("RuneType:Normal");
        } else {
            // 特殊符文顺序：火 -> 冰 -> 暗
            switch (this.currentRound) {
                case 0 -> rune.addTag("RuneType:Fire");
                case 1 -> rune.addTag("RuneType:Ice");
                case 2 -> rune.addTag("RuneType:Dark");
                default -> rune.addTag("RuneType:Dark");
            }
        }

        this.boss.level().addFreshEntity(rune);
        // 可选音效
        // this.boss.playSound(SoundEvents.SNOW_GOLEM_SHOOT, 1.0F, 1.0F);
    }

    @Override
    public boolean canContinueToUse() {
        // 3轮结束
        return this.isActive && this.currentRound < 3;
    }

    @Override
    public void stop() {
        this.isActive = false;
        this.boss.setCustomName(null);
        // 技能结束，推进循环
        this.boss.advanceSkillCycle();
    }
}