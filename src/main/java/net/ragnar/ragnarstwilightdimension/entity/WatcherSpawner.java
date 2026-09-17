package net.ragnar.ragnarstwilightdimension.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.event.TwilightSchedule;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;
import net.ragnar.ragnarstwilightdimension.world.dimension.TheBlank;

import java.util.List;

/**
 * Keeps somebody standing out in the dark, always, for as long as anybody is on the disc.
 *
 * <h2>Why this is a floor and not a roll</h2>
 *
 * <p>The eyes this replaces were a roll: one chance in twelve every ten seconds, and whatever it put
 * up was gone inside half a minute. That is the right shape for a thing that is meant to be doubted -
 * most of the time there was nothing out there, so an eye was something you saw rather than something
 * that was there. It is the wrong shape for something a hundred blocks tall. A watcher that comes and
 * goes is an event; one that is simply always up is the room you are standing in.
 *
 * <p>So the first one is not rolled for at all: {@link #onWorldTick} counts what is up and puts one
 * back whenever the count is short, which makes it self-healing against every way one can go away -
 * the arena unloading, a lifespan running out, somebody clearing entities. The <em>extra</em> ones are
 * still a roll, and still leave, because two or three of them turning up over the course of a fight
 * is the escalation the eyes used to be.
 *
 * <h2>Why the ring is round the disc and not round the player</h2>
 *
 * <p>The eyes were placed relative to whoever they were for, because at thirty blocks across they had
 * to be inside that player's own loaded chunks or the server would never mention them. This is a
 * hundred blocks tall and belongs to the place rather than to a person: it stands on a ring around the
 * middle of the disc, and everyone on the circle sees the same one in the same spot, which is most of
 * what makes it read as scenery.
 *
 * <p>{@link #RING_MAX} is what keeps that honest. The furthest anybody can get from a watcher is the
 * ring plus the radius of the disc, and that sum has to stay inside the smallest render distance worth
 * supporting - eight chunks, a hundred and twenty-eight blocks - or the thing quietly stops existing
 * for somebody standing on the far rim.
 */
public final class WatcherSpawner {
	/** How often the count is checked. Cheap, and the floor should be restored quickly. */
	private static final int CHECK_INTERVAL_TICKS = 40;

	/** How many are always up, once anybody is on the disc. */
	private static final int ALWAYS = 1;

	/** And the most there can ever be, counting the one that is always there. */
	private static final int MOST = 3;

	/** Chance, per roll of {@link TwilightSchedule#WATCHER}, of another one arriving. */
	private static final float EXTRA_CHANCE = 0.10F;

	/** How long an extra stands there before it goes: a minute and a half to three minutes. */
	private static final int EXTRA_LIFE_MIN = 1800;
	private static final int EXTRA_LIFE_MAX = 3600;

	/**
	 * The ring they stand on, measured from the middle of the disc.
	 *
	 * <p>Well past the drop, so one is never on the floor with you, and near enough that the far rim
	 * of the disc is still inside a hundred and twenty-eight blocks of it. See the note above.
	 */
	private static final double RING_MIN = 72.0;
	private static final double RING_MAX = 90.0;

	/**
	 * How far below the floor of the disc its feet are.
	 *
	 * <p>It is standing on nothing, so this number is pure framing: it decides how high the face ends
	 * up. Sunk this far, the eye comes out about sixty-five blocks above the floor at eighty out,
	 * which is a thirty-eight degree look - high enough to be over everything, low enough to be found
	 * without craning. Level with the floor it would be nearly fifty degrees and easy to walk under
	 * for an entire fight without ever seeing.
	 */
	private static final double SUNK = 26.0;

	/**
	 * How far apart two of them have to be on the ring, in radians.
	 *
	 * <p>About forty degrees. Any closer and the second reads as the first having two heads, which is
	 * a different and much worse effect than two of them standing in different parts of the dark.
	 */
	private static final double APART = 0.7;

	/** How many bearings are tried before one is placed anyway. */
	private static final int TRIES = 8;

