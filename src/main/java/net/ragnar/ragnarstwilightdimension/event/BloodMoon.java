package net.ragnar.ragnarstwilightdimension.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.ragnar.ragnarstwilightdimension.entity.BloodSteveEntity;
import net.ragnar.ragnarstwilightdimension.entity.GiantSteveEntity;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;
import net.ragnar.ragnarstwilightdimension.network.BloodMoonPayload;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

/**
 * The blood moon.
 *
 * <p>Rarely, the twilight turns. It runs to a fixed shape, keyed to the length of the track:
 *
 * <ul>
 *   <li><b>0s</b> - the music starts, the fog goes red in a single frame, and a
 *       {@link GiantSteveEntity} is put down seventy-five blocks out, facing the player. Nothing
 *       else happens yet.
 *   <li><b>12.8s</b> - {@link BloodSteveEntity}s start coming, one every 0.8 seconds, from every
 *       direction, while noises you cannot place go off around you. Exactly 64 will arrive.
 *   <li><b>54s</b> - the noises stop. The music and the things running at you do not, so the last
 *       ten seconds are the one stretch where the only sound left is the track finishing.
 *   <li><b>64s</b> - the last note lands. The music stops, the fog snaps back in a single frame and
 *       every one of them is removed rather than killed - the giant included. Nothing is left to
 *       find.
 * </ul>
 *
 * <p>The abruptness of the ending is the whole point - there is no wind-down and no wreckage, so
 * afterwards there is nothing to show it happened at all.
 *
 * <p>The server owns the event: it keeps the clock, spawns everything and decides when it is over.
 * The fog and the music are client-side effects driven off a single synced flag - see
 * {@link BloodMoonPayload}.
 *
 * <p>State is deliberately not saved. An event in progress does not survive a restart, which is the
 * behaviour you want - coming back to a world should not drop you into the middle of one.
 */
public final class BloodMoon {
	// --- tuning ---------------------------------------------------------------
	/** How often the dimension rolls. 600 ticks = 30 seconds. */
	private static final int CHECK_INTERVAL_TICKS = 600;

	/** Chance per roll. 0.004 every 30s averages out to roughly one every two hours. */
	private static final float CHANCE = 0.004F;

	/** Hard floor between events. */
	private static final int COOLDOWN_TICKS = 20 * 60 * 30;

	/**
	 * How long one lasts: 64 seconds, which is the length of the track.
	 *
	 * <p>The two are the same number on purpose. The music is not background to the event, it is the
	 * clock for it - it starts as the event starts and the last note lands as everything is taken
	 * away. Change the track and this has to change with it.
	 */
	private static final int DURATION_TICKS = 1280;

	// --- the ones that come for you -------------------------------------------
	/**
	 * Nothing comes for the first 12.8 seconds. That window is the red arriving, the music
	 * establishing itself and the giant being noticed - long enough to work out what is happening and
	 * not yet be able to do anything about it.
	 *
	 * <p>16 x 16 ticks, which is what makes the count below come out exactly. See
	 * {@link #SPAWN_INTERVAL_TICKS}.
	 */
	private static final int SPAWN_DELAY_TICKS = 256;

	/**
	 * How many arrive over one event, per player.
	 *
	 * <p>64, and not by coincidence. It is the compression ratio of the dimension itself - a
	 * coordinate scale of 8 is 8x8 of ground folded into every block - and the event is that ratio
	 * counted out at one a second over 64 seconds. It is the one number here a player can actually
	 * arrive at on their own, by counting, which is the only reason it is worth pinning exactly.
	 *
	 * <p>This is the knob, not {@link #SPAWN_INTERVAL_TICKS}. The interval is derived from it below
	 * so the count cannot quietly drift when the duration or the lead-in is retuned.
	 *
	 * <p>There is deliberately no ceiling on how many can be alive at once. Killing them does not
	 * reduce the rate and never buys any ground - whatever you fail to put down is still there when
	 * the next one arrives. The count is fixed, so what a player can change is how many are standing
	 * at the end, never how many came.
	 */
	private static final int SPAWNS_PER_EVENT = 64;

	/**
	 * How often one arrives, once they start coming: the spawning window divided by the count above.
	 *
	 * <p>Works out to 16 ticks, so the last of the 64 lands at 63.2s - the pressure runs right up to
	 * the final note instead of tailing off before it. For the count to come out exactly, the window
	 * has to divide evenly, which is what the deliberate 256 and 1280 above are for.
	 */
	private static final int SPAWN_INTERVAL_TICKS = (DURATION_TICKS - SPAWN_DELAY_TICKS) / SPAWNS_PER_EVENT;

