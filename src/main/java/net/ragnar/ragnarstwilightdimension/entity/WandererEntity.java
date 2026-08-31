package net.ragnar.ragnarstwilightdimension.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Something the shape of a player, twice the size, going somewhere in a hurry.
 *
 * <p>It does not spawn, stand around and then leave the way a {@link SilhouetteEntity} does. Its
 * whole existence is a single straight line: it is placed well out in the fog, runs a fixed distance
 * along a path that passes the player at {@code WandererSpawner.PASS_DISTANCE}, and is discarded the
 * moment it reaches the far end. It never turns, never slows down and never acknowledges anyone.
 *
 * <p>It runs faster than a sprinting player on purpose, so following it is hopeless - by the time
 * you have turned to face it, it is already going away from you and gaining.
 */
public class WandererEntity extends MobEntity {
	/** How tall it stands, in blocks. */
	public static final float HEIGHT = 4.0F;

	/** The player model is 1.8 blocks tall, so this is what the renderer has to scale it by. */
	public static final float MODEL_SCALE = HEIGHT / 1.8F;

	/** Width of the hitbox, the ordinary player width taken up by the same factor. */
	public static final float WIDTH = 0.6F * MODEL_SCALE;

	/** Total length of the run. Both ends sit far enough out to be well inside the fog. */
	public static final double RUN_LENGTH = 48.0;

	/**
	 * How long this one's run actually is. Defaults to {@link #RUN_LENGTH} and is only ever different
	 * for the boss fight, where the disc is seventy across and a forty-eight block run would stop dead
	 * in the middle of the arena instead of crossing it. See {@code ChargeAttack}.
	 */
	private double runLength = RUN_LENGTH;

	/**
	 * Whether it walks the ground under it or holds the height it started at.
	 *
	 * <p>True everywhere in the twilight, which is hilly and where a runner that ignored it would walk
	 * through the sides of things. False on the disc, which is one flat sheet at a known height with
	 * nothing under it - and where reading the ground would be worse than useless: the run starts and
	 * ends out past the rim, over the void, where the heightmap has no floor to report and the answer
	 * is the bottom of the world. It would come in sinking and leave the same way.
	 */
	private boolean followGround = true;

	/**
	 * Whether it has stopped part way along its run.
	 *
	 * <p>A wanderer that stops is a contradiction everywhere else in the mod - the whole of what it is
	 * out in the twilight is a thing that does not stop, does not turn, and cannot be caught. The boss
	 * fight is the one place that gets broken on purpose, and it is broken exactly once, by the jumper.
	 * See {@code JumperAttack}.
	 *
	 * <p>Paused is not finished: it keeps its heading, its yaw and its lifetime, and whatever paused it
	 * is expected to be moving it by hand from then on.
	 */
	private boolean paused;

	/**
	 * Blocks per tick. A sprinting player manages about 0.28, so this clears them by roughly 60% -
	 * chasing it does not just fail to close the gap, it loses ground fast.
	 */
	private static final double SPEED = 0.45;

	/**
	 * Most it will rise or drop in a single tick while following the ground. Without a cap it snaps
	 * straight up cliff faces, which reads as a teleport rather than a run.
	 */
	private static final double MAX_STEP = 0.6;

	/** Backstop, in case a run is somehow never finished - nothing is left standing around. */
	private static final int MAX_LIFETIME_TICKS = 20 * 30;

	/**
	 * Stride lengths in blocks, cycled through: two short then one long.
	 *
	 * <p>This is the whole trick. What you are looking at is a biped, and the legs on screen swing in
	 * an even two-beat cycle because the walk animation is driven by distance travelled. The footfalls
	 * are not - they run on this three-beat pattern instead, so the sound never lines up with the
	 * legs. It comes across as something with more feet than it is showing you, without ever being
	 * explicit enough to point at. Set all three entries equal to get an ordinary gait back.
	 */
	private static final double[] STRIDE_PATTERN = {2.0, 2.0, 3.2};

	/** Blocks travelled before the first footfall, so it does not land on the spawn tick. */
	private static final double FIRST_STEP_AT = 1.0;

	/**
	 * Above 1.0 on purpose. Volume past 1 does not play louder - the client clamps gain - but it does
	 * stretch the range to {@code 16 x volume}, putting the footfalls out to about 19 blocks against
	 * a fog line of 11. The run is only visible for around 1.4 seconds but audible for nearly 4, so
	 * you hear it coming before there is anything to see and hear it leave after there is not.
	 */
	private static final float STEP_VOLUME = 1.2F;
	private static final float STEP_PITCH = 0.55F;

	/** The weight layer under each footfall. */
	private static final float WEIGHT_VOLUME = 1.1F;
	private static final float WEIGHT_PITCH = 0.65F;

	private int strideIndex;
	private double nextStepAt = FIRST_STEP_AT;

	/** Unit vector along the run. Zero means it has no run to make, and it removes itself. */
	private Vec3d runDirection = Vec3d.ZERO;

	private double travelled;
	private int ticksAlive;

	/** Last horizontal position, so the walk cycle can be driven from actual movement. */
	private double lastX;
	private double lastZ;
	private boolean hasLast;

