package com.example.twilightbossaddon.entity.ai;

import com.example.twilightbossaddon.Config;
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

public class TalesOfTheFallenGoal extends Goal {
    private final NarratorEntity boss;
    private boolean hasSummoned = false;
    private boolean isFinished = false;

    public static final String MINION_TAG = "NarratorSummon";
    private static final UUID DAMAGE_BOOST_ID = UUID.fromString("710D4861-E402-4157-8436-714735325400");

    public TalesOfTheFallenGoal(NarratorEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    public boolean isRunning() { return !isFinished && hasSummoned; }

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
        this.boss.setNoGravity(true);
        this.boss.setCustomName(Component.literal("讲述者-守门人: 消逝传说"));
        this.boss.setCustomNameVisible(true);
    }

    @Override
    public void tick() {
        // 悬浮逻辑
        if (this.boss.getY() < this.boss.getTarget().getY() + 5.0D) {
            this.boss.setDeltaMovement(0, 0.1, 0);
        } else {
            this.boss.setDeltaMovement(0, 0, 0);
        }
        this.boss.getNavigation().stop();

        if (!hasSummoned && this.boss.level() instanceof ServerLevel serverLevel) {
            summonBosses(serverLevel);
            hasSummoned = true;
        }

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

    private void spawnMinion(ServerLevel level, EntityType<?> type, BlockPos pos) {
        Entity entity = type.create(level);
        if (entity instanceof LivingEntity minion) {
            minion.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);

            // --- 修复逻辑：先改上限，再回血 ---
            double multiplier = Config.minionHealthMultiplier;
            double originalMaxHealth = minion.getAttribute(Attributes.MAX_HEALTH).getValue();
            double newMaxHealth = originalMaxHealth * multiplier;

            AttributeInstance healthAttr = minion.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(newMaxHealth);
            }
            // 必须强制更新当前血量
            minion.setHealth((float) newMaxHealth);

            // 攻击力修改
            double dmgMultiplier = Config.minionDamageMultiplier;
            double boostAmount = Math.max(0, dmgMultiplier - 1.0);
            AttributeInstance damageAttr = minion.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null && boostAmount > 0) {
                damageAttr.removeModifier(DAMAGE_BOOST_ID);
                damageAttr.addPermanentModifier(new AttributeModifier(
                        DAMAGE_BOOST_ID, "Narrator Minion Boost", boostAmount, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }

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