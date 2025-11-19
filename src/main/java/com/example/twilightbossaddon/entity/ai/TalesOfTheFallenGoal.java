package com.example.twilightbossaddon.entity.ai;

import com.example.twilightbossaddon.Config; // 导入配置文件
import com.example.twilightbossaddon.entity.NarratorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 技能：消逝传说 (Tales of the Fallen)
 */

public class TalesOfTheFallenGoal extends Goal {
    private final NarratorEntity boss;
    private boolean hasSummoned = false;
    private boolean isFinished = false;

    public static final String MINION_TAG = "NarratorSummon";
    // 定义一个固定的 UUID 用于攻击力修饰符，避免冲突
    private static final UUID DAMAGE_BOOST_ID = UUID.fromString("710D4861-E402-4157-8436-714735325400");

    public TalesOfTheFallenGoal(NarratorEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.boss.getPhase() == 1
                && this.boss.getTarget() != null
                && !isFinished;
    }

    @Override
    public boolean canContinueToUse() {
        return !isFinished;
    }

    @Override
    public void start() {
        this.boss.setInvulnerablePhase(true);
        this.boss.setNoGravity(true); // 确保设置无重力，避免抖动
        this.boss.setCustomName(Component.literal("讲述者-守门人: 消逝传说"));
        this.boss.setCustomNameVisible(true);
    }

    @Override
    public void tick() {
        // 1. 悬浮逻辑
        if (this.boss.getY() < this.boss.getTarget().getY() + 5.0D) {
            this.boss.setDeltaMovement(0, 0.1, 0);
        } else {
            this.boss.setDeltaMovement(0, 0, 0);
        }
        this.boss.getNavigation().stop();

        // 2. 召唤逻辑
        if (!hasSummoned && this.boss.level() instanceof ServerLevel serverLevel) {
            summonBosses(serverLevel);
            hasSummoned = true;
        }

        // 3. 监测逻辑
        if (hasSummoned) {
            checkMinionsStatus();
        }
    }

    private void summonBosses(ServerLevel level) {
        EntityType<?> nagaType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("twilightforest", "naga"));
        EntityType<?> lichType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("twilightforest", "lich"));

        BlockPos bossPos = this.boss.blockPosition();

        if (nagaType != null) spawnMinion(level, nagaType, bossPos.offset(5, 0, 5));
        if (lichType != null) spawnMinion(level, lichType, bossPos.offset(-5, 0, -5));
    }

    // 提取出一个通用的生成方法，避免重复代码
    private void spawnMinion(ServerLevel level, EntityType<?> type, BlockPos pos) {
        Entity entity = type.create(level);
        if (entity instanceof LivingEntity minion) {
            minion.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);

            // --- 1. 血量调整 (从 Config 读取) ---
            double multiplier = Config.minionHealthMultiplier; // 默认为 1.2
            double originalMaxHealth = minion.getMaxHealth();
            double newMaxHealth = originalMaxHealth * multiplier;

            // 修改最大生命值属性
            AttributeInstance healthAttr = minion.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(newMaxHealth);
            }
            // 将当前血量回满到新的最大值
            minion.setHealth((float) newMaxHealth);

            // --- 2. 攻击力调整 (从 Config 读取) ---
            double dmgMultiplier = Config.minionDamageMultiplier; // 默认为 1.2
            // 注意：AttributeModifier 的 Operation.MULTIPLY_TOTAL 是在基础值上乘 (1 + value)
            // 所以如果我们想要 1.2 倍伤害，value 应该是 0.2 (即增加 20%)
            // 如果 Config 写的是倍率 1.2，我们需要减去 1.0
            double boostAmount = Math.max(0, dmgMultiplier - 1.0);

            AttributeInstance damageAttr = minion.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null && boostAmount > 0) {
                damageAttr.removeModifier(DAMAGE_BOOST_ID);
                damageAttr.addPermanentModifier(new AttributeModifier(
                        DAMAGE_BOOST_ID,
                        "Narrator Minion Damage Boost",
                        boostAmount,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }

            // --- 3. 行为控制 ---
            minion.addTag(MINION_TAG);
            if (this.boss.getTarget() != null) {
                minion.setLastHurtByMob(this.boss.getTarget());
            }

            level.addFreshEntity(minion);
        }
    }

    private void checkMinionsStatus() {
        Level level = this.boss.level();
        List<LivingEntity> minions = level.getEntitiesOfClass(LivingEntity.class,
                this.boss.getBoundingBox().inflate(100.0D),
                entity -> entity.getTags().contains(MINION_TAG));

        if (minions.isEmpty()) {
            finishSkill();
        }
    }

    private void finishSkill() {
        isFinished = true;
        this.boss.setInvulnerablePhase(false);
        this.boss.setNoGravity(false);
        this.boss.setCustomName(null);
    }

    @Override
    public void stop() {
        this.boss.setInvulnerablePhase(false);
        this.boss.setNoGravity(false);
    }
}