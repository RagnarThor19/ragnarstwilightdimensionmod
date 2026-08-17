package net.ragnar.ragnarstwilightdimension.client.render;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.entity.ChurchSteveEntity;

/**
 * Draws the congregation: one ordinary wide player in the default skin, sat in a pew.
 *
 * <p>Same model and same texture as everything else that wears a face in this dimension. The only
 * work done here is dropping him onto the seat - the pose itself is {@link ChurchSteveModel}'s.
 */
public class ChurchSteveRenderer extends MobEntityRenderer<ChurchSteveEntity, ChurchSteveModel> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

	/**
	 * How far the whole figure is lowered, in blocks.
	 *
	 * <p>The entity stands at the height of the seat, because that is where a seated person's weight
	 * is and it keeps the hitbox out of the bench. The model does not know that: it is built standing,
	 * with its hips three quarters of a block up from wherever the entity is. Left alone he would sit
	 * in mid-air with his legs out over the back of the pew in front.
	 *
	 * <p>So the model comes down by exactly the height of those hips, which puts the hip joint - the
	 * thing the seated pose rotates the legs around - on the entity's own position, and the entity's
	 * own position on the seat surface.
	 */
	private static final float SEAT_DROP = 0.75F;

	public ChurchSteveRenderer(EntityRendererFactory.Context context) {
		super(context, new ChurchSteveModel(context.getPart(EntityModelLayers.PLAYER), false), 0.5F);
	}

	/**
	 * By the time this runs the matrix has already been flipped upside down for the model, which is
	 * why lowering him in the world is a <i>positive</i> translation here - the same sign vanilla uses
	 * two lines later when it lifts every entity model up onto its feet.
	 */
	@Override
	protected void scale(ChurchSteveEntity entity, MatrixStack matrices, float amount) {
		matrices.translate(0.0F, SEAT_DROP, 0.0F);
	}

	@Override
	public Identifier getTexture(ChurchSteveEntity entity) {
		return TEXTURE;
	}
}
