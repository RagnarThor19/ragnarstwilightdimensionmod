package net.ragnar.ragnarstwilightdimension.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * A figure the shape of a player, with nothing on it.
 *
 * <p>Not a Steve. Where every other thing in the dimension wears the default skin - the silhouette,
 * the giant, the ones that come for you on a blood moon - this one is blank white all over: no face,
 * no clothes, no features at all. It is the same shape as the others and none of the detail, which is
 * what makes it read as the thing the others are copies of rather than one more copy.
 *
 * <p>It hangs in the air, it does not move, and it does not do anything except turn to face whoever
 * it was put there for. It cannot be hurt and drops nothing.
 *
 * <p>Used by {@code Stare}, and deliberately written so it is not owned by that event: give it a
 * target and a position and it will hang there staring at them until something takes it away. Any
 * later beat that wants a blank figure standing somewhere can use this one as it is.
 */
public class PaleFigureEntity extends MobEntity {
	/**
	 * Removes itself after this long no matter what. Nothing but a bug can leave one of these
	 * standing - whatever put it there is expected to take it away - so this is the backstop that
	 * keeps a bug from becoming a permanent fixture of somebody's world.
	 */
	private static final int MAX_LIFETIME_TICKS = 20 * 30;

	/** How far away it will still turn to face the one it is watching. */
	private static final double LOOK_RANGE = 128.0;

	/** Who it is here for. Null means it faces whoever is nearest instead. */
	private UUID target;

	private int ticksAlive;

	public PaleFigureEntity(EntityType<? extends PaleFigureEntity> type, World world) {
		super(type, world);
		this.setInvulnerable(true);
		this.setSilent(true);
		this.setAiDisabled(true);
		this.setNoGravity(true);

		// It is placed in mid-air by whatever spawns it and is never meant to be walked into, so it
		// takes no part in collision at all.
		this.noClip = true;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 0.0);
	}

	/** Points it at one player in particular, and keeps it pointed there. */
	public void watch(PlayerEntity player) {
		this.target = player.getUuid();
		facePlayer(player);
	}

	@Override
	public void tick() {
		super.tick();

		// Both sides: the server decides the angles, the client interpolates between the rotation
		// packets it is sent. Doing it server-side only would step once a tick and read as stuttering.
		facePlayer(findTarget());

		if (this.getWorld().isClient) {
			return;
		}

		if (++this.ticksAlive > MAX_LIFETIME_TICKS) {
			this.discard();
		}
	}

	private PlayerEntity findTarget() {
		if (this.target != null) {
			PlayerEntity player = this.getWorld().getPlayerByUuid(this.target);
			if (player != null) {
				return player;
			}
		}

		return this.getWorld().getClosestPlayer(this, LOOK_RANGE);
	}

	/**
	 * Squares the whole figure up to them - body, head and eyes together.
	 *
	 * <p>Not the {@link SilhouetteEntity} treatment of turning only the neck. That one is pretending
	 * to have been looking at something else; this one has never been looking at anything else.
	 */
	private void facePlayer(PlayerEntity player) {
		if (player == null) {
			return;
		}

		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		double dy = player.getEyeY() - this.getEyeY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) (-(MathHelper.atan2(dy, horizontal) * MathHelper.DEGREES_PER_RADIAN));

		this.setYaw(yaw);
		this.setBodyYaw(yaw);
		this.setHeadYaw(yaw);
		this.setPitch(pitch);

		if (this.ticksAlive == 0) {
			// Otherwise the first frame it is visible is a frame of it swinging round, which reads as it
			// having noticed you. It was facing you before you looked up.
			this.prevYaw = yaw;
			this.prevBodyYaw = yaw;
			this.prevHeadYaw = yaw;
			this.prevPitch = pitch;
		}
	}

	/** Cannot be hurt, and unlike the silhouette does not even acknowledge the attempt. */
	@Override
	public boolean damage(DamageSource source, float amount) {
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

	/** Lifetime is decided by {@link #tick()}, not by the vanilla mob-cap rules. */
	@Override
	public void checkDespawn() {
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}
}
