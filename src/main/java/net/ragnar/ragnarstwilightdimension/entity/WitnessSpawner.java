package net.ragnar.ragnarstwilightdimension.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.ragnarstwilightdimension.event.BloodMoon;
import net.ragnar.ragnarstwilightdimension.event.TwilightSchedule;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Puts one in a field, out past the fog, and never touches it again.
 *
 * <p>Unlike everything else the dimension spawns, this one is not a moment - it is furniture. It does
 * not despawn, does not wander and does not time out, so the roll is not asking "should something
 * happen now" but "should there be one of these out here at all". The odds are set accordingly: about
 * one every two hours of play per player, with an hour's floor between them, which over a long
 * playthrough leaves a handful of them standing in the world at the kind of density where finding a
 * second one means something.
 *
 * <p>Placed between twenty-four and forty blocks away - well outside the eleven-block fog, so it is
 * never seen arriving. It is either not there, or it has always been there.
 */
public final class WitnessSpawner {
	/** How often the roll happens, per player. 200 ticks = 10 seconds. */
	private static final int CHECK_INTERVAL_TICKS = 200;

	/** Chance per roll. 0.0015 every 10s is roughly one in two hours, before the floor below. */
	private static final float SPAWN_CHANCE = 0.0015F;

	/** Nothing is placed for the same player twice inside an hour. */
	private static final int COOLDOWN_TICKS = 20 * 60 * 60;

	/** How far out it is put. Past the fog at the near end and inside the tracker at the far one. */
	private static final double MIN_DISTANCE = 24.0;
	private static final double MAX_DISTANCE = 40.0;

	/** Never a second one within this of the first. They are landmarks, and landmarks are spread out. */
	private static final double CROWDING_RADIUS = 192.0;

	/** How far down to look for something to stand it on. */
	private static final int GROUND_SEARCH = 12;

	/** It is 2.7 blocks tall and stands with an arm up. Four is what it needs above the ground. */
	private static final int HEADROOM = 4;

	private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();

	private WitnessSpawner() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(WitnessSpawner::onWorldTick);
	}

	private static void onWorldTick(ServerWorld world) {
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			return;
		}

		int now = world.getServer().getTicks();
		if (!TwilightSchedule.rolls(now, CHECK_INTERVAL_TICKS, TwilightSchedule.WITNESS)) {
			return;
		}

		// Not during a blood moon. The dimension is already saying something that night, and a thing
		// that is only legible when it is the only thing in the field would be lost in it.
		if (BloodMoon.isActive()) {
			return;
		}

		for (ServerPlayerEntity player : world.getPlayers()) {
			if (now < COOLDOWNS.getOrDefault(player.getUuid(), 0)) {
				continue;
			}
			if (world.getRandom().nextFloat() >= SPAWN_CHANCE) {
				continue;
			}
			if (!world.getEntitiesByClass(WitnessEntity.class,
					player.getBoundingBox().expand(CROWDING_RADIUS), witness -> true).isEmpty()) {
				continue;
			}

			if (place(world, player) != null) {
				COOLDOWNS.put(player.getUuid(), now + COOLDOWN_TICKS);
			}
		}
	}

	/**
	 * Stands one somewhere near a player, facing a bearing of its own.
	 *
	 * <p>The direction it points is picked here and kept forever. It has nothing to do with where the
	 * player is or where they came from - two of them found in the same world are looking at different
	 * parts of the sky, and neither of those parts has anything in it.
	 *
	 * @return the one that was placed, or null if there was nowhere flat and clear enough
	 */
	public static WitnessEntity place(ServerWorld world, ServerPlayerEntity player) {
		double bearing = world.getRandom().nextDouble() * Math.PI * 2.0;
		double distance = MathHelper.lerp(world.getRandom().nextDouble(), MIN_DISTANCE, MAX_DISTANCE);

		int x = MathHelper.floor(player.getX() + Math.cos(bearing) * distance);
		int z = MathHelper.floor(player.getZ() + Math.sin(bearing) * distance);

		BlockPos ground = findGround(world, x, MathHelper.floor(player.getY()) + 4, z);
		if (ground == null) {
			return null;
		}

		return place(world, Vec3d.ofBottomCenter(ground.up()), world.getRandom().nextFloat() * 360.0F - 180.0F);
	}

	/** Stands one at an exact spot. Used by the roll above and by {@code /witness}. */
	public static WitnessEntity place(ServerWorld world, Vec3d at, float watchYaw) {
		WitnessEntity witness = ModEntities.WITNESS.create(world);
		if (witness == null) {
			return null;
		}

		witness.refreshPositionAndAngles(at.x, at.y, at.z, watchYaw, 0.0F);
		witness.watchSky(watchYaw);

		if (!world.isSpaceEmpty(witness, witness.getBoundingBox())) {
			return null;
		}

		world.spawnEntity(witness);
		return witness;
	}

	/**
	 * Finds the top of the ground in a column, or null if there is nothing worth standing on within
	 * reach - a cave roof, a pool, or the middle of the air.
	 */
	static BlockPos findGround(ServerWorld world, int x, int fromY, int z) {
		BlockPos.Mutable pos = new BlockPos.Mutable(x, fromY, z);

		for (int i = 0; i < GROUND_SEARCH; i++) {
			BlockState state = world.getBlockState(pos);

			if (!state.getFluidState().isEmpty()) {
				return null;
			}
			if (!state.isAir() && !state.isReplaceable()) {
				return state.isSolidBlock(world, pos) && clearAbove(world, pos) ? pos.toImmutable() : null;
			}

			pos.move(0, -1, 0);
		}

		return null;
	}

	private static boolean clearAbove(ServerWorld world, BlockPos ground) {
		for (int i = 1; i <= HEADROOM; i++) {
			BlockState state = world.getBlockState(ground.up(i));

			if (!state.isAir() && !state.isReplaceable()) {
				return false;
			}
		}

		return true;
	}
}