	/** Spawn ring. Outside the fog, so they are heard and then seen, never just present. */
	private static final double MIN_SPAWN_DISTANCE = 18.0;
	private static final double MAX_SPAWN_DISTANCE = 30.0;

	// --- noises you cannot place ----------------------------------------------
	/**
	 * Placed at a fresh random bearing every time, close enough to be clearly nearby but never from
	 * anything you can find. The direction carries no information, which is the point - it is not a
	 * warning, it just means something is out there.
	 *
	 * <p>These are vanilla sounds so the event works before any custom audio exists. Swap them for
	 * your own registered events whenever you like.
	 */
	private static final SoundEvent[] CUES = {
			SoundEvents.AMBIENT_CAVE.value(),
			SoundEvents.ENTITY_ENDERMAN_STARE,
			SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE,
			SoundEvents.ENTITY_WARDEN_HEARTBEAT
	};

	private static final int CUE_MIN_GAP_TICKS = 30;
	private static final int CUE_MAX_GAP_TICKS = 70;
	private static final double CUE_MIN_DISTANCE = 5.0;
	private static final double CUE_MAX_DISTANCE = 14.0;

	/**
	 * The noises stop this long before the end. Ten seconds, mirroring the ten at the start where
	 * nothing was chasing you yet.
	 *
	 * <p>Nothing else winds down - they are still coming and the track is still going. Taking only
	 * the ambience away leaves the last stretch sounding thinner than the rest of the event, so the
	 * ending is audible before it arrives without anything actually announcing it.
	 *
	 * <p>Only the <i>starting</i> of new noises is what stops here. One begun on the last tick before
	 * the cutoff still plays itself out, which is why this is set well clear of any of their lengths.
	 */
	private static final int CUE_SILENCE_TICKS = 200;
	// --------------------------------------------------------------------------

	// --- the one that does not come for you -----------------------------------
	/**
	 * How far out the giant is put, in blocks. Nearly seven times the fog distance, so it is never
	 * anything more than an outline - and it is placed once, at the start, rather than kept at this
	 * range as the player moves. Walking towards it is supposed to work.
	 */
	private static final double GIANT_DISTANCE = 75.0;

	/** Bearings tried before giving up on placing one, for when the ground out there is unusable. */
	private static final int GIANT_PLACEMENT_ATTEMPTS = 8;
	// --------------------------------------------------------------------------

	private static boolean active;
	private static int startedAt;
	private static int endsAt;
	private static int nextAllowedTick;
	private static int nextCueTick;

	private BloodMoon() {
	}

	public static boolean isActive() {
		return active;
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(BloodMoon::onWorldTick);

		// Anyone joining part-way through needs to be told, or they get an ordinary-looking sky with
		// things sprinting out of it.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				ServerPlayNetworking.send(handler.player, new BloodMoonPayload(active)));
	}

	private static void onWorldTick(ServerWorld world) {
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			return;
		}

		int now = world.getServer().getTicks();

		if (active) {
			if (now >= endsAt) {
				stop(world);
			} else {
				tickEvent(world, now);
			}
			return;
		}

		if (now % CHECK_INTERVAL_TICKS != 0 || now < nextAllowedTick || world.getPlayers().isEmpty()) {
			return;
		}

