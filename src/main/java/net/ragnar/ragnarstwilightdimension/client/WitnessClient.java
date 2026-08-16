package net.ragnar.ragnarstwilightdimension.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.ragnarstwilightdimension.mixin.client.MusicTrackerAccessor;
import net.ragnar.ragnarstwilightdimension.mixin.client.SoundManagerAccessor;
import net.ragnar.ragnarstwilightdimension.mixin.client.SoundSystemAccessor;
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
	/**
	 * How long the track takes to go, in ticks. Three seconds - the length of the death itself, so the
	 * last of it is gone at about the moment the last of the thing playing it is.
	 */
	private static final int FADE_TICKS = 60;

	/** How long the dimension stays quiet afterwards before its own playlist starts up again. */
	private static final int SILENCE_AFTER_FADE = 400;

	private static boolean music;
	private static float fogEnd;
	private static int holdTicks;

	/** Ticks left of the fade, or 0 when nothing is fading. See {@link #tickFade}. */
	private static int fadeTicks;

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
	 * Whether its track is still playing after the fight is over, on its way down to nothing. Read by
	 * the music mixin, which for as long as this is true hands back a playlist that is not allowed to
	 * interrupt it - otherwise vanilla would cut the track on the first tick of the fade and there
	 * would be nothing left to fade.
	 */
	public static boolean isMusicFading() {
		return fadeTicks > 0;
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
				// The one ending that is not a cut. Nothing is stopped here at all - the track is left
				// running and turned down a little further every tick from now until there is none of it
				// left. See tickFade.
				fadeTicks = FADE_TICKS;
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
		if (fadeTicks > 0) {
			tickFade(client);
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

	/**
	 * Turns the track down one tick's worth, and stops it outright when there is nothing left of it.
	 *
	 * <p>Reaching the source directly is the only way this can be done. Music is played as a plain
	 * non-tickable sound instance, so its volume is settled the moment it starts and vanilla never
	 * looks at it again; the one method that appears to offer a way in,
	 * {@code SoundSystem#updateSoundVolume}, ignores the volume it is given for every category except
	 * master and recomputes from the player's own settings - see {@link SoundSystemAccessor}. So the
	 * fade goes to the audio source, which affects that one sound and leaves every setting alone.
	 *
	 * <p>Squared on the way down, because volume is not heard the way it is measured: a straight ramp
	 * spends most of its length in the part of the scale the ear barely separates and reads as the
	 * sound holding steady and then vanishing near the end. Squaring pulls the drop early, which is
	 * what a thing going quiet actually sounds like.
	 *
	 * <p>Anything that stops the track from under this - the player leaving the dimension, the file
	 * ending on its own - simply ends the fade. There is nothing to turn down and nothing to put back.
	 */
	private static void tickFade(MinecraftClient client) {
		MusicTracker tracker = client.getMusicTracker();

		if (--fadeTicks <= 0) {
			// All the way down, so it is stopped properly rather than left running at silence, and the
			// dimension gets its own quiet back before the playlist returns.
			fadeTicks = 0;
			tracker.stop();
			((MusicTrackerAccessor) tracker).setTimeUntilNextSong(SILENCE_AFTER_FADE);
			return;
		}

		SoundInstance current = ((MusicTrackerAccessor) tracker).getCurrent();
		if (current == null) {
			fadeTicks = 0;
			return;
		}

		SoundSystem system = ((SoundManagerAccessor) client.getSoundManager()).getSoundSystem();
		Channel.SourceManager manager = ((SoundSystemAccessor) system).getSources().get(current);

		if (manager == null || manager.isStopped()) {
			fadeTicks = 0;
			return;
		}

		float share = (float) fadeTicks / FADE_TICKS;

		// The same sum vanilla does when it starts a sound, with the fade on the end of it, so the
		// player's music slider still means what it meant a second ago. Master is not in here on
		// purpose - that one is applied to the listener rather than to any one source.
		float volume = MathHelper.clamp(
				current.getVolume() * client.options.getSoundVolume(SoundCategory.MUSIC), 0.0F, 1.0F);

		float faded = volume * share * share;
		manager.run(source -> source.setVolume(faded));
	}

	private static void clear() {
		music = false;
		fogEnd = 0.0F;
		holdTicks = 0;

		// Whether the track is faded is decided by the packet that ends the fight, not here. This is the
		// state going back to nothing, and nothing is the ordinary cut.
		fadeTicks = 0;

		// The legs go back with everything else. A fight that ended by the chunk unloading must not
		// leave somebody walking north for the rest of the session.
		pullTicks = 0;
	}
}
