package net.ragnar.ragnarstwilightdimension.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import net.ragnar.ragnarstwilightdimension.client.BloodMoonClient;
import net.ragnar.ragnarstwilightdimension.client.TwilightClient;
import net.ragnar.ragnarstwilightdimension.client.TwilightFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {
	@Shadow
	private static float red;
	@Shadow
	private static float green;
	@Shadow
	private static float blue;

	/**
	 * Replaces the fog colour with blood while the event is up.
	 *
	 * <p>Outright, on the first frame after the packet lands - there is nothing to ease in from. See
	 * {@link BloodMoonClient} for why both edges are hard.
	 *
	 * <p>Written into vanilla's own colour fields rather than pushed straight at the shader, because
	 * those three floats are what {@code applyFogColor} hands the shader every frame.
	 *
	 * <p><b>The clear colour is deliberately left alone, and this is not an oversight - do not
	 * "fix" it.</b> Vanilla's own {@code RenderSystem.clearColor} call is the last statement of this
	 * method, so it has already run by the time this injection does and is still holding the ordinary
	 * twilight colour. Normally that would not be visible, but the dimension type uses the nether's
	 * effects, whose sky type is NONE - no sky is drawn at all, so the background of every frame is
	 * literally that clear colour.
	 *
	 * <p>So during the event the fog is red while the background behind it is not. Distant terrain
	 * that would ordinarily wash out invisibly instead sits against a colour it does not match, and
	 * the entire landscape comes through as hard silhouettes - the world drawn in outline, out to the
	 * horizon, through fog that is supposed to stop at eleven blocks. Re-issuing {@code clearColor}
	 * here with the tinted values makes the fog behave correctly and throws the effect away.
	 */
	@Inject(method = "render", at = @At("RETURN"))
	private static void twilight$bloodMoonFogColour(Camera camera, float tickDelta, ClientWorld world,
													int viewDistance, float skyDarkness, CallbackInfo ci) {
		if (camera.getSubmersionType() != CameraSubmersionType.NONE || !TwilightClient.isInTwilight()
				|| !BloodMoonClient.isActive()) {
			return;
		}

		red = BloodMoonClient.FOG_RED;
		green = BloodMoonClient.FOG_GREEN;
		blue = BloodMoonClient.FOG_BLUE;
	}
	/**
	 * Clamps the fog to a few blocks while in the twilight dimension. Runs after vanilla has set up
	 * its own fog so the values here simply win, but only when the camera is in open air - water,
	 * lava and powder snow keep their vanilla fog.
	 */
	@Inject(method = "applyFog", at = @At("RETURN"))
	private static void twilight$applyLiminalFog(Camera camera, BackgroundRenderer.FogType fogType,
												 float viewDistance, boolean thickFog, float tickDelta,
												 CallbackInfo ci) {
		if (camera.getSubmersionType() != CameraSubmersionType.NONE || !TwilightClient.isInTwilight()) {
			return;
		}

		RenderSystem.setShaderFogStart(TwilightFog.FOG_START);
		RenderSystem.setShaderFogEnd(TwilightFog.FOG_END);
		RenderSystem.setShaderFogShape(FogShape.SPHERE);
	}
}
