package net.ragnar.ragnarstwilightdimension.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Somebody who loaded in wrong.
 *
 * <p>A default-skinned figure standing out in the open, a quarter of the way into the ground, with a
 * name over its head that is not a name: {@code Player1} or {@code User}, the placeholders a game
 * falls back to when it has nothing to call somebody. It does not move, does not turn and does not
 * notice you. It is the only thing in the dimension with a label over it, and the label is the
 * default.
 *
 * <p>Built exactly like {@link ChurchSteveEntity}: no AI, no gravity, no noise, persistent, can be
 * killed, drops nothing. The sinking is not drawn - the entity is really standing
 * {@link #SINK} blocks below the surface, which is what keeps it there. With no gravity and no AI
 * nothing ever moves it, and its eyes stay well clear of the ground, so it does not suffocate.
 *
 * <p>Placed by {@code StuckPlayerFeature}, once, when the chunk generates. Like the churchgoer,
 * killing it is permanent.
 */
public class StuckPlayerEntity extends MobEntity {
	/** Width of the hitbox: an ordinary player's. */
	public static final float WIDTH = 0.6F;

	/** Height of the hitbox: an ordinary player's. */
	public static final float HEIGHT = 1.8F;

	/** How far into the ground it stands: a quarter of its own height. */
	public static final double SINK = HEIGHT / 4.0;

	/** The two names the game gives to somebody it does not have a name for. */
	private static final String[] NAMES = {"Player1", "User"};

	/** Set once the first tick has pinned the angles, so they are not interpolated from zero. */
	private boolean settled;

	public StuckPlayerEntity(EntityType<? extends StuckPlayerEntity> type, World world) {
		super(type, world);
		this.setAiDisabled(true);
		this.setNoGravity(true);
		this.setSilent(true);
		this.setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 0.0);
	}

	/** Gives it one of the placeholder names, always shown. Called once, by whatever places it. */
	public void assignName(Random random) {
		this.setCustomName(Text.literal(NAMES[random.nextInt(NAMES.length)]));
		this.setCustomNameVisible(true);
	}

	/** Body and head follow the yaw it was placed at, so it never looks anywhere else. */
	@Override
	public void tick() {
		super.tick();

		float yaw = this.getYaw();
		this.setBodyYaw(yaw);
		this.setHeadYaw(yaw);

		if (!this.settled) {
			this.prevYaw = yaw;
			this.prevBodyYaw = yaw;
			this.prevHeadYaw = yaw;
			this.settled = true;
		}
	}

	/** Knockback would lift it out of the ground and leave it hanging, since nothing brings it back. */
	@Override
	public void takeKnockback(double strength, double x, double z) {
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void pushAwayFrom(Entity entity) {
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean canUsePortals(boolean allowVehicles) {
		return false;
	}

	@Override
	public void checkDespawn() {
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}
}
