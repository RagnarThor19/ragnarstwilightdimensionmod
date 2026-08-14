package net.ragnar.ragnarstwilightdimension.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * Puts a silhouette at the edge of a player's fog now and then.
 *
 * <p>This is done by hand rather than through the biome's spawner list on purpose: natural spawning
 * would happily drop one sixty blocks away where the fog hides it completely, or right on top of
 * you. Here the distance is always chosen so it materialises just at the limit of what you can see.
 */
public final class SilhouetteSpawner {
	/** How often the roll happens, per player. 200 ticks = 10 seconds. */
	private static final int CHECK_INTERVAL_TICKS = 200;

	/** Chance per roll. 0.04 at a 10s interval averages out to roughly one every 4 minutes. */
	private static final float SPAWN_CHANCE = 0.04F;

	/** Spawn ring, in blocks. Sits beyond the fog limit so it fades in rather than pops in. */
	private static final double MIN_SPAWN_DISTANCE = 12.0;
	private static final double MAX_SPAWN_DISTANCE = 16.0;

	/** Never more than one anywhere near a player. */
	private static final double CROWDING_RADIUS = 64.0;

	private SilhouetteSpawner() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(SilhouetteSpawner::onWorldTick);
	}

	private static void onWorldTick(ServerWorld world) {
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			return;
		}
		if (world.getTime() % CHECK_INTERVAL_TICKS != 0) {
			return;
		}

		for (ServerPlayerEntity player : world.getPlayers()) {
			if (world.getRandom().nextFloat() < SPAWN_CHANCE) {
				trySpawnNear(world, player);
			}
		}
	}

	private static void trySpawnNear(ServerWorld world, ServerPlayerEntity player) {
		// Grave watchers don't count: they are part of the scenery and never leave on their own, so
		// letting them occupy the slot would silence the wandering kind for anyone camped near a grave.
		Box crowding = player.getBoundingBox().expand(CROWDING_RADIUS);
		if (!world.getEntitiesByClass(SilhouetteEntity.class, crowding, e -> !e.isGraveWatcher()).isEmpty()) {
			return;
		}

		float angle = world.getRandom().nextFloat() * MathHelper.TAU;
		double distance = MathHelper.lerp(world.getRandom().nextDouble(), MIN_SPAWN_DISTANCE, MAX_SPAWN_DISTANCE);

		int x = MathHelper.floor(player.getX() + Math.cos(angle) * distance);
		int z = MathHelper.floor(player.getZ() + Math.sin(angle) * distance);

		spawnAt(world, x, z);
	}

	/** Places one on the surface of the given column, if there is room for it to stand. */
	public static SilhouetteEntity spawnAt(ServerWorld world, int x, int z) {
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos feet = new BlockPos(x, y, z);

		boolean roomToStand = world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
				&& world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()
				&& !world.getBlockState(feet.down()).getCollisionShape(world, feet.down()).isEmpty();

		if (!roomToStand) {
			return null;
		}

		SilhouetteEntity silhouette = ModEntities.SILHOUETTE.create(world);
		if (silhouette == null) {
			return null;
		}

		silhouette.refreshPositionAndAngles(x + 0.5, y, z + 0.5, world.getRandom().nextFloat() * 360.0F, 0.0F);
		world.spawnEntity(silhouette);
		return silhouette;
	}
}
