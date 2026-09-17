package net.ragnar.ragnarstwilightdimension.entity.attack;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.BloodSteveEntity;
import net.ragnar.ragnarstwilightdimension.entity.PaleFigureEntity;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;
import net.ragnar.ragnarstwilightdimension.entity.WandererEntity;
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
			new ArcherAttack(),
			new ArrowfallAttack(),
			FrostAttack.hail(),
			FrostAttack.spikes(),
			new LanceAttack(),
			new WatcherAttack());

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
		burst(boss, world, centre, radius, damage, knockback, reachUp, 0.0, knockback, 0.0);
	}

	/**
	 * The same burst, with a middle that hits harder than its edge.
	 *
	 * <p>Only the throw changes and never the damage. The difference between clipping something and
	 * being directly underneath it ought to be felt in where you end up rather than in a number, because
	 * where you end up is the only part of a hit a player can read without opening the log - and a
	 * fight fought on a disc with an edge has somewhere for that reading to matter.
	 *
	 * <p>The lift is here because vanilla will not do it. {@code takeKnockback} caps its own vertical
	 * component at 0.4 for anybody standing on the ground, which is the right number for a sword and a
	 * useless one for something that fell thirty metres - so the horizontal half of the throw is left
	 * to vanilla and the vertical half is written onto the velocity afterwards.
	 *
	 * @param core          how close to the middle counts as a direct hit. Zero for a burst with no middle.
	 * @param coreKnockback how hard a direct hit is thrown outwards, in the usual units.
	 * @param lift          how hard a direct hit is thrown upwards. Everybody else gets half of it.
	 */
	public static void burst(TheEntity boss, ServerWorld world, Vec3d centre,
							 double radius, float damage, double knockback, double reachUp,
							 double core, double coreKnockback, double lift) {
		DamageSource source = boss.getDamageSources().mobAttack(boss);

		for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class,
				new Box(centre.x - radius, centre.y - 2.0, centre.z - radius,
						centre.x + radius, centre.y + reachUp, centre.z + radius),
				candidate -> !candidate.isSpectator() && candidate.isAlive())) {
			double dx = player.getX() - centre.x;
			double dz = player.getZ() - centre.z;
			double away = dx * dx + dz * dz;

			if (away > radius * radius) {
				continue;
			}

			boolean direct = away <= core * core;

			player.damage(source, damage);

			// Vanilla's knockback takes the direction of the push and applies the opposite, so what goes
			// in is the middle of the burst as seen from the player.
			player.takeKnockback(direct ? coreKnockback : knockback, -dx, -dz);

			if (lift > 0.0) {
				// Raised to rather than added to, so that two of these landing on somebody in the same
				// tick throw them once. Twice would be a number nobody chose.
				Vec3d going = player.getVelocity();
				player.setVelocity(going.x, Math.max(going.y, direct ? lift : lift * 0.5), going.z);
			}

			player.velocityModified = true;
		}
	}

	/**
	 * Clears the disc of everything the last fight was in the middle of.
	 *
	 * <p>Run once, by the boss, as a new one arrives - see {@code TheEntity#begin} - and it is the
	 * backstop for the one case none of the ordinary cleanup can reach.
	 *
	 * <p>Half of what these attacks throw is particles, which cost nothing and are gone by themselves.
	 * The other half is real entities: the archers and the figures that fire the beams, the wanderers
	 * that charge, the blocks the lances are drawn with. Every attack that spawns one takes it back -
	 * on the tick it is finished, at every phase seam, and again when the fight ends - so in ordinary
	 * play nothing here ever finds anything. What it is for is the endings that are not calls at all:
	 * the server stopping mid-attack, or the arena unloading a second after the last player died, both
	 * of which skip {@code Ongoing#cancel} entirely and write whatever was standing there into the
	 * chunk. Without this, a party that wipes walks back in to a fresh boss with the last fight's
	 * archers still standing round it.
	 *
	 * <p>Arrows are swept too, except the players' own. One somebody shot ten seconds ago on their way
	 * in is theirs; the fifteen that came out of an archer belong to a fight that is over.
	 */
	public static void sweep(ServerWorld world) {
		Solid.sweep(world);

		double reach = TheBlank.RADIUS + 8.0;
		Box arena = new Box(-reach, TheBlank.FLOOR_Y - 8.0, -reach, reach, TheBlank.FLOOR_Y + 64.0, reach);

		for (Entity thing : world.getEntitiesByClass(Entity.class, arena, Attacks::leftOver)) {
			thing.discard();
		}
	}

	/** Whether this is something an attack put there and did not get the chance to take back. */
	private static boolean leftOver(Entity thing) {
		if (thing instanceof PersistentProjectileEntity arrow) {
			return !(arrow.getOwner() instanceof PlayerEntity);
		}

		return thing instanceof PaleFigureEntity
				|| thing instanceof WandererEntity
				|| thing instanceof BloodSteveEntity;
	}
}
