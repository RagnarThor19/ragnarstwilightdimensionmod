package net.ragnar.ragnarstwilightdimension.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.ragnar.ragnarstwilightdimension.entity.DeepSteveEntity;
import net.ragnar.ragnarstwilightdimension.network.DeepPayload;
import net.ragnar.ragnarstwilightdimension.sound.ModSounds;

/**
 * The client half of the bedrock layer: the heartbeat, the second of open fog, and starting the
 * figure's loop.
 *
 * <h2>The fog does not widen. It stops matching.</h2>
 *
 * <p>Nothing here touches the fog <i>distance</i>, and that is a rule rather than an oversight: the
 * eleven-block room is the dimension's one constant and widening it for a dramatic beat reads as a
 * cutscene rather than as something happening in the world.
 *
 * <p>What this does instead is the trick the blood moon already runs on, described at length in
 * {@code BackgroundRendererMixin}. Vanilla uses one colour for two jobs - the fog the world fades
 * into, and the flat colour the frame is cleared to - and it sets the clear colour first. Overwriting
 * the fog colour after that leaves the two disagreeing: empty space stays the ordinary twilight grey
 * it always was, while everything solid fades toward {@link #FOG_RED} instead of toward the
 * background it is standing against. Distant geometry stops washing out and comes through as hard
 * silhouette.
 *
 * <p>So the fog is exactly as thick on the beat as it was before it. You still cannot see eleven
 * blocks. You can just suddenly see <i>shapes</i>, all the way out, for one second - which in a
 * sealed room with one thing in it is the entire point, and is why the colour chosen is near-black:
 * against the grey, a figure at a hundred and fifty blocks is a hole in the air.
 */
@Environment(EnvType.CLIENT)
public final class DeepClient {
	/**
	 * The colour the fog is replaced with on a beat. Dark grey - close to black, not black - and
	 * deliberately not the blood moon's red: these two must never be mistaken for each other, and
	 * nothing else in the mod turns the world into an outline of itself.
	 *
	 * <p>It started at near-black, which is the value that maximises the outline against the twilight
	 * grey the frame is cleared to, and that turned out to be too much: the floor and the ceiling of
	 * the room get painted this colour as well, so at near-black the whole view goes out at once and
	 * the figure has nothing to be darker than. A shade up keeps the room legible while it is open.
	 *
	 * <p>Worth knowing before tuning this further: <b>past the eleven-block fog, colour alone cannot
	 * separate the figure from what is behind it.</b> Everything beyond that distance is painted
	 * exactly this colour - the figure, the bedrock floor, the underside of the world - so the only
	 * place an outline can appear is where its edge crosses a part of the view with no geometry drawn
	 * in it at all. Under the world that is the band around the horizon where the ceiling has receded
	 * past the render distance, and how wide that band is depends on the player's own settings. Moving
	 * this value changes how dark the room is, not how far into it anybody can see.
	 */
	public static final float FOG_RED = 0.14F;
	public static final float FOG_GREEN = 0.14F;
	public static final float FOG_BLUE = 0.15F;

	/** How long the fog stays wrong after a beat. One second. */
	private static final int REVEAL_TICKS = 20;

	private static int revealing;

	private DeepClient() {
	}

	/**
	 * Whether a beat is currently open. Asked once a frame by {@code BackgroundRendererMixin}.
	 *
	 * <p>Not gated on being in the twilight here - the mixin already is, and a countdown that runs
	 * down on its own cannot strand anybody who leaves part-way through a beat.
	 */
	public static boolean isRevealing() {
		return revealing > 0;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DeepPayload.ID, (payload, context) ->
				context.client().execute(DeepClient::beat));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (revealing > 0 && !client.isPaused()) {
				revealing--;
			}
		});

		// The loop is started the moment the figure reaches the client rather than by the server
		// playing a sound at it. A ten-second file re-played every ten seconds has a seam in it, and
		// anybody arriving mid-file would get silence until the next one; a moving sound instance
		// loops seamlessly, follows the entity and starts wherever the listener happens to turn up.
		ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof DeepSteveEntity figure) {
				MinecraftClient.getInstance().getSoundManager().play(new DeepSteveSound(figure));
			}
		});
	}

	private static void beat() {
		revealing = REVEAL_TICKS;

		// At the listener rather than at a point in the world: it is not a sound in the room, it is
		// your own pulse, and it should not get quieter because you walked away from something.
		MinecraftClient.getInstance().getSoundManager().play(
				PositionedSoundInstance.master(ModSounds.HEARTBEAT.value(), 1.0F));
	}
}
