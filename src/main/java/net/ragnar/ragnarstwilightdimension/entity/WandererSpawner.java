package net.ragnar.ragnarstwilightdimension.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.ragnar.ragnarstwilightdimension.event.TwilightSchedule;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * Sends a {@link WandererEntity} past a player, very occasionally.
 *
 * <p>The run is laid out as a straight line tangent to a circle drawn round the player: a bearing is
 * picked at random, the point {@link #PASS_DISTANCE} blocks away along it becomes the closest the
 * run ever gets, and the line runs perpendicular through that point. Half the run sits on either
 * side, so it comes out of the fog on one flank and goes back into it on the other without ever
 * heading towards anyone.
 */
public final class WandererSpawner {
	/** How often the roll happens, per player. 200 ticks = 10 seconds. */
	private static final int CHECK_INTERVAL_TICKS = 200;

	/**
	 * Chance per roll. 0.0025 at a 10s interval works out to roughly one an hour of play - rare
	 * enough that most trips through the dimension never see one at all.
	 */
	private static final float SPAWN_CHANCE = 0.0025F;

	/** Hard floor between runs, so a lucky streak cannot send two through back to back. */
	private static final int COOLDOWN_TICKS = 20 * 60 * 20;

	/**
	 * How close the run gets at its nearest point.
	 *
	 * <p>Worth knowing: {@code TwilightFog.FOG_END} is 11, so at 9 blocks it is still most of the way
	 * into the fog and will read as a heavy shape rather than a clear figure. That is the intended
	 * effect - drop this to 7 if you want it plainly readable instead.
	 */
	public static final double PASS_DISTANCE = 9.0;

	/** Never two anywhere near the same player. */
	private static final double CROWDING_RADIUS = 96.0;

	/** Server tick before which no run may start. */
	private static int nextAllowedTick = 0;

	private WandererSpawner() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(WandererSpawner::onWorldTick);
	}

	private static void onWorldTick(ServerWorld world) {
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			return;
		}

		int now = world.getServer().getTicks();
		if (!TwilightSchedule.rolls(now, CHECK_INTERVAL_TICKS, TwilightSchedule.WANDERER)
				|| now < nextAllowedTick) {
			return;
		}

		for (ServerPlayerEntity player : world.getPlayers()) {
			if (world.getRandom().nextFloat() < SPAWN_CHANCE && sendPast(world, player, PASS_DISTANCE) != null) {
				nextAllowedTick = now + COOLDOWN_TICKS;
				return;
			}
		}
	}

	/**
	 * Starts a run past the given player.
	 *
	 * @return the entity, or null if one is already about or the start of the run has no ground
	 */
	public static WandererEntity sendPast(ServerWorld world, ServerPlayerEntity player, double passDistance) {
		Box crowding = player.getBoundingBox().expand(CROWDING_RADIUS);
		if (!world.getEntitiesByClass(WandererEntity.class, crowding, e -> true).isEmpty()) {
			return null;
		}

		Random random = world.getRandom();
		float bearing = random.nextFloat() * MathHelper.TAU;

		// Closest point of the run to the player.
		double passX = player.getX() + Math.cos(bearing) * passDistance;
		double passZ = player.getZ() + Math.sin(bearing) * passDistance;

		// Perpendicular to the bearing, so the line only ever grazes that circle. The sign picks
		// which of the two directions along it the run goes.
		double side = random.nextBoolean() ? 1.0 : -1.0;
		Vec3d direction = new Vec3d(-Math.sin(bearing) * side, 0.0, Math.cos(bearing) * side);

		double half = WandererEntity.RUN_LENGTH / 2.0;
		double startX = passX - direction.x * half;
		double startZ = passZ - direction.z * half;

		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				MathHelper.floor(startX), MathHelper.floor(startZ));
		if (y <= world.getBottomY()) {
			return null;
		}

		WandererEntity wanderer = ModEntities.WANDERER.create(world);
		if (wanderer == null) {
			return null;
		}

		wanderer.beginRun(new Vec3d(startX, y, startZ), direction);
		world.spawnEntity(wanderer);
		return wanderer;
	}
}
