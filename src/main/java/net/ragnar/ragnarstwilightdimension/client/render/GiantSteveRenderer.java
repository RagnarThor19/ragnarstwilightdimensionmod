package net.ragnar.ragnarstwilightdimension.client.render;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.entity.GiantSteveEntity;

/**
 * Draws the giant as an ordinary wide player in the default skin, scaled up to
 * {@link GiantSteveEntity#HEIGHT} blocks.
 *
 * <p>Nothing is dressed up and nothing is hidden - same model and same texture as the ones running
 * at you, just far too much of it. The fog does the rest at the distance it stands at.
 */
public class GiantSteveRenderer extends MobEntityRenderer<GiantSteveEntity, PlayerEntityModel<GiantSteveEntity>> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

	public GiantSteveRenderer(EntityRendererFactory.Context context) {
		super(context,
				new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false),
				0.5F * GiantSteveEntity.MODEL_SCALE);
	}

	/**
	 * The player model is built at 1.8 blocks, so everything drawn for this entity is taken up by the
	 * same factor its hitbox was. Applied here rather than baked into the model so the two cannot
	 * drift apart.
	 */
	@Override
	protected void scale(GiantSteveEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(GiantSteveEntity.MODEL_SCALE, GiantSteveEntity.MODEL_SCALE, GiantSteveEntity.MODEL_SCALE);
	}

	@Override
	public Identifier getTexture(GiantSteveEntity entity) {
		return TEXTURE;
	}
}
