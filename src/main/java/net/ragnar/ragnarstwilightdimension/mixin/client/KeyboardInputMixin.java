package net.ragnar.ragnarstwilightdimension.mixin.client;

import net.minecraft.client.input.KeyboardInput;
import net.ragnar.ragnarstwilightdimension.client.StareClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The one place in the mod where the keyboard stops being the player's.
 *
 * <p>Injected after vanilla has read the keys rather than in front of it, so the keys themselves are
 * still tracked - what is thrown away is only this tick's worth of intent. Releasing and re-pressing
 * a key during the three seconds behaves exactly as it would have; it just does not go anywhere.
 *
 * <p>Gravity is left alone: somebody caught mid-fall keeps falling, because what is being taken away
 * is the player's control and not the world's.
 *
 * <p>The witness used to be here too - it took the legs for the four seconds it spent pointing and
 * walked them at itself. It does not any more. What it points at now comes down on its own and has to
 * be got out of the way of, and a boss that hands you a dodge and then takes the controls to do it is
 * two designs at once.
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void twilight$dropInput(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		if (!StareClient.isLocked()) {
			return;
		}

		KeyboardInput input = (KeyboardInput) (Object) this;
		input.movementForward = 0.0F;
		input.movementSideways = 0.0F;
		input.jumping = false;
		input.sneaking = false;
	}
}
