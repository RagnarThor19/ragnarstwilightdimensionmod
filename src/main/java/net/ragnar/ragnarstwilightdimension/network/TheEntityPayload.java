package net.ragnar.ragnarstwilightdimension.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;
import net.ragnar.ragnarstwilightdimension.sound.ModSounds;

/**
 * What The Entity's fight is doing to one player's client: which of its tracks is playing, and
 * whether the world has just been taken away for half a second.
 *
 * <p>Both of those have to happen on an exact tick, which is what makes this a different shape from
 * {@link WitnessPayload} even though it does a similar job. That one carries a <i>state</i> - fog
 * this thick, music on - and the client holds it until told otherwise. This one carries a state
 * <b>and an instant</b>, and "sixty-four seconds of survival start now" is not something a client can
 * be told twice: the second telling would restart the track from the top.
 *
 * <p>So it is split in two.
 *
 * <ul>
 *   <li>{@code cue} is a counter the fight bumps every time something actually happens. The client
 *       remembers the last one it acted on and ignores every packet repeating it, which is how the
 *       heartbeat can be sent twice a second without ever restarting anything.
 *   <li>{@code track} and {@code blackTicks} are the instant: what to start playing, and how long to
 *       black the screen out for. Both are acted on once, on the tick the cue changes.
 * </ul>
 *
 * <p><b>{@code holdTicks} expires</b>, for exactly the reason {@link WitnessPayload} gives at length.
 * It is how long the client should go on believing the fight is up - which is what holds vanilla's
 * music tracker off it - and the fight re-states it every ten ticks. A server that stops mid-phase,
 * or a player sent somewhere else, lapses back to an ordinary client on its own instead of being left
 * in permanent silence.
 *
 * @param cue        counter of things that have happened; the client acts only when this changes
 * @param track      the {@link Track} to start on this cue, by ordinal
 * @param blackTicks how long to black the screen out for, starting now, or 0 for not at all
 * @param holdTicks  how long to keep believing the fight is up, 0 to stop believing it now
 */
public record TheEntityPayload(int cue, int track, int blackTicks, int holdTicks) implements CustomPayload {
	public static final CustomPayload.Id<TheEntityPayload> ID =
			new CustomPayload.Id<>(Identifier.of(RagnarsTwilightDimension.MOD_ID, "the_entity"));

	public static final PacketCodec<RegistryByteBuf, TheEntityPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, TheEntityPayload::cue,
			PacketCodecs.VAR_INT, TheEntityPayload::track,
			PacketCodecs.VAR_INT, TheEntityPayload::blackTicks,
			PacketCodecs.VAR_INT, TheEntityPayload::holdTicks,
			TheEntityPayload::new);

	/**
	 * The tracks the fight can ask for, in the order they go on the wire.
	 *
	 * <p>Kept in the packet rather than on either side of it because both sides need the same
	 * numbering and there is no way to notice they have stopped agreeing: an off-by-one here would not
	 * fail to compile, it would play the pause music over the survival phase.
	 */
	public enum Track {
		/** Nothing. Either the fight has not started, or whatever was playing has been cut. */
		NONE(null),

		/** {@code entitystart.ogg} - the thirty seconds it hangs there and does nothing. */
		START(ModSounds.MUSIC_ENTITY_START),

		/** {@code entity.ogg} - sixty-four seconds, which is why the survival phase is that long. */
		FIGHT(ModSounds.MUSIC_ENTITY),

		/** {@code entitypause1..3.ogg} - one of the three, rolled for when the pause begins. */
		PAUSE_1(ModSounds.MUSIC_ENTITY_PAUSE_1),
		PAUSE_2(ModSounds.MUSIC_ENTITY_PAUSE_2),
		PAUSE_3(ModSounds.MUSIC_ENTITY_PAUSE_3);

		private static final Track[] BY_ID = values();

		private final RegistryEntry.Reference<SoundEvent> sound;

		Track(RegistryEntry.Reference<SoundEvent> sound) {
			this.sound = sound;
		}

		/** The file, or null for {@link #NONE}. */
		public SoundEvent sound() {
			return this.sound == null ? null : this.sound.value();
		}

		/** What the number on the wire meant. Anything unrecognised is nothing at all. */
		public static Track byId(int id) {
			return id >= 0 && id < BY_ID.length ? BY_ID[id] : NONE;
		}

		/** One of the three pause tracks, for the roll at the start of each pause. */
		public static Track pause(int roll) {
			return BY_ID[PAUSE_1.ordinal() + Math.floorMod(roll, 3)];
		}
	}

	/**
	 * The fight is over for this client: whatever is playing stops, and vanilla gets its music back.
	 *
	 * <p>The cue is deliberately -1 rather than a number in the sequence. Every real cue is a value the
	 * client might already have seen and would then ignore; -1 is one it cannot have seen, so an ending
	 * is the one packet that is always acted on however late it arrives.
	 */
	public static TheEntityPayload off() {
		return new TheEntityPayload(-1, Track.NONE.ordinal(), 0, 0);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** Has to run on both sides, so this is called from the common initialiser. */
	public static void register() {
		PayloadTypeRegistry.playS2C().register(ID, CODEC);
	}
}
