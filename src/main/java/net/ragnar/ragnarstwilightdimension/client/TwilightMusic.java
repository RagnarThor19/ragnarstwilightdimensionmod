package net.ragnar.ragnarstwilightdimension.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.sound.MusicSound;
import net.ragnar.ragnarstwilightdimension.mixin.client.MusicTrackerAccessor;
import net.ragnar.ragnarstwilightdimension.sound.ModSounds;

/**
 * Drives the twilight dimension's music.
 *
 * <p>Playback itself is left to vanilla's {@link MusicTracker} - a mixin on
 * {@code MinecraftClient#getMusicType} hands it {@link #TWILIGHT} while you are in the dimension,
 * which also means overworld music is cut off the moment you arrive. All this class does is set the
 * countdown so the first track starts a fixed three seconds after you step through.
 */
public final class TwilightMusic {
	/** Ticks between arriving and the first track. 20 ticks = 1 second. */
	private static final int START_DELAY_TICKS = 60;

	/** Silence between tracks once the first one has finished, in ticks (60s - 180s). */
	private static final int MIN_GAP_TICKS = 1200;
	private static final int MAX_GAP_TICKS = 3600;

	public static final MusicSound TWILIGHT =
			new MusicSound(ModSounds.MUSIC_TWILIGHT, MIN_GAP_TICKS, MAX_GAP_TICKS, true);

	private static boolean wasInTwilight;

	private TwilightMusic() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TwilightMusic::onClientTick);
	}

	private static void onClientTick(MinecraftClient client) {
		if (client.world == null) {
			wasInTwilight = false;
			return;
		}

		boolean inTwilight = TwilightClient.isInTwilight();

		if (inTwilight && !wasInTwilight) {
			MusicTracker tracker = client.getMusicTracker();
			// stop() cuts whatever was playing but pushes the timer out by 100 ticks of its own,
			// so the delay has to be set afterwards, not before.
			tracker.stop();
			((MusicTrackerAccessor) tracker).setTimeUntilNextSong(START_DELAY_TICKS);
		}

		wasInTwilight = inTwilight;
	}
}
