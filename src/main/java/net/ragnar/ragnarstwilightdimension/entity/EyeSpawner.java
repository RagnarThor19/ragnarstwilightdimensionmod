package net.ragnar.ragnarstwilightdimension.entity;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;

/**
 * Where an eye goes when something asks for one.
 *
 * <p>This used to roll for them as well - one chance in twelve every ten seconds, for whoever was
 * standing on the disc. That is gone: the eyes out in the dark are watchers now, there is always one
 * of them up, and the rolling lives in {@link WatcherSpawner}. What is left here is the placement, and
 * it is left because two things still want an eye of their own. {@code EyeAttack} puts a small one at
 * eleven blocks to shoot somebody with, and {@code /blank eye} puts a full-sized one on the ring, which
 * is the only way left to look at the picture on its own.
 *
 * <h2>Why the ring is measured from the player</h2>
 *
 * <p>An eye has to be inside the chunks the player's client has actually loaded or the server never
 * tells them it exists - and that limit is a radius around the <em>player</em>, not around the disc.
 * A fixed ring around the origin would be a comfortable distance for somebody standing in the middle
 * and past the edge of the world for somebody on the far rim, who would see nothing and never know
 * why. The watchers can be placed round the disc instead because they are a hundred blocks tall and
 * stand much closer in; thirty blocks of picture at eighty out cannot afford it.
 */
public final class EyeSpawner {
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

	private EyeSpawner() {
	}

	/**
	 * Puts one on the ring around this player, at a bearing picked at random.
	 *
	 * <p>Nothing is checked and nothing is rolled: everything that calls this has already decided it
	 * wants an eye, and gets one.
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
