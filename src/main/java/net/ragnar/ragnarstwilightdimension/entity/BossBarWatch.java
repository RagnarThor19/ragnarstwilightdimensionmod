package net.ragnar.ragnarstwilightdimension.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A boss bar that stops being renewed takes itself down.
 *
 * <h2>The hole this fills</h2>
 *
 * <p>Everything a fight does to a client in this mod expires on its own - the fog and the music are
 * held on a countdown that the fight re-states twice a second, for the reason {@code WitnessPayload}
 * gives at length: a state that lapses cannot strand anybody, and every "turn it off" message can be
 * missed. The boss bar was the one exception, because it is vanilla's and vanilla's does not expire.
 * It goes up when it is told and stays up for ever.
 *
 * <p>That is fine until the thing holding it stops being asked to do anything, and there is one
 * ordinary way for that to happen: <b>you die fighting it, alone</b>. Dying in the twilight is an
 * immediate respawn with no death screen - see {@code TwilightRespawn} - so within a tick or two you
 * are in the overworld, nobody is near the boss, its chunk stops ticking, and every line of code that
 * would have taken the bar back is in a method that is never called again. Unloading a chunk does not
 * call {@code Entity#remove} either; it goes through the final {@code setRemoved}. So the fight
 * quietly ends and the bar stays on the screen of somebody now standing in another world.
 *
 * <p>So the bar gets the same treatment as the fog and the music. A boss says {@link #alive} once a
 * tick while it is showing one, and anything that has not said so for two seconds is taken down.
 *
 * <h2>There is deliberately no "gone"</h2>
 *
 * <p>The obvious companion to {@link #alive} would be a method a boss calls when it ends properly,
 * and it is left out on purpose. The entire failure this exists for is a boss that never got to say
 * anything; a design where the ending still has to be announced would have the same hole in it one
 * layer up. Bosses that end cleanly already take their own bar down in the same breath - what lapses
 * here a moment later is an empty bar and a map entry, which costs nothing and cannot be forgotten.
 *
 * <p>{@link TheEntity} does not use this. Its bar belongs to {@link TheEntityFight}, which ticks with
 * the world whether or not anything is loaded and so already has a stronger guarantee than a lapse.
 * The witness cannot be given the same treatment because there can be several of them at once - they
 * are landmarks, spread across the dimension - so each needs its own bar, and a per-bar expiry is
 * what fits that.
 */
public final class BossBarWatch {
	/**
	 * How long a bar survives without being renewed, in ticks.
	 *
	 * <p>Two seconds, matching the hold on the fog and the music, so all three of a fight's effects
	 * lapse together rather than the picture going before the sound. Comfortably longer than the ten
	 * ticks between the renewals it is measuring, so ordinary jitter never takes a bar down mid-fight.
	 */
	private static final int LAPSE_TICKS = 40;

	/** Every bar currently up, and how long it has left before it is assumed abandoned. */
	private static final Map<ServerBossBar, Integer> SHOWING = new HashMap<>();

	private BossBarWatch() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BossBarWatch::onServerTick);
	}

	/** Called once a tick by anything currently showing a bar. Anything that stops loses it. */
	public static void alive(ServerBossBar bar) {
		SHOWING.put(bar, LAPSE_TICKS);
	}

	private static void onServerTick(MinecraftServer server) {
		if (SHOWING.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<ServerBossBar, Integer>> showing = SHOWING.entrySet().iterator();

		while (showing.hasNext()) {
			Map.Entry<ServerBossBar, Integer> entry = showing.next();
			int left = entry.getValue() - 1;

			if (left > 0) {
				entry.setValue(left);
				continue;
			}

			ServerBossBar bar = entry.getKey();

			// setVisible is what actually takes it off the screens: it sends a removal to everybody
			// currently holding the bar. clearPlayers after it sends nothing - removePlayer is silent
			// once the bar is invisible - and is only here to empty the set. Either order works, as long
			// as it is understood that whichever runs first is the one doing the talking; two calls that
			// both assume the other one sent the packet is how a bar gets left on somebody for ever.
			bar.setVisible(false);
			bar.clearPlayers();

			// Dropped rather than kept at zero, so this map is only ever as big as the number of fights
			// actually happening, and so the bar and the players it was holding can be collected.
			showing.remove();
		}
	}
}
