package net.ragnar.ragnarstwilightdimension.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.MusicSound;
import net.ragnar.ragnarstwilightdimension.client.TwilightClient;
import net.ragnar.ragnarstwilightdimension.client.TwilightMusic;
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
		if (TwilightClient.isInTwilight()) {
			cir.setReturnValue(TwilightMusic.TWILIGHT);
		}
	}
}
