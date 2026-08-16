package net.ragnar.ragnarstwilightdimension.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;
import net.ragnar.ragnarstwilightdimension.entity.PaleFigureEntity;
import net.ragnar.ragnarstwilightdimension.network.StarePayload;
import net.ragnar.ragnarstwilightdimension.sound.ModSounds;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Something looks at you.
 *
 * <p>Three seconds, and it happens to one person. Their head goes up on its own, the world goes the
 * same red as a blood moon, the ground shakes, and every control they have stops answering. Hanging
 * in the air at about fifty degrees above the horizon, at the exact bearing they were already facing,
 * is a blank white figure the shape of a player, and it is facing back. Something enormous is heard
 * coming off it. Then all of it stops at once, in a single frame, and the sky is empty.
 *
 * <p><b>It is nearer every time.</b> The first is forty blocks out, then thirty-two, twenty-four,
 * sixteen, and the fifth is eight - and the count is kept per player, forever, by {@link StareState}.
 * The distances are chosen against the fog, which stops at eleven blocks: the first four are all
 * beyond it, so what is seen is the same thing the blood moon does to the landscape - a shape in flat
 * fog colour against a background that is not fog colour, an outline with nothing on it. The fifth is
 * inside the fog. That is the only one where it is lit, and the only one where there is anything to
 * see: a figure with no face, close enough to touch, that has been getting closer for hours.
 *
 * <p>Everything the player experiences is sent to that player alone - see {@link StarePayload}. The
 * figure itself is a real entity that anybody could see, but at those distances the fog leaves
 * nothing for anyone who is not the one being shown it. To the rest of the server, somebody stopped
 * walking for three seconds while something screamed.
 *
 * <p>The odds are the wanderer's, deliberately: rare enough that most trips through the dimension
 * have nothing in them, common enough that a player who lives there will meet all five.
 */
public final class Stare {
	// --- tuning ---------------------------------------------------------------
	/** How often the roll happens, per player. 200 ticks = 10 seconds. */
	private static final int CHECK_INTERVAL_TICKS = 200;

	/** Chance per roll. The wanderer's number - roughly one an hour of play, per player. */
	private static final float CHANCE = 0.0025F;

	/** Hard floor between two of these for the same player, so the count cannot be run up quickly. */
	private static final int COOLDOWN_TICKS = 20 * 60 * 20;

	/** Three seconds. Long enough to understand what is happening and not long enough to study it. */
	public static final int DURATION_TICKS = 60;

	/**
	 * How long the music is dead before anything else happens, in ticks. Half a second.
	 *
	 * <p>The music cutting is the first thing, and it is on its own. Everything else in the event
	 * arrives together in a single frame, which is loud and total and gives the player nothing to do
	 * but receive it; this is the one beat where they are still in control of themselves and
	 * something is already wrong. Half a second is long enough to notice the silence and much too
	 * short to act on it.
	 *
	 * <p>It is also why the position is worked out twice - once here to check there is anywhere to put
	 * it at all, and again when it fires, off the bearing the player is facing by then. Half a second
	 * of walking is nearly two blocks, and the figure has to be where they are looking when their head
	 * comes up, not where they were looking when the music stopped.
	 */
	private static final int HUSH_LEAD_TICKS = 10;

	/**
	 * How loud it is for everybody who is not the one being looked at.
	 *
	 * <p>Above 1.0 on purpose. For a fixed-range sound event the volume argument does not change how
	 * far the server sends it - {@link ModSounds#STARE_RANGE} decides that - but the client fades over
	 * {@code volume x attenuation_distance}, so this stretches the falloff to 225 blocks and leaves
	 * the sound still near full strength across the seventy-five it is actually heard over. Quiet at
	 * the edge of earshot is wrong for this: anyone close enough to hear it at all should be in no
	 * doubt about it.
	 */
	private static final float VOLUME_AROUND = 3.0F;

	/**
	 * How far up it hangs, in degrees above the horizon.
	 *
	 * <p>This is what makes the head go up rather than round: the figure is put at the bearing the
	 * player is already facing, so the only part of the view that has to move is the pitch. Fifty-two
	 * is steep enough that it can never be mistaken for something standing on the ground and shallow
	 * enough to stay a neck movement rather than a somersault.
	 */
	private static final double ELEVATION_DEGREES = 52.0;

	/**
	 * How far away it is on each successive occasion, in blocks. The last one is the one it stays at:
	 * a player who has been through all five gets the eight-block version for the rest of the world's
	 * life. There is nowhere nearer for it to stand.
	 */
	private static final double[] DISTANCES = {40.0, 32.0, 24.0, 16.0, 8.0};
	// --------------------------------------------------------------------------