		if (world.getRandom().nextFloat() < CHANCE) {
			start(world);
		}
	}

	public static void start(ServerWorld world) {
		if (active) {
			return;
		}

		active = true;
		startedAt = world.getServer().getTicks();
		endsAt = startedAt + DURATION_TICKS;
		nextCueTick = startedAt + CUE_MIN_GAP_TICKS;
		broadcast(world.getServer());

		// Placed on the same tick the fog turns, so it is already standing there when the player looks
		// up. Anything later and they would see it arrive, which makes it a spawn rather than a thing
		// that was always out there.
		for (ServerPlayerEntity player : world.getPlayers()) {
			spawnGiant(world, player, world.getRandom());
		}
	}

	public static void stop(ServerWorld world) {
		if (!active) {
			return;
		}

		active = false;
		nextAllowedTick = world.getServer().getTicks() + COOLDOWN_TICKS;
		broadcast(world.getServer());
		// The entities clear themselves out - each one checks isActive() on its next tick.
	}

	private static void broadcast(MinecraftServer server) {
		BloodMoonPayload payload = new BloodMoonPayload(active);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static void tickEvent(ServerWorld world, int now) {
		Random random = world.getRandom();

		// The last ten seconds belong to the track alone.
		if (now >= nextCueTick && now < endsAt - CUE_SILENCE_TICKS) {
			nextCueTick = now + CUE_MIN_GAP_TICKS
					+ random.nextInt(CUE_MAX_GAP_TICKS - CUE_MIN_GAP_TICKS + 1);
			for (ServerPlayerEntity player : world.getPlayers()) {
				playCue(world, player, random);
			}
		}

		// Nothing arrives during the opening stretch - that time belongs to the red and the music.
		//
		// Measured from the start of the event rather than off the absolute server tick. Against the
		// server clock the window would begin on whatever phase the event happened to start on, and the
		// run would come out at 63 or 64 depending on nothing at all. This way it is 64 every time.
		int elapsed = now - startedAt;
		if (elapsed < SPAWN_DELAY_TICKS || elapsed % SPAWN_INTERVAL_TICKS != 0) {
			return;
		}

		for (ServerPlayerEntity player : world.getPlayers()) {
			spawnOne(world, player, random);
		}
	}

	/** One unplaceable noise, somewhere around the player. */
	private static void playCue(ServerWorld world, ServerPlayerEntity player, Random random) {
		float bearing = random.nextFloat() * MathHelper.TAU;
		double distance = MathHelper.lerp(random.nextDouble(), CUE_MIN_DISTANCE, CUE_MAX_DISTANCE);

		double x = player.getX() + Math.cos(bearing) * distance;
		double z = player.getZ() + Math.sin(bearing) * distance;

		SoundEvent cue = CUES[random.nextInt(CUES.length)];

		world.playSound(null, x, player.getY(), z, cue, SoundCategory.HOSTILE,
				0.9F, 0.6F + random.nextFloat() * 0.3F);
	}

	/**
	 * Stands one giant on the ground at {@link #GIANT_DISTANCE} on a random bearing from the player.
	 *
	 * <p>The bearing is rolled fresh, so which way you have to be facing to see it is different every
	 * event and there is no direction worth checking first. It gets a handful of attempts in case the
	 * first spot it picks has nothing solid under it - a lake, or a hole - and if none of them work it
	 * is simply skipped rather than being dropped somewhere that looks wrong.
	 *
	 * <p>The entity itself does the rest: it turns to face whoever is nearest for as long as the event
	 * lasts, and removes itself when it ends. See {@link GiantSteveEntity}.
	 */
	private static void spawnGiant(ServerWorld world, ServerPlayerEntity player, Random random) {
		for (int attempt = 0; attempt < GIANT_PLACEMENT_ATTEMPTS; attempt++) {
			float bearing = random.nextFloat() * MathHelper.TAU;

			int x = MathHelper.floor(player.getX() + Math.cos(bearing) * GIANT_DISTANCE);
			int z = MathHelper.floor(player.getZ() + Math.sin(bearing) * GIANT_DISTANCE);
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

			// Only the block it would be standing on is checked. It noclips, so nothing above matters -
			// twenty blocks of it are going to be inside something on any terrain that is not flat.
			BlockPos ground = new BlockPos(x, y - 1, z);
			if (world.getBlockState(ground).getCollisionShape(world, ground).isEmpty()) {
				continue;
			}

			GiantSteveEntity giant = ModEntities.GIANT_STEVE.create(world);
			if (giant == null) {
				return;
			}

			// Aimed at the player on the spawn tick as well as every tick after, so it is facing the
			// right way in the same instant it appears rather than turning to find them.
			float yaw = (float) (MathHelper.atan2(player.getZ() - (z + 0.5), player.getX() - (x + 0.5))
					* MathHelper.DEGREES_PER_RADIAN) - 90.0F;

			giant.refreshPositionAndAngles(x + 0.5, y, z + 0.5, yaw, 0.0F);
			giant.setHeadYaw(yaw);
			giant.setBodyYaw(yaw);
			// Otherwise the vanilla mob cap removes it the moment the player wanders off.
			giant.setPersistent();
			world.spawnEntity(giant);
			return;
		}
	}

	private static void spawnOne(ServerWorld world, ServerPlayerEntity player, Random random) {
		float bearing = random.nextFloat() * MathHelper.TAU;
		double distance = MathHelper.lerp(random.nextDouble(), MIN_SPAWN_DISTANCE, MAX_SPAWN_DISTANCE);

		int x = MathHelper.floor(player.getX() + Math.cos(bearing) * distance);
		int z = MathHelper.floor(player.getZ() + Math.sin(bearing) * distance);
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

		BlockPos feet = new BlockPos(x, y, z);
		boolean roomToStand = world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
				&& world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()
				&& !world.getBlockState(feet.down()).getCollisionShape(world, feet.down()).isEmpty();

		if (!roomToStand) {
			return;
		}

		BloodSteveEntity steve = ModEntities.BLOOD_STEVE.create(world);
		if (steve == null) {
			return;
		}

		steve.refreshPositionAndAngles(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
		// Otherwise the vanilla mob cap removes them the moment the player turns their back.
		steve.setPersistent();
		steve.setTarget(player);
		world.spawnEntity(steve);
	}
}
