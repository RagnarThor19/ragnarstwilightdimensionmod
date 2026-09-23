package net.ragnar.ragnarstwilightdimension.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;

/**
 * One beat.
 *
 * <p>Carries nothing, because there is nothing to carry. The server knows who is under the world and
 * how long they have been there; the client only needs to be told the moment a beat lands, and
 * everything it then does - the sound, and the second of open fog - it runs on its own clock from
 * there.
 *
 * <p>Sent to one player rather than broadcast, unlike {@code BloodMoonPayload}. The blood moon is
 * weather and happens to everybody at once; this is happening to <i>you</i>, and two people standing
 * next to each other under the world are each on their own count.
 *
 * <p>It is a pulse and not a state, which means a dropped one costs a single beat and nothing else.
 * There is deliberately no "it has stopped" message to lose: what the client holds is a one-second
 * countdown that expires by itself, so a player who logs out mid-beat, or walks back up through the
 * ceiling, cannot be left with the fog stuck open.
 */
public record DeepPayload() implements CustomPayload {
	public static final CustomPayload.Id<DeepPayload> ID =
			new CustomPayload.Id<>(Identifier.of(RagnarsTwilightDimension.MOD_ID, "deep"));

	public static final PacketCodec<RegistryByteBuf, DeepPayload> CODEC =
			PacketCodec.unit(new DeepPayload());

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** Has to run on both sides, so this is called from the common initialiser. */
	public static void register() {
		PayloadTypeRegistry.playS2C().register(ID, CODEC);
	}
}
