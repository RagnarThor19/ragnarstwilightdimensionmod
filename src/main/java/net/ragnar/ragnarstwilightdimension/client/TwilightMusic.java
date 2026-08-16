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

	/**
	 * The same playlist, handed over in a form that will not interrupt anything.
	 *
	 * <p>Asking for a different track is normally the same thing as cutting the current one:
	 * {@code MusicTracker#tick} stops whatever is playing the moment the client wants something else -
	 * but only if the something else says it may, which is the last argument here. This is
	 * {@link #TWILIGHT} with that permission withheld.
	 *
	 * <p>It exists for one moment in the whole mod. When the witness dies its track is faded out over
	 * three seconds instead of cut, and a fade needs the thing being faded to still be playing - so for
	 * those three seconds the dimension asks for a version of itself that will not stop it. The fade
	 * does the stopping itself when it reaches the bottom.
	 */
	public static final MusicSound TWILIGHT_RINGING_OUT =
			new MusicSound(ModSounds.MUSIC_TWILIGHT, MIN_GAP_TICKS, MAX_GAP_TICKS, false);

	/**
	 * Both longer than the event itself, which is the point: the track runs exactly once and can
	 * never start a second time part-way through. The event is over before either of these could
	 * elapse, and its end cuts the tracker anyway.
	 */
	private static final int BLOODMOON_MIN_GAP_TICKS = 2400;
	private static final int BLOODMOON_MAX_GAP_TICKS = 3600;

	/** Plays instead of {@link #TWILIGHT} for as long as the event is up. */
	public static final MusicSound BLOODMOON =
			new MusicSound(ModSounds.MUSIC_BLOODMOON, BLOODMOON_MIN_GAP_TICKS, BLOODMOON_MAX_GAP_TICKS, true);

	/**
	 * The witness's track, from the moment the fare is paid until the thing is dead or has forgotten
	 * you.
	 *
	 * <p>The gap is short on purpose, unlike the blood moon's. That event is exactly as long as its
	 * track and can only happen once; this one runs until somebody wins, which might be two minutes or
	 * might be ten, so the track has to be allowed to come round again rather than leave the back half
	 * of a long fight in silence.
	 */
	public static final MusicSound WITNESS = new MusicSound(ModSounds.MUSIC_WITNESS, 40, 80, true);

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
