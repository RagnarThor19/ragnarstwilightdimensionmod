package net.ragnar.ragnarstwilightdimension.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Something that is shaped like a player and is not one.
 *
 * <p>It never walks anywhere. It stands at the edge of the fog with its head turned towards you,
 * and the moment you close the distance it leaves - sprinting off into the murk, sinking through
 * the floor, or simply not being there any more. It cannot be hurt and drops nothing.
 *
 * <p>Visibility is handled on the render side: the renderer refuses to draw it inside
 * {@code MIN_VISIBLE_DISTANCE}, so even if you somehow get next to one there is nothing to look at.
 */
public class SilhouetteEntity extends MobEntity {
	/** How far away it will still track a player with its head. */
	private static final double LOOK_RANGE = 48.0;

	/**
	 * Get closer than this and it leaves. This needs to stay comfortably outside the renderer's
	 * cutoff, otherwise the player walks out of drawing range mid-exit and every departure looks
	 * like a plain disappearance.
	 */
	private static final double FLEE_DISTANCE = 9.0;

	/** Gives up and vanishes on its own after this long, so they never accumulate. */
	private static final int MAX_LIFETIME_TICKS = 20 * 90;

	/** Vanishes if the nearest player is further away than this. */
	private static final double ABANDON_DISTANCE = 40.0;

	/**
	 * Blocks per tick. 0.75 is about four times a sprinting player - fast enough to read as bolting,
	 * slow enough that the eye catches it. Much above 1.0 and it crosses the whole fog range inside
	 * a couple of ticks, which just looks like it blinked out.
	 */
	private static final double SPRINT_SPEED = 0.75;

	/** Blocks per tick downwards. A 1.8-block-tall body takes about 18 ticks to fully submerge. */
	private static final double SINK_SPEED = 0.10;

	private static final int SPRINT_TICKS = 20;
	private static final int SINK_TICKS = 30;

	/** How it leaves when disturbed. */
	public enum Exit {
		SPRINT,
		SINK,
		BLINK
	}

	private Exit exit;
	private Exit forcedExit;
	private int exitTicks = -1;
	private Vec3d exitDirection = Vec3d.ZERO;
	private int ticksAlive;

	public SilhouetteEntity(EntityType<? extends SilhouetteEntity> type, World world) {
		super(type, world);
		this.setInvulnerable(true);
		this.setSilent(true);
		this.setAiDisabled(true);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 0.0);
	}

	@Override
	public void tick() {
		super.tick();

		// Tracked on both sides: the client keeps the head smooth between rotation packets.
		faceNearestPlayer();

		if (this.getWorld().isClient) {
			return;
		}

		this.ticksAlive++;

		if (this.exitTicks >= 0) {
			tickExit();
			return;
		}

		PlayerEntity player = this.getWorld().getClosestPlayer(this, LOOK_RANGE);

		if (player == null) {
			if (this.ticksAlive > 100) {
				this.discard();
			}
			return;
		}

		double distance = player.squaredDistanceTo(this);

		if (distance < FLEE_DISTANCE * FLEE_DISTANCE) {
			beginExit(player);
		} else if (this.ticksAlive > MAX_LIFETIME_TICKS || distance > ABANDON_DISTANCE * ABANDON_DISTANCE) {
			this.discard();
		}
	}

	private void faceNearestPlayer() {
		PlayerEntity player = this.getWorld().getClosestPlayer(this, LOOK_RANGE);
		if (player == null) {
			return;
		}

		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		double dy = player.getEyeY() - this.getEyeY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) (-(MathHelper.atan2(dy, horizontal) * MathHelper.DEGREES_PER_RADIAN));

		this.setHeadYaw(yaw);
		this.setPitch(pitch);

		// Let the body swing round only once the neck would be at a silly angle.
		if (Math.abs(MathHelper.wrapDegrees(yaw - this.getBodyYaw())) > 60.0F) {
			this.setBodyYaw(yaw);
		}

		if (this.ticksAlive == 0) {
			this.prevHeadYaw = yaw;
			this.prevBodyYaw = yaw;
			this.prevPitch = pitch;
			this.prevYaw = yaw;
		}
	}

	/** Pins the next exit instead of rolling for it. Used by {@code /silhouette} for testing. */
	public void forceExit(Exit exit) {
		this.forcedExit = exit;
	}

	private void beginExit(PlayerEntity player) {
		this.exit = this.forcedExit != null
				? this.forcedExit
				: Exit.values()[this.random.nextInt(Exit.values().length)];
		this.exitTicks = 0;
		this.noClip = true;
		this.setNoGravity(true);

		Vec3d away = this.getPos().subtract(player.getPos());
		this.exitDirection = new Vec3d(away.x, 0.0, away.z).normalize();

		if (this.exitDirection.lengthSquared() < 1.0E-4) {
			this.exitDirection = Vec3d.fromPolar(0.0F, this.getBodyYaw());
		}
	}

	/**
	 * Moves by setting the position outright rather than by setting velocity.
	 *
	 * <p>This matters: the constructor calls {@code setAiDisabled(true)} to keep it standing still,
	 * and {@link MobEntity#isImmobile()} reports true whenever AI is disabled, which makes
	 * {@code LivingEntity} skip its movement step altogether. Velocity set on this entity is simply
	 * never applied - both moving exits silently did nothing and only the final discard showed up,
	 * which looked exactly like the blink exit. Writing the position directly bypasses that, and the
	 * entity tracker syncs it to clients as normal.
	 */
	private void tickExit() {
		this.exitTicks++;

		switch (this.exit) {
			case BLINK -> this.discard();
			case SPRINT -> {
				this.setPosition(this.getX() + this.exitDirection.x * SPRINT_SPEED,
						this.getY(),
						this.getZ() + this.exitDirection.z * SPRINT_SPEED);
				if (this.exitTicks > SPRINT_TICKS) {
					this.discard();
				}
			}
			case SINK -> {
				this.setPosition(this.getX(), this.getY() - SINK_SPEED, this.getZ());
				if (this.exitTicks > SINK_TICKS) {
					this.discard();
				}
			}
		}
	}

	/** Cannot be hurt - but noticing the attempt is reason enough to leave. */
	@Override
	public boolean damage(DamageSource source, float amount) {
		if (!this.getWorld().isClient && this.exitTicks < 0) {
			PlayerEntity player = this.getWorld().getClosestPlayer(this, LOOK_RANGE);
			if (player != null) {
				beginExit(player);
			} else {
				this.discard();
			}
		}
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean canUsePortals(boolean allowVehicles) {
		return false;
	}

	/** Despawning is driven by {@link #tick()}, not by the vanilla mob-cap rules. */
	@Override
	public void checkDespawn() {
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}
}
