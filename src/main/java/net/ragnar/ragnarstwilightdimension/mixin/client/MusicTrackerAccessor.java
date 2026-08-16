package net.ragnar.ragnarstwilightdimension.mixin.client;

import net.minecraft.client.sound.MusicTracker;
import net.minecraft.client.sound.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MusicTracker.class)
public interface MusicTrackerAccessor {
	@Accessor("timeUntilNextSong")
	void setTimeUntilNextSong(int value);

	/**
	 * The track that is playing right now, or null between them.
	 *
	 * <p>Needed because vanilla gives no way to turn a piece of music down. Music is played as a plain
	 * non-tickable instance, so its volume is fixed at the moment it starts and nothing revisits it -
	 * the only way to fade one is to find the audio source it is running on and set the volume there,
	 * and this is the handle everything else is looked up by. See {@code WitnessClient#tickFade}.
	 */
	@Accessor("current")
	SoundInstance getCurrent();
}
