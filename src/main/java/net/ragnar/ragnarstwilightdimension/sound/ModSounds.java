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
	 * The witness's track, for the five seconds it looks at you and every second of the fight after.
	 *
	 * <p>Music, so it plays flat and at the same volume wherever the listener is standing - which is
	 * why this one wants to be a <b>stereo</b> file, unlike the positional sounds in this class.
	 * {@code sounds/music/witness.ogg}.
	 */
	public static final RegistryEntry.Reference<SoundEvent> MUSIC_WITNESS = register("music.witness");

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

	/**
	 * How far the stare carries, in blocks. Same two-places-to-keep-in-sync arrangement as
	 * {@link #LEVIATHAN_RANGE} above - here and {@code attenuation_distance} in {@code sounds.json}.
	 *
	 * <p>This number is only the <i>reach</i>: it is what makes the server forward the sound to
	 * everyone within seventy-five blocks instead of the default sixteen. How loud it is when it gets
	 * there is decided in two other places, and both of them matter more:
	 *
	 * <ul>
	 *   <li>{@code Stare} plays the positional copy at volume 3.0, which stretches the client's fade
	 *       to 225 blocks so the sound is still near full strength right out to the edge of earshot.
	 *   <li>The player being looked at does not get the positional copy at all. They get it at their
	 *       own ears, at gain 1.0, because a source is clamped at 1.0 however loud the world says it
	 *       is and no amount of tuning here would make a forty-block one hit hard enough.
	 * </ul>
	 *
	 * <p>Past that, loudness is the file's job. A quiet .ogg cannot be rescued from Java: normalise it
	 * so it peaks near 0 dBFS, because gain 1.0 on a quiet recording is still a quiet recording.
	 */
	public static final float STARE_RANGE = 75.0F;

	/**
	 * The noise the blank figure makes while it is looking at you. One file:
	 * {@code sounds/event/stare.ogg}, and it must be <b>mono</b> - the game plays a stereo file
	 * flat, with no direction and no falloff, and this one has to be coming from a specific point in
	 * the sky.
	 */
	public static final RegistryEntry.Reference<SoundEvent> STARE = registerRanged("event.stare", STARE_RANGE);

	/**
	 * How far the church bell carries, in blocks. Same two-places-to-keep-in-sync arrangement as
	 * {@link #LEVIATHAN_RANGE} and {@link #STARE_RANGE} - here, and {@code attenuation_distance} in
	 * {@code sounds.json}.
	 *
	 * <p>Two hundred and fifty is a long way past the fog, which is the point: the bell is the only
	 * thing in the dimension that tells you a building exists before you can see it. Gain works out to
	 * {@code 1 - distance / 250}, so it is at half strength at a hundred and twenty-five blocks and
	 * still just there at two hundred.
	 */
	public static final float BELL_RANGE = 250.0F;

	/**
	 * The church bell, rung once a day by {@code ChurchBell}. One file:
	 * {@code sounds/ambience/twilight_bell.ogg}, and it must be <b>mono</b> - a stereo file is played
	 * flat, with no direction and no falloff, and the entire job of this one is to come from a
	 * particular tower a long way off.
	 */
	public static final RegistryEntry.Reference<SoundEvent> BELL = registerRanged("ambience.bell", BELL_RANGE);

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
