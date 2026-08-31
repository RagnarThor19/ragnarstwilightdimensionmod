package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;

import java.util.List;

/**
 * Everything that comes down on the disc during the survival phase, and the handful of sums they all
 * need.
 *
 * <p>The survival phase is sixty-four seconds in which the players cannot reach the thing and it does
 * not move: all of it, every second, is what is in {@link #SURVIVAL}. That list is meant to grow -
 * the difficulty of this fight is how many of these overlap, not how hard any one of them hits.
 *
 * <p>The phase does not know what any of them are. {@link TheEntity} walks the list once a tick, asks
 * each one whether it wants to go off, and lets it.
 */
public final class Attacks {
	/**
	 * The attacks the survival phase runs, in the order they are asked.
	 *
	 * <p>They are not coordinated and are not meant to be. Each keeps its own beat, so what any given
	 * ten seconds of the phase looks like is whatever those beats happen to line up into - which is
	 * the point of building it this way rather than as a script.
	 */
	public static final List<Attack> SURVIVAL = List.of(
			new ChargeAttack(),
			new SnowfallAttack(),
			new EyeAttack(),
			new BoltAttack(),
			new CircleAttack(),
			new PillarAttack(),
			new WallAttack(),
			new JumperAttack(),
			new BeamAttack(),
			new SteveAttack(),
			new HoleAttack(),
			new ArcherAttack());

	/** The height everything on the disc stands at. It is one flat sheet, so this is all of it. */
	public static final double GROUND_Y = TheBlank.FLOOR_Y + 1;

	/** How far from the middle anything is allowed to be placed. Two blocks in from the drop. */
	public static final double SAFE_RADIUS = TheBlank.RADIUS - 2.0;

	private Attacks() {
	}

	/**
	 * The ordinary schedule: nothing for a while, then a steady beat that tightens as the phase wears
	 * on.
	 *
	 * <p>The grace at the front is not politeness, it is legibility - the phase opens on a blackout
	 * and a switch, and something landing in that same half second reads as a bug rather than as an
	 * attack. After that the gap is interpolated from {@code slowest} down to {@code fastest}, so the
	 * last twenty seconds of a phase are roughly twice as busy as the first twenty.
	 */
	public static boolean ramped(int tick, float progress, int grace, int slowest, int fastest) {
		if (tick < grace) {
			return false;
		}

		int gap = Math.max(1, Math.round(MathHelper.lerp(progress, slowest, fastest)));
		return tick % gap == 0;
	}

	/** A point on the disc picked at random, well inside the edge. */
	public static Vec3d somewhereOnDisc(Random random) {
		// Square-rooted, because a bearing and a flat random radius crowds everything into the middle -
		// the area of a ring grows with its distance out, so the radius has to as well.
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = Math.sqrt(random.nextDouble()) * SAFE_RADIUS;

		return new Vec3d(Math.cos(angle) * distance, GROUND_Y, Math.sin(angle) * distance);
	}

	/** The same point, pulled back inside the circle if it was outside it. */
	public static Vec3d ontoDisc(Vec3d spot) {
		double distance = Math.sqrt(spot.x * spot.x + spot.z * spot.z);

		if (distance <= SAFE_RADIUS || distance < 1.0E-4) {
			return spot;
		}

		double share = SAFE_RADIUS / distance;
		return new Vec3d(spot.x * share, spot.y, spot.z * share);
	}

	/**
	 * Where somebody will be in {@code ticks} ticks if they keep doing what they are doing.
	 *
	 * <p>Under-led on purpose, by {@code share}. Full lead lands on anybody moving in a straight line
	 * every single time, which is not a dodge - it is a tax on moving. Two thirds of it punishes the
	 * straight line and leaves changing your mind free, which is the whole of this phase in one
	 * sentence.
	 */
	public static Vec3d leadOn(TheEntity boss, PlayerEntity player, int ticks, double share, double cap) {
		Vec3d drift = boss.driftOf(player);

		if (drift.lengthSquared() < 1.0E-6) {
			return player.getPos();
		}

		double ahead = Math.min(drift.length() * ticks * share, cap);
		return ontoDisc(player.getPos().add(drift.normalize().multiply(ahead)));
	}

	/**
	 * Hurts and throws everybody inside a circle on the ground.
	 *
	 * <p>Round rather than square, and every attack in the fight uses this one so that they all agree
	 * about it. A box query is what the game offers and it catches the corners, and being hit by
	 * something you were demonstrably clear of is the one failure that turns a dodge back into a tax.
	 *
	 * @param reachUp how far above the middle it still counts. Jumping is not a dodge.
	 */
	public static void burst(TheEntity boss, ServerWorld world, Vec3d centre,
							 double radius, float damage, double knockback, double reachUp) {
		DamageSource source = boss.getDamageSources().mobAttack(boss);

		for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class,
				new Box(centre.x - radius, centre.y - 2.0, centre.z - radius,
						centre.x + radius, centre.y + reachUp, centre.z + radius),
				candidate -> !candidate.isSpectator() && candidate.isAlive())) {
			double dx = player.getX() - centre.x;
			double dz = player.getZ() - centre.z;

			if (dx * dx + dz * dz > radius * radius) {
				continue;
			}

			player.damage(source, damage);

			// Vanilla's knockback takes the direction of the push and applies the opposite, so what goes
			// in is the middle of the burst as seen from the player.
			player.takeKnockback(knockback, -dx, -dz);
			player.velocityModified = true;
		}
	}
}
