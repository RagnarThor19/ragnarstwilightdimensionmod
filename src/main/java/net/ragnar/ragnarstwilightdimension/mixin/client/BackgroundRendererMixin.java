package net.ragnar.ragnarstwilightdimension.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.ragnar.ragnarstwilightdimension.client.TwilightFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {
	/**
	 * Clamps the fog to a few blocks while in the twilight dimension. Runs after vanilla has set up
	 * its own fog so the values here simply win, but only when the camera is in open air - water,
	 * lava and powder snow keep their vanilla fog.
	 */
	@Inject(method = "applyFog", at = @At("RETURN"))
	private static void twilight$applyLiminalFog(Camera camera, BackgroundRenderer.FogType fogType,
												 float viewDistance, boolean thickFog, float tickDelta,
												 CallbackInfo ci) {
		if (camera.getSubmersionType() != CameraSubmersionType.NONE || !TwilightFog.isInTwilight()) {
			return;
		}

		RenderSystem.setShaderFogStart(TwilightFog.FOG_START);
		RenderSystem.setShaderFogEnd(TwilightFog.FOG_END);
		RenderSystem.setShaderFogShape(FogShape.SPHERE);
	}
}
