package net.ragnar.ragnarstwilightdimension.sound;

import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.event.TwilightSchedule;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * Something enormous, a long way off.
 *
 * <p>Rarely - on the order of once every half hour - a call goes up somewhere out past the fog. It
 * is never placed close to anybody: the spawner picks a spot at least {@value #EXCLUSION_RADIUS}
 * blocks from every player in the dimension, so the sound always arrives worn down by distance and
 * never right on top of you. Whatever is making it does not get any closer, and never appears.
 *
 * <p>The range is not the usual 16 blocks. {@link ModSounds#LEVIATHAN_RANGE} makes the server
 * forward the call to everyone within 512 blocks, and the matching {@code attenuation_distance} in
 * {@code sounds.json} makes the client fade it out linearly over that same span.
 */
public final class TwilightLeviathan {
	// --- tuning ---------------------------------------------------------------
	/** How often the dimension rolls for a call. 200 ticks = 10 seconds. */
	private static final int CHECK_INTERVAL_TICKS = 200;

	/** Chance per roll. 0.006 every 10s averages out to roughly one call every 28 minutes. */
	private static final float CHANCE = 0.006F;

	/** Hard floor between calls, so a lucky streak cannot stack them up. */
	private static final int COOLDOWN_TICKS = 20 * 60 * 6;

	/** Nothing is ever placed closer than this to any player. */
	private static final double EXCLUSION_RADIUS = 100.0;

	/**
	 * Where the call is placed, measured from whichever player was picked as the anchor. The floor
	 * sits above {@link #EXCLUSION_RADIUS} so the anchor themselves is always clear; the check
	 * against everyone else is what {@link #clearsEveryone} is for.
	 */
	private static final double MIN_DISTANCE = 110.0;
	private static final double MAX_DISTANCE = 190.0;

	/** Rerolls before giving up, for when players are spread out enough to box the spawner in. */
	private static final int PLACEMENT_ATTEMPTS = 12;

	/**
	 * Left at 1.0 deliberately. The client clamps gain to 1.0, so a higher number here would not
	 * make the call any louder - it would only stretch the range, which the fixed-range sound event
	 * already handles. Loudness has to come from the .ogg itself.
	 */
	private static final float VOLUME = 1.0F;

	/** Pitched down, which drags the playback rate with it and makes the source sound huge. */
	private static final float MIN_PITCH = 0.72F;
	private static final float MAX_PITCH = 0.88F;
	// --------------------------------------------------------------------------

	/** Server tick before which no call may go out. */
	private static int nextAllowedTick = 0;

	private TwilightLeviathan() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(TwilightLeviathan::onWorldTick);
	}

	private static void onWorldTick(ServerWorld world) {
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			return;
		}

		int now = world.getServer().getTicks();
		if (!TwilightSchedule.rolls(now, CHECK_INTERVAL_TICKS, TwilightSchedule.LEVIATHAN)
				|| now < nextAllowedTick) {
			return;
		}

		// Only players actually in the twilight are considered - nobody else could hear it anyway.
		List<ServerPlayerEntity> players = world.getPlayers();
		if (players.isEmpty() || world.getRandom().nextFloat() >= CHANCE) {
			return;
		}

		Vec3d at = findCallPosition(world, players);
		if (at == null) {
			return;
		}

		// A null "except" player means nobody is skipped, so everyone in range hears it.
		world.playSound(null, at.x, at.y, at.z, ModSounds.AMBIENCE_LEVIATHAN.value(),
				SoundCategory.AMBIENT, VOLUME, pitch(world.getRandom()));
		nextAllowedTick = now + COOLDOWN_TICKS;
	}

	/** A spot far from the anchor and no closer than the exclusion radius to anyone, or null. */
	private static Vec3d findCallPosition(ServerWorld world, List<ServerPlayerEntity> players) {
		Random random = world.getRandom();

		for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
			ServerPlayerEntity anchor = players.get(random.nextInt(players.size()));
			float bearing = random.nextFloat() * MathHelper.TAU;
			double distance = MathHelper.lerp(random.nextDouble(), MIN_DISTANCE, MAX_DISTANCE);

			// Height is taken straight from the anchor rather than the terrain, so the distance the
			// player hears is the horizontal distance chosen above with nothing vertical added on
			// top of it. Attenuation is measured in 3D, so a call dropped at surface height would
			// arrive much quieter for anyone underground.
			Vec3d candidate = new Vec3d(
					anchor.getX() + Math.cos(bearing) * distance,
					anchor.getY(),
					anchor.getZ() + Math.sin(bearing) * distance);

			if (clearsEveryone(candidate, players)) {
				return candidate;
			}
		}

		return null;
	}

	private static boolean clearsEveryone(Vec3d at, List<ServerPlayerEntity> players) {
		double minimum = EXCLUSION_RADIUS * EXCLUSION_RADIUS;
		for (ServerPlayerEntity player : players) {
			if (player.squaredDistanceTo(at) < minimum) {
				return false;
			}
		}
		return true;
	}

	private static float pitch(Random random) {
		return MIN_PITCH + random.nextFloat() * (MAX_PITCH - MIN_PITCH);
	}
}
