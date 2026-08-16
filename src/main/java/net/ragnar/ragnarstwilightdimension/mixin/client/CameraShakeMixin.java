package net.ragnar.ragnarstwilightdimension.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import net.ragnar.ragnarstwilightdimension.client.StareClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * Shakes the camera while something is looking at the player.
 *
 * <p>Applied to the camera rather than to the player, which matters: the player's own yaw and pitch
 * are being held on the figure by {@link StareClient}, and jittering those would be sending the shake
 * to the server as a real head movement. Here it exists for exactly one frame of drawing and nothing
 * outside the renderer ever sees it.
 *
 * <p>Re-rolled every frame rather than run off a wave, so it does not settle into a rhythm the eye
 * can follow. A rhythm reads as a machine; noise reads as something too big to be holding still.
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	public abstract float getYaw();

	@Shadow
	public abstract float getPitch();

	private static final Random TWILIGHT$SHAKE = new Random();

	@Inject(method = "update", at = @At("TAIL"))
	private void twilight$shake(BlockView area, Entity focusedEntity, boolean thirdPerson,
								boolean inverseView, float tickDelta, CallbackInfo ci) {
		if (!StareClient.isActive()) {
			return;
		}

		float yaw = getYaw() + jitter();
		float pitch = getPitch() + jitter();
		setRotation(yaw, pitch);
	}

	private static float jitter() {
		return (TWILIGHT$SHAKE.nextFloat() * 2.0F - 1.0F) * StareClient.SHAKE_DEGREES;
	}
}
