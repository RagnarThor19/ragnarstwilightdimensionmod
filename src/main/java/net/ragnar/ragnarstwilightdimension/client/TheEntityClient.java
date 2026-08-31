package net.ragnar.ragnarstwilightdimension.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvent;
import net.ragnar.ragnarstwilightdimension.mixin.client.MusicTrackerAccessor;
import net.ragnar.ragnarstwilightdimension.network.TheEntityPayload;

/**
 * The client half of The Entity's fight: its four tracks, and the half second of black between every
 * phase and the next.
 *
 * <h2>Why the music is not left to the music tracker</h2>
 *
 * <p>Every other track in the mod is handed to vanilla's {@link MusicTracker} through a mixin on
 * {@code getMusicType}, and the tracker decides when to start it. That works because nothing else in
 * the dimension cares within ten seconds of when its music begins.
 *
 * <p>This fight does. The survival phase is sixty-four seconds because {@code entity.ogg} is, and the
 * pause is fifteen because the pause tracks are: a track that starts whenever the tracker feels like
 * it would be out of step by the end of the first loop and further out by every loop after. So the
 * server names the tick, the packet carries a cue, and the track is played straight into the sound
 * manager here - which also means it can be <i>cut</i> on a named tick, which is the whole point of
 * the blackouts.
 *
 * <p>The tracker is not fought with; it is starved. While the fight is up its countdown is pushed
 * back out of reach every tick, so it never gets as far as choosing a song of its own, and when the
 * fight ends it is handed a long silence instead of the floor. See {@link #hold}.
 *
 * <p>Everything here is held on a countdown that lapses rather than on a flag - see
 * {@link TheEntityPayload}. A client that stops hearing from the fight goes back to being an ordinary
 * client a couple of seconds later, whatever went wrong.
 */
@Environment(EnvType.CLIENT)
public final class TheEntityClient {
	/**
	 * How far ahead the music tracker's countdown is pushed, every tick, while the fight is up.
	 *
	 * <p>Any number comfortably larger than a tick would do - it is reset before it can be reached.
	 * A minute is chosen so that if this ever stops running mid-fight the failure is a minute of
	 * silence and then vanilla music, rather than the dimension's playlist over the top of the boss.
	 */
	private static final int HOLD_OFF_TICKS = 1200;

	/** How long the dimension stays quiet after the fight before vanilla may put anything on. */
	private static final int SILENCE_AFTER = 300;

	/** A cue value no packet carries, so the first real one is always acted on. */
	private static final int NO_CUE = Integer.MIN_VALUE;

	private static int lastCue = NO_CUE;
	private static int holdTicks;

	/** Ticks of black left, and how many there were, so the last one can be faded off. */
	private static int blackTicks;
	private static int blackTotal;

	/** What is playing, kept so that the next phase can stop it on the tick it begins. */
	private static SoundInstance track;

	private TheEntityClient() {
	}

	/** Whether the fight is up as far as this client knows. */
	public static boolean isActive() {
		return holdTicks > 0;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TheEntityPayload.ID, (payload, context) ->
				context.client().execute(() -> accept(payload)));

		ClientTickEvents.END_CLIENT_TICK.register(TheEntityClient::onClientTick);
		HudRenderCallback.EVENT.register(TheEntityClient::onHudRender);
	}

	private static void accept(TheEntityPayload payload) {
		// An ending is acted on whatever cue it carries and whatever this client thought was going on.
		if (payload.holdTicks() <= 0) {
			clear();
			return;
		}

		boolean wasActive = isActive();
		holdTicks = payload.holdTicks();

		if (!wasActive) {
			// Whatever vanilla had on goes now rather than a beat into the fight.
			MinecraftClient.getInstance().getMusicTracker().stop();
		}

		if (payload.cue() == lastCue) {
			// The heartbeat. It renews the hold above and nothing else - acting on the cue again would
			// restart the track from the top twice a second.
			return;
		}

		lastCue = payload.cue();

		stopTrack();

		SoundEvent sound = TheEntityPayload.Track.byId(payload.track()).sound();
		if (sound != null) {
			// music(): the music category, no position, no falloff and no distance - the same instance
			// vanilla's own tracker builds, played by us so that we know the tick it started on.
			track = PositionedSoundInstance.music(sound);
			MinecraftClient.getInstance().getSoundManager().play(track);
		}

		blackTicks = blackTotal = payload.blackTicks();
	}

	private static void onClientTick(MinecraftClient client) {
		if (blackTicks > 0) {
			blackTicks--;
		}

		if (holdTicks <= 0) {
			return;
		}

		// The disc is the only place this fight exists. A player who is somewhere else - dead and
		// respawned in the overworld, sent out through the portal - takes their own silence with them
		// rather than waiting for the hold to lapse.
		if (!TwilightClient.isInBlank()) {
			clear();
			return;
		}

		hold(client);

		if (--holdTicks == 0) {
			clear();
		}
	}

	/**
	 * Keeps vanilla's music tracker away from the fight.
	 *
	 * <p>Run at the end of the client tick, which is after {@code MusicTracker#tick} has had its turn,
	 * so the countdown it just decremented is put straight back out of reach. Nothing is stopped here
	 * and nothing is overridden - the tracker simply never reaches the line where it would choose a
	 * song, and our own track is not its and is not touched by it.
	 */
	private static void hold(MinecraftClient client) {
		((MusicTrackerAccessor) client.getMusicTracker()).setTimeUntilNextSong(HOLD_OFF_TICKS);
	}

	/**
	 * The half second the world is taken away.
	 *
	 * <p>Drawn after the whole HUD, so it covers the hotbar, the health and the boss bar as well as
	 * the world - the point of it is that there is nothing, not that the scenery has changed.
	 *
	 * <p>The last two ticks are faded rather than cut. A hard edge back into the world reads as a
	 * dropped frame; two ticks of it lifting reads as the lights coming back on, and is still far too
	 * short to be a transition anybody would call gentle.
	 */
	private static void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (blackTicks <= 0) {
			return;
		}

		int alpha = 255;
		if (blackTicks <= 2 && blackTotal > 2) {
			alpha = 255 * blackTicks / 3;
		}

		context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), alpha << 24);
	}

	/** Back to an ordinary client: nothing playing, nothing black, and vanilla's music let back in. */
	private static void clear() {
		boolean wasActive = isActive();

		stopTrack();
		holdTicks = 0;
		blackTicks = 0;
		blackTotal = 0;
		lastCue = NO_CUE;

		if (wasActive) {
			MusicTracker tracker = MinecraftClient.getInstance().getMusicTracker();
			tracker.stop();
			((MusicTrackerAccessor) tracker).setTimeUntilNextSong(SILENCE_AFTER);
		}
	}

	private static void stopTrack() {
		if (track != null) {
			MinecraftClient.getInstance().getSoundManager().stop(track);
			track = null;
		}
	}
}