	public WandererEntity(EntityType<? extends WandererEntity> type, World world) {
		super(type, world);
		this.setInvulnerable(true);
		this.setSilent(true);
		this.setAiDisabled(true);
		this.setNoGravity(true);
		this.noClip = true;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 0.0);
	}

	/**
	 * Points it down its run and puts it at the start.
	 *
	 * @param start     where it comes out of the fog
	 * @param direction which way it is heading - flattened and normalised here, so a rough vector is fine
	 */
	public void beginRun(Vec3d start, Vec3d direction) {
		beginRun(start, direction, RUN_LENGTH);
	}

	/** The same, for a run that has to be longer or shorter than the one it was written for. */
	public void beginRun(Vec3d start, Vec3d direction, double length) {
		beginRun(start, direction, length, true);
	}

	/** The same, for a run across ground that should be ignored. See {@link #followGround}. */
	public void beginRun(Vec3d start, Vec3d direction, double length, boolean followGround) {
		this.runLength = length;
		this.followGround = followGround;
		Vec3d flat = new Vec3d(direction.x, 0.0, direction.z);
		this.runDirection = flat.lengthSquared() < 1.0E-6 ? Vec3d.ZERO : flat.normalize();

		float yaw = facing(this.runDirection);
		this.refreshPositionAndAngles(start.x, start.y, start.z, yaw, 0.0F);
		this.setBodyYaw(yaw);
		this.setHeadYaw(yaw);
		this.prevBodyYaw = yaw;
		this.prevHeadYaw = yaw;

		this.noClip = true;
	}

	/** Stops it where it stands without ending it. See {@link #paused}. */
	public void pause() {
		this.paused = true;
	}

	@Override
	public void tick() {
		super.tick();

		if (!this.getWorld().isClient) {
			this.ticksAlive++;

			// A saved-and-reloaded one has no run left in it, so it leaves rather than standing there.
			if (this.runDirection.lengthSquared() < 1.0E-6 || this.ticksAlive > MAX_LIFETIME_TICKS) {
				this.discard();
				return;
			}

			if (!this.paused) {
				advance();
			}
		}

		// Driven from how far it actually moved rather than from velocity, which it never has. This
		// runs on both sides: the server moves it outright, and the client sees the same movement
		// arrive through the entity tracker, so the legs swing in both places.
		if (this.hasLast) {
			double dx = this.getX() - this.lastX;
			double dz = this.getZ() - this.lastZ;
			this.updateLimbs((float) Math.sqrt(dx * dx + dz * dz));
		}

		this.lastX = this.getX();
		this.lastZ = this.getZ();
		this.hasLast = true;
	}

	/**
	 * Steps one tick along the run.
	 *
	 * <p>Moves by writing the position rather than by setting velocity, for the same reason
	 * {@link SilhouetteEntity} does: the constructor disables AI, {@code MobEntity.isImmobile()}
	 * reports true whenever AI is off, and {@code LivingEntity} then skips its movement step
	 * entirely, so velocity set on this entity would never be applied.
	 */
	private void advance() {
		double x = this.getX() + this.runDirection.x * SPEED;
		double z = this.getZ() + this.runDirection.z * SPEED;

		this.setPosition(x, this.followGround ? followGround(x, z) : this.getY(), z);

		float yaw = facing(this.runDirection);
		this.setYaw(yaw);
		this.setBodyYaw(yaw);
		this.setHeadYaw(yaw);

		this.travelled += SPEED;
		maybeStep();

		if (this.travelled >= this.runLength) {
			this.discard();
		}
	}

	/** Emits a footfall once the run has covered the next stride in {@link #STRIDE_PATTERN}. */
	private void maybeStep() {
		if (this.travelled < this.nextStepAt) {
			return;
		}

		this.nextStepAt += STRIDE_PATTERN[this.strideIndex % STRIDE_PATTERN.length];
		this.strideIndex++;
		playStep();
	}

	/**
	 * One footfall, built from two sounds at once.
	 *
	 * <p>A grass step carries the material - the same contact noise a player makes - dropped a long
	 * way in pitch so the scale is wrong for the sound it is obviously meant to be. Underneath it a
	 * heavier thud carries the mass. Neither is unusual on its own; together they are a person's
	 * footstep with far too much behind it.
	 */
	private void playStep() {
		// Keeps consecutive footfalls from reading as one sample on a loop.
		float jitter = 0.96F + this.random.nextFloat() * 0.08F;
		World world = this.getWorld();

		world.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.BLOCK_GRASS_STEP, SoundCategory.PLAYERS,
				STEP_VOLUME, STEP_PITCH * jitter);

		world.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE,
				WEIGHT_VOLUME, WEIGHT_PITCH * jitter);
	}

	/** The ground height at a column, approached a step at a time so slopes are run up, not jumped. */
	private double followGround(double x, double z) {
		double target = this.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				MathHelper.floor(x), MathHelper.floor(z));
		return this.getY() + MathHelper.clamp(target - this.getY(), -MAX_STEP, MAX_STEP);
	}

	private static float facing(Vec3d direction) {
		return (float) (MathHelper.atan2(direction.z, direction.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
	}

	/** Cannot be hurt, and does not care that you tried. */
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

	/** Removal is driven by the end of the run, not by the vanilla mob-cap rules. */
	@Override
	public void checkDespawn() {
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}
}
