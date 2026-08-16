package net.ragnar.ragnarstwilightdimension.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.util.math.Vec3d;
import net.ragnar.ragnarstwilightdimension.mixin.client.MusicTrackerAccessor;
import net.ragnar.ragnarstwilightdimension.network.WitnessPayload;
import net.ragnar.ragnarstwilightdimension.network.WitnessPullPayload;

/**
 * The client half of the witness fight: the track it puts on, and how much of the world it takes
 * away while it is on.
 *
 * <p>Both are held on a countdown rather than a flag. The server re-states them twice a second and
 * they lapse a moment after it stops - see {@link WitnessPayload} for why an event that can end by
 * a chunk unloading must never rely on being told that it ended.
 */
public final class WitnessClient {
	private static boolean music;
	private static float fogEnd;
	private static int holdTicks;

	/** Set while its track is being left to finish of its own accord. See {@link #accept}. */
	private static boolean ringingOut;

	private static Vec3d pull = Vec3d.ZERO;
	private static int pullTicks;

	private WitnessClient() {
	}

	/** Whether the player is being walked towards something. Read by the movement mixin. */
	public static boolean isPulling() {
		return pullTicks > 0;
	}

	/** Where they are being walked to. */
	public static Vec3d pullTarget() {
		return pull;
	}

	/** Whether the witness's track has taken over. Read by the music mixin. */
	public static boolean isMusicActive() {
		return music;
	}

	/**
	 * Whether its track is still playing after the fight is over, and is to be left alone until it
	 * finishes. Read by the music mixin, which hands back a playlist that cannot interrupt it.
	 */
	public static boolean isMusicRingingOut() {
		return ringingOut;
	}

	/** Where the fog should end, or 0 for the ordinary eleven. Read by the fog mixin. */
	public static float fogEnd() {
		return fogEnd;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(WitnessPayload.ID, (payload, context) ->
				context.client().execute(() -> accept(payload)));

		ClientPlayNetworking.registerGlobalReceiver(WitnessPullPayload.ID, (payload, context) ->
				context.client().execute(() -> {
					pull = new Vec3d(payload.x(), payload.y(), payload.z());
					pullTicks = payload.ticks();
				}));

		ClientTickEvents.END_CLIENT_TICK.register(WitnessClient::onClientTick);
	}

	private static void accept(WitnessPayload payload) {
		boolean wasMusic = music;

		music = payload.music();
		fogEnd = payload.fogEnd();
		holdTicks = payload.ticks();

		if (payload.ticks() <= 0) {
			clear();
		}

		// The tracker is only disturbed when the answer actually changes, because cutting it every
		// half second would mean the track restarts from the top twice a second and never plays.
		if (music != wasMusic) {
			if (!music && payload.fade()) {
				// The one ending that is not a cut. Nothing is stopped and no timer is set: the track is
				// simply left running, and the music mixin starts handing back a playlist that is not
				// allowed to interrupt it - see TwilightMusic.TWILIGHT_RINGING_OUT. It ends when the file
				// ends, which is the only ending in the dimension that is not decided by the dimension.
				ringingOut = true;
			} else {
				MusicTracker tracker = MinecraftClient.getInstance().getMusicTracker();
				tracker.stop();

				// Straight in with no pause on the way to the fight, and a long silence on the way out
				// of it - the same trick, in both directions, that the blood moon and the stare use.
				((MusicTrackerAccessor) tracker).setTimeUntilNextSong(music ? 0 : 200);
			}
		}
	}

	private static void onClientTick(MinecraftClient client) {
		// Asked rather than timed, because how long is a question about the file and not about the
		// fight. It also unsticks itself: a fight that ended while the tracker happened to be between
		// tracks was never ringing out at all, and clears on the next tick.
		if (ringingOut && !client.getMusicTracker().isPlayingType(TwilightMusic.WITNESS)) {
			ringingOut = false;
		}

		if (pullTicks > 0) {
			pullTicks--;
		}

		if (holdTicks <= 0) {
			return;
		}

		if (--holdTicks == 0) {
			boolean wasMusic = music;
			clear();

			if (wasMusic) {
				MusicTracker tracker = client.getMusicTracker();
				tracker.stop();
				((MusicTrackerAccessor) tracker).setTimeUntilNextSong(200);
			}
		}
	}

	private static void clear() {
		music = false;
		fogEnd = 0.0F;
		holdTicks = 0;

		// Whether the track is left running is decided by the packet that ends the fight, not here.
		// This is the state going back to nothing, and nothing is the ordinary cut.
		ringingOut = false;

		// The legs go back with everything else. A fight that ended by the chunk unloading must not
		// leave somebody walking north for the rest of the session.
		pullTicks = 0;
	}
}
