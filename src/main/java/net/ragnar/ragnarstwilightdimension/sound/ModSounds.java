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

	/** The blood moon track. Replaces the ordinary playlist for as long as the event is up. */
	public static final RegistryEntry.Reference<SoundEvent> MUSIC_BLOODMOON = register("music.bloodmoon");

	/**
	 * Custom atmosphere beds, one picked at random. The files are optional - drop them into
	 * {@code sounds/ambience/} and flip {@code INCLUDE_CUSTOM_AMBIENCE} in {@code TwilightAmbience}.
	 */
	public static final RegistryEntry.Reference<SoundEvent> AMBIENCE_TWILIGHT = register("ambience.twilight");

	public static final RegistryEntry.Reference<SoundEvent> AMBIENCE_TWILIGHT_1 = register("ambience.twilight_1");
	public static final RegistryEntry.Reference<SoundEvent> AMBIENCE_TWILIGHT_2 = register("ambience.twilight_2");
	public static final RegistryEntry.Reference<SoundEvent> AMBIENCE_TWILIGHT_3 = register("ambience.twilight_3");

	/**
	 * How far the leviathan calls carry, in blocks.
	 *
	 * <p>This has to be set in two places that the game never cross-checks, so they are kept in sync
	 * by hand:
	 *
	 * <ul>
	 *   <li>Here, via {@link SoundEvent#of(Identifier, float)}. A sound event built this way has a
	 *       <i>fixed</i> range, so the server forwards the play packet to every player within this
	 *       many blocks. The default {@link SoundEvent#of(Identifier)} instead gives a range of
	 *       {@code 16 x volume}, which would need a volume of 32 to reach this far.
	 *   <li>In {@code sounds.json}, as {@code attenuation_distance} on each of the three files. The
	 *       client fades the sound out linearly over {@code max(volume, 1) x attenuation_distance}
	 *       blocks, so a mismatch either cuts the sound off early or leaves it audible but silent.
	 * </ul>
	 *
	 * <p>Gain works out to {@code 1 - distance / 512}, so a call placed at the 110-190 blocks the
	 * spawner uses lands around 63-79% volume, and stays faintly audible out to 512.
	 */
	public static final float LEVIATHAN_RANGE = 512.0F;

	/** Something enormous, a long way off. Holds all three calls, so the game picks one at random. */
	public static final RegistryEntry.Reference<SoundEvent> AMBIENCE_LEVIATHAN =
			registerRanged("ambience.leviathan", LEVIATHAN_RANGE);

	/** Individually addressable calls, handy for {@code /playsound} while testing. */
	public static final RegistryEntry.Reference<SoundEvent> AMBIENCE_LEVIATHAN_1 =
			registerRanged("ambience.leviathan_1", LEVIATHAN_RANGE);
	public static final RegistryEntry.Reference<SoundEvent> AMBIENCE_LEVIATHAN_2 =
			registerRanged("ambience.leviathan_2", LEVIATHAN_RANGE);
	public static final RegistryEntry.Reference<SoundEvent> AMBIENCE_LEVIATHAN_3 =
			registerRanged("ambience.leviathan_3", LEVIATHAN_RANGE);

	private ModSounds() {
	}

	/** Touching this class is enough to run the static initialisers above. */
	public static void initialize() {
	}

	private static RegistryEntry.Reference<SoundEvent> register(String path) {
		Identifier id = Identifier.of(RagnarsTwilightDimension.MOD_ID, path);
		return Registry.registerReference(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	/** Like {@link #register}, but the server forwards it {@code range} blocks instead of 16. */
	private static RegistryEntry.Reference<SoundEvent> registerRanged(String path, float range) {
		Identifier id = Identifier.of(RagnarsTwilightDimension.MOD_ID, path);
		return Registry.registerReference(Registries.SOUND_EVENT, id, SoundEvent.of(id, range));
	}
}
