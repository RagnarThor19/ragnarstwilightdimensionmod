package net.ragnar.ragnarstwilightdimension.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Arm;
import net.minecraft.world.World;

/**
 * The thing in the bedrock layer.
 *
 * <p>Fifty blocks of Steve standing on the bedrock floor. The empty space under the dimension's crust
 * runs from the bedrock at {@code -64} up to the underside of the terrain at {@code 0}, which is
 * sixty-three blocks; this stands in it with thirteen to spare, so it fits the room exactly and could
 * not be anywhere else in the world.
 *
 * <p>It does not move, in the sense that matters. Between heartbeats it is perfectly still - no
 * walking, no swaying, no tracking you with its head. What happens instead is that it is somewhere
 * closer the next time the fog opens, and nothing is ever shown moving it. See {@link TheDeep}, which
 * owns every part of that and is where the distance actually lives.
 *
 * <h2>It faces you. It does not look at you.</h2>
 *
 * <p>The yaw is set once, when it is put down, so that its front is turned towards wherever the
 * player was standing at that moment - you always see the face, never the back or the side. The pitch
 * is flat and the head is locked to the body, so it is not looking <i>down</i> at you the way
 * {@link GiantSteveEntity} does, and it does not turn as you move.
 *
 * <p>That is the whole difference between the two, and it is the entire effect. The giant is aimed at
 * you and keeps being aimed at you, which reads as attention. This is a statue that happens to be
 * pointed your way, which reads as something far worse: it does not need to watch you, and it is
 * still getting closer.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>Owned by {@link TheDeep}, not by itself, and {@link #shouldSave} is false so it can never be
 * written to disk. Between those two it cannot outlive the thing that put it there - a chunk unload
 * drops it outright rather than parking a fifty-block figure in the save file, and anything that
 * somehow survives without an owner removes itself on its next tick.
 */
public class DeepSteveEntity extends MobEntity {
	/** How tall it stands. Thirteen blocks short of the ceiling it is standing under. */
	public static final float HEIGHT = 50.0F;

	/** The player model is 1.8 blocks tall, so this is what the renderer has to scale it by. */
	public static final float MODEL_SCALE = HEIGHT / 1.8F;

	/** Width of the hitbox, the ordinary player width taken up by the same factor. */
	public static final float WIDTH = 0.6F * MODEL_SCALE;

	public DeepSteveEntity(EntityType<? extends DeepSteveEntity> type, World world) {
		super(type, world);
		this.setInvulnerable(true);
		// Silent to the server. What you hear coming off it is started by the client the moment the
		// thing is sent - see {@code DeepSteveSound} - because a looping sound has to actually loop,
		// and re-playing a ten-second file every ten seconds is not a loop, it is a stutter.
		this.setSilent(true);
		this.setAiDisabled(true);
		this.setNoGravity(true);
		// Fifty blocks of hitbox in a sixty-three block room is always intersecting something, and it
		// has nothing to collide with anyway.
		this.noClip = true;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 0.0);
	}

	/**
	 * Puts it down facing a point, with the head locked and the gaze level.
	 *
	 * <p>Both the current and the previous angles are written. Without that the client interpolates
	 * from whatever it was last facing, and fifty blocks of figure swinging round on arrival reads as
	 * an animation - which would say it had just got there, when the entire idea is that it has been
	 * standing exactly there the whole time and you simply could not see it.
	 */
	public void faceOnce(double towardsX, double towardsZ) {
		double dx = towardsX - this.getX();
		double dz = towardsZ - this.getZ();
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90.0F;

		this.setYaw(yaw);
		this.setBodyYaw(yaw);
		this.setHeadYaw(yaw);
		this.setPitch(0.0F);

		this.prevYaw = yaw;
		this.prevBodyYaw = yaw;
		this.prevHeadYaw = yaw;
		this.prevPitch = 0.0F;
	}

	@Override
	public void tick() {
		super.tick();

		if (this.getWorld().isClient) {
			return;
		}

		// A last resort rather than the normal path. TheDeep takes its own figure away when the watch
		// ends; this catches one that outlived its owner some other way - a reload, a crash, a rule
		// changing underneath it - so that nothing this size is ever left standing with no reason to.
		if (!TheDeep.owns(this)) {
			this.discard();
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

	/**
	 * Never written to disk.
	 *
	 * <p>The same rule {@code TheEntity} follows, for the same reason: this belongs to a watch that
	 * only exists while somebody is standing under the world. Saved, it would come back on load with
	 * nothing driving it, in a chunk nobody is in, fifty blocks tall and permanent.
	 */
	@Override
	public boolean shouldSave() {
		return false;
	}

	/** Removal is driven by {@link TheDeep}, not by the vanilla mob-cap rules. */
	@Override
	public void checkDespawn() {
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}
}
