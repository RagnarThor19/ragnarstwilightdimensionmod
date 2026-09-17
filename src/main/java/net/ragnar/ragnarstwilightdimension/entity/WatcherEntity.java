package net.ragnar.ragnarstwilightdimension.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A hundred blocks of it, standing off the edge of the world, with the eye for a face.
 *
 * <p>This is what the eyes out in the dark turned into. The eye itself has not changed at all - it is
 * the same picture at the same distance - but it is no longer hanging in the air on its own. It is
 * the face of something, and the something is taller than anything else in the game: a black shape
 * that starts below where the disc's floor ends and goes up past where the world does.
 *
 * <p>Everything but the face is <b>flat black</b>, which is the whole of the design. There is nothing
 * out there to light it, the sky is not drawn, and the fog colour is zero - so the body is not a shape
 * you see, it is a shape you see <em>through</em>. What reads is the hole it makes: the white of the
 * far rim of the disc goes out behind its legs, the snow stops falling where it is standing, and
 * eighty blocks up there is an eye. Somebody who never looks up never finds out what the eye is
 * attached to.
 *
 * <h2>What it does</h2>
 *
 * <p>Outside the fight, nothing whatsoever. It cannot be reached - it stands in the void a good forty
 * blocks past the drop - cannot be hurt, cannot be pushed, and never moves. There is always at least
 * one of them up, which is the one real change from the eyes: those rolled once every two minutes and
 * were gone inside half of one, so the dark was mostly empty. See {@link WatcherSpawner}.
 *
 * <p>During the fight they fire, and what they fire is the largest thing in the mod by a distance.
 * See {@code WatcherAttack}. All this class holds of that is {@link #charge(float)}, which is the eye
 * winding up, and which is tracked so that every client watching sees the same four and a half
 * seconds of warning.
 */
public class WatcherEntity extends Entity {
	/**
	 * How tall it is drawn, in blocks, and the height of the box it is culled against.
	 *
	 * <p>The blank one's dimension is a hundred and twenty-eight blocks tall in total and the floor
	 * sits at sixty-four, so one of these standing on the disc's own level would have its head well
	 * past the top of the world. That is fine - nothing about drawing an entity stops at the build
	 * limit - and it is also why {@link WatcherSpawner} stands them in the void <i>below</i> the disc
	 * rather than level with it: sunk far enough that the face comes out at a height somebody standing
	 * in the middle of the circle can find without looking straight up.
	 */
	public static final float HEIGHT = 100.0F;

	/** How wide the shape gets, across the shoulders and the hanging arms. Also the box. */
	public static final float WIDTH = 36.0F;

	/**
	 * Where the face is, as a share of the height.
	 *
	 * <p>The one number the fight reads off this class: it is where a beam leaves from, and it has to
	 * agree with the renderer or the laser comes out of its chest. See {@link #eyePos()}.
	 */
	public static final double EYE_SHARE = 0.91;

	/**
	 * Server tick count, synced, for exactly the reason the eye's is - a client that walks into range
	 * halfway through cannot count from zero and be right about the fade.
	 */
	private static final TrackedData<Integer> AGE =
			DataTracker.registerData(WatcherEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/** How long it stands there, or 0 for one that is not going anywhere. */
	private static final TrackedData<Integer> SPAN =
			DataTracker.registerData(WatcherEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/** How far into winding up a shot it is, 0 to 1. Read by the renderer, and by nothing else. */
	private static final TrackedData<Float> CHARGE =
			DataTracker.registerData(WatcherEntity.class, TrackedDataHandlerRegistry.FLOAT);

	/**
	 * How long it takes to be there, in ticks.
	 *
	 * <p>Five seconds, and pointedly slower than the eye's four at a fraction of the size. A hundred
	 * blocks of anything arriving quicker than that reads as a thing being spawned; this way nobody
	 * can say when it came, only that the shape of the dark is not the shape it was.
	 */
	private static final int FADE_IN_TICKS = 100;

	/** And how long one with a lifespan takes to stop being there. */
	private static final int FADE_OUT_TICKS = 80;

	/**
	 * How fast a charge nobody is holding up runs back down.
	 *
	 * <p>The attack sets this every tick while it winds up and clears it when it fires. The decay is
	 * for the cases the attack cannot handle: a phase seam, a server stopping, the last player leaving
	 * mid-wind-up. Without it, a fight ended at the wrong moment leaves something a hundred blocks
	 * tall with its eye lit, for ever.
	 */
	private static final float CHARGE_DECAY = 0.04F;

	/** The tick {@link #charge(float)} was last called on, so the decay knows when to run. */
	private int chargedAt = -100;

	public WatcherEntity(EntityType<? extends WatcherEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.setNoGravity(true);
		this.setInvulnerable(true);
		this.setSilent(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(AGE, 0);
		builder.add(SPAN, 0);
		builder.add(CHARGE, 0.0F);
	}

	@Override
	public void tick() {
		super.tick();

		if (this.getWorld().isClient()) {
			return;
		}

		int now = this.dataTracker.get(AGE) + 1;
		int span = this.dataTracker.get(SPAN);

		if (span > 0 && now >= span) {
			this.discard();
			return;
		}

		this.dataTracker.set(AGE, now);

		float charge = this.dataTracker.get(CHARGE);

		if (charge > 0.0F && now - this.chargedAt > 2) {
			this.dataTracker.set(CHARGE, Math.max(0.0F, charge - CHARGE_DECAY));
		}
	}

	/**
	 * How solid it is this frame, 0 to 1.
	 *
	 * <p>Multiplied into the alpha of what is already a black shape, so on the body it is doing
	 * nothing anybody can see. It is for the face: the eye comes up out of the dark, and the black
	 * around it was never visible in the first place.
	 */
	public float alpha(float tickDelta) {
		float age = this.dataTracker.get(AGE) + tickDelta;
		int span = this.dataTracker.get(SPAN);

		if (age < FADE_IN_TICKS) {
			return age / FADE_IN_TICKS;
		}

		if (span > 0 && age > span - FADE_OUT_TICKS) {
			return Math.max(0.0F, (span - age) / FADE_OUT_TICKS);
		}

		return 1.0F;
	}

	/** How lit the eye is, 0 to 1. Read by the renderer. */
	public float charge() {
		return this.dataTracker.get(CHARGE);
	}

	/** The fight, winding one up. Called every tick it is charging, and once more to clear it. */
	public void charge(float amount) {
		this.chargedAt = this.dataTracker.get(AGE);
		this.dataTracker.set(CHARGE, MathHelper.clamp(amount, 0.0F, 1.0F));
	}

	/** Where a beam leaves from: the middle of the face, ninety-one blocks up. */
	public Vec3d eyePos() {
		return new Vec3d(this.getX(), this.getY() + HEIGHT * EYE_SHARE, this.getZ());
	}

	/**
	 * How long it stands there, in ticks, or 0 for one that stays.
	 *
	 * <p>Has to be called before it is spawned, like the eye's {@code shrink} and for the same reason:
	 * it is tracked data, and one set afterwards is a correction every client has to be sent.
	 */
	public void lifespan(int ticks) {
		this.dataTracker.set(SPAN, ticks);
	}

	/** Whether this one is here for good rather than passing through. */
	public boolean permanent() {
		return this.dataTracker.get(SPAN) == 0;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	/** Not saved. The spawner puts one back the moment anybody is on the disc to see it. */
	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
