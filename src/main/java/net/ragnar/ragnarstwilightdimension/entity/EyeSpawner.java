package net.ragnar.ragnarstwilightdimension.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.ragnar.ragnarstwilightdimension.event.TwilightSchedule;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * Puts an eye out in the dark now and then, for whoever is standing on the disc.
 *
 * <h2>Why the distance is measured from the player and not from the middle</h2>
 *
 * <p>An eye has to be inside the chunks the player's client has actually loaded or the server never
 * tells them it exists - and that limit is a radius around the <em>player</em>, not around the disc.
 * Placing these on a fixed ring around the origin would put them a comfortable distance away for
 * somebody standing in the middle and past the edge of the world for somebody standing on the far
 * rim, who would see nothing at all and never know why.
 *
 * <p>So the ring travels with whoever it is for. {@link #MAX_DISTANCE} is what keeps it honest: it
 * has to stay inside the smallest render distance worth supporting, or the effect quietly stops
 * existing for anybody who plays at eight chunks.
 */
public final class EyeSpawner {
	/** How often the roll happens, per player. 200 ticks = 10 seconds. */
	private static final int CHECK_INTERVAL_TICKS = 200;

	/** Chance per roll. 0.08 at a 10s interval averages out to one about every two minutes. */
	private static final float SPAWN_CHANCE = 0.08F;

	/**
	 * The ring it appears on, in blocks from the player.
	 *
	 * <p>The disc is seventy across, so even the near edge of this is past anywhere anybody can stand:
	 * an eye is always out in the dark rather than merely across the room, from wherever it is seen.
	 *
	 * <p>The far edge is held at 110 because eight chunks - the lowest render distance anybody really
	 * plays at - is 128 blocks, and past that the client is simply never sent it. That is a hard
	 * ceiling on this number and not a matter of taste.
	 *
	 * <p>What is <em>not</em> a constraint any more is fog. It used to be: the disc inherited the
	 * Nether's thick fog, which is fully opaque at 96 blocks, so an eye out here was black on black.
	 * See {@code BlankFog}.
	 */
	private static final double MIN_DISTANCE = 80.0;
	private static final double MAX_DISTANCE = 110.0;

	/** How far above the player's own height it hangs. Enough to sit clear of the horizon. */
	private static final double MIN_RISE = 8.0;
	private static final double MAX_RISE = 28.0;

	/** More than this many already up near a player and the roll is skipped. */
	private static final int MOST_AT_ONCE = 2;

	/** What counts as "near a player" for that count. Comfortably past the spawn ring. */
	private static final double CROWDING_RADIUS = 160.0;

	private EyeSpawner() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(EyeSpawner::onWorldTick);
	}

	private static void onWorldTick(ServerWorld world) {
		if (!ModDimensions.BLANK_WORLD.equals(world.getRegistryKey())) {
			return;
		}

		int now = world.getServer().getTicks();
		if (!TwilightSchedule.rolls(now, CHECK_INTERVAL_TICKS, TwilightSchedule.EYE)) {
			return;
		}

		for (ServerPlayerEntity player : world.getPlayers()) {
			if (world.getRandom().nextFloat() < SPAWN_CHANCE) {
				trySpawnNear(world, player);
			}
		}
	}

	private static void trySpawnNear(ServerWorld world, ServerPlayerEntity player) {
		Box crowding = player.getBoundingBox().expand(CROWDING_RADIUS);
		if (world.getEntitiesByClass(EyeEntity.class, crowding, e -> true).size() >= MOST_AT_ONCE) {
			return;
		}

		spawnFor(world, player);
	}

	/**
	 * Puts one on the ring around this player, at a bearing picked at random.
	 *
	 * <p>The roll and the crowding check are the caller's business and are deliberately not in here,
	 * so that {@code /blank eye} gets an eye every time it is asked rather than one time in twelve.
	 */
	public static EyeEntity spawnFor(ServerWorld world, ServerPlayerEntity player) {
		float angle = world.getRandom().nextFloat() * MathHelper.TAU;
		double distance = MathHelper.lerp(world.getRandom().nextDouble(), MIN_DISTANCE, MAX_DISTANCE);
		double rise = MathHelper.lerp(world.getRandom().nextDouble(), MIN_RISE, MAX_RISE);

		return spawnAt(world,
				player.getX() + Math.cos(angle) * distance,
				player.getY() + rise,
				player.getZ() + Math.sin(angle) * distance);
	}

	/** Puts one at exactly this spot. */
	public static EyeEntity spawnAt(ServerWorld world, double x, double y, double z) {
		return spawnAt(world, x, y, z, eye -> {
		});
	}

	/**
	 * The same, with a chance to change it before anybody is told it exists.
	 *
	 * <p>The hook is not decoration. An eye's size and lifespan are tracked data, so setting them
	 * after {@code spawnEntity} means every client in range is first told about a thirty-block eye and
	 * then corrected a tick later - which at the eleven blocks the boss fight opens them at is a wall
	 * appearing across the arena for a frame. See {@code EyeAttack}.
	 */
	public static EyeEntity spawnAt(ServerWorld world, double x, double y, double z,
									java.util.function.Consumer<EyeEntity> before) {
		EyeEntity eye = ModEntities.EYE.create(world);
		if (eye == null) {
			return null;
		}

		eye.refreshPositionAndAngles(x, y, z, 0.0F, 0.0F);
		before.accept(eye);
		world.spawnEntity(eye);
		return eye;
	}
}
