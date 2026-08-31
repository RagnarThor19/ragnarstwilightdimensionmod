package net.ragnar.ragnarstwilightdimension.client.render;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;
import net.ragnar.ragnarstwilightdimension.entity.PaleFigureEntity;

/**
 * Draws the blank figure: the ordinary wide player model, in a texture that is nothing but white.
 *
 * <p>The outer layer - hat, jacket, sleeves, trouser legs - is switched off. On a skin that is one
 * flat colour those parts add nothing but a half-pixel of slop around every edge, and the edge is the
 * only thing this entity has. What is left is the exact player silhouette with no detail inside it.
 *
 * <p>No shadow, for the same reason the silhouette has none: it hangs thirty blocks up, and a patch
 * of shade arriving on the ground underneath the player would announce it before they looked.
 */
public class PaleFigureRenderer extends MobEntityRenderer<PaleFigureEntity, PlayerEntityModel<PaleFigureEntity>> {
	private static final Identifier TEXTURE =
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "textures/entity/pale_figure.png");

	public PaleFigureRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.0F);

		this.model.hat.visible = false;
		this.model.jacket.visible = false;
		this.model.leftSleeve.visible = false;
		this.model.rightSleeve.visible = false;
		this.model.leftPants.visible = false;
		this.model.rightPants.visible = false;

		// So one of them can carry a bow. Every other blank figure in the mod holds nothing and draws
		// nothing extra for this - an empty stack renders as an empty hand - so the feature costs the
		// ones that are only standing there absolutely nothing. See {@code ArcherAttack}.
		this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
	}

	/**
	 * Draws it at whatever size it was given.
	 *
	 * <p>One for every blank figure in the mod except the four that stand at the rim during the boss
	 * fight. The hitbox is unchanged and stays player-sized, which is deliberate - see
	 * {@link PaleFigureEntity} for why the box and the drawing are allowed to disagree.
	 */
	@Override
	protected void scale(PaleFigureEntity entity, MatrixStack matrices, float tickDelta) {
		float scale = entity.scale();

		if (scale != 1.0F) {
			matrices.scale(scale, scale, scale);
		}
	}

	@Override
	public Identifier getTexture(PaleFigureEntity entity) {
		return TEXTURE;
	}
}
