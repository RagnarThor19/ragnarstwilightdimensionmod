package net.ragnar.ragnarstwilightdimension.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

/**
 * Tells one player that something is looking at them.
 *
 * <p>Sent to that player and nobody else, which is the whole shape of the event: the figure is a real
 * entity that anybody standing there could see, but the red, the lock, the shake and the loss of
 * control belong to one person. To everyone else, they just stopped walking.
 *
 * <p>Carries the point to look at rather than the entity's id on purpose. The figure is spawned in
 * the same tick this is sent, and entity spawn packets go out on the tracker's own schedule, so the
 * client can easily get this first and find nothing to aim at. A position is true the moment it
 * arrives and does not need anything else to have happened yet.
 *
 * <p>Three things arrive on this one packet, told apart by {@code ticks}:
 *
 * <ul>
 *   <li><b>positive</b> - it is happening now, and lasts that many ticks.
 *   <li><b>{@link #HUSH}</b> - it is about to happen. Cuts the music and does nothing else, half a
 *       second ahead of the rest, so the silence lands before the thing that caused it.
 *   <li><b>zero</b> - call it off. See {@code Stare} for the cases where the server has to end one
 *       early.
 * </ul>
 */
public record StarePayload(double x, double y, double z, int ticks) implements CustomPayload {
	public static final CustomPayload.Id<StarePayload> ID =
			new CustomPayload.Id<>(Identifier.of(RagnarsTwilightDimension.MOD_ID, "stare"));

	public static final PacketCodec<RegistryByteBuf, StarePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.DOUBLE, StarePayload::x,
			PacketCodecs.DOUBLE, StarePayload::y,
			PacketCodecs.DOUBLE, StarePayload::z,
			PacketCodecs.VAR_INT, StarePayload::ticks,
			StarePayload::new);

	/** Marks the packet that only kills the music. Negative so it can never be a real duration. */
	public static final int HUSH = -1;

	/** The one that cuts the music, sent {@code Stare.HUSH_LEAD_TICKS} before the event itself. */
	public static StarePayload hush() {
		return new StarePayload(0.0, 0.0, 0.0, HUSH);
	}

	/** The one that calls it off. */
	public static StarePayload cancel() {
		return new StarePayload(0.0, 0.0, 0.0, 0);
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
