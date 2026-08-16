package net.ragnar.ragnarstwilightdimension.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.MusicSound;
import net.ragnar.ragnarstwilightdimension.client.BloodMoonClient;
import net.ragnar.ragnarstwilightdimension.client.TwilightClient;
import net.ragnar.ragnarstwilightdimension.client.TwilightMusic;
import net.ragnar.ragnarstwilightdimension.client.WitnessClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMusicMixin {
	/**
	 * Hands vanilla's music tracker the twilight playlist while the player is in the dimension.
	 * Because the returned {@link MusicSound} replaces the current music, whatever was playing in
	 * the overworld stops as soon as you arrive.
	 */
	@Inject(method = "getMusicType", at = @At("HEAD"), cancellable = true)
	private void twilight$useTwilightMusic(CallbackInfoReturnable<MusicSound> cir) {
		if (!TwilightClient.isInTwilight()) {
			return;
		}

		// The witness wins over the blood moon, on the grounds that one of them is happening to the
		// whole dimension and the other is happening to you personally, two metres away.
		if (WitnessClient.isMusicActive()) {
			cir.setReturnValue(TwilightMusic.WITNESS);
			return;
		}

		// Checked before the ringing out below, and it is the only thing that is. A blood moon starting
		// over the top of a witness that has just died does not wait politely for its track to finish -
		// nothing in this dimension waits for the blood moon.
		if (BloodMoonClient.isActive()) {
			cir.setReturnValue(TwilightMusic.BLOODMOON);
			return;
		}

		// Its track outlives it by however long the file has left. Handing back the ordinary playlist
		// here would cut it off mid-bar, so for as long as it is still going the dimension asks for a
		// version of itself that is not allowed to interrupt anything.
		cir.setReturnValue(WitnessClient.isMusicRingingOut()
				? TwilightMusic.TWILIGHT_RINGING_OUT
				: TwilightMusic.TWILIGHT);
	}
}
