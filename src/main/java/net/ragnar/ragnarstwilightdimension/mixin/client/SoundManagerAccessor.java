package net.ragnar.ragnarstwilightdimension.mixin.client;

import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The engine underneath the sound manager.
 *
 * <p>{@link SoundManager} is the public face of the sound system and deliberately exposes only whole
 * sounds - play this, stop that. Turning one down while it is running is a step below that, and the
 * only door to it is the system the manager is wrapping.
 *
 * <p>Read only, and used for exactly one thing: fading the witness's track out as it goes into the
 * ground. See {@code WitnessClient#tickFade}.
 */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
	@Accessor("soundSystem")
	SoundSystem getSoundSystem();
}
