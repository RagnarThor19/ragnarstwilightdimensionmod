package net.ragnar.ragnarstwilightdimension.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.ragnarstwilightdimension.mixin.client.MusicTrackerAccessor;
import net.ragnar.ragnarstwilightdimension.network.StarePayload;

/**
 * The client half of the stare: the three seconds where nothing answers.
 *
 * <p>Four things happen at once and stop at once. The view is dragged up onto the figure and pinned
 * there; the fog goes the same red the blood moon uses, through the same code, so it is not a
 * similar effect but the same one; the camera shakes; and every control the player has is ignored.
 * Movement and mouse are handled by the three mixins that read {@link #isLocked()} - this class does
 * not do the blocking itself, it only says when.
 *
 * <p>The snap is the one part that is not instant. The head sweeps up over {@link #SNAP_TICKS}
 * rather than cutting, because a cut is not read as a movement at all - the player simply finds
 * themselves looking somewhere else and assumes the game glitched. Three ticks is fast enough to be
 * violent and slow enough to be seen happening to you.
 *
 * <p>The clock is kept here rather than waited on from the server, so the three seconds are the same
 * three seconds on a bad connection. The server sends its own ending anyway and whichever arrives
 * first wins - see {@code Stare.end}.
 */
public final class StareClient {
	/** How long the head takes to come up, in ticks. */
	private static final int SNAP_TICKS = 3;

	/** How far the camera wanders, in degrees. Small - it is a rumble, not a seizure. */
	public static final float SHAKE_DEGREES = 0.65F;

	/** How long the dimension stays silent from the music being cut. Three seconds of event, then ten. */
	private static final int SILENCE_TICKS = 260;

	/** Where it is. The point the view is pinned to, which is the figure's eyes. */
	private static Vec3d aim = Vec3d.ZERO;

	private static int ticksLeft;
	private static int elapsed;

	/** Where the player was looking when it started, for the sweep to come up from. */
	private static float fromYaw;
	private static float fromPitch;

	private StareClient() {
	}

	/** Whether one is running. Everything else in here is gated on this. */
	public static boolean isActive() {
		return ticksLeft > 0;
	}

	/** Whether the world should be red. Separate name because it is asked from the fog code. */
	public static boolean isRed() {
		return isActive();
	}

	/** Whether the player's controls are being ignored. Asked from the input and mouse mixins. */
	public static boolean isLocked() {
		return isActive();
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(StarePayload.ID, (payload, context) ->
				context.client().execute(() -> accept(payload)));

		ClientTickEvents.END_CLIENT_TICK.register(StareClient::onClientTick);
	}

	private static void accept(StarePayload payload) {
		if (payload.ticks() == StarePayload.HUSH) {
			hush();
			return;
		}
		if (payload.ticks() <= 0) {
			stop();
			return;
		}

		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) {
			return;
		}

		aim = new Vec3d(payload.x(), payload.y(), payload.z());
		ticksLeft = payload.ticks();
		elapsed = 0;
		fromYaw = player.getYaw();
		fromPitch = player.getPitch();
	}

	/**
	 * Kills the music, half a second before anything else happens.
	 *
	 * <p>An outright cut, not a fade - the same treatment the blood moon gets on both its edges, and
	 * for the same reason: a fade is a transition and reads as the game changing state, while a cut
	 * reads as something having been interrupted.
	 *
	 * <p>The silence outlasts the event by a good ten seconds. Coming back to music the moment the
	 * sky empties would tidy the whole thing away and say it was a set piece; leaving the player
	 * standing in a dimension that has gone quiet says something happened here. Ordinary gaps between
	 * twilight tracks run to three minutes anyway, so this is well inside what the dimension does on
	 * its own - it is only the timing of it that means anything.
	 *
	 * <p>{@code stop()} first and the timer second, always. Stopping pushes out a hundred ticks of its
	 * own and would overwrite this if the order were the other way round - the same ordering
	 * {@link TwilightMusic} and {@link BloodMoonClient} both depend on.
	 */
	private static void hush() {
		MusicTracker tracker = MinecraftClient.getInstance().getMusicTracker();
		tracker.stop();
		((MusicTrackerAccessor) tracker).setTimeUntilNextSong(SILENCE_TICKS);
	}

	private static void stop() {
		ticksLeft = 0;
		elapsed = 0;
	}

	private static void onClientTick(MinecraftClient client) {
		if (!isActive()) {
			return;
		}

		ClientPlayerEntity player = client.player;

		// Leaving the dimension, dying, or getting disconnected all end it here rather than waiting for
		// a packet that may never come.
		if (player == null || !TwilightClient.isInTwilight() || player.isDead()) {
			stop();
			return;
		}

		elapsed++;
		ticksLeft--;

		aimView(player);
	}

	/**
	 * Points the player at it, sweeping for the first few ticks and pinned after that.
	 *
	 * <p>Only {@code yaw} and {@code pitch} are written, never {@code prevYaw} and {@code prevPitch}.
	 * That is deliberate: the camera draws the angle interpolated between the two every frame, so
	 * leaving the previous values alone is what turns three ticks of steps into a smooth sweep at
	 * whatever framerate the player is running.
	 */
	private static void aimView(ClientPlayerEntity player) {
		Vec3d eyes = player.getEyePos();
		double dx = aim.x - eyes.x;
		double dy = aim.y - eyes.y;
		double dz = aim.z - eyes.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		float pitch = (float) (-(MathHelper.atan2(dy, horizontal) * MathHelper.DEGREES_PER_RADIAN));

		if (elapsed < SNAP_TICKS) {
			// Eased out, so it leaves fast and arrives slowing down. A linear sweep over three ticks
			// reads as the camera being dragged; this reads as a head being pulled.
			float progress = (float) elapsed / SNAP_TICKS;
			float eased = 1.0F - (1.0F - progress) * (1.0F - progress);

			// Through wrapDegrees so a turn across the back of the compass goes the short way round
			// instead of spinning the player most of the way about.
			yaw = fromYaw + MathHelper.wrapDegrees(yaw - fromYaw) * eased;
			pitch = fromPitch + (pitch - fromPitch) * eased;
		}

		player.setYaw(yaw);
		player.setPitch(pitch);
	}
}
