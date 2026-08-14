package net.ragnar.ragnarstwilightdimension.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Arm;
import net.minecraft.util.math.BlockPos;
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
 *
 * <p>The one exception is the grave watcher - see {@link #watchGrave(BlockPos)} - which stands at a
 * gravestone rather than out in the fog, ignores you until you are almost touching it, and is drawn
 * at any distance because the whole point of it is to be walked right up to.
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

	/**
	 * Tracked rather than kept server-side only, because both the renderer and the client-side facing
	 * code behave differently for a grave watcher and both run before any of its state is asked for.
	 */
	private static final TrackedData<Boolean> GRAVE_WATCHER =
			DataTracker.registerData(SilhouetteEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/**
	 * How close a player has to get before a grave watcher notices them. Small on purpose - it should
	 * be possible to stand and look at it from across the clearing without anything happening.
	 */
	private static final double NOTICE_DISTANCE = 2.0;

	/** Half a second between the head coming round and the rest of it leaving. */
	private static final int NOTICE_DELAY_TICKS = 10;

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

	/** Grave watchers only: the sign it is reading. */
	private BlockPos gazeTarget;

	/** Grave watchers only: ticks since it noticed a player, or -1 while it is still undisturbed. */
	private int noticeTicks = -1;

	public SilhouetteEntity(EntityType<? extends SilhouetteEntity> type, World world) {
		super(type, world);
		this.setInvulnerable(true);
		this.setSilent(true);
		this.setAiDisabled(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(GRAVE_WATCHER, false);
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

		if (this.isGraveWatcher()) {
			// Unlike the wandering kind, every bit of a grave watcher's facing is decided by what it is
			// doing rather than by where the player is, and the client has no way to know which of the
			// two it is mid-way through. So the server drives all of it and the client just interpolates
			// the rotation packets, which is what turns the snap into a fast turn rather than a jump.
			if (!this.getWorld().isClient) {
				tickGraveWatcher();
			}
			return;
		}

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

	public boolean isGraveWatcher() {
		return this.dataTracker.get(GRAVE_WATCHER);
	}

	/**
	 * Turns this one into the figure that stands at a gravestone.
	 *
	 * <p>It looks at the sign instead of at you, it never wanders off or times out, and it stays where
	 * it was put across saves. It only reacts when someone gets within {@link #NOTICE_DISTANCE}: the
	 * head comes round first, and half a second later the rest of it bolts.
	 *
	 * @param sign the block position of the sign it is reading - may be null if the grave has since
	 *             been dug up, in which case it simply keeps whatever direction it was left facing
	 */
	public void watchGrave(BlockPos sign) {
		this.dataTracker.set(GRAVE_WATCHER, true);
		this.gazeTarget = sign;

		// Nothing else stops it despawning: the lifetime and abandon-distance checks in tick() are on
		// the branch this one never reaches, and vanilla's mob cap would otherwise remove it the first
		// time the player walks far enough away.
		this.setPersistent();

		faceGazeTarget();
	}

	private void tickGraveWatcher() {
		this.ticksAlive++;

		if (this.exitTicks >= 0) {
			// Keeps staring while it goes.
			snapHeadTo(this.getWorld().getClosestPlayer(this, LOOK_RANGE));
			tickExit();
			return;
		}

		if (this.noticeTicks >= 0) {
			PlayerEntity player = this.getWorld().getClosestPlayer(this, LOOK_RANGE);
			snapHeadTo(player);

			if (++this.noticeTicks >= NOTICE_DELAY_TICKS) {
				if (player == null) {
					// Whoever it was is gone. Back to the sign.
					this.noticeTicks = -1;
					faceGazeTarget();
				} else {
					forceExit(Exit.SPRINT);
					beginExit(player);
				}
			}
			return;
		}

		faceGazeTarget();

		PlayerEntity intruder = this.getWorld().getClosestPlayer(this, NOTICE_DISTANCE);
		if (intruder != null) {
			this.noticeTicks = 0;
			snapHeadTo(intruder);
		}
	}

	/** Points the whole figure - body, head and eyes - down the grave at the sign. */
	private void faceGazeTarget() {
		if (this.gazeTarget == null) {
			return;
		}

		double dx = this.gazeTarget.getX() + 0.5 - this.getX();
		double dz = this.gazeTarget.getZ() + 0.5 - this.getZ();
		double dy = this.gazeTarget.getY() + 0.5 - this.getEyeY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) (-(MathHelper.atan2(dy, horizontal) * MathHelper.DEGREES_PER_RADIAN));

		this.setYaw(yaw);
		this.setBodyYaw(yaw);
		this.setHeadYaw(yaw);
		this.setPitch(pitch);

		// Held level with the current values the whole time it is undisturbed, so that the moment it
		// does look up there is a real previous angle for the client to interpolate away from - that
		// interpolation is the snap.
		this.prevYaw = yaw;
		this.prevBodyYaw = yaw;
		this.prevHeadYaw = yaw;
		this.prevPitch = pitch;
	}

	/**
	 * Head and eyes only. The body is left squared up to the grave, which is the entire effect - a
	 * figure that has not moved a muscle except to turn its face towards you.
	 */
	private void snapHeadTo(PlayerEntity player) {
		if (player == null) {
			return;
		}

		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		double dy = player.getEyeY() - this.getEyeY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		this.setHeadYaw((float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F);
		this.setPitch((float) (-(MathHelper.atan2(dy, horizontal) * MathHelper.DEGREES_PER_RADIAN)));
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

			if (this.isGraveWatcher()) {
				// Anyone close enough to swing at it is well inside the notice distance anyway, so this
				// only matters for arrows. Same reaction either way: look up, then go.
				if (this.noticeTicks < 0) {
					this.noticeTicks = 0;
					snapHeadTo(player);
				}
			} else if (player != null) {
				beginExit(player);
			} else {
				this.discard();
			}
		}
		return false;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);

		if (this.isGraveWatcher()) {
			nbt.putBoolean("GraveWatcher", true);
			if (this.gazeTarget != null) {
				nbt.putInt("GazeX", this.gazeTarget.getX());
				nbt.putInt("GazeY", this.gazeTarget.getY());
				nbt.putInt("GazeZ", this.gazeTarget.getZ());
			}
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);

		if (nbt.getBoolean("GraveWatcher")) {
			watchGrave(nbt.contains("GazeX")
					? new BlockPos(nbt.getInt("GazeX"), nbt.getInt("GazeY"), nbt.getInt("GazeZ"))
					: null);
		}
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
