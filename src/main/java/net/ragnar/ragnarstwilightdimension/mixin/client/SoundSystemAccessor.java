package net.ragnar.ragnarstwilightdimension.mixin.client;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Every sound currently making a noise, and the audio source it is making it on.
 *
 * <p>This is what makes a fade possible at all. {@code SoundSystem#updateSoundVolume} looks like the
 * method for the job and is not: for anything other than the master category it ignores the volume it
 * is handed and recomputes every source from the player's own settings, so the only way to use it
 * would be to edit their music slider - which would show up in their options menu and stay there.
 *
 * <p>Going through the source instead changes one playing sound and nothing else, touches no setting,
 * and lasts exactly as long as that sound does. See {@code WitnessClient#tickFade}.
 */
@Mixin(SoundSystem.class)
public interface SoundSystemAccessor {
	@Accessor("sources")
	Map<SoundInstance, Channel.SourceManager> getSources();
}