	private WatcherSpawner() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(WatcherSpawner::onWorldTick);
	}

	private static void onWorldTick(ServerWorld world) {
		if (!ModDimensions.BLANK_WORLD.equals(world.getRegistryKey())) {
			return;
		}

		int now = world.getServer().getTicks();
		boolean check = now % CHECK_INTERVAL_TICKS == 0;
		boolean roll = TwilightSchedule.rolls(now, 200, TwilightSchedule.WATCHER);

		if (!check && !roll) {
			return;
		}

		if (!anybodyHere(world)) {
			return;
		}

		List<WatcherEntity> here = around(world);

		// The floor, first and unconditionally. Everything else about this class is decoration on top
		// of "there is one out there", which is the only part that is a promise.
		if (check && here.size() < ALWAYS) {
			place(world, here, 0);
			return;
		}

		if (roll && here.size() < MOST && world.getRandom().nextFloat() < EXTRA_CHANCE) {
			Random random = world.getRandom();
			place(world, here, MathHelper.nextInt(random, EXTRA_LIFE_MIN, EXTRA_LIFE_MAX));
		}
	}

	/** Whether there is anybody the dark is worth being occupied for. */
	private static boolean anybodyHere(ServerWorld world) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!player.isSpectator()) {
				return true;
			}
		}

		return false;
	}

	/** Everything of ours standing round the disc at the moment. */
	public static List<WatcherEntity> around(ServerWorld world) {
		double reach = RING_MAX + TheBlank.RADIUS + 16.0;

		Box arena = new Box(-reach, TheBlank.FLOOR_Y - SUNK - 16.0, -reach,
				reach, TheBlank.FLOOR_Y + WatcherEntity.HEIGHT, reach);

		return world.getEntitiesByClass(WatcherEntity.class, arena, watcher -> true);
	}

	/**
	 * Another one, on a bearing nothing is standing on, for {@code /blank watcher}.
	 *
	 * <p>Always places one, unlike everything above it - the floor and the roll are the world's
	 * business and this is somebody asking.
	 */
	public static WatcherEntity add(ServerWorld world, int lifetime) {
		return place(world, around(world), lifetime);
	}

	/**
	 * One of them, on a bearing that is not already taken.
	 *
	 * @param lifetime how long it stands there, or 0 for one that stays
	 */
	private static WatcherEntity place(ServerWorld world, List<WatcherEntity> already, int lifetime) {
		Random random = world.getRandom();
		double bearing = 0.0;

		for (int attempt = 0; attempt < TRIES; attempt++) {
			bearing = random.nextDouble() * Math.PI * 2.0;

			if (clear(bearing, already)) {
				break;
			}
		}

		double out = MathHelper.lerp(random.nextDouble(), RING_MIN, RING_MAX);

		return spawnAt(world,
				Math.cos(bearing) * out,
				TheBlank.FLOOR_Y - SUNK,
				Math.sin(bearing) * out,
				lifetime);
	}

	/** Whether this bearing is far enough round from everything already standing. */
	private static boolean clear(double bearing, List<WatcherEntity> already) {
		for (WatcherEntity watcher : already) {
			double taken = Math.atan2(watcher.getZ(), watcher.getX());

			// Wrapped the short way round the circle, so a bearing just past north and one just short
			// of it are two tenths apart rather than the six the raw subtraction gives.
			double gap = Math.abs(Math.atan2(Math.sin(bearing - taken), Math.cos(bearing - taken)));

			if (gap < APART) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Puts one at exactly this spot.
	 *
	 * <p>The lifespan is set before it is spawned rather than after, for the reason the eye's size is:
	 * it is tracked data, and a client told the wrong thing first has to be corrected, which for a
	 * fade is a hundred blocks of something appearing and then starting to appear.
	 */
	public static WatcherEntity spawnAt(ServerWorld world, double x, double y, double z, int lifetime) {
		WatcherEntity watcher = ModEntities.WATCHER.create(world);

		if (watcher == null) {
			return null;
		}

		watcher.refreshPositionAndAngles(x, y, z, 0.0F, 0.0F);

		if (lifetime > 0) {
			watcher.lifespan(lifetime);
		}

		world.spawnEntity(watcher);
		return watcher;
	}

	/**
	 * One to shoot with, for the fight.
	 *
	 * <p>Almost always one that is already standing there, which is the point of the attack - the
	 * thing that has been out in the dark doing nothing for ten minutes is the thing that fires. The
	 * fallback exists because the fight cannot be left holding nothing: a watcher can be missing for a
	 * couple of seconds after the arena reloads, and an attack that quietly did not happen is worse
	 * than one that put its own shooter up first.
	 *
	 * @return one of them, or null if even placing one failed
	 */
	public static WatcherEntity shooter(ServerWorld world, Vec3d at) {
		List<WatcherEntity> here = around(world);

		if (here.isEmpty()) {
			return place(world, here, 0);
		}

		// The nearest to where it is going to shoot, so the beam crosses as little of the arena as it
		// has to on the way in. Picking blind put half the shots on a line straight over everybody's
		// heads from the far side of the circle.
		WatcherEntity nearest = here.get(0);
		double best = Double.MAX_VALUE;

		for (WatcherEntity watcher : here) {
			double gap = watcher.getPos().squaredDistanceTo(at.x, watcher.getY(), at.z);

			if (gap < best) {
				best = gap;
				nearest = watcher;
			}
		}

		return nearest;
	}
}