	/** One in progress. */
	private record Watch(PaleFigureEntity figure, int endsAt) {
	}

	/** One whose music has been cut and which has not arrived yet. */
	private record Pending(int forcedStage, int firesAt) {
	}

	private static final Map<UUID, Watch> WATCHING = new HashMap<>();
	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	/** Player to the tick before which they cannot be found again. */
	private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();

	private Stare() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(Stare::onWorldTick);
	}

	private static void onWorldTick(ServerWorld world) {
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			return;
		}

		int now = world.getServer().getTicks();
		tickPending(world, now);
		tickWatches(world, now);

		if (!TwilightSchedule.rolls(now, CHECK_INTERVAL_TICKS, TwilightSchedule.STARE)) {
			return;
		}

		for (ServerPlayerEntity player : world.getPlayers()) {
			UUID id = player.getUuid();

			if (WATCHING.containsKey(id) || PENDING.containsKey(id) || now < COOLDOWNS.getOrDefault(id, 0)) {
				continue;
			}
			if (world.getRandom().nextFloat() < CHANCE) {
				start(world, player, -1);
			}
		}
	}

	/**
	 * Cuts the player's music, and books the rest of it for half a second later.
	 *
	 * @param forcedStage the occasion to run, one-based, or -1 to use the player's own count and add
	 *                    one to it. Only {@code /stare} passes a stage, and forcing one deliberately
	 *                    does not touch the count - a stage you asked for is not a stage that happened
	 *                    to you.
	 * @return whether it is coming, which is false when the line of sight is blocked
	 */
	public static boolean start(ServerWorld world, ServerPlayerEntity player, int forcedStage) {
		UUID id = player.getUuid();
		end(world, id);

		// Checked before the music goes rather than after, so a roll that could never have produced
		// anything does not leave the player in an unexplained silence.
		if (!clear(world, player, stageDistance(forcedStage > 0 ? forcedStage : nextStage(world, id)))) {
			return false;
		}

		int now = world.getServer().getTicks();
		PENDING.put(id, new Pending(forcedStage, now + HUSH_LEAD_TICKS));

		// Booked here rather than when it fires, so the ten ticks in between cannot take a second roll.
		COOLDOWNS.put(id, now + COOLDOWN_TICKS);

		ServerPlayNetworking.send(player, StarePayload.hush());
		return true;
	}

	private static void tickPending(ServerWorld world, int now) {
		if (PENDING.isEmpty()) {
			return;
		}

		for (UUID id : new ArrayList<>(PENDING.keySet())) {
			Pending pending = PENDING.get(id);
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);

			if (player == null || player.isRemoved() || player.isDead() || player.getWorld() != world) {
				PENDING.remove(id);
				continue;
			}
			if (now < pending.firesAt()) {
				continue;
			}

			PENDING.remove(id);
			fire(world, player, pending.forcedStage());
		}
	}

	/**
	 * Puts it in the sky, all at once: the figure, the noise, and the packet that takes the player's
	 * body off them. Everything the player experiences beyond the silence starts on this tick.
	 */
	private static void fire(ServerWorld world, ServerPlayerEntity player, int forcedStage) {
		UUID id = player.getUuid();

		// Worked out again rather than carried over from start(): half a second ago is not where they
		// are looking now, and the whole point is that it is dead ahead when their head comes up.
		int stage = forcedStage > 0 ? forcedStage : nextStage(world, id);
		Vec3d aim = above(player, stageDistance(stage));

		if (!clear(world, player, stageDistance(stage))) {
			return;
		}

		PaleFigureEntity figure = ModEntities.PALE_FIGURE.create(world);
		if (figure == null) {
			return;
		}

		if (forcedStage <= 0) {
			StareState.get(world).increment(id);
		}

		// Placed by its eyes rather than its feet: the point the player's view is locked to is the
		// point it is looking back from, and being a block and a half out at eight blocks is the
		// difference between meeting its eyes and staring at its chest.
		figure.refreshPositionAndAngles(aim.x, aim.y - figure.getStandingEyeHeight(), aim.z, 0.0F, 0.0F);
		figure.watch(player);
		world.spawnEntity(figure);

		playNoise(world, player, figure.getEyePos());

		int now = world.getServer().getTicks();
		WATCHING.put(id, new Watch(figure, now + DURATION_TICKS));

		ServerPlayNetworking.send(player, new StarePayload(aim.x, aim.y, aim.z, DURATION_TICKS));
	}

	/**
	 * The noise, twice over, and never to the same person twice.
	 *
	 * <p>Everybody except the one being looked at gets it from the figure, out of the sky, falling off
	 * with distance the way a sound should. The one being looked at gets it <b>at their own ears</b>,
	 * at full gain, because the client clamps every sound source at 1.0 no matter how loud the world
	 * says it is - at forty blocks the positional version arrives at roughly half strength, and half
	 * strength is not what this is. It is not coming from over there as far as they are concerned. It
	 * is happening to them.
	 *
	 * <p>The two never overlap for one listener on purpose. Two copies of the same file starting a few
	 * milliseconds apart do not add up to a louder sound, they comb-filter into a thin metallic one.
	 */
	private static void playNoise(ServerWorld world, ServerPlayerEntity player, Vec3d from) {
		world.playSound(player, from.x, from.y, from.z, ModSounds.STARE.value(),
				SoundCategory.HOSTILE, VOLUME_AROUND, 1.0F);

		player.playSoundToPlayer(ModSounds.STARE.value(), SoundCategory.HOSTILE, 1.0F, 1.0F);
	}

	/** The distance this occasion is run at, with everything past the last one staying at the last. */
	private static double stageDistance(int stage) {
		return DISTANCES[MathHelper.clamp(stage - 1, 0, DISTANCES.length - 1)];
	}

	private static int nextStage(ServerWorld world, UUID id) {
		return StareState.get(world).count(id) + 1;
	}

	/**
	 * Whether there is anywhere to put it. Nothing is worth doing if it would be standing inside a
	 * hill: the roll is simply spent - the dimension is open enough that this is rare, and a version of
	 * the event where the player is held still for three seconds staring at a wall is worse than one
	 * that did not happen.
	 */
	private static boolean clear(ServerWorld world, ServerPlayerEntity player, double distance) {
		return world.raycast(new RaycastContext(player.getEyePos(), above(player, distance),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player))
				.getType() == HitResult.Type.MISS;
	}

	/**
	 * Where it hangs: {@link #ELEVATION_DEGREES} above the horizon, on the bearing the player is
	 * already facing, at the distance this occasion calls for.
	 *
	 * <p>Measured from the eyes rather than the feet, so the angle the head has to travel is the angle
	 * written above no matter what the player is standing on.
	 */
	private static Vec3d above(ServerPlayerEntity player, double distance) {
		double elevation = Math.toRadians(ELEVATION_DEGREES);
		double out = distance * Math.cos(elevation);
		double up = distance * Math.sin(elevation);

		// Yaw runs clockwise from south, which is why forward is (-sin, cos) rather than (cos, sin).
		double yaw = Math.toRadians(player.getYaw());

		return new Vec3d(
				player.getX() - Math.sin(yaw) * out,
				player.getEyeY() + up,
				player.getZ() + Math.cos(yaw) * out);
	}

	private static void tickWatches(ServerWorld world, int now) {
		if (WATCHING.isEmpty()) {
			return;
		}

		for (UUID id : new ArrayList<>(WATCHING.keySet())) {
			Watch watch = WATCHING.get(id);
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);

			// Ends on time, and also the moment there is nobody left for it to be happening to: gone from
			// the dimension, gone from the server, or dead. A player who dies mid-stare respawns with the
			// screen still red and the controls still locked if this is not checked.
			boolean over = now >= watch.endsAt()
					|| player == null
					|| player.isRemoved()
					|| player.isDead()
					|| player.getWorld() != world;

			if (over) {
				end(world, id);
			}
		}
	}

	/** Takes it away, wherever it got to. Safe to call for a player who is not in one. */
	public static void end(ServerWorld world, UUID id) {
		PENDING.remove(id);

		Watch watch = WATCHING.remove(id);
		if (watch == null) {
			return;
		}

		Entity figure = watch.figure();
		if (!figure.isRemoved()) {
			figure.discard();
		}

		ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
		if (player != null && !player.isRemoved()) {
			// Even when the timer ran out on its own. The client keeps its own copy of the clock so that
			// the three seconds are not at the mercy of the connection, but it is the server that decides
			// this is over, and a player left red because a packet was late is a bug worth one packet.
			ServerPlayNetworking.send(player, StarePayload.cancel());
		}
	}

	/** For {@code /stare reset}: puts a player back to never having been found. */
	public static void forget(ServerWorld world, ServerPlayerEntity player) {
		end(world, player.getUuid());
		COOLDOWNS.remove(player.getUuid());
		StareState.get(world).reset(player.getUuid());
	}

	/** How many times this player has been through it. */
	public static int count(ServerWorld world, ServerPlayerEntity player) {
		return StareState.get(world).count(player.getUuid());
	}

	/** The distances, for {@code /stare} to report and for nothing else. */
	public static List<Double> distances() {
		List<Double> out = new ArrayList<>(DISTANCES.length);
		for (double distance : DISTANCES) {
			out.add(distance);
		}
		return out;
	}
}
