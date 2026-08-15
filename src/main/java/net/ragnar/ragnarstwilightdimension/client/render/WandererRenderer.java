package net.ragnar.ragnarstwilightdimension.client.render;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.entity.WandererEntity;

/**
 * Draws the wanderer as an ordinary wide player in the default skin, scaled up to
 * {@link WandererEntity#HEIGHT} blocks.
 *
 * <p>Unlike {@link SilhouetteRenderer} this one keeps its shadow and has no minimum draw distance.
 * The silhouette hides both because it is meant to be unverifiable; this is meant to be seen, and a
 * four-block figure sprinting past with no shadow under it reads as a texture rather than a thing.
 */
public class WandererRenderer extends MobEntityRenderer<WandererEntity, PlayerEntityModel<WandererEntity>> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

	public WandererRenderer(EntityRendererFactory.Context context) {
		super(context,
				new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false),
				0.5F * WandererEntity.MODEL_SCALE);
	}

	/**
	 * The player model is built at 1.8 blocks, so everything drawn for this entity is taken up by the
	 * same factor its hitbox was. Applied here rather than baked into the model so the two cannot
	 * drift apart.
	 */
	@Override
	protected void scale(WandererEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(WandererEntity.MODEL_SCALE, WandererEntity.MODEL_SCALE, WandererEntity.MODEL_SCALE);
	}

	@Override
	public Identifier getTexture(WandererEntity entity) {
		return TEXTURE;
	}
}
