package net.ragnar.ragnarstwilightdimension.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import net.ragnar.ragnarstwilightdimension.mixin.client.MusicTrackerAccessor;
import net.ragnar.ragnarstwilightdimension.network.BloodMoonPayload;

/**
 * The client half of the blood moon: what it looks like and what it sounds like.
 *
 * <p>Holds the flag the server sends, swaps the fog colour over to red and back, and cuts the music
 * over. Everything here is gated on actually being in the twilight by its callers, so the flag can
 * safely stay set while the player is elsewhere.
 *
 * <p>Both edges are instant. There is no fade in and no fade out: the frame the packet lands on is
 * already fully red, and the frame the event ends on is already back to ordinary twilight. The
 * colour is not a warning that something is coming, it is the thing arriving.
 */
public final class BloodMoonClient {
	/** The colour the fog is replaced with. Dark and dirty rather than bright - it is old blood. */
	public static final float FOG_RED = 0.38F;
	public static final float FOG_GREEN = 0.04F;
	public static final float FOG_BLUE = 0.05F;

	private static boolean active;

	private BloodMoonClient() {
	}

	public static boolean isActive() {
		return active;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BloodMoonPayload.ID, (payload, context) ->
				context.client().execute(() -> setActive(payload.active())));
	}

	private static void setActive(boolean value) {
		if (active == value) {
			return;
		}
		active = value;

		MinecraftClient client = MinecraftClient.getInstance();
		MusicTracker tracker = client.getMusicTracker();
		tracker.stop();

		if (value) {
			// No pause before the track. The song is exactly as long as the event, so it has to begin
			// on the same tick the event does or its last note lands after everything else is already
			// gone. stop() pushes out a 100-tick timer of its own, which is why this is set after it
			// rather than before - the same ordering TwilightMusic relies on when you arrive.
			((MusicTrackerAccessor) tracker).setTimeUntilNextSong(0);
		}
	}
}
