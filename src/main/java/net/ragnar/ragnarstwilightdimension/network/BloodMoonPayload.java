package net.ragnar.ragnarstwilightdimension.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

/**
 * Tells clients whether the blood moon is up.
 *
 * <p>The event is decided by the server - it is what spawns things and keeps the clock - but two of
 * its effects, the fog colour and the music, are purely client side. This is the one bit of state
 * that has to cross over.
 *
 * <p>Sent to every player on the server rather than only those in the twilight, so that walking into
 * the dimension part-way through an event does not need its own message. The client only acts on it
 * while actually in the twilight.
 */
public record BloodMoonPayload(boolean active) implements CustomPayload {
	public static final CustomPayload.Id<BloodMoonPayload> ID =
			new CustomPayload.Id<>(Identifier.of(RagnarsTwilightDimension.MOD_ID, "bloodmoon"));

	public static final PacketCodec<RegistryByteBuf, BloodMoonPayload> CODEC =
			PacketCodec.tuple(PacketCodecs.BOOL, BloodMoonPayload::active, BloodMoonPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** Has to run on both sides, so this is called from the common initialiser. */
	public static void register() {
		PayloadTypeRegistry.playS2C().register(ID, CODEC);
	}
}
