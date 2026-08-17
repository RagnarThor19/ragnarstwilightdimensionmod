package net.ragnar.ragnarstwilightdimension.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * The thing it has been pointing at, arriving.
 *
 * <p>For the whole of the mod the witness stands in a field with one arm up at a sky that is not
 * rendered, and the joke has always been that there is nothing there. Three times in the fight - see
 * {@link WitnessEntity#beginPoint} - it stops, plants itself, puts the arm back up, and the joke stops
 * being one: a ring is drawn on the ground, something comes down through the fog into the middle of
 * it, and whoever is still standing inside wears it.
 *
 * <p>Each one is a second and a half of warning and then a circle of damage. The warning is drawn
 * three ways at once because the fog during a point is four or five blocks and no single cue survives
 * that:
 *
 * <ul>
 *   <li>a <b>rim</b> of pale dust at the edge of the circle, which is the honest boundary - everything
 *       inside it is hit and everything outside it is not, exactly;
 *   <li>a <b>fuse</b> burning round that same rim, one point at a time, which lands when it gets back
 *       to where it started - a timer read as an angle, which is the only kind anybody can read while
 *       running;
 *   <li>and a <b>column</b> at the middle, which is the part still visible when the player is close
 *       enough that the rim has gone into the fog behind them.
 * </ul>
 *
 * <p>The fuse burns <i>outward</i> around the rim rather than closing inward towards the middle, which
 * is the other obvious way to draw a countdown and the wrong one. Particles outlive the tick they are
 * spawned on by a long way, so a ring walked inward leaves every position it has been in still lying
 * on the ground behind it, and a second in the whole circle is full of them and says nothing. An arc
 * only ever grows, so what it leaves behind <i>is</i> the reading.
 *
 * <p>The rim follows the ground rather than sitting at one height. Each of its points is dropped onto
 * its own column when the mark is laid, so on a slope the circle bends over the slope instead of
 * hanging in the air on the low side and burying itself on the high one - and because the clock is
 * drawn by walking from the middle out to those same points, it follows the ground for free.
 *
 * <p>It is not an entity. Nothing about it needs to be saved, ticked by the world, or seen by anybody
 * who is not already in the fight, so it is a handful of numbers the witness keeps in a list and ticks
 * itself, and it goes away with the witness on every path out - including the ones that are not a
 * death. See {@link WitnessEntity#clearStrikes}.
 */
final class SkyStrike {
	/** How long from being marked to landing. A second and a half: long enough to read, short enough to run. */
	static final int FUSE = 30;

	/** The ordinary one, laid on somebody's feet. */
	static final double RADIUS = 3.0;
	static final float DAMAGE = 8.5F;

	/** Enough to move a player and not enough to be the shove. That one is at the end. */
	static final double KNOCKBACK = 0.7;

	/**
	 * The last one, laid on the witness's own feet as the arm comes down.
	 *
	 * <p>Wider and softer than the rest, and the only one anybody is told where to expect: it is centred
	 * on the one thing in the fog the player can definitely see. It hurts less than the others because
	 * what it is for is not the damage - it is the four seconds ending by throwing everybody back out
	 * into a room they can no longer see across.
	 */
	static final double FINAL_RADIUS = 6.5;
	static final float FINAL_DAMAGE = 6.0F;

	/**
	 * Points around the rim, and how often the whole ring is laid down again.
	 *
	 * <p>Ten ticks is a top-up rather than a redraw. Dust lives somewhere between one and five seconds,
	 * so the circle is complete from the tick it is drawn and only needs replacing before the shortest
	 * lived of it starts dropping out - which over a thirty tick fuse is twice. Drawing forty points
	 * every tick would look no different and cost fifteen times the packets, with up to four of these
	 * burning at once in the last quarter.
	 */
	private static final int RIM_POINTS = 40;
	private static final int RIM_EVERY = 10;

	/** How far up it starts. Well above anything the fog will show, so it is heard before it is seen. */
	private static final double SKY_HEIGHT = 16.0;

	/** How far above and below the marked ground it reaches. Jumping is not a dodge; a hilltop is. */
	private static final double REACH_UP = 6.0;
	private static final double REACH_DOWN = 1.5;

	/** How far down a column is read looking for something to draw on. */
	private static final int GROUND_SEARCH = 10;

	/** The dimension's own colour. Everything here is the white the fog is, one shade colder. */
	private static final Vector3f PALE = new Vector3f(0.93F, 0.95F, 1.0F);
	private static final DustParticleEffect RIM_DUST = new DustParticleEffect(PALE, 1.1F);
	private static final DustParticleEffect FINE_DUST = new DustParticleEffect(PALE, 0.8F);

	private final ServerWorld world;
	private final Vec3d centre;

	/** The rim, dropped onto the ground once when the mark is laid and reused every tick after. */
	private final Vec3d[] rim;

	/** What it is standing on, for the debris. Sampled at the middle and good enough for all of it. */
	private final BlockState ground;

	private final double radius;
	private final float damage;
	private final double knockback;

	/** Whether this is the one that ends the punctuation. Louder, wider, and it thunders. */
	private final boolean last;

	private int ticksLeft = FUSE;

	/** How far round the rim the fuse has burnt, in rim points. Only ever goes up. */
	private int burnt;

	private SkyStrike(ServerWorld world, Vec3d centre, Vec3d[] rim, BlockState ground,
					  double radius, float damage, double knockback, boolean last) {
		this.world = world;
		this.centre = centre;
		this.rim = rim;
		this.ground = ground;
		this.radius = radius;
		this.damage = damage;
		this.knockback = knockback;
		this.last = last;
	}

	/**
	 * Lays one down, works out its rim, and starts the fuse.
	 *
	 * <p>The point handed in is a guess at where somebody is going to be, so its height is a guess too -
	 * everything vertical about the mark is settled here against the actual ground rather than trusted
	 * from the caller.
	 */
	static SkyStrike mark(ServerWorld world, Vec3d at, double radius, float damage, double knockback, boolean last) {
		Vec3d centre = new Vec3d(at.x, surfaceAt(world, at.x, at.z, at.y), at.z);

		Vec3d[] rim = new Vec3d[RIM_POINTS];
		for (int i = 0; i < RIM_POINTS; i++) {
			double bearing = Math.PI * 2.0 * i / RIM_POINTS;
			double x = centre.x + Math.cos(bearing) * radius;
			double z = centre.z + Math.sin(bearing) * radius;

			rim[i] = new Vec3d(x, surfaceAt(world, x, z, centre.y), z);
		}

		BlockState ground = world.getBlockState(BlockPos.ofFloored(centre.x, centre.y - 0.5, centre.z));
		world.playSound(null, centre.x, centre.y, centre.z, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE,
				SoundCategory.HOSTILE, 1.1F, 1.3F);

		return new SkyStrike(world, centre, rim, ground, radius, damage, knockback, last);
	}

	/**
	 * One tick of warning, or the landing.
	 *
	 * @return true when it is finished with and the witness should drop it
	 */
	boolean tick(WitnessEntity witness) {
		if (--this.ticksLeft <= 0) {
			hurt(witness);
			burst();
			return true;
		}

		draw();
		return false;
	}

	// --- the warning ----------------------------------------------------------

	private void draw() {
		int elapsed = FUSE - this.ticksLeft;

		if (elapsed % RIM_EVERY == 0) {
			for (Vec3d point : this.rim) {
				this.world.spawnParticles(RIM_DUST, point.x, point.y + 0.08, point.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}

		// The fuse. Only the points it has reached since last tick are drawn - one or two of them - and
		// the ones already burning are simply left where they are, which is both the cheapest way to do
		// it and the only way the arc is still readable a second later.
		int reached = MathHelper.floor((double) RIM_POINTS * elapsed / FUSE);

		while (this.burnt < reached) {
			Vec3d point = this.rim[this.burnt % RIM_POINTS];
			this.burnt++;

			this.world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
					point.x, point.y + 0.14, point.z, 1, 0.0, 0.0, 0.0, 0.0);
		}

		// The head of it, re-lit every tick so there is always one bright point on the ring and it is
		// always the one that says how far round this has got.
		Vec3d head = this.rim[Math.min(this.burnt, RIM_POINTS - 1)];
		this.world.spawnParticles(ParticleTypes.END_ROD, head.x, head.y + 0.16, head.z, 1, 0.0, 0.0, 0.0, 0.0);

		// The middle. Cheap, and the only one of the three cues that survives being stood on: a player
		// inside a three-block circle in four blocks of fog has the far rim behind them and out of sight.
		this.world.spawnParticles(FINE_DUST, this.centre.x,
				this.centre.y + 0.2 + this.world.random.nextDouble() * 2.6, this.centre.z,
				1, 0.14, 0.0, 0.14, 0.0);

		// And the thing itself, coming down out of a sky with nothing in it. Sixteen blocks up when the
		// ring is drawn and at head height as it closes, so a player who looks up sees it arrive - which
		// is the first time in the whole mod that looking where it is pointing has been worth anything.
		double height = SKY_HEIGHT * ((double) this.ticksLeft / FUSE);

		// Three of them half a block apart rather than one, so what is falling is a streak with a
		// direction and not a dot that happens to be lower than it was.
		for (int i = 0; i < 3; i++) {
			this.world.spawnParticles(ParticleTypes.END_ROD,
					this.centre.x, this.centre.y + height + i * 0.5, this.centre.z, 0, 0.0, -1.0, 0.0, 0.45);
		}

		this.world.spawnParticles(ParticleTypes.WHITE_ASH,
				this.centre.x, this.centre.y + height, this.centre.z, 3, 0.35, 0.35, 0.35, 0.01);
	}

	// --- the landing ----------------------------------------------------------

	private void hurt(WitnessEntity witness) {
		Box reach = new Box(
				this.centre.x - this.radius, this.centre.y - REACH_DOWN, this.centre.z - this.radius,
				this.centre.x + this.radius, this.centre.y + REACH_UP, this.centre.z + this.radius);

		DamageSource source = witness.getDamageSources().mobAttack(witness);

		for (PlayerEntity player : this.world.getEntitiesByClass(PlayerEntity.class, reach,
				player -> !player.isSpectator() && player.isAlive())) {
			double dx = player.getX() - this.centre.x;
			double dz = player.getZ() - this.centre.z;

			// The query box is square and the mark is not. Anything caught in the corners was standing
			// outside the circle it was shown, and being hit by a thing you were demonstrably clear of is
			// the one failure that turns a dodge back into a tax.
			if (dx * dx + dz * dz > this.radius * this.radius) {
				continue;
			}

			player.damage(source, this.damage);

			// Outwards, through vanilla's own knockback for the reason shove() gives at length: the
			// arguments are the direction of the push and it applies the opposite, so what goes in is the
			// middle of the mark seen from the player. Somebody standing exactly on the centre gets the
			// lift and no direction, which is fine - there is nowhere in particular to throw them.
			player.takeKnockback(this.knockback, -dx, -dz);
			player.velocityModified = true;
		}
	}

	private void burst() {
		double x = this.centre.x;
		double y = this.centre.y;
		double z = this.centre.z;

		this.world.spawnParticles(ParticleTypes.SONIC_BOOM, x, y + 1.2, z, 1, 0.0, 0.0, 0.0, 0.0);
		this.world.spawnParticles(ParticleTypes.EXPLOSION, x, y + 0.8, z,
				this.last ? 6 : 2, this.radius * 0.35, 0.4, this.radius * 0.35, 0.0);
		this.world.spawnParticles(FINE_DUST, x, y + 1.0, z, 60, 0.5, 1.2, 0.5, 0.35);

		// Air only happens when the mark could not find any ground to sit on - a ledge, or somebody
		// caught over a drop. There is nothing to kick up, so nothing is.
		if (!this.ground.isAir()) {
			this.world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, this.ground),
					x, y + 0.2, z, 80, this.radius * 0.4, 0.15, this.radius * 0.4, 0.45);
		}

		// The rim goes out along itself rather than up, so what is left hanging in the air for a second
		// afterwards is the shape that was drawn on the ground - and a player who got out gets to see
		// exactly how much of them was outside it.
		for (Vec3d point : this.rim) {
			double dx = point.x - x;
			double dz = point.z - z;
			double length = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));

			this.world.spawnParticles(ParticleTypes.POOF, point.x, point.y + 0.2, point.z,
					0, dx / length, 0.25, dz / length, 0.4);
		}

		// Two layers, always: the crack and the weight under it. One sound on its own reads as an effect
		// going off, and two reads as something landing.
		this.world.playSound(null, x, y, z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE,
				this.last ? 3.0F : 1.6F, this.last ? 0.55F : 0.9F);
		this.world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE,
				this.last ? 1.6F : 0.9F, this.last ? 0.4F : 0.55F);

		if (this.last) {
			// Thunder, once, for the big one. The twilight has no weather and no sky to have weather in,
			// so this is the only noise in the dimension that comes from above, and it is only ever heard
			// three times.
			this.world.playSound(null, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
					SoundCategory.HOSTILE, 2.4F, 0.6F);
		}
	}

	// --- ground ---------------------------------------------------------------

	/**
	 * The top of the ground in one column, as a height to draw on.
	 *
	 * <p>Read downwards a block at a time rather than out of the heightmap. The twilight's heightmaps
	 * are not to be trusted - they have been caught answering the world bottom for columns with dirt
	 * plainly in them - and a rim point that believes one draws its share of the circle somewhere nobody
	 * will ever see it.
	 *
	 * @return the surface, or the height that was asked about if the column has no floor within reach
	 */
	private static double surfaceAt(ServerWorld world, double x, double z, double from) {
		BlockPos.Mutable pos = new BlockPos.Mutable(MathHelper.floor(x), 0, MathHelper.floor(z));
		int top = MathHelper.floor(from) + 3;

		for (int y = top; y > top - GROUND_SEARCH; y--) {
			pos.setY(y);
			BlockState state = world.getBlockState(pos);

			if (!state.isAir() && !state.isReplaceable()) {
				return y + 1.0;
			}
		}

		return from;
	}
}
