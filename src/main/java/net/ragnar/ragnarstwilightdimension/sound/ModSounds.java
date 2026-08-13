package net.ragnar.ragnarstwilightdimension.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

/**
 * Sound events for the mod. The audio files themselves live in
 * {@code assets/ragnarstwilightdimension/sounds/music/} and are wired up by {@code sounds.json}.
 */
public final class ModSounds {
	/** The dimension's music. Holds all three tracks, so the game picks one at random each time. */
	public static final RegistryEntry.Reference<SoundEvent> MUSIC_TWILIGHT = register("music.twilight");

	/** Individually addressable tracks, handy for {@code /playsound} while testing. */
	public static final RegistryEntry.Reference<SoundEvent> MUSIC_TWILIGHT_1 = register("music.twilight_1");
	public static final RegistryEntry.Reference<SoundEvent> MUSIC_TWILIGHT_2 = register("music.twilight_2");
	public static final RegistryEntry.Reference<SoundEvent> MUSIC_TWILIGHT_3 = register("music.twilight_3");

	/**
	 * Custom atmosphere beds, one picked at random. The files are optional - drop them into
	 * {@code sounds/ambience/} and flip {@code INCLUDE_CUSTOM_AMBIENCE} in {@code TwilightAmbience}.
	 */
	public static final RegistryEntry.Reference<SoundEvent> AMBIENCE_TWILIGHT = register("ambience.twilight");

	public static final RegistryEntry.Reference<SoundEvent> AMBIENCE_TWILIGHT_1 = register("ambience.twilight_1");
	public static final RegistryEntry.Reference<SoundEvent> AMBIENCE_TWILIGHT_2 = register("ambience.twilight_2");
	public static final RegistryEntry.Reference<SoundEvent> AMBIENCE_TWILIGHT_3 = register("ambience.twilight_3");

	private ModSounds() {
	}

	/** Touching this class is enough to run the static initialisers above. */
	public static void initialize() {
	}

	private static RegistryEntry.Reference<SoundEvent> register(String path) {
		Identifier id = Identifier.of(RagnarsTwilightDimension.MOD_ID, path);
		return Registry.registerReference(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}
}
