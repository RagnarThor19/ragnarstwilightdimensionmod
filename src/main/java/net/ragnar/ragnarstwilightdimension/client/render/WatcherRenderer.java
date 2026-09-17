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
import net.minecraft.util.math.RotationAxis;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;
import net.ragnar.ragnarstwilightdimension.entity.WatcherEntity;
import org.joml.Vector3f;

/**
 * Draws the watcher: a hundred blocks of black, with the eye for a face.
 *
 * <h2>Why it is flat</h2>
 *
 * <p>There is no model here and there is not going to be one. The thing is pure silhouette - every
 * part of it that is not the face is one colour, and that colour is black - so a model would be a
 * hundred blocks of geometry that cannot be told apart from the same shape cut out of paper. What is
 * drawn instead is that cut-out: nine flat pieces in a plane, turned to face the viewer.
 *
 * <p>Turned <b>on one axis only</b>, which is the one difference from {@code EyeRenderer}. The eye
 * uses the camera's whole rotation, pitch included, because a disc has no up. A figure does: cancel
 * the viewer's pitch out of a hundred-block-tall shape and looking up at it tips the entire thing over
 * backwards like a felled tree. So only the yaw is taken, and it is read back off the camera's own
 * quaternion rather than asked for separately, so the two renderers cannot drift apart.
 *
 * <h2>Why the black is worth drawing at all</h2>
 *
 * <p>Against the void it is invisible, and that is correct - it is not meant to be lit, it is meant to
 * be an absence. What makes it a shape is everything it stands in front of: the white far rim of the
 * disc, the snow coming down, the other watchers. Somebody at the middle of the circle sees its legs
 * as two black bars cut out of the floor on the other side, and nothing at all above them, until they
 * look up.
 */
