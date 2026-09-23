package net.ragnar.ragnarstwilightdimension.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;
import net.ragnar.ragnarstwilightdimension.entity.DeepSteveEntity;
import net.ragnar.ragnarstwilightdimension.sound.ModSounds;

/**
 * The noise the figure makes, for as long as it is there.
 *
 * <p>A ten-second file on a seamless loop, coming from the figure's chest rather than from its feet,
 * and the only thing in the bedrock layer that tells you where it is between beats. Between the fog
 * opening and the fog opening again you cannot see it at all - this is the ten seconds in which you
 * work out that it is louder than it was.
 *
 * <h2>Why this is a sound instance and not the server playing a file</h2>
 *
 * <p>Playing the same ten-second sound every ten seconds from the server is not a loop. Clock drift
 * puts a seam in it, a player who arrives half-way through the cycle hears nothing for five seconds,
 * and the sound would restart at full strength from the figure's position at the moment of playing
 * rather than following it. A looping instance has none of those problems: the audio engine repeats
 * it end to end, {@link #tick} keeps it on top of the entity, and it starts the instant the figure
 * reaches the client whenever that happens to be.
 *
 * <h2>Loudness</h2>
 *
 * <p>Volume is left at 1.0 and never modulated by distance here, because the engine already does
 * that better: {@code attenuation_distance} in {@code sounds.json} sets a linear fade over that many
 * blocks, so the figure is faint at the far end of the room and full strength when it is on top of
 * you, with no steps in between.
 *
 * <p><b>Raising this number does not make it louder.</b> A sound source is clamped at 1.0 however
 * loud the code asks for, so how frightening it is at five blocks is entirely a property of the
 * {@code .ogg} - it has to be normalised to peak near 0 dBFS, and mono, or the game will play it
 * flat with no direction and no falloff at all.
 */
@Environment(EnvType.CLIENT)
public class DeepSteveSound extends MovingSoundInstance {
	/** Where on the figure the sound comes from, as a fraction of its height. Chest, not ankles. */
	private static final double CHEST = 0.75;

	private final DeepSteveEntity figure;

	public DeepSteveSound(DeepSteveEntity figure) {
		super(ModSounds.DEEP.value(), SoundCategory.HOSTILE, Random.create());
		this.figure = figure;

		this.repeat = true;
		this.repeatDelay = 0;
		this.volume = 1.0F;
		this.pitch = 1.0F;

		follow();
	}

	@Override
	public void tick() {
		// Gone when the figure is - either because the watch ended and it was discarded, or because
		// the player got out from under the world and the whole thing stopped.
		if (!this.figure.isAlive()) {
			this.setDone();
			return;
		}

		follow();
	}

	private void follow() {
		this.x = this.figure.getX();
		this.y = this.figure.getY() + DeepSteveEntity.HEIGHT * CHEST;
		this.z = this.figure.getZ();
	}
}
