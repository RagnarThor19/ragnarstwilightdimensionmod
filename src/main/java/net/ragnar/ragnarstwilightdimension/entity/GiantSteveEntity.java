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
import net.ragnar.ragnarstwilightdimension.event.BloodMoon;

/**
 * The one that does not come for you.
 *
 * <p>It is put down at the far edge of the world the moment a blood moon starts, twenty blocks tall
 * and seventy-five blocks out, and from then until the event ends it does exactly one thing: face
 * the player. It never takes a step, never makes a sound and cannot be hurt. Everything else that
 * night is running at you; this is the only thing that does not have to.
 *
 * <p>The fog stops at eleven blocks, so it is never seen properly - at that range it is drawn
 * entirely in fog colour, which during the event is red, against a background that is not. What is
 * actually visible is a shape, in outline, that is far too large and is pointed at you. Walking
 * towards it does not resolve it into anything, because it turns to keep facing you the whole way.
 *
 * <p>Like {@link BloodSteveEntity} it belongs to the event and nothing else. When the blood moon
 * ends it removes itself on its next tick, at the same time as all the ordinary ones.
 */
public class GiantSteveEntity extends MobEntity {
	/** How tall it stands, in blocks. Eleven times a player, and about three times the wanderer. */
	public static final float HEIGHT = 20.0F;

	/** The player model is 1.8 blocks tall, so this is what the renderer has to scale it by. */
	public static final float MODEL_SCALE = HEIGHT / 1.8F;

	/** Width of the hitbox, the ordinary player width taken up by the same factor. */
	public static final float WIDTH = 0.6F * MODEL_SCALE;

	/**
	 * How far away it will still turn to face someone. Comfortably past the distance it is placed at,
	 * so that walking towards it never gets it to stop watching - the head only ever comes round
	 * further as you close.
	 */
	private static final double LOOK_RANGE = 160.0;

	private int ticksAlive;

	public GiantSteveEntity(EntityType<? extends GiantSteveEntity> type, World world) {
		super(type, world);
		this.setInvulnerable(true);
		this.setSilent(true);
		this.setAiDisabled(true);
		this.setNoGravity(true);
		// Twenty blocks of hitbox will always be intersecting something on uneven ground, and there is
		// nothing it needs to collide with anyway - it never moves.
		this.noClip = true;
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

		// Run on both sides: the server decides the angles and the client interpolates between the
		// rotation packets, which is what keeps the turn smooth rather than stepping once a tick.
		facePlayer();

		if (this.getWorld().isClient) {
			return;
		}

		this.ticksAlive++;

		// The event owning it is the only thing keeping it here.
		if (!BloodMoon.isActive()) {
			this.discard();
		}
	}

	/**
	 * Points the whole figure at the nearest player - body as well as head.
	 *
	 * <p>Deliberately not the {@link SilhouetteEntity} treatment of turning only the neck. At this
	 * size a head swivel is lost in the silhouette, and something this large squaring itself up to
	 * you is the entire effect.
	 */
	private void facePlayer() {
		PlayerEntity player = this.getWorld().getClosestPlayer(this, LOOK_RANGE);
		if (player == null) {
			return;
		}

		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		double dy = player.getEyeY() - this.getEyeY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		// Its eyes are seventeen blocks up, so this is always some way downwards - it is looking down
		// at you from the start and only more so the closer you get.
		float pitch = (float) (-(MathHelper.atan2(dy, horizontal) * MathHelper.DEGREES_PER_RADIAN));

		this.setYaw(yaw);
		this.setBodyYaw(yaw);
		this.setHeadYaw(yaw);
		this.setPitch(pitch);

		if (this.ticksAlive == 0) {
			// Otherwise the first frame interpolates from whatever it was spawned facing, and something
			// this big swinging round on arrival reads as an animation rather than as having been there.
			this.prevYaw = yaw;
			this.prevBodyYaw = yaw;
			this.prevHeadYaw = yaw;
			this.prevPitch = pitch;
		}
	}

	/** Cannot be hurt, and does not react to the attempt. */
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

	/** Removal is driven by the end of the event, not by the vanilla mob-cap rules. */
	@Override
	public void checkDespawn() {
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}
}
