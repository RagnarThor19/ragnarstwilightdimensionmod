package net.ragnar.ragnarstwilightdimension.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.ragnar.ragnarstwilightdimension.network.DeepPayload;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * What happens to somebody who goes under the world and stays there.
 *
 * <h2>The room</h2>
 *
 * <p>The twilight's terrain is solid from {@code y=0} upward and the surface rule turns everything
 * below that to air, so under the crust there is a sealed, unlit, sixty-three block room the size of
 * the dimension, with a bedrock floor and no way in. {@code subsidence} is the way in: it is the only
 * thing that opens a hole down to it. Nothing else in the mod acknowledges the place exists.
 *
 * <p>This does. It is the reason the shaft is worth finding and the reason it is worth not falling
 * down.
 *
 * <h2>The clock</h2>
 *
 * <p>Everything here runs off one beat, every ten seconds, for as long as a player is under
 * {@code y=0} in the twilight:
 *
 * <ul>
 *   <li>They hear a heartbeat, and the fog opens for one second. Not wider - see
 *       {@code DeepClient} for why the fog distance is never touched - just briefly transparent
 *       enough to see what is down there with them.
 *   <li>After three minutes, on one of those beats, a fifty-block figure is standing
 *       {@value #START_RANGE} blocks away. It was not shown arriving. The first anybody knows of it
 *       is that the fog opened and it was there.
 *   <li>Every beat after that, it is {@value #STEP} blocks closer. Again, never shown moving: the
 *       fog opens, it has changed place, the fog closes.
 *   <li>At {@value #KILL_RANGE} blocks it kills them.
 * </ul>
 *
 * <p>That is five beats between first seeing it and dying - fifty seconds - and it is deliberately
 * not survivable by standing still. The only thing that stops any of it is getting back above
 * {@code y=0}.
 *
 * <h2>Why the state is here and not on the entity</h2>
 *
 * <p>The distance is a property of the <i>watch</i>, not of the figure. It has to keep counting down
 * by thirty a beat whatever the player does, so that walking away buys nothing: the figure is simply
 * re-placed at the new distance from wherever they are now. Holding that on the entity would also
 * repeat a mistake this codebase has already made once - a chunk unload does not call
 * {@code Entity#remove}, so anything whose lifecycle lives on the entity leaks when the entity
 * quietly stops existing. The world tick always runs. See {@code BossBarWatch} for the same
 * reasoning at length.
 */
public final class TheDeep {
	/** Everything in here is measured in these. Ten seconds. */
	public static final int BEAT_TICKS = 200;

	/** Under this is "in the bedrock layer". The terrain starts at zero and there is nothing below. */
	public static final int CEILING = 0;

	/** How long somebody has to stay down there before it appears. Three minutes. */
	private static final int APPEARS_AFTER = 20 * 60 * 3;

	/** How far out it is first put. */
	private static final int START_RANGE = 150;

	/** How much closer it is on each beat after that. */
	private static final int STEP = 30;

	/** Any nearer than this and they are dead. */
	private static final int KILL_RANGE = 5;

	/**
	 * Where its feet go. The bedrock is the single layer at {@code min_y}, so this is the first block
	 * of air above it - it stands on the floor of the world.
	 */
	private static final int FLOOR = -63;

	/**
	 * Margin taken off the tracking limit when clamping {@link #START_RANGE}.
	 *
	 * <p>A player is only sent entities inside their own chunk view, so on a short render distance a
	 * figure at a hundred and fifty blocks would never reach the client at all and the whole thing
	 * would silently do nothing. Rather than require a setting, the first placement is brought in to
	 * whatever that player can actually be shown, less this, so it is always visible on the beat it
	 * appears.
	 */
	private static final int TRACKING_MARGIN = 16;

	/** What is being done to one player. Gone the moment they are above the ceiling. */
	private static final class Watch {
		private int ticks;
		private int range;
		private DeepSteveEntity figure;
	}

	private static final Map<UUID, Watch> WATCHES = new HashMap<>();

	private TheDeep() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(TheDeep::onWorldTick);
	}

	/** Whether this figure still belongs to a running watch. Asked by the figure itself. */
	public static boolean owns(DeepSteveEntity figure) {
		for (Watch watch : WATCHES.values()) {
			if (watch.figure == figure) {
				return true;
			}
		}

		return false;
	}

	private static void onWorldTick(ServerWorld world) {
		if (!ModDimensions.TWILIGHT_WORLD.equals(world.getRegistryKey())) {
			return;
		}

		for (ServerPlayerEntity player : world.getPlayers()) {
			tickPlayer(world, player);
		}

		// Anybody who left the dimension, logged out or died is no longer in the loop above, so their
		// watch is dropped here rather than being left to count up for ever.
		Iterator<Map.Entry<UUID, Watch>> watches = WATCHES.entrySet().iterator();
		while (watches.hasNext()) {
			Map.Entry<UUID, Watch> entry = watches.next();
			if (world.getServer().getPlayerManager().getPlayer(entry.getKey()) == null) {
				end(entry.getValue());
				watches.remove();
			}
		}
	}

	private static void tickPlayer(ServerWorld world, ServerPlayerEntity player) {
		// Spectators are not in the world in the sense that matters - nothing can reach them and they
		// did not go down there, they flew through it.
		if (player.getY() >= CEILING || player.isSpectator()) {
			Watch over = WATCHES.remove(player.getUuid());
			if (over != null) {
				end(over);
			}
			return;
		}

		Watch watch = WATCHES.computeIfAbsent(player.getUuid(), uuid -> new Watch());
		watch.ticks++;

		if (watch.ticks % BEAT_TICKS != 0) {
			return;
		}

		// The beat itself: the sound and the second of open fog, every time, from the first one.
		ServerPlayNetworking.send(player, new DeepPayload());

		if (watch.ticks < APPEARS_AFTER) {
			return;
		}

		if (watch.figure == null || !watch.figure.isAlive()) {
			watch.range = firstRange(player);
			watch.figure = place(world, player, watch.range);
			return;
		}

		watch.range -= STEP;

		if (watch.range <= KILL_RANGE) {
			// It is put down on top of them first, so the last thing on the screen is the thing that
			// did it rather than an empty room.
			move(watch.figure, player, 0);
			player.damage(world.getDamageSources().genericKill(), Float.MAX_VALUE);
			WATCHES.remove(player.getUuid());
			end(watch);
			return;
		}

		move(watch.figure, player, watch.range);
	}

	/**
	 * How far out the first one goes: {@link #START_RANGE}, unless this player could not be shown
	 * something that far away, in which case as far as they can.
	 */
	private static int firstRange(ServerPlayerEntity player) {
		int chunks = Math.min(player.getServer().getPlayerManager().getViewDistance(),
				ModEntities.DEEP_STEVE.getMaxTrackDistance());
		return Math.max(KILL_RANGE + STEP, Math.min(START_RANGE, chunks * 16 - TRACKING_MARGIN));
	}

	private static DeepSteveEntity place(ServerWorld world, ServerPlayerEntity player, int range) {
		DeepSteveEntity figure = ModEntities.DEEP_STEVE.create(world);
		if (figure == null) {
			return null;
		}

		// A bearing at random, so it is not always over the same shoulder, and so a second visit to
		// the bedrock layer is not the same event twice.
		double bearing = world.getRandom().nextDouble() * Math.PI * 2.0;
		setPosition(figure, player, Math.cos(bearing), Math.sin(bearing), range);

		world.spawnEntity(figure);
		return figure;
	}

	/** Re-places it at {@code range} from the player, along the line it is already standing on. */
	private static void move(DeepSteveEntity figure, ServerPlayerEntity player, int range) {
		double dx = figure.getX() - player.getX();
		double dz = figure.getZ() - player.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);

		// Directly on top of somebody has no direction to keep, which only happens on the killing beat.
		if (length < 1.0E-4) {
			dx = 1.0;
			dz = 0.0;
			length = 1.0;
		}

		setPosition(figure, player, dx / length, dz / length, range);
	}

	/**
	 * Writes the position outright rather than nudging it.
	 *
	 * <p>Velocity is never spent on a mob with its AI off, and there is nothing to animate anyway -
	 * this is meant to be a change of place between two frames nobody saw, not a walk.
	 */
	private static void setPosition(DeepSteveEntity figure, ServerPlayerEntity player,
									double unitX, double unitZ, int range) {
		double x = player.getX() + unitX * range;
		double z = player.getZ() + unitZ * range;

		figure.refreshPositionAndAngles(x, FLOOR, z, figure.getYaw(), 0.0F);
		figure.faceOnce(player.getX(), player.getZ());
	}

	private static void end(Watch watch) {
		if (watch.figure != null) {
			watch.figure.discard();
			watch.figure = null;
		}
	}
}
