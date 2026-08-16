package net.ragnar.ragnarstwilightdimension.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

/**
 * What the witness is doing to one player's client: the music it has replaced and the size of the
 * room it has left them.
 *
 * <p>Sent only to players near the fight, because both effects are the fight - somebody four hundred
 * blocks away has no business being in a five-block fog. That makes it the opposite of
 * {@link BloodMoonPayload}, which goes to everyone because the blood moon <i>is</i> everyone.
 *
 * <p><b>Everything here expires.</b> {@code ticks} is how long the client should hold this before
 * putting itself back to normal, and the witness re-sends every half second while the fight is on.
 * The alternative - on at one edge, off at the other - leaves a player in a permanent five-block fog
 * with no music if the "off" is ever missed, and it can be missed: the chunk unloads, the server
 * stops, the packet is dropped. A state that lapses on its own cannot strand anybody.
 *
 * @param music   whether the witness's track has taken over from the dimension's own
 * @param fogEnd  where the fog should end in blocks, or 0 to leave the ordinary twilight fog alone
 * @param ticks   how long to hold it before reverting, 0 to revert now
 */
public record WitnessPayload(boolean music, float fogEnd, int ticks, boolean fade) implements CustomPayload {
	public static final CustomPayload.Id<WitnessPayload> ID =
			new CustomPayload.Id<>(Identifier.of(RagnarsTwilightDimension.MOD_ID, "witness"));

	public static final PacketCodec<RegistryByteBuf, WitnessPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOL, WitnessPayload::music,
			PacketCodecs.FLOAT, WitnessPayload::fogEnd,
			PacketCodecs.VAR_INT, WitnessPayload::ticks,
			PacketCodecs.BOOL, WitnessPayload::fade,
			WitnessPayload::new);

	/** Back to an ordinary twilight, now. */
	public static WitnessPayload off() {
		return new WitnessPayload(false, 0.0F, 0, false);
	}

	/**
	 * The same, except the track is taken down slowly instead of cut.
	 *
	 * <p>Every other music change in the mod is a hard cut, and they are all interruptions - something
	 * arrived, something started. This is the one that is an ending, and an ending is the one thing in
	 * the dimension allowed to take its time.
	 */
	public static WitnessPayload fadeOut() {
		return new WitnessPayload(false, 0.0F, 0, true);
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
