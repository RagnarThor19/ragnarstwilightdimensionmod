package net.ragnar.ragnarstwilightdimension.client.render;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.entity.DeepSteveEntity;

/**
 * Draws the one in the bedrock layer as an ordinary wide player in the default skin, scaled up to
 * {@link DeepSteveEntity#HEIGHT} blocks.
 *
 * <p>Nothing is dressed up, nothing is hidden and nothing is given its own texture - it is Steve, at
 * fifty blocks, standing still in a room with no light in it. Which face it is matters less than
 * that it is a face you already know.
 */
public class DeepSteveRenderer extends MobEntityRenderer<DeepSteveEntity, PlayerEntityModel<DeepSteveEntity>> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

	public DeepSteveRenderer(EntityRendererFactory.Context context) {
		super(context,
				new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false),
				0.5F * DeepSteveEntity.MODEL_SCALE);
	}

	/**
	 * The player model is built at 1.8 blocks, so everything drawn for this entity is taken up by the
	 * same factor its hitbox was. Applied here rather than baked into the model so the two cannot
	 * drift apart.
	 */
	@Override
	protected void scale(DeepSteveEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(DeepSteveEntity.MODEL_SCALE, DeepSteveEntity.MODEL_SCALE, DeepSteveEntity.MODEL_SCALE);
	}

	@Override
	public Identifier getTexture(DeepSteveEntity entity) {
		return TEXTURE;
	}
}
