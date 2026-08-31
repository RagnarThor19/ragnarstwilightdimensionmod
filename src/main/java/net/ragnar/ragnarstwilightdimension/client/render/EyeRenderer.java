package net.ragnar.ragnarstwilightdimension.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;
import net.ragnar.ragnarstwilightdimension.entity.EyeEntity;

/**
 * Draws the eye: one flat square, turned to face the camera.
 *
 * <h2>Why it always looks at you</h2>
 *
 * <p>{@code dispatcher.getRotation()} is the camera's own rotation, and multiplying the matrix by it
 * cancels out the viewer's orientation entirely - the quad is drawn square-on to the screen no matter
 * where the viewer stands or which way they turn. That is the whole trick. There is no aiming code
 * and nothing tracking anybody, because a billboard cannot do anything <em>except</em> look at you.
 *
 * <h2>Why it is visible in the dark</h2>
 *
 * <p>The disc has no skylight and there is nothing out in the void to light anything, so an ordinarily
 * lit quad hanging a hundred blocks out would be drawn nearly black on black. The emissive layer
 * ignores the lightmap completely and draws the texture at its own brightness, which is what makes
 * this readable at the distance it is meant to be seen from.
 *
 * <p>Translucent rather than cutout, because the fade in and out is done by alpha on the vertex
 * colour and a cutout layer would round that to fully on or fully off.
 */
@Environment(EnvType.CLIENT)
public class EyeRenderer extends EntityRenderer<EyeEntity> {
	private static final Identifier TEXTURE =
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "textures/entity/eye.png");

	private static final RenderLayer LAYER = RenderLayer.getEntityTranslucentEmissive(TEXTURE);

	public EyeRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public void render(EyeEntity eye, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider vertexConsumers, int light) {
		float alpha = eye.alpha(tickDelta);
		if (alpha <= 0.0F) {
			return;
		}

		float half = EyeEntity.SIZE * eye.scale() / 2.0F;

		matrices.push();

		// An entity's position is the middle of the bottom of its box, so lifting by half the height
		// centres the picture inside the box it is culled against. Without this the eye hangs half
		// below its own bounding box and gets clipped away whenever you look at its lower half.
		matrices.translate(0.0F, half, 0.0F);
		matrices.multiply(this.dispatcher.getRotation());

		MatrixStack.Entry entry = matrices.peek();
		VertexConsumer buffer = vertexConsumers.getBuffer(LAYER);
		int a = (int) (alpha * 255.0F);

		vertex(buffer, entry, -half, -half, 0.0F, 1.0F, a);
		vertex(buffer, entry, half, -half, 1.0F, 1.0F, a);
		vertex(buffer, entry, half, half, 1.0F, 0.0F, a);
		vertex(buffer, entry, -half, half, 0.0F, 0.0F, a);

		matrices.pop();

		super.render(eye, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry,
							   float x, float y, float u, float v, int alpha) {
		buffer.vertex(entry, x, y, 0.0F)
				.color(255, 255, 255, alpha)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				// Full bright. The emissive layer ignores this, but it is what the layer would want if
				// it were ever swapped for one that does not.
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(entry, 0.0F, 0.0F, 1.0F);
	}

	@Override
	public Identifier getTexture(EyeEntity eye) {
		return TEXTURE;
	}
}
