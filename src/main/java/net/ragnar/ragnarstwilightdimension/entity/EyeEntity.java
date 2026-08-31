package net.ragnar.ragnarstwilightdimension.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * An eye, very large, hanging a long way out in the dark.
 *
 * <p>It is a flat picture rather than a thing: no depth, no model, no back. The renderer turns it to
 * face whoever is looking, which means it is always looking straight at them and always has been -
 * there is no angle you can get to where it is looking somewhere else, and no way to walk round it
 * and catch it side on. It cannot be reached, it cannot be hurt, and it does nothing at all except
 * be there and then stop being there.
 *
 * <p>Everything about how long it lasts lives here rather than in the renderer, so that the fade is
 * the same fade for everybody watching. See {@link #alpha}.
 */
public class EyeEntity extends Entity {
	/**
	 * Server tick count, synced.
	 *
	 * <p>The client cannot keep this itself. A player who walks into tracking range halfway through
	 * would start their own count at zero, fade the eye in from nothing when it has been up for ten
	 * seconds already, and then have it vanish mid-fade when the server takes it away. Sending the
	 * number costs a handful of bytes a tick for the one or two of these that are ever up at once.
	 */
	private static final TrackedData<Integer> AGE =
			DataTracker.registerData(EyeEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/**
	 * How much of {@link #SIZE} it is actually drawn at, and how long it lasts, both synced for the
	 * same reason {@link #AGE} is - the client cannot work either of them out on its own.
	 *
	 * <p>Both exist for the boss fight and are left alone by everything else. An eye out in the dark
	 * on the disc is thirty blocks across and hangs there for the best part of half a minute, which is
	 * exactly right for something that is only ever scenery. One that is about to shoot at somebody
	 * has to be small enough to read as an object rather than a wall, and gone inside a few seconds.
	 * Defaulted so that an eye nobody has said anything to is the scenery one, unchanged.
	 */
	private static final TrackedData<Float> SCALE =
			DataTracker.registerData(EyeEntity.class, TrackedDataHandlerRegistry.FLOAT);

	private static final TrackedData<Integer> SPAN =
			DataTracker.registerData(EyeEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/**
	 * How tall and wide it is drawn, in blocks. The texture is square, so this is both.
	 *
	 * <p>Also the size of the bounding box, which is what makes it survive being culled: an entity is
	 * dropped from rendering past {@code 64 x} the average side of its box, so a box this size is
	 * good to nearly two kilometres. A small box on a large picture is invisible from exactly the
	 * distance it is meant to be seen at.
	 */
	public static final float SIZE = 30.0F;

	private static final int FADE_IN_TICKS = 80;
	private static final int HOLD_TICKS = 300;
	private static final int FADE_OUT_TICKS = 100;

	/** About twenty-four seconds, all told. */
	public static final int LIFETIME_TICKS = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS;

	public EyeEntity(EntityType<? extends EyeEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.setNoGravity(true);
		this.setInvulnerable(true);
		this.setSilent(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(AGE, 0);
		builder.add(SCALE, 1.0F);
		builder.add(SPAN, LIFETIME_TICKS);
	}

	@Override
	public void tick() {
		super.tick();

		if (this.getWorld().isClient()) {
			return;
		}

		int next = this.dataTracker.get(AGE) + 1;
		if (next >= this.dataTracker.get(SPAN)) {
			this.discard();
			return;
		}

		this.dataTracker.set(AGE, next);
	}

	/**
	 * How solid it is this frame, 0 to 1.
	 *
	 * <p>In and out both, so it never appears or disappears on a frame boundary. The thing it is
	 * trying not to be is a picture that switches on: fading it up over four seconds at that distance
	 * means nobody can say when it started, only that it is there now.
	 */
	public float alpha(float tickDelta) {
		float age = this.dataTracker.get(AGE) + tickDelta;

		// Both fades are shares of the whole rather than fixed lengths, so a short-lived one is the
		// same shape of appearance in less time instead of a four-second fade-in on a three-second eye.
		float span = this.dataTracker.get(SPAN);
		float share = span / LIFETIME_TICKS;
		float fadeIn = FADE_IN_TICKS * share;
		float fadeOut = FADE_OUT_TICKS * share;

		if (age < fadeIn) {
			return age / fadeIn;
		}
		if (age > span - fadeOut) {
			return Math.max(0.0F, (span - age) / fadeOut);
		}
		return 1.0F;
	}

	/** How much of {@link #SIZE} to draw it at. Read by the renderer. */
	public float scale() {
		return this.dataTracker.get(SCALE);
	}

	/**
	 * Makes it a small, brief one - an eye that has turned up to do something rather than to be seen.
	 *
	 * <p>Has to be called before it is spawned, or the first thing every client in range gets is a
	 * thirty-block eye for one tick.
	 */
	public void shrink(float scale, int lifetime) {
		this.dataTracker.set(SCALE, scale);
		this.dataTracker.set(SPAN, lifetime);
	}

	/** Nothing can touch it, so nothing needs to try. */
	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	/** Not saved. One that was up when the server went down has been over for a long time. */
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
