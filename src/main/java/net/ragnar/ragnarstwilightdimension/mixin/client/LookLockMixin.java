package net.ragnar.ragnarstwilightdimension.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.ragnar.ragnarstwilightdimension.client.StareClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes the mouse away for the three seconds of a stare.
 *
 * <p>{@code changeLookDirection} is the hook rather than {@code Mouse.updateMouse}, and the
 * difference is not cosmetic. The mouse handler works out how far the cursor moved since the last
 * frame and then stores that frame's position; cancelling the whole method would leave the stored
 * position three seconds stale, and the moment the lock came off the entire accumulated movement
 * would be applied in one frame. Cancelling only the step that applies the rotation lets the
 * bookkeeping run as normal, so when it ends the player is still looking exactly where the figure
 * was and nothing has piled up behind the door.
 */
@Mixin(Entity.class)
public class LookLockMixin {
	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
	private void twilight$holdTheView(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		if (StareClient.isLocked() && (Object) this == MinecraftClient.getInstance().player) {
			ci.cancel();
		}
	}
}