@Environment(EnvType.CLIENT)
public class WatcherRenderer extends EntityRenderer<WatcherEntity> {
	/** The body: sixteen pixels of nothing, used for every piece that is not the face. */
	private static final Identifier BLACK =
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "textures/entity/watcher.png");

	/** The face, and the same file the eyes out in the dark have always used. */
	private static final Identifier EYE =
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "textures/entity/eye.png");

	private static final RenderLayer BODY = RenderLayer.getEntityTranslucent(BLACK);

	/**
	 * Emissive, for the same reason the eye's is: there is no light out here, and an ordinarily lit
	 * face eighty blocks up would be drawn as black on black. The body wants no such thing - it is
	 * supposed to be exactly as dark as whatever is behind it.
	 */
	private static final RenderLayer FACE = RenderLayer.getEntityTranslucentEmissive(EYE);

	/**
	 * The shape, in blocks off the floor of it, at {@link WatcherEntity#HEIGHT} tall.
	 *
	 * <p>Gaunt on purpose. The proportions are a person's stretched vertically and then narrowed
	 * again: at this height an ordinary human ratio reads as a giant, and a giant is a thing of a
	 * known kind that happens to be big. Something eight times as tall as it is wide is not.
	 */
	private static final float HIP_Y = 44.0F;
	private static final float CHEST_Y = 70.0F;
	private static final float SHOULDER_Y = 76.0F;
	private static final float NECK_Y = 82.0F;

	private static final float FOOT_HALF = 3.5F;
	private static final float HIP_HALF = 6.5F;
	private static final float CHEST_HALF = 12.0F;
	private static final float SHOULDER_HALF = 14.0F;
	private static final float NECK_HALF = 3.0F;

	/** The face is square, because the texture is. It runs from the neck to the top of the box. */
	private static final float HEAD_HALF = (WatcherEntity.HEIGHT - NECK_Y) / 2.0F;

	/** Where the arms hang from, and to. They reach past the hip, as everything's do in the mod. */
	private static final float ARM_TOP_Y = 73.0F;
	private static final float ARM_BOTTOM_Y = 18.0F;
	private static final float ARM_TOP_INNER = 10.0F;
	private static final float ARM_TOP_OUTER = 14.0F;
	private static final float ARM_BOTTOM_INNER = 13.5F;
	private static final float ARM_BOTTOM_OUTER = 17.5F;

	/**
	 * How far in front of the body the face is drawn.
	 *
	 * <p>Towards the viewer is local {@code -z} once the yaw has been applied. A fifth of a block is
	 * nothing at eighty blocks out and is enough to keep the face off the neck it overlaps, which
	 * would otherwise flicker between the two as the camera moves.
	 */
	private static final float FACE_OUT = -0.2F;

	/**
	 * Where the body texture is sampled.
	 *
	 * <p>It is one colour, so which point does not matter and the middle is as good as anywhere. What
	 * matters is that it is a <em>point</em> rather than the whole image: a piece drawn across the full
	 * {@code 0..1} filters its edges against the edge of the texture, and these pieces are far too
	 * large for that to stay invisible.
	 */
	private static final float BLACK_U = 0.5F;
	private static final float BLACK_V = 0.5F;

	/** How much wider the eye is drawn as it winds up, and how much of it is the glow. */
	private static final float CHARGE_SWELL = 0.35F;
	private static final float CHARGE_GLOW = 0.75F;

	/** The cold white-blue everything else in this fight lights up in. */
	private static final int GLOW_R = 205;
	private static final int GLOW_G = 230;
	private static final int GLOW_B = 255;

	public WatcherRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public void render(WatcherEntity watcher, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider vertexConsumers, int light) {
		float alpha = watcher.alpha(tickDelta);

		if (alpha <= 0.0F) {
			return;
		}

		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotation(cameraYaw()));

		MatrixStack.Entry entry = matrices.peek();
		int a = (int) (alpha * 255.0F);

		body(vertexConsumers.getBuffer(BODY), entry, a);
		face(vertexConsumers.getBuffer(FACE), entry, watcher.charge(), a);

		matrices.pop();

		super.render(watcher, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	/**
	 * Which way the camera is pointing on the ground, in radians.
	 *
	 * <p>Taken out of the dispatcher's own rotation rather than off the camera, so this is by
	 * construction the same turn the eye gets with its pitch left in. The quaternion sends local
	 * {@code +z} to wherever the viewer is looking; flattening that and taking its bearing is the yaw.
	 */
	private float cameraYaw() {
		Vector3f facing = this.dispatcher.getRotation().transform(new Vector3f(0.0F, 0.0F, 1.0F));
		return (float) Math.atan2(facing.x, facing.z);
	}

	/** Everything that is not the face, from the feet up. */
	private static void body(VertexConsumer buffer, MatrixStack.Entry entry, int alpha) {
		piece(buffer, entry, 0.0F, FOOT_HALF, HIP_Y, HIP_HALF, alpha);
		piece(buffer, entry, HIP_Y, HIP_HALF, CHEST_Y, CHEST_HALF, alpha);
		piece(buffer, entry, CHEST_Y, CHEST_HALF, SHOULDER_Y, SHOULDER_HALF, alpha);
		piece(buffer, entry, SHOULDER_Y, NECK_HALF, NECK_Y, NECK_HALF, alpha);

		// The arms, hanging off the shoulders and splayed a little at the wrist, one each side.
		for (int side = -1; side <= 1; side += 2) {
			quad(buffer, entry, BLACK_U, BLACK_V,
					ARM_BOTTOM_Y, side * ARM_BOTTOM_INNER, side * ARM_BOTTOM_OUTER,
					ARM_TOP_Y, side * ARM_TOP_INNER, side * ARM_TOP_OUTER, 255, 255, 255, alpha);
		}
	}

	/**
	 * The face, and the glow of it winding up.
	 *
	 * <p>Two passes of the same picture. The first is the eye as it has always been drawn. The second
	 * is the charge: the same texture again, larger and brighter with every tick of the wind-up, added
	 * over the top - which lights the white of the eye and does nothing whatsoever to the black around
	 * it, so what the player sees is an eye opening rather than a square getting brighter.
	 */
	private static void face(VertexConsumer buffer, MatrixStack.Entry entry, float charge, int alpha) {
		square(buffer, entry, NECK_Y, HEAD_HALF, 255, 255, 255, alpha);

		if (charge > 0.0F) {
			float half = HEAD_HALF * (1.0F + CHARGE_SWELL * charge);
			int lit = (int) (alpha * Math.min(1.0F, charge) * CHARGE_GLOW);

			square(buffer, entry, NECK_Y - (half - HEAD_HALF), half, GLOW_R, GLOW_G, GLOW_B, lit);
		}
	}

	/** A piece of the body: a trapezium from one width to another, centred on the spine. */
	private static void piece(VertexConsumer buffer, MatrixStack.Entry entry,
							  float bottomY, float bottomHalf, float topY, float topHalf, int alpha) {
		quad(buffer, entry, BLACK_U, BLACK_V,
				bottomY, -bottomHalf, bottomHalf, topY, -topHalf, topHalf, 255, 255, 255, alpha);
	}

	/** The face: the whole of the eye texture, square, sitting just in front of the body. */
	private static void square(VertexConsumer buffer, MatrixStack.Entry entry,
							   float bottomY, float half, int r, int g, int b, int alpha) {
		float top = bottomY + half * 2.0F;

		vertex(buffer, entry, -half, bottomY, FACE_OUT, 0.0F, 1.0F, r, g, b, alpha);
		vertex(buffer, entry, half, bottomY, FACE_OUT, 1.0F, 1.0F, r, g, b, alpha);
		vertex(buffer, entry, half, top, FACE_OUT, 1.0F, 0.0F, r, g, b, alpha);
		vertex(buffer, entry, -half, top, FACE_OUT, 0.0F, 0.0F, r, g, b, alpha);
	}

	/** A four-cornered piece with its own width at the bottom and at the top. */
	private static void quad(VertexConsumer buffer, MatrixStack.Entry entry, float u, float v,
							 float bottomY, float bottomLeft, float bottomRight,
							 float topY, float topLeft, float topRight,
							 int r, int g, int b, int alpha) {
		vertex(buffer, entry, bottomLeft, bottomY, 0.0F, u, v, r, g, b, alpha);
		vertex(buffer, entry, bottomRight, bottomY, 0.0F, u, v, r, g, b, alpha);
		vertex(buffer, entry, topRight, topY, 0.0F, u, v, r, g, b, alpha);
		vertex(buffer, entry, topLeft, topY, 0.0F, u, v, r, g, b, alpha);
	}

	private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry,
							   float x, float y, float z, float u, float v,
							   int r, int g, int b, int alpha) {
		buffer.vertex(entry, x, y, z)
				.color(r, g, b, alpha)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				// Full bright. The face's layer ignores it; the body's does not, and a black pixel is
				// black at every light level there is, so it costs nothing to hand both the same thing.
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(entry, 0.0F, 0.0F, 1.0F);
	}

	@Override
	public Identifier getTexture(WatcherEntity watcher) {
		return BLACK;
	}
}
