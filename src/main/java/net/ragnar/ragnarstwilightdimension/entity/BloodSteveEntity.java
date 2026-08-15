package net.ragnar.ragnarstwilightdimension.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import net.ragnar.ragnarstwilightdimension.event.BloodMoon;

/**
 * What comes for you while the blood moon is up.
 *
 * <p>Player-shaped and player-sized, unlike the {@link SilhouetteEntity} it does not watch and it
 * does not leave. It runs straight at you at full speed from wherever it spawned and hits hard, but
 * it has a single point of health - anything that connects at all kills it outright. The trade is
 * deliberate: they are lethal in a group and free to kill one at a time, so the danger is in being
 * surrounded rather than in any single one of them.
 *
 * <p>It only exists for the duration of an event. When the blood moon ends every one of them
 * removes itself on its next tick.
 */
public class BloodSteveEntity extends HostileEntity {
	/**
	 * Faster than almost anything vanilla - a zombie is 0.23 and a vindicator 0.35. Above a sprinting
	 * player, so running is not an escape, only a delay.
	 */
	private static final double SPEED = 0.34;

	/** Three hearts a hit on normal difficulty. A zombie does one and a half. */
	private static final double DAMAGE = 6.0;

	/** Far enough that they lock on from outside the fog and are already running when you see them. */
	private static final double FOLLOW_RANGE = 64.0;

	/** Gives up and vanishes if it somehow ends up this far from everyone. */
	private static final double ABANDON_DISTANCE = 80.0;

	public BloodSteveEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 0;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				// One hit is all it takes, whatever the hit was.
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 1.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, DAMAGE)
				.add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 0.5)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, FOLLOW_RANGE);
	}

	@Override
	protected void initGoals() {
		// Speed multiplier 1.0 is flat out. There is no wander goal and no idle behaviour on purpose -
		// there is nothing it would rather be doing.
		this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, 16.0F));
		this.goalSelector.add(3, new LookAroundGoal(this));

		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	public void tick() {
		super.tick();

		if (this.getWorld().isClient) {
			return;
		}

		// The event owning them is the only thing keeping them here.
		if (!BloodMoon.isActive()) {
			this.discard();
			return;
		}

		PlayerEntity nearest = this.getWorld().getClosestPlayer(this, ABANDON_DISTANCE);
		if (nearest == null) {
			this.discard();
		}
	}

	/** Player noises, because that is what it looks like. */
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_PLAYER_DEATH;
	}

	/** Nothing ambient - the unplaceable noises during an event come from {@link BloodMoon}. */
	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	/** Removal is driven by the event, not by the vanilla despawn rules. */
	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean canUsePortals(boolean allowVehicles) {
		return false;
	}
}
