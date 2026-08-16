package net.ragnar.ragnarstwilightdimension.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

/**
 * Your legs are not yours for the next four seconds.
 *
 * <p>Sent when the witness plants itself and points. For as long as it holds, the player walks
 * towards the point named here whatever they press - and they can still look wherever they like,
 * which is the entire trick. Taking the camera as well would read as a cutscene; taking only the legs
 * reads as being <i>drawn in</i>, because the player can watch themselves being walked into something
 * they can see perfectly well and cannot stop approaching.
 *
 * <p>Carries a position rather than an entity id for the same reason {@link StarePayload} does: it is
 * true the moment it lands, without anything else having to have arrived first. The witness does not
 * move while it points, so one position is good for the whole four seconds.
 *
 * @param ticks how long it holds, or zero to hand the legs back now. It expires on the client's own
 *              clock so a dropped "release" cannot leave anybody walking forever.
 */
public record WitnessPullPayload(double x, double y, double z, int ticks) implements CustomPayload {
	public static final CustomPayload.Id<WitnessPullPayload> ID =
			new CustomPayload.Id<>(Identifier.of(RagnarsTwilightDimension.MOD_ID, "witness_pull"));

	public static final PacketCodec<RegistryByteBuf, WitnessPullPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.DOUBLE, WitnessPullPayload::x,
			PacketCodecs.DOUBLE, WitnessPullPayload::y,
			PacketCodecs.DOUBLE, WitnessPullPayload::z,
			PacketCodecs.VAR_INT, WitnessPullPayload::ticks,
			WitnessPullPayload::new);

	/** Hands the legs back. */
	public static WitnessPullPayload release() {
		return new WitnessPullPayload(0.0, 0.0, 0.0, 0);
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
